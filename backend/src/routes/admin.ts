import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { z } from 'zod';
import { config, adminEnabled, isDevnet } from '../config.js';
import { db } from '../db/index.js';
import {
  audit,
  isLockedOut,
  login,
  recentAudit,
  revoke,
  sessionFromToken,
  type AdminSession,
} from '../services/admin.js';
import {
  forceAbortCurrent,
  forceStartMatch,
  publicState,
  purgeQueue,
  queueUuids,
} from '../services/match.js';
import { applyDelta, getUserById, totalLiabilitiesLamports } from '../services/accounts.js';
import { houseAddress, houseBalanceLamports } from '../solana/custody.js';
import { availableRewardPool, mockTopUp } from '../solana/treasury.js';
import { pluginConnected } from '../ws/hub.js';
import { lamportsToSol, solToLamports } from '../util/money.js';

const COOKIE = 'admin_session';
const COOKIE_PATH = '/api/admin/panel';

function readCookie(req: FastifyRequest, name: string): string | undefined {
  const header = req.headers.cookie;
  if (!header) return undefined;
  for (const part of header.split(';')) {
    const [k, ...v] = part.trim().split('=');
    if (k === name) return decodeURIComponent(v.join('='));
  }
  return undefined;
}

function setSessionCookie(reply: FastifyReply, token: string): void {
  const maxAge = config.admin.sessionTtlMinutes * 60;
  reply.header(
    'set-cookie',
    `${COOKIE}=${token}; HttpOnly; Secure; SameSite=Strict; Path=${COOKIE_PATH}; Max-Age=${maxAge}`,
  );
}

function clearSessionCookie(reply: FastifyReply): void {
  reply.header('set-cookie', `${COOKIE}=; HttpOnly; Secure; SameSite=Strict; Path=${COOKIE_PATH}; Max-Age=0`);
}

// Auth guard: valid session cookie required; mutating requests also need a matching
// CSRF header (the token is only ever returned in the login response body).
function guard(req: FastifyRequest, reply: FastifyReply): AdminSession | null {
  if (!adminEnabled()) {
    reply.code(404).send({ error: 'not_found' });
    return null;
  }
  const session = sessionFromToken(readCookie(req, COOKIE));
  if (!session) {
    reply.code(401).send({ error: 'unauthorized' });
    return null;
  }
  if (req.method !== 'GET' && req.headers['x-csrf-token'] !== session.csrf) {
    reply.code(403).send({ error: 'bad_csrf' });
    return null;
  }
  return session;
}

