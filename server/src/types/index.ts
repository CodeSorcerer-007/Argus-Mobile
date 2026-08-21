export interface User {
  id: string;
  username: string;
  displayName: string;
  passwordHash?: string;
  salt?: string;
  recoveryKeyHash?: string;
  recoveryKeySalt?: string;
  phoneNumber?: string;
  phoneHash?: string;
  avatarUrl?: string;
  about?: string;
  identityKeyBase64: string;
  createdAt: number;
  lastSeen: number;
  isOnline: boolean;
}

export interface Device {
  id: string;
  userId: string;
  deviceName: string;
  platform: string;
  createdAt: number;
  lastActive: number;
}

export interface StoredPreKeyBundle {
  userId: string;
  deviceId: string;
  identityPublicKeyBase64: string;
  signedPreKeyId: number;
  signedPreKeyPublicBase64: string;
  signedPreKeySignatureBase64: string;
  oneTimePreKeys: { keyId: number; publicKeyBase64: string }[];
  updatedAt: number;
}

export interface EncryptedMessagePayload {
  id: string;
  conversationId: string;
  senderId: string;
  recipientId: string;
  dhPublicKeyBase64: string;
  sequenceNumber: number;
  previousChainLength: number;
  ivBase64: string;
  ciphertextBase64: string;
  senderIdentityPublicKeyBase64?: string;
  ephemeralPublicKeyBase64?: string;
  mediaUrl?: string;
  mediaType?: string;
  mediaSize?: number;
  replyToMessageId?: string;
  timestamp: number;
  expiresAt?: number;
  status: 'QUEUED' | 'SENT' | 'DELIVERED' | 'READ';
}

export interface Group {
  id: string;
  title: string;
  description?: string;
  avatarUrl?: string;
  createdBy: string;
  admins: string[];
  members: string[];
  createdAt: number;
  disappearingDurationSec?: number;
}

export interface CallSession {
  callId: string;
  callerId: string;
  receiverId: string;
  callType: 'VOICE' | 'VIDEO';
  status: 'RINGING' | 'ACCEPTED' | 'REJECTED' | 'ENDED';
  createdAt: number;
}

export type WsClientEvent =
  | { type: 'AUTH'; token: string; deviceId: string }
  | { type: 'SEND_MESSAGE'; payload: EncryptedMessagePayload }
  | { type: 'ACK_DELIVERED'; messageId: string; senderId: string }
  | { type: 'ACK_READ'; messageId: string; senderId: string }
  | { type: 'TYPING_START'; recipientId: string; conversationId: string }
  | { type: 'TYPING_STOP'; recipientId: string; conversationId: string }
  | { type: 'CALL_OFFER'; targetUserId: string; callId: string; callType: 'VOICE' | 'VIDEO'; sdp: any }
  | { type: 'CALL_ANSWER'; targetUserId: string; callId: string; sdp: any }
  | { type: 'ICE_CANDIDATE'; targetUserId: string; callId: string; candidate: any }
  | { type: 'CALL_END'; targetUserId: string; callId: string }
  | { type: 'CALL_REJECT'; targetUserId: string; callId: string }
  | { type: 'HEARTBEAT' };

export type WsServerEvent =
  | { type: 'AUTH_SUCCESS'; userId: string }
  | { type: 'AUTH_ERROR'; message: string }
  | { type: 'NEW_MESSAGE'; payload: EncryptedMessagePayload }
  | { type: 'MESSAGE_STATUS'; messageId: string; status: 'SENT' | 'DELIVERED' | 'READ' }
  | { type: 'TYPING'; userId: string; conversationId: string; isTyping: boolean }
  | { type: 'PRESENCE'; userId: string; isOnline: boolean; lastSeen: number }
  | { type: 'INCOMING_CALL'; callerId: string; callId: string; callType: 'VOICE' | 'VIDEO'; sdp: any }
  | { type: 'CALL_ANSWERED'; callId: string; sdp: any }
  | { type: 'REMOTE_ICE_CANDIDATE'; callId: string; candidate: any }
  | { type: 'CALL_TERMINATED'; callId: string; reason?: string }
  | { type: 'PONG' };
