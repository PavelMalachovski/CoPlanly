/**
 * `web/verify/` in Chromium (MON-16): the page a court or a mediator opens to check an exported
 * record, driven the way they would drive it — choose the file, press the button, read the answer.
 *
 * Two groups:
 *  - **Against the Functions emulator** (skipped without it, failed without it in CI): receipts
 *    are reserved and registered through the real `reserveExportRecordId`/`registerExportReceipt`
 *    callables as an Auth-emulator account, exactly as `ExportViewModel` does — reserve, render the
 *    file with the ID in it, hash, register — and the page answers from the real `verifyExport`.
 *  - **Against a stubbed endpoint** (always): what the page does with answers the emulator will not
 *    readily give — a rate limit, a hostile value — and that the `?functions=` hook cannot send a
 *    fingerprint anywhere but production or loopback.
 *
 * Every test blocks `*.cloudfunctions.net` at the browser, so no run can reach production.
 */
'use strict';

const crypto = require('crypto');
const {test, expect} = require('@playwright/test');
const {startStaticServer} = require('./support/static-server');
const emu = require('./support/emulators');

const PRODUCTION = /^https:\/\/[^/]*cloudfunctions\.net\//;
const FROM = '2026-08-01';
const TO = '2026-08-31';

/** The keys `publicView` in functions/export-receipts.js returns — and nothing else. */
const PUBLIC_KEYS = ['byteLength', 'format', 'found', 'fromDate', 'generatedBy', 'recordId',
  'recordedAt', 'recordedAtMillis', 'toDate'].sort();

let server;

test.beforeAll(async () => {
  server = await startStaticServer();
});

test.afterAll(async () => {
  if (server) await server.close();
});

/**
 * Blocks production at the browser and records any attempt, then opens the page.
 *
 * @param {import('@playwright/test').Page} page The page.
 * @param {string} functions The `?functions=` value.
 * @return {Promise<string[]>} The production URLs the page tried (normally none).
 */
async function openVerify(page, functions) {
  const production = [];
  await page.route(PRODUCTION, (route) => {
    production.push(route.request().url());
    return route.abort();
  });
  await page.goto(`${server.origin}/verify/?functions=${encodeURIComponent(functions)}`);
  return production;
}

/** @param {Buffer} bytes @return {string} lowercase hex SHA-256 */
const sha256 = (bytes) => crypto.createHash('sha256').update(bytes).digest('hex');

/** @param {string} id A canonical record ID. @return {string} Four groups of four. */
const grouped = (id) => id.match(/.{4}/g).join('-');

/**
 * Chooses [bytes] as the file and presses "Check the file", returning the `verifyExport`
 * request and response the page made.
 *
 * @param {import('@playwright/test').Page} page The page.
 * @param {{name: string, mimeType: string, buffer: Buffer}} file The file.
 * @return {Promise<{request: Object, response: Object}>} The exchange, parsed.
 */
async function checkFile(page, file) {
  await page.setInputFiles('#file', file);
  const [response] = await Promise.all([
    page.waitForResponse((r) => r.url().endsWith('/verifyExport') && r.request().method() === 'POST'),
    page.click('#checkFile'),
  ]);
  return {request: JSON.parse(response.request().postData()), response: await response.json()};
}

/**
 * Types [id] into the lookup and presses "Look it up", returning the exchange.
 *
 * @param {import('@playwright/test').Page} page The page.
 * @param {string} id What a reader types.
 * @return {Promise<{request: Object, response: Object}>} The exchange, parsed.
 */
async function lookUp(page, id) {
  await page.fill('#recordId', id);
  const [response] = await Promise.all([
    page.waitForResponse((r) => r.url().endsWith('/verifyExport') && r.request().method() === 'POST'),
    page.click('#checkId'),
  ]);
  return {request: JSON.parse(response.request().postData()), response: await response.json()};
}

