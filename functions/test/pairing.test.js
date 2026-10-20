const test = require('firebase-functions-test')();
const assert = require('assert');
const sinon = require('sinon');
const admin = require('firebase-admin');

/**
 * Minimal in-memory Firestore covering what `acceptPairingInvitation` needs: reading an
 * invitation and two user documents inside a transaction, updating all three, and adding a
 * notification. Modeled on the fake in `unpair-callable.test.js`, trimmed to this callable's
 * calls (no queries, no batches).
 *
 * @param {!Object<string, !Object<string, !Object>>} seed Documents keyed by collection then
 *     document id.
 * @return {!Object} The fake, carrying `_docs` for assertions.
 */
function fakeDb(seed) {
  const docs = JSON.parse(JSON.stringify(seed));

  /**
   * Applies one field value, interpreting the `arrayUnion`/`arrayRemove` sentinels the real
   * Admin SDK resolves server-side.
   *
   * Without this the fake stored the sentinel object itself, and a test asserting on
   * `partnerIds` compared an `ArrayUnionTransform` against a list — which is a fake that
   * quietly disagrees with production about the one field multi-family pairing turns on.
   *
   * @param {*} current The value the field holds now.
   * @param {*} incoming The value the code under test wrote.
   * @return {*} The value to store.
   */
  function resolveValue(current, incoming) {
    if (!(incoming instanceof admin.firestore.FieldValue)) {
      return incoming;
    }
    const existing = Array.isArray(current) ? current : [];
    const elements = incoming.elements || [];
    // `_methodName` is how the SDK labels a sentinel; both spellings have shipped.
    const name = incoming._methodName || incoming.methodName || '';
    if (name.indexOf('arrayRemove') >= 0) {
      return existing.filter((v) => !elements.includes(v));
    }
    if (name.indexOf('arrayUnion') >= 0) {
      return existing.concat(elements.filter((v) => !existing.includes(v)));
    }
    return incoming;
  }

  /**
   * Merges an update map onto a stored document, resolving sentinels.
   *
   * @param {?Object} stored The document as it stands.
   * @param {!Object} update The update map.
   * @return {!Object} The merged document.
   */
  function merge(stored, update) {
    const next = Object.assign({}, stored);
    Object.keys(update).forEach((key) => {
      // `FieldValue.delete()` removes the key, as the real SDK does.
      if (update[key] instanceof admin.firestore.FieldValue &&
          update[key].constructor.name === 'DeleteTransform') {
        delete next[key];
        return;
      }
      next[key] = resolveValue(next[key], update[key]);
    });
    return next;
  }

  /**
   * Builds a document reference.
   *
   * @param {string} collection Collection name.
   * @param {string} id Document id.
   * @return {!Object} The reference.
   */
  function docRef(collection, id) {
    return {
      id,
      collection,
      async get() {
        const data = (docs[collection] || {})[id];
        return {exists: data !== undefined, data: () => data, ref: docRef(collection, id)};
      },
      async update(update) {
        docs[collection] = docs[collection] || {};
        docs[collection][id] = merge(docs[collection][id], update);
      },
    };
  }

  return {
    _docs: docs,
    collection(name) {
      return {
        doc: (id) => docRef(name, id),
        async add(data) {
          return {id: 'generated-1', data};
        },
      };
    },
    async runTransaction(fn) {
      const staged = [];
      const result = await fn({
        get: (ref) => ref.get(),
        update: (ref, update) => staged.push({ref, update}),
        // `set` replaces where `update` merges, which is the difference that matters for the
        // family document: it is written whole, once, at pairing.
        set: (ref, value) => staged.push({ref, value}),
      });
      staged.forEach(({ref, update, value}) => {
        docs[ref.collection] = docs[ref.collection] || {};
        docs[ref.collection][ref.id] = value !== undefined ?
          value :
          merge(docs[ref.collection][ref.id], update);
      });
      return result;
    },
  };
}

