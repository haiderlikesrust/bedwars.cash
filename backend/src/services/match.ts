import { EventEmitter } from 'node:events';
import { db, kvGet, kvSet } from '../db/index.js';
import { config, TEAM_COLORS, type TeamColor } from '../config.js';
import type { JoinAction, MatchPhase, MatchRow, OddsView } from '../types.js';
import {
  applyDelta,
  getBalance,
  getOrCreateByUuid,
  getUserById,
  getUserByUuid,
} from './accounts.js';
import {
  computeMultipliers,
  refundAll,
  settleParimutuel,
  splitRewardPool,
  tallyTeams,
  type SimpleBet,
} from './parimutuel.js';
import { availableRewardPool } from '../solana/treasury.js';
import { houseTransfer } from '../solana/custody.js';
import { solToLamports } from '../util/money.js';
import { clearBroadcastCamera, streamPublicView } from './broadcast.js';
import {
  assignTeamsWithParties,
  partyAccept,
  partyInvite,
  partyLeave,
  partyList,
  partyFor,
} from './party.js';

export const matchEvents = new EventEmitter();

interface MatchRowRaw {
  id: number;
  phase: MatchPhase;
  reward_pool_lamports: string;
  winning_team: TeamColor | null;
  created_at: number;
  started_at: number | null;
  ended_at: number | null;
}

function toMatch(r: MatchRowRaw): MatchRow {
  return {
    id: r.id,
    phase: r.phase,
    rewardPoolLamports: BigInt(r.reward_pool_lamports),
    winningTeam: r.winning_team,
    createdAt: r.created_at,
    startedAt: r.started_at,
    endedAt: r.ended_at,
  };
}

export function currentMatch(): MatchRow | null {
  const id = kvGet('current_match');
  if (!id) return null;
  const r = db.prepare('SELECT * FROM matches WHERE id = ?').get(Number(id)) as MatchRowRaw | undefined;
  return r ? toMatch(r) : null;
}

function openLobby(): MatchRow {
  const info = db
    .prepare("INSERT INTO matches (phase, reward_pool_lamports, created_at) VALUES ('lobby', '0', ?)")
    .run(Date.now());
  const id = Number(info.lastInsertRowid);
  kvSet('current_match', String(id));
  const m = currentMatch()!;
  broadcastState();
  return m;
}

// Ensure there is always a joinable lobby when idle.
export function ensureLobby(): MatchRow {
  const m = currentMatch();
  if (!m || m.phase === 'settling' || m.phase === 'idle') return openLobby();
  return m;
}

// ---- Queue ----
export function queueUuids(): string[] {
  const rows = db.prepare('SELECT mc_uuid FROM queue ORDER BY joined_at ASC').all() as { mc_uuid: string }[];
  return rows.map((r) => r.mc_uuid);
}

function inCooldown(uuid: string): boolean {
  const r = db.prepare('SELECT matches_remaining FROM win_cooldown WHERE mc_uuid = ?').get(uuid) as
    | { matches_remaining: number }
    | undefined;
  return !!r && r.matches_remaining > 0;
}

