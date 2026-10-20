#!/usr/bin/env node
/**
 * Builds the virtual tester's report: a static `index.html`, `results.json` and `summary.md` (for
 * the workflow's job summary) from the step lines the scenarios wrote. No dependencies.
 *
 *   node tools/virtual-tester/report.js <site dir> [variant ...]
 *
 * `<site dir>` holds `virtual-tester/<variant>/steps.jsonl` and `virtual-tester/<variant>/shots/`,
 * as `tools/virtual-tester/run-virtual-tester.sh` pulls them. Variants are read in the order given
 * (default: every directory, sorted). Two lists beside this file shape the verdicts:
 *
 *   - `known.json` — `[{ "step": "<Scenario>/<id>", "reason": "…", "ref": "…" }]`: a failed step
 *     named there is shown as *known* (a limitation already recorded), not counted as a failure;
 *   - `coverage.json` — `[{ "area": "…", "scenario": "…" }]`: a scenario with no step in this run
 *     is listed as not covered, so a run that silently skipped a whole area says so.
 *
 * `VIRTUAL_TESTER_SOURCE` (e.g. "claude/my-branch @ 1a2b3c4") is printed under the title when set.
 */
'use strict';

const fs = require('fs');
const path = require('path');

const VERDICTS = ['failed', 'could_not_run', 'known', 'passed'];
const VERDICT_LABELS = {
  passed: 'Passed',
  failed: 'Failed',
  could_not_run: 'Could not run',
  known: 'Known',
};

