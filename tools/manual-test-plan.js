#!/usr/bin/env node
/**
 * Which sections of docs/DEVICE-CHECKLIST.md a pull request needs run on a real phone.
 *
 * CI proves what a JVM, an emulator and the Firebase emulators can prove. The checklist holds
 * the rest — an upgrade over data an older build wrote, a hardware Keystore, two phones in two
 * time zones — and it is 600 lines long, so nobody runs all of it for every pull request. This
 * maps the paths a pull request changes to the checklist sections those paths can break, and
 * prints a markdown checklist that the CI `report` job puts in the sticky PR comment under
 * "Manual checks this PR needs".
 *
 * The mapping is deliberately small and path-based. It errs towards listing a section: a check
 * run needlessly costs a few minutes on a phone, a check skipped is how "written but never run
 * on a device" happens. Changed app sources that no rule claims are listed separately rather
 * than silently dropped, so a gap in the table is visible in every comment it affects.
 *
 * Section titles are read from the checklist itself, by number, so renaming a heading needs no
 * edit here — but renumbering one does, and `tools/test/manual-test-plan.test.js` fails when a
 * rule names a section the checklist no longer has.
 *
 * Usage:
 *   node tools/manual-test-plan.js --base <sha> [--head HEAD] [--out plan.md]
 *   git diff --name-only main... | node tools/manual-test-plan.js --stdin
 *
 * No dependencies, like tools/check-invariants.js.
 */
'use strict';

const fs = require('fs');
const path = require('path');
const {execFileSync} = require('child_process');

const ROOT = path.resolve(__dirname, '..');
const CHECKLIST = 'docs/DEVICE-CHECKLIST.md';

/** Kotlin source root of the app, abbreviated `K/` in the patterns below. */
const K = 'app/src/main/java/com/coparently/app/';

/**
 * The mapping. `sections` are checklist section numbers ("2.1", or "7" for a top-level
 * section); `paths` are globs (`**` any depth, `*` within one segment), where a leading `K/`
 * stands for the app's Kotlin source root. Order is the order the checklist runs in.
 */
