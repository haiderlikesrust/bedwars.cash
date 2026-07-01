import { db } from '../db/index.js';
import { config } from '../config.js';
import { makeOpaqueToken, verifyPassword } from '../util/crypto.js';

export interface AdminSession {
  token: string;
  csrf: string;
  username: string;
}

interface SessionRow {
  token: string;
  csrf: string;
  username: string;
  created_at: number;
  last_seen: number;
}

// ── Login rate-limiting (per IP, in-memory) ──
const attempts = new Map<string, { count: number; lockedUntil: number }>();

export function isLockedOut(ip: string): number {
  const a = attempts.get(ip);
  if (a && a.lockedUntil > Date.now()) return a.lockedUntil;
  return 0;
}

function recordFailure(ip: string): void {
  const a = attempts.get(ip) ?? { count: 0, lockedUntil: 0 };
  a.count += 1;
  if (a.count >= config.admin.maxLoginAttempts) {
    a.lockedUntil = Date.now() + config.admin.lockoutMinutes * 60_000;
    a.count = 0;
  }
  attempts.set(ip, a);
}

function clearFailures(ip: string): void {
  attempts.delete(ip);
}

// ── Sessions ──
const ttlMs = () => config.admin.sessionTtlMinutes * 60_000;

export function login(username: string, password: string, ip: string): AdminSession | null {
  // Constant-ish: always run the hash comparison against the configured hash.
  const userOk = username === config.admin.username;
  const passOk = verifyPassword(password, config.admin.passwordHash);
  if (!userOk || !passOk) {
    recordFailure(ip);
    return null;
  }
  clearFailures(ip);
  const token = makeOpaqueToken(32);
  const csrf = makeOpaqueToken(32);
  const now = Date.now();
  db.prepare(
    'INSERT INTO admin_sessions (token, csrf, username, created_at, last_seen, ip) VALUES (?, ?, ?, ?, ?, ?)',
  ).run(token, csrf, username, now, now, ip);
  return { token, csrf, username };
}

// Validate a session token, enforce idle TTL, and refresh last_seen.
export function sessionFromToken(token: string | undefined): AdminSession | null {
  if (!token) return null;
  const row = db.prepare('SELECT * FROM admin_sessions WHERE token = ?').get(token) as
    | SessionRow
    | undefined;
  if (!row) return null;
  const now = Date.now();
  if (now - row.last_seen > ttlMs()) {
    revoke(token);
    return null;
  }
  db.prepare('UPDATE admin_sessions SET last_seen = ? WHERE token = ?').run(now, token);
  return { token: row.token, csrf: row.csrf, username: row.username };
}

export function revoke(token: string): void {
  db.prepare('DELETE FROM admin_sessions WHERE token = ?').run(token);
}

// ── Audit log ──
export function audit(username: string, action: string, details: string, ip: string): void {
  db.prepare(
    'INSERT INTO admin_audit (username, action, details, ip, created_at) VALUES (?, ?, ?, ?, ?)',
  ).run(username, action, details, ip, Date.now());
}

export function recentAudit(limit = 100): Array<{
  username: string;
  action: string;
  details: string | null;
  ip: string | null;
  createdAt: number;
}> {
  const rows = db
    .prepare('SELECT username, action, details, ip, created_at FROM admin_audit ORDER BY id DESC LIMIT ?')
    .all(limit) as Array<{
    username: string;
    action: string;
    details: string | null;
    ip: string | null;
    created_at: number;
  }>;
  return rows.map((r) => ({
    username: r.username,
    action: r.action,
    details: r.details,
    ip: r.ip,
    createdAt: r.created_at,
  }));
}
