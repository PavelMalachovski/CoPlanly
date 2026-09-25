/**
 * Record photographs on the server (L-4): a child's medical photos, a pet's photos, an expense's
 * receipt and an event's photo.
 *
 * The app stores each photo at `{prefix}/{owner}/{recordId}/{objectName}` in Cloud Storage and a
 * `ph1|{path}|{contentType}|{size}|{sha256}` reference on the record (`RecordPhotoCodec` in the
 * app — change both together). `{owner}` is the record's family id, or `solo_{uid}` while the
 * uploader had no co-parent. `storage.rules` decides who reads a photo from that segment alone.
 *
 * Three jobs live here:
 *
 * - [movePhotosToFamily] — when a pair forms, the photos a parent took alone are copied into the
 *   family's folder, the reference on the record rewritten, and the personal copy deleted. Run by
 *   `onFamilyCreated` and by `backfillRecordFamilyIds`, only for a family `stampOwnBlankFamilyIds`
 *   resolved without guessing.
 * - [recordPhotoFolders] / [soloPhotoFolders] — what account deletion and the tombstone sweep
 *   remove.
 * - [purgeLegacyPhotoPathsImpl] — the operator's one-off removal of every object under the flat
 *   layouts from before L-4, and of the download URLs and paths the records still carry.
 */

/** Marks a stored string as a photo reference; the `1` is the format version. */
const PHOTO_REF_PREFIX = 'ph1|';

/**
 * Where each record kind keeps its photos: the Storage prefix, the field on the Firestore
 * document, and whether that field is a list (`medicalPhotos`, `photos`) or a single string.
 */
const RECORD_PHOTO_LAYOUTS = {
  child_info: {prefix: 'medical_photos', field: 'medicalPhotos', list: true},
  pets: {prefix: 'pet_photos', field: 'photos', list: true},
  expenses: {prefix: 'receipts', field: 'receiptUrl', list: false},
  events: {prefix: 'event_images', field: 'imageUrl', list: false},
};

/** The owner segment of a personal folder is this followed by the uploader's uid. */
const SOLO_PREFIX = 'solo_';

/** The image types a record photo may be — `RecordPhotoPolicy` and `storage.rules` agree. */
const PHOTO_CONTENT_TYPES = ['image/jpeg', 'image/png', 'image/heic', 'image/heif', 'image/webp'];

/** Exclusive size cap, as the rule spells it. */
const PHOTO_MAX_BYTES = 10 * 1024 * 1024;

/** Firestore caps a batched write at 500 operations; stay clear of the edge. */
const PHOTO_BATCH_LIMIT = 400;

/**
 * Whether [owner] is a family id: two non-empty uids joined with `__`.
 *
 * @param {string} owner A path's owner segment.
 * @return {boolean} True for a family id.
 */
function isFamilyOwner(owner) {
  const parts = owner.split('__');
  return parts.length === 2 && parts.every((p) => p !== '');
}

/**
 * The parts of a record-photo path, or null when it is not one.
 *
 * @param {string} path A Storage path.
 * @return {?{prefix: string, owner: string, recordId: string, objectName: string}} The parts.
 */
function parsePhotoPath(path) {
  if (typeof path !== 'string') return null;
  const parts = path.split('/');
  if (parts.length !== 4 || parts.some((p) => p === '')) return null;
  const [prefix, owner, recordId, objectName] = parts;
  if (!Object.values(RECORD_PHOTO_LAYOUTS).some((l) => l.prefix === prefix)) return null;
  const soloUid = owner.startsWith(SOLO_PREFIX) ? owner.slice(SOLO_PREFIX.length) : null;
  const validOwner = soloUid !== null ?
    soloUid !== '' && !soloUid.includes('__') :
    isFamilyOwner(owner);
  return validOwner ? {prefix, owner, recordId, objectName} : null;
}

