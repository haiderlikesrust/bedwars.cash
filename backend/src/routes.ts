import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { z } from 'zod';
import { config, isDevnet } from './config.js';
import { db } from './db/index.js';
import {
  createLinkCode,
  createSession,
  createEmptyUser,
  getBalance,
  getUserById,
  userIdForToken,
} from './services/accounts.js';
import { buildOdds, forceAbortCurrent, forceStartMatch, publicState } from './services/match.js';
import { ensureDepositWallet, houseAddress, houseBalanceLamports, houseTransfer } from './solana/custody.js';
import { availableRewardPool, mockTopUp } from './solana/treasury.js';
import { combatLeaderboard, getPlayerStats, sweatzoneLeaderboard } from './services/playerStats.js';
import { pluginConnected } from './ws/hub.js';
import { formatSol, lamportsToSol, solToLamports } from './util/money.js';

function authUserId(req: FastifyRequest): number | null {
  const header = req.headers['authorization'];
  if (!header || !header.startsWith('Bearer ')) return null;
  return userIdForToken(header.slice('Bearer '.length).trim());
}

function requireAdmin(req: FastifyRequest, reply: FastifyReply): boolean {
  if (req.headers['x-admin-token'] !== config.appSecret) {
    reply.code(401).send({ error: 'unauthorized' });
    return false;
  }
  return true;
}

