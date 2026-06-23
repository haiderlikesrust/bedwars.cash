import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  computeMultipliers,
  refundAll,
  settleParimutuel,
  splitRewardPool,
  tallyTeams,
  type SimpleBet,
} from '../src/services/parimutuel.ts';

// The user's worked example: A=10 + D=10 on YELLOW, B=5 RED, C=20 BLUE.
// Total = 45, 5% rake -> net 42.75, YELLOW wins -> A and D each get 21.375.
// In lamports (1 SOL = 1e9): scale by 1e9.
const L = 1_000_000_000n;

test('parimutuel: worked example pays winners proportionally', () => {
  const bets: SimpleBet[] = [
    { userId: 1, team: 'YELLOW', amount: 10n * L },
    { userId: 4, team: 'YELLOW', amount: 10n * L },
    { userId: 2, team: 'RED', amount: 5n * L },
    { userId: 3, team: 'BLUE', amount: 20n * L },
  ];
  const s = settleParimutuel(bets, 'YELLOW', 0.05);
  assert.equal(s.refunded, false);
  // rake = ceil(45 * 0.05) = 2.25 SOL
  assert.equal(s.rake, 2_250_000_000n);
  // net = 42.75 split evenly between equal stakers -> 21.375 each
  assert.equal(s.payouts.get(1), 21_375_000_000n);
  assert.equal(s.payouts.get(4), 21_375_000_000n);
  assert.equal(s.payouts.get(2), undefined);
  assert.equal(s.payouts.get(3), undefined);
  // Conservation: winners' payouts + rake == grand total.
  const totalOut = (s.payouts.get(1) ?? 0n) + (s.payouts.get(4) ?? 0n) + s.rake;
  assert.equal(totalOut, 45n * L);
});

test('parimutuel: refunds all when nobody backed the winner', () => {
  const bets: SimpleBet[] = [
    { userId: 1, team: 'RED', amount: 5n * L },
    { userId: 2, team: 'BLUE', amount: 7n * L },
  ];
  const s = settleParimutuel(bets, 'YELLOW', 0.05);
  assert.equal(s.refunded, true);
  assert.equal(s.rake, 0n);
  assert.equal(s.payouts.get(1), 5n * L);
  assert.equal(s.payouts.get(2), 7n * L);
});

test('parimutuel: lamports are conserved exactly with awkward ratios', () => {
  const bets: SimpleBet[] = [
    { userId: 1, team: 'GREEN', amount: 1n },
    { userId: 2, team: 'GREEN', amount: 2n },
    { userId: 3, team: 'GREEN', amount: 7n },
    { userId: 4, team: 'RED', amount: 13n },
  ];
  const s = settleParimutuel(bets, 'GREEN', 0.05);
  const grand = 23n;
  const winnersOut = (s.payouts.get(1) ?? 0n) + (s.payouts.get(2) ?? 0n) + (s.payouts.get(3) ?? 0n);
  assert.equal(winnersOut + s.rake, grand);
});

test('refundAll returns every stake', () => {
  const bets: SimpleBet[] = [
    { userId: 1, team: 'GREEN', amount: 3n },
    { userId: 1, team: 'RED', amount: 4n },
  ];
  const s = refundAll(bets);
  assert.equal(s.refunded, true);
  assert.equal(s.payouts.get(1), 7n);
});

test('splitRewardPool splits equally and conserves lamports', () => {
  const shares = splitRewardPool(10n, 4);
  assert.equal(shares.reduce((a, b) => a + b, 0n), 10n);
  assert.deepEqual(shares, [3n, 3n, 2n, 2n]);
});

test('computeMultipliers reflects live dynamic odds', () => {
  const bets: SimpleBet[] = [
    { userId: 1, team: 'YELLOW', amount: 20n * L },
    { userId: 2, team: 'RED', amount: 5n * L },
    { userId: 3, team: 'BLUE', amount: 20n * L },
  ];
  const mult = computeMultipliers(tallyTeams(bets), 0.05);
  // Smaller pool (RED) -> bigger multiplier if it wins; empty pool (GREEN) -> 0.
  assert.ok(mult.RED > mult.YELLOW);
  assert.equal(mult.GREEN, 0);
});
