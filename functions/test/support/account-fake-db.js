// Shared by `account-deletion.test.js` and `apple-tokens.test.js`.

/**
 * The Firestore fake for `deleteAccountDataImpl`. It is fuller than the one in `unpair.test.js`
 * because this path uses more of Firestore: equality and array-contains queries, per-document
 * deletes, and the transaction `unpairCoParentImpl` runs. It stores documents by collection and
 * applies writes to them, so assertions are about the resulting state rather than about which
 * calls were made.
 *
 * @param {!Object<string, !Array<!Object>>} collections Seed documents, keyed by collection.
 * @return {!Object} A Firestore-shaped fake exposing its documents as `_store`.
 */
function fakeDb(collections) {
  const store = {};
  Object.keys(collections).forEach((name) => {
    store[name] = collections[name].map((doc) => JSON.parse(JSON.stringify(doc)));
  });

  const docsOf = (name) => (store[name] = store[name] || []);

  const matches = (doc, field, op, value) => {
    const actual = doc[field];
    if (op === '==') return actual === value;
    if (op === 'array-contains') return Array.isArray(actual) && actual.includes(value);
    // Ranges match numbers only, as Firestore's do across types: that is what keeps a thread
    // nobody marked out of the retention sweep.
    if (op === '>') return typeof actual === 'number' && actual > value;
    if (op === '<=') return typeof actual === 'number' && actual <= value;
    throw new Error(`fakeDb: unsupported operator ${op}`);
  };

  const wrap = (name, doc) => ({
    id: doc.id,
    exists: true,
    data: () => doc,
    ref: {_collection: name, _id: doc.id},
  });

  const applyUpdate = (name, id, update) => {
    const root = docsOf(name).find((d) => d.id === id);
    if (!root) return;
    Object.keys(update).forEach((path) => {
      const value = update[path];
      // A dotted key is a field path into a map, as the Admin SDK's `update` reads it.
      const parts = path.split('.');
      const key = parts.pop();
      const doc = parts.reduce((node, part) => {
        if (!node[part] || typeof node[part] !== 'object') node[part] = {};
        return node[part];
      }, root);
      // Real `FieldValue` sentinels, applied rather than stored. They arrive as
      // `ArrayRemoveTransform` / `DeleteTransform` instances from firebase-admin; storing one
      // verbatim would replace the array with an opaque object and quietly pass a test that
      // asserts only "the field changed".
      const transform = value && value.constructor && value.constructor.name;
      if (transform === 'ArrayRemoveTransform') {
        doc[key] = (doc[key] || []).filter((v) => !value.elements.includes(v));
      } else if (transform === 'ArrayUnionTransform') {
        doc[key] = (doc[key] || []).concat(
            value.elements.filter((v) => !(doc[key] || []).includes(v)));
      } else if (transform === 'DeleteTransform') {
        delete doc[key];
      } else {
        doc[key] = value;
      }
    });
  };

  const removeDoc = (name, id) => {
    store[name] = docsOf(name).filter((d) => d.id !== id);
  };

  const collection = (name) => ({
    where(field, op, value) {
      const build = (predicates, max) => ({
        where(f2, o2, v2) {
          return build(predicates.concat([[f2, o2, v2]]), max);
        },
        limit(n) {
          return build(predicates, n);
        },
        async get() {
          const found = docsOf(name).filter((doc) =>
            predicates.every(([f, o, v]) => matches(doc, f, o, v))).slice(0, max || Infinity);
          return {docs: found.map((doc) => wrap(name, doc)), size: found.length};
        },
      });
      return build([[field, op, value]], 0);
    },
    doc(id) {
      return {
        _collection: name,
        _id: id,
        async get() {
          const doc = docsOf(name).find((d) => d.id === id);
          return doc ?
            wrap(name, doc) :
            {id, exists: false, data: () => undefined, ref: {_collection: name, _id: id}};
        },
        async set(value) {
          removeDoc(name, id);
          docsOf(name).push(Object.assign({id}, JSON.parse(JSON.stringify(value))));
        },
        async update(update) {
          applyUpdate(name, id, update);
        },
        async delete() {
          removeDoc(name, id);
        },
      };
    },
    async add(doc) {
      docsOf(name).push(Object.assign({id: `gen-${docsOf(name).length}`}, doc));
    },
  });

  return {
    _store: store,
    collection,
    batch() {
      const ops = [];
      return {
        delete(ref) {
          ops.push({kind: 'delete', ref});
        },
        update(ref, update) {
          ops.push({kind: 'update', ref, update});
        },
        async commit() {
          ops.forEach((op) => {
            if (op.kind === 'delete') removeDoc(op.ref._collection, op.ref._id);
            else applyUpdate(op.ref._collection, op.ref._id, op.update);
          });
          ops.length = 0;
        },
      };
    },
    async runTransaction(fn) {
      return fn({
        async get(ref) {
          return collection(ref._collection).doc(ref._id).get();
        },
        update(ref, update) {
          applyUpdate(ref._collection, ref._id, update);
        },
        set(ref, value) {
          removeDoc(ref._collection, ref._id);
          docsOf(ref._collection).push(Object.assign({id: ref._id}, value));
        },
        delete(ref) {
          removeDoc(ref._collection, ref._id);
        },
      });
    },
  };
}

module.exports = {fakeDb};
