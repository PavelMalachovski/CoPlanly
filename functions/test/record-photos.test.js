const test = require('firebase-functions-test')();
const assert = require('assert');

/**
 * Record photographs on the server (L-4): the reference format the app writes, the move of a
 * parent's personal photos into the family's folder when a pair forms, and the operator's purge
 * of the layouts from before L-4.
 *
 * The move's property under test is the one `stampOwnBlankFamilyIds` has: it never guesses a
 * family. It runs only for a member whose family was resolved, touches only that member's own
 * records naming that family, and is a no-op the second time.
 */

const SHA = '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08';
const FAMILY = 'alice__bob';

/**
 * A reference as the app writes it (`RecordPhotoCodec`).
 *
 * @param {string} path The object's path.
 * @return {string} The reference.
 */
function ref(path) {
  return `ph1|${path}|image/jpeg|48213|${SHA}`;
}

/**
 * In-memory Firestore covering what the move, the stamping and the purge use: whole-collection
 * and `==` queries, document reads and batched updates.
 *
 * @param {!Object<string, !Object<string, !Object>>} seed Documents by collection, then id.
 * @return {!Object} The fake, carrying `_docs`.
 */
function fakeDb(seed) {
  const docs = JSON.parse(JSON.stringify(seed));
  const snapshotOf = (collection, id) => ({
    id,
    data: () => docs[collection][id],
    ref: {collection, id},
  });
  return {
    _docs: docs,
    collection(name) {
      return {
        doc(id) {
          return {
            async get() {
              const data = (docs[name] || {})[id];
              return {exists: data !== undefined, data: () => data};
            },
          };
        },
        async get() {
          return {docs: Object.keys(docs[name] || {}).map((id) => snapshotOf(name, id))};
        },
        where(field, op, value) {
          assert.strictEqual(op, '==');
          return {
            async get() {
              const ids = Object.keys(docs[name] || {})
                  .filter((id) => docs[name][id][field] === value);
              return {docs: ids.map((id) => snapshotOf(name, id))};
            },
          };
        },
      };
    },
    batch() {
      const ops = [];
      return {
        update(r, update) {
          ops.push({r, update});
        },
        async commit() {
          ops.forEach(({r, update}) => Object.assign(docs[r.collection][r.id], update));
        },
      };
    },
  };
}

/**
 * A bucket holding [paths], recording copies and deletes, each object keeping its metadata.
 *
 * @param {!Array<string>} paths The objects stored at the start.
 * @return {!Object} The fake, exposing `objects` (path → metadata), `copies` and `deleted`.
 */
function fakeBucket(paths) {
  const objects = new Map(paths.map((p) => [p, {uploader: 'alice', sha256: SHA}]));
  const copies = [];
  const deleted = [];
  const file = (path) => ({
    name: path,
    async exists() {
      return [objects.has(path)];
    },
    async copy(target) {
      assert.ok(objects.has(path), `copied a missing object ${path}`);
      objects.set(target.name, Object.assign({}, objects.get(path)));
      copies.push([path, target.name]);
    },
    async delete(opts) {
      assert.ok(opts && opts.ignoreNotFound, 'a missing object must not fail');
      objects.delete(path);
      deleted.push(path);
    },
  });
  return {
    objects,
    copies,
    deleted,
    file,
    async getFiles(opts) {
      return [[...objects.keys()].filter((p) => p.startsWith(opts.prefix)).map(file)];
    },
  };
}

/**
 * Alice and Bob paired with each other and nobody else; Alice's pet, receipt and event photos
 * were taken while she was alone.
 *
 * @param {!Object=} overrides Collections to replace wholesale.
 * @return {!Object} The seed.
 */
function pairSeed(overrides) {
  return Object.assign({
    users: {
      alice: {partnerIds: ['bob'], partnerId: 'bob'},
      bob: {partnerIds: ['alice'], partnerId: 'alice'},
    },
    pets: {
      pet1: {
        createdByFirebaseUid: 'alice', familyId: '',
        photos: [ref('pet_photos/solo_alice/pet1/a.jpg'), 'https://legacy.example/pet.jpg'],
      },
    },
    expenses: {
      ex1: {createdByFirebaseUid: 'alice', familyId: '', receiptUrl: ref('receipts/solo_alice/ex1/r.jpg')},
    },
    events: {
      ev1: {createdByFirebaseUid: 'alice', familyId: FAMILY, imageUrl: ref('event_images/solo_alice/ev1/e.jpg')},
    },
  }, overrides || {});
}

const SOLO_OBJECTS = [
  'pet_photos/solo_alice/pet1/a.jpg',
  'receipts/solo_alice/ex1/r.jpg',
  'event_images/solo_alice/ev1/e.jpg',
];

