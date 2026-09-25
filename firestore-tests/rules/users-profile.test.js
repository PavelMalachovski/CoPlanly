/**
 * The `users/{uid}` profile write that `UserRepositoryImpl.ensureProfile` performs.
 *
 * Background: nothing in the app ever wrote the signed-in user's own `name` and `email`
 * into `users/{uid}`. The document existed only because `FcmService.updateUserToken`
 * merges a lone `fcmToken` key into it, so the co-parent — who reads `name`/`email` from
 * that same document to render the paired-partner card — saw "Unknown"/"Email unavailable"
 * for a perfectly valid pairing.
 *
 * `ensureProfile` closes that gap with a `set(..., merge)` carrying only the identity keys.
 * Two things have to hold, and both are checked here against the real ruleset rather than
 * argued from the source:
 *
 *   1. the write is *permitted* — it hits `allow create` when no document exists yet and
 *      `allow update` when the FCM registration got there first; and
 *   2. the write is *non-destructive* — `partnerId`, `pairedAt`, `fcmToken` and
 *      `pendingRevocationOf` all belong to other writers (the pairing callable, the unpair
 *      sweep, the FCM registration) and must survive it.
 */

const {
  CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails,
} = require('../harness');

const firebase = require('firebase/compat/app').default;
require('firebase/compat/firestore');

const PROJECT = 'demo-coplanly-user-profile';
const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const STRANGER = 'stranger-uid';

/** The exact map `ensureProfile` merges when the stored document carries no identity. */
const IDENTITY_PATCH = {
  id: ALICE,
  firebaseUid: ALICE,
  name: 'Alice Novak',
  email: 'alice@example.com',
};

/**
 * The compat SDK's delete sentinel — what `FieldValue.delete()` sends from the app. The contexts
 * the harness hands out are compat Firestore instances, so the modular `deleteField()` is not
 * one they accept.
 *
 * @return {!Object} The sentinel.
 */
function deleteField() {
  return firebase.firestore.FieldValue.delete();
}

/**
 * Performs the profile write as the signed-in owner.
 *
 * @param {!Object} env Rules test environment.
 * @param {!Object} patch Keys to merge into the profile.
 * @return {!Promise} The pending write.
 */
function mergeProfile(env, patch) {
  return env.authenticatedContext(ALICE).firestore()
      .doc(`users/${ALICE}`).set(patch, {merge: true});
}

/**
 * Reads a document back with the rules bypassed.
 *
 * @param {!Object} env Rules test environment.
 * @param {string} refPath Document path.
 * @return {!Promise<!Object>} The stored data.
 */
async function readRaw(env, refPath) {
  let data;
  await env.withSecurityRulesDisabled(async (ctx) => {
    data = (await ctx.firestore().doc(refPath).get()).data();
  });
  return data;
}

