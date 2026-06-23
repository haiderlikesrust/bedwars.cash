# BedWars.cash (devnet)

A skill-based BedWars wagering platform on **Solana devnet** (free test SOL, zero real-money / legal exposure).

- One live match at a time: **4 teams** (Green / Blue / Red / Yellow), 4 players each (16 total).
- **Player reward pool** funded from Pump.fun creator fees (mocked on devnet). The winning team of 4 splits it equally.
- **Spectator parimutuel betting pool** (separate): bettors on the winning team split 95% of the combined pool in proportion to stake (5% house rake). Dynamic odds, not fixed multipliers.
- Custodial model: each user gets a generated deposit wallet; balances + parimutuel math live in the backend DB; the backend signs withdrawals/payouts from a house hot wallet.

> Real-money mainnet operation would require legal review (spectator betting is gambling on a third-party outcome), security review, and hardened custody. This repo targets devnet only.

## Monorepo layout

| Path | What |
| --- | --- |
| `backend/` | Node + TypeScript API: custodial wallets, match lifecycle, parimutuel betting, settlement, treasury |
| `plugins/bedwarscash/` | Paper plugin: `/setwallet`, in-game betting GUI, match orchestration, anti-cheat hooks |
| `server/` | Paper 26.1.2 server runtime, Java 25 (BedWars2026 fork + GrimAC) - jars are gitignored |
| `web/` | React (Vite) app `bedwars.cash/bet`: onboarding, deposit/withdraw, live odds, leaderboard |
| `docs/` | Setup and run notes |

## Prerequisites

- Java 25 (required by Paper 26.1.2) to run the server; the plugin build auto-downloads a JDK 25 toolchain
- Node.js 20+ and npm
- Solana CLI (optional, for funding test wallets)

## Quick start

See [docs/SETUP.md](docs/SETUP.md) for full instructions. In short:

```bash
# backend
cd backend && npm install && cp .env.example .env && npm run dev

# web
cd web && npm install && npm run dev

# minecraft server
# see docs/SETUP.md to download Paper + BedWars2023 + GrimAC into server/
```

## Status

Active build. See the project plan for milestones.