export function joinQueue(uuid: string): {
  ok: boolean;
  message: string;
  action?: JoinAction;
  queueSize?: number;
  queueCapacity?: number;
  phase?: MatchPhase;
} {
  const m = ensureLobby();
  const capacity = config.game.matchPlayerCount;
  if (m.phase !== 'lobby') {
    return {
      ok: false,
      message: 'A match is in progress. Spectate and use /bet.',
      action: 'spectate',
      queueSize: queueUuids().length,
      queueCapacity: capacity,
      phase: m.phase,
    };
  }
  if (inCooldown(uuid)) {
    return {
      ok: false,
      message: 'You won last match — sit out one round (spectating).',
      action: 'spectate',
      queueSize: queueUuids().length,
      queueCapacity: capacity,
      phase: m.phase,
    };
  }
  if (queueUuids().includes(uuid)) {
    return {
      ok: true,
      message: `Already queued (${queueUuids().length}/${capacity}).`,
      action: 'queued',
      queueSize: queueUuids().length,
      queueCapacity: capacity,
      phase: m.phase,
    };
  }
  const members = partyFor(uuid) ?? [uuid];
  const queued = new Set(queueUuids());
  const toAdd = members.filter((m) => !queued.has(m));
  if (queued.size + toAdd.length > capacity) {
    return {
      ok: false,
      message: `Not enough queue room for your party (need ${toAdd.length}, ${capacity - queued.size} free).`,
      action: 'denied',
      queueSize: queueUuids().length,
      queueCapacity: capacity,
      phase: m.phase,
    };
  }
  const now = Date.now();
  for (const member of toAdd) {
    db.prepare('INSERT OR IGNORE INTO queue (mc_uuid, joined_at) VALUES (?, ?)').run(member, now);
  }
  broadcastState();
  if (queueUuids().length >= capacity) void startMatch();
  return {
    ok: true,
    message: toAdd.length > 1
      ? `Party queued (${queueUuids().length}/${capacity}).`
      : `Queued (${queueUuids().length}/${capacity}).`,
    action: 'queued',
    queueSize: queueUuids().length,
    queueCapacity: capacity,
    phase: m.phase,
  };
}

// Called when a player connects to the Minecraft server.
export function handlePlayerJoin(uuid: string, username: string): {
  action: JoinAction;
  message: string;
  queueSize?: number;
  queueCapacity?: number;
  phase?: MatchPhase;
} {
  getOrCreateByUuid(uuid, username);
  const m = currentMatch() ?? ensureLobby();
  if (m.phase === 'live' || m.phase === 'settling') {
    return {
      action: 'spectate',
      message: 'Match in progress — you are spectating. Use /bet <team> <sol>.',
      queueSize: queueUuids().length,
      queueCapacity: config.game.matchPlayerCount,
      phase: m.phase,
    };
  }
  const r = joinQueue(uuid);
  return {
    action: r.action ?? (r.ok ? 'queued' : 'denied'),
    message: r.message,
    queueSize: r.queueSize,
    queueCapacity: r.queueCapacity,
    phase: r.phase,
  };
}

export function leaveQueue(uuid: string): void {
  const members = partyFor(uuid) ?? [uuid];
  for (const member of members) {
    db.prepare('DELETE FROM queue WHERE mc_uuid = ?').run(member);
  }
  broadcastState();
}

function clearQueue(): void {
  db.prepare('DELETE FROM queue').run();
}

// ---- Betting ----
function placedBets(matchId: number): SimpleBet[] {
  const rows = db
    .prepare("SELECT user_id, team, amount_lamports FROM bets WHERE match_id = ? AND status = 'placed'")
    .all(matchId) as { user_id: number; team: TeamColor; amount_lamports: string }[];
  return rows.map((r) => ({ userId: r.user_id, team: r.team, amount: BigInt(r.amount_lamports) }));
}

export function buildOdds(): OddsView {
  const m = currentMatch();
  if (!m) {
    return {
      matchId: null,
      phase: 'idle',
      rakeFraction: config.game.bettingRake,
      totalPoolLamports: '0',
      teams: TEAM_COLORS.map((team) => ({ team, poolLamports: '0', bettors: 0, impliedMultiplier: 0 })),
    };
  }
  const bets = placedBets(m.id);
  const tally = tallyTeams(bets);
  const mult = computeMultipliers(tally, config.game.bettingRake);
  return {
    matchId: m.id,
    phase: m.phase,
    rakeFraction: config.game.bettingRake,
    totalPoolLamports: tally.grandTotal.toString(),
    teams: TEAM_COLORS.map((team) => ({
      team,
      poolLamports: tally.totals[team].toString(),
      bettors: tally.bettors[team],
      impliedMultiplier: mult[team],
    })),
  };
}

