import { Router, Request, Response } from 'express';
import { ArgusDatabase } from '../db/database';
import { StoredPreKeyBundle } from '../types';

export function createKeysRouter(db: ArgusDatabase): Router {
  const router = Router();

  /**
   * Publish or update PreKey bundle for the authenticated user and device
   */
  router.post('/publish-bundle', (req: Request, res: Response): void => {
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
      oneTimePreKeyPublicBase64: otpk ? otpk.publicKeyBase64 : null
    });
  });

  return router;
}
