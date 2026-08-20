import { Router, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import crypto from 'crypto';
import { z } from 'zod';
import { ArgusDatabase } from '../db/database';
import { User, Device } from '../types';

const usernameRegex = /^[a-zA-Z0-9._]{3,30}$/;

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

export function createAuthRouter(db: ArgusDatabase, jwtSecret: string): Router {
  const router = Router();

  /**
   * Check if a username is available in real-time
   */
  router.get('/check-username/:username', (req: Request, res: Response): void => {
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
  router.post('/register', (req: Request, res: Response): void => {
    const parseResult = registerSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({ error: parseResult.error.issues[0].message });
      return;
    }

    const { username, password, displayName, identityKeyBase64, deviceName, platform } = parseResult.data;
    const cleanUsername = username.toLowerCase().trim();

    if (!db.isUsernameAvailable(cleanUsername)) {
      res.status(400).json({ error: `Username '@${cleanUsername}' is already taken. Please choose another.` });
      return;
    }

    const salt = crypto.randomBytes(16).toString('hex');
    const passwordHash = db.hashPassword(password, salt);
    const now = Date.now();
    const userId = uuidv4();

    const user: User = {
      id: userId,
      username: cleanUsername,
      displayName: displayName.trim(),
      passwordHash,
      salt,
      identityKeyBase64,
      createdAt: now,
      lastSeen: now,
      isOnline: true
    };
    db.users.set(user.id, user);

    const deviceId = uuidv4();
    const device: Device = {
      id: deviceId,
      userId: user.id,
      deviceName: deviceName || 'Android Device',
      platform: platform || 'android',
      createdAt: now,
      lastActive: now
    };
    db.devices.set(deviceId, device);

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

    db.save();

    console.log(`[Auth Service] Registered new user @${user.username} (${user.displayName})`);

    res.status(201).json({
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
   * Log in with Username and Password
   */
  router.post('/login', (req: Request, res: Response): void => {
    const parseResult = loginSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({ error: parseResult.error.issues[0].message });
      return;
    }

    const { username, password, identityKeyBase64, deviceName, platform } = parseResult.data;
    const cleanUsername = username.toLowerCase().trim();

    // Check brute-force lockout
    const attemptRecord = db.failedPasswordAttempts.get(cleanUsername);
    if (attemptRecord && Date.now() < attemptRecord.lockedUntil) {
      const waitMinutes = Math.ceil((attemptRecord.lockedUntil - Date.now()) / 60000);
      res.status(429).json({
        error: `Account locked due to too many failed password attempts. Try again in ${waitMinutes} minute(s).`
      });
      return;
    }

    const user = db.findUserByUsername(cleanUsername);
    if (!user || !user.passwordHash || !user.salt) {
      res.status(401).json({ error: 'Invalid username or password' });
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
        res.status(429).json({ error: 'Too many incorrect attempts. Account locked for 15 minutes.' });
      } else {
        db.failedPasswordAttempts.set(cleanUsername, {
          count: currentFailures,
          lockedUntil: 0
        });
        res.status(401).json({
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

    const deviceId = uuidv4();
    const device: Device = {
      id: deviceId,
      userId: user.id,
      deviceName: deviceName || 'Android Device',
      platform: platform || 'android',
      createdAt: now,
      lastActive: now
    };
    db.devices.set(deviceId, device);

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

    db.save();

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
   * Refresh expired access token using refresh token (with Refresh Token Rotation)
   */
  router.post('/refresh-token', (req: Request, res: Response): void => {
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
      res.status(404).json({ error: 'User not found' });
      return;
    }

    // Refresh Token Rotation (RTR): Invalidate old refresh token to prevent replay attacks
    db.refreshTokens.delete(refreshToken);
    db.revokedTokens.add(refreshToken);

    // Issue new access token
    const newToken = jwt.sign(
      { userId: user.id, username: user.username, deviceId: record.deviceId },
      jwtSecret,
      { expiresIn: '7d' }
    );

    // Issue new rotated refresh token
    const newRefreshToken = uuidv4() + '.' + crypto.randomBytes(32).toString('hex');
    db.refreshTokens.set(newRefreshToken, {
      token: newRefreshToken,
      userId: user.id,
      deviceId: record.deviceId,
      expiresAt: Date.now() + 90 * 24 * 60 * 60 * 1000
    });

    db.save();

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
      db.revokedTokens.add(refreshToken);
      db.save();
    }
    res.json({ success: true, message: 'Logged out successfully' });
  });

  return router;
}