const RULES = [
  {
    sections: ['0'],
    why: 'server code or rules change: the owner-ops deploys come first',
    paths: ['functions/**', 'firestore.rules', 'storage.rules', 'firestore.indexes.json',
      'firebase.json'],
  },
  {
    sections: ['2.1'],
    why: 'database, encryption or schema',
    paths: ['K/data/local/**', 'K/data/security/**', 'K/di/DatabaseModule.kt', 'app/schemas/**',
      'app/src/androidTest/**/data/local/**'],
  },
  {
    sections: ['2.2'],
    why: 'first run: splash, sign-in, consent, onboarding, theme or manifest',
    paths: ['K/presentation/splash/**', 'K/presentation/auth/**', 'K/presentation/consent/**',
      'K/presentation/onboarding/**', 'K/domain/onboarding/**', 'K/presentation/MainActivity.kt',
      'K/presentation/common/NotificationPermission.kt',
      'K/presentation/common/PrivacyPolicyLink.kt', 'K/data/telemetry/**',
      'app/src/main/AndroidManifest.xml', 'app/src/main/res/values/themes.xml',
      'app/src/main/res/values-night/**'],
  },
  {
    sections: ['3.1'],
    why: 'a date picker or the dates it saves',
    paths: ['**/*DatePicker*', 'K/presentation/event/**', 'K/presentation/changerequests/**',
      'K/presentation/childinfo/**', 'K/presentation/pets/**', 'K/presentation/profile/**'],
  },
  {
    sections: ['3.2'],
    why: 'motion or navigation',
    paths: ['K/presentation/theme/Motion.kt', 'K/presentation/navigation/**',
      'K/presentation/calendar/MonthView.kt'],
  },
  {
    sections: ['3.3'],
    why: 'parent colours',
    paths: ['K/presentation/theme/ParentColors.kt', 'K/presentation/theme/ParentPalette.kt',
      'K/presentation/theme/Color.kt', 'K/presentation/common/ParentsSource.kt',
      'K/presentation/common/ParentPalette*', 'K/presentation/calendar/**'],
  },
  {
    sections: ['3.4'],
    why: 'holidays',
    paths: ['K/domain/holidays/**'],
  },
  {
    sections: ['3.5'],
    why: 'custody schedule or contact windows',
    paths: ['K/domain/custody/**', 'K/presentation/custody/**', 'K/data/repository/Custody*'],
  },
  {
    sections: ['3.11'],
    why: 'seasonal custody layers or holiday fairness',
    paths: ['K/domain/custody/SeasonalLayer.kt', 'K/domain/custody/HolidayFairness.kt',
      'K/domain/holidays/SchoolVacationSuggestions.kt', 'K/presentation/custody/Seasonal*',
      'K/presentation/custody/HolidayFairness*', 'K/data/repository/SeasonalLayerJson.kt'],
  },
  {
    sections: ['3.6', '4.2'],
    why: 'strings, locales or the language picker',
    paths: ['app/src/main/res/values*/**', 'app/src/main/res/xml/locales_config.xml',
      'K/presentation/settings/AppLanguage.kt'],
  },
  {
    sections: ['3.7'],
    why: 'push notifications',
    paths: ['K/data/remote/firebase/FcmService.kt', 'K/data/remote/firebase/PushPayload.kt',
      'K/data/notification/**', 'K/domain/notification/**', '**/*MessagingService*',
      'app/src/main/res/values*/push_strings.xml'],
  },
  {
    sections: ['3.8'],
    why: 'receipt OCR',
    paths: ['K/data/mlkit/**', 'K/domain/receipts/**'],
  },
  {
    sections: ['3.9'],
    why: 'a screen whose insets, keyboard or accessibility only a phone shows',
    paths: ['K/presentation/home/**', 'K/domain/home/**', 'K/presentation/common/**',
      'K/presentation/components/**', 'K/presentation/expenses/**', 'K/presentation/settings/**', 'K/presentation/sync/**'],
  },
  {
    sections: ['3.10'],
    why: 'photo upload or Storage rules',
    paths: ['storage.rules', 'K/presentation/pets/**', 'K/presentation/childinfo/**',
      '**/*Upload*'],
  },
  {
    sections: ['4.1'],
    why: 'R8, Gson models or the build itself',
    paths: ['app/proguard-rules.pro', 'app/build.gradle.kts', 'build.gradle.kts',
      'gradle.properties', 'gradle/**', 'settings.gradle.kts', 'K/data/sync/**',
      'tools/check-r8-mapping.js'],
  },
  {
    sections: ['5.1'],
    why: 'chat',
    paths: ['K/presentation/chat/**', 'K/domain/chat/**', 'K/data/chat/**', 'K/**/*Message*',
      'K/**/*Chat*', 'K/**/Conversation*'],
  },
  {
    sections: ['5.2'],
    why: 'families and the family switcher',
    paths: ['K/presentation/common/FamilySwitcher*', 'K/domain/family/**', 'K/data/family/**',
      'K/presentation/common/ParentsSource.kt'],
  },
  {
    sections: ['5.3'],
    why: 'pairing or anything the co-parent\'s phone reads',
    paths: ['K/domain/pairing/**', 'K/presentation/pairing/**', 'K/data/sync/**',
      'K/data/repository/**', 'K/data/remote/**', 'firestore.rules', 'functions/**'],
  },
  {
    sections: ['6'],
    why: 'export',
    paths: ['K/presentation/export/**', 'K/domain/export/**', 'K/data/export/**',
      'K/data/versions/**', 'app/src/main/res/xml/file_paths.xml'],
  },
  {
    sections: ['7'],
    why: 'account deletion',
    paths: ['**/*AccountDeletion*', 'functions/index.js', 'K/data/session/**'],
  },
];

