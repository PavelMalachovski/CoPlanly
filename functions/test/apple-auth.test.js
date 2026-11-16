const assert = require('assert');
const crypto = require('crypto');

const apple = require('../apple-auth');

/**
 * Sign in with Apple's pure helpers: the client secret, the code exchange and the revocation.
 *
 * The key pair is made here, so the secret is verified as Apple would verify it: an ES256 JWT
 * whose signature is the raw `r || s` pair, checked against the public half.
 */

const NOW = 1794787200; // 2026-11-16T00:00:00Z, in seconds.

const KEYS = crypto.generateKeyPairSync('ec', {namedCurve: 'P-256'});

/** @return {!Object} A configuration signing with the test key. */
function config() {
  return {
    teamId: 'TEAM123456',
    keyId: 'KEY1234567',
    privateKey: KEYS.privateKey.export({type: 'pkcs8', format: 'pem'}),
    bundleId: 'app.coplanly',
    servicesId: 'app.coplanly.signin',
  };
}

/**
 * A `fetch` that records each request and answers with [status] and [body].
 *
 * @param {number} status The HTTP status to answer.
 * @param {string} body The body to answer.
 * @return {function(string, !Object): !Promise<!Object>} The fake, carrying `calls`.
 */
function fakeFetch(status, body) {
  const calls = [];
  const fetch = async (url, init) => {
    calls.push({url, init, form: Object.fromEntries(new URLSearchParams(init.body))});
    return {status, text: async () => body};
  };
  fetch.calls = calls;
  return fetch;
}

/**
 * @param {string} part A base64url JWT segment.
 * @return {!Object} Its JSON.
 */
function decode(part) {
  return JSON.parse(Buffer.from(part, 'base64url').toString('utf8'));
}

