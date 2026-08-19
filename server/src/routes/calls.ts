import { Router, Request, Response } from 'express';
import { getIceServers } from '../services/turnService';

export function createCallsRouter(): Router {
  const router = Router();

  /**
   * GET /api/calls/ice-servers
   * Returns STUN/TURN ICE server credentials for WebRTC audio/video calls.
   */
  router.get('/ice-servers', (_req: Request, res: Response): void => {
    const servers = getIceServers();
    res.json({
      iceServers: servers,
      ttlSec: 86400
    });
  });

  return router;
}
