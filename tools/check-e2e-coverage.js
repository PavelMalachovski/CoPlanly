#!/usr/bin/env node
/**
 * Every feature two phones share has a two-parent test, or says in writing why it cannot.
 *
 * "Shared between two phones" is read off the places that define it, never off a list someone
 * has to remember to extend:
 *
 *  - **Firestore collections**: every top-level `match /<name>/{…}` in `firestore.rules`. A
 *    collection a client can reach has a rule block, so a new one arrives here the day it is
 *    written.
 *  - **Storage prefixes**: every `match /<name>/…` in `storage.rules`.
 *  - **Push types**: every snake_case value in `PushPayload.kt` (field names there are camelCase
 *    or a single word, so the two cannot be confused).
 *  - **Callables and HTTPS functions**: every `exports.<name> = functions….https.onCall/onRequest`
 *    in `functions/*.js`.
 *
 * Each one must appear in `tools/e2e/coverage.json`, either with `e2e` — the two-parent tests in
 * `app/src/androidTest/java/com/coparently/app/e2e/` that exercise it, as `Class` or
 * `Class#method`, each of which must exist — or with `exempt`, a sentence saying why no emulator
 * run can cover it and what does instead. An entry for something that no longer exists fails too,
 * so the map cannot drift into describing a codebase that is gone.
 *
 * What this cannot know is whether a named test really exercises the feature; review decides
 * that, and the entry is where a reviewer looks.
 *
 * Exports `check(root)` for `tools/test/check-e2e-coverage.test.js`; exits non-zero from the CLI.
 */

const fs = require('fs');
const path = require('path');

const E2E_DIR = 'app/src/androidTest/java/com/coparently/app/e2e';
const COVERAGE = 'tools/e2e/coverage.json';
const PUSH_PAYLOAD = 'app/src/main/java/com/coparently/app/data/remote/firebase/PushPayload.kt';
const KINDS = ['firestore', 'storage', 'push', 'functions'];

/** Strips `//` and `/* … *\/` comments, which name collections in prose. */
function stripComments(text) {
  return text.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1');
}

