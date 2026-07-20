# Руководство по настройке Cloud Functions для CoPlanly

## Обзор

Cloud Functions автоматически обрабатывают push-уведомления и другие серверные задачи для приложения CoPlanly.

## Что было реализовано

### 1. Firebase Cloud Functions
- `sendNotification` - отправка push-уведомлений из очереди notification_queue
- `cleanupOldNotifications` - автоматическая очистка старых уведомлений (каждый день в 2:00 UTC)
- `onEventCreated` - автоматическое создание уведомления при добавлении события
- `onChildInfoUpdated` - автоматическое создание уведомления при обновлении информации о ребенке

### 2. Структура проекта
```
functions/
├── index.js           # Основной файл с функциями
├── package.json       # Зависимости Node.js
├── .eslintrc.js      # Конфигурация ESLint
├── .gitignore        # Игнорируемые файлы
└── README.md         # Документация функций
```

### 3. Конфигурационные файлы
- `firebase.json` - конфигурация Firebase проекта
- `firestore.indexes.json` - индексы для Firestore
- `firestore.rules` - правила безопасности (уже существовали)

## Установка и деплой

### Шаг 1: Установить Firebase CLI

```bash
npm install -g firebase-tools
```

### Шаг 2: Войти в Firebase

```bash
firebase login
```

### Шаг 3: Выбрать проект

```bash
firebase use --add
```
Выберите ваш Firebase проект из списка.

### Шаг 4: Установить зависимости

```bash
cd functions
npm install
```

### Шаг 5: Задеплоить функции

```bash
# Из корневой директории проекта
firebase deploy --only functions

# Или задеплоить конкретную функцию
firebase deploy --only functions:sendNotification
```

### Шаг 6: Создать индексы Firestore

```bash
firebase deploy --only firestore:indexes
```

## Проверка работы

### 1. Проверить в Firebase Console

1. Откройте [Firebase Console](https://console.firebase.google.com/)
2. Перейдите в ваш проект
3. Functions → Dashboard
4. Проверьте, что функции успешно задеплоены

### 2. Просмотреть логи

```bash
# Все логи
firebase functions:log

# Логи конкретной функции
firebase functions:log --only sendNotification

# Последние 50 записей
firebase functions:log --limit 50

# С автообновлением
firebase functions:log --follow
```

### 3. Локальное тестирование (опционально)

```bash
# Запустить эмуляторы
firebase emulators:start

# Или только Functions
cd functions
npm run serve
```

## Использование в приложении

### Автоматические уведомления

Функции автоматически создают уведомления при:
- Создании нового события (`onEventCreated`)
- Обновлении информации о ребенке (`onChildInfoUpdated`)

### Ручная отправка уведомлений

Для ручной отправки уведомлений используйте `FcmService` в Android приложении:

```kotlin
// Пример: отправка уведомления партнеру о новом событии
val notificationData = fcmService.createEventNotificationPayload(
    eventId = event.id,
    eventTitle = event.title,
    action = "created",
    performedBy = currentUser.email
)

fcmService.queueNotificationForUser(
    targetUserId = partnerId,
    notificationData = notificationData
)
```

## Мониторинг и отладка

### Проверка статуса уведомлений

В Firebase Console → Firestore → notification_queue:
- `status: 'pending'` - ожидает отправки
- `status: 'sent'` - успешно отправлено
- `status: 'failed'` - ошибка отправки
- `status: 'skipped'` - пропущено (нет FCM токена)

### Типичные проблемы

#### Уведомления не отправляются

1. **Проверьте FCM токен:**
   ```bash
   # В Firestore Console проверьте документ пользователя
   users/{userId} → fcmToken
   ```

2. **Проверьте логи функции:**
   ```bash
   firebase functions:log --only sendNotification
   ```

3. **Проверьте notification channel в приложении:**
   - Channel ID должен быть `coparently_notifications`
   - Importance: `IMPORTANCE_HIGH`

#### Ошибка "registration-token-not-registered"

Токен недействителен. Функция автоматически удаляет токен.
Приложение создаст новый токен при следующем запуске.

#### Ошибка прав доступа

Убедитесь, что Firebase Admin SDK имеет права:
```bash
firebase functions:config:set admin.project_id="YOUR_PROJECT_ID"
```

## Стоимость и лимиты

### Бесплатный план (Spark)
- 2M вызовов в месяц
- 400,000 ГБ-секунд
- 200,000 ЦП-секунд
- 5 ГБ исходящего трафика

### Платный план (Blaze)
Для production рекомендуется перейти на план Blaze:
```bash
firebase projects:update --billing=true
```

### Оптимизация затрат

1. **Используйте очередь уведомлений** вместо прямой отправки
2. **Настройте cleanupOldNotifications** для удаления старых записей
3. **Мониторьте использование** в Firebase Console

## Обновление функций

### Добавление новой функции

1. Отредактируйте `functions/index.js`
2. Добавьте новую функцию
3. Задеплойте:
   ```bash
   firebase deploy --only functions:newFunctionName
   ```

### Обновление существующей функции

```bash
# Обновить все функции
firebase deploy --only functions

# Обновить конкретную функцию
firebase deploy --only functions:sendNotification
```

### Удаление функции

```bash
firebase functions:delete functionName
```

## Безопасность

### Firestore Rules

Убедитесь, что правила в `firestore.rules` ограничивают доступ к notification_queue:

```javascript
match /notification_queue/{notificationId} {
  // Только авторизованные пользователи могут создавать уведомления
  allow create: if request.auth != null;
  // Только Cloud Functions могут обновлять/удалять
  allow update, delete: if false;
  // Чтение запрещено для клиентов
  allow read: if false;
}
```

### Environment Variables

Для хранения секретов используйте Firebase Config:

```bash
# Установить переменную
firebase functions:config:set someservice.key="THE API KEY"

# Получить все переменные
firebase functions:config:get

# Использовать в коде
const serviceKey = functions.config().someservice.key;
```

## Миграция и бэкапы

### Экспорт функций

```bash
firebase deploy --only functions --export-on-exit=./backup
```

### Восстановление

```bash
firebase deploy --only functions --import=./backup
```

## Дополнительные ресурсы

- [Firebase Cloud Functions Documentation](https://firebase.google.com/docs/functions)
- [Firebase Admin SDK](https://firebase.google.com/docs/admin/setup)
- [FCM Server Documentation](https://firebase.google.com/docs/cloud-messaging/server)
- [Firestore Security Rules](https://firebase.google.com/docs/firestore/security/get-started)

## Контрольный чеклист

- [ ] Firebase CLI установлен
- [ ] Выполнен `firebase login`
- [ ] Проект выбран через `firebase use`
- [ ] Зависимости установлены (`npm install` в functions/)
- [ ] Функции задеплоены (`firebase deploy --only functions`)
- [ ] Индексы созданы (`firebase deploy --only firestore:indexes`)
- [ ] Notification channel создан в Android приложении
- [ ] FCM токен сохраняется в Firestore
- [ ] Проверены логи (`firebase functions:log`)
- [ ] Протестирована отправка уведомлений

## Следующие шаги

1. ✅ Миграция с deprecated GoogleSignIn API
2. ✅ Реализация Cloud Functions для Push Notifications
3. ⏭️ Расширенная валидация форм

---

*Документ создан: 17 ноября 2025*

