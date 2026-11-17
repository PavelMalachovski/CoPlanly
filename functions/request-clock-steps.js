/**
 * The server's steps of a request's clock (MON-25): the deadline push and the end, which the
 * proposer's phone ran from WorkManager jobs until part B (`RequestClockSteps.deadline`/`end` and
 * `RequestRecords` in `shared-data`). A port of those two, with the same decisions, acting in the
 * clock's own family (CLAUDE.md item 42). Nothing here schedules anything: `index.js` wires the
 * trigger that keeps `serverNextStepAt` and the sweep that calls [stepClock] for each due clock.
 *
 * **One step is one transaction.** [stepClock] re-reads the clock, does nothing when its
 * `serverNextStepAt` is no longer due, reads the request's record, and writes everything the step
 * decides in the same commit: the next `serverNextStepAt` or the told mark, and for the end the
 * closure, the `REQUEST_UNANSWERED` card, the push and a proposal's withdrawal. So two overlapping
 * sweeps cannot both act, a request answered at the last minute is never closed (the record is
 * read inside the transaction that closes), and a crash leaves either all of it or none of it.
 * The card and the pushes are written under ids derived from the clock's id, with `set()`, so even
 * a repeated commit could not post a second one (`onCreate` triggers do not fire on an overwrite).
 *
 * **What the record says decides** (spec §5.3): pending acts; answered or gone settles the clock;
 * a read that failed leaves `serverNextStepAt` as it is, so the next sweep tries again.
 */

const {FieldValue} = require('firebase-admin/firestore');
const clocks = require('./request-clocks');
const reminderPlan = require('./reminder-plan');
const {previewFirstName} = require('./first-name');

/** The clocks' collection. */
const COLLECTION = 'request_deadlines';

/** The kinds a clock may time (`RequestKind.wire`). */
const KINDS = new Set(['swap', 'change', 'event', 'proposal', 'split']);

/** The day swap statuses the phone reads; an entry with another is dropped, as the phone drops it. */
const DAY_OVERRIDE_STATUSES = new Set(['PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED']);

/** A decision's outcomes the phone reads, for custody and for the expense split alike. */
const DECISION_OUTCOMES = new Set(['ACCEPTED', 'DECLINED']);

/** The custody document's key for a proposal's plan citation, which goes with the proposal (MON-21). */
const PLAN_CITATION = 'proposalPlanCitation';

/** A share is stored in basis points, 0 to 10 000 (`SplitRatio.fromStored`). */
const FULL_SHARE_BASIS_POINTS = 10000;

/** The two server push types (spec §5.4); server-only, so not in the rules' client allow-list. */
const PUSH_DEADLINE_PASSED = 'request_deadline_passed';
const PUSH_NO_ANSWER = 'request_no_answer';

/** The reminder to the answering parent (iPhone phase 4 part C, spec §7.3); server-only too. */
const PUSH_REMINDER = 'request_reminder';

const GONE = Object.freeze({status: 'gone', card: null});
const UNKNOWN = Object.freeze({status: 'unknown', card: null});

/**
 * Whether a field is present: neither undefined nor null.
 * @param {*} value the field.
 * @return {boolean}
 */
function has(value) {
  return value !== undefined && value !== null;
}

/**
 * A non-blank string, or null (`RequestDeadlineDocument.text`).
 * @param {*} value the field.
 * @return {string|null}
 */
function text(value) {
  return typeof value === 'string' && value.trim() !== '' ? value : null;
}

/**
 * Whether a value reads as an ISO date, as `IsoText.parseDate` reads one.
 * @param {*} value the field.
 * @return {boolean}
 */
function isIsoDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

/**
 * The clock a document describes, or null when the server cannot time it — an unknown kind, or a
 * field nothing can be done without (`RequestDeadlineDocument.parse`).
 *
 * @param {!Object} doc the clock document's data.
 * @return {?{kind: string, ref: string, familyId: string, proposerUid: string, responderUid: string}}
 */
