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
import { ArgusWebSocketManager } from './ws/wsManager';

dotenv.config();

const PORT = parseInt(process.env.PORT || '8080', 10);
const JWT_SECRET = process.env.JWT_SECRET || 'argus_super_secret_jwt_key_2026_production_change_in_prod';
const DATA_DIR = process.env.DATA_DIR || './data';
const UPLOAD_DIR = process.env.UPLOAD_DIR || './uploads';

export function createApp(db: ArgusDatabase) {
  const app = express();

  // 1. Security Headers (Helmet)
  app.use(helmet({
    contentSecurityPolicy: false, // Mobile API focus
    crossOriginResourcePolicy: { policy: 'cross-origin' }
  }));

  // 2. CORS Policy (Configured for Mobile Clients)
  app.use(cors({
    origin: '*',
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'Range']
  }));

  // 3. Body Parsers with strict bounds
  app.use(express.json({ limit: '10mb' }));
  app.use(express.urlencoded({ extended: true, limit: '10mb' }));

  // 4. Rate Limiting Middleware
  const isTest = process.env.NODE_ENV === 'test';

  const generalLimiter = rateLimit({
    windowMs: 60 * 1000, // 1 minute
    max: isTest ? 10000 : 300,
    standardHeaders: true,
    legacyHeaders: false,
    message: { error: 'Too many requests, please slow down.' }
  });

  const authRateLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: isTest ? 10000 : 15,
    standardHeaders: true,
    legacyHeaders: false,
    message: { error: 'Too many authentication attempts. Please try again later.' }
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
      environment: process.env.NODE_ENV || 'development',
      uptimeSec: Math.floor(process.uptime()),
      timestamp: Date.now()
    });
  });

  // Auth Middleware for protected endpoints
  const authMiddleware = (req: Request, res: Response, next: NextFunction): void => {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      res.status(401).json({ error: 'Missing or invalid Authorization header' });
      return;
    }

    const token = authHeader.substring(7);
    try {
      const decoded = jwt.verify(token, JWT_SECRET);
      (req as any).user = decoded;
      next();
    } catch (err) {
      res.status(401).json({ error: 'Invalid or expired token' });
    }
  };

  // Mount API Routers
  app.use('/api/auth', authRateLimiter, createAuthRouter(db, JWT_SECRET));
  app.use('/api/keys', authMiddleware, createKeysRouter(db));
  app.use('/api/users', authMiddleware, createUsersRouter(db));
  app.use('/api/groups', authMiddleware, createGroupsRouter(db));
  app.use('/api/media', createMediaRouter(UPLOAD_DIR));

  return app;
}

export function startServer() {
  const db = new ArgusDatabase(DATA_DIR);
  const app = createApp(db);
  const server = http.createServer(app);

  const wsManager = new ArgusWebSocketManager(server, db, JWT_SECRET);

  server.listen(PORT, '0.0.0.0', () => {
    console.log(`=========================================`);
    console.log(`  Argus E2EE Production Gateway Running  `);
    console.log(`  HTTP API:  http://0.0.0.0:${PORT}      `);
    console.log(`  WebSocket: ws://0.0.0.0:${PORT}/ws     `);
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
