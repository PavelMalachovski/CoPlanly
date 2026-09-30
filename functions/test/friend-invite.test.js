const test = require('firebase-functions-test')();
const assert = require('assert');
const sinon = require('sinon');

/**
 * Minimal in-memory Firestore covering what `acceptCalendarFriendInvitation` needs: reading an
 * invitation and the inviter's profile, writing the grant with `set` inside a transaction, and
 * adding notifications. Modeled on the fake in `guest-invite.test.js`, with `set` staged as well
 * as `update` — the friend path creates a `calendar_friends` document that does not exist yet.
 *
 * @param {!Object<string, !Object<string, !Object>>} seed Documents keyed by collection then
 *     document id.
 * @return {!Object} The fake, carrying `_docs` and `_added` for assertions.
 */
function fakeDb(seed) {
  const docs = JSON.parse(JSON.stringify(seed));

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
        docs[collection][id] = Object.assign({}, docs[collection][id], update);
      },
    };
  }

  return {
    _docs: docs,
    _added: [],
    collection(name) {
      const self = this;
      return {
        doc: (id) => docRef(name, id),
        async add(data) {
          self._added.push({collection: name, data});
          return {id: 'generated-1', data};
        },
      };
    },
    async runTransaction(fn) {
      const staged = [];
      const result = await fn({
        get: (ref) => ref.get(),
        update: (ref, update) => staged.push({ref, update, whole: false}),
        set: (ref, value) => staged.push({ref, update: value, whole: true}),
        delete: (ref) => staged.push({ref, remove: true}),
      });
      staged.forEach(({ref, update, whole, remove}) => {
        docs[ref.collection] = docs[ref.collection] || {};
        if (remove) {
          delete docs[ref.collection][ref.id];
          return;
        }
        docs[ref.collection][ref.id] = whole ?
          update : Object.assign({}, docs[ref.collection][ref.id], update);
      });
      return result;
    },
  };
}

/** Far enough out that no test run is ever near it. */
const GRANT_ENDS = Date.parse('2099-01-01T00:00:00Z');

/** Nina's grant over Alice and Bob's family: `{familyId}__{friendUid}` (L-5). */
const GRANT_ID = 'alice__bob__nina';

/**
 * A world with paired parents Alice and Bob, and a pending friend invitation Alice made.
 *
 * @param {!Object=} inviteOverrides Fields to change on the invitation.
 * @param {!Object=} userOverrides Users map to replace the default.
 * @return {!Object} The fake db.
 */
function seeded(inviteOverrides, userOverrides) {
  return fakeDb({
    invitations: {
      inv1: Object.assign({
        id: 'inv1',
        code: '4F7K2M',
        kind: 'friend',
        status: 'pending',
        fromUserId: 'alice',
        toEmail: '',
        friendExpiresAt: GRANT_ENDS,
      }, inviteOverrides || {}),
    },
    users: userOverrides || {
      alice: {id: 'alice', name: 'Alice', role: 'mom', partnerId: 'bob'},
      bob: {id: 'bob', name: 'Bob', role: 'dad', partnerId: 'alice'},
      nina: {id: 'nina', name: 'Nina'},
    },
  });
}

