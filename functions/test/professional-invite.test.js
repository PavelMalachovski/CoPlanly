const test = require('firebase-functions-test')();
const assert = require('assert');

/**
 * Professional access (MON-18): the fourth redemption callable, its refusal by the other three,
 * its nightly sweep, and its end at unpair.
 *
 * The fake follows `friend-invite.test.js`, plus the equality query and batched delete that
 * unpair's cleanup uses.
 *
 * @param {!Object<string, !Object<string, !Object>>} seed Documents by collection then id.
 * @return {!Object} The fake, carrying `_docs` and `_added`.
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
      async delete() {
        delete (docs[collection] || {})[id];
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
        where(field, op, value) {
          const filters = [[field, op, value]];
          const query = {
            where(f, o, v) {
              filters.push([f, o, v]);
              return query;
            },
            limit() {
              return query;
            },
            async get() {
              const ids = Object.keys(docs[name] || {}).filter((id) => filters.every(([f, o, v]) => {
                const actual = docs[name][id][f];
                if (o === '==') return actual === v;
                if (o === '>') return typeof actual === 'number' && actual > v;
                if (o === '<=') return typeof actual === 'number' && actual <= v;
                throw new Error(`unsupported operator ${o}`);
              }));
              return {
                size: ids.length,
                docs: ids.map((id) => ({id, data: () => docs[name][id], ref: docRef(name, id)})),
              };
            },
          };
          return query;
        },
        async add(data) {
          self._added.push({collection: name, data});
          return {id: `generated-${self._added.length}`, data};
        },
      };
    },
    batch() {
      const ops = [];
      return {
        delete(ref) {
          ops.push(ref);
        },
        async commit() {
          ops.forEach((ref) => delete (docs[ref.collection] || {})[ref.id]);
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

const NOW = Date.parse('2026-09-23T12:00:00Z');
const DAY = 24 * 60 * 60 * 1000;
const GRANT_ID = 'alice__bob__med';

/**
 * Alice and Bob, paired, with a pending professional invitation Alice made for their family.
 *
 * @param {!Object=} inviteOverrides Fields to change on the invitation.
 * @param {!Object=} extra Collections to add or replace.
 * @return {!Object} The fake db.
 */
function seeded(inviteOverrides, extra) {
  return fakeDb(Object.assign({
    invitations: {
      inv1: Object.assign({
        id: 'inv1', code: 'P7M2QX', kind: 'professional', status: 'pending',
        fromUserId: 'alice', toEmail: '', expiresAt: NOW + 7 * DAY,
        professionalRole: 'mediator', professionalExpiresAt: NOW + 60 * DAY,
        familyId: 'alice__bob',
      }, inviteOverrides || {}),
    },
    users: {
      alice: {name: 'Alice', role: 'mom', partnerId: 'bob', partnerIds: ['bob']},
      bob: {name: 'Bob', role: 'dad', partnerId: 'alice', partnerIds: ['alice']},
      med: {name: 'Mgr. Nováková', profilePhotoUrl: 'https://lh3.googleusercontent.com/a/m'},
    },
    families: {
      alice__bob: {members: ['alice', 'bob'], slots: {alice: 'mom', bob: 'dad'}},
    },
  }, extra || {}));
}

const ref = {code: null, invitationId: 'inv1'};

