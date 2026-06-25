# Deploy BedWars.cash on a VPS (Docker)

Production layout:

| Hostname | Service | Notes |
|----------|---------|--------|
| `bedwars.cash` | React website | HTTPS via Caddy **or** your existing nginx |
| `server.bedwars.cash` | Node backend | REST API + WebSockets |
| `join.bedwars.cash` | Minecraft | **A record → VPS IP**, port **25565** |

## Deployment modes

| Mode | When to use | Start command |
|------|-------------|---------------|
| **A — existing nginx/Apache** | Another site already uses ports 80/443 on the VPS | `docker compose --env-file .env up -d` |
| **B — Caddy (dedicated VPS)** | Nothing else needs ports 80/443 | `docker compose --profile caddy --env-file .env up -d` |

**Mode A** publishes the app on localhost only (`127.0.0.1:8080` web, `127.0.0.1:8787` backend). Add nginx server blocks from [`docker/nginx/host-nginx.example.conf`](../docker/nginx/host-nginx.example.conf) to your existing config.

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

### Mode A — you already run nginx/Apache on this VPS

```bash
# Remove any failed Caddy container from earlier attempts
docker compose --env-file .env rm -f caddy 2>/dev/null || true

docker compose --env-file .env up -d --build
```

Add the server blocks in [`docker/nginx/host-nginx.example.conf`](../docker/nginx/host-nginx.example.conf) to your **existing** nginx config, then:

```bash
sudo nginx -t && sudo systemctl reload nginx
```

Get certificates if you do not have them yet:

```bash
sudo certbot certonly --nginx -d bedwars.cash -d www.bedwars.cash -d server.bedwars.cash
```

Verify:

```bash
curl -s http://127.0.0.1:8080 | head
curl -s http://127.0.0.1:8787/api/health
curl -s https://bedwars.cash/api/health   # should 404 on main site — use server.bedwars.cash for API
curl -s https://server.bedwars.cash/api/health
```

### Mode B — dedicated VPS (Caddy handles HTTPS)

Requires ports **80** and **443** free on the host.

```bash
docker compose --profile caddy --env-file .env up -d --build
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
docker port bedwarscash-caddy-1
```

**Both** ports must be free (or owned by Caddy). A common mistake is stopping nginx for port 80 while it still holds **443** — the site then shows `ERR_SSL_PROTOCOL_ERROR` in the browser.

Common on fresh VPS images: **nginx** or **Apache**.

**Option A — use Caddy (recommended for this repo):** stop the other web server on **80 and 443**, then recreate Caddy:

```bash
# nginx
sudo systemctl stop nginx
sudo systemctl disable nginx

# or Apache
sudo systemctl stop apache2
sudo systemctl disable apache2

# confirm nothing else is on 80/443
sudo ss -tlnp | grep -E ':80|:443'

cd ~/bedwars.cash
git pull
docker compose --env-file .env up -d --force-recreate caddy
docker compose ps
docker port bedwarscash-caddy-1
docker compose logs caddy --tail 50
```

`docker port bedwarscash-caddy-1` should show `80/tcp` and `443/tcp` mapped to the host.

**Option B — keep nginx/Apache:** do not run the `caddy` service; point your existing reverse proxy at the Docker network instead. Example config: [`docker/nginx/host-nginx.example.conf`](../docker/nginx/host-nginx.example.conf).

Publish app ports on localhost only:

```yaml
# docker-compose.yml
web:
  ports:
    - "127.0.0.1:8080:80"
backend:
  ports:
    - "127.0.0.1:8787:8787"
```

Then start without Caddy:

```bash
docker compose --env-file .env up -d web backend minecraft
sudo certbot --nginx -d bedwars.cash -d www.bedwars.cash -d server.bedwars.cash
```

For Option B you handle TLS in nginx/Apache instead of Caddy.

### `ERR_SSL_PROTOCOL_ERROR` in the browser

Usually means port **443** is not serving TLS from Caddy. Run the checks above, then:

```bash
curl -vI http://bedwars.cash          # should redirect to https
curl -vI https://bedwars.cash         # should show HTTP/2 200 and a Let's Encrypt cert
docker compose logs caddy --tail 100  # look for "certificate obtained successfully"
```

If the domain uses **Cloudflare**, set DNS records to **DNS only** (grey cloud) until Caddy has issued certificates, then use **Full (strict)** — not Flexible.

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
