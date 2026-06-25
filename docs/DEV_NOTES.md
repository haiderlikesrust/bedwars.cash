# BedWars.cash — Developer Notes

Internal reference for how the project is built, what is implemented, and how the pieces connect.

- **Players / operators:** [GAME.md](./GAME.md) — match flow, rewards, betting, plugin commands  
- **Setup:** [SETUP.md](./SETUP.md)  
- **Deploy:** [DEPLOY.md](./DEPLOY.md)

---

## What this is

**BedWars.cash** is a devnet-only wagering platform around competitive BedWars matches on Minecraft. Two separate money flows exist:

| Pool | Who participates | How it is funded | How winners are paid |
|------|------------------|------------------|----------------------|
| **Player reward pool** | 16 queued fighters (4 teams × 4 players) | Pump.fun creator fees (mocked on devnet via house wallet top-ups) | On-chain SOL to `/setwallet` addresses, split equally among the winning team |
| **Spectator betting pool** | Linked web/in-game bettors | Custodial balances (deposited devnet SOL) | Internal ledger credits via parimutuel settlement (95% to winners, 5% house rake) |

Everything runs on **Solana devnet** with test SOL. This repo is not production-ready for mainnet or real-money gambling.

---

## Monorepo layout

```
minecraft server/
├── backend/          Node + TypeScript — API, WebSocket hub, SQLite, Solana custody
├── web/              React + Vite — spectator dashboard (deposit, bet odds, stream)
├── plugins/bedwarscash/   Paper plugin source (Java 25 toolchain via Gradle)
├── server/           Paper 26.1.2 runtime (jars/world gitignored)
└── docs/             SETUP.md, this file
```

| Component | Stack | Default port |
|-----------|-------|--------------|
| Backend | Fastify, better-sqlite3, @solana/web3.js | `8787` |
| Web | React 19, Vite, react-router | `5173` |
| Minecraft | Paper 26.1.2, **Java 25 required** | `25565` |
| Streaming (optional) | OvenMediaEngine Docker, OBS/ffmpeg | `8080`, `9000`, etc. |

---

## Architecture

```mermaid
flowchart TB
  subgraph clients [Clients]
    Web[Web app /play]
    MC[Minecraft players]
    Cast[Broadcast client BWC_Cast]
  end

  subgraph backend [Backend :8787]
    REST[REST /api/*]
    WSPlugin[/ws/plugin]
    WSWeb[/ws/web]
    Match[match.ts lifecycle]
    Pari[parimutuel.ts]
    Custody[custody.ts Solana]
    DB[(SQLite bedwars.sqlite)]
  end

  subgraph chain [Solana devnet]
    House[House hot wallet]
    DepWallets[Per-user deposit wallets]
  end

  Web --> REST
  Web --> WSWeb
  MC -->|BedWarsCash plugin| WSPlugin
  Cast --> MC

  WSPlugin --> Match
  WSWeb --> Match
  Match --> Pari
  Match --> DB
  REST --> Custody
  Custody --> House
  Custody --> DepWallets
  House --> chain
  DepWallets --> chain
```

**Single source of truth:** the backend owns match phase, queue, bets, balances, and settlement. The Paper plugin is a thin game client that runs the embedded BedWars arena and forwards player actions over WebSocket.

---

## What is implemented

### Backend

- [x] Custodial user accounts with encrypted per-user deposit keypairs
- [x] Session tokens (`POST /api/session`) and Minecraft linking via one-time codes
- [x] Deposit scanning (15s poll): credit balance, sweep to house wallet
- [x] Withdrawals from house wallet with balance reservation/refund on failure
- [x] Match lifecycle: `lobby` → `live` → `settling` → new `lobby`
- [x] Queue (16 players default), win cooldown (sit out 1 match after winning)
- [x] Party system (up to `TEAM_SIZE`, kept on same team at match start)
- [x] Parimutuel betting with dynamic implied multipliers and largest-remainder payout
- [x] Player reward pool calculation (house balance − reserve − custodial liabilities)
- [x] WebSocket hub: plugin auth + web live feed
- [x] Admin endpoints: topup, credit, force-start, abort
- [x] Cheat flag logging from plugin
- [x] Optional live stream URL + broadcast camera state for the website
- [x] Unit tests for parimutuel math

