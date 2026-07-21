# CLAUDE.md

Guidance for Claude Code (and other AI assistants) working in this repository.

## What this project is

CoPlanly — an Android shared-calendar app for separated parents. Kotlin + Jetpack Compose
(Material 3), Clean Architecture with Hilt, Room as the offline-first source of truth,
Firebase (Auth/Firestore/FCM) for sync between the two parents, Google Calendar integration,
Gemini for AI features.

**The authoritative roadmap is `docs/CoPlanly/MVP_phases.md`** (not `.cursor/roadmap.md`,
which is the historical original plan). MVP 1 is complete; MVP 2 (receipts, change requests,
dashboards) is next. The latest full audit lives in `docs/AUDIT-2026-07.md`.

## Agreed UX/UI direction (July 2026 design review)

Decisions locked in with the product owner after a live walkthrough — follow these when
touching the UI:

1. **Bottom navigation bar** (Calendar / Chat / Expenses / Settings) is the target
   top-level navigation. Until it lands, `Screen.Conversations/Chat/Expenses/AddExpense/
   Budgets/EventList` are registered in `NavGraph` but unreachable from the UI;
   `QuickActionsBottomSheet` is dead code — wire it up or remove it with the nav work.
2. **Toolchain upgrade approved**: compileSdk/targetSdk 34 → 36, Compose BOM
   2024.11 → 2025.x, Material 3 1.4+ (M3 Expressive), predictive back
   (`android:enableOnBackInvokedCallback="true"`).
3. **Calendar**: replace the hand-rolled threshold swipes in `DayWeekView`/`MonthView`
   with `HorizontalPager` physics; month view becomes a classic grid starting at the 1st
   with horizontal month paging (not the current selected-week vertical buffer).
4. **Event tap opens a preview bottom sheet** (details + Edit/Delete); the editor is the
   second step. Event form gets a sticky bottom Save button (not only the top-right ✓).
5. **Color semantics**: Mom-pink/Dad-blue are reserved for parent identity only. Neutral
   selected states (chips, toggles) must use `primary`, not the pink `secondary` —
   today `secondary = MomPink` leaks into every selected FilterChip.
6. **Notification permission** must be requested contextually (first reminder/sync
   enable), not via the system dialog on every cold start in `MainActivity`.

Known crash found during the review: upgrading an install with a very old DB
(schema v3) throws "A migration from 3 to 9 was required but not found" — the migration
chain in `DatabaseMigrations` starts later; add the missing path or a guarded
destructive fallback for pre-chain versions.

## Build & verify

```bash
./gradlew assembleDebug          # main build — run after every code change
./gradlew testDebugUnitTest      # JVM unit tests (MockK + coroutines-test + Turbine)
./gradlew lint detekt            # static analysis (detekt config in app/config/detekt)
```

- Windows dev machine; Gradle wrapper works from Git Bash and PowerShell.
- `google-services.json` is required for the Google Services plugin, but the build
  degrades gracefully if it is missing (see the conditional apply in `app/build.gradle.kts`).
- No GitHub CI: builds and tests are run locally
  (`./gradlew clean assembleDebug testDebugUnitTest`). After switching branches,
  prefer `clean` — stale Hilt/kapt stubs from another branch cause errors like
  "Could not find class file for '…Application'".

## Hard project rules

- **Jetpack Compose only** — never add XML layouts.
- **Stateless composables** — state lives in ViewModels (`StateFlow`), UI receives values
  and callbacks. Follow the existing `UiState` sealed-class pattern.
- **Hilt** for all DI. New modules go to `app/src/main/java/com/coparently/app/di/`.
- **minSdk = 26** — beware of newer `java.time` additions
  (e.g. `LocalDate.ofInstant` is API 34+; use `Instant.atZone(...).toLocalDate()`).
- **KDoc** on public classes/functions; code and comments in **English**.
- Material 3 components; theme tokens from `presentation/theme/`
  (`CoPlanlyColors`, `Typography`, `CoPlanlyShapes`, `dimensions()`).
- Parent color semantics are product-level: **Mom = pink, Dad = blue** — do not repurpose.
- Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`).

## Architecture map

```
domain/    — models, repository interfaces, use cases, holidays, ReminderScheduler
data/      — Room (v9 + migrations), Firestore/Google/AI clients, repository impls, sync
presentation/ — Compose screens per feature + ViewModels + theme
di/        — Hilt modules (Database, Firebase, Google, UseCase, Notification, …)
```

Data flow: UI → ViewModel → UseCase → Repository → Room (source of truth) → Firestore sync.

### Things that are easy to get wrong

1. **Room schema changes** require: entity change → version bump in `CoPlanlyDatabase` →
   migration in `DatabaseMigrations` (it is auto-registered via `ALL_MIGRATIONS`).
   Exported schemas live in `app/schemas/`.
2. **Event editing must preserve fields.** `AddEditEventScreen` keeps a snapshot of the
   loaded event and uses `copy()`. Never rebuild an `Event` from scratch on save —
   that wipes `sharedWith`/`permissions`/`createdByFirebaseUid` (this was a real bug).
3. **Private events (`isPrivate`)** must never be written to Firestore. Both
   `EventRepositoryImpl` and `SyncService` filter them — keep any new sync path consistent.
4. **Recurring events** are stored once and expanded to occurrences at query time via
   `RecurrenceExpander` (wired in `EventRepositoryImpl.getEventsByDateRange`).
   Occurrences share the master event id — don't use the id as a unique list key.
5. **The Firestore document schema for events** is defined in one place:
   `EventRepositoryImpl.toFirestoreMap()`. `SyncService` maps must stay in sync with it.
6. **Calendar query ranges** come from `queryRangeFor()` in `CalendarScreen.kt` —
   extend that function instead of inlining new range math.
7. **View modes** are `MONTH, WEEK, DAY` (roadmap order). There is no 3-day view anymore.
8. **Czech holidays** come from `domain/holidays/CzechHolidays` (pure, computed; Easter
   via computus). School vacations are the nationwide MŠMT ones; the district-dependent
   spring break is intentionally not included.
9. **Reminders** are scheduled through the `ReminderScheduler` domain interface
   (WorkManager impl `EventReminderScheduler`), hooked into the event use cases —
   schedule on create/update, cancel on delete.

## Known issues / do not "fix" silently

- `firestore.rules` (strict) currently **rejects the app's own event writes** (exact-key
  validation + timestamp type vs ISO strings). `firestore.rules.simple` is the permissive
  variant. Aligning the strict rules with the real schema is an open task — see audit §2.3.
- **All `strings.xml` files are gitignored** (they hold OAuth values locally), so a fresh
  clone has no string resources and cannot build — see audit §2.1. New string resources
  must go into separately named, tracked files (e.g. `reminders.xml`), never into
  `strings.xml`. Real secrets belong in `gradle.properties`/env vars like `GEMINI_API_KEY`.
- Multi-day events are only matched by their start date in `EventDao` range queries
  (audit §3.5).
- Unit tests for ChildInfo/Pairing/Settings/Sync ViewModels were removed as stale
  (they targeted long-gone APIs); rewrite them against the current constructors when
  touching those features.

## Language conventions

- The user communicates in Russian; reply in Russian in chat.
- All repository content — code, comments, docs, commit messages — is **English**.