/**
 * The photo a stored reference names, or null for anything else — a legacy download URL, a flat
 * path, a blank or a malformed string.
 *
 * @param {*} value A stored reference.
 * @return {?{path: string, contentType: string, size: number, sha256: string}} The photo.
 */
function decodePhotoRef(value) {
  if (typeof value !== 'string' || !value.startsWith(PHOTO_REF_PREFIX)) return null;
  const parts = value.slice(PHOTO_REF_PREFIX.length).split('|');
  if (parts.length !== 4) return null;
  const [path, contentType, sizeText, sha256] = parts;
  if (!/^[0-9]+$/.test(sizeText)) return null;
  const size = Number(sizeText);
  if (parsePhotoPath(path) === null ||
      !PHOTO_CONTENT_TYPES.includes(contentType) ||
      size <= 0 || size >= PHOTO_MAX_BYTES ||
      !/^[0-9a-f]{64}$/.test(sha256)) {
    return null;
  }
  return {path, contentType, size, sha256};
}

/**
 * The stored string for [photo].
 *
 * @param {{path: string, contentType: string, size: number, sha256: string}} photo The photo.
 * @return {string} Its reference.
 */
function encodePhotoRef(photo) {
  return PHOTO_REF_PREFIX + [photo.path, photo.contentType, String(photo.size), photo.sha256].join('|');
}

/**
 * Every string the record's photo field holds, in order.
 *
 * @param {{field: string, list: boolean}} layout The collection's layout.
 * @param {!Object} data The record.
 * @return {!Array<string>} The stored strings; empty when there are none.
 */
function photoValuesOf(layout, data) {
  const value = data[layout.field];
  if (layout.list) {
    return Array.isArray(value) ? value.filter((v) => typeof v === 'string') : [];
  }
  return typeof value === 'string' && value !== '' ? [value] : [];
}

/**
 * The folders a record's photos can be in: its family's, and its creator's personal one.
 *
 * Folders rather than the objects its references name, so a photo the record no longer names —
 * a replaced receipt whose delete failed, an upload whose record never saved — goes too.
 *
 * @param {string} collection The record's collection.
 * @param {string} id The record's id.
 * @param {!Object} data The record.
 * @return {!Array<string>} Folder prefixes, each ending in `/`; empty for a collection with no
 *     photos or a record naming neither a family nor a creator.
 */
function recordPhotoFolders(collection, id, data) {
  const layout = RECORD_PHOTO_LAYOUTS[collection];
  if (!layout || typeof id !== 'string' || id === '' || id.includes('/')) return [];
  const folders = [];
  const familyId = data && data.familyId;
  if (typeof familyId === 'string' && isFamilyOwner(familyId)) {
    folders.push(`${layout.prefix}/${familyId}/${id}/`);
  }
  const creator = data && data.createdByFirebaseUid;
  if (typeof creator === 'string' && creator !== '' && !creator.includes('/')) {
    folders.push(`${layout.prefix}/${SOLO_PREFIX}${creator}/${id}/`);
  }
  return folders;
}

/**
 * Every personal photo folder [uid] can own, one per prefix — what an erased account leaves in
 * `solo_` folders, whether or not a record still names it.
 *
 * @param {string} uid The account.
 * @return {!Array<string>} Folder prefixes, each ending in `/`.
 */
function soloPhotoFolders(uid) {
  if (typeof uid !== 'string' || uid === '' || uid.includes('/')) return [];
  return Object.values(RECORD_PHOTO_LAYOUTS).map((l) => `${l.prefix}/${SOLO_PREFIX}${uid}/`);
}

/**
 * Whether an object exists in [bucket].
 *
 * @param {!Object} bucket A `@google-cloud/storage` bucket.
 * @param {string} path The object's path.
 * @return {!Promise<boolean>} True when it exists.
 */
async function objectExists(bucket, path) {
  const [exists] = await bucket.file(path).exists();
  return exists === true;
}

