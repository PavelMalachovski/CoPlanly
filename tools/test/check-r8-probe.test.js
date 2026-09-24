/**
 * Tests for tools/check-r8-probe.js. Run with `node --test tools/test/*.test.js`; no dependencies.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { parse, problemsOf, junit } = require('../check-r8-probe.js');

const MED = 'com.coparently.app.domain.model.Medication';
const VAC = 'com.coparently.app.domain.model.Vaccination';

/** What `am instrument -w -r` prints, CRLF included as some adb versions write it. */
function output({ cases, done = true }) {
  const lines = [];
  for (const c of cases) {
    lines.push(`INSTRUMENTATION_STATUS: type=${c.type}`);
    lines.push(`INSTRUMENTATION_STATUS: via=${c.via || 'ChildInfoRepositoryImpl'}`);
    lines.push(`INSTRUMENTATION_STATUS: ok=${c.ok}`);
    lines.push(`INSTRUMENTATION_STATUS: detail=${c.detail || 'ok'}`);
    lines.push(`INSTRUMENTATION_STATUS: json=${c.json || '{"name":"MMR"}'}`);
    lines.push('INSTRUMENTATION_STATUS_CODE: 1');
  }
  if (done) {
    lines.push('INSTRUMENTATION_RESULT: probe=done');
    lines.push(`INSTRUMENTATION_RESULT: cases=${cases.length}`);
    lines.push('INSTRUMENTATION_CODE: -1');
  } else {
    lines.push('INSTRUMENTATION_RESULT: shortMsg=Process crashed.');
    lines.push('INSTRUMENTATION_CODE: 0');
  }
  return lines.join('\r\n') + '\r\n';
}

test('a finished run whose cases all pass has no problems', () => {
  const run = parse(output({ cases: [{ type: MED, ok: 'true' }, { type: VAC, ok: 'true' }] }));
  assert.equal(run.cases.length, 2);
  assert.equal(run.cases[0].json, '{"name":"MMR"}');
  assert.equal(run.result.probe, 'done');
  assert.equal(run.code, -1);
  assert.deepEqual(problemsOf(run, [MED, VAC]), []);
});

test('a failed case names its type, its path and what was wrong', () => {
  const run = parse(output({
    cases: [{ type: MED, ok: 'false', via: 'PetRepositoryImpl', detail: 'keys missing from the JSON: [dosage]' }],
  }));
  const problems = problemsOf(run, [MED]);
  assert.equal(problems.length, 1);
  assert.match(problems[0], /Medication \(via PetRepositoryImpl\): keys missing from the JSON: \[dosage\]/);
});

test('a crashed process is never a pass, even with every reported case green', () => {
  const run = parse(output({ cases: [{ type: MED, ok: 'true' }], done: false }));
  const problems = problemsOf(run, [MED]);
  assert.equal(problems.length, 1);
  assert.match(problems[0], /did not finish: Process crashed\./);
});

test('empty output (the component was never started) fails', () => {
  const problems = problemsOf(parse('android.util.AndroidException: INSTRUMENTATION_FAILED: x\n'), [MED]);
  assert.ok(problems.some((p) => /did not finish/.test(p)));
  assert.ok(problems.some((p) => /Medication is handed to Gson, but the probe reported no case/.test(p)));
});

test('a required Gson model with no case fails, so a new model cannot slip past', () => {
  const run = parse(output({ cases: [{ type: MED, ok: 'true' }] }));
  const problems = problemsOf(run, [MED, VAC]);
  assert.deepEqual(problems.length, 1);
  assert.match(problems[0], /Vaccination is handed to Gson/);
});

test('a value continued on the next line stays with its key', () => {
  const run = parse(
    'INSTRUMENTATION_RESULT: shortMsg=Process crashed.\nat com.example.Foo\nINSTRUMENTATION_CODE: 0\n'
  );
  assert.equal(run.result.shortMsg, 'Process crashed.\nat com.example.Foo');
});

test('the JUnit document counts one test per case plus the finish check, failures escaped', () => {
  const run = parse(output({ cases: [{ type: MED, ok: 'false', detail: 'wrote "a" <not> "name"' }] }));
  const doc = junit(run, [MED, VAC]);
  assert.equal((doc.match(/<testcase /g) || []).length, 3);
  assert.equal((doc.match(/<failure /g) || []).length, 2);
  assert.match(doc, /wrote &quot;a&quot; &lt;not&gt; &quot;name&quot;/);
});

test('the real discovery requires the models the probe names', () => {
  const { gsonTargets } = require('../check-invariants.js');
  const targets = [...gsonTargets().keys()];
  assert.ok(targets.includes(MED));
  assert.ok(targets.includes('com.coparently.app.domain.model.SchoolInfo'), 'multi-line fromJson is discovered');
});