/**
 * The result box's definition list as `{term: value}`.
 *
 * @param {import('@playwright/test').Page} page The page.
 * @return {Promise<Object<string, string>>} The rows.
 */
async function receiptRows(page) {
  return page.$$eval('#result dl > dt', (dts) =>
    Object.fromEntries(dts.map((dt) => [dt.textContent, dt.nextElementSibling.textContent])));
}

/**
 * Whether the page overflows its viewport sideways — what a phone reader would have to scroll.
 *
 * @param {import('@playwright/test').Page} page The page.
 * @return {Promise<number>} Pixels of horizontal overflow (0 is right).
 */
async function horizontalOverflow(page) {
  return page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
}

// ── Against the Functions emulator ─────────────────────────────────────────────────────────

test.describe('against the Functions emulator', () => {
  test.describe.configure({mode: 'serial'});

  /** Everything a leak would be: both accounts' uids, emails, names, and the family id. */
  let identity;
  let parent;
  let base;
  let csv;
  let pdf;

  /**
   * Reserves an ID, renders a file that prints it, and registers that file's hash — the order
   * `ExportViewModel` follows, so the hash covers the ID printed inside it.
   *
   * @param {string} format 'csv' or 'pdf'.
   * @param {function(string): Buffer} render The file's bytes, given its grouped record ID.
   * @return {Promise<{recordId: string, bytes: Buffer, recordedAtMillis: number}>} The receipt.
   */
  async function registerExport(format, render) {
    const {recordId} = await emu.callable('reserveExportRecordId',
        {familyId: identity.familyId, fromDate: FROM, toDate: TO, format}, parent.token);
    const bytes = render(grouped(recordId));
    const registered = await emu.callable('registerExportReceipt',
        {recordId, sha256: sha256(bytes), byteLength: bytes.length}, parent.token);
    expect(registered.recordId).toBe(recordId);
    return {recordId, bytes, recordedAtMillis: registered.recordedAtMillis};
  }

  test.beforeAll(async () => {
    if (!emu.emulatorsRunning()) return;
    base = await emu.functionsBase();
    // Names with the characters a careless page would mangle, so "never shows a name" is tested
    // against something that would be recognisable if it leaked.
    parent = await emu.signUp('Zdeňka Nováková');
    const coParent = await emu.signUp('Jörg Müller');
    for (const p of [parent, coParent]) {
      await emu.writeAs(`users/${p.uid}`,
          {name: p.name, email: p.email, id: p.uid, firebaseUid: p.uid}, p.token);
    }
    const familyId = [parent.uid, coParent.uid].sort().join('__');
    identity = {familyId, strings: [parent.uid, coParent.uid, parent.email, coParent.email,
      parent.name, coParent.name, familyId]};

    csv = await registerExport('csv', (id) => Buffer.from([
      '"CoPlanly communication record","","",""',
      `"Record ID","${id}","",""`,
      '"This is a record of what the parents recorded and wrote in CoPlanly, not of what happened.","","",""',
      '"2026-08-03 15:00","Event","Pick-up, school","Created"',
      '"2026-08-04 09:12","Message","Díky, platí.","Sent"',
    ].join('\r\n') + '\r\n', 'utf8'));
    pdf = await registerExport('pdf', (id) => Buffer.concat([
      Buffer.from(`%PDF-1.4\n% CoPlanly record ${id}\n`, 'latin1'),
      crypto.randomBytes(2048),
      Buffer.from('\n%%EOF\n', 'latin1'),
    ]));
  });

  test.beforeEach(() => emu.requireEmulators(test));

  /** Nothing identifying anybody is on the page — or in what the server sent it. */
  async function expectNoIdentity(page, answer) {
    const text = await page.locator('body').innerText();
    const json = JSON.stringify(answer || {});
    for (const s of identity.strings) {
      expect(text, `the page shows ${s}`).not.toContain(s);
      expect(json, `the answer carries ${s}`).not.toContain(s);
    }
  }

  test('the exported file, unchanged, is a match with its receipt', async ({page}) => {
    const production = await openVerify(page, base);
    const {request, response} = await checkFile(page,
        {name: 'coplanly-record.csv', mimeType: 'text/csv', buffer: csv.bytes});

    // Only the fingerprint left the browser — not the file, not its name.
    expect(request).toEqual({data: {sha256: sha256(csv.bytes)}});
    expect(Object.keys(response.result).sort()).toEqual(PUBLIC_KEYS);

    const result = page.locator('#result');
    await expect(result).toHaveClass(/\bok\b/);
    await expect(result.locator('h3')).toHaveText('Match — this file is unchanged');
    await expect(page.locator('#fingerprint')).toHaveText(`SHA-256: ${sha256(csv.bytes)}`);
    const rows = await receiptRows(page);
    expect(rows['Record ID']).toBe(grouped(csv.recordId));
    expect(rows['Registered at']).toContain(new Date(csv.recordedAtMillis).toISOString());
    expect(rows['Period covered']).toBe(`${FROM} – ${TO}`);
    expect(rows['Format']).toBe('CSV');
    expect(rows['Registered size']).toBe(`${csv.bytes.length} bytes`);
    expect(rows['Your file']).toBe(`${csv.bytes.length} bytes`);
    expect(rows['Made by']).toBe('one of the family\'s parents');
    await expectNoIdentity(page, response);
    expect(production).toEqual([]);
  });

  test('the file checked against the ID printed on it — and against another ID', async ({page}) => {
    await openVerify(page, base);
    await page.fill('#expected', grouped(pdf.recordId).toLowerCase());
    await checkFile(page, {name: 'record.pdf', mimeType: 'application/pdf', buffer: pdf.bytes});
    await expect(page.locator('#result h3')).toHaveText('Match — this file is unchanged');
    expect((await receiptRows(page))['Format']).toBe('PDF');

    await page.fill('#expected', grouped(csv.recordId));
    const {response} = await checkFile(page,
        {name: 'record.pdf', mimeType: 'application/pdf', buffer: pdf.bytes});
    await expect(page.locator('#result')).toHaveClass(/\bbad\b/);
    await expect(page.locator('#result h3')).toHaveText('Registered — but under a different record ID');
    // The receipt shown is the one the file really matches.
    expect((await receiptRows(page))['Record ID']).toBe(grouped(pdf.recordId));
    await expectNoIdentity(page, response);
  });

  test('one changed byte is no match', async ({page}) => {
    await openVerify(page, base);
    const tampered = Buffer.from(csv.bytes);
    // "Created" → "Crested": the kind of edit that changes what a record says.
    const at = tampered.indexOf('Created');
    tampered[at + 2] = 's'.charCodeAt(0);
    const {response} = await checkFile(page,
        {name: 'coplanly-record.csv', mimeType: 'text/csv', buffer: tampered});
    expect(response.result).toEqual({found: false});
    await expect(page.locator('#result')).toHaveClass(/\bbad\b/);
    await expect(page.locator('#result h3')).toHaveText('No match');
    await expect(page.locator('#result dl')).toHaveCount(0);
  });

  test('a record ID looks up its receipt, typed the way people retype it', async ({page}) => {
    await openVerify(page, base);
    // Lower case, spaces for dashes, and O for 0 / l for 1 where the ID has them.
    const typed = grouped(csv.recordId).toLowerCase().replace(/-/g, ' ')
        .replace(/0/g, 'o').replace(/1/g, 'l');
    const {request, response} = await lookUp(page, typed);
    expect(request).toEqual({data: {recordId: csv.recordId}});
    await expect(page.locator('#result')).toHaveClass(/\binfo\b/);
    await expect(page.locator('#result h3')).toHaveText('Registered');
    const rows = await receiptRows(page);
    expect(rows['Record ID']).toBe(grouped(csv.recordId));
    expect(rows['Period covered']).toBe(`${FROM} – ${TO}`);
    expect(rows['Your file']).toBeUndefined();
    await expectNoIdentity(page, response);
  });

  test('an ID nobody registered is not found — and neither is a mere reservation', async ({page}) => {
    await openVerify(page, base);
    // A well-formed ID with no receipt at all…
    await lookUp(page, '7ZZZ-ZZZZ-ZZZZ-ZZZZ');
    await expect(page.locator('#result h3')).toHaveText('Not found');
    // …and one reserved but never given a hash, which vouches for no file.
    const {recordId} = await emu.callable('reserveExportRecordId',
        {familyId: identity.familyId, fromDate: FROM, toDate: TO, format: 'csv'}, parent.token);
    const {response} = await lookUp(page, recordId);
    expect(response.result).toEqual({found: false});
    await expect(page.locator('#result h3')).toHaveText('Not found');
  });

  test.describe('in Czech', () => {
    test.use({locale: 'cs-CZ'});

    test('follows the browser language, and the switch re-renders the answer', async ({page}) => {
      await openVerify(page, base);
      await expect(page).toHaveTitle('Ověření záznamu CoPlanly');
      await expect(page.locator('html')).toHaveAttribute('lang', 'cs');
      await checkFile(page, {name: 'zaznam.csv', mimeType: 'text/csv', buffer: csv.bytes});
      await expect(page.locator('#result h3')).toHaveText('Shoda — soubor je beze změny');
      expect((await receiptRows(page))['Vytvořil']).toBe('jeden z rodičů rodiny');

      await page.getByRole('button', {name: 'English'}).click();
      await expect(page.locator('html')).toHaveAttribute('lang', 'en');
      await expect(page.locator('#result h3')).toHaveText('Match — this file is unchanged');
      await expect(page.getByRole('button', {name: 'English'})).toHaveAttribute('aria-pressed', 'true');
      await expect(page.getByRole('button', {name: 'Čeština'})).toHaveAttribute('aria-pressed', 'false');
    });
  });

  test.describe('on a phone', () => {
    test.use({viewport: {width: 375, height: 812}, isMobile: true, hasTouch: true});

    test('375 px wide, nothing scrolls sideways — before and after an answer, in both languages',
        async ({page}) => {
          await openVerify(page, base);
          expect(await horizontalOverflow(page)).toBe(0);
          // The widest things the page prints: a 64-character fingerprint and a receipt table.
          await page.fill('#expected', grouped(csv.recordId));
          await checkFile(page, {name: 'a-rather-long-file-name-for-an-export.csv', mimeType: 'text/csv',
            buffer: csv.bytes});
          await expect(page.locator('#result h3')).toHaveText('Match — this file is unchanged');
          expect(await horizontalOverflow(page)).toBe(0);
          await page.getByRole('button', {name: 'Čeština'}).click();
          await expect(page.locator('#result h3')).toHaveText('Shoda — soubor je beze změny');
          expect(await horizontalOverflow(page)).toBe(0);
        });
  });
});

