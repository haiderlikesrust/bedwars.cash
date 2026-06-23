// Best-effort devnet funding for the house wallet, with retries across endpoints.
import { Connection, LAMPORTS_PER_SOL, PublicKey } from '@solana/web3.js';

const ADDRESS = process.argv[2];
if (!ADDRESS) {
  console.error('usage: node scripts/fund.mjs <houseAddress> [sol]');
  process.exit(1);
}
const SOL = Number(process.argv[3] ?? 1);
const ENDPOINTS = [
  process.env.SOLANA_RPC_URL ?? 'https://api.devnet.solana.com',
  'https://api.devnet.solana.com',
];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const pk = new PublicKey(ADDRESS);

for (let attempt = 1; attempt <= 6; attempt++) {
  const url = ENDPOINTS[attempt % ENDPOINTS.length];
  const conn = new Connection(url, 'confirmed');
  try {
    console.log(`attempt ${attempt}: airdrop ${SOL} SOL via ${url}`);
    const sig = await conn.requestAirdrop(pk, SOL * LAMPORTS_PER_SOL);
    await conn.confirmTransaction(sig, 'confirmed');
    const bal = await conn.getBalance(pk);
    console.log(`  ✅ funded. balance = ${bal / LAMPORTS_PER_SOL} SOL (sig ${sig.slice(0, 16)}…)`);
    process.exit(0);
  } catch (e) {
    console.log(`  failed: ${e.message}`);
    await sleep(3000);
  }
}
console.error('All airdrop attempts failed. The public devnet faucet is likely rate-limited.');
console.error('Alternatives: `solana airdrop 1 <addr> --url devnet`, https://faucet.solana.com, or QuickNode/Helius devnet faucet.');
process.exit(2);