function clockOf(doc) {
  const kind = text(doc.kind);
  const ref = text(doc.ref);
  const familyId = text(doc.familyId);
  const proposerUid = text(doc.proposerUid);
  const responderUid = text(doc.responderUid);
  if (!kind || !KINDS.has(kind) || !ref || typeof doc.answerByMillis !== 'number') return null;
  if (!familyId || !proposerUid || !responderUid) return null;
  return {kind, ref, familyId, proposerUid, responderUid, firstDate: doc.firstDate};
}

/**
 * The co-parents a profile names: `partnerIds` and `partnerId`, without blanks or the owner
 * (`FamilyAudience.coParents`).
 *
 * @param {string} uid the profile's owner.
 * @param {!Object} profile the profile's data.
 * @return {!Set<string>}
 */
function coParentsOf(uid, profile) {
  const ids = Array.isArray(profile.partnerIds) ? profile.partnerIds.slice() : [];
  if (has(profile.partnerId)) ids.push(profile.partnerId);
  return new Set(ids.filter((id) => typeof id === 'string' && id.trim() !== '' && id !== uid));
}

/**
 * Whether the clock's family is still live, decided as the proposer's phone decided it
 * (`RequestRecords.familyIsLive`): the clock's `familyId` names exactly the proposer and the
 * responder, and the proposer's own profile still names the responder as a co-parent. Not
 * `families/{id}`, which an old pair may lack. A proposer with no profile (a deleted account) has no
 * live family.
 *
 * @param {function(!Object): !Promise<!Object>} read reads a document reference.
 * @param {!Object} db Firestore.
 * @param {!Object} clock the clock, from [clockOf].
 * @return {!Promise<boolean>}
 */
async function familyIsLive(read, db, clock) {
  if (clock.proposerUid === clock.responderUid) return false;
  const parts = clock.familyId.split('__');
  if (parts.length !== 2 || parts.some((part) => part.trim() === '')) return false;
  if (!parts.includes(clock.proposerUid) || !parts.includes(clock.responderUid)) return false;
  const snap = await read(db.collection('users').doc(clock.proposerUid));
  if (!snap.exists) return false;
  return coParentsOf(clock.proposerUid, snap.data() || {}).has(clock.responderUid);
}

/**
 * A change request: pending while its status is `PENDING`, answered otherwise; gone when it is
 * missing or is not between the clock's two parents (`RequestRecords.lookChange`).
 *
 * @param {!Object} snap the change request's snapshot.
 * @param {!Object} clock the clock.
 * @return {{status: string, card: ?Object}}
 */
function lookChange(snap, clock) {
  const data = snap.exists ? snap.data() || {} : null;
  if (!data || data.requestedBy !== clock.proposerUid || data.requestedTo !== clock.responderUid) return GONE;
  return {
    status: data.status === 'PENDING' ? 'pending' : 'answered',
    card: {
      entityType: 'CHANGE_REQUEST',
      entityId: typeof data.eventId === 'string' ? data.eventId : '',
      title: typeof data.eventTitle === 'string' ? data.eventTitle : '',
    },
  };
}

/**
 * An event awaiting an answer: pending while its acceptance is `PENDING`; gone when it is missing, a
 * tombstone or not created by the proposer (`RequestRecords.lookEventOnServer`; an absent acceptance
 * is `NOT_REQUIRED`, as `EventDocument.acceptanceOf` reads it).
 *
 * @param {!Object} snap the event's snapshot.
 * @param {!Object} clock the clock.
 * @return {{status: string, card: ?Object}}
 */
function lookEvent(snap, clock) {
  const data = snap.exists ? snap.data() || {} : null;
  if (!data) return GONE;
  if (typeof data.deletedAtMillis === 'number' && data.deletedAtMillis > 0) return GONE;
  if (data.createdByFirebaseUid !== clock.proposerUid) return GONE;
  const acceptance = text(data.acceptance) || 'NOT_REQUIRED';
  return {
    status: acceptance === 'PENDING' ? 'pending' : 'answered',
    card: {entityType: 'EVENT', entityId: clock.ref, title: typeof data.title === 'string' ? data.title : ''},
  };
}

