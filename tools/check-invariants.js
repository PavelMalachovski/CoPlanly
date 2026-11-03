#!/usr/bin/env node
/**
 * Offline checks for invariants CLAUDE.md states and no other CI job enforces.
 *
 * All of them are pure text comparisons over resources and sources, so they need neither an
 * Android SDK nor a device — which is the point. Each of them has a reason it is not already
 * covered:
 *
 *  1. **Locale completeness.** `MissingTranslation` is *disabled* in `app/build.gradle.kts`,
 *     not demoted — a disabled check reports nothing under any severity, so a key added to
 *     `values/` alone falls back to English at runtime and nothing says so. CLAUDE.md tells a
 *     reader to verify by grep; this is that grep, over every key at once, and it also catches
 *     a duplicate key, which the lint check never would.
 *  2. **Format-argument agreement.** A translation that drops or renumbers a `%1$s` throws
 *     `IllegalFormatException` on the device of whoever reads that language, and only there.
 *  3. **Push-type agreement (SEC-3, CLAUDE.md item 15).** A type has to be named in four
 *     places; missing from any one of them it is a push that silently never appears, and a
 *     server-only type that leaks into the client allow-list is a forgeable notification.
 *  4. **No Gson in production code.** Until MON-24 Gson derived the stored JSON keys from Kotlin
 *     *field names* by reflection, and R8 renamed fields it could not see being read: a model no
 *     `-keepclassmembers ... { <fields>; }` rule covered was written with obfuscated keys in
 *     release builds only. It shipped once (see `app/proguard-rules.pro`) and was found a second
 *     time in `DayOverride`. `StoredJson` replaced it, byte for byte, with serializers that carry
 *     their keys as literals; this check keeps Gson from coming back into the app or the shared
 *     module's code (tests keep it as the oracle `GsonParityTest` compares with).
 *  5. **Stored models are probed at runtime.** Every `@Serializable` class or enum in production
 *     code — a model whose JSON `StoredJson` writes — must have a case in the R8 runtime probe
 *     (`app/src/r8Test/`), which the `r8-runtime` CI job runs inside a minified build on an
 *     emulator; `tools/check-r8-probe.js` then requires each case to pass.
 *  6. **The Google scope the app asks for is the one it discloses.** Google verifies the OAuth
 *     consent screen against the privacy policy and the home page, and a parent reads them to
 *     decide whether to connect. `GoogleCalendarAccess.SCOPE` must be the only Calendar scope
 *     `docs/legal/PRIVACY-POLICY.md`, its hosted copy and `web/index.html` name, so widening the
 *     scope in code fails until the texts say so too.
 *
 *  7. **The strings read the same on Android and in Compose Multiplatform.** Since MON-24 phase 2
 *     (Task 9) one set of files is read twice: by aapt2 and `String.format` into the app's
 *     `R.string`, and by Compose Multiplatform 1.9.3's resource plugin into `:shared-ui`'s `Res`.
 *     The plugin unescapes only `\n`, `\t`, `\uXXXX` and `\\`, keeps whitespace as written, and
 *     its formatter replaces only `%<n>$s` and `%<n>$d`; aapt2 also takes `\'`, `\"`, `\@` and
 *     `\?`, drops an unescaped `"`, collapses and trims whitespace, and `String.format` turns `%%`
 *     into `%`. A text that uses any of those reads differently in the two, so it is written the
 *     way both read alike (`\u0027` for an apostrophe, `\u0022` for a straight quote).
 *     `StringsParityTest` and `PluralsParityTest` (Robolectric, in the app) compare the two
 *     readings of every key; this is their offline half, which fails before CI runs them.
 *
 * Exits non-zero listing every problem found, so it can gate a build.
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
// The string resources live in `:shared-ui` as Compose Multiplatform resources since MON-24
// phase 2 (Task 9); the app's Android copy is generated from them unchanged.
const RES_DIR = 'shared-ui/src/commonMain/composeResources';
const RES = path.join(ROOT, RES_DIR);
const BASE_DIR = 'values';
const LOCALE_DIRS = ['values-cs', 'values-de', 'values-ru', 'values-uk'];

const problems = [];
const fail = (msg) => problems.push(msg);
const read = (p) => fs.readFileSync(path.join(ROOT, p), 'utf8');

// XML comments carry prose — apostrophes, percent signs, stray tags — that must never be read
// as resource content. Strip them before any parsing.
const stripComments = (xml) => xml.replace(/<!--[\s\S]*?-->/g, '');

/**
 * Every `<string>`, `<plurals>` and `<string-array>` in one `values*` directory.
 *
 * Plurals are keyed with a `plurals:` prefix so a plural and a string of the same name stay
 * distinct, and their whole body is kept: a format argument may appear in any one `<item>`.
 */