describe('record photo references', () => {
  const photos = require('../record-photos');

  it('reads what the app writes and writes it back unchanged', () => {
    const value = ref(`pet_photos/${FAMILY}/pet1/a.jpg`);
    const photo = photos.decodePhotoRef(value);
    assert.deepStrictEqual(photo,
        {path: `pet_photos/${FAMILY}/pet1/a.jpg`, contentType: 'image/jpeg', size: 48213, sha256: SHA});
    assert.strictEqual(photos.encodePhotoRef(photo), value);
  });

  it('is not fooled by a download URL, a flat path or a malformed reference', () => {
    for (const value of [
      'https://firebasestorage.googleapis.com/v0/b/x/o/receipts%2Fe.jpg?alt=media&token=t',
      'receipts/e1.jpg',
      'medical_photos/c1/p1.jpg',
      `ph1|pet_photos/alice/pet1/a.jpg|image/jpeg|10|${SHA}`,
      `ph1|pet_photos/${FAMILY}/pet1/a.jpg|application/pdf|10|${SHA}`,
      `ph1|pet_photos/${FAMILY}/pet1/a.jpg|image/jpeg|0|${SHA}`,
      `ph1|pet_photos/${FAMILY}/pet1/a.jpg|image/jpeg|10|nothex`,
      `ph1|chat_attachments/${FAMILY}/m/a.jpg|image/jpeg|10|${SHA}`,
      '',
      null,
    ]) {
      assert.strictEqual(photos.decodePhotoRef(value), null, String(value));
    }
  });

  it('names a record\'s folders in its family and in its creator\'s own space', () => {
    assert.deepStrictEqual(
        photos.recordPhotoFolders('pets', 'pet1', {familyId: FAMILY, createdByFirebaseUid: 'alice'}),
        [`pet_photos/${FAMILY}/pet1/`, 'pet_photos/solo_alice/pet1/']);
    assert.deepStrictEqual(
        photos.recordPhotoFolders('expenses', 'ex1', {familyId: '', createdByFirebaseUid: 'alice'}),
        ['receipts/solo_alice/ex1/']);
    // Never a folder that would widen to a whole family or a whole prefix.
    assert.deepStrictEqual(photos.recordPhotoFolders('events', '', {familyId: FAMILY}), []);
    assert.deepStrictEqual(photos.recordPhotoFolders('budgets', 'b1', {familyId: FAMILY}), []);
    assert.deepStrictEqual(photos.soloPhotoFolders('alice'), [
      'medical_photos/solo_alice/', 'pet_photos/solo_alice/', 'receipts/solo_alice/',
      'event_images/solo_alice/',
    ]);
    assert.deepStrictEqual(photos.soloPhotoFolders(''), []);
  });
});

