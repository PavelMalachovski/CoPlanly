/**
 * `web/index.html` in Chromium: the site's home page, which Google's verification of the OAuth
 * consent screen reads — what the app is, what it does with Google Calendar, and the privacy policy
 * on the same domain.
 *
 * Served by `support/static-server.js` as Hosting serves it. No emulator: the page makes no
 * request, and the first test holds it to that. That the permission it names is the one the app
 * asks for is `tools/check-invariants.js`' check 6, which runs on every pull request.
 */
'use strict';

const {test, expect} = require('@playwright/test');
const {startStaticServer} = require('./support/static-server');

const GOOGLE_POLICY = 'https://developers.google.com/terms/api-services-user-data-policy';

/** Google's wording for the Limited Use disclosure, which the English page carries verbatim. */
const LIMITED_USE =
    'CoPlanly’s use and transfer to any other app of information received from Google APIs will ' +
    'adhere to the Google API Services User Data Policy, including the Limited Use requirements.';

let server;

test.beforeAll(async () => {
  server = await startStaticServer();
});

test.afterAll(async () => {
  if (server) await server.close();
});

/**
 * Opens the home page in a fresh context, recording every request that leaves for anywhere but
 * the page's own origin.
 *
 * @param {import('@playwright/test').Browser} browser The browser.
 * @param {!Object=} options Context options (locale, viewport, javaScriptEnabled).
 * @return {Promise<{page: !Object, context: !Object, outbound: string[]}>} The open page.
 */
async function open(browser, options = {}) {
  const context = await browser.newContext(options);
  const page = await context.newPage();
  const outbound = [];
  page.on('request', (r) => {
    if (!r.url().startsWith(server.origin)) outbound.push(r.url());
  });
  await page.goto(`${server.origin}/`);
  return {page, context, outbound};
}

/** @return {Promise<!Object<string, string>>} Every translated text on the page, by key. */
async function texts(page) {
  return page.$$eval('[data-t]', (els) => Object.fromEntries(
      els.map((el) => [el.dataset.t, el.textContent.replace(/\s+/g, ' ').trim()])));
}

test('is the site\'s root, names the app, and sends nothing anywhere', async ({browser}) => {
  const {page, context, outbound} = await open(browser);

  await expect(page.locator('h1')).toHaveText('CoPlanly');
  await expect(page).toHaveTitle(/CoPlanly/);
  expect(outbound).toEqual([]);
  await context.close();
});

test('can be found: nothing keeps it out of search', async ({browser, request}) => {
  const response = await request.get(`${server.origin}/`);
  expect(response.status()).toBe(200);
  expect(response.headers()['x-robots-tag']).toBeUndefined();

  const {page, context} = await open(browser);
  await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
  await context.close();
});

test('says what it does with Google Calendar, in Google\'s words for Limited Use', async ({browser}) => {
  const {page, context} = await open(browser);

  const google = page.locator('#google-calendar');
  await expect(google).toContainText('calendar.events.owned.readonly');
  await expect(google).toContainText('never creates, changes or deletes anything in your Google Calendar');
  await expect(google).toContainText('Imported events stay on your phone');
  await expect(page.locator('[data-t="limitedUse"]')).toHaveText(LIMITED_USE);
  await expect(page.locator('#google-policy')).toHaveAttribute('href', GOOGLE_POLICY);
  await context.close();
});

test('links the privacy policy on its own domain, and the other pages, all of which exist', async ({browser, request}) => {
  const {page, context} = await open(browser);

  for (const [id, path] of [['privacy', '/privacy/'], ['terms', '/terms/'],
    ['delete-account', '/delete-account/'], ['verify', '/verify/']]) {
    const href = await page.locator(`#${id}`).evaluate((a) => a.href);
    expect(href, id).toBe(`${server.origin}${path}`);
    expect((await request.get(href)).status(), id).toBe(200);
  }
  await context.close();
});

test('reads in English without JavaScript, the same English the script keeps', async ({browser}) => {
  const noScript = await open(browser, {javaScriptEnabled: false});
  const written = await texts(noScript.page);
  await noScript.context.close();

  const scripted = await open(browser, {locale: 'en-GB'});
  const rendered = await texts(scripted.page);
  await scripted.context.close();

  expect(written.limitedUse).toBe(LIMITED_USE);
  expect(Object.values(written).filter((t) => t === '')).toEqual([]);
  expect(rendered).toEqual(written);
});

test('speaks the reader\'s language, and all five are complete', async ({browser}) => {
  const expected = {
    'en-US': 'What it does',
    'cs-CZ': 'Co umí',
    'sk-SK': 'Co umí',
    'de-AT': 'Was die App kann',
    'ru-RU': 'Что умеет приложение',
    'uk-UA': 'Що вміє застосунок',
  };
  const english = await (async () => {
    const {page, context} = await open(browser, {locale: 'en-US'});
    const t = await texts(page);
    await context.close();
    return t;
  })();
  for (const [locale, heading] of Object.entries(expected)) {
    const {page, context} = await open(browser, {locale});
    await expect(page.locator('[data-t="featuresHead"]'), locale).toHaveText(heading);
    const shown = await texts(page);
    // A missing key would leave its element empty ("undefined" as text, in fact).
    const missing = Object.entries(shown)
        .filter(([, t]) => t === '' || t === 'undefined').map(([k]) => k);
    expect(missing, locale).toEqual([]);
    if (!locale.startsWith('en')) {
      // Every text is translated, except the permission's name, which is Google's.
      const untranslated = Object.keys(shown).filter((k) => shown[k] === english[k]);
      expect(untranslated, locale).toEqual([]);
      expect(shown.googleIntro, locale).toContain('calendar.events.owned.readonly');
    }
    await context.close();
  }
});

test('the language buttons switch every text, and back', async ({browser}) => {
  const {page, context} = await open(browser, {locale: 'en-US'});
  const english = await texts(page);

  await page.click('[data-lang="uk"]');
  await expect(page.locator('html')).toHaveAttribute('lang', 'uk');
  await expect(page.locator('[data-t="tagline"]')).toHaveText('Спільний календар для батьків, які живуть окремо.');
  await expect(page).toHaveTitle(/спільний календар/);
  await expect(page.locator('h1')).toHaveText('CoPlanly');

  await page.click('[data-lang="en"]');
  expect(await texts(page)).toEqual(english);
  await context.close();
});

test('fits a 375 px phone without sideways scrolling', async ({browser}) => {
  const {page, context} = await open(browser, {viewport: {width: 375, height: 740}, locale: 'de-DE'});
  await expect(page.locator('[data-t="featuresHead"]')).toHaveText('Was die App kann');

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  await context.close();
});
