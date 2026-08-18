import { Router, Request, Response } from 'express';
import { ArgusDatabase } from '../db/database';

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
    const { displayName, username, about, avatarUrl } = req.body;
    const user = db.users.get(userId);

    if (!user) {
      res.status(404).json({ error: 'User not found' });
      return;
    }

    if (username !== undefined) {
      const cleanUsername = username.trim().toLowerCase();
      if (cleanUsername.length > 0) {
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

    if (displayName) user.displayName = displayName.trim();
    if (about !== undefined) user.about = about.trim();
    if (avatarUrl !== undefined) user.avatarUrl = avatarUrl;

    db.save();
    res.json({ success: true, user });
  });

  /**
   * Search users by username or exact phone number query
   */
  router.get('/search', (req: Request, res: Response): void => {
    const query = ((req.query.q as string) || '').trim().toLowerCase();
    if (!query || query.length < 2) {
      res.json({ results: [] });
      return;
    }

    const results: any[] = [];
    for (const u of db.users.values()) {
      if (
        (u.username && u.username.toLowerCase().includes(query)) ||
        u.displayName.toLowerCase().includes(query) ||
        u.phoneNumber.includes(query)
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
    }

    res.json({ results });
  });

  /**
   * Privacy-preserving Contact Discovery:
   * Client sends a list of SHA-256 phone number hashes.
   * Server returns matching user profiles without learning the user's un-hashed address book!
   */
  router.post('/discover-contacts', (req: Request, res: Response): void => {
    const { phoneHashes } = req.body;
    if (!Array.isArray(phoneHashes)) {
      res.status(400).json({ error: 'phoneHashes array is required' });
      return;
    }

    const hashSet = new Set(phoneHashes);
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

  return router;
}