### Web app

- [x] Landing page (`/`) and dashboard (`/play`)
- [x] Auto session creation, deposit QR/address, withdraw form
- [x] Minecraft link code flow
- [x] Live odds, queue size, match phase via `/ws/web`
- [x] Live stream panel (HLS `<video>` or iframe embed)
- [x] Leaderboard (top bettors by net profit, top players by reward winnings)
- [x] Player reward pool display from `/api/house`

### Paper plugin (BedWarsCash v0.1.0)

- [x] **Embedded BedWars** — no external BedWars2023/2026 plugin required
- [x] Procedural void lobby (`bwc_lobby`) and arena (`bwc_arena`) with 4 team islands
- [x] Custom lobby map import from `plugins/BedWarsCash/maps/<template>/`
- [x] Resource generators (iron/gold/emerald/diamond), item shop, team upgrades
- [x] Special items (bridge eggs, fireballs, etc.)
- [x] Match flow: countdown → teleport to islands → bed destruction → last team standing
- [x] Spectator mode for late joiners and non-queued players during live matches
- [x] Scoreboard with live odds and match state
- [x] GrimAC soft-depend + CPS autoclicker flagging
- [x] Broadcast camera director (optional cast account rotation)
- [x] Party commands (`/party invite|accept|leave|list`)
- [x] All wagering commands wired to backend WebSocket
- [x] Legacy BedWars2023 API hook code exists but **embedded GameManager is the primary path**

### Infrastructure / ops

- [x] E2E test scripts (`e2e.mjs`, `e2e_bet.mjs`) that drive the plugin protocol without Minecraft
- [x] House funding script (`fund.mjs`)
- [x] Optional OvenMediaEngine Docker container for HLS streaming

---

## Match lifecycle (end to end)

### 1. Lobby phase

1. Backend always maintains a match in `lobby` phase (`ensureLobby()` on startup).
2. Player joins Minecraft server → plugin sends `player_join` → backend queues them (unless match is `live`/`settling` or they are on win cooldown).
3. Plugin receives `join_action`: `queued` → teleport to lobby; `spectate` → spectator mode.
4. Spectators and linked users can `/bet <team> <amountSol>` while phase is `lobby`. Bets debit custodial balance in SQLite.
5. Queued players cannot bet on their own match (anti-collusion).

### 2. Match start

1. When queue reaches `MATCH_PLAYER_COUNT` (default 16), backend calls `startMatch()`.
2. Backend assigns teams via `assignTeamsWithParties()` — parties stay together, capped at `TEAM_SIZE` per team.
3. Backend snapshots `availableRewardPool()` from the house wallet and sets phase to `live`.
4. Backend emits `start_match` on WebSocket with `{ matchId, teams: { GREEN: [uuids...], ... } }`.
5. Plugin `GameManager.startMatch()` runs countdown, teleports fighters to islands, starts generators.

**Force start:** ops use `/bwc forcestart` or `POST /api/admin/force-start` to begin with fewer than 16 players.

### 3. Live play

- Bed destruction eliminates players; last team with a bed or alive members wins.
- `GameManager` detects winner and calls `backend.matchResult()`.
- Fallback: op runs `/bwresult <green|blue|red|yellow>`.
- Late joiners get `spectate` and can still `/bet` only during lobby (betting locks at start).

### 4. Settlement

Backend `reportResult()`:

1. Sets phase to `settling`.
2. **Parimutuel:** winners on the winning team split 95% of the betting pool proportional to stake. If nobody bet on the winner, all bets refunded with no rake.
3. **Player rewards:** winning team UUIDs split `rewardPoolLamports` equally via on-chain `houseTransfer()` to each player's `/setwallet` address. If no wallet set, credited to custodial balance.
4. Winners get a 1-match cooldown in `win_cooldown`.
5. Queue cleared, new lobby opened, state/odds broadcast to plugin and web.

### 5. Abort

- `/bwc end`, plugin `match_aborted`, or admin abort → all bets refunded, queue cleared, new lobby.

---