export function registerRoutes(app: FastifyInstance): void {
  app.get('/api/health', async () => ({
    ok: true,
    cluster: config.solana.cluster,
    pluginConnected: pluginConnected(),
  }));

  // Create an anonymous website account + session and provision a deposit wallet.
  app.post('/api/session', async () => {
    const userId = createEmptyUser();
    const token = createSession(userId);
    const depositAddress = ensureDepositWallet(userId);
    return { token, userId, depositAddress };
  });

  app.get('/api/me', async (req, reply) => {
    const userId = authUserId(req);
    if (!userId) return reply.code(401).send({ error: 'unauthorized' });
    const user = getUserById(userId);
    if (!user) return reply.code(404).send({ error: 'not_found' });
    const depositAddress = user.depositPubkey ?? ensureDepositWallet(userId);
    return {
      userId: user.id,
      mcUsername: user.mcUsername,
      linked: !!user.mcUuid,
      depositAddress,
      balanceLamports: user.balanceLamports.toString(),
      balanceSol: lamportsToSol(user.balanceLamports),
    };
  });

  // Generate a code the user types in-game to link their Minecraft account.
  app.post('/api/link/code', async (req, reply) => {
    const userId = authUserId(req);
    if (!userId) return reply.code(401).send({ error: 'unauthorized' });
    return { code: createLinkCode(userId) };
  });

  const withdrawBody = z.object({
    destination: z.string().min(32).max(64),
    amountSol: z.number().positive(),
  });

  app.post('/api/withdraw', async (req, reply) => {
    const userId = authUserId(req);
    if (!userId) return reply.code(401).send({ error: 'unauthorized' });
    const parsed = withdrawBody.safeParse(req.body);
    if (!parsed.success) return reply.code(400).send({ error: 'bad_request', details: parsed.error.format() });

    const amount = solToLamports(parsed.data.amountSol);
    if (getBalance(userId) < amount) return reply.code(400).send({ error: 'insufficient_balance' });

    // Reserve funds first, then send; refund on failure.
    const { applyDelta } = await import('./services/accounts.js');
    applyDelta(userId, -amount, 'withdraw_reserve');
    try {
      const result = await houseTransfer(parsed.data.destination, amount, 'withdraw', userId);
      if (result.status === 'failed') {
        applyDelta(userId, amount, 'withdraw_refund_failed');
        return reply.code(502).send({ error: 'transfer_failed' });
      }
      return { status: result.status, signature: result.signature };
    } catch {
      applyDelta(userId, amount, 'withdraw_refund_error');
      return reply.code(502).send({ error: 'transfer_error' });
    }
  });

  app.get('/api/odds', async () => buildOdds());
  app.get('/api/state', async () => publicState());

  app.get('/api/house', async () => ({
    address: houseAddress(),
    balanceSol: lamportsToSol(await houseBalanceLamports()),
    availableRewardPoolSol: lamportsToSol(await availableRewardPool()),
  }));

  app.get<{ Params: { mcUuid: string } }>('/api/stats/:mcUuid', async (req) => {
    const stats = getPlayerStats(req.params.mcUuid);
    if (!stats) return { stats: null };
    return { stats };
  });

  app.get('/api/sweatzone', async () => ({
    players: sweatzoneLeaderboard(),
  }));

  app.get('/api/leaderboard', async () => {
    // Top bettors by net profit across all bet-related ledger entries.
    const bettors = db
      .prepare(
        `SELECT u.mc_username AS name, COALESCE(SUM(CAST(l.delta_lamports AS INTEGER)), 0) AS net
         FROM ledger l JOIN users u ON u.id = l.user_id
         WHERE l.reason LIKE 'bet%'
         GROUP BY l.user_id
         HAVING name IS NOT NULL
         ORDER BY net DESC LIMIT 10`,
      )
      .all() as { name: string; net: number }[];

    // Top players by total reward winnings.
    const players = db
      .prepare(
        `SELECT u.mc_username AS name, COALESCE(SUM(CAST(p.amount_lamports AS INTEGER)), 0) AS won
         FROM payouts p JOIN users u ON u.id = p.user_id
         WHERE p.kind = 'reward'
         GROUP BY p.user_id
         HAVING name IS NOT NULL
         ORDER BY won DESC LIMIT 10`,
      )
      .all() as { name: string; won: number }[];

    return {
      bettors: bettors.map((b) => ({ name: b.name, netProfitSol: lamportsToSol(BigInt(b.net)) })),
      players: players.map((p) => ({ name: p.name, wonSol: lamportsToSol(BigInt(p.won)) })),
      ...combatLeaderboard(),
    };
  });

  // ---- Admin (devnet mock funding) ----
  const topupBody = z.object({ sol: z.number().positive().max(100) });
  app.post('/api/admin/topup', async (req, reply) => {
    if (!requireAdmin(req, reply)) return;
    if (!isDevnet()) return reply.code(400).send({ error: 'devnet_only' });
    const parsed = topupBody.safeParse(req.body);
    if (!parsed.success) return reply.code(400).send({ error: 'bad_request' });
    const added = await mockTopUp(parsed.data.sol);
    return { addedSol: lamportsToSol(added), houseBalanceSol: formatSol(await houseBalanceLamports()) };
  });

  // Devnet-only: credit a user's custodial balance directly. Useful for demos and
  // tests when the public devnet faucet is rate-limited. Never enable on mainnet.
  const creditBody = z.object({ userId: z.number().int().positive(), sol: z.number().positive().max(100) });
  app.post('/api/admin/credit', async (req, reply) => {
    if (!requireAdmin(req, reply)) return;
    if (!isDevnet()) return reply.code(400).send({ error: 'devnet_only' });
    const parsed = creditBody.safeParse(req.body);
    if (!parsed.success) return reply.code(400).send({ error: 'bad_request' });
    const { applyDelta } = await import('./services/accounts.js');
    const next = applyDelta(parsed.data.userId, solToLamports(parsed.data.sol), 'admin_credit_devnet');
    return { userId: parsed.data.userId, balanceSol: lamportsToSol(next) };
  });

  app.post('/api/admin/force-start', async (req, reply) => {
    if (!requireAdmin(req, reply)) return;
    const r = await forceStartMatch();
    if (!r.ok) return reply.code(400).send({ error: r.message });
    return { ok: true, message: r.message, state: publicState() };
  });

  app.post('/api/admin/abort', async (req, reply) => {
    if (!requireAdmin(req, reply)) return;
    const reason = typeof (req.body as { reason?: string })?.reason === 'string'
        ? (req.body as { reason: string }).reason
        : 'Admin abort';
    const r = forceAbortCurrent(reason);
    if (!r.ok) return reply.code(400).send({ error: r.message });
    return { ok: true, message: r.message, state: publicState() };
  });
}