describe('acceptProfessionalInvitation', () => {
  let fns;

  before(() => {
    fns = require('../index');
  });

  after(() => {
    test.cleanup();
  });

  it('rejects an unauthenticated caller', async () => {
    const wrapped = test.wrap(fns.acceptProfessionalInvitation);
    await assert.rejects(() => wrapped({code: 'P7M2QX'}, {}),
        (err) => err.code === 'unauthenticated');
  });

  it('requires exactly one of code or invitationId', async () => {
    const wrapped = test.wrap(fns.acceptProfessionalInvitation);
    await assert.rejects(
        () => wrapped({}, {auth: {uid: 'med', token: {}}}),
        (err) => err.code === 'invalid-argument');
  });

  it('writes one grant carrying only the inviter\'s consent', async () => {
    const db = seeded();

    const result = await fns.acceptProfessionalInvitationImpl(db, 'med', '', ref, NOW);

    const grant = db._docs.professional_grants[GRANT_ID];
    assert.strictEqual(result.grantId, GRANT_ID);
    assert.strictEqual(grant.familyId, 'alice__bob');
    assert.deepStrictEqual(grant.familyParents, ['alice', 'bob']);
    assert.strictEqual(grant.proUid, 'med');
    assert.strictEqual(grant.role, 'mediator');
    assert.strictEqual(grant.name, 'Mgr. Nováková');
    assert.strictEqual(grant.photoUrl, 'https://lh3.googleusercontent.com/a/m');
    // The whole two-consent design: redeeming the code opens nothing until Bob says yes.
    assert.deepStrictEqual(Object.keys(grant.consents), ['alice']);
    assert.strictEqual(grant.expiresAtMillis, NOW + 60 * DAY);
    assert.deepStrictEqual(grant.parentNames, {alice: 'Alice', bob: 'Bob'});
    assert.deepStrictEqual(grant.parentSlots, {alice: 'mom', bob: 'dad'});
    assert.strictEqual(db._docs.invitations.inv1.status, 'accepted');
  });

  it('touches no user document', async () => {
    const db = seeded();
    await fns.acceptProfessionalInvitationImpl(db, 'med', '', ref, NOW);
    assert.deepStrictEqual(db._docs.users.med,
        {name: 'Mgr. Nováková', profilePhotoUrl: 'https://lh3.googleusercontent.com/a/m'});
    assert.deepStrictEqual(db._docs.users.alice.partnerIds, ['bob']);
  });

  it('clamps an end beyond the ceiling to 180 days from now', async () => {
    const db = seeded({professionalExpiresAt: NOW + 400 * DAY});
    const result = await fns.acceptProfessionalInvitationImpl(db, 'med', '', ref, NOW);
    assert.strictEqual(result.expiresAtMillis, NOW + fns.PROFESSIONAL_MAX_DAYS * DAY);
  });

  it('tells both parents with a type and a name, never a sentence', async () => {
    const db = seeded();
    await fns.acceptProfessionalInvitationImpl(db, 'med', '', ref, NOW);
    const pushes = db._added.filter((a) => a.collection === 'notification_queue');
    assert.deepStrictEqual(pushes.map((p) => p.data.targetUserId).sort(), ['alice', 'bob']);
    pushes.forEach((p) => {
      assert.strictEqual(p.data.data.type, 'professional_access_requested');
      assert.strictEqual(p.data.data.actorName, 'Mgr. Nováková');
      assert.strictEqual(p.data.data.familyId, 'alice__bob');
      assert.ok(!('title' in p.data.data) && !('body' in p.data.data));
    });
  });

  const refusals = [
    ['a missing profession', {professionalRole: undefined}, 'invitation-malformed'],
    ['an unknown profession', {professionalRole: 'co_parent'}, 'invitation-malformed'],
    ['an end already past', {professionalExpiresAt: NOW - 1}, 'grant-expired'],
    ['no end at all', {professionalExpiresAt: undefined}, 'grant-expired'],
    ['no family', {familyId: ''}, 'invitation-malformed'],
    ['a family the inviter is not in', {familyId: 'bob__carol'}, 'invitation-malformed'],
    ['an invitation no longer pending', {status: 'accepted'}, 'invitation-not-pending'],
    ['an expired offer', {expiresAt: NOW - 1}, 'invitation-expired'],
    ['a friend invitation', {kind: 'friend'}, 'not-a-professional-invitation'],
    ['a co-parent invitation', {kind: undefined}, 'not-a-professional-invitation'],
  ];
  refusals.forEach(([what, overrides, reason]) => {
    it(`refuses ${what}`, async () => {
      const db = seeded(overrides);
      await assert.rejects(
          () => fns.acceptProfessionalInvitationImpl(db, 'med', '', ref, NOW),
          (err) => err.details && err.details.reason === reason);
      assert.strictEqual(db._docs.professional_grants, undefined);
    });
  });

  it('refuses when the family is no longer a live pairing', async () => {
    const db = seeded({}, {
      users: {
        alice: {name: 'Alice', partnerIds: []},
        bob: {name: 'Bob', partnerIds: ['alice']},
        med: {name: 'Med'},
      },
    });
    await assert.rejects(
        () => fns.acceptProfessionalInvitationImpl(db, 'med', '', ref, NOW),
        (err) => err.details && err.details.reason === 'inviter-not-paired');
  });

  it('refuses the co-parent taking a professional grant on their own family', async () => {
    const db = seeded();
    await assert.rejects(
        () => fns.acceptProfessionalInvitationImpl(db, 'bob', '', ref, NOW),
        (err) => err.details && err.details.reason === 'already-entitled');
  });

  it('refuses the inviter accepting their own invitation', async () => {
    const db = seeded();
    await assert.rejects(
        () => fns.acceptProfessionalInvitationImpl(db, 'alice', '', ref, NOW),
        (err) => err.details && err.details.reason === 'self-pairing');
  });
});

