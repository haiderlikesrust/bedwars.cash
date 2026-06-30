import { db } from '../db/index.js';
import { config } from '../config.js';
import { getPlayerStats, type MatchPlayerStatLine, type PlayerStatsRow } from './playerStats.js';

// ── Level curve ──
// Advancing from level n to n+1 costs 100 * n XP, so the cumulative XP required to
// *reach* level L is 100 * (1 + 2 + ... + (L-1)) = 50 * (L-1) * L.
export function cumulativeXpForLevel(level: number): number {
  if (level <= 1) return 0;
  return 50 * (level - 1) * level;
}

export function levelForXp(xp: number): number {
  let level = 1;
  while (cumulativeXpForLevel(level + 1) <= xp) level++;
  return level;
}

export interface Progression {
  level: number;
  xp: number; // total lifetime XP
  xpIntoLevel: number; // XP earned within the current level
  xpForLevel: number; // XP span of the current level (to next)
}

export function progressionView(xp: number): Progression {
  const level = levelForXp(xp);
  const base = cumulativeXpForLevel(level);
  const next = cumulativeXpForLevel(level + 1);
  return { level, xp, xpIntoLevel: xp - base, xpForLevel: next - base };
}

// ── Achievements catalog ──
// `cum` is the player's cumulative stats *after* the match was recorded; `line` is this match only.
export interface Achievement {
  id: string;
  name: string;
  description: string;
  check: (cum: PlayerStatsRow, line?: MatchPlayerStatLine) => boolean;
}

export const ACHIEVEMENTS: Achievement[] = [
  { id: 'first_win', name: 'First Victory', description: 'Win your first match.', check: (c) => c.wins >= 1 },
  { id: 'wins_10', name: 'Contender', description: 'Win 10 matches.', check: (c) => c.wins >= 10 },
  { id: 'wins_50', name: 'Veteran', description: 'Win 50 matches.', check: (c) => c.wins >= 50 },
  { id: 'wins_100', name: 'Champion', description: 'Win 100 matches.', check: (c) => c.wins >= 100 },
  { id: 'kills_50', name: 'Slayer', description: 'Land 50 total kills.', check: (c) => c.kills >= 50 },
  { id: 'kills_250', name: 'Executioner', description: 'Land 250 total kills.', check: (c) => c.kills >= 250 },
  { id: 'beds_10', name: 'Bed Breaker', description: 'Break 10 beds.', check: (c) => c.bedsBroken >= 10 },
  { id: 'beds_50', name: 'Bed Wrecker', description: 'Break 50 beds.', check: (c) => c.bedsBroken >= 50 },
  { id: 'sweat_50', name: 'Sweat', description: 'Play 50 matches.', check: (c) => c.matchesPlayed >= 50 },
  { id: 'rampage', name: 'Rampage', description: 'Get 5+ kills in a single match.', check: (_c, l) => !!l && l.kills >= 5 },
  { id: 'closer', name: 'Closer', description: 'Get 3+ final kills in a single match.', check: (_c, l) => !!l && l.finalKills >= 3 },
  { id: 'demolition', name: 'Demolition', description: 'Break 2+ beds in a single match.', check: (_c, l) => !!l && l.bedsBroken >= 2 },
];

const ACHIEVEMENT_BY_ID = new Map(ACHIEVEMENTS.map((a) => [a.id, a]));

interface ProgressionRow {
  xp: number;
  level: number;
}

function progressionRow(mcUuid: string): ProgressionRow {
  const r = db.prepare('SELECT xp, level FROM player_progression WHERE mc_uuid = ?').get(mcUuid) as
    | ProgressionRow
    | undefined;
  return r ?? { xp: 0, level: 1 };
}

export function getProgression(mcUuid: string): Progression {
  return progressionView(progressionRow(mcUuid).xp);
}

export interface AchievementView {
  id: string;
  name: string;
  description: string;
  unlocked: boolean;
  unlockedAt: number | null;
}

