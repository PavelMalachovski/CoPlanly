#!/usr/bin/env node
/**
 * Builds the CI summary for a pull request: one markdown document the `report` job in
 * .github/workflows/ci.yml posts as a sticky PR comment (one comment, edited on every run) and
 * writes to the run's job summary.
 *
 * It exists so a CI result can be read without opening a log — by a person, and by the
 * maintainer's assistant, which reads PR comments and check runs through the GitHub API. For
 * the second reader the comment also carries the same facts as JSON inside an HTML comment
 * (`<!-- ci-report-json … -->`), invisible on the page and trivially parsed.
 *
 * Inputs, all optional so a missing one degrades to "not reported" rather than a failed job:
 *   --artifacts <dir>  downloaded artifacts, one sub-directory per artifact. `junit-*` hold
 *                      JUnit XML; `coverage-report` holds Kover's XML.
 *   --plan <file>      markdown from tools/manual-test-plan.js
 *   --out <file>       where to write the markdown (default: stdout)
 *   GITHUB_TOKEN, GITHUB_REPOSITORY, GITHUB_RUN_ID, GITHUB_API_URL, GITHUB_SERVER_URL — to list
 *   the run's jobs and artifacts. Without a token those two sections are left out.
 *
 * No dependencies: JUnit and JaCoCo XML are read with regular expressions, which is enough for
 * the shapes Gradle, AGP and mocha write and keeps this runnable with a bare `node`.
 */
'use strict';

const fs = require('fs');
const path = require('path');

const MAX_FAILURES_LISTED = 25;

/** @param {string} s XML text @return {string} it with the five entities and numeric refs decoded */
function decodeXml(s) {
  return String(s)
      .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCodePoint(parseInt(h, 16)))
      .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(parseInt(d, 10)))
      .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"')
      .replace(/&apos;/g, '\'').replace(/&amp;/g, '&');
}

/** @param {string} tag an opening tag @return {Record<string, string>} its attributes, decoded */
function attributes(tag) {
  const out = {};
  const re = /([\w:.-]+)\s*=\s*("([^"]*)"|'([^']*)')/g;
  let m;
  while ((m = re.exec(tag)) !== null) out[m[1]] = decodeXml(m[3] !== undefined ? m[3] : m[4]);
  return out;
}

/** @param {string} s any text @return {string} its first non-blank line, trimmed and capped */
function firstLine(s) {
  const line = String(s || '').split(/\r?\n/).map((l) => l.trim()).find(Boolean) || '';
  return line.length > 200 ? line.slice(0, 197) + '...' : line;
}

/**
 * Counts and failures from one JUnit XML document. Counts come from the `<testcase>` elements
 * rather than the suites' attributes, which differ between writers and nest in some of them.
 * @param {string} xml the document
 * @return {{tests: number, failed: number, skipped: number,
 *   failures: {name: string, message: string}[]}} the result
 */
function parseJUnit(xml) {
  const result = {tests: 0, failed: 0, skipped: 0, failures: []};
  const re = /<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g;
  let m;
  while ((m = re.exec(xml)) !== null) {
    const attrs = attributes(m[1]);
    const body = m[3] || '';
    result.tests++;
    const failure = /<(failure|error)\b([^>]*?)(\/>|>([\s\S]*?)<\/\1>)/.exec(body);
    if (failure) {
      result.failed++;
      const fattrs = attributes(failure[2]);
      const text = fattrs.message || decodeXml((failure[4] || '').replace(/<!\[CDATA\[|\]\]>/g, ''));
      const name = attrs.classname ? `${attrs.classname} > ${attrs.name}` : attrs.name;
      result.failures.push({name: name || '(unnamed)', message: firstLine(text)});
    } else if (/<skipped\b/.test(body)) {
      result.skipped++;
    }
  }
  return result;
}

