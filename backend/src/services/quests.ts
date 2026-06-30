import { db } from '../db/index.js';
import { grantXp } from './progression.js';
import type { MatchPlayerStatLine } from './playerStats.js';

export type QuestMetric = 'wins' | 'kills' | 'finalKills' | 'bedsBroken' | 'matches';

export interface QuestDef {
  id: string;
  name: string;
  description: string;
  metric: QuestMetric;
  target: number;
  xp: number;
}

// Pool of daily challenges. Three are active per day (chosen deterministically).
export const QUEST_CATALOG: QuestDef[] = [
  { id: 'win1', name: 'Victor', description: 'Win a match', metric: 'wins', target: 1, xp: 75 },
  { id: 'win3', name: 'Hat Trick', description: 'Win 3 matches', metric: 'wins', target: 3, xp: 150 },
  { id: 'kills10', name: 'Aggressor', description: 'Get 10 kills', metric: 'kills', target: 10, xp: 60 },
  { id: 'kills25', name: 'Warpath', description: 'Get 25 kills', metric: 'kills', target: 25, xp: 110 },
  { id: 'final3', name: 'Finisher', description: 'Get 3 final kills', metric: 'finalKills', target: 3, xp: 80 },
  { id: 'beds3', name: 'Bed Hunter', description: 'Break 3 beds', metric: 'bedsBroken', target: 3, xp: 90 },
  { id: 'beds1', name: 'Wake-up Call', description: 'Break a bed', metric: 'bedsBroken', target: 1, xp: 40 },
  { id: 'play3', name: 'Grinder', description: 'Play 3 matches', metric: 'matches', target: 3, xp: 45 },
];

const BY_ID = new Map(QUEST_CATALOG.map((q) => [q.id, q]));

export function dayKey(now = new Date()): string {
  return now.toISOString().slice(0, 10); // UTC YYYY-MM-DD
}

function epochDay(now = Date.now()): number {
  return Math.floor(now / 86_400_000);
}

// The three active quest ids for a given day — deterministic so everyone shares
// the same daily set, and it rotates each day.
export function todaysQuestIds(day = epochDay()): string[] {
  const ids = QUEST_CATALOG.map((q) => q.id);
  const n = ids.length;
  const s = ((day % n) + n) % n;
  return [ids[s], ids[(s + 3) % n], ids[(s + 5) % n]];
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

export function getPlayerQuests(mcUuid: string): QuestView[] {
  const day = dayKey();
  const ids = todaysQuestIds();
  const rows = db
    .prepare('SELECT quest_id, progress, completed FROM player_quests WHERE mc_uuid = ? AND day = ?')
    .all(mcUuid, day) as Array<{ quest_id: string; progress: number; completed: number }>;
  const byId = new Map(rows.map((r) => [r.quest_id, r]));
  return ids.map((id) => {
    const def = BY_ID.get(id)!;
    const row = byId.get(id);
    return {
      id,
      name: def.name,
      description: def.description,
      target: def.target,
      progress: row ? row.progress : 0,
      completed: row ? !!row.completed : false,
      xp: def.xp,
    };
  });
}

function metricValue(metric: QuestMetric, line: MatchPlayerStatLine, won: boolean): number {
  switch (metric) {
    case 'wins':
      return won ? 1 : 0;
    case 'kills':
      return line.kills;
    case 'finalKills':
      return line.finalKills;
    case 'bedsBroken':
      return line.bedsBroken;
    case 'matches':
      return 1;
  }
}

export interface CompletedQuest {
  id: string;
  name: string;
  xp: number;
}

// Apply one finished match to today's quests for a player; award XP on completion.
export function applyMatchToQuests(
  mcUuid: string,
  line: MatchPlayerStatLine,
  won: boolean,
): CompletedQuest[] {
  const day = dayKey();
  const ids = todaysQuestIds();
  const completed: CompletedQuest[] = [];
  const upsert = db.prepare(`
    INSERT INTO player_quests (mc_uuid, day, quest_id, progress, completed)
    VALUES (?, ?, ?, ?, ?)
    ON CONFLICT(mc_uuid, day, quest_id) DO UPDATE SET
      progress = excluded.progress,
      completed = excluded.completed
  `);

  const tx = db.transaction(() => {
    const rows = db
      .prepare('SELECT quest_id, progress, completed FROM player_quests WHERE mc_uuid = ? AND day = ?')
      .all(mcUuid, day) as Array<{ quest_id: string; progress: number; completed: number }>;
    const byId = new Map(rows.map((r) => [r.quest_id, r]));

    for (const id of ids) {
      const def = BY_ID.get(id)!;
      const cur = byId.get(id);
      if (cur && cur.completed) continue;
      const inc = metricValue(def.metric, line, won);
      if (inc === 0 && !cur) continue; // nothing to record yet
      const progress = Math.min(def.target, (cur ? cur.progress : 0) + inc);
      const isComplete = progress >= def.target;
      upsert.run(mcUuid, day, id, progress, isComplete ? 1 : 0);
      if (isComplete) {
        grantXp(mcUuid, def.xp);
        completed.push({ id, name: def.name, xp: def.xp });
      }
    }
  });
  tx();
  return completed;
}