describe('acceptPairingInvitation', () => {
  let myFunctions;

  before(() => {
    myFunctions = require('../index');
  });

  after(() => {
    test.cleanup();
    sinon.restore();
  });

  it('rejects an unauthenticated caller', async () => {
    const wrapped = test.wrap(myFunctions.acceptPairingInvitation);
    await assert.rejects(
        () => wrapped({code: '4F7K2M'}, {}),
        (err) => err.code === 'unauthenticated',
    );
  });

  it('requires exactly one of code or invitationId', async () => {
    const wrapped = test.wrap(myFunctions.acceptPairingInvitation);
    await assert.rejects(
        () => wrapped({}, {auth: {uid: 'u1', token: {email: 'a@b.c'}}}),
        (err) => err.code === 'invalid-argument',
    );
  });

  it('puts the two parents in different slots', () => {
    const {assignSlots} = require('../index');
    assert.deepStrictEqual(
        assignSlots('mom'),
        {inviterRole: 'mom', accepterRole: 'dad'},
        'a pair where both defaulted to mom must be separated');
  });

  it('keeps the inviter slot and gives the accepter the other one', () => {
    const {assignSlots} = require('../index');
    assert.deepStrictEqual(
        assignSlots('dad'),
        {inviterRole: 'dad', accepterRole: 'mom'});
  });

  it('is idempotent for a pair that is already separated', () => {
    const {assignSlots} = require('../index');
    assert.deepStrictEqual(
        assignSlots('mom'),
        {inviterRole: 'mom', accepterRole: 'dad'});
  });

  it('falls back to mom for the inviter when no slot is stored', () => {
    const {assignSlots} = require('../index');
    assert.deepStrictEqual(
        assignSlots(undefined),
        {inviterRole: 'mom', accepterRole: 'dad'});
  });

  it('returns the accepter\'s newly assigned role alongside partnerId', async () => {
    const db = fakeDb({
      invitations: {
        inv1: {id: 'inv1', status: 'pending', fromUserId: 'alice', toEmail: 'bob@example.com'},
      },
      users: {
        alice: {id: 'alice', name: 'Alice', role: 'mom'},
        bob: {id: 'bob', name: 'Bob'},
      },
    });

    const result = await myFunctions.acceptPairingInvitationImpl(
        db, 'bob', 'bob@example.com', {code: null, invitationId: 'inv1'});

    assert.deepStrictEqual(result, {partnerId: 'alice', role: 'dad'});
    assert.strictEqual(db._docs.users.alice.role, 'mom');
    assert.strictEqual(db._docs.users.bob.role, 'dad');
  });

  it('names the new family on the push it queues for the inviter (M-8)', async () => {
    // The inviter's tap switches to this family, which is where a parent with two families
    // wants to land once the second one exists.
    const db = fakeDb({
      invitations: {
        inv1: {id: 'inv1', status: 'pending', fromUserId: 'alice', toEmail: 'bob@example.com'},
      },
      users: {
        alice: {id: 'alice', name: 'Alice', role: 'mom'},
        bob: {id: 'bob', name: 'Bob'},
      },
    });
    const queued = [];
    const collection = db.collection.bind(db);
    db.collection = (name) => {
      const ref = collection(name);
      if (name !== 'notification_queue') return ref;
      return Object.assign({}, ref, {
        async add(data) {
          queued.push(data);
          return ref.add(data);
        },
      });
    };

    await myFunctions.acceptPairingInvitationImpl(
        db, 'bob', 'bob@example.com', {code: null, invitationId: 'inv1'});

    assert.strictEqual(queued.length, 1);
    assert.strictEqual(queued[0].targetUserId, 'alice');
    assert.strictEqual(queued[0].data.type, 'pairing_accepted');
    assert.strictEqual(queued[0].data.familyId, 'alice__bob');
  });

  it('clears an unfinished revocation of the person being paired again, on both sides', async () => {
    // Alice's earlier unpair from Bob left its marker. Once they pair again the marker names a
    // live family, and a later unpair from somebody else would resume it and tear this family's
    // audiences apart. Other entries stay: they are other ended relationships.
    const db = fakeDb({
      invitations: {
        inv1: {id: 'inv1', status: 'pending', fromUserId: 'alice', toEmail: 'bob@example.com'},
      },
      users: {
        alice: {id: 'alice', name: 'Alice', role: 'mom', pendingRevocationOf: ['bob', 'dave']},
        bob: {id: 'bob', name: 'Bob', pendingRevocationOf: 'alice'},
      },
    });

    await myFunctions.acceptPairingInvitationImpl(
        db, 'bob', 'bob@example.com', {code: null, invitationId: 'inv1'});

    assert.deepStrictEqual(db._docs.users.alice.pendingRevocationOf, ['dave']);
    assert.ok(!('pendingRevocationOf' in db._docs.users.bob),
        'a legacy single-string marker naming the inviter goes too');
  });

  it('refuses a code that was spent between the lookup and the transaction', async () => {
    // Two accounts redeeming one code at the same moment both passed the plain read at the
    // top of the function; only the transaction can see that the first commit has already
    // marked the invitation accepted. Without the re-read the inviter ended up with a second
    // co-parent, holding a parent's access, on the strength of a code that had been spent.
    const db = fakeDb({
      invitations: {
        inv1: {id: 'inv1', status: 'pending', fromUserId: 'alice', toEmail: ''},
      },
      users: {
        alice: {id: 'alice', name: 'Alice', role: 'mom'},
        bob: {id: 'bob', name: 'Bob'},
      },
    });
    const plainTransaction = db.runTransaction.bind(db);
    db.runTransaction = (fn) => plainTransaction((tx) => {
      // The other redeemer's commit lands before this transaction reads.
      db._docs.invitations.inv1.status = 'accepted';
      return fn(tx);
    });

    await assert.rejects(
        () => myFunctions.acceptPairingInvitationImpl(
            db, 'bob', 'bob@example.com', {code: null, invitationId: 'inv1'}),
        (err) => err.details && err.details.reason === 'invitation-not-pending',
    );
    assert.strictEqual(db._docs.users.bob.partnerId, undefined, 'nothing may be paired');
  });

  it('records the relationship as a family document naming both adults', async () => {
    // `families/{id}.members` is what the security rules read to decide who may see the
    // records a pair shares. It is written here, as admin, and by no client ever — the
    // membership is the grant, so a client create path would let anyone name themselves a
    // member of any pair.
    const db = fakeDb({
      invitations: {
        inv1: {id: 'inv1', status: 'pending', fromUserId: 'alice', toEmail: 'bob@example.com'},
      },
      users: {
        alice: {id: 'alice', name: 'Alice', role: 'mom'},
        bob: {id: 'bob', name: 'Bob'},
      },
    });

    await myFunctions.acceptPairingInvitationImpl(
        db, 'bob', 'bob@example.com', {code: null, invitationId: 'inv1'});

    // The same derived id the custody model and the conversation already use, so the three
    // subsystems that were pair-keyed all along need no migration.
    const family = db._docs.families['alice__bob'];
    assert.ok(family, 'pairing must record the family');
    assert.deepStrictEqual(family.members, ['alice', 'bob']);
  });

  it('sorts the two members, so the id and the array agree', async () => {
    // The id is the sorted join; an unsorted array would still resolve to the same id and pass
    // every membership check, which is how a mismatch could sit there unnoticed until
    // something compared the two.
    const db = fakeDb({
      invitations: {
        inv1: {id: 'inv1', status: 'pending', fromUserId: 'zoe', toEmail: 'adam@example.com'},
      },
      users: {
        zoe: {id: 'zoe', name: 'Zoe', role: 'mom'},
        adam: {id: 'adam', name: 'Adam'},
      },
    });

    await myFunctions.acceptPairingInvitationImpl(
        db, 'adam', 'adam@example.com', {code: null, invitationId: 'inv1'});

    assert.deepStrictEqual(db._docs.families['adam__zoe'].members, ['adam', 'zoe']);
  });

  it('records the two slots on the family, keyed by uid', async () => {
    // The slot belongs to the pair, not to the person: somebody who co-parents with two
    // others holds two of them, and one `users/{uid}.role` cannot carry both. Written here
    // as admin and by no client, because a parent who could set their own would take the
    // co-parent's colour and re-point what `parentOwner` means across the calendar.
    const db = fakeDb({
      invitations: {
        inv1: {id: 'inv1', status: 'pending', fromUserId: 'alice', toEmail: 'bob@example.com'},
      },
      users: {
        alice: {id: 'alice', name: 'Alice', role: 'dad'},
        bob: {id: 'bob', name: 'Bob'},
      },
    });

    await myFunctions.acceptPairingInvitationImpl(
        db, 'bob', 'bob@example.com', {code: null, invitationId: 'inv1'});

    // The inviter keeps the slot they had; the accepter takes the other. Exactly what lands
    // on the two profiles, so the family and the profiles cannot disagree.
    assert.deepStrictEqual(db._docs.families['alice__bob'].slots, {alice: 'dad', bob: 'mom'});
    assert.strictEqual(db._docs.users.alice.role, 'dad');
    assert.strictEqual(db._docs.users.bob.role, 'mom');
  });

  it('carries each parent\'s own caresFor answer onto the family', async () => {
    // Per family, not per person: a man with children by one woman and a dog with another
    // must not get child sections in the pet family. The stored form is the one
    // `users/{uid}.caresFor` already uses, so there is a single spelling to parse.
    const db = fakeDb({
      invitations: {
        inv1: {id: 'inv1', status: 'pending', fromUserId: 'alice', toEmail: 'bob@example.com'},
      },
      users: {
        alice: {id: 'alice', name: 'Alice', role: 'mom', caresFor: 'CHILDREN|PETS'},
        bob: {id: 'bob', name: 'Bob', caresFor: 'PETS'},
      },
    });

    await myFunctions.acceptPairingInvitationImpl(
        db, 'bob', 'bob@example.com', {code: null, invitationId: 'inv1'});

    assert.deepStrictEqual(db._docs.families['alice__bob'].caresFor, {
      alice: 'CHILDREN|PETS',
      bob: 'PETS',
    });
  });

  it('reads an account that never answered as \'\', not as the string undefined', async () => {
    // `String(undefined)` is four characters that `FamilyKind.fromStored` drops as an unknown
    // name — but only after both phones have read it. Empty is the honest value, and an empty
    // union already reads as "show everything", so silence hides nothing.
    const db = fakeDb({
      invitations: {
        inv1: {id: 'inv1', status: 'pending', fromUserId: 'alice', toEmail: 'bob@example.com'},
      },
      users: {
        alice: {id: 'alice', name: 'Alice', role: 'mom'},
        bob: {id: 'bob', name: 'Bob'},
      },
    });

    await myFunctions.acceptPairingInvitationImpl(
        db, 'bob', 'bob@example.com', {code: null, invitationId: 'inv1'});

    assert.deepStrictEqual(db._docs.families['alice__bob'].caresFor, {alice: '', bob: ''});
  });
});

