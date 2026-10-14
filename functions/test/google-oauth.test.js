const test = require('firebase-functions-test')();
const assert = require('assert');

/**
 * The Google OAuth token exchange (SEC-1 §2).
 *
 * The web OAuth client's secret used to be compiled into every APK, so anyone who installed the
 * app could mint tokens against the project's client. It now lives only in the functions'
 * environment, and these pin the two properties that make that worth doing: the secret reaches
 * Google and nothing else, and the refresh grant is not an oracle that will refresh anybody's
 * token for anybody.
 */

/**
 * A fake Firestore holding just `google_oauth/{uid}`, with the one-document transactions the
 * exchange uses to add a fingerprint to the list.
 *
 * @param {!Object=} seed Documents keyed by collection then id.
 * @return {!Object} The fake, carrying `_docs` for assertions.
 */
function fakeDb(seed) {
  const docs = JSON.parse(JSON.stringify(seed || {}));
  return {
    _docs: docs,
    collection(name) {
      return {
        doc: (id) => ({
          async get() {
            const data = (docs[name] || {})[id];
            return {exists: data !== undefined, data: () => data};
          },
          async set(value) {
            docs[name] = docs[name] || {};
            docs[name][id] = value;
          },
        }),
      };
    },
    async runTransaction(work) {
      return work({get: (ref) => ref.get(), set: (ref, value) => ref.set(value)});
    },
  };
}

const CONFIG = {clientId: 'client-id.apps.googleusercontent.com', clientSecret: 's3cret'};
const ALICE = 'uid-alice';
const REFRESH = 'refresh-token-value';

/**
 * Records what was posted and answers with [response].
 *
 * @param {!Object} response What Google is pretending to say.
 * @return {function(!Object): !Promise<!Object>} The post function, carrying `calls`.
 */
function recordingPost(response) {
  const calls = [];
  const post = async (form) => {
    calls.push(form);
    return response;
  };
  post.calls = calls;
  return post;
}

/**
 * Records what was posted and fails the way [failure] says, as `postToGoogleToken` does.
 *
 * @param {!Error} failure What the post rejects with.
 * @return {function(!Object): !Promise<!Object>} The post function, carrying `calls`.
 */
function failingPost(failure) {
  const calls = [];
  const post = async (form) => {
    calls.push(form);
    throw failure;
  };
  post.calls = calls;
  return post;
}

/**
 * A `google_oauth/{uid}` document as this build writes it: the fingerprints of [tokens], newest
 * first, and the newest alone under the field functions deployed before the list read.
 *
 * @param {!Object} index The functions module, for its fingerprint.
 * @param {!Array<string>} tokens Refresh tokens, newest first.
 * @return {!Object} The document.
 */
function listedDoc(index, tokens) {
  const hashes = tokens.map((token) => index.refreshTokenFingerprint(token));
  return {refreshTokenHashes: hashes, refreshTokenHash: hashes[0], updatedAtMillis: 1};
}

