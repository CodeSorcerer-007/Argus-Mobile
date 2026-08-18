import { Router, Request, Response } from 'express';
import fs from 'fs';
import path from 'path';
import multer from 'multer';
import { v4 as uuidv4 } from 'uuid';

export function createMediaRouter(uploadDir: string = './uploads'): Router {
  const router = Router();

  if (!fs.existsSync(uploadDir)) {
    fs.mkdirSync(uploadDir, { recursive: true });
  }

  const storage = multer.diskStorage({
    destination: (_req, _file, cb) => cb(null, uploadDir),
    filename: (_req, file, cb) => {
      const ext = path.extname(file.originalname);
      cb(null, `${uuidv4()}${ext}`);
    }
  });

  const upload = multer({
    storage,
    limits: { fileSize: 500 * 1024 * 1024 } // 500MB max per attachment
  });

  /**
   * Upload encrypted media blob
   */
  router.post('/upload', upload.single('file'), (req: Request, res: Response): void => {
    if (!req.file) {
      res.status(400).json({ error: 'File is required' });
      return;
    }

    const fileUrl = `/api/media/download/${req.file.filename}`;
    res.json({
      success: true,
      fileId: req.file.filename,
      fileUrl,
      sizeBytes: req.file.size,
      mimeType: req.file.mimetype
    });
  });

  /**
   * Download encrypted media blob with range/resumable chunk support
   */
  router.get('/download/:filename', (req: Request, res: Response): void => {
    const filename = req.params.filename as string;
    const safeFilename = path.basename(filename);
    const filePath = path.join(uploadDir, safeFilename);

    if (!fs.existsSync(filePath)) {
      res.status(404).json({ error: 'File not found' });
      return;
    }

    const stat = fs.statSync(filePath);
    const fileSize = stat.size;
    const range = req.headers.range;

    if (range) {
      const parts = range.replace(/bytes=/, '').split('-');
      const start = parseInt(parts[0], 10);
      const end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;
      const chunksize = end - start + 1;
      const file = fs.createReadStream(filePath, { start, end });
      const head = {
        'Content-Range': `bytes ${start}-${end}/${fileSize}`,
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
      fs.createReadStream(filePath).pipe(res);
    }
  });

  return router;
}
