import { Router, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import crypto from 'crypto';
import { z } from 'zod';
import { ArgusDatabase } from '../db/database';
import { User, Device } from '../types';

export const usernameRegex = /^[a-zA-Z0-9._]{3,30}$/;
const MAX_DEVICES_PER_USER = 5;

const registerSchema = z.object({
  username: z.string().trim().min(3).max(30).regex(usernameRegex, {
    message: 'Username must be 3-30 characters (letters, numbers, dots, and underscores only)'
  }),
  password: z.string().min(6, { message: 'Password must be at least 6 characters long' }).max(128),
  displayName: z.string().trim().min(1, { message: 'Display name is required' }).max(60),
  identityKeyBase64: z.string().trim().min(20).max(128).regex(/^[A-Za-z0-9+/=_-]+$/, {
    message: 'Identity key must be valid Base64'
  }),
  deviceName: z.string().trim().max(100).optional(),
  platform: z.string().trim().max(50).optional()
});

const loginSchema = z.object({
  username: z.string().trim().min(3).max(30),
  password: z.string().min(1, { message: 'Password is required' }),
  identityKeyBase64: z.string().trim().min(20).max(128).regex(/^[A-Za-z0-9+/=_-]+$/).optional(),
  deviceName: z.string().trim().max(100).optional(),
  platform: z.string().trim().max(50).optional()
});

const resetPasswordSchema = z.object({
  username: z.string().trim().min(3).max(30),
  newPassword: z.string().min(6, { message: 'New password must be at least 6 characters long' }).max(128),
  recoveryKey: z.string({ message: 'Emergency recovery key is required' }).trim().min(8, { message: 'Emergency recovery key is required' }),
  identityKeyBase64: z.string().trim().min(20).max(128).regex(/^[A-Za-z0-9+/=_-]+$/).optional(),
  deviceName: z.string().trim().max(100).optional(),
  platform: z.string().trim().max(50).optional()
});

/**
 * Helper to manage user device sessions without unbounded memory proliferation (BUG-3 fixed)
 */
function getOrCreateUserDevice(db: ArgusDatabase, userId: string, deviceName?: string, platform?: string): Device {
  const dName = (deviceName || 'Android Device').trim();
  const pForm = (platform || 'android').trim();
  const now = Date.now();

  // Reuse existing active device if names and platform match
  for (const dev of db.devices.values()) {
    if (dev.userId === userId && dev.deviceName === dName && dev.platform === pForm) {
      dev.lastActive = now;
      return dev;
    }
  }

  // Bound device count per user
  const userDevices: Device[] = [];
  for (const dev of db.devices.values()) {
    if (dev.userId === userId) {
      userDevices.push(dev);
    }
  }

  if (userDevices.length >= MAX_DEVICES_PER_USER) {
    userDevices.sort((a, b) => a.lastActive - b.lastActive);
    const toRemove = userDevices.slice(0, userDevices.length - (MAX_DEVICES_PER_USER - 1));
    for (const oldDev of toRemove) {
      db.devices.delete(oldDev.id);
      db.keyBundles.delete(`${userId}:${oldDev.id}`);
    }
  }

  const deviceId = uuidv4();
  const newDevice: Device = {
    id: deviceId,
    userId,
    deviceName: dName,
    platform: pForm,
    createdAt: now,
    lastActive: now
  };
  db.devices.set(deviceId, newDevice);
  return newDevice;
}

export function createAuthRouter(
  db: ArgusDatabase,
  jwtSecret: string,
  options?: { sensitiveLimiter?: any; checkUsernameLimiter?: any }
): Router {
  const router = Router();
  const sensitiveLimiter = options?.sensitiveLimiter || ((_req: any, _res: any, next: any) => next());
  const checkUsernameLimiter = options?.checkUsernameLimiter || ((_req: any, _res: any, next: any) => next());

  /**
   * Check if a username is available in real-time (Lightweight limiter)
   */
  router.get('/check-username/:username', checkUsernameLimiter, (req: Request, res: Response): void => {
    const raw = req.params.username;
    const username = (Array.isArray(raw) ? raw[0] : raw || '').trim();
    if (!username || !usernameRegex.test(username)) {
      res.status(400).json({ error: 'Invalid username format', available: false });
      return;
    }
    const available = db.isUsernameAvailable(username);
    res.json({ username: username.toLowerCase(), available });
  });

  /**
   * Register a new user with Username, Password, and Display Name
   */
  router.post('/register', sensitiveLimiter, (req: Request, res: Response): void => {
    const parseResult = registerSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({ success: false, error: parseResult.error.issues[0].message });
      return;
    }

    const { username, password, displayName, identityKeyBase64, deviceName, platform } = parseResult.data;
    const cleanUsername = username.toLowerCase().trim();

    if (!db.isUsernameAvailable(cleanUsername)) {
      res.status(400).json({ success: false, error: `Username '@${cleanUsername}' is already taken. Please choose another.` });
      return;
    }

    const salt = crypto.randomBytes(16).toString('hex');
    const passwordHash = db.hashPassword(password, salt);
    
    // Generate Master Emergency Recovery Key
    const recoveryKey = db.generateRecoveryKey();
    const recoveryKeySalt = crypto.randomBytes(16).toString('hex');
    const recoveryKeyHash = db.hashRecoveryKey(recoveryKey, recoveryKeySalt);

    const now = Date.now();
    const userId = uuidv4();

    const user: User = {
      id: userId,
      username: cleanUsername,
      displayName: displayName.trim(),
      passwordHash,
      salt,
      recoveryKeyHash,
      recoveryKeySalt,
      identityKeyBase64,
      createdAt: now,
      lastSeen: now,
      isOnline: true
    };
    db.users.set(user.id, user);

    const device = getOrCreateUserDevice(db, user.id, deviceName, platform);

    const token = jwt.sign(
      { userId: user.id, username: user.username, deviceId: device.id },
      jwtSecret,
      { expiresIn: '7d' }
    );

    const refreshToken = uuidv4() + '.' + crypto.randomBytes(32).toString('hex');
    db.refreshTokens.set(refreshToken, {
      token: refreshToken,
      userId: user.id,
      deviceId: device.id,
      expiresAt: now + 90 * 24 * 60 * 60 * 1000
    });

    db.scheduleSave();

    console.log(`[Auth Service] Registered new user @${user.username} (${user.displayName})`);

    res.status(201).json({
      success: true,
      token,
      refreshToken,
      recoveryKey,
      user: {
        id: user.id,
        username: user.username,
        displayName: user.displayName,
        avatarUrl: user.avatarUrl,
        about: user.about,
        identityKeyBase64: user.identityKeyBase64
      },
      device: {
        id: device.id,
        deviceName: device.deviceName
      }
    });
  });

  /**
   * Log in with Username and Password
   */
  router.post('/login', sensitiveLimiter, (req: Request, res: Response): void => {
    const parseResult = loginSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({ success: false, error: parseResult.error.issues[0].message });
      return;
    }

    const { username, password, identityKeyBase64, deviceName, platform } = parseResult.data;
    const cleanUsername = username.toLowerCase().trim();

    // Check brute-force lockout
    const attemptRecord = db.failedPasswordAttempts.get(cleanUsername);
    if (attemptRecord && Date.now() < attemptRecord.lockedUntil) {
      const waitMinutes = Math.ceil((attemptRecord.lockedUntil - Date.now()) / 60000);
      res.status(429).json({
        success: false,
        error: `Account locked due to too many failed password attempts. Try again in ${waitMinutes} minute(s).`
      });
      return;
    }

    const user = db.findUserByUsername(cleanUsername);
    if (!user || !user.passwordHash || !user.salt) {
      res.status(401).json({ success: false, error: 'Invalid username or password' });
      return;
    }

    const computedHash = db.hashPassword(password, user.salt);
    const expectedBuffer = Buffer.from(user.passwordHash, 'hex');
    const actualBuffer = Buffer.from(computedHash, 'hex');

    const isMatch = expectedBuffer.length === actualBuffer.length &&
      crypto.timingSafeEqual(expectedBuffer, actualBuffer);

    if (!isMatch) {
      const currentFailures = (attemptRecord?.count || 0) + 1;
      if (currentFailures >= 5) {
        db.failedPasswordAttempts.set(cleanUsername, {
          count: currentFailures,
          lockedUntil: Date.now() + 15 * 60 * 1000 // 15-minute lock
        });
        res.status(429).json({ success: false, error: 'Too many incorrect attempts. Account locked for 15 minutes.' });
      } else {
        db.failedPasswordAttempts.set(cleanUsername, {
          count: currentFailures,
          lockedUntil: 0
        });
        res.status(401).json({
          success: false,
          error: `Invalid username or password. (${5 - currentFailures} attempt(s) remaining)`
        });
      }
      return;
    }

    // Successful login - clear failed attempts
    db.failedPasswordAttempts.delete(cleanUsername);

    if (identityKeyBase64) {
      user.identityKeyBase64 = identityKeyBase64;
    }
    const now = Date.now();
    user.lastSeen = now;
    user.isOnline = true;

    const device = getOrCreateUserDevice(db, user.id, deviceName, platform);

    const token = jwt.sign(
      { userId: user.id, username: user.username, deviceId: device.id },
      jwtSecret,
      { expiresIn: '7d' }
    );

    const refreshToken = uuidv4() + '.' + crypto.randomBytes(32).toString('hex');
    db.refreshTokens.set(refreshToken, {
      token: refreshToken,
      userId: user.id,
      deviceId: device.id,
      expiresAt: now + 90 * 24 * 60 * 60 * 1000
    });

    db.scheduleSave();

    console.log(`[Auth Service] User @${user.username} logged in successfully`);

    res.json({
      success: true,
      token,
      refreshToken,
      user: {
        id: user.id,
        username: user.username,
        displayName: user.displayName,
        avatarUrl: user.avatarUrl,
        about: user.about,
        identityKeyBase64: user.identityKeyBase64
      },
      device: {
        id: device.id,
        deviceName: device.deviceName
      }
    });
  });

  /**
   * Verify Recovery Key before Resetting Password
   */
  router.post('/verify-recovery-key', sensitiveLimiter, (req: Request, res: Response): void => {
    const { username, recoveryKey } = req.body;
    if (!username || !recoveryKey) {
      res.status(400).json({ valid: false, error: 'Username and Recovery Key are required' });
      return;
    }

    const cleanUsername = String(username).toLowerCase().trim();
    const user = db.findUserByUsername(cleanUsername);
    if (!user) {
      res.status(404).json({ valid: false, error: `User '@${cleanUsername}' does not exist` });
      return;
    }

    // Strict recovery key verification (BUG-4 fixed: no bypass if recovery key hash is missing)
    if (!user.recoveryKeyHash || !user.recoveryKeySalt) {
      res.status(403).json({ valid: false, error: 'Emergency recovery is not configured for this account' });
      return;
    }

    const computedHash = db.hashRecoveryKey(recoveryKey, user.recoveryKeySalt);
    const expectedBuffer = Buffer.from(user.recoveryKeyHash, 'hex');
    const actualBuffer = Buffer.from(computedHash, 'hex');

    const isValid = expectedBuffer.length === actualBuffer.length &&
      crypto.timingSafeEqual(expectedBuffer, actualBuffer);

    if (!isValid) {
      res.status(400).json({ valid: false, error: 'Invalid recovery key. Please check your 16-character code.' });
      return;
    }

    res.json({ valid: true, message: 'Recovery key verified successfully' });
  });

  /**
   * Reset Password with Username and Required Recovery Key
   */
  router.post('/reset-password', sensitiveLimiter, (req: Request, res: Response): void => {
    const parseResult = resetPasswordSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({ success: false, error: parseResult.error.issues[0].message });
      return;
    }

    const { username, newPassword, recoveryKey, identityKeyBase64, deviceName, platform } = parseResult.data;
    const cleanUsername = username.toLowerCase().trim();

    const user = db.findUserByUsername(cleanUsername);
    if (!user) {
      res.status(404).json({ success: false, error: `Account '@${cleanUsername}' not found` });
      return;
    }

    // Strict recovery key verification (BUG-4 fixed: mandatory check)
    if (!user.recoveryKeyHash || !user.recoveryKeySalt) {
      res.status(403).json({ success: false, error: 'Emergency recovery is not configured for this account. Cannot reset password.' });
      return;
    }

    const computedHash = db.hashRecoveryKey(recoveryKey, user.recoveryKeySalt);
    const expectedBuffer = Buffer.from(user.recoveryKeyHash, 'hex');
    const actualBuffer = Buffer.from(computedHash, 'hex');

    const isKeyMatch = expectedBuffer.length === actualBuffer.length &&
      crypto.timingSafeEqual(expectedBuffer, actualBuffer);

    if (!isKeyMatch) {
      res.status(400).json({ success: false, error: 'Invalid recovery key. Please enter the valid emergency key.' });
      return;
    }

    // Generate new salt and hash for new password
    const salt = crypto.randomBytes(16).toString('hex');
    const passwordHash = db.hashPassword(newPassword, salt);

    // Issue a fresh Master Emergency Recovery Key
    const newRecoveryKey = db.generateRecoveryKey();
    const newRecoveryKeySalt = crypto.randomBytes(16).toString('hex');
    const newRecoveryKeyHash = db.hashRecoveryKey(newRecoveryKey, newRecoveryKeySalt);

    user.salt = salt;
    user.passwordHash = passwordHash;
    user.recoveryKeyHash = newRecoveryKeyHash;
    user.recoveryKeySalt = newRecoveryKeySalt;
    if (identityKeyBase64) {
      user.identityKeyBase64 = identityKeyBase64;
    }
    const now = Date.now();
    user.lastSeen = now;
    user.isOnline = true;

    // Reset failed brute-force lockout attempts
    db.failedPasswordAttempts.delete(cleanUsername);

    const device = getOrCreateUserDevice(db, user.id, deviceName, platform);

    const token = jwt.sign(
      { userId: user.id, username: user.username, deviceId: device.id },
      jwtSecret,
      { expiresIn: '7d' }
    );

    const refreshToken = uuidv4() + '.' + crypto.randomBytes(32).toString('hex');
    db.refreshTokens.set(refreshToken, {
      token: refreshToken,
      userId: user.id,
      deviceId: device.id,
      expiresAt: now + 90 * 24 * 60 * 60 * 1000
    });

    db.scheduleSave();

    console.log(`[Auth Service] Password reset completed for user @${user.username}`);

    res.json({
      success: true,
      message: 'Password reset successfully',
      token,
      refreshToken,
      recoveryKey: newRecoveryKey,
      user: {
        id: user.id,
        username: user.username,
        displayName: user.displayName,
        avatarUrl: user.avatarUrl,
        about: user.about,
        identityKeyBase64: user.identityKeyBase64
      },
      device: {
        id: device.id,
        deviceName: device.deviceName
      }
    });
  });

  /**
   * Refresh expired access token using refresh token (with Refresh Token Rotation)
   */
  router.post('/refresh-token', sensitiveLimiter, (req: Request, res: Response): void => {
    const { refreshToken } = req.body;
    if (!refreshToken || typeof refreshToken !== 'string') {
      res.status(400).json({ error: 'refreshToken is required' });
      return;
    }

    if (db.revokedTokens.has(refreshToken)) {
      res.status(401).json({ error: 'Token has been revoked or already used' });
      return;
    }

    const record = db.refreshTokens.get(refreshToken);
    if (!record || Date.now() > record.expiresAt) {
      db.refreshTokens.delete(refreshToken);
      res.status(401).json({ error: 'Invalid or expired refresh token' });
      return;
    }

    const user = db.users.get(record.userId);
    if (!user) {
      db.refreshTokens.delete(refreshToken);
      db.revokedTokens.set(refreshToken, Date.now());
      res.status(404).json({ error: 'User not found or account deleted' });
      return;
    }

    // Refresh Token Rotation (RTR): Invalidate old refresh token to prevent replay attacks (BUG-2 & BUG-11 fixed)
    db.refreshTokens.delete(refreshToken);
    db.revokedTokens.set(refreshToken, Date.now());

    // Ensure device still exists or reuse record deviceId
    const device = db.devices.get(record.deviceId) || getOrCreateUserDevice(db, user.id);

    // Issue new access token
    const newToken = jwt.sign(
      { userId: user.id, username: user.username, deviceId: device.id },
      jwtSecret,
      { expiresIn: '7d' }
    );

    // Issue new rotated refresh token
    const newRefreshToken = uuidv4() + '.' + crypto.randomBytes(32).toString('hex');
    db.refreshTokens.set(newRefreshToken, {
      token: newRefreshToken,
      userId: user.id,
      deviceId: device.id,
      expiresAt: Date.now() + 90 * 24 * 60 * 60 * 1000
    });

    db.scheduleSave();

    res.json({
      success: true,
      token: newToken,
      refreshToken: newRefreshToken
    });
  });

  /**
   * Logout and revoke tokens
   */
  router.post('/logout', (req: Request, res: Response): void => {
    const { refreshToken } = req.body;
    if (refreshToken && typeof refreshToken === 'string') {
      db.refreshTokens.delete(refreshToken);
      db.revokedTokens.set(refreshToken, Date.now());
      db.scheduleSave();
    }
    res.json({ success: true, message: 'Logged out successfully' });
  });

  return router;
}
