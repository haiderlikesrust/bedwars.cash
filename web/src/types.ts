export type TeamColor = 'GREEN' | 'BLUE' | 'RED' | 'YELLOW';
export type MatchPhase = 'idle' | 'lobby' | 'live' | 'settling';

export interface TeamOdds {
  team: TeamColor;
  poolLamports: string;
  bettors: number;
  impliedMultiplier: number;
}

export interface Odds {
  matchId: number | null;
  phase: MatchPhase;
  rakeFraction: number;
  totalPoolLamports: string;
  teams: TeamOdds[];
}

export interface PublicState {
  match: { id: number; phase: MatchPhase; rewardPoolLamports: string; winningTeam: TeamColor | null } | null;
  queue: { size: number; capacity: number };
  odds: Odds;
  stream: {
    url: string | null;
    camera: { team: TeamColor; playerName: string } | null;
  };
}

export const LAMPORTS_PER_SOL = 1_000_000_000;

export function lamportsToSol(lamports: string): number {
  return Number(lamports) / LAMPORTS_PER_SOL;
}
