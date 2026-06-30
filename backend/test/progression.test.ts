import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  ACHIEVEMENTS,
  cumulativeXpForLevel,
  levelForXp,
  matchXp,
  progressionView,
} from '../src/services/progression.ts';
import type { MatchPlayerStatLine, PlayerStatsRow } from '../src/services/playerStats.ts';

function ach(id: string) {
  const a = ACHIEVEMENTS.find((x) => x.id === id);
  if (!a) throw new Error(`no achievement ${id}`);
  return a;
}

function cum(over: Partial<PlayerStatsRow> = {}): PlayerStatsRow {
  return {
    mcUuid: 'u',
    mcUsername: 'p',
    matchesPlayed: 0,
    wins: 0,
    kills: 0,
    finalKills: 0,
    bedsBroken: 0,
    deaths: 0,
    ...over,
  };
}

function line(over: Partial<MatchPlayerStatLine> = {}): MatchPlayerStatLine {
  return { mcUuid: 'u', mcUsername: 'p', kills: 0, finalKills: 0, bedsBroken: 0, deaths: 0, ...over };
}

test('level curve: cumulative XP thresholds', () => {
  assert.equal(cumulativeXpForLevel(1), 0);
  assert.equal(cumulativeXpForLevel(2), 100);
  assert.equal(cumulativeXpForLevel(3), 300);
  assert.equal(cumulativeXpForLevel(4), 600);
});

test('levelForXp: maps total XP to the right level at boundaries', () => {
  assert.equal(levelForXp(0), 1);
  assert.equal(levelForXp(99), 1);
  assert.equal(levelForXp(100), 2);
  assert.equal(levelForXp(299), 2);
  assert.equal(levelForXp(300), 3);
});

test('progressionView reports progress within the current level', () => {
  const v = progressionView(150);
  assert.equal(v.level, 2);
  assert.equal(v.xpIntoLevel, 50); // 150 - 100
  assert.equal(v.xpForLevel, 200); // 300 - 100
});

test('matchXp sums per-action rewards (defaults)', () => {
  // 5 kills, 3 finals, 2 beds, win, + play participation
  assert.equal(matchXp(line({ kills: 5, finalKills: 3, bedsBroken: 2 }), true), 325);
  // losing with no actions still grants participation XP
  assert.equal(matchXp(line(), false), 20);
});

test('per-match achievements fire on single-match performance', () => {
  assert.equal(ach('rampage').check(cum(), line({ kills: 5 })), true);
  assert.equal(ach('rampage').check(cum(), line({ kills: 4 })), false);
  assert.equal(ach('closer').check(cum(), line({ finalKills: 3 })), true);
  assert.equal(ach('demolition').check(cum(), line({ bedsBroken: 2 })), true);
});

test('cumulative achievements fire on lifetime totals', () => {
  assert.equal(ach('first_win').check(cum({ wins: 1 })), true);
  assert.equal(ach('first_win').check(cum({ wins: 0 })), false);
  assert.equal(ach('beds_10').check(cum({ bedsBroken: 10 })), true);
  assert.equal(ach('sweat_50').check(cum({ matchesPlayed: 50 })), true);
  assert.equal(ach('kills_250').check(cum({ kills: 249 })), false);
});
