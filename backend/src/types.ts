import type { TeamColor } from './config.js';

export type MatchPhase = 'idle' | 'lobby' | 'live' | 'settling';

export interface User {
  id: number;
  mcUuid: string | null;
  mcUsername: string | null;
  payoutWallet: string | null; // player payout address (set in-game)
  depositPubkey: string | null; // custodial deposit wallet address
  balanceLamports: bigint; // available custodial balance
  createdAt: number;
}

export interface Bet {
  id: number;
  matchId: number;
  userId: number;
  team: TeamColor;
  amountLamports: bigint;
  createdAt: number;
}

export interface MatchRow {
  id: number;
  phase: MatchPhase;
  rewardPoolLamports: bigint;
  winningTeam: TeamColor | null;
  createdAt: number;
  startedAt: number | null;
  endedAt: number | null;
}

export interface TeamPoolView {
  team: TeamColor;
  totalLamports: string;
  bettors: number;
}

export interface OddsView {
  matchId: number | null;
  phase: MatchPhase;
  rakeFraction: number;
  totalPoolLamports: string;
  teams: Array<{
    team: TeamColor;
    poolLamports: string;
    bettors: number;
    // multiplier you'd receive per lamport staked if this team wins right now
    impliedMultiplier: number;
  }>;
}

// Messages exchanged with the Minecraft plugin over WebSocket.
export type PluginInbound =
  | { type: 'hello'; token: string }
  | { type: 'setwallet'; mcUuid: string; mcUsername: string; address: string }
  | { type: 'verify'; mcUuid: string; mcUsername: string; code: string }
  | { type: 'player_join'; mcUuid: string; mcUsername: string }
  | { type: 'queue_join'; mcUuid: string }
  | { type: 'queue_leave'; mcUuid: string }
  | { type: 'place_bet'; mcUuid: string; team: TeamColor; amountSol: number }
  | { type: 'match_result'; matchId: number; winningTeam: TeamColor; winnerUuids: string[] }
  | { type: 'match_aborted'; matchId: number; reason: string }
  | { type: 'cheat_flag'; mcUuid: string; check: string; details: string }
  | { type: 'force_start'; mcUuid: string }
  | { type: 'force_abort'; mcUuid: string; reason?: string }
  | { type: 'match_camera'; matchId: number; team: TeamColor; playerName: string }
  | { type: 'match_camera_clear' }
  | { type: 'party_invite'; mcUuid: string; targetUuid: string; leaderName: string }
  | { type: 'party_accept'; mcUuid: string }
  | { type: 'party_leave'; mcUuid: string }
  | { type: 'party_list'; mcUuid: string };

export interface PublicState {
  match: {
    id: number;
    phase: MatchPhase;
    rewardPoolLamports: string;
    winningTeam: TeamColor | null;
  } | null;
  queue: { size: number; capacity: number };
  odds: OddsView;
  stream: {
    url: string | null;
    camera: { team: TeamColor; playerName: string } | null;
  };
}

export type JoinAction = 'queued' | 'spectate' | 'denied';

export type PluginOutbound =
  | { type: 'welcome' }
  | { type: 'ack'; ok: boolean; message?: string }
  | { type: 'start_match'; matchId: number; teams: Record<string, string[]> } // team -> uuids
  | { type: 'odds'; odds: OddsView }
  | { type: 'state'; state: PublicState }
  | {
      type: 'join_action';
      mcUuid: string;
      action: JoinAction;
      message: string;
      queueSize?: number;
      queueCapacity?: number;
      phase?: MatchPhase;
    }
  | { type: 'notice'; mcUuid?: string; message: string };