describe('acceptCalendarFriendInvitation', () => {
  let myFunctions;

  before(() => {
    myFunctions = require('../index');
  });

  after(() => {
    test.cleanup();
    sinon.restore();
  });

  const ref = {code: null, invitationId: 'inv1'};

  it('rejects an unauthenticated caller', async () => {
    const wrapped = test.wrap(myFunctions.acceptCalendarFriendInvitation);
    await assert.rejects(
        () => wrapped({code: '4F7K2M'}, {}),
        (err) => err.code === 'unauthenticated');
  });

  it('requires exactly one of code or invitationId', async () => {
    const wrapped = test.wrap(myFunctions.acceptCalendarFriendInvitation);
    await assert.rejects(
        () => wrapped({}, {auth: {uid: 'nina', token: {email: 'nina@example.com'}}}),
        (err) => err.code === 'invalid-argument');
  });

  it('writes one grant naming both parents, and no user document', async () => {
    const db = seeded();

    const result = await myFunctions.acceptCalendarFriendInvitationImpl(
        db, 'nina', 'nina@example.com', ref);

    const grant = db._docs.calendar_friends[GRANT_ID];
    assert.deepStrictEqual(grant.familyParents, ['alice', 'bob']);
    assert.strictEqual(grant.grantedBy, 'alice');
    assert.strictEqual(grant.expiresAtMillis, GRANT_ENDS);
    assert.strictEqual(grant.name, 'Nina');
    assert.deepStrictEqual(result.familyParents, ['alice', 'bob']);
    // The whole point of the central grant: no parent's profile is rewritten, and nothing
    // fans out over the family's events.
    assert.strictEqual(db._docs.users.nina.partnerId, undefined);
    assert.strictEqual(db._docs.users.alice.partnerId, 'bob');
  });

  it('copies the accepter Google picture into the grant', async () => {
    // The parents' "who can see this" list would otherwise need a second read of a document
    // that is not theirs. A Google sign-in puts the account picture in profilePhotoUrl.
    const db = seeded({}, {
      alice: {id: 'alice', name: 'Alice', role: 'mom', partnerId: 'bob'},
      bob: {id: 'bob', name: 'Bob', role: 'dad', partnerId: 'alice'},
      nina: {id: 'nina', name: 'Nina', profilePhotoUrl: 'https://lh3.googleusercontent.com/a/x'},
    });

    await myFunctions.acceptCalendarFriendInvitationImpl(db, 'nina', 'nina@example.com', ref);

    assert.strictEqual(
        db._docs.calendar_friends[GRANT_ID].photoUrl, 'https://lh3.googleusercontent.com/a/x');
  });

  it('writes no photoUrl key at all when the accepter has no picture', async () => {
    // Never `photoUrl: undefined` — Firestore rejects that outright, so the helper returns an
    // object to merge rather than a value.
    const db = seeded();
    await myFunctions.acceptCalendarFriendInvitationImpl(db, 'nina', 'nina@example.com', ref);
    assert.ok(!('photoUrl' in db._docs.calendar_friends[GRANT_ID]));
  });

  it('marks the invitation accepted', async () => {
    const db = seeded();
    await myFunctions.acceptCalendarFriendInvitationImpl(db, 'nina', 'nina@example.com', ref);
    assert.strictEqual(db._docs.invitations.inv1.status, 'accepted');
    assert.strictEqual(db._docs.invitations.inv1.acceptedBy, 'nina');
  });

  it('tells both parents, not only the inviter', async () => {
    const db = seeded();
    await myFunctions.acceptCalendarFriendInvitationImpl(db, 'nina', 'nina@example.com', ref);
    const targets = db._added
        .filter((a) => a.collection === 'notification_queue')
        .map((a) => a.data.targetUserId)
        .sort();
    assert.deepStrictEqual(targets, ['alice', 'bob']);
  });

  it('refuses a guest invitation offered to the friend path', async () => {
    const db = seeded({kind: 'guest'});
    await assert.rejects(
        () => myFunctions.acceptCalendarFriendInvitationImpl(db, 'nina', 'n@e.com', ref),
        (err) => err.code === 'failed-precondition');
  });

  it('refuses when the inviter is not paired', async () => {
    const db = seeded({}, {
      alice: {id: 'alice', name: 'Alice', role: 'mom', partnerId: ''},
      nina: {id: 'nina', name: 'Nina'},
    });
    await assert.rejects(
        () => myFunctions.acceptCalendarFriendInvitationImpl(db, 'nina', 'n@e.com', ref),
        (err) => err.code === 'failed-precondition');
  });

  it('refuses the co-parent taking a friend grant on their own family', async () => {
    const db = seeded();
    await assert.rejects(
        () => myFunctions.acceptCalendarFriendInvitationImpl(db, 'bob', 'bob@e.com', ref),
        (err) => err.code === 'failed-precondition');
  });

  it('refuses an invitation whose grant has already ended', async () => {
    const db = seeded({friendExpiresAt: 1000});
    await assert.rejects(
        () => myFunctions.acceptCalendarFriendInvitationImpl(db, 'nina', 'n@e.com', ref),
        (err) => err.code === 'failed-precondition');
  });

  it('refuses an invitation that is no longer pending', async () => {
    const db = seeded({status: 'accepted'});
    await assert.rejects(
        () => myFunctions.acceptCalendarFriendInvitationImpl(db, 'nina', 'n@e.com', ref),
        (err) => err.code === 'failed-precondition');
  });

  it('refuses the inviter accepting their own invitation', async () => {
    const db = seeded();
    await assert.rejects(
        () => myFunctions.acceptCalendarFriendInvitationImpl(db, 'alice', 'a@e.com', ref),
        (err) => err.code === 'invalid-argument');
  });
});

