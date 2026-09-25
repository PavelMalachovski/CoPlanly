#!/usr/bin/env node
/**
 * Pairs two fresh accounts on the local Firebase emulators, from Node, in a few seconds.
 *
 * Run inside `firebase emulators:exec --only auth,firestore,functions --project demo-coplanly`
 * (see `run-two-parent-tests.sh`, which does exactly that). It exists for two reasons:
 *
 *  1. **It is the readiness gate for the Android job.** Booting an Android emulator costs
 *     minutes; finding out *then* that the Functions emulator failed to load `index.js`, or that
 *     the Auth emulator is not where `firebase.json` says, wastes all of them and produces a
 *     timeout rather than a reason. This fails first, and says which service is missing.
 *  2. **It proves the server half of pairing against the real rules and the real callable**,
 *     independently of any Kotlin. Every client write below goes through the Firestore REST API
 *     with the parent's own ID token, so `firestore.rules` is enforced exactly as it is for the
 *     app; only the final checks use the emulator's `Bearer owner` bypass, and only to read.
 *
 * The document shapes mirror the client's: the profile is what `UserRepositoryImpl.ensureProfile`
 * merges and the invitation is what `PairingRepositoryImpl.writeNewInvite` sets. If either
 * changes, change it here too — a smoke test that writes a shape the app never writes proves
 * nothing about the app.
 *
 * No dependencies: Node 20's global `fetch` is enough, which is what lets it run before any
 * `npm ci` it does not need.
 */

'use strict';

const crypto = require('crypto');

const PROJECT = process.env.GCLOUD_PROJECT || 'demo-coplanly';
const AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const FIRESTORE = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080';
const DOCS = `http://${FIRESTORE}/v1/projects/${PROJECT}/databases/(default)/documents`;

/** The alphabet `InviteCodeGenerator` draws from — no 0/O, 1/I/L. */
const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

/**
 * Sends a JSON request and returns the parsed body, failing loudly with the status and body.
 *
 * @param {string} url Absolute URL.
 * @param {{method?: string, token?: string, body?: Object}} options Request options.
 * @return {Promise<Object>} The parsed response.
 */
async function call(url, {method = 'GET', token, body} = {}) {
  const headers = {'Content-Type': 'application/json'};
  if (token) headers.Authorization = `Bearer ${token}`;
  let response;
  try {
    response = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (error) {
    // `fetch failed` alone names neither the URL nor the reason; an emulator that is not
    // listening is the usual one, and the address is what tells you which.
    throw new Error(`${method} ${url} -> ${error.cause ? error.cause.message : error.message}`);
  }
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`${method} ${url} -> ${response.status}: ${text}`);
  }
  return text ? JSON.parse(text) : {};
}

/**
 * Converts a flat JS object into Firestore REST `fields`.
 *
 * @param {Object} data Strings, integers, booleans and null only — all the shapes need.
 * @return {Object} REST fields.
 */
function toFields(data) {
  const fields = {};
  for (const [key, value] of Object.entries(data)) {
    if (value === null) fields[key] = {nullValue: null};
    else if (typeof value === 'boolean') fields[key] = {booleanValue: value};
    else if (typeof value === 'number') fields[key] = {integerValue: String(value)};
    else fields[key] = {stringValue: String(value)};
  }
  return {fields};
}

/**
 * Creates an Auth emulator account.
 *
 * @param {string} name Display name, used for the profile as well.
 * @return {Promise<{uid: string, token: string, email: string, name: string}>} The account.
 */
async function signUp(name) {
  const email = `${name.toLowerCase()}-${crypto.randomUUID()}@e2e.coplanly.test`;
  const result = await call(
      `http://${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`,
      {method: 'POST', body: {email, password: 'e2e-password-1', returnSecureToken: true}});
  return {uid: result.localId, token: result.idToken, email, name};
}

/**
 * Writes the profile `ensureProfile` would, as the parent themselves.
 *
 * @param {{uid: string, token: string, email: string, name: string}} parent The account.
 */
