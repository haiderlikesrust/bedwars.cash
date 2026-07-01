import { test } from 'node:test';
import assert from 'node:assert/strict';
import { hashPassword, verifyPassword } from '../src/util/crypto.ts';

test('password hashing round-trips and rejects wrong passwords', () => {
  const hash = hashPassword('correct horse battery staple');
  assert.ok(hash.includes('.'));
  assert.equal(verifyPassword('correct horse battery staple', hash), true);
  assert.equal(verifyPassword('wrong password', hash), false);
  assert.equal(verifyPassword('', hash), false);
});

test('verifyPassword is safe against malformed stored hashes', () => {
  assert.equal(verifyPassword('x', ''), false);
  assert.equal(verifyPassword('x', 'garbage'), false);
  assert.equal(verifyPassword('x', 'only-one-part'), false);
});

test('each hash uses a fresh salt', () => {
  const a = hashPassword('same-password-12chars');
  const b = hashPassword('same-password-12chars');
  assert.notEqual(a, b);
  assert.equal(verifyPassword('same-password-12chars', a), true);
  assert.equal(verifyPassword('same-password-12chars', b), true);
});
