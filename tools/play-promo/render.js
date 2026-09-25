#!/usr/bin/env node
'use strict';

/**
 * Renders the Google Play store-listing images from UI tour screenshots.
 *
 *   node tools/play-promo/render.js --lang cs --shots <tour>/light-cs-100
 *   node tools/play-promo/render.js --lang en --shots <tour>/light-en-100
 *
 * Writes `<out>/<lang>/01.png … 08.png` (1080 × 1920, the phone screenshots) and
 * `<out>/feature-graphic-<lang>.png` (1024 × 500). `--out` defaults to `docs/play-listing`.
 * `--preview` stamps every image "Preview — not for upload", for a layout check made from
 * another language's screenshots; such images must never be committed or uploaded.
 * `--only slides|feature` renders one kind.
 *
 * Each image is an HTML page rendered by Playwright's Chromium, borrowed from `web-tests/`
 * (`cd web-tests && npm ci` once; `PLAYWRIGHT_BROWSERS_PATH` if Chromium lives elsewhere).
 * The screenshot is scaled into the frame, never stretched: the status bar is painted over in
 * the app bar's own colour and the system navigation bar is cropped. See docs/play-listing/README.md.
 */

const fs = require('fs');
const path = require('path');
const { SLIDES, FEATURE, FEATURE_SHOT } = require('./captions');

const REPO = path.resolve(__dirname, '..', '..');
const FONT_DIR = path.join(REPO, 'app', 'src', 'main', 'res', 'font');

// presentation/theme/Color.kt — BrandPrimary and its container, the parent fills as accents.
const COLORS = {
  primary: '#4F46E5',
  primaryDeep: '#3730B8',
  primaryContainer: '#E2E0FF',
  momPink: '#E91E63',
  dadBlue: '#1976D2',
  bezel: '#16161C',
};

// The tour's device: 1080 × 2400 at 2.75× density (Pixel 6, API 30).
const SOURCE = { width: 1080, height: 2400, statusBar: 64, navBar: 126 };
// The month grid's rows, without the header and the weekday names (the only text on it).
const GRID = { top: 644, bottom: 1832 };

const SLIDE_SIZE = { width: 1080, height: 1920 };
const FEATURE_SIZE = { width: 1024, height: 500 };

function parseArgs(argv) {
  const args = { out: path.join(REPO, 'docs', 'play-listing'), preview: false, only: null };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--lang') args.lang = argv[++i];
    else if (a === '--shots') args.shots = argv[++i];
    else if (a === '--out') args.out = path.resolve(argv[++i]);
    else if (a === '--only') args.only = argv[++i];
    else if (a === '--preview') args.preview = true;
    else throw new Error(`Unknown argument: ${a}`);
  }
  if (!args.lang || !args.shots) {
    throw new Error('Usage: render.js --lang cs|en --shots <dir> [--out <dir>] [--preview] [--only slides|feature]');
  }
  if (!SLIDES[0][args.lang] || !FEATURE[args.lang]) throw new Error(`No captions for language "${args.lang}"`);
  if (args.only && !['slides', 'feature'].includes(args.only)) throw new Error('--only takes slides or feature');
  return args;
}

function dataUri(file, type) {
  return `data:${type};base64,${fs.readFileSync(file).toString('base64')}`;
}

function shotUri(dir, name) {
  const file = path.join(dir, `${name}.png`);
  if (!fs.existsSync(file)) throw new Error(`Missing tour screenshot: ${file}`);
  return dataUri(file, 'image/png');
}

function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    // A pattern name such as 3-4-4-3 must not break at its hyphens.
    .replace(/\d(?:-\d)+/g, (m) => `<span style="white-space:nowrap">${m}</span>`);
}

function fontFaces() {
  const weights = { regular: 400, medium: 500, semibold: 600, bold: 700 };
  return Object.entries(weights)
    .map(([name, weight]) => `@font-face{font-family:Onest;font-weight:${weight};` +
      `src:url(${dataUri(path.join(FONT_DIR, `onest_${name}.ttf`), 'font/ttf')}) format('truetype');}`)
    .join('\n');
}