describe('exchangeGoogleAuthCode', () => {
  let index;

  before(() => {
    index = require('../index');
  });

  after(() => {
    test.cleanup();
  });

  it('sends the secret to Google and hands the tokens back', async () => {
    const db = fakeDb({});
    const post = recordingPost({
      access_token: 'access', refresh_token: REFRESH, expires_in: 3599,
    });

    const result = await index.exchangeGoogleAuthCodeImpl(
        db, CONFIG, post, ALICE, 'auth-code');

    assert.strictEqual(post.calls[0].client_secret, 's3cret');
    assert.strictEqual(post.calls[0].grant_type, 'authorization_code');
    assert.strictEqual(post.calls[0].code, 'auth-code');
    assert.deepStrictEqual(result, {
      accessToken: 'access', refreshToken: REFRESH, expiresInSeconds: 3599,
    });
  });

  it('records a fingerprint of the refresh token, never the token', async () => {
    // The store exists so a stolen token cannot be used; storing the token itself would make
    // the store worth stealing.
    const db = fakeDb({});

    await index.exchangeGoogleAuthCodeImpl(
        db, CONFIG, recordingPost({access_token: 'a', refresh_token: REFRESH, expires_in: 1}),
        ALICE, 'auth-code');

    const stored = db._docs.google_oauth[ALICE];
    assert.deepStrictEqual(stored.refreshTokenHashes, [index.refreshTokenFingerprint(REFRESH)]);
    // The newest alone too, under the field the functions deployed before the list read: a
    // rollback still accepts the device that connected last.
    assert.strictEqual(stored.refreshTokenHash, index.refreshTokenFingerprint(REFRESH));
    assert.strictEqual(JSON.stringify(stored).includes(REFRESH), false);
  });

  it('records nothing when Google returns no refresh token', async () => {
    // Google omits it when the account has already granted consent and the app did not ask for
    // offline access again. Overwriting the recorded fingerprint with nothing would lock the
    // account out of its own working token.
    const db = fakeDb({google_oauth: {[ALICE]: {refreshTokenHash: 'previous'}}});

    await index.exchangeGoogleAuthCodeImpl(
        db, CONFIG, recordingPost({access_token: 'a', expires_in: 1}), ALICE, 'auth-code');

    assert.deepStrictEqual(db._docs.google_oauth[ALICE], {refreshTokenHash: 'previous'});
  });

  it('keeps the tokens of the devices connected before, newest first', async () => {
    // One fingerprint per account meant that connecting a second phone — or reinstalling —
    // silently refused the first phone's token on its next refresh, about an hour later.
    const db = fakeDb({google_oauth: {[ALICE]: listedDoc(index, ['phone-1'])}});

    await index.exchangeGoogleAuthCodeImpl(
        db, CONFIG, recordingPost({access_token: 'a', refresh_token: 'phone-2', expires_in: 1}),
        ALICE, 'auth-code');

    assert.deepStrictEqual(db._docs.google_oauth[ALICE].refreshTokenHashes, [
      index.refreshTokenFingerprint('phone-2'),
      index.refreshTokenFingerprint('phone-1'),
    ]);
  });

  it('carries an account\'s single fingerprint from before the list into it', async () => {
    const db = fakeDb({
      google_oauth: {[ALICE]: {refreshTokenHash: index.refreshTokenFingerprint('old-phone')}},
    });

    await index.exchangeGoogleAuthCodeImpl(
        db, CONFIG, recordingPost({access_token: 'a', refresh_token: 'new-phone', expires_in: 1}),
        ALICE, 'auth-code');

    assert.deepStrictEqual(db._docs.google_oauth[ALICE].refreshTokenHashes, [
      index.refreshTokenFingerprint('new-phone'),
      index.refreshTokenFingerprint('old-phone'),
    ]);
  });

  it('keeps only the five newest tokens', async () => {
    // Bounded, so an account that reconnects every week does not keep every token it was ever
    // issued working: the sixth connection lets the oldest one go.
    const db = fakeDb({google_oauth: {[ALICE]: listedDoc(index, ['t5', 't4', 't3', 't2', 't1'])}});

    await index.exchangeGoogleAuthCodeImpl(
        db, CONFIG, recordingPost({access_token: 'a', refresh_token: 't6', expires_in: 1}),
        ALICE, 'auth-code');

    assert.deepStrictEqual(
        db._docs.google_oauth[ALICE].refreshTokenHashes,
        ['t6', 't5', 't4', 't3', 't2'].map((token) => index.refreshTokenFingerprint(token)));
  });

  it('lists a token that comes back again once, as the newest', async () => {
    const db = fakeDb({google_oauth: {[ALICE]: listedDoc(index, ['t2', 't1'])}});

    await index.exchangeGoogleAuthCodeImpl(
        db, CONFIG, recordingPost({access_token: 'a', refresh_token: 't1', expires_in: 1}),
        ALICE, 'auth-code');

    assert.deepStrictEqual(
        db._docs.google_oauth[ALICE].refreshTokenHashes,
        ['t1', 't2'].map((token) => index.refreshTokenFingerprint(token)));
  });

  it('fails loudly when Google returns no access token', async () => {
    await assert.rejects(
        () => index.exchangeGoogleAuthCodeImpl(
            fakeDb({}), CONFIG, recordingPost({}), ALICE, 'auth-code'),
        (err) => err.details && err.details.reason === 'no-access-token');
  });

  it('asks for a new sign-in when Google refuses the code', async () => {
    // An expired or already used code is `invalid_grant`, the same answer a revoked refresh
    // token gets — and the same remedy: go through Google's sign-in again.
    const refused = new index.GoogleTokenError('Google said 400', 400, 'invalid_grant');

    await assert.rejects(
        () => index.exchangeGoogleAuthCodeImpl(fakeDb({}), CONFIG, failingPost(refused), ALICE, 'code'),
        (err) => err.code === 'failed-precondition' && err.details.reason === 'reconnect');
  });

  it('says Google is unavailable, not that the code is bad, when Google cannot be reached', async () => {
    await assert.rejects(
        () => index.exchangeGoogleAuthCodeImpl(
            fakeDb({}), CONFIG, failingPost(new Error('socket hang up')), ALICE, 'code'),
        (err) => err.code === 'unavailable' && err.details.reason === 'google-unavailable');
  });
});

