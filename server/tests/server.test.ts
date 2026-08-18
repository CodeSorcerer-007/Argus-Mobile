import request from 'supertest';
import { createApp } from '../src/server';
import { ArgusDatabase } from '../src/db/database';

describe('Argus Backend Integration Tests', () => {
  let db: ArgusDatabase;
  let app: any;
  let authToken: string;
  let userId: string;
  let testPhone = '+15550001234';

  beforeAll(() => {
    db = new ArgusDatabase('./data_test_' + Date.now());
    app = createApp(db);
  });

  test('GET /health returns 200 OK', async () => {
    const res = await request(app).get('/health');
    expect(res.status).toBe(200);
    expect(res.body.status).toBe('ok');
    expect(res.body.service).toBe('Argus E2EE Gateway');
  });

  test('POST /api/auth/request-otp generates OTP', async () => {
    const res = await request(app)
      .post('/api/auth/request-otp')
      .send({ phoneNumber: testPhone });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
  });

  test('POST /api/auth/verify-otp authenticates and issues JWT', async () => {
    const otpRecord = db.otps.get(testPhone);
    expect(otpRecord).toBeDefined();

    const res = await request(app)
      .post('/api/auth/verify-otp')
      .send({
        phoneNumber: testPhone,
        code: otpRecord!.code,
        deviceName: 'Pixel 9 Pro',
        displayName: 'Test User'
      });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.token).toBeDefined();
    expect(res.body.user.id).toBeDefined();

    authToken = res.body.token;
    userId = res.body.user.id;
  });

  test('POST /api/keys/publish-bundle publishes PreKey Bundle', async () => {
    const res = await request(app)
      .post('/api/keys/publish-bundle')
      .set('Authorization', `Bearer ${authToken}`)
      .send({
        identityPublicKeyBase64: 'mockIdentityKeyBase64',
        signedPreKeyId: 1,
        signedPreKeyPublicBase64: 'mockSignedPreKeyPublicBase64',
        signedPreKeySignatureBase64: 'mockSignedPreKeySignatureBase64',
        oneTimePreKeys: [
          { keyId: 101, publicKeyBase64: 'mockOneTimeKey1' },
          { keyId: 102, publicKeyBase64: 'mockOneTimeKey2' }
        ]
      });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.availableOneTimeKeys).toBe(2);
  });

  test('GET /api/keys/bundle/:userId retrieves target bundle and consumes 1 OTP key', async () => {
    const res = await request(app)
      .get(`/api/keys/bundle/${userId}`)
      .set('Authorization', `Bearer ${authToken}`);

    expect(res.status).toBe(200);
    expect(res.body.userId).toBe(userId);
    expect(res.body.identityPublicKeyBase64).toBe('mockIdentityKeyBase64');
    expect(res.body.oneTimePreKeyId).toBe(101);
  });

  test('GET /api/users/me returns authenticated user', async () => {
    const res = await request(app)
      .get('/api/users/me')
      .set('Authorization', `Bearer ${authToken}`);

    expect(res.status).toBe(200);
    expect(res.body.user.phoneNumber).toBe(testPhone);
  });

  test('PUT /api/users/me updates profile with unique username', async () => {
    const res = await request(app)
      .put('/api/users/me')
      .set('Authorization', `Bearer ${authToken}`)
      .send({
        displayName: 'Argus Prime',
        username: 'argus_prime',
        about: 'Testing Argus secure messenger'
      });

    expect(res.status).toBe(200);
    expect(res.body.user.username).toBe('argus_prime');
    expect(res.body.user.displayName).toBe('Argus Prime');
  });

  test('POST /api/users/discover-contacts matches hashed phone numbers', async () => {
    const phoneHash = db.hashPhone(testPhone);
    const res = await request(app)
      .post('/api/users/discover-contacts')
      .set('Authorization', `Bearer ${authToken}`)
      .send({ phoneHashes: [phoneHash, 'nonexistent_hash_123'] });

    expect(res.status).toBe(200);
    expect(res.body.contacts.length).toBe(1);
    expect(res.body.contacts[0].displayName).toBe('Argus Prime');
  });

  test('POST /api/groups/create creates a new secure group', async () => {
    const res = await request(app)
      .post('/api/groups/create')
      .set('Authorization', `Bearer ${authToken}`)
      .send({
        title: 'Argus Core Team',
        description: 'Private encrypted team group'
      });

    expect(res.status).toBe(200);
    expect(res.body.group.title).toBe('Argus Core Team');
    expect(res.body.group.admins).toContain(userId);
  });
});