/**
 * Paths whose change needs no phone at all. They are neither mapped nor reported as unmapped.
 */
const NO_DEVICE = ['docs/**', '**/*.md', '.github/**', 'tools/**', 'firestore-tests/**',
  'app/src/test/**', 'app/config/detekt/**', '.cursor/**', '.gitignore', 'LICENSE'];

/**
 * @param {string} glob a pattern from RULES or NO_DEVICE
 * @return {RegExp} the anchored regular expression it stands for
 */
function globToRegExp(glob) {
  const expanded = glob.startsWith('K/') ? K + glob.slice(2) : glob;
  let re = '';
  for (let i = 0; i < expanded.length; i++) {
    const c = expanded[i];
    if (c === '*' && expanded[i + 1] === '*') {
      // `**/` matches zero or more whole segments; a trailing `**` matches anything.
      if (expanded[i + 2] === '/') {
        re += '(?:.*/)?';
        i += 2;
      } else {
        re += '.*';
        i += 1;
      }
    } else if (c === '*') {
      re += '[^/]*';
    } else {
      re += c.replace(/[.+?^${}()|[\]\\]/g, '\\$&');
    }
  }
  return new RegExp('^' + re + '$');
}

/**
 * Reads the numbered sections of the checklist: "## 7. Last: …" is section "7",
 * "### 2.1 SEC-2 …" is "2.1".
 * @param {string} markdown the checklist
 * @return {Map<string, string>} section number → heading text, markdown emphasis removed
 */