/**
 * Moves [uid]'s personal photos into [familyId]'s folder — the photos a parent took while they
 * had no co-parent, on their own records that now name that family.
 *
 * **It never guesses a family.** The caller passes the family `stampOwnBlankFamilyIds` resolved
 * for this person, which it does only for exactly one live, mutual co-parent and no trace of an
 * earlier relationship; and only a record whose own `familyId` is that family is touched, so a
 * record left blank for a reason is left alone, photos included.
 *
 * For each reference naming `{prefix}/solo_{uid}/{recordId}/{name}` on the record `{recordId}`:
 * the object is copied to `{prefix}/{familyId}/{recordId}/{name}` (a copy keeps the object's
 * metadata, so `uploader` and `sha256` travel with it), the reference is rewritten to the new path
 * — only that field is written — and then the personal copy is deleted. **Idempotent**: a copy
 * already made is not made again, a reference already rewritten is not touched, and a run that
 * died between the copy and the delete finishes the delete next time. A reference whose object is
 * in neither place is left as it is and counted as `missing`.
 *
 * The app resolves a `solo_` reference on a family's record to the family path by itself (see
 * `RecordPhotoPaths.candidatesFor`), so a phone that writes its stale reference back afterwards
 * still shows the photo to both parents.
 *
 * @param {FirebaseFirestore.Firestore} db Firestore instance.
 * @param {?Object} bucket The Storage bucket, or null to do nothing (tests without Storage).
 * @param {string} uid The uploader, whose own records are moved.
 * @param {string} familyId The family those records name.
 * @return {!Promise<{moved: number, rewritten: number, missing: number}>} Photos moved, records
 *     whose references were rewritten, and references whose object was nowhere.
 */
async function movePhotosToFamily(db, bucket, uid, familyId) {
  const result = {moved: 0, rewritten: 0, missing: 0};
  if (!bucket || typeof uid !== 'string' || uid === '' ||
      typeof familyId !== 'string' || !isFamilyOwner(familyId) ||
      !familyId.split('__').includes(uid)) {
    return result;
  }
  const soloOwner = `${SOLO_PREFIX}${uid}`;
  for (const [collection, layout] of Object.entries(RECORD_PHOTO_LAYOUTS)) {
    const snap = await db.collection(collection).where('createdByFirebaseUid', '==', uid).get();
    for (const doc of snap.docs) {
      const data = doc.data() || {};
      if (data.familyId !== familyId) continue;
      const values = photoValuesOf(layout, data);
      const next = [];
      const leftovers = [];
      let changed = false;
      for (const value of values) {
        const photo = decodePhotoRef(value);
        const parsed = photo && parsePhotoPath(photo.path);
        if (!parsed || parsed.prefix !== layout.prefix || parsed.owner !== soloOwner ||
            parsed.recordId !== doc.id) {
          next.push(value);
          continue;
        }
        const target = `${layout.prefix}/${familyId}/${doc.id}/${parsed.objectName}`;
        const hereAlready = await objectExists(bucket, target);
        const stillSolo = await objectExists(bucket, photo.path);
        if (!hereAlready && !stillSolo) {
          next.push(value);
          result.missing++;
          continue;
        }
        if (!hereAlready) {
          await bucket.file(photo.path).copy(bucket.file(target));
          result.moved++;
        }
        if (stillSolo) leftovers.push(photo.path);
        next.push(encodePhotoRef(Object.assign({}, photo, {path: target})));
        changed = true;
      }
      if (changed) {
        const batch = db.batch();
        batch.update(doc.ref, {[layout.field]: layout.list ? next : next[0]});
        await batch.commit();
        result.rewritten++;
      }
      // After the record names the family's copy, never before: a record must not name an object
      // that is gone.
      for (const path of leftovers) {
        await bucket.file(path).delete({ignoreNotFound: true});
      }
    }
  }
  return result;
}

/**
 * Whether a stored value is a legacy photo value — a download URL or a flat path from before L-4 —
 * rather than a reference.
 *
 * @param {*} value A stored string.
 * @return {boolean} True for a non-blank string that is not a `ph1|` reference.
 */
