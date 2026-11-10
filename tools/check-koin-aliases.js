#!/usr/bin/env node
/**
 * Proves, against R8's own output, that every Koin alias still joins two different classes.
 *
 * Koin keys a definition by its class. About fifty bindings in this app have the form
 *
 *     singleOf(::FcmService)
 *     single<PushQueue> { get<FcmService>() }
 *
 * an implementation with its own definition, and an interface answered by asking for that
 * implementation. It works while `PushQueue` and `FcmService` are two classes. R8 may merge an
 * interface into its only implementer; the two definitions would then share one key, the alias
 * would replace the implementation's own definition (Koin allows an override silently), and
 * `get<FcmService>()` inside it would ask for itself: a `StackOverflowError` on the first
 * resolution, at launch, in the release build only. No other check starts the Koin graph in a
 * minified build: the unit tests, the emulator tests and the R8 probe all run it unminified or not
 * at all.
 *
 * So this reads `mapping.txt` after a minified build and requires both classes of every alias to
 * be there under their own names. A class R8 merged away has no entry of its own.
 *
 * Usage: node tools/check-koin-aliases.js [path/to/mapping.txt]
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const DEFAULT_MAPPING = path.join(ROOT, 'app/build/outputs/mapping/release/mapping.txt');

/** Where the Koin modules are declared. */
const MODULE_DIRS = [
  'app/src/main/java/com/coparently/app/di/koin',
  'shared-data/src/commonMain/kotlin/com/coparently/app/di/koin',
  'shared-data/src/androidMain/kotlin/com/coparently/app/di/koin',
  'shared-ui/src/commonMain/kotlin/com/coparently/app/di/koin',
  'shared-ui/src/androidMain/kotlin/com/coparently/app/di/koin',
];

/** Where the classes an Android build compiles are declared. */
const SOURCE_ROOTS = [
  'app/src/main/java',
  'shared/src/commonMain/kotlin',
  'shared/src/androidMain/kotlin',
  'shared-data/src/commonMain/kotlin',
  'shared-data/src/androidMain/kotlin',
  'shared-ui/src/commonMain/kotlin',
  'shared-ui/src/androidMain/kotlin',
];

const ALIAS =
  /\b(?:single|factory)<([A-Za-z0-9_.]+)>\s*(?:\((?:[^()]|\([^()]*\))*\))?\s*\{\s*get<([A-Za-z0-9_.]+)>\(\)\s*\}/g;
const DECLARATION = /^(?:[a-z]+\s+)*(?:class|interface|object)\s+([A-Z][A-Za-z0-9_]*)/gm;

/** Every `.kt` file under [dir], recursively; none when it does not exist. */
function kotlinFiles(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return kotlinFiles(full);
    return entry.name.endsWith('.kt') ? [full] : [];
  });
}

/** The package a Kotlin source declares, or ''. */
function packageOf(source) {
  const match = /^package\s+([A-Za-z0-9_.]+)/m.exec(source);
  return match ? match[1] : '';
}

/**
 * The aliases one module file declares.
 *
 * @param {string} source the module file's text
 * @returns {{iface: string, impl: string}[]} simple or qualified names, as written
 */
function aliasesIn(source) {
  return [...source.matchAll(ALIAS)].map((m) => ({ iface: m[1], impl: m[2] }));
}

/**
 * The fully-qualified name [name] stands for in a file with [imports] and [ownPackage].
 *
 * @param {Map<string, string[]>} declared simple name -> every qualified name declaring it
 * @returns {string|null} null when the sources do not say which class is meant
 */
function qualify(name, imports, ownPackage, declared) {
  if (name.includes('.')) return name;
  if (imports.has(name)) return imports.get(name);
  const candidates = declared.get(name) || [];
  const samePackage = candidates.find((c) => c === `${ownPackage}.${name}`);
  if (samePackage) return samePackage;
  return candidates.length === 1 ? candidates[0] : null;
}

