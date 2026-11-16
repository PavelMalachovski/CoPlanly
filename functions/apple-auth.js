/**
 * Sign in with Apple's server half (MON-24 phase 4, part A): the client secret, the exchange of an
 * authorization code for a refresh token, and the revocation of that token.
 *
 * **Why the server holds Apple's refresh token at all.** Apple requires an app that offers Sign in
 * with Apple to revoke the user's token when the account is deleted (App Store guideline
 * 5.1.1(v)), and revoking needs a token to revoke. Firebase Auth keeps none it hands out, so the
 * iPhone sends the authorization code Apple gave it to `exchangeAppleAuthCode`, which redeems it
 * here and keeps the refresh token in `apple_tokens/{uid}` — a collection closed to every client
 * (`firestore.rules`). `unlinkApple` and account deletion revoke it.
 *
 * **The key never leaves the server.** The client secret is an ES256 JWT signed with the `.p8`
 * key in `functions/.env` (`APPLE_PRIVATE_KEY`), made afresh for each request and valid for five
 * minutes. Nothing here logs a token, a code, a secret or the key; Apple's own words on a refusal
 * are carried as its error code only.
 *
 * Pure: no Firebase import, as `calendar-feed.js` and `export-receipts.js` are not either, and
 * `fetch` is injected so the tests reach every request without a network.
 */

const crypto = require('crypto');

/** Apple's token endpoint: redeems an authorization code. */
const APPLE_TOKEN_URL = 'https://appleid.apple.com/auth/token';

/** Apple's revocation endpoint: ends a refresh token and the authorization behind it. */
const APPLE_REVOKE_URL = 'https://appleid.apple.com/auth/revoke';

/** The audience every client secret names, as Apple requires. */
const APPLE_AUDIENCE = 'https://appleid.apple.com';

/** How long a client secret is valid. One request needs seconds; Apple allows up to six months. */
const CLIENT_SECRET_LIFETIME_SECONDS = 300;

/** How long a request to Apple may take before it counts as failed. */
const APPLE_REQUEST_TIMEOUT_MS = 10000;

/** The server-only collection that keeps each account's Apple refresh token. */
const APPLE_TOKENS_COLLECTION = 'apple_tokens';

/** The Firebase Auth provider id of Sign in with Apple. */
const APPLE_PROVIDER_ID = 'apple.com';

/**
 * A request to Apple that failed: no answer, or an answer that was not a success.
 *
 * Carries Apple's OAuth error code (`invalid_grant`, `invalid_client`, …) and the HTTP status,
 * never the response body, which on a success is where tokens travel.
 */
class AppleTokenError extends Error {
  /**
   * @param {string} message What went wrong, without any token.
   * @param {number} status The HTTP status, or 0 when Apple did not answer.
   * @param {string} appleError Apple's `error` field, or ''.
   */
  constructor(message, status, appleError) {
    super(message);
    this.name = 'AppleTokenError';
    this.status = status;
    this.appleError = appleError;
  }
}

/**
 * The deployment's Apple configuration, or null when any of the five values is missing.
 *
 * `APPLE_PRIVATE_KEY` is the `.p8` file's PEM text; a `.env` file carries it on one line with
 * `\n` escapes, which are turned back into line breaks here.
 *
 * @param {!Object<string, (string|undefined)>} env The environment, `process.env` in production.
 * @return {?{teamId: string, keyId: string, privateKey: string, bundleId: string,
 *     servicesId: string}} The configuration, or null.
 */
function appleConfig(env) {
  const read = (name) => (typeof env[name] === 'string' ? env[name].trim() : '');
  const config = {
    teamId: read('APPLE_TEAM_ID'),
    keyId: read('APPLE_KEY_ID'),
    privateKey: read('APPLE_PRIVATE_KEY').replace(/\\n/g, '\n'),
    bundleId: read('APPLE_BUNDLE_ID'),
    servicesId: read('APPLE_SERVICES_ID'),
  };
  return Object.values(config).every((value) => value) ? config : null;
}

