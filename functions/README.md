# CoPlanly Cloud Functions

Firebase Cloud Functions для обработки push-уведомлений и автоматизации в приложении CoPlanly.

## Функции

### 1. sendNotification
Отправляет push-уведомления пользователям при создании записи в коллекции `notification_queue`.

**Триггер:** onCreate в `notification_queue/{notificationId}`

**Поля документа notification_queue:**
```javascript
{
  targetUserId: string,        // Firebase UID пользователя
  data: {
    title: string,             // Заголовок уведомления
    body: string,              // Текст уведомления
    type: string,              // Тип уведомления (optional)
    eventId: string,           // ID события (optional)
    childInfoId: string        // ID информации о ребенке (optional)
  },
  status: 'pending',           // Статус: pending/sent/failed/skipped
  createdAt: timestamp,
  sentAt: timestamp,           // Время отправки (optional)
  error: string                // Сообщение об ошибке (optional)
}
```

### 2. cleanupOldNotifications
Автоматически удаляет старые уведомления (старше 30 дней).

**Триггер:** Каждый день в 2:00 UTC

### 3. onEventCreated
Автоматически создает уведомление для партнера при создании нового события.

**Триггер:** onCreate в `events/{eventId}`

### 4. onChildInfoUpdated
Автоматически создает уведомление для партнера при обновлении информации о ребенке.

**Триггер:** onUpdate в `child_info/{childInfoId}`

## Установка

### 1. Установить Firebase CLI
```bash
npm install -g firebase-tools
```

### 2. Войти в Firebase
```bash
firebase login
```

### 3. Инициализировать проект (если еще не сделано)
```bash
firebase init functions
```
Выберите:
- JavaScript
- Use ESLint: Yes
- Install dependencies: Yes

### 4. Установить зависимости
```bash
cd functions
npm install
```

## Разработка

### Локальное тестирование
```bash
# Запустить эмуляторы Firebase
firebase emulators:start

# Или только Functions
npm run serve
```

### Проверка кода
```bash
npm run lint
```

## Деплой

### Деплой всех функций
```bash
firebase deploy --only functions
```

### Деплой конкретной функции
```bash
firebase deploy --only functions:sendNotification
```

## Требования к Android приложению

### 1. FCM Token
Приложение должно сохранять FCM токен в документе пользователя:

```kotlin
// В FcmService.kt
fun updateUserToken(token: String) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    FirebaseFirestore.getInstance()
        .collection("users")
        .document(userId)
        .update("fcmToken", token)
}
```

### 2. Notification Channel
Создать канал уведомлений в Android приложении:

```kotlin
// В MainActivity или Application
private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "coparently_notifications",
            "CoPlanly Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications about co-parenting events"
            enableLights(true)
            lightColor = Color.GREEN
            enableVibration(true)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}
```

## Структура базы данных

### Коллекция users
```javascript
{
  uid: string,
  email: string,
  partnerId: string,
  fcmToken: string,  // FCM token для push-уведомлений
  createdAt: timestamp
}
```

### Коллекция notification_queue
```javascript
{
  targetUserId: string,
  data: {
    title: string,
    body: string,
    type: string,
    eventId: string,
    childInfoId: string
  },
  status: 'pending' | 'sent' | 'failed' | 'skipped',
  createdAt: timestamp,
  sentAt: timestamp,
  messageId: string,
  error: string
}
```

## Мониторинг

### Просмотр логов
```bash
# Все логи
firebase functions:log

# Логи конкретной функции
firebase functions:log --only sendNotification

# Последние N записей
firebase functions:log --limit 50
```

### Метрики в Firebase Console
1. Перейти в Firebase Console > Functions
2. Выбрать функцию для просмотра:
   - Количество вызовов
   - Время выполнения
   - Ошибки
   - Использование памяти

## Troubleshooting

### Ошибка: "registration-token-not-registered"
FCM токен недействителен. Функция автоматически удаляет токен из документа пользователя.
Приложение должно обновить токен при следующем запуске.

### Ошибка: "No FCM token"
Пользователь еще не зарегистрировал FCM токен.
Проверьте, что приложение корректно сохраняет токен в Firestore.

### Уведомления не приходят
1. Проверьте, что FCM токен сохранен в документе пользователя
2. Проверьте логи функции: `firebase functions:log`
3. Убедитесь, что notification channel создан в приложении
4. Проверьте, что приложение имеет разрешения на уведомления