## Economy details

### Custodial model

Each web user gets:

- A **session token** (Bearer auth)
- A **deposit wallet** (Solana keypair, secret encrypted with `APP_SECRET`)
- An internal **balance** in lamports (SQLite)

Flow: user sends devnet SOL to deposit address → backend detects transfer → credits balance → sweeps SOL to house wallet. Withdrawals and player rewards are signed by the **house hot wallet** (`wallets/house.json`, auto-generated if `HOUSE_WALLET_SECRET` empty).

### Reward pool formula

```
availableRewardPool = houseBalance
                    - HOUSE_RESERVE_SOL
                    - sum(user custodial balances)
                    - (optional REWARD_POOL_CAP_SOL cap)
```

User deposits and bet stakes are liabilities — the reward pool never spends them.

### Parimutuel math

Implemented in `backend/src/services/parimutuel.ts`:

- **Implied multiplier** for team T = `(totalPool - rake) / teamPool` (0 if team pool is 0)
- **Rake:** 5% default (`BETTING_RAKE=0.05`), rounded up in lamports so house never loses dust
- **Payout:** largest-remainder method — exact conservation of lamports across winners

Run tests: `cd backend && npm test`

---

## WebSocket protocol

### Plugin → backend (`/ws/plugin?token=PLUGIN_TOKEN`)

| Message | Purpose |
|---------|---------|
| `hello` | Handshake |
| `player_join` | Player connected to server |
| `queue_join` / `queue_leave` | Manual queue |
| `setwallet` | Save payout Solana address |
| `verify` | Link web account via code |
| `place_bet` | In-game bet |
| `match_result` | Report winning team + winner UUIDs |
| `match_aborted` | Void match |
| `cheat_flag` | Log anticheat hit |
| `force_start` / `force_abort` | Op commands |
| `match_camera` / `match_camera_clear` | Broadcast director |
| `party_*` | Party invite/accept/leave/list |

### Backend → plugin

| Message | Purpose |
|---------|---------|
| `welcome` | Connected |
| `start_match` | Begin arena with team assignments |
| `join_action` | Tell plugin how to handle a joining player |
| `odds` / `state` | Scoreboard + UI updates |
| `notice` | Chat message to player(s) |
| `ack` | Command acknowledgment |

### Web → backend (`/ws/web`)

Receives `{ kind: 'state' | 'odds' | 'notice' | 'camera', ... }` — same public state the plugin gets, plus camera/stream overlay updates.

---

## REST API summary

| Endpoint | Auth | Description |
|----------|------|-------------|
| `GET /api/health` | — | Cluster, plugin connected |
| `POST /api/session` | — | Create account + token + deposit wallet |
| `GET /api/me` | Bearer | Profile, balance, link status |
| `POST /api/link/code` | Bearer | Generate Minecraft link code |
| `POST /api/withdraw` | Bearer | Withdraw SOL to external address |
| `GET /api/odds` | — | Current parimutuel odds |
| `GET /api/state` | — | Match, queue, odds, stream |
| `GET /api/house` | — | House address, balance, reward pool |
| `GET /api/leaderboard` | — | Top bettors and players |
| `POST /api/admin/*` | `x-admin-token: APP_SECRET` | Devnet funding, force start/abort |

---

## Database (SQLite)

File: `backend/data/bedwars.sqlite` (WAL mode)

| Table | Purpose |
|-------|---------|
| `users` | Accounts, MC link, deposit/payout wallets, balance |
| `sessions` | Bearer tokens |
| `link_codes` | One-time MC link codes |
| `matches` | Match phases, reward pool, winner |
| `bets` | Parimutuel bets per match |
| `queue` | MC UUIDs waiting for next match |
| `win_cooldown` | Winners sit out N matches |
| `deposits` | Processed on-chain deposit signatures |
| `payouts` | On-chain outbound transfers |
| `ledger` | All balance deltas with reason strings |
| `cheat_flags` | Anticheat reports |
| `kv` | Key-value (e.g. `current_match`, deposit scan cursors) |

Parties are **in-memory only** (backend restart clears them).

---

## Plugin architecture

### Core classes

