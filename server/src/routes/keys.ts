import { Router, Request, Response } from 'express';
import { ArgusDatabase } from '../db/database';
import { StoredPreKeyBundle } from '../types';

export function createKeysRouter(db: ArgusDatabase): Router {
  const router = Router();

  /**
   * Publish or update PreKey bundle for the authenticated user and device
   */
  const handlePublishBundle = (req: Request, res: Response): void => {
    const { userId, deviceId } = (req as any).user;
    const {
      identityPublicKeyBase64,
      signedPreKeyId,
      signedPreKeyPublicBase64,
      signedPreKeySignatureBase64,
      oneTimePreKeys
    } = req.body;

    if (!identityPublicKeyBase64 || signedPreKeyId === undefined || !signedPreKeyPublicBase64 || !signedPreKeySignatureBase64) {
      res.status(400).json({ error: 'Missing required prekey cryptographic parameters' });
      return;
    }

    const bundleKey = `${userId}:${deviceId}`;
    const bundle: StoredPreKeyBundle = {
      userId,
      deviceId,
      identityPublicKeyBase64,
      signedPreKeyId,
      signedPreKeyPublicBase64,
      signedPreKeySignatureBase64,
      oneTimePreKeys: Array.isArray(oneTimePreKeys) ? oneTimePreKeys : [],
      updatedAt: Date.now()
    };

    db.keyBundles.set(bundleKey, bundle);

    // Update user identity key if present
    const user = db.users.get(userId);
    if (user) {
      user.identityKeyBase64 = identityPublicKeyBase64;
    }

    db.save();
    res.json({ success: true, message: 'PreKey bundle published successfully', availableOneTimeKeys: bundle.oneTimePreKeys.length });
  };

  router.post('/publish-bundle', handlePublishBundle);
  router.post('/publish', handlePublishBundle);

  /**
   * Check PreKey bundle health and remaining one-time keys count
   */
  router.get('/status', (req: Request, res: Response): void => {
    const { userId, deviceId } = (req as any).user;
    const bundleKey = `${userId}:${deviceId}`;
    const bundle = db.keyBundles.get(bundleKey);

    if (!bundle) {
      res.status(404).json({ error: 'No PreKey bundle found for device', needsReplenishment: true, availableOneTimeKeys: 0 });
      return;
    }

    const availableOneTimeKeys = bundle.oneTimePreKeys.length;
    res.json({
      success: true,
      userId,
      deviceId,
      signedPreKeyId: bundle.signedPreKeyId,
      availableOneTimeKeys,
      needsReplenishment: availableOneTimeKeys < 10,
      updatedAt: bundle.updatedAt
    });
  });

  /**
   * Replenish one-time prekeys when pool is running low
   */
  router.post('/replenish', (req: Request, res: Response): void => {
    const { userId, deviceId } = (req as any).user;
    const { oneTimePreKeys } = req.body;

    if (!Array.isArray(oneTimePreKeys) || oneTimePreKeys.length === 0) {
      res.status(400).json({ error: 'oneTimePreKeys array is required and must not be empty' });
      return;
    }

    const bundleKey = `${userId}:${deviceId}`;
    const bundle = db.keyBundles.get(bundleKey);

    if (!bundle) {
      res.status(404).json({ error: 'Initial PreKey bundle must be published before replenishing' });
      return;
    }

    // Append new one-time prekeys to existing pool
    bundle.oneTimePreKeys.push(...oneTimePreKeys);
    bundle.updatedAt = Date.now();
    db.save();

    res.json({
      success: true,
      message: `Successfully replenished ${oneTimePreKeys.length} one-time prekey(s)`,
      availableOneTimeKeys: bundle.oneTimePreKeys.length,
      needsReplenishment: bundle.oneTimePreKeys.length < 10
    });
  });

  /**
   * Fetch a PreKey bundle for establishing a new E2EE Double Ratchet session with target user
   */
  router.get('/bundle/:targetUserId', (req: Request, res: Response): void => {
    const targetUserId = req.params.targetUserId as string;
    const targetUser = db.users.get(targetUserId);

    if (!targetUser) {
      res.status(404).json({ error: 'Target user not found' });
      return;
    }

    // Find any active device key bundle for target user
    let foundBundle: StoredPreKeyBundle | undefined;
    for (const bundle of db.keyBundles.values()) {
      if (bundle.userId === targetUserId) {
        foundBundle = bundle;
        break;
      }
    }

    if (!foundBundle) {
      res.status(404).json({ error: 'Target user has no published cryptographic pre-keys' });
      return;
    }

    // Safely consume one one-time prekey if available (X3DH forward secrecy)
    const otpk = db.popOneTimePreKey(foundBundle.userId, foundBundle.deviceId);

    res.json({
      userId: foundBundle.userId,
      deviceId: foundBundle.deviceId,
      identityPublicKeyBase64: foundBundle.identityPublicKeyBase64,
      signedPreKeyId: foundBundle.signedPreKeyId,
      signedPreKeyPublicBase64: foundBundle.signedPreKeyPublicBase64,
      signedPreKeySignatureBase64: foundBundle.signedPreKeySignatureBase64,
      oneTimePreKeyId: otpk ? otpk.keyId : null,
      oneTimePreKeyPublicBase64: otpk ? otpk.publicKeyBase64 : null,
      remainingOneTimeKeys: foundBundle.oneTimePreKeys.length
    });
  });

  /**
   * Fetch PreKey bundles for multiple users in a single batch request (e.g. for Group E2EE sessions)
   */
  router.post('/bundles', (req: Request, res: Response): void => {
    const { userIds } = req.body;
    if (!Array.isArray(userIds) || userIds.length === 0) {
      res.status(400).json({ error: 'userIds array is required and cannot be empty' });
      return;
    }

    const maxBatchSize = 50;
    const requestedIds = userIds.slice(0, maxBatchSize);
    const bundles: Record<string, any> = {};

    for (const targetUserId of requestedIds) {
      const targetUser = db.users.get(targetUserId);
      if (!targetUser) continue;

      let foundBundle: StoredPreKeyBundle | undefined;
      for (const bundle of db.keyBundles.values()) {
        if (bundle.userId === targetUserId) {
          foundBundle = bundle;
          break;
        }
      }

      if (foundBundle) {
        const otpk = db.popOneTimePreKey(foundBundle.userId, foundBundle.deviceId);
        bundles[targetUserId] = {
          userId: foundBundle.userId,
          deviceId: foundBundle.deviceId,
          identityPublicKeyBase64: foundBundle.identityPublicKeyBase64,
          signedPreKeyId: foundBundle.signedPreKeyId,
          signedPreKeyPublicBase64: foundBundle.signedPreKeyPublicBase64,
          signedPreKeySignatureBase64: foundBundle.signedPreKeySignatureBase64,
          oneTimePreKeyId: otpk ? otpk.keyId : null,
          oneTimePreKeyPublicBase64: otpk ? otpk.publicKeyBase64 : null,
          remainingOneTimeKeys: foundBundle.oneTimePreKeys.length
        };
      }
    }

    res.json({ success: true, bundles });
  });

  return router;
}
