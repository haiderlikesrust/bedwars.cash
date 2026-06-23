import Fastify from 'fastify';
import cors from '@fastify/cors';
import websocket from '@fastify/websocket';
import { config } from './config.js';
import { initSchema } from './db/index.js';
import { ensureLobby } from './services/match.js';
import { registerWs } from './ws/hub.js';
import { registerRoutes } from './routes.js';
import { houseAddress, houseBalanceLamports, scanDeposits } from './solana/custody.js';
import { availableRewardPool } from './solana/treasury.js';
import { lamportsToSol } from './util/money.js';

async function main() {
  initSchema();
  ensureLobby();

  const app = Fastify({
    logger: {
      transport: { target: 'pino-pretty', options: { translateTime: 'HH:MM:ss', ignore: 'pid,hostname' } },
    },
  });

  await app.register(cors, { origin: config.webOrigin, credentials: true });
  await app.register(websocket);
  registerWs(app);
  registerRoutes(app);

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
