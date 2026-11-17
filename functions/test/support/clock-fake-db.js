// For `request-clock-steps.test.js` and `request-clock-sweep.test.js`.

/**
 * A Firestore fake for the request clock's steps. Documents live by path (`collection/id`), so a
 * test reads the resulting state rather than the calls made.
 *
 * Transactions behave as Firestore's do where it matters to the steps:
 * - **they are serialised**: one runs at a time, so two overlapping steps see each other's commit,
 *   which is what Firestore's optimistic retry guarantees;
 * - **writes are buffered** and applied when the function resolves, and a read after a write throws,
 *   as the Admin SDK refuses one;
 * - `beforeTransaction(fn)` runs `fn(store)` at the start of the next transaction, to change a
 *   record between the sweep's query and the step's own read.
 *
 * `failRead(path)` makes every read of that path reject, as an unreachable server would.
 *
 * `FieldValue.delete()` and `FieldValue.serverTimestamp()` are applied, not stored: a delete removes
 * the key, a server timestamp is stored as `{__serverTimestamp: true}`.
 *
 * A collection answers the queries the sweep and the backfill make, and nothing more:
 * `where(field, '<=', value)`, `orderBy(field)` (a field name, or anything else for the document
 * id), `limit(n)`, `startAfter(snapshot)` and `get()`. As in Firestore, a range filter matches only
 * a document whose field is a number, ties order by id, and `startAfter` resumes after the values
 * the snapshot was read with, whatever the document holds now.
 *
 * @param {!Object<string, !Object>} seed documents by path.
 * @return {!Object} a Firestore-shaped fake exposing its documents as `store`.
 */
function fakeDb(seed) {
  const store = {};
  Object.keys(seed || {}).forEach((path) => {
    store[path] = JSON.parse(JSON.stringify(seed[path]));
  });
  const failing = new Set();
  const beforeHooks = [];
  let queue = Promise.resolve();

  const sentinel = (value) => value && value.constructor && value.constructor.name;

  const resolveValue = (value) =>
    sentinel(value) === 'ServerTimestampTransform' ? {__serverTimestamp: true} : value;

  const snapshot = (path) => {
    if (failing.has(path)) return Promise.reject(Object.assign(new Error('unavailable'), {code: 14}));
    const data = store[path];
    const id = path.split('/')[1];
    return Promise.resolve(data === undefined ?
      {id, exists: false, data: () => undefined} :
      {id, exists: true, data: () => JSON.parse(JSON.stringify(data))});
  };

  const applySet = (path, value) => {
    const copy = {};
    Object.keys(value).forEach((key) => {
      if (sentinel(value[key]) !== 'DeleteTransform') copy[key] = resolveValue(value[key]);
    });
    store[path] = JSON.parse(JSON.stringify(copy));
  };

  const applyUpdate = (path, update) => {
    if (store[path] === undefined) throw Object.assign(new Error(`no document to update: ${path}`), {code: 5});
    Object.keys(update).forEach((key) => {
      if (key.includes('.')) throw new Error('fakeDb: dotted update paths are not supported');
      if (sentinel(update[key]) === 'DeleteTransform') delete store[path][key];
      else store[path][key] = JSON.parse(JSON.stringify(resolveValue(update[key])));
    });
  };

  const refOf = (collection, id) => ({
    path: `${collection}/${id}`,
    id,
    get: () => snapshot(`${collection}/${id}`),
    set: async (value) => applySet(`${collection}/${id}`, value),
    update: async (update) => applyUpdate(`${collection}/${id}`, update),
  });

  const runTransaction = (fn) => {
    const run = queue.then(async () => {
      beforeHooks.splice(0).forEach((hook) => hook(store));
      const writes = [];
      const tx = {
        get(ref) {
          if (writes.length > 0) throw new Error('fakeDb: a transaction read after a write');
          return snapshot(ref.path);
        },
        set(ref, value) {
          writes.push(() => applySet(ref.path, value));
        },
        update(ref, update) {
          writes.push(() => applyUpdate(ref.path, update));
        },
      };
      const result = await fn(tx);
      writes.forEach((write) => write());
      return result;
    });
    queue = run.catch(() => undefined);
    return run;
  };

  const query = (name, spec) => ({
    where: (field, op, value) => {
      if (op !== '<=') throw new Error(`fakeDb: unsupported operator ${op}`);
      return query(name, Object.assign({}, spec, {where: {field, value}}));
    },
    orderBy: (field) => query(name, Object.assign({}, spec, {orderBy: field})),
    limit: (n) => query(name, Object.assign({}, spec, {limit: n})),
    startAfter: (snap) => query(name, Object.assign({}, spec, {after: snap.orderKey})),
    get: async () => {
      const keyOf = (id, data) => (typeof spec.orderBy === 'string' ? [data[spec.orderBy], id] : [id, id]);
      const before = (a, b) => (a[0] < b[0] || (a[0] === b[0] && a[1] < b[1]));
      let docs = Object.keys(store)
          .filter((path) => path.startsWith(`${name}/`) && path.split('/').length === 2)
          .map((path) => ({id: path.split('/')[1], data: store[path]}))
          .filter(({data}) => !spec.where ||
            (typeof data[spec.where.field] === 'number' && data[spec.where.field] <= spec.where.value))
          .map(({id, data}) => ({id, data, orderKey: keyOf(id, data)}))
          .sort((a, b) => (before(a.orderKey, b.orderKey) ? -1 : 1));
      if (spec.after) docs = docs.filter((doc) => before(spec.after, doc.orderKey));
      if (spec.limit !== undefined) docs = docs.slice(0, spec.limit);
      const out = docs.map(({id, data, orderKey}) => {
        const copy = JSON.parse(JSON.stringify(data));
        return {id, ref: refOf(name, id), exists: true, orderKey, data: () => JSON.parse(JSON.stringify(copy))};
      });
      return {docs: out, size: out.length, empty: out.length === 0};
    },
  });

  return {
    store,
    collection: (name) => Object.assign({doc: (id) => refOf(name, id)}, query(name, {})),
    runTransaction,
    failRead(path) {
      failing.add(path);
    },
    beforeTransaction(hook) {
      beforeHooks.push(hook);
    },
    /**
     * The documents of a collection, by id.
     * @param {string} name the collection.
     * @return {!Object<string, !Object>}
     */
    docsOf(name) {
      const out = {};
      Object.keys(store).filter((path) => path.startsWith(`${name}/`)).forEach((path) => {
        out[path.slice(name.length + 1)] = store[path];
      });
      return out;
    },
  };
}

module.exports = {fakeDb};
