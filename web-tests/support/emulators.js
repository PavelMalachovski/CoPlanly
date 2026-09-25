/**
 * The Firebase emulators, from a test: where they are, and the few REST calls the tests make.
 *
 * Run inside `firebase emulators:exec --only auth,firestore,functions` (`run-with-emulators.sh`
 * does exactly that), which exports `FIREBASE_EMULATOR_HUB`, `FIREBASE_AUTH_EMULATOR_HOST` and
 * `FIRESTORE_EMULATOR_HOST`. Same shapes as `tools/e2e/pairing-smoke.js`: Node's global `fetch`,
 * the callable protocol (`{data}` in, `{result}` out, the ID token as the bearer), and the
 * Firestore emulator's `Bearer owner` bypass **only** for seeding and reading fixtures — never to
 * stand in for a write the app itself makes through the rules.
 *
 * Nothing here can reach production: every address is the emulator's, and the project is the
 * credential-free `demo-coplanly`.
 */
'use strict';

const crypto = require('crypto');

const PROJECT = process.env.GCLOUD_PROJECT || 'demo-coplanly';
const AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const FIRESTORE = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080';
const DOCS = `http://${FIRESTORE}/v1/projects/${PROJECT}/databases/(default)/documents`;

/** Whether this process runs under `firebase emulators:exec`. */
function emulatorsRunning() {
  return Boolean(process.env.FIREBASE_EMULATOR_HUB);
}

/**
 * Skips the calling test when the emulators are absent — or fails it, when
 * `COPLANLY_REQUIRE_EMULATORS=1` (CI sets it, so the job cannot turn green by running nothing).
 *
 * @param {import('@playwright/test').TestType} test The Playwright `test` object.
 */
function requireEmulators(test) {
  if (emulatorsRunning()) return;
  if (process.env.COPLANLY_REQUIRE_EMULATORS === '1') {
    throw new Error('COPLANLY_REQUIRE_EMULATORS=1 but FIREBASE_EMULATOR_HUB is not set: ' +
      'run through run-with-emulators.sh (firebase emulators:exec).');
  }
  test.skip(true, 'Firebase emulators not running (use `npm test`, which starts them)');
}

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
  const response = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`${method} ${url} -> ${response.status}: ${text}`);
  return text ? JSON.parse(text) : {};
}

/**
 * Where the Functions emulator listens — asked of the hub, which `emulators:exec` names.
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

/**
 * The base URL a callable or HTTPS function is served under on the emulator — what the page's
 * `?functions=` parameter takes.
 *
 * @return {Promise<string>} e.g. `http://127.0.0.1:5001/demo-coplanly/europe-west3`.
 */
async function functionsBase() {
  return `http://${await functionsHost()}/${PROJECT}/europe-west3`;
}

/**
 * Calls a callable as [token]'s account (or unauthenticated when [token] is omitted).
 *
 * @param {string} name The function.
 * @param {Object} data The callable's `data`.
 * @param {string=} token An ID token.
 * @return {Promise<Object>} The callable's `result`.
 */
async function callable(name, data, token) {
  const body = await call(`${await functionsBase()}/${name}`, {method: 'POST', token, body: {data}});
  if (!body.result) throw new Error(`${name} returned ${JSON.stringify(body)}`);
  return body.result;
}

/**
 * Creates an Auth emulator account.
 *
 * @param {string} name Display name, used for the profile too.
 * @return {Promise<{uid: string, token: string, email: string, name: string}>} The account.
 */
async function signUp(name) {
  const email = `web-${crypto.randomUUID()}@web-tests.coplanly.test`;
  const result = await call(
      `http://${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`,
      {method: 'POST', body: {email, password: 'web-tests-password-1', returnSecureToken: true}});
  return {uid: result.localId, token: result.idToken, email, name};
}

/**
 * A JS value as a Firestore REST value: strings, integers, doubles, booleans, null, arrays and
 * plain objects — every shape the fixtures need.
 *
 * @param {*} value The value.
 * @return {Object} The REST value.
 */
function toValue(value) {
  if (value === null || value === undefined) return {nullValue: null};
  if (typeof value === 'boolean') return {booleanValue: value};
  if (typeof value === 'number') {
    return Number.isInteger(value) ? {integerValue: String(value)} : {doubleValue: value};
  }
  if (Array.isArray(value)) return {arrayValue: {values: value.map(toValue)}};
  if (typeof value === 'object') return {mapValue: toFields(value)};
  return {stringValue: String(value)};
}

/**
 * @param {!Object} data A plain object.
 * @return {{fields: !Object}} REST fields.
 */
function toFields(data) {
  const fields = {};
  for (const [key, value] of Object.entries(data)) fields[key] = toValue(value);
  return {fields};
}

/**
 * Writes a document through the rules, as [token]'s account.
 *
 * @param {string} path Document path under `documents/`.
 * @param {!Object} data The fields.
 * @param {string} token An ID token.
 */
async function writeAs(path, data, token) {
  await call(`${DOCS}/${path}`, {method: 'PATCH', token, body: toFields(data)});
}

/**
 * Seeds a document with the emulator's rules bypass. Fixtures only.
 *
 * @param {string} path Document path under `documents/`.
 * @param {!Object} data The fields.
 */
async function seed(path, data) {
  await call(`${DOCS}/${path}`, {method: 'PATCH', token: 'owner', body: toFields(data)});
}

/**
 * Two accounts, profiles written as themselves, paired through the real
 * `acceptPairingInvitation` — the same steps `tools/e2e/pairing-smoke.js` takes, and the
 * invitation shape `PairingRepositoryImpl.writeNewInvite` sets.
 *
 * @param {string} nameA The inviter's display name.
 * @param {string} nameB The accepter's display name.
 * @return {Promise<{a: Object, b: Object, familyId: string}>} The pair.
 */
async function pairedParents(nameA, nameB) {
  const a = await signUp(nameA);
  const b = await signUp(nameB);
  for (const p of [a, b]) {
    await writeAs(`users/${p.uid}`, {name: p.name, email: p.email, id: p.uid, firebaseUid: p.uid}, p.token);
  }
  const alphabet = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
  const code = Array.from(crypto.randomBytes(6), (x) => alphabet[x % alphabet.length]).join('');
  const id = crypto.randomUUID();
  const now = Date.now();
  await writeAs(`invitations/${id}`, {
    id,
    code,
    fromUserId: a.uid,
    fromUserName: a.name,
    fromUserEmail: a.email,
    toEmail: '',
    status: 'pending',
    createdAt: now,
    expiresAt: now + 24 * 60 * 60 * 1000,
    acceptedBy: null,
  }, a.token);
  const accepted = await callable('acceptPairingInvitation', {code}, b.token);
  if (accepted.partnerId !== a.uid) {
    throw new Error(`acceptPairingInvitation returned ${JSON.stringify(accepted)}`);
  }
  return {a, b, familyId: [a.uid, b.uid].sort().join('__')};
}

module.exports = {
  PROJECT,
  emulatorsRunning,
  requireEmulators,
  functionsBase,
  callable,
  signUp,
  writeAs,
  seed,
  pairedParents,
};
