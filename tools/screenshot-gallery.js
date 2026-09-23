#!/usr/bin/env node
/**
 * Writes an `index.html` beside the Roborazzi screenshots so they can be browsed from the CI
 * artefact without an Android SDK, an IDE or any tool beyond a browser.
 *
 * The screenshot tests (`app/src/test/java/com/coparently/app/screenshots`) record each image as
 * `<component>/<locale>_<theme>_fs<percent>_<palette>.png` under `app/build/outputs/roborazzi`.
 * This reads that layout back: one section per component, one card per variant, and four filters
 * (language, theme, font scale, palette) so "every component in dark" or "everything in German
 * at 1.5x" is one click.
 *
 * No dependencies, deliberately, like the other scripts here: it runs in the `screenshots` job
 * straight after Gradle, with nothing installed.
 *
 * Usage: node tools/screenshot-gallery.js [screenshot-directory]
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const DEFAULT_DIR = path.join(ROOT, 'app/build/outputs/roborazzi');
const dir = process.argv[2] ? path.resolve(process.argv[2]) : DEFAULT_DIR;

/** `de_light_fs150_pinkblue` → its four facets; anything else is kept under "other". */
const VARIANT = /^([a-z]{2})_(light|dark)_fs(\d+)_([a-z]+)$/;

// Roborazzi writes `_compare`/`_actual` images beside the recorded ones when it verifies or
// compares. Record mode produces none, but a gallery that listed them would show one variant
// three times, so they are skipped rather than trusted never to appear.
const DERIVED = /_(compare|actual)\.png$/;

function listImages(base) {
  const found = [];
  const walk = (current) => {
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (entry.name.endsWith('.png') && !DERIVED.test(entry.name)) {
        found.push(path.relative(base, full).split(path.sep).join('/'));
      }
    }
  };
  walk(base);
  return found.sort();
}

function describe(relative) {
  const parts = relative.split('/');
  const file = parts.pop().replace(/\.png$/, '');
  const component = parts.length > 0 ? parts.join('/') : 'other';
  const match = VARIANT.exec(file);
  if (!match) {
    return { path: relative, component, name: file, locale: '', theme: '', scale: '', palette: '' };
  }
  const [, locale, theme, percent, palette] = match;
  return {
    path: relative,
    component,
    name: file,
    locale,
    theme,
    scale: (Number(percent) / 100).toFixed(1) + 'x',
    palette,
  };
}

const escape = (text) =>
  String(text).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

function options(values) {
  return ['<option value="">all</option>']
    .concat([...values].sort().map((v) => `<option>${escape(v)}</option>`))
    .join('');
}

function render(images) {
  const byComponent = new Map();
  for (const image of images) {
    if (!byComponent.has(image.component)) byComponent.set(image.component, []);
    byComponent.get(image.component).push(image);
  }
  const facet = (key) => new Set(images.map((i) => i[key]).filter(Boolean));

  const sections = [...byComponent.entries()].map(([component, list]) => {
    const cards = list.map((i) => `
      <figure data-locale="${escape(i.locale)}" data-theme="${escape(i.theme)}"
              data-scale="${escape(i.scale)}" data-palette="${escape(i.palette)}">
        <a href="${escape(i.path)}"><img loading="lazy" src="${escape(i.path)}" alt="${escape(i.name)}"></a>
        <figcaption>${escape([i.locale, i.theme, i.scale, i.palette].filter(Boolean).join(' · ') || i.name)}</figcaption>
      </figure>`).join('');
    return `
    <section data-component="${escape(component)}">
      <h2>${escape(component)} <small>${list.length}</small></h2>
      <div class="grid">${cards}</div>
    </section>`;
  }).join('');

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>CoPlanly screenshots</title>
<style>
  :root { color-scheme: light dark; --bg: #f6f4fa; --fg: #1b1b21; --card: #ffffff; --muted: #5f5e68; }
  @media (prefers-color-scheme: dark) { :root { --bg: #131318; --fg: #e5e1e9; --card: #1f1f25; --muted: #c8c5d0; } }
  body { margin: 0; padding: 16px; font: 14px/1.4 system-ui, sans-serif; background: var(--bg); color: var(--fg); }
  header { position: sticky; top: 0; background: var(--bg); padding: 8px 0 12px; z-index: 1; }
  label { margin-right: 12px; color: var(--muted); }
  select { margin-left: 4px; }
  h2 { font-size: 16px; margin: 24px 0 8px; }
  h2 small { color: var(--muted); font-weight: normal; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
  figure { margin: 0; background: var(--card); border-radius: 8px; padding: 8px; }
  figure img { width: 100%; height: auto; display: block; border-radius: 4px; }
  figcaption { margin-top: 6px; color: var(--muted); font-size: 12px; }
  .hidden { display: none; }
</style>
</head>
<body>
<header>
  <strong>${images.length} screenshots</strong> —
  <label>component<select id="component">${options(new Set(byComponent.keys()))}</select></label>
  <label>language<select id="locale">${options(facet('locale'))}</select></label>
  <label>theme<select id="theme">${options(facet('theme'))}</select></label>
  <label>font scale<select id="scale">${options(facet('scale'))}</select></label>
  <label>palette<select id="palette">${options(facet('palette'))}</select></label>
</header>
${sections}
<script>
  const keys = ['locale', 'theme', 'scale', 'palette'];
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

if (!fs.existsSync(dir)) {
  console.error('No screenshot directory at ' + dir + '.');
  console.error('Run `./gradlew recordRoborazziDebug` first.');
  process.exit(1);
}

const images = listImages(dir).map(describe);
if (images.length === 0) {
  console.error('No screenshots under ' + dir + ' — the recording produced nothing.');
  process.exit(1);
}

const out = path.join(dir, 'index.html');
fs.writeFileSync(out, render(images));
const components = new Set(images.map((i) => i.component)).size;
console.log(`Wrote ${out}: ${images.length} screenshots across ${components} components.`);