export function placeBet(
  userId: number,
  team: TeamColor,
  amountSol: number,
): { ok: boolean; message: string } {
  const m = currentMatch();
  if (!m || m.phase !== 'lobby') return { ok: false, message: 'Betting is closed.' };
  if (!TEAM_COLORS.includes(team)) return { ok: false, message: 'Unknown team.' };
  if (amountSol < config.game.minBetSol) return { ok: false, message: `Minimum bet is ${config.game.minBetSol} SOL.` };
  if (amountSol > config.game.maxBetSol) return { ok: false, message: `Maximum bet is ${config.game.maxBetSol} SOL.` };

  const user = getUserById(userId);
  if (!user) return { ok: false, message: 'Account not found.' };
  // Anti-collusion: players in this match cannot bet on it.
  if (user.mcUuid && queueUuids().includes(user.mcUuid)) {
    return { ok: false, message: 'Players cannot bet on their own match.' };
  }

  const amount = solToLamports(amountSol);
  if (getBalance(userId) < amount) return { ok: false, message: 'Insufficient balance.' };

  const tx = db.transaction(() => {
    applyDelta(userId, -amount, `bet:match${m.id}:${team}`);
    db.prepare(
      "INSERT INTO bets (match_id, user_id, team, amount_lamports, status, created_at) VALUES (?, ?, ?, ?, 'placed', ?)",
    ).run(m.id, userId, team, amount.toString(), Date.now());
  });
  tx();
  broadcastOdds();
  return { ok: true, message: `Bet ${amountSol} SOL on ${team}.` };
}

// ---- Match start ----
function assignTeams(uuids: string[]): Record<string, string[]> {
  return assignTeamsWithParties(uuids);
}

export async function startMatch(options?: { force?: boolean }): Promise<{ ok: boolean; message: string }> {
  const m = currentMatch();
  if (!m || m.phase !== 'lobby') {
    return { ok: false, message: 'No lobby match to start (a match may already be live).' };
  }
  const players = queueUuids().slice(0, config.game.matchPlayerCount);
  if (players.length === 0) {
    return { ok: false, message: 'Queue is empty — use /queue or rejoin the server first.' };
  }
  if (!options?.force && players.length < config.game.matchPlayerCount) {
    return {
      ok: false,
      message: `Need ${config.game.matchPlayerCount} players (${players.length} queued). Use force-start to begin early.`,
    };
  }

  let pool = 0n;
  try {
    pool = await availableRewardPool();
  } catch {
    pool = 0n;
  }

  db.prepare("UPDATE matches SET phase = 'live', reward_pool_lamports = ?, started_at = ? WHERE id = ?").run(
    pool.toString(),
    Date.now(),
    m.id,
  );
  decrementCooldowns();

  const teams = assignTeams(players);
  matchEvents.emit('start_match', { matchId: m.id, teams });
  broadcastState();
  broadcastOdds();
  return {
    ok: true,
    message: options?.force
      ? `Force-started match #${m.id} with ${players.length}/${config.game.matchPlayerCount} players.`
      : `Match #${m.id} started.`,
  };
}

export async function forceStartMatch(): Promise<{ ok: boolean; message: string }> {
  return startMatch({ force: true });
}

function decrementCooldowns(): void {
  db.prepare('UPDATE win_cooldown SET matches_remaining = matches_remaining - 1').run();
  db.prepare('DELETE FROM win_cooldown WHERE matches_remaining <= 0').run();
}

