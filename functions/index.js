/**
 * Firebase Cloud Functions для CoParently
 *
 * Обрабатывает отправку push-уведомлений при создании записей в notification_queue
 */

const functions = require('firebase-functions');
const admin = require('firebase-admin');

// Инициализация Firebase Admin SDK
admin.initializeApp();

/**
 * Cloud Function для отправки push-уведомлений.
 * Триггерится при создании нового документа в коллекции notification_queue.
 *
 * Структура документа notification_queue:
 * {
 *   targetUserId: string,
 *   data: {
 *     title: string,
 *     body: string,
 *     type: string (optional),
 *     eventId: string (optional),
 *     childInfoId: string (optional)
 *   },
 *   status: 'pending' | 'sent' | 'failed',
 *   createdAt: timestamp,
 *   sentAt: timestamp (optional),
 *   error: string (optional)
 * }
 */
exports.sendNotification = functions.firestore
  .document('notification_queue/{notificationId}')
  .onCreate(async (snap, context) => {
    const notificationId = context.params.notificationId;
    const notificationData = snap.data();

    console.log(`Processing notification ${notificationId} for user ${notificationData.targetUserId}`);

    try {
      // Получаем FCM токен целевого пользователя
      const userDoc = await admin.firestore()
        .collection('users')
        .doc(notificationData.targetUserId)
        .get();

      if (!userDoc.exists) {
        throw new Error(`User ${notificationData.targetUserId} not found`);
      }

      const userData = userDoc.data();
      const fcmToken = userData.fcmToken;

      if (!fcmToken) {
        console.log(`User ${notificationData.targetUserId} has no FCM token. Skipping notification.`);
        await snap.ref.update({
          status: 'skipped',
          error: 'No FCM token',
          sentAt: admin.firestore.FieldValue.serverTimestamp()
        });
        return null;
      }

      // Подготовка сообщения для отправки
      const message = {
        token: fcmToken,
        notification: {
          title: notificationData.data.title,
          body: notificationData.data.body
        },
        data: {
          // Преобразуем все значения в строки (требование FCM)
          type: notificationData.data.type || 'general',
          eventId: notificationData.data.eventId || '',
          childInfoId: notificationData.data.childInfoId || '',
          // Добавляем любые другие данные
          ...Object.keys(notificationData.data)
            .filter(key => !['title', 'body'].includes(key))
            .reduce((acc, key) => {
              acc[key] = String(notificationData.data[key]);
              return acc;
            }, {})
        },
        android: {
          priority: 'high',
          notification: {
            sound: 'default',
            color: '#4CAF50',
            channelId: 'coparently_notifications'
          }
        }
      };

      // Отправка уведомления
      const response = await admin.messaging().send(message);
      console.log(`Successfully sent notification ${notificationId}:`, response);

      // Обновление статуса в базе данных
      await snap.ref.update({
        status: 'sent',
        sentAt: admin.firestore.FieldValue.serverTimestamp(),
        messageId: response
      });

      return response;
    } catch (error) {
      console.error(`Error sending notification ${notificationId}:`, error);

      // Обновление статуса с ошибкой
      await snap.ref.update({
        status: 'failed',
        error: error.message,
        sentAt: admin.firestore.FieldValue.serverTimestamp()
      });

      // Повторная попытка для определенных ошибок
      if (error.code === 'messaging/registration-token-not-registered') {
        console.log(`FCM token for user ${notificationData.targetUserId} is invalid. Clearing token.`);
        // Очищаем недействительный токен
        await admin.firestore()
          .collection('users')
          .doc(notificationData.targetUserId)
          .update({
            fcmToken: admin.firestore.FieldValue.delete()
          });
      }

      throw error;
    }
  });

/**
 * Cloud Function для очистки старых уведомлений.
 * Запускается каждый день в 2:00 по UTC.
 * Удаляет уведомления старше 30 дней.
 */
exports.cleanupOldNotifications = functions.pubsub
  .schedule('0 2 * * *')
  .timeZone('UTC')
  .onRun(async (context) => {
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

    console.log(`Cleaning up notifications older than ${thirtyDaysAgo.toISOString()}`);

    const oldNotificationsQuery = await admin.firestore()
      .collection('notification_queue')
      .where('createdAt', '<', admin.firestore.Timestamp.fromDate(thirtyDaysAgo))
      .get();

    const batch = admin.firestore().batch();
    let count = 0;

    oldNotificationsQuery.forEach((doc) => {
      batch.delete(doc.ref);
      count++;
    });

    if (count > 0) {
      await batch.commit();
      console.log(`Deleted ${count} old notifications`);
    } else {
      console.log('No old notifications to delete');
    }

    return null;
  });

/**
 * Cloud Function для отправки уведомления о новом событии.
 * Триггерится при создании нового события в коллекции events.
 */
exports.onEventCreated = functions.firestore
  .document('events/{eventId}')
  .onCreate(async (snap, context) => {
    const eventData = snap.data();
    const eventId = context.params.eventId;

    console.log(`New event created: ${eventId}`);

    // Находим партнера пользователя
    const creatorDoc = await admin.firestore()
      .collection('users')
      .doc(eventData.createdByFirebaseUid)
      .get();

    if (!creatorDoc.exists) {
      console.log('Creator not found');
      return null;
    }

    const creatorData = creatorDoc.data();
    const partnerId = creatorData.partnerId;

    if (!partnerId) {
      console.log('Creator has no partner');
      return null;
    }

    // Создаем уведомление для партнера
    await admin.firestore()
      .collection('notification_queue')
      .add({
        targetUserId: partnerId,
        data: {
          title: 'New Event Created',
          body: `${creatorData.email || 'Your partner'} created a new event: ${eventData.title}`,
          type: 'event_created',
          eventId: eventId
        },
        status: 'pending',
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });

    console.log(`Notification queued for partner ${partnerId}`);
    return null;
  });

/**
 * Cloud Function для отправки уведомления об обновлении информации о ребенке.
 * Триггерится при обновлении документа в коллекции child_info.
 */
exports.onChildInfoUpdated = functions.firestore
  .document('child_info/{childInfoId}')
  .onUpdate(async (change, context) => {
    const newData = change.after.data();
    const oldData = change.before.data();
    const childInfoId = context.params.childInfoId;

    console.log(`Child info updated: ${childInfoId}`);

    // Проверяем, действительно ли изменились данные
    if (JSON.stringify(newData) === JSON.stringify(oldData)) {
      console.log('No actual changes detected');
      return null;
    }

    // Находим создателя
    const creatorDoc = await admin.firestore()
      .collection('users')
      .doc(newData.createdByFirebaseUid)
      .get();

    if (!creatorDoc.exists) {
      console.log('Creator not found');
      return null;
    }

    const creatorData = creatorDoc.data();
    const partnerId = creatorData.partnerId;

    if (!partnerId) {
      console.log('Creator has no partner');
      return null;
    }

    // Создаем уведомление для партнера
    await admin.firestore()
      .collection('notification_queue')
      .add({
        targetUserId: partnerId,
        data: {
          title: 'Child Info Updated',
          body: `${creatorData.email || 'Your partner'} updated information about ${newData.name}`,
          type: 'child_info_updated',
          childInfoId: childInfoId
        },
        status: 'pending',
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      });

    console.log(`Notification queued for partner ${partnerId}`);
    return null;
  });

