# BedWars.cash API + WebSocket hub
FROM node:22-bookworm-slim AS build

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 make g++ \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY backend/package.json backend/package-lock.json ./
RUN npm ci

COPY backend/tsconfig.json backend/tsconfig.json
COPY backend/src ./src
RUN npm run build

FROM node:22-bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 make g++ \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY backend/package.json backend/package-lock.json ./
RUN npm ci --omit=dev

COPY --from=build /app/dist ./dist
RUN mkdir -p data wallets

ENV NODE_ENV=production
EXPOSE 8787

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:8787/api/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "dist/index.js"]