/**
 * The custody document's data, or null when it cannot describe a pattern — missing, or without a
 * start date or a cycle length (`FirestoreCustodyDataSource.toSharedCustody`).
 *
 * @param {!Object} snap the custody document's snapshot.
 * @return {?Object}
 */
function custodyData(snap) {
  const data = snap.exists ? snap.data() || {} : null;
  if (!data || !isIsoDate(data.startDate) || typeof data.patternDays !== 'number') return null;
  return data;
}

/**
 * A day swap: every day of the custody document in the clock's group offered by the proposer;
 * pending while any is pending, answered once none is; gone when none is left
 * (`RequestRecords.lookSwap`). The card names the clock's first date, or the group's first day.
 *
 * @param {!Object} custody the custody document's data.
 * @param {!Object} clock the clock.
 * @return {{status: string, card: ?Object}}
 */
function lookSwap(custody, clock) {
  const overrides = custody.dayOverrides && typeof custody.dayOverrides === 'object' ? custody.dayOverrides : {};
  const days = Object.keys(overrides).filter((date) => {
    const entry = overrides[date];
    return date.trim() !== '' && entry && typeof entry === 'object' &&
      text(entry.toParent) && text(entry.requestedBy) && DAY_OVERRIDE_STATUSES.has(entry.status) &&
      entry.groupId === clock.ref && entry.requestedBy === clock.proposerUid;
  }).sort();
  if (days.length === 0) return GONE;
  const pending = days.some((date) => overrides[date].status === 'PENDING');
  const date = isIsoDate(clock.firstDate) ? clock.firstDate : days[0];
  return {status: pending ? 'pending' : 'answered', card: {entityType: 'DAY_SWAP', entityId: date, title: date}};
}

/**
 * The custody document's proposal, or null when it cannot describe a pattern
 * (`FirestoreCustodyDataSource.toProposal`).
 *
 * @param {!Object} custody the custody document's data.
 * @return {?Object}
 */
function custodyProposalOf(custody) {
  const proposal = custody.proposal;
  if (!proposal || typeof proposal !== 'object') return null;
  if (!isIsoDate(proposal.startDate) || typeof proposal.patternDays !== 'number' || !text(proposal.proposedBy)) {
    return null;
  }
  return proposal;
}

/**
 * Whether the custody document's proposal is the clock's: its time and its proposer.
 *
 * @param {!Object} custody the custody document's data.
 * @param {!Object} clock the clock.
 * @return {boolean}
 */
function isClocksCustodyProposal(custody, clock) {
  const proposal = custodyProposalOf(custody);
  return proposal !== null && (proposal.proposedAt || '') === clock.ref && proposal.proposedBy === clock.proposerUid;
}

/**
 * A custody proposal: pending while the document's proposal is the clock's; answered when the last
 * decision is about it; gone otherwise (`RequestRecords.lookProposal`).
 *
 * @param {!Object} custody the custody document's data.
 * @param {!Object} clock the clock.
 * @return {{status: string, card: ?Object}}
 */
function lookProposal(custody, clock) {
  const decision = custody.lastDecision;
  const decided = decision && typeof decision === 'object' && DECISION_OUTCOMES.has(decision.outcome) &&
    (decision.proposalAt || '') === clock.ref;
  let status = 'gone';
  if (isClocksCustodyProposal(custody, clock)) status = 'pending';
  else if (decided) status = 'answered';
  return {status, card: {entityType: 'CUSTODY_PROPOSAL', entityId: clock.familyId, title: ''}};
}

/**
 * Whether a stored share reads as a ratio (`SplitRatio.fromStored`).
 * @param {*} value the field.
 * @return {boolean}
 */
function isShare(value) {
  return Number.isInteger(value) && value >= 0 && value <= FULL_SHARE_BASIS_POINTS;
}

/**
 * The money agreement's data, or null when it cannot describe one
 * (`FirestoreFamilySettingsDataSource.toSettings`).
 *
 * @param {!Object} snap the settings document's snapshot.
 * @return {?Object}
 */
