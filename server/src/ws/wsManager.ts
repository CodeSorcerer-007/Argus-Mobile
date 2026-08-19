import { Server as HttpServer } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import jwt from 'jsonwebtoken';
import { ArgusDatabase } from '../db/database';
import { WsClientEvent, WsServerEvent, EncryptedMessagePayload } from '../types';

interface AuthenticatedSocket extends WebSocket {
  userId?: string;
  deviceId?: string;
  isAlive?: boolean;
}

export class ArgusWebSocketManager {
  private wss: WebSocketServer;
  private db: ArgusDatabase;
  private jwtSecret: string;
  // userId -> Set<AuthenticatedSocket>
  private userSockets: Map<string, Set<AuthenticatedSocket>> = new Map();

  private heartbeatInterval: NodeJS.Timeout;

  constructor(server: HttpServer, db: ArgusDatabase, jwtSecret: string) {
    this.db = db;
    this.jwtSecret = jwtSecret;
    this.wss = new WebSocketServer({ server, path: '/ws' });

    this.wss.on('connection', (ws: AuthenticatedSocket) => {
      ws.isAlive = true;

      ws.on('pong', () => {
        ws.isAlive = true;
      });

      ws.on('message', (rawData: string) => {
        try {
          const event: WsClientEvent = JSON.parse(rawData.toString());
          this.handleClientEvent(ws, event);
        } catch (err) {
          console.error('Failed to parse WebSocket message:', err);
        }
      });

      ws.on('close', () => {
        this.handleDisconnect(ws);
      });
    });

    // Heartbeat liveness check every 30 seconds
    this.heartbeatInterval = setInterval(() => {
      this.wss.clients.forEach((ws: WebSocket) => {
        const socket = ws as AuthenticatedSocket;
        if (socket.isAlive === false) {
          return socket.terminate();
        }
        socket.isAlive = false;
        socket.ping();
      });
    }, 30000);
  }

