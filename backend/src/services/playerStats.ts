import { db } from '../db/index.js';
import { lamportsToSol } from '../util/money.js';

export interface MatchPlayerStatLine {
  mcUuid: string;
  mcUsername: string;
  kills: number;
  finalKills: number;
  bedsBroken: number;
  deaths: number;
}

export interface PlayerStatsRow {
  mcUuid: string;
  mcUsername: string;
  matchesPlayed: number;
  wins: number;
  kills: number;
  finalKills: number;
  bedsBroken: number;
  deaths: number;
}

/** Accumulate per-player combat stats after a completed match. */
export function recordMatchPlayerStats(
  winnerUuids: string[],
  lines: MatchPlayerStatLine[],
): void {
  if (lines.length === 0) return;
  const winners = new Set(winnerUuids);
  const now = Date.now();
  const upsert = db.prepare(`
    INSERT INTO player_stats (
      mc_uuid, mc_username, matches_played, wins, kills, final_kills, beds_broken, deaths, updated_at
    ) VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(mc_uuid) DO UPDATE SET
      mc_username = excluded.mc_username,
      matches_played = player_stats.matches_played + 1,
      wins = player_stats.wins + excluded.wins,
      kills = player_stats.kills + excluded.kills,
      final_kills = player_stats.final_kills + excluded.final_kills,
      beds_broken = player_stats.beds_broken + excluded.beds_broken,
      deaths = player_stats.deaths + excluded.deaths,
      updated_at = excluded.updated_at
  `);

  const tx = db.transaction(() => {
    for (const line of lines) {
      const won = winners.has(line.mcUuid) ? 1 : 0;
      upsert.run(
        line.mcUuid,
        line.mcUsername,
        won,
        line.kills,
        line.finalKills,
        line.bedsBroken,
        line.deaths,
        now,
      );
    }
  });
  tx();
}

export function getPlayerStats(mcUuid: string): PlayerStatsRow | null {
  const row = db
    .prepare(
      `SELECT mc_uuid, mc_username, matches_played, wins, kills, final_kills, beds_broken, deaths
       FROM player_stats WHERE mc_uuid = ?`,
    )
    .get(mcUuid) as
    | {
        mc_uuid: string;
        mc_username: string;
        matches_played: number;
        wins: number;
        kills: number;
        final_kills: number;
        beds_broken: number;
        deaths: number;
      }
    | undefined;
  if (!row) return null;
  return {
    mcUuid: row.mc_uuid,
    mcUsername: row.mc_username,
    matchesPlayed: row.matches_played,
    wins: row.wins,
    kills: row.kills,
    finalKills: row.final_kills,
    bedsBroken: row.beds_broken,
    deaths: row.deaths,
  };
}

export function combatLeaderboard(): {
  killers: Array<{ name: string; kills: number; finalKills: number }>;
  bedBreakers: Array<{ name: string; bedsBroken: number }>;
  winners: Array<{ name: string; wins: number; matchesPlayed: number }>;
} {
  const killers = db
    .prepare(
      `SELECT mc_username AS name, kills, final_kills AS finalKills
       FROM player_stats
       WHERE kills > 0
       ORDER BY kills DESC, final_kills DESC
       LIMIT 10`,
    )
    .all() as Array<{ name: string; kills: number; finalKills: number }>;

  const bedBreakers = db
    .prepare(
      `SELECT mc_username AS name, beds_broken AS bedsBroken
       FROM player_stats
       WHERE beds_broken > 0
       ORDER BY beds_broken DESC
       LIMIT 10`,
    )
    .all() as Array<{ name: string; bedsBroken: number }>;

  const winners = db
    .prepare(
      `SELECT mc_username AS name, wins, matches_played AS matchesPlayed
       FROM player_stats
       WHERE wins > 0
       ORDER BY wins DESC, matches_played DESC
       LIMIT 10`,
    )
    .all() as Array<{ name: string; wins: number; matchesPlayed: number }>;

  return { killers, bedBreakers, winners };
}

export interface SweatzoneEntry {
  username: string;
  wins: number;
  kills: number;
  bedsBroken: number;
  solWon: number;
}

/** All-time combat stats plus total SOL won from match rewards. */
export function sweatzoneLeaderboard(limit = 50): SweatzoneEntry[] {
  const rows = db
    .prepare(
      `SELECT
        ps.mc_username AS username,
        ps.wins AS wins,
        ps.kills AS kills,
        ps.beds_broken AS bedsBroken,
        (
          COALESCE((
            SELECT SUM(CAST(p.amount_lamports AS INTEGER))
            FROM payouts p
            WHERE p.user_id = u.id AND p.kind = 'reward'
          ), 0)
          + COALESCE((
            SELECT SUM(CAST(l.delta_lamports AS INTEGER))
            FROM ledger l
            WHERE l.user_id = u.id AND l.reason LIKE 'reward%'
          ), 0)
        ) AS wonLamports
      FROM player_stats ps
      LEFT JOIN users u ON u.mc_uuid = ps.mc_uuid
      ORDER BY ps.wins DESC, ps.kills DESC, ps.beds_broken DESC, wonLamports DESC
      LIMIT ?`,
    )
    .all(limit) as Array<{
      username: string;
      wins: number;
      kills: number;
      bedsBroken: number;
      wonLamports: number;
    }>;

  return rows.map((r) => ({
    username: r.username,
    wins: r.wins,
    kills: r.kills,
    bedsBroken: r.bedsBroken,
    solWon: lamportsToSol(BigInt(r.wonLamports)),
  }));
}