// ---- Settlement ----
export async function reportResult(
  matchId: number,
  winningTeam: TeamColor,
  winnerUuids: string[],
): Promise<void> {
  const m = currentMatch();
  if (!m || m.id !== matchId || m.phase !== 'live') return;
  clearBroadcastCamera();
  db.prepare("UPDATE matches SET phase = 'settling', winning_team = ? WHERE id = ?").run(winningTeam, matchId);

  // 1) Settle the parimutuel spectator pool (internal custodial credits).
  const bets = placedBets(matchId);
  const settlement = settleParimutuel(bets, winningTeam, config.game.bettingRake);
  const settleTx = db.transaction(() => {
    for (const [userId, amount] of settlement.payouts) {
      applyDelta(userId, amount, settlement.refunded ? 'bet_refund' : 'bet_win');
    }
    if (settlement.refunded) {
      db.prepare("UPDATE bets SET status = 'refunded' WHERE match_id = ?").run(matchId);
    } else {
      db.prepare("UPDATE bets SET status = 'won' WHERE match_id = ? AND team = ?").run(matchId, winningTeam);
      db.prepare("UPDATE bets SET status = 'lost' WHERE match_id = ? AND team != ?").run(matchId, winningTeam);
    }
  });
  settleTx();

  // 2) Pay the winning team's reward pool to their linked wallets (on-chain).
  const shares = splitRewardPool(m.rewardPoolLamports, winnerUuids.length);
  for (let i = 0; i < winnerUuids.length; i++) {
    const uuid = winnerUuids[i];
    const share = shares[i] ?? 0n;
    if (share <= 0n) continue;
    const user = getUserByUuid(uuid) ?? getOrCreateByUuid(uuid, uuid.slice(0, 8));
    db.prepare('INSERT OR REPLACE INTO win_cooldown (mc_uuid, matches_remaining) VALUES (?, 1)').run(uuid);
    if (user.payoutWallet) {
      try {
        await houseTransfer(user.payoutWallet, share, 'reward', user.id);
      } catch {
        applyDelta(user.id, share, 'reward_payout_failed_credit');
      }
    } else {
      // No payout wallet set: credit custodial balance so they can withdraw later.
      applyDelta(user.id, share, 'reward_no_wallet');
    }
  }

  db.prepare("UPDATE matches SET phase = 'settling', ended_at = ? WHERE id = ?").run(Date.now(), matchId);
  clearQueue();
  matchEvents.emit('notice', { message: `${winningTeam} won! Payouts settled.` });
  openLobby();
}

export function abortMatch(matchId: number, reason: string): void {
  const m = currentMatch();
  if (!m || m.id !== matchId) return;
  clearBroadcastCamera();
  const bets = placedBets(matchId);
  const settlement = refundAll(bets);
  const tx = db.transaction(() => {
    for (const [userId, amount] of settlement.payouts) applyDelta(userId, amount, 'bet_refund_abort');
    db.prepare("UPDATE bets SET status = 'refunded' WHERE match_id = ?").run(matchId);
    db.prepare("UPDATE matches SET phase = 'idle', ended_at = ? WHERE id = ?").run(Date.now(), matchId);
  });
  tx();
  clearQueue();
  matchEvents.emit('notice', { message: `Match voided (${reason}). All bets refunded.` });
  openLobby();
}

export function forceAbortCurrent(reason: string): { ok: boolean; message: string } {
  const m = currentMatch();
  if (!m || (m.phase !== 'live' && m.phase !== 'settling')) {
    return { ok: false, message: 'No active match on backend.' };
  }
  abortMatch(m.id, reason);
  return { ok: true, message: `Match #${m.id} aborted.` };
}

// ---- Broadcast helpers ----
export function broadcastOdds(): void {
  matchEvents.emit('odds', buildOdds());
}

export function broadcastState(): void {
  matchEvents.emit('state', publicState());
}

export function publicState() {
  const m = currentMatch();
  return {
    match: m
      ? {
          id: m.id,
          phase: m.phase,
          rewardPoolLamports: m.rewardPoolLamports.toString(),
          winningTeam: m.winningTeam,
        }
      : null,
    queue: { size: queueUuids().length, capacity: config.game.matchPlayerCount },
    odds: buildOdds(),
    stream: streamPublicView(config.stream.url),
  };
}
