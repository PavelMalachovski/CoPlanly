/**
 * Nobody outside a family reaches it — even somebody one of its parents co-parents with elsewhere.
 *
 * The setting throughout: **Zuzana co-parents in two families**, family A with Igor and family B
 * with Carol. A family id is `FamilyKey.of` — the two uids sorted and joined with `__`. Four
 * people are outsiders of family A, and each holds a real door into family B:
 *
 * - **Carol**, Zuzana's live co-parent in B. `isPartnerOf(zuzana)` and Zuzana's `myAudience()`
 *   both admit her, which is exactly why a rule that asks "is the caller the author's co-parent"
 *   instead of "is the caller in this record's family" lets her into A.
 * - **a calendar friend of B** (`calendar_friends/{B}__{friend}`), who reads B's calendar;
 * - **a professional of B** (`professional_grants/{B}__{pro}`, both parents consented), who reads
 *   B's calendar, plan and custody schedule;
 * - **a guest of a B child** (in that child's `sharedWith` and `guests`).
 *
 * Each block first proves the outsider's door into B is open — so a refusal below means "not in
 * A", not "the fixture is broken" — then pins what the October 2026 isolation audit found:
 * writes that were allowed and must not be (the outsider editing, re-stamping or widening an A
 * record once in its audience, a record or an audience moving between families, a friend
 * invitation that names no family), and reads and writes that were already refused but had never
 * been tried with a parent in two families. The cases that must keep working stand beside them.
 */

