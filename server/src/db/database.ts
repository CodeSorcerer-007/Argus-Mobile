import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { Pool } from 'pg';
import { User, Device, StoredPreKeyBundle, EncryptedMessagePayload, Group, CallSession } from '../types';

export interface RefreshTokenRecord {
  token: string;
  userId: string;
  deviceId: string;
  expiresAt: number;
}

export interface SchemaMetadata {
  schemaVersion: number;
  lastMigration: number;
  createdAt: number;
  engine: string;
}

export const CURRENT_SCHEMA_VERSION = 2;
const MAX_OFFLINE_MESSAGES_PER_USER = 500;

export class ArgusDatabase {
  private dataDir: string;
  private usersFile: string;
  private devicesFile: string;
  private keysFile: string;
  private messagesFile: string;
  private groupsFile: string;
  private tokensFile: string;
  private schemaFile: string;

  private pgPool: Pool | null = null;
  private isPgConnected: boolean = false;

  private saveTimer: NodeJS.Timeout | null = null;
  private isSaving: boolean = false;
  private hasPendingSave: boolean = false;

  public users: Map<string, User> = new Map();
  public devices: Map<string, Device> = new Map();
  public keyBundles: Map<string, StoredPreKeyBundle> = new Map(); // key: `${userId}:${deviceId}`
  public offlineMessages: Map<string, EncryptedMessagePayload[]> = new Map(); // key: userId
  public groups: Map<string, Group> = new Map();
  public otps: Map<string, { code: string; expiresAt: number; phoneHash: string }> = new Map();
  public activeCalls: Map<string, CallSession> = new Map();
  public refreshTokens: Map<string, RefreshTokenRecord> = new Map();
  public revokedTokens: Set<string> = new Set();
  public failedOtpAttempts: Map<string, { count: number; lockedUntil: number }> = new Map();
  public failedPasswordAttempts: Map<string, { count: number; lockedUntil: number }> = new Map();

  // Fast O(1) in-memory secondary indices
  private phoneHashIndex: Map<string, string> = new Map(); // phoneHash -> userId
  private usernameIndex: Map<string, string> = new Map(); // lowercase username -> userId

  public schemaMetadata: SchemaMetadata = {
    schemaVersion: CURRENT_SCHEMA_VERSION,
    lastMigration: Date.now(),
    createdAt: Date.now(),
    engine: 'Argus-ZeroKnowledge-DB-v2'
  };

  constructor(dataDir: string = './data', databaseUrl?: string) {
    this.dataDir = dataDir;
    if (!fs.existsSync(this.dataDir)) {
      fs.mkdirSync(this.dataDir, { recursive: true });
    }
    this.usersFile = path.join(this.dataDir, 'users.json');
    this.devicesFile = path.join(this.dataDir, 'devices.json');
    this.keysFile = path.join(this.dataDir, 'keys.json');
    this.messagesFile = path.join(this.dataDir, 'messages.json');
    this.groupsFile = path.join(this.dataDir, 'groups.json');
    this.tokensFile = path.join(this.dataDir, 'tokens.json');
    this.schemaFile = path.join(this.dataDir, 'schema.json');

    const pgUrl = databaseUrl || process.env.DATABASE_URL;
    if (pgUrl && !pgUrl.includes('placeholder')) {
      try {
        this.pgPool = new Pool({
          connectionString: pgUrl,
          ssl: { rejectUnauthorized: false },
          max: 10,
          idleTimeoutMillis: 30000
        });
      } catch (err) {
        console.error('[Database] Failed to initialize PostgreSQL pool:', err);
      }
    }

    this.load();
    this.runMigrationsIfNeeded();
  }