describe('users profile: the identity write ensureProfile performs', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
  });

  it('creates the profile when no document exists yet', async () => {
    // A merge onto a missing document is a create, so the `allow create` clause applies:
    // it demands both `email` and `name`, which is why the patch always carries them.
    await assertSucceeds(mergeProfile(env, IDENTITY_PATCH));
  });

  it('updates the fcm-token-only document the app actually produces on a device', async () => {
    await seed(env, {[`users/${ALICE}`]: {fcmToken: 'token-1'}});

    await assertSucceeds(mergeProfile(env, IDENTITY_PATCH));
  });

  it('leaves partnerId, pairedAt, fcmToken and pendingRevocationOf intact', async () => {
    await seed(env, {
      [`users/${ALICE}`]: {
        fcmToken: 'token-1',
        partnerId: BOB,
        pairedAt: 1754000000000,
        pendingRevocationOf: [BOB],
      },
    });

    await assertSucceeds(mergeProfile(env, IDENTITY_PATCH));

    const stored = await readRaw(env, `users/${ALICE}`);
    if (stored.partnerId !== BOB) throw new Error('partnerId was lost');
    if (stored.pairedAt !== 1754000000000) throw new Error('pairedAt was lost');
    if (stored.fcmToken !== 'token-1') throw new Error('fcmToken was lost');
    if (!Array.isArray(stored.pendingRevocationOf)) {
      throw new Error('pendingRevocationOf was lost');
    }
    if (stored.name !== 'Alice Novak') throw new Error('name was not written');
    if (stored.email !== 'alice@example.com') throw new Error('email was not written');
  });

  it('refuses a full set that would drop the server-owned pairing state', async () => {
    // The hazard the removed `upsertUser` carried: a full replace erased `partnerId` along
    // with `pendingRevocationOf`. The rules used to allow it — merge was the only safeguard.
    // `partnerId`, `partnerIds` and `role` are server-owned now, so a write that moves any of
    // them is refused whatever shape it takes.
    await seed(env, {
      [`users/${ALICE}`]: {partnerId: BOB, pendingRevocationOf: [BOB]},
    });

    await assertFails(
        env.authenticatedContext(ALICE).firestore()
            .doc(`users/${ALICE}`).set(IDENTITY_PATCH));
  });

  it('refuses the owner moving their own slot or pairing', async () => {
    // A parent who could set their own slot would take the co-parent's colour and re-point
    // what `parentOwner` means across the calendar; both in one slot switches the split off.
    await seed(env, {
      [`users/${ALICE}`]: {name: 'Alice', email: 'alice@example.com', role: 'mom', partnerId: BOB, partnerIds: [BOB]},
    });
    const alice = env.authenticatedContext(ALICE).firestore();
    await assertFails(alice.doc(`users/${ALICE}`).update({role: 'dad'}));
    await assertFails(alice.doc(`users/${ALICE}`).update({partnerIds: [BOB, STRANGER]}));
    await assertFails(alice.doc(`users/${ALICE}`).update({partnerId: ''}));
  });

  it('still accepts a write that repeats the stored slot unchanged', async () => {
    await seed(env, {
      [`users/${ALICE}`]: {name: 'Alice', email: 'alice@example.com', role: 'mom', partnerId: BOB},
    });
    const alice = env.authenticatedContext(ALICE).firestore();
    await assertSucceeds(alice.doc(`users/${ALICE}`).set({role: 'mom', name: 'Alice N'}, {merge: true}));
  });

  it('lets the owner record and clear their holiday country and region', async () => {
    // MON-13's regional half. `UserRepositoryImpl.updateUser` merges `regionCode` beside
    // `countryCode`, and writes "" rather than omitting the key for "nationwide", so a cleared
    // region is actually cleared. Nothing in the rule names either key: this pins that the
    // profile rule stays an open map for them.
    await seed(env, {
      [`users/${ALICE}`]: {name: 'Alice', email: 'alice@example.com', role: 'mom', countryCode: 'CZ'},
    });
    await assertSucceeds(mergeProfile(env, {countryCode: 'DE', regionCode: 'BY'}));
    await assertSucceeds(mergeProfile(env, {countryCode: 'DE', regionCode: ''}));

    const stored = await readRaw(env, `users/${ALICE}`);
    if (stored.countryCode !== 'DE') throw new Error('countryCode was not written');
    if (stored.regionCode !== '') throw new Error('a cleared region was not cleared');
  });

  it('does not let the co-parent set the other parent\'s region', async () => {
    await seed(env, {
      [`users/${ALICE}`]: {name: 'Alice', email: 'alice@example.com', role: 'mom', partnerId: BOB},
      [`users/${BOB}`]: {name: 'Bob', email: 'bob@example.com', role: 'dad', partnerId: ALICE},
    });
    await assertFails(
        env.authenticatedContext(BOB).firestore()
            .doc(`users/${ALICE}`).set({regionCode: 'BY'}, {merge: true}));
  });

  it('bounds the name on update as it does on create', async () => {
    await seed(env, {
      [`users/${ALICE}`]: {name: 'Alice', email: 'alice@example.com', role: 'mom'},
    });
    const alice = env.authenticatedContext(ALICE).firestore();
    await assertFails(alice.doc(`users/${ALICE}`).update({name: 'x'.repeat(5000)}));
  });

  it('rejects a create that carries no name', async () => {
    // `ensureProfile` refuses to write when it cannot derive a name; this is the rule that
    // makes that refusal the right call rather than an over-cautious one.
    await assertFails(mergeProfile(env, {id: ALICE, email: 'alice@example.com'}));
  });

  it('accepts a name-less merge into a document that already exists', async () => {
    // The other half of the same question, and the one that decides how much
    // `ensureProfile` may salvage when no name can be derived: `allow update` places no
    // requirement on `name`, so an existing document can take the email, the photo and
    // the ids without one. A device stuck without a display name therefore does not have
    // to discard everything else it knows — it only has to skip the name.
    await seed(env, {[`users/${ALICE}`]: {fcmToken: 'token-1'}});

    await assertSucceeds(mergeProfile(env, {
      id: ALICE,
      firebaseUid: ALICE,
      email: 'alice@example.com',
      profilePhotoUrl: 'https://lh3.googleusercontent.com/a/alice',
    }));

    const stored = await readRaw(env, `users/${ALICE}`);
    if (stored.name !== undefined) throw new Error('no name should have been written');
    if (stored.fcmToken !== 'token-1') throw new Error('fcmToken was lost');
  });

  it('still rejects the same name-less merge when no document exists', async () => {
    // Same patch, no seeded document: now it is a create, and the create rule wins. This
    // pair is why `ensureProfile` keys the salvage attempt on having read a document.
    await assertFails(mergeProfile(env, {
      id: ALICE,
      firebaseUid: ALICE,
      email: 'alice@example.com',
      profilePhotoUrl: 'https://lh3.googleusercontent.com/a/alice',
    }));
  });

  it('rejects a firebaseUid that does not match the caller', async () => {
    await assertFails(mergeProfile(env, {...IDENTITY_PATCH, firebaseUid: BOB}));
  });

  it('does not let the co-parent write the profile', async () => {
    await seed(env, {
      [`users/${ALICE}`]: {partnerId: BOB, name: 'Alice Novak'},
      [`users/${BOB}`]: {partnerId: ALICE, name: 'Bob Novak'},
    });

    await assertFails(
        env.authenticatedContext(BOB).firestore()
            .doc(`users/${ALICE}`).set({name: 'Hacked'}, {merge: true}));
  });

  it('lets a co-parent read the other parent\'s phone', async () => {
    // Deliberate: if something happens to one parent, the other can reach them. The parent's own
    // health data that used to ride on this document is gone — see the block below.
    await seed(env, {
      [`users/${ALICE}`]: {
        name: 'Alice Novak',
        partnerId: BOB,
        phone: '+420123456789',
      },
      [`users/${BOB}`]: {name: 'Bob Novak', partnerId: ALICE},
    });

    const snap = await assertSucceeds(
        env.authenticatedContext(BOB).firestore().doc(`users/${ALICE}`).get());
    const data = snap.data();
    if (data.phone !== '+420123456789') throw new Error('phone did not come back with the document');
  });

  it('refuses to let a co-parent write the other parent\'s profile fields', async () => {
    // Load-bearing. Client writes to another user's document are what forced the permissive
    // firestore.rules.simple to be deployed once already.
    await seed(env, {
      [`users/${ALICE}`]: {
        name: 'Alice Novak',
        partnerId: BOB,
        phone: '+420123456789',
      },
      [`users/${BOB}`]: {name: 'Bob Novak', partnerId: ALICE},
    });

    await assertFails(
        env.authenticatedContext(BOB).firestore()
            .doc(`users/${ALICE}`).set({phone: '+420000000000'}, {merge: true}));
  });

  it('refuses a stranger who is nobody\'s co-parent', async () => {
    await seed(env, {
      [`users/${ALICE}`]: {
        name: 'Alice Novak',
        partnerId: BOB,
        phone: '+420123456789',
      },
    });

    await assertFails(
        env.authenticatedContext(STRANGER).firestore().doc(`users/${ALICE}`).get());
  });
});