/** Top-level collections in `firestore.rules`: the `match` blocks directly under `documents`. */
function firestoreCollections(rules) {
  const text = stripComments(rules);
  const start = text.indexOf('match /databases/{database}/documents');
  if (start < 0) throw new Error('firestore.rules: no documents block');
  const names = new Set();
  let depth = 0;
  // Walk the braces from the documents block; a top-level collection sits at depth 1 inside it.
  const header = 'match /databases/{database}/documents';
  const body = text.slice(text.indexOf('{', start + header.length) + 1);
  const re = /match\s+\/([a-z_]+)\/\{|[{}]/g;
  let m;
  while ((m = re.exec(body)) !== null) {
    if (m[1]) {
      if (depth === 0) names.add(m[1]);
      // The token swallowed the `{` of the path wildcard; its `}` comes next and decrements.
      depth += 1;
    } else if (m[0] === '{') {
      depth += 1;
    } else {
      depth -= 1;
      if (depth < 0) break;
    }
  }
  return names;
}

/** Top-level prefixes in `storage.rules`, under `match /b/{bucket}/o`. */
function storagePrefixes(rules) {
  const names = new Set();
  for (const m of stripComments(rules).matchAll(/match\s+\/([a-z_]+)\//g)) {
    if (m[1] !== 'b') names.add(m[1]);
  }
  return names;
}

/** The push types: snake_case string constants in `PushPayload`. */
function pushTypes(source) {
  const names = new Set();
  for (const m of stripComments(source).matchAll(/const val [A-Z_]+ = "([a-z]+(?:_[a-z]+)+)"/g)) {
    names.add(m[1]);
  }
  return names;
}

/** Callable and HTTPS functions exported from `functions/*.js`. */
function httpsFunctions(sources) {
  const names = new Set();
  const re = /exports\.(\w+)\s*=\s*functions((?:(?!exports\.)[\s\S]){0,240}?)\.https\.on(Call|Request)\b/g;
  for (const source of sources) {
    for (const m of stripComments(source).matchAll(re)) names.add(m[1]);
  }
  return names;
}

/** Test classes in the e2e directory, each with the names of its test methods. */
function e2eTests(root) {
  const dir = path.join(root, E2E_DIR);
  const tests = new Map();
  if (!fs.existsSync(dir)) return tests;
  for (const file of fs.readdirSync(dir).filter((f) => f.endsWith('.kt'))) {
    const source = fs.readFileSync(path.join(dir, file), 'utf8');
    const methods = new Set();
    for (const m of source.matchAll(/@Test\s+fun\s+`?([A-Za-z0-9_ ,'-]+?)`?\s*\(/g)) methods.add(m[1]);
    if (methods.size > 0) tests.set(file.replace(/\.kt$/, ''), methods);
  }
  return tests;
}

/** What `root` shares between two phones, by kind. */
function discover(root) {
  const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');
  const functionSources = fs.readdirSync(path.join(root, 'functions'))
    .filter((f) => f.endsWith('.js'))
    .map((f) => read(path.join('functions', f)));
  return {
    firestore: firestoreCollections(read('firestore.rules')),
    storage: storagePrefixes(read('storage.rules')),
    push: pushTypes(read(PUSH_PAYLOAD)),
    functions: httpsFunctions(functionSources),
  };
}

/**
 * Every problem with `root`'s coverage map, as sentences. Empty when every shared feature is
 * either tested or exempted with a reason.
 */
function check(root) {
  const problems = [];
  const coveragePath = path.join(root, COVERAGE);
  if (!fs.existsSync(coveragePath)) return [`${COVERAGE} is missing`];
  const coverage = JSON.parse(fs.readFileSync(coveragePath, 'utf8'));
  const found = discover(root);
  const tests = e2eTests(root);

  for (const kind of KINDS) {
    const map = coverage[kind] || {};
    for (const name of [...found[kind]].sort()) {
      const entry = map[name];
      if (!entry) {
        problems.push(
          `${kind} "${name}" is shared between phones but has no entry in ${COVERAGE}: ` +
          'name the two-parent test that covers it ("e2e"), or say why none can ("exempt")',
        );
        continue;
      }
      const named = Array.isArray(entry.e2e) ? entry.e2e : [];
      const exempt = typeof entry.exempt === 'string' ? entry.exempt.trim() : '';
      if (named.length === 0 && !exempt) {
        problems.push(`${kind} "${name}": an entry needs a non-empty "e2e" list or an "exempt" reason`);
      }
      if (named.length > 0 && exempt) {
        problems.push(`${kind} "${name}": either "e2e" or "exempt", not both`);
      }
      for (const ref of named) {
        const [cls, method] = ref.split('#');
        if (!tests.has(cls)) {
          problems.push(`${kind} "${name}": no e2e test class ${cls} in ${E2E_DIR}`);
        } else if (method && !tests.get(cls).has(method)) {
          problems.push(`${kind} "${name}": ${cls} has no @Test ${method}`);
        }
      }
    }
    for (const name of Object.keys(map)) {
      if (!found[kind].has(name)) {
        problems.push(`${kind} "${name}" is in ${COVERAGE} but no longer exists — remove the entry`);
      }
    }
  }
  for (const kind of Object.keys(coverage)) {
    if (!KINDS.includes(kind) && !kind.startsWith('_')) {
      problems.push(`${COVERAGE}: unknown section "${kind}" (expected ${KINDS.join(', ')})`);
    }
  }
  return problems;
}

module.exports = {check, discover, firestoreCollections, storagePrefixes, pushTypes, httpsFunctions};

if (require.main === module) {
  const problems = check(path.resolve(__dirname, '..'));
  if (problems.length > 0) {
    console.error(`Two-parent coverage: ${problems.length} problem(s)\n`);
    for (const p of problems) console.error(`  - ${p}`);
    process.exit(1);
  }
  console.log('Two-parent coverage: every shared feature is tested or exempted with a reason.');
}
