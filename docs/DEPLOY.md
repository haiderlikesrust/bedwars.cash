# Deploy BedWars.cash on a VPS (Docker)

Production layout:

| Hostname | Service | Notes |
|----------|---------|--------|
| `bedwars.cash` | React website | HTTPS via Caddy |
| `server.bedwars.cash` | Node backend | REST API + WebSockets |
| `join.bedwars.cash` | Minecraft | **A record → VPS IP**, port **25565** |

## 1. VPS requirements

- Ubuntu 22.04+ (or any Linux with Docker Compose v2)
- **4 GB+ RAM** recommended (2 GB backend/web + 4 GB MC heap is tight — 8 GB+ ideal)
- Ports open: **80**, **443**, **25565**

```bash
# Install Docker (Ubuntu)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
# log out and back in
```

## 2. DNS records

At your domain registrar (replace `YOUR_VPS_IP`):

| Type | Name | Value |
|------|------|-------|
| A | `@` | YOUR_VPS_IP |
| A | `www` | YOUR_VPS_IP |
| A | `server` | YOUR_VPS_IP |
| A | `join` | YOUR_VPS_IP |

Optional SRV (lets players use `bedwars.cash` without `:25565`):

| Type | Name | Value |
|------|------|-------|
| SRV | `_minecraft._tcp` | `0 5 25565 join.bedwars.cash.` |

## 3. Configure environment

```bash
git clone https://github.com/haiderlikesrust/bedwars.cash.git
cd bedwars.cash
cp .env.production.example .env
nano .env
```

**Required changes in `.env`:**

- `ACME_EMAIL` — Let's Encrypt contact (HTTPS certificates)
- `PLUGIN_TOKEN` — long random string (same value used by backend + Minecraft plugin)
- `APP_SECRET` — 32+ character random string

Generate secrets:

```bash
openssl rand -hex 32   # PLUGIN_TOKEN
openssl rand -hex 32   # APP_SECRET
```

## 4. Build and start

```bash
docker compose --env-file .env up -d --build
```

First build takes several minutes (Gradle plugin + Paper download on first MC start).

Check status:

```bash
docker compose ps
docker compose logs -f backend
docker compose logs -f minecraft
```

## 5. Verify

- **Website:** https://bedwars.cash
- **API health:** https://server.bedwars.cash/api/health
- **Minecraft:** add server `join.bedwars.cash` (port 25565)
- **Plugin connected:** backend logs should show `Minecraft plugin connected`

## 6. Fund the house wallet (devnet)

```bash
curl https://server.bedwars.cash/api/house
# Fund the printed address on devnet, or:
curl -X POST https://server.bedwars.cash/api/admin/topup \
  -H "Content-Type: application/json" \
  -H "x-admin-token: YOUR_APP_SECRET" \
  -d '{"sol": 2}'
```

## 7. Updates

```bash
git pull
docker compose --env-file .env up -d --build
```

World and database persist in Docker volumes (`minecraft_data`, `backend_data`).

## 8. Optional: GrimAC anticheat

Mount a GrimAC jar into the running MC container:

```yaml
# docker-compose.yml under minecraft.volumes:
- ./extras/GrimAC.jar:/minecraft/plugins/GrimAC.jar:ro
```

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `Bind for 0.0.0.0:80 failed: port is already allocated` | Another web server (nginx, Apache, etc.) is using port 80. See **Port 80 / 443 in use** below. |
| Caddy no HTTPS | DNS must point to VPS before starting; check `docker compose logs caddy` |
| Plugin won't connect | `PLUGIN_TOKEN` must match in `.env`; check `docker compose logs backend` |
| MC out of memory | Raise `MC_MEMORY=6G` in `.env` and ensure VPS has enough RAM |
| CORS errors | `WEB_ORIGIN` is set from `DOMAIN`; must be `https://bedwars.cash` |

### Port 80 / 443 in use

Caddy needs ports **80** and **443** for HTTPS. If the stack built but Caddy failed to start, find what is bound to those ports:

```bash
sudo ss -tlnp | grep -E ':80|:443'
```

Common on fresh VPS images: **nginx** or **Apache**.

**Option A — use Caddy (recommended for this repo):** stop the other web server, then start Caddy:

```bash
# nginx
sudo systemctl stop nginx
sudo systemctl disable nginx

# or Apache
sudo systemctl stop apache2
sudo systemctl disable apache2

cd ~/bedwars.cash
docker compose --env-file .env up -d caddy
docker compose ps
```

**Option B — keep nginx/Apache:** do not run the `caddy` service; point your existing reverse proxy at the Docker network instead:

- `bedwars.cash` → `http://127.0.0.1:8080` (publish `web` on 8080) or proxy to container IP
- `server.bedwars.cash` → backend on 8787

For Option B you must publish `web` and `backend` ports yourself and handle TLS in nginx/Apache instead of Caddy.

## Architecture

```
Internet
   │
   ├─ :443  bedwars.cash ──────► Caddy ──► web (nginx + React)
   ├─ :443  server.bedwars.cash ► Caddy ──► backend:8787
   └─ :25565 join.bedwars.cash ► minecraft:25565
                                    │
                                    └── ws://backend:8787/ws/plugin (internal)
```