/**
 * Line coverage from a Kover (JaCoCo-format) XML report: the report-level LINE counter, which
 * JaCoCo writes as a direct child of `<report>`, after the last `</package>`.
 * @param {string} xml the report
 * @return {{covered: number, missed: number, percent: number}|null} null when absent
 */
function parseCoverage(xml) {
  const tailStart = xml.lastIndexOf('</package>');
  const tail = tailStart >= 0 ? xml.slice(tailStart) : xml;
  const m = /<counter\b[^>]*type="LINE"[^>]*\/>/.exec(tail);
  if (!m) return null;
  const a = attributes(m[0]);
  const covered = Number(a.covered);
  const missed = Number(a.missed);
  if (!Number.isFinite(covered) || !Number.isFinite(missed) || covered + missed === 0) return null;
  return {covered, missed, percent: Math.round((covered / (covered + missed)) * 1000) / 10};
}

/**
 * Which test suite an artifact holds, from its name.
 * @param {string} artifact directory name, e.g. `junit-instrumented-api30-default`
 * @return {string} a label for the table
 */
function suiteLabel(artifact) {
  const name = artifact.replace(/^junit-/, '');
  const inst = /^instrumented-api(\d+)-?(.*)$/.exec(name);
  if (inst) return `Instrumented, API ${inst[1]}${inst[2] && inst[2] !== 'default' ? ` (${inst[2]})` : ''}`;
  return ({unit: 'Unit (JVM)', functions: 'Cloud Functions', rules: 'Firestore / Storage rules'})[name] ||
    name;
}

/** @param {string} dir a directory @return {string[]} every *.xml file below it */
function xmlFilesIn(dir) {
  const out = [];
  const walk = (d) => {
    for (const e of fs.readdirSync(d, {withFileTypes: true})) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) walk(p);
      else if (e.name.endsWith('.xml')) out.push(p);
    }
  };
  if (fs.existsSync(dir)) walk(dir);
  return out.sort();
}

/**
 * @param {string} artifactsDir directory holding one sub-directory per downloaded artifact
 * @return {{suites: {label: string, tests: number, failed: number, skipped: number,
 *   failures: {name: string, message: string}[]}[], coverage: object|null}} parsed results
 */
function collect(artifactsDir) {
  const suites = [];
  let coverage = null;
  if (!artifactsDir || !fs.existsSync(artifactsDir)) return {suites, coverage};
  for (const entry of fs.readdirSync(artifactsDir).sort()) {
    const dir = path.join(artifactsDir, entry);
    if (entry.startsWith('junit-')) {
      const total = {label: suiteLabel(entry), tests: 0, failed: 0, skipped: 0, failures: []};
      for (const file of xmlFilesIn(dir)) {
        const r = parseJUnit(fs.readFileSync(file, 'utf8'));
        total.tests += r.tests;
        total.failed += r.failed;
        total.skipped += r.skipped;
        total.failures.push(...r.failures);
      }
      suites.push(total);
    } else if (entry === 'coverage-report') {
      for (const file of xmlFilesIn(dir)) {
        coverage = parseCoverage(fs.readFileSync(file, 'utf8')) || coverage;
      }
    }
  }
  return {suites, coverage};
}

/** @param {{status: string, conclusion: string|null}} job a job from the API @return {string} */
function jobState(job) {
  if (job.status !== 'completed') return job.status.replace('_', ' ');
  return job.conclusion || 'unknown';
}