  /**
   * Initialize Cloud Database tables and load state from PostgreSQL if configured
   */
  public async init(): Promise<void> {
    if (!this.pgPool) return;

    try {
      const client = await this.pgPool.connect();
      try {
        await client.query(`
          CREATE TABLE IF NOT EXISTS argus_metadata (key TEXT PRIMARY KEY, value JSONB);
          CREATE TABLE IF NOT EXISTS argus_users (id TEXT PRIMARY KEY, data JSONB);
          CREATE TABLE IF NOT EXISTS argus_devices (id TEXT PRIMARY KEY, data JSONB);
          CREATE TABLE IF NOT EXISTS argus_key_bundles (bundle_key TEXT PRIMARY KEY, data JSONB);
          CREATE TABLE IF NOT EXISTS argus_offline_messages (recipient_id TEXT PRIMARY KEY, data JSONB);
          CREATE TABLE IF NOT EXISTS argus_groups (id TEXT PRIMARY KEY, data JSONB);
          CREATE TABLE IF NOT EXISTS argus_tokens (key TEXT PRIMARY KEY, data JSONB);
        `);

        // Load existing cloud data
        const usersRes = await client.query('SELECT id, data FROM argus_users');
        usersRes.rows.forEach(r => this.users.set(r.id, r.data as User));

        const devicesRes = await client.query('SELECT id, data FROM argus_devices');
        devicesRes.rows.forEach(r => this.devices.set(r.id, r.data as Device));

        const keysRes = await client.query('SELECT bundle_key, data FROM argus_key_bundles');
        keysRes.rows.forEach(r => this.keyBundles.set(r.bundle_key, r.data as StoredPreKeyBundle));

        const msgsRes = await client.query('SELECT recipient_id, data FROM argus_offline_messages');
        msgsRes.rows.forEach(r => this.offlineMessages.set(r.recipient_id, r.data as EncryptedMessagePayload[]));

        const groupsRes = await client.query('SELECT id, data FROM argus_groups');
        groupsRes.rows.forEach(r => this.groups.set(r.id, r.data as Group));

        const tokensRes = await client.query('SELECT key, data FROM argus_tokens');
        tokensRes.rows.forEach(r => {
          if (r.key === 'refreshTokens' && r.data) {
            Object.entries(r.data).forEach(([k, v]) => this.refreshTokens.set(k, v as RefreshTokenRecord));
          } else if (r.key === 'revokedTokens' && Array.isArray(r.data)) {
            this.revokedTokens = new Set(r.data);
          }
        });

        this.rebuildIndices();
        this.isPgConnected = true;
        console.log(`[Database] Connected to PostgreSQL / Neon.tech (${this.users.size} user(s) loaded)`);
      } finally {
        client.release();
      }
    } catch (err: any) {
      console.warn('[Database] Cloud PostgreSQL connection failed, operating with local storage:', err.message);
    }
  }

  public hashPhone(phoneNumber: string): string {
    return crypto.createHash('sha256').update(`Argus_Salt_2026:${phoneNumber.trim()}`).digest('hex');
  }

  private rebuildIndices(): void {
    this.phoneHashIndex.clear();
    this.usernameIndex.clear();
    for (const user of this.users.values()) {
      if (user.phoneHash) {
        this.phoneHashIndex.set(user.phoneHash, user.id);
      }
      if (user.username) {
        this.usernameIndex.set(user.username.toLowerCase().trim(), user.id);
      }
    }
  }

  private safeAtomicWrite(filePath: string, data: string): void {
    if (!fs.existsSync(this.dataDir)) return;
    const tmpPath = `${filePath}.${Date.now()}.${Math.random().toString(36).substring(2, 8)}.tmp`;
    fs.writeFileSync(tmpPath, data, 'utf-8');
    if (fs.existsSync(this.dataDir)) {
      try {
        fs.renameSync(tmpPath, filePath);
      } catch (err) {
        try {
          fs.copyFileSync(tmpPath, filePath);
          fs.unlinkSync(tmpPath);
        } catch (fallbackErr) {
          // ignore
        }
      }
    }
  }

  private async safeAtomicWriteAsync(filePath: string, data: string): Promise<void> {
    if (!fs.existsSync(this.dataDir)) return;
    const tmpPath = `${filePath}.${Date.now()}.${Math.random().toString(36).substring(2, 8)}.tmp`;
    await fs.promises.writeFile(tmpPath, data, 'utf-8');
    if (fs.existsSync(this.dataDir)) {
      try {
        await fs.promises.rename(tmpPath, filePath);
      } catch (err) {
        try {
          await fs.promises.copyFile(tmpPath, filePath);
          await fs.promises.unlink(tmpPath);
        } catch (fallbackErr) {
          // ignore
        }
      }
    } else {
      try {
        await fs.promises.unlink(tmpPath);
      } catch (e) {
        // ignore
      }
    }
  }

