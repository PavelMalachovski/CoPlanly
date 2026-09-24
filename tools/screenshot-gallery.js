#!/usr/bin/env node
/**
 * Writes an `index.html` beside the Roborazzi screenshots so they can be browsed from the CI
 * artefact without an Android SDK, an IDE or any tool beyond a browser.
 *
 * The screenshot tests (`app/src/test/java/com/coparently/app/screenshots`) record each image as
 * `<component>/<locale>_<theme>_fs<percent>_<palette>.png` under the committed baseline directory
 * `app/src/test/screenshots`. This reads that layout back: one section per component, one card
 * per variant, and filters (language, theme, font scale, palette, status) so "every component in
 * dark" or "everything in German at 1.5x" is one click.
 *
 * **Verify runs.** When the `screenshots` job verifies against the baselines, Roborazzi writes a
 * `<variant>_compare.png` (baseline, diff and new image side by side) and a `<variant>_actual.png`
 * for every image that no longer matches, into `<component>/` under its compare directory
 * (`ScreenshotMatrix` points the compare directory at the component, because Roborazzi itself
 * would drop the component and let every component's `en_light_fs100_default_compare.png`
 * overwrite the last). The job copies those beside the baselines before calling this, and each
 * one turns its variant's card into a "changed" card showing the diff — or a "new" card when
 * there is no baseline at all (a test added without re-running the Regenerate workflow).
 * `--summary <file>` also writes what changed as JSON, which `tools/ci-report.js` puts in the PR
 * comment.
 *
 * No dependencies, deliberately, like the other scripts here: it runs in the `screenshots` job
 * straight after Gradle, with nothing installed.
 *
 * Usage: node tools/screenshot-gallery.js [screenshot-directory] [--mode verify|record]
 *                                         [--summary <file>]
 */
'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const DEFAULT_DIR = path.join(ROOT, 'app/src/test/screenshots');

/**
 * `de_light_fs150_pinkblue` → its four facets, and `en_dark_fs100_pinkblue_high` → a fifth, the
 * contrast level, which a file names only when it is raised; anything else is kept under "other".
 */
const VARIANT = /^([a-z]{2})_(light|dark)_fs(\d+)_([a-z]+?)(?:_(medium|high))?$/;

/** The two images Roborazzi derives from a recorded one when it verifies or compares. */
const DERIVED = /_(compare|actual)\.png$/;

/** @param {string} base a directory @return {string[]} every PNG below it, `/`-separated, sorted */
function listImages(base) {
  const found = [];
  const walk = (current) => {
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (entry.name.endsWith('.png')) {
        found.push(path.relative(base, full).split(path.sep).join('/'));
      }
    }
  };
  walk(base);
  return found.sort();
}

/**
 * Folds each variant's recorded image and its derived `_compare`/`_actual` images into one entry.
 * A derived image without a recorded one is a screenshot with no baseline ("new"); one with a
 * recorded image is a screenshot that no longer matches it ("changed").
 * @param {string[]} files relative PNG paths, as listImages returns them
 * @return {{key: string, baseline: string|null, compare: string|null, actual: string|null,
 *   status: string}[]} one entry per variant, sorted by key
 */
function group(files) {
  const byKey = new Map();
  for (const file of files) {
    const derived = DERIVED.exec(file);
    const key = derived ? file.replace(DERIVED, '.png') : file;
    if (!byKey.has(key)) byKey.set(key, { key, baseline: null, compare: null, actual: null });
    byKey.get(key)[derived ? derived[1] : 'baseline'] = file;
  }
  const status = (e) => (e.compare || e.actual ? (e.baseline ? 'changed' : 'new') : 'unchanged');
  return [...byKey.values()]
    .map((e) => ({ ...e, status: status(e) }))
    .sort((a, b) => (a.key < b.key ? -1 : a.key > b.key ? 1 : 0));
}

/**
 * @param {{key: string, baseline: string|null, compare: string|null, actual: string|null,
 *   status: string}} entry one variant, as group returns it
 * @return {object} the entry with its component and facets
 */
