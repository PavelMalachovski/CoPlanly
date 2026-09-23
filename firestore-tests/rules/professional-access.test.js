/**
 * Professional access (MON-18): a mediator, lawyer, guardian ad litem or therapist reading one
 * family's calendar and parenting plan, read-only, for a bounded time, with **both** parents'
 * consent.
 *
 * Four collections work together:
 *   - `invitations` — a parent mints a `professional` code naming the family and the end date;
 *   - `professional_grants/{familyId}__{proUid}` — written by `acceptProfessionalInvitation`
 *     alone, carrying `consents: {parentUid: epochMillis}`; a parent may add only their own key,
 *     and either parent may delete the grant;
 *   - `events`, `parenting_plans`, `custody_models` — each admits a professional whose grant is
 *     **active**: both parents consented, not expired, and naming this family;
 *   - everything else (chat, expenses, child records, money, profiles) admits no professional at
 *     all, whatever the grant says.
 */

const {
  CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-professional';
const MOM = 'uid-mom';
const DAD = 'uid-dad';
const PRO = 'uid-pro';
const STRANGER = 'uid-stranger';

// Mom's second co-parent (M-4). Her other household must stay invisible to a mediator admitted
// by Mom and Dad — the same leak M-6 closed for calendar friends.
const OTHER_PARENT = 'uid-other-parent';

// `FamilyKey.of` — the two uids sorted and joined.
const FAMILY = 'uid-dad__uid-mom';
const OTHER_FAMILY = 'uid-mom__uid-other-parent';
const GRANT_ID = `${FAMILY}__${PRO}`;
const GRANT = `professional_grants/${GRANT_ID}`;

const FAR_FUTURE = 4102444800000; // 2100-01-01
const PAST = 1000; // 1970
const DAY_MS = 24 * 60 * 60 * 1000;

function grant(overrides) {
  return Object.assign({
    familyId: FAMILY, familyParents: [DAD, MOM], proUid: PRO, role: 'mediator',
    name: 'Mgr. Mediator', invitedBy: MOM, grantedAtMillis: 1, expiresAtMillis: FAR_FUTURE,
    consents: {[MOM]: 1, [DAD]: 2},
  }, overrides);
}

function event(overrides) {
  return Object.assign({
    id: 'ev-1', createdByFirebaseUid: MOM, title: 'Handover', eventType: 'CUSTODY',
    parentOwner: 'mom', startDateTime: '2026-10-01T15:00:00', sharedWith: [MOM, DAD],
    familyId: FAMILY,
  }, overrides);
}

function custody(overrides) {
  return Object.assign({
    participants: [DAD, MOM], modelType: 'ALTERNATING_WEEKS', patternDays: 14,
    momDayIndices: [0, 1, 2, 3, 4, 5, 6], startDate: '2026-01-05',
    lastModifiedBy: MOM, lastModifiedAt: '2026-01-05T10:00:00Z', createdAt: '2026-01-05T10:00:00',
  }, overrides);
}

function plan() {
  return {
    answers: {[MOM]: {residence_home: 'With both, alternate weeks'}},
    agreedTo: {}, catalogueVersions: {[MOM]: 1}, updatedAt: {[MOM]: 1},
  };
}

function as(env, uid) {
  return env.authenticatedContext(uid).firestore();
}

describe('professional invitations', () => {
  let env;
  before(async () => { env = await testEnv(PROJECT, CURRENT_RULES); });
  beforeEach(async () => { await env.clearFirestore(); });

  function invite(overrides) {
    return Object.assign({
      id: 'inv-pro', code: 'P7M2QX', fromUserId: MOM, fromUserName: 'Mom', fromUserEmail: '',
      toEmail: '', status: 'pending', createdAt: Date.now(), expiresAt: Date.now() + DAY_MS,
      acceptedBy: null, kind: 'professional', professionalRole: 'mediator',
      professionalExpiresAt: Date.now() + 30 * DAY_MS, familyId: FAMILY,
    }, overrides);
  }

  it('lets a parent mint a professional invitation for their own family', async () => {
    await assertSucceeds(as(env, MOM).doc('invitations/inv-pro').set(invite({})));
  });

  it('refuses one for a family the inviter is not in', async () => {
    await assertFails(as(env, STRANGER).doc('invitations/inv-pro')
        .set(invite({fromUserId: STRANGER})));
  });

  it('refuses one with no family at all', async () => {
    await assertFails(as(env, MOM).doc('invitations/inv-pro').set(invite({familyId: ''})));
  });

  it('refuses an end date beyond the 180-day ceiling', async () => {
    await assertFails(as(env, MOM).doc('invitations/inv-pro')
        .set(invite({professionalExpiresAt: Date.now() + 200 * DAY_MS})));
  });

  it('refuses an end date already in the past', async () => {
    await assertFails(as(env, MOM).doc('invitations/inv-pro')
        .set(invite({professionalExpiresAt: PAST})));
  });

  it('refuses a role outside the four professions and "other"', async () => {
    await assertFails(as(env, MOM).doc('invitations/inv-pro')
        .set(invite({professionalRole: 'co_parent'})));
  });
});

describe('professional_grants', () => {
  let env;
  before(async () => { env = await testEnv(PROJECT, CURRENT_RULES); });
  beforeEach(async () => { await env.clearFirestore(); });

  it('refuses every client create — the callable is the only writer', async () => {
    await assertFails(as(env, MOM).doc(GRANT).set(grant({})));
    await assertFails(as(env, PRO).doc(GRANT).set(grant({})));
  });

  it('lets the professional and both parents read it, and refuses a stranger', async () => {
    await seed(env, {[GRANT]: grant({})});
    await assertSucceeds(as(env, PRO).doc(GRANT).get());
    await assertSucceeds(as(env, MOM).doc(GRANT).get());
    await assertSucceeds(as(env, DAD).doc(GRANT).get());
    await assertFails(as(env, STRANGER).doc(GRANT).get());
  });

  it('serves the professional\'s own list query and the parents\' family query', async () => {
    await seed(env, {[GRANT]: grant({})});
    await assertSucceeds(as(env, PRO).collection('professional_grants')
        .where('proUid', '==', PRO).get());
    await assertSucceeds(as(env, DAD).collection('professional_grants')
        .where('familyParents', 'array-contains', DAD).get());
    await assertFails(as(env, STRANGER).collection('professional_grants').get());
  });

  it('lets the other parent add their own consent', async () => {
    await seed(env, {[GRANT]: grant({consents: {[MOM]: 1}})});
    await assertSucceeds(as(env, DAD).doc(GRANT).update({[`consents.${DAD}`]: Date.now()}));
  });

  it('refuses a parent consenting on the co-parent\'s behalf', async () => {
    await seed(env, {[GRANT]: grant({consents: {}})});
    await assertFails(as(env, MOM).doc(GRANT).update({[`consents.${DAD}`]: Date.now()}));
  });

  it('refuses a parent removing the co-parent\'s consent', async () => {
    await seed(env, {[GRANT]: grant({})});
    await assertFails(as(env, MOM).doc(GRANT).update({consents: {[MOM]: 1}}));
  });

  it('refuses a consent that is not a positive number', async () => {
    await seed(env, {[GRANT]: grant({consents: {[MOM]: 1}})});
    await assertFails(as(env, DAD).doc(GRANT).update({[`consents.${DAD}`]: 'yes'}));
  });

  it('refuses a consent write that also moves the expiry', async () => {
    await seed(env, {[GRANT]: grant({consents: {[MOM]: 1}, expiresAtMillis: Date.now() + DAY_MS})});
    await assertFails(as(env, DAD).doc(GRANT).update({
      [`consents.${DAD}`]: Date.now(), expiresAtMillis: FAR_FUTURE,
    }));
  });

  it('refuses consenting to a grant that has already expired', async () => {
    await seed(env, {[GRANT]: grant({consents: {[MOM]: 1}, expiresAtMillis: PAST})});
    await assertFails(as(env, DAD).doc(GRANT).update({[`consents.${DAD}`]: Date.now()}));
  });

  it('refuses the professional editing consents, at all', async () => {
    await seed(env, {[GRANT]: grant({consents: {[MOM]: 1}})});
    await assertFails(as(env, PRO).doc(GRANT).update({[`consents.${DAD}`]: Date.now()}));
    await assertFails(as(env, PRO).doc(GRANT).update({[`consents.${PRO}`]: Date.now()}));
  });

  it('refuses a stranger consenting', async () => {
    await seed(env, {[GRANT]: grant({consents: {[MOM]: 1}})});
    await assertFails(as(env, STRANGER).doc(GRANT).update({[`consents.${STRANGER}`]: 1}));
  });

  it('lets either parent revoke alone, and refuses the professional and a stranger', async () => {
    await seed(env, {[GRANT]: grant({})});
    await assertFails(as(env, PRO).doc(GRANT).delete());
    await assertFails(as(env, STRANGER).doc(GRANT).delete());
    await assertSucceeds(as(env, DAD).doc(GRANT).delete());
  });
});

describe('what an active professional grant opens', () => {
  let env;
  before(async () => { env = await testEnv(PROJECT, CURRENT_RULES); });
  beforeEach(async () => { await env.clearFirestore(); });

  const EVENT = 'events/ev-1';
  const PLAN = `parenting_plans/${FAMILY}`;
  const CUSTODY = `custody_models/${FAMILY}`;

  it('reads a family event, the plan and the custody schedule', async () => {
    await seed(env, {[GRANT]: grant({}), [EVENT]: event({}), [PLAN]: plan(), [CUSTODY]: custody({})});
    await assertSucceeds(as(env, PRO).doc(EVENT).get());
    await assertSucceeds(as(env, PRO).doc(PLAN).get());
    await assertSucceeds(as(env, PRO).doc(CUSTODY).get());
  });

  it('reads the plan and the schedule before either document exists', async () => {
    // Both screens subscribe before the parents have written anything; a denial there would
    // fail the listener for good, the startup race `custody_models` already lost once.
    await seed(env, {[GRANT]: grant({})});
    await assertSucceeds(as(env, PRO).doc(PLAN).get());
    await assertSucceeds(as(env, PRO).doc(CUSTODY).get());
  });

  it('serves the family-scoped events query the professional client runs', async () => {
    await seed(env, {[GRANT]: grant({}), [EVENT]: event({})});
    await assertSucceeds(as(env, PRO).collection('events')
        .where('familyId', '==', FAMILY)
        .where('createdByFirebaseUid', 'in', [DAD, MOM]).get());
  });

  it('refuses everything while only one parent has consented', async () => {
    await seed(env, {
      [GRANT]: grant({consents: {[MOM]: 1}}),
      [EVENT]: event({}), [PLAN]: plan(), [CUSTODY]: custody({}),
    });
    await assertFails(as(env, PRO).doc(EVENT).get());
    await assertFails(as(env, PRO).doc(PLAN).get());
    await assertFails(as(env, PRO).doc(CUSTODY).get());
  });

  it('refuses a consent map that names the professional instead of a parent', async () => {
    await seed(env, {[GRANT]: grant({consents: {[MOM]: 1, [PRO]: 1}}), [EVENT]: event({})});
    await assertFails(as(env, PRO).doc(EVENT).get());
  });

  it('refuses everything once the grant has expired', async () => {
    await seed(env, {
      [GRANT]: grant({expiresAtMillis: PAST}),
      [EVENT]: event({}), [PLAN]: plan(), [CUSTODY]: custody({}),
    });
    await assertFails(as(env, PRO).doc(EVENT).get());
    await assertFails(as(env, PRO).doc(PLAN).get());
    await assertFails(as(env, PRO).doc(CUSTODY).get());
  });

  it('refuses a grant with no expiry at all', async () => {
    const noExpiry = grant({});
    delete noExpiry.expiresAtMillis;
    await seed(env, {[GRANT]: noExpiry, [EVENT]: event({})});
    await assertFails(as(env, PRO).doc(EVENT).get());
  });

  it('refuses everything once a parent has revoked', async () => {
    await seed(env, {[GRANT]: grant({}), [EVENT]: event({})});
    await assertSucceeds(as(env, MOM).doc(GRANT).delete());
    await assertFails(as(env, PRO).doc(EVENT).get());
  });

  it('refuses the inviter\'s other family — events, plan and schedule', async () => {
    const OTHER_EVENT = 'events/ev-other';
    await seed(env, {
      [GRANT]: grant({}),
      [OTHER_EVENT]: event({id: 'ev-other', familyId: OTHER_FAMILY, sharedWith: [MOM, OTHER_PARENT]}),
      [`parenting_plans/${OTHER_FAMILY}`]: plan(),
      [`custody_models/${OTHER_FAMILY}`]: custody({participants: [MOM, OTHER_PARENT]}),
    });
    await assertFails(as(env, PRO).doc(OTHER_EVENT).get());
    await assertFails(as(env, PRO).doc(`parenting_plans/${OTHER_FAMILY}`).get());
    await assertFails(as(env, PRO).doc(`custody_models/${OTHER_FAMILY}`).get());
  });

  it('refuses an outsider\'s event stamped with this family\'s id', async () => {
    await seed(env, {
      [GRANT]: grant({}),
      [EVENT]: event({createdByFirebaseUid: STRANGER, sharedWith: [STRANGER]}),
    });
    await assertFails(as(env, PRO).doc(EVENT).get());
  });

  it('refuses a grant stored under another professional\'s id', async () => {
    // The document id names the professional, and the rule reads the caller's own id: a grant
    // for somebody else, even for this family, opens nothing to this caller.
    await seed(env, {
      [`professional_grants/${FAMILY}__${STRANGER}`]: grant({proUid: STRANGER}),
      [EVENT]: event({}),
    });
    await assertFails(as(env, PRO).doc(EVENT).get());
  });

  it('refuses a grant whose stored professional is not the caller', async () => {
    await seed(env, {[GRANT]: grant({proUid: STRANGER}), [EVENT]: event({})});
    await assertFails(as(env, PRO).doc(EVENT).get());
  });

  it('never opens chat, money, child records, profiles or the family', async () => {
    await seed(env, {
      [GRANT]: grant({}),
      [`conversations/${FAMILY}`]: {participants: [DAD, MOM]},
      'messages/m-1': {conversationId: FAMILY, senderId: MOM, text: 'hello'},
      'expenses/x-1': {createdByFirebaseUid: MOM, familyId: FAMILY, amount: 10},
      'budgets/b-1': {createdByFirebaseUid: MOM, familyId: FAMILY, amount: 10},
      'child_info/c-1': {createdByFirebaseUid: MOM, familyId: FAMILY, sharedWith: [MOM, DAD]},
      'pets/p-1': {createdByFirebaseUid: MOM, familyId: FAMILY, sharedWith: [MOM, DAD]},
      [`family_settings/${FAMILY}`]: {participants: [DAD, MOM], momShareBasisPoints: 5000},
      [`families/${FAMILY}`]: {members: [DAD, MOM]},
      [`users/${MOM}`]: {name: 'Mom', email: 'm@x.test', partnerId: DAD, partnerIds: [DAD]},
    });
    const pro = as(env, PRO);
    await assertFails(pro.doc(`conversations/${FAMILY}`).get());
    await assertFails(pro.doc('messages/m-1').get());
    await assertFails(pro.doc('expenses/x-1').get());
    await assertFails(pro.doc('budgets/b-1').get());
    await assertFails(pro.doc('child_info/c-1').get());
    await assertFails(pro.doc('pets/p-1').get());
    await assertFails(pro.doc(`family_settings/${FAMILY}`).get());
    await assertFails(pro.doc(`families/${FAMILY}`).get());
    await assertFails(pro.doc(`users/${MOM}`).get());
  });

  it('writes nothing: not an event, not the plan, not the schedule', async () => {
    await seed(env, {[GRANT]: grant({}), [EVENT]: event({}), [PLAN]: plan(), [CUSTODY]: custody({})});
    const pro = as(env, PRO);
    await assertFails(pro.doc(EVENT).update({title: 'rewritten'}));
    await assertFails(pro.doc(PLAN).set({
      answers: {[PRO]: {residence_home: 'x'}}, agreedTo: {}, catalogueVersions: {}, updatedAt: {},
    }, {merge: true}));
    await assertFails(pro.doc(CUSTODY).update({patternDays: 7}));
    await assertFails(pro.doc('events/ev-new').set(event({
      id: 'ev-new', createdByFirebaseUid: PRO, sharedWith: [PRO], familyId: FAMILY,
    })));
  });

  it('leaves the parents\' own reads untouched', async () => {
    await seed(env, {[EVENT]: event({}), [PLAN]: plan()});
    await assertSucceeds(as(env, DAD).doc(EVENT).get());
    await assertSucceeds(as(env, DAD).doc(PLAN).get());
  });
});
