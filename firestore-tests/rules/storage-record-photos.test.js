/**
 * `storage.rules` for the four record-photo prefixes (L-4): `medical_photos/`, `pet_photos/`,
 * `receipts/` and `event_images/`, plus the closing deny-all.
 *
 * Every photo lives at `{prefix}/{owner}/{recordId}/{objectName}`, where `{owner}` is a family id
 * — `FamilyKey.of(uidA, uidB)`, the two parents' uids sorted and joined with `__` — or
 * `solo_{uid}` for a photo taken before its uploader had a family. So who may read, create and
 * delete is a property of the path, and every case below runs for real against the emulator, with
 * no cross-service call the emulator cannot resolve (firebase-js-sdk#6803).
 *
 * What these tests can and cannot do is worth stating: they prove the ruleset **in this
 * repository** behaves as written. They say nothing about what is deployed to the live bucket,
 * which only `firebase deploy --only storage` and a check against the console can settle — the
 * gap the `storage.rules` entry under "Known issues" in CLAUDE.md is about.
 */

const {ref, uploadBytes, getBytes, deleteObject, listAll} = require('firebase/storage');

const {
  storageTestEnv, assertSucceeds, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-storage';
const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const CAROL = 'carol-uid';
/** A guest, a calendar friend or a professional: signed in, never one of the pair. */
const GUEST = 'grandma-uid';
const FAMILY = `${ALICE}__${BOB}`;
const SOLO_ALICE = `solo_${ALICE}`;
const SHA = 'a'.repeat(64);

/** Under the cap, so size is never the reason a test fails. */
const SMALL = new Uint8Array(64).fill(7);

/**
 * Exactly the 10 MB cap, which the rule spells `size < 10 * 1024 * 1024` — so this must be
 * refused. A megabyte over would also fail and would not tell us whether the comparison is `<`.
 */
const AT_CAP = new Uint8Array(10 * 1024 * 1024).fill(7);

/**
 * Upload metadata as `FirebaseImageStorage` writes it.
 *
 * @param {string} uploader The uid stamped as the uploader.
 * @param {string=} contentType MIME type; JPEG by default.
 * @return {!Object} Storage upload metadata.
 */
function meta(uploader, contentType = 'image/jpeg') {
  return {contentType, customMetadata: {uploader, sha256: SHA}};
}

/**
 * The storage handle of a caller, or of an anonymous one when uid is null.
 *
 * @param {!Object} env Rules test environment.
 * @param {?string} uid Caller.
 * @return {!Object} A Storage instance.
 */
function storageOf(env, uid) {
  const ctx = uid ? env.authenticatedContext(uid) : env.unauthenticatedContext();
  return ctx.storage();
}

/**
 * Writes an object with the rules bypassed, stamped as uploaded by [uploader].
 *
 * @param {!Object} env Rules test environment.
 * @param {string} objectPath Path within the bucket.
 * @param {string} uploader The uid to stamp.
 * @return {!Promise<void>} Resolves once the object exists.
 */
async function seedObject(env, objectPath, uploader) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await uploadBytes(ref(ctx.storage(), objectPath), SMALL, meta(uploader));
  });
}

/**
 * A fresh object per test: an object uploaded through a user context survived `clearStorage()`
 * in the MON-23 suite, so a create rule requiring `resource == null` would fail every later test
 * on the same path. A path no earlier test used is the isolation that holds.
 */
let objectCounter = 0;

/**
 * A fresh path under [prefix] for [owner].
 *
 * @param {string} prefix One of the four record-photo prefixes.
 * @param {string} owner A family id or `solo_{uid}`.
 * @return {string} The path.
 */
function freshPath(prefix, owner) {
  objectCounter++;
  return `${prefix}/${owner}/record-${objectCounter}/photo-${objectCounter}.jpg`;
}

const PREFIXES = ['medical_photos', 'pet_photos', 'receipts', 'event_images'];

