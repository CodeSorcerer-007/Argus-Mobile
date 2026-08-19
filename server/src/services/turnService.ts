/**
 * Argus STUN/TURN ICE Configuration Service
 * Provides dynamic ICE server credentials for WebRTC peer-to-peer audio/video calls
 * to penetrate symmetric NATs and enterprise firewalls.
 */

export interface IceServer {
  urls: string | string[];
  username?: string;
  credential?: string;
}

export function getIceServers(): IceServer[] {
  // Public high-reliability Google & Cloudflare STUN servers as baseline
  const servers: IceServer[] = [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' },
    { urls: 'stun:stun.cloudflare.com:3478' }
  ];

  // If custom TURN server credentials (e.g. Coturn or Twilio Network Traversal) are configured
  if (process.env.TURN_SERVER_URL && process.env.TURN_USERNAME && process.env.TURN_CREDENTIAL) {
    servers.push({
      urls: process.env.TURN_SERVER_URL,
      username: process.env.TURN_USERNAME,
      credential: process.env.TURN_CREDENTIAL
    });
  }

  return servers;
}
