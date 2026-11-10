const test = require('node:test');
const assert = require('node:assert');

const { aliasesIn, qualify, mappedClasses, problemsOf, allAliases } = require('../check-koin-aliases.js');

test('an alias is found whatever its scope or qualifier', () => {
  const source = [
    'single<PushQueue> { get<FcmService>() }',
    'factory<Reader>(named("a")) { get<FileReader>() }',
    'single<EventDao> { get<CoPlanlyDatabase>().eventDao() }',
    'single { Impl(get<Context>()) }',
  ].join('\n');
  assert.deepStrictEqual(aliasesIn(source), [
    { iface: 'PushQueue', impl: 'FcmService' },
    { iface: 'Reader', impl: 'FileReader' },
  ]);
});

test('a name is qualified by the import, then the package, then the one declaration', () => {
  const declared = new Map([
    ['A', ['x.A', 'y.A']],
    ['B', ['z.B']],
  ]);
  assert.strictEqual(qualify('A', new Map([['A', 'y.A']]), 'p', declared), 'y.A');
  assert.strictEqual(qualify('A', new Map(), 'x', declared), 'x.A');
  assert.strictEqual(qualify('B', new Map(), 'p', declared), 'z.B');
  assert.strictEqual(qualify('A', new Map(), 'p', declared), null);
  assert.strictEqual(qualify('q.C', new Map(), 'p', declared), 'q.C');
});

test('the mapping lists classes, not members or comments', () => {
  const mapping = '# compiler: R8\ncom.a.B -> a.b:\n    int count -> a\n    1:1:void run():3:3 -> b\ncom.a.C -> com.a.C:\n';
  assert.deepStrictEqual([...mappedClasses(mapping)], ['com.a.B', 'com.a.C']);
});

test('a class R8 merged away fails its alias, and so does a name nobody can place', () => {
  const aliases = [
    { file: 'M.kt', written: 'single<I> { get<Impl>() }', iface: 'a.I', impl: 'a.Impl' },
    { file: 'M.kt', written: 'single<J> { get<Other>() }', iface: null, impl: 'a.Other' },
  ];
  assert.strictEqual(problemsOf([aliases[0]], new Set(['a.I', 'a.Impl'])).length, 0);
  const merged = problemsOf([aliases[0]], new Set(['a.Impl']));
  assert.strictEqual(merged.length, 1);
  assert.match(merged[0], /a\.I has no class of its own/);
  assert.match(problemsOf([aliases[1]], new Set(['a.Other']))[0], /could not tell which class/);
  assert.match(problemsOf([], new Set())[0], /no Koin alias was found/);
});

test("the app's own aliases are found and every name is placed", () => {
  const aliases = allAliases();
  assert.ok(aliases.length >= 40, `only ${aliases.length} aliases found`);
  assert.deepStrictEqual(aliases.filter((a) => !a.iface || !a.impl), []);
});
