import { db } from '../db/index.js';
import { makeSessionToken, makeVerificationCode } from '../util/crypto.js';
import type { User } from '../types.js';

interface UserRow {
  id: number;
  mc_uuid: string | null;
  mc_username: string | null;
  payout_wallet: string | null;
  deposit_pubkey: string | null;
  deposit_secret_enc: string | null;
  balance_lamports: string;
  created_at: number;
}

function rowToUser(r: UserRow): User {
  return {
    id: r.id,
    mcUuid: r.mc_uuid,
    mcUsername: r.mc_username,
    payoutWallet: r.payout_wallet,
    depositPubkey: r.deposit_pubkey,
    balanceLamports: BigInt(r.balance_lamports),
    createdAt: r.created_at,
  };
}

export function getUserById(id: number): User | null {
  const r = db.prepare('SELECT * FROM users WHERE id = ?').get(id) as UserRow | undefined;
  return r ? rowToUser(r) : null;
}

export function getUserByUuid(uuid: string): User | null {
  const r = db.prepare('SELECT * FROM users WHERE mc_uuid = ?').get(uuid) as UserRow | undefined;
  return r ? rowToUser(r) : null;
}

export function getUserByDepositPubkey(pubkey: string): User | null {
  const r = db.prepare('SELECT * FROM users WHERE deposit_pubkey = ?').get(pubkey) as UserRow | undefined;
  return r ? rowToUser(r) : null;
}

export function listUsersWithDepositWallets(): User[] {
  const rows = db.prepare('SELECT * FROM users WHERE deposit_pubkey IS NOT NULL').all() as UserRow[];
  return rows.map(rowToUser);
}

export function createEmptyUser(): number {
  const info = db
    .prepare('INSERT INTO users (created_at) VALUES (?)')
    .run(Date.now());
  return Number(info.lastInsertRowid);
}

// Find or create a user keyed by Minecraft UUID (used when a player acts in-game).
export function getOrCreateByUuid(uuid: string, username: string): User {
  const existing = getUserByUuid(uuid);
  if (existing) {
    if (username && existing.mcUsername !== username) {
      db.prepare('UPDATE users SET mc_username = ? WHERE id = ?').run(username, existing.id);
    }
    return getUserById(existing.id)!;
  }
  const info = db
    .prepare('INSERT INTO users (mc_uuid, mc_username, created_at) VALUES (?, ?, ?)')
    .run(uuid, username, Date.now());
  return getUserById(Number(info.lastInsertRowid))!;
}

export function attachDepositWallet(userId: number, pubkey: string, encSecret: string): void {
  db.prepare('UPDATE users SET deposit_pubkey = ?, deposit_secret_enc = ? WHERE id = ?').run(
    pubkey,
    encSecret,
    userId,
  );
}

export function getDepositSecretEnc(userId: number): string | null {
  const r = db.prepare('SELECT deposit_secret_enc FROM users WHERE id = ?').get(userId) as
    | { deposit_secret_enc: string | null }
    | undefined;
  return r?.deposit_secret_enc ?? null;
}

export function setPayoutWallet(uuid: string, username: string, address: string): User {
  const user = getOrCreateByUuid(uuid, username);
  db.prepare('UPDATE users SET payout_wallet = ? WHERE id = ?').run(address, user.id);
  return getUserById(user.id)!;
}

export function getBalance(userId: number): bigint {
  const r = db.prepare('SELECT balance_lamports FROM users WHERE id = ?').get(userId) as
    | { balance_lamports: string }
    | undefined;
  return r ? BigInt(r.balance_lamports) : 0n;
}

// Atomically apply a signed balance delta and write an audit ledger row.
// Throws if it would make the balance negative.
export const applyDelta = db.transaction((userId: number, delta: bigint, reason: string) => {
  const current = getBalance(userId);
  const next = current + delta;
  if (next < 0n) throw new Error('INSUFFICIENT_BALANCE');
  db.prepare('UPDATE users SET balance_lamports = ? WHERE id = ?').run(next.toString(), userId);
  db.prepare('INSERT INTO ledger (user_id, delta_lamports, reason, created_at) VALUES (?, ?, ?, ?)').run(
    userId,
    delta.toString(),
    reason,
    Date.now(),
  );
  return next;
}) as (userId: number, delta: bigint, reason: string) => bigint;

// ---- Sessions (website auth) ----
export function createSession(userId: number): string {
  const token = makeSessionToken();
  db.prepare('INSERT INTO sessions (token, user_id, created_at) VALUES (?, ?, ?)').run(
    token,
    userId,
    Date.now(),
  );
  return token;
}

export function userIdForToken(token: string): number | null {
  const r = db.prepare('SELECT user_id FROM sessions WHERE token = ?').get(token) as
    | { user_id: number }
    | undefined;
  return r?.user_id ?? null;
}

// ---- Link codes (website <-> in-game verification) ----
export function createLinkCode(userId: number): string {
  // Invalidate prior unconsumed codes for this user.
  db.prepare('DELETE FROM link_codes WHERE user_id = ? AND consumed = 0').run(userId);
  const code = makeVerificationCode();
  db.prepare('INSERT INTO link_codes (code, user_id, consumed, created_at) VALUES (?, ?, 0, ?)').run(
    code,
    userId,
    Date.now(),
  );
  return code;
}

// Consume a code typed in-game, binding the in-game Mojang identity to the website user.
export const consumeLinkCode = db.transaction((code: string, mcUuid: string, mcUsername: string) => {
  const row = db.prepare('SELECT user_id FROM link_codes WHERE code = ? AND consumed = 0').get(code) as
    | { user_id: number }
    | undefined;
  if (!row) return null;
  // If this Mojang account is already a separate user, merge balances into the website user.
  const existing = getUserByUuid(mcUuid);
  if (existing && existing.id !== row.user_id) {
    const bal = getBalance(existing.id);
    if (bal > 0n) {
      applyDelta(row.user_id, bal, 'merge_from_ingame_account');
      db.prepare('UPDATE users SET balance_lamports = ? WHERE id = ?').run('0', existing.id);
    }
    db.prepare('UPDATE users SET mc_uuid = NULL WHERE id = ?').run(existing.id);
  }
  db.prepare('UPDATE users SET mc_uuid = ?, mc_username = ? WHERE id = ?').run(
    mcUuid,
    mcUsername,
    row.user_id,
  );
  db.prepare('UPDATE link_codes SET consumed = 1 WHERE code = ?').run(code);
  return row.user_id as number;
}) as (code: string, mcUuid: string, mcUsername: string) => number | null;

// Total custodial liabilities = every user's balance + all unsettled bet stakes.
// The reward pool must never dip into this; it can only spend treasury surplus.
export function totalLiabilitiesLamports(): bigint {
  const bal = db.prepare("SELECT COALESCE(SUM(CAST(balance_lamports AS INTEGER)), 0) AS s FROM users").get() as {
    s: number;
  };
  const bets = db
    .prepare("SELECT COALESCE(SUM(CAST(amount_lamports AS INTEGER)), 0) AS s FROM bets WHERE status = 'placed'")
    .get() as { s: number };
  return BigInt(bal.s) + BigInt(bets.s);
}

export function recordCheatFlag(mcUuid: string, check: string, details: string): void {
  db.prepare('INSERT INTO cheat_flags (mc_uuid, check_name, details, created_at) VALUES (?, ?, ?, ?)').run(
    mcUuid,
    check,
    details,
    Date.now(),
  );
}
