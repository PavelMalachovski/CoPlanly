/**
 * A child's or a pet's record stays in its own family.
 *
 * The scenario throughout: **Alice co-parents in two families** — with Bob, and with Carol. A
 * record stamped with the Alice–Bob family id is the Bob family's: its medical profile, its
 * allergies, its medications. The leak this file pins came from the client: `SyncService` and the
 * repositories named the co-parent of the family Alice was *looking at* in every upload, so a switch
 * to the Carol family followed by one sync rewrote every child Alice had entered for Bob's family
 * with `sharedWith: [alice, carol]` — Carol could read the children, Bob lost them. The rules let
 * it through, because `myAudience()` and `onlyFamilyOrGuestsAdded` accept any of the caller's
 * co-parents, from any family.
 *
 * What the rules bound now: once a record names a family, a write may only *add* to `sharedWith`
 * one of that family's two parents — derived from the family id itself, `FamilyKey.of`'s two uids
 * sorted and joined — or, on a child's record, a guest the same write names in `guests`. On create
 * the whole audience must satisfy that. Removals stay allowed (the bound is on what a write adds,
 * CLAUDE.md item 23), and a record that names no family keeps the older bound alone.
 */

const {
  CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-child-pet-family-audience';
const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const CAROL = 'carol-uid';
const NINA = 'nina-uid';

/** The family id for a pair, built the way `FamilyKey.of` builds it. */
function familyId(uidA, uidB) {
  return [uidA, uidB].sort().join('__');
}

const WITH_BOB = familyId(ALICE, BOB);
const WITH_CAROL = familyId(ALICE, CAROL);

/** Epoch millis well in the future: a guest grant that is still good. */
const FUTURE = Date.parse('2099-01-01T00:00:00Z');

/**
 * A guests map holding one grant, as `ChildInfoGuests.encode` writes it.
 *
 * @param {string} uid The guest's UID.
 * @return {!Object} The map to store under `guests`.
 */
function guestOf(uid) {
  return {[uid]: {
    name: 'Nina',
    grantedBy: ALICE,
    grantedAtMillis: Date.parse('2026-09-01T09:30:00Z'),
    expiresAtMillis: FUTURE,
  }};
}

/**
 * A child record as `SyncService.syncChildInfo` writes it, in the Bob family by default.
 *
 * @param {!Object} overrides Fields to override on the default document.
 * @return {!Object} The document data.
 */
function childDoc(overrides) {
  return Object.assign({
    id: 'child-1',
    childName: 'Ema',
    dateOfBirth: '2018-03-04T00:00:00',
    medications: [{name: 'Salbutamol', dosage: '100 mcg', frequency: 'as needed', notes: null}],
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
    createdByFirebaseUid: ALICE,
    lastModifiedBy: ALICE,
    familyId: WITH_BOB,
    sharedWith: [ALICE, BOB],
  }, overrides);
}

/**
 * A pet record as `PetRepositoryImpl.toFirestoreMap` writes it, in the Bob family by default.
 *
 * @param {!Object} overrides Fields to override on the default document.
 * @return {!Object} The document data.
 */
function petDoc(overrides) {
  return Object.assign({
    id: 'pet-1',
    name: 'Rex',
    species: 'DOG',
    breed: 'Beagle',
    dateOfBirth: null,
    medications: [{name: 'Heartworm tablet', dosage: '1', frequency: 'monthly', notes: null}],
    vaccinations: [],
    specialNeeds: null,
    feedingNotes: 'Twice a day',
    vetName: null,
    vetPhone: null,
    photos: [],
    createdAt: '2026-08-01T10:00:00',
    updatedAt: '2026-08-01T10:00:00',
    createdByFirebaseUid: ALICE,
    lastModifiedBy: ALICE,
    familyId: WITH_BOB,
    sharedWith: [ALICE, BOB],
  }, overrides);
}

describe('a child or pet record stays in its own family', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, {
      // Alice is in both families; Bob and Carol share nothing. The singular `partnerId` names
      // whichever family Alice's phone happened to write last — it is no tie-breaker here.
      [`users/${ALICE}`]: {name: 'Alice', partnerIds: [BOB, CAROL], partnerId: CAROL},
      [`users/${BOB}`]: {name: 'Bob', partnerIds: [ALICE], partnerId: ALICE},
      [`users/${CAROL}`]: {name: 'Carol', partnerIds: [ALICE], partnerId: ALICE},
      [`users/${NINA}`]: {name: 'Nina', partnerId: ''},
      [`families/${WITH_BOB}`]: {members: [ALICE, BOB]},
      [`families/${WITH_CAROL}`]: {members: [ALICE, CAROL]},
    });
  });

  const as = (uid) => env.authenticatedContext(uid).firestore();

  describe('child_info', () => {
    it('refuses the leak: Alice re-uploads a Bob-family child naming Carol', async () => {
      // Exactly what the client did after a switch to the Carol family and one sync: a full
      // `set()` from Room, with the co-parent of the family on screen in the audience.
      await seed(env, {'child_info/child-1': childDoc({})});
      await assertFails(as(ALICE).doc('child_info/child-1').set(childDoc({
        sharedWith: [ALICE, CAROL],
      })));
    });

    it('refuses Carol being added beside Bob, too', async () => {
      await seed(env, {'child_info/child-1': childDoc({})});
      await assertFails(as(ALICE).doc('child_info/child-1').update({
        sharedWith: [ALICE, BOB, CAROL],
      }));
    });

    it('refuses a create in the Bob family that names Carol', async () => {
      await assertFails(as(ALICE).doc('child_info/child-2').set(childDoc({
        id: 'child-2', sharedWith: [ALICE, BOB, CAROL],
      })));
      await assertFails(as(ALICE).doc('child_info/child-2').set(childDoc({
        id: 'child-2', sharedWith: [ALICE, CAROL],
      })));
    });

    it('refuses moving the record into the Carol family to let Carol in', async () => {
      // A record keeps its family (CLAUDE.md item 18); re-stamping it is how a bound on the new
      // family alone would be walked around.
      await seed(env, {'child_info/child-1': childDoc({})});
      await assertFails(as(ALICE).doc('child_info/child-1').set(childDoc({
        familyId: WITH_CAROL, sharedWith: [ALICE, CAROL],
      })));
    });

    it('refuses a family id that does not name exactly two people', async () => {
      await assertFails(as(ALICE).doc('child_info/child-2').set(childDoc({
        id: 'child-2', familyId: `${ALICE}__${BOB}__${CAROL}`, sharedWith: [ALICE, BOB, CAROL],
      })));
    });

    it('still lets Alice create a child in the Bob family shared with Bob', async () => {
      await assertSucceeds(as(ALICE).doc('child_info/child-2').set(childDoc({id: 'child-2'})));
    });

    it('still lets Alice create a child in the Carol family shared with Carol', async () => {
      await assertSucceeds(as(ALICE).doc('child_info/child-2').set(childDoc({
        id: 'child-2', familyId: WITH_CAROL, sharedWith: [ALICE, CAROL],
      })));
    });

    it('still lets Alice re-upload the record with its own family\'s audience', async () => {
      await seed(env, {'child_info/child-1': childDoc({})});
      await assertSucceeds(as(ALICE).doc('child_info/child-1').set(childDoc({
        medicalNotes: 'Inhaler before sport',
      })));
    });

    it('still lets Alice let in a guest the write names', async () => {
      await seed(env, {'child_info/child-1': childDoc({})});
      await assertSucceeds(as(ALICE).doc('child_info/child-1').set(childDoc({
        sharedWith: [ALICE, BOB, NINA], guests: guestOf(NINA),
      })));
    });

    it('still lets Alice create a record with a guest beside the family', async () => {
      await assertSucceeds(as(ALICE).doc('child_info/child-2').set(childDoc({
        id: 'child-2', sharedWith: [ALICE, BOB, NINA], guests: guestOf(NINA),
      })));
    });

    it('still lets Bob, the family\'s other parent, edit and let a guest in', async () => {
      await seed(env, {'child_info/child-1': childDoc({})});
      await assertSucceeds(as(BOB).doc('child_info/child-1').update({
        medicalNotes: 'Pollen allergy', lastModifiedBy: BOB,
      }));
      await assertSucceeds(as(BOB).doc('child_info/child-1').update({
        sharedWith: [ALICE, BOB, NINA], guests: guestOf(NINA),
      }));
    });

    it('still lets Alice narrow the audience: a removal is never refused', async () => {
      // The repair direction: the leaked uid comes out, whoever is writing.
      await seed(env, {'child_info/child-1': childDoc({sharedWith: [ALICE, BOB, CAROL]})});
      await assertSucceeds(as(ALICE).doc('child_info/child-1').set(childDoc({})));
    });

    it('still lets Alice upload a record that predates the pairing into its family', async () => {
      // The pairing backfill (CLAUDE.md items 16 and 22): uploaded as `familyId: ""` with an
      // audience of one, stamped locally once there is a family, then uploaded again.
      await seed(env, {'child_info/child-1': childDoc({familyId: '', sharedWith: [ALICE]})});
      await assertSucceeds(as(ALICE).doc('child_info/child-1').set(childDoc({})));
    });

    it('keeps the older bound for a record that names no family', async () => {
      // Nothing to derive the two parents from: any of Alice's co-parents, as before. The client
      // no longer names one for an unstamped record while there are two (`recordPartnerFor`);
      // the rule cannot tell which family it was, so it does not pretend to.
      await seed(env, {'child_info/child-1': childDoc({familyId: '', sharedWith: [ALICE]})});
      await assertSucceeds(as(ALICE).doc('child_info/child-1').update({
        sharedWith: [ALICE, CAROL],
      }));
      await assertSucceeds(as(ALICE).doc('child_info/child-2').set(childDoc({
        id: 'child-2', familyId: '', sharedWith: [ALICE, BOB, CAROL],
      })));
    });

    it('still refuses a stranger in a record that names no family', async () => {
      await seed(env, {'child_info/child-1': childDoc({familyId: '', sharedWith: [ALICE]})});
      await assertFails(as(ALICE).doc('child_info/child-1').update({
        sharedWith: [ALICE, 'dave-uid'],
      }));
    });
  });

  describe('pets', () => {
    it('refuses the leak: Alice re-uploads a Bob-family pet naming Carol', async () => {
      await seed(env, {'pets/pet-1': petDoc({})});
      await assertFails(as(ALICE).doc('pets/pet-1').set(petDoc({sharedWith: [ALICE, CAROL]})));
    });

    it('refuses the audience repair adding Carol beside Bob', async () => {
      // `PetRepositoryImpl.repairAudience` is a one-field `update()` that only widens.
      await seed(env, {'pets/pet-1': petDoc({})});
      await assertFails(as(ALICE).doc('pets/pet-1').update({sharedWith: [ALICE, BOB, CAROL]}));
    });

    it('refuses a create in the Bob family that names Carol', async () => {
      await assertFails(as(ALICE).doc('pets/pet-2').set(petDoc({
        id: 'pet-2', sharedWith: [ALICE, BOB, CAROL],
      })));
    });

    it('refuses letting anybody in as a "guest": a pet has none', async () => {
      // The child record's guest exception does not carry over. A pet document carrying a
      // `guests` map is something no client writes, and it must not become a way in.
      await seed(env, {'pets/pet-1': petDoc({})});
      await assertFails(as(ALICE).doc('pets/pet-1').set(petDoc({
        sharedWith: [ALICE, BOB, CAROL], guests: guestOf(CAROL),
      })));
    });

    it('refuses moving the pet into the Carol family to let Carol in', async () => {
      await seed(env, {'pets/pet-1': petDoc({})});
      await assertFails(as(ALICE).doc('pets/pet-1').set(petDoc({
        familyId: WITH_CAROL, sharedWith: [ALICE, CAROL],
      })));
    });

    it('still lets Alice create and edit a Bob-family pet shared with Bob', async () => {
      await assertSucceeds(as(ALICE).doc('pets/pet-2').set(petDoc({id: 'pet-2'})));
      await assertSucceeds(as(ALICE).doc('pets/pet-2').set(petDoc({
        id: 'pet-2', feedingNotes: 'Once a day',
      })));
    });

    it('still lets the audience repair add the family\'s own co-parent', async () => {
      await seed(env, {'pets/pet-1': petDoc({sharedWith: [ALICE]})});
      await assertSucceeds(as(ALICE).doc('pets/pet-1').update({sharedWith: [ALICE, BOB]}));
    });

    it('still lets Bob, the family\'s other parent, edit', async () => {
      await seed(env, {'pets/pet-1': petDoc({})});
      await assertSucceeds(as(BOB).doc('pets/pet-1').update({
        feedingNotes: 'No chicken', lastModifiedBy: BOB,
      }));
    });

    it('still lets Alice narrow the audience', async () => {
      await seed(env, {'pets/pet-1': petDoc({sharedWith: [ALICE, BOB, CAROL]})});
      await assertSucceeds(as(ALICE).doc('pets/pet-1').update({sharedWith: [ALICE, BOB]}));
    });

    it('keeps the older bound for a pet that names no family', async () => {
      await seed(env, {'pets/pet-1': petDoc({familyId: '', sharedWith: [ALICE]})});
      await assertSucceeds(as(ALICE).doc('pets/pet-1').update({sharedWith: [ALICE, CAROL]}));
    });
  });
});
