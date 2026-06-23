import { connection } from './connection.js';
import { house, houseBalanceLamports } from './custody.js';
import { config, isDevnet } from '../config.js';
import { solToLamports } from '../util/money.js';
import { totalLiabilitiesLamports } from '../services/accounts.js';

// A fee source funds the house wallet that pays the player reward pool.
export interface FeeSource {
  readonly name: string;
  // Pull available creator fees into the house wallet; returns lamports added.
  collect(): Promise<bigint>;
}

// Devnet: simulate Pump.fun creator-fee inflow by airdropping test SOL to the house wallet.
class MockFeeSource implements FeeSource {
  readonly name = 'mock';
  async collect(): Promise<bigint> {
    return 0n; // periodic auto-collect is a no-op; use topUp() for explicit mock funding
  }
}

// Mainnet: sweep real Pump.fun creator fees into the house wallet via @pump-fun/pump-sdk.
// Intentionally not wired on devnet (Pump.fun is mainnet-only). To enable on mainnet:
//   1. npm i @pump-fun/pump-sdk
//   2. use OnlinePumpSdk.getCreatorVaultBalanceBothPrograms + collect instructions
//      to sweep PUMPFUN_CREATOR_WALLET's vaults into the house wallet.
class PumpFunFeeSource implements FeeSource {
  readonly name = 'pumpfun';
  async collect(): Promise<bigint> {
    throw new Error(
      'PumpFunFeeSource is mainnet-only and not enabled. Install @pump-fun/pump-sdk and implement the sweep before mainnet.',
    );
  }
}

export const feeSource: FeeSource =
  config.treasury.feeSource === 'pumpfun' ? new PumpFunFeeSource() : new MockFeeSource();

// Explicit mock funding for devnet testing (airdrop test SOL to the house wallet).
export async function mockTopUp(sol: number): Promise<bigint> {
  if (!isDevnet()) throw new Error('mockTopUp is only allowed on devnet');
  const lamports = solToLamports(sol);
  const sig = await connection.requestAirdrop(house.publicKey, Number(lamports));
  await connection.confirmTransaction(sig, 'confirmed');
  return lamports;
}

// The reward pool available for the next match: house balance minus the reserve
// floor, optionally capped.
export async function availableRewardPool(): Promise<bigint> {
  const balance = await houseBalanceLamports();
  const reserve = solToLamports(config.solana.houseReserveSol);
  // Subtract custodial liabilities so rewards never spend user deposits or bet stakes.
  const liabilities = totalLiabilitiesLamports();
  let pool = balance - reserve - liabilities;
  if (pool < 0n) pool = 0n;
  if (config.solana.rewardPoolCapSol > 0) {
    const cap = solToLamports(config.solana.rewardPoolCapSol);
    if (pool > cap) pool = cap;
  }
  return pool;
}