function collectStrings(dir) {
  const out = new Map();
  const dirPath = path.join(RES, dir);
  if (!fs.existsSync(dirPath)) {
    fail(`resource directory is missing: ${dir}`);
    return out;
  }
  for (const file of fs.readdirSync(dirPath).filter((f) => f.endsWith('.xml'))) {
    const xml = stripComments(fs.readFileSync(path.join(dirPath, file), 'utf8'));

    const strings = /<string\s+([^>]*?)name="([^"]+)"([^>]*?)>([\s\S]*?)<\/string>/g;
    for (let m; (m = strings.exec(xml)); ) {
      const attrs = m[1] + m[3];
      add(out, m[2], { file, body: m[4], translatable: !/translatable\s*=\s*"false"/.test(attrs) }, dir);
    }

    const plurals = /<plurals\s+([^>]*?)name="([^"]+)"([^>]*?)>([\s\S]*?)<\/plurals>/g;
    for (let m; (m = plurals.exec(xml)); ) {
      const attrs = m[1] + m[3];
      add(out, `plurals:${m[2]}`, { file, body: m[4], translatable: !/translatable\s*=\s*"false"/.test(attrs) }, dir);
    }

    const arrays = /<string-array\s+([^>]*?)name="([^"]+)"([^>]*?)>([\s\S]*?)<\/string-array>/g;
    for (let m; (m = arrays.exec(xml)); ) {
      const attrs = m[1] + m[3];
      add(out, `array:${m[2]}`, { file, body: m[4], translatable: !/translatable\s*=\s*"false"/.test(attrs) }, dir);
    }
  }
  return out;
}

function add(map, name, value, dir) {
  if (map.has(name)) {
    fail(`duplicate resource "${name}" in ${dir}: ${map.get(name).file} and ${value.file}`);
  }
  map.set(name, value);
}

/**
 * The set of format arguments a resource body uses.
 *
 * Positional (`%1$s`) and implicit (`%s`) arguments are counted separately because Android
 * treats them as different things; `%%` is a literal percent and not an argument at all.
 */
function formatArgs(body) {
  const args = new Set();
  const re = /%(?:(\d+)\$[a-zA-Z]|%|([a-zA-Z]))/g;
  let implicit = 0;
  for (let m; (m = re.exec(body)); ) {
    if (m[1]) args.add(`#${m[1]}`);
    else if (m[2]) args.add(`implicit#${++implicit}`);
  }
  return args;
}

// ---- 1 + 2: locales ---------------------------------------------------------

function checkLocales() {
  const base = collectStrings(BASE_DIR);
  let translatable = 0;
  for (const v of base.values()) if (v.translatable) translatable++;

  for (const dir of LOCALE_DIRS) {
    const loc = collectStrings(dir);

    for (const [name, v] of base) {
      if (!v.translatable) continue;
      if (!loc.has(name)) {
        fail(`missing translation: "${name}" (${v.file}) has no ${dir} entry`);
        continue;
      }
      const baseArgs = formatArgs(v.body);
      const locArgs = formatArgs(loc.get(name).body);
      const missing = [...baseArgs].filter((a) => !locArgs.has(a));
      const extra = [...locArgs].filter((a) => !baseArgs.has(a));
      if (missing.length || extra.length) {
        fail(
          `format arguments disagree for "${name}" in ${dir}: ` +
            `base uses {${[...baseArgs].join(', ')}}, ${dir} uses {${[...locArgs].join(', ')}}`
        );
      }
    }

    for (const name of loc.keys()) {
      if (!base.has(name)) fail(`orphan resource: "${name}" exists in ${dir} but not in ${BASE_DIR}`);
    }
  }

  console.log(`locales: ${base.size} base resources (${translatable} translatable) x ${LOCALE_DIRS.length} locales`);
}

