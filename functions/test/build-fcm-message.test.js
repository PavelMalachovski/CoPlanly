/**
 * The `apns` block every FCM message carries for an iPhone (iPhone phase 4 part C, design §7.2).
 *
 * A data-only push is not reliably delivered to an iPhone in the background, so every message is
 * also an alert with `mutable-content: 1`: the phone's Notification Service Extension rewrites its
 * text in the reader's language, and when it cannot, iOS shows the server's fixed neutral text. The
 * thread id and the collapse id play the role of Android's tray id. Android ignores the block, so
 * `data` and `android` stay exactly what they were (`notification.test.js` holds the rest).
 */

const test = require('firebase-functions-test')();
const assert = require('assert');

describe('buildFcmMessage: the APNs alert', () => {
  let buildFcmMessage;

  before(() => {
    buildFcmMessage = require('../index').buildFcmMessage;
  });

  after(() => {
    test.cleanup();
  });

  it('gives a typed push the neutral alert, threaded by its type, with no collapse id', () => {
    const message = buildFcmMessage('token-1', {type: 'event_updated', actorName: 'Lana'});

    assert.deepStrictEqual(message.apns, {
      payload: {
        aps: {
          'alert': {title: 'CoPlanly', body: 'You have a new update in CoPlanly.'},
          'mutable-content': 1,
          'thread-id': 'event_updated',
        },
      },
      headers: {'apns-push-type': 'alert'},
    });
  });

  it('threads and collapses a chat message by its conversation', () => {
    const message = buildFcmMessage('token-1', {
      type: 'chat_message', conversationId: 'a__b', actorName: 'Lana', preview: 'Hi',
    });

    assert.strictEqual(message.apns.payload.aps['thread-id'], 'a__b');
    assert.strictEqual(message.apns.headers['apns-collapse-id'], 'a__b');
    assert.strictEqual(message.apns.headers['apns-push-type'], 'alert');
  });

  it('collapses a chat card by its entity, in its conversation\'s thread', () => {
    const message = buildFcmMessage('token-1', {
      type: 'chat_message', conversationId: 'a__b', activityKind: 'EVENT_CREATED',
      activityEntityType: 'EVENT', activityEntityId: 'e1',
    });

    assert.strictEqual(message.apns.payload.aps['thread-id'], 'a__b');
    assert.strictEqual(message.apns.headers['apns-collapse-id'], 'card_e1');
  });

  it('keeps a collapse id within the 64 bytes APNs accepts', () => {
    const message = buildFcmMessage('token-1', {
      type: 'chat_message', conversationId: 'c', activityKind: 'EVENT_CREATED',
      activityEntityType: 'EVENT', activityEntityId: 'x'.repeat(120),
    });

    assert.ok(Buffer.byteLength(message.apns.headers['apns-collapse-id']) <= 64);
  });

  it('threads a chat message with no conversation by its type', () => {
    const message = buildFcmMessage('token-1', {type: 'chat_message'});

    assert.strictEqual(message.apns.payload.aps['thread-id'], 'chat_message');
    assert.ok(!('apns-collapse-id' in message.apns.headers));
  });

  it('threads a malformed payload as the general type it falls back to', () => {
    assert.strictEqual(buildFcmMessage('t', undefined).apns.payload.aps['thread-id'], 'general');
  });

  it('never words the alert from the payload: the fallback is the server\'s own text', () => {
    const message = buildFcmMessage('token-1', {type: 'event_updated', title: 'Forged', body: 'Forged'});

    assert.deepStrictEqual(
        message.apns.payload.aps.alert, {title: 'CoPlanly', body: 'You have a new update in CoPlanly.'});
  });

  it('leaves data and android exactly as before', () => {
    const message = buildFcmMessage('token-1', {
      type: 'chat_message', conversationId: 'a__b', eventId: 42, unread: null,
    });

    assert.deepStrictEqual(message.data, {
      type: 'chat_message', eventId: '42', childInfoId: '', conversationId: 'a__b', unread: '',
    });
    assert.deepStrictEqual(message.android, {priority: 'high'});
    assert.deepStrictEqual(Object.keys(message).sort(), ['android', 'apns', 'data', 'token']);
    assert.ok(!('notification' in message));
  });
});
