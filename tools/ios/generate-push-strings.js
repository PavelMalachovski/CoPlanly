#!/usr/bin/env node
/**
 * Writes the iPhone notification extension's strings from the one source of every string, the
 * Compose Multiplatform resources in `shared-ui/src/commonMain/composeResources/values*` (iPhone
 * phase 4 part C, Task 1; design §7.1).
 *
 *   node tools/ios/generate-push-strings.js <out-dir> --keys <file> [--res <dir>]
 *
 * For each language (`values` is English, `en`; `values-cs` is `cs`, …) it writes
 * `<out-dir>/<lang>.lproj/PushStrings.strings` and `PushStrings.stringsdict`, holding exactly the
 * keys the keys file lists (`push-wording/push-keys.txt`: one key per line, `#` comments and blank
 * lines ignored). A key a language does not translate takes the base text, as Android's resources
 * fall back to `values/`. A key that is neither a string nor a plural in `values/` fails the run,
 * naming it.
 *
 * - **A string** is unescaped as Compose Multiplatform 1.9.3 does (`\n`, `\t`, `\uXXXX`, `\\`, and
 *   the XML entities), its `%<n>$s` written `%<n>$@` and its `%<n>$d` kept. `BundleWordingStrings`
 *   reads it as text and formats it with `formatArgs`, never with `NSString`'s formatter.
 * - **A plural** is a `.stringsdict` entry of `NSStringPluralRuleType` with the XML's quantities.
 *   Android selects the quantity by a count passed beside the arguments (`getQuantityString(id,
 *   count, args)`), and a text need not print the count at all ("%1$s are with you"), so the entry's
 *   format key is `%1$#@value@` over a count passed **first**, with `NSStringFormatValueTypeKey`
 *   `d`, and every argument in a quantity's text is shifted by one (`%1$s` → `%2$@`, `%2$d` →
 *   `%3$d`), and a literal `%` in a quantity's text is written `%%`: a valid entry Foundation's own
 *   `localizedStringWithFormat(entry, count, args…)` would format. `BundleWordingStrings.plural` does
 *   not call it (Kotlin/Native hands the localized `NSString` back as a plain `String`, which loses
 *   the entry): it reads the file as a property list, picks the quantity by Android's rule
 *   (`pluralQuantity`) and reverses this encoding exactly — arguments shifted back, `%%` to `%` —
 *   before formatting with `formatArgs`. A `zero`
 *   quantity is left out: Apple applies it to 0 in every language, Android only where the language's
 *   plural rules have it, and none of the five languages the app ships does.
 */
'use strict';

const fs = require('fs');
const path = require('path');

const DEFAULT_RES = path.join(__dirname, '..', '..', 'shared-ui', 'src', 'commonMain', 'composeResources');
const BASE_LANGUAGE = 'en';
const QUANTITIES = ['zero', 'one', 'two', 'few', 'many', 'other'];

const stripComments = (xml) => xml.replace(/<!--[\s\S]*?-->/g, '');

/** The XML entities a resource text may carry. */
function decodeEntities(text) {
  return text.replace(/&(amp|lt|gt|quot|apos|#x[0-9a-fA-F]+|#\d+);/g, (_, e) => {
    switch (e) {
      case 'amp': return '&';
      case 'lt': return '<';
      case 'gt': return '>';
      case 'quot': return '"';
      case 'apos': return '\'';
      default: return String.fromCodePoint(e[1] === 'x' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10));
    }
  });
}

/**
 * What Compose Multiplatform 1.9.3 makes of a resource text: `\n`, `\t`, `\uXXXX` and `\\` are
 * unescaped, everything else (whitespace included) is kept as written. Check 7 of
 * `check-invariants.js` holds every text to a spelling aapt2 reads alike.
 */
function unescapeCmp(text) {
  return text.replace(/\\(n|t|\\|u[0-9a-fA-F]{4})/g, (_, e) => {
    if (e === 'n') return '\n';
    if (e === 't') return '\t';
    if (e === '\\') return '\\';
    return String.fromCharCode(parseInt(e.slice(1), 16));
  });
}

/** `%<n>$s` written `%<n>$@`; `%<n>$d` kept. */
function toApple(text) {
  return text.replace(/%(\d+)\$s/g, '%$1$@');
}

/** A plural quantity's text for `NSString`'s formatter: arguments shifted by one, `%` doubled. */
function pluralText(text) {
  return text.replace(/%(?:(\d+)\$([sd]))?/g, (whole, n, conv) => {
    if (!n) return '%%';
    return `%${Number(n) + 1}$${conv === 's' ? '@' : 'd'}`;
  });
}

/** `values` → `en`, `values-cs` → `cs`, `values-pt-rBR` → `pt-BR`; null for any other folder. */
function languageOf(dir) {
  if (dir === 'values') return BASE_LANGUAGE;
  const m = /^values-([a-z]{2,3})(?:-r([A-Z]{2}))?$/.exec(dir);
  if (!m) return null;
  return m[2] ? `${m[1]}-${m[2]}` : m[1];
}

