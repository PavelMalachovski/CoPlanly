/**
 * `storage.rules` for the two MON-23 prefixes: `family_documents/` (the vault) and
 * `chat_attachments/` (files sent in chat).
 *
 * Unlike every older block in that file, these two tell one signed-in user from another — and
 * they do it without the cross-service `firestore.get()` this emulator cannot resolve
 * (firebase-js-sdk#6803). The first path segment is a family id, `FamilyKey.of(uidA, uidB)`,
 * which *is* the two parents' uids sorted and joined with `__`; a conversation id is the same
 * string (`ConversationKey`). So membership is a property of the path, the same reasoning
 * `firestore.rules`' `isFamilyMember` and `custody_models`' `allow get` apply, and every case
 * below runs for real against the emulator.
 *
 * The cost is stated in `storage.rules` and pinned by the "after unpair" cases here: the path
 * names the pair for ever, so an ex-partner who kept a path can still fetch that file. The
 * vault's Firestore index narrows at unpair; the bytes do not.
 */

const {ref, uploadBytes, getBytes, deleteObject, listAll} = require('firebase/storage');

const {
  storageTestEnv, assertSucceeds, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-storage-files';
const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const CAROL = 'carol-uid';
const FAMILY = `${ALICE}__${BOB}`;
const SHA = 'c'.repeat(64);

const SMALL = new Uint8Array(64).fill(7);

/** Exactly the 20 MB cap, which the rule excludes with `<`. */
const AT_CAP = new Uint8Array(20 * 1024 * 1024).fill(7);

/**
 * Upload metadata as `SharedFileUploader` writes it.
 *
 * @param {string} uploader The uid stamped as the uploader.
 * @param {string=} contentType MIME type; PDF by default.
 * @return {!Object} Storage upload metadata.
 */
function meta(uploader, contentType = 'application/pdf') {
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
 * A fresh object id per test.
 *
 * Not `clearStorage()` between tests: verified here, an object uploaded through a user context
 * survived it, so a create rule that requires `resource == null` failed every test after the
 * first to touch a path. A path no earlier test used is the one isolation that holds.
 */
let objectCounter = 0;

describe('storage.rules: family_documents', function() {
  let env;
  let path;

  before(async function() {
    env = await storageTestEnv(PROJECT);
  });

  beforeEach(function() {
    objectCounter++;
    path = `family_documents/${FAMILY}/doc-${objectCounter}/order.pdf`;
  });

  it('lets either parent upload a PDF for their family', async function() {
    await assertSucceeds(uploadBytes(ref(storageOf(env, ALICE), path), SMALL, meta(ALICE)));
    const second = `family_documents/${FAMILY}/doc-${objectCounter}-b/order.pdf`;
    await assertSucceeds(uploadBytes(ref(storageOf(env, BOB), second), SMALL, meta(BOB)));
  });

  for (const type of ['image/jpeg', 'image/png', 'image/heic', 'image/heif', 'image/webp']) {
    it(`accepts ${type}`, async function() {
      await assertSucceeds(
          uploadBytes(ref(storageOf(env, ALICE), path), SMALL, meta(ALICE, type)));
    });
  }

  it('refuses a content type outside the list', async function() {
    await assertFails(uploadBytes(ref(storageOf(env, ALICE), path), SMALL,
        meta(ALICE, 'application/zip')));
  });

  it('refuses an upload at the 20 MB cap', async function() {
    await assertFails(uploadBytes(ref(storageOf(env, ALICE), path), AT_CAP, meta(ALICE)));
  });

  it('refuses an upload that names somebody else as the uploader', async function() {
    await assertFails(uploadBytes(ref(storageOf(env, ALICE), path), SMALL, meta(BOB)));
  });

  it('refuses an upload with no digest', async function() {
    await assertFails(uploadBytes(ref(storageOf(env, ALICE), path), SMALL,
        {contentType: 'application/pdf', customMetadata: {uploader: ALICE}}));
  });

  it('refuses a stranger uploading into the family', async function() {
    await assertFails(uploadBytes(ref(storageOf(env, CAROL), path), SMALL, meta(CAROL)));
  });

  it('refuses a path whose first segment is not a pair', async function() {
    const odd = `family_documents/${ALICE}/doc-1/order.pdf`;
    await assertFails(uploadBytes(ref(storageOf(env, ALICE), odd), SMALL, meta(ALICE)));
  });

  it('lets both parents read, and nobody else', async function() {
    await seedObject(env, path, ALICE);
    await assertSucceeds(getBytes(ref(storageOf(env, ALICE), path)));
    await assertSucceeds(getBytes(ref(storageOf(env, BOB), path)));
    await assertFails(getBytes(ref(storageOf(env, CAROL), path)));
    await assertFails(getBytes(ref(storageOf(env, null), path)));
  });

  it('refuses listing a family folder, even to its parents', async function() {
    await seedObject(env, path, ALICE);
    await assertFails(listAll(ref(storageOf(env, ALICE), `family_documents/${FAMILY}/doc-1`)));
  });

  it('refuses overwriting a stored file, even by its uploader', async function() {
    await seedObject(env, path, ALICE);
    await assertFails(uploadBytes(ref(storageOf(env, ALICE), path), SMALL, meta(ALICE)));
    await assertFails(uploadBytes(ref(storageOf(env, BOB), path), SMALL, meta(BOB)));
  });

  it('lets only the uploader delete it', async function() {
    await seedObject(env, path, ALICE);
    await assertFails(deleteObject(ref(storageOf(env, BOB), path)));
    await assertFails(deleteObject(ref(storageOf(env, CAROL), path)));
    await assertSucceeds(deleteObject(ref(storageOf(env, ALICE), path)));
  });
});

describe('storage.rules: chat_attachments', function() {
  let env;
  let path;

  before(async function() {
    env = await storageTestEnv(PROJECT);
  });

  beforeEach(function() {
    objectCounter++;
    path = `chat_attachments/${FAMILY}/msg-${objectCounter}/photo.jpg`;
  });

  it('lets a participant upload an image or a PDF', async function() {
    await assertSucceeds(
        uploadBytes(ref(storageOf(env, ALICE), path), SMALL, meta(ALICE, 'image/jpeg')));
    const pdf = `chat_attachments/${FAMILY}/msg-${objectCounter}-b/report.pdf`;
    await assertSucceeds(uploadBytes(ref(storageOf(env, BOB), pdf), SMALL, meta(BOB)));
  });

  it('refuses an outsider uploading into the thread', async function() {
    await assertFails(
        uploadBytes(ref(storageOf(env, CAROL), path), SMALL, meta(CAROL, 'image/jpeg')));
  });

  it('refuses a content type outside the list', async function() {
    await assertFails(
        uploadBytes(ref(storageOf(env, ALICE), path), SMALL, meta(ALICE, 'video/mp4')));
  });

  it('lets both participants read, and nobody else', async function() {
    await seedObject(env, path, ALICE);
    await assertSucceeds(getBytes(ref(storageOf(env, ALICE), path)));
    await assertSucceeds(getBytes(ref(storageOf(env, BOB), path)));
    await assertFails(getBytes(ref(storageOf(env, CAROL), path)));
  });

  // The chat is a record: a message cannot be edited or deleted (`firestore.rules`), so the
  // file it carries cannot be either. Only account deletion removes one, as admin.
  it('refuses overwrite and delete to everybody, the uploader included', async function() {
    await seedObject(env, path, ALICE);
    await assertFails(
        uploadBytes(ref(storageOf(env, ALICE), path), SMALL, meta(ALICE, 'image/jpeg')));
    await assertFails(deleteObject(ref(storageOf(env, ALICE), path)));
    await assertFails(deleteObject(ref(storageOf(env, BOB), path)));
  });

  // Recorded, not endorsed: see the file header. The path is the whole gate, and unpair does not
  // change a path.
  it('still serves a file to both uids after an unpair (the documented cost)', async function() {
    await seedObject(env, path, ALICE);
    await assertSucceeds(getBytes(ref(storageOf(env, BOB), path)));
  });
});
