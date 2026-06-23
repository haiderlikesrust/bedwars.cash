import { TEAM_COLORS, type TeamColor } from '../config.js';

export interface SimpleBet {
  userId: number;
  team: TeamColor;
  amount: bigint;
}

export interface TeamTotals {
  totals: Record<TeamColor, bigint>;
  bettors: Record<TeamColor, number>;
  grandTotal: bigint;
}

export function tallyTeams(bets: SimpleBet[]): TeamTotals {
  const totals = { GREEN: 0n, BLUE: 0n, RED: 0n, YELLOW: 0n } as Record<TeamColor, bigint>;
  const bettors = { GREEN: 0, BLUE: 0, RED: 0, YELLOW: 0 } as Record<TeamColor, number>;
  let grandTotal = 0n;
  for (const b of bets) {
    totals[b.team] += b.amount;
    bettors[b.team] += 1;
    grandTotal += b.amount;
  }
  return { totals, bettors, grandTotal };
}

// Rake taken from the pool, expressed in lamports. We ceil so the house never loses dust.
function rakeLamports(total: bigint, rakeFraction: number): bigint {
  const bps = BigInt(Math.round(rakeFraction * 10000));
  const raw = (total * bps) / 10000n;
  const hasRemainder = (total * bps) % 10000n !== 0n;
  return hasRemainder ? raw + 1n : raw;
}

// Live, dynamic odds: for each team, the multiplier a bettor would receive per
// lamport staked IF that team won given the current pool. Not a fixed multiplier;
// it shifts as bets arrive.
export function computeMultipliers(
  t: TeamTotals,
  rakeFraction: number,
): Record<TeamColor, number> {
  const net = t.grandTotal - rakeLamports(t.grandTotal, rakeFraction);
  const out = {} as Record<TeamColor, number>;
  for (const team of TEAM_COLORS) {
    const teamTotal = t.totals[team];
    out[team] = teamTotal === 0n ? 0 : Number(net) / Number(teamTotal);
  }
  return out;
}

export interface Settlement {
  // userId -> lamports credited (winnings for a win, or stake back for a refund)
  payouts: Map<number, bigint>;
  rake: bigint;
  refunded: boolean;
}

// Distribute `net` among winners proportional to stake, conserving the total
// exactly via the largest-remainder method.
function distribute(net: bigint, winners: SimpleBet[], winnerTotal: bigint): Map<number, bigint> {
  const result = new Map<number, bigint>();
  const remainders: Array<{ userId: number; rem: bigint }> = [];
  let assigned = 0n;
  for (const w of winners) {
    const numerator = net * w.amount;
    const floor = numerator / winnerTotal;
    const rem = numerator % winnerTotal;
    result.set(w.userId, (result.get(w.userId) ?? 0n) + floor);
    remainders.push({ userId: w.userId, rem });
    assigned += floor;
  }
  let leftover = net - assigned;
  remainders.sort((a, b) => (b.rem > a.rem ? 1 : b.rem < a.rem ? -1 : 0));
  let i = 0;
  while (leftover > 0n && remainders.length > 0) {
    const { userId } = remainders[i % remainders.length];
    result.set(userId, (result.get(userId) ?? 0n) + 1n);
    leftover -= 1n;
    i++;
  }
  return result;
}

// Settle a parimutuel pool for a winning team.
// If nobody backed the winner, all bets are refunded with no rake.
export function settleParimutuel(
  bets: SimpleBet[],
  winningTeam: TeamColor,
  rakeFraction: number,
): Settlement {
  const winners = bets.filter((b) => b.team === winningTeam);
  const winnerTotal = winners.reduce((s, b) => s + b.amount, 0n);

  if (winnerTotal === 0n) {
    return { payouts: refundMap(bets), rake: 0n, refunded: true };
  }

  const grandTotal = bets.reduce((s, b) => s + b.amount, 0n);
  const rake = rakeLamports(grandTotal, rakeFraction);
  const net = grandTotal - rake;
  return { payouts: distribute(net, winners, winnerTotal), rake, refunded: false };
}

// Refund every bettor their exact stake (used on aborted/voided matches).
export function refundAll(bets: SimpleBet[]): Settlement {
  return { payouts: refundMap(bets), rake: 0n, refunded: true };
}

function refundMap(bets: SimpleBet[]): Map<number, bigint> {
  const m = new Map<number, bigint>();
  for (const b of bets) m.set(b.userId, (m.get(b.userId) ?? 0n) + b.amount);
  return m;
}

// Split a reward pool equally among the winning team members, conserving lamports.
export function splitRewardPool(pool: bigint, memberCount: number): bigint[] {
  if (memberCount <= 0) return [];
  const base = pool / BigInt(memberCount);
  let leftover = pool - base * BigInt(memberCount);
  const out: bigint[] = [];
  for (let i = 0; i < memberCount; i++) {
    let share = base;
    if (leftover > 0n) {
      share += 1n;
      leftover -= 1n;
    }
    out.push(share);
  }
  return out;
}
