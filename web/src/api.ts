export const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8787';
export const WS_URL = import.meta.env.VITE_WS_URL ?? 'ws://localhost:8787/ws/web';

const TOKEN_KEY = 'bwcash_token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

async function http<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const res = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers ?? {}),
    },
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error((body as { error?: string }).error ?? `HTTP ${res.status}`);
  }
  return res.json() as Promise<T>;
}

export interface SessionResp {
  token: string;
  userId: number;
  depositAddress: string;
}

export interface Progression {
  level: number;
  xp: number;
  xpIntoLevel: number;
  xpForLevel: number;
}

export interface AchievementView {
  id: string;
  name: string;
  description: string;
  unlocked: boolean;
  unlockedAt: number | null;
}

export interface QuestView {
  id: string;
  name: string;
  description: string;
  target: number;
  progress: number;
  completed: boolean;
  xp: number;
}

export interface MeResp {
  userId: number;
  mcUsername: string | null;
  linked: boolean;
  depositAddress: string;
  balanceLamports: string;
  balanceSol: number;
  progression: Progression | null;
  achievements: AchievementView[];
  quests: QuestView[];
}

export interface HouseResp {
  address: string;
  balanceSol: number;
  availableRewardPoolSol: number;
}

export interface Leaderboard {
  bettors: Array<{ name: string; netProfitSol: number }>;
  players: Array<{ name: string; wonSol: number }>;
  killers: Array<{ name: string; kills: number; finalKills: number }>;
  bedBreakers: Array<{ name: string; bedsBroken: number }>;
  winners: Array<{ name: string; wins: number; matchesPlayed: number }>;
}

export interface SweatzonePlayer {
  username: string;
  level: number;
  wins: number;
  kills: number;
  bedsBroken: number;
  solWon: number;
}

export interface SweatzoneResp {
  players: SweatzonePlayer[];
}

// Ensure we have a session; create one (and a custodial deposit wallet) if needed.
export async function ensureSession(): Promise<void> {
  if (getToken()) return;
  const s = await http<SessionResp>('/api/session', { method: 'POST', body: '{}' });
  setToken(s.token);
}

export const getMe = () => http<MeResp>('/api/me');
export const getHouse = () => http<HouseResp>('/api/house');
export const getLeaderboard = () => http<Leaderboard>('/api/leaderboard');
export const getSweatzone = () => http<SweatzoneResp>('/api/sweatzone');
export const createLinkCode = () => http<{ code: string }>('/api/link/code', { method: 'POST', body: '{}' });

export const withdraw = (destination: string, amountSol: number) =>
  http<{ status: string; signature?: string }>('/api/withdraw', {
    method: 'POST',
    body: JSON.stringify({ destination, amountSol }),
  });
