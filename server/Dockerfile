# Multi-stage build for production-ready Argus Backend Server
FROM node:20-alpine AS builder

WORKDIR /app
COPY package*.json tsconfig.json ./
RUN npm ci

COPY src ./src
RUN npm run build

# Production runtime stage
FROM node:20-alpine AS runner

WORKDIR /app
ENV NODE_ENV=production
ENV PORT=8080
ENV DATA_DIR=/app/data
ENV UPLOAD_DIR=/app/uploads

COPY package*.json ./
RUN npm ci --omit=dev

COPY --from=builder /app/dist ./dist

# Create persistence directories
RUN mkdir -p /app/data /app/uploads

EXPOSE 8080

CMD ["node", "dist/server.js"]
