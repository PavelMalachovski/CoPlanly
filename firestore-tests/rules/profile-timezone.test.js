/**
 * `users/{uid}.timeZone` (iPhone phase 4 part C, design §7.3): the IANA zone the parent's phone is
 * in, which the server's request reminders are planned in when no phone delivered the clock, and
 * whose quiet hours (21:00–08:00) they keep. Both apps write it with the profile, on start and when
 * the zone changes (`UserRepository.recordTimeZone`).
 *
 * The rules hold its shape — a non-empty string of at most 64 characters — and its writer: the
 * profile's owner alone. A zone the server could not read would make it plan at the wrong hour.
 */

const {
  CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-profile-timezone';
const ALICE = 'alice-uid';
const BOB = 'bob-uid';

describe('users profile: the time zone', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, {
      [`users/${ALICE}`]: {name: 'Alice', email: 'a@x.test', partnerId: BOB, partnerIds: [BOB]},
      [`users/${BOB}`]: {name: 'Bob', email: 'b@x.test', partnerId: ALICE, partnerIds: [ALICE]},
    });
  });

  const profile = (as, uid) => env.authenticatedContext(as).firestore().doc(`users/${uid}`);

  it('lets the owner write their zone, and change it', async () => {
    await assertSucceeds(profile(ALICE, ALICE).set({timeZone: 'Europe/Prague'}, {merge: true}));
    await assertSucceeds(profile(ALICE, ALICE).set({timeZone: 'America/Argentina/Buenos_Aires'}, {merge: true}));
    await assertSucceeds(profile(ALICE, ALICE).set({timeZone: 'x'.repeat(64)}, {merge: true}));
  });

  it('refuses a zone longer than 64 characters, an empty one, or one that is not text', async () => {
    await assertFails(profile(ALICE, ALICE).set({timeZone: 'x'.repeat(65)}, {merge: true}));
    await assertFails(profile(ALICE, ALICE).set({timeZone: ''}, {merge: true}));
    await assertFails(profile(ALICE, ALICE).set({timeZone: 3600}, {merge: true}));
    await assertFails(profile(ALICE, ALICE).set({timeZone: {id: 'Europe/Prague'}}, {merge: true}));
  });

  it('refuses another user writing the zone, the co-parent included', async () => {
    await assertFails(profile(BOB, ALICE).set({timeZone: 'Europe/Prague'}, {merge: true}));
    await assertFails(profile('stranger-uid', ALICE).set({timeZone: 'Europe/Prague'}, {merge: true}));
  });

  it('allows a profile created with its zone, and refuses one created with a bad zone', async () => {
    const carol = (data) => profile('carol-uid', 'carol-uid').set(data);
    await assertFails(carol({name: 'Carol', email: 'c@x.test', timeZone: 'x'.repeat(65)}));
    await assertSucceeds(carol({name: 'Carol', email: 'c@x.test', timeZone: 'Europe/Berlin'}));
  });
});