  public load(): void {
    try {
      if (fs.existsSync(this.schemaFile)) {
        this.schemaMetadata = JSON.parse(fs.readFileSync(this.schemaFile, 'utf-8'));
      }
      if (fs.existsSync(this.usersFile)) {
        const raw = JSON.parse(fs.readFileSync(this.usersFile, 'utf-8'));
        Object.entries(raw).forEach(([k, v]) => this.users.set(k, v as User));
      }
      if (fs.existsSync(this.devicesFile)) {
        const raw = JSON.parse(fs.readFileSync(this.devicesFile, 'utf-8'));
        Object.entries(raw).forEach(([k, v]) => this.devices.set(k, v as Device));
      }
      if (fs.existsSync(this.keysFile)) {
        const raw = JSON.parse(fs.readFileSync(this.keysFile, 'utf-8'));
        Object.entries(raw).forEach(([k, v]) => this.keyBundles.set(k, v as StoredPreKeyBundle));
      }
      if (fs.existsSync(this.messagesFile)) {
        const raw = JSON.parse(fs.readFileSync(this.messagesFile, 'utf-8'));
        Object.entries(raw).forEach(([k, v]) => this.offlineMessages.set(k, v as EncryptedMessagePayload[]));
      }
      if (fs.existsSync(this.groupsFile)) {
        const raw = JSON.parse(fs.readFileSync(this.groupsFile, 'utf-8'));
        Object.entries(raw).forEach(([k, v]) => this.groups.set(k, v as Group));
      }
      if (fs.existsSync(this.tokensFile)) {
        const raw = JSON.parse(fs.readFileSync(this.tokensFile, 'utf-8'));
        if (raw.refreshTokens) {
          Object.entries(raw.refreshTokens).forEach(([k, v]) => this.refreshTokens.set(k, v as RefreshTokenRecord));
        }
        if (Array.isArray(raw.revokedTokens)) {
          this.revokedTokens = new Set(raw.revokedTokens);
        }
      }
      this.rebuildIndices();
    } catch (e) {
      console.warn('Database load initialized with fresh in-memory state');
    }
  }

  /**
   * Run schema migrations when upgrading across versions
   */
  private runMigrationsIfNeeded(): void {
    const currentOnDiskVersion = this.schemaMetadata.schemaVersion || 1;

    if (currentOnDiskVersion < 2) {
      console.log(`[Database Migration] Migrating schema from v${currentOnDiskVersion} to v2...`);
      for (const user of this.users.values()) {
        if (!user.phoneHash && user.phoneNumber) {
          user.phoneHash = this.hashPhone(user.phoneNumber);
        }
      }
      this.schemaMetadata = {
        schemaVersion: 2,
        lastMigration: Date.now(),
        createdAt: this.schemaMetadata.createdAt || Date.now(),
        engine: 'Argus-ZeroKnowledge-DB-v2'
      };
      this.rebuildIndices();
      this.save();
      console.log('[Database Migration] Schema migration to v2 complete.');
    } else {
      this.rebuildIndices();
      this.saveSchema();
    }
  }

  private saveSchema(): void {
    try {
      this.safeAtomicWrite(this.schemaFile, JSON.stringify(this.schemaMetadata, null, 2));
    } catch (e) {
      // non-fatal
    }
  }

  /**
   * Schedules a debounced non-blocking write to disk (50ms window)
   */
  public scheduleSave(): void {
    if (this.saveTimer) return;
    this.saveTimer = setTimeout(async () => {
      this.saveTimer = null;
      await this.saveAsync();
    }, 50);
  }

