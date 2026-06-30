import { test } from 'node:test';
import assert from 'node:assert/strict';
import { applyMatchToQuests, getPlayerQuests, todaysQuestIds, QUEST_CATALOG } from '../src/services/quests.ts';
import { getProgression } from '../src/services/progression.ts';
import { db, initSchema } from '../src/db/index.ts';

initSchema();

const UUID = 'quest-test-uuid';
const LINE = { mcUuid: UUID, mcUsername: 'Q', kills: 99, finalKills: 99, bedsBroken: 99, deaths: 0 };

function clean() {
  db.prepare('DELETE FROM player_quests WHERE mc_uuid = ?').run(UUID);
  db.prepare('DELETE FROM player_progression WHERE mc_uuid = ?').run(UUID);
}

test("today's quests are 3 distinct ids from the catalog", () => {
  const ids = todaysQuestIds();
  assert.equal(ids.length, 3);
  assert.equal(new Set(ids).size, 3);
  const all = new Set(QUEST_CATALOG.map((q) => q.id));
  for (const id of ids) assert.ok(all.has(id));
});

test('a fresh player has 3 quests at zero progress', () => {
  clean();
  const qs = getPlayerQuests(UUID);
  assert.equal(qs.length, 3);
  assert.ok(qs.every((q) => q.progress === 0 && !q.completed));
});

test('quests complete over several matches, grant XP, and never double-award', () => {
  clean();
  const before = getProgression(UUID).xp;

  // Huge stat line; 3 matches so even the "do X 3 times" quests finish.
  const completedIds = new Set<string>();
  for (let i = 0; i < 3; i++) {
    for (const c of applyMatchToQuests(UUID, LINE, true)) completedIds.add(c.id);
  }

  assert.equal(completedIds.size, 3); // all three daily quests completed exactly once
  assert.ok(getPlayerQuests(UUID).every((q) => q.completed));
  assert.ok(getProgression(UUID).xp > before); // XP was granted

  // Completed quests are not re-awarded on a later match.
  assert.equal(applyMatchToQuests(UUID, LINE, true).length, 0);
  clean();
});