describe('the other callables refuse a professional invitation', () => {
  let fns;

  before(() => {
    fns = require('../index');
  });

  it('acceptPairingInvitation never turns a mediator into a co-parent', async () => {
    const db = seeded();
    await assert.rejects(
        () => fns.acceptPairingInvitationImpl(db, 'med', '', ref),
        (err) => err.code === 'failed-precondition' &&
                 err.details && err.details.reason === 'professional-invitation');
    assert.strictEqual(db._docs.users.med.partnerId, undefined);
  });

  it('acceptCalendarFriendInvitation refuses it', async () => {
    const db = seeded();
    await assert.rejects(
        () => fns.acceptCalendarFriendInvitationImpl(db, 'med', '', ref),
        (err) => err.details && err.details.reason === 'not-a-friend-invitation');
    assert.strictEqual(db._docs.calendar_friends, undefined);
  });

  it('acceptGuestInvitation refuses it', async () => {
    const db = seeded();
    await assert.rejects(
        () => fns.acceptGuestInvitationImpl(db, 'med', '', ref),
        (err) => err.details && err.details.reason === 'not-a-guest-invitation');
  });
});

describe('sweepLapsedProfessionalGrants', () => {
  let fns;

  before(() => {
    fns = require('../index');
  });

  it('deletes a lapsed grant, keeps a live one, and never one without an expiry', async () => {
    const db = fakeDb({
      professional_grants: {
        lapsed: {familyId: 'a__b', expiresAtMillis: NOW - DAY},
        endsNow: {familyId: 'a__b', expiresAtMillis: NOW},
        live: {familyId: 'a__b', expiresAtMillis: NOW + DAY},
        noExpiry: {familyId: 'a__b'},
        zero: {familyId: 'a__b', expiresAtMillis: 0},
      },
    });

    const removed = await fns.sweepLapsedProfessionalGrantsImpl(db, NOW);

    assert.strictEqual(removed, 2);
    assert.deepStrictEqual(Object.keys(db._docs.professional_grants).sort(),
        ['live', 'noExpiry', 'zero']);
  });
});

describe('unpair ends the family\'s professional grants', () => {
  let fns;

  before(() => {
    fns = require('../index');
  });

  it('deletes every grant over the ended family and none over another', async () => {
    const db = seeded({}, {
      professional_grants: {
        [GRANT_ID]: {familyId: 'alice__bob', proUid: 'med', expiresAtMillis: NOW + DAY},
        'alice__bob__lawyer': {familyId: 'alice__bob', proUid: 'lawyer', expiresAtMillis: NOW + DAY},
        'alice__carol__med': {familyId: 'alice__carol', proUid: 'med', expiresAtMillis: NOW + DAY},
      },
    });
    // The rest of unpair (audience sweep) needs more of Firestore than this fake has; what is
    // pinned here is only that the grants are gone before it runs.
    await fns.unpairCoParentImpl(db, 'alice', 'bob').catch(() => {});

    assert.deepStrictEqual(Object.keys(db._docs.professional_grants), ['alice__carol__med']);
  });
});