/** @param {number} bytes a size @return {string} it in human units */
function humanSize(bytes) {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

/**
 * What a tester looks for in an artifact list, and what each one is.
 * @param {string} name artifact name
 * @return {string|null} a description, or null for artifacts only a developer wants
 */
function describeArtifact(name) {
  if (name === 'coplanly-debug-apk') {
    return 'Debug APK. **UI-only build: CI has no `google-services.json`, so sign-in and sync ' +
      'will not work.** For full testing build locally with `google-services.json` ' +
      '(docs/DEVICE-CHECKLIST.md §0-§1).';
  }
  if (name.startsWith('emulator-video-')) return 'Screen recording of the instrumented tests, ' + name.replace('emulator-video-', '');
  if (/screenshot|roborazzi/i.test(name)) return 'Screenshot tests';
  if (name === 'coverage-report') return 'Unit-test coverage (Kover HTML + XML)';
  if (/^android-reports-/.test(name)) return 'Gradle reports: ' + name.replace('android-reports-', '');
  if (/e2e/i.test(name)) return 'End-to-end tests against the Firebase emulators';
  return null;
}

/**
 * The whole document. Pure, so it can be tested without a network.
 * @param {object} data {suites, coverage, jobs, artifacts, plan, runUrl, repoUrl, runId, sha}
 * @return {string} markdown
 */
function render(data) {
  const {suites = [], coverage = null, jobs = null, artifacts = null, plan = '', runUrl} = data;
  const failedJobs = (jobs || []).filter((j) => ['failure', 'cancelled', 'timed_out'].includes(jobState(j)));
  const failedTests = suites.reduce((n, s) => n + s.failed, 0);
  const pending = (jobs || []).filter((j) => j.status !== 'completed');
  const verdict = failedJobs.length || failedTests ?
    `**CI failed**: ${failedJobs.length} job(s), ${failedTests} test(s).` :
    pending.length ? `**CI in progress**: ${pending.length} job(s) had not finished when this was written.` :
    jobs ? '**CI passed.**' : '**No failed tests.** The job results could not be read, so this is not a verdict on the run.';

  const lines = ['## CI summary', '', `${verdict} ` + (data.sha ? `Commit \`${data.sha.slice(0, 7)}\`. ` : '') +
    (runUrl ? `[Workflow run](${runUrl}).` : ''), ''];

  if (jobs) {
    lines.push('| Job | Result |', '| --- | --- |');
    for (const j of jobs) {
      const state = jobState(j);
      lines.push(`| ${j.html_url ? `[${j.name}](${j.html_url})` : j.name} | ${state === 'success' ? 'passed' : state} |`);
    }
    lines.push('');
  }

  lines.push('### Tests', '');
  if (suites.length === 0) {
    lines.push('No JUnit results were uploaded (the test jobs were skipped or did not get that far).', '');
  } else {
    lines.push('| Suite | Tests | Failed | Skipped |', '| --- | ---: | ---: | ---: |');
    for (const s of suites) lines.push(`| ${s.label} | ${s.tests} | ${s.failed} | ${s.skipped} |`);
    lines.push('');
  }
  lines.push(`**Line coverage (unit tests, Kover):** ${coverage ?
    `${coverage.percent}% (${coverage.covered} of ${coverage.covered + coverage.missed} lines). No gate; visibility only.` :
    'not reported.'}`, '');

  const failing = suites.filter((s) => s.failed);
  if (failing.length) {
    lines.push('### Failed tests', '');
    let listed = 0;
    for (const s of failing) {
      lines.push(`**${s.label}**`, '');
      for (const f of s.failures) {
        if (listed >= MAX_FAILURES_LISTED) break;
        lines.push(`- \`${f.name}\`${f.message ? `: ${f.message.replace(/\|/g, '\\|')}` : ''}`);
        listed++;
      }
      lines.push('');
    }
    if (failedTests > listed) lines.push(`… and ${failedTests - listed} more; see the check runs.`, '');
  }

  if (artifacts) {
    const shown = artifacts.map((a) => ({...a, what: describeArtifact(a.name)})).filter((a) => a.what);
    lines.push('### Artifacts', '');
    if (!artifacts.some((a) => a.name === 'coplanly-debug-apk')) {
      lines.push('No debug APK in this run (the Android jobs were skipped or the build failed).', '');
    }
    if (shown.length) {
      for (const a of shown) {
        lines.push(`- [${a.name}](${a.url}) (${humanSize(a.size_in_bytes)}): ${a.what}`);
      }
      lines.push('', 'Artifacts download as a zip and need a GitHub login; they expire after 14 days.', '');
    }
  }

  if (plan) lines.push(plan.trim(), '');

  const json = {
    sha: data.sha || null,
    run: runUrl || null,
    failedJobs: failedJobs.map((j) => j.name),
    pendingJobs: pending.map((j) => j.name),
    suites: suites.map(({label, tests, failed, skipped, failures}) =>
      ({label, tests, failed, skipped, failures: failures.slice(0, MAX_FAILURES_LISTED)})),
    coverage,
  };
  lines.push(`<!-- ci-report-json ${JSON.stringify(json).replace(/--/g, '-\\u002d')} -->`);
  return lines.join('\n') + '\n';
}

/**
 * @param {string} url API URL
 * @param {string} token GITHUB_TOKEN
 * @param {string} key the array field of the response
 * @return {Promise<object[]|null>} every item, or null when the API could not be read
 */
async function listAll(url, token, key) {
  const items = [];
  try {
    for (let page = 1; page <= 10; page++) {
      const res = await fetch(`${url}${url.includes('?') ? '&' : '?'}per_page=100&page=${page}`, {
        headers: {'Authorization': `Bearer ${token}`, 'Accept': 'application/vnd.github+json'},
      });
      if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
      const body = await res.json();
      items.push(...body[key]);
      if (body[key].length < 100) break;
    }
    return items;
  } catch (e) {
    console.error(`::warning::Could not read ${url}: ${e.message}`);
    return null;
  }
}

async function main() {
  const args = {};
  const argv = process.argv.slice(2);
  for (let i = 0; i < argv.length; i += 2) args[argv[i].replace(/^--/, '')] = argv[i + 1];

  const {suites, coverage} = collect(args.artifacts);
  const env = process.env;
  const api = env.GITHUB_API_URL || 'https://api.github.com';
  const server = env.GITHUB_SERVER_URL || 'https://github.com';
  const repo = env.GITHUB_REPOSITORY;
  const runId = env.GITHUB_RUN_ID;
  let jobs = null;
  let artifacts = null;
  if (env.GITHUB_TOKEN && repo && runId) {
    const self = env.REPORT_JOB_NAME || 'CI report';
    jobs = await listAll(`${api}/repos/${repo}/actions/runs/${runId}/attempts/${env.GITHUB_RUN_ATTEMPT || 1}/jobs`,
        env.GITHUB_TOKEN, 'jobs');
    if (jobs) jobs = jobs.filter((j) => j.name !== self);
    artifacts = await listAll(`${api}/repos/${repo}/actions/runs/${runId}/artifacts`, env.GITHUB_TOKEN, 'artifacts');
    if (artifacts) {
      artifacts = artifacts.filter((a) => !a.expired)
          .map((a) => ({...a, url: `${server}/${repo}/actions/runs/${runId}/artifacts/${a.id}`}));
    }
  }
  const plan = args.plan && fs.existsSync(args.plan) ? fs.readFileSync(args.plan, 'utf8') : '';
  const markdown = render({
    suites, coverage, jobs, artifacts, plan,
    sha: env.HEAD_SHA || env.GITHUB_SHA,
    runUrl: repo && runId ? `${server}/${repo}/actions/runs/${runId}` : undefined,
  });
  if (args.out) fs.writeFileSync(args.out, markdown);
  else process.stdout.write(markdown);
  if (env.GITHUB_STEP_SUMMARY) fs.appendFileSync(env.GITHUB_STEP_SUMMARY, markdown);
}

if (require.main === module) {
  main().catch((e) => {
    // Never fail the report job over the report: it is informational.
    console.error(`::warning::ci-report failed: ${e.stack || e}`);
  });
}

module.exports = {decodeXml, parseJUnit, parseCoverage, suiteLabel, collect, render, describeArtifact};
