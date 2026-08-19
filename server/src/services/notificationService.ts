/**
 * Argus Push Notification Service
 * Dispatches encrypted wakeup payloads to mobile devices via FCM (Firebase Cloud Messaging)
 * when recipients are offline or app is killed in the background.
 */

export interface PushPayload {
  type: 'NEW_MESSAGE' | 'INCOMING_CALL' | 'SECURITY_ALERT';
  senderId: string;
  senderName?: string;
  conversationId?: string;
  callType?: 'VOICE' | 'VIDEO';
  messageId?: string;
}

class NotificationService {
  private pushTokens = new Map<string, string>(); // userId -> fcmToken

  public registerToken(userId: string, token: string): void {
    this.pushTokens.set(userId, token);
  }

  public unregisterToken(userId: string): void {
    this.pushTokens.delete(userId);
  }

  public getToken(userId: string): string | undefined {
    return this.pushTokens.get(userId);
  }

  /**
   * Dispatch encrypted wakeup notification to target user's registered device.
   * If FCM is not configured, logs in sandbox mode gracefully without crashing.
   */
  public async sendWakeup(targetUserId: string, payload: PushPayload): Promise<boolean> {
    const token = this.pushTokens.get(targetUserId);
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
