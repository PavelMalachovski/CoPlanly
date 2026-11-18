#!/usr/bin/env node
/**
 * Seeds the family the iPhone shell's UI test signs in to (MON-24 phase 3, Task 8): two accounts on
 * the local Firebase emulators, paired through the real `acceptPairingInvitation`, and prints the
 * first parent's e-mail address and password as JSON on stdout for the test to sign in with, with
 * their uid and the family's id (`uid`, `familyId`), which the push test addresses its pushes to.
 *
 * Run inside `firebase emulators:exec --only auth,firestore,functions,storage --project
 * demo-coplanly`, like `tools/e2e/pairing-smoke.js`, whose helpers it uses: every write goes through
 * the REST API with the parent's own token, so `firestore.rules` holds it as it holds the app.
 *
 * Each profile is the one `ensureProfile` writes, plus the two answers a parent gives once and the
 * first screens would otherwise ask for: `onboardingCompletedAt` (the wizard has run) and
 * `parentRole` (Home's stickers question, CLAUDE.md item 47). Loopback only; nothing reaches a real
 * project.
 *
 * With `--tour` (MON-24 phase 3, part B, Task 13: the iPhone tour, `ShellUITests.testTour`) it also
 * writes, as the first parent, the records a screen needs before the tour can open it: an event
 * today (Edit event and, from its form, Request change), a pending change request about it addressed
 * to the co-parent (the Requests inbox's "Waiting for Bob", which asks nothing of the parent signed
 * in), a child (Child information, the child's page and its editor), a pet (Pets and its editor), an
 * expense (Edit expense) and a vault document's index row (Documents; its file is not uploaded, and
 * the tour never opens it). Each is the document Android writes to that collection: the
 * `app/src/test/resources/wire/current/` fixture of its writer (events, child_info, pets, expenses)
 * or the shape `tools/ios/check-ios-writes.js` holds for one with none (change_requests), and, for
 * the vault, `FamilyDocumentMapper.toFirestoreMap`. A private journal entry is not seeded: it never
 * leaves the phone (CLAUDE.md item 34), so the tour opens the journal empty. A record that cannot be
 * written is reported on stderr as `COPLANLY_TOUR_SEED skipped …` and the rest are written: the
 * tour then records the screens it could not reach, as it records every other skip. The walk
 * (`shell-test`) seeds without the flag, so its screens are the ones it was written against.
 */

'use strict';

const smoke = require('../e2e/pairing-smoke.js');
const {toFields, signUp, writeInvitation, functionsHost, DOCS, PROJECT} = smoke;

/** How long a call waits before its one retry, in milliseconds. */
const RETRY_AFTER_MS = 5000;

/**
 * Whether a failed call is worth one retry: an emulator still loading its functions answers a
 * callable with 404 "does not exist", and one not yet listening refuses the connection (the error
 * then carries no HTTP status). Any other answer is the call's own and is not retried.
 *
 * @param {Error} error The failure `call` threw.
 * @return {boolean} Whether to retry.
 */
function retryable(error) {
  const message = String(error && error.message);
  if (/ -> 404: .*does not exist/s.test(message)) return true;
  return !/ -> \d{3}: /.test(message);
}

/**
 * `pairing-smoke.js`' `call`, retried once after five seconds when the emulators were not ready
 * ([retryable]): on a slow macOS runner the Functions emulator can still be loading when the seed
 * starts.
 *
 * @param {string} url Absolute URL.
 * @param {Object} options The request, as `call` takes it.
 * @return {Promise<Object>} The parsed response.
 */
async function call(url, options) {
  try {
    return await smoke.call(url, options);
  } catch (error) {
    if (!retryable(error)) throw error;
    console.error(`Retrying once in ${RETRY_AFTER_MS / 1000} s: ${error.message}`);
    await new Promise((resolve) => setTimeout(resolve, RETRY_AFTER_MS));
    return smoke.call(url, options);
  }
}

/** The password `signUp` gives every account. */
const PASSWORD = 'e2e-password-1';

/**
 * Writes a parent's profile as a parent who has finished onboarding.
 *
 * @param {{uid: string, token: string, email: string, name: string}} parent The account.
 * @param {string} role Their `parentRole`: "mom", "dad" or "parent".
 */
