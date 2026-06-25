# BedWars.cash - Setup & Run (devnet)

Everything runs on **Solana devnet** with free test SOL. No real money.

> **How the game works** (match flow, plugin, rewards, betting, commands): see **[GAME.md](./GAME.md)**.

## Components

1. **backend/** - Node + TypeScript API + WebSocket hub (custodial wallets, matchmaking, parimutuel betting, settlement).
2. **web/** - React (Vite) app: spectator onboarding, deposit/withdraw, live odds, leaderboard.
3. **plugins/bedwarscash/** - Paper plugin: `/setwallet`, `/bet`, `/queue`, match orchestration, anti-cheat hooks.
4. **server/** - Paper 26.1.2 server (**Java 25**). **BedWars.cash** plugin only (embedded BedWars + void lobby) + GrimAC.

## 1. Backend

```bash
cd backend
npm install
cp .env.example .env        # edit PLUGIN_TOKEN + APP_SECRET for anything public
npm run dev                 # starts on http://localhost:8787
npm test                    # parimutuel math unit tests
```

On first run it generates a **house hot wallet** and prints its address. Fund it on devnet (see below). The reward pool for each match = house balance minus `HOUSE_RESERVE_SOL`, minus custodial liabilities.

### Funding devnet test SOL

The public devnet faucet (`requestAirdrop`) is frequently rate-limited. Options:

- `POST /api/admin/topup { "sol": 2 }` with header `x-admin-token: <APP_SECRET>` (uses the airdrop faucet; may 429).
- `node scripts/fund.mjs <houseAddress> 1` (retries the faucet).
- Solana CLI: `solana airdrop 1 <houseAddress> --url devnet`.
- Web faucet: https://faucet.solana.com (paste the house address).
- Any QuickNode/Helius devnet faucet.

If the faucet is dry, you can still demo balances with the devnet-only endpoint
`POST /api/admin/credit { "userId": N, "sol": 1 }` (header `x-admin-token`).

## 2. Web app

```bash
cd web
npm install
cp .env.example .env         # points at the backend by default
npm run dev                  # http://localhost:5173
```

Open it, and a custodial account + deposit wallet is created automatically. Deposit devnet SOL to the shown address; it is credited within ~15s. Generate a link code and run `/bwlink <code>` in-game to bind your Minecraft account. Withdraw to any address.

## 3. Plugin

```bash
cd plugins/bedwarscash
./gradlew build              # produces build/libs/bedwarscash-0.1.0.jar
```

The build compiles against the Paper 26.1.2 API using a **JDK 25 toolchain** (Gradle auto-downloads it via the foojay resolver). Gson is fetched at runtime via the `libraries` block in `plugin.yml`, so the jar is not shaded.

Copy the jar into `server/plugins/`. Edit `server/plugins/BedWarsCash/config.yml` (created on first run) so `backend.token` matches the backend `PLUGIN_TOKEN`.

## 4. Minecraft server

> **Java 25 is mandatory.** Minecraft/Paper 26.1+ refuses to start on Java 24 or lower (`UnsupportedClassVersionError`). Install Temurin/OpenJDK 25 and make sure `java -version` reports 25 before launching.

1. Download Paper 26.1.2 into `server/`:
   - https://papermc.io/downloads/paper -> `paper-26.1.2-XX.jar` (rename to `paper.jar`).
2. Download plugins into `server/plugins/`:
   - **bedwarscash-0.1.0.jar** (built above) — includes void lobby + embedded BedWars game
   - **GrimAC**: https://modrinth.com/plugin/grimac (26.1-compatible build)
   - Do **not** install BedWars2023/2026 — BedWars.cash replaces it
3. First run to generate configs:
   ```bash
   cd server
   java -Xms2G -Xmx4G -jar paper.jar nogui    # must be Java 25
   ```
   Accept the EULA (`eula=true` in `eula.txt`), set `online-mode=true` in `server.properties`.
4. Arena and lobby are **built-in** (procedural void worlds) or imported via `BedWarsCash/config.yml`. See [GAME.md](./GAME.md#custom-maps) for custom maps. No external BedWars plugin is required.

### Reporting the winner across BedWars forks

If your BedWars fork exposes the `com.tomkeuper` `GameEndEvent`, the plugin reports the winning team automatically. If it doesn't (e.g. the horiciastko fork or any API mismatch), an op can report it manually with:

```
/bwresult <green|blue|red|yellow>
```

This uses the team roster the backend assigned at match start, so settlement and reward payout work on any server version.

## Player & spectator flow

- **Players**: join → teleported to **`bwc_lobby`** (void world, glass platform, no mobs, Hypixel-style hub) → auto-queued. Match starts when queue fills; fighters teleport to **`bwc_arena`** (4 team islands, beds, PvP).
- **Late joiners / spectators**: if a match is already **live**, joining puts you in **spectator mode** immediately so you can watch and `/bet`.
- **Manual queue**: `/queue` and `/queue leave` still work. Toggle auto-queue in `BedWarsCash/config.yml` (`join.auto-queue`).
- **Spectators (web)**: go to the web app, deposit, link the account (`/bwlink`), then `/bet <team> <amountSol>` in-game while in the lobby or as spectator. Betting locks at match start. Winners split 95% of the pool (5% house rake).

## Live stream on the website (optional)

The plugin can **rotate an in-game broadcast camera** across teams during live matches. Video still comes from a normal Minecraft client + capture tool — the plugin only controls *who* that client watches.

1. Create a dedicated Minecraft account (e.g. `BWC_Cast`) and keep a client logged in on the server during matches.
2. In `BedWarsCash/config.yml`:
   ```yaml
   broadcast:
     enabled: true
     username: "BWC_Cast"
     seconds-per-team: 120
   ```
3. Capture that client with **OBS** or **ffmpeg** and push to your media server (OvenMediaEngine, LiveKit, nginx-rtmp, etc.).
4. Set the playback URL in the backend `.env`:
   ```
   STREAM_URL=https://your-server/live/match.m3u8
   ```
   HLS (`.m3u8`) uses a `<video>` tag; any other URL is embedded in an iframe.

During `live` / `starting` / `settling` phases, the Arena dashboard shows the stream and which team/player the cast camera is following. With `broadcast.enabled: false` (default), nothing changes — existing matchmaking and betting behave as before.

## Testing without a Minecraft server

The plugin protocol is plain JSON over WebSocket, so you can drive a full match from Node:

```bash
cd backend
# start backend with a tiny match for testing:
#   $env:MATCH_PLAYER_COUNT=4; $env:TEAM_SIZE=1; npm run dev   (PowerShell)
node scripts/e2e_bet.mjs     # verifies parimutuel settlement end-to-end
node scripts/e2e.mjs         # verifies match start + on-chain reward payout (needs a funded house)
```

## Production / mainnet notes (not in scope here)

- Real-money mainnet requires legal review (spectator betting is gambling on a third-party outcome), a security review of custodial key handling, and a hardened hot-wallet/KMS setup.
- Set `FEE_SOURCE=pumpfun` and implement the `PumpFunFeeSource.collect()` sweep with `@pump-fun/pump-sdk` (Pump.fun is mainnet-only).