describe('pairing with more than one co-parent', () => {
  let myFunctions;

  before(() => {
    myFunctions = require('../index');
  });

  it('lets an already-paired account take a second co-parent', async () => {
    // The refusal used to be "one of the accounts is already paired". A person may co-parent
    // with more than one other adult, and this is where the second relationship is created.
    const db = fakeDb({
      invitations: {
        inv2: {id: 'inv2', status: 'pending', fromUserId: 'alice', toEmail: 'carol@example.com'},
      },
      users: {
        alice: {id: 'alice', name: 'Alice', role: 'mom', partnerIds: ['bob'], partnerId: 'bob'},
        carol: {id: 'carol', name: 'Carol'},
      },
    });

    const result = await myFunctions.acceptPairingInvitationImpl(
        db, 'carol', 'carol@example.com', {code: null, invitationId: 'inv2'});

    assert.strictEqual(result.partnerId, 'alice');
    assert.deepStrictEqual(db._docs.families['alice__carol'].members, ['alice', 'carol']);
  });

  it('accumulates co-parents rather than replacing the first', async () => {
    const db = fakeDb({
      invitations: {
        inv2: {id: 'inv2', status: 'pending', fromUserId: 'alice', toEmail: 'carol@example.com'},
      },
      users: {
        alice: {id: 'alice', name: 'Alice', role: 'mom', partnerIds: ['bob'], partnerId: 'bob'},
        carol: {id: 'carol', name: 'Carol'},
      },
    });

    await myFunctions.acceptPairingInvitationImpl(
        db, 'carol', 'carol@example.com', {code: null, invitationId: 'inv2'});

    assert.deepStrictEqual(db._docs.users.alice.partnerIds, ['bob', 'carol']);
    // The singular field stays pinned to the first. A build that predates the array can only
    // cope with one relationship, so it should keep showing the one it already knew rather
    // than being silently moved to a family it has never heard of.
    assert.strictEqual(db._docs.users.alice.partnerId, 'bob');
    // And the newcomer, who had none, gets it as their first.
    assert.strictEqual(db._docs.users.carol.partnerId, 'alice');
  });

  it('still refuses a repeat of the same pair', async () => {
    // Two people are one family, and a second `families/{id}` between them is the same
    // document — accepting again would re-run `assignSlots` and could flip the slots their
    // whole history is stamped with.
    const db = fakeDb({
      invitations: {
        inv2: {id: 'inv2', status: 'pending', fromUserId: 'alice', toEmail: 'bob@example.com'},
      },
      users: {
        alice: {id: 'alice', name: 'Alice', role: 'mom', partnerIds: ['bob'], partnerId: 'bob'},
        bob: {id: 'bob', name: 'Bob', role: 'dad', partnerIds: ['alice'], partnerId: 'alice'},
      },
    });

    await assert.rejects(
        () => myFunctions.acceptPairingInvitationImpl(
            db, 'bob', 'bob@example.com', {code: null, invitationId: 'inv2'}),
        (err) => err.details && err.details.reason === 'already-paired');
  });

  it('reads a document that carries only the old singular field', async () => {
    // Mid-migration: the array has not been written yet, but the account is paired. The two
    // shapes are unioned rather than one winning, so the existing relationship is still seen.
    const db = fakeDb({
      invitations: {
        inv2: {id: 'inv2', status: 'pending', fromUserId: 'alice', toEmail: 'bob@example.com'},
      },
      users: {
        alice: {id: 'alice', name: 'Alice', role: 'mom', partnerId: 'bob'},
        bob: {id: 'bob', name: 'Bob', role: 'dad', partnerId: 'alice'},
      },
    });

    await assert.rejects(
        () => myFunctions.acceptPairingInvitationImpl(
            db, 'bob', 'bob@example.com', {code: null, invitationId: 'inv2'}),
        (err) => err.details && err.details.reason === 'already-paired');
  });

  // A parent slot is stored once per person (`users/{uid}.role`) and every client reads it from
  // there, for every family that person is in. An accepter who already co-parents with somebody
  // else is given the slot opposite the inviter's; when that is not the slot they already hold,
  // accepting would move them in their first family too — the two parents of that family would
  // share a colour, and the accepter's phone would re-stamp every record and invert their custody
  // pattern. So the accept is refused, and the reason says which way round works.

  it('refuses to move an already-paired accepter to the other slot', async () => {
    // Zuzana co-parents with Pavel as mom. Barbora, new, shares a code; Zuzana enters it.
    // assignSlots would keep Barbora's (default) mom and make Zuzana dad.
    const db = fakeDb({
      invitations: {
        inv2: {id: 'inv2', status: 'pending', fromUserId: 'barbora', toEmail: ''},
      },
      users: {
        barbora: {id: 'barbora', name: 'Barbora'},
        zuzana: {id: 'zuzana', name: 'Zuzana', role: 'mom', partnerIds: ['pavel'], partnerId: 'pavel'},
      },
    });

    await assert.rejects(
        () => myFunctions.acceptPairingInvitationImpl(
            db, 'zuzana', 'zuzana@example.com', {code: null, invitationId: 'inv2'}),
        (err) => err.code === 'failed-precondition' &&
            err.details && err.details.reason === 'enter-their-code');
    assert.strictEqual(db._docs.users.zuzana.role, 'mom', 'the slot must not move');
    assert.deepStrictEqual(db._docs.users.zuzana.partnerIds, ['pavel']);
    assert.strictEqual(db._docs.invitations.inv2.status, 'pending', 'the code stays usable');
    assert.strictEqual((db._docs.families || {})['barbora__zuzana'], undefined);
  });

  it('pairs the same two people the other way round', async () => {
    // The direction the refusal points to: Zuzana shares her code and Barbora enters it.
    // Zuzana keeps mom, Barbora, who has no other family, takes dad.
    const db = fakeDb({
      invitations: {
        inv3: {id: 'inv3', status: 'pending', fromUserId: 'zuzana', toEmail: ''},
      },
      users: {
        barbora: {id: 'barbora', name: 'Barbora'},
        zuzana: {id: 'zuzana', name: 'Zuzana', role: 'mom', partnerIds: ['pavel'], partnerId: 'pavel'},
      },
    });

    const result = await myFunctions.acceptPairingInvitationImpl(
        db, 'barbora', 'barbora@example.com', {code: null, invitationId: 'inv3'});

    assert.deepStrictEqual(result, {partnerId: 'zuzana', role: 'dad'});
    assert.strictEqual(db._docs.users.zuzana.role, 'mom');
    assert.deepStrictEqual(db._docs.users.zuzana.partnerIds, ['pavel', 'barbora']);
  });

  it('lets an already-paired accepter in whose slot the pairing leaves alone', async () => {
    // Nothing moves: the inviter is dad, so the accepter takes mom, which she already is.
    const db = fakeDb({
      invitations: {
        inv2: {id: 'inv2', status: 'pending', fromUserId: 'david', toEmail: ''},
      },
      users: {
        david: {id: 'david', name: 'David', role: 'dad'},
        zuzana: {id: 'zuzana', name: 'Zuzana', role: 'mom', partnerIds: ['pavel'], partnerId: 'pavel'},
      },
    });

    const result = await myFunctions.acceptPairingInvitationImpl(
        db, 'zuzana', 'zuzana@example.com', {code: null, invitationId: 'inv2'});

    assert.deepStrictEqual(result, {partnerId: 'david', role: 'mom'});
    assert.deepStrictEqual(db._docs.users.zuzana.partnerIds, ['pavel', 'david']);
  });

  it('refuses two already-paired parents who hold the same slot', async () => {
    // Neither direction works while a slot is one per person: whoever accepts would move.
    const db = fakeDb({
      invitations: {
        inv2: {id: 'inv2', status: 'pending', fromUserId: 'eva', toEmail: ''},
      },
      users: {
        eva: {id: 'eva', name: 'Eva', role: 'mom', partnerIds: ['tomas'], partnerId: 'tomas'},
        zuzana: {id: 'zuzana', name: 'Zuzana', role: 'mom', partnerIds: ['pavel'], partnerId: 'pavel'},
      },
    });

    await assert.rejects(
        () => myFunctions.acceptPairingInvitationImpl(
            db, 'zuzana', 'zuzana@example.com', {code: null, invitationId: 'inv2'}),
        (err) => err.details && err.details.reason === 'slot-conflict');
    assert.strictEqual(db._docs.users.zuzana.role, 'mom');
    assert.strictEqual(db._docs.users.eva.role, 'mom');
  });

  it('still moves the slot of an accepter who has no other family', async () => {
    // The ordinary first pairing: a slot the accepter held alone (or a stale one left by an
    // earlier, ended relationship) is theirs to give up, and their phone re-stamps their records.
    const db = fakeDb({
      invitations: {
        inv1: {id: 'inv1', status: 'pending', fromUserId: 'alice', toEmail: ''},
      },
      users: {
        alice: {id: 'alice', name: 'Alice', role: 'mom'},
        bob: {id: 'bob', name: 'Bob', role: 'mom', partnerIds: [], partnerId: ''},
      },
    });

    const result = await myFunctions.acceptPairingInvitationImpl(
        db, 'bob', 'bob@example.com', {code: null, invitationId: 'inv1'});

    assert.deepStrictEqual(result, {partnerId: 'alice', role: 'dad'});
  });
});

