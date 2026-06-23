import { LAMPORTS_PER_SOL } from '@solana/web3.js';

export const LAMPORTS = BigInt(LAMPORTS_PER_SOL);

export function solToLamports(sol: number): bigint {
  // Avoid floating point drift by rounding to the nearest lamport.
  return BigInt(Math.round(sol * Number(LAMPORTS)));
}

export function lamportsToSol(lamports: bigint): number {
  return Number(lamports) / Number(LAMPORTS);
}

export function formatSol(lamports: bigint, dp = 4): string {
  return lamportsToSol(lamports).toFixed(dp);
}
