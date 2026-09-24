/**
 * Tests for tools/ci-report.js. Run with `node --test tools/test/*.test.js`; no dependencies.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const report = require('../ci-report.js');

// The shape Gradle's test task writes: one suite per class, a failure with a message attribute
// and the stack trace as the body.
const GRADLE_XML = `<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.coparently.app.FooTest" tests="3" skipped="1" failures="1" errors="0">
  <testcase name="passes" classname="com.coparently.app.FooTest" time="0.01"/>
  <testcase name="fails &amp; says why" classname="com.coparently.app.FooTest" time="0.02">
    <failure message="expected:&lt;1&gt; but was:&lt;2&gt;" type="java.lang.AssertionError">java.lang.AssertionError: expected:&lt;1&gt; but was:&lt;2&gt;
	at com.coparently.app.FooTest.fails(FooTest.kt:12)</failure>
  </testcase>
  <testcase name="ignored" classname="com.coparently.app.FooTest" time="0">
    <skipped/>
  </testcase>
  <system-out><![CDATA[]]></system-out>
</testsuite>`;

// The shape mocha's xunit reporter writes: no message attribute, the message in the body.
const MOCHA_XML = `<testsuite name="functions" tests="2" failures="1" errors="1" skipped="0">
<testcase classname="acceptPairingInvitation" name="refuses a friend code" time="0.003"><failure>expected 'ok' to equal 'refused'
AssertionError: expected 'ok' to equal 'refused'
    at Context.&lt;anonymous&gt; (test/pairing.test.js:40:12)</failure></testcase>
<testcase classname="x" name="y" time="0"/>
</testsuite>`;

const KOVER_XML = `<?xml version="1.0" ?><report name="Kover Gradle Plugin">
<package name="com/coparently/app"><class name="A"><counter type="LINE" missed="1" covered="1"/></class>
<counter type="LINE" missed="1" covered="1"/></package>
<counter type="INSTRUCTION" missed="100" covered="300"/>
<counter type="LINE" missed="250" covered="750"/>
<counter type="BRANCH" missed="5" covered="5"/>
</report>`;

test('JUnit from Gradle: counts from test cases, failure message decoded', () => {
  const r = report.parseJUnit(GRADLE_XML);
  assert.equal(r.tests, 3);
  assert.equal(r.failed, 1);
  assert.equal(r.skipped, 1);
  assert.deepEqual(r.failures, [{
    name: 'com.coparently.app.FooTest > fails & says why',
    message: 'expected:<1> but was:<2>',
  }]);
});

test('JUnit from mocha: the message is the first line of the body', () => {
  const r = report.parseJUnit(MOCHA_XML);
  assert.equal(r.tests, 2);
  assert.equal(r.failed, 1);
  assert.equal(r.failures[0].message, 'expected \'ok\' to equal \'refused\'');
});

test('coverage is the report-level LINE counter, not a package\'s', () => {
  assert.deepEqual(report.parseCoverage(KOVER_XML), {covered: 750, missed: 250, percent: 75});
  assert.equal(report.parseCoverage('<report></report>'), null);
});

test('suite labels', () => {
  assert.equal(report.suiteLabel('junit-unit'), 'Unit (JVM)');
  assert.equal(report.suiteLabel('junit-instrumented-api30-default'), 'Instrumented, API 30');
  assert.equal(report.suiteLabel('junit-instrumented-api35-google_apis_ps16k'),
      'Instrumented, API 35 (google_apis_ps16k)');
});

test('collect reads junit-* and coverage-report directories', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ci-report-'));
  fs.mkdirSync(path.join(dir, 'junit-unit', 'testDebugUnitTest'), {recursive: true});
  fs.writeFileSync(path.join(dir, 'junit-unit', 'testDebugUnitTest', 'TEST-Foo.xml'), GRADLE_XML);
  fs.mkdirSync(path.join(dir, 'junit-functions'));
  fs.writeFileSync(path.join(dir, 'junit-functions', 'functions.xml'), MOCHA_XML);
  fs.mkdirSync(path.join(dir, 'coverage-report'));
  fs.writeFileSync(path.join(dir, 'coverage-report', 'reportDebug.xml'), KOVER_XML);
  const {suites, coverage} = report.collect(dir);
  assert.deepEqual(suites.map((s) => [s.label, s.tests, s.failed]),
      [['Cloud Functions', 2, 1], ['Unit (JVM)', 3, 1]]);
  assert.equal(coverage.percent, 75);
  fs.rmSync(dir, {recursive: true});
});

test('render: verdict, tables, failures, the APK caveat and machine-readable JSON', () => {
  const md = report.render({
    suites: [{label: 'Unit (JVM)', tests: 3, failed: 1, skipped: 1,
      failures: [{name: 'A > b', message: 'boom | pipe'}]}],
    coverage: {covered: 750, missed: 250, percent: 75},
    jobs: [{name: 'Android — build & unit tests', status: 'completed', conclusion: 'failure', html_url: 'https://j/1'},
      {name: 'Cloud Functions', status: 'completed', conclusion: 'success'}],
    artifacts: [{name: 'coplanly-debug-apk', size_in_bytes: 20 * 1024 * 1024, url: 'https://a/1'},
      {name: 'emulator-video-api30-default', size_in_bytes: 5000000, url: 'https://a/2'},
      {name: 'r8-mapping', size_in_bytes: 1000, url: 'https://a/3'}],
    plan: '### Manual checks this PR needs\n\n- [ ] §5.1 chat\n',
    sha: 'abcdef1234567',
    runUrl: 'https://run',
  });
  assert.match(md, /\*\*CI failed\*\*: 1 job\(s\), 1 test\(s\)/);
  assert.match(md, /\| \[Android — build & unit tests\]\(https:\/\/j\/1\) \| failure \|/);
  assert.match(md, /\| Cloud Functions \| passed \|/);
  assert.match(md, /- `A > b`: boom \\\| pipe/);
  assert.match(md, /75% \(750 of 1000 lines\)/);
  assert.match(md, /UI-only build/);
  assert.match(md, /sign-in and sync will not work/);
  assert.match(md, /emulator-video-api30-default/);
  assert.doesNotMatch(md, /r8-mapping/);
  assert.match(md, /Manual checks this PR needs/);
  const json = JSON.parse(/<!-- ci-report-json (.*) -->/.exec(md)[1]);
  assert.deepEqual(json.failedJobs, ['Android — build & unit tests']);
  assert.equal(json.suites[0].failures[0].name, 'A > b');
});

test('render without API access or results still says something true', () => {
  const md = report.render({});
  assert.match(md, /No failed tests\.\*\* The job results could not be read/);
  assert.match(md, /No JUnit results were uploaded/);
  assert.match(md, /not reported/);
});

test('collect reads the screenshot summary; the suite label names Roborazzi', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ci-report-'));
  fs.mkdirSync(path.join(dir, 'screenshot-summary'));
  fs.writeFileSync(path.join(dir, 'screenshot-summary', 'screenshot-summary.json'),
      JSON.stringify({mode: 'verify', total: 113, changed: ['a/en_light_fs100_default.png'], added: []}));
  const {screenshots} = report.collect(dir);
  assert.deepEqual(screenshots, {mode: 'verify', total: 113, changed: ['a/en_light_fs100_default.png'], added: []});
  assert.equal(report.suiteLabel('junit-screenshots'), 'Screenshots (Roborazzi)');
  fs.rmSync(dir, {recursive: true});
});

test('render: a verify failure lists the images and points at the diffs and at Regenerate', () => {
  const md = report.render({
    screenshots: {mode: 'verify', total: 113, changed: ['home/en_light_fs100_default.png'],
      added: ['new_thing/en_light_fs100_default.png']},
    artifacts: [{name: 'screenshot-diffs', size_in_bytes: 300000, url: 'https://a/9'},
      {name: 'screenshot-summary', size_in_bytes: 200, url: 'https://a/10'},
      {name: 'junit-screenshots', size_in_bytes: 200, url: 'https://a/11'},
      {name: 'screenshots', size_in_bytes: 4000000, url: 'https://a/12'}],
  });
  assert.match(md, /\*\*1 changed, 1 without a baseline\*\* \(of 113\)/);
  assert.match(md, /- `home\/en_light_fs100_default\.png`/);
  assert.match(md, /- `new_thing\/en_light_fs100_default\.png` \(no baseline\)/);
  assert.match(md, /Regenerate workflow/);
  assert.match(md, /\[screenshot-diffs\]\(https:\/\/a\/9\).*Screenshot diffs/);
  assert.doesNotMatch(md, /\[screenshot-summary\]/);
  assert.doesNotMatch(md, /\[junit-screenshots\]/);
  assert.match(md, /\[screenshots\]\(https:\/\/a\/12\).*open `index\.html`/);
  const json = JSON.parse(/<!-- ci-report-json (.*) -->/.exec(md)[1]);
  assert.deepEqual(json.screenshots.changed, ['home/en_light_fs100_default.png']);
});

test('render: a matching verify run and a record fallback say which one they were', () => {
  assert.match(report.render({screenshots: {mode: 'verify', total: 113, changed: [], added: []}}),
      /All 113 screenshots match their committed baselines/);
  assert.match(report.render({screenshots: {mode: 'record', total: 113, changed: [], added: []}}),
      /compared nothing/);
});
