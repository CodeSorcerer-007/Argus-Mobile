import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { User, Device, StoredPreKeyBundle, EncryptedMessagePayload, Group, CallSession } from '../types';

export class ArgusDatabase {
  private dataDir: string;
  private usersFile: string;
  private devicesFile: string;
  private keysFile: string;
  private messagesFile: string;
  private groupsFile: string;
  private otpsFile: string;

  public users: Map<string, User> = new Map();
  public devices: Map<string, Device> = new Map();
  public keyBundles: Map<string, StoredPreKeyBundle> = new Map(); // key: `${userId}:${deviceId}`
  public offlineMessages: Map<string, EncryptedMessagePayload[]> = new Map(); // key: userId
  public groups: Map<string, Group> = new Map();
  public otps: Map<string, { code: string; expiresAt: number; phoneHash: string }> = new Map();
  public activeCalls: Map<string, CallSession> = new Map();

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
    this.otpsFile = path.join(this.dataDir, 'otps.json');

    this.load();
  }

  public hashPhone(phoneNumber: string): string {
    return crypto.createHash('sha256').update(`Argus_Salt_2026:${phoneNumber.trim()}`).digest('hex');
  }

  public load(): void {
    try {
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
    } catch (e) {
      console.warn('Database load initialized with fresh in-memory state');
    }
  }

  public save(): void {
    try {
      const uObj: Record<string, User> = {};
      this.users.forEach((v, k) => (uObj[k] = v));
      fs.writeFileSync(this.usersFile, JSON.stringify(uObj, null, 2));

      const dObj: Record<string, Device> = {};
      this.devices.forEach((v, k) => (dObj[k] = v));
      fs.writeFileSync(this.devicesFile, JSON.stringify(dObj, null, 2));

      const kObj: Record<string, StoredPreKeyBundle> = {};
      this.keyBundles.forEach((v, k) => (kObj[k] = v));
      fs.writeFileSync(this.keysFile, JSON.stringify(kObj, null, 2));

      const mObj: Record<string, EncryptedMessagePayload[]> = {};
      this.offlineMessages.forEach((v, k) => (mObj[k] = v));
      fs.writeFileSync(this.messagesFile, JSON.stringify(mObj, null, 2));

      const gObj: Record<string, Group> = {};
      this.groups.forEach((v, k) => (gObj[k] = v));
      fs.writeFileSync(this.groupsFile, JSON.stringify(gObj, null, 2));
    } catch (e) {
      console.error('Failed to persist database to disk:', e);
    }
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
}