describe('previewPairingInvitation', () => {
  let myFunctions;

  before(() => {
    myFunctions = require('../index');
  });

  /**
   * One pending co-parent invitation from Alice, and the two profiles behind it.
   *
   * @param {!Object=} overrides Fields to change on the invitation.
   * @param {!Object=} users Profiles to use instead of the default two.
   * @return {!Object} The fake Firestore.
   */
  function seeded(overrides = {}, users = null) {
    return fakeDb({
      invitations: {
        inv1: Object.assign(
            {id: 'inv1', status: 'pending', fromUserId: 'alice', toEmail: ''}, overrides),
      },
      users: users || {
        alice: {id: 'alice', name: 'Alice Novak', role: 'mom'},
        bob: {id: 'bob', name: 'Bob'},
      },
    });
  }

  it('rejects an unauthenticated caller', async () => {
    const wrapped = test.wrap(myFunctions.previewPairingInvitation);
    await assert.rejects(
        () => wrapped({code: '4F7K2M'}, {}),
        (err) => err.code === 'unauthenticated',
    );
  });

  it('requires a code', async () => {
    const wrapped = test.wrap(myFunctions.previewPairingInvitation);
    await assert.rejects(
        () => wrapped({}, {auth: {uid: 'bob', token: {}}}),
        (err) => err.code === 'invalid-argument',
    );
  });

  it('names the inviter by first name and changes nothing', async () => {
    const db = seeded();

    const result = await myFunctions.previewPairingInvitationImpl(
        db, 'bob', '', {code: null, invitationId: 'inv1'});

    assert.deepStrictEqual(result, {inviterName: 'Alice'});
    assert.strictEqual(db._docs.invitations.inv1.status, 'pending');
    assert.strictEqual(db._docs.users.bob.partnerId, undefined);
    assert.strictEqual(db._docs.families, undefined);
  });

  it('never answers with an email kept as a name', async () => {
    const db = seeded({}, {
      alice: {id: 'alice', name: 'alice@example.com'},
      bob: {id: 'bob', name: 'Bob'},
    });

    const result = await myFunctions.previewPairingInvitationImpl(
        db, 'bob', '', {code: null, invitationId: 'inv1'});

    assert.deepStrictEqual(result, {inviterName: ''});
  });

  it('answers with an empty name when the inviter has none', async () => {
    const db = seeded({}, {alice: {id: 'alice'}, bob: {id: 'bob'}});

    const result = await myFunctions.previewPairingInvitationImpl(
        db, 'bob', '', {code: null, invitationId: 'inv1'});

    assert.deepStrictEqual(result, {inviterName: ''});
  });

  // The preview refuses what the accept refuses, so a confirmation that names somebody is
  // never followed by a refusal.
  const refusals = [
    ['an expired code', {expiresAt: Date.now() - 1000}, 'bob', 'invitation-expired'],
    ['a spent code', {status: 'accepted'}, 'bob', 'invitation-not-pending'],
    ['the caller\'s own code', {}, 'alice', 'self-pairing'],
    ['a guest code', {kind: 'guest'}, 'bob', 'guest-invitation'],
    ['a friend code', {kind: 'friend'}, 'bob', 'friend-invitation'],
    ['a professional code', {kind: 'professional'}, 'bob', 'professional-invitation'],
    ['a code addressed to somebody else', {toEmail: 'carol@example.com'}, 'bob', 'wrong-recipient'],
  ];
  refusals.forEach(([what, overrides, caller, reason]) => {
    it(`refuses ${what} with the accept's reason`, async () => {
      const db = seeded(overrides);
      await assert.rejects(
          () => myFunctions.previewPairingInvitationImpl(
              db, caller, 'bob@example.com', {code: null, invitationId: 'inv1'}),
          (err) => err.details && err.details.reason === reason);
    });
  });

  it('refuses a code that matches no invitation', async () => {
    await assert.rejects(
        () => myFunctions.previewPairingInvitationImpl(
            seeded(), 'bob', '', {code: null, invitationId: 'missing'}),
        (err) => err.code === 'not-found' && err.details.reason === 'not-found');
  });

  it('refuses a repeat of the same pair', async () => {
    const db = seeded({}, {
      alice: {id: 'alice', name: 'Alice', role: 'mom', partnerIds: ['bob'], partnerId: 'bob'},
      bob: {id: 'bob', name: 'Bob', role: 'dad', partnerIds: ['alice'], partnerId: 'alice'},
    });
    await assert.rejects(
        () => myFunctions.previewPairingInvitationImpl(
            db, 'bob', '', {code: null, invitationId: 'inv1'}),
        (err) => err.details && err.details.reason === 'already-paired');
  });

  it('says which way round works before anybody confirms', async () => {
    // Bob already co-parents as mom elsewhere; Alice keeps mom, so accepting would move him.
    const db = seeded({}, {
      alice: {id: 'alice', name: 'Alice', role: 'mom'},
      bob: {id: 'bob', name: 'Bob', role: 'mom', partnerIds: ['carol'], partnerId: 'carol'},
    });
    await assert.rejects(
        () => myFunctions.previewPairingInvitationImpl(
            db, 'bob', '', {code: null, invitationId: 'inv1'}),
        (err) => err.details && err.details.reason === 'enter-their-code');
  });

  it('leaves a caller with no profile yet to the accept', async () => {
    const db = seeded({}, {alice: {id: 'alice', name: 'Alice', role: 'mom'}});

    const result = await myFunctions.previewPairingInvitationImpl(
        db, 'bob', '', {code: null, invitationId: 'inv1'});

    assert.deepStrictEqual(result, {inviterName: 'Alice'});
  });

  it('keeps only the first word of a name', () => {
    assert.strictEqual(myFunctions.previewFirstName('  Jana   Nováková '), 'Jana');
    assert.strictEqual(myFunctions.previewFirstName(''), '');
    assert.strictEqual(myFunctions.previewFirstName(null), '');
    assert.strictEqual(myFunctions.previewFirstName('x'.repeat(60)).length, 40);
  });
});