// ---- 3: push types ----------------------------------------------------------

const PAYLOAD_KT = 'shared-data/src/commonMain/kotlin/com/coparently/app/data/remote/firebase/PushPayload.kt';
const SERVICE_KT = 'app/src/main/java/com/coparently/app/data/remote/firebase/PushNotifier.kt';
const RULES = 'firestore.rules';
const PUSH_STRINGS = `${RES_DIR}/values/push_strings.xml`;

/**
 * `chat_message` is composed by a branch of its own in `PushNotifier.compose()`,
 * ahead of the `PUSH_TEXT` lookup, because its text is not a frame: the title is who sent the
 * message and the body is what they wrote, and only the Cloud Function that saw the message
 * could supply either. It is renderable without a `PUSH_TEXT` entry — the one type that is.
 */
const COMPOSED_WITHOUT_SPEC = new Set(['chat_message']);

/** Types declared between two `// ---- <marker> ----` section headers in PushPayload.kt. */
function typesInSection(source, marker) {
  const start = source.indexOf(marker);
  if (start === -1) {
    fail(`${PAYLOAD_KT} no longer contains the section marker "${marker}" this check parses`);
    return { values: new Set(), constants: new Map() };
  }
  const rest = source.slice(start + marker.length);
  const end = rest.search(/\n\s*\/\/ ----/);
  const body = end === -1 ? rest : rest.slice(0, end);
  const values = new Set();
  const constants = new Map();
  const re = /const val ([A-Z_0-9]+)\s*=\s*"([a-z_]+)"/g;
  for (let m; (m = re.exec(body)); ) {
    constants.set(m[1], m[2]);
    values.add(m[2]);
  }
  return { values, constants };
}