describe('storage.rules: record photos (L-4)', function() {
  let env;

  before(async function() {
    env = await storageTestEnv(PROJECT);
  });

  // The four blocks carry identical rules, so they are exercised identically. Per prefix rather
  // than one looped assertion, so a failure names the path that broke.
  for (const prefix of PREFIXES) {
    describe(`${prefix} in a family's folder`, function() {
      it('lets either parent upload a photo for their family', async function() {
        await assertSucceeds(
            uploadBytes(ref(storageOf(env, ALICE), freshPath(prefix, FAMILY)), SMALL, meta(ALICE)));
        await assertSucceeds(
            uploadBytes(ref(storageOf(env, BOB), freshPath(prefix, FAMILY)), SMALL, meta(BOB)));
      });

      for (const type of ['image/png', 'image/heic', 'image/heif', 'image/webp']) {
        it(`accepts ${type}`, async function() {
          await assertSucceeds(uploadBytes(
              ref(storageOf(env, ALICE), freshPath(prefix, FAMILY)), SMALL, meta(ALICE, type)));
        });
      }

      it('lets both parents read, and nobody else', async function() {
        const path = freshPath(prefix, FAMILY);
        await seedObject(env, path, ALICE);
        await assertSucceeds(getBytes(ref(storageOf(env, ALICE), path)));
        await assertSucceeds(getBytes(ref(storageOf(env, BOB), path)));
        await assertFails(getBytes(ref(storageOf(env, CAROL), path)));
        await assertFails(getBytes(ref(storageOf(env, GUEST), path)));
        await assertFails(getBytes(ref(storageOf(env, null), path)));
      });

      it('lets either parent delete it, and nobody else', async function() {
        const byBob = freshPath(prefix, FAMILY);
        await seedObject(env, byBob, ALICE);
        await assertFails(deleteObject(ref(storageOf(env, CAROL), byBob)));
        await assertFails(deleteObject(ref(storageOf(env, GUEST), byBob)));
        await assertFails(deleteObject(ref(storageOf(env, null), byBob)));
        await assertSucceeds(deleteObject(ref(storageOf(env, BOB), byBob)));

        const byAlice = freshPath(prefix, FAMILY);
        await seedObject(env, byAlice, ALICE);
        await assertSucceeds(deleteObject(ref(storageOf(env, ALICE), byAlice)));
      });

      it('refuses overwriting a stored photo, even by its uploader', async function() {
        const path = freshPath(prefix, FAMILY);
        await seedObject(env, path, ALICE);
        await assertFails(uploadBytes(ref(storageOf(env, ALICE), path), SMALL, meta(ALICE)));
        await assertFails(uploadBytes(ref(storageOf(env, BOB), path), SMALL, meta(BOB)));
      });

      it('refuses a stranger, a guest or nobody uploading into the family', async function() {
        await assertFails(
            uploadBytes(ref(storageOf(env, CAROL), freshPath(prefix, FAMILY)), SMALL, meta(CAROL)));
        await assertFails(
            uploadBytes(ref(storageOf(env, GUEST), freshPath(prefix, FAMILY)), SMALL, meta(GUEST)));
        await assertFails(
            uploadBytes(ref(storageOf(env, null), freshPath(prefix, FAMILY)), SMALL, meta(ALICE)));
      });

      it('refuses an upload without its stamp', async function() {
        await assertFails(uploadBytes(ref(storageOf(env, ALICE), freshPath(prefix, FAMILY)),
            SMALL, {contentType: 'image/jpeg'}));
        await assertFails(uploadBytes(ref(storageOf(env, ALICE), freshPath(prefix, FAMILY)),
            SMALL, {contentType: 'image/jpeg', customMetadata: {uploader: ALICE}}));
        await assertFails(uploadBytes(ref(storageOf(env, ALICE), freshPath(prefix, FAMILY)),
            SMALL, {contentType: 'image/jpeg', customMetadata: {sha256: SHA}}));
        await assertFails(uploadBytes(ref(storageOf(env, ALICE), freshPath(prefix, FAMILY)),
            SMALL, {contentType: 'image/jpeg', customMetadata: {uploader: ALICE, sha256: 'xyz'}}));
      });

      it('refuses an upload that names somebody else as the uploader', async function() {
        await assertFails(
            uploadBytes(ref(storageOf(env, ALICE), freshPath(prefix, FAMILY)), SMALL, meta(BOB)));
      });

      it('refuses a type that is not an image', async function() {
        for (const type of ['application/pdf', 'text/plain', 'video/mp4']) {
          await assertFails(uploadBytes(
              ref(storageOf(env, ALICE), freshPath(prefix, FAMILY)), SMALL, meta(ALICE, type)));
        }
      });

      it('refuses an empty upload and one at the 10 MB cap', async function() {
        await assertFails(uploadBytes(
            ref(storageOf(env, ALICE), freshPath(prefix, FAMILY)), new Uint8Array(0), meta(ALICE)));
        await assertFails(
            uploadBytes(ref(storageOf(env, ALICE), freshPath(prefix, FAMILY)), AT_CAP, meta(ALICE)));
      });

      it('refuses listing a family folder, even to its parents', async function() {
        const path = freshPath(prefix, FAMILY);
        await seedObject(env, path, ALICE);
        await assertFails(listAll(ref(storageOf(env, ALICE), `${prefix}/${FAMILY}`)));
      });

      // Recorded, not endorsed: see the header of `storage.rules`. The path is the whole gate, and
      // unpair does not change a path.
      it('still serves a photo to both uids after an unpair (the documented cost)', async function() {
        const path = freshPath(prefix, FAMILY);
        await seedObject(env, path, ALICE);
        await assertSucceeds(getBytes(ref(storageOf(env, BOB), path)));
      });
    });

    describe(`${prefix} in an uploader's own folder`, function() {
      it('lets the uploader upload, read and delete', async function() {
        const path = freshPath(prefix, SOLO_ALICE);
        await assertSucceeds(uploadBytes(ref(storageOf(env, ALICE), path), SMALL, meta(ALICE)));
        await assertSucceeds(getBytes(ref(storageOf(env, ALICE), path)));
        await assertSucceeds(deleteObject(ref(storageOf(env, ALICE), path)));
      });

      it('refuses everybody else, the future co-parent included', async function() {
        const path = freshPath(prefix, SOLO_ALICE);
        await seedObject(env, path, ALICE);
        await assertFails(getBytes(ref(storageOf(env, BOB), path)));
        await assertFails(getBytes(ref(storageOf(env, GUEST), path)));
        await assertFails(getBytes(ref(storageOf(env, null), path)));
        await assertFails(deleteObject(ref(storageOf(env, BOB), path)));
        await assertFails(
            uploadBytes(ref(storageOf(env, BOB), freshPath(prefix, SOLO_ALICE)), SMALL, meta(BOB)));
      });

      it('refuses overwriting a stored photo', async function() {
        const path = freshPath(prefix, SOLO_ALICE);
        await seedObject(env, path, ALICE);
        await assertFails(uploadBytes(ref(storageOf(env, ALICE), path), SMALL, meta(ALICE)));
      });

      it('refuses a folder that is not a pair and not the caller\'s own', async function() {
        await assertFails(
            uploadBytes(ref(storageOf(env, ALICE), freshPath(prefix, ALICE)), SMALL, meta(ALICE)));
        await assertFails(
            uploadBytes(ref(storageOf(env, ALICE), freshPath(prefix, 'solo_')), SMALL, meta(ALICE)));
        await assertFails(uploadBytes(
            ref(storageOf(env, ALICE), freshPath(prefix, `${CAROL}__${BOB}`)), SMALL, meta(ALICE)));
      });
    });

    describe(`${prefix} under the flat layouts from before L-4`, function() {
      // Every old object is removed by `purgeLegacyPhotoPaths`; until then nobody reads it, and
      // nothing new may be written there.
      const legacy = prefix === 'receipts' || prefix === 'event_images' ?
        [`${prefix}/record-1.jpg`] :
        [`${prefix}/record-1/photo-uuid.jpg`];
      for (const path of legacy) {
        it(`closes ${path} to everybody`, async function() {
          await assertFails(uploadBytes(ref(storageOf(env, ALICE), path), SMALL, meta(ALICE)));
          await seedObject(env, path, ALICE);
          await assertFails(getBytes(ref(storageOf(env, ALICE), path)));
          await assertFails(getBytes(ref(storageOf(env, BOB), path)));
          await assertFails(deleteObject(ref(storageOf(env, ALICE), path)));
        });
      }

      it('refuses a path one segment deeper than the block matches', async function() {
        await assertFails(uploadBytes(
            ref(storageOf(env, ALICE), `${prefix}/${FAMILY}/record-1/2026/photo.jpg`), SMALL, meta(ALICE)));
      });
    });
  }

  describe('everything else is closed', function() {
    it('refuses an upload to an unmatched prefix', async function() {
      await assertFails(uploadBytes(ref(storageOf(env, ALICE), 'avatars/alice.jpg'), SMALL, meta(ALICE)));
    });

    it('refuses a read from an unmatched prefix', async function() {
      await seedObject(env, 'avatars/alice.jpg', ALICE);
      await assertFails(getBytes(ref(storageOf(env, ALICE), 'avatars/alice.jpg')));
    });

    it('refuses the bucket root', async function() {
      await assertFails(uploadBytes(ref(storageOf(env, ALICE), 'stray.jpg'), SMALL, meta(ALICE)));
    });
  });
});