function escapeHtml(text) {
  return String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/** The steps in one `steps.jsonl`; a line cut off by a crash, or a missing file, is skipped. */
function readSteps(file) {
  if (!fs.existsSync(file)) return [];
  return fs
    .readFileSync(file, 'utf8')
    .split('\n')
    .filter((line) => line.trim() !== '')
    .flatMap((line) => {
      try {
        return [JSON.parse(line)];
      } catch {
        return [];
      }
    });
}

/** Marks each failed step that `known` names as `known`, with its reason and reference. */
function applyKnown(steps, known) {
  const byStep = new Map((known || []).map((entry) => [entry.step, entry]));
  return steps.map((s) => {
    const entry = byStep.get(`${s.scenario}/${s.id}`);
    if (s.verdict !== 'failed' || !entry) return s;
    return { ...s, verdict: 'known', knownReason: entry.reason, knownRef: entry.ref };
  });
}

/** Counts by verdict, steps with findings, and the coverage scenarios this run has no step for. */
function summarise(steps, coverage) {
  const count = (verdict) => steps.filter((s) => s.verdict === verdict).length;
  const seen = new Set(steps.map((s) => s.scenario));
  return {
    total: steps.length,
    passed: count('passed'),
    failed: count('failed'),
    couldNotRun: count('could_not_run'),
    known: count('known'),
    findings: steps.filter((s) => (s.findings || []).length > 0).length,
    notCovered: (coverage || []).filter((c) => !seen.has(c.scenario)).map((c) => c.area),
  };
}

function cell(text) {
  return String(text ?? '').replace(/\s*\n\s*/g, ' ').replace(/\|/g, '\\|');
}

/** The job summary: totals, then one table row per failed step, then steps with findings. */
function renderSummaryMarkdown(summary, steps) {
  const out = ['### Virtual tester', ''];
  out.push(
    `${summary.total} steps: ${summary.passed} passed, ${summary.failed} failed, ` +
      `${summary.couldNotRun} could not run, ${summary.known} known; ${summary.findings} with visual findings.`,
  );
  out.push('');
  const failed = steps.filter((s) => s.verdict === 'failed');
  if (failed.length === 0) {
    out.push('All steps passed or are known limitations.');
  } else {
    out.push('| Scenario | Step | Expected | Actual |', '| --- | --- | --- | --- |');
    failed.forEach((s) => out.push(`| ${cell(s.scenario)} | ${cell(s.id)} | ${cell(s.expected)} | ${cell(s.actual)} |`));
  }
  const withFindings = steps.filter((s) => (s.findings || []).length > 0);
  if (withFindings.length > 0) {
    out.push('', '**Visual findings**', '');
    withFindings.forEach((s) => out.push(`- ${cell(s.scenario)}/${cell(s.id)}: ${cell(s.findings.join('; '))}`));
  }
  if (summary.notCovered.length > 0) {
    out.push('', `Not covered in this run: ${summary.notCovered.join(', ')}.`);
  }
  out.push('');
  return out.join('\n');
}

function groupBy(items, key) {
  const groups = new Map();
  items.forEach((item) => {
    const k = key(item);
    if (!groups.has(k)) groups.set(k, []);
    groups.get(k).push(item);
  });
  return groups;
}

function renderStep(s) {
  const findings = (s.findings || []).map((f) => `<li>${escapeHtml(f)}</li>`).join('');
  const known = s.verdict === 'known'
    ? `<p class="known">Known: ${escapeHtml(s.knownReason)}${s.knownRef ? ` (${escapeHtml(s.knownRef)})` : ''}</p>`
    : '';
  const delivery = s.delivery
    ? `<p class="meta">Delivered by ${s.delivery === 'listener' ? 'a live listener' : 'a forced sync'}</p>`
    : '';
  const shot = s.screenshot
    ? `<a href="${escapeHtml(s.screenshot)}"><img loading="lazy" src="${escapeHtml(s.screenshot)}" alt="Screenshot after ${escapeHtml(s.id)}"></a>`
    : '<div class="noshot">No screenshot</div>';
  return `<article class="step ${escapeHtml(s.verdict)}">
  <div class="text">
    <p><span class="badge">${escapeHtml(VERDICT_LABELS[s.verdict] || s.verdict)}</span> <strong>${escapeHtml(s.title)}</strong> <code>${escapeHtml(s.id)}</code></p>
    <p><span class="label">Expected</span> ${escapeHtml(s.expected)}</p>
    <p><span class="label">Actual</span> ${escapeHtml(s.actual)}</p>
    ${known}${delivery}${findings ? `<ul class="findings">${findings}</ul>` : ''}
  </div>
  ${shot}
</article>`;
}

/** The whole page: totals and the failures first, then every step by variant, scenario and method. */
function renderHtml(steps, summary, source) {
  const order = (s) => VERDICTS.indexOf(s.verdict);
  const failures = steps.filter((s) => s.verdict === 'failed').sort((a, b) => order(a) - order(b));
  const sections = [...groupBy(steps, (s) => s.variant)].map(([variant, vSteps]) => {
    const scenarios = [...groupBy(vSteps, (s) => s.scenario)].map(([scenario, sSteps]) => {
      const methods = [...groupBy(sSteps, (s) => s.method)]
        .map(([method, mSteps]) => `<h4>${escapeHtml(method)}</h4>\n${mSteps.map(renderStep).join('\n')}`)
        .join('\n');
      return `<section><h3>${escapeHtml(scenario)}</h3>\n${methods}</section>`;
    });
    return `<section><h2>${escapeHtml(variant)}</h2>\n${scenarios.join('\n')}</section>`;
  });
  const notCovered = summary.notCovered.length
    ? `<p class="meta">Not covered in this run: ${summary.notCovered.map(escapeHtml).join(', ')}</p>`
    : '';
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Virtual tester</title>
<style>
:root { --bg:#f6f5fb; --fg:#1b1b21; --muted:#5e5d6b; --card:#fff; --line:#dcdbe6;
  --pass:#1e7f4c; --fail:#b3261e; --skip:#7a5900; --known:#4f4cc9; }
@media (prefers-color-scheme: dark) { :root { --bg:#131318; --fg:#e5e1e9; --muted:#a5a2b3; --card:#1f1f25;
  --line:#3a3a44; --pass:#6fd39c; --fail:#ffb4ab; --skip:#f1c55a; --known:#c2c1ff; } }
body { background:var(--bg); color:var(--fg); font:15px/1.45 system-ui, sans-serif; margin:0; padding:24px 16px 48px; }
main { max-width:1100px; margin:0 auto; }
h1 { margin:0 0 4px; } .meta { color:var(--muted); }
.totals { display:flex; flex-wrap:wrap; gap:8px; margin:12px 0 20px; }
.totals span { background:var(--card); border:1px solid var(--line); border-radius:999px; padding:4px 12px; }
.step { display:grid; grid-template-columns:1fr 180px; gap:12px; background:var(--card); border:1px solid var(--line);
  border-left:4px solid var(--line); border-radius:12px; padding:12px; margin:8px 0; }
.step.passed { border-left-color:var(--pass); } .step.failed { border-left-color:var(--fail); }
.step.could_not_run { border-left-color:var(--skip); } .step.known { border-left-color:var(--known); }
.step p { margin:4px 0; overflow-wrap:anywhere; } .label { color:var(--muted); display:inline-block; min-width:70px; }
.badge { font-weight:600; } .step.failed .badge { color:var(--fail); } .step.passed .badge { color:var(--pass); }
.step.could_not_run .badge { color:var(--skip); } .step.known .badge, .known { color:var(--known); }
.step img { width:100%; border-radius:8px; border:1px solid var(--line); }
.noshot { color:var(--muted); font-size:13px; } .findings { margin:4px 0; padding-left:18px; color:var(--skip); }
@media (max-width:600px) { .step { grid-template-columns:1fr; } }
</style>
</head>
<body>
<main>
<h1>Virtual tester</h1>
${source ? `<p class="meta">${escapeHtml(source)}</p>` : ''}
<div class="totals"><span>${summary.total} steps</span><span>${summary.passed} passed</span><span>${summary.failed} failed</span><span>${summary.couldNotRun} could not run</span><span>${summary.known} known</span><span>${summary.findings} with visual findings</span></div>
${notCovered}
${failures.length ? `<section><h2>Failed steps</h2>\n${failures.map(renderStep).join('\n')}</section>` : ''}
${sections.join('\n')}
</main>
</body>
</html>
`;
}

function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

function main(argv) {
  const [siteDir, ...requested] = argv;
  if (!siteDir) {
    console.error('usage: report.js <site dir> [variant ...]');
    process.exit(2);
  }
  const root = path.join(siteDir, 'virtual-tester');
  const variants = requested.length
    ? requested
    : fs.existsSync(root) ? fs.readdirSync(root).filter((d) => fs.statSync(path.join(root, d)).isDirectory()).sort() : [];
  const known = readJson(path.join(__dirname, 'known.json'), []);
  const coverage = readJson(path.join(__dirname, 'coverage.json'), []);
  const steps = applyKnown(variants.flatMap((v) => readSteps(path.join(root, v, 'steps.jsonl'))), known);
  const summary = summarise(steps, coverage);
  fs.mkdirSync(siteDir, { recursive: true });
  fs.writeFileSync(path.join(siteDir, 'index.html'), renderHtml(steps, summary, process.env.VIRTUAL_TESTER_SOURCE));
  fs.writeFileSync(path.join(siteDir, 'results.json'), `${JSON.stringify({ summary, steps }, null, 2)}\n`);
  fs.writeFileSync(path.join(siteDir, 'summary.md'), renderSummaryMarkdown(summary, steps));
  console.log(`Virtual tester report: ${summary.total} steps, ${summary.failed} failed, in ${siteDir}`);
}

if (require.main === module) main(process.argv.slice(2));

module.exports = { readSteps, applyKnown, summarise, renderSummaryMarkdown, renderHtml, escapeHtml };
