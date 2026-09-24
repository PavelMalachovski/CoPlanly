#!/usr/bin/env node
/**
 * Reads the raw output of `adb shell am instrument -w -r …`, writes it as JUnit XML, and decides
 * whether the phase passed. Used by tools/upgrade/run-upgrade-test.sh for the CI `upgrade` job:
 *
 *   node tools/upgrade/instrument-results.js --raw seed.txt --junit seed.xml \
 *     --suite "Upgrade — seed (base build)" --expect-class com.coparently.app.upgrade.UpgradeSeedTest
 *
 * `am instrument` prints `OK (1 test)` for a test that was *skipped* by an assumption, and exits 0
 * whether or not anything passed, so neither its exit status nor its summary line is a result.
 * This reads the per-test status codes instead (`-r`, raw mode): 1 = started, 0 = passed,
 * -1 = error, -2 = failure, -3 = ignored, -4 = assumption failure (skipped). The phase passes only
 * when at least one test of the expected class ran, every test that started passed, none was
 * skipped, and the run itself finished (`INSTRUMENTATION_CODE: -1`, no `shortMsg` — which is how a
 * crashed process or a missing instrumentation reports). Exits 0 on a pass, 1 otherwise, and
 * prints one line per test. No dependencies.
 */
'use strict';

const fs = require('fs');

const STARTED = 1;
const PASSED = 0;
const OUTCOME = { 0: 'passed', '-1': 'error', '-2': 'failure', '-3': 'ignored', '-4': 'skipped' };

/**
 * Parses raw `am instrument -r` output.
 * @param {string} text the whole output
 * @return {{statuses: {bundle: Object<string,string>, code: number}[],
 *   result: Object<string,string>, resultCode: number|null, failed: string[]}}
 */
function parse(text) {
  const statuses = [];
  const result = {};
  const failed = [];
  let resultCode = null;
  let bundle = {};
  let target = null; // the object the last key was written to, for continuation lines
  let lastKey = null;
  for (const line of text.replace(/\r\n/g, '\n').split('\n')) {
    let m;
    if ((m = /^INSTRUMENTATION_STATUS: ([^=]+)=(.*)$/.exec(line))) {
      bundle[m[1]] = m[2];
      target = bundle;
      lastKey = m[1];
    } else if ((m = /^INSTRUMENTATION_STATUS_CODE: (-?\d+)/.exec(line))) {
      statuses.push({ bundle, code: Number(m[1]) });
      bundle = {};
      target = null;
    } else if ((m = /^INSTRUMENTATION_RESULT: ([^=]+)=(.*)$/.exec(line))) {
      result[m[1]] = m[2];
      target = result;
      lastKey = m[1];
    } else if ((m = /^INSTRUMENTATION_CODE: (-?\d+)/.exec(line))) {
      resultCode = Number(m[1]);
      target = null;
    } else if (/^INSTRUMENTATION_(FAILED|ABORTED)\b/.test(line) || (!target && /^Error: /.test(line))) {
      failed.push(line);
      target = null;
    } else if (target && lastKey) {
      target[lastKey] += `\n${line}`;
    }
  }
  return { statuses, result, resultCode, failed };
}

/**
 * Turns parsed output into test cases and a verdict.
 * @param {ReturnType<typeof parse>} run
 * @param {string|undefined} expectClass the class that must have run
 * @return {{cases: {classname: string, name: string, outcome: string, message: string}[],
 *   ok: boolean, problems: string[]}}
 */