function settingsData(snap) {
  const data = snap.exists ? snap.data() || {} : null;
  if (!data || !Array.isArray(data.participants)) return null;
  if (data.participants.filter((uid) => typeof uid === 'string').length !== 2) return null;
  return isShare(data.momShareBasisPoints) ? data : null;
}

/**
 * Whether the money agreement's proposal is the clock's: its time, as decimal text, and its proposer.
 *
 * @param {!Object} settings the settings document's data.
 * @param {!Object} clock the clock.
 * @return {boolean}
 */
function isClocksSplitProposal(settings, clock) {
  const proposal = settings.proposal;
  if (!proposal || typeof proposal !== 'object' || !isShare(proposal.momShareBasisPoints)) return false;
  if (!text(proposal.proposedBy) || proposal.proposedBy !== clock.proposerUid) return false;
  const at = typeof proposal.proposedAtMillis === 'number' ? Math.trunc(proposal.proposedAtMillis) : 0;
  return String(at) === clock.ref;
}

/**
 * An expense split proposal: [lookProposal] for the money agreement (`RequestRecords.lookSplit`).
 *
 * @param {!Object} settings the settings document's data.
 * @param {!Object} clock the clock.
 * @return {{status: string, card: ?Object}}
 */
function lookSplit(settings, clock) {
  const decision = settings.lastDecision;
  const decidedAt = decision && typeof decision === 'object' && DECISION_OUTCOMES.has(decision.outcome) &&
    typeof decision.proposalAtMillis === 'number' ? Math.trunc(decision.proposalAtMillis) : 0;
  const decided = decision && typeof decision === 'object' && DECISION_OUTCOMES.has(decision.outcome) &&
    String(decidedAt) === clock.ref;
  let status = 'gone';
  if (isClocksSplitProposal(settings, clock)) status = 'pending';
  else if (decided) status = 'answered';
  return {status, card: {entityType: 'SPLIT_PROPOSAL', entityId: clock.familyId, title: ''}};
}

/**
 * [lookRecord] over a reader: `ref.get()` outside a transaction, `tx.get(ref)` inside one.
 *
 * @param {function(!Object): !Promise<!Object>} read reads a document reference.
 * @param {!Object} db Firestore.
 * @param {!Object} clock the clock, from [clockOf].
 * @return {!Promise<{status: string, card: ?Object}>}
 */
async function lookWith(read, db, clock) {
  try {
    if (!(await familyIsLive(read, db, clock))) return GONE;
    switch (clock.kind) {
      case 'change':
        return lookChange(await read(db.collection('change_requests').doc(clock.ref)), clock);
      case 'event':
        return lookEvent(await read(db.collection('events').doc(clock.ref)), clock);
      case 'swap':
      case 'proposal': {
        const custody = custodyData(await read(db.collection('custody_models').doc(clock.familyId)));
        if (!custody) return GONE;
        return clock.kind === 'swap' ? lookSwap(custody, clock) : lookProposal(custody, clock);
      }
      case 'split': {
        const settings = settingsData(await read(db.collection('family_settings').doc(clock.familyId)));
        return settings ? lookSplit(settings, clock) : GONE;
      }
      default:
        return GONE;
    }
  } catch (e) {
    // Never "gone": a step must not close a request it could not see.
    console.warn('Could not read a request clock\'s record', e && e.code);
    return UNKNOWN;
  }
}

/**
 * Where the request a clock times stands, read by the admin SDK (always the server's copy), and
 * what its cards name: a port of `RequestRecords.lookOnServer`. A family that is no longer live
 * makes the record `gone`; a read that fails is `unknown`.
 *
 * @param {!Object} db Firestore.
 * @param {!Object} clock the clock document's data (the stored field names).
 * @return {!Promise<{status: string, card: ?{entityType: string, entityId: string, title: string}}>}
 */
async function lookRecord(db, clock) {
  const parsed = clockOf(clock);
  if (!parsed) return GONE;
  return lookWith((ref) => ref.get(), db, parsed);
}

