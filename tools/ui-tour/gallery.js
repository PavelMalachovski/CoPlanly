#!/usr/bin/env node
/**
 * Builds the UI tour's gallery: one static `index.html`, no dependencies, that shows every screen
 * the tour captured with its variants side by side.
 *
 *   node tools/ui-tour/gallery.js <site dir> [variant ...]
 *
 * `<site dir>` holds `ui-tour/<variant>/NN_<screen>.png` and each variant's `manifest.json`, as
 * `tools/ui-tour/run-ui-tour.sh` pulls them; `index.html` is written next to `ui-tour/`, with
 * relative links, so the page works from the published branch, from an unzipped artifact and from
 * `file://`. Variants are shown in the order given (default: every directory, sorted). A screen is
 * matched across variants by its name without the number; one a variant skipped shows the reason
 * the manifest gives for it.
 *
 * `UI_TOUR_SOURCE` (e.g. "claude/my-branch @ 1a2b3c4") is printed under the title when set.
 */
'use strict';

const fs = require('fs');
const path = require('path');

const PNG = /^(\d+)_(.+)\.png$/;

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/** One variant's captures and skips, from its manifest and, failing that, from the files. */
function readVariant(dir, name) {
  const variantDir = path.join(dir, 'ui-tour', name);
  const files = fs.existsSync(variantDir) ? fs.readdirSync(variantDir).filter((f) => PNG.test(f)) : [];
  let manifest = {};
  const manifestPath = path.join(variantDir, 'manifest.json');
  if (fs.existsSync(manifestPath)) {
    try {
      manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
    } catch (e) {
      manifest = { error: `manifest.json is not JSON: ${e.message}` };
    }
  }
  const skipped = new Map();
  for (const section of Object.values(manifest.sections || {})) {
    for (const entry of section.skipped || []) skipped.set(entry.screen, entry.reason || 'skipped');
  }
  const shots = new Map();
  for (const file of files) {
    const [, number, screen] = file.match(PNG);
    shots.set(screen, { file, number: Number(number) });
  }
  return { name, manifest, shots, skipped };
}

/** Every screen any variant has, in the tour's order (the lowest number it was given). */
function screenOrder(variants) {
  const order = new Map();
  for (const variant of variants) {
    for (const [screen, shot] of variant.shots) {
      order.set(screen, Math.min(order.get(screen) ?? Infinity, shot.number));
    }
    for (const screen of variant.skipped.keys()) {
      if (!order.has(screen)) order.set(screen, Infinity);
    }
  }
  return [...order.entries()].sort((a, b) => a[1] - b[1] || a[0].localeCompare(b[0])).map(([s]) => s);
}

function describe(variant) {
  const m = variant.manifest;
  const device = m.device ? `${m.device.model}, API ${m.device.sdk}, ${m.device.widthPx}×${m.device.heightPx}` : '';
  const font = typeof m.fontScale === 'number' ? `font ×${m.fontScale.toFixed(2)}` : '';
  const counts = `${variant.shots.size} captured, ${variant.skipped.size} skipped`;
  return [counts, device, font, m.error || ''].filter(Boolean).join(' · ');
}

function render(variants, source) {
  const screens = screenOrder(variants);
  const columns = variants.length || 1;
  const rows = screens.map((screen) => {
    const cells = variants.map((variant) => {
      const shot = variant.shots.get(screen);
      if (shot) {
        const href = `ui-tour/${encodeURIComponent(variant.name)}/${encodeURIComponent(shot.file)}`;
        return `<figure><a href="${href}"><img loading="lazy" src="${href}" alt="${escapeHtml(screen)}, ${escapeHtml(variant.name)}"></a><figcaption>${escapeHtml(variant.name)}</figcaption></figure>`;
      }
      const reason = variant.skipped.get(screen) || 'not captured';
      return `<figure class="missing"><div>${escapeHtml(reason)}</div><figcaption>${escapeHtml(variant.name)}</figcaption></figure>`;
    });
    return `<section id="${escapeHtml(screen)}"><h2><a href="#${escapeHtml(screen)}">${escapeHtml(screen)}</a></h2><div class="row">${cells.join('')}</div></section>`;
  });
  const summary = variants
    .map((v) => `<li><strong>${escapeHtml(v.name)}</strong>: ${escapeHtml(describe(v))}</li>`)
    .join('');
  const index = screens.map((s) => `<a href="#${escapeHtml(s)}">${escapeHtml(s)}</a>`).join(' ');
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>CoPlanly UI tour</title>
<style>
:root { color-scheme: light dark; --bg: #fafafa; --fg: #1b1b1f; --muted: #5f5f6b; --card: #ffffff; --line: #d9d9e0; }
@media (prefers-color-scheme: dark) { :root { --bg: #131316; --fg: #e4e1e6; --muted: #a09fa8; --card: #1f1f25; --line: #34343c; } }
body { margin: 0; padding: 16px; background: var(--bg); color: var(--fg); font: 14px/1.45 system-ui, sans-serif; }
h1 { font-size: 20px; margin: 0 0 4px; }
.meta, figcaption, .index { color: var(--muted); }
.index { margin: 12px 0 24px; line-height: 1.9; }
.index a { margin-right: 10px; color: inherit; }
section { margin: 0 0 32px; padding-top: 8px; border-top: 1px solid var(--line); }
h2 { font-size: 15px; margin: 8px 0 12px; font-family: ui-monospace, monospace; }
h2 a { color: inherit; text-decoration: none; }
.row { display: grid; grid-template-columns: repeat(${columns}, minmax(0, 1fr)); gap: 12px; max-width: ${columns * 380}px; }
figure { margin: 0; }
img { width: 100%; height: auto; border: 1px solid var(--line); border-radius: 8px; background: var(--card); }
.missing div { aspect-ratio: 9 / 20; display: flex; align-items: center; justify-content: center; padding: 12px;
  border: 1px dashed var(--line); border-radius: 8px; color: var(--muted); font-size: 12px; overflow-wrap: anywhere; }
@media (max-width: 700px) { .row { grid-template-columns: 1fr; } }
</style>
</head>
<body>
<h1>CoPlanly UI tour</h1>
<p class="meta">${source ? `${escapeHtml(source)} · ` : ''}${screens.length} screens · full-device screenshots from an emulator, with realistic data</p>
<ul class="meta">${summary}</ul>
<nav class="index">${index}</nav>
${rows.join('\n')}
</body>
</html>
`;
}

function main(argv) {
  const [dir, ...requested] = argv;
  if (!dir) {
    process.stderr.write('usage: gallery.js <site dir> [variant ...]\n');
    return 2;
  }
  const tourDir = path.join(dir, 'ui-tour');
  const present = fs.existsSync(tourDir)
    ? fs.readdirSync(tourDir).filter((d) => fs.statSync(path.join(tourDir, d)).isDirectory()).sort()
    : [];
  const names = requested.length > 0 ? requested.filter((v) => present.includes(v)) : present;
  const variants = names.map((name) => readVariant(dir, name));
  fs.writeFileSync(path.join(dir, 'index.html'), render(variants, process.env.UI_TOUR_SOURCE || ''));
  const total = variants.reduce((sum, v) => sum + v.shots.size, 0);
  process.stdout.write(`index.html: ${variants.length} variants, ${total} screenshots\n`);
  return 0;
}

if (require.main === module) {
  process.exitCode = main(process.argv.slice(2));
}

module.exports = { render, readVariant, screenOrder, escapeHtml };