export function getAchievements(mcUuid: string): AchievementView[] {
  const rows = db
    .prepare('SELECT achievement_id, unlocked_at FROM player_achievements WHERE mc_uuid = ?')
    .all(mcUuid) as Array<{ achievement_id: string; unlocked_at: number }>;
  const unlockedAt = new Map(rows.map((r) => [r.achievement_id, r.unlocked_at]));
  return ACHIEVEMENTS.map((a) => ({
    id: a.id,
    name: a.name,
    description: a.description,
    unlocked: unlockedAt.has(a.id),
    unlockedAt: unlockedAt.get(a.id) ?? null,
  }));
}

export function matchXp(line: MatchPlayerStatLine, won: boolean): number {
  const p = config.progression;
  return (
    line.kills * p.xpKill +
    line.finalKills * p.xpFinalKill +
    line.bedsBroken * p.xpBed +
    (won ? p.xpWin : 0) +
    p.xpPlay
  );
}

export interface ProgressionResult {
  mcUuid: string;
  mcUsername: string;
  xpGained: number;
  level: number;
  fromLevel: number;
  leveledUp: boolean;
  unlocked: Array<{ id: string; name: string }>;
}

// Award XP and evaluate achievements for one finished match. Must run *after*
// recordMatchPlayerStats so cumulative player_stats already include this match.
export function awardMatchProgression(
  winnerUuids: string[],
  lines: MatchPlayerStatLine[],
): ProgressionResult[] {
  if (lines.length === 0) return [];
  const winners = new Set(winnerUuids);
  const now = Date.now();
  const results: ProgressionResult[] = [];

  const upsertProg = db.prepare(`
    INSERT INTO player_progression (mc_uuid, xp, level, updated_at)
    VALUES (?, ?, ?, ?)
    ON CONFLICT(mc_uuid) DO UPDATE SET
      xp = excluded.xp,
      level = excluded.level,
      updated_at = excluded.updated_at
  `);
  const insAchievement = db.prepare(
    'INSERT OR IGNORE INTO player_achievements (mc_uuid, achievement_id, unlocked_at) VALUES (?, ?, ?)',
  );

  const tx = db.transaction(() => {
    for (const line of lines) {
      const won = winners.has(line.mcUuid);
      const prev = progressionRow(line.mcUuid);
      const gained = matchXp(line, won);
      const newXp = prev.xp + gained;
      const newLevel = levelForXp(newXp);
      upsertProg.run(line.mcUuid, newXp, newLevel, now);

      const cum = getPlayerStats(line.mcUuid);
      const already = new Set(
        (
          db
            .prepare('SELECT achievement_id FROM player_achievements WHERE mc_uuid = ?')
            .all(line.mcUuid) as Array<{ achievement_id: string }>
        ).map((r) => r.achievement_id),
      );
      const unlocked: Array<{ id: string; name: string }> = [];
      if (cum) {
        for (const a of ACHIEVEMENTS) {
          if (!already.has(a.id) && a.check(cum, line)) {
            insAchievement.run(line.mcUuid, a.id, now);
            unlocked.push({ id: a.id, name: a.name });
          }
        }
      }

      results.push({
        mcUuid: line.mcUuid,
        mcUsername: line.mcUsername,
        xpGained: gained,
        level: newLevel,
        fromLevel: prev.level,
        leveledUp: newLevel > prev.level,
        unlocked,
      });
    }
  });
  tx();
  return results;
}

export function achievementName(id: string): string {
  return ACHIEVEMENT_BY_ID.get(id)?.name ?? id;
}

// Grant a flat amount of XP (e.g. a completed quest) and recompute level.
export function grantXp(mcUuid: string, amount: number): {
  xp: number;
  level: number;
  fromLevel: number;
  leveledUp: boolean;
} {
  const prev = progressionRow(mcUuid);
  const newXp = prev.xp + Math.max(0, amount);
  const newLevel = levelForXp(newXp);
  db.prepare(`
    INSERT INTO player_progression (mc_uuid, xp, level, updated_at)
    VALUES (?, ?, ?, ?)
    ON CONFLICT(mc_uuid) DO UPDATE SET
      xp = excluded.xp,
      level = excluded.level,
      updated_at = excluded.updated_at
  `).run(mcUuid, newXp, newLevel, Date.now());
  return { xp: newXp, level: newLevel, fromLevel: prev.level, leveledUp: newLevel > prev.level };
}