/**
 * The `REQUEST_UNANSWERED` card the proposer's phone posted at the end (`RequestTelling.unanswered`
 * through `ActivityAnnouncer.announce`), in the clock's family's thread and in the proposer's name,
 * with the fields `Message.toFirestoreMap` writes and the base-English fallback
 * `ActivityFallbackText` writes for the kind. Its id is derived from the clock's, so it is one card
 * however often it is written.
 *
 * @param {!Object} clock the clock document's data, with its id as `id`.
 * @param {{entityType: string, entityId: string, title: string}} card what the request's cards name.
 * @param {string} proposerName the proposer's profile name, for the bubble; blank is tolerated.
 * @param {number=} sentAtMillis when the card is sent; now by default.
 * @return {{id: string, data: !Object}}
 */
function unansweredCardFor(clock, card, proposerName, sentAtMillis) {
  const id = `srv_unanswered_${clock.id}`;
  const title = card.title || '';
  return {
    id,
    data: {
      id,
      // The conversation id is the family id: both are `FamilyKey.of` of the pair.
      conversationId: clock.familyId,
      senderId: clock.proposerUid,
      senderName: proposerName || '',
      content: title.trim() === '' ? 'No answer' : `No answer: ${title}`,
      timestamp: typeof sentAtMillis === 'number' ? sentAtMillis : Date.now(),
      messageType: 'ACTIVITY',
      attachments: [card.entityType, card.entityId],
      isRead: false,
      replyToMessageId: '',
      activity: {
        kind: 'REQUEST_UNANSWERED',
        entityType: card.entityType,
        entityId: card.entityId,
        title,
      },
    },
  };
}

/**
 * Reads what a proposal's withdrawal needs, in a transaction: the document and whether its proposal
 * is still the clock's. Null for any other kind.
 *
 * @param {!Object} tx the transaction.
 * @param {!Object} db Firestore.
 * @param {!Object} clock the clock, from [clockOf].
 * @return {!Promise<?{ref: !Object, keys: !Array<string>, same: boolean}>}
 */
async function readWithdrawal(tx, db, clock) {
  if (clock.kind === 'proposal') {
    const ref = db.collection('custody_models').doc(clock.familyId);
    const custody = custodyData(await tx.get(ref));
    // `CustodyProposalTransition.withdraw` empties the proposal, and the citation goes with it.
    return {ref, keys: ['proposal', PLAN_CITATION], same: custody !== null && isClocksCustodyProposal(custody, clock)};
  }
  if (clock.kind === 'split') {
    const ref = db.collection('family_settings').doc(clock.familyId);
    const settings = settingsData(await tx.get(ref));
    // `SplitRatioTransition.withdraw` empties the proposal alone.
    return {ref, keys: ['proposal'], same: settings !== null && isClocksSplitProposal(settings, clock)};
  }
  return null;
}

/**
 * Writes a withdrawal [readWithdrawal] found possible: an `update()` that deletes only the keys the
 * phone's withdrawal empties, never a `set()`, so keys the server does not know survive.
 *
 * @param {!Object} tx the transaction.
 * @param {!Object} plan from [readWithdrawal], with `same` true.
 */
function writeWithdrawal(tx, plan) {
  const update = {};
  plan.keys.forEach((key) => {
    update[key] = FieldValue.delete();
  });
  tx.update(plan.ref, update);
}

/**
 * A push to the proposer, in the shape every server push takes (`pairing_accepted`): the type, the
 * family and the answering parent's first name, never a sentence (SEC-3).
 *
 * @param {!Object} clock the clock, from [clockOf].
 * @param {string} type the push type.
 * @param {string} actorName the answering parent's first name.
 * @param {*} createdAt the server timestamp sentinel.
 * @return {!Object}
 */
function pushFor(clock, type, actorName, createdAt) {
  return {
    targetUserId: clock.proposerUid,
    data: {type, actorName, familyId: clock.familyId},
    status: 'pending',
    createdAt,
  };
}

/**
 * The answering parent's first name, read in the transaction; '' when their profile cannot be read.
 *
 * @param {!Object} tx the transaction.
 * @param {!Object} db Firestore.
 * @param {!Object} clock the clock.
 * @return {!Promise<string>}
 */