describe('refreshGoogleAccessToken', () => {
  let index;

  before(() => {
    index = require('../index');
  });

  it('refreshes the token this account was issued', async () => {
    // A document written before the list holds one `refreshTokenHash`; it keeps working.
    const db = fakeDb({
      google_oauth: {[ALICE]: {refreshTokenHash: require('crypto')
          .createHash('sha256').update(REFRESH).digest('hex')}},
    });
    const post = recordingPost({access_token: 'fresh', expires_in: 3599});

    const result = await index.refreshGoogleAccessTokenImpl(db, CONFIG, post, ALICE, REFRESH);

    assert.strictEqual(post.calls[0].grant_type, 'refresh_token');
    assert.strictEqual(post.calls[0].client_secret, 's3cret');
    assert.deepStrictEqual(result, {accessToken: 'fresh', expiresInSeconds: 3599});
  });

  it('refreshes an older listed token, so a second phone does not end the first', async () => {
    const db = fakeDb({google_oauth: {[ALICE]: listedDoc(index, ['phone-2', 'phone-1'])}});
    const post = recordingPost({access_token: 'fresh', expires_in: 3599});

    const result = await index.refreshGoogleAccessTokenImpl(db, CONFIG, post, ALICE, 'phone-1');

    assert.strictEqual(post.calls[0].refresh_token, 'phone-1');
    assert.deepStrictEqual(result, {accessToken: 'fresh', expiresInSeconds: 3599});
  });

  it('refuses a token that fell off the list, as one to reconnect', async () => {
    const db = fakeDb({google_oauth: {[ALICE]: listedDoc(index, ['t6', 't5', 't4', 't3', 't2'])}});
    const post = recordingPost({access_token: 'fresh', expires_in: 1});

    await assert.rejects(
        () => index.refreshGoogleAccessTokenImpl(db, CONFIG, post, ALICE, 't1'),
        (err) => err.details.reason === 'reconnect');
    assert.strictEqual(post.calls.length, 0, 'Google is never asked');
  });

  it('refuses a refresh token issued to somebody else', async () => {
    // Without this the function would be an oracle turning any stolen refresh token into an
    // access token — exactly the capability taking the secret out of the APK removes.
    const db = fakeDb({
      google_oauth: {[ALICE]: {refreshTokenHash: index.refreshTokenFingerprint('mine')}},
    });
    const post = recordingPost({access_token: 'fresh', expires_in: 1});

    await assert.rejects(
        () => index.refreshGoogleAccessTokenImpl(db, CONFIG, post, ALICE, 'somebody-elses'),
        (err) => err.code === 'permission-denied' &&
                 err.details.reason === 'reconnect' &&
                 err.details.cause === 'unknown-refresh-token');
    assert.strictEqual(post.calls.length, 0, 'Google is never asked');
  });

  it('refuses a token with no fingerprint rather than trusting it on first use', async () => {
    // Trust-on-first-use would let whoever presents a stolen token first bind it to themselves.
    // The cost is one re-consent for an account whose Calendar was connected before this
    // shipped: the app reads the refusal as "reconnect" and offers the button.
    const post = recordingPost({access_token: 'fresh', expires_in: 1});

    await assert.rejects(
        () => index.refreshGoogleAccessTokenImpl(fakeDb({}), CONFIG, post, ALICE, REFRESH),
        (err) => err.details.reason === 'reconnect');
    assert.strictEqual(post.calls.length, 0);
  });

  it('tells the app to reconnect when Google says the token is revoked or expired', async () => {
    // `invalid_grant`: revoked in the Google account, unused for six months, or — while the
    // consent screen is in Testing — seven days old. No retry brings it back. It used to reach
    // the app as a bare `internal`, indistinguishable from a network blip.
    const db = fakeDb({google_oauth: {[ALICE]: listedDoc(index, [REFRESH])}});
    const revoked = new index.GoogleTokenError('Token has been expired or revoked.', 400, 'invalid_grant');

    await assert.rejects(
        () => index.refreshGoogleAccessTokenImpl(db, CONFIG, failingPost(revoked), ALICE, REFRESH),
        (err) => err.code === 'failed-precondition' &&
                 err.details.reason === 'reconnect' &&
                 err.details.cause === 'invalid_grant');
  });

  it('says unavailable, never reconnect, when Google cannot be reached or is struggling', async () => {
    const db = fakeDb({google_oauth: {[ALICE]: listedDoc(index, [REFRESH])}});
    const failures = [
      new Error('getaddrinfo ENOTFOUND oauth2.googleapis.com'),
      new index.GoogleTokenError('Google token endpoint did not answer in time', 0, ''),
      new index.GoogleTokenError('Google said 503', 503, ''),
      new index.GoogleTokenError('Google said 429', 429, 'rate_limit_exceeded'),
    ];

    for (const failure of failures) {
      await assert.rejects(
          () => index.refreshGoogleAccessTokenImpl(db, CONFIG, failingPost(failure), ALICE, REFRESH),
          (err) => err.code === 'unavailable' && err.details.reason === 'google-unavailable',
          failure.message);
    }
    assert.deepStrictEqual(db._docs.google_oauth[ALICE], listedDoc(index, [REFRESH]), 'nothing forgotten');
  });

  it('does not blame the token when Google refuses this deployment\'s own client', async () => {
    // A wrong or rotated client secret is the owner's to fix. Telling every parent to reconnect
    // would cost them their tokens for an ops mistake; `internal` reads as "try again" instead.
    const db = fakeDb({google_oauth: {[ALICE]: listedDoc(index, [REFRESH])}});
    const misconfigured = new index.GoogleTokenError('Google said 401', 401, 'invalid_client');

    await assert.rejects(
        () => index.refreshGoogleAccessTokenImpl(db, CONFIG, failingPost(misconfigured), ALICE, REFRESH),
        (err) => err.code === 'internal' &&
                 err.details.reason === 'google-refused' &&
                 err.details.cause === 'invalid_client');
  });
});

