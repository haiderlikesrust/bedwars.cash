import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { initSchema, db } from '../src/db/index.js';
import { combatLeaderboard, getPlayerStats, recordMatchPlayerStats, sweatzoneLeaderboard } from '../src/services/playerStats.js';

initSchema();

beforeEach(() => {
  db.exec('DELETE FROM player_stats');
});

describe('playerStats', () => {
  it('accumulates kills, beds, wins, and deaths across matches', () => {
    recordMatchPlayerStats(['uuid-a'], [
      {
        mcUuid: 'uuid-a',
        mcUsername: 'Alice',
        kills: 5,
        finalKills: 2,
        bedsBroken: 1,
        deaths: 3,
      },
      {
        mcUuid: 'uuid-b',
        mcUsername: 'Bob',
        kills: 1,
        finalKills: 0,
        bedsBroken: 0,
        deaths: 5,
      },
    ]);

    const alice = getPlayerStats('uuid-a');
    assert.deepEqual(alice, {
      mcUuid: 'uuid-a',
      mcUsername: 'Alice',
      matchesPlayed: 1,
      wins: 1,
      kills: 5,
      finalKills: 2,
      bedsBroken: 1,
      deaths: 3,
    });

    recordMatchPlayerStats(['uuid-b'], [
      {
        mcUuid: 'uuid-a',
        mcUsername: 'Alice',
        kills: 2,
        finalKills: 1,
        bedsBroken: 0,
        deaths: 1,
      },
      {
        mcUuid: 'uuid-b',
        mcUsername: 'Bob',
        kills: 3,
        finalKills: 2,
        bedsBroken: 2,
        deaths: 2,
      },
    ]);

    assert.deepEqual(getPlayerStats('uuid-a'), {
      mcUuid: 'uuid-a',
      mcUsername: 'Alice',
      matchesPlayed: 2,
      wins: 1,
      kills: 7,
      finalKills: 3,
      bedsBroken: 1,
      deaths: 4,
    });
    assert.deepEqual(getPlayerStats('uuid-b'), {
      mcUuid: 'uuid-b',
      mcUsername: 'Bob',
      matchesPlayed: 2,
      wins: 1,
      kills: 4,
      finalKills: 2,
      bedsBroken: 2,
      deaths: 7,
    });

    const board = combatLeaderboard();
    assert.equal(board.killers[0]?.name, 'Alice');
    assert.equal(board.bedBreakers[0]?.name, 'Bob');
    assert.deepEqual(board.winners.map((w) => w.name).sort(), ['Alice', 'Bob']);
  });

  it('sweatzone ranks by wins and includes SOL won from rewards', () => {
    db.exec('DELETE FROM payouts');
    db.exec('DELETE FROM ledger');

    const userInsert = db
      .prepare(
        'INSERT INTO users (mc_uuid, mc_username, balance_lamports, created_at) VALUES (?, ?, ?, ?)',
      )
      .run('uuid-a', 'Alice', '0', Date.now());
    const aliceId = Number(userInsert.lastInsertRowid);

    recordMatchPlayerStats(['uuid-a'], [
      {
        mcUuid: 'uuid-a',
        mcUsername: 'Alice',
        kills: 10,
        finalKills: 4,
        bedsBroken: 3,
        deaths: 1,
      },
    ]);

    db.prepare(
      'INSERT INTO payouts (user_id, kind, amount_lamports, destination, status, created_at) VALUES (?, ?, ?, ?, ?, ?)',
    ).run(aliceId, 'reward', '1500000000', 'addr', 'sent', Date.now());

    const board = sweatzoneLeaderboard();
    assert.equal(board.length, 1);
    assert.equal(board[0]?.username, 'Alice');
    assert.equal(board[0]?.wins, 1);
    assert.equal(board[0]?.kills, 10);
    assert.equal(board[0]?.bedsBroken, 3);
    assert.equal(board[0]?.solWon, 1.5);
  });
});