| Class | Role |
|-------|------|
| `BedWarsCashPlugin` | Bootstrap, command/event registration |
| `BackendClient` | WebSocket to backend, reconnect loop |
| `GameManager` | Embedded BedWars match engine |
| `WorldManager` | Creates/loads `bwc_lobby` and `bwc_arena` |
| `LobbyService` | Hub teleport, spawn rules |
| `GeneratorService` | Team/global resource generators |
| `ShopService` / `UpgradeShopService` | In-match purchases from `shop.yml` / `upgrades.yml` |
| `BroadcastDirector` | Rotates cast client spectating per team |
| `CashScoreboard` | Live odds and match info sidebar |
| `SpectatorHelper` | Spectator mode utilities |

### Worlds

- **`bwc_lobby`** — void world, glass platform hub (procedural or imported map)
- **`bwc_arena`** — 4 team islands with beds, generators, shop NPC markers

Lobby rules: peaceful, no mob spawn, no weather/day cycle. Players in adventure mode with cleared inventory.

### Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/setwallet <address>` | — | Payout wallet for player rewards |
| `/bwlink <code>` | — | Link website account |
| `/bet <team> <sol>` | — | Place parimutuel bet |
| `/bets` | — | In-game betting board GUI |
| `/queue [leave]` | — | Join/leave match queue |
| `/party invite\|accept\|leave\|list` | — | Party for same-team queueing |
| `/shop` | — | Item shop (in match) |
| `/upgrades` | — | Team upgrade shop (in match) |
| `/bwresult <team>` | `bedwarscash.admin` | Manual winner report |
| `/bwc setlobby\|forcestart\|end` | `bedwarscash.admin` | Admin tools |

### Config files

| File | Location |
|------|----------|
| Plugin runtime config | `server/plugins/BedWarsCash/config.yml` |
| Default template | `plugins/bedwarscash/src/main/resources/config.yml` |
| Shop catalog | `plugins/bedwarscash/src/main/resources/shop.yml` |
| Upgrades catalog | `plugins/bedwarscash/src/main/resources/upgrades.yml` |
| Generator tuning | `plugins/bedwarscash/src/main/resources/generators.yml` |

**Critical:** `backend.token` in plugin config must match `PLUGIN_TOKEN` in `backend/.env`.

### Build

```bash
cd plugins/bedwarscash
./gradlew build
# Output: build/libs/bedwarscash-0.1.0.jar → copy to server/plugins/
```

Uses JDK 25 toolchain (Gradle foojay resolver). Gson loaded at runtime via Paper `libraries` in `plugin.yml` — jar is not shaded.

---

## Web app structure

```
web/src/
├── pages/LandingPage.tsx    Marketing / entry
├── pages/DashboardPage.tsx  Main /play dashboard
├── components/BettingBoard.tsx
├── components/LiveStreamPanel.tsx
├── components/Layout.tsx
├── api.ts                   REST client + session storage
├── useLive.ts               WebSocket subscription
└── types.ts                 Shared TS types mirroring backend
```

Env (`.env`):

```
VITE_API_URL=http://localhost:8787
VITE_WS_URL=ws://localhost:8787/ws/web
```

Session token stored in `localStorage` key `bwcash_token`.

---

## Environment variables (backend)

See `backend/.env.example`. Key vars:

| Variable | Default | Notes |
|----------|---------|-------|
| `PORT` | 8787 | HTTP + WS |
| `PLUGIN_TOKEN` | — | Must match plugin config |
| `APP_SECRET` | — | Session signing, key encryption, admin auth |
| `MATCH_PLAYER_COUNT` | 16 | Set to 4 with `TEAM_SIZE=1` for solo testing |
| `TEAM_SIZE` | 4 | Max party size and team capacity |
| `BETTING_RAKE` | 0.05 | 5% spectator pool rake |
| `HOUSE_RESERVE_SOL` | 0.5 | Floor balance house keeps |
| `STREAM_URL` | empty | HLS/embed URL for website stream panel |
| `MIN_WALLET_AGE_HOURS` | 24 | Deposit anti-abuse (wallet age check) |
| `MANUAL_REVIEW_THRESHOLD_SOL` | 5 | Large withdrawals held for review |