function evaluate(run, expectClass) {
  const cases = [];
  const open = new Map();
  for (const { bundle, code } of run.statuses) {
    const key = `${bundle.class}#${bundle.test}`;
    if (!bundle.test) continue;
    if (code === STARTED) {
      open.set(key, bundle);
      continue;
    }
    open.delete(key);
    cases.push({
      classname: bundle.class || '',
      name: bundle.test,
      outcome: OUTCOME[String(code)] || `status ${code}`,
      message: (bundle.stack || '').trim(),
    });
  }
  for (const bundle of open.values()) {
    cases.push({
      classname: bundle.class || '',
      name: bundle.test,
      outcome: 'error',
      message: `Started and never finished: ${run.result.shortMsg || 'the process died'}`,
    });
  }

  const problems = [];
  if (run.failed.length) problems.push(`The instrumentation did not run: ${run.failed.join(' | ')}`);
  if (run.result.shortMsg) problems.push(`The run ended with "${run.result.shortMsg.trim()}"`);
  if (run.resultCode !== -1) problems.push(`INSTRUMENTATION_CODE was ${run.resultCode}, not -1 (RESULT_OK)`);
  if (cases.length === 0) problems.push('No test ran');
  if (expectClass && !cases.some((c) => c.classname === expectClass)) {
    problems.push(`No test of ${expectClass} ran`);
  }
  for (const c of cases) {
    if (c.outcome !== 'passed') problems.push(`${c.classname}#${c.name} ${c.outcome}`);
  }
  return { cases, ok: problems.length === 0, problems };
}

/** @param {string} s @return {string} s, safe inside an XML attribute or text node */
function xml(s) {
  return String(s)
    .replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g, '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/**
 * JUnit XML for one phase. A run that failed before any test (a crash, a missing
 * instrumentation) becomes one erroring case, so a check run never shows green on nothing — and
 * a skipped or ignored test is written as a **failure**, because in this job a skip means the
 * phase argument did not arrive and nothing was checked.
 */
function toJUnit(suite, verdict) {
  const cases = verdict.cases.length
    ? verdict.cases
    : [{ classname: 'instrumentation', name: suite, outcome: 'error', message: verdict.problems.join('\n') }];
  const isFailure = (c) => ['failure', 'skipped', 'ignored'].includes(c.outcome);
  const failures = cases.filter(isFailure).length;
  const errors = cases.filter((c) => c.outcome !== 'passed' && !isFailure(c)).length;
  const body = cases.map((c) => {
    const open = `    <testcase classname="${xml(c.classname)}" name="${xml(c.name)}">`;
    const skip = c.outcome === 'skipped' || c.outcome === 'ignored' ? `${c.outcome}, which fails this job: ` : '';
    const firstLine = xml(skip + (c.message.split('\n')[0] || c.outcome).slice(0, 300));
    if (c.outcome === 'passed') return `${open}</testcase>`;
    const tag = isFailure(c) ? 'failure' : 'error';
    return `${open}<${tag} message="${firstLine}">${xml(c.message)}</${tag}></testcase>`;
  });
  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    `<testsuites><testsuite name="${xml(suite)}" tests="${cases.length}" failures="${failures}" ` +
      `errors="${errors}" skipped="0">`,
    ...body,
    '  </testsuite></testsuites>',
    '',
  ].join('\n');
}

function main(argv) {
  const arg = (name) => {
    const i = argv.indexOf(name);
    return i >= 0 ? argv[i + 1] : undefined;
  };
  const raw = arg('--raw');
  if (!raw) {
    process.stderr.write('usage: instrument-results.js --raw <file> [--junit <file>] [--suite <name>] [--expect-class <fqcn>]\n');
    return 2;
  }
  const suite = arg('--suite') || 'Instrumentation';
  const text = fs.existsSync(raw) ? fs.readFileSync(raw, 'utf8') : '';
  const verdict = evaluate(parse(text), arg('--expect-class'));
  if (arg('--junit')) fs.writeFileSync(arg('--junit'), toJUnit(suite, verdict));
  for (const c of verdict.cases) process.stdout.write(`  ${c.outcome.padEnd(7)} ${c.classname}#${c.name}\n`);
  if (verdict.ok) {
    process.stdout.write(`${suite}: passed (${verdict.cases.length} test(s))\n`);
    return 0;
  }
  for (const p of verdict.problems) process.stdout.write(`::error::${suite}: ${p}\n`);
  return 1;
}

if (require.main === module) process.exitCode = main(process.argv.slice(2));

module.exports = { parse, evaluate, toJUnit };
