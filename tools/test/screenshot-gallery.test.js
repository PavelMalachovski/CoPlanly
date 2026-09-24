/**
 * Tests for tools/screenshot-gallery.js. Run with `node --test tools/test/*.test.js`; no dependencies.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const gallery = require('../screenshot-gallery.js');

test('a baseline alone is unchanged; a diff beside it is changed; a diff alone is new', () => {
  const entries = gallery.group([
    'home_stat_tiles/en_light_fs100_default.png',
    'home_stat_tiles/de_dark_fs150_default.png',
    'home_stat_tiles/de_dark_fs150_default_actual.png',
    'home_stat_tiles/de_dark_fs150_default_compare.png',
    'month_grid/cs_light_fs100_purpleorange_actual.png',
    'month_grid/cs_light_fs100_purpleorange_compare.png',
  ]);
  assert.deepEqual(entries.map((e) => [e.key, e.status]), [
    ['home_stat_tiles/de_dark_fs150_default.png', 'changed'],
    ['home_stat_tiles/en_light_fs100_default.png', 'unchanged'],
    ['month_grid/cs_light_fs100_purpleorange.png', 'new'],
  ]);
  const changed = entries[0];
  assert.equal(changed.baseline, 'home_stat_tiles/de_dark_fs150_default.png');
  assert.equal(changed.compare, 'home_stat_tiles/de_dark_fs150_default_compare.png');
  assert.equal(changed.actual, 'home_stat_tiles/de_dark_fs150_default_actual.png');
  assert.equal(entries[2].baseline, null);
});

test('describe reads the component and the four facets from the key', () => {
  const [entry] = gallery.group(['settings_group/uk_dark_fs150_purpleorange.png']).map(gallery.describe);
  assert.equal(entry.component, 'settings_group');
  assert.equal(entry.locale, 'uk');
  assert.equal(entry.theme, 'dark');
  assert.equal(entry.scale, '1.5x');
  assert.equal(entry.palette, 'purpleorange');
});

test('summary lists what changed and what has no baseline', () => {
  const images = gallery.group([
    'a/en_light_fs100_default.png',
    'a/en_light_fs100_default_compare.png',
    'b/en_light_fs100_default_compare.png',
    'c/en_light_fs100_default.png',
  ]).map(gallery.describe);
  assert.deepEqual(gallery.summarize(images, 'verify'), {
    mode: 'verify',
    total: 3,
    changed: ['a/en_light_fs100_default.png'],
    added: ['b/en_light_fs100_default.png'],
  });
});

test('a changed card shows the diff and links all three images', () => {
  const images = gallery.group([
    'a/en_light_fs100_default.png',
    'a/en_light_fs100_default_actual.png',
    'a/en_light_fs100_default_compare.png',
  ]).map(gallery.describe);
  const html = gallery.render(images, 'verify');
  assert.match(html, /<img loading="lazy" src="a\/en_light_fs100_default_compare\.png"/);
  assert.match(html, /href="a\/en_light_fs100_default\.png">baseline</);
  assert.match(html, /href="a\/en_light_fs100_default_actual\.png">new</);
  assert.match(html, /1 changed, 0 without a baseline/);
  assert.match(html, /data-status="changed"/);
});

test('a record run says nothing was compared', () => {
  const images = gallery.group(['a/en_light_fs100_default.png']).map(gallery.describe);
  assert.match(gallery.render(images, 'record'), /recorded, not compared/);
});

test('listImages walks the tree and ignores what is not a PNG', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'gallery-'));
  fs.mkdirSync(path.join(dir, 'a'));
  fs.writeFileSync(path.join(dir, 'a', 'en_light_fs100_default.png'), '');
  fs.writeFileSync(path.join(dir, 'a', 'en_light_fs100_default_compare.png'), '');
  fs.writeFileSync(path.join(dir, 'index.html'), '');
  assert.deepEqual(gallery.listImages(dir),
      ['a/en_light_fs100_default.png', 'a/en_light_fs100_default_compare.png']);
  fs.rmSync(dir, {recursive: true});
});

test('arguments: directory, mode and summary in any order', () => {
  const a = gallery.parseArgs(['--mode', 'verify', 'shots', '--summary', 'out.json']);
  assert.equal(a.mode, 'verify');
  assert.equal(a.dir, path.resolve('shots'));
  assert.equal(a.summary, path.resolve('out.json'));
  assert.equal(gallery.parseArgs([]).mode, 'record');
});