describe('apple-auth', () => {
  describe('clientSecret', () => {
    it('is an ES256 JWT with the kid, the team, the client id and five minutes', () => {
      const jwt = apple.clientSecret(config(), 'app.coplanly', NOW);
      const [header, claims, signature] = jwt.split('.');

      assert.deepStrictEqual(decode(header), {alg: 'ES256', kid: 'KEY1234567'});
      assert.deepStrictEqual(decode(claims), {
        iss: 'TEAM123456',
        iat: NOW,
        exp: NOW + 300,
        aud: 'https://appleid.apple.com',
        sub: 'app.coplanly',
      });

      const raw = Buffer.from(signature, 'base64url');
      assert.strictEqual(raw.length, 64, 'JOSE wants the raw r || s pair, not DER');
      assert.ok(crypto.verify('sha256', Buffer.from(`${header}.${claims}`),
          {key: KEYS.publicKey, dsaEncoding: 'ieee-p1363'}, raw));
    });

    it('names the Services ID when the token is for the web flow', () => {
      const jwt = apple.clientSecret(config(), 'app.coplanly.signin', NOW);
      assert.strictEqual(decode(jwt.split('.')[1]).sub, 'app.coplanly.signin');
    });
  });

  describe('appleConfig', () => {
    const env = () => ({
      APPLE_TEAM_ID: 'TEAM123456',
      APPLE_KEY_ID: 'KEY1234567',
      APPLE_PRIVATE_KEY: '-----BEGIN PRIVATE KEY-----\\nabc\\n-----END PRIVATE KEY-----',
      APPLE_BUNDLE_ID: 'app.coplanly',
      APPLE_SERVICES_ID: 'app.coplanly.signin',
    });

    it('reads the five values and turns the key\'s \\n escapes into line breaks', () => {
      const read = apple.appleConfig(env());
      assert.strictEqual(read.privateKey,
          '-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----');
      assert.strictEqual(read.bundleId, 'app.coplanly');
    });

    for (const name of Object.keys(env())) {
      it(`is null without ${name}`, () => {
        const partial = env();
        delete partial[name];
        assert.strictEqual(apple.appleConfig(partial), null);
      });
    }
  });

  describe('exchangeCode', () => {
    it('posts the authorization code grant to Apple\'s token endpoint', async () => {
      const fetch = fakeFetch(200, JSON.stringify(
          {access_token: 'at', refresh_token: 'rt-1', id_token: 'it', expires_in: 3600}));
      const result = await apple.exchangeCode(
          config(), 'app.coplanly', 'code-1', {fetch, nowSeconds: NOW});

      assert.deepStrictEqual(result, {refreshToken: 'rt-1', idToken: 'it'});
      assert.strictEqual(fetch.calls.length, 1);
      const {url, init, form} = fetch.calls[0];
      assert.strictEqual(url, 'https://appleid.apple.com/auth/token');
      assert.strictEqual(init.method, 'POST');
      assert.strictEqual(init.headers['Content-Type'], 'application/x-www-form-urlencoded');
      assert.deepStrictEqual(Object.keys(form).sort(),
          ['client_id', 'client_secret', 'code', 'grant_type']);
      assert.strictEqual(form.client_id, 'app.coplanly');
      assert.strictEqual(form.code, 'code-1');
      assert.strictEqual(form.grant_type, 'authorization_code');
      assert.strictEqual(decode(form.client_secret.split('.')[1]).sub, 'app.coplanly');
    });

    it('answers an empty refresh token when Apple sends none', async () => {
      const fetch = fakeFetch(200, JSON.stringify({access_token: 'at'}));
      const result = await apple.exchangeCode(
          config(), 'app.coplanly', 'code-1', {fetch, nowSeconds: NOW});
      assert.deepStrictEqual(result, {refreshToken: '', idToken: ''});
    });

    it('carries Apple\'s error code, and no body, on a refusal', async () => {
      const fetch = fakeFetch(400, JSON.stringify({error: 'invalid_grant'}));
      await assert.rejects(
          apple.exchangeCode(config(), 'app.coplanly', 'code-1', {fetch, nowSeconds: NOW}),
          (err) => err instanceof apple.AppleTokenError &&
            err.status === 400 && err.appleError === 'invalid_grant');
    });

    it('is a status-0 failure when Apple cannot be reached', async () => {
      const fetch = async () => {
        throw new Error('ECONNRESET');
      };
      await assert.rejects(
          apple.exchangeCode(config(), 'app.coplanly', 'code-1', {fetch, nowSeconds: NOW}),
          (err) => err instanceof apple.AppleTokenError && err.status === 0);
    });

    it('is a status-0 failure when the body cannot be read', async () => {
      const fetch = async () => ({
        status: 200,
        text: async () => {
          throw new Error('ECONNRESET');
        },
      });
      await assert.rejects(
          apple.exchangeCode(config(), 'app.coplanly', 'code-1', {fetch, nowSeconds: NOW}),
          (err) => err instanceof apple.AppleTokenError && err.status === 0);
    });
  });

  describe('subjectOf', () => {
    const jwt = (payload) => ['{}', JSON.stringify(payload)]
        .map((part) => Buffer.from(part).toString('base64url')).join('.') + '.sig';

    it('reads the sub claim of an identity token', () => {
      assert.strictEqual(apple.subjectOf(jwt({sub: '001234.abc', aud: 'app.coplanly'})),
          '001234.abc');
    });

    it('is empty for anything it cannot read', () => {
      for (const bad of ['', 'a.b', 'a.b.c', jwt({aud: 'x'}), jwt({sub: 42}), null]) {
        assert.strictEqual(apple.subjectOf(bad), '');
      }
    });
  });

  describe('revoke', () => {
    it('posts the refresh token to Apple\'s revocation endpoint', async () => {
      const fetch = fakeFetch(200, '');
      await apple.revoke(config(), 'app.coplanly', 'rt-1', {fetch, nowSeconds: NOW});

      assert.strictEqual(fetch.calls.length, 1);
      const {url, form} = fetch.calls[0];
      assert.strictEqual(url, 'https://appleid.apple.com/auth/revoke');
      assert.deepStrictEqual(Object.keys(form).sort(),
          ['client_id', 'client_secret', 'token', 'token_type_hint']);
      assert.strictEqual(form.client_id, 'app.coplanly');
      assert.strictEqual(form.token, 'rt-1');
      assert.strictEqual(form.token_type_hint, 'refresh_token');
      assert.strictEqual(decode(form.client_secret.split('.')[0]).kid, 'KEY1234567');
    });

    it('rejects when Apple refuses', async () => {
      const fetch = fakeFetch(400, JSON.stringify({error: 'invalid_client'}));
      await assert.rejects(
          apple.revoke(config(), 'app.coplanly', 'rt-1', {fetch, nowSeconds: NOW}),
          (err) => err.appleError === 'invalid_client');
    });
  });
});