## Стоимость

Cloud Functions для Firebase использует модель оплаты pay-as-you-go.
Бесплатный план (Spark) включает:
- 2M вызовов/месяц
- 400,000 ГБ-секунд
- 200,000 ЦП-секунд
- 5 ГБ исходящего трафика

Для production рекомендуется план Blaze.

## Безопасность

1. **Firestore Rules:** Убедитесь, что правила Firestore разрешают создание документов в notification_queue только авторизованным пользователям
2. **Валидация данных:** Функция проверяет существование пользователя перед отправкой уведомления
3. **Обработка ошибок:** Все ошибки логируются и сохраняются в документе для отладки

## Дополнительно

### Добавление новых типов уведомлений
1. Создайте новый триггер в `index.js`
2. Используйте структуру notification_queue для создания уведомления
3. Задеплойте функцию

### Настройка расписания
Измените cron-выражение в функции `cleanupOldNotifications`:
```javascript
.schedule('0 2 * * *')  // Каждый день в 2:00 UTC
```

Формат cron: `минута час день_месяца месяц день_недели`

## Google OAuth (SEC-1 §2)

The token exchange for Google Calendar happens **here**, not in the app. Google's web OAuth
client — the client type the Calendar scope is granted through — requires a client secret to
redeem an authorization code and to refresh an access token, and that secret used to be compiled
into every APK. An APK is not a secret.

Two environment variables in `functions/.env`, and **the app's Calendar sign-in stops working
until they are set and the functions deployed**:

```
GOOGLE_OAUTH_CLIENT_ID=<the web client id, the same value as default_web_client_id>
GOOGLE_OAUTH_CLIENT_SECRET=<its secret, from Google Cloud console → Credentials>
```

With either missing the callables answer `failed-precondition` / `oauth-not-configured` rather
than failing obscurely — the same posture `sendEmailInvitation` takes for its mail provider.

Two things worth knowing before touching this:

- **`google_oauth/{uid}` stores a SHA-256 of the refresh token, never the token.** It exists so
  the refresh callable is not an oracle: without it, moving the secret out of the APK would leave
  a function that would happily refresh *any* stolen refresh token for *any* signed-in caller,
  which is exactly the capability the secret's removal takes away. `firestore.rules` denies every
  client both reads and writes; only the callables, which run as Admin, touch it.
- **Anyone whose Calendar was connected before this shipped re-consents once.** Their refresh
  token predates the fingerprint, and an unknown token is refused rather than trusted on first
  use — trusting it would let whoever presents a stolen token first bind it to themselves. The
  app already prompts to reconnect when a refresh fails, so this surfaces as that prompt.

## Calendar feed (MON-17)

A read-only iCalendar subscription for a parent whose phone cannot run the app. Five functions,
all shipped by the ordinary `firebase deploy --only functions`:

| Function | Kind | What it does |
| --- | --- | --- |
| `createCalendarFeed` | callable | `{familyId, locale}` → `{feedId, url, webcalUrl}`. The caller must be live in the family (both profiles exist and name each other). Returns the token **once**, inside the URL. At most 10 live links per parent. |
| `listCalendarFeeds` | callable | The caller's links — `feedId`, `familyId`, `createdAtMillis`, `lastUsedAtMillis`. Never a token or a hash. |
| `revokeCalendarFeed` | callable | `{feedId}` → deletes the caller's link. Idempotent. |
| `calendarFeed` | HTTPS (`onRequest`) | `GET /calendarFeed/<token>.ics` → `text/calendar`. No sign-in: the token is the authorisation. |
| `sweepIdleCalendarFeeds` | scheduled, 04:30 UTC | Deletes links no calendar has fetched for 90 days. |

What to know before touching it:

- **Only `sha256(token)` is stored** — it is the document id in `calendar_feeds`, which
  `firestore.rules` closes to every client. Never log a request path or a token; the handler logs
  only an error's message.
- **The URL** defaults to `https://us-central1-<project>.cloudfunctions.net/calendarFeed/<token>.ics`.
  Set `CALENDAR_FEED_BASE_URL` in `functions/.env` (no trailing slash) if the functions move region
  or a Hosting rewrite / custom domain fronts them — links already handed out keep the old base.
