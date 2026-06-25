FROM node:22-alpine AS build

WORKDIR /app
COPY web/package.json web/package-lock.json ./
RUN npm ci

COPY web/ ./

ARG VITE_API_URL=https://server.bedwars.cash
ARG VITE_WS_URL=wss://server.bedwars.cash/ws/web
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_WS_URL=$VITE_WS_URL

RUN npm run build

FROM nginx:1.27-alpine

COPY docker/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
