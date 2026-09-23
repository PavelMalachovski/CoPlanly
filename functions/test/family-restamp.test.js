const test = require('firebase-functions-test')();
const assert = require('assert');

/**
 * Tests for the server-side re-stamp of blank `familyId`s (CLAUDE.md item 22): the policy in
 * `stampOwnBlankFamilyIds`, the callable body that runs it over every account, and the
 * `onFamilyCreated` trigger body that runs it for a pair the moment it forms.
 *
 * The property under test throughout is "stamp only when it is not a guess". Most cases below
 * are ones where the obvious implementation — `partnerId`, or "the family this person is in
 * now" — would write a family, and the right answer is to write nothing and say why.
 */

/**
 * In-memory Firestore covering what the re-stamp uses: whole-collection and `==` queries,
 * document reads, and batched updates. Every commit is recorded so batching can be asserted.
 *
 * @param {!Object<string, !Object<string, !Object>>} seed Documents by collection, then id.
 * @return {!Object} The fake, carrying `_docs` and `_commits`.
 */
function fakeDb(seed) {
  const docs = JSON.parse(JSON.stringify(seed));
  const commits = [];

  const snapshotOf = (collection, id) => ({
    id,
    data: () => docs[collection][id],
    ref: {collection, id},
  });

  return {
    _docs: docs,
    _commits: commits,
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
        update(ref, update) {
          ops.push({ref, update});
        },
        async commit() {
          ops.forEach(({ref, update}) => Object.assign(docs[ref.collection][ref.id], update));
          commits.push(ops.length);
        },
      };
    },
  };
}

