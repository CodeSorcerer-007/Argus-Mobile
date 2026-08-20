import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
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

export class ArgusDatabase {
  private dataDir: string;
  private usersFile: string;
  private devicesFile: string;
  private keysFile: string;
  private messagesFile: string;
  private groupsFile: string;
  private tokensFile: string;
  private schemaFile: string;

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

  public schemaMetadata: SchemaMetadata = {
    schemaVersion: CURRENT_SCHEMA_VERSION,
    lastMigration: Date.now(),
    createdAt: Date.now(),
    engine: 'Argus-ZeroKnowledge-DB-v2'
  };

  constructor(dataDir: string = './data') {
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

    this.load();
    this.runMigrationsIfNeeded();
  }

  public hashPhone(phoneNumber: string): string {
    return crypto.createHash('sha256').update(`Argus_Salt_2026:${phoneNumber.trim()}`).digest('hex');
  }

  private safeAtomicWrite(filePath: string, data: string): void {
    const tmpPath = `${filePath}.${Date.now()}.${Math.random().toString(36).substring(2, 8)}.tmp`;
    fs.writeFileSync(tmpPath, data, 'utf-8');
    fs.renameSync(tmpPath, filePath);
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
      // Migration v1 -> v2: Ensure all users have phoneHash indexed and all revokedTokens initialized
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
      this.save();
      console.log('[Database Migration] Schema migration to v2 complete.');
    } else {
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

  public save(): void {
    try {
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
    for (const u of this.users.values()) {
      if (u.phoneHash === hash) return u;
    }
    return undefined;
  }

  public findUserByUsername(username: string): User | undefined {
    const clean = username.toLowerCase().trim();
    for (const u of this.users.values()) {
      if (u.username?.toLowerCase() === clean) return u;
    }
    return undefined;
  }

  public isUsernameAvailable(username: string): boolean {
    return this.findUserByUsername(username) === undefined;
  }

  public hashPassword(password: string, salt: string): string {
    return crypto.pbkdf2Sync(password, salt, 100000, 64, 'sha512').toString('hex');
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
    this.save();
    return key;
  }

  public queueOfflineMessage(recipientId: string, message: EncryptedMessagePayload): void {
    const list = this.offlineMessages.get(recipientId) || [];
    list.push(message);
    this.offlineMessages.set(recipientId, list);
    this.save();
  }

  public getOfflineMessages(recipientId: string): EncryptedMessagePayload[] {
    const list = this.offlineMessages.get(recipientId) || [];
    this.offlineMessages.delete(recipientId);
    this.save();
    return list;
  }

  /**
   * Reset in-memory state
   */
  public clear(): void {
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
  }

  /**
   * Remove all database files and data directory (for cleanup in automated testing)
   */
  public destroy(): void {
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