export function registerAdminRoutes(app: FastifyInstance): void {
  // ── Auth ──
  const loginBody = z.object({ username: z.string().min(1).max(64), password: z.string().min(1).max(256) });

  app.post('/api/admin/panel/login', async (req, reply) => {
    if (!adminEnabled()) return reply.code(404).send({ error: 'not_found' });
    const ip = req.ip;
    const until = isLockedOut(ip);
    if (until) {
      return reply.code(429).send({ error: 'locked_out', retryAfterMs: until - Date.now() });
    }
    const parsed = loginBody.safeParse(req.body);
    if (!parsed.success) return reply.code(400).send({ error: 'bad_request' });
    const session = login(parsed.data.username, parsed.data.password, ip);
    if (!session) {
      audit(parsed.data.username, 'login_failed', '', ip);
      return reply.code(401).send({ error: 'invalid_credentials' });
    }
    audit(session.username, 'login', '', ip);
    setSessionCookie(reply, session.token);
    return { ok: true, username: session.username, csrf: session.csrf };
  });

  app.post('/api/admin/panel/logout', async (req, reply) => {
    const session = sessionFromToken(readCookie(req, COOKIE));
    if (session) {
      revoke(session.token);
      audit(session.username, 'logout', '', req.ip);
    }
    clearSessionCookie(reply);
    return { ok: true };
  });

  app.get('/api/admin/panel/me', async (req, reply) => {
    const session = guard(req, reply);
    if (!session) return;
    return { username: session.username, csrf: session.csrf };
  });

  // ── Overview / solvency ──
  app.get('/api/admin/panel/overview', async (req, reply) => {
    if (!guard(req, reply)) return;
    const state = publicState();
    const balance = await houseBalanceLamports().catch(() => 0n);
    const pool = await availableRewardPool().catch(() => 0n);
    const liabilities = totalLiabilitiesLamports();
    return {
      phase: state.match?.phase ?? 'idle',
      matchId: state.match?.id ?? null,
      queue: state.queue,
      pluginConnected: pluginConnected(),
      houseAddress: houseAddress(),
      houseBalanceSol: lamportsToSol(balance),
      availableRewardPoolSol: lamportsToSol(pool),
      liabilitiesSol: lamportsToSol(liabilities),
      solvent: balance >= liabilities,
      cluster: config.solana.cluster,
    };
  });

  // ── Queue & match control ──
  app.get('/api/admin/panel/queue', async (req, reply) => {
    if (!guard(req, reply)) return;
    return { players: queueUuids() };
  });

  app.post('/api/admin/panel/queue/clear', async (req, reply) => {
    const session = guard(req, reply);
    if (!session) return;
    const removed = purgeQueue();
    audit(session.username, 'queue_clear', `removed ${removed}`, req.ip);
    return { ok: true, removed };
  });

  app.post('/api/admin/panel/match/force-start', async (req, reply) => {
    const session = guard(req, reply);
    if (!session) return;
    const r = await forceStartMatch();
    audit(session.username, 'match_force_start', r.message, req.ip);
    return r;
  });

  const abortBody = z.object({ reason: z.string().max(200).optional() });
  app.post('/api/admin/panel/match/abort', async (req, reply) => {
    const session = guard(req, reply);
    if (!session) return;
    const reason = abortBody.safeParse(req.body).data?.reason ?? 'Admin abort';
    const r = forceAbortCurrent(reason);
    audit(session.username, 'match_abort', `${reason} — ${r.message}`, req.ip);
    return r;
  });

  // ── Moderation: cheat flags ──
  app.get('/api/admin/panel/cheat-flags', async (req, reply) => {
    if (!guard(req, reply)) return;
    const rows = db
      .prepare(
        `SELECT cf.mc_uuid AS mcUuid, u.mc_username AS username, cf.check_name AS checkName, cf.details, cf.created_at AS createdAt
         FROM cheat_flags cf LEFT JOIN users u ON u.mc_uuid = cf.mc_uuid
         ORDER BY cf.id DESC LIMIT 100`,
      )
      .all();
    return { flags: rows };
  });

  // ── Players ──
  app.get('/api/admin/panel/players', async (req, reply) => {
    if (!guard(req, reply)) return;
    const rows = db
      .prepare(
        `SELECT id, mc_username AS mcUsername, mc_uuid AS mcUuid, payout_wallet AS payoutWallet,
                balance_lamports AS balanceLamports, created_at AS createdAt
         FROM users ORDER BY id DESC LIMIT 200`,
      )
      .all() as Array<{ balanceLamports: string; [k: string]: unknown }>;
    return {
      players: rows.map((r) => ({ ...r, balanceSol: lamportsToSol(BigInt(r.balanceLamports)) })),
    };
  });

  const creditBody = z.object({ userId: z.number().int().positive(), sol: z.number().finite() });
  app.post('/api/admin/panel/players/credit', async (req, reply) => {
    const session = guard(req, reply);
    if (!session) return;
    const parsed = creditBody.safeParse(req.body);
    if (!parsed.success) return reply.code(400).send({ error: 'bad_request' });
    const user = getUserById(parsed.data.userId);
    if (!user) return reply.code(404).send({ error: 'user_not_found' });
    const delta = solToLamports(Math.abs(parsed.data.sol)) * (parsed.data.sol < 0 ? -1n : 1n);
    try {
      const next = applyDelta(user.id, delta, 'admin_adjust');
      audit(session.username, 'player_credit', `user ${user.id} ${parsed.data.sol} SOL`, req.ip);
      return { ok: true, balanceSol: lamportsToSol(next) };
    } catch (e) {
      return reply.code(400).send({ error: (e as Error).message });
    }
  });

  // ── Held withdrawals (review) ──
  app.get('/api/admin/panel/withdrawals/held', async (req, reply) => {
    if (!guard(req, reply)) return;
    const rows = db
      .prepare(
        `SELECT p.id, p.user_id AS userId, u.mc_username AS username, p.amount_lamports AS amountLamports,
                p.destination, p.created_at AS createdAt
         FROM payouts p LEFT JOIN users u ON u.id = p.user_id
         WHERE p.kind = 'withdraw' AND p.status = 'held'
         ORDER BY p.id DESC LIMIT 100`,
      )
      .all() as Array<{ amountLamports: string; [k: string]: unknown }>;
    return {
      withdrawals: rows.map((r) => ({ ...r, amountSol: lamportsToSol(BigInt(r.amountLamports)) })),
    };
  });

  // Reject a held withdrawal: refund the reserved amount back to the user's balance.
  app.post<{ Params: { id: string } }>('/api/admin/panel/withdrawals/:id/reject', async (req, reply) => {
    const session = guard(req, reply);
    if (!session) return;
    const row = db
      .prepare("SELECT id, user_id, amount_lamports, status FROM payouts WHERE id = ? AND kind = 'withdraw'")
      .get(Number(req.params.id)) as
      | { id: number; user_id: number; amount_lamports: string; status: string }
      | undefined;
    if (!row || row.status !== 'held') return reply.code(404).send({ error: 'not_found_or_settled' });
    const tx = db.transaction(() => {
      applyDelta(row.user_id, BigInt(row.amount_lamports), 'withdraw_rejected_refund');
      db.prepare("UPDATE payouts SET status = 'rejected' WHERE id = ?").run(row.id);
    });
    tx();
    audit(session.username, 'withdrawal_reject', `payout ${row.id} refunded`, req.ip);
    return { ok: true };
  });

  // ── Treasury (devnet mock funding) ──
  const topupBody = z.object({ sol: z.number().positive().max(100) });
  app.post('/api/admin/panel/treasury/topup', async (req, reply) => {
    const session = guard(req, reply);
    if (!session) return;
    if (!isDevnet()) return reply.code(400).send({ error: 'devnet_only' });
    const parsed = topupBody.safeParse(req.body);
    if (!parsed.success) return reply.code(400).send({ error: 'bad_request' });
    await mockTopUp(parsed.data.sol);
    audit(session.username, 'treasury_topup', `${parsed.data.sol} SOL`, req.ip);
    return { ok: true };
  });

  // ── Audit log ──
  app.get('/api/admin/panel/audit', async (req, reply) => {
    if (!guard(req, reply)) return;
    return { entries: recentAudit(150) };
  });
}
