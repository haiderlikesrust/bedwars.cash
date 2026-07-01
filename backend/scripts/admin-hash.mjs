#!/usr/bin/env node
// Generate an ADMIN_PASSWORD_HASH for the admin panel.
//
//   node scripts/admin-hash.mjs 'your-strong-password'
//
// Copy the printed value into your .env as ADMIN_PASSWORD_HASH=...
// The plaintext password is never stored — only this scrypt hash.
import { scryptSync, randomBytes } from 'node:crypto';

const password = process.argv[2];
if (!password) {
  console.error("usage: node scripts/admin-hash.mjs '<password>'");
  process.exit(1);
}
if (password.length < 12) {
  console.error('Refusing: use a password of at least 12 characters.');
  process.exit(1);
}

const salt = randomBytes(16);
const hash = scryptSync(password, salt, 64);
console.log(`${salt.toString('base64')}.${hash.toString('base64')}`);
