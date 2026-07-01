import Fastify from 'fastify';
import cors from '@fastify/cors';
import websocket from '@fastify/websocket';
import { config, adminEnabled } from './config.js';
import { initSchema } from './db/index.js';
import { ensureLobby, purgeQueue } from './services/match.js';
import { registerWs } from './ws/hub.js';
import { registerRoutes } from './routes.js';
import { registerAdminRoutes } from './routes/admin.js';
import { houseAddress, houseBalanceLamports, scanDeposits } from './solana/custody.js';
import { availableRewardPool } from './solana/treasury.js';
import { lamportsToSol } from './util/money.js';

async function main() {
  initSchema();
  ensureLobby();
  // Queue membership is ephemeral; drop anything left over from a previous run so
  // disconnected players never linger as phantom queue slots.
  purgeQueue();

  const app = Fastify({
    // Behind Caddy/nginx: trust X-Forwarded-For so admin audit/rate-limit see real client IPs.
    trustProxy: true,
    logger: {
      transport: { target: 'pino-pretty', options: { translateTime: 'HH:MM:ss', ignore: 'pid,hostname' } },
    },
  });

  await app.register(cors, { origin: config.webOrigin, credentials: true });
  await app.register(websocket);
  registerWs(app);
  registerRoutes(app);
  registerAdminRoutes(app);
  if (adminEnabled()) {
    app.log.info('Admin panel enabled (/api/admin/panel).');
  } else {
    app.log.warn('Admin panel DISABLED — set ADMIN_PASSWORD_HASH to enable it.');
  }

  await app.listen({ port: config.port, host: '0.0.0.0' });

  app.log.info(`House wallet: ${houseAddress()}`);
  try {
    const bal = await houseBalanceLamports();
    const pool = await availableRewardPool();
    app.log.info(`House balance: ${lamportsToSol(bal)} SOL | Reward pool available: ${lamportsToSol(pool)} SOL`);
    if (bal === 0n) {
      app.log.warn('House wallet is empty. Fund it on devnet (POST /api/admin/topup or solana airdrop).');
    }
  } catch (err) {
    app.log.warn(`Could not read house balance (RPC issue?): ${(err as Error).message}`);
  }

  // Poll devnet for deposits into custodial wallets and sweep them to the house wallet.
  const loop = async () => {
    try {
      const res = await scanDeposits();
      for (const r of res) {
        app.log.info(`Credited user ${r.userId} with ${lamportsToSol(r.creditedLamports)} SOL (deposit)`);
      }
    } catch (err) {
      app.log.error(`deposit scan failed: ${(err as Error).message}`);
    }
  };
  setInterval(() => void loop(), 15_000);
  void loop();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