/**
 * The parent's own health data is gone (GDPR data minimisation): the co-parent reads this
 * document, so an adult's `allergies` and `medicalProfile` reached their ex-partner. A write may
 * never add or change either key; it may leave one an older build stored untouched, and it may
 * delete it — which is exactly what `UserRepositoryImpl.updateUser` now sends on every save.
 */
describe('users profile: the parent\'s own health data is refused', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
  });

  it('refuses a create carrying allergies', async () => {
    await assertFails(mergeProfile(env, {...IDENTITY_PATCH, allergies: ['peanuts']}));
  });

  it('refuses a create carrying a medical profile', async () => {
    await assertFails(mergeProfile(env, {...IDENTITY_PATCH, medicalProfile: {}}));
  });

  it('refuses an update adding allergies', async () => {
    await seed(env, {[`users/${ALICE}`]: {name: 'Alice Novak', email: 'alice@example.com'}});

    await assertFails(mergeProfile(env, {allergies: ['peanuts']}));
  });

  it('refuses an update adding a medical profile, even an empty one', async () => {
    await seed(env, {[`users/${ALICE}`]: {name: 'Alice Novak', email: 'alice@example.com'}});

    await assertFails(mergeProfile(env, {medicalProfile: {bloodType: 'A_POSITIVE'}}));
    await assertFails(mergeProfile(env, {medicalProfile: {}}));
  });

  it('refuses changing what an older build stored', async () => {
    await seed(env, {
      [`users/${ALICE}`]: {name: 'Alice Novak', allergies: ['peanuts'], medicalProfile: {}},
    });

    await assertFails(mergeProfile(env, {allergies: ['peanuts', 'penicillin']}));
    await assertFails(mergeProfile(env, {medicalProfile: {bloodType: 'O_NEGATIVE'}}));
  });

  it('allows the profile save that deletes both keys', async () => {
    await seed(env, {
      [`users/${ALICE}`]: {
        name: 'Alice Novak',
        allergies: ['peanuts'],
        medicalProfile: {bloodType: 'O_NEGATIVE'},
      },
    });

    await assertSucceeds(mergeProfile(env, {
      ...IDENTITY_PATCH,
      phone: '+420123456789',
      allergies: deleteField(),
      medicalProfile: deleteField(),
    }));

    const stored = await readRaw(env, `users/${ALICE}`);
    if ('allergies' in stored || 'medicalProfile' in stored) {
      throw new Error('the health keys survived the deleting save');
    }
  });

  it('allows the deleting save on a document that never held the keys', async () => {
    // Every save sends the deletes, so they must be harmless where there is nothing to delete.
    await seed(env, {[`users/${ALICE}`]: {name: 'Alice Novak', email: 'alice@example.com'}});

    await assertSucceeds(mergeProfile(env, {
      name: 'Alice N.',
      allergies: deleteField(),
      medicalProfile: deleteField(),
    }));
  });

  it('still allows an ordinary write to a document an older build left the keys on', async () => {
    // The FCM token refresh and ensureProfile merge only their own keys; refusing them until the
    // legacy keys were gone would lock the owner out of their own profile.
    await seed(env, {
      [`users/${ALICE}`]: {name: 'Alice Novak', allergies: ['peanuts'], medicalProfile: {}},
    });

    await assertSucceeds(mergeProfile(env, {fcmToken: 'token-2'}));
  });

  it('still allows an ordinary profile write', async () => {
    await seed(env, {[`users/${ALICE}`]: {name: 'Alice Novak', email: 'alice@example.com'}});

    await assertSucceeds(mergeProfile(env, {name: 'Alice N.', phone: '+420123456789'}));
  });
});