function checkPushTypes() {
  const payload = read(PAYLOAD_KT);
  const service = read(SERVICE_KT);
  const rules = read(RULES);
  const strings = stripComments(read(PUSH_STRINGS));

  const client = typesInSection(payload, '// ---- types a client may produce');
  const server = typesInSection(payload, '// ---- types only a Cloud Function may produce');

  // The CLIENT_TYPES set the sending code reads.
  const setBlock = payload.match(/val CLIENT_TYPES:\s*Set<String>\s*=\s*setOf\(([\s\S]*?)\)/);
  const clientSet = new Set();
  if (!setBlock) fail(`${PAYLOAD_KT}: could not find the CLIENT_TYPES set`);
  else {
    for (const m of setBlock[1].matchAll(/([A-Z_0-9]+)/g)) {
      const value = client.constants.get(m[1]) || server.constants.get(m[1]);
      if (!value) fail(`CLIENT_TYPES names ${m[1]}, which is not a declared push type`);
      else clientSet.add(value);
    }
  }

  // The allow-list firestore.rules actually enforces.
  const ruleBlock = rules.match(/function isClientPushType\(type\)\s*\{[\s\S]*?return type in \[([\s\S]*?)\];/);
  const ruleAllowed = new Set();
  if (!ruleBlock) fail(`${RULES}: could not find isClientPushType's allow-list`);
  else for (const m of ruleBlock[1].matchAll(/'([a-z_]+)'/g)) ruleAllowed.add(m[1]);

  // Types the receiving device can put words to.
  const rendered = new Set();
  for (const m of service.matchAll(/PushPayload\.([A-Z_0-9]+)\s+to\s+PushTextSpec/g)) {
    const value = client.constants.get(m[1]) || server.constants.get(m[1]);
    if (value) rendered.add(value);
  }
  // Match the branch itself, not a mention of the type: `chat_message` appears throughout the
  // file's prose, so an `includes` on the name would go on passing after the branch was deleted
  // — which is exactly the regression this is here to catch.
  if (/if\s*\(type == TYPE_CHAT_MESSAGE\)/.test(service)) {
    for (const t of COMPOSED_WITHOUT_SPEC) rendered.add(t);
  }

  const declared = [...client.values, ...server.values];

  for (const type of declared) {
    if (!rendered.has(type)) {
      fail(`push type "${type}" is declared but PushNotifier has no wording for it — the push would be silently dropped on arrival`);
    }
  }

  for (const type of client.values) {
    if (!clientSet.has(type)) fail(`client push type "${type}" is missing from PushPayload.CLIENT_TYPES — it would fail to enqueue`);
    if (!ruleAllowed.has(type)) fail(`client push type "${type}" is missing from isClientPushType in ${RULES} — the write would be denied`);
  }

  for (const type of server.values) {
    if (clientSet.has(type)) fail(`SECURITY: server-only push type "${type}" appears in PushPayload.CLIENT_TYPES`);
    if (ruleAllowed.has(type)) fail(`SECURITY: server-only push type "${type}" appears in the ${RULES} client allow-list — a co-parent could forge it`);
  }

  for (const type of ruleAllowed) {
    if (!client.values.has(type)) fail(`${RULES} allows push type "${type}", which PushPayload does not declare as a client type`);
  }

  // Every string resource the service names must exist.
  const defined = new Set([...strings.matchAll(/<(?:string|plurals)\s+[^>]*name="([^"]+)"/g)].map((m) => m[1]));
  const referenced = new Set(
    [...service.matchAll(/R\.(?:string|plurals)\.(push_[a-z_0-9]+)/g)].map((m) => m[1])
  );
  for (const name of referenced) {
    if (!defined.has(name)) fail(`PushNotifier references R.string/plurals.${name}, which ${PUSH_STRINGS} does not define`);
  }

  console.log(
    `push types: ${client.values.size} client, ${server.values.size} server-only, ` +
      `${ruleAllowed.size} in the rules allow-list, ${referenced.size} string resources referenced`
  );
}

// ---- 4: no Gson in production code -----------------------------------------

const SOURCE_ROOT = 'app/src/main/java';
/**
 * The shared modules' production code (MON-24): the domain moved to `shared/` with its package
 * unchanged, and its models are stored too (`DayOverride`, the child and pet records), and the data
 * layer follows into `shared-data/` from phase 2, so checks 4 and 5 read both beside the app's.
 * The platform source sets count: they ship as much as `commonMain` does.
 */
const SHARED_SOURCE_ROOTS = ['shared', 'shared-data'].flatMap((module) =>
  ['commonMain', 'androidMain', 'iosMain'].map((set) => `${module}/src/${set}/kotlin`)
);

/** Every Kotlin file under the source root, recursively. */
function kotlinFiles(dir = SOURCE_ROOT, out = []) {
  const abs = path.join(ROOT, dir);
  if (!fs.existsSync(abs)) return out;
  for (const entry of fs.readdirSync(abs, { withFileTypes: true })) {
    const rel = path.join(dir, entry.name);
    if (entry.isDirectory()) kotlinFiles(rel, out);
    else if (entry.name.endsWith('.kt')) out.push(rel);
  }
  return out;
}

/** Every production Kotlin file, the app's and the shared module's. */
function productionFiles() {
  return [...kotlinFiles(), ...SHARED_SOURCE_ROOTS.flatMap((root) => kotlinFiles(root))];
}

/**
 * The production files that import Gson, as path -> the first import found.
 *
 * Google's calendar client brought Gson for its own `GsonFactory`
 * (`com.google.api.client.json.gson`), which was not an import of `com.google.gson` and was not
 * counted; it left with that client in MON-24 phase 2.
 */
function gsonImports(files = productionFiles()) {
  const found = new Map();
  for (const file of files) {
    const m = /^import\s+(com\.google\.gson\.[\w.]+)/m.exec(fs.readFileSync(path.join(ROOT, file), 'utf8'));
    if (m) found.set(file, m[1]);
  }
  return found;
}

function checkNoGson() {
  const found = gsonImports();
  for (const [file, imported] of found) {
    fail(
      `${file} imports ${imported} — the app's JSON goes through StoredJson (shared/…/domain/serialization), ` +
        'which writes the bytes Gson wrote; Gson left production code in MON-24 and stays as a test oracle only'
    );
  }
  console.log(`gson: ${found.size} production file(s) import it`);
}

// ---- 5: every stored model is probed at runtime -----------------------------

const PROBE_SOURCES = 'app/src/r8Test/java';

/**
 * Every `@Serializable` class, data class or enum in production code, as fully-qualified name ->
 * the file that declares it: the models whose JSON `StoredJson` writes. Exported for
 * `tools/check-r8-probe.js`, which requires the runtime probe to have passed a case for each.
 *
 * A nested declaration is named by its own simple name under the file's package, as the probe's
 * labels have always named them; none is nested today.
 */
function storedJsonModels() {
  const models = new Map();
  for (const file of productionFiles()) {
    const src = fs.readFileSync(path.join(ROOT, file), 'utf8');
    if (!src.includes('@Serializable')) continue;
    const pkg = src.match(/^package\s+([\w.]+)/m);
    if (!pkg) continue;
    // `@Serializable` (or `@Serializable(with = …)`) on its own line or before the modifiers, then
    // the declaration. A `@Serializable(with = …)` on a property is not followed by `class`.
    const decl =
      /@Serializable(?:\([^)]*\))?\s+(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|internal|private|data|enum|sealed|abstract|open|value)\s+)*(?:class|object|interface)\s+(\w+)/g;
    for (let m; (m = decl.exec(src)); ) {
      const fqcn = `${pkg[1]}.${m[1]}`;
      if (!models.has(fqcn)) models.set(fqcn, file);
    }
  }
  return models;
}