describe('movePhotosToFamily', () => {
  const photos = require('../record-photos');

  it('copies, rewrites and then deletes the personal copy — once', async () => {
    const db = fakeDb(pairSeed({
      pets: {
        pet1: {
          createdByFirebaseUid: 'alice', familyId: FAMILY,
          photos: [ref('pet_photos/solo_alice/pet1/a.jpg'), 'https://legacy.example/pet.jpg'],
        },
      },
      expenses: {
        ex1: {createdByFirebaseUid: 'alice', familyId: FAMILY, receiptUrl: ref('receipts/solo_alice/ex1/r.jpg')},
      },
    }));
    const bucket = fakeBucket(SOLO_OBJECTS);

    const first = await photos.movePhotosToFamily(db, bucket, 'alice', FAMILY);

    assert.deepStrictEqual(first, {moved: 3, rewritten: 3, missing: 0});
    assert.deepStrictEqual(db._docs.pets.pet1.photos,
        [ref(`pet_photos/${FAMILY}/pet1/a.jpg`), 'https://legacy.example/pet.jpg']);
    assert.strictEqual(db._docs.expenses.ex1.receiptUrl, ref(`receipts/${FAMILY}/ex1/r.jpg`));
    assert.strictEqual(db._docs.events.ev1.imageUrl, ref(`event_images/${FAMILY}/ev1/e.jpg`));
    assert.deepStrictEqual([...bucket.objects.keys()].sort(), [
      `event_images/${FAMILY}/ev1/e.jpg`, `pet_photos/${FAMILY}/pet1/a.jpg`, `receipts/${FAMILY}/ex1/r.jpg`,
    ]);
    // The stamp travels with the copy.
    assert.deepStrictEqual(bucket.objects.get(`pet_photos/${FAMILY}/pet1/a.jpg`), {uploader: 'alice', sha256: SHA});

    const second = await photos.movePhotosToFamily(db, bucket, 'alice', FAMILY);
    assert.deepStrictEqual(second, {moved: 0, rewritten: 0, missing: 0});
    assert.strictEqual(bucket.copies.length, 3);
  });

  it('finishes a move that died between the copy and the delete', async () => {
    const db = fakeDb(pairSeed({pets: {}, expenses: {}}));
    const bucket = fakeBucket([
      'event_images/solo_alice/ev1/e.jpg', `event_images/${FAMILY}/ev1/e.jpg`,
    ]);

    const result = await photos.movePhotosToFamily(db, bucket, 'alice', FAMILY);

    assert.deepStrictEqual(result, {moved: 0, rewritten: 1, missing: 0});
    assert.strictEqual(db._docs.events.ev1.imageUrl, ref(`event_images/${FAMILY}/ev1/e.jpg`));
    assert.deepStrictEqual(bucket.deleted, ['event_images/solo_alice/ev1/e.jpg']);
  });

  it('leaves a record naming no family, or another one, exactly as it is', async () => {
    const db = fakeDb(pairSeed({
      events: {
        ev1: {
          createdByFirebaseUid: 'alice', familyId: 'alice__carol',
          imageUrl: ref('event_images/solo_alice/ev1/e.jpg'),
        },
      },
    }));
    const bucket = fakeBucket(SOLO_OBJECTS);

    const result = await photos.movePhotosToFamily(db, bucket, 'alice', FAMILY);

    // pet1 and ex1 are still blank (nothing stamped them), ev1 names another family.
    assert.deepStrictEqual(result, {moved: 0, rewritten: 0, missing: 0});
    assert.deepStrictEqual(bucket.copies, []);
    assert.deepStrictEqual(bucket.deleted, []);
  });

  it('never moves somebody else\'s photo, or one filed under another record', async () => {
    const db = fakeDb({
      events: {
        ev1: {createdByFirebaseUid: 'alice', familyId: FAMILY, imageUrl: ref('event_images/solo_bob/ev1/e.jpg')},
        ev2: {createdByFirebaseUid: 'alice', familyId: FAMILY, imageUrl: ref('event_images/solo_alice/ev1/e.jpg')},
      },
    });
    const bucket = fakeBucket(['event_images/solo_bob/ev1/e.jpg', 'event_images/solo_alice/ev1/e.jpg']);

    const result = await photos.movePhotosToFamily(db, bucket, 'alice', FAMILY);

    assert.deepStrictEqual(result, {moved: 0, rewritten: 0, missing: 0});
    assert.deepStrictEqual(bucket.copies, []);
  });

  it('counts a reference whose object is nowhere, and keeps it', async () => {
    const db = fakeDb(pairSeed({pets: {}, expenses: {}}));
    const bucket = fakeBucket([]);

    const result = await photos.movePhotosToFamily(db, bucket, 'alice', FAMILY);

    assert.deepStrictEqual(result, {moved: 0, rewritten: 0, missing: 1});
    assert.strictEqual(db._docs.events.ev1.imageUrl, ref('event_images/solo_alice/ev1/e.jpg'));
  });

  it('refuses a family the uploader is not in, and does nothing without a bucket', async () => {
    const db = fakeDb(pairSeed());
    const bucket = fakeBucket(SOLO_OBJECTS);

    assert.deepStrictEqual(await photos.movePhotosToFamily(db, bucket, 'alice', 'bob__carol'),
        {moved: 0, rewritten: 0, missing: 0});
    assert.deepStrictEqual(await photos.movePhotosToFamily(db, null, 'alice', FAMILY),
        {moved: 0, rewritten: 0, missing: 0});
    assert.deepStrictEqual(bucket.copies, []);
  });
});