- **The cache and rate limit are per instance** (15-minute render cache, 30 requests per token per
  10 minutes). The feed record itself is read on every request, so a revoke is immediate.
- **The custody port lives in `calendar-feed.js`** and must agree with `CustodyResolver` /
  `ContactWindowCodec` on the Android side. `test/calendar-feed.test.js` pins it against fixtures;
  add one there when the Kotlin changes.
- **What is never served**: private events, tombstoned events, events of another family or not
  created by one of the family's two parents, and anything that is not the calendar (chat,
  expenses, children's records). Parents are titled by name, never by slot.

## Verifiable exports (MON-16)

`export-receipts.js` holds the logic, `index.js` the three callables. The design is
`docs/DESIGN-court-record.md` §10; what follows is what an operator needs.

| callable | auth | what it does |
| --- | --- | --- |
| `reserveExportRecordId({familyId, fromDate, toDate, format})` | a parent of `familyId` (decided from the id, not from a live pairing), or `''` for no co-parent; 20 per account per 10 minutes per instance | mints `export_receipts/{recordId}` in state `reserved`, bound to the caller, the family, the period and the format — **before** the phone renders the file, because the hash must cover the id it prints |
| `registerExportReceipt({recordId, sha256, byteLength})` | the parent who reserved the id, within an hour | records the SHA-256 (64 lowercase hex characters) and the length, with `recordedAt` from the function's clock. Create-once: another hash is `already-exists`; the same hash returns the original time |
| `verifyExport({sha256} \| {recordId})` | **none** — a lawyer has no account; 30 per address per 10 minutes per instance | `{found: false}`, or `recordId`, `recordedAt`/`recordedAtMillis`, `fromDate`, `toDate`, `format`, `byteLength` and `generatedBy: "one of the family's parents"`. **Never** a uid, a family id or a name |

- **Deploy:** `firebase deploy --only functions` for the three callables and the account-deletion
  change, `firebase deploy --only firestore:rules` for the closed `export_receipts` block, and
  `firebase deploy --only hosting` for `web/verify/`. Until the functions exist every export says
  "not registered", which is honest and loses nothing.
- **`verifyExport` must be invokable by `allUsers`.** A 1st-gen callable deployed by the Firebase
  CLI is public by default; if an organisation policy strips that, the verification page answers
  every file with a network error. Check the Cloud Functions Invoker role after the first deploy.
- **`maxInstances: 10`** on `reserveExportRecordId` and `verifyExport` is what makes the in-memory,
  per-instance rate limits a bound on the service. Raising it raises the ceiling proportionally.
  No address is stored anywhere.
- **Account deletion scrubs receipts, never deletes a registered one** (`scrubReceipts`):
  `generatorUid` and `familyId` are blanked and the hash is kept, so the other parent's filed
  evidence still verifies. Reservations that never received a hash are deleted.
- **Region.** The callables run in `us-central1` with every other function here; `web/verify/`
  hard-codes that base URL (`FUNCTIONS_BASE`) and must change with it.

## Admin operations

### The multi-family migration (run these in order)

Three callables and one deploy turn on family-scoped isolation — the property that a co-parent in
one family can reach nothing belonging to a co-parent in another. **The order matters and the
failure mode of getting it wrong is visible to users**, so run them one at a time and read each
summary before starting the next. All three are gated on the same `BACKFILL_ADMIN_UIDS`
allow-list described under `backfillParentSlots` below.

```bash
firebase deploy --only functions          # 1. ship the callables
# 2. every live pair gets families/{id} with members, slots and caresFor
# 3. every existing record gets its familyId
firebase deploy --only firestore:rules    # 4. turn the isolation on
```

Steps 2 and 3 are invoked as callables (from the app, a script, or the Firebase console's
functions shell), not from the CLI:

| step | callable | what it writes |
| --- | --- | --- |
| 2 | `backfillFamilyDocuments` | `families/{id}`: `members`, `slots`, `caresFor` |
| 3 | `backfillRecordFamilyIds` | `familyId` on events, expenses, budgets, child\_info, pets, change\_requests, and on calendar-friend grants |

Both are idempotent — a second run reports everything as skipped — and both report per-reason
counts rather than a bare "ok", so a pair they declined to touch is visible rather than silent.