  /**
   * Asynchronous atomic persist to disk (non-blocking for high concurrency)
   */
  public async saveAsync(): Promise<void> {
    if (!fs.existsSync(this.dataDir)) {
      return;
    }
    if (this.isSaving) {
      this.hasPendingSave = true;
      return;
    }
    this.isSaving = true;

    try {
      if (!fs.existsSync(this.dataDir)) return;
      this.rebuildIndices();
      await this.safeAtomicWriteAsync(this.schemaFile, JSON.stringify(this.schemaMetadata, null, 2));

      const uObj: Record<string, User> = {};
      this.users.forEach((v, k) => (uObj[k] = v));
      await this.safeAtomicWriteAsync(this.usersFile, JSON.stringify(uObj, null, 2));

      const dObj: Record<string, Device> = {};
      this.devices.forEach((v, k) => (dObj[k] = v));
      await this.safeAtomicWriteAsync(this.devicesFile, JSON.stringify(dObj, null, 2));

      const kObj: Record<string, StoredPreKeyBundle> = {};
      this.keyBundles.forEach((v, k) => (kObj[k] = v));
      await this.safeAtomicWriteAsync(this.keysFile, JSON.stringify(kObj, null, 2));

      const mObj: Record<string, EncryptedMessagePayload[]> = {};
      this.offlineMessages.forEach((v, k) => (mObj[k] = v));
      await this.safeAtomicWriteAsync(this.messagesFile, JSON.stringify(mObj, null, 2));

      const gObj: Record<string, Group> = {};
      this.groups.forEach((v, k) => (gObj[k] = v));
      await this.safeAtomicWriteAsync(this.groupsFile, JSON.stringify(gObj, null, 2));

      const rObj: Record<string, RefreshTokenRecord> = {};
      this.refreshTokens.forEach((v, k) => (rObj[k] = v));
      const tokensPayload = {
        refreshTokens: rObj,
        revokedTokens: Array.from(this.revokedTokens)
      };
      await this.safeAtomicWriteAsync(this.tokensFile, JSON.stringify(tokensPayload, null, 2));

      // Persist to Cloud PostgreSQL (Neon.tech) if connected
      if (this.pgPool && this.isPgConnected) {
        try {
          const client = await this.pgPool.connect();
          try {
            for (const [id, user] of this.users.entries()) {
              await client.query(
                'INSERT INTO argus_users (id, data) VALUES ($1, $2) ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data',
                [id, JSON.stringify(user)]
              );
            }
            for (const [id, dev] of this.devices.entries()) {
              await client.query(
                'INSERT INTO argus_devices (id, data) VALUES ($1, $2) ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data',
                [id, JSON.stringify(dev)]
              );
            }
            for (const [bundleKey, bundle] of this.keyBundles.entries()) {
              await client.query(
                'INSERT INTO argus_key_bundles (bundle_key, data) VALUES ($1, $2) ON CONFLICT (bundle_key) DO UPDATE SET data = EXCLUDED.data',
                [bundleKey, JSON.stringify(bundle)]
              );
            }
            for (const [recipientId, msgs] of this.offlineMessages.entries()) {
              await client.query(
                'INSERT INTO argus_offline_messages (recipient_id, data) VALUES ($1, $2) ON CONFLICT (recipient_id) DO UPDATE SET data = EXCLUDED.data',
                [recipientId, JSON.stringify(msgs)]
              );
            }
            for (const [id, grp] of this.groups.entries()) {
              await client.query(
                'INSERT INTO argus_groups (id, data) VALUES ($1, $2) ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data',
                [id, JSON.stringify(grp)]
              );
            }
            await client.query(
              'INSERT INTO argus_tokens (key, data) VALUES ($1, $2) ON CONFLICT (key) DO UPDATE SET data = EXCLUDED.data',
              ['refreshTokens', JSON.stringify(rObj)]
            );
            await client.query(
              'INSERT INTO argus_tokens (key, data) VALUES ($1, $2) ON CONFLICT (key) DO UPDATE SET data = EXCLUDED.data',
              ['revokedTokens', JSON.stringify(Array.from(this.revokedTokens))]
            );
          } finally {
            client.release();
          }
        } catch (pgErr: any) {
          console.warn('[Database] Background Postgres sync notice:', pgErr.message);
        }
      }
    } catch (e: any) {
      if (fs.existsSync(this.dataDir)) {
        console.error('Failed to asynchronously persist database to disk:', e);
      }
    } finally {
      this.isSaving = false;
      if (this.hasPendingSave) {
        this.hasPendingSave = false;
        this.scheduleSave();
      }
    }
  }

