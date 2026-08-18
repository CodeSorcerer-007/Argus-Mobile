import { Router, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import crypto from 'crypto';
import { z } from 'zod';
import twilio from 'twilio';
import { ArgusDatabase } from '../db/database';
import { User, Device } from '../types';

const phoneSchema = z.string().trim().regex(/^\+[1-9]\d{6,14}$/, {
  message: 'Phone number must be in valid international E.164 format (e.g. +15551234567)'
});

const verifySchema = z.object({
  phoneNumber: z.string().trim().regex(/^\+[1-9]\d{6,14}$/),
  code: z.string().trim().regex(/^\d{6}$/, { message: 'Code must be a 6-digit numeric string' }),
  deviceName: z.string().trim().max(100).optional(),
  platform: z.string().trim().max(50).optional(),
  identityKeyBase64: z.string().trim().min(10),
  displayName: z.string().trim().max(60).optional()
});

export function createAuthRouter(db: ArgusDatabase, jwtSecret: string): Router {
  const router = Router();

  const twilioClient = (process.env.TWILIO_ACCOUNT_SID && process.env.TWILIO_AUTH_TOKEN)
    ? twilio(process.env.TWILIO_ACCOUNT_SID, process.env.TWILIO_AUTH_TOKEN)
    : null;

  /**
   * Request OTP for a phone number
   */
  router.post('/request-otp', async (req: Request, res: Response): Promise<void> => {
    const parseResult = phoneSchema.safeParse(req.body.phoneNumber);
    if (!parseResult.success) {
      res.status(400).json({ error: parseResult.error.issues[0].message });
      return;
    }

    const phoneNumber = parseResult.data;
    const phoneHash = db.hashPhone(phoneNumber);

    // Check brute-force lockout
    const attemptRecord = db.failedOtpAttempts.get(phoneNumber);
    if (attemptRecord && Date.now() < attemptRecord.lockedUntil) {
      const waitMinutes = Math.ceil((attemptRecord.lockedUntil - Date.now()) / 60000);
      res.status(429).json({ error: `Too many failed attempts. Please try again in ${waitMinutes} minute(s).` });
      return;
    }

    const isSandboxTestNumber = phoneNumber.endsWith('0000');
    const code = isSandboxTestNumber ? '000000' : Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = Date.now() + 5 * 60 * 1000; // 5 minutes expiration

    db.otps.set(phoneNumber, { code, expiresAt, phoneHash });

    // Send real SMS if Twilio credentials are configured
    let smsDispatched = false;
    if (twilioClient && process.env.TWILIO_PHONE_NUMBER && !isSandboxTestNumber) {
      try {
        await twilioClient.messages.create({
          body: `Your Argus verification code is: ${code}. Valid for 5 minutes. Do not share this code.`,
          from: process.env.TWILIO_PHONE_NUMBER,
          to: phoneNumber
        });
        smsDispatched = true;
      } catch (smsError: any) {
        console.error('Twilio SMS dispatch failed:', smsError.message);
      }
    }

    const isProductionWithSms = process.env.NODE_ENV === 'production' && smsDispatched;

    res.json({
      success: true,
      message: smsDispatched ? 'Verification code sent via SMS' : 'OTP generated successfully',
      expiresInSec: 300,
      code: isProductionWithSms ? undefined : code,
      devCode: isProductionWithSms ? undefined : code
    });
  });

  /**
   * Verify OTP and log in / register user
   */
  router.post('/verify-otp', (req: Request, res: Response): void => {
    const parseResult = verifySchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({ error: parseResult.error.issues[0].message });
      return;
    }

    const { phoneNumber, code, deviceName, platform, identityKeyBase64, displayName } = parseResult.data;

    // Check brute-force lockout
    const attemptRecord = db.failedOtpAttempts.get(phoneNumber);
    if (attemptRecord && Date.now() < attemptRecord.lockedUntil) {
      const waitMinutes = Math.ceil((attemptRecord.lockedUntil - Date.now()) / 60000);
      res.status(429).json({ error: `Account locked due to too many failed attempts. Try again in ${waitMinutes} minute(s).` });
      return;
    }

    const record = db.otps.get(phoneNumber);
    if (!record) {
      res.status(400).json({ error: 'No active OTP requested for this phone number' });
      return;
    }

    if (Date.now() > record.expiresAt) {
      db.otps.delete(phoneNumber);
      res.status(400).json({ error: 'Verification code has expired. Please request a new one.' });
      return;
    }

    if (record.code !== code) {
      const currentFailures = (attemptRecord?.count || 0) + 1;
      if (currentFailures >= 5) {
        db.failedOtpAttempts.set(phoneNumber, {
          count: currentFailures,
          lockedUntil: Date.now() + 15 * 60 * 1000 // 15-minute lock
        });
        res.status(429).json({ error: 'Too many incorrect attempts. Account locked for 15 minutes.' });
      } else {
        db.failedOtpAttempts.set(phoneNumber, {
          count: currentFailures,
          lockedUntil: 0
        });
        res.status(400).json({ error: `Invalid verification code. (${5 - currentFailures} attempts remaining)` });
      }
      return;
    }

    // Successful OTP verification
    db.otps.delete(phoneNumber);
    db.failedOtpAttempts.delete(phoneNumber);

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

    const token = jwt.sign(
      { userId: user.id, deviceId: device.id, phoneNumber: user.phoneNumber },
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

    res.json({
      success: true,
      token,
      refreshToken,
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

  /**
   * Refresh expired access token using refresh token
   */
  router.post('/refresh-token', (req: Request, res: Response): void => {
    const { refreshToken } = req.body;
    if (!refreshToken || typeof refreshToken !== 'string') {
      res.status(400).json({ error: 'refreshToken is required' });
      return;
    }

    if (db.revokedTokens.has(refreshToken)) {
      res.status(401).json({ error: 'Token has been revoked' });
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

    const newToken = jwt.sign(
      { userId: user.id, deviceId: record.deviceId, phoneNumber: user.phoneNumber },
      jwtSecret,
      { expiresIn: '7d' }
    );

    res.json({
      success: true,
      token: newToken
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