function isLegacyPhotoValue(value) {
  return typeof value === 'string' && value !== '' && decodePhotoRef(value) === null;
}

/**
 * Whether an object path is under a flat layout from before L-4, and so matches no rule block:
 * anything under a record-photo prefix that is not a well-formed `{owner}/{recordId}/{name}` path.
 *
 * @param {string} name The object's path.
 * @return {boolean} True for a legacy object.
 */
function isLegacyPhotoObject(name) {
  const prefix = name.split('/')[0];
  if (!Object.values(RECORD_PHOTO_LAYOUTS).some((l) => l.prefix === prefix)) return false;
  return parsePhotoPath(name) === null;
}

/**
 * Body of the `purgeLegacyPhotoPaths` callable — removes what the photo layouts before L-4 left
 * behind. An owner decision: before release every one of them is test data, so they are deleted,
 * not migrated.
 *
 * Two passes. **Objects**: every object under the four prefixes whose path is not a current
 * `{owner}/{recordId}/{name}` path — `receipts/{id}.jpg`, `event_images/{id}.jpg`,
 * `medical_photos/{childId}/{photoId}.jpg`, `pet_photos/{petId}/{photoId}.jpg` — is deleted. No
 * rule block matches those paths any more, so no client can reach them either way. **Records**:
 * every legacy value (a download URL or a flat path) is removed from `medicalPhotos`/`photos` and
 * blanked from `receiptUrl`/`imageUrl`; references in the current format are kept. Only that
 * field is written, and the tombstone fields, `updatedAt` and every other field are left alone,
 * so no conflict comparison on a phone changes its answer.
 *
 * Idempotent: a second run finds nothing to delete and nothing to clear.
 *
 * @param {FirebaseFirestore.Firestore} db Firestore instance.
 * @param {?Object} bucket The Storage bucket, or null to skip the object pass.
 * @return {!Promise<{objectsDeleted: number, recordsCleared: number,
 *     perCollection: !Object<string, number>}>} What was removed.
 */
async function purgeLegacyPhotoPathsImpl(db, bucket) {
  const summary = {objectsDeleted: 0, recordsCleared: 0, perCollection: {}};
  if (bucket) {
    for (const layout of Object.values(RECORD_PHOTO_LAYOUTS)) {
      const [files] = await bucket.getFiles({prefix: `${layout.prefix}/`});
      for (const file of files) {
        if (!isLegacyPhotoObject(file.name)) continue;
        await file.delete({ignoreNotFound: true});
        summary.objectsDeleted++;
      }
    }
  }
  for (const [collection, layout] of Object.entries(RECORD_PHOTO_LAYOUTS)) {
    summary.perCollection[collection] = 0;
    const snap = await db.collection(collection).get();
    let batch = db.batch();
    let pending = 0;
    for (const doc of snap.docs) {
      const values = photoValuesOf(layout, doc.data() || {});
      if (!values.some(isLegacyPhotoValue)) continue;
      const kept = values.filter((v) => !isLegacyPhotoValue(v));
      batch.update(doc.ref, {[layout.field]: layout.list ? kept : (kept[0] || '')});
      pending++;
      summary.recordsCleared++;
      summary.perCollection[collection]++;
      if (pending === PHOTO_BATCH_LIMIT) {
        await batch.commit();
        batch = db.batch();
        pending = 0;
      }
    }
    if (pending > 0) await batch.commit();
  }
  return summary;
}

module.exports = {
  PHOTO_REF_PREFIX,
  RECORD_PHOTO_LAYOUTS,
  SOLO_PREFIX,
  parsePhotoPath,
  decodePhotoRef,
  encodePhotoRef,
  recordPhotoFolders,
  soloPhotoFolders,
  movePhotosToFamily,
  isLegacyPhotoObject,
  purgeLegacyPhotoPathsImpl,
};