**Step 1 also deploys the `onFamilyCreated` trigger**, which stamps the pre-pairing records of both
members whenever a `families/{id}` document is created. That includes the documents step 2
creates, so by the time step 3 runs much of its work may already be done — expect its `stamped`
count to be smaller than the history suggests. That is the trigger working, not step 3 failing;
run step 3 anyway, because it is the only pass that also covers pairs whose family document
already existed.

#### What step 3 stamps, and what it deliberately leaves

`backfillRecordFamilyIds` (and the trigger, which applies the same policy through
`stampOwnBlankFamilyIds`) writes `familyId` only on a record whose `familyId` is absent or `""`,
and only when its author's family is not a guess: **exactly one live, mutual co-parent, and no
trace of an earlier one**. It never overwrites a stamped record and never touches
`deletedAtMillis`/`deletedBy` on a tombstone. Everything else is skipped and counted:

| `skippedReasons` | meaning | what to do |
| --- | --- | --- |
| `unpaired` | nobody to share with — `""` is the right value | nothing |
| `ambiguous` | the author co-parents with two or more people, so a blank record could belong to either family | nothing server-side can decide this; these records stay visible to their author only (CLAUDE.md item 22) |
| `priorRelationship` | one co-parent now, but an earlier one on the evidence (an unfinished `pendingRevocationOf`, an accepted co-parent invitation with somebody else, or a record naming another family or adult) | as `ambiguous`: stamping would move the old household's records into the new one |
| `notMutual` / `missingAccount` | a half-ended pairing, or a deleted co-parent | finish the unpair; do not hand-stamp |

`unresolved` is the total number of blank records those skips left behind, so the cost of a skip
is visible rather than a bare count of people.

**Re-running step 3 later is safe and is the repair path** for CLAUDE.md item 22 (expenses and
budgets recorded before pairing uploaded with `familyId: ""`): anything the trigger missed — a
pair formed before it was deployed, a failed run, a pre-pairing upload that landed after it —
is stamped on the next run.

**Run `backfillParentSlots` before step 2** if any pair still shares a slot. Step 2 records the
slots the two profiles hold and counts how many pairs came out indistinct (`sameSlot`); it does
not decide who is parent 1, because that needs the invitation. Running them the other way round
loses nothing — `backfillParentSlots` updates an existing family's `slots` as it separates a
pair — but you then have to know it already went.

**Do not deploy the rules (step 4) before step 3 finishes.** `expenses` and `budgets` are read by
membership of the record's own family, with no fallback to "a co-parent of the author". That
fallback was tried and removed: Firestore validates a query by its *structure*, so while any
branch of the rule mentioned `isPartnerOf(createdByFirebaseUid)`, the client's old
`whereIn('createdByFirebaseUid', […])` query satisfied it and Firestore served a second family's
documents — the leak survived a rule that looked closed. Proved against the emulator in
`firestore-tests/rules/family-isolation.test.js`. The consequence is that a record with no
`familyId` is readable only by its author, so deploying step 4 early leaves each co-parent's
expense and budget history looking empty on the other phone until step 3 completes. Nothing is
lost — Room is the source of truth on each device — but it is alarming to watch.

### backfillParentSlots

`backfillParentSlots` is an operator-only `onCall` callable (in `index.js`) that re-slots
co-parent pairs created before pairing started assigning distinct `"mom"`/`"dad"` slots. It
is not a user-facing feature — every existing pair before this change has both parents in the
same slot, and this migration is the one-time fix for that, invoked manually.

**It refuses every caller by default.** The gate reads a comma-separated allow-list of
Firebase Auth UIDs from the `BACKFILL_ADMIN_UIDS` environment variable
(`backfillAdminUids()`/`isBackfillOperator()` in `index.js`); with nothing configured, the
list is empty and the callable rejects every caller with `permission-denied`.

**To open it for an operator:**

1. Copy `functions/.env.example` to `functions/.env` (the latter is gitignored — never
   commit it) and set `BACKFILL_ADMIN_UIDS` to the operator's Firebase Auth UID(s), comma
   separated:
   ```
   BACKFILL_ADMIN_UIDS=uid-of-operator-1,uid-of-operator-2
   ```
