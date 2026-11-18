/**
 * `push-wording/push-keys.txt` is the list of every resource key a notification is worded with
 * (iPhone phase 4 part C, Task 2): the iPhone's strings are generated for exactly these keys, and on
 * Android the release build's resource shrinker keeps exactly these (`card_push_keep.xml`), because
 * the module reads them by name. A key the wording starts to read and the list misses would be
 * missing from the iPhone's strings and shrunk away on Android; a key the list keeps after the
 * wording stopped reading it is dead weight. The Today widget's `TodayWidgetText` (iPhone phase 4
 * part D) reads its keys the same way and is held to the list too. Run with `node --test tools/test/*.test.js`.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const { readKeys } = require('../ios/generate-push-strings.js');

const ROOT = path.join(__dirname, '..', '..');
const KEYS_FILE = path.join(ROOT, 'push-wording', 'push-keys.txt');
const WORDING_DIR = path.join(ROOT, 'push-wording', 'src', 'commonMain', 'kotlin', 'com', 'coparently', 'app', 'pushwording');
const WIDGET_DIR = path.join(ROOT, 'shared-ui', 'src', 'commonMain', 'kotlin', 'com', 'coparently', 'app', 'presentation', 'widget');
/** The files that read keys by name: the three wordings, and the Today widget's (iPhone phase 4 part D). */
const WORDING_FILES = [
  path.join(WORDING_DIR, 'PushWording.kt'),
  path.join(WORDING_DIR, 'CardWording.kt'),
  path.join(WORDING_DIR, 'ReminderWording.kt'),
  path.join(WIDGET_DIR, 'TodayWidgetText.kt'),
];
const VALUES_DIR = path.join(ROOT, 'shared-ui', 'src', 'commonMain', 'composeResources', 'values');
const KEEP_FILE = path.join(ROOT, 'app', 'src', 'main', 'res', 'raw', 'card_push_keep.xml');

/** Kotlin source without its comments: a key named in a comment is not a key the code reads. */
function withoutComments(source) {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
}

/** `{ name: 'string' | 'plurals' }` for every string and plural `values/` defines. */
function definedResources() {
  const defined = new Map();
  for (const file of fs.readdirSync(VALUES_DIR).filter((f) => f.endsWith('.xml'))) {
    const xml = fs.readFileSync(path.join(VALUES_DIR, file), 'utf8').replace(/<!--[\s\S]*?-->/g, '');
    for (const m of xml.matchAll(/<(string|plurals)\s[^>]*name="([^"]+)"/g)) defined.set(m[2], m[1]);
  }
  return defined;
}

/** Every string literal of the wording files that names a resource in `values/`. */
function referencedKeys(defined) {
  const keys = new Set();
  for (const file of WORDING_FILES) {
    const source = withoutComments(fs.readFileSync(file, 'utf8'));
    for (const m of source.matchAll(/"([a-z][a-z0-9_]*)"/g)) if (defined.has(m[1])) keys.add(m[1]);
  }
  return keys;
}

/** The entries of the keep file's `tools:keep`, as `@string/name`, `@plurals/name` or a `*` pattern. */
function keptEntries() {
  const xml = fs.readFileSync(KEEP_FILE, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
  const m = xml.match(/tools:keep="([^"]*)"/);
  assert.ok(m, 'card_push_keep.xml has a tools:keep attribute');
  return m[1].split(',').map((entry) => entry.trim()).filter(Boolean);
}

/** Whether [entries] keep the resource `@<type>/<name>`, by name or by a `*` pattern. */
function isKept(entries, type, name) {
  return entries.some((entry) => {
    const [kind, pattern] = entry.replace(/^@/, '').split('/');
    if (kind !== type) return false;
    const re = new RegExp(`^${pattern.replace(/[.+?^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*')}$`);
    return re.test(name);
  });
}

test('push-keys.txt lists exactly the keys PushWording, CardWording, ReminderWording and TodayWidgetText name', () => {
  const defined = definedResources();
  const listed = new Set(readKeys(KEYS_FILE));
  const referenced = referencedKeys(defined);
  const missing = [...referenced].filter((k) => !listed.has(k)).sort();
  const unused = [...listed].filter((k) => !referenced.has(k)).sort();
  assert.deepEqual(missing, [], `keys the wording names that push-keys.txt misses: ${missing.join(', ')}`);
  assert.deepEqual(unused, [], `keys push-keys.txt lists that the wording no longer names: ${unused.join(', ')}`);
});

test('every key in push-keys.txt is a string or a plural of values/', () => {
  const defined = definedResources();
  const unknown = readKeys(KEYS_FILE).filter((k) => !defined.has(k));
  assert.deepEqual(unknown, []);
});

test('card_push_keep.xml keeps every key in push-keys.txt from the resource shrinker', () => {
  const defined = definedResources();
  const entries = keptEntries();
  const shrunk = readKeys(KEYS_FILE).filter((k) => !isKept(entries, defined.get(k), k));
  assert.deepEqual(shrunk, [], `keys the release build would shrink away: ${shrunk.join(', ')}`);
});

test('a plural is kept as a plural, not as a string', () => {
  const entries = ['@string/push_*'];
  assert.equal(isKept(entries, 'plurals', 'push_day_swap_gives_title'), false);
  assert.equal(isKept(['@plurals/push_day_swap_gives_title'], 'plurals', 'push_day_swap_gives_title'), true);
  assert.equal(isKept(['@string/activity_*'], 'string', 'activity_event_created'), true);
});
