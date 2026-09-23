/**
 * The `family_documents` block — the document vault (MON-23).
 *
 * A vault document is the index of one file in Cloud Storage: who put it there, for which
 * family, what it is, and the SHA-256 of its bytes. It is shared by definition — there is no
 * private document — so the audience is the family's two parents and nothing narrower or wider.
 *
 * What these cases pin, in the order a hostile writer would try them:
 * - a stranger cannot file a document *into* a family (item 23's audience bound, plus the
 *   family id itself naming the two uids);
 * - a parent cannot hand one to somebody outside the family, even a co-parent from another
 *   family;
 * - the file reference is derived, not free: `storagePath` must be the one the Storage rule
 *   gates, under this family and this document id;
 * - after creation nothing but the title, the category and the tombstone may move, only the
 *   uploader may move them, and a document is never removed by a client (item 14).
 */

const {
  CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-family-documents';
const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const CAROL = 'carol-uid';
const FAMILY = `${ALICE}__${BOB}`;
const SHA = 'a'.repeat(64);

/**
 * Builds a vault document as `FamilyDocumentMapper.toFirestoreMap` writes it.
 *
 * @param {!Object} overrides Fields to override on the default document.
 * @return {!Object} The document data.
 */
function vaultDoc(overrides) {
  const base = {
    id: 'doc-1',
    familyId: FAMILY,
    createdByFirebaseUid: ALICE,
    sharedWith: [ALICE, BOB],
    title: 'Custody order',
    category: 'court_order',
    fileName: 'order.pdf',
    storagePath: `family_documents/${FAMILY}/doc-1/order.pdf`,
    contentType: 'application/pdf',
    sizeBytes: 12345,
    sha256: SHA,
    createdAtMillis: 1790000000000,
  };
  return Object.assign(base, overrides);
}

describe('family_documents', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, {
      'users/alice-uid': {name: 'Alice', partnerId: BOB, partnerIds: [BOB, CAROL]},
      'users/bob-uid': {name: 'Bob', partnerId: ALICE, partnerIds: [ALICE]},
      'users/carol-uid': {name: 'Carol', partnerId: ALICE, partnerIds: [ALICE]},
    });
  });

  const as = (uid) => env.authenticatedContext(uid).firestore();

  describe('read', () => {
    it('lets both parents of the family read it', async () => {
      await seed(env, {'family_documents/doc-1': vaultDoc({})});
      await assertSucceeds(as(ALICE).doc('family_documents/doc-1').get());
      await assertSucceeds(as(BOB).doc('family_documents/doc-1').get());
    });

    it('denies a co-parent from another family', async () => {
      await seed(env, {'family_documents/doc-1': vaultDoc({})});
      await assertFails(as(CAROL).doc('family_documents/doc-1').get());
    });

    it('serves the list query the vault screen runs', async () => {
      await seed(env, {'family_documents/doc-1': vaultDoc({})});
      await assertSucceeds(as(BOB).collection('family_documents')
          .where('sharedWith', 'array-contains', BOB)
          .where('familyId', '==', FAMILY)
          .get());
    });

    it('refuses an unfiltered list query', async () => {
      await seed(env, {'family_documents/doc-1': vaultDoc({})});
      await assertFails(as(BOB).collection('family_documents').get());
    });
  });

  describe('create', () => {
    it('allows a parent to file a document for their family', async () => {
      await assertSucceeds(as(ALICE).doc('family_documents/doc-1').set(vaultDoc({})));
    });

    it('allows the co-parent to file one too', async () => {
      await assertSucceeds(as(BOB).doc('family_documents/doc-1').set(vaultDoc({
        createdByFirebaseUid: BOB,
      })));
    });

    it('refuses a stranger filing into a family they are not in', async () => {
      await assertFails(as(CAROL).doc('family_documents/doc-1').set(vaultDoc({
        createdByFirebaseUid: CAROL,
        sharedWith: [CAROL, ALICE, BOB],
      })));
    });

    it('refuses a document naming somebody else as its creator', async () => {
      await assertFails(as(BOB).doc('family_documents/doc-1').set(vaultDoc({})));
    });

    it('refuses an audience wider than the family, even a co-parent of the writer', async () => {
      await assertFails(as(ALICE).doc('family_documents/doc-1').set(vaultDoc({
        sharedWith: [ALICE, BOB, CAROL],
      })));
    });

    it('refuses an audience that leaves the writer out', async () => {
      await assertFails(as(ALICE).doc('family_documents/doc-1').set(vaultDoc({
        sharedWith: [BOB],
      })));
    });

    it('refuses a blank family: a vault document is shared by definition', async () => {
      await assertFails(as(ALICE).doc('family_documents/doc-1').set(vaultDoc({
        familyId: '',
        sharedWith: [ALICE],
        storagePath: 'family_documents//doc-1/order.pdf',
      })));
    });

    it('refuses a storage path outside this family and this document', async () => {
      await assertFails(as(ALICE).doc('family_documents/doc-1').set(vaultDoc({
        storagePath: `family_documents/${ALICE}__${CAROL}/doc-1/order.pdf`,
      })));
      await assertFails(as(ALICE).doc('family_documents/doc-1').set(vaultDoc({
        storagePath: `family_documents/${FAMILY}/doc-2/order.pdf`,
      })));
    });

    it('refuses a content type the Storage rule would refuse', async () => {
      await assertFails(as(ALICE).doc('family_documents/doc-1').set(vaultDoc({
        contentType: 'application/zip',
      })));
    });

    it('refuses a size over the 20 MB cap', async () => {
      await assertFails(as(ALICE).doc('family_documents/doc-1').set(vaultDoc({
        sizeBytes: 20 * 1024 * 1024,
      })));
    });

    it('refuses a digest that is not 64 lowercase hex digits', async () => {
      await assertFails(as(ALICE).doc('family_documents/doc-1').set(vaultDoc({
        sha256: 'ABC',
      })));
    });

    it('refuses an unknown category', async () => {
      await assertFails(as(ALICE).doc('family_documents/doc-1').set(vaultDoc({
        category: 'secret',
      })));
    });

    it('refuses a document born as a tombstone', async () => {
      await assertFails(as(ALICE).doc('family_documents/doc-1').set(vaultDoc({
        deletedAtMillis: 1790000000000,
        deletedBy: ALICE,
      })));
    });

    it('refuses a field the vault does not define', async () => {
      await assertFails(as(ALICE).doc('family_documents/doc-1').set(vaultDoc({
        downloadUrl: 'https://example.test/token',
      })));
    });
  });

  describe('update and delete', () => {
    beforeEach(async () => {
      await seed(env, {'family_documents/doc-1': vaultDoc({})});
    });

    it('lets the uploader rename and re-file it', async () => {
      await assertSucceeds(as(ALICE).doc('family_documents/doc-1').update({
        title: 'Custody order (2026)', category: 'other',
      }));
    });

    it('lets the uploader tombstone it', async () => {
      await assertSucceeds(as(ALICE).doc('family_documents/doc-1').update({
        deletedAtMillis: 1790000001000, deletedBy: ALICE,
      }));
    });

    it('refuses a tombstone that names somebody else as the deleter', async () => {
      await assertFails(as(ALICE).doc('family_documents/doc-1').update({
        deletedAtMillis: 1790000001000, deletedBy: BOB,
      }));
    });

    it('refuses the co-parent any edit, including a tombstone', async () => {
      await assertFails(as(BOB).doc('family_documents/doc-1').update({title: 'Mine now'}));
      await assertFails(as(BOB).doc('family_documents/doc-1').update({
        deletedAtMillis: 1790000001000, deletedBy: BOB,
      }));
    });

    it('refuses repointing the file or rewriting its digest', async () => {
      await assertFails(as(ALICE).doc('family_documents/doc-1').update({
        storagePath: `family_documents/${FAMILY}/doc-1/other.pdf`,
      }));
      await assertFails(as(ALICE).doc('family_documents/doc-1').update({sha256: 'b'.repeat(64)}));
    });

    it('refuses widening the audience', async () => {
      await assertFails(as(ALICE).doc('family_documents/doc-1').update({
        sharedWith: [ALICE, BOB, CAROL],
      }));
    });

    it('refuses a hard delete from any client', async () => {
      await assertFails(as(ALICE).doc('family_documents/doc-1').delete());
      await assertFails(as(BOB).doc('family_documents/doc-1').delete());
    });
  });
});
