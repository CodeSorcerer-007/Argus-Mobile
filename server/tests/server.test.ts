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
  let aliceRecoveryKey: string;
  const aliceUsername = 'alicesecurity';
  const alicePassword = 'SecurePassword123!';
  const aliceIdentityKey = 'AliceIdentityPublicKeyBase64SamplePayloadForArgus2026==';

  let bobToken: string;
  let bobUserId: string;
  const bobUsername = 'bobcrypto';
  const bobPassword = 'SecurePassword456!';
  const bobIdentityKey = 'BobIdentityPublicKeyBase64SamplePayloadForArgus2026====';

  let charlieToken: string;
  let charlieUserId: string;
  const charlieUsername = 'charliecrypto';
  const charliePassword = 'SecurePassword789!';
  const charlieIdentityKey = 'CharlieIdentityPublicKeyBase64SamplePayloadForArgus2026==';

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
      done();
    });
  });

  // ===========================================================================
  // 1. HEALTH & METADATA
  // ===========================================================================
  describe('Health & Service Diagnostics', () => {
    test('GET /health returns 200 OK with gateway metadata', async () => {
      const res = await request(app).get('/health');
      expect(res.status).toBe(200);
      expect(res.body.status).toBe('ok');
      expect(res.body.version).toBe('2.4.0');
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
  // 2. USER ID & PASSWORD AUTHENTICATION & SECURITY
  // ===========================================================================
  describe('User ID & Password Authentication', () => {
    test('GET /api/auth/check-username/:username checks availability', async () => {
      const availableRes = await request(app).get('/api/auth/check-username/newhandle');
      expect(availableRes.status).toBe(200);
      expect(availableRes.body.available).toBe(true);
      expect(availableRes.body.username).toBe('newhandle');

      const invalidRes = await request(app).get('/api/auth/check-username/a');
      expect(invalidRes.status).toBe(400);
    });

    test('POST /api/auth/register creates user account and issues tokens', async () => {
      const res = await request(app)
        .post('/api/auth/register')
        .send({
          username: aliceUsername,
          password: alicePassword,
          displayName: 'Alice Security',
          identityKeyBase64: aliceIdentityKey,
          deviceName: 'Pixel 9 Pro'
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.token).toBeDefined();
      expect(res.body.refreshToken).toBeDefined();
      expect(res.body.recoveryKey).toBeDefined();
      expect(res.body.recoveryKey).toMatch(/^ARGUS-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}$/);
      expect(res.body.user.username).toBe(aliceUsername);
      expect(res.body.user.displayName).toBe('Alice Security');
      expect(res.body.user.passwordHash).toBeUndefined(); // Security: passwordHash must NEVER be returned

      aliceToken = res.body.token;
      aliceRefreshToken = res.body.refreshToken;
      aliceUserId = res.body.user.id;
      aliceRecoveryKey = res.body.recoveryKey;
    });

    test('POST /api/auth/register rejects duplicate username', async () => {
      const res = await request(app)
        .post('/api/auth/register')
        .send({
          username: aliceUsername.toUpperCase(), // case-insensitive check
          password: 'AnotherPassword123!',
          displayName: 'Alice Duplicate',
          identityKeyBase64: aliceIdentityKey
        });

      expect(res.status).toBe(400);
      expect(res.body.error).toContain('already taken');
    });

    test('POST /api/auth/register creates Bob and Charlie accounts', async () => {
      // Bob
      const bobRes = await request(app)
        .post('/api/auth/register')
        .send({
          username: bobUsername,
          password: bobPassword,
          displayName: 'Bob Crypto',
          identityKeyBase64: bobIdentityKey,
          deviceName: 'Galaxy S25 Ultra'
        });

      expect(bobRes.status).toBe(201);
      expect(bobRes.body.success).toBe(true);
      bobToken = bobRes.body.token;
      bobUserId = bobRes.body.user.id;

      // Charlie
      const charlieRes = await request(app)
        .post('/api/auth/register')
        .send({
          username: charlieUsername,
          password: charliePassword,
          displayName: 'Charlie Crypto',
          identityKeyBase64: charlieIdentityKey,
          deviceName: 'OnePlus 13'
        });

      expect(charlieRes.status).toBe(201);
      expect(charlieRes.body.success).toBe(true);
      charlieToken = charlieRes.body.token;
      charlieUserId = charlieRes.body.user.id;
    });

    test('POST /api/auth/login enforces brute-force lockout after 5 incorrect password attempts', async () => {
      for (let i = 0; i < 4; i++) {
        const failRes = await request(app)
          .post('/api/auth/login')
          .send({
            username: aliceUsername,
            password: 'WrongPassword999!'
          });
        expect(failRes.status).toBe(401);
        expect(failRes.body.error).toContain('remaining');
      }

      // 5th failed attempt should trigger 429 lockout
      const lockRes = await request(app)
        .post('/api/auth/login')
        .send({
          username: aliceUsername,
          password: 'WrongPassword999!'
        });
      expect(lockRes.status).toBe(429);
      expect(lockRes.body.error).toContain('locked');

      // Clear lockout for remaining tests
      db.failedPasswordAttempts.delete(aliceUsername);
    });

    test('POST /api/auth/login successfully logs in Alice with correct password', async () => {
      const res = await request(app)
        .post('/api/auth/login')
        .send({
          username: aliceUsername,
          password: alicePassword
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.token).toBeDefined();
      expect(res.body.user.username).toBe(aliceUsername);
      expect(res.body.user.passwordHash).toBeUndefined();

      aliceToken = res.body.token;
      aliceRefreshToken = res.body.refreshToken;
    });

    test('Device sessions per user are bounded (BUG-3 fixed)', async () => {
      for (let i = 1; i <= 6; i++) {
        await request(app)
          .post('/api/auth/login')
          .send({
            username: aliceUsername,
            password: alicePassword,
            deviceName: `Test Device ${i}`,
            platform: 'android'
          });
      }

      const aliceDevices = Array.from(db.devices.values()).filter(d => d.userId === aliceUserId);
      expect(aliceDevices.length).toBeLessThanOrEqual(5);
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

    test('POST /api/auth/verify-recovery-key validates emergency recovery key', async () => {
      // 1. Rejects invalid key
      const badRes = await request(app)
        .post('/api/auth/verify-recovery-key')
        .send({
          username: aliceUsername,
          recoveryKey: 'ARGUS-9999-9999-9999-9999'
        });
      expect(badRes.status).toBe(400);
      expect(badRes.body.error).toContain('Invalid recovery key');

      // 2. Accepts valid key
      const goodRes = await request(app)
        .post('/api/auth/verify-recovery-key')
        .send({
          username: aliceUsername,
          recoveryKey: aliceRecoveryKey
        });
      expect(goodRes.status).toBe(200);
      expect(goodRes.body.valid).toBe(true);
    });

    test('POST /api/auth/reset-password rejects requests without recovery key (Security Protection)', async () => {
      const exploitRes = await request(app)
        .post('/api/auth/reset-password')
        .send({
          username: aliceUsername,
          newPassword: 'AttackerNewPassword123!'
        });

      expect(exploitRes.status).toBe(400);
      expect(exploitRes.body.error).toContain('recovery key is required');
    });

    test('POST /api/auth/reset-password rejects requests with wrong recovery key', async () => {
      const wrongKeyRes = await request(app)
        .post('/api/auth/reset-password')
        .send({
          username: aliceUsername,
          newPassword: 'AttackerNewPassword123!',
          recoveryKey: 'ARGUS-0000-0000-0000-0000'
        });

      expect(wrongKeyRes.status).toBe(400);
      expect(wrongKeyRes.body.error).toContain('Invalid recovery key');
    });

    test('POST /api/auth/reset-password strictly rejects when recovery key hash is missing (BUG-4 fixed)', async () => {
      const alice = db.findUserByUsername(aliceUsername)!;
      const savedHash = alice.recoveryKeyHash;
      alice.recoveryKeyHash = undefined;

      const bypassRes = await request(app)
        .post('/api/auth/reset-password')
        .send({
          username: aliceUsername,
          newPassword: 'AttackerNewPassword123!',
          recoveryKey: 'ARGUS-1111-2222-3333-4444'
        });

      expect(bypassRes.status).toBe(403);
      expect(bypassRes.body.error).toContain('not configured');

      alice.recoveryKeyHash = savedHash; // restore
    });

    test('POST /api/auth/reset-password resets password and issues fresh tokens & recovery key with valid recovery key', async () => {
      const newPassword = 'BrandNewSuperPassword999!';
      const res = await request(app)
        .post('/api/auth/reset-password')
        .send({
          username: aliceUsername,
          newPassword,
          recoveryKey: aliceRecoveryKey
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.recoveryKey).toBeDefined();
      expect(res.body.recoveryKey).not.toBe(aliceRecoveryKey); // New emergency recovery key issued

      aliceToken = res.body.token;
      aliceRefreshToken = res.body.refreshToken;
      aliceRecoveryKey = res.body.recoveryKey;

      // Verify login with new password works
      const loginRes = await request(app)
        .post('/api/auth/login')
        .send({
          username: aliceUsername,
          password: newPassword
        });
      expect(loginRes.status).toBe(200);
      aliceToken = loginRes.body.token;
      aliceRefreshToken = loginRes.body.refreshToken;
    });
  });

  // ===========================================================================
  // 3. PREKEY CRYPTOGRAPHIC MANAGEMENT
  // ===========================================================================
  describe('PreKey Cryptographic Management', () => {
    test('POST /api/keys/publish-bundle publishes X3DH pre-keys for Bob', async () => {
      const res = await request(app)
        .post('/api/keys/publish-bundle')
        .set('Authorization', `Bearer ${bobToken}`)
        .send({
          identityPublicKeyBase64: bobIdentityKey,
          signedPreKeyId: 1,
          signedPreKeyPublicBase64: 'BobSignedPreKeyPublicBase64Mock1234567890==',
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
      // Fetch first time -> consumes OTP key 201
      const res1 = await request(app)
        .get(`/api/keys/bundle/${bobUserId}`)
        .set('Authorization', `Bearer ${aliceToken}`);

      expect(res1.status).toBe(200);
      expect(res1.body.userId).toBe(bobUserId);
      expect(res1.body.oneTimePreKeyId).toBe(201);
      expect(res1.body.remainingOneTimeKeys).toBe(1);

      // Fetch second time -> consumes OTP key 202
      const res2 = await request(app)
        .get(`/api/keys/bundle/${bobUserId}`)
        .set('Authorization', `Bearer ${aliceToken}`);

      expect(res2.status).toBe(200);
      expect(res2.body.oneTimePreKeyId).toBe(202);
      expect(res2.body.remainingOneTimeKeys).toBe(0);

      // Fetch third time -> no more OTP keys left
      const res3 = await request(app)
        .get(`/api/keys/bundle/${bobUserId}`)
        .set('Authorization', `Bearer ${aliceToken}`);

      expect(res3.status).toBe(200);
      expect(res3.body.oneTimePreKeyId).toBeNull();
      expect(res3.body.remainingOneTimeKeys).toBe(0);
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
      expect(res.body.availableOneTimeKeys).toBe(3);
    });

    test('POST /api/keys/bundles retrieves PreKey bundles in batch', async () => {
      const res = await request(app)
        .post('/api/keys/bundles')
        .set('Authorization', `Bearer ${aliceToken}`)
        .send({ userIds: [bobUserId] });

      expect(res.status).toBe(200);
      expect(res.body.bundles[bobUserId]).toBeDefined();
      expect(res.body.bundles[bobUserId].userId).toBe(bobUserId);
    });
  });

  // ===========================================================================
  // 4. USERS & PRIVACY CONTACT DISCOVERY
  // ===========================================================================
  describe('Users & Contact Discovery', () => {
    test('GET /api/users/me returns authenticated user profile without leaking credential hashes (BUG-6 fixed)', async () => {
      const res = await request(app)
        .get('/api/users/me')
        .set('Authorization', `Bearer ${aliceToken}`);

      expect(res.status).toBe(200);
      expect(res.body.user.username).toBe(aliceUsername);
      expect(res.body.user.displayName).toBe('Alice Security');
      expect(res.body.user.passwordHash).toBeUndefined();
      expect(res.body.user.salt).toBeUndefined();
      expect(res.body.user.recoveryKeyHash).toBeUndefined();
      expect(res.body.user.recoveryKeySalt).toBeUndefined();
    });

    test('GET /api/users/:userId returns public profile of target user', async () => {
      const res = await request(app)
        .get(`/api/users/${bobUserId}`)
        .set('Authorization', `Bearer ${aliceToken}`);

      expect(res.status).toBe(200);
      expect(res.body.user.id).toBe(bobUserId);
      expect(res.body.user.username).toBe(bobUsername);
      expect(res.body.user.passwordHash).toBeUndefined(); // Security: passwordHash must NEVER be exposed
    });

    test('GET /api/users/search finds users by username handle', async () => {
      const res = await request(app)
        .get('/api/users/search?q=bob')
        .set('Authorization', `Bearer ${aliceToken}`);

      expect(res.status).toBe(200);
      expect(res.body.results.length).toBeGreaterThanOrEqual(1);
      expect(res.body.results[0].username).toBe(bobUsername);
      expect(res.body.results[0].passwordHash).toBeUndefined();
    });

    test('PUT /api/users/me updates profile, allows dot in username, and prevents duplicates (BUG-7 & BUG-10 fixed)', async () => {
      const updateRes = await request(app)
        .put('/api/users/me')
        .set('Authorization', `Bearer ${aliceToken}`)
        .send({
          displayName: 'Alice Wonder',
          username: 'alice.wonder',
          about: 'Encrypted communication only'
        });

      expect(updateRes.status).toBe(200);
      expect(updateRes.body.user.username).toBe('alice.wonder');
      expect(updateRes.body.user.passwordHash).toBeUndefined();

      // Bob trying to claim same username must receive 409 Conflict
      const conflictRes = await request(app)
        .put('/api/users/me')
        .set('Authorization', `Bearer ${bobToken}`)
        .send({ username: 'alice.wonder' });

      expect(conflictRes.status).toBe(409);
    });

    test('POST /api/users/push-token registers FCM device token and persists it (BUG-12 fixed)', async () => {
      const res = await request(app)
        .post('/api/users/push-token')
        .set('Authorization', `Bearer ${aliceToken}`)
        .send({ token: 'fcm_sample_device_registration_token_123456' });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);

      // Force save and verify persistence across reload
      db.save();
      const freshDb = new ArgusDatabase(testDataDir);
      expect(freshDb.pushTokens.get(aliceUserId)).toBe('fcm_sample_device_registration_token_123456');
    });

    test('Revoked tokens TTL pruning cleans expired entries (BUG-2 fixed)', () => {
      const expiredToken = 'expired_revoked_token_1234';
      const recentToken = 'recent_revoked_token_5678';
      const oldTime = Date.now() - (95 * 24 * 60 * 60 * 1000); // 95 days ago

      db.revokedTokens.set(expiredToken, oldTime);
      db.revokedTokens.set(recentToken, Date.now());

      db.pruneExpiredRecords();

      expect(db.revokedTokens.has(expiredToken)).toBe(false);
      expect(db.revokedTokens.has(recentToken)).toBe(true);
    });

    test('DELETE /api/users/me permanently deletes user account and revokes active access (Google Play compliance)', async () => {
      // 1. Register temporary user
      const regRes = await request(app)
        .post('/api/auth/register')
        .send({
          username: 'temporary_user_for_delete',
          password: 'TempPassword123!',
          displayName: 'Temp Delete User',
          identityKeyBase64: 'TempIdentityKeyBase64SamplePayload1234567890===='
        });
      expect(regRes.status).toBe(201);
      const tempToken = regRes.body.token;
      const tempUserId = regRes.body.user.id;

      // 2. Publish a prekey for temp user
      await request(app)
        .post('/api/keys/publish-bundle')
        .set('Authorization', `Bearer ${tempToken}`)
        .send({
          identityPublicKeyBase64: 'TempIdentityKeyBase64SamplePayload1234567890====',
          signedPreKeyId: 1,
          signedPreKeyPublicBase64: 'TempSignedPreKeyPublicBase64Mock1234567890==',
          signedPreKeySignatureBase64: 'TempSignedPreKeySignatureBase64Mock1234567890==',
          oneTimePreKeys: [{ keyId: 101, publicKeyBase64: 'TempOTPK_101===' }]
        });

      // 3. Delete account
      const deleteRes = await request(app)
        .delete('/api/users/me')
        .set('Authorization', `Bearer ${tempToken}`);
      expect(deleteRes.status).toBe(200);
      expect(deleteRes.body.success).toBe(true);

      // 4. Stale token must now be rejected
      const staleRes = await request(app)
        .get('/api/users/me')
        .set('Authorization', `Bearer ${tempToken}`);
      expect(staleRes.status).toBe(401);

      // 5. Lookups for deleted user must return 404
      const lookupRes = await request(app)
        .get(`/api/users/${tempUserId}`)
        .set('Authorization', `Bearer ${aliceToken}`);
      expect(lookupRes.status).toBe(404);
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

      expect(res.status).toBe(201);
      expect(res.body.group.title).toBe('Cryptography Research Unit');
      expect(res.body.group.admins).toContain(aliceUserId);
      expect(res.body.group.members).toContain(bobUserId);
      testGroupId = res.body.group.id;
    });

    test('GET /api/groups/:groupId returns group details and member profiles', async () => {
      const res = await request(app)
        .get(`/api/groups/${testGroupId}`)
        .set('Authorization', `Bearer ${bobToken}`);

      expect(res.status).toBe(200);
      expect(res.body.group.id).toBe(testGroupId);
      expect(res.body.memberProfiles.length).toBeGreaterThanOrEqual(2);
    });

    test('PUT /api/groups/:groupId updates group settings (Admin only)', async () => {
      // Bob (member) cannot update group settings
      const unauthRes = await request(app)
        .put(`/api/groups/${testGroupId}`)
        .set('Authorization', `Bearer ${bobToken}`)
        .send({ title: 'Hacked Title' });
      expect(unauthRes.status).toBe(403);

      // Alice (admin) updates group settings
      const adminRes = await request(app)
        .put(`/api/groups/${testGroupId}`)
        .set('Authorization', `Bearer ${aliceToken}`)
        .send({ title: 'Argus Core Crypto Unit', disappearingDurationSec: 86400 });
      expect(adminRes.status).toBe(200);
      expect(adminRes.body.group.title).toBe('Argus Core Crypto Unit');
      expect(adminRes.body.group.disappearingDurationSec).toBe(86400);
    });

    test('POST /api/groups/:groupId/add-members enforces admin authorization and member existence (BUG-8 fixed)', async () => {
      // 1. Bob is a member but not admin -> 403 Forbidden
      const unauthRes = await request(app)
        .post(`/api/groups/${testGroupId}/add-members`)
        .set('Authorization', `Bearer ${bobToken}`)
        .send({ memberIds: [charlieUserId] });
      expect(unauthRes.status).toBe(403);

      // 2. Alice (admin) adding non-existent user -> 400 Bad Request
      const fakeUserRes = await request(app)
        .post(`/api/groups/${testGroupId}/add-members`)
        .set('Authorization', `Bearer ${aliceToken}`)
        .send({ memberIds: ['non_existent_fake_user_id_999'] });
      expect(fakeUserRes.status).toBe(400);

      // 3. Alice (admin) adding valid user Charlie -> 200 OK
      const adminRes = await request(app)
        .post(`/api/groups/${testGroupId}/add-members`)
        .set('Authorization', `Bearer ${aliceToken}`)
        .send({ memberIds: [charlieUserId] });

      expect(adminRes.status).toBe(200);
      expect(adminRes.body.group.members).toContain(charlieUserId);
    });

    test('POST /api/groups/:groupId/remove-member removes a member (Admin only)', async () => {
      const res = await request(app)
        .post(`/api/groups/${testGroupId}/remove-member`)
        .set('Authorization', `Bearer ${aliceToken}`)
        .send({ memberId: charlieUserId });

      expect(res.status).toBe(200);
      expect(res.body.group.members).not.toContain(charlieUserId);
    });

    test('POST /api/groups/:groupId/leave allows member to leave group', async () => {
      const res = await request(app)
        .post(`/api/groups/${testGroupId}/leave`)
        .set('Authorization', `Bearer ${bobToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);

      const group = db.groups.get(testGroupId);
      expect(group?.members).not.toContain(bobUserId);
    });

    test('DELETE /api/groups/:groupId deletes the group (Admin only)', async () => {
      const res = await request(app)
        .delete(`/api/groups/${testGroupId}`)
        .set('Authorization', `Bearer ${aliceToken}`);

      expect(res.status).toBe(200);
      expect(db.groups.get(testGroupId)).toBeUndefined();
    });
  });

  // ===========================================================================
  // 6. ENCRYPTED MEDIA STORAGE & STREAMING
  // ===========================================================================
  describe('Encrypted Media Storage & Streaming', () => {
    let uploadedFileUrl: string;
    let uploadedFilename: string;

    test('POST /api/media/upload rejects unauthenticated requests', async () => {
      const fakeBlob = Buffer.from('EncryptedMediaMockBinaryPayloadData2026');
      const res = await request(app)
        .post('/api/media/upload')
        .attach('file', fakeBlob, 'secret_document.enc');

      expect(res.status).toBe(401);
    });

    test('POST /api/media/upload uploads authenticated encrypted payload', async () => {
      const fakeBlob = Buffer.from('EncryptedMediaMockBinaryPayloadData2026');
      const res = await request(app)
        .post('/api/media/upload')
        .set('Authorization', `Bearer ${aliceToken}`)
        .attach('file', fakeBlob, 'secret_document.enc');

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.fileUrl).toBeDefined();
      expect(res.body.fileId).toBeDefined();

      uploadedFileUrl = res.body.fileUrl;
      uploadedFilename = res.body.fileId;
    });

    test('GET /api/media/download/:filename downloads media with chunked range streaming', async () => {
      // 1. Full download
      const fullRes = await request(app).get(`/api/media/download/${uploadedFilename}`);
      expect(fullRes.status).toBe(200);
      expect(fullRes.headers['content-type']).toBe('application/octet-stream');

      // 2. HTTP 206 Partial Content Range Stream
      const rangeRes = await request(app)
        .get(`/api/media/download/${uploadedFilename}`)
        .set('Range', 'bytes=0-10');

      expect(rangeRes.status).toBe(206);
      expect(rangeRes.headers['content-range']).toContain('bytes 0-10/');
    });

    test('GET /api/media/download/:filename defends against path traversal', async () => {
      const traversalRes = await request(app).get('/api/media/download/../../package.json');
      expect([403, 404]).toContain(traversalRes.status);
    });
  });

  // ===========================================================================
  // 7. WEBRTC ICE TRAVERSAL
  // ===========================================================================
  describe('WebRTC ICE Traversal', () => {
    test('GET /api/calls/ice-servers returns STUN/TURN configurations', async () => {
      const res = await request(app)
        .get('/api/calls/ice-servers')
        .set('Authorization', `Bearer ${aliceToken}`);

      expect(res.status).toBe(200);
      expect(res.body.iceServers).toBeDefined();
      expect(res.body.iceServers.length).toBeGreaterThanOrEqual(1);
      expect(res.body.ttlSec).toBe(86400);
    });
  });

  // ===========================================================================
  // 8. REAL-TIME WEBSOCKET ROUTER & E2EE RELAY
  // ===========================================================================
  describe('Real-Time WebSocket Router & E2EE Relay', () => {
    let aliceWs: WebSocket;
    let bobWs: WebSocket;

    afterEach(() => {
      if (aliceWs && aliceWs.readyState === WebSocket.OPEN) aliceWs.close();
      if (bobWs && bobWs.readyState === WebSocket.OPEN) bobWs.close();
    });

    test('WebSocket connection rejects unauthenticated socket', (done) => {
      const unauthWs = new WebSocket(`ws://localhost:${wsPort}/ws`);
      unauthWs.on('open', () => {
        unauthWs.send(JSON.stringify({ type: 'SEND_MESSAGE', payload: {} }));
      });
      unauthWs.on('message', (data) => {
        const event = JSON.parse(data.toString());
        if (event.type === 'AUTH_ERROR') {
          unauthWs.close();
          done();
        }
      });
    });

    test('WebSocket authenticates and relays real-time encrypted messages between users', (done) => {
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
          checkReadyAndSend();
        }
        if (event.type === 'MESSAGE_STATUS') {
          expect(event.messageId).toBe('msg_test_001');
          expect(['SENT', 'DELIVERED']).toContain(event.status);
        }
      });

      bobWs.on('message', (data) => {
        const event = JSON.parse(data.toString());
        if (event.type === 'AUTH_SUCCESS') {
          bobAuthed = true;
          checkReadyAndSend();
        }
        if (event.type === 'NEW_MESSAGE') {
          expect(event.payload.id).toBe('msg_test_001');
          expect(event.payload.ciphertextBase64).toBe('mockEncryptedPayload==');
          done();
        }
      });

      function checkReadyAndSend() {
        if (aliceAuthed && bobAuthed) {
          const testMessage: EncryptedMessagePayload = {
            id: 'msg_test_001',
            conversationId: 'conv_alice_bob',
            senderId: aliceUserId,
            recipientId: bobUserId,
            dhPublicKeyBase64: 'AliceDhPubMock==',
            sequenceNumber: 0,
            previousChainLength: 0,
            ivBase64: 'MockIV==',
            ciphertextBase64: 'mockEncryptedPayload==',
            timestamp: Date.now(),
            status: 'QUEUED'
          };
          aliceWs.send(JSON.stringify({ type: 'SEND_MESSAGE', payload: testMessage }));
        }
      }
    });

    test('WebSocket buffers offline messages and delivers upon reconnection', (done) => {
      aliceWs = new WebSocket(`ws://localhost:${wsPort}/ws`);

      aliceWs.on('open', () => {
        aliceWs.send(JSON.stringify({ type: 'AUTH', token: aliceToken, deviceId: 'pixel9' }));
      });

      aliceWs.on('message', (data) => {
        const event = JSON.parse(data.toString());
        if (event.type === 'AUTH_SUCCESS') {
          // Bob is offline; Alice sends message
          const offlinePayload: EncryptedMessagePayload = {
            id: 'msg_offline_999',
            conversationId: 'conv_alice_bob',
            senderId: aliceUserId,
            recipientId: bobUserId,
            dhPublicKeyBase64: 'AliceDhPubMock==',
            sequenceNumber: 1,
            previousChainLength: 0,
            ivBase64: 'MockIV==',
            ciphertextBase64: 'mockOfflineCiphertext==',
            timestamp: Date.now(),
            status: 'QUEUED'
          };
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
