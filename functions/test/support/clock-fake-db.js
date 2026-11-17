// For `request-clock-steps.test.js`.

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

  return {
    store,
    collection: (name) => ({doc: (id) => refOf(name, id)}),
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