describe('googleOAuthErrorOf', () => {
  let index;

  before(() => {
    index = require('../index');
  });

  it('reads Google\'s error code from the body of a refusal', () => {
    assert.strictEqual(
        index.googleOAuthErrorOf('{"error": "invalid_grant", "error_description": "Bad Request"}'),
        'invalid_grant');
  });

  it('reads nothing from a body that is not an OAuth error', () => {
    assert.strictEqual(index.googleOAuthErrorOf('<html>502 Bad Gateway</html>'), '');
    assert.strictEqual(index.googleOAuthErrorOf('{"error": {"code": 400}}'), '');
    assert.strictEqual(index.googleOAuthErrorOf(''), '');
  });
});

describe('googleOAuthConfig', () => {
  let index;

  before(() => {
    index = require('../index');
  });

  afterEach(() => {
    delete process.env.GOOGLE_OAUTH_CLIENT_ID;
    delete process.env.GOOGLE_OAUTH_CLIENT_SECRET;
  });

  it('is null until both halves are set, so a deployment is visibly unconfigured', () => {
    assert.strictEqual(index.googleOAuthConfig(), null);

    process.env.GOOGLE_OAUTH_CLIENT_ID = 'id';
    assert.strictEqual(index.googleOAuthConfig(), null, 'an id alone is not a client');

    process.env.GOOGLE_OAUTH_CLIENT_SECRET = 'secret';
    assert.deepStrictEqual(
        index.googleOAuthConfig(), {clientId: 'id', clientSecret: 'secret'});
  });

  it('is read fresh, so a re-deploy can change it', () => {
    // Bound at module load, the value would be one a re-deploy could not move — the reason
    // `backfillAdminUids` reads its own env on every call.
    process.env.GOOGLE_OAUTH_CLIENT_ID = 'first';
    process.env.GOOGLE_OAUTH_CLIENT_SECRET = 'secret';
    assert.strictEqual(index.googleOAuthConfig().clientId, 'first');

    process.env.GOOGLE_OAUTH_CLIENT_ID = 'second';
    assert.strictEqual(index.googleOAuthConfig().clientId, 'second');
  });
});
