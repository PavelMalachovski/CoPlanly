'use strict';

// node --test tools/test/*.test.js
//
// check-invariants.js check 6: the Google Calendar scope the app requests is the one the privacy
// policy and the home page name, and the only one they name.

const test = require('node:test');
const assert = require('node:assert');
const { googleScopeProblems } = require('../check-invariants');

const SOURCE = `
object GoogleCalendarAccess {
    const val SCOPE: String = "https://www.googleapis.com/auth/calendar.events.owned.readonly"
}
`;

const policy = (scope) => `CoPlanly asks Google for the \`${scope}\` permission and nothing else.`;

test('holds when every document names the scope the app asks for', () => {
  const docs = {
    'docs/legal/PRIVACY-POLICY.md': policy('calendar.events.owned.readonly'),
    'web/index.html': '<p>to see the events on the calendars you own (calendar.events.owned.readonly).</p>',
  };
  assert.deepStrictEqual(googleScopeProblems(SOURCE, docs), []);
});

test('fails a document that names a scope the app no longer asks for', () => {
  const problems = googleScopeProblems(SOURCE, {
    'docs/legal/PRIVACY-POLICY.md': policy('calendar.events.readonly'),
  });
  assert.strictEqual(problems.length, 1);
  assert.match(problems[0], /PRIVACY-POLICY\.md/);
  assert.match(problems[0], /calendar\.events\.readonly/);
  assert.match(problems[0], /calendar\.events\.owned\.readonly/);
});

test('fails a document that names no scope at all', () => {
  const problems = googleScopeProblems(SOURCE, { 'web/index.html': '<p>We read your calendar.</p>' });
  assert.strictEqual(problems.length, 1);
  assert.match(problems[0], /web\/index\.html/);
});

test('fails a document that names a second scope beside the right one', () => {
  const problems = googleScopeProblems(SOURCE, {
    'web/index.html': 'calendar.events.owned.readonly, and later calendar.events for writing',
  });
  assert.strictEqual(problems.length, 1);
  assert.match(problems[0], /calendar\.events\b/);
});

test('the full calendar scope fails every document that promises read-only access', () => {
  const full = 'const val SCOPE: String = "https://www.googleapis.com/auth/calendar"';
  const problems = googleScopeProblems(full, { 'docs/legal/PRIVACY-POLICY.md': policy('calendar.events.owned.readonly') });
  assert.strictEqual(problems.length, 1);
});

test('a host name is not a scope', () => {
  const docs = {
    'web/index.html': 'calendar.events.owned.readonly — open calendar.google.com to remove access',
  };
  assert.deepStrictEqual(googleScopeProblems(SOURCE, docs), []);
});

test('a source without the scope literal is a problem, not a pass', () => {
  const problems = googleScopeProblems('object GoogleCalendarAccess', { 'web/index.html': '' });
  assert.strictEqual(problems.length, 1);
  assert.match(problems[0], /SCOPE/);
});