2. Deploy the function so the CLI picks up the new value:
   ```bash
   firebase deploy --only functions:backfillParentSlots
   ```
   The Firebase CLI loads `functions/.env` into `process.env` for every deployed function —
   1st gen and 2nd gen alike — with no extra code needed to bind it. This project
   deliberately does **not** use a Secret Manager secret (`firebase functions:secrets:set`)
   for this value: a secret additionally requires the function to declare
   `functions.runWith({secrets: [...]})`, and without that binding the secret's value never
   reaches `process.env` at runtime even though `firebase functions:secrets:set` reports
   success — silently leaving the gate impossible to open by the route that looks like it
   should open it. A plain `.env` variable has no such trap.
3. Sign in to the app as one of the configured operator UIDs and invoke the callable with no
   arguments (e.g. from `firebase functions:shell`, or any authenticated client SDK call to
   `backfillParentSlots`). It returns a summary — `scanned`/`updated`/`skipped`/`failed`
   counts, with `skipped` broken down by reason — rather than a bare success flag, so the
   result can be checked afterwards.

**Do not invoke it before the Task 12b client (the one that reacts to a slot changing outside
the accept flow) has reached users.** Re-slotting a pair on the server before that client
ships leaves the affected parent's app stamping new records with their *old* slot while the
co-parent's app already sees the new one — the exact "history reads as my co-parent's"
failure the accept-path re-stamp exists to prevent, delivered by this migration instead.

## Scheduled sweeps

Five daily jobs, an hour apart so they never contend (all UTC):

| time | function | what it removes |
| --- | --- | --- |
| 02:00 | `cleanupOldNotifications` | `notification_queue` entries older than 30 days |
| 03:00 | `sweepExpiredGuests` | expired guest grants on `child_info` (from `guests` and `sharedWith`) |
| 04:00 | `sweepDeletedDocuments` | tombstones older than 90 days (do not shorten — CLAUDE.md item 14) |
| 05:00 | `sweepLapsedCalendarFriends` | `calendar_friends/{uid}` grants whose `expiresAtMillis` has passed |
| 06:00 | `sweepLapsedProfessionalGrants` | `professional_grants/{familyId}__{proUid}` grants whose `expiresAtMillis` has passed (MON-18) |

None of them enforces anything: the rules already refuse an expired guest or friend from the
instant the grant ends. They remove the rows that would otherwise linger in the parents' lists.
Both grant sweeps share `sweepLapsedByExpiry`, which never deletes a grant without a positive numeric `expiresAtMillis` —
the callable does not write one, and the rule reads a missing expiry as 0 and admits nothing
through it, so such a row is inert; deciding what it means is left to a person.

## Professional access (MON-18)

A mediator, lawyer, guardian ad litem or therapist reads **one** family's calendar, custody
schedule and parenting plan — never chat, money or child records — once **both** parents have
consented, until a date at most 180 days out.

- **`acceptProfessionalInvitation`** is the fourth redemption callable, beside
  `acceptPairingInvitation`, `acceptGuestInvitation` and `acceptCalendarFriendInvitation`. It
  accepts only `kind: 'professional'`; the pairing callable refuses that kind by name
  (`professional-invitation`), and the guest and friend callables refuse it as not theirs. It
  checks the family on the invitation is a live pairing **from both sides** (no fallback to the
  family on screen), refuses a parent of that family, requires a known role, clamps the end to
  `PROFESSIONAL_MAX_DAYS` (180) from now, and writes one document,
  `professional_grants/{familyId}__{proUid}`, with the inviting parent's consent only. It copies
  the professional's name and Google photo, and the two parents' names and slots, because the
  professional may read no profile. It queues `professional_access_requested` (a type and a
  name, no sentence) to both parents.
- The **second consent** is a client write the rules restrict to the co-parent's own key; there
  is no callable for it. **Revocation** is a client delete by either parent.
- **`unpairCoParent`** deletes every grant over the ended family, after its transaction and
  before the audience sweep; a failure there is logged, not thrown. **`deleteAccount`** deletes
  grants over the account's families and grants the account holds as a professional.
- **Deploy both halves together**: the functions (callable + sweep) and the rules
  (`professional_grants` block, `isProfessionalOf`). Tests: `test/professional-invite.test.js`
  here, `rules/professional-access.test.js` in `firestore-tests/`.

## Лицензия

© 2025 CoPlanly. All rights reserved.

