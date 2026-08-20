import { Server as HttpServer } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import jwt from 'jsonwebtoken';
import { ArgusDatabase } from '../db/database';
import { WsClientEvent, WsServerEvent, EncryptedMessagePayload } from '../types';
import { notificationService } from '../services/notificationService';

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
  // Bidirectional peer tracking for scoped privacy-preserving presence broadcasts (BUG-9 fixed)
  private userDirectPeers: Map<string, Set<string>> = new Map();

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
    this.userDirectPeers.clear();
    this.wss.close();
  }

  private addPeerRelation(userA: string, userB: string): void {
    if (!userA || !userB || userA === userB) return;
    if (!this.userDirectPeers.has(userA)) this.userDirectPeers.set(userA, new Set());
    if (!this.userDirectPeers.has(userB)) this.userDirectPeers.set(userB, new Set());
    this.userDirectPeers.get(userA)!.add(userB);
    this.userDirectPeers.get(userB)!.add(userA);
  }

  private handleClientEvent(ws: AuthenticatedSocket, event: WsClientEvent): void {
    if (event.type === 'AUTH') {
      try {
        const decoded = jwt.verify(event.token, this.jwtSecret) as { userId: string; deviceId: string };
        const user = this.db.users.get(decoded.userId);
        if (!user) {
          this.send(ws, { type: 'AUTH_ERROR', message: 'User account not found' });
          ws.close();
          return;
        }

        ws.userId = decoded.userId;
        ws.deviceId = event.deviceId || decoded.deviceId;

        if (!this.userSockets.has(ws.userId)) {
          this.userSockets.set(ws.userId, new Set());
        }
        this.userSockets.get(ws.userId)!.add(ws);

        // Update presence
        user.isOnline = true;
        user.lastSeen = Date.now();
        this.db.scheduleSave();
        this.broadcastPresence(ws.userId, true, user.lastSeen);

        this.send(ws, { type: 'AUTH_SUCCESS', userId: ws.userId });

        // Deliver any queued offline messages (filtering expired ones)
        const now = Date.now();
        const offlineMsgs = this.db.getOfflineMessages(ws.userId);
        offlineMsgs.forEach(msg => {
          if (!msg.expiresAt || msg.expiresAt > now) {
            this.send(ws, { type: 'NEW_MESSAGE', payload: msg });
          }
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
        if (!event.payload) break;
        this.handleSendMessage(ws, event.payload);
        break;
      }

      case 'ACK_DELIVERED': {
        if (!event.senderId || !event.messageId) break;
        this.relayStatusToSender(event.senderId, event.messageId, 'DELIVERED');
        break;
      }

      case 'ACK_READ': {
        // BUG-5 fixed: Validate required fields before relaying status
        if (!event.senderId || !event.messageId) break;
        this.relayStatusToSender(event.senderId, event.messageId, 'READ');
        break;
      }

      case 'TYPING_START':
      case 'TYPING_STOP': {
        if (event.recipientId) {
          this.addPeerRelation(ws.userId, event.recipientId);
        }
        this.relayTyping(ws.userId, event.recipientId, event.conversationId, event.type === 'TYPING_START');
        break;
      }

      case 'CALL_OFFER': {
        const caller = this.db.users.get(ws.userId);
        const targetSockets = this.userSockets.get(event.targetUserId);
        const isTargetOnline = targetSockets && targetSockets.size > 0;
        this.addPeerRelation(ws.userId, event.targetUserId);

        this.relayToUser(event.targetUserId, {
          type: 'INCOMING_CALL',
          callerId: ws.userId,
          callId: event.callId,
          callType: event.callType,
          sdp: event.sdp
        });

        // Dispatch high-priority wakeup push notification if callee is offline
        if (!isTargetOnline) {
          notificationService.sendWakeup(event.targetUserId, {
            type: 'INCOMING_CALL',
            senderId: ws.userId,
            senderName: caller?.displayName || 'Argus Contact',
            callType: event.callType
          });
        }
        break;
      }

      case 'CALL_ANSWER': {
        this.addPeerRelation(ws.userId, event.targetUserId);
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
    if (!payload || !payload.id || !payload.recipientId) {
      return;
    }
    const senderUser = senderWs.userId ? this.db.users.get(senderWs.userId) : undefined;
    if (senderWs.userId && payload.recipientId) {
      this.addPeerRelation(senderWs.userId, payload.recipientId);
    }

    // Check if recipient is a group
    const group = this.db.groups.get(payload.recipientId) || this.db.groups.get(payload.conversationId);
    if (group && senderWs.userId && group.members.includes(senderWs.userId)) {
      // Fan out message to all group members except sender
      group.members.forEach(memberId => {
        if (memberId === senderWs.userId) return;

        const memberSockets = this.userSockets.get(memberId);
        let delivered = false;

        if (memberSockets && memberSockets.size > 0) {
          memberSockets.forEach(sock => {
            if (sock.readyState === WebSocket.OPEN) {
              this.send(sock, { type: 'NEW_MESSAGE', payload: { ...payload, status: 'DELIVERED' } });
              delivered = true;
            }
          });
        }

        if (!delivered) {
          this.db.queueOfflineMessage(memberId, { ...payload, status: 'SENT' });
          notificationService.sendWakeup(memberId, {
            type: 'NEW_MESSAGE',
            senderId: senderWs.userId || payload.senderId,
            senderName: senderUser?.displayName || 'Argus Contact',
            conversationId: payload.conversationId,
            messageId: payload.id
          });
        }
      });

      this.send(senderWs, { type: 'MESSAGE_STATUS', messageId: payload.id, status: 'SENT' });
      return;
    }

    // Direct 1-to-1 message
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

    // Recipient is offline: buffer in encrypted offline storage and dispatch FCM push wakeup
    this.db.queueOfflineMessage(payload.recipientId, { ...payload, status: 'SENT' });
    this.send(senderWs, { type: 'MESSAGE_STATUS', messageId: payload.id, status: 'SENT' });

    notificationService.sendWakeup(payload.recipientId, {
      type: 'NEW_MESSAGE',
      senderId: payload.senderId,
      senderName: senderUser?.displayName || 'Argus Contact',
      conversationId: payload.conversationId,
      messageId: payload.id
    });
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
    // Check if recipient is a group
    const group = this.db.groups.get(recipientId) || this.db.groups.get(conversationId);
    if (group && group.members.includes(senderId)) {
      group.members.forEach(memberId => {
        if (memberId !== senderId) {
          this.relayTypingToSingleUser(senderId, memberId, conversationId, isTyping);
        }
      });
      return;
    }

    this.relayTypingToSingleUser(senderId, recipientId, conversationId, isTyping);
  }

  private relayTypingToSingleUser(senderId: string, targetUserId: string, conversationId: string, isTyping: boolean): void {
    const sockets = this.userSockets.get(targetUserId);
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

  /**
   * Broadcast presence to relevant group peers and direct conversational contacts only (BUG-9 fixed)
   */
  private broadcastPresence(userId: string, isOnline: boolean, lastSeen: number): void {
    const event: WsServerEvent = { type: 'PRESENCE', userId, isOnline, lastSeen };
    const targetPeerIds = new Set<string>();

    // 1. Group peers
    for (const group of this.db.groups.values()) {
      if (group.members.includes(userId)) {
        group.members.forEach(mId => {
          if (mId !== userId) targetPeerIds.add(mId);
        });
      }
    }

    // 2. Direct peers (people who have exchanged messages, calls, or typing)
    const directPeers = this.userDirectPeers.get(userId);
    if (directPeers) {
      directPeers.forEach(pId => targetPeerIds.add(pId));
    }

    // Deliver presence event only to relevant authenticated active sockets
    targetPeerIds.forEach(targetId => {
      const sockets = this.userSockets.get(targetId);
      if (sockets) {
        sockets.forEach(sock => {
          if (sock.readyState === WebSocket.OPEN) {
            this.send(sock, event);
          }
        });
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
            this.db.scheduleSave();
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