describe('the grant is scoped to one family (M-6)', () => {
  let myFunctions;

  before(() => {
    myFunctions = require('../index');
  });

  const ref = {code: null, invitationId: 'inv1'};

  /** Alice co-parents with Bob *and* with Carol; Bob's family is the one on her screen. */
  const twoFamilies = {
    alice: {id: 'alice', name: 'Alice', role: 'mom', partnerId: 'bob',
      partnerIds: ['bob', 'carol']},
    bob: {id: 'bob', name: 'Bob', role: 'dad', partnerId: 'alice', partnerIds: ['alice']},
    carol: {id: 'carol', name: 'Carol', role: 'dad', partnerId: 'alice', partnerIds: ['alice']},
    nina: {id: 'nina', name: 'Nina'},
  };

  it('stamps the family id the events read rule keys on', async () => {
    const db = seeded();

    await myFunctions.acceptCalendarFriendInvitationImpl(db, 'nina', 'nina@example.com', ref);

    assert.strictEqual(db._docs.calendar_friends[GRANT_ID].familyId, 'alice__bob');
  });

  it('honours the family the invitation was generated in', async () => {
    // Alice was looking at her family with Carol when she made the code. Without this the grant
    // would land in whichever family she happens to be showing when the friend redeems it —
    // days later, and invisibly.
    const db = seeded({familyId: 'alice__carol'}, twoFamilies);

    const result = await myFunctions.acceptCalendarFriendInvitationImpl(
        db, 'nina', 'nina@example.com', ref);

    assert.strictEqual(db._docs.calendar_friends['alice__carol__nina'].familyId, 'alice__carol');
    assert.deepStrictEqual(result.familyParents, ['alice', 'carol']);
  });

  it('refuses a family the inviter is no longer part of', async () => {
    // The relationship ended between generating the code and redeeming it. The id is a claim,
    // not proof: it is checked against the inviter's live co-parents — and a stale one is
    // refused rather than re-pointed at whichever family the inviter happens to be showing,
    // which would hand the friend a household nobody invited them into.
    const db = seeded({familyId: 'alice__dave'}, twoFamilies);

    await assert.rejects(
        () => myFunctions.acceptCalendarFriendInvitationImpl(db, 'nina', 'nina@example.com', ref),
        (err) => err.code === 'failed-precondition' && err.details.reason === 'inviter-not-paired');
    assert.deepStrictEqual(Object.keys(db._docs.calendar_friends || {}), []);
    assert.strictEqual(db._docs.invitations.inv1.status, 'pending');
  });

  it('refuses a family the inviter is not even named in', async () => {
    const db = seeded({familyId: 'bob__carol'}, twoFamilies);

    await assert.rejects(
        () => myFunctions.acceptCalendarFriendInvitationImpl(db, 'nina', 'nina@example.com', ref),
        (err) => err.code === 'failed-precondition' && err.details.reason === 'inviter-not-paired');
    assert.deepStrictEqual(Object.keys(db._docs.calendar_friends || {}), []);
  });

  it('refuses a stale family even when the inviter still has another one', async () => {
    // Alice unpaired from Carol after making the code in that family; Bob's family is still
    // live and on her screen. The friend must not land in it.
    const aliceWithBobOnly = Object.assign({}, twoFamilies, {
      alice: {id: 'alice', name: 'Alice', role: 'mom', partnerId: 'bob', partnerIds: ['bob']},
      carol: {id: 'carol', name: 'Carol', role: 'dad', partnerId: '', partnerIds: []},
    });
    const db = seeded({familyId: 'alice__carol'}, aliceWithBobOnly);

    await assert.rejects(
        () => myFunctions.acceptCalendarFriendInvitationImpl(db, 'nina', 'nina@example.com', ref),
        (err) => err.code === 'failed-precondition');
    assert.deepStrictEqual(Object.keys(db._docs.calendar_friends || {}), []);
  });

  it('falls back for an invitation made by a build that predates M-6', async () => {
    const db = seeded({}, twoFamilies);

    await myFunctions.acceptCalendarFriendInvitationImpl(db, 'nina', 'nina@example.com', ref);

    assert.strictEqual(db._docs.calendar_friends[GRANT_ID].familyId, 'alice__bob');
  });
});