async function responderFirstName(tx, db, clock) {
  const snap = await tx.get(db.collection('users').doc(clock.responderUid));
  return snap.exists ? previewFirstName((snap.data() || {}).name) : '';
}

/**
 * The reminder push to the answering parent (`request_reminder`): the family, the request's kind,
 * the proposer's first name and the deadline as epoch millis in decimal text, never a sentence.
 *
 * @param {!Object} clock the clock, from [clockOf].
 * @param {number} answerByMillis the deadline.
 * @param {string} actorName the proposer's first name.
 * @param {*} createdAt the server timestamp sentinel.
 * @return {!Object}
 */
function reminderPushFor(clock, answerByMillis, actorName, createdAt) {
  return {
    targetUserId: clock.responderUid,
    data: {
      type: PUSH_REMINDER,
      actorName,
      familyId: clock.familyId,
      requestKind: clock.kind,
      answerBy: String(answerByMillis),
    },
    status: 'pending',
    createdAt,
  };
}

/**
 * The answering parent's time zone as their profile states it (`users/{uid}.timeZone`), read in the
 * transaction; null when the profile cannot be read or names no zone this runtime knows.
 *
 * @param {!Object} tx the transaction.
 * @param {!Object} db Firestore.
 * @param {!Object} clock the clock.
 * @return {!Promise<?string>}
 */
async function responderZone(tx, db, clock) {
  const snap = await tx.get(db.collection('users').doc(clock.responderUid));
  const zone = snap.exists ? (snap.data() || {}).timeZone : null;
  return reminderPlan.isZone(zone) ? zone : null;
}

/**
 * The reminders of an open clock (spec §7.3, §11 call 1), inside [stepClock]'s transaction:
 *
 * - nothing planned (no phone delivered it, the server has not planned): the server's own plan in
 *   the answering parent's zone, from the clock's creation, never a time already past, written once
 *   (`planned`); with no zone stored, nothing, and the clock waits for the phone's delivery
 *   (`rescheduled`);
 * - no reminder due yet: the next moment (`rescheduled`);
 * - the record answered or gone: settled; unreadable: `skipped`, still due;
 * - in the quiet hours of the stored zone: the next 08:00 when that is before the deadline
 *   (`deferred`), otherwise the reminder is dropped (`dropped`);
 * - otherwise one push for every reminder now due, counted once (`reminded`).
 *
 * @param {!Object} tx the transaction.
 * @param {!Object} db Firestore.
 * @param {!Object} step the clock's reference and id, its data and parsed clock, the moment, the
 *   timestamp sentinel and [stepClock]'s `settle`.
 * @return {!Promise<{action: string}>}
 */
async function remindOrPlan(tx, db, step) {
  const {clockRef, clockId, doc, clock, nowMillis, serverTimestamp, settle} = step;
  const writeAndReschedule = (update, action) => {
    const after = Object.assign({}, doc, update);
    const next = clocks.nextStepAt(after);
    tx.update(clockRef, Object.assign({}, update, {serverNextStepAt: next === null ? FieldValue.delete() : next}));
    return {action};
  };

  if (clocks.reminderPlanOf(doc) === null) {
    const sentAt = clocks.toMillis(doc.createdAt);
    const zone = sentAt === null ? null : await responderZone(tx, db, clock);
    if (zone === null) {
      // No zone: the server waits for the answering phone's own plan, and the deadline is next. A
      // later write of the clock (the phone's delivery, say) has the trigger look again.
      tx.update(clockRef, {serverNextStepAt: doc.answerByMillis});
      return {action: 'rescheduled'};
    }
    const times = reminderPlan.plan(sentAt, doc.answerByMillis, nowMillis, zone);
    return writeAndReschedule({serverReminderTimesMillis: times}, 'planned');
  }

  const due = clocks.remindersLeft(doc).filter((at) => at <= nowMillis);
  if (due.length === 0) return writeAndReschedule({}, 'rescheduled');
  const last = due[due.length - 1];

  const look = await lookWith((ref) => tx.get(ref), db, clock);
  if (look.status === 'unknown') return {action: 'skipped'};
  if (look.status !== 'pending') return settle();

  const zone = await responderZone(tx, db, clock);
  if (zone !== null && reminderPlan.isQuietAt(nowMillis, zone)) {
    const morning = reminderPlan.nextMorning(nowMillis, zone);
    if (morning < doc.answerByMillis) {
      tx.update(clockRef, {serverNextStepAt: morning});
      return {action: 'deferred'};
    }
    return writeAndReschedule({serverLastReminderAt: last}, 'dropped');
  }

  const proposer = await tx.get(db.collection('users').doc(clock.proposerUid));
  const actor = proposer.exists ? previewFirstName((proposer.data() || {}).name) : '';
  tx.set(db.collection('notification_queue').doc(`srv_${PUSH_REMINDER}_${clockId}_${last}`),
      reminderPushFor(clock, doc.answerByMillis, actor, serverTimestamp()));
  const sent = Number.isInteger(doc.serverRemindersSent) ? doc.serverRemindersSent : 0;
  return writeAndReschedule({serverRemindersSent: sent + 1, serverLastReminderAt: last}, 'reminded');
}

