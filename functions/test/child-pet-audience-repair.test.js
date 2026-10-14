const test = require('firebase-functions-test')();
const assert = require('assert');

/**
 * Tests for `repairChildAndPetAudiences`, the operator-run repair of the cross-family leak.
 *
 * The leak: a parent in two families switched their phone to the second family, and the next sync
 * re-uploaded every child and pet they had entered for the first with the second family's parent
 * in `sharedWith`. The client and `firestore.rules` no longer allow it; this pass takes the leaked
 * uid out of every document already written. The property under test throughout: a document's
 * audience ends up inside the family its own `familyId` names — plus, on a child's record, the
 * guests it names — and nothing else about the document moves.
 */

/**
 * In-memory Firestore covering what the repair uses: whole-collection reads, document reads (for
 * the liveness check, which reads both profiles) and batched updates. Commits are recorded.
 *
 * @param {!Object<string, !Object<string, !Object>>} seed Documents by collection, then id.
 * @return {!Object} The fake, carrying `_docs`, `_commits` and `_reads`.
 */
function fakeDb(seed) {
  const docs = JSON.parse(JSON.stringify(seed));
  const commits = [];
  const reads = [];

  return {
    _docs: docs,
    _commits: commits,
    _reads: reads,
    collection(name) {
      return {
        doc(id) {
          return {
            async get() {
              reads.push(`${name}/${id}`);
              const data = (docs[name] || {})[id];
              return {exists: data !== undefined, data: () => data};
            },
          };
        },
        async get() {
          return {
            docs: Object.keys(docs[name] || {}).map((id) => ({
              id,
              data: () => docs[name][id],
              ref: {collection: name, id},
            })),
          };
        },
      };
    },
    batch() {
      const ops = [];
      return {
        update(ref, update) {
          ops.push({ref, update});
        },
        async commit() {
          ops.forEach(({ref, update}) => Object.assign(docs[ref.collection][ref.id], update));
          commits.push(ops.map(({ref, update}) => ({path: `${ref.collection}/${ref.id}`, update})));
        },
      };
    },
  };
}

const WITH_BOB = 'alice__bob';
const WITH_CAROL = 'alice__carol';

/**
 * Alice co-parents with Bob and with Carol, and both families are live: each pair's profiles name
 * each other.
 *
 * @param {!Object=} records `child_info` and `pets` documents to add.
 * @return {!Object} The seed.
 */
function twoFamilies(records) {
  return Object.assign({
    users: {
      alice: {name: 'Alice', partnerIds: ['bob', 'carol'], partnerId: 'carol'},
      bob: {name: 'Bob', partnerIds: ['alice'], partnerId: 'alice'},
      carol: {name: 'Carol', partnerIds: ['alice'], partnerId: 'alice'},
    },
    child_info: {},
    pets: {},
  }, records || {});
}

/**
 * A child's record as the client writes it, in the Bob family unless told otherwise.
 *
 * @param {!Object} overrides Fields to replace.
 * @return {!Object} The document.
 */
function child(overrides) {
  return Object.assign({
    id: 'child-1',
    childName: 'Ema',
    medicalNotes: 'Mild asthma',
    allergies: ['Peanuts'],
    medicalProfile: {bloodType: 'A_POSITIVE'},
    guests: {},
    createdByFirebaseUid: 'alice',
    lastModifiedBy: 'alice',
    updatedAt: '2026-10-01T10:00:00',
    familyId: WITH_BOB,
    sharedWith: ['alice', 'bob'],
  }, overrides);
}

/**
 * A pet's record as the client writes it, in the Bob family unless told otherwise.
 *
 * @param {!Object} overrides Fields to replace.
 * @return {!Object} The document.
 */
function pet(overrides) {
  return Object.assign({
    id: 'pet-1',
    name: 'Rex',
    createdByFirebaseUid: 'alice',
    lastModifiedBy: 'alice',
    updatedAt: '2026-10-01T10:00:00',
    familyId: WITH_BOB,
    sharedWith: ['alice', 'bob'],
  }, overrides);
}

