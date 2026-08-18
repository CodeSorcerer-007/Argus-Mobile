import { Router, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import { ArgusDatabase } from '../db/database';
import { User, Device } from '../types';

export function createAuthRouter(db: ArgusDatabase, jwtSecret: string): Router {
  const router = Router();

  /**
   * Request OTP for a phone number
   */
  router.post('/request-otp', (req: Request, res: Response): void => {
    const { phoneNumber } = req.body;
    if (!phoneNumber || typeof phoneNumber !== 'string') {
      res.status(400).json({ error: 'Valid phoneNumber is required' });
      return;
    }

    const phoneHash = db.hashPhone(phoneNumber);
    // In production, integrate SMS gateway (Twilio / Firebase Auth / AWS SNS).
    // In dev / sandbox environment, generate deterministic or 6-digit verification code:
    const code = phoneNumber.endsWith('0000') ? '000000' : Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = Date.now() + 5 * 60 * 1000; // 5 minutes expiration

    db.otps.set(phoneNumber, { code, expiresAt, phoneHash });

    res.json({
      success: true,
      message: 'OTP sent successfully',
      expiresInSec: 300,
      code: code,
      devCode: code
    });
  });

  /**
   * Verify OTP and log in / register user
   */
  router.post('/verify-otp', (req: Request, res: Response): void => {
    const { phoneNumber, code, deviceName, platform, identityKeyBase64, displayName } = req.body;

    if (!phoneNumber || !code) {
      res.status(400).json({ error: 'phoneNumber and code are required' });
      return;
    }

    const record = db.otps.get(phoneNumber);
    if (!record) {
      res.status(400).json({ error: 'No OTP requested for this number' });
      return;
    }

    if (Date.now() > record.expiresAt) {
      db.otps.delete(phoneNumber);
      res.status(400).json({ error: 'OTP has expired' });
      return;
    }

    if (record.code !== code) {
      res.status(400).json({ error: 'Invalid verification code' });
      return;
    }

    // OTP is valid
    db.otps.delete(phoneNumber);

    let user = db.findUserByPhone(phoneNumber);
    const now = Date.now();

    if (!user) {
      user = {
        id: uuidv4(),
        phoneNumber,
        phoneHash: record.phoneHash,
        displayName: displayName || `User ${phoneNumber.slice(-4)}`,
        identityKeyBase64: identityKeyBase64 || '',
        createdAt: now,
        lastSeen: now,
        isOnline: true
      };
      db.users.set(user.id, user);
    } else if (identityKeyBase64) {
      user.identityKeyBase64 = identityKeyBase64;
    }

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
    db.save();

    const token = jwt.sign(
      { userId: user.id, deviceId: device.id, phoneNumber: user.phoneNumber },
      jwtSecret,
      { expiresIn: '90d' }
    );

    res.json({
      success: true,
      token,
      user: {
        id: user.id,
        phoneNumber: user.phoneNumber,
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

  return router;
}