function describe(entry) {
  const parts = entry.key.split('/');
  const file = parts.pop().replace(/\.png$/, '');
  const component = parts.length > 0 ? parts.join('/') : 'other';
  const match = VARIANT.exec(file);
  const facets = match
    ? {
      locale: match[1],
      theme: match[2],
      scale: (Number(match[3]) / 100).toFixed(1) + 'x',
      palette: match[4],
      contrast: match[5] || 'standard',
    }
    : { locale: '', theme: '', scale: '', palette: '', contrast: '' };
  return { ...entry, component, name: file, ...facets };
}

/**
 * What a run found, for `tools/ci-report.js`.
 * @param {object[]} images described entries
 * @param {string} mode `verify` or `record`
 * @return {{mode: string, total: number, changed: string[], added: string[]}} the summary
 */
function summarize(images, mode) {
  return {
    mode,
    total: images.length,
    changed: images.filter((i) => i.status === 'changed').map((i) => i.key),
    added: images.filter((i) => i.status === 'new').map((i) => i.key),
  };
}

const escape = (text) =>
  String(text).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

function options(values) {
  return ['<option value="">all</option>']
    .concat([...values].sort().map((v) => `<option>${escape(v)}</option>`))
    .join('');
}

const STATUS_LABEL = { changed: 'changed', new: 'no baseline' };

/** @param {object} i a described entry @return {string} its card */
function card(i) {
  const shown = i.compare || i.baseline || i.actual;
  const links = [['baseline', i.baseline], ['diff', i.compare], ['new', i.actual]]
    .filter(([, p]) => p)
    .map(([label, p]) => `<a href="${escape(p)}">${label}</a>`);
  const contrast = i.contrast && i.contrast !== 'standard' ? `${i.contrast} contrast` : '';
  const facets = [i.locale, i.theme, i.scale, i.palette, contrast].filter(Boolean).join(' · ') || i.name;
  const badge = STATUS_LABEL[i.status] ? `<span class="badge">${STATUS_LABEL[i.status]}</span> ` : '';
  return `
      <figure class="${escape(i.status)}" data-locale="${escape(i.locale)}" data-theme="${escape(i.theme)}"
              data-scale="${escape(i.scale)}" data-palette="${escape(i.palette)}"
              data-contrast="${escape(i.contrast)}" data-status="${escape(i.status)}">
        <a href="${escape(shown)}"><img loading="lazy" src="${escape(shown)}" alt="${escape(i.name)}"></a>
        <figcaption>${badge}${escape(facets)}${links.length > 1 ? ' — ' + links.join(' · ') : ''}</figcaption>
      </figure>`;
}

