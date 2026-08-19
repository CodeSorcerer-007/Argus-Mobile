import http from 'http';
import request from 'supertest';
import { WebSocket } from 'ws';
import { createApp } from '../src/server';
import { ArgusDatabase } from '../src/db/database';
import { ArgusWebSocketManager } from '../src/ws/wsManager';
import { EncryptedMessagePayload } from '../src/types';
import fs from 'fs';
import path from 'path';

describe('Argus Comprehensive Production Test Suite', () => {
  const TEST_JWT_SECRET = 'argus_test_jwt_secret_key_very_secure_and_long_2026_test';
  const testDataDir = path.resolve(__dirname, '../data_test_' + Date.now());
  const testUploadDir = path.resolve(__dirname, '../uploads_test_' + Date.now());

  let db: ArgusDatabase;
  let app: any;
  let server: http.Server;
  let wsManager: ArgusWebSocketManager;
  let wsPort: number;

  let aliceToken: string;
  let aliceRefreshToken: string;
  let aliceUserId: string;
  const alicePhone = '+15550001111';
  const aliceIdentityKey = 'AliceIdentityPublicKeyBase64SamplePayloadForArgus2026==';

  let bobToken: string;
  let bobUserId: string;
  const bobPhone = '+15550002222';
  const bobIdentityKey = 'BobIdentityPublicKeyBase64SamplePayloadForArgus2026====';

  beforeAll((done) => {
    process.env.JWT_SECRET = TEST_JWT_SECRET;
    db = new ArgusDatabase(testDataDir);
    app = createApp(db, TEST_JWT_SECRET);

    server = http.createServer(app);
    wsManager = new ArgusWebSocketManager(server, db, TEST_JWT_SECRET);

    server.listen(0, () => {
      const address = server.address() as any;
      wsPort = address.port;
      done();
    });
  });

  afterAll((done) => {
    wsManager.close();
    server.close(() => {
      db.destroy();
      if (fs.existsSync(testUploadDir)) {
        fs.rmSync(testUploadDir, { recursive: true, force: true });
      }
      done();
    });
  });

  // ===========================================================================
  // 1. SYSTEM HEALTH & METRICS
  // ===========================================================================
  describe('Health & Service Diagnostics', () => {
    test('GET /health returns 200 OK with gateway metadata', async () => {
      const res = await request(app).get('/health');
      expect(res.status).toBe(200);
      expect(res.body.status).toBe('ok');
      expect(res.body.service).toBe('Argus E2EE Gateway');
      expect(res.body.uptimeSec).toBeDefined();
    });

    test('Database initializes with schema v2 and verified integrity', () => {
      const info = db.verifyIntegrity();
      expect(info.isValid).toBe(true);
      expect(info.version).toBe(2);
      expect(db.schemaMetadata.engine).toBe('Argus-ZeroKnowledge-DB-v2');
    });
  });

  // ===========================================================================
  // 2. AUTHENTICATION, OTP SECURITY & BRUTE-FORCE DEFENSE
  // ===========================================================================
  describe('Authentication & Zero-Knowledge OTP', () => {
    test('POST /api/auth/request-otp rejects invalid phone format', async () => {
      const res = await request(app)
        .post('/api/auth/request-otp')
        .send({ phoneNumber: 'invalid_phone_123' });

      expect(res.status).toBe(400);
      expect(res.body.error).toContain('E.164');
    });

    test('POST /api/auth/request-otp generates OTP without leaking code in response', async () => {
      const res = await request(app)
        .post('/api/auth/request-otp')
        .send({ phoneNumber: alicePhone });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.code).toBeUndefined(); // Zero-knowledge: code never exposed in HTTP response
      expect(res.body.devCode).toBeUndefined();
      expect(db.otps.has(alicePhone)).toBe(true);
    });

    test('POST /api/auth/verify-otp enforces brute-force lockout after 5 failures', async () => {
      const targetPhone = '+15550009999';
      await request(app).post('/api/auth/request-otp').send({ phoneNumber: targetPhone });

      for (let i = 1; i <= 4; i++) {
        const failRes = await request(app)
          .post('/api/auth/verify-otp')
          .send({
            phoneNumber: targetPhone,
            code: '999999',
            identityKeyBase64: aliceIdentityKey
          });
        expect(failRes.status).toBe(400);
        expect(failRes.body.error).toContain('attempts remaining');
      }

      // 5th failed attempt should trigger 429 lockout
      const lockRes = await request(app)
        .post('/api/auth/verify-otp')
        .send({
          phoneNumber: targetPhone,
          code: '999999',
          identityKeyBase64: aliceIdentityKey
        });
      expect(lockRes.status).toBe(429);
      expect(lockRes.body.error).toContain('locked');
    });

    test('POST /api/auth/verify-otp successfully authenticates Alice', async () => {
      const otpRecord = db.otps.get(alicePhone);
      expect(otpRecord).toBeDefined();

      const res = await request(app)
        .post('/api/auth/verify-otp')
        .send({
          phoneNumber: alicePhone,
          code: otpRecord!.code,
          deviceName: 'Pixel 9 Pro',
          identityKeyBase64: aliceIdentityKey,
          displayName: 'Alice Security'
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.token).toBeDefined();
      expect(res.body.refreshToken).toBeDefined();
      expect(res.body.user.phoneNumber).toBe(alicePhone);

      aliceToken = res.body.token;
      aliceRefreshToken = res.body.refreshToken;
      aliceUserId = res.body.user.id;
    });

    test('POST /api/auth/verify-otp successfully authenticates Bob', async () => {
      await request(app).post('/api/auth/request-otp').send({ phoneNumber: bobPhone });
      const otpRecord = db.otps.get(bobPhone);

      const res = await request(app)
        .post('/api/auth/verify-otp')
        .send({
          phoneNumber: bobPhone,
          code: otpRecord!.code,
          deviceName: 'Galaxy S25 Ultra',
          identityKeyBase64: bobIdentityKey,
          displayName: 'Bob Crypto'
        });

      expect(res.status).toBe(200);
      bobToken = res.body.token;
      bobUserId = res.body.user.id;
    });

    test('POST /api/auth/refresh-token rotates access token and refresh token (RTR)', async () => {
      const res = await request(app)
        .post('/api/auth/refresh-token')
        .send({ refreshToken: aliceRefreshToken });

      expect(res.status).toBe(200);
      expect(res.body.token).toBeDefined();
      expect(res.body.refreshToken).toBeDefined();
      expect(res.body.refreshToken).not.toBe(aliceRefreshToken);

      const rotatedRefreshToken = res.body.refreshToken;
      aliceToken = res.body.token;

      // Old refresh token must now be revoked (replay attack defense)
      const replayRes = await request(app)
        .post('/api/auth/refresh-token')
        .send({ refreshToken: aliceRefreshToken });
      expect(replayRes.status).toBe(401);
      expect(replayRes.body.error).toContain('revoked');

      // Update to new active refresh token
      aliceRefreshToken = rotatedRefreshToken;
    });

    test('POST /api/auth/logout revokes refresh token', async () => {
      const logoutRes = await request(app)
        .post('/api/auth/logout')
        .send({ refreshToken: aliceRefreshToken });
      expect(logoutRes.status).toBe(200);

      // Attempting to refresh with revoked token must fail with 401
      const refreshRes = await request(app)
        .post('/api/auth/refresh-token')
        .send({ refreshToken: aliceRefreshToken });
      expect(refreshRes.status).toBe(401);
      expect(refreshRes.body.error).toContain('revoked');
    });
  });

  // ===========================================================================
  // 3. CRYPTOGRAPHIC PREKEY BUNDLE MANAGEMENT (X3DH)
  // ===========================================================================
  describe('PreKey Cryptographic Management', () => {
    test('POST /api/keys/publish-bundle publishes X3DH pre-keys for Bob', async () => {
      const res = await request(app)
        .post('/api/keys/publish-bundle')
        .set('Authorization', `Bearer ${bobToken}`)
        .send({
          identityPublicKeyBase64: bobIdentityKey,
          signedPreKeyId: 1,
          signedPreKeyPublicBase64: 'BobSignedPreKeyBase64MockPayload1234567890==',
          signedPreKeySignatureBase64: 'BobSignedPreKeySignatureBase64Mock1234567890==',
          oneTimePreKeys: [
            { keyId: 201, publicKeyBase64: 'BobOTPK_201_MockPayload===' },
            { keyId: 202, publicKeyBase64: 'BobOTPK_202_MockPayload===' }
          ]
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.availableOneTimeKeys).toBe(2);
    });

    test('GET /api/keys/status reports PreKey pool health', async () => {
      const res = await request(app)
        .get('/api/keys/status')
        .set('Authorization', `Bearer ${bobToken}`);

      expect(res.status).toBe(200);
      expect(res.body.availableOneTimeKeys).toBe(2);
      expect(res.body.needsReplenishment).toBe(true); // < 10 threshold
    });

    test('GET /api/keys/bundle/:targetUserId fetches bundle and consumes 1 OTP key for forward secrecy', async () => {
      const res = await request(app)
        .get(`/api/keys/bundle/${bobUserId}`)
        .set('Authorization', `Bearer ${bobToken}`);

      expect(res.status).toBe(200);
      expect(res.body.userId).toBe(bobUserId);
      expect(res.body.identityPublicKeyBase64).toBe(bobIdentityKey);
      expect(res.body.oneTimePreKeyId).toBe(201);

      // Fetch second time -> should consume key 202
      const res2 = await request(app)
        .get(`/api/keys/bundle/${bobUserId}`)
        .set('Authorization', `Bearer ${bobToken}`);

      expect(res2.status).toBe(200);
      expect(res2.body.oneTimePreKeyId).toBe(202);

      // Fetch third time -> no more OTP keys left
      const res3 = await request(app)
        .get(`/api/keys/bundle/${bobUserId}`)
        .set('Authorization', `Bearer ${bobToken}`);

      expect(res3.status).toBe(200);
      expect(res3.body.oneTimePreKeyId).toBeNull();
    });

    test('POST /api/keys/replenish replenishes one-time prekeys', async () => {
      const res = await request(app)
        .post('/api/keys/replenish')
        .set('Authorization', `Bearer ${bobToken}`)
        .send({
          oneTimePreKeys: [
            { keyId: 301, publicKeyBase64: 'BobOTPK_301_Replenished===' },
            { keyId: 302, publicKeyBase64: 'BobOTPK_302_Replenished===' },
            { keyId: 303, publicKeyBase64: 'BobOTPK_303_Replenished===' }
          ]
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.availableOneTimeKeys).toBe(3);

      // Consume replenished key
      const fetchRes = await request(app)
        .get(`/api/keys/bundle/${bobUserId}`)
        .set('Authorization', `Bearer ${bobToken}`);
      expect(fetchRes.status).toBe(200);
      expect(fetchRes.body.oneTimePreKeyId).toBe(301);
    });
  });

  // ===========================================================================
  // 4. USERS & PRIVACY CONTACT DISCOVERY
  // ===========================================================================
  describe('Users & Contact Discovery', () => {
    test('GET /api/users/me returns authenticated user profile', async () => {
      const res = await request(app)
        .get('/api/users/me')
        .set('Authorization', `Bearer ${aliceToken}`);

      expect(res.status).toBe(200);
      expect(res.body.user.phoneNumber).toBe(alicePhone);
    });

    test('PUT /api/users/me updates profile and prevents duplicate usernames', async () => {
      const updateRes = await request(app)
        .put('/api/users/me')
        .set('Authorization', `Bearer ${aliceToken}`)
        .send({
          displayName: 'Alice Wonder',
          username: 'alice_wonder',
          about: 'Encrypted communication only'
        });

      expect(updateRes.status).toBe(200);
      expect(updateRes.body.user.username).toBe('alice_wonder');

      // Bob trying to claim same username must receive 409 Conflict
      const conflictRes = await request(app)
        .put('/api/users/me')
        .set('Authorization', `Bearer ${bobToken}`)
        .send({ username: 'alice_wonder' });

      expect(conflictRes.status).toBe(409);
    });

    test('POST /api/users/discover-contacts matches salted phone hashes', async () => {
      const aliceHash = db.hashPhone(alicePhone);
      const bobHash = db.hashPhone(bobPhone);

      const res = await request(app)
        .post('/api/users/discover-contacts')
        .set('Authorization', `Bearer ${aliceToken}`)
        .send({ phoneHashes: [aliceHash, bobHash, 'unregistered_hash_123'] });

      expect(res.status).toBe(200);
      expect(res.body.contacts.length).toBe(2);
    });

    test('POST /api/users/push-token registers FCM device token', async () => {
      const res = await request(app)
        .post('/api/users/push-token')
        .set('Authorization', `Bearer ${aliceToken}`)
        .send({ token: 'fcm_sample_device_registration_token_123456' });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
    });
  });

  // ===========================================================================
  // 5. GROUP MANAGEMENT & AUTHORIZATION
  // ===========================================================================
  describe('Group Management', () => {
    let testGroupId: string;

    test('POST /api/groups/create creates a new encrypted group', async () => {
      const res = await request(app)
        .post('/api/groups/create')
        .set('Authorization', `Bearer ${aliceToken}`)
        .send({
          title: 'Cryptography Research Unit',
          description: 'Top-secret protocols',
          memberIds: [bobUserId]
        });

      expect(res.status).toBe(200);
      expect(res.body.group.title).toBe('Cryptography Research Unit');
      expect(res.body.group.admins).toContain(aliceUserId);
      expect(res.body.group.members).toContain(bobUserId);
      testGroupId = res.body.group.id;
    });

    test('POST /api/groups/:groupId/add-members enforces admin authorization', async () => {
      // Bob is a member but not admin -> 403 Forbidden
      const unauthRes = await request(app)
        .post(`/api/groups/${testGroupId}/add-members`)
        .set('Authorization', `Bearer ${bobToken}`)
        .send({ memberIds: ['new_member_id_999'] });

      expect(unauthRes.status).toBe(403);

      // Alice is admin -> 200 OK
      const adminRes = await request(app)
        .post(`/api/groups/${testGroupId}/add-members`)
        .set('Authorization', `Bearer ${aliceToken}`)
        .send({ memberIds: ['new_member_id_999'] });

      expect(adminRes.status).toBe(200);
      expect(adminRes.body.group.members).toContain('new_member_id_999');
    });
  });

  // ===========================================================================
  // 6. ENCRYPTED MEDIA STORAGE & STREAMING
  // ===========================================================================
  describe('Encrypted Media Storage & Streaming', () => {
    let uploadedFileUrl: string;

    test('POST /api/media/upload rejects unauthenticated requests', async () => {
      const res = await request(app)
        .post('/api/media/upload')
        .attach('file', Buffer.from('encrypted ciphertext blob'), 'payload.enc');

      expect(res.status).toBe(401);
    });

    test('POST /api/media/upload uploads authenticated encrypted payload', async () => {
      const res = await request(app)
        .post('/api/media/upload')
        .set('Authorization', `Bearer ${aliceToken}`)
        .attach('file', Buffer.from('Sample AES-256-GCM encrypted media data stream'), 'vault_secret.enc');

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.fileUrl).toBeDefined();
      uploadedFileUrl = res.body.fileUrl;
    });

    test('GET /api/media/download/:filename downloads media with chunked range streaming', async () => {
      const resFull = await request(app).get(uploadedFileUrl);
      expect(resFull.status).toBe(200);
      expect(resFull.headers['x-content-type-options']).toBe('nosniff');

      // Range request (resumable chunks)
      const resRange = await request(app)
        .get(uploadedFileUrl)
        .set('Range', 'bytes=0-10');

      expect(resRange.status).toBe(206);
      expect(resRange.headers['content-range']).toBeDefined();
    });

    test('GET /api/media/download/:filename defends against path traversal', async () => {
      const res = await request(app).get('/api/media/download/..%2F..%2Fpackage.json');
      expect([403, 404]).toContain(res.status);
    });
  });

  // ===========================================================================
  // 7. WEBRTC STUN/TURN CONFIGURATION
  // ===========================================================================
  describe('WebRTC ICE Traversal', () => {
    test('GET /api/calls/ice-servers returns STUN/TURN configurations', async () => {
      const res = await request(app)
        .get('/api/calls/ice-servers')
        .set('Authorization', `Bearer ${aliceToken}`);

      expect(res.status).toBe(200);
      expect(Array.isArray(res.body.iceServers)).toBe(true);
      expect(res.body.iceServers.length).toBeGreaterThan(0);
    });
  });

  // ===========================================================================
  // 8. REAL-TIME WEBSOCKET ROUTING & E2EE SIGNALING
  // ===========================================================================
  describe('Real-Time WebSocket Router & E2EE Relay', () => {
    let aliceWs: WebSocket;
    let bobWs: WebSocket;

    afterEach(() => {
      if (aliceWs && aliceWs.readyState === WebSocket.OPEN) aliceWs.close();
      if (bobWs && bobWs.readyState === WebSocket.OPEN) bobWs.close();
    });

    test('WebSocket connection rejects unauthenticated socket', (done) => {
      const ws = new WebSocket(`ws://localhost:${wsPort}/ws`);
      ws.on('open', () => {
        ws.send(JSON.stringify({ type: 'HEARTBEAT' }));
      });
      ws.on('message', (data) => {
        const msg = JSON.parse(data.toString());
        if (msg.type === 'AUTH_ERROR') {
          ws.close();
          done();
        }
      });
    });

    test('WebSocket authenticates and relays real-time encrypted messages between users', (done) => {
      aliceWs = new WebSocket(`ws://localhost:${wsPort}/ws`);
      bobWs = new WebSocket(`ws://localhost:${wsPort}/ws`);

      let aliceAuthed = false;
      let bobAuthed = false;

      const testPayload: EncryptedMessagePayload = {
        id: 'msg_test_001',
        conversationId: 'conv_alice_bob',
        senderId: aliceUserId,
        recipientId: bobUserId,
        dhPublicKeyBase64: 'mockDhRatchetKey==',
        sequenceNumber: 0,
        previousChainLength: 0,
        ivBase64: 'mockIv==',
        ciphertextBase64: 'mockCiphertext==',
        timestamp: Date.now(),
        status: 'SENT'
      };

      aliceWs.on('open', () => {
        aliceWs.send(JSON.stringify({ type: 'AUTH', token: aliceToken, deviceId: 'pixel9' }));
      });

      bobWs.on('open', () => {
        bobWs.send(JSON.stringify({ type: 'AUTH', token: bobToken, deviceId: 'galaxy25' }));
      });

      aliceWs.on('message', (data) => {
        const event = JSON.parse(data.toString());
        if (event.type === 'AUTH_SUCCESS') {
          aliceAuthed = true;
          checkReady();
        }
        if (event.type === 'MESSAGE_STATUS' && event.status === 'DELIVERED') {
          // Alice received delivery confirmation
          done();
        }
      });

      bobWs.on('message', (data) => {
        const event = JSON.parse(data.toString());
        if (event.type === 'AUTH_SUCCESS') {
          bobAuthed = true;
          checkReady();
        }
        if (event.type === 'NEW_MESSAGE') {
          expect(event.payload.id).toBe('msg_test_001');
          expect(event.payload.ciphertextBase64).toBe('mockCiphertext==');
          // Bob sends delivered ACK
          bobWs.send(JSON.stringify({
            type: 'ACK_DELIVERED',
            messageId: event.payload.id,
            senderId: aliceUserId
          }));
        }
      });

      function checkReady() {
        if (aliceAuthed && bobAuthed) {
          // Alice sends message to Bob
          aliceWs.send(JSON.stringify({
            type: 'SEND_MESSAGE',
            payload: testPayload
          }));
        }
      }
    });

    test('WebSocket buffers offline messages and delivers upon reconnection', (done) => {
      aliceWs = new WebSocket(`ws://localhost:${wsPort}/ws`);

      const offlinePayload: EncryptedMessagePayload = {
        id: 'msg_offline_999',
        conversationId: 'conv_alice_bob',
        senderId: aliceUserId,
        recipientId: bobUserId,
        dhPublicKeyBase64: 'mockDhOfflineKey==',
        sequenceNumber: 1,
        previousChainLength: 0,
        ivBase64: 'mockIv==',
        ciphertextBase64: 'mockOfflineCiphertext==',
        timestamp: Date.now(),
        status: 'SENT'
      };

      aliceWs.on('open', () => {
        aliceWs.send(JSON.stringify({ type: 'AUTH', token: aliceToken, deviceId: 'pixel9' }));
      });

      aliceWs.on('message', (data) => {
        const event = JSON.parse(data.toString());
        if (event.type === 'AUTH_SUCCESS') {
          // Bob is offline; Alice sends message
          aliceWs.send(JSON.stringify({
            type: 'SEND_MESSAGE',
            payload: offlinePayload
          }));
        }
        if (event.type === 'MESSAGE_STATUS' && event.status === 'SENT') {
          // Offline message acknowledged as buffered
          connectBob();
        }
      });

      function connectBob() {
        bobWs = new WebSocket(`ws://localhost:${wsPort}/ws`);
        bobWs.on('open', () => {
          bobWs.send(JSON.stringify({ type: 'AUTH', token: bobToken, deviceId: 'galaxy25' }));
        });
        bobWs.on('message', (data) => {
          const event = JSON.parse(data.toString());
          if (event.type === 'NEW_MESSAGE' && event.payload.id === 'msg_offline_999') {
            // Bob successfully received buffered offline message
            expect(event.payload.ciphertextBase64).toBe('mockOfflineCiphertext==');
            done();
          }
        });
      }
    });

    test('WebSocket relays typing indicators and WebRTC call signaling', (done) => {
      aliceWs = new WebSocket(`ws://localhost:${wsPort}/ws`);
      bobWs = new WebSocket(`ws://localhost:${wsPort}/ws`);

      let aliceAuthed = false;
      let bobAuthed = false;

      aliceWs.on('open', () => {
        aliceWs.send(JSON.stringify({ type: 'AUTH', token: aliceToken, deviceId: 'pixel9' }));
      });
      bobWs.on('open', () => {
        bobWs.send(JSON.stringify({ type: 'AUTH', token: bobToken, deviceId: 'galaxy25' }));
      });

      aliceWs.on('message', (data) => {
        const event = JSON.parse(data.toString());
        if (event.type === 'AUTH_SUCCESS') {
          aliceAuthed = true;
          checkReady();
        }
      });

      bobWs.on('message', (data) => {
        const event = JSON.parse(data.toString());
        if (event.type === 'AUTH_SUCCESS') {
          bobAuthed = true;
          checkReady();
        }
        if (event.type === 'TYPING') {
          expect(event.userId).toBe(aliceUserId);
          expect(event.isTyping).toBe(true);

          // Test WebRTC call offer relay
          aliceWs.send(JSON.stringify({
            type: 'CALL_OFFER',
            targetUserId: bobUserId,
            callId: 'call_test_777',
            callType: 'VIDEO',
            sdp: { type: 'offer', sdp: 'v=0...' }
          }));
        }
        if (event.type === 'INCOMING_CALL') {
          expect(event.callId).toBe('call_test_777');
          expect(event.callerId).toBe(aliceUserId);
          done();
        }
      });

      function checkReady() {
        if (aliceAuthed && bobAuthed) {
          aliceWs.send(JSON.stringify({
            type: 'TYPING_START',
            recipientId: bobUserId,
            conversationId: 'conv_alice_bob'
          }));
        }
      }
    });
  });
});