/**
 * Whether [clientId] is one this deployment's tokens may be issued to: the app's bundle id (a
 * code from the iPhone's sheet) or the Services ID (a code from the web flow).
 *
 * @param {!{bundleId: string, servicesId: string}} config The Apple configuration.
 * @param {*} clientId What the caller named.
 * @return {boolean} True for one of the two.
 */
function isKnownClientId(config, clientId) {
  return typeof clientId === 'string' &&
    (clientId === config.bundleId || clientId === config.servicesId);
}

/**
 * @param {!Buffer|string} input Bytes or text.
 * @return {string} Its base64url text, unpadded, as JOSE writes it.
 */
function base64url(input) {
  return Buffer.from(input).toString('base64url');
}

/**
 * The client secret Apple asks for: an ES256 JWT signed with the team's key.
 *
 * Header `{alg: 'ES256', kid}`; claims `{iss: team, iat, exp: iat + 300, aud:
 * 'https://appleid.apple.com', sub: clientId}`. The signature is the raw `r || s` pair JOSE
 * requires, which Node writes with `dsaEncoding: 'ieee-p1363'` (its default is DER).
 *
 * @param {!{teamId: string, keyId: string, privateKey: string}} config The Apple configuration.
 * @param {string} clientId The bundle id or Services ID the token is for.
 * @param {number} nowSeconds The issue time, in seconds since the epoch.
 * @return {string} The signed JWT.
 */