function render(images, mode) {
  const byComponent = new Map();
  for (const image of images) {
    if (!byComponent.has(image.component)) byComponent.set(image.component, []);
    byComponent.get(image.component).push(image);
  }
  const facet = (key) => new Set(images.map((i) => i[key]).filter(Boolean));
  const summary = summarize(images, mode);

  const sections = [...byComponent.entries()].map(([component, list]) => {
    const changed = list.filter((i) => i.status !== 'unchanged').length;
    return `
    <section data-component="${escape(component)}">
      <h2>${escape(component)} <small>${list.length}${changed ? `, ${changed} not matching` : ''}</small></h2>
      <div class="grid">${list.map(card).join('')}</div>
    </section>`;
  }).join('');

  const verdict = mode === 'verify'
    ? (summary.changed.length || summary.added.length
      ? `<span class="badge">${summary.changed.length} changed, ${summary.added.length} without a baseline</span>`
      : 'all match their baselines')
    : 'recorded, not compared';

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>CoPlanly screenshots</title>
<style>
  :root { color-scheme: light dark; --bg: #f6f4fa; --fg: #1b1b21; --card: #ffffff; --muted: #5f5e68; --alert: #ba1a1a; }
  @media (prefers-color-scheme: dark) { :root { --bg: #131318; --fg: #e5e1e9; --card: #1f1f25; --muted: #c8c5d0; --alert: #ffb4ab; } }
  body { margin: 0; padding: 16px; font: 14px/1.4 system-ui, sans-serif; background: var(--bg); color: var(--fg); }
  header { position: sticky; top: 0; background: var(--bg); padding: 8px 0 12px; z-index: 1; }
  label { margin-right: 12px; color: var(--muted); }
  select { margin-left: 4px; }
  h2 { font-size: 16px; margin: 24px 0 8px; }
  h2 small { color: var(--muted); font-weight: normal; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
  figure { margin: 0; background: var(--card); border-radius: 8px; padding: 8px; }
  figure.changed, figure.new { outline: 2px solid var(--alert); grid-column: span 2; }
  figure img { width: 100%; height: auto; display: block; border-radius: 4px; }
  figcaption { margin-top: 6px; color: var(--muted); font-size: 12px; }
  .badge { color: var(--alert); font-weight: 600; }
  .hidden { display: none; }
</style>
</head>
<body>
<header>
  <strong>${images.length} screenshots</strong> (${mode}: ${verdict}) —
  <label>component<select id="component">${options(new Set(byComponent.keys()))}</select></label>
  <label>status<select id="status">${options(facet('status'))}</select></label>
  <label>language<select id="locale">${options(facet('locale'))}</select></label>
  <label>theme<select id="theme">${options(facet('theme'))}</select></label>
  <label>font scale<select id="scale">${options(facet('scale'))}</select></label>
  <label>palette<select id="palette">${options(facet('palette'))}</select></label>
  <label>contrast<select id="contrast">${options(facet('contrast'))}</select></label>
</header>
${sections}
<script>
  const keys = ['status', 'locale', 'theme', 'scale', 'palette', 'contrast'];
  function apply() {
    const component = document.getElementById('component').value;
    const wanted = Object.fromEntries(keys.map((k) => [k, document.getElementById(k).value]));
    for (const section of document.querySelectorAll('section')) {
      let shown = 0;
      for (const figure of section.querySelectorAll('figure')) {
        const ok = keys.every((k) => !wanted[k] || figure.dataset[k] === wanted[k]);
        figure.classList.toggle('hidden', !ok);
        if (ok) shown++;
      }
      section.classList.toggle('hidden', shown === 0 || (component && section.dataset.component !== component));
    }
  }
  for (const id of ['component'].concat(keys)) document.getElementById(id).addEventListener('change', apply);
</script>
</body>
</html>
`;
}

/** @param {string[]} argv arguments after the script @return {{dir: string, mode: string, summary: string|null}} */
function parseArgs(argv) {
  const out = { dir: DEFAULT_DIR, mode: 'record', summary: null };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--mode') out.mode = argv[++i];
    else if (argv[i] === '--summary') out.summary = path.resolve(argv[++i]);
    else out.dir = path.resolve(argv[i]);
  }
  return out;
}

function main() {
  const { dir, mode, summary } = parseArgs(process.argv.slice(2));
  if (!fs.existsSync(dir)) {
    console.error('No screenshot directory at ' + dir + '.');
    console.error('Run `./gradlew recordRoborazziDebug` first.');
    process.exit(1);
  }

  const images = group(listImages(dir)).map(describe);
  if (images.length === 0) {
    console.error('No screenshots under ' + dir + ' — the run produced nothing.');
    process.exit(1);
  }

  const out = path.join(dir, 'index.html');
  fs.writeFileSync(out, render(images, mode));
  const result = summarize(images, mode);
  if (summary) fs.writeFileSync(summary, JSON.stringify(result, null, 2) + '\n');
  const components = new Set(images.map((i) => i.component)).size;
  console.log(`Wrote ${out}: ${images.length} screenshots across ${components} components ` +
    `(${mode}; ${result.changed.length} changed, ${result.added.length} without a baseline).`);
}

if (require.main === module) main();

module.exports = { listImages, group, describe, summarize, render, parseArgs };