---

## Local dev workflow

### Start everything

```powershell
# Terminal 1 — backend
cd backend
npm install
cp .env.example .env   # edit secrets
npm run dev

# Terminal 2 — web
cd web
npm install
npm run dev

# Terminal 3 — Minecraft (Java 25)
cd server
java -Xms2G -Xmx4G -jar paper.jar nogui
```

### Quick test match (4 players)

```powershell
cd backend
$env:MATCH_PLAYER_COUNT=4; $env:TEAM_SIZE=1; npm run dev
```

Fund house wallet (required for player rewards):

```powershell
node scripts/fund.mjs <houseAddress> 1
# or POST /api/admin/topup with x-admin-token header
# or POST /api/admin/credit to bypass faucet for user balances
```

### Test without Minecraft

```bash
cd backend
node scripts/e2e_bet.mjs    # parimutuel settlement
node scripts/e2e.mjs        # full match + on-chain reward payout
```

---

## Optional: live streaming

1. Dedicated MC account (`BWC_Cast`) stays logged in during matches.
2. Plugin `broadcast.enabled: true` — rotates spectating across teams.
3. Capture that client with OBS → RTMP/HLS server (e.g. OvenMediaEngine Docker).
4. Set `STREAM_URL` in backend `.env` (e.g. `http://localhost:8080/live/match.m3u8`).

Website `LiveStreamPanel` shows the stream during `lobby`, `live`, `starting`, and `settling` phases.

---

## Anti-abuse (implemented)

| Mechanism | Where |
|-----------|-------|
| Players in queue cannot bet on current match | `match.ts placeBet()` |
| Win cooldown (1 match) | `win_cooldown` table |
| Deposit wallet age check | `custody.ts walletAgeHours()` |
| Large withdrawal manual review | `custody.ts houseTransfer()` |
| CPS autoclicker flag | Plugin `CombatListener` → `cheat_flag` |
| GrimAC integration | Soft-depend in `plugin.yml` |

---

## Known limitations / not done

- **Mainnet / real money** — not supported; legal and custody hardening required
- **Pump.fun fee sweep** — stub only (`PumpFunFeeSource` throws); devnet uses mock top-ups
- **Parties** — in-memory; lost on backend restart
- **Custom arena maps** — lobby import works; arena custom template marked "coming soon" in config comments
- **BedWars2023 hook** — legacy code path; primary game is embedded `GameManager`
- **Devnet faucet** — frequently rate-limited (429); use admin credit endpoint for demos
- **Multiple concurrent matches** — single match at a time by design
- **README drift** — root `README.md` and `server/README.md` still mention BedWars2026 as required; current setup uses embedded BedWars only + GrimAC

---

## File index (important paths)

```
backend/src/
  index.ts              Server entry, deposit poll loop
  routes.ts             REST endpoints
  ws/hub.ts             WebSocket handlers
  services/match.ts     Match lifecycle (central)
  services/parimutuel.ts
  services/accounts.ts
  services/party.ts
  solana/custody.ts     Wallets, deposits, withdrawals
  solana/treasury.ts    Reward pool sizing
  db/index.ts           Schema

plugins/bedwarscash/src/main/java/cash/bedwars/
  BedWarsCashPlugin.java
  BackendClient.java
  game/GameManager.java
  world/WorldManager.java

web/src/pages/DashboardPage.tsx
server/plugins/BedWarsCash/config.yml   Runtime plugin config
backend/data/bedwars.sqlite             Runtime DB (gitignored)
backend/wallets/house.json              House keypair (gitignored)
```

---

## Production checklist (future, out of scope)

- Legal review for spectator betting jurisdiction
- KMS/HSM for house and deposit key management
- Implement `PumpFunFeeSource` with `@pump-fun/pump-sdk` on mainnet
- Persist parties; horizontal scaling / Redis for WS fan-out
- Rate limiting, audit logging, monitoring
- Security review of custodial encryption (`APP_SECRET` rotation)
- Harden admin endpoints (remove devnet credit in production)

---

*Last updated: June 2026 — reflects BedWarsCash v0.1.0 embedded BedWars architecture.*