/**
 * Runs one step of one clock: the decision `RequestClockSteps.deadline` and `end` made on the
 * proposer's phone, by the state at [nowMillis] (spec §5.3).
 *
 * - not due (no `serverNextStepAt`, or a later one; another run acted first): nothing, `notDue`;
 * - `OPEN` or `LAST_CHANCE`, or the deadline already told: the next moment, `rescheduled`;
 * - `NO_ANSWER_LAST_CHANCE_POSSIBLE`: the deadline push while the record waits, `toldDeadline`;
 * - `NO_ANSWER` with no outcome: the end while the record waits — closed, the card, the push and a
 *   proposal's withdrawal — `closed`;
 * - already closed (by an older phone, or by the server), a custody or split proposal still the
 *   clock's: the withdrawal again, `withdrawalRetried`; nothing is told twice;
 * - answered, gone, or nothing more to do: `settled`;
 * - the record could not be read: nothing written, the field stays due, `skipped`.
 *
 * @param {!Object} db Firestore.
 * @param {string} clockId the clock's document id.
 * @param {number} nowMillis the moment to judge at.
 * @param {{serverTimestamp: (function(): *|undefined)}=} deps `serverTimestamp()` for `closedAt` and
 *   the queue's `createdAt`; Firestore's own by default.
 * @return {!Promise<{action: string}>}
 */
async function stepClock(db, clockId, nowMillis, deps) {
  const serverTimestamp = (deps && deps.serverTimestamp) || (() => FieldValue.serverTimestamp());
  const clockRef = db.collection(COLLECTION).doc(clockId);
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(clockRef);
    if (!snap.exists) return {action: 'notDue'};
    const doc = snap.data() || {};
    if (typeof doc.serverNextStepAt !== 'number' || doc.serverNextStepAt > nowMillis) return {action: 'notDue'};

    const settle = () => {
      tx.update(clockRef, {serverSettledAt: nowMillis, serverNextStepAt: FieldValue.delete()});
      return {action: 'settled'};
    };
    const reschedule = (at) => {
      tx.update(clockRef, {serverNextStepAt: at});
      return {action: 'rescheduled'};
    };

    const clock = clockOf(doc);
    if (!clock || has(doc.serverSettledAt)) return settle();
    const read = (ref) => tx.get(ref);

    if (has(doc.outcome)) {
      if (!clocks.withdrawnAtEnd(clock.kind)) return settle();
      const look = await lookWith(read, db, clock);
      if (look.status === 'unknown') return {action: 'skipped'};
      if (look.status !== 'pending') return settle();
      const plan = await readWithdrawal(tx, db, clock);
      // Refused: the field stays as it is, already due, so the next run tries again.
      if (!plan || !plan.same) return {action: 'skipped'};
      writeWithdrawal(tx, plan);
      settle();
      return {action: 'withdrawalRetried'};
    }

    switch (clocks.stateOf(doc, nowMillis)) {
      case 'OPEN':
        return remindOrPlan(tx, db, {clockRef, clockId, doc, clock, nowMillis, serverTimestamp, settle});
      case 'LAST_CHANCE':
        return reschedule(doc.lastChanceUntilMillis);
      case 'NO_ANSWER_LAST_CHANCE_POSSIBLE': {
        if (has(doc.serverToldDeadlineAt)) return reschedule(clocks.endMillis(doc));
        const look = await lookWith(read, db, clock);
        if (look.status === 'unknown') return {action: 'skipped'};
        if (look.status !== 'pending') return settle();
        const actor = await responderFirstName(tx, db, clock);
        tx.set(db.collection('notification_queue').doc(`srv_${PUSH_DEADLINE_PASSED}_${clockId}`),
            pushFor(clock, PUSH_DEADLINE_PASSED, actor, serverTimestamp()));
        tx.update(clockRef, {serverToldDeadlineAt: nowMillis, serverNextStepAt: clocks.endMillis(doc)});
        return {action: 'toldDeadline'};
      }
      default:
        return closeUnanswered(tx, db, {clockRef, clockId, doc, clock, nowMillis, serverTimestamp, settle});
    }
  });
}