describe('one grant per family (L-5)', () => {
  let myFunctions;

  before(() => {
    myFunctions = require('../index');
  });

  /**
   * Two unrelated families — Alice and Bob, Dave and Erin — each with a pending friend
   * invitation, so one grandmother can be admitted by both.
   *
   * @param {!Object=} extra More collections to seed.
   * @return {!Object} The fake db.
   */
  function twoFamilies(extra) {
    return fakeDb(Object.assign({
      invitations: {
        inv1: {id: 'inv1', kind: 'friend', status: 'pending', fromUserId: 'alice', toEmail: '',
          friendExpiresAt: GRANT_ENDS, familyId: 'alice__bob'},
        inv2: {id: 'inv2', kind: 'friend', status: 'pending', fromUserId: 'dave', toEmail: '',
          friendExpiresAt: GRANT_ENDS - 1, familyId: 'dave__erin'},
      },
      users: {
        alice: {id: 'alice', name: 'Alice', partnerId: 'bob', partnerIds: ['bob']},
        bob: {id: 'bob', name: 'Bob', partnerId: 'alice', partnerIds: ['alice']},
        dave: {id: 'dave', name: 'Dave', partnerId: 'erin', partnerIds: ['erin']},
        erin: {id: 'erin', name: 'Erin', partnerId: 'dave', partnerIds: ['dave']},
        nina: {id: 'nina', name: 'Nina'},
      },
    }, extra || {}));
  }

  const accept = (db, invitationId) => myFunctions.acceptCalendarFriendInvitationImpl(
      db, 'nina', 'nina@example.com', {code: null, invitationId});

  it('keeps the first family when a second one admits the same friend', async () => {
    // The defect: both grants lived at `calendar_friends/nina`, and the second redemption
    // overwrote the first without anybody being told.
    const db = twoFamilies();

    await accept(db, 'inv1');
    await accept(db, 'inv2');

    assert.deepStrictEqual(
        Object.keys(db._docs.calendar_friends).sort(), ['alice__bob__nina', 'dave__erin__nina']);
    const first = db._docs.calendar_friends['alice__bob__nina'];
    assert.deepStrictEqual(first.familyParents, ['alice', 'bob']);
    assert.strictEqual(first.expiresAtMillis, GRANT_ENDS);
    const second = db._docs.calendar_friends['dave__erin__nina'];
    assert.strictEqual(second.familyId, 'dave__erin');
    assert.strictEqual(second.expiresAtMillis, GRANT_ENDS - 1);
  });

  it('repeats the family and the friend in the grant, which the rule checks against the id',
      async () => {
        const db = twoFamilies();

        await accept(db, 'inv1');

        const grant = db._docs.calendar_friends['alice__bob__nina'];
        assert.strictEqual(grant.familyId, 'alice__bob');
        assert.strictEqual(grant.friendUid, 'nina');
      });

  it('re-keys a per-person grant from before L-5 when the friend redeems another family',
      async () => {
        const db = twoFamilies({
          calendar_friends: {
            nina: {familyParents: ['dave', 'erin'], familyId: 'dave__erin', name: 'Nina',
              grantedBy: 'dave', grantedAtMillis: 1, expiresAtMillis: GRANT_ENDS},
          },
        });

        await accept(db, 'inv1');

        assert.deepStrictEqual(
            Object.keys(db._docs.calendar_friends).sort(),
            ['alice__bob__nina', 'dave__erin__nina']);
        const moved = db._docs.calendar_friends['dave__erin__nina'];
        assert.strictEqual(moved.friendUid, 'nina');
        assert.strictEqual(moved.grantedBy, 'dave');
        assert.strictEqual(moved.expiresAtMillis, GRANT_ENDS);
      });

  it('lets a new grant over the same family supersede the legacy one', async () => {
    const db = twoFamilies({
      calendar_friends: {nina: {familyParents: ['alice', 'bob'], expiresAtMillis: 5}},
    });

    await accept(db, 'inv1');

    assert.deepStrictEqual(Object.keys(db._docs.calendar_friends), ['alice__bob__nina']);
    assert.strictEqual(db._docs.calendar_friends['alice__bob__nina'].expiresAtMillis, GRANT_ENDS);
  });

  it('only deletes a legacy grant that has already lapsed', async () => {
    const db = twoFamilies({
      calendar_friends: {nina: {familyParents: ['dave', 'erin'], expiresAtMillis: 5}},
    });

    await accept(db, 'inv1');

    assert.deepStrictEqual(Object.keys(db._docs.calendar_friends), ['alice__bob__nina']);
  });

  it('leaves a legacy grant it cannot name a family for where it is', async () => {
    const db = twoFamilies({
      calendar_friends: {nina: {familyParents: ['dave'], expiresAtMillis: GRANT_ENDS}},
    });

    await accept(db, 'inv1');

    assert.deepStrictEqual(
        Object.keys(db._docs.calendar_friends).sort(), ['alice__bob__nina', 'nina']);
  });

  it('widens the friend profile gate to the new family, keeping the first', async () => {
    const db = twoFamilies({
      friend_profiles: {nina: {uid: 'nina', name: 'Nina', familyParents: ['alice', 'bob']}},
    });

    await accept(db, 'inv1');
    assert.deepStrictEqual(db._docs.friend_profiles.nina.familyParents, ['alice', 'bob']);
    await accept(db, 'inv2');

    assert.deepStrictEqual(
        db._docs.friend_profiles.nina.familyParents, ['alice', 'bob', 'dave', 'erin']);
    assert.strictEqual(db._docs.friend_profiles.nina.name, 'Nina');
  });

  it('creates no profile for a friend who has not written one', async () => {
    const db = twoFamilies();
    await accept(db, 'inv1');
    assert.strictEqual(db._docs.friend_profiles, undefined);
  });
});

