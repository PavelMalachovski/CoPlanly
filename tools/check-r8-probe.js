#!/usr/bin/env node
/**
 * Reads what the R8 runtime probe reported and decides whether the minified app passed (REL-7).
 *
 * The probe (`app/src/r8Test/`) runs inside the `r8Test` build — `release` as R8 builds it — and
 * `tools/run-r8-probe.sh` captures `adb shell am instrument -w -r` into a file. Each case arrives
 * as one `INSTRUMENTATION_STATUS` block (`type`, `via`, `ok`, `detail`, `json`) and the run ends
 * with `INSTRUMENTATION_RESULT: probe=done`. This fails when:
 *
 *  - the probe did not finish (the process crashed, the component was not found, or it was never
 *    started) — no `probe=done` is never a pass;
 *  - any case failed — its type, the production path it went through and what was wrong are each
 *    printed as a GitHub `::error::` annotation;
 *  - a type `tools/check-invariants.js` finds handed to Gson has no case at all, so a model added
 *    without a probe case fails here as well as in `invariants`.
 *
 * Usage: node tools/check-r8-probe.js <am-instrument-output.txt> [--junit <out.xml>]
 * No dependencies. `node --test tools/test/check-r8-probe.test.js` covers the parser.
 */
'use strict';

const fs = require('fs');
const path = require('path');

/**
 * Parses `am instrument -r` output.
 * @param {string} text the captured output
 * @return {{cases: Object<string, string>[], result: Object<string, string>, code: number|null}}
 */
function parse(text) {
  const cases = [];
  const result = {};
  let block = {};
  let code = null;
  let target = null;
  let key = null;
  for (const raw of text.split('\n')) {
    const line = raw.replace(/\r$/, '');
    let m;
    if ((m = /^INSTRUMENTATION_STATUS: ([^=]+)=(.*)$/.exec(line))) {
      block[m[1]] = m[2];
      target = block;
      key = m[1];
    } else if (/^INSTRUMENTATION_STATUS_CODE: /.test(line)) {
      if ('type' in block) cases.push(block);
      block = {};
      target = key = null;
    } else if ((m = /^INSTRUMENTATION_RESULT: ([^=]+)=(.*)$/.exec(line))) {
      result[m[1]] = m[2];
      target = result;
      key = m[1];
    } else if ((m = /^INSTRUMENTATION_CODE: (-?\d+)/.exec(line))) {
      code = Number(m[1]);
      target = key = null;
    } else if (target && key && line && !line.startsWith('INSTRUMENTATION_')) {
      // A value with a newline in it continues on the next line; the probe avoids them, a crash
      // message does not.
      target[key] += '\n' + line;
    }
  }
  return { cases, result, code };
}

/**
 * Everything wrong with a parsed run.
 * @param {{cases: Object<string, string>[], result: Object<string, string>}} run the parsed output
 * @param {string[]} required the types every run must have passed a case for
 * @return {string[]} one line per problem; empty when the run passed
 */
function problemsOf(run, required) {
  const problems = [];
  if (run.result.probe !== 'done') {
    const why = run.result.shortMsg || run.result.longMsg || 'no INSTRUMENTATION_RESULT: probe=done';
    problems.push(`The probe did not finish: ${why.split('\n')[0]}. A run that did not finish has not passed.`);
  }
  for (const c of run.cases) {
    if (c.ok !== 'true') problems.push(`${c.type} (via ${c.via}): ${c.detail}`);
  }
  for (const type of required) {
    if (!run.cases.some((c) => c.type === type)) {
      problems.push(
        `${type} is handed to Gson, but the probe reported no case for it — add one to ` +
          'app/src/r8Test/java/com/coparently/app/r8probe/R8GsonProbe.kt'
      );
    }
  }
  return problems;
}

const xml = (s) =>
  String(s).replace(/[<>&"]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;' })[c]);

/**
 * A JUnit document for `tools/ci-report.js` and the check run: one test case per probe case, plus
 * one for "the probe finished" and one per required type with no case.
 */
function junit(run, required) {
  const cases = [];
  const finished = run.result.probe === 'done';
  cases.push({
    classname: 'r8probe',
    name: 'the probe finished',
    failure: finished ? null : run.result.shortMsg || 'no probe=done',
  });
  for (const c of run.cases) {
    cases.push({ classname: c.type, name: c.via, failure: c.ok === 'true' ? null : c.detail });
  }
  for (const type of required) {
    if (!run.cases.some((c) => c.type === type)) {
      cases.push({ classname: type, name: 'has a probe case', failure: 'no case reported' });
    }
  }
  const failures = cases.filter((c) => c.failure).length;
  const body = cases
    .map((c) => {
      const open = `    <testcase classname="${xml(c.classname)}" name="${xml(c.name)}"`;
      return c.failure
        ? `${open}>\n      <failure message="${xml(c.failure)}"/>\n    </testcase>`
        : `${open}/>`;
    })
    .join('\n');
  return (
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
    `<testsuites>\n  <testsuite name="R8 runtime probe" tests="${cases.length}" failures="${failures}">\n` +
    `${body}\n  </testsuite>\n</testsuites>\n`
  );
}

if (require.main === module) {
  const args = process.argv.slice(2);
  const junitAt = args.indexOf('--junit');
  const junitPath = junitAt >= 0 ? args[junitAt + 1] : null;
  const input = args.find((a, i) => !a.startsWith('--') && (junitAt < 0 || i !== junitAt + 1));
  if (!input || !fs.existsSync(input)) {
    console.error(`usage: node tools/check-r8-probe.js <am-instrument-output.txt> [--junit <out.xml>]`);
    console.error(input ? `No such file: ${input}` : 'No input file given.');
    process.exit(2);
  }

  const run = parse(fs.readFileSync(input, 'utf8'));
  const required = [...require('./check-invariants.js').gsonTargets().keys()];
  const problems = problemsOf(run, required);

  for (const c of run.cases) {
    console.log(`${c.ok === 'true' ? 'ok  ' : 'FAIL'}  ${c.type}  (${c.via})`);
    if (c.ok !== 'true') console.log(`      ${c.detail}`);
    if (c.json) console.log(`      ${c.json}`);
  }
  if (junitPath) {
    fs.mkdirSync(path.dirname(junitPath), { recursive: true });
    fs.writeFileSync(junitPath, junit(run, required));
  }

  const passed = run.cases.filter((c) => c.ok === 'true').length;
  console.log(`\nr8 probe: ${passed} of ${run.cases.length} case(s) passed; ${required.length} Gson model(s) required`);
  if (problems.length) {
    const annotate = process.env.GITHUB_ACTIONS === 'true' ? '::error::' : '';
    console.error(`\n${problems.length} problem(s) in the minified build:`);
    for (const p of problems) console.error(`${annotate}R8 runtime probe: ${p}`);
    process.exit(1);
  }
  console.log('Every Gson model wrote its source field names and read back equal in the minified build.');
}

module.exports = { parse, problemsOf, junit };
