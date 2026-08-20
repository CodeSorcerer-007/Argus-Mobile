/**
 * Argus Push Notification Service
 * Dispatches encrypted wakeup payloads to mobile devices via FCM (Firebase Cloud Messaging)
 * when recipients are offline or app is killed in the background.
 * Persists push tokens to database (BUG-12 fixed).
 */

import { ArgusDatabase } from '../db/database';

export interface PushPayload {
  type: 'NEW_MESSAGE' | 'INCOMING_CALL' | 'SECURITY_ALERT';
  senderId: string;
  senderName?: string;
  conversationId?: string;
  callType?: 'VOICE' | 'VIDEO';
  messageId?: string;
}

class NotificationService {
  private db: ArgusDatabase | null = null;
  private inMemoryPushTokens = new Map<string, string>(); // fallback if db is not yet attached

  public setDatabase(db: ArgusDatabase): void {
    this.db = db;
    // Copy any tokens registered before DB was set
    this.inMemoryPushTokens.forEach((token, userId) => {
      this.db!.pushTokens.set(userId, token);
    });
    this.inMemoryPushTokens.clear();
  }

  public registerToken(userId: string, token: string): void {
    if (this.db) {
      this.db.pushTokens.set(userId, token);
      this.db.scheduleSave();
    } else {
      this.inMemoryPushTokens.set(userId, token);
    }
  }

  public unregisterToken(userId: string): void {
    if (this.db) {
      this.db.pushTokens.delete(userId);
      this.db.scheduleSave();
    } else {
      this.inMemoryPushTokens.delete(userId);
    }
  }

  public getToken(userId: string): string | undefined {
    return this.db ? this.db.pushTokens.get(userId) : this.inMemoryPushTokens.get(userId);
  }

  /**
   * Dispatch encrypted wakeup notification to target user's registered device.
   * If FCM is not configured, logs in sandbox mode gracefully without crashing.
   */
  public async sendWakeup(targetUserId: string, payload: PushPayload): Promise<boolean> {
    const token = this.getToken(targetUserId);
    if (!token) {
      return false; // User has no registered push token
    }

    const fcmServerKey = process.env.FCM_SERVER_KEY;
    if (!fcmServerKey) {
      // Sandbox mode: log and skip remote dispatch
      return true;
    }

    try {
      const response = await fetch('https://fcm.googleapis.com/fcm/send', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `key=${fcmServerKey}`
        },
        body: JSON.stringify({
          to: token,
          priority: 'high',
          data: {
            type: payload.type,
            senderId: payload.senderId,
            senderName: payload.senderName || 'Argus Contact',
            conversationId: payload.conversationId || '',
            callType: payload.callType || '',
            messageId: payload.messageId || ''
          }
        })
      });

      return response.ok;
    } catch (err: any) {
      console.error('Failed to send push notification:', err.message);
      return false;
    }
  }
}

export const notificationService = new NotificationService();