/** The launcher glyph (res/mipmap/ic_launcher_foreground.xml), unscaled, in the brand colour. */
function logoSvg(size) {
  const dot = (x, y) => `<rect x="${x}" y="${y}" width="6" height="6" rx="1.5"/>`;
  const dots = [54, 63, 72].flatMap((y) => [37, 51, 65].map((x) => dot(x, y))).join('');
  return `<svg width="${size}" height="${size}" viewBox="22 24 64 64" fill="${COLORS.primary}">
    <path d="M40,28 A3,3 0 0 1 43,31 V39 A3,3 0 0 1 37,39 V31 A3,3 0 0 1 40,28 Z"/>
    <path d="M68,28 A3,3 0 0 1 71,31 V39 A3,3 0 0 1 65,39 V31 A3,3 0 0 1 68,28 Z"/>
    <path fill-rule="evenodd" d="M35,34 H73 A9,9 0 0 1 82,43 V75 A9,9 0 0 1 73,84 H35 A9,9 0 0 1 26,75 V43 A9,9 0 0 1 35,34 Z M35,38 H73 A5,5 0 0 1 78,43 V75 A5,5 0 0 1 73,80 H35 A5,5 0 0 1 30,75 V43 A5,5 0 0 1 35,38 Z"/>
    <path d="M35,38 H73 A5,5 0 0 1 78,43 V49 H30 V43 A5,5 0 0 1 35,38 Z"/>
    ${dots}
  </svg>`;
}

const BASE_CSS = `
  *{box-sizing:border-box;margin:0;padding:0}
  html,body{width:100%;height:100%}
  body{font-family:Onest,sans-serif;color:#fff;overflow:hidden;position:relative;
    background:${COLORS.primary};-webkit-font-smoothing:antialiased}
  .bg{position:absolute;inset:0;
    background:
      radial-gradient(ellipse 60% 45% at 0% 0%, rgba(233,30,99,.30), transparent 70%),
      radial-gradient(ellipse 70% 50% at 100% 100%, rgba(25,118,210,.45), transparent 70%),
      linear-gradient(165deg, #5A51F0 0%, ${COLORS.primary} 45%, ${COLORS.primaryDeep} 100%)}
  .phone{position:absolute;background:${COLORS.bezel};
    box-shadow:0 2px 0 1px rgba(255,255,255,.08) inset, 0 40px 80px rgba(12,8,70,.38), 0 8px 20px rgba(12,8,70,.22)}
  .screen{position:relative;overflow:hidden;width:100%;height:100%;background:#FCFBFF}
  .screen img{position:absolute;left:0;display:block}
  .status{position:absolute;left:0;top:0;width:100%}
  .camera{position:absolute;left:50%;border-radius:50%;background:#0B0B0F;transform:translateX(-50%)}
  .preview{position:absolute;z-index:10;left:0;right:0;text-align:center;font-weight:700;
    color:#fff;background:rgba(186,26,26,.92);letter-spacing:.08em;text-transform:uppercase}
`;

/**
 * Paints the status bar in the colour just below it, so the frame shows the app and not the
 * tour device's clock and notification icons. Runs in the page, after the image has loaded.
 */
const PAINT_STATUS_BAR = `
  (async () => {
    const img = document.querySelector('.screen img');
    await img.decode();
    const c = document.createElement('canvas');
    c.width = img.naturalWidth; c.height = img.naturalHeight;
    const g = c.getContext('2d');
    g.drawImage(img, 0, 0);
    const [r, gr, b] = g.getImageData(8, ${SOURCE.statusBar + 4}, 1, 1).data;
    const bar = document.querySelector('.status');
    if (bar) bar.style.background = 'rgb(' + r + ',' + gr + ',' + b + ')';
    document.body.dataset.ready = '1';
  })();
`;

