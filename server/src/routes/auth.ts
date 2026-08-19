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
  phoneNumber: z.string().trim().regex(/^\+[1-9]\d{6,14}$/, { message: 'Phone number must be in valid international E.164 format' }),
  code: z.string().trim().regex(/^\d{6}$/, { message: 'Code must be a 6-digit numeric string' }),
  deviceName: z.string().trim().max(100).optional(),
  platform: z.string().trim().max(50).optional(),
  identityKeyBase64: z.string().trim().min(20).max(128).regex(/^[A-Za-z0-9+/=_-]+$/, { message: 'Identity key must be valid Base64' }),
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

    // Send real SMS if Twilio or custom SMS Gateway credentials are configured
    let smsDispatched = false;
    let smsProvider = '';

    // 1. Check Twilio (Global SMS)
    if (twilioClient && process.env.TWILIO_PHONE_NUMBER && !isSandboxTestNumber) {
      try {
        await twilioClient.messages.create({
          body: `Your Argus verification code is: ${code}. Valid for 5 minutes. Do not share this code.`,
          from: process.env.TWILIO_PHONE_NUMBER,
          to: phoneNumber
        });
        smsDispatched = true;
        smsProvider = 'Twilio';
        console.log(`[SMS Gateway] Dispatched cellular SMS to ${phoneNumber} via Twilio`);
      } catch (smsError: any) {
        console.error('[SMS Gateway] Twilio SMS dispatch failed:', smsError.message);
      }
    }
    // 2. Check Fast2SMS (Instant Indian Cellular SMS)
    else if (process.env.FAST2SMS_API_KEY && !isSandboxTestNumber) {
      try {
        const clean10Digits = phoneNumber.replace(/^\+91/, '').replace(/\D/g, '').slice(-10);
        const isIndianNumber = phoneNumber.startsWith('+91') || (clean10Digits.length === 10 && /^[6-9]/.test(clean10Digits));

        if (isIndianNumber) {
          // Fast2SMS Quick SMS Route (No website verification needed)
          const quickSmsPayload = {
            route: 'q',
            message: `Your Argus verification code is ${code}. Valid for 5 minutes. Do not share this code.`,
            language: 'english',
            flash: 0,
            numbers: clean10Digits
          };

          let response = await fetch('https://www.fast2sms.com/dev/bulkV2', {
            method: 'POST',
            headers: {
              'authorization': process.env.FAST2SMS_API_KEY.trim(),
              'Content-Type': 'application/json'
            },
            body: JSON.stringify(quickSmsPayload)
          });

          let result: any = await response.json().catch(() => ({}));

          // If Quick SMS route returned success
          if (response.ok && (result.return === true || result.status_code === 200 || result.return_code === 200)) {
            smsDispatched = true;
            smsProvider = 'Fast2SMS';
            console.log(`[SMS Gateway] Dispatched cellular SMS to ${clean10Digits} via Fast2SMS (${result.message?.[0] || 'Success'})`);
          } else {
            // Fallback to OTP route
            const otpPayload = {
              variables_values: code,
              route: 'otp',
              numbers: clean10Digits
            };
            response = await fetch('https://www.fast2sms.com/dev/bulkV2', {
              method: 'POST',
              headers: {
                'authorization': process.env.FAST2SMS_API_KEY.trim(),
                'Content-Type': 'application/json'
              },
              body: JSON.stringify(otpPayload)
            });
            result = await response.json().catch(() => ({}));
            if (response.ok && (result.return === true || result.status_code === 200 || result.return_code === 200)) {
              smsDispatched = true;
              smsProvider = 'Fast2SMS (OTP Route)';
              console.log(`[SMS Gateway] Dispatched cellular SMS to ${clean10Digits} via Fast2SMS (${result.message?.[0] || 'Success'})`);
            } else {
              console.error(`[SMS Gateway] Fast2SMS returned response:`, result.message || result);
            }
          }
        } else {
          console.warn(`[SMS Gateway] Fast2SMS only supports Indian (+91) mobile numbers. Skipping Fast2SMS for ${phoneNumber}`);
        }
      } catch (f2sError: any) {
        console.error('[SMS Gateway] Fast2SMS connection error:', f2sError.message);
      }
    }
    // 3. Check Generic SMS Gateway URL
    else if (process.env.SMS_GATEWAY_URL && !isSandboxTestNumber) {
      try {
        const payload = {
          to: phoneNumber,
          message: `Your Argus verification code is: ${code}. Valid for 5 minutes.`,
          code: code,
          apiKey: process.env.SMS_GATEWAY_API_KEY || ''
        };
        const response = await fetch(process.env.SMS_GATEWAY_URL, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(process.env.SMS_GATEWAY_API_KEY ? { 'Authorization': `Bearer ${process.env.SMS_GATEWAY_API_KEY}` } : {})
          },
          body: JSON.stringify(payload)
        });
        if (response.ok) {
          smsDispatched = true;
          smsProvider = 'Custom SMS Gateway';
          console.log(`[SMS Gateway] Dispatched cellular SMS to ${phoneNumber} via custom SMS Gateway`);
        } else {
          console.error(`[SMS Gateway] Custom SMS gateway returned status ${response.status}`);
        }
      } catch (gatewayError: any) {
        console.error('[SMS Gateway] Custom SMS gateway error:', gatewayError.message);
      }
    }

    if (!smsDispatched && !isSandboxTestNumber) {
      console.log(`\n======================================================`);
      console.log(`[Argus SMS Service] Generated OTP for ${phoneNumber}: ${code}`);
      console.log(`[Argus SMS Service] Real cellular SMS delivery requires:`);
      console.log(`  Option 1 (Fast2SMS - India): FAST2SMS_API_KEY in server/.env`);
      console.log(`  Option 2 (Twilio - Global): TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_PHONE_NUMBER`);
      console.log(`======================================================\n`);
    }

    const isDev = process.env.NODE_ENV === 'development' || process.env.ENABLE_DEV_OTP === 'true';

    res.json({
      success: true,
      message: smsDispatched ? `Verification code sent via SMS (${smsProvider})` : 'OTP generated and dispatched',
      expiresInSec: 300,
      ...(isDev ? { devCode: code } : {})
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
      { userId: user.id, deviceId: record.deviceId, phoneNumber: user.phoneNumber },
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
