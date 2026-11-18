/**
 * Tests for tools/ios/generate-push-strings.js. Run with `node --test tools/test/*.test.js`; no
 * dependencies.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');
const { generate, unescapeCmp, toApple, pluralText, readKeys } = require('../ios/generate-push-strings.js');

const REAL_RES = path.join(__dirname, '..', '..', 'shared-ui', 'src', 'commonMain', 'composeResources');
const SCRIPT = path.join(__dirname, '..', 'ios', 'generate-push-strings.js');

/** A resource directory in a temporary folder: { 'values': '<xml body>', 'values-cs': … }. */
function resDir(files) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'push-strings-'));
  for (const [dir, body] of Object.entries(files)) {
    fs.mkdirSync(path.join(root, dir), { recursive: true });
    fs.writeFileSync(path.join(root, dir, 'push_strings.xml'),
      `<?xml version="1.0" encoding="utf-8"?>\n<!-- a comment's 'quote' -->\n<resources>\n${body}\n</resources>\n`);
  }
  return root;
}

const FIXTURE = {
  values: [
    '<string name="a_title">Don\\u0027t miss it\\nsecond line</string>',
    '<string name="a_named">%1$s added “%2$s”</string>',
    '<string name="a_amp">Mon &amp; Wed\\ttab \\\\ back</string>',
    '<plurals name="a_days">',
    '  <item quantity="one">%1$s gives you %2$d day</item>',
    '  <item quantity="other">%1$s gives you %2$d days</item>',
    '</plurals>',
    '<plurals name="a_children">',
    '  <item quantity="one">%1$s is with you</item>',
    '  <item quantity="other">%1$s are with you</item>',
    '</plurals>',
  ].join('\n'),
  'values-cs': [
    '<string name="a_title">Nenechte si to ujít</string>',
    '<plurals name="a_days">',
    '  <item quantity="one">%1$s vám dává %2$d den</item>',
    '  <item quantity="few">%1$s vám dává %2$d dny</item>',
    '  <item quantity="many">%1$s vám dává %2$d dne</item>',
    '  <item quantity="other">%1$s vám dává %2$d dní</item>',
    '</plurals>',
  ].join('\n'),
  'values-ru': [
    '<plurals name="a_days">',
    '  <item quantity="one">%1$s даёт вам %2$d день</item>',
    '  <item quantity="few">%1$s даёт вам %2$d дня</item>',
    '  <item quantity="many">%1$s даёт вам %2$d дней</item>',
    '  <item quantity="other">%1$s даёт вам %2$d дня</item>',
    '</plurals>',
  ].join('\n'),
};

const KEYS = ['a_title', 'a_named', 'a_amp', 'a_days', 'a_children'];

test('an English string is unescaped as Compose Multiplatform does and written as a .strings entry', () => {
  const out = generate(resDir(FIXTURE), KEYS);
  const en = out.get('en').strings;
  // The apostrophe arrives as ' and the line break as \n; the .strings file escapes the line
  // break again for its own syntax.
  assert.match(en, /^"a_title" = "Don't miss it\\nsecond line";$/m);
  assert.match(en, /^"a_amp" = "Mon & Wed\\ttab \\\\ back";$/m);
});

test('an argument %1$s is written %1$@ and %2$d stays %2$d', () => {
  const en = generate(resDir(FIXTURE), KEYS).get('en').strings;
  assert.match(en, /^"a_named" = "%1\$@ added “%2\$@”";$/m);
  assert.equal(toApple('%1$s has %2$d'), '%1$@ has %2$d');
});

