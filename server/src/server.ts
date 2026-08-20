import http from 'http';
import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import dotenv from 'dotenv';
import jwt from 'jsonwebtoken';
import { ArgusDatabase } from './db/database';
import { createAuthRouter } from './routes/auth';
import { createKeysRouter } from './routes/keys';
import { createUsersRouter } from './routes/users';
import { createGroupsRouter } from './routes/groups';
import { createMediaRouter } from './routes/media';
import { createCallsRouter } from './routes/calls';
import { ArgusWebSocketManager } from './ws/wsManager';
import { notificationService } from './services/notificationService';

dotenv.config();

const PORT = parseInt(process.env.PORT || '8080', 10);
const DEFAULT_JWT_SECRET = 'argus_super_secret_jwt_key_2026_production_change_in_prod';
let JWT_SECRET = process.env.JWT_SECRET || DEFAULT_JWT_SECRET;
const DATA_DIR = process.env.DATA_DIR || './data';
const UPLOAD_DIR = process.env.UPLOAD_DIR || './uploads';

// Production fallback: Ensure JWT secret is secure on cloud environments like Render
if (!process.env.JWT_SECRET || process.env.JWT_SECRET === DEFAULT_JWT_SECRET) {
  if (process.env.NODE_ENV === 'production') {
    console.warn('[Security Notice] No custom JWT_SECRET specified. Generating an ephemeral high-entropy key for this session.');
    JWT_SECRET = require('crypto').randomBytes(32).toString('hex');
  }
}

export function createApp(db: ArgusDatabase, customJwtSecret?: string) {
  notificationService.setDatabase(db);
  const app = express();
  const activeJwtSecret = customJwtSecret || JWT_SECRET;

  // Render & Reverse Proxy support (Trust first proxy for correct client IP detection in rate limiting)
  app.set('trust proxy', 1);

  // 1. Security Headers (Helmet)
  app.use(helmet({
    contentSecurityPolicy: false, // Mobile API focus
    crossOriginResourcePolicy: { policy: 'cross-origin' }
  }));

  // 2. CORS Policy (Configured for Mobile & Web Clients)
  const allowedOrigins = process.env.CORS_ORIGIN 
    ? process.env.CORS_ORIGIN.split(',').map(s => s.trim()) 
    : '*';

  app.use(cors({
    origin: allowedOrigins,
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'Range']
  }));

  // 3. Body Parsers with strict bounds
  app.use(express.json({ limit: '10mb' }));
  app.use(express.urlencoded({ extended: true, limit: '10mb' }));

  // 4. Rate Limiting Middleware
  const isTest = process.env.NODE_ENV === 'test';
  const customWindowMs = parseInt(process.env.RATE_LIMIT_WINDOW_MS || '60000', 10);
  const customMaxRequests = parseInt(process.env.RATE_LIMIT_MAX_REQUESTS || '300', 10);

  const generalLimiter = rateLimit({
    windowMs: customWindowMs,
    max: isTest ? 10000 : customMaxRequests,
    standardHeaders: true,
    legacyHeaders: false,
    message: { error: 'Too many requests, please slow down.' }
  });

  const authSensitiveLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: isTest ? 10000 : 30,
    standardHeaders: true,
    legacyHeaders: false,
    message: { error: 'Too many authentication attempts. Please try again later.' }
  });

  const usernameCheckLimiter = rateLimit({
    windowMs: 60 * 1000, // 1 minute
    max: isTest ? 10000 : 120,
    standardHeaders: true,
    legacyHeaders: false,
    message: { error: 'Too many username checks. Please try again in a moment.' }
  });

  app.use(generalLimiter);

  // Request logger
  app.use((req: Request, _res: Response, next: NextFunction) => {
    if (process.env.NODE_ENV !== 'test') {
      console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`);
    }
    next();
  });

  // Health check endpoint
  app.get('/health', (_req: Request, res: Response) => {
    res.json({
      status: 'ok',
      service: 'Argus E2EE Gateway',
      version: '2.4.0',
      environment: process.env.NODE_ENV || 'development',
      uptimeSec: Math.floor(process.uptime()),
      timestamp: Date.now()
    });
  });

  // Auth Middleware for protected endpoints with active account validation
  const authMiddleware = (req: Request, res: Response, next: NextFunction): void => {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      res.status(401).json({ error: 'Missing or invalid Authorization header' });
      return;
    }

    const token = authHeader.substring(7);
    try {
      const decoded = jwt.verify(token, activeJwtSecret) as { userId: string; username?: string; deviceId?: string };
      const user = db.users.get(decoded.userId);
      if (!user) {
        res.status(401).json({ error: 'User account not found or has been deleted' });
        return;
      }
      (req as any).user = decoded;
      next();
    } catch (err) {
      res.status(401).json({ error: 'Invalid or expired token' });
    }
  };

  // Mount API Routers
  app.use(
    '/api/auth',
    createAuthRouter(db, activeJwtSecret, {
      sensitiveLimiter: authSensitiveLimiter,
      checkUsernameLimiter: usernameCheckLimiter
    })
  );
  app.use('/api/keys', authMiddleware, createKeysRouter(db));
  app.use('/api/users', authMiddleware, createUsersRouter(db));
  app.use('/api/groups', authMiddleware, createGroupsRouter(db));
  app.use('/api/calls', authMiddleware, createCallsRouter());
  app.use('/api/media', createMediaRouter(UPLOAD_DIR, authMiddleware));

  // Global Centralized Error Handling Middleware
  app.use((err: any, _req: Request, res: Response, _next: NextFunction) => {
    if (process.env.NODE_ENV !== 'test') {
      console.error('[Unhandled Error]', err);
    }
    res.status(err.status || 500).json({
      error: err.message || 'Internal server error'
    });
  });

  return app;
}

export async function startServer() {
  const db = new ArgusDatabase(DATA_DIR);
  await db.init();
  const app = createApp(db);
  const server = http.createServer(app);

  const wsManager = new ArgusWebSocketManager(server, db, JWT_SECRET);

  server.listen(PORT, '0.0.0.0', () => {
    const dbMode = process.env.DATABASE_URL
      ? 'Cloud PostgreSQL / Neon.tech (Persistent 🚀)'
      : 'Local Zero-Knowledge JSON Store';

    console.log(`=========================================`);
    console.log(`  Argus E2EE Production Gateway Running  `);
    console.log(`  HTTP API:  http://0.0.0.0:${PORT}      `);
    console.log(`  WebSocket: ws://0.0.0.0:${PORT}/ws     `);
    console.log(`  Database:  ${dbMode}                   `);
    console.log(`  Health:    http://0.0.0.0:${PORT}/health`);
    console.log(`=========================================`);
  });

  // Graceful Shutdown on SIGTERM / SIGINT
  const handleShutdown = (signal: string) => {
    console.log(`Received ${signal}. Starting graceful shutdown...`);
    db.save();

    server.close(() => {
      console.log('HTTP server closed.');
      process.exit(0);
    });

    // Force exit after 10 seconds if connections hang
    setTimeout(() => {
      console.error('Could not close connections in time, forcefully shutting down');
      process.exit(1);
    }, 10000);
  };

  process.on('SIGTERM', () => handleShutdown('SIGTERM'));
  process.on('SIGINT', () => handleShutdown('SIGINT'));

  return { server, db, wsManager };
}

if (require.main === module) {
  startServer();
}