function clientSecret(config, clientId, nowSeconds) {
  const iat = Math.floor(nowSeconds);
  const header = {alg: 'ES256', kid: config.keyId};
  const claims = {
    iss: config.teamId,
    iat,
    exp: iat + CLIENT_SECRET_LIFETIME_SECONDS,
    aud: APPLE_AUDIENCE,
    sub: clientId,
  };
  const signingInput = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claims))}`;
  const signature = crypto.sign('sha256', Buffer.from(signingInput), {
    key: config.privateKey,
    dsaEncoding: 'ieee-p1363',
  });
  return `${signingInput}.${base64url(signature)}`;
}

/**
 * Apple's `error` field from a refusal's body, or '' when it has none.
 *
 * @param {string} body The response body.
 * @return {string} The OAuth error code.
 */
function appleErrorOf(body) {
  try {
    const parsed = JSON.parse(body);
    return parsed && typeof parsed.error === 'string' ? parsed.error : '';
  } catch (err) {
    return '';
  }
}

/**
 * Posts [form] to [url] as `application/x-www-form-urlencoded`.
 *
 * @param {function(string, !Object): !Promise<!Object>} fetchFn `fetch`, or a fake.
 * @param {string} url Apple's endpoint.
 * @param {!Object<string, string>} form The form fields.
 * @return {!Promise<string>} The body of a 2xx answer.
 * @throws {AppleTokenError} When Apple did not answer in time (or its body could not be read),
 *     or answered otherwise.
 */
async function postForm(fetchFn, url, form) {
  let response;
  let body;
  try {
    response = await fetchFn(url, {
      method: 'POST',
      headers: {'Content-Type': 'application/x-www-form-urlencoded'},
      body: new URLSearchParams(form).toString(),
      signal: AbortSignal.timeout(APPLE_REQUEST_TIMEOUT_MS),
    });
    // Inside the try: a connection dropped while the body is read is Apple out of reach too.
    body = await response.text();
  } catch (err) {
    throw new AppleTokenError(`Could not reach Apple: ${err && err.name}`, 0, '');
  }
  if (response.status < 200 || response.status >= 300) {
    throw new AppleTokenError(
        `Apple answered ${response.status}`, response.status, appleErrorOf(body));
  }
  return body;
}

/**
 * Redeems an authorization code for Apple's tokens.
 *
 * Posts `client_id`, `client_secret`, `code` and `grant_type=authorization_code`. No
 * `redirect_uri`: a code from the iPhone's native sheet has none to match, and Apple answers
 * `invalid_grant` to one sent that does not match (the Google exchange learnt the same about an
 * empty one, `exchangeGoogleAuthCodeImpl`).
 *
 * @param {!Object} config The Apple configuration ([appleConfig]).
 * @param {string} clientId The bundle id or Services ID the code was issued to.
 * @param {string} code The authorization code.
 * @param {!{fetch: function(string, !Object): !Promise<!Object>, nowSeconds: number}} io
 *     The `fetch` to post with and the current time.
 * @return {!Promise<{refreshToken: string, idToken: string}>} The refresh token and the identity
 *     token, each '' when Apple sent none.
 * @throws {AppleTokenError} When Apple refused or could not be reached.
 */
async function exchangeCode(config, clientId, code, io) {
  const body = await postForm(io.fetch, APPLE_TOKEN_URL, {
    client_id: clientId,
    client_secret: clientSecret(config, clientId, io.nowSeconds),
    code,
    grant_type: 'authorization_code',
  });
  let parsed;
  try {
    parsed = JSON.parse(body);
  } catch (err) {
    // Not echoed: a success body is where tokens travel.
    throw new AppleTokenError('Apple answered with a body that is not JSON', 200, '');
  }
  const refreshToken = parsed && typeof parsed.refresh_token === 'string' ?
    parsed.refresh_token : '';
  const idToken = parsed && typeof parsed.id_token === 'string' ? parsed.id_token : '';
  return {refreshToken, idToken};
}

/**
 * The `sub` claim of Apple's identity token: the stable id of the Apple ID the code belongs to,
 * the same value Firebase Auth records as the `apple.com` provider's `uid`.
 *
 * The payload is decoded without checking the signature, which is enough **only** for a token
 * this server received from Apple's token endpoint itself, over TLS, in answer to its own
 * request. Never call this on an identity token a client sent.
 *
 * @param {string} idToken The `id_token` of Apple's token response.
 * @return {string} The `sub` claim, or '' when the token cannot be read.
 */
function subjectOf(idToken) {
  const parts = typeof idToken === 'string' ? idToken.split('.') : [];
  if (parts.length !== 3) return '';
  try {
    const claims = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
    return claims && typeof claims.sub === 'string' ? claims.sub : '';
  } catch (err) {
    return '';
  }
}

/**
 * Revokes a refresh token: Apple ends it and the authorization behind it.
 *
 * Posts `client_id`, `client_secret`, `token` and `token_type_hint=refresh_token`.
 *
 * @param {!Object} config The Apple configuration ([appleConfig]).
 * @param {string} clientId The bundle id or Services ID the token was issued to.
 * @param {string} refreshToken The token to revoke.
 * @param {!{fetch: function(string, !Object): !Promise<!Object>, nowSeconds: number}} io
 *     The `fetch` to post with and the current time.
 * @return {!Promise<void>} Resolves once Apple accepted the revocation.
 * @throws {AppleTokenError} When Apple refused or could not be reached.
 */
async function revoke(config, clientId, refreshToken, io) {
  await postForm(io.fetch, APPLE_REVOKE_URL, {
    client_id: clientId,
    client_secret: clientSecret(config, clientId, io.nowSeconds),
    token: refreshToken,
    token_type_hint: 'refresh_token',
  });
}

module.exports = {
  APPLE_TOKEN_URL,
  APPLE_REVOKE_URL,
  APPLE_AUDIENCE,
  APPLE_TOKENS_COLLECTION,
  APPLE_PROVIDER_ID,
  CLIENT_SECRET_LIFETIME_SECONDS,
  AppleTokenError,
  appleConfig,
  isKnownClientId,
  clientSecret,
  exchangeCode,
  subjectOf,
  revoke,
};