describe('repairChildAndPetAudiences', () => {
  const ORIGINAL_ADMIN_UIDS = process.env.BACKFILL_ADMIN_UIDS;
  let index;

  before(() => {
    index = require('../index');
  });

  afterEach(() => {
    if (ORIGINAL_ADMIN_UIDS === undefined) {
      delete process.env.BACKFILL_ADMIN_UIDS;
    } else {
      process.env.BACKFILL_ADMIN_UIDS = ORIGINAL_ADMIN_UIDS;
    }
  });

  after(() => {
    test.cleanup();
  });

  it('takes the other family\'s parent out of a leaked child, and writes nothing else', async () => {
    // The leak itself: the Bob family's child re-uploaded naming Carol.
    const db = fakeDb(twoFamilies({
      child_info: {'child-1': child({sharedWith: ['alice', 'carol']})},
    }));

    const summary = await index.repairChildAndPetAudiencesImpl(db);

    assert.deepStrictEqual(db._docs.child_info['child-1'].sharedWith, ['alice', 'bob']);
    assert.deepStrictEqual(db._commits, [[{
      path: 'child_info/child-1', update: {sharedWith: ['alice', 'bob']},
    }]]);
    assert.strictEqual(summary.repaired, 1);
    assert.strictEqual(summary.removed, 1);
    assert.strictEqual(summary.readded, 1);
    assert.deepStrictEqual(summary.perCollection, {child_info: 1, pets: 0});
  });

  it('repairs a leaked pet the same way', async () => {
    // A pet's audience repair added the family on screen beside the pet's own co-parent.
    const db = fakeDb(twoFamilies({
      pets: {'pet-1': pet({sharedWith: ['alice', 'bob', 'carol']})},
    }));

    const summary = await index.repairChildAndPetAudiencesImpl(db);

    assert.deepStrictEqual(db._docs.pets['pet-1'].sharedWith, ['alice', 'bob']);
    assert.strictEqual(summary.repaired, 1);
    assert.strictEqual(summary.removed, 1);
    assert.strictEqual(summary.readded, 0);
    assert.deepStrictEqual(summary.perCollection, {child_info: 0, pets: 1});
  });

  it('leaves a healthy record untouched, and a second run writes nothing', async () => {
    const db = fakeDb(twoFamilies({
      child_info: {
        'child-1': child({}),
        'child-2': child({id: 'child-2', familyId: WITH_CAROL, sharedWith: ['alice', 'carol']}),
      },
      pets: {'pet-1': pet({})},
    }));

    const summary = await index.repairChildAndPetAudiencesImpl(db);

    assert.deepStrictEqual(db._commits, []);
    assert.strictEqual(summary.scanned, 3);
    assert.strictEqual(summary.repaired, 0);
  });

  it('keeps a guest the child\'s record names, and drops a "guest" from a pet', async () => {
    // A child's guest (a grandmother, for a weekend) is admitted by the record's `guests` map.
    // A pet has no guests: a `guests` key on a pet document admits nobody.
    const grant = {nina: {name: 'Nina', grantedBy: 'alice', expiresAtMillis: 4102444800000}};
    const db = fakeDb(twoFamilies({
      child_info: {'child-1': child({sharedWith: ['alice', 'bob', 'nina', 'carol'], guests: grant})},
      pets: {'pet-1': pet({sharedWith: ['alice', 'bob', 'nina'], guests: grant})},
    }));

    await index.repairChildAndPetAudiencesImpl(db);

    assert.deepStrictEqual(db._docs.child_info['child-1'].sharedWith, ['alice', 'bob', 'nina']);
    assert.deepStrictEqual(db._docs.child_info['child-1'].guests, grant, 'guests are not written');
    assert.deepStrictEqual(db._docs.pets['pet-1'].sharedWith, ['alice', 'bob']);
  });

  it('skips a record that names no family: there is nothing to bound it by', async () => {
    // Blank, absent or null: every shape "no family" has been written in. Which of Alice's two
    // families such a record is about is exactly what nothing on the server can say.
    const unstamped = child({id: 'child-2', sharedWith: ['alice', 'bob']});
    delete unstamped.familyId;
    const db = fakeDb(twoFamilies({
      child_info: {
        'child-1': child({familyId: '', sharedWith: ['alice', 'carol']}),
        'child-2': unstamped,
      },
      pets: {'pet-1': pet({familyId: null, sharedWith: ['alice', 'carol']})},
    }));

    const summary = await index.repairChildAndPetAudiencesImpl(db);

    assert.deepStrictEqual(db._commits, []);
    assert.strictEqual(summary.skipped.blankFamily, 3);
  });

  it('does not bring back the other parent of a family that has ended', async () => {
    // Alice and Bob unpaired: neither profile names the other. The unpair sweep already narrowed
    // the record; this pass must not undo it — but it still takes Carol out.
    const db = fakeDb(twoFamilies({
      users: {
        alice: {name: 'Alice', partnerIds: ['carol'], partnerId: 'carol'},
        bob: {name: 'Bob', partnerIds: [], partnerId: ''},
        carol: {name: 'Carol', partnerIds: ['alice'], partnerId: 'alice'},
      },
      child_info: {
        'child-1': child({sharedWith: ['alice']}),
        'child-2': child({id: 'child-2', sharedWith: ['alice', 'carol']}),
      },
    }));

    const summary = await index.repairChildAndPetAudiencesImpl(db);

    assert.deepStrictEqual(db._docs.child_info['child-1'].sharedWith, ['alice']);
    assert.deepStrictEqual(db._docs.child_info['child-2'].sharedWith, ['alice']);
    assert.strictEqual(summary.readded, 0);
    assert.strictEqual(summary.repaired, 1);
  });

  it('does not re-add the other parent when only one side still names the other', async () => {
    // An interrupted unpair leaves a one-sided `partnerIds`; `liveFamily` reads both profiles.
    const db = fakeDb(twoFamilies({
      users: {
        alice: {name: 'Alice', partnerIds: ['bob', 'carol']},
        bob: {name: 'Bob', partnerIds: []},
        carol: {name: 'Carol', partnerIds: ['alice']},
      },
      child_info: {'child-1': child({sharedWith: ['alice', 'carol']})},
    }));

    await index.repairChildAndPetAudiencesImpl(db);

    assert.deepStrictEqual(db._docs.child_info['child-1'].sharedWith, ['alice']);
  });

  it('never touches a tombstone\'s deletion fields, and still takes the leaked uid out', async () => {
    // A tombstone keeps the record's data until the 90-day sweep, so it is repaired too — by
    // `sharedWith` alone.
    const db = fakeDb(twoFamilies({
      child_info: {'child-1': child({
        sharedWith: ['alice', 'bob', 'carol'], deletedAtMillis: 1767225600000, deletedBy: 'alice',
      })},
    }));

    await index.repairChildAndPetAudiencesImpl(db);

    const stored = db._docs.child_info['child-1'];
    assert.deepStrictEqual(stored.sharedWith, ['alice', 'bob']);
    assert.strictEqual(stored.deletedAtMillis, 1767225600000);
    assert.strictEqual(stored.deletedBy, 'alice');
    assert.deepStrictEqual(Object.keys(db._commits[0][0].update), ['sharedWith']);
  });

  it('leaves a record whose family does not name its creator, or two people, to a person',
      async () => {
        // A record's family always names its creator — the create rule requires it. One that
        // does not is not a leak this pass understands, so it is counted and left alone rather
        // than "repaired" into something unreadable by the parent who wrote it.
        const db = fakeDb(twoFamilies({
          child_info: {
            'child-1': child({familyId: 'bob__dave', sharedWith: ['alice', 'bob', 'dave']}),
            'child-2': child({id: 'child-2', familyId: 'a__b__c', sharedWith: ['alice', 'a']}),
          },
        }));

        const summary = await index.repairChildAndPetAudiencesImpl(db);

        assert.deepStrictEqual(db._commits, []);
        assert.strictEqual(summary.skipped.creatorOutsideFamily, 1);
        assert.strictEqual(summary.skipped.malformedFamily, 1);
      });

  it('reads each family\'s liveness once, however many of its records there are', async () => {
    const db = fakeDb(twoFamilies({
      child_info: {
        'child-1': child({sharedWith: ['alice', 'carol']}),
        'child-2': child({id: 'child-2', sharedWith: ['alice', 'carol']}),
      },
      pets: {'pet-1': pet({sharedWith: ['alice', 'carol']})},
    }));

    await index.repairChildAndPetAudiencesImpl(db);

    assert.deepStrictEqual(db._reads.sort(), ['users/alice', 'users/bob']);
  });

  it('a dry run counts what it would repair and writes nothing', async () => {
    const db = fakeDb(twoFamilies({
      child_info: {'child-1': child({sharedWith: ['alice', 'carol']})},
      pets: {'pet-1': pet({sharedWith: ['alice', 'bob', 'carol']})},
    }));

    const summary = await index.repairChildAndPetAudiencesImpl(db, {dryRun: true});

    assert.deepStrictEqual(db._commits, []);
    assert.deepStrictEqual(db._docs.child_info['child-1'].sharedWith, ['alice', 'carol']);
    assert.strictEqual(summary.dryRun, true);
    assert.strictEqual(summary.repaired, 2);
    assert.strictEqual(summary.removed, 2);
    assert.strictEqual(summary.readded, 1);
  });

  it('commits in batches below Firestore\'s 500-write cap', async () => {
    const records = {};
    for (let i = 0; i < 901; i++) {
      records[`child-${i}`] = child({id: `child-${i}`, sharedWith: ['alice', 'bob', 'carol']});
    }
    const db = fakeDb(twoFamilies({child_info: records}));

    const summary = await index.repairChildAndPetAudiencesImpl(db);

    assert.strictEqual(summary.repaired, 901);
    assert.deepStrictEqual(db._commits.map((ops) => ops.length), [400, 400, 101]);
  });

  it('refuses a caller who is not an operator', async () => {
    process.env.BACKFILL_ADMIN_UIDS = 'op-uid';
    const wrapped = test.wrap(index.repairChildAndPetAudiences);

    await assert.rejects(
        () => wrapped({}, {auth: {uid: 'someone-else'}}),
        (err) => err.code === 'permission-denied');
    await assert.rejects(
        () => wrapped({dryRun: true}, {}),
        (err) => err.code === 'permission-denied');
  });
});