  public close(): void {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
    }
    this.userSockets.clear();
    this.wss.close();
  }

  private handleClientEvent(ws: AuthenticatedSocket, event: WsClientEvent): void {
    if (event.type === 'AUTH') {
      try {
        const decoded = jwt.verify(event.token, this.jwtSecret) as { userId: string; deviceId: string };
        ws.userId = decoded.userId;
        ws.deviceId = event.deviceId || decoded.deviceId;

        if (!this.userSockets.has(ws.userId)) {
          this.userSockets.set(ws.userId, new Set());
        }
        this.userSockets.get(ws.userId)!.add(ws);

        // Update presence
        const user = this.db.users.get(ws.userId);
        if (user) {
          user.isOnline = true;
          user.lastSeen = Date.now();
          this.db.save();
          this.broadcastPresence(ws.userId, true, user.lastSeen);
        }

        this.send(ws, { type: 'AUTH_SUCCESS', userId: ws.userId });

        // Deliver any queued offline messages
        const offlineMsgs = this.db.getOfflineMessages(ws.userId);
        offlineMsgs.forEach(msg => {
          this.send(ws, { type: 'NEW_MESSAGE', payload: msg });
        });
      } catch (err) {
        this.send(ws, { type: 'AUTH_ERROR', message: 'Invalid or expired auth token' });
        ws.close();
      }
      return;
    }

    if (!ws.userId) {
      this.send(ws, { type: 'AUTH_ERROR', message: 'Unauthenticated socket connection' });
      return;
    }

    switch (event.type) {
      case 'HEARTBEAT': {
        this.send(ws, { type: 'PONG' });
        break;
      }

      case 'SEND_MESSAGE': {
        this.handleSendMessage(ws, event.payload);
        break;
      }

      case 'ACK_DELIVERED': {
        this.relayStatusToSender(event.senderId, event.messageId, 'DELIVERED');
        break;
      }

      case 'ACK_READ': {
        this.relayStatusToSender(event.senderId, event.messageId, 'READ');
        break;
      }

      case 'TYPING_START':
      case 'TYPING_STOP': {
        this.relayTyping(ws.userId, event.recipientId, event.conversationId, event.type === 'TYPING_START');
        break;
      }

      case 'CALL_OFFER': {
        this.relayToUser(event.targetUserId, {
          type: 'INCOMING_CALL',
          callerId: ws.userId,
          callId: event.callId,
          callType: event.callType,
          sdp: event.sdp
        });
        break;
      }

      case 'CALL_ANSWER': {
        this.relayToUser(event.targetUserId, {
          type: 'CALL_ANSWERED',
          callId: event.callId,
          sdp: event.sdp
        });
        break;
      }

      case 'ICE_CANDIDATE': {
        this.relayToUser(event.targetUserId, {
          type: 'REMOTE_ICE_CANDIDATE',
          callId: event.callId,
          candidate: event.candidate
        });
        break;
      }

      case 'CALL_END':
      case 'CALL_REJECT': {
        this.relayToUser(event.targetUserId, {
          type: 'CALL_TERMINATED',
          callId: event.callId,
          reason: event.type
        });
        break;
      }
    }
  }

  private handleSendMessage(senderWs: AuthenticatedSocket, payload: EncryptedMessagePayload): void {
    const recipientSockets = this.userSockets.get(payload.recipientId);

    if (recipientSockets && recipientSockets.size > 0) {
      let delivered = false;
      recipientSockets.forEach(sock => {
        if (sock.readyState === WebSocket.OPEN) {
          this.send(sock, { type: 'NEW_MESSAGE', payload: { ...payload, status: 'DELIVERED' } });
          delivered = true;
        }
      });

      if (delivered) {
        this.send(senderWs, { type: 'MESSAGE_STATUS', messageId: payload.id, status: 'DELIVERED' });
        return;
      }
    }

    // Recipient is offline: buffer in encrypted offline storage
    this.db.queueOfflineMessage(payload.recipientId, { ...payload, status: 'SENT' });
    this.send(senderWs, { type: 'MESSAGE_STATUS', messageId: payload.id, status: 'SENT' });
  }

  private relayStatusToSender(senderId: string, messageId: string, status: 'DELIVERED' | 'READ'): void {
    const sockets = this.userSockets.get(senderId);
    if (sockets) {
      sockets.forEach(s => {
        if (s.readyState === WebSocket.OPEN) {
          this.send(s, { type: 'MESSAGE_STATUS', messageId, status });
        }
      });
    }
  }

  private relayTyping(senderId: string, recipientId: string, conversationId: string, isTyping: boolean): void {
    const sockets = this.userSockets.get(recipientId);
    if (sockets) {
      sockets.forEach(s => {
        if (s.readyState === WebSocket.OPEN) {
          this.send(s, { type: 'TYPING', userId: senderId, conversationId, isTyping });
        }
      });
    }
  }

  private relayToUser(targetUserId: string, message: WsServerEvent): void {
    const sockets = this.userSockets.get(targetUserId);
    if (sockets) {
      sockets.forEach(s => {
        if (s.readyState === WebSocket.OPEN) {
          this.send(s, message);
        }
      });
    }
  }

  private broadcastPresence(userId: string, isOnline: boolean, lastSeen: number): void {
    const event: WsServerEvent = { type: 'PRESENCE', userId, isOnline, lastSeen };
    this.wss.clients.forEach(ws => {
      const sock = ws as AuthenticatedSocket;
      if (sock.userId && sock.userId !== userId && sock.readyState === WebSocket.OPEN) {
        this.send(sock, event);
      }
    });
  }

  private handleDisconnect(ws: AuthenticatedSocket): void {
    if (ws.userId) {
      const set = this.userSockets.get(ws.userId);
      if (set) {
        set.delete(ws);
        if (set.size === 0) {
          this.userSockets.delete(ws.userId);
          const user = this.db.users.get(ws.userId);
          if (user) {
            user.isOnline = false;
            user.lastSeen = Date.now();
            this.db.save();
            this.broadcastPresence(ws.userId, false, user.lastSeen);
          }
        }
      }
    }
  }

  private send(ws: WebSocket, data: WsServerEvent): void {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(data));
    }
  }
}
