'use strict';

// node --test tools/test/*.test.js
//
// check-invariants.js check 7: a resource text that aapt2 (with String.format) and Compose
// Multiplatform 1.9.3's resource plugin (with its own formatter) would read differently is refused,
// with the spelling both read alike.

const test = require('node:test');
const assert = require('node:assert');
const { stringTextProblems } = require('../check-invariants');

const ok = (text) => assert.deepStrictEqual(stringTextProblems(text), [], text);
const refused = (text, pattern) => {
  const problems = stringTextProblems(text);
  assert.ok(problems.some((p) => pattern.test(p)), `${text}: ${problems.join('; ') || 'no problem found'}`);
};

test('the escapes both unescape alike pass', () => {
  ok('Line one\\nline two\\ttab');
  ok('%1$d\\u00A0expenses');
  ok('a back\\\\slash');
  ok('It\\u0027s \\u0022quoted\\u0022');
  ok('Mon &amp; Wed');
  ok('Úterý → středa, «так»');
});

test('an apostrophe or a quote escaped with a backslash is refused, naming the spelling to use', () => {
  refused("Don\\'t", /\\u0027/);
  refused('tap \\"Request\\"', /\\u0022/);
  refused('\\@home', /keeps with its backslash/);
});

test('an unescaped quote is refused: aapt2 drops it', () => {
  refused('"System default" follows', /unescaped "/);
  refused('a back\\\\"quote', /unescaped "/);
});

test('an unescaped apostrophe is refused: aapt2 refuses it', () => {
  refused("Don't", /unescaped '/);
  refused("a back\\\\'s", /unescaped '/);
});

test('whitespace aapt2 would collapse or trim is refused', () => {
  refused('two  spaces', /whitespace/);
  refused('a\nnew line', /whitespace/);
  refused(' leading', /whitespace/);
  refused('trailing ', /whitespace/);
});

test('a leading @ or ? is refused', () => {
  refused('@string/x', /reference/);
  refused('?attr', /reference/);
});

test('only %<n>$s and %<n>$d are format arguments both replace', () => {
  ok('%1$s pays %2$d');
  refused('%s pays', /%s/);
  refused('%d days', /%d/);
  refused('%1$.1f km', /%1\$\.1f/);
  refused('%1$,d', /%1\$,d/);
});

test('%% beside format arguments is refused in every key, and the sign as an argument passes', () => {
  ok('100 %% sure');
  ok('%');
  refused('%1$s pays %2$d %%', /%% beside format arguments/);
  ok('%1$s pays %2$d %3$s');
});

test('markup and quote entities are refused until the two parsers are compared on them', () => {
  refused('<b>bold</b>', /markup/);
  refused('<![CDATA[x]]>', /markup/);
  refused('it&apos;s', /entity/);
  refused('&quot;x&quot;', /entity/);
});