test('a Czech plural has one few many other, the count passed first and each argument shifted by one', () => {
  const cs = generate(resDir(FIXTURE), KEYS).get('cs').stringsdict;
  const entry = cs.slice(cs.indexOf('<key>a_days</key>'));
  assert.match(entry, /<key>NSStringLocalizedFormatKey<\/key>\s*<string>%1\$#@value@<\/string>/);
  assert.match(entry, /<key>NSStringFormatSpecTypeKey<\/key>\s*<string>NSStringPluralRuleType<\/string>/);
  assert.match(entry, /<key>NSStringFormatValueTypeKey<\/key>\s*<string>d<\/string>/);
  assert.match(entry, /<key>one<\/key>\s*<string>%2\$@ vám dává %3\$d den<\/string>/);
  assert.match(entry, /<key>few<\/key>\s*<string>%2\$@ vám dává %3\$d dny<\/string>/);
  assert.match(entry, /<key>many<\/key>\s*<string>%2\$@ vám dává %3\$d dne<\/string>/);
  assert.match(entry, /<key>other<\/key>\s*<string>%2\$@ vám dává %3\$d dní<\/string>/);
});

test('a Russian plural keeps its own quantities', () => {
  const ru = generate(resDir(FIXTURE), KEYS).get('ru').stringsdict;
  assert.match(ru, /<key>one<\/key>\s*<string>%2\$@ даёт вам %3\$d день<\/string>/);
  assert.match(ru, /<key>many<\/key>\s*<string>%2\$@ даёт вам %3\$d дней<\/string>/);
  assert.doesNotMatch(ru, /<key>two<\/key>/);
});

test('a plural that prints no count still selects by it', () => {
  const en = generate(resDir(FIXTURE), KEYS).get('en').stringsdict;
  const entry = en.slice(en.indexOf('<key>a_children</key>'));
  assert.match(entry, /<string>%1\$#@value@<\/string>/);
  assert.match(entry, /<key>one<\/key>\s*<string>%2\$@ is with you<\/string>/);
});

test('a key a locale does not translate falls back to the base text, as Android does', () => {
  const out = generate(resDir(FIXTURE), KEYS);
  // Czech has a_title but not a_named; Russian has neither, and no a_children plural.
  assert.match(out.get('cs').strings, /^"a_title" = "Nenechte si to ujít";$/m);
  assert.match(out.get('cs').strings, /^"a_named" = "%1\$@ added “%2\$@”";$/m);
  assert.match(out.get('ru').strings, /^"a_title" = "Don't miss it\\nsecond line";$/m);
  assert.match(out.get('ru').stringsdict, /<key>a_children<\/key>[\s\S]*%2\$@ are with you/);
  assert.deepEqual([...out.keys()].sort(), ['cs', 'en', 'ru']);
});

test('an unknown key fails with its name', () => {
  assert.throws(() => generate(resDir(FIXTURE), ['a_title', 'no_such_key']), /no_such_key/);
});

test('the keys file takes one key per line and ignores blank lines and comments', () => {
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'push-keys-')), 'keys.txt');
  fs.writeFileSync(file, '# a comment\n\na_title\n  a_days  \n');
  assert.deepEqual(readKeys(file), ['a_title', 'a_days']);
});

test('Compose Multiplatform unescapes \\n \\t \\uXXXX and \\\\ and nothing else', () => {
  assert.equal(unescapeCmp('a\\nb\\tc\\u00A0d\\\\e'), 'a\nb\tc d\\e');
  assert.equal(unescapeCmp('keep \\@ as it is'), 'keep \\@ as it is');
});

test('the real resources word a Czech and a Russian push plural, and the command writes the files', () => {
  const keys = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'push-keys-')), 'keys.txt');
  fs.writeFileSync(keys, 'push_day_swap_group_offered_body\npush_event_created_title\n');
  const out = fs.mkdtempSync(path.join(os.tmpdir(), 'push-out-'));
  execFileSync(process.execPath, [SCRIPT, out, '--keys', keys, '--res', REAL_RES]);
  for (const lang of ['en', 'cs', 'de', 'ru', 'uk']) {
    assert.ok(fs.existsSync(path.join(out, `${lang}.lproj`, 'PushStrings.strings')), lang);
    assert.ok(fs.existsSync(path.join(out, `${lang}.lproj`, 'PushStrings.stringsdict')), lang);
  }
  const cs = fs.readFileSync(path.join(out, 'cs.lproj', 'PushStrings.stringsdict'), 'utf8');
  assert.match(cs, /<key>few<\/key>\s*<string>Druhý rodič navrhuje vyměnit %2\$d dny<\/string>/);
  const ru = fs.readFileSync(path.join(out, 'ru.lproj', 'PushStrings.stringsdict'), 'utf8');
  assert.match(ru, /<key>many<\/key>\s*<string>Второй родитель предлагает обменять %2\$d дней<\/string>/);
  const en = fs.readFileSync(path.join(out, 'en.lproj', 'PushStrings.strings'), 'utf8');
  assert.match(en, /^"push_event_created_title" = "New event";$/m);
});

test('a literal percent sign in a plural is doubled, because NSString formats the entry', () => {
  assert.equal(pluralText('%1$d % of %2$s'), '%2$d %% of %3$@');
});
