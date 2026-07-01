FROM node:22-alpine AS build

WORKDIR /app
COPY admin/package.json admin/package-lock.json ./
RUN npm ci

COPY admin/ ./
RUN npm run build

FROM nginx:1.27-alpine

RUN apk add --no-cache curl

COPY docker/admin/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
  CMD curl -fsS http://127.0.0.1/ >/dev/null || exit 1