describe('rekeyedLegacyCalendarFriendGrant', () => {
  let myFunctions;

  before(() => {
    myFunctions = require('../index');
  });

  it('moves a legacy grant to its family, stamping a missing familyId', () => {
    const out = myFunctions.rekeyedLegacyCalendarFriendGrant(
        'nina', {familyParents: ['bob', 'alice'], expiresAtMillis: 9});
    assert.deepStrictEqual(out, {
      id: 'alice__bob__nina',
      data: {familyParents: ['bob', 'alice'], expiresAtMillis: 9, familyId: 'alice__bob',
        friendUid: 'nina'},
    });
  });

  it('leaves a per-family id alone', () => {
    assert.strictEqual(myFunctions.rekeyedLegacyCalendarFriendGrant(
        'alice__bob__nina', {familyParents: ['alice', 'bob'], familyId: 'alice__bob'}), null);
  });

  it('refuses to name a family it cannot state honestly', () => {
    [
      {familyParents: ['alice']},
      {familyParents: ['alice', 'alice']},
      {familyParents: ['alice', '']},
      {},
      // A stored family that disagrees with the parents is a person's call, not a migration's.
      {familyParents: ['alice', 'bob'], familyId: 'alice__carol'},
    ].forEach((data) => {
      assert.strictEqual(myFunctions.rekeyedLegacyCalendarFriendGrant('nina', data), null);
    });
  });
});

describe('partnerFromFamilyId', () => {
  let myFunctions;

  before(() => {
    myFunctions = require('../index');
  });

  it('names the other member, from either side', () => {
    assert.strictEqual(myFunctions.partnerFromFamilyId('alice__bob', 'alice'), 'bob');
    assert.strictEqual(myFunctions.partnerFromFamilyId('alice__bob', 'bob'), 'alice');
  });

  it('refuses an id that does not name the caller', () => {
    assert.strictEqual(myFunctions.partnerFromFamilyId('bob__carol', 'alice'), '');
  });

  it('refuses anything malformed rather than throwing', () => {
    // It is fed a value straight off an invitation document, which anyone may write.
    ['', 'alice', 'a__b__c', 'alice__alice', null, undefined, 42, {}].forEach((value) => {
      assert.strictEqual(myFunctions.partnerFromFamilyId(value, 'alice'), '');
    });
  });
});

describe('acceptPairingInvitation refuses a friend invitation', () => {
  let myFunctions;

  before(() => {
    myFunctions = require('../index');
  });

  it('never turns a friend into a co-parent', async () => {
    // The dangerous direction of the three-callable split: `assignSlots` would hand a friend a
    // permanent parent slot and write `partnerId` on both users.
    const db = seeded();
    await assert.rejects(
        () => myFunctions.acceptPairingInvitationImpl(db, 'nina', 'nina@example.com',
            {code: null, invitationId: 'inv1'}),
        (err) => err.code === 'failed-precondition' &&
                 err.details && err.details.reason === 'friend-invitation');
  });
});