/**
 * Every stored model must be named, as a string literal, in the R8 runtime probe
 * (`app/src/r8Test/`, run by the `r8-runtime` CI job).
 *
 * The probe writes each model through the app's own call site inside the minified build and checks
 * the keys and the read-back. It reports each case under the source's fully-qualified name, a
 * literal because R8 renames the classes themselves, and the CI check requires a passing case for
 * every type found here. This catches the omission a PR earlier, without an emulator: a new model
 * must arrive with a probe case.
 */
function checkProbeCoverage() {
  const dir = path.join(ROOT, PROBE_SOURCES);
  if (!fs.existsSync(dir)) {
    fail(`${PROBE_SOURCES} is missing — the R8 runtime probe lives there`);
    return;
  }
  const probeSource = kotlinFiles(PROBE_SOURCES)
    .map((f) => fs.readFileSync(path.join(ROOT, f), 'utf8'))
    .join('\n');
  const models = storedJsonModels();
  if (!models.size) fail('no @Serializable model found in production code — the discovery in check 5 is broken');
  for (const [fqcn, file] of models) {
    if (!probeSource.includes(`"${fqcn}"`)) {
      fail(
        `${fqcn} is @Serializable (${file}) but the R8 runtime probe (${PROBE_SOURCES}) has no ` +
          `case for it — add one to R8JsonProbe that writes it through the production call site and ` +
          `reports it as "${fqcn}"`
      );
    }
  }
  console.log(`r8 probe: ${models.size} stored model(s) named in ${PROBE_SOURCES}`);
}

// ---- 6. the Google scope the app asks for is the one it discloses ------------

const GOOGLE_SCOPE_SOURCE = 'shared-data/src/commonMain/kotlin/com/coparently/app/data/remote/google/GoogleCalendarAccess.kt';

