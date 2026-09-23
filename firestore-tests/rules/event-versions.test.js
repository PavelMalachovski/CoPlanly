/**
 * MON-4 — event revisions, and the chat guarantee the export leans on.
 *
 * `event_versions/{versionId}` holds one immutable document per saved revision of a non-private
 * event (docs/DESIGN-court-record.md §4). The export sells exactly two properties of it, and
 * these cases pin both: **nobody can change or remove a revision once written**, and **the
 * server's clock, not the device's, is what `recordedAt` says**. Everything else here is the
 * ordinary bound on who may write into somebody's record (CLAUDE.md item 23).
 *
 * The last block pins the chat half of the same record. `conversations-messages.test.js`
 * covers the message rules for the chat screen's sake; these cases exist so that a rule edit
 * which would make a message alterable fails a test named after the export it would falsify.
 */

const {Timestamp, serverTimestamp} = require('firebase/firestore');
const {
  CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-event-versions';
const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const CAROL = 'carol-uid';
const FRIEND = 'friend-uid';

/** Alice and Bob's family, as `FamilyKey.of` spells it. */
const FAMILY = [ALICE, BOB].sort().join('__');

const PAIRED_USERS = {
  'users/alice-uid': {name: 'Alice', partnerId: BOB, partnerIds: [BOB]},
  'users/bob-uid': {name: 'Bob', partnerId: ALICE, partnerIds: [ALICE]},
  'users/carol-uid': {name: 'Carol', partnerId: '', partnerIds: []},
};

/**
 * An event document as `EventRepositoryImpl.toFirestoreMap()` writes it.
 *
 * @param {!Object} overrides Fields to override.
 * @return {!Object} The document.
 */
function eventDoc(overrides) {
  return Object.assign({
    id: 'event-1',
    title: 'Swimming lesson',
    description: '',
    startDateTime: '2026-08-05T16:00:00',
    endDateTime: '2026-08-05T17:00:00',
    eventType: 'ACTIVITY',
    parentOwner: 'mom',
    createdAt: '2026-08-01T10:00:00',
    updatedAt: '2026-08-01T10:00:00',
    createdByFirebaseUid: ALICE,
    familyId: FAMILY,
    sharedWith: [ALICE, BOB],
    lastModifiedBy: ALICE,
    permissions: 'read_write',
  }, overrides);
}

/**
 * A revision as `EventVersionRecorder` uploads it.
 *
 * @param {string} editor The uid writing it.
 * @param {!Object} overrides Fields to override.
 * @return {!Object} The document.
 */
function versionDoc(editor, overrides) {
  return Object.assign({
    eventId: 'event-1',
    kind: 'updated',
    editorUid: editor,
    deviceTimeMillis: 1787000000000,
    recordedAt: serverTimestamp(),
    sharedWith: [ALICE, BOB],
    familyId: FAMILY,
    event: eventDoc({lastModifiedBy: editor}),
    formatVersion: 1,
  }, overrides);
}

describe('event_versions', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, Object.assign({'events/event-1': eventDoc({})}, PAIRED_USERS));
  });

  const as = (uid) => env.authenticatedContext(uid).firestore();

  describe('create', () => {
    it('lets the creator record a revision of their own event', async () => {
      await assertSucceeds(as(ALICE).doc('event_versions/v1').set(versionDoc(ALICE, {})));
    });

    it('lets a read_write co-parent record a revision of the creator\'s event', async () => {
      await assertSucceeds(as(BOB).doc('event_versions/v1').set(versionDoc(BOB, {})));
    });

    it('refuses a co-parent the event is shared with read-only', async () => {
      await seed(env, {'events/event-1': eventDoc({permissions: 'read_only'})});
      await assertFails(as(BOB).doc('event_versions/v1').set(versionDoc(BOB, {})));
    });

    it('refuses a stranger recording a revision of somebody else\'s event', async () => {
      await assertFails(as(CAROL).doc('event_versions/v1').set(versionDoc(CAROL, {
        sharedWith: [CAROL],
        familyId: '',
      })));
    });

    it('refuses a revision attributed to somebody other than the caller', async () => {
      await assertFails(as(BOB).doc('event_versions/v1').set(versionDoc(ALICE, {})));
    });

    it('refuses a recordedAt the client chose (the server clock is the point)', async () => {
      await assertFails(as(ALICE).doc('event_versions/v1').set(versionDoc(ALICE, {
        recordedAt: Timestamp.fromMillis(1787000000000),
      })));
    });

    it('refuses a revision without a server time at all', async () => {
      const doc = versionDoc(ALICE, {});
      delete doc.recordedAt;
      await assertFails(as(ALICE).doc('event_versions/v1').set(doc));
    });

    it('refuses a device time that is not epoch millis', async () => {
      await assertFails(as(ALICE).doc('event_versions/v1').set(versionDoc(ALICE, {
        deviceTimeMillis: '2026-08-01T10:00:00',
      })));
    });

    it('refuses an unknown kind', async () => {
      await assertFails(as(ALICE).doc('event_versions/v1').set(versionDoc(ALICE, {
        kind: 'restored',
      })));
    });

    it('refuses a key outside the revision schema', async () => {
      await assertFails(as(ALICE).doc('event_versions/v1').set(versionDoc(ALICE, {
        note: 'smuggled',
      })));
    });

    it('refuses a snapshot of a different event than the one named', async () => {
      await assertFails(as(ALICE).doc('event_versions/v1').set(versionDoc(ALICE, {
        event: eventDoc({id: 'event-2'}),
      })));
    });

    it('refuses an audience naming a stranger', async () => {
      await assertFails(as(ALICE).doc('event_versions/v1').set(versionDoc(ALICE, {
        sharedWith: [ALICE, BOB, CAROL],
      })));
    });

    it('refuses an audience the caller is not in', async () => {
      await assertFails(as(ALICE).doc('event_versions/v1').set(versionDoc(ALICE, {
        sharedWith: [BOB],
      })));
    });

    it('refuses a family the caller is not in', async () => {
      await assertFails(as(ALICE).doc('event_versions/v1').set(versionDoc(ALICE, {
        familyId: [BOB, CAROL].sort().join('__'),
      })));
    });

    it('accepts a revision of an event that has not landed yet (an offline create)', async () => {
      await assertSucceeds(as(ALICE).doc('event_versions/v1').set(versionDoc(ALICE, {
        eventId: 'event-offline',
        kind: 'created',
        event: eventDoc({id: 'event-offline'}),
      })));
    });

    it('accepts a delete revision of a tombstoned event', async () => {
      await seed(env, {'events/event-1': eventDoc({deletedAtMillis: 1787000000000, deletedBy: ALICE})});
      await assertSucceeds(as(ALICE).doc('event_versions/v1').set(versionDoc(ALICE, {
        kind: 'deleted',
        event: eventDoc({deletedAtMillis: 1787000000000, deletedBy: ALICE}),
      })));
    });
  });

  describe('immutability — the guarantee the export sells', () => {
    beforeEach(async () => {
      await seed(env, {
        'event_versions/v1': Object.assign(versionDoc(ALICE, {}), {
          recordedAt: Timestamp.fromMillis(1787000001000),
        }),
      });
    });

    it('refuses the author editing their own revision', async () => {
      await assertFails(as(ALICE).doc('event_versions/v1').update({
        'event.title': 'Something else',
      }));
    });

    it('refuses the author re-dating their own revision', async () => {
      await assertFails(as(ALICE).doc('event_versions/v1').update({
        deviceTimeMillis: 1700000000000,
      }));
    });

    it('refuses overwriting a revision with set()', async () => {
      await assertFails(as(ALICE).doc('event_versions/v1').set(versionDoc(ALICE, {})));
    });

    it('refuses the author deleting their own revision', async () => {
      await assertFails(as(ALICE).doc('event_versions/v1').delete());
    });

    it('refuses the co-parent deleting it', async () => {
      await assertFails(as(BOB).doc('event_versions/v1').delete());
    });
  });

  describe('read', () => {
    beforeEach(async () => {
      await seed(env, {
        'event_versions/v1': Object.assign(versionDoc(ALICE, {}), {
          recordedAt: Timestamp.fromMillis(1787000001000),
        }),
        'calendar_friends/friend-uid': {
          familyId: FAMILY,
          familyParents: [ALICE, BOB],
          expiresAtMillis: 4102444800000,
        },
      });
    });

    it('lets both parents in the revision\'s audience read it', async () => {
      await assertSucceeds(as(ALICE).doc('event_versions/v1').get());
      await assertSucceeds(as(BOB).doc('event_versions/v1').get());
    });

    it('refuses a stranger', async () => {
      await assertFails(as(CAROL).doc('event_versions/v1').get());
    });

    it('refuses a live calendar friend (the history is the parents\', not the calendar)',
        async () => {
          await assertFails(as(FRIEND).doc('event_versions/v1').get());
        });

    it('serves the export\'s query, filtered on the audience', async () => {
      await assertSucceeds(as(BOB).collection('event_versions')
          .where('sharedWith', 'array-contains', BOB).get());
    });

    it('refuses an unfiltered scan of the collection', async () => {
      await assertFails(as(ALICE).collection('event_versions').get());
    });

    it('keeps a revision readable after its event is swept', async () => {
      await env.withSecurityRulesDisabled(async (ctx) => {
        await ctx.firestore().doc('events/event-1').delete();
      });
      await assertSucceeds(as(BOB).doc('event_versions/v1').get());
    });
  });
});

