/**
 * The friend (item 16): a trusted third person with their own account who reads a family's
 * calendar without occupying a parent slot.
 *
 * Three collections work together:
 *   - `friend_profiles/{uid}` — the friend authors their own profile; the parents read it.
 *   - `calendar_friends/{familyId}__{friendUid}` — one family's grant of calendar read access,
 *     with an expiry. One document per family (L-5), so a friend of two families holds two.
 *   - `events` — read now also admits a *live* calendar friend of the event's family, so the
 *     friend queries the family's events without any event document being rewritten.
 */

const {
  CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-friend';
const MOM = 'uid-mom';
const DAD = 'uid-dad';
const FRIEND = 'uid-friend';
const STRANGER = 'uid-stranger';

// Mom's *second* co-parent. A person may co-parent with more than one other adult (M-4), and the
// two families that person belongs to must have nothing in common — which is what M-6 finally
// makes true of a calendar friend.
const OTHER_PARENT = 'uid-other-parent';

// A second, unrelated family that also admits the same friend (L-5).
const AUNT = 'uid-aunt';
const UNCLE = 'uid-uncle';

// `FamilyKey.of` — the two uids sorted and joined. Written out rather than computed so a test
// that fails says which family it meant.
const FAMILY = 'uid-dad__uid-mom';
const OTHER_FAMILY = 'uid-mom__uid-other-parent';
const SECOND_FAMILY = 'uid-aunt__uid-uncle';
// A family neither of the friend's grants names.
const THIRD_FAMILY = 'uid-stranger__uid-uncle';

const FAR_FUTURE = 4102444800000; // 2100-01-01
const PAST = 1000; // 1970

/**
 * The document id of [friendUid]'s grant over [familyId] — what `calendarFriendGrantId` in
 * functions/index.js writes and `isCalendarFriendOf` builds.
 *
 * @param {string} familyId The family.
 * @param {string=} friendUid The friend; FRIEND by default.
 * @return {string} The path of the grant.
 */
function grantPath(familyId, friendUid) {
  return `calendar_friends/${familyId}__${friendUid || FRIEND}`;
}

function profile(overrides) {
  return Object.assign({
    uid: FRIEND, name: 'Babushka', role: 'GRANDPARENT',
    phones: ['+420111222333'], bloodGroup: 'A+', familyParents: [MOM, DAD],
  }, overrides);
}

function grant(overrides) {
  return Object.assign({
    familyParents: [MOM, DAD], familyId: FAMILY, friendUid: FRIEND, grantedBy: MOM,
    grantedAtMillis: 1, expiresAtMillis: FAR_FUTURE,
  }, overrides);
}

/** A per-person grant as it was written before L-5: keyed on the friend, no `friendUid`. */
function legacyGrant() {
  const legacy = grant({});
  delete legacy.friendUid;
  return legacy;
}

function secondGrant(overrides) {
  return grant(Object.assign(
      {familyParents: [AUNT, UNCLE], familyId: SECOND_FAMILY, grantedBy: AUNT}, overrides));
}

function event(overrides) {
  return Object.assign({
    createdByFirebaseUid: MOM, title: 'School pickup', eventType: 'CUSTODY',
    parentOwner: 'mom', startDateTime: '2026-09-01T15:00:00', sharedWith: [MOM, DAD],
    familyId: FAMILY,
  }, overrides);
}

describe('friend_profiles', () => {
  let env;
  before(async () => { env = await testEnv(PROJECT, CURRENT_RULES); });
  beforeEach(async () => { await env.clearFirestore(); });

  const PATH = `friend_profiles/${FRIEND}`;

  it('lets the friend create their own profile with a two-parent gate', async () => {
    await assertSucceeds(env.authenticatedContext(FRIEND).firestore().doc(PATH).set(profile({})));
  });

  it('lets a friend of two families create a profile both families can read (L-5)', async () => {
    await assertSucceeds(env.authenticatedContext(FRIEND).firestore().doc(PATH)
        .set(profile({familyParents: [MOM, DAD, AUNT, UNCLE]})));
    await assertSucceeds(env.authenticatedContext(UNCLE).firestore().doc(PATH).get());
    await assertSucceeds(env.authenticatedContext(DAD).firestore().doc(PATH).get());
  });

  it('refuses a profile whose familyParents is not even a pair', async () => {
    await assertFails(
        env.authenticatedContext(FRIEND).firestore().doc(PATH).set(profile({familyParents: [MOM]})));
    await assertFails(
        env.authenticatedContext(FRIEND).firestore().doc(PATH).set(profile({familyParents: []})));
  });

  it('refuses one account creating another account\'s profile', async () => {
    await assertFails(env.authenticatedContext(STRANGER).firestore().doc(PATH).set(profile({})));
  });

  it('lets a listed parent and the friend read it, and refuses a stranger', async () => {
    await seed(env, {[PATH]: profile({})});
    await assertSucceeds(env.authenticatedContext(MOM).firestore().doc(PATH).get());
    await assertSucceeds(env.authenticatedContext(FRIEND).firestore().doc(PATH).get());
    await assertFails(env.authenticatedContext(STRANGER).firestore().doc(PATH).get());
  });

  it('lets the friend edit name/photo but not the read gate, and refuses a parent editing', async () => {
    await seed(env, {[PATH]: profile({})});
    const friend = env.authenticatedContext(FRIEND).firestore();
    await assertSucceeds(friend.doc(PATH).update({name: 'Grandma Olya', photoUrl: 'https://x/y.jpg'}));
    await assertFails(friend.doc(PATH).update({familyParents: [MOM, STRANGER]}));
    await assertFails(friend.doc(PATH).update({familyParents: [MOM, DAD, STRANGER, UNCLE]}));
    await assertFails(env.authenticatedContext(MOM).firestore().doc(PATH).update({name: 'Renamed'}));
  });
});

describe('calendar_friends', () => {
  let env;
  before(async () => { env = await testEnv(PROJECT, CURRENT_RULES); });
  beforeEach(async () => { await env.clearFirestore(); });

  const PATH = grantPath(FAMILY);

  // No client writes a grant. `acceptCalendarFriendInvitation` does, on Admin credentials,
  // and it is the only thing that proves the inviter is a paired parent before doing so.
  // These cases used to assert the opposite — that a parent could write one directly — which
  // is what let anybody write one for themselves. See the block comment in `firestore.rules`.
  it('refuses a parent writing a grant directly (the callable is the only writer)', async () => {
    await assertFails(env.authenticatedContext(MOM).firestore().doc(PATH).set(grant({})));
  });

  it('refuses a friend granting themselves access', async () => {
    await assertFails(
        env.authenticatedContext(FRIEND).firestore().doc(PATH).set(grant({grantedBy: FRIEND})));
  });

  it('refuses a grant stamped by a non-parent', async () => {
    await assertFails(env.authenticatedContext(MOM).firestore().doc(PATH).set(grant({grantedBy: DAD})));
  });

  // The breach this rule was closed for: the old condition asked only that the written
  // document name the caller among its own two `familyParents` and credit them as
  // `grantedBy` — both attacker-supplied. So a stranger could write a grant for *themselves*
  // naming their victim as the other "parent", and the third disjunct of the `events` read rule
  // then served the victim's whole calendar.
  it('refuses a stranger self-granting access to a victim they name as a parent', async () => {
    const selfGrant = {
      familyParents: [STRANGER, MOM], familyId: 'uid-mom__uid-stranger', friendUid: STRANGER,
      grantedBy: STRANGER, grantedAtMillis: 1, expiresAtMillis: FAR_FUTURE,
    };
    await assertFails(
        env.authenticatedContext(STRANGER).firestore()
            .doc(grantPath('uid-mom__uid-stranger', STRANGER)).set(selfGrant));
    await assertFails(
        env.authenticatedContext(STRANGER).firestore()
            .doc(grantPath(FAMILY, STRANGER)).set(grant({friendUid: STRANGER})));
  });

  // Even with a grant seeded past the rules, a second account must not be able to rewrite it
  // — repointing somebody else's grant at themselves both revokes the real friend and admits
  // the writer.
  it('refuses overwriting an existing grant', async () => {
    await seed(env, {[PATH]: grant({})});
    await assertFails(
        env.authenticatedContext(STRANGER).firestore().doc(PATH)
            .update({familyParents: [STRANGER, MOM]}));
    await assertFails(
        env.authenticatedContext(FRIEND).firestore().doc(PATH)
            .update({expiresAtMillis: FAR_FUTURE + 1}));
  });

  it('lets the friend and both parents read the grant, refuses a stranger', async () => {
    await seed(env, {[PATH]: grant({})});
    await assertSucceeds(env.authenticatedContext(FRIEND).firestore().doc(PATH).get());
    await assertSucceeds(env.authenticatedContext(DAD).firestore().doc(PATH).get());
    await assertFails(env.authenticatedContext(STRANGER).firestore().doc(PATH).get());
  });

  it('refuses another family\'s parents reading this family\'s grant', async () => {
    await seed(env, {[PATH]: grant({}), [grantPath(SECOND_FAMILY)]: secondGrant({})});
    await assertFails(env.authenticatedContext(UNCLE).firestore().doc(PATH).get());
    await assertFails(env.authenticatedContext(MOM).firestore().doc(grantPath(SECOND_FAMILY)).get());
  });

  it('lets a parent revoke, but not the friend', async () => {
    await seed(env, {[PATH]: grant({})});
    await assertFails(env.authenticatedContext(FRIEND).firestore().doc(PATH).delete());
    await assertSucceeds(env.authenticatedContext(DAD).firestore().doc(PATH).delete());
  });

  it('refuses one family\'s parent revoking another family\'s grant', async () => {
    await seed(env, {[PATH]: grant({}), [grantPath(SECOND_FAMILY)]: secondGrant({})});
    await assertFails(env.authenticatedContext(MOM).firestore().doc(grantPath(SECOND_FAMILY)).delete());
  });

  // ---- The two list queries the app runs (item 12: a query needs a filter the rule keys on) --

  it('serves the friend listing their own grants, across both families', async () => {
    await seed(env, {[PATH]: grant({}), [grantPath(SECOND_FAMILY)]: secondGrant({})});
    const snap = await assertSucceeds(
        env.authenticatedContext(FRIEND).firestore().collection('calendar_friends')
            .where('friendUid', '==', FRIEND).get());
    if (snap.size !== 2) throw new Error(`expected both grants, got ${snap.size}`);
  });

  it('refuses a friend listing somebody else\'s grants', async () => {
    await seed(env, {[grantPath(FAMILY, 'uid-other-friend')]: grant({friendUid: 'uid-other-friend'})});
    await assertFails(
        env.authenticatedContext(FRIEND).firestore().collection('calendar_friends')
            .where('friendUid', '==', 'uid-other-friend').get());
  });

  it('serves a parent listing the grants naming them', async () => {
    await seed(env, {[PATH]: grant({}), [grantPath(SECOND_FAMILY)]: secondGrant({})});
    const snap = await assertSucceeds(
        env.authenticatedContext(DAD).firestore().collection('calendar_friends')
            .where('familyParents', 'array-contains', DAD).get());
    if (snap.size !== 1) throw new Error(`expected Dad's family's grant only, got ${snap.size}`);
  });

  it('refuses an unfiltered listing', async () => {
    await seed(env, {[PATH]: grant({})});
    await assertFails(env.authenticatedContext(FRIEND).firestore().collection('calendar_friends').get());
  });

  it('keeps a per-person grant from before L-5 away from its friend', async () => {
    // No `friendUid`, keyed on the friend's uid alone: the parents can still see and revoke it,
    // and `backfillRecordFamilyIds` re-keys it; the friend reads nothing through it.
    await seed(env, {[`calendar_friends/${FRIEND}`]: legacyGrant()});
    await assertFails(env.authenticatedContext(FRIEND).firestore().doc(`calendar_friends/${FRIEND}`).get());
    await assertSucceeds(env.authenticatedContext(MOM).firestore().doc(`calendar_friends/${FRIEND}`).get());
    await assertSucceeds(env.authenticatedContext(MOM).firestore().doc(`calendar_friends/${FRIEND}`).delete());
  });
});

describe('events read for a calendar friend', () => {
  let env;
  before(async () => { env = await testEnv(PROJECT, CURRENT_RULES); });
  beforeEach(async () => { await env.clearFirestore(); });

  const EVENT = 'events/ev-1';
  const GRANT = grantPath(FAMILY);

  it('lets a live friend read a family event they are not in the audience of', async () => {
    await seed(env, {[EVENT]: event({sharedWith: [MOM, DAD]}), [GRANT]: grant({})});
    await assertSucceeds(env.authenticatedContext(FRIEND).firestore().doc(EVENT).get());
  });

  it('refuses a friend with no grant', async () => {
    await seed(env, {[EVENT]: event({})});
    await assertFails(env.authenticatedContext(FRIEND).firestore().doc(EVENT).get());
  });

  it('refuses a friend whose grant has expired', async () => {
    await seed(env, {[EVENT]: event({}), [GRANT]: grant({expiresAtMillis: PAST})});
    await assertFails(env.authenticatedContext(FRIEND).firestore().doc(EVENT).get());
  });

  it('refuses a friend an event created by someone outside their family', async () => {
    await seed(env, {[EVENT]: event({createdByFirebaseUid: STRANGER}), [GRANT]: grant({})});
    await assertFails(env.authenticatedContext(FRIEND).firestore().doc(EVENT).get());
  });

  it('refuses a friend writing an event (read-only)', async () => {
    await seed(env, {[EVENT]: event({}), [GRANT]: grant({})});
    await assertFails(
        env.authenticatedContext(FRIEND).firestore().doc(EVENT).update({title: 'hijacked'}));
  });

  // ---- M-6: the grant is scoped to one family, not to a person ---------------------------
  //
  // Mom co-parents with Dad *and* with OTHER_PARENT. The grandmother was admitted by Mom and Dad.
  // Before M-6 the rule asked only "did this event's creator appear among my two parents", and
  // Mom is one of them — so the grandmother read Mom's events in the other household too.

  it('refuses a friend an event from the inviter\'s other family', async () => {
    await seed(env, {
      [EVENT]: event({familyId: OTHER_FAMILY, sharedWith: [MOM, OTHER_PARENT]}),
      [GRANT]: grant({}),
    });
    await assertFails(env.authenticatedContext(FRIEND).firestore().doc(EVENT).get());
  });

  it('refuses a friend an event that has no familyId yet', async () => {
    // Everything written before M-2, until `backfillRecordFamilyIds` has run. Deliberately a
    // denial rather than a fallback: a fallback to "a co-parent of the author" is what re-opened
    // this same leak in `expenses`, because Firestore validates a query by its structure.
    await seed(env, {[EVENT]: event({familyId: ''}), [GRANT]: grant({})});
    await assertFails(env.authenticatedContext(FRIEND).firestore().doc(EVENT).get());
  });

  it('refuses an outsider who stamps this family\'s id onto their own event', async () => {
    // Why `ownerUid in familyParents` stays alongside the familyId check: an event's `familyId`
    // is client-written and the create rule does not pin it, so without the second check any
    // account could inject an event into a family's friend view.
    await seed(env, {
      [EVENT]: event({createdByFirebaseUid: STRANGER, familyId: FAMILY}),
      [GRANT]: grant({}),
    });
    await assertFails(env.authenticatedContext(FRIEND).firestore().doc(EVENT).get());
  });

  it('rejects the by-creator query the friend client used to be told to run', async () => {
    // The shape CLAUDE.md item 16 documented. It is not merely wider than it should be — with
    // the rule keyed on the record's own family it is now structurally undecidable, so Firestore
    // refuses it outright rather than serving a subset. Same lesson as the expenses leak: the
    // client query and the rule have to be keyed on the same field.
    await seed(env, {[EVENT]: event({}), [GRANT]: grant({})});
    await assertFails(
        env.authenticatedContext(FRIEND).firestore().collection('events')
            .where('createdByFirebaseUid', 'in', [MOM, DAD]).get());
  });

  it('serves the family-scoped query a friend client must run instead', async () => {
    await seed(env, {[EVENT]: event({}), [GRANT]: grant({})});
    await assertSucceeds(
        env.authenticatedContext(FRIEND).firestore().collection('events')
            .where('familyId', '==', FAMILY)
            .where('createdByFirebaseUid', 'in', [MOM, DAD]).get());
  });

  // ---- L-5: one grant per family, so a friend of two families reads both ----------------

  describe('a friend of two families (L-5)', () => {
    const SECOND_EVENT = 'events/ev-2';
    const THIRD_EVENT = 'events/ev-3';

    beforeEach(async () => {
      await seed(env, {
        [EVENT]: event({}),
        [SECOND_EVENT]: event({
          createdByFirebaseUid: AUNT, sharedWith: [AUNT, UNCLE], familyId: SECOND_FAMILY,
        }),
        [THIRD_EVENT]: event({
          createdByFirebaseUid: UNCLE, sharedWith: [STRANGER, UNCLE], familyId: THIRD_FAMILY,
        }),
        [GRANT]: grant({}),
        [grantPath(SECOND_FAMILY)]: secondGrant({}),
      });
    });

    it('reads both families\' events, each through its own grant', async () => {
      const friend = env.authenticatedContext(FRIEND).firestore();
      await assertSucceeds(friend.doc(EVENT).get());
      await assertSucceeds(friend.doc(SECOND_EVENT).get());
      await assertSucceeds(friend.collection('events')
          .where('familyId', '==', FAMILY)
          .where('createdByFirebaseUid', 'in', [MOM, DAD]).get());
      await assertSucceeds(friend.collection('events')
          .where('familyId', '==', SECOND_FAMILY)
          .where('createdByFirebaseUid', 'in', [AUNT, UNCLE]).get());
    });

    it('reads no third family, even one that shares a parent with a granting family', async () => {
      // The uncle is a parent in the second family and in a third one that never admitted her —
      // the M-6 leak again, one family further out.
      const friend = env.authenticatedContext(FRIEND).firestore();
      await assertFails(friend.doc(THIRD_EVENT).get());
      await assertFails(friend.collection('events')
          .where('familyId', '==', THIRD_FAMILY)
          .where('createdByFirebaseUid', 'in', [STRANGER, UNCLE]).get());
    });

    it('keeps the other family when one family revokes', async () => {
      await assertSucceeds(
          env.authenticatedContext(DAD).firestore().doc(GRANT).delete());
      const friend = env.authenticatedContext(FRIEND).firestore();
      await assertFails(friend.doc(EVENT).get());
      await assertSucceeds(friend.doc(SECOND_EVENT).get());
    });

    it('ends each family on its own expiry', async () => {
      await seed(env, {[grantPath(SECOND_FAMILY)]: secondGrant({expiresAtMillis: PAST})});
      const friend = env.authenticatedContext(FRIEND).firestore();
      await assertSucceeds(friend.doc(EVENT).get());
      await assertFails(friend.doc(SECOND_EVENT).get());
    });

    it('refuses a grant filed under one family that names another', async () => {
      // The id is not trusted alone: a grant at `{FAMILY}__friend` that names SECOND_FAMILY in
      // its own fields opens neither family's events through the wrong path.
      await seed(env, {[GRANT]: secondGrant({})});
      await assertFails(env.authenticatedContext(FRIEND).firestore().doc(EVENT).get());
    });

    it('refuses a grant whose friendUid is somebody else', async () => {
      await seed(env, {[GRANT]: grant({friendUid: 'uid-other-friend'})});
      await assertFails(env.authenticatedContext(FRIEND).firestore().doc(EVENT).get());
    });
  });

  it('admits nothing through a per-person grant from before L-5', async () => {
    // `calendar_friends/{friendUid}` names the right family, but the rule reads only the
    // per-family id: the backfill re-keys these rather than the rule keeping a second shape.
    await seed(env, {[EVENT]: event({}), [`calendar_friends/${FRIEND}`]: legacyGrant()});
    await assertFails(env.authenticatedContext(FRIEND).firestore().doc(EVENT).get());
  });
});
