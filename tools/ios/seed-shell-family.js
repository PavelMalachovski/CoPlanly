#!/usr/bin/env node
/**
 * Seeds the family the iPhone shell's UI test signs in to (MON-24 phase 3, Task 8): two accounts on
 * the local Firebase emulators, paired through the real `acceptPairingInvitation`, and prints the
 * first parent's e-mail address and password as JSON on stdout for the test to sign in with.
 *
 * Run inside `firebase emulators:exec --only auth,firestore,functions,storage --project
 * demo-coplanly`, like `tools/e2e/pairing-smoke.js`, whose helpers it uses: every write goes through
 * the REST API with the parent's own token, so `firestore.rules` holds it as it holds the app.
 *
 * Each profile is the one `ensureProfile` writes, plus the two answers a parent gives once and the
 * first screens would otherwise ask for: `onboardingCompletedAt` (the wizard has run) and
 * `parentRole` (Home's stickers question, CLAUDE.md item 47). Loopback only; nothing reaches a real
 * project.
 */

'use strict';

const {call, toFields, signUp, writeInvitation, functionsHost, DOCS, PROJECT} =
    require('../e2e/pairing-smoke.js');

/** The password `signUp` gives every account. */
const PASSWORD = 'e2e-password-1';

/**
 * Writes a parent's profile as a parent who has finished onboarding.
 *
 * @param {{uid: string, token: string, email: string, name: string}} parent The account.
 * @param {string} role Their `parentRole`: "mom", "dad" or "parent".
 */
async function writeFinishedProfile(parent, role) {
  await call(`${DOCS}/users/${parent.uid}`, {
    method: 'PATCH',
    token: parent.token,
    body: toFields({
      name: parent.name,
      email: parent.email,
      id: parent.uid,
      firebaseUid: parent.uid,
      onboardingCompletedAt: '2026-11-01T09:00:00',
      parentRole: role,
    }),
  });
}

/** Seeds the pair and prints the sign-in. */
async function main() {
  const functions = await functionsHost();
  const alice = await signUp('Alice');
  const bob = await signUp('Bob');
  await writeFinishedProfile(alice, 'mom');
  await writeFinishedProfile(bob, 'dad');
  const code = await writeInvitation(alice);
  const accepted = await call(
      `http://${functions}/${PROJECT}/europe-west3/acceptPairingInvitation`,
      {method: 'POST', token: bob.token, body: {data: {code}}});
  if (!accepted.result || accepted.result.partnerId !== alice.uid) {
    throw new Error(`acceptPairingInvitation returned ${JSON.stringify(accepted)}`);
  }
  process.stdout.write(JSON.stringify({email: alice.email, password: PASSWORD}) + '\n');
}

main().catch((error) => {
  console.error(`Seeding the shell's family FAILED: ${error.message}`);
  process.exit(1);
});
