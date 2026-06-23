import { Keypair } from '@solana/web3.js';
import bs58 from 'bs58';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { config } from '../config.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const walletsDir = join(__dirname, '..', '..', 'wallets');
mkdirSync(walletsDir, { recursive: true });

export function keypairFromBase58(secret: string): Keypair {
  return Keypair.fromSecretKey(bs58.decode(secret.trim()));
}

export function secretToBase58(kp: Keypair): string {
  return bs58.encode(kp.secretKey);
}

// Loads the house hot wallet from env, else from wallets/house.json, else generates one.
export function loadHouseWallet(): Keypair {
  if (config.solana.houseWalletSecret) {
    return keypairFromBase58(config.solana.houseWalletSecret);
  }
  const file = join(walletsDir, 'house.json');
  if (existsSync(file)) {
    const secret = JSON.parse(readFileSync(file, 'utf8')) as number[];
    return Keypair.fromSecretKey(Uint8Array.from(secret));
  }
  const kp = Keypair.generate();
  writeFileSync(file, JSON.stringify(Array.from(kp.secretKey)));
  return kp;
}

export function newDepositKeypair(): Keypair {
  return Keypair.generate();
}
