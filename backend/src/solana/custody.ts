import {
  Keypair,
  PublicKey,
  SystemProgram,
  Transaction,
  sendAndConfirmTransaction,
} from '@solana/web3.js';
import { connection } from './connection.js';
import { loadHouseWallet, newDepositKeypair, secretToBase58, keypairFromBase58 } from './wallet.js';
import { encryptSecret, decryptSecret } from '../util/crypto.js';
import {
  applyDelta,
  attachDepositWallet,
  getDepositSecretEnc,
  getUserById,
  listUsersWithDepositWallets,
} from '../services/accounts.js';
import { db, kvGet, kvSet } from '../db/index.js';
import { config } from '../config.js';
import { lamportsToSol } from '../util/money.js';

const FEE_BUFFER = 5000n; // lamports reserved for a signature fee
export const house = loadHouseWallet();

export function houseAddress(): string {
  return house.publicKey.toBase58();
}

export async function houseBalanceLamports(): Promise<bigint> {
  return BigInt(await connection.getBalance(house.publicKey));
}

// Lazily create a custodial deposit wallet for a user.
export function ensureDepositWallet(userId: number): string {
  const user = getUserById(userId);
  if (!user) throw new Error('USER_NOT_FOUND');
  if (user.depositPubkey) return user.depositPubkey;
  const kp = newDepositKeypair();
  attachDepositWallet(userId, kp.publicKey.toBase58(), encryptSecret(secretToBase58(kp)));
  return kp.publicKey.toBase58();
}

function depositKeypair(userId: number): Keypair {
  const enc = getDepositSecretEnc(userId);
  if (!enc) throw new Error('NO_DEPOSIT_WALLET');
  return keypairFromBase58(decryptSecret(enc));
}

// Best-effort on-chain wallet age in hours; returns null if it can't be determined.
export async function walletAgeHours(address: string): Promise<number | null> {
  try {
    const sigs = await connection.getSignaturesForAddress(new PublicKey(address), { limit: 1000 });
    if (sigs.length === 0) return 0;
    const oldest = sigs[sigs.length - 1];
    if (!oldest.blockTime) return null;
    return (Date.now() / 1000 - oldest.blockTime) / 3600;
  } catch {
    return null;
  }
}

interface DepositScanResult {
  userId: number;
  creditedLamports: bigint;
  signatures: string[];
}

// Detect new incoming transfers to each deposit wallet, credit the user's balance
// (idempotent by signature), then sweep funds to the house wallet.
export async function scanDeposits(): Promise<DepositScanResult[]> {
  const results: DepositScanResult[] = [];
  for (const user of listUsersWithDepositWallets()) {
    if (!user.depositPubkey) continue;
    const pubkey = new PublicKey(user.depositPubkey);
    const lastSig = kvGet(`lastsig:${user.depositPubkey}`) ?? undefined;
    let sigs;
    try {
      sigs = await connection.getSignaturesForAddress(pubkey, { until: lastSig, limit: 100 });
    } catch {
      continue;
    }
    if (sigs.length === 0) {
      await sweepDeposit(user.id, pubkey);
      continue;
    }
    // Process oldest -> newest so `until` cursor advances correctly.
    const ordered = [...sigs].reverse();
    let credited = 0n;
    const processed: string[] = [];
    for (const s of ordered) {
      if (depositAlreadyProcessed(s.signature)) continue;
      const received = await incomingLamports(s.signature, user.depositPubkey);
      if (received > 0n) {
        recordDeposit(s.signature, user.id, received);
        applyDelta(user.id, received, `deposit:${s.signature.slice(0, 12)}`);
        credited += received;
        processed.push(s.signature);
      }
    }
    kvSet(`lastsig:${user.depositPubkey}`, sigs[0].signature);
    if (credited > 0n) results.push({ userId: user.id, creditedLamports: credited, signatures: processed });
    await sweepDeposit(user.id, pubkey);
  }
  return results;
}

function depositAlreadyProcessed(signature: string): boolean {
  return !!db.prepare('SELECT 1 FROM deposits WHERE signature = ?').get(signature);
}

function recordDeposit(signature: string, userId: number, lamports: bigint): void {
  db.prepare(
    'INSERT OR IGNORE INTO deposits (signature, user_id, amount_lamports, created_at) VALUES (?, ?, ?, ?)',
  ).run(signature, userId, lamports.toString(), Date.now());
}

// Net lamports received by `address` in a confirmed transaction.
async function incomingLamports(signature: string, address: string): Promise<bigint> {
  try {
    const tx = await connection.getParsedTransaction(signature, {
      maxSupportedTransactionVersion: 0,
      commitment: 'confirmed',
    });
    if (!tx || tx.meta == null) return 0n;
    const keys = tx.transaction.message.accountKeys.map((k) => k.pubkey.toBase58());
    const idx = keys.indexOf(address);
    if (idx < 0) return 0n;
    const delta = BigInt(tx.meta.postBalances[idx]) - BigInt(tx.meta.preBalances[idx]);
    return delta > 0n ? delta : 0n;
  } catch {
    return 0n;
  }
}

// Move everything (minus a fee buffer) from a deposit wallet into the house wallet.
async function sweepDeposit(userId: number, pubkey: PublicKey): Promise<void> {
  let balance: bigint;
  try {
    balance = BigInt(await connection.getBalance(pubkey));
  } catch {
    return;
  }
  if (balance <= FEE_BUFFER) return;
  const amount = balance - FEE_BUFFER;
  try {
    const kp = depositKeypair(userId);
    const tx = new Transaction().add(
      SystemProgram.transfer({
        fromPubkey: pubkey,
        toPubkey: house.publicKey,
        lamports: Number(amount),
      }),
    );
    await sendAndConfirmTransaction(connection, tx, [kp], { commitment: 'confirmed' });
  } catch {
    // Sweep can be retried on the next scan; deposit was already credited.
  }
}

export interface PayoutResult {
  status: 'sent' | 'held' | 'failed';
  signature?: string;
  payoutId: number;
}

// Send lamports from the house wallet to an arbitrary destination address.
// Amounts above the manual-review threshold are held instead of sent.
export async function houseTransfer(
  destination: string,
  lamports: bigint,
  kind: string,
  userId: number | null,
): Promise<PayoutResult> {
  const insert = db
    .prepare(
      'INSERT INTO payouts (user_id, kind, amount_lamports, destination, status, created_at) VALUES (?, ?, ?, ?, ?, ?)',
    )
    .run(userId, kind, lamports.toString(), destination, 'pending', Date.now());
  const payoutId = Number(insert.lastInsertRowid);

  if (lamportsToSol(lamports) > config.antiAbuse.manualReviewThresholdSol) {
    db.prepare('UPDATE payouts SET status = ? WHERE id = ?').run('held', payoutId);
    return { status: 'held', payoutId };
  }

  try {
    const tx = new Transaction().add(
      SystemProgram.transfer({
        fromPubkey: house.publicKey,
        toPubkey: new PublicKey(destination),
        lamports: Number(lamports),
      }),
    );
    const signature = await sendAndConfirmTransaction(connection, tx, [house], {
      commitment: 'confirmed',
    });
    db.prepare('UPDATE payouts SET status = ?, signature = ? WHERE id = ?').run('sent', signature, payoutId);
    return { status: 'sent', signature, payoutId };
  } catch (err) {
    db.prepare('UPDATE payouts SET status = ? WHERE id = ?').run('failed', payoutId);
    throw err;
  }
}
