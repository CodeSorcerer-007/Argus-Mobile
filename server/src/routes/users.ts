import { Router, Request, Response } from 'express';
import { z } from 'zod';
import { ArgusDatabase } from '../db/database';

const profileUpdateSchema = z.object({
  displayName: z.string().trim().min(1).max(60).optional(),
  username: z.string().trim().regex(/^[a-zA-Z0-9_]{3,30}$/, {
    message: 'Username must be 3-30 characters (letters, numbers, underscores only)'
  }).optional().nullable(),
  about: z.string().trim().max(200).optional(),
  avatarUrl: z.string().trim().max(500).optional().nullable()
});

const contactDiscoverySchema = z.object({
  phoneHashes: z.array(z.string().trim().min(16).max(128)).max(2000, {
    message: 'Maximum 2000 contact hashes per batch'
  })
});

export function createUsersRouter(db: ArgusDatabase): Router {
  const router = Router();

  /**
   * Get current user profile
   */
  router.get('/me', (req: Request, res: Response): void => {
    const { userId } = (req as any).user;
    const user = db.users.get(userId);
    if (!user) {
      res.status(404).json({ error: 'User not found' });
      return;
    }
    res.json({ user });
  });

  /**
   * Update current user profile (display name, username, about, avatar)
   */
  router.put('/me', (req: Request, res: Response): void => {
    const { userId } = (req as any).user;
    const parseResult = profileUpdateSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({ error: parseResult.error.issues[0].message });
      return;
    }

    const { displayName, username, about, avatarUrl } = parseResult.data;
    const user = db.users.get(userId);

    if (!user) {
      res.status(404).json({ error: 'User not found' });
      return;
    }

    if (username !== undefined) {
      if (username) {
        const cleanUsername = username.toLowerCase();
        const existing = db.findUserByUsername(cleanUsername);
        if (existing && existing.id !== userId) {
          res.status(409).json({ error: 'Username is already taken' });
          return;
        }
        user.username = cleanUsername;
      } else {
        user.username = undefined;
      }
    }

    if (displayName) user.displayName = displayName;
    if (about !== undefined) user.about = about;
    if (avatarUrl !== undefined) user.avatarUrl = avatarUrl || undefined;

    db.save();
    res.json({ success: true, user });
  });

  /**
   * Search users by username or display name (Privacy-safe: NEVER searches raw phone numbers)
   */
  router.get('/search', (req: Request, res: Response): void => {
    const query = ((req.query.q as string) || '').trim().toLowerCase();
    if (!query || query.length < 2 || query.length > 50) {
      res.json({ results: [] });
      return;
    }

    const results: any[] = [];
    for (const u of db.users.values()) {
      if (
        (u.username && u.username.toLowerCase().includes(query)) ||
        u.displayName.toLowerCase().includes(query)
      ) {
        results.push({
          id: u.id,
          username: u.username,
          displayName: u.displayName,
          avatarUrl: u.avatarUrl,
          about: u.about,
          identityKeyBase64: u.identityKeyBase64,
          isOnline: u.isOnline,
          lastSeen: u.lastSeen
        });
      }
      if (results.length >= 20) break; // Limit search result size
    }

    res.json({ results });
  });

  /**
   * Privacy-preserving Contact Discovery:
   * Client sends a list of SHA-256 phone number hashes.
   * Server returns matching user profiles without learning the user's un-hashed address book!
   */
  router.post('/discover-contacts', (req: Request, res: Response): void => {
    const parseResult = contactDiscoverySchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({ error: parseResult.error.issues[0].message });
      return;
    }

    const hashSet = new Set(parseResult.data.phoneHashes);
    const matched: any[] = [];

    for (const u of db.users.values()) {
      if (hashSet.has(u.phoneHash)) {
        matched.push({
          id: u.id,
          phoneHash: u.phoneHash,
          username: u.username,
          displayName: u.displayName,
          avatarUrl: u.avatarUrl,
          about: u.about,
          identityKeyBase64: u.identityKeyBase64,
          isOnline: u.isOnline,
          lastSeen: u.lastSeen
        });
      }
    }

    res.json({ contacts: matched });
  });

  /**
   * Register push notification device token (FCM)
   */
  router.post('/push-token', (req: Request, res: Response): void => {
    const userId = (req as any).user?.userId;
    const token = req.body.token;

    if (!userId || !token || typeof token !== 'string') {
      res.status(400).json({ error: 'Valid token is required' });
      return;
    }

    const { notificationService } = require('../services/notificationService');
    notificationService.registerToken(userId, token.trim());
    res.json({ success: true, message: 'Push token registered' });
  });

  return router;
}