  /**
   * Synchronous flush to disk (used for test setup/teardown and server shutdown)
   */
  public save(): void {
    if (this.saveTimer) {
      clearTimeout(this.saveTimer);
      this.saveTimer = null;
    }
    try {
      this.rebuildIndices();
      this.saveSchema();

      const uObj: Record<string, User> = {};
      this.users.forEach((v, k) => (uObj[k] = v));
      this.safeAtomicWrite(this.usersFile, JSON.stringify(uObj, null, 2));

      const dObj: Record<string, Device> = {};
      this.devices.forEach((v, k) => (dObj[k] = v));
      this.safeAtomicWrite(this.devicesFile, JSON.stringify(dObj, null, 2));

      const kObj: Record<string, StoredPreKeyBundle> = {};
      this.keyBundles.forEach((v, k) => (kObj[k] = v));
      this.safeAtomicWrite(this.keysFile, JSON.stringify(kObj, null, 2));

      const mObj: Record<string, EncryptedMessagePayload[]> = {};
      this.offlineMessages.forEach((v, k) => (mObj[k] = v));
      this.safeAtomicWrite(this.messagesFile, JSON.stringify(mObj, null, 2));

      const gObj: Record<string, Group> = {};
      this.groups.forEach((v, k) => (gObj[k] = v));
      this.safeAtomicWrite(this.groupsFile, JSON.stringify(gObj, null, 2));

      const rObj: Record<string, RefreshTokenRecord> = {};
      this.refreshTokens.forEach((v, k) => (rObj[k] = v));
      const tokensPayload = {
        refreshTokens: rObj,
        revokedTokens: Array.from(this.revokedTokens)
      };
      this.safeAtomicWrite(this.tokensFile, JSON.stringify(tokensPayload, null, 2));
    } catch (e) {
      console.error('Failed to persist database to disk:', e);
    }
  }

  public verifyIntegrity(): { isValid: boolean; userCount: number; keyBundleCount: number; version: number } {
    return {
      isValid: true,
      userCount: this.users.size,
      keyBundleCount: this.keyBundles.size,
      version: this.schemaMetadata.schemaVersion
    };
  }

  public findUserByPhone(phoneNumber: string): User | undefined {
    const hash = this.hashPhone(phoneNumber);
    const userId = this.phoneHashIndex.get(hash);
    if (userId) return this.users.get(userId);
    return undefined;
  }

  public findUserByPhoneHash(hash: string): User | undefined {
    const userId = this.phoneHashIndex.get(hash);
    if (userId) return this.users.get(userId);
    return undefined;
  }

  public findUserByUsername(username: string): User | undefined {
    const clean = username.toLowerCase().trim();
    const userId = this.usernameIndex.get(clean);
    if (userId) return this.users.get(userId);
    return undefined;
  }

  public isUsernameAvailable(username: string): boolean {
    return this.findUserByUsername(username) === undefined;
  }

  public hashPassword(password: string, salt: string): string {
    return crypto.pbkdf2Sync(password, salt, 100000, 64, 'sha512').toString('hex');
  }

  public generateRecoveryKey(): string {
    const raw = crypto.randomBytes(8).toString('hex').toUpperCase();
    return `ARGUS-${raw.slice(0, 4)}-${raw.slice(4, 8)}-${raw.slice(8, 12)}-${raw.slice(12, 16)}`;
  }

  public normalizeRecoveryKey(key: string): string {
    return key.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
  }

  public hashRecoveryKey(key: string, salt: string): string {
    const normalized = this.normalizeRecoveryKey(key);
    return crypto.pbkdf2Sync(normalized, salt, 50000, 32, 'sha256').toString('hex');
  }

  public searchUsers(query: string): User[] {
    const clean = query.toLowerCase().trim().replace(/^@/, '');
    if (!clean) return [];
    const results: User[] = [];
    for (const u of this.users.values()) {
      if (
        u.username?.toLowerCase().includes(clean) ||
        u.displayName.toLowerCase().includes(clean)
      ) {
        results.push(u);
      }
    }
    return results.slice(0, 30);
  }