describe('messages — the half of the record that was already append-only', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, Object.assign({
      'conversations/conv-1': {id: 'conv-1', participants: [ALICE, BOB]},
      'messages/msg-1': {
        id: 'msg-1',
        conversationId: 'conv-1',
        senderId: ALICE,
        senderName: 'Alice',
        content: 'Pickup at six?',
        timestamp: 1787000000000,
        messageType: 'TEXT',
        attachments: [],
        isRead: false,
        replyToMessageId: '',
      },
    }, PAIRED_USERS));
  });

  const as = (uid) => env.authenticatedContext(uid).firestore();

  it('refuses the sender rewriting what they said', async () => {
    await assertFails(as(ALICE).doc('messages/msg-1').update({content: 'Pickup at seven?'}));
  });

  it('refuses the sender re-dating the message', async () => {
    await assertFails(as(ALICE).doc('messages/msg-1').update({timestamp: 1700000000000}));
  });

  it('refuses the sender changing the attachments', async () => {
    await assertFails(as(ALICE).doc('messages/msg-1').update({attachments: ['x.jpg']}));
  });

  it('refuses the sender deleting their own message', async () => {
    await assertFails(as(ALICE).doc('messages/msg-1').delete());
  });

  it('serves the export\'s ranged thread query to a participant (MON-3)', async () => {
    await assertSucceeds(as(BOB).collection('messages')
        .where('conversationId', '==', 'conv-1')
        .where('timestamp', '>=', 1780000000000)
        .where('timestamp', '<', 1790000000000)
        .orderBy('timestamp')
        .get());
  });

  it('refuses the same ranged query to an outsider', async () => {
    await assertFails(as(CAROL).collection('messages')
        .where('conversationId', '==', 'conv-1')
        .where('timestamp', '>=', 1780000000000)
        .orderBy('timestamp')
        .get());
  });

  it('refuses the recipient deleting it', async () => {
    await assertFails(as(BOB).doc('messages/msg-1').delete());
  });

  // MON-16 completed the pin: the export's statement says "cannot be edited or deleted by either
  // parent once sent", so every field a reader relies on is refused to both parents, and the one
  // write that is allowed is shown to succeed so the refusals cannot pass on a broken fixture.

  it('still lets the recipient mark it read (the control for every refusal here)', async () => {
    await assertSucceeds(as(BOB).doc('messages/msg-1').update({isRead: true}));
  });

  it('refuses the recipient rewriting what the sender said', async () => {
    await assertFails(as(BOB).doc('messages/msg-1').update({content: 'Pickup at seven, you said'}));
  });

  it('refuses the recipient re-dating it', async () => {
    await assertFails(as(BOB).doc('messages/msg-1').update({timestamp: 1790000000000}));
  });

  it('refuses re-attributing it to the other parent, by id or by name', async () => {
    await assertFails(as(ALICE).doc('messages/msg-1').update({senderId: BOB}));
    await assertFails(as(BOB).doc('messages/msg-1').update({senderId: BOB}));
    await assertFails(as(ALICE).doc('messages/msg-1').update({senderName: 'Bob'}));
  });

  it('refuses changing its type or what it replies to', async () => {
    await assertFails(as(ALICE).doc('messages/msg-1').update({messageType: 'SYSTEM'}));
    await assertFails(as(ALICE).doc('messages/msg-1').update({replyToMessageId: 'msg-0'}));
  });

  it('refuses smuggling a rewrite in beside the one allowed field', async () => {
    await assertFails(as(BOB).doc('messages/msg-1')
        .update({isRead: true, content: 'Pickup at seven?'}));
    await assertFails(as(BOB).doc('messages/msg-1')
        .update({isRead: true, timestamp: 1790000000000}));
  });

  it('refuses replacing the whole message with set()', async () => {
    await assertFails(as(ALICE).doc('messages/msg-1').set({
      id: 'msg-1',
      conversationId: 'conv-1',
      senderId: ALICE,
      senderName: 'Alice',
      content: 'Something else entirely',
      timestamp: 1787000000000,
      messageType: 'TEXT',
      attachments: [],
      isRead: false,
      replyToMessageId: '',
    }));
  });

  it('refuses a stranger touching it at all', async () => {
    await assertFails(as(CAROL).doc('messages/msg-1').update({isRead: true}));
    await assertFails(as(CAROL).doc('messages/msg-1').delete());
  });
});
