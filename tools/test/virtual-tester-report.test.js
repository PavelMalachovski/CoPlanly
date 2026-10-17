/**
 * Tests for tools/virtual-tester/report.js. Run with `node --test tools/test/*.test.js`; no dependencies.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  readSteps,
  applyKnown,
  summarise,
  renderSummaryMarkdown,
  renderHtml,
} = require('../virtual-tester/report.js');

function step(overrides) {
  return {
    variant: 'light-cs-100',
    scenario: 'Swap',
    method: 'noAnswer',
    id: 'no_answer_history',
    title: 'History says No answer',
    expected: 'No answer in History',
    actual: 'No answer in History',
    verdict: 'passed',
    delivery: null,
    screenshot: 'virtual-tester/light-cs-100/shots/001_Smoke_home.png',
    findings: [],
    startedAtMillis: 1,
    durationMs: 2,
    ...overrides,
  };
}

test('readSteps tolerates a truncated last line', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'vt-'));
  const file = path.join(dir, 'steps.jsonl');
  fs.writeFileSync(file, `${JSON.stringify(step({}))}\n${JSON.stringify(step({ id: 'b' }))}\n{"variant":"li`);
  const steps = readSteps(file);
  assert.equal(steps.length, 2);
  assert.equal(steps[1].id, 'b');
});

test('readSteps of a missing file is empty', () => {
  assert.deepEqual(readSteps(path.join(os.tmpdir(), 'no-such-dir-vt', 'steps.jsonl')), []);
});

test('applyKnown turns a matching failure into known and leaves passes alone', () => {
  const known = [{ step: 'Swap/no_answer_history', reason: 'older build', ref: 'CLAUDE.md item 42' }];
  const [failed, passed, other] = applyKnown(
    [step({ verdict: 'failed' }), step({}), step({ id: 'x', verdict: 'failed' })],
    known,
  );
  assert.equal(failed.verdict, 'known');
  assert.equal(failed.knownReason, 'older build');
  assert.equal(failed.knownRef, 'CLAUDE.md item 42');
  assert.equal(passed.verdict, 'passed');
  assert.equal(other.verdict, 'failed');
});

test('summarise counts verdicts and lists scenarios with no steps', () => {
  const coverage = [
    { area: 'Calendar', scenario: 'Calendar' },
    { area: 'Chat', scenario: 'Chat' },
  ];
  const summary = summarise(
    [
      step({ scenario: 'Calendar', verdict: 'passed', findings: ['money cut off: "1 290 Kč"'] }),
      step({ scenario: 'Calendar', verdict: 'failed' }),
      step({ scenario: 'Calendar', verdict: 'could_not_run' }),
      step({ scenario: 'Calendar', verdict: 'known' }),
    ],
    coverage,
  );
  assert.deepEqual(
    { total: summary.total, passed: summary.passed, failed: summary.failed, couldNotRun: summary.couldNotRun, known: summary.known, findings: summary.findings },
    { total: 4, passed: 1, failed: 1, couldNotRun: 1, known: 1, findings: 1 },
  );
  assert.deepEqual(summary.notCovered, ['Chat']);
});

test('renderSummaryMarkdown lists each failed step with its expected and actual', () => {
  const steps = [step({ verdict: 'failed', actual: 'History was empty' })];
  const md = renderSummaryMarkdown(summarise(steps, []), steps);
  assert.match(md, /\| Swap \| no_answer_history \|/);
  assert.match(md, /No answer in History/);
  assert.match(md, /History was empty/);
  const clean = renderSummaryMarkdown(summarise([step({})], []), [step({})]);
  assert.match(clean, /All steps passed/);
});

test('renderSummaryMarkdown keeps a table cell on one line', () => {
  const steps = [step({ verdict: 'failed', actual: 'line one\nline | two' })];
  const md = renderSummaryMarkdown(summarise(steps, []), steps);
  assert.match(md, /line one line \\\| two/);
});

test('renderHtml escapes text and links screenshots relatively', () => {
  const steps = [step({ actual: '<b>' })];
  const html = renderHtml(steps, summarise(steps, []), 'branch @ abc1234');
  assert.match(html, /&lt;b&gt;/);
  assert.doesNotMatch(html, /<b>/);
  assert.match(html, /src="virtual-tester\/light-cs-100\/shots\/001_Smoke_home\.png"/);
  assert.match(html, /branch @ abc1234/);
});