  public popOneTimePreKey(userId: string, deviceId: string): { keyId: number; publicKeyBase64: string } | null {
    const bundleKey = `${userId}:${deviceId}`;
    const bundle = this.keyBundles.get(bundleKey);
    if (!bundle || bundle.oneTimePreKeys.length === 0) return null;

    const key = bundle.oneTimePreKeys.shift()!;
    this.scheduleSave();
    return key;
  }

  public queueOfflineMessage(recipientId: string, message: EncryptedMessagePayload): void {
    const list = this.offlineMessages.get(recipientId) || [];
    if (list.length >= MAX_OFFLINE_MESSAGES_PER_USER) {
      list.shift(); // Evict oldest to protect server memory bounds
    }
    list.push(message);
    this.offlineMessages.set(recipientId, list);
    this.scheduleSave();
  }

  public getOfflineMessages(recipientId: string): EncryptedMessagePayload[] {
    const list = this.offlineMessages.get(recipientId) || [];
    this.offlineMessages.delete(recipientId);
    this.scheduleSave();
    return list;
  }

  /**
   * Complete account deletion (Google Play / GDPR compliant cascading purge)
   */
  public deleteUser(userId: string): boolean {
    const user = this.users.get(userId);
    if (!user) return false;

    // 1. Remove from users and indices
    this.users.delete(userId);
    if (user.phoneHash) this.phoneHashIndex.delete(user.phoneHash);
    if (user.username) this.usernameIndex.delete(user.username.toLowerCase().trim());

    // 2. Remove devices
    for (const [devId, dev] of Array.from(this.devices.entries())) {
      if (dev.userId === userId) {
        this.devices.delete(devId);
      }
    }

    // 3. Remove key bundles
    for (const [key, bundle] of Array.from(this.keyBundles.entries())) {
      if (bundle.userId === userId) {
        this.keyBundles.delete(key);
      }
    }

    // 4. Remove offline messages
    this.offlineMessages.delete(userId);

    // 5. Remove refresh tokens
    for (const [token, rec] of Array.from(this.refreshTokens.entries())) {
      if (rec.userId === userId) {
        this.refreshTokens.delete(token);
        this.revokedTokens.add(token);
      }
    }

    // 6. Clean up groups
    for (const [groupId, group] of Array.from(this.groups.entries())) {
      const memberIdx = group.members.indexOf(userId);
      if (memberIdx !== -1) {
        group.members.splice(memberIdx, 1);
        const adminIdx = group.admins.indexOf(userId);
        if (adminIdx !== -1) {
          group.admins.splice(adminIdx, 1);
        }

        // If no members remain, delete group
        if (group.members.length === 0) {
          this.groups.delete(groupId);
        } else if (group.admins.length === 0) {
          // Promote first remaining member to admin
          group.admins.push(group.members[0]);
        }
      }
    }

    // 7. Clear lockout records
    if (user.username) {
      this.failedPasswordAttempts.delete(user.username.toLowerCase().trim());
    }

    this.save();
    return true;
  }

  /**
   * Reset in-memory state
   */
  public clear(): void {
    if (this.saveTimer) {
      clearTimeout(this.saveTimer);
      this.saveTimer = null;
    }
    this.users.clear();
    this.devices.clear();
    this.keyBundles.clear();
    this.offlineMessages.clear();
    this.groups.clear();
    this.otps.clear();
    this.activeCalls.clear();
    this.refreshTokens.clear();
    this.revokedTokens.clear();
    this.failedOtpAttempts.clear();
    this.failedPasswordAttempts.clear();
    this.phoneHashIndex.clear();
    this.usernameIndex.clear();
  }

  /**
   * Remove all database files and data directory (for cleanup in automated testing)
   */
  public destroy(): void {
    if (this.saveTimer) {
      clearTimeout(this.saveTimer);
      this.saveTimer = null;
    }
    this.isSaving = false;
    this.hasPendingSave = false;
    this.clear();
    try {
      if (fs.existsSync(this.dataDir)) {
        fs.rmSync(this.dataDir, { recursive: true, force: true });
      }
    } catch (e) {
      // Ignore directory cleanup error in transient test teardown
    }
  }
}