/**
 * The end of a request nobody answered (`RequestClockSteps.closeUnanswered`), inside [stepClock]'s
 * transaction: every read first, then the closure, the card, the push and the withdrawal.
 *
 * @param {!Object} tx the transaction.
 * @param {!Object} db Firestore.
 * @param {!Object} step the clock's reference, id, data and parsed clock, the moment, the
 *   timestamp sentinel and [stepClock]'s `settle`.
 * @return {!Promise<{action: string}>}
 */
async function closeUnanswered(tx, db, step) {
  const {clockRef, clockId, doc, clock, nowMillis, serverTimestamp, settle} = step;
  const look = await lookWith((ref) => tx.get(ref), db, clock);
  if (look.status === 'unknown') return {action: 'skipped'};
  // An orphaned clock (its request withdrawn or replaced) shows nothing, as on the phone.
  if (look.status !== 'pending' || !look.card) return settle();

  const proposerSnap = await tx.get(db.collection('users').doc(clock.proposerUid));
  const proposerName = proposerSnap.exists ? String((proposerSnap.data() || {}).name || '') : '';
  const actor = await responderFirstName(tx, db, clock);
  const conversationRef = db.collection('conversations').doc(clock.familyId);
  const conversation = await tx.get(conversationRef);
  const plan = clocks.withdrawnAtEnd(clock.kind) ? await readWithdrawal(tx, db, clock) : null;

  const card = unansweredCardFor(Object.assign({id: clockId}, doc), look.card, proposerName, nowMillis);
  const withdrawn = plan !== null && plan.same;
  tx.update(clockRef, Object.assign({
    outcome: 'no_answer',
    closedAt: serverTimestamp(),
    serverToldNoAnswerAt: nowMillis,
  }, plan === null || withdrawn ?
    {serverSettledAt: nowMillis, serverNextStepAt: FieldValue.delete()} :
    // A withdrawal this step could not make: the field stays due, and the next run retries it.
    {}));
  tx.set(db.collection('messages').doc(card.id), card.data);
  // `MessageRepositoryImpl.deliver` moves the thread's ordering time with every message; only an
  // existing thread, since an update of a missing one would fail the whole step.
  if (conversation.exists) tx.update(conversationRef, {lastMessageAt: nowMillis});
  tx.set(db.collection('notification_queue').doc(`srv_${PUSH_NO_ANSWER}_${clockId}`),
      pushFor(clock, PUSH_NO_ANSWER, actor, serverTimestamp()));
  if (withdrawn) writeWithdrawal(tx, plan);
  return {action: 'closed'};
}

module.exports = {
  PUSH_DEADLINE_PASSED,
  PUSH_NO_ANSWER,
  PUSH_REMINDER,
  lookRecord,
  stepClock,
  unansweredCardFor,
};