function parseSections(markdown) {
  const sections = new Map();
  for (const line of markdown.split(/\r?\n/)) {
    const m = /^#{2,3}\s+(\d+(?:\.\d+)?)\.?\s+(.*)$/.exec(line);
    if (m) sections.set(m[1], m[2].replace(/\*\*|`/g, '').trim());
  }
  return sections;
}

/**
 * GitHub's heading anchor: lower case, punctuation other than `-` and `_` removed, each space a
 * hyphen (so "a · b" becomes "a--b").
 * @param {string} heading the full heading text after the hashes
 * @return {string} the anchor, without `#`
 */
function anchorFor(heading) {
  return heading.replace(/\*\*|`/g, '').trim().toLowerCase()
      .replace(/[^\p{L}\p{N}\s_-]/gu, '').replace(/ /g, '-');
}

/**
 * The heart of it, pure so it can be tested.
 * @param {string[]} files changed paths, repository-relative
 * @param {string} checklist the checklist's markdown
 * @return {{items: {section: string, title: string, why: string[], files: string[]}[],
 *   unmapped: string[], missing: string[]}} the plan
 */
function planFor(files, checklist) {
  const sections = parseSections(checklist);
  const compiled = RULES.map((r) => ({...r, res: r.paths.map(globToRegExp)}));
  const noDevice = NO_DEVICE.map(globToRegExp);
  const bySection = new Map();
  const unmapped = [];
  const missing = new Set();

  for (const file of files) {
    // Documentation needs no phone, wherever it lives (functions/README.md is not server code).
    if (/\.md$/.test(file)) continue;
    let claimed = false;
    for (const rule of compiled) {
      if (!rule.res.some((re) => re.test(file))) continue;
      claimed = true;
      for (const s of rule.sections) {
        if (!sections.has(s)) {
          missing.add(s);
          continue;
        }
        const item = bySection.get(s) || {section: s, title: sections.get(s), why: [], files: []};
        if (!item.why.includes(rule.why)) item.why.push(rule.why);
        if (!item.files.includes(file)) item.files.push(file);
        bySection.set(s, item);
      }
    }
    if (!claimed && !noDevice.some((re) => re.test(file)) && file.startsWith('app/src/main/')) {
      unmapped.push(file);
    }
  }

  const order = [...sections.keys()];
  const items = [...bySection.values()].sort((a, b) => order.indexOf(a.section) - order.indexOf(b.section));
  return {items, unmapped, missing: [...missing]};
}

/**
 * @param {ReturnType<typeof planFor>} plan the plan
 * @param {{link?: string}} [opts] link: URL of the checklist at the pull request's head
 * @return {string} markdown for the PR comment
 */
function toMarkdown(plan, opts = {}) {
  const lines = ['### Manual checks this PR needs', ''];
  if (plan.items.length === 0) {
    lines.push('None: nothing this PR changes maps to a section of `' + CHECKLIST + '`.');
  } else {
    lines.push(`From \`${CHECKLIST}\`, chosen by the paths this PR changes ` +
        '(`tools/manual-test-plan.js`). Run them on a phone before merging, or say why not.', '');
    for (const item of plan.items) {
      const label = `§${item.section} ${item.title}`;
      const heading = opts.link ?
        `[${label}](${opts.link}#${anchorFor(`${item.section}${item.section.includes('.') ? '' : '.'} ${item.title}`)})` :
        label;
      const shown = item.files.slice(0, 3).map((f) => '`' + f.replace(K, '…/') + '`').join(', ');
      const more = item.files.length > 3 ? ` and ${item.files.length - 3} more` : '';
      lines.push(`- [ ] ${heading}  `, `  ${item.why.join('; ')}: ${shown}${more}`);
    }
  }
  if (plan.unmapped.length) {
    lines.push('', `<details><summary>${plan.unmapped.length} changed app file(s) no rule maps ` +
        'to a device check</summary>', '');
    for (const f of plan.unmapped.slice(0, 30)) lines.push('- `' + f.replace(K, '…/') + '`');
    if (plan.unmapped.length > 30) lines.push(`- … and ${plan.unmapped.length - 30} more`);
    lines.push('', 'Decide whether one applies, and extend `RULES` in ' +
        '`tools/manual-test-plan.js` if it does.', '</details>');
  }
  if (plan.missing.length) {
    lines.push('', `**Mapping out of date:** \`tools/manual-test-plan.js\` names section(s) ` +
        `${plan.missing.join(', ')}, which \`${CHECKLIST}\` no longer has.`);
  }
  return lines.join('\n') + '\n';
}

/**
 * @param {string[]} argv command-line arguments
 * @return {Record<string, string|boolean>} parsed `--key value` / `--flag` pairs
 */
function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    const key = argv[i].replace(/^--/, '');
    if (argv[i + 1] !== undefined && !argv[i + 1].startsWith('--')) out[key] = argv[++i];
    else out[key] = true;
  }
  return out;
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  let files;
  if (args.stdin) {
    files = fs.readFileSync(0, 'utf8').split(/\r?\n/).filter(Boolean);
  } else if (args.base) {
    const range = `${args.base}...${args.head || 'HEAD'}`;
    files = execFileSync('git', ['diff', '--name-only', range], {cwd: ROOT, encoding: 'utf8'})
        .split(/\r?\n/).filter(Boolean);
  } else {
    console.error('usage: manual-test-plan.js --base <sha> [--head <ref>] [--out file] | --stdin');
    process.exit(2);
  }
  const checklist = fs.readFileSync(path.join(ROOT, CHECKLIST), 'utf8');
  const repo = process.env.GITHUB_REPOSITORY;
  const sha = args.ref || process.env.GITHUB_SHA;
  const link = repo && sha ? `https://github.com/${repo}/blob/${sha}/${CHECKLIST}` : undefined;
  const markdown = toMarkdown(planFor(files, checklist), {link});
  if (args.out) fs.writeFileSync(args.out, markdown);
  else process.stdout.write(markdown);
}

if (require.main === module) main();

module.exports = {RULES, NO_DEVICE, K, globToRegExp, parseSections, anchorFor, planFor, toMarkdown};