// ── Against a stubbed endpoint ─────────────────────────────────────────────────────────────

test.describe('against a stubbed endpoint', () => {
  // A loopback address nothing listens on; every request to it is answered by page.route.
  const STUB = 'http://127.0.0.1:9/stub';

  /**
   * Answers every `verifyExport` call with [body] and records what was asked.
   *
   * @param {import('@playwright/test').Page} page The page.
   * @param {number} status HTTP status.
   * @param {Object} body The JSON body.
   * @return {Promise<Object[]>} The requests' bodies, as they arrive.
   */
  async function stub(page, status, body) {
    const asked = [];
    const cors = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'POST',
      'Access-Control-Allow-Headers': 'Content-Type',
    };
    await page.route(`${STUB}/verifyExport`, (route) => {
      // The page's POST is cross-origin JSON, so the browser may ask first.
      if (route.request().method() === 'OPTIONS') return route.fulfill({status: 204, headers: cors});
      asked.push(JSON.parse(route.request().postData()));
      return route.fulfill({status, contentType: 'application/json', headers: cors, body: JSON.stringify(body)});
    });
    return asked;
  }

  test('a hosted copy ignores ?functions=, even one naming loopback, and calls production',
      async ({page}) => {
        // Serve the real file under a non-loopback https origin, as Hosting would.
        const html = require('fs').readFileSync(
            require('path').join(__dirname, '..', 'web', 'verify', 'index.html'));
        await page.route('https://verify.coplanly.test/**', (route) =>
          route.fulfill({status: 200, contentType: 'text/html; charset=utf-8', body: html}));
        const production = [];
        await page.route(PRODUCTION, (route) => {
          production.push(route.request().url());
          return route.abort();
        });
        const loopback = [];
        await page.route(/^http:\/\/127\.0\.0\.1:9\//, (route) => {
          loopback.push(route.request().url());
          return route.abort();
        });
        await page.goto(`https://verify.coplanly.test/verify/?functions=${encodeURIComponent(STUB)}`);
        await page.fill('#recordId', '7ZZZ-ZZZZ-ZZZZ-ZZZZ');
        await page.click('#checkId');
        await expect(page.locator('#result')).toContainText('The check could not be completed');
        expect(loopback).toEqual([]);
        expect(production).toEqual([
          'https://us-central1-coparently-a39c9.cloudfunctions.net/verifyExport',
        ]);
      });

  test('a loopback page ignores ?functions= that is not loopback, and calls production', async ({page}) => {
    const production = await openVerify(page, 'https://attacker.example/steal');
    const attacker = [];
    await page.route(/attacker\.example/, (route) => {
      attacker.push(route.request().url());
      return route.abort();
    });
    await page.fill('#recordId', '7ZZZ-ZZZZ-ZZZZ-ZZZZ');
    await page.click('#checkId');
    await expect(page.locator('#result')).toContainText('The check could not be completed');
    expect(attacker).toEqual([]);
    expect(production).toEqual([
      'https://us-central1-coparently-a39c9.cloudfunctions.net/verifyExport',
    ]);
  });

  test('a malformed ID is refused in the browser, without a request', async ({page}) => {
    await openVerify(page, STUB);
    const asked = await stub(page, 200, {result: {found: false}});
    await page.fill('#recordId', '1234-5678');
    await page.click('#checkId');
    await expect(page.locator('#result')).toHaveText(
        'A record ID has 16 letters and digits, usually written as four groups of four.');
    expect(asked).toEqual([]);
  });

  test('a rate limit reads as one, not as "no match"', async ({page}) => {
    await openVerify(page, STUB);
    await stub(page, 429, {error: {status: 'RESOURCE_EXHAUSTED', message: 'Too many lookups'}});
    await page.setInputFiles('#file', {name: 'x.csv', mimeType: 'text/csv', buffer: Buffer.from('x')});
    await page.click('#checkFile');
    await expect(page.locator('#result')).toHaveText(
        'Too many checks from this connection. Please wait a few minutes and try again.');
  });

  test('whatever the server says is shown as text, never as markup', async ({page}) => {
    await openVerify(page, STUB);
    const hostile = '<img src=x onerror="window.__pwned=1">';
    await stub(page, 200, {result: {
      found: true, recordId: '7ZZZZZZZZZZZZZZZ', recordedAtMillis: 0, recordedAt: '1970-01-01T00:00:00.000Z',
      fromDate: hostile, toDate: '2026-08-31', format: hostile, byteLength: 1, generatedBy: 'x',
    }});
    await page.fill('#recordId', '7ZZZZZZZZZZZZZZZ');
    await page.click('#checkId');
    await expect(page.locator('#result h3')).toHaveText('Registered');
    await expect(page.locator('#result img')).toHaveCount(0);
    expect(await page.evaluate(() => window.__pwned)).toBeUndefined();
    expect((await receiptRows(page))['Period covered']).toBe(`${hostile} – 2026-08-31`);
  });
});