/**
 * Alice and Bob, paired with each other and nobody else, each with one pre-pairing expense and
 * budget carrying the blank the client writes for "no family yet".
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
    expenses: {
      exA: {createdByFirebaseUid: 'alice', familyId: '', splitBetween: ['alice']},
      exB: {createdByFirebaseUid: 'bob', familyId: ''},
    },
    budgets: {
      buA: {createdByFirebaseUid: 'alice', familyId: ''},
    },
  }, overrides || {});
}

describe('stampOwnBlankFamilyIds', () => {
  let index;

  before(() => {
    index = require('../index');
  });

  after(() => {
    test.cleanup();
  });

  it('stamps a single-family author\'s pre-pairing expenses and budgets', async () => {
    // The item 22 case itself: recorded while unpaired, uploaded as "", and under the
    // family-keyed read rules invisible to the co-parent until something names the family.
    const db = fakeDb(pairSeed());

    const outcome = await index.stampOwnBlankFamilyIds(db, 'alice', db._docs.users.alice);

    assert.strictEqual(outcome.reason, '');
    assert.strictEqual(outcome.familyId, 'alice__bob');
    assert.strictEqual(outcome.stamped, 2);
    assert.strictEqual(db._docs.expenses.exA.familyId, 'alice__bob');
    assert.strictEqual(db._docs.budgets.buA.familyId, 'alice__bob');
    // Only the author's own records: Bob's expense is Bob's to be decided.
    assert.strictEqual(db._docs.expenses.exB.familyId, '');
  });

  it('produces the same id as the client\'s FamilyKey.of — the two uids sorted, joined by __',
      async () => {
        // The read rule compares the stamped id with `familyId.split('__')`, and the client
        // queries `whereEqualTo("familyId", FamilyKey.of(me, partner))`. A different spelling
        // here would stamp every record into a family nobody's query ever asks for.
        const db = fakeDb(pairSeed({
          users: {
            zed: {partnerIds: ['amy']},
            amy: {partnerIds: ['zed']},
          },
          expenses: {ex: {createdByFirebaseUid: 'zed'}},
          budgets: {},
        }));

        await index.stampOwnBlankFamilyIds(db, 'zed', db._docs.users.zed);

        assert.strictEqual(db._docs.expenses.ex.familyId, 'amy__zed');
      });

  it('never re-derives a family a record already names', async () => {
    const db = fakeDb(pairSeed({
      expenses: {exA: {createdByFirebaseUid: 'alice', familyId: 'alice__bob'}},
    }));

    const outcome = await index.stampOwnBlankFamilyIds(db, 'alice', db._docs.users.alice);

    assert.strictEqual(outcome.perCollection.expenses, 0);
    assert.strictEqual(db._docs.expenses.exA.familyId, 'alice__bob');
  });

  it('touches nothing on a tombstone but its familyId', async () => {
    // Item 14: `deletedAtMillis`/`deletedBy` are how the deletion travels to the co-parent.
    // The stamp is what lets the family-keyed query reach the tombstone at all; the deletion
    // fields must come out byte-for-byte as they went in.
    const db = fakeDb(pairSeed({
      expenses: {
        gone: {
          createdByFirebaseUid: 'alice', familyId: '',
          deletedAtMillis: 1700000000000, deletedBy: 'alice',
        },
      },
    }));

    await index.stampOwnBlankFamilyIds(db, 'alice', db._docs.users.alice);

    assert.deepStrictEqual(db._docs.expenses.gone, {
      createdByFirebaseUid: 'alice', familyId: 'alice__bob',
      deletedAtMillis: 1700000000000, deletedBy: 'alice',
    });
  });

  it('refuses to guess for a person with two co-parents, and counts what it left', async () => {
    // Alice co-parents with Bob and with Carol. `partnerId` says Bob, but since M-4 that is only
    // the family a phone happens to be showing — using it would hand Carol-era spending to Bob.
    const db = fakeDb(pairSeed({
      users: {
        alice: {partnerIds: ['bob', 'carol'], partnerId: 'bob'},
        bob: {partnerIds: ['alice']},
        carol: {partnerIds: ['alice']},
      },
    }));

    const outcome = await index.stampOwnBlankFamilyIds(db, 'alice', db._docs.users.alice);

    assert.strictEqual(outcome.reason, 'ambiguous');
    assert.strictEqual(outcome.unresolved, 2);
    assert.strictEqual(outcome.stamped, 0);
    assert.strictEqual(db._docs.expenses.exA.familyId, '');
    assert.strictEqual(db._docs.budgets.buA.familyId, '');
  });

  it('still stamps the single-family partner of a two-family person', async () => {
    // Carol's only family is the one with Alice; that Alice has another does not make Carol's
    // records ambiguous. The mutual check reads `partnerIds`, not `partnerId` — Alice's
    // `partnerId` names Bob, and the pre-fix check would have called Carol's link one-sided.
    const db = fakeDb(pairSeed({
      users: {
        alice: {partnerIds: ['bob', 'carol'], partnerId: 'bob'},
        carol: {partnerIds: ['alice'], partnerId: 'alice'},
      },
      expenses: {exC: {createdByFirebaseUid: 'carol', familyId: ''}},
      budgets: {},
    }));

    const outcome = await index.stampOwnBlankFamilyIds(db, 'carol', db._docs.users.carol);

    assert.strictEqual(outcome.reason, '');
    assert.strictEqual(db._docs.expenses.exC.familyId, 'alice__carol');
  });

  it('leaves an unpaired author\'s records blank without counting them', async () => {
    // "Mine alone" is a value (item 18) — for an unpaired person it is simply the answer.
    const db = fakeDb(pairSeed({users: {alice: {}}}));

    const outcome = await index.stampOwnBlankFamilyIds(db, 'alice', db._docs.users.alice);

    assert.strictEqual(outcome.reason, 'unpaired');
    assert.strictEqual(outcome.unresolved, 0);
    assert.strictEqual(db._docs.expenses.exA.familyId, '');
  });

  it('does not revive a one-sided link', async () => {
    const db = fakeDb(pairSeed({
      users: {alice: {partnerIds: ['bob'], partnerId: 'bob'}, bob: {partnerIds: []}},
    }));

    const outcome = await index.stampOwnBlankFamilyIds(db, 'alice', db._docs.users.alice);

    assert.strictEqual(outcome.reason, 'notMutual');
    assert.strictEqual(outcome.unresolved, 2);
    assert.strictEqual(db._docs.expenses.exA.familyId, '');
  });

  it('skips a co-parent whose account is gone', async () => {
    const db = fakeDb(pairSeed({users: {alice: {partnerIds: ['bob']}}}));

    const outcome = await index.stampOwnBlankFamilyIds(db, 'alice', db._docs.users.alice);

    assert.strictEqual(outcome.reason, 'missingAccount');
  });

  describe('a person with one co-parent now and evidence of another before', () => {
    // Each case is a trace an earlier relationship leaves. With any of them, a blank record may
    // date from that relationship, and stamping it with the current one would move an old
    // household's expenses into the new one's ledger — the re-derivation item 18 forbids.

    it('an earlier accepted co-parent invitation', async () => {
      const db = fakeDb(pairSeed({
        invitations: {
          old: {fromUserId: 'alice', acceptedBy: 'dave', status: 'accepted'},
          now: {fromUserId: 'bob', acceptedBy: 'alice', status: 'accepted'},
        },
      }));

      const outcome = await index.stampOwnBlankFamilyIds(db, 'alice', db._docs.users.alice);

      assert.strictEqual(outcome.reason, 'priorRelationship');
      assert.strictEqual(db._docs.expenses.exA.familyId, '');
    });

    it('but not a guest, a friend, a professional, or an invitation nobody accepted', async () => {
      const db = fakeDb(pairSeed({
        invitations: {
          g: {fromUserId: 'alice', acceptedBy: 'gran', status: 'accepted', kind: 'guest'},
          f: {fromUserId: 'alice', acceptedBy: 'nina', status: 'accepted', kind: 'friend'},
          m: {fromUserId: 'alice', acceptedBy: 'med', status: 'accepted', kind: 'professional'},
          p: {fromUserId: 'alice', status: 'pending'},
          now: {fromUserId: 'alice', acceptedBy: 'bob', status: 'accepted'},
        },
      }));

      const outcome = await index.stampOwnBlankFamilyIds(db, 'alice', db._docs.users.alice);

      assert.strictEqual(outcome.reason, '');
      assert.strictEqual(db._docs.expenses.exA.familyId, 'alice__bob');
    });

    it('an unfinished revocation', async () => {
      const db = fakeDb(pairSeed({
        users: {
          alice: {partnerIds: ['bob'], pendingRevocationOf: ['dave']},
          bob: {partnerIds: ['alice']},
        },
      }));

      const outcome = await index.stampOwnBlankFamilyIds(db, 'alice', db._docs.users.alice);

      assert.strictEqual(outcome.reason, 'priorRelationship');
    });

    it('a record already stamped with another family', async () => {
      const db = fakeDb(pairSeed({
        events: {ev: {createdByFirebaseUid: 'alice', familyId: 'alice__dave'}},
      }));

      const outcome = await index.stampOwnBlankFamilyIds(db, 'alice', db._docs.users.alice);

      assert.strictEqual(outcome.reason, 'priorRelationship');
      assert.strictEqual(outcome.unresolved, 2);
    });

    it('an expense split with a third adult', async () => {
      const db = fakeDb(pairSeed({
        expenses: {
          exA: {createdByFirebaseUid: 'alice', familyId: '', splitBetween: ['alice', 'dave']},
        },
      }));

      const outcome = await index.stampOwnBlankFamilyIds(db, 'alice', db._docs.users.alice);

      assert.strictEqual(outcome.reason, 'priorRelationship');
    });

    it('an event still shared with a third adult', async () => {
      const db = fakeDb(pairSeed({
        events: {ev: {createdByFirebaseUid: 'alice', familyId: '', sharedWith: ['alice', 'dave']}},
      }));

      const outcome = await index.stampOwnBlankFamilyIds(db, 'alice', db._docs.users.alice);

      assert.strictEqual(outcome.reason, 'priorRelationship');
    });

    it('a change request addressed to a third adult', async () => {
      const db = fakeDb(pairSeed({
        change_requests: {cr: {requestedBy: 'alice', requestedTo: 'dave', familyId: ''}},
      }));

      const outcome = await index.stampOwnBlankFamilyIds(db, 'alice', db._docs.users.alice);

      assert.strictEqual(outcome.reason, 'priorRelationship');
    });
  });

  it('splits a long history into batches below the write cap', async () => {
    const expenses = {};
    for (let i = 0; i < 1000; i++) {
      expenses[`ex${i}`] = {createdByFirebaseUid: 'alice', familyId: ''};
    }
    const db = fakeDb(pairSeed({expenses, budgets: {}}));

    const outcome = await index.stampOwnBlankFamilyIds(db, 'alice', db._docs.users.alice);

    assert.strictEqual(outcome.stamped, 1000);
    assert.ok(db._commits.every((n) => n <= 450), `commits: ${db._commits}`);
  });
});

describe('backfillRecordFamilyIdsImpl, as the item 22 repair', () => {
  let index;

  before(() => {
    index = require('../index');
  });

  it('stamps both members of a pair and is a no-op the second time', async () => {
    const db = fakeDb(pairSeed());

    const first = await index.backfillRecordFamilyIdsImpl(db);
    const second = await index.backfillRecordFamilyIdsImpl(db);

    assert.strictEqual(first.stamped, 3);
    assert.strictEqual(first.perCollection.expenses, 2);
    assert.strictEqual(first.perCollection.budgets, 1);
    assert.strictEqual(second.stamped, 0);
    assert.strictEqual(db._docs.expenses.exB.familyId, 'alice__bob');
  });

  it('reports the ambiguous and their unresolved records rather than a bare skip', async () => {
    const db = fakeDb(pairSeed({
      users: {
        alice: {partnerIds: ['bob', 'carol'], partnerId: 'bob'},
        bob: {partnerIds: ['alice']},
        carol: {partnerIds: ['alice']},
      },
    }));

    const summary = await index.backfillRecordFamilyIdsImpl(db);

    assert.strictEqual(summary.skippedReasons.ambiguous, 1);
    assert.strictEqual(summary.unresolved, 2, 'Alice\'s expense and budget');
    // Bob's own expense is his, and his only family is the one with Alice.
    assert.strictEqual(db._docs.expenses.exB.familyId, 'alice__bob');
    assert.strictEqual(db._docs.expenses.exA.familyId, '');
  });
});

describe('stampFamilyOnCreateImpl', () => {
  let index;

  before(() => {
    index = require('../index');
  });

  it('stamps both members\' pre-pairing records when their family is created', async () => {
    const db = fakeDb(pairSeed());

    const outcomes = await index.stampFamilyOnCreateImpl(
        db, 'alice__bob', {members: ['alice', 'bob']});

    assert.strictEqual(outcomes.alice.stamped, 2);
    assert.strictEqual(outcomes.bob.stamped, 1);
    assert.strictEqual(db._docs.expenses.exA.familyId, 'alice__bob');
    assert.strictEqual(db._docs.expenses.exB.familyId, 'alice__bob');
  });

  it('stamps only the member for whom this is the one family', async () => {
    // Alice already co-parents with Carol and has just paired with Bob: her blanks could belong
    // to either family. Bob's could only belong to this one.
    const db = fakeDb(pairSeed({
      users: {
        alice: {partnerIds: ['carol', 'bob'], partnerId: 'carol'},
        bob: {partnerIds: ['alice'], partnerId: 'alice'},
        carol: {partnerIds: ['alice']},
      },
    }));

    const outcomes = await index.stampFamilyOnCreateImpl(
        db, 'alice__bob', {members: ['alice', 'bob']});

    assert.strictEqual(outcomes.alice.reason, 'ambiguous');
    assert.strictEqual(db._docs.expenses.exA.familyId, '');
    assert.strictEqual(outcomes.bob.reason, '');
    assert.strictEqual(db._docs.expenses.exB.familyId, 'alice__bob');
  });

  it('writes nothing if the pair is no longer together when it runs', async () => {
    // Created, unpaired, and Alice re-paired with Dave before the trigger got to her.
    const db = fakeDb(pairSeed({
      users: {
        alice: {partnerIds: ['dave']},
        dave: {partnerIds: ['alice']},
        bob: {partnerIds: []},
      },
    }));

    const outcomes = await index.stampFamilyOnCreateImpl(
        db, 'alice__bob', {members: ['alice', 'bob']});

    assert.strictEqual(outcomes.alice.reason, 'otherFamily');
    assert.strictEqual(outcomes.bob.reason, 'unpaired');
    assert.strictEqual(db._docs.expenses.exA.familyId, '');
  });

  it('ignores a document whose members do not spell its id', async () => {
    const db = fakeDb(pairSeed());

    const outcomes = await index.stampFamilyOnCreateImpl(
        db, 'alice__bob', {members: ['alice', 'mallory']});

    assert.deepStrictEqual(outcomes, {});
    assert.strictEqual(db._docs.expenses.exA.familyId, '');
  });
});