/** The documents that tell a parent, and Google's reviewer, what the app asks of Google Calendar. */
const GOOGLE_SCOPE_DOCUMENTS = ['docs/legal/PRIVACY-POLICY.md', 'web/privacy/index.html', 'web/index.html'];

/**
 * Every Google Calendar scope a text names, by its short form (`calendar.events.owned.readonly`).
 * A scope's dotted parts come from Google's own vocabulary, so a host such as `calendar.google.com`
 * is not one.
 */
const CALENDAR_SCOPE_TOKEN =
  /(?<![\w.-])calendar(?:\.(?:acls|app|calendarlist|calendars|created|events|freebusy|owned|public|readonly|settings))*(?![\w-]|\.\w)/g;

/**
 * What is wrong with the documents' account of the scope: each must name the scope [source]
 * declares and no other Calendar scope. Bare "calendar" is prose, not a scope, so it is not
 * counted — which is also why the full `calendar` scope can never pass a document that promises
 * read-only access.
 *
 * @param {string} source `GoogleCalendarAccess.kt`.
 * @param {!Object<string, string>} documents Text by repository path.
 * @return {string[]} One line per problem; empty when the disclosure holds.
 */
function googleScopeProblems(source, documents) {
  const declared = /const val SCOPE: String = "https:\/\/www\.googleapis\.com\/auth\/([a-z.]+)"/.exec(source);
  if (!declared) {
    return [`${GOOGLE_SCOPE_SOURCE}: no \`const val SCOPE: String = "https://www.googleapis.com/auth/…"\` ` +
      'literal, so the disclosure check cannot tell which scope the app asks for'];
  }
  const scope = declared[1];
  const out = [];
  for (const [file, text] of Object.entries(documents)) {
    const named = [...new Set((text.match(CALENDAR_SCOPE_TOKEN) || []).filter((t) => t.includes('.')))].sort();
    if (named.length !== 1 || named[0] !== scope) {
      out.push(
        `The app asks Google for ${scope} (${GOOGLE_SCOPE_SOURCE}), but ${file} names ` +
          `${named.length ? named.join(', ') : 'no Calendar scope'} — the privacy policy, its hosted copy ` +
          'and the home page must name exactly the scope the app requests (regenerate web/privacy/ after ' +
          'editing the policy, and re-check the OAuth consent screen)'
      );
    }
  }
  return out;
}

function checkGoogleScopeDisclosed() {
  const documents = Object.fromEntries(GOOGLE_SCOPE_DOCUMENTS.map((f) => [f, read(f)]));
  googleScopeProblems(read(GOOGLE_SCOPE_SOURCE), documents).forEach(fail);
  console.log(`google scope: checked against ${GOOGLE_SCOPE_DOCUMENTS.length} documents`);
}

// ---- 7. the strings read the same on Android and in Compose Multiplatform ---

/**
 * The keys whose text holds a literal `%%` beside format arguments. `String.format` prints `%`;
 * Compose Multiplatform 1.9.3's formatter leaves `%%` as it is, and no spelling of a literal percent
 * sign reads as one in both (`%` becomes a bare `%`, which `String.format` refuses). They are
 * read through `R.string` today (`presentation/changerequests/SplitShares.kt`); before that file
 * moves to `:shared-ui` (MON-24 Task 11) they need a wording or a format both read alike.
 * `StringsParityTest` holds the same list.
 */
const PERCENT_WITH_ARGUMENTS = new Set([
  'expenses_split_shares_named',
  'expenses_split_shares_you_first',
  'expenses_split_shares_you_second',
]);

/**
 * What in one resource text Android and Compose Multiplatform 1.9.3 would read differently.
 *
 * @param {string} text the text between the tags, as written in the file (comments stripped)
 * @param {string} name the resource name, for the `%%` allowance
 * @return {string[]} one line per problem; empty when both read it alike
 */
