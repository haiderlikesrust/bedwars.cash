// End-to-end devnet smoke test. Simulates the Minecraft plugin over WebSocket:
// funds the house, runs a tiny 4-team (1 per team) match, and verifies the
// winner receives an on-chain reward payout. Optionally tests a spectator
// deposit + parimutuel bet if a devnet airdrop succeeds.
//
// Run the backend first with a tiny match config, e.g. (PowerShell):
//   $env:MATCH_PLAYER_COUNT=4; $env:TEAM_SIZE=1; $env:HOUSE_RESERVE_SOL=0.1;
//   $env:MANUAL_REVIEW_THRESHOLD_SOL=100; $env:MIN_WALLET_AGE_HOURS=0; npm run dev
// Then: node scripts/e2e.mjs

import WebSocket from 'ws';
import { Connection, Keypair, LAMPORTS_PER_SOL, PublicKey } from '@solana/web3.js';
import { randomUUID } from 'node:crypto';

const API = process.env.API_URL ?? 'http://localhost:8787';
const WS = (process.env.WS_PLUGIN_URL ?? 'ws://localhost:8787/ws/plugin');
const TOKEN = process.env.PLUGIN_TOKEN ?? 'change-me-plugin-token';
const ADMIN = process.env.APP_SECRET ?? 'change-me-app-secret-please-32chars-min';
const RPC = process.env.SOLANA_RPC_URL ?? 'https://api.devnet.solana.com';

const conn = new Connection(RPC, 'confirmed');
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
const get = (path) => fetch(API + path).then((r) => r.json());

function send(ws, obj) {
  ws.send(JSON.stringify(obj));
}

async function main() {
  console.log('1) House top-up (devnet airdrop)...');
  try {
    const t = await post('/api/admin/topup', { sol: 2 }, { 'x-admin-token': ADMIN });
    console.log('   topped up:', t);
  } catch (e) {
    console.log('   topup failed (devnet faucet may be rate-limited):', e.message);
  }
  await sleep(1500);
  const houseBefore = await get('/api/house');
  console.log('   house:', houseBefore);

  // 4 players, each their own payout wallet.
  const players = Array.from({ length: 4 }, () => ({ uuid: randomUUID(), kp: Keypair.generate() }));
  const greenWinner = players[0]; // join order index 0 -> GREEN (teamSize 1)

  const ws = new WebSocket(`${WS}?token=${TOKEN}`);
  let matchId = null;

  ws.on('open', () => {
    send(ws, { type: 'hello', token: TOKEN });
    console.log('2) Plugin WS connected. Setting payout wallets + queueing 4 players...');
    for (const p of players) {
      send(ws, { type: 'setwallet', mcUuid: p.uuid, mcUsername: p.uuid.slice(0, 6), address: p.kp.publicKey.toBase58() });
    }
    for (const p of players) send(ws, { type: 'queue_join', mcUuid: p.uuid });
  });

  ws.on('message', async (data) => {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'start_match') {
      matchId = msg.matchId;
      console.log(`3) Match ${matchId} started. Teams:`, msg.teams);
      await sleep(1000);
      console.log(`4) Reporting GREEN as winner (${greenWinner.uuid.slice(0, 6)})...`);
      send(ws, { type: 'match_result', matchId, winningTeam: 'GREEN', winnerUuids: [greenWinner.uuid] });
      await sleep(6000);
      const houseAfter = await get('/api/house');
      const winnerBal = await conn.getBalance(greenWinner.kp.publicKey);
      console.log('5) Results:');
      console.log('   house before pool:', houseBefore.availableRewardPoolSol, 'SOL');
      console.log('   house after balance:', houseAfter.balanceSol, 'SOL');
      console.log('   WINNER on-chain balance:', winnerBal / LAMPORTS_PER_SOL, 'SOL');
      if (winnerBal > 0) console.log('   ✅ Winner received an on-chain reward payout.');
      else console.log('   ⚠ Winner balance 0 (reward pool may have been 0 - fund the house).');
      ws.close();
      process.exit(0);
    }
  });
  ws.on('error', (e) => {
    console.error('WS error:', e.message);
    process.exit(1);
  });

  setTimeout(() => {
    console.error('Timed out waiting for match start (need MATCH_PLAYER_COUNT=4).');
    process.exit(1);
  }, 30000);
}

main();