describe('the move, run when a pair forms', () => {
  let index;

  before(() => {
    index = require('../index');
  });

  after(() => {
    test.cleanup();
  });

  it('stamps the pre-pairing records and moves their photos', async () => {
    const db = fakeDb(pairSeed());
    const bucket = fakeBucket(SOLO_OBJECTS);

    const outcomes = await index.stampFamilyOnCreateImpl(db, FAMILY, {members: ['alice', 'bob']}, bucket);

    assert.strictEqual(outcomes.alice.reason, '');
    assert.deepStrictEqual(outcomes.alice.photos, {moved: 3, rewritten: 3, missing: 0});
    assert.strictEqual(db._docs.pets.pet1.familyId, FAMILY);
    assert.strictEqual(db._docs.pets.pet1.photos[0], ref(`pet_photos/${FAMILY}/pet1/a.jpg`));
    assert.strictEqual(db._docs.expenses.ex1.receiptUrl, ref(`receipts/${FAMILY}/ex1/r.jpg`));
    assert.ok(!bucket.objects.has('pet_photos/solo_alice/pet1/a.jpg'));
  });

  it('moves nothing for a member whose family would be a guess', async () => {
    // Alice already co-parents with Carol: her blanks, and the photos on them, stay where they are.
    const db = fakeDb(pairSeed({
      users: {
        alice: {partnerIds: ['carol', 'bob'], partnerId: 'carol'},
        bob: {partnerIds: ['alice'], partnerId: 'alice'},
        carol: {partnerIds: ['alice']},
      },
    }));
    const bucket = fakeBucket(SOLO_OBJECTS);

    const outcomes = await index.stampFamilyOnCreateImpl(db, FAMILY, {members: ['alice', 'bob']}, bucket);

    assert.strictEqual(outcomes.alice.reason, 'ambiguous');
    assert.strictEqual(outcomes.alice.photos, undefined);
    assert.deepStrictEqual(bucket.copies, []);
    assert.strictEqual(db._docs.pets.pet1.photos[0], ref('pet_photos/solo_alice/pet1/a.jpg'));
  });

  it('is repeated by the backfill as a backstop, and is a no-op the second time', async () => {
    const db = fakeDb(pairSeed());
    const bucket = fakeBucket(SOLO_OBJECTS);

    const first = await index.backfillRecordFamilyIdsImpl(db, bucket);
    const second = await index.backfillRecordFamilyIdsImpl(db, bucket);

    assert.deepStrictEqual(first.photos, {moved: 3, rewritten: 3, missing: 0});
    assert.deepStrictEqual(second.photos, {moved: 0, rewritten: 0, missing: 0});
  });
});

describe('purgeLegacyPhotoPathsImpl', () => {
  const photos = require('../record-photos');

  it('deletes every object under a flat layout and clears the legacy values from records', async () => {
    const db = fakeDb({
      pets: {
        pet1: {photos: ['https://legacy.example/pet.jpg', ref(`pet_photos/${FAMILY}/pet1/a.jpg`)]},
        pet2: {photos: [ref(`pet_photos/${FAMILY}/pet2/b.jpg`)]},
      },
      child_info: {c1: {medicalPhotos: ['medical_photos/c1/p1.jpg'], updatedAt: '2026-09-01T10:00:00'}},
      expenses: {
        ex1: {receiptUrl: 'https://legacy.example/r.jpg', deletedAtMillis: 5},
        ex2: {receiptUrl: ''},
      },
      events: {ev1: {imageUrl: ref(`event_images/${FAMILY}/ev1/e.jpg`)}},
    });
    const bucket = fakeBucket([
      'receipts/ex1.jpg',
      'event_images/ev9.jpg',
      'medical_photos/c1/p1.jpg',
      'pet_photos/pet1/photo.jpg',
      `pet_photos/${FAMILY}/pet1/a.jpg`,
      'pet_photos/solo_alice/pet3/c.jpg',
      `family_documents/${FAMILY}/d1/order.pdf`,
    ]);

    const summary = await photos.purgeLegacyPhotoPathsImpl(db, bucket);

    assert.strictEqual(summary.objectsDeleted, 4);
    assert.deepStrictEqual([...bucket.objects.keys()].sort(), [
      `family_documents/${FAMILY}/d1/order.pdf`,
      `pet_photos/${FAMILY}/pet1/a.jpg`,
      'pet_photos/solo_alice/pet3/c.jpg',
    ]);
    assert.strictEqual(summary.recordsCleared, 3);
    assert.deepStrictEqual(db._docs.pets.pet1.photos, [ref(`pet_photos/${FAMILY}/pet1/a.jpg`)]);
    assert.deepStrictEqual(db._docs.child_info.c1.medicalPhotos, []);
    // Only the photo field is written: no clock and no tombstone field moves.
    assert.strictEqual(db._docs.child_info.c1.updatedAt, '2026-09-01T10:00:00');
    assert.strictEqual(db._docs.expenses.ex1.receiptUrl, '');
    assert.strictEqual(db._docs.expenses.ex1.deletedAtMillis, 5);
    assert.strictEqual(db._docs.events.ev1.imageUrl, ref(`event_images/${FAMILY}/ev1/e.jpg`));

    const again = await photos.purgeLegacyPhotoPathsImpl(db, bucket);
    assert.deepStrictEqual(again,
        {objectsDeleted: 0, recordsCleared: 0, perCollection: {child_info: 0, pets: 0, expenses: 0, events: 0}});
  });
});