/** simple name -> every qualified name that declares it, over [roots]. */
function declarations(roots) {
  const declared = new Map();
  for (const root of roots) {
    for (const file of kotlinFiles(path.join(ROOT, root))) {
      const source = fs.readFileSync(file, 'utf8');
      const pkg = packageOf(source);
      for (const m of source.matchAll(DECLARATION)) {
        const qualified = pkg ? `${pkg}.${m[1]}` : m[1];
        const known = declared.get(m[1]) || [];
        if (!known.includes(qualified)) declared.set(m[1], [...known, qualified]);
      }
    }
  }
  return declared;
}

/** Every alias the Koin modules declare, with both names qualified (null where unknown). */
function allAliases(moduleDirs = MODULE_DIRS, roots = SOURCE_ROOTS) {
  const declared = declarations(roots);
  const found = [];
  for (const dir of moduleDirs) {
    for (const file of kotlinFiles(path.join(ROOT, dir))) {
      const source = fs.readFileSync(file, 'utf8');
      const imports = new Map(
        [...source.matchAll(/^import\s+([A-Za-z0-9_.]+)\.([A-Za-z0-9_]+)\s*$/gm)].map((m) => [m[2], `${m[1]}.${m[2]}`]),
      );
      const ownPackage = packageOf(source);
      for (const alias of aliasesIn(source)) {
        found.push({
          file: path.relative(ROOT, file),
          written: `single<${alias.iface}> { get<${alias.impl}>() }`,
          iface: qualify(alias.iface, imports, ownPackage, declared),
          impl: qualify(alias.impl, imports, ownPackage, declared),
        });
      }
    }
  }
  return found;
}

/** The original names of every class R8's mapping lists. */
function mappedClasses(mapping) {
  const classes = new Set();
  for (const line of mapping.split('\n')) {
    if (line.startsWith(' ') || line.startsWith('#') || line.startsWith('\t')) continue;
    const match = /^(\S+) -> \S+:\s*$/.exec(line.trimEnd());
    if (match) classes.add(match[1]);
  }
  return classes;
}

/**
 * What is wrong with [aliases] in a build whose classes are [classes]; empty when nothing is.
 *
 * @param {{file: string, written: string, iface: string|null, impl: string|null}[]} aliases
 * @param {Set<string>} classes original class names in the mapping
 * @returns {string[]} one line per problem
 */
function problemsOf(aliases, classes) {
  const problems = [];
  if (aliases.length === 0) problems.push('no Koin alias was found: the pattern or the module directories changed');
  for (const alias of aliases) {
    const where = `${alias.file}: ${alias.written}`;
    if (!alias.iface || !alias.impl) {
      problems.push(`${where}: could not tell which class is meant; import it by name in the module file`);
      continue;
    }
    if (alias.iface === alias.impl) continue;
    for (const name of [alias.iface, alias.impl]) {
      if (!classes.has(name)) {
        problems.push(
          `${where}: ${name} has no class of its own in the minified build. If R8 merged it with the ` +
            'other, the alias replaces the definition it asks for and the app overflows the stack at launch. ' +
            'Bind the interface on the implementation\'s own definition instead: singleOf(::Impl) { bind<Interface>() }',
        );
      }
    }
  }
  return problems;
}

function main() {
  const mappingPath = process.argv[2] ? path.resolve(process.argv[2]) : DEFAULT_MAPPING;
  if (!fs.existsSync(mappingPath)) {
    console.error('No mapping file at ' + mappingPath + '.');
    console.error('Run `./gradlew assembleRelease` first: R8 writes it only for a minified build.');
    process.exit(1);
  }
  const aliases = allAliases();
  const classes = mappedClasses(fs.readFileSync(mappingPath, 'utf8'));
  const problems = problemsOf(aliases, classes);
  for (const problem of problems) console.log(`::error::${problem}`);
  if (problems.length > 0) {
    console.error(`\nkoin aliases: ${problems.length} problem(s) in ${aliases.length} alias(es)`);
    process.exit(1);
  }
  console.log(`koin aliases: ${aliases.length} alias(es), each joining two classes R8 kept apart (${classes.size} classes in the mapping)`);
}

if (require.main === module) main();

module.exports = { aliasesIn, qualify, mappedClasses, problemsOf, allAliases };