function stringTextProblems(text, name) {
  const out = [];
  if (/[<]|&(?:quot|apos|#34|#39|#x22|#x27);/i.test(text)) {
    out.push('markup, CDATA or a quote entity, which the two parsers have not been compared on');
  }
  for (const m of text.matchAll(/\\(u[0-9a-fA-F]{4}|.)/g)) {
    const e = m[1];
    if (e[0] === 'u' && e.length === 5) continue;
    if (e === 'n' || e === 't' || e === '\\') continue;
    const hint = e === "'" ? ' (write \\u0027)' : e === '"' ? ' (write \\u0022)' : '';
    out.push(`the escape \\${e}, which Compose Multiplatform keeps with its backslash${hint}`);
  }
  if (/(?:^|[^\\])"/.test(text)) {
    out.push('an unescaped ", which aapt2 drops and Compose Multiplatform keeps (write \\u0022)');
  }
  if (/[ \t\r\n]{2,}|[\t\r\n]/.test(text) || text !== text.trim()) {
    out.push('whitespace aapt2 collapses or trims and Compose Multiplatform keeps (write \\n for a line break)');
  }
  if (/^[@?]/.test(text)) out.push('a leading @ or ?, which aapt2 reads as a reference');
  const args = [...text.matchAll(/%(?:(\d+)\$)?([-#+ 0,(]*\d*(?:\.\d+)?)([a-zA-Z%])/g)];
  for (const m of args) {
    if (m[3] === '%' && !m[1] && !m[2]) continue;
    if (m[1] && !m[2] && (m[3] === 's' || m[3] === 'd')) continue;
    out.push(`the format ${m[0]}, which Compose Multiplatform does not replace (only %<n>$s and %<n>$d)`);
  }
  const hasArgs = args.some((m) => m[3] !== '%');
  if (hasArgs && /%%/.test(text) && !PERCENT_WITH_ARGUMENTS.has(name)) {
    out.push('%% beside format arguments, which Compose Multiplatform prints as %% and Android as %');
  }
  return out;
}

/** Every text of every string, plural item and array item in one `values*` directory. */
function* resourceTexts(dir) {
  const dirPath = path.join(RES, dir);
  if (!fs.existsSync(dirPath)) return;
  for (const file of fs.readdirSync(dirPath).filter((f) => f.endsWith('.xml'))) {
    const xml = stripComments(fs.readFileSync(path.join(dirPath, file), 'utf8'));
    for (const m of xml.matchAll(/<string\s[^>]*?name="([^"]+)"[^>]*?(?:\/>|>([\s\S]*?)<\/string>)/g)) {
      yield { file, name: m[1], text: m[2] || '' };
    }
    for (const m of xml.matchAll(/<(plurals|string-array)\s[^>]*?name="([^"]+)"[^>]*>([\s\S]*?)<\/\1>/g)) {
      for (const item of m[3].matchAll(/<item\b[^>]*?(?:\/>|>([\s\S]*?)<\/item>)/g)) {
        yield { file, name: m[2], text: item[1] || '' };
      }
    }
  }
}

function checkStringTexts() {
  let count = 0;
  for (const dir of [BASE_DIR, ...LOCALE_DIRS]) {
    for (const { file, name, text } of resourceTexts(dir)) {
      count++;
      for (const p of stringTextProblems(text, name)) fail(`${RES_DIR}/${dir}/${file} "${name}": ${p}`);
    }
  }
  console.log(`string texts: ${count} checked for what Android and Compose Multiplatform read differently`);
}

// ---- run --------------------------------------------------------------------

if (require.main === module) {
  checkLocales();
  checkPushTypes();
  checkNoGson();
  checkProbeCoverage();
  checkGoogleScopeDisclosed();
  checkStringTexts();

  if (problems.length) {
    console.error(`\n${problems.length} problem(s):`);
    for (const p of problems) console.error(`  - ${p}`);
    process.exit(1);
  }
  console.log('\nAll invariants hold.');
}

module.exports = { storedJsonModels, gsonImports, googleScopeProblems, stringTextProblems, PERCENT_WITH_ARGUMENTS };
