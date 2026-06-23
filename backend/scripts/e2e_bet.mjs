// Full parimutuel betting e2e (no external SOL needed). Verifies that when GREEN
// wins, GREEN's backer splits 95% of the pool and the loser gets nothing.
//
// Backend must run with: MATCH_PLAYER_COUNT=4 TEAM_SIZE=1
//   node scripts/e2e_bet.mjs

import WebSocket from 'ws';
import { randomUUID } from 'node:crypto';

const API = process.env.API_URL ?? 'http://localhost:8787';
const WS = process.env.WS_PLUGIN_URL ?? 'ws://localhost:8787/ws/plugin';
const TOKEN = process.env.PLUGIN_TOKEN ?? 'change-me-plugin-token';
const ADMIN = process.env.APP_SECRET ?? 'change-me-app-secret-please-32chars-min';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function post(path, body, headers = {}) {
  const res = await fetch(API + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body ?? {}),
  });
  if (!res.ok) throw new Error(`${path} -> ${res.status} ${await res.text()}`);
  return res.json();
}
const meWith = (token) => fetch(API + '/api/me', { headers: { Authorization: `Bearer ${token}` } }).then((r) => r.json());
const send = (ws, obj) => ws.send(JSON.stringify(obj));

async function setupSpectator(name, team) {
  const s = await post('/api/session');
  await post('/api/admin/credit', { userId: s.userId, sol: 1 }, { 'x-admin-token': ADMIN });
  const { code } = await post('/api/link/code', {}, { Authorization: `Bearer ${s.token}` });
  return { ...s, name, team, uuid: randomUUID(), code };
}

async function main() {
  console.log('Setting up two spectators with 1 SOL each (devnet admin credit)...');
  const s1 = await setupSpectator('green-backer', 'GREEN');
  const s2 = await setupSpectator('red-backer', 'RED');

  const players = Array.from({ length: 4 }, () => randomUUID());
  const ws = new WebSocket(`${WS}?token=${TOKEN}`);
  let matchId = null;

  ws.on('open', async () => {
    send(ws, { type: 'hello', token: TOKEN });
    // Link spectator website accounts to in-game uuids.
    send(ws, { type: 'verify', mcUuid: s1.uuid, mcUsername: s1.name, code: s1.code });
    send(ws, { type: 'verify', mcUuid: s2.uuid, mcUsername: s2.name, code: s2.code });
    await sleep(800);

    console.log('Placing bets: GREEN 0.5 SOL and RED 0.5 SOL (pool 1.0, 5% rake)...');
    send(ws, { type: 'place_bet', mcUuid: s1.uuid, team: 'GREEN', amountSol: 0.5 });
    send(ws, { type: 'place_bet', mcUuid: s2.uuid, team: 'RED', amountSol: 0.5 });
    await sleep(800);

    console.log('Queueing 4 players to start the match (betting locks)...');
    for (const p of players) send(ws, { type: 'queue_join', mcUuid: p });
  });

  ws.on('message', async (data) => {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'start_match') {
      matchId = msg.matchId;
      const greenUuid = msg.teams.GREEN[0];
      console.log(`Match ${matchId} started. Reporting GREEN winner...`);
      await sleep(800);
      send(ws, { type: 'match_result', matchId, winningTeam: 'GREEN', winnerUuids: [greenUuid] });
      await sleep(2500);

      const m1 = await meWith(s1.token);
      const m2 = await meWith(s2.token);
      console.log('\nResults (parimutuel):');
      console.log(`  GREEN backer balance: ${m1.balanceSol} SOL  (expected 1.45 = 1.0 - 0.5 bet + 0.95 win)`);
      console.log(`  RED   backer balance: ${m2.balanceSol} SOL  (expected 0.5  = 1.0 - 0.5 bet + 0 loss)`);
      const ok = Math.abs(m1.balanceSol - 1.45) < 1e-6 && Math.abs(m2.balanceSol - 0.5) < 1e-6;
      console.log(ok ? '\n  ✅ Parimutuel settlement correct.' : '\n  ❌ Unexpected balances.');
      ws.close();
      process.exit(ok ? 0 : 1);
    }
  });
  ws.on('error', (e) => { console.error('WS error:', e.message); process.exit(1); });
  setTimeout(() => { console.error('Timed out.'); process.exit(1); }, 30000);
}

main();