function slideHtml({ shot, headline, subline, preview }) {
  const W = SLIDE_SIZE.width;
  const phoneTop = 492;
  const phoneBottomMargin = 64;
  const bezel = 14;
  const visibleHeight = SOURCE.height - SOURCE.navBar; // status bar stays, painted over
  const screenH = SLIDE_SIZE.height - phoneTop - phoneBottomMargin - 2 * bezel;
  const scale = screenH / visibleHeight;
  const screenW = Math.round(SOURCE.width * scale);
  const phoneW = screenW + 2 * bezel;
  const statusH = Math.round(SOURCE.statusBar * scale);
  const radius = 64;

  return `<!doctype html><html><head><meta charset="utf-8"><style>
  ${fontFaces()}
  ${BASE_CSS}
  .text{position:absolute;left:80px;right:80px;top:112px;text-align:center}
  h1{font-weight:700;font-size:80px;line-height:1.1;letter-spacing:-.015em;text-wrap:balance}
  p{margin-top:26px;font-weight:400;font-size:38px;line-height:1.35;color:${COLORS.primaryContainer};text-wrap:balance}
  .phone{left:${(W - phoneW) / 2}px;top:${phoneTop}px;width:${phoneW}px;height:${screenH + 2 * bezel}px;
    padding:${bezel}px;border-radius:${radius}px}
  .screen{border-radius:${radius - bezel}px}
  .screen img{top:0;width:${screenW}px;height:${Math.round(SOURCE.height * scale)}px}
  .status{height:${statusH}px}
  .camera{top:${Math.round(statusH / 2) - 9}px;width:18px;height:18px}
  .preview{top:40px;font-size:30px;padding:10px 0}
  </style></head><body>
  <div class="bg"></div>
  ${preview ? '<div class="preview">Preview — not for upload</div>' : ''}
  <div class="text"><h1>${escapeHtml(headline)}</h1>${subline ? `<p>${escapeHtml(subline)}</p>` : ''}</div>
  <div class="phone"><div class="screen">
    <img src="${shot}"><div class="status"></div><div class="camera"></div>
  </div></div>
  <script>${PAINT_STATUS_BAR}</script>
  </body></html>`;
}

function featureHtml({ shot, tagline, points, preview }) {
  const bezel = 12;
  const screenW = 400;
  const scale = screenW / SOURCE.width;
  const phoneW = screenW + 2 * bezel;
  const inset = 18; // the grid's own top margin inside the screen
  const gridH = Math.round((GRID.bottom - GRID.top) * scale);
  const screenH = inset + gridH + 40; // runs off the bottom edge
  return `<!doctype html><html><head><meta charset="utf-8"><style>
  ${fontFaces()}
  ${BASE_CSS}
  .bg{background:
      radial-gradient(ellipse 45% 70% at 0% 0%, rgba(233,30,99,.30), transparent 70%),
      radial-gradient(ellipse 55% 80% at 100% 100%, rgba(25,118,210,.50), transparent 70%),
      linear-gradient(120deg, #5A51F0 0%, ${COLORS.primary} 45%, ${COLORS.primaryDeep} 100%)}
  .brand{position:absolute;left:72px;top:0;bottom:0;width:430px;display:flex;flex-direction:column;justify-content:center}
  .name{display:flex;align-items:center;gap:22px}
  .tile{width:96px;height:96px;border-radius:26px;background:#fff;display:flex;align-items:center;justify-content:center;
    box-shadow:0 12px 30px rgba(12,8,70,.30)}
  .name span{font-weight:700;font-size:72px;letter-spacing:-.02em;line-height:1}
  .tagline{margin-top:30px;font-weight:600;font-size:32px;line-height:1.22;text-wrap:balance}
  .points{margin-top:20px;font-weight:500;font-size:24px;color:${COLORS.primaryContainer};display:flex;align-items:center;gap:12px}
  .dot{width:12px;height:12px;border-radius:50%;display:inline-block}
  .phone{left:${FEATURE_SIZE.width - phoneW - 60}px;top:40px;width:${phoneW}px;height:${screenH + 2 * bezel}px;
    padding:${bezel}px;border-radius:52px;transform:rotate(4deg);transform-origin:50% 0}
  .screen{border-radius:40px}
  .screen img{left:0;top:${inset - Math.round(GRID.top * scale)}px;width:${screenW}px;height:${Math.round(SOURCE.height * scale)}px;
    clip-path:inset(${Math.round(GRID.top * scale) - 2}px 0 ${Math.round((SOURCE.height - GRID.bottom) * scale)}px 0)}
  .preview{top:16px;font-size:18px;padding:6px 0}
  </style></head><body>
  <div class="bg"></div>
  ${preview ? '<div class="preview">Preview — not for upload</div>' : ''}
  <div class="brand">
    <div class="name"><div class="tile">${logoSvg(70)}</div><span>CoPlanly</span></div>
    <div class="tagline">${escapeHtml(tagline)}</div>
    <div class="points"><span class="dot" style="background:${COLORS.momPink}"></span><span class="dot" style="background:${COLORS.dadBlue};margin-left:-6px"></span>${escapeHtml(points)}</div>
  </div>
  <div class="phone"><div class="screen"><img src="${shot}"></div></div>
  <script>document.querySelector('img').decode().then(() => { document.body.dataset.ready = '1'; });</script>
  </body></html>`;
}