/**
 * The child-health consent (GDPR Art. 9(2)(a)) lives on the parent's own document as
 * `healthDataConsent: {version, atMillis}` — written by `UserRepositoryImpl.setHealthConsent`,
 * bounded here to exactly that shape.
 */
describe('users profile: the child-health consent', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, {
      [`users/${ALICE}`]: {name: 'Alice Novak', email: 'alice@example.com', partnerId: BOB},
      [`users/${BOB}`]: {name: 'Bob Novak', partnerId: ALICE},
    });
  });

  it('lets the owner record their consent', async () => {
    await assertSucceeds(mergeProfile(env, {
      healthDataConsent: {version: 1, atMillis: 1787000000000},
    }));
  });

  it('lets the owner withdraw it', async () => {
    await mergeProfile(env, {healthDataConsent: {version: 1, atMillis: 1787000000000}});

    await assertSucceeds(mergeProfile(env, {healthDataConsent: deleteField()}));
    const stored = await readRaw(env, `users/${ALICE}`);
    if ('healthDataConsent' in stored) throw new Error('the consent survived its withdrawal');
  });

  it('accepts the consent on a create too', async () => {
    await env.clearFirestore();

    await assertSucceeds(mergeProfile(env, {
      ...IDENTITY_PATCH,
      healthDataConsent: {version: 1, atMillis: 1787000000000},
    }));
  });

  it('refuses a consent with an extra key', async () => {
    await assertFails(mergeProfile(env, {
      healthDataConsent: {version: 1, atMillis: 1787000000000, scope: 'everything'},
    }));
  });

  it('refuses a consent missing a key', async () => {
    await assertFails(mergeProfile(env, {healthDataConsent: {version: 1}}));
    await assertFails(mergeProfile(env, {healthDataConsent: {atMillis: 1787000000000}}));
  });

  it('refuses a consent whose values are not integers', async () => {
    await assertFails(mergeProfile(env, {
      healthDataConsent: {version: '1', atMillis: 1787000000000},
    }));
    await assertFails(mergeProfile(env, {
      healthDataConsent: {version: 1, atMillis: '2026-09-25'},
    }));
    await assertFails(mergeProfile(env, {
      healthDataConsent: {version: 1.5, atMillis: 1787000000000},
    }));
  });

  it('refuses a consent that is not a map', async () => {
    await assertFails(mergeProfile(env, {healthDataConsent: true}));
  });

  it('refuses a version or time below one', async () => {
    await assertFails(mergeProfile(env, {healthDataConsent: {version: 0, atMillis: 1787000000000}}));
    await assertFails(mergeProfile(env, {healthDataConsent: {version: 1, atMillis: 0}}));
  });

  it('does not let the co-parent record or withdraw it', async () => {
    await assertFails(
        env.authenticatedContext(BOB).firestore().doc(`users/${ALICE}`)
            .set({healthDataConsent: {version: 1, atMillis: 1787000000000}}, {merge: true}));
  });
});