async function writeFinishedProfile(parent, role) {
  await call(`${DOCS}/users/${parent.uid}`, {
    method: 'PATCH',
    token: parent.token,
    body: toFields({
      name: parent.name,
      email: parent.email,
      id: parent.uid,
      firebaseUid: parent.uid,
      onboardingCompletedAt: '2026-11-01T09:00:00',
      parentRole: role,
    }),
  });
}

/** A number Firestore stores as a double, where `toValue` would write an integer. */
class Double {
  /** @param {number} value The value. */
  constructor(value) {
    this.value = value;
  }
}

/**
 * Converts a JS value into a Firestore REST value, nested lists and maps included.
 *
 * @param {*} value A string, an integer, a [Double], a boolean, null, an array or a plain object.
 * @return {Object} The REST value.
 */
function toValue(value) {
  if (value === null) return {nullValue: null};
  if (value instanceof Double) return {doubleValue: value.value};
  if (typeof value === 'boolean') return {booleanValue: value};
  if (typeof value === 'number') return {integerValue: String(value)};
  if (Array.isArray(value)) return {arrayValue: value.length ? {values: value.map(toValue)} : {}};
  if (typeof value === 'object') return {mapValue: {fields: documentFields(value)}};
  return {stringValue: String(value)};
}

/** The REST `fields` of a document. */
function documentFields(data) {
  return Object.fromEntries(Object.entries(data).map(([key, value]) => [key, toValue(value)]));
}

