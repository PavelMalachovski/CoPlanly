/**
 * `web/pair/` in Chromium: the page a pairing link opens when the app does not open it itself —
 * somebody without the app, or an Android phone before the App Link is verified.
 *
 * Served through `firebase.json`'s own rewrite (`support/static-server.js` reads it), at the
 * address the app shares: `/pair/CODE`. No emulator is needed: the page makes no request at all,
 * and the first test holds it to that.
 */
'use strict';

const {test, expect, devices} = require('@playwright/test');
const {startStaticServer} = require('./support/static-server');

const CODE = '4F7K2M';
const PLAY = 'https://play.google.com/store/apps/details?id=app.coplanly';

/** Play's listing with the code as an install referrer, so the app finds it after installing. */
const playWith = (code) => `${PLAY}&referrer=${encodeURIComponent(`pair=${code}`)}`;
const ANDROID_UA = devices['Pixel 7'].userAgent;
const IPHONE_UA = devices['iPhone 14'].userAgent;

let server;

test.beforeAll(async () => {
  server = await startStaticServer();
});

test.afterAll(async () => {
  if (server) await server.close();
});

/**
 * Opens [address] in a fresh context, recording every request that leaves for anywhere but the
 * page itself.
 *
 * @param {import('@playwright/test').Browser} browser The browser.
 * @param {string} address Path and query, e.g. `/pair/4F7K2M`.
 * @param {!Object=} options Context options (user agent, locale, viewport).
 * @return {Promise<{page: !Object, context: !Object, outbound: string[]}>} The open page.
 */
async function open(browser, address, options = {}) {
  const context = await browser.newContext(options);
  const page = await context.newPage();
  const outbound = [];
  page.on('request', (r) => {
    if (!r.url().startsWith(server.origin)) outbound.push(r.url());
  });
  await page.goto(`${server.origin}${address}`);
  return {page, context, outbound};
}

test('shows the code from the address and sends nothing anywhere', async ({browser}) => {
  const {page, context, outbound} = await open(browser, `/pair/${CODE}`, {userAgent: ANDROID_UA});

  await expect(page.locator('#code')).toHaveText(CODE);
  await expect(page.locator('#bad')).toBeHidden();
  expect(outbound).toEqual([]);
  await context.close();
});

test('the address is served with no referrer and kept out of search', async ({request}) => {
  const response = await request.get(`${server.origin}/pair/${CODE}`);

  expect(response.status()).toBe(200);
  expect(response.headers()['referrer-policy']).toBe('no-referrer');
  expect(response.headers()['x-robots-tag']).toBe('noindex');
});

test('on Android the button hands the code to the app, or falls back to Google Play', async ({browser}) => {
  const {page, context} = await open(browser, `/pair/${CODE.toLowerCase()}/`, {userAgent: ANDROID_UA});

  const open_ = page.locator('#open');
  await expect(open_).toBeVisible();
  const href = await open_.getAttribute('href');
  expect(href).toBe(
      `intent://pair?code=${CODE}#Intent;scheme=coplanly;package=app.coplanly;` +
      `S.browser_fallback_url=${encodeURIComponent(playWith(CODE))};end`);
  await expect(page.locator('#play')).toHaveAttribute('href', playWith(CODE));
  await expect(page.locator('#ios')).toBeHidden();
  await context.close();
});

test('on an iPhone it says the app is Android-only and offers nothing to install', async ({browser}) => {
  const {page, context} = await open(browser, `/pair/${CODE}`, {userAgent: IPHONE_UA});

  await expect(page.locator('#ios')).toBeVisible();
  await expect(page.locator('#open')).toBeHidden();
  await expect(page.locator('#install')).toBeHidden();
  await expect(page.locator('#code')).toHaveText(CODE);
  await context.close();
});

test('on a computer it says to open the link on the phone', async ({browser}) => {
  const {page, context} = await open(browser, `/pair/${CODE}`);

  await expect(page.locator('#desktop')).toBeVisible();
  await expect(page.locator('#open')).toBeHidden();
  await context.close();
});

test('an address without a valid code says so and shows no code', async ({browser}) => {
  for (const address of ['/pair/', '/pair/4F7K2O', '/pair/%E0%A4%A', '/pair/4F7K2MX']) {
    const {page, context} = await open(browser, address, {userAgent: ANDROID_UA});
    await expect(page.locator('#bad'), address).toBeVisible();
    await expect(page.locator('#invite'), address).toBeHidden();
    await context.close();
  }
});

test('it never names the inviter, even when the address tries to', async ({browser}) => {
  // The name is the app's to show, to a signed-in account. A forwarded link must not carry one.
  const {page, context} = await open(
      browser, `/pair/${CODE}?name=%3Cb%3EMallory%3C%2Fb%3E&from=Mallory`, {userAgent: ANDROID_UA});

  await expect(page.locator('body')).not.toContainText('Mallory');
  await context.close();
});

test('it speaks the reader\'s language, and all five are complete', async ({browser}) => {
  const expected = {
    'en-GB': 'Open in CoPlanly',
    'cs-CZ': 'Otevřít v CoPlanly',
    'sk-SK': 'Otevřít v CoPlanly',
    'de-AT': 'In CoPlanly öffnen',
    'ru-RU': 'Открыть в CoPlanly',
    'uk-UA': 'Відкрити в CoPlanly',
  };
  for (const [locale, label] of Object.entries(expected)) {
    const {page, context} = await open(browser, `/pair/${CODE}`, {locale, userAgent: ANDROID_UA});
    await expect(page.locator('#open'), locale).toHaveText(label);
    // Every text node the page writes is filled in: a missing key would leave it empty.
    const empty = await page.$$eval('[data-t]', (els) => els
        .filter((el) => !el.closest('[hidden]') && el.textContent.trim() === '')
        .map((el) => el.dataset.t));
    expect(empty, locale).toEqual([]);
    await context.close();
  }
});

test('the language buttons switch every text', async ({browser}) => {
  const {page, context} = await open(browser, `/pair/${CODE}`, {userAgent: ANDROID_UA});

  await page.click('[data-lang="uk"]');
  await expect(page.locator('h1')).toHaveText('Запрошення до CoPlanly');
  await expect(page.locator('html')).toHaveAttribute('lang', 'uk');
  await context.close();
});

test('fits a 375 px phone without sideways scrolling', async ({browser}) => {
  const {page, context} = await open(browser, `/pair/${CODE}`, {
    userAgent: ANDROID_UA, viewport: {width: 375, height: 740}, locale: 'de-DE',
  });

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  await context.close();
});
