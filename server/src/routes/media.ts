import { Router, Request, Response } from 'express';
import fs from 'fs';
import path from 'path';
import multer from 'multer';
import { v4 as uuidv4 } from 'uuid';

const ALLOWED_EXTENSIONS = new Set([
  '.enc', '.bin', '.dat', '.jpg', '.jpeg', '.png', '.webp', '.gif',
  '.mp4', '.mov', '.webm', '.aac', '.m4a', '.mp3', '.ogg', '.opus', '.wav',
  '.pdf', '.txt', '.doc', '.docx', '.blob'
]);

export function createMediaRouter(uploadDir: string = './uploads', authMiddleware?: any): Router {
  const router = Router();
  const absoluteUploadDir = path.resolve(uploadDir);

  if (!fs.existsSync(absoluteUploadDir)) {
    fs.mkdirSync(absoluteUploadDir, { recursive: true });
  }

  const storage = multer.diskStorage({
    destination: (_req, _file, cb) => cb(null, absoluteUploadDir),
    filename: (_req, file, cb) => {
      let ext = path.extname(file.originalname).toLowerCase();
      if (!ALLOWED_EXTENSIONS.has(ext)) {
        ext = '.bin'; // default to safe binary blob
      }
      cb(null, `${uuidv4()}${ext}`);
    }
  });

  const upload = multer({
    storage,
    limits: {
      fileSize: 100 * 1024 * 1024, // 100MB max per attachment
      files: 1
    }
  });

  const uploadHandlers: any[] = [];
  if (authMiddleware) {
    uploadHandlers.push(authMiddleware);
  }
  uploadHandlers.push(upload.single('file'));

  /**
   * Upload encrypted media blob (Authenticated)
   */
  router.post('/upload', ...uploadHandlers, (req: Request, res: Response): void => {
    if (!req.file) {
      res.status(400).json({ error: 'Valid file attachment is required' });
      return;
    }

    const fileUrl = `/api/media/download/${req.file.filename}`;
    res.json({
      success: true,
      fileId: req.file.filename,
      fileUrl,
      sizeBytes: req.file.size,
      mimeType: req.file.mimetype || 'application/octet-stream'
    });
  });

  /**
   * Download encrypted media blob with range/resumable chunk support
   */
  router.get('/download/:filename', (req: Request, res: Response): void => {
    const filename = req.params.filename as string;
    const safeFilename = path.basename(filename);
    const filePath = path.resolve(absoluteUploadDir, safeFilename);

    // Path traversal defense
    if (!filePath.startsWith(absoluteUploadDir)) {
      res.status(403).json({ error: 'Access denied' });
      return;
    }

    if (!fs.existsSync(filePath)) {
      res.status(404).json({ error: 'Requested media not found' });
      return;
    }

    const stat = fs.statSync(filePath);
    const fileSize = stat.size;
    const range = req.headers.range;

    res.setHeader('X-Content-Type-Options', 'nosniff');

    if (range) {
      const parts = range.replace(/bytes=/, '').split('-');
      const start = parseInt(parts[0], 10);
      const end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;

      if (isNaN(start) || isNaN(end) || start < 0 || start > end || start >= fileSize) {
        res.setHeader('Content-Range', `bytes */${fileSize}`);
        res.status(416).json({ error: 'Requested range not satisfiable' });
        return;
      }

      const safeEnd = Math.min(end, fileSize - 1);
      const chunksize = safeEnd - start + 1;
      const file = fs.createReadStream(filePath, { start, end: safeEnd });
      file.on('error', () => {
        if (!res.headersSent) {
          res.status(500).json({ error: 'Error reading media stream' });
        }
      });
      const head = {
        'Content-Range': `bytes ${start}-${safeEnd}/${fileSize}`,
        'Accept-Ranges': 'bytes',
        'Content-Length': chunksize,
        'Content-Type': 'application/octet-stream'
      };
      res.writeHead(206, head);
      file.pipe(res);
    } else {
      const head = {
        'Content-Length': fileSize,
        'Content-Type': 'application/octet-stream',
        'Accept-Ranges': 'bytes'
      };
      res.writeHead(200, head);
      const file = fs.createReadStream(filePath);
      file.on('error', () => {
        if (!res.headersSent) {
          res.status(500).json({ error: 'Error reading media stream' });
        }
      });
      file.pipe(res);
    }
  });

  return router;
}