/** A day and a wall clock as the app's ISO text: `2026-11-14T18:00:00`, in this host's zone. */
function wallClock(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T` +
      `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

/** The title of the event the tour opens, which `ShellUITests.testTour` looks for. */
const TOUR_EVENT_TITLE = 'Tour dentist';

/**
 * The records the tour needs, written as [alice] into the family she shares with [bob]. Best
 * effort: a refused write is reported and the next is written.
 *
 * The simulator runs on this host, so "today" here is the simulator's today too.
 *
 * @param {string} aliceSlot Alice's parent slot in the family ("mom" or "dad"), which an event's
 *     `parentOwner` names as `EventRepositoryImpl` does for the creator's own event.
 */
async function seedTourRecords(alice, bob, aliceSlot) {
  const familyId = [alice.uid, bob.uid].sort().join('__');
  const audience = [alice.uid, bob.uid];
  const now = new Date();
  const created = wallClock(now);
  const day = (offset, hour) => {
    const date = new Date(now);
    date.setDate(date.getDate() + offset);
    date.setHours(hour, 0, 0, 0);
    return wallClock(date);
  };
  const eventId = 'tour-event-1';
  const records = [
    // EventRepositoryImpl.toFirestoreMap (wire/current/events/repository-save-minimal.json).
    ['events', eventId, {
      acceptance: 'NOT_REQUIRED', acceptedAt: '', acceptedBy: '', createdAt: created,
      createdByFirebaseUid: alice.uid, description: '', endDateTime: day(0, 19), eventType: 'activity',
      familyId, forMembers: [], friendParticipates: '', id: eventId, imageUrl: '', isImportant: false,
      isRecurring: false, lastModifiedBy: alice.uid, parentOwner: aliceSlot, permissions: 'read_write',
      pickupConfirmedAt: '', pickupConfirmedBy: '', recurrenceEndDate: '', recurrencePattern: '',
      reminderMinutes: null, sharedWith: audience, startDateTime: day(0, 18), title: TOUR_EVENT_TITLE,
      updatedAt: created,
    }],
    // ChangeRequestRepositoryImpl.toFirestoreMap (check-ios-writes.js' change_requests shape).
    ['change_requests', 'tour-change-1', {
      id: 'tour-change-1', eventId, eventTitle: TOUR_EVENT_TITLE, requestedBy: alice.uid,
      requestedTo: bob.uid, currentStartDateTime: day(0, 18), currentEndDateTime: day(0, 19),
      proposedStartDateTime: day(1, 18), proposedEndDateTime: day(1, 19),
      note: 'Could we move it to tomorrow?', status: 'PENDING', createdAt: created, respondedAt: '',
      familyId,
    }],
    // ChildInfoRepositoryImpl.upsertChildInfo (wire/current/child_info/repository-save.json),
    // with nothing optional: no medical details, no guest, no photo.
    ['child_info', 'tour-child-1', {
      activities: [], allergies: [], childName: 'Emma', createdAt: created,
      createdByFirebaseUid: alice.uid, dateOfBirth: '2018-04-02T00:00:00', emergencyContacts: [],
      familyId, guests: {}, id: 'tour-child-1', lastModifiedBy: alice.uid, medicalNotes: null,
      medicalPhotos: [], medicalProfile: {hereditaryConditions: [], intolerances: [], vaccinations: []},
      medications: [], photo: null,
      schoolInfo: {address: null, grade: null, name: null, phone: null, teacherEmail: null, teacherName: null},
      sharedWith: audience, updatedAt: created,
    }],
    // PetRepositoryImpl's upsert (wire/current/pets/repository-save.json), nothing optional.
    ['pets', 'tour-pet-1', {
      breed: null, createdAt: created, createdByFirebaseUid: alice.uid, dateOfBirth: null, familyId,
      feedingNotes: null, id: 'tour-pet-1', lastModifiedBy: alice.uid, medications: [], name: 'Max',
      photos: [], sharedWith: audience, specialNeeds: null, species: 'DOG', updatedAt: created,
      vaccinations: [], vetName: null, vetPhone: null,
    }],
    // ExpenseRepositoryImpl.addExpense (wire/current/expenses/repository-save.json), split evenly.
    ['expenses', 'tour-expense-1', {
      amount: new Double(450.5), category: 'ACTIVITIES', createdAt: created,
      createdByFirebaseUid: alice.uid, currency: 'EUR', date: day(0, 0).slice(0, 10), familyId,
      forMembers: [], id: 'tour-expense-1', notes: '', paidBy: alice.uid, receiptUrl: '',
      splitBasisPoints: 5000, splitBetween: audience, title: 'Swimming course',
    }],
    // FamilyDocumentMapper.toFirestoreMap: the index row alone.
    ['family_documents', 'tour-document-1', {
      id: 'tour-document-1', familyId, createdByFirebaseUid: alice.uid, sharedWith: audience,
      title: 'School report', category: 'school', fileName: 'report.pdf',
      storagePath: `family_documents/${familyId}/tour-document-1/report.pdf`,
      contentType: 'application/pdf', sizeBytes: 48213,
      sha256: '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08',
      createdAtMillis: now.getTime(),
    }],
  ];
  for (const [collection, id, data] of records) {
    try {
      await call(`${DOCS}/${collection}/${id}`, {
        method: 'PATCH', token: alice.token, body: {fields: documentFields(data)},
      });
      console.error(`COPLANLY_TOUR_SEED wrote ${collection}/${id}`);
    } catch (error) {
      console.error(`COPLANLY_TOUR_SEED skipped ${collection}/${id}: ${error.message}`);
    }
  }
}

/** Seeds the pair and prints the sign-in. */
async function main() {
  const functions = await functionsHost();
  const alice = await signUp('Alice');
  const bob = await signUp('Bob');
  await writeFinishedProfile(alice, 'mom');
  await writeFinishedProfile(bob, 'dad');
  const code = await writeInvitation(alice);
  const accepted = await call(
      `http://${functions}/${PROJECT}/europe-west3/acceptPairingInvitation`,
      {method: 'POST', token: bob.token, body: {data: {code}}});
  if (!accepted.result || accepted.result.partnerId !== alice.uid) {
    throw new Error(`acceptPairingInvitation returned ${JSON.stringify(accepted)}`);
  }
  if (process.argv.includes('--tour')) {
    // The server assigns the slots and answers with the accepter's; the inviter holds the other.
    const bobSlot = accepted.result.familyRole || accepted.result.role;
    await seedTourRecords(alice, bob, bobSlot === 'mom' ? 'dad' : 'mom');
  }
  // The uid and the family id are for the push test (`PushUITests`), which addresses its pushes.
  const familyId = [alice.uid, bob.uid].sort().join('__');
  process.stdout.write(JSON.stringify({email: alice.email, password: PASSWORD, uid: alice.uid, familyId}) + '\n');
}

main().catch((error) => {
  console.error(`Seeding the shell's family FAILED: ${error.message}`);
  process.exit(1);
});