const {serverTimestamp} = require('firebase/firestore');
const {
  CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-cross-family-outsiders';
const ZUZANA = 'zuzana-uid';
const IGOR = 'igor-uid';
const CAROL = 'carol-uid';
const FRIEND_B = 'friend-b-uid';
const PRO_B = 'pro-b-uid';
const GUEST_B = 'guest-b-uid';
const FRIEND_A = 'friend-a-uid';
const PRO_A = 'pro-a-uid';
const DAVE = 'dave-uid';

/**
 * The family id for a pair, built the way `FamilyKey.of` builds it.
 *
 * @param {string} uidA One parent.
 * @param {string} uidB The other parent.
 * @return {string} The family id.
 */
function familyId(uidA, uidB) {
  return [uidA, uidB].sort().join('__');
}

const A = familyId(ZUZANA, IGOR);
const B = familyId(ZUZANA, CAROL);

/** Epoch millis well in the future: a grant that is still good. */
const FUTURE = Date.parse('2099-01-01T00:00:00Z');

/**
 * A guests map holding one grant, as `ChildInfoGuests.encode` writes it.
 *
 * @param {string} uid The guest.
 * @param {number} expiresAtMillis When the grant ends.
 * @param {string=} grantedBy The parent who granted it.
 * @return {!Object} The map to store under `guests`.
 */
function guestOf(uid, expiresAtMillis, grantedBy) {
  return {[uid]: {
    name: 'Guest',
    grantedBy: grantedBy || ZUZANA,
    grantedAtMillis: Date.parse('2026-09-01T09:30:00Z'),
    expiresAtMillis,
  }};
}

/**
 * A child record as `SyncService.syncChildInfo` writes it, Zuzana's, in family A by default.
 *
 * @param {!Object} overrides Fields to override.
 * @return {!Object} The document.
 */
function childDoc(overrides) {
  return Object.assign({
    id: 'kid-a',
    childName: 'Ema',
    dateOfBirth: '2018-03-04T00:00:00',
    medications: [],
    activities: [],
    allergies: ['Peanuts'],
    medicalNotes: 'Mild asthma',
    emergencyContacts: [],
    schoolInfo: null,
    medicalProfile: {bloodType: 'A_POSITIVE'},
    medicalPhotos: [],
    guests: {},
    createdAt: '2026-08-01T10:00:00',
    updatedAt: '2026-08-01T10:00:00',
    createdByFirebaseUid: ZUZANA,
    lastModifiedBy: ZUZANA,
    familyId: A,
    sharedWith: [ZUZANA, IGOR],
  }, overrides);
}

/**
 * A pet record as `PetRepositoryImpl.toFirestoreMap` writes it, Zuzana's, in family A by default.
 *
 * @param {!Object} overrides Fields to override.
 * @return {!Object} The document.
 */
function petDoc(overrides) {
  return Object.assign({
    id: 'pet-a',
    name: 'Rex',
    species: 'DOG',
    medications: [],
    vaccinations: [],
    photos: [],
    createdAt: '2026-08-01T10:00:00',
    updatedAt: '2026-08-01T10:00:00',
    createdByFirebaseUid: ZUZANA,
    lastModifiedBy: ZUZANA,
    familyId: A,
    sharedWith: [ZUZANA, IGOR],
  }, overrides);
}

/**
 * An event as `EventDocument.fromEvent` writes it, Zuzana's, in family A by default.
 *
 * @param {!Object} overrides Fields to override.
 * @return {!Object} The document.
 */
function eventDoc(overrides) {
  return Object.assign({
    id: 'ev-a',
    title: 'Dentist',
    description: 'Ema, check-up',
    startDateTime: '2026-11-01T10:00:00',
    endDateTime: '2026-11-01T11:00:00',
    eventType: 'medical',
    parentOwner: 'mom',
    createdAt: '2026-10-01T10:00:00',
    updatedAt: '2026-10-01T10:00:00',
    createdByFirebaseUid: ZUZANA,
    lastModifiedBy: ZUZANA,
    familyId: A,
    sharedWith: [ZUZANA, IGOR],
    permissions: 'read_write',
  }, overrides);
}

/**
 * A revision as `EventVersionRecorder` uploads it, of `ev-a` by default.
 *
 * @param {string} editor The uid writing it.
 * @param {!Object} overrides Fields to override.
 * @return {!Object} The document.
 */
function versionDoc(editor, overrides) {
  return Object.assign({
    eventId: 'ev-a',
    kind: 'updated',
    editorUid: editor,
    deviceTimeMillis: 1790000000000,
    recordedAt: serverTimestamp(),
    sharedWith: [ZUZANA, IGOR],
    familyId: A,
    event: eventDoc({lastModifiedBy: editor}),
    formatVersion: 1,
  }, overrides);
}

/**
 * A friend invitation as `FriendRepositoryImpl.createFriendInvite` writes it, Zuzana's.
 *
 * @param {!Object} overrides Fields to override; a key set to `undefined` is removed.
 * @return {!Object} The document.
 */
function friendInvite(overrides) {
  const doc = Object.assign({
    code: 'FRND42',
    fromUserId: ZUZANA,
    fromUserName: 'Zuzana',
    fromUserEmail: '',
    toEmail: '',
    status: 'pending',
    createdAt: Date.now(),
    expiresAt: Date.now() + 3600000,
    acceptedBy: null,
    kind: 'friend',
    friendExpiresAt: FUTURE,
    familyId: B,
  }, overrides);
  Object.keys(doc).forEach((key) => doc[key] === undefined && delete doc[key]);
  return doc;
}

/**
 * A change request as `ChangeRequestRepositoryImpl.toFirestoreMap` writes it.
 *
 * @param {!Object} overrides Fields to override.
 * @return {!Object} The document.
 */
function changeRequestDoc(overrides) {
  return Object.assign({
    id: 'cr-1',
    eventId: 'ev-b',
    eventTitle: 'Swimming',
    requestedBy: ZUZANA,
    requestedTo: CAROL,
    currentStartDateTime: '2026-11-02T16:00:00',
    currentEndDateTime: '2026-11-02T17:00:00',
    proposedStartDateTime: '2026-11-03T16:00:00',
    proposedEndDateTime: '2026-11-03T17:00:00',
    note: '',
    status: 'PENDING',
    createdAt: '2026-10-20T09:00:00',
    respondedAt: '',
    familyId: B,
  }, overrides);
}

describe('cross-family outsiders: nobody outside family A reaches it', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    const dayAhead = Date.now() + 86400000;
    await seed(env, {
      // The server keeps the singular `partnerId` on the *first* co-parent; `partnerIds` names both.
      [`users/${ZUZANA}`]: {name: 'Zuzana', partnerIds: [IGOR, CAROL], partnerId: IGOR},
      [`users/${IGOR}`]: {name: 'Igor', partnerIds: [ZUZANA], partnerId: ZUZANA},
      [`users/${CAROL}`]: {name: 'Carol', partnerIds: [ZUZANA], partnerId: ZUZANA},
      [`users/${DAVE}`]: {name: 'Dave', partnerId: ''},
      [`users/${FRIEND_B}`]: {name: 'Grandma', partnerId: ''},
      [`users/${PRO_B}`]: {name: 'Mediator', partnerId: ''},
      [`users/${GUEST_B}`]: {name: 'Sitter', partnerId: ''},
      [`families/${A}`]: {members: [IGOR, ZUZANA].sort()},
      [`families/${B}`]: {members: [CAROL, ZUZANA].sort()},
      // The outsiders' doors into B…
      [`calendar_friends/${B}__${FRIEND_B}`]: {familyId: B, friendUid: FRIEND_B,
        familyParents: [CAROL, ZUZANA].sort(), expiresAtMillis: FUTURE},
      [`professional_grants/${B}__${PRO_B}`]: {familyId: B, proUid: PRO_B,
        familyParents: [CAROL, ZUZANA].sort(), consents: {[ZUZANA]: 1, [CAROL]: 1},
        expiresAtMillis: dayAhead},
      'child_info/kid-b': childDoc({id: 'kid-b', childName: 'Leo', familyId: B,
        sharedWith: [ZUZANA, CAROL, GUEST_B], guests: guestOf(GUEST_B, FUTURE)}),
      'events/ev-b': eventDoc({id: 'ev-b', title: 'Swimming', familyId: B,
        sharedWith: [ZUZANA, CAROL]}),
      // …and A's own, which none of them may list.
      [`calendar_friends/${A}__${FRIEND_A}`]: {familyId: A, friendUid: FRIEND_A,
        familyParents: [IGOR, ZUZANA].sort(), expiresAtMillis: FUTURE},
      [`professional_grants/${A}__${PRO_A}`]: {familyId: A, proUid: PRO_A,
        familyParents: [IGOR, ZUZANA].sort(), consents: {[ZUZANA]: 1, [IGOR]: 1},
        expiresAtMillis: dayAhead},
      // Family A's records.
      'child_info/kid-a': childDoc({}),
      'pets/pet-a': petDoc({}),
      'events/ev-a': eventDoc({}),
      'expenses/x-a': {id: 'x-a', createdByFirebaseUid: ZUZANA, familyId: A, amount: 120,
        currency: 'CZK', description: 'Dentist'},
      [`custody_models/${A}`]: {participants: [IGOR, ZUZANA].sort(), lastModifiedBy: ZUZANA},
      [`family_settings/${A}`]: {participants: [IGOR, ZUZANA].sort(), lastModifiedBy: ZUZANA,
        momShareBasisPoints: 5000},
      [`parenting_plans/${A}`]: {answers: {[ZUZANA]: {care_weekday: 'Alternate weeks'}}},
      [`parenting_plans/${B}`]: {answers: {[ZUZANA]: {care_weekday: 'Weekends'}}},
      [`conversations/${A}`]: {participants: [IGOR, ZUZANA].sort()},
      'messages/m-a': {conversationId: A, senderId: ZUZANA, content: 'Dentist on Monday'},
      [`request_deadlines/event_${ZUZANA}_ev-a`]: {kind: 'event', ref: 'ev-a', familyId: A,
        proposerUid: ZUZANA, responderUid: IGOR, participants: [ZUZANA, IGOR],
        createdAt: new Date(), answerByMillis: dayAhead},
    });
  });

  const as = (uid) => env.authenticatedContext(uid).firestore();

  describe('the outsiders\' doors into family B are open (so the refusals below mean something)', () => {
    it('the friend of B reads a B event', async () => {
      await assertSucceeds(as(FRIEND_B).doc('events/ev-b').get());
    });

    it('the professional of B reads a B event, B\'s plan and B\'s custody schedule', async () => {
      await assertSucceeds(as(PRO_B).doc('events/ev-b').get());
      await assertSucceeds(as(PRO_B).doc(`parenting_plans/${B}`).get());
      await assertSucceeds(as(PRO_B).doc(`custody_models/${B}`).get());
    });

    it('the guest of a B child reads that child', async () => {
      await assertSucceeds(as(GUEST_B).doc('child_info/kid-b').get());
    });

    it('Carol reads Zuzana\'s profile — a person document, by design', async () => {
      await assertSucceeds(as(CAROL).doc(`users/${ZUZANA}`).get());
    });
  });

  describe('none of them reads family A', () => {
    for (const [who, uid] of [['Carol', CAROL], ['the friend of B', FRIEND_B],
      ['the professional of B', PRO_B], ['the guest of a B child', GUEST_B]]) {
      it(`refuses ${who} family A's child, pet, event, expense, schedule, plan and thread`,
          async () => {
            await assertFails(as(uid).doc('child_info/kid-a').get());
            await assertFails(as(uid).doc('pets/pet-a').get());
            await assertFails(as(uid).doc('events/ev-a').get());
            await assertFails(as(uid).doc('expenses/x-a').get());
            await assertFails(as(uid).doc(`custody_models/${A}`).get());
            await assertFails(as(uid).doc(`family_settings/${A}`).get());
            await assertFails(as(uid).doc(`parenting_plans/${A}`).get());
            await assertFails(as(uid).doc(`conversations/${A}`).get());
            await assertFails(as(uid).doc('messages/m-a').get());
            await assertFails(as(uid).doc(`users/${IGOR}`).get());
          });
    }

    it('refuses Carol\'s list queries aimed at family A', async () => {
      await assertFails(as(CAROL).collection('events').where('familyId', '==', A).get());
      await assertFails(as(CAROL).collection('expenses').where('familyId', '==', A).get());
      await assertFails(as(CAROL).collection('request_deadlines').where('familyId', '==', A).get());
      await assertFails(as(CAROL).collection('calendar_friends').where('familyId', '==', A).get());
      await assertFails(as(CAROL).collection('professional_grants').where('familyId', '==', A).get());
    });

    it('lists Carol only B\'s friend and professional grants, never A\'s', async () => {
      const friends = await assertSucceeds(as(CAROL).collection('calendar_friends')
          .where('familyParents', 'array-contains', CAROL).get());
      if (friends.docs.some((d) => d.data().familyId === A) || friends.size !== 1) {
        throw new Error(`Carol listed ${friends.docs.map((d) => d.id)}`);
      }
      const pros = await assertSucceeds(as(CAROL).collection('professional_grants')
          .where('familyParents', 'array-contains', CAROL).get());
      if (pros.docs.some((d) => d.data().familyId === A) || pros.size !== 1) {
        throw new Error(`Carol listed ${pros.docs.map((d) => d.id)}`);
      }
      const clocks = await assertSucceeds(as(CAROL).collection('request_deadlines')
          .where('participants', 'array-contains', CAROL).get());
      if (clocks.size !== 0) throw new Error('Carol listed family A\'s request clock');
    });
  });

  describe('none of them writes family A\'s schedule, settings, plan, thread or pushes', () => {
    it('refuses Carol writing A\'s custody schedule, money agreement and plan', async () => {
      await assertFails(as(CAROL).doc(`custody_models/${A}`).set(
          {participants: [IGOR, ZUZANA].sort(), lastModifiedBy: CAROL}));
      await assertFails(as(CAROL).doc(`family_settings/${A}`).set(
          {participants: [IGOR, ZUZANA].sort(), lastModifiedBy: CAROL, momShareBasisPoints: 9000}));
      await assertFails(as(CAROL).doc(`parenting_plans/${A}`).set(
          {answers: {[CAROL]: {care_weekday: 'x'}}}, {merge: true}));
    });

    it('refuses Carol a message into A\'s thread, a request clock in A and a push naming A',
        async () => {
          await assertFails(as(CAROL).collection('messages').add(
              {conversationId: A, senderId: CAROL, content: 'x'}));
          await assertFails(as(CAROL).doc(`request_deadlines/event_${CAROL}_ev-a`).set({
            kind: 'event', ref: 'ev-a', familyId: A, proposerUid: CAROL, responderUid: ZUZANA,
            participants: [CAROL, ZUZANA], createdAt: serverTimestamp(),
            answerByMillis: Date.now() + 3600000,
          }));
          await assertFails(as(CAROL).collection('notification_queue').add(
              {targetUserId: ZUZANA, data: {type: 'event_updated', familyId: A}}));
          await assertFails(as(CAROL).collection('notification_queue').add(
              {targetUserId: IGOR, data: {type: 'event_updated'}}));
        });

    it('refuses Carol creating an event stamped with family A', async () => {
      await assertFails(as(CAROL).doc('events/ev-c').set(eventDoc({
        id: 'ev-c', createdByFirebaseUid: CAROL, sharedWith: [CAROL, ZUZANA],
      })));
    });
  });

  describe('child_info', () => {
    describe('Carol as a guest of an A child Zuzana created', () => {
      // Guest codes carry no addressee, so whoever holds one redeems it — Carol included.
      const asGuest = () => childDoc({
        sharedWith: [ZUZANA, IGOR, CAROL],
        guests: guestOf(CAROL, Date.now() + 3600000),
      });

      beforeEach(async () => {
        await seed(env, {'child_info/kid-a': asGuest()});
      });

      it('still lets her read it while the grant stands — she is a guest', async () => {
        await assertSucceeds(as(CAROL).doc('child_info/kid-a').get());
      });

      it('refuses her extending her own grant', async () => {
        await assertFails(as(CAROL).doc('child_info/kid-a').update({
          guests: guestOf(CAROL, FUTURE),
        }));
      });

      it('refuses her dropping her grant while staying in the audience (access for ever)',
          async () => {
            await assertFails(as(CAROL).doc('child_info/kid-a').update({guests: {}}));
          });

      it('refuses her adding a guest of her own', async () => {
        await assertFails(as(CAROL).doc('child_info/kid-a').update({
          sharedWith: [ZUZANA, IGOR, CAROL, DAVE],
          guests: Object.assign(guestOf(CAROL, Date.now() + 3600000), guestOf(DAVE, FUTURE, CAROL)),
        }));
      });

      it('refuses her removing Igor and moving the child into family B', async () => {
        await assertFails(as(CAROL).doc('child_info/kid-a').update({
          sharedWith: [ZUZANA, CAROL], familyId: B,
        }));
      });

      it('refuses her editing the medical notes', async () => {
        await assertFails(as(CAROL).doc('child_info/kid-a').update({medicalNotes: 'changed'}));
      });
    });

    describe('Carol left in an A child\'s audience (what the item-38 leak wrote)', () => {
      beforeEach(async () => {
        await seed(env, {'child_info/kid-a': childDoc({sharedWith: [ZUZANA, IGOR, CAROL]})});
      });

      it('refuses her editing it', async () => {
        await assertFails(as(CAROL).doc('child_info/kid-a').update({medicalNotes: 'x'}));
      });

      it('refuses her naming herself a guest, which the operator repair would keep', async () => {
        await assertFails(as(CAROL).doc('child_info/kid-a').update({
          guests: guestOf(CAROL, FUTURE, CAROL),
        }));
      });

      it('refuses her moving it into family B and dropping Igor', async () => {
        await assertFails(as(CAROL).doc('child_info/kid-a').update({
          familyId: B, sharedWith: [ZUZANA, CAROL],
        }));
      });

      it('still lets Igor, the family\'s other parent, take her out', async () => {
        await assertSucceeds(as(IGOR).doc('child_info/kid-a').update({
          sharedWith: [ZUZANA, IGOR], lastModifiedBy: IGOR,
        }));
      });
    });

    describe('a stamped child keeps its family', () => {
      it('refuses the creator re-stamping it into her other family, the first of two writes',
          async () => {
            await assertFails(as(ZUZANA).doc('child_info/kid-a').update({familyId: B}));
          });

      it('refuses the creator blanking its family, which would let the next write add Carol',
          async () => {
            await assertFails(as(ZUZANA).doc('child_info/kid-a').update({familyId: ''}));
          });

      it('refuses Igor re-stamping Zuzana\'s child into her family with Carol', async () => {
        await assertFails(as(IGOR).doc('child_info/kid-a').update({familyId: B}));
      });

      it('refuses Igor re-stamping it into a strangers\' family, or blanking it', async () => {
        await assertFails(as(IGOR).doc('child_info/kid-a').update({
          familyId: familyId(DAVE, 'x-uid'),
        }));
        await assertFails(as(IGOR).doc('child_info/kid-a').update({familyId: ''}));
      });

      it('still lets Igor edit it and let a guest in', async () => {
        await assertSucceeds(as(IGOR).doc('child_info/kid-a').update({
          medicalNotes: 'Inhaler before sport', lastModifiedBy: IGOR,
        }));
        await assertSucceeds(as(IGOR).doc('child_info/kid-a').update({
          sharedWith: [ZUZANA, IGOR, DAVE], guests: guestOf(DAVE, FUTURE, IGOR),
        }));
      });

      it('still lets the creator, or Igor, fill a blank family with family A', async () => {
        await seed(env, {'child_info/kid-a': childDoc({familyId: ''})});
        await assertSucceeds(as(IGOR).doc('child_info/kid-a').update({familyId: A}));
        await seed(env, {'child_info/kid-a': childDoc({familyId: '', sharedWith: [ZUZANA]})});
        await assertSucceeds(as(ZUZANA).doc('child_info/kid-a').set(childDoc({})));
      });

      it('refuses Igor filling a blank family with one Zuzana is in without him', async () => {
        await seed(env, {'child_info/kid-a': childDoc({familyId: ''})});
        await assertFails(as(IGOR).doc('child_info/kid-a').update({familyId: B}));
      });
    });
  });

  describe('pets', () => {
    it('refuses Carol, left in an A pet\'s audience, editing it or moving it into family B',
        async () => {
          await seed(env, {'pets/pet-a': petDoc({sharedWith: [ZUZANA, IGOR, CAROL]})});
          await assertFails(as(CAROL).doc('pets/pet-a').update({feedingNotes: 'x'}));
          await assertFails(as(CAROL).doc('pets/pet-a').update({
            familyId: B, sharedWith: [ZUZANA, CAROL],
          }));
        });

    it('refuses the creator or Igor re-stamping a stamped pet', async () => {
      await assertFails(as(ZUZANA).doc('pets/pet-a').update({familyId: B}));
      await assertFails(as(ZUZANA).doc('pets/pet-a').update({familyId: ''}));
      await assertFails(as(IGOR).doc('pets/pet-a').update({familyId: B}));
    });

    it('refuses a pet with no family letting anybody in through a "guests" map', async () => {
      // A pet has no guests, and its read rule checks no grant expiry: a guests map here would be
      // a door that never closes.
      await assertFails(as(ZUZANA).doc('pets/pet-x').set(petDoc({
        id: 'pet-x', familyId: '', sharedWith: [ZUZANA, DAVE], guests: {[DAVE]: {}},
      })));
      await seed(env, {'pets/pet-x': petDoc({id: 'pet-x', familyId: ''})});
      await assertFails(as(ZUZANA).doc('pets/pet-x').update({
        sharedWith: [ZUZANA, IGOR, DAVE], guests: {[DAVE]: {}},
      }));
      await assertFails(as(IGOR).doc('pets/pet-x').update({
        sharedWith: [ZUZANA, IGOR, DAVE], guests: {[DAVE]: {}},
      }));
    });

    it('still lets Igor edit a family-A pet, and a pet with no family be shared', async () => {
      await assertSucceeds(as(IGOR).doc('pets/pet-a').update({
        feedingNotes: 'No chicken', lastModifiedBy: IGOR,
      }));
      await assertSucceeds(as(ZUZANA).doc('pets/pet-y').set(petDoc({
        id: 'pet-y', familyId: '', sharedWith: [ZUZANA, IGOR],
      })));
    });
  });

  describe('events', () => {
    it('refuses Zuzana creating a family-A event shared with Carol', async () => {
      await assertFails(as(ZUZANA).doc('events/ev-x').set(eventDoc({
        id: 'ev-x', sharedWith: [ZUZANA, CAROL],
      })));
      await assertFails(as(ZUZANA).doc('events/ev-x').set(eventDoc({
        id: 'ev-x', sharedWith: [ZUZANA, IGOR, CAROL],
      })));
    });

    it('refuses Zuzana adding Carol to an existing family-A event', async () => {
      // What `FamilyAudience.partnerFor`'s fallback uploads once family A has ended: the
      // co-parent on screen in place of the event's own family's.
      await assertFails(as(ZUZANA).doc('events/ev-a').update({sharedWith: [ZUZANA, IGOR, CAROL]}));
      await assertFails(as(ZUZANA).doc('events/ev-a').set(eventDoc({sharedWith: [ZUZANA, CAROL]})));
    });

    it('refuses the creator re-stamping a family-A event, so B\'s friend and professional never read it',
        async () => {
          await assertFails(as(ZUZANA).doc('events/ev-a').update({familyId: B}));
          await assertFails(as(ZUZANA).doc('events/ev-a').update({familyId: ''}));
          await assertFails(as(FRIEND_B).doc('events/ev-a').get());
          await assertFails(as(PRO_B).doc('events/ev-a').get());
        });

    it('still lets Zuzana create and edit a family-A event shared with Igor', async () => {
      await assertSucceeds(as(ZUZANA).doc('events/ev-y').set(eventDoc({id: 'ev-y'})));
      await assertSucceeds(as(ZUZANA).doc('events/ev-a').update({title: 'Dentist (moved)'}));
    });

    it('still lets Igor edit Zuzana\'s family-A event', async () => {
      await assertSucceeds(as(IGOR).doc('events/ev-a').update({
        title: 'Dentist, 10:30', lastModifiedBy: IGOR,
      }));
    });

    it('still lets the creator file an unstamped event into one of her families', async () => {
      await seed(env, {'events/ev-u': eventDoc({id: 'ev-u', familyId: '', sharedWith: [ZUZANA]})});
      await assertSucceeds(as(ZUZANA).doc('events/ev-u').update({
        familyId: B, sharedWith: [ZUZANA, CAROL],
      }));
    });
  });

  describe('event_versions', () => {
    it('refuses a family-A revision whose audience names Carol', async () => {
      await assertFails(as(ZUZANA).doc('event_versions/v1').set(versionDoc(ZUZANA, {
        sharedWith: [ZUZANA, CAROL],
      })));
    });

    it('refuses an unstamped revision of a family-A event whose audience names Carol', async () => {
      await assertFails(as(ZUZANA).doc('event_versions/v1').set(versionDoc(ZUZANA, {
        familyId: '', sharedWith: [ZUZANA, CAROL],
      })));
    });

    it('refuses Carol filing a revision under the id of an event that does not exist', async () => {
      await assertFails(as(CAROL).doc('event_versions/v2').set(versionDoc(CAROL, {
        eventId: 'ev-gone', kind: 'deleted', familyId: '', sharedWith: [CAROL, ZUZANA],
        event: eventDoc({id: 'ev-gone', title: 'forged', createdByFirebaseUid: ZUZANA}),
      })));
    });

    it('still lets either parent of A record a family-A revision', async () => {
      await assertSucceeds(as(ZUZANA).doc('event_versions/v1').set(versionDoc(ZUZANA, {})));
      await assertSucceeds(as(IGOR).doc('event_versions/v2').set(versionDoc(IGOR, {})));
    });

    it('still lets the creator record a revision of her own event that has not landed', async () => {
      await assertSucceeds(as(ZUZANA).doc('event_versions/v3').set(versionDoc(ZUZANA, {
        eventId: 'ev-offline', kind: 'created', event: eventDoc({id: 'ev-offline'}),
      })));
    });
  });

  describe('expenses', () => {
    it('refuses the creator re-stamping a family-A expense, which Carol would then read',
        async () => {
          await assertFails(as(ZUZANA).doc('expenses/x-a').update({familyId: B}));
          await assertFails(as(ZUZANA).doc('expenses/x-a').update({familyId: ''}));
          await assertFails(as(CAROL).doc('expenses/x-a').get());
        });

    it('still lets the creator edit it, and fill a blank family', async () => {
      await assertSucceeds(as(ZUZANA).doc('expenses/x-a').update({amount: 150}));
      await seed(env, {'expenses/x-u': {id: 'x-u', createdByFirebaseUid: ZUZANA, familyId: '',
        amount: 10}});
      await assertSucceeds(as(ZUZANA).doc('expenses/x-u').update({familyId: A}));
    });
  });

  describe('friend invitations name the family they open', () => {
    it('refuses one with no family, or a blank one', async () => {
      await assertFails(as(ZUZANA).doc('invitations/f1').set(friendInvite({familyId: undefined})));
      await assertFails(as(ZUZANA).doc('invitations/f1').set(friendInvite({familyId: ''})));
    });

    it('refuses one naming a family the inviter is not in, or not a pair', async () => {
      await assertFails(as(CAROL).doc('invitations/f2').set(friendInvite({
        fromUserId: CAROL, familyId: A,
      })));
      await assertFails(as(ZUZANA).doc('invitations/f1').set(friendInvite({
        familyId: `${CAROL}__${IGOR}__${ZUZANA}`,
      })));
    });

    it('still lets Zuzana invite a friend into either of her families', async () => {
      await assertSucceeds(as(ZUZANA).doc('invitations/f1').set(friendInvite({})));
      await assertSucceeds(as(ZUZANA).doc('invitations/f3').set(friendInvite({familyId: A})));
    });
  });

  describe('change_requests', () => {
    it('refuses a request stamped with family A addressed to Carol, either way round', async () => {
      await assertFails(as(ZUZANA).doc('change_requests/cr-1').set(changeRequestDoc({
        familyId: A,
      })));
      await assertFails(as(CAROL).doc('change_requests/cr-1').set(changeRequestDoc({
        requestedBy: CAROL, requestedTo: ZUZANA, familyId: A,
      })));
    });

    it('refuses re-stamping a request into another family, or filling a blank with one',
        async () => {
          await seed(env, {'change_requests/cr-1': changeRequestDoc({})});
          await assertFails(as(ZUZANA).doc('change_requests/cr-1').update({familyId: A}));
          await seed(env, {'change_requests/cr-2': changeRequestDoc({id: 'cr-2', familyId: ''})});
          await assertFails(as(ZUZANA).doc('change_requests/cr-2').update({familyId: A}));
        });

    it('still lets Zuzana address Carol in their own family, and the backfill fill a blank',
        async () => {
          await assertSucceeds(as(ZUZANA).doc('change_requests/cr-1').set(changeRequestDoc({})));
          await assertSucceeds(as(ZUZANA).doc('change_requests/cr-3').set(changeRequestDoc({
            id: 'cr-3', familyId: '',
          })));
          await assertSucceeds(as(CAROL).doc('change_requests/cr-3').update({familyId: B}));
          await assertSucceeds(as(CAROL).doc('change_requests/cr-1').update({
            status: 'ACCEPTED', respondedAt: '2026-10-20T10:00:00',
          }));
        });
  });
});
