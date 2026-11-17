# iPhone app, phase 4 part C: pushes and local notifications — design

Status: approved by the owner, 17 November 2026. Parent: `2026-11-16-iphone-phase-4-design.md` §2 and
§4 (part C). Builds on part B (MON-25, pull request #168), which put a request's deadline notice and
its end on the server.

## 1. Intent

After part B the iPhone still receives no push and shows no reminder: `NoPush`,
`NoEventReminders`, `NoRequestClockJobs`, `NoRequestClockNotices` and `NoSupportReminderJob` stand
in (`shared-data/src/iosMain/.../di/koin/IosUnavailable.kt`). A co-parenting app that does not tell a
parent the other one wrote, asked or paid is not usable, and an iPhone build cannot reach a user
without it. Part C brings the iPhone's notifications to Android's, with one wording for both.

## 2. Owner decisions (17 November 2026, chat)

1. **One push token per account.** No parent uses two phones on one account; `users/{uid}.fcmToken`
   stays one value, and the phone that registered last receives the pushes.
2. **Request reminders move to the server, for both platforms.** The answering parent's reminders
   ("Bob is waiting for your answer") are sent by the scheduled sweep part B built, as pushes, and
   the phones stop scheduling them. The server reads the request at the moment it sends, so no
   reminder is sent for a request already answered, withdrawn or ended.
3. **The push's text is written by one shared Kotlin implementation.** The wording that is
   Android's `PushNotifier` today moves to common Kotlin; Android calls it as now, and the iPhone's
   Notification Service Extension links a small framework built from the same code and strings.

4. **The neutral fallback is accepted until Apple grants the filtering entitlement.** Pushes are
   switched on for the iPhone at once, and the owner applies for
   `com.apple.developer.usernotifications.filtering` in parallel. Until it is granted, a push meant
   for another uid, or of a type the extension cannot word, shows the server's neutral fallback
   (names nothing) instead of nothing. Once granted, the extension suppresses both, and anti-goal 2
   and G1's "dropped" hold in full.

## 3. Goals

- **G1. The iPhone receives every push Android receives**, worded on the phone in the app's
  language, with the same text, and a tap opens the same screen (`PushDestination`) in the push's
  own family (item 15: the family is switched before the link is armed).
- **G2. One wording.** Every push type's title and body are produced by one Kotlin function over
  the push's data, in `commonMain`, read by Android's `PushNotifier` and by the iPhone's extension.
  The strings stay the five `push_strings.xml` (and the chat card strings `cardPushText` reads).
- **G3. Request reminders are the server's** (decision 2): a new server-only push type,
  `request_reminder`, sent by `sweepRequestClocks` at the planned times, for both platforms.
- **G4. Event reminders and the payer's child support reminder on the iPhone**, as local
  notifications, matching Android's rules as far as iOS lets a notification be decided in advance
  (§6).
- **G5. A push is asked for in context** on the iPhone too (UX item 7): the notification permission
  is requested where Android requests it (onboarding's Notifications step, the push switch, a
  reminder's choice), never on cold start.

## 4. Non-goals

- Several devices per account (decision 1).
- Rich notifications (images, actions on the notification itself, replying from the lock screen).
- Background sync or the widget: part D.
- Changing any push's wording or adding push types other than `request_reminder`.
- A notification badge count on the app icon.

## 5. Anti-goals (it fails even if it works)

- **A sentence written by the sender on the reader's lock screen.** Item 15 (SEC-3) holds: a push
  carries a type and facts, and the receiving phone writes the text. The server's fixed fallback
  (§7.2) is the app's own neutral text, never a client's.
- **The previous account's push on the lock screen.** A push whose `targetUserId` is not the uid
  signed in on this phone is never shown, on the iPhone as on Android.
- **A reminder about a request that no longer waits** (the reason for decision 2).
- **Two wordings that drift.** No Swift copy of the wording, no server copy.
- **A reminder at night.** The quiet hours (21:00–08:00 on the answering parent's clock,
  `ReminderPlan`) hold for server reminders too.

## 6. Constraints iOS imposes

- **A data-only push is not reliably delivered in the background**, so a push to an iPhone must be
  an alert with `mutable-content: 1`; the Notification Service Extension rewrites it before it is
  shown, within about 30 seconds and about 24 MB of memory. When the extension fails or runs out of
  time, iOS shows the alert as the server sent it: the fallback (§7.2).
- **A local notification cannot decide anything when it fires**: there is no code at fire time.
  Whatever Android checks at fire time is decided at scheduling time and undone by cancelling.
- **At most 64 pending local notifications per app.** Event reminders take the nearest ones; the
  phone keeps a few slots for the support reminder.
- **The extension is another process with another bundle.** It cannot read the app's Keychain item
  or the app's per-app language unless the app shares them through an App Group.

## 7. The design

### 7.1 The shared wording (decision 3)

- `PushNotifier.compose` and what it calls (`framedText`, `bodyOf`, the swap offer's wording, the
  "Answer by …" sentence, the expense amount, `cardPushText` for chat cards) move to a new
  Kotlin Multiplatform module, **`:push-wording`**, in `commonMain`: a pure function
  `PushWording.compose(type, data, reader): PushText?` where `reader` carries the language, the
  time zone and the 12/24-hour choice. A type it has no wording for answers null (item 15: dropped,
  never shown with a fallback).
- Its strings are the push and card strings, as Compose Multiplatform resources read without a
  composition, or generated into the module as Kotlin tables at build time from the same XML (the
  builder's choice, §11), so the extension does not carry the Compose runtime. Either way the XML
  in `shared-ui/src/commonMain/composeResources/` stays the one source, and check 7 of
  `check-invariants.js` still holds it.
- Money and dates go through the module's own small formatters, which the parity tests hold to
  what Android's `currencyFormat` and `java.time` write today for the same inputs.
- **Android**: `PushNotifier` keeps everything Android-specific (channels, `PendingIntent`s, the tray
  id, `BigTextStyle`, `inAppLanguage()`) and calls `PushWording.compose` for the text.
  `PushNotificationTest` and the existing unit tests must pass unchanged in what they assert.
- **iPhone**: the module builds a separate static framework, `PushWording`, which only the
  extension links. Its memory is measured on the simulator in CI and on a phone (device check).

### 7.2 Delivery to the iPhone

- **Registration.** The app registers for remote notifications, hands the APNs token to Firebase
  Messaging, and writes the FCM token to `users/{uid}.fcmToken` through the existing
  `PushDevice.updateUserToken`, replacing `NoPush`. `PushRegistration` (the push switch, the token's
  removal at sign-out and deletion) gets its iPhone half. The APNs entitlement
  (`aps-environment`) is added; the APNs key goes into the Firebase console (owner step).
- **The server's message** (`buildFcmMessage`) gains an `apns` block for every push: an alert with a
  fixed neutral title and body (`CoPlanly` / `You have a new update in CoPlanly.`, English, decided
  by the server, never by a client), `mutable-content: 1`, `thread-id` and the collapse id that
  play the role of Android's tray id (one per conversation for messages, one per card), and the
  same `data`. Android ignores the `apns` block, so Android's delivery is unchanged.
- **The extension** reads the push's data, drops it (shows nothing) when `targetUserId` is not the
  uid the app shared through the App Group, calls `PushWording.compose` with the language the app
  shared, and replaces the alert's title and body. A type it cannot word: until Apple grants the
  notification filtering entitlement (`com.apple.developer.usernotifications.filtering`), iOS will
  still show the fallback, so the owner applies for it (owner step); once granted, the extension
  suppresses such a push as Android drops it.
- **The tap** opens the app with the push's data; the shell reads `PushDestination` and the family
  as `MainActivity.readLaunchIntent` does, switching the family before arming the link.
- **The App Group** (`group.app.coplanly`) carries what the extension needs and nothing else: the
  signed-in uid and the app's resolved language (`AppLanguageRule`'s answer), written by the app on
  start, on sign-in and sign-out, and on a language change. Both are cleared at sign-out and
  deletion.

### 7.3 Request reminders on the server (decision 2)

- **The plan.** The answering phone already writes its plan at delivery
  (`reminderTimesMillis`, made by `ReminderPlan` in its own zone). The server sends a
  `request_reminder` push at each planned time, from the same sweep as part B, every 5 minutes.
- **When no phone delivered the clock** (the answering parent has not opened the app since the
  request), the server plans by itself with a port of `ReminderPlan`, in the answering parent's zone
  from a new profile field, **`users/{uid}.timeZone`** (an IANA name, written by both apps on start
  and when the zone changes). With no zone stored (an older build), the server waits for the
  phone's delivery, as today. `ReminderPlan`'s arithmetic is shared between Kotlin and JavaScript
  through a fixture both test suites read, as the request clock's is.
- **At send time** the step re-reads the request inside its transaction (part B's `stepClock`): no
  push for a request answered, withdrawn or past its last chance, and none in the quiet hours of the
  stored zone (a sweep delayed into the night moves the reminder to the next 08:00, or drops it if
  that is past the deadline).
- **The push** `request_reminder` carries `familyId`, the request's kind and the proposer's first
  name, opens the Requests inbox on the schedule channel, and is worded from the strings the
  phone's local reminder uses today. It is server-only (item 15's four places: `PushPayload`, the
  rules' allow-list refusal, `PushNotifier`'s table, the five `push_strings.xml`).
- **Counting.** The server records each reminder sent in a server-only field; the phone no longer
  writes `remindersShown`. A reminder counts as given when it was sent, not shown (the push
  cannot report that), which `docs/legal/DPIA.md` R22 and item 42 then say.
- **The phones.** Android stops scheduling request reminders; a reminder job an older build queued
  finishes without acting, as part B's deadline jobs do. The iPhone never had them.
- **During the transition** a parent on an older Android build can get a reminder twice (its own
  and the server's), accepted as in part B.

### 7.4 Event reminders on the iPhone (local)

- `IosEventReminders` replaces `NoEventReminders`: `UNUserNotificationCenter` requests with a
  calendar trigger at the reminder's instant, one per event, identified by the event id (as Android's
  alarm is), worded by `PushWording`'s reminder text in the app's language.
- It arms the next occurrence (`ReminderPlanner`), re-arms on each sync and on each app start, and
  keeps only the nearest reminders within the 64-notification limit, leaving four slots free.
  `cancelAll` runs at sign-out, account switch and deletion, as on Android.
- What Android checks when the alarm fires (that the event still exists and the occurrence is still
  one) is checked when the app last synced; an event deleted on the other phone while this app was
  never opened can still remind once. Recorded, not hidden (device check).

### 7.5 Child support reminder on the iPhone (local)

- `IosSupportReminders` replaces `NoSupportReminderJob`: the payer's reminder for the next due day,
  at 0, 1, 3 or 7 days before (the parent's choice), outside the quiet hours, scheduled when the
  agreement or the choice changes and on each app start.
- Recording "I paid" on this phone cancels it; a month already paid is never scheduled. What
  Android's worker reads from the server every six hours (a payment recorded elsewhere) is read on
  the next app start.

### 7.6 The permission

`PermissionRequester`'s iPhone half asks `UNUserNotificationCenter` for alert, sound and badge
where Android asks for `POST_NOTIFICATIONS`; the microphone stays part E's.

## 8. Approaches considered

- **Who writes the push's text on the iPhone** (owner chose 1):
  1. shared Kotlin wording in a small framework for the extension — one wording; memory risk in the
     extension, measured;
  2. a Swift copy of the wording with the same strings and a parity test — safe on memory, two
     implementations;
  3. the server words the alert in the profile's language — no extension, a third implementation,
     and a language change reaches the server late.
- **Request reminders** (owner chose 2): local on the iPhone, re-planned on each sync and cancelled
  by the withdrawal's push, with a residual stale reminder; or on the server for both platforms,
  never stale, not sent while the server cannot reach the phone.
- **Tokens** (owner chose 1): one per account, or a list per device.

## 9. Legal and documents

- Privacy policy, `RECORDS-OF-PROCESSING.md`, `DATA-SAFETY.md`: the FCM token on the iPhone (Apple's
  APNs as a processor), the time zone on the profile and why, server reminders. `DPIA.md` R22.
- CLAUDE.md items 15 (the new type, the `apns` block, the shared wording) and 42 (reminders on the
  server); ROADMAP MON-24 (the stand-ins replaced) and MON-25.
- `docs/DEVICE-CHECKLIST.md`: pushes on a real iPhone (APNs only works on a device), the extension's
  memory, a reminder at the planned time, the lock-screen fallback.

## 10. Tests

- `:push-wording`: every type in `PUSH_TEXT` worded in the five languages, compared with what
  Android's `PushNotifier` wrote before the move (a golden fixture generated once from the old code
  and committed), on the JVM and the iOS simulator.
- Android: the existing `PushNotificationTest`, `PushRoutingTest` and `PushNotifierRequestCodeTest`
  unchanged.
- Functions: `buildFcmMessage`'s `apns` block; `request_reminder` sent at the planned times, never
  for an answered, withdrawn or ended request, never at night, planned by the server only with a
  zone; the `ReminderPlan` port against the shared fixture.
- Rules: `timeZone` writable by the profile's owner only, bounded; `request_reminder` refused from a
  client.
- e2e: `TwoParentServerClocksTest` gains the reminder (queued to the right parent, not after an
  answer); `tools/e2e/coverage.json` names it.
- iPhone: unit tests of the local reminders' scheduling (the 64 limit, cancelAll) on the simulator;
  the extension's wording run through the XCUITest walk with a simulated push
  (`xcrun simctl push`), including a push for another uid that shows nothing.

## 11. Calls made after the builder check (the author's, for the owner to overturn)

1. **Two plans, one rule.** The server's own plan (§7.3, made only when no phone delivered the
   clock) goes in a server-only field, `serverReminderTimesMillis`; the phone's delivery write stays
   as the rules allow it today. The sweep sends from the phone's plan when one exists, else from the
   server's; a reminder already sent is never sent again, whichever plan named it. The count of
   reminders sent is the server-only `serverRemindersSent`. The screens ("reminded N of M",
   `CardClock`, `RequestClockText`) read planned as the phone's count, else the server plan's, and
   sent as the larger of `serverRemindersSent` and the `remindersShown` an older build may still
   write.
2. **Android's text does not change.** Money and dates in `:push-wording` go through
   `expect`/`actual` formatters, the calls Android makes today on Android (`currencyFormat`,
   `java.time`) and Foundation on the iPhone, as `ReaderMoneyText` and `ReaderDateText` already do.
   The two platforms may differ where their locale data differ; the golden fixture is Android's.
3. **The module owns every notification's wording**: the pushes, the chat cards' push text, the
   event reminder, the child support reminder and `request_reminder`. Android's `DueReminders`,
   `EventReminderScheduler` and `SupportReminderWorker` switch to it, with golden fixtures of their
   current text; the iPhone's local notifications are worded by it in the app process.
4. **The chat cards' wording decisions move down.** What `cardPushText` reads of `ActivityCardText`
   (which string a card kind takes, and its arguments) moves into `:push-wording` without Compose;
   `:shared-ui` depends on the module and reads the same decisions. In scope for this part.

## 12. Left to the builder

- Whether the strings reach `:push-wording` as Compose Multiplatform resources or as generated
  Kotlin tables, decided by the extension's measured memory.
- The exact fallback text's wording, within "neutral, English, names nothing".
- How the reminder's server-only count field is named, within part B's rule (server-only fields
  carry the `server` prefix and no client write may carry one).
- The task order, provided Android's pushes keep working at every commit.