/** Every string and plural of one `values*` folder: `{strings: Map, plurals: Map<name, Map<q, text>>}`. */
function readFolder(folder) {
  const strings = new Map();
  const plurals = new Map();
  for (const file of fs.readdirSync(folder).filter((f) => f.endsWith('.xml')).sort()) {
    const xml = stripComments(fs.readFileSync(path.join(folder, file), 'utf8'));
    for (const m of xml.matchAll(/<string\s[^>]*?name="([^"]+)"[^>]*?(?:\/>|>([\s\S]*?)<\/string>)/g)) {
      strings.set(m[1], unescapeCmp(decodeEntities(m[2] || '')));
    }
    for (const m of xml.matchAll(/<plurals\s[^>]*?name="([^"]+)"[^>]*>([\s\S]*?)<\/plurals>/g)) {
      const items = new Map();
      for (const item of m[2].matchAll(/<item\s[^>]*?quantity="([a-z]+)"[^>]*?(?:\/>|>([\s\S]*?)<\/item>)/g)) {
        items.set(item[1], unescapeCmp(decodeEntities(item[2] || '')));
      }
      plurals.set(m[1], items);
    }
  }
  return { strings, plurals };
}

/** A `.strings` value: quoted, with `"`, `\`, line breaks and tabs escaped. */
function stringsLiteral(text) {
  return `"${text.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n').replace(/\t/g, '\\t')}"`;
}

/** Text inside a property list's `<string>`. */
function plistText(text) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function stringsFile(entries) {
  const lines = ['/* Generated by tools/ios/generate-push-strings.js from shared-ui\'s resources. Do not edit. */'];
  for (const [key, text] of entries) lines.push(`${stringsLiteral(key)} = ${stringsLiteral(toApple(text))};`);
  return `${lines.join('\n')}\n`;
}

function stringsdictFile(entries) {
  const out = [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">',
    '<!-- Generated by tools/ios/generate-push-strings.js from shared-ui\'s resources. Do not edit. -->',
    '<plist version="1.0">',
    '<dict>',
  ];
  for (const [key, items] of entries) {
    out.push(`  <key>${plistText(key)}</key>`, '  <dict>',
      '    <key>NSStringLocalizedFormatKey</key>', '    <string>%1$#@value@</string>',
      '    <key>value</key>', '    <dict>',
      '      <key>NSStringFormatSpecTypeKey</key>', '      <string>NSStringPluralRuleType</string>',
      '      <key>NSStringFormatValueTypeKey</key>', '      <string>d</string>');
    for (const q of QUANTITIES) {
      if (q === 'zero' || !items.has(q)) continue;
      out.push(`      <key>${q}</key>`, `      <string>${plistText(pluralText(items.get(q)))}</string>`);
    }
    out.push('    </dict>', '  </dict>');
  }
  out.push('</dict>', '</plist>');
  return `${out.join('\n')}\n`;
}

/**
 * The files for every language under [resRoot], for [keys].
 *
 * @return {Map<string, {strings: string, stringsdict: string}>} by language
 */
function generate(resRoot, keys) {
  const folders = fs.readdirSync(resRoot)
    .filter((d) => languageOf(d) && fs.statSync(path.join(resRoot, d)).isDirectory())
    .sort();
  if (!folders.includes('values')) throw new Error(`no base values/ folder in ${resRoot}`);
  const base = readFolder(path.join(resRoot, 'values'));
  const unknown = keys.filter((k) => !base.strings.has(k) && !base.plurals.has(k));
  if (unknown.length) throw new Error(`push-keys: not a string or a plural in values/: ${unknown.join(', ')}`);

  const result = new Map();
  for (const dir of folders) {
    const own = dir === 'values' ? base : readFolder(path.join(resRoot, dir));
    const strings = [];
    const plurals = [];
    for (const key of keys) {
      if (base.plurals.has(key)) plurals.push([key, own.plurals.get(key) || base.plurals.get(key)]);
      else strings.push([key, own.strings.has(key) ? own.strings.get(key) : base.strings.get(key)]);
    }
    result.set(languageOf(dir), { strings: stringsFile(strings), stringsdict: stringsdictFile(plurals) });
  }
  return result;
}

/** The keys a keys file lists: one per line, `#` comments and blank lines ignored. */
function readKeys(file) {
  return fs.readFileSync(file, 'utf8').split('\n')
    .map((line) => line.replace(/#.*/, '').trim())
    .filter(Boolean);
}

function main(argv) {
  const args = argv.slice(2);
  const option = (name) => {
    const i = args.indexOf(name);
    if (i < 0) return null;
    const value = args[i + 1];
    args.splice(i, 2);
    return value;
  };
  const keysFile = option('--keys');
  const res = option('--res') || DEFAULT_RES;
  const outDir = args[0];
  if (!outDir || !keysFile) {
    process.stderr.write('usage: generate-push-strings.js <out-dir> --keys <file> [--res <dir>]\n');
    process.exit(2);
  }
  const files = generate(res, readKeys(keysFile));
  for (const [lang, { strings, stringsdict }] of files) {
    const dir = path.join(outDir, `${lang}.lproj`);
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, 'PushStrings.strings'), strings);
    fs.writeFileSync(path.join(dir, 'PushStrings.stringsdict'), stringsdict);
  }
  process.stderr.write(`push strings: ${files.size} languages written to ${outDir}\n`);
}

if (require.main === module) {
  try {
    main(process.argv);
  } catch (e) {
    process.stderr.write(`${e.message}\n`);
    process.exit(1);
  }
}

module.exports = { generate, readKeys, unescapeCmp, toApple, pluralText, languageOf };
