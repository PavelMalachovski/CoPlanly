/**
 * Tests for tools/manual-test-plan.js. Run with `node --test tools/test/*.test.js` (the CI
 * `invariants` job does); no dependencies.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const plan = require('../manual-test-plan.js');

const CHECKLIST = fs.readFileSync(
    path.join(__dirname, '..', '..', 'docs', 'DEVICE-CHECKLIST.md'), 'utf8');
const K = plan.K;

/** @param {string[]} files changed paths @return {string[]} the sections planned for them */
function sectionsFor(files) {
  return plan.planFor(files, CHECKLIST).items.map((i) => i.section);
}

test('every section a rule names exists in DEVICE-CHECKLIST.md', () => {
  const sections = plan.parseSections(CHECKLIST);
  for (const rule of plan.RULES) {
    for (const s of rule.sections) {
      assert.ok(sections.has(s), `rule "${rule.why}" names §${s}, which the checklist lacks`);
    }
  }
});

test('the checklist parses into its numbered sections', () => {
  const sections = plan.parseSections(CHECKLIST);
  assert.match(sections.get('2.1'), /^SEC-2/);
  assert.match(sections.get('7'), /account deletion/);
});

test('globs: ** spans segments, * stays within one, K/ is the Kotlin root', () => {
  assert.ok(plan.globToRegExp('functions/**').test('functions/test/a.test.js'));
  assert.ok(plan.globToRegExp('**/*DatePicker*')
      .test(K + 'presentation/childinfo/components/DatePickerDialog.kt'));
  assert.ok(plan.globToRegExp('*.md').test('README.md'));
  assert.ok(!plan.globToRegExp('*.md').test('docs/README.md'));
  assert.ok(plan.globToRegExp('K/data/local/**').test(K + 'data/local/dao/EventDao.kt'));
  assert.ok(plan.globToRegExp('app/src/main/res/values*/**')
      .test('app/src/main/res/values-cs/chat_strings.xml'));
});

test('database and encryption changes need the SEC-2 upgrade run', () => {
  assert.deepEqual(sectionsFor([K + 'data/local/CoPlanlyDatabase.kt']), ['2.1']);
  assert.deepEqual(
      sectionsFor(['app/schemas/com.coparently.app.data.local.CoPlanlyDatabase/37.json']), ['2.1']);
  assert.ok(sectionsFor([K + 'data/security/DatabaseKey.kt']).includes('2.1'));
});

test('chat changes need the cross-time-zone chat check', () => {
  assert.deepEqual(sectionsFor([K + 'presentation/chat/ChatScreen.kt']), ['5.1']);
});

test('functions changes need the owner-ops deploys, the paired checks and account deletion', () => {
  assert.deepEqual(sectionsFor(['functions/index.js']), ['0', '5.3', '7']);
  assert.deepEqual(sectionsFor(['functions/calendar-feed.js']), ['0', '5.3']);
});

test('strings need both language-picker checks', () => {
  assert.deepEqual(sectionsFor(['app/src/main/res/values-de/expenses_strings.xml']), ['3.6', '4.2']);
});

test('the push strings add the push check to the locale ones', () => {
  assert.deepEqual(sectionsFor(['app/src/main/res/values/push_strings.xml']), ['3.6', '3.7', '4.2']);
});

test('docs, CI and JVM tests need no phone and are not reported as unmapped', () => {
  const result = plan.planFor(['docs/ROADMAP.md', '.github/workflows/ci.yml',
    'app/src/test/java/com/coparently/app/FooTest.kt', 'tools/ci-report.js', 'CLAUDE.md'], CHECKLIST);
  assert.deepEqual(result.items, []);
  assert.deepEqual(result.unmapped, []);
});

test('a wire-format fixture change asks for the mixed-version check, a hand-written one does not', () => {
  assert.deepEqual(sectionsFor(['app/src/test/resources/wire/current/events/sync-upload.json']), ['5.3']);
  assert.deepEqual(sectionsFor(['app/src/test/resources/wire/events/older-build.json']), []);
});

test('an app source no rule claims is reported rather than dropped', () => {
  const file = K + 'presentation/guests/GuestScreen.kt';
  const result = plan.planFor([file], CHECKLIST);
  assert.deepEqual(result.items, []);
  assert.deepEqual(result.unmapped, [file]);
  assert.match(plan.toMarkdown(result), /no rule maps/);
});

test('items come out in checklist order, each listing its files once', () => {
  const result = plan.planFor([K + 'presentation/chat/A.kt', K + 'data/local/B.kt',
    K + 'presentation/chat/C.kt'], CHECKLIST);
  assert.deepEqual(result.items.map((i) => i.section), ['2.1', '5.1']);
  assert.equal(result.items[1].files.length, 2);
});

test('a section the checklist lost is reported as a stale mapping', () => {
  const result = plan.planFor([K + 'presentation/chat/A.kt'], '## 2. Something\n### 2.1 Other\n');
  assert.deepEqual(result.missing, ['5.1']);
  assert.match(plan.toMarkdown(result), /Mapping out of date/);
});

test('markdown: an empty plan says so, a link gets GitHub\'s anchor', () => {
  assert.match(plan.toMarkdown(plan.planFor([], CHECKLIST)), /None: nothing this PR changes/);
  const md = plan.toMarkdown(plan.planFor([K + 'data/local/X.kt'], CHECKLIST), {link: 'https://x/c.md'});
  assert.match(md, /\(https:\/\/x\/c\.md#21-sec-2--room-migrations-upgrade-over-real-plaintext-data--1p--do-this-first\)/);
  assert.equal(plan.anchorFor('7. Last: account deletion · 1P · **destructive**'),
      '7-last-account-deletion--1p--destructive');
});

test('a section the e2e job exercises says so, and says what is left for the phone', () => {
  const e2e = plan.e2eBySection({
    firestore: {family_documents: {e2e: ['TwoParentAttachmentsTest#aVault'], checklist: ['5.5']}},
    storage: {chat_attachments: {e2e: ['TwoParentAttachmentsTest'], checklist: ['5.5']}},
    push: {x: {exempt: 'none'}},
  });
  assert.deepEqual(e2e.get('5.5'), ['TwoParentAttachmentsTest']);
  const planned = plan.planFor([K + 'presentation/documents/DocumentsScreen.kt'], CHECKLIST, e2e);
  const item = planned.items.find((i) => i.section === '5.5');
  assert.ok(item, 'the documents screen maps to §5.5');
  assert.deepEqual(item.e2e, ['TwoParentAttachmentsTest']);
  assert.match(plan.toMarkdown(planned), /CI already runs the mechanism between two parents/);
});

test('every checklist section the coverage map names exists', () => {
  const sections = plan.parseSections(CHECKLIST);
  const coverage = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'e2e', 'coverage.json'), 'utf8'));
  for (const section of plan.e2eBySection(coverage).keys()) {
    assert.ok(sections.has(section), `tools/e2e/coverage.json names §${section}, which the checklist lacks`);
  }
});
