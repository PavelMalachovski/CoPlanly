/**
 * Tests for tools/upgrade/instrument-results.js — the verdict of the CI `upgrade` job's two
 * `am instrument` runs. Run with `node --test tools/test/*.test.js`; no dependencies.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { parse, evaluate, toJUnit } = require('../upgrade/instrument-results.js');

const CLASS = 'com.coparently.app.upgrade.UpgradeVerifyTest';
const TEST = 'verify_theNewBuildOpensWhatTheBaseBuildWrote';

/** One test's start and end, in `am instrument -r` form. */
function block(code, extra = []) {
  const bundle = (c) => [
    `INSTRUMENTATION_STATUS: class=${CLASS}`,
    'INSTRUMENTATION_STATUS: current=1',
    'INSTRUMENTATION_STATUS: id=AndroidJUnitRunner',
    'INSTRUMENTATION_STATUS: numtests=1',
    ...(c === 1 ? [] : extra),
    'INSTRUMENTATION_STATUS: stream=',
    `${CLASS}:`,
    `INSTRUMENTATION_STATUS: test=${TEST}`,
    `INSTRUMENTATION_STATUS_CODE: ${c}`,
  ];
  return [...bundle(1), ...bundle(code)];
}

const FINISHED_OK = ['INSTRUMENTATION_RESULT: stream=', '', 'Time: 1.2', '', 'OK (1 test)', '', '', 'INSTRUMENTATION_CODE: -1'];

const verdictOf = (lines, expect = CLASS) => evaluate(parse(lines.join('\n')), expect);

test('a passed test is a pass', () => {
  const v = verdictOf([...block(0), ...FINISHED_OK]);
  assert.equal(v.ok, true, v.problems.join('; '));
  assert.deepEqual(v.cases.map((c) => c.outcome), ['passed']);
});

test('a skipped test is a failure, although am instrument prints "OK (1 test)"', () => {
  const v = verdictOf([
    ...block(-4, ['INSTRUMENTATION_STATUS: stack=org.junit.AssumptionViolatedException: No -e coplanlyUpgradePhase']),
    ...FINISHED_OK,
  ]);
  assert.equal(v.ok, false);
  assert.deepEqual(v.cases.map((c) => c.outcome), ['skipped']);
  // …and the check run must not show it as a harmless skip either.
  assert.match(toJUnit('Upgrade — seed', v), /failures="1" errors="0" skipped="0"[\s\S]*<failure message="skipped, which fails/);
});

test('a failed assertion fails, and its multi-line stack is kept', () => {
  const v = verdictOf([
    ...block(-2, [
      'INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: The Google refresh token was lost',
      '\tat org.junit.Assert.fail(Assert.java:89)',
    ]),
    'INSTRUMENTATION_RESULT: stream=',
    'FAILURES!!!',
    'Tests run: 1,  Failures: 1',
    'INSTRUMENTATION_CODE: -1',
  ]);
  assert.equal(v.ok, false);
  assert.match(v.cases[0].message, /refresh token was lost\n\tat org\.junit/);
});

test('a crashed process fails, and the test it was running is an error', () => {
  const started = block(0).slice(0, 8);
  const v = verdictOf([...started, 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.', 'INSTRUMENTATION_CODE: 0']);
  assert.equal(v.ok, false);
  assert.deepEqual(v.cases.map((c) => c.outcome), ['error']);
  assert.ok(v.problems.some((p) => p.includes('Process crashed')));
});

test('a missing instrumentation fails with no test at all', () => {
  const v = verdictOf(['Error: Unable to find instrumentation info for: ComponentInfo{x/y}']);
  assert.equal(v.ok, false);
  assert.ok(v.problems.includes('No test ran'));
  const xml = toJUnit('Upgrade — verify', v);
  assert.match(xml, /tests="1" failures="0" errors="1" skipped="0"/);
});

test('a test of another class does not satisfy the expected class', () => {
  const v = verdictOf([...block(0), ...FINISHED_OK], 'com.coparently.app.upgrade.UpgradeSeedTest');
  assert.equal(v.ok, false);
});

test('empty output is a failure', () => {
  assert.equal(verdictOf([]).ok, false);
});

test('JUnit XML counts outcomes and escapes the message', () => {
  const v = verdictOf([
    ...block(-2, ['INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: expected:<a & "b"> but was:<null>']),
    'INSTRUMENTATION_CODE: -1',
  ]);
  const xml = toJUnit('Upgrade — verify', v);
  assert.match(xml, /tests="1" failures="1" errors="0" skipped="0"/);
  assert.match(xml, /expected:&lt;a &amp; &quot;b&quot;&gt;/);
  assert.match(xml, new RegExp(`classname="${CLASS.replace(/\./g, '\\.')}" name="${TEST}"`));
});