/** Play refuses an image with an alpha channel: check the PNG header says 8-bit RGB. */
function assertOpaquePng(file, size) {
  const buf = fs.readFileSync(file);
  const width = buf.readUInt32BE(16);
  const height = buf.readUInt32BE(20);
  const bitDepth = buf[24];
  const colorType = buf[25];
  if (width !== size.width || height !== size.height) {
    throw new Error(`${file}: ${width}×${height}, expected ${size.width}×${size.height}`);
  }
  if (bitDepth !== 8 || colorType !== 2) {
    throw new Error(`${file}: PNG colour type ${colorType} at ${bitDepth} bits, expected 8-bit RGB (no alpha)`);
  }
}

async function renderPage(browser, html, size, file) {
  const page = await browser.newPage({ viewport: size, deviceScaleFactor: 1 });
  await page.setContent(html, { waitUntil: 'load' });
  await page.waitForFunction(() => document.body.dataset.ready === '1');
  await page.evaluate(() => document.fonts.ready);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  await page.screenshot({ path: file, type: 'png', omitBackground: false });
  await page.close();
  assertOpaquePng(file, size);
  console.log(`wrote ${path.relative(process.cwd(), file)}`);
}

function loadPlaywright() {
  try {
    return require(require.resolve('@playwright/test', { paths: [path.join(REPO, 'web-tests')] }));
  } catch (e) {
    throw new Error('Playwright not found: run `cd web-tests && npm ci` first');
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const { chromium } = loadPlaywright();
  const browser = await chromium.launch();
  try {
    if (args.only !== 'feature') {
      for (const [i, slide] of SLIDES.entries()) {
        const words = slide[args.lang];
        const file = path.join(args.out, args.lang, `${String(i + 1).padStart(2, '0')}.png`);
        const html = slideHtml({ shot: shotUri(args.shots, slide.shot), ...words, preview: args.preview });
        await renderPage(browser, html, SLIDE_SIZE, file);
      }
    }
    if (args.only !== 'slides') {
      const file = path.join(args.out, `feature-graphic-${args.lang}.png`);
      const html = featureHtml({ shot: shotUri(args.shots, FEATURE_SHOT), ...FEATURE[args.lang], preview: args.preview });
      await renderPage(browser, html, FEATURE_SIZE, file);
    }
  } finally {
    await browser.close();
  }
}

main().catch((e) => {
  console.error(e.message || e);
  process.exit(1);
});
