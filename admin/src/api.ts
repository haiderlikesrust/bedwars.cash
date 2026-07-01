// Same-origin admin API client. The session lives in an httpOnly cookie; the CSRF
// token (returned only by login/me) is kept in memory and sent on mutations.
let csrf = '';

export function setCsrf(token: string): void {
  csrf = token;
}

async function req<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') headers['X-CSRF-Token'] = csrf;
  const res = await fetch('/api/admin/panel' + path, {
    method,
    headers,
    credentials: 'include',
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    const e = (await res.json().catch(() => ({}))) as { error?: string };
    throw new Error(e.error ?? `HTTP ${res.status}`);
  }
  return res.json() as Promise<T>;
}

export interface Overview {
  phase: string;
  matchId: number | null;
  queue: { size: number; capacity: number };
  pluginConnected: boolean;
  houseAddress: string;
  houseBalanceSol: number;
  availableRewardPoolSol: number;
  liabilitiesSol: number;
  solvent: boolean;
  cluster: string;
}

export interface Player {
  id: number;
  mcUsername: string | null;
  mcUuid: string | null;
  payoutWallet: string | null;
  balanceSol: number;
  createdAt: number;
}

export interface CheatFlag {
  mcUuid: string;
  username: string | null;
  checkName: string;
  details: string | null;
  createdAt: number;
}

export interface HeldWithdrawal {
  id: number;
  userId: number;
  username: string | null;
  amountSol: number;
  destination: string;
  createdAt: number;
}

export interface AuditEntry {
  username: string;
  action: string;
  details: string | null;
  ip: string | null;
  createdAt: number;
}

export const api = {
  me: () => req<{ username: string; csrf: string }>('/me'),
  login: (username: string, password: string) =>
    req<{ ok: true; username: string; csrf: string }>('/login', 'POST', { username, password }),
  logout: () => req<{ ok: true }>('/logout', 'POST'),
  overview: () => req<Overview>('/overview'),
  clearQueue: () => req<{ ok: true; removed: number }>('/queue/clear', 'POST'),
  forceStart: () => req<{ ok: boolean; message: string }>('/match/force-start', 'POST'),
  abort: (reason: string) => req<{ ok: boolean; message: string }>('/match/abort', 'POST', { reason }),
  players: () => req<{ players: Player[] }>('/players'),
  credit: (userId: number, sol: number) =>
    req<{ ok: true; balanceSol: number }>('/players/credit', 'POST', { userId, sol }),
  cheatFlags: () => req<{ flags: CheatFlag[] }>('/cheat-flags'),
  heldWithdrawals: () => req<{ withdrawals: HeldWithdrawal[] }>('/withdrawals/held'),
  rejectWithdrawal: (id: number) => req<{ ok: true }>(`/withdrawals/${id}/reject`, 'POST'),
  topup: (sol: number) => req<{ ok: true }>('/treasury/topup', 'POST', { sol }),
  audit: () => req<{ entries: AuditEntry[] }>('/audit'),
};
