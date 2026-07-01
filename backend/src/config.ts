import 'dotenv/config';

function str(name: string, fallback?: string): string {
  const v = process.env[name];
  if (v === undefined || v === '') {
    if (fallback !== undefined) return fallback;
    throw new Error(`Missing required env var: ${name}`);
  }
  return v;
}

function num(name: string, fallback: number): number {
  const v = process.env[name];
  if (v === undefined || v === '') return fallback;
  const n = Number(v);
  if (Number.isNaN(n)) throw new Error(`Env var ${name} must be a number`);
  return n;
}

export const config = {
  port: num('PORT', 8787),
  webOrigin: str('WEB_ORIGIN', 'http://localhost:5173'),
  pluginToken: str('PLUGIN_TOKEN', 'change-me-plugin-token'),
  appSecret: str('APP_SECRET', 'change-me-app-secret-please-32chars-min'),

  solana: {
    cluster: str('SOLANA_CLUSTER', 'devnet'),
    rpcUrl: str('SOLANA_RPC_URL', 'https://api.devnet.solana.com'),
    houseWalletSecret: process.env.HOUSE_WALLET_SECRET ?? '',
    houseReserveSol: num('HOUSE_RESERVE_SOL', 0.5),
    rewardPoolCapSol: num('REWARD_POOL_CAP_SOL', 0),
  },

  treasury: {
    feeSource: str('FEE_SOURCE', 'mock') as 'mock' | 'pumpfun',
    pumpfunTokenMint: process.env.PUMPFUN_TOKEN_MINT ?? '',
    pumpfunCreatorWallet: process.env.PUMPFUN_CREATOR_WALLET ?? '',
  },

  game: {
    matchPlayerCount: num('MATCH_PLAYER_COUNT', 16),
    teamSize: num('TEAM_SIZE', 4),
    bettingRake: num('BETTING_RAKE', 0.05),
    minBetSol: num('MIN_BET_SOL', 0.01),
    maxBetSol: num('MAX_BET_SOL', 100),
  },

  // XP awarded per match action; levels are derived from total XP (see services/progression.ts).
  progression: {
    xpKill: num('XP_KILL', 10),
    xpFinalKill: num('XP_FINAL_KILL', 25),
    xpBed: num('XP_BED', 40),
    xpWin: num('XP_WIN', 100),
    xpPlay: num('XP_PLAY', 20),
  },

  antiAbuse: {
    minWalletAgeHours: num('MIN_WALLET_AGE_HOURS', 24),
    manualReviewThresholdSol: num('MANUAL_REVIEW_THRESHOLD_SOL', 5),
  },

  stream: {
    // HLS (.m3u8), direct video URL, or embed page shown on the website during live matches.
    url: process.env.STREAM_URL?.trim() ?? '',
  },

  // Admin panel (admin.bedwars.cash). Disabled unless ADMIN_PASSWORD_HASH is set.
  // Generate the hash with: node scripts/admin-hash.mjs <password>
  admin: {
    username: str('ADMIN_USERNAME', 'admin'),
    passwordHash: process.env.ADMIN_PASSWORD_HASH?.trim() ?? '',
    sessionTtlMinutes: num('ADMIN_SESSION_TTL_MIN', 30),
    maxLoginAttempts: num('ADMIN_MAX_LOGIN_ATTEMPTS', 5),
    lockoutMinutes: num('ADMIN_LOCKOUT_MIN', 15),
  },
} as const;

export function adminEnabled(): boolean {
  return config.admin.passwordHash.length > 0;
}

export const TEAM_COLORS = ['GREEN', 'BLUE', 'RED', 'YELLOW'] as const;
export type TeamColor = (typeof TEAM_COLORS)[number];

export function isDevnet(): boolean {
  return config.solana.cluster !== 'mainnet-beta';
}