async function writeProfile(parent) {
  await call(`${DOCS}/users/${parent.uid}`, {
    method: 'PATCH',
    token: parent.token,
    body: toFields({
      name: parent.name,
      email: parent.email,
      id: parent.uid,
      firebaseUid: parent.uid,
    }),
  });
}

/**
 * Mints a code invitation exactly as `PairingRepositoryImpl.writeNewInvite` does.
 *
 * @param {{uid: string, token: string, email: string, name: string}} inviter The inviter.
 * @return {Promise<string>} The six-character code.
 */
async function writeInvitation(inviter) {
  const id = crypto.randomUUID();
  const code = Array.from(crypto.randomBytes(6), (b) => CODE_ALPHABET[b % CODE_ALPHABET.length])
      .join('');
  const now = Date.now();
  await call(`${DOCS}/invitations/${id}`, {
    method: 'PATCH',
    token: inviter.token,
    body: toFields({
      id,
      code,
      fromUserId: inviter.uid,
      fromUserName: inviter.name,
      fromUserEmail: inviter.email,
      toEmail: '',
      status: 'pending',
      createdAt: now,
      expiresAt: now + 24 * 60 * 60 * 1000,
      acceptedBy: null,
    }),
  });
  return code;
}

/**
 * Reads a document with the emulator's rules bypass. Read-only checks use this and nothing else.
 *
 * @param {string} path Document path under `documents/`.
 * @return {Promise<Object>} The REST document.
 */
function adminGet(path) {
  return call(`${DOCS}/${path}`, {token: 'owner'});
}

/**
 * Throws unless [condition] holds.
 *
 * @param {boolean} condition What must be true.
 * @param {string} message What went wrong otherwise.
 */
function check(condition, message) {
  if (!condition) throw new Error(message);
}

/**
 * Where the Functions emulator listens.
 *
 * `emulators:exec` exports the Auth and Firestore addresses but not this one, so it is asked of
 * the emulator hub, which does know — falling back to `firebase.json`'s port.
 *
 * @return {Promise<string>} `host:port`.
 */
async function functionsHost() {
  if (process.env.FUNCTIONS_EMULATOR_HOST) return process.env.FUNCTIONS_EMULATOR_HOST;
  const hub = process.env.FIREBASE_EMULATOR_HUB;
  if (hub) {
    const emulators = await call(`http://${hub}/emulators`);
    if (emulators.functions) return `${emulators.functions.host}:${emulators.functions.port}`;
  }
  return '127.0.0.1:5001';
}

/** Runs the scenario. */
async function main() {
  const functions = await functionsHost();
  const alice = await signUp('Alice');
  const bob = await signUp('Bob');
  await writeProfile(alice);
  await writeProfile(bob);
  const code = await writeInvitation(alice);

  // The callable protocol: `{data}` in, `{result}` out, the caller's ID token as the bearer.
  const accepted = await call(
      `http://${functions}/${PROJECT}/europe-west3/acceptPairingInvitation`,
      {method: 'POST', token: bob.token, body: {data: {code}}});
  check(accepted.result && accepted.result.partnerId === alice.uid,
      `acceptPairingInvitation returned ${JSON.stringify(accepted)}`);

  const familyId = [alice.uid, bob.uid].sort().join('__');
  const family = await adminGet(`families/${familyId}`);
  const slots = family.fields.slots.mapValue.fields;
  check(slots[alice.uid] && slots[bob.uid], `families/${familyId} has no slots for both parents`);
  check(slots[alice.uid].stringValue !== slots[bob.uid].stringValue,
      'both parents were given the same slot');

  for (const [me, partner] of [[alice, bob], [bob, alice]]) {
    const profile = await adminGet(`users/${me.uid}`);
    check(profile.fields.partnerId.stringValue === partner.uid,
        `users/${me.uid}.partnerId does not name the co-parent`);
  }

  console.log(`Pairing smoke passed: ${familyId} ` +
      `(${slots[alice.uid].stringValue}/${slots[bob.uid].stringValue}).`);
}

main().catch((error) => {
  console.error(`Pairing smoke FAILED: ${error.message}`);
  process.exit(1);
});
