# EduPage Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import a child's EduPage lesson hours, school events, days off, tests and homework into the family calendar, beside the existing Bakaláři import, behind a release flag.

**Architecture:** A `SchoolSource` seam in `shared-data` lets `SchoolImporter` read more than one school system; Bakaláři's existing authorizer and client become one source, a new hand-written Ktor client for EduPage the other. Every decision stays in the pure `SchoolImportPlanner` (`shared/`), which learns a fourth kind (`task`) and per-system ids. The connect screen in `:shared-ui` gains a system choice, shown for EduPage only when `AppBuildValues.edupageImport` is true.

**Tech Stack:** Kotlin Multiplatform, Ktor 3.6.1 (mock engine in tests through `RecordingServer`), kotlinx-serialization `JsonElement` parsed by hand, Koin, Compose Multiplatform resources.

**Spec:** `docs/superpowers/specs/2026-11-12-edupage-import-design.md`

## Global Constraints

- The password goes only to `https://<school>.edupage.org` (or `login1.edupage.org`); no Cloud Function, no proxy, never logged, never in `toString`, never in a crash report.
- Bakaláři's stored bytes do not change: `SchoolConnectionId.of`, the `"bakalari|…"` event-id formula, `SchoolConnectionCodec`'s v1 text and the `school_connection_` key prefix (`SchoolImportParityTest`, `GsonParityTest`).
- EduPage records live under the key prefix `school_edupage_connection_`, which does not start with `school_connection_`.
- EduPage ids: connection id `SchoolConnectionId.of(uid, baseUrl, "$username|$studentId")`; event id `"school-" + sha256Hex("edupage|$host|$studentId|$kind|$key").take(32)`.
- Imported events: `eventType = "school"`, not private, `forMembers = [child:<id>]`, importer's slot as owner, `announce = false` — through the existing `SchoolEventWriter`, unchanged.
- Nothing is deleted by absence; the only deletion stays a school-hours event on a day that became a day off.
- Grades, messages, attendance, lunches and substitutions are never imported.
- No code copied from `edupage-api` (GPL-3.0); fixtures written from its protocol description carry `// Inferred from the edupage-api 0.13.3 protocol description; not yet recorded live.`
- EduPage in release builds only when `-PCOPLANLY_EDUPAGE_IMPORT=true`; default false for release, true for debug.
- New strings in all five locales; apostrophes as `'`; `node tools/check-invariants.js` passes.
- A backticked commonTest name contains no comma or parentheses.
- No Android SDK in cloud sessions: each task's verification is the command shown, run by CI on push where it cannot run locally (`./gradlew` needs the SDK; `:shared:testAndroidHostTest` and `:shared-data:testAndroidHostTest` need it too). Push after every task and read CI.

## Review Focus

1. **A cookie that expired between two daily runs** — the import must sign in again with the stored password and carry on, not mark the connection `NEEDS_PASSWORD`. Test in Task 5.
2. **A changed EduPage password** — the background sign-in fails with bad credentials; the connection becomes `NEEDS_PASSWORD`, nothing is deleted, and the stored password is kept until the parent signs in again. Test in Task 5.
3. **Both parents connect the same child with their own logins** — the second import creates nothing new (same event ids). Test in Task 1.
4. **A timeline item for another class or another pupil** — never imported. Test in Task 4.
5. **A downgrade** — an EduPage record is invisible to Bakaláři's store (`all()` does not list it) and `clearAll()` removes both kinds. Test in Task 2.

---

### Task 1: Per-system ids and the `task` kind in the planner

**Files:**
- Create: `shared/src/commonMain/kotlin/com/coparently/app/domain/school/SchoolSystem.kt`
- Create: `shared/src/commonMain/kotlin/com/coparently/app/domain/school/SchoolTask.kt`
- Modify: `shared/src/commonMain/kotlin/com/coparently/app/domain/school/SchoolImportPlanner.kt` (`SchoolImportKind`, `SchoolImportTarget`, `SchoolImportInput`)
- Modify: `shared/src/commonMain/kotlin/com/coparently/app/domain/school/SchoolImportItems.kt` (`SchoolImportIds.of`, `SchoolImportItems.all`, new `tasks`)
- Test: `shared/src/commonTest/kotlin/com/coparently/app/domain/school/SchoolImportIdsTest.kt` (create), `SchoolTaskItemsTest.kt` (create)

**Interfaces:**
- Produces:
  - `enum class SchoolSystem(val stored: String) { BAKALARI("bakalari"), EDUPAGE("edupage"); companion object { fun of(stored: String?): SchoolSystem /* null or unknown → BAKALARI */ } }`
  - `SchoolImportKind.TASK("task")`
  - `data class SchoolTask(val id: String, val date: LocalDate, val title: String, val description: String?)` — `id` is EduPage's timeline id.
  - `SchoolImportTarget` gains `val system: SchoolSystem = SchoolSystem.BAKALARI` and `val studentKey: String = ""` (EduPage's student id).
  - `SchoolImportInput` gains `val tasks: List<SchoolTask> = emptyList()`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// SchoolImportIdsTest
@Test fun `bakalari ids keep their formula`() {
    val target = target(system = SchoolSystem.BAKALARI, baseUrl = "https://s.cz/bakaweb", username = "novak")
    assertEquals(
        "school-" + SchoolImportIds.sha256Hex("bakalari|https://s.cz/bakaweb|novak|event|42|2026-11-20T08:00").take(32),
        SchoolImportIds.of(target, SchoolImportKind.EVENT, "42|2026-11-20T08:00")
    )
}
@Test fun `edupage ids name the school host and the child and not the login`() {
    val mine = target(system = SchoolSystem.EDUPAGE, baseUrl = "https://zsx.edupage.org", username = "mama", studentKey = "-1234")
    val theirs = mine.copy(username = "tata", ownerUid = "other")
    assertEquals(SchoolImportIds.of(mine, SchoolImportKind.TASK, "991"), SchoolImportIds.of(theirs, SchoolImportKind.TASK, "991"))
    assertEquals(
        "school-" + SchoolImportIds.sha256Hex("edupage|zsx.edupage.org|-1234|task|991").take(32),
        SchoolImportIds.of(mine, SchoolImportKind.TASK, "991")
    )
}
// SchoolTaskItemsTest
@Test fun `a task becomes an all-day event on its date`()          // start = date 00:00, end = date 23:59, kind TASK, key = task.id
@Test fun `a task before today is not imported`()
@Test fun `a task after the event window is not imported`()        // EVENT_WINDOW_DAYS = 90
@Test fun `a task title is cut to MAX_TITLE_LENGTH`()              // 100
```

- [ ] **Step 2: Run** `./gradlew :shared:testAndroidHostTest --tests '*SchoolImportIdsTest*' --tests '*SchoolTaskItemsTest*'` — Expected: FAIL (unresolved `SchoolSystem`).
- [ ] **Step 3: Implement.** `SchoolImportIds.of` branches on `target.system`; EduPage's host is `WebAddress.parse(baseUrl)?.host` (fall back to `baseUrl`). `SchoolImportItems.all` adds `tasks(input)`; an all-day task uses the same start/end shape as a whole-day `SchoolEventTime` (`AllDayEvent.LAST_MINUTE` end). The planner's update/never-resurrect/ledger rules apply unchanged because they work per `ImportItem`.
- [ ] **Step 4: Run** the same command and `:shared:compileKotlinIosSimulatorArm64`. Expected: PASS; the existing `SchoolImportPlannerTest` and `SchoolImportParityTest` still pass.
- [ ] **Step 5: Commit** `feat(school): per-system import ids and a task kind`.

---

### Task 2: EduPage connections in the store

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/coparently/app/domain/school/SchoolConnection.kt` (add `val system: SchoolSystem = SchoolSystem.BAKALARI`)
- Modify: `shared-data/src/commonMain/kotlin/com/coparently/app/data/school/SchoolConnectionStore.kt`
- Modify: `shared-data/src/commonMain/kotlin/com/coparently/app/data/platform/PreferenceKeys.kt` (or wherever `SCHOOL_CONNECTION_PREFIX` lives; add `EDUPAGE_CONNECTION_PREFIX = "school_edupage_connection_"`), and the `EncryptedPreferences.clear()` exemption list in the app
- Test: `shared-data/src/commonTest/kotlin/com/coparently/app/data/school/EdupageConnectionStoreTest.kt` (create)

**Interfaces:**
- Consumes: `SchoolSystem` (Task 1).
- Produces:
  - `data class EdupageSecrets(val password: String, val cookies: Map<String, String>, val studentId: String, val deviceToken: String? = null) { override fun toString() = "EdupageSecrets(studentId=$studentId)" }`
  - `StoredSchoolConnection` gains `val edupage: EdupageSecrets? = null`; `tokens` stays (blank `BakalariTokens("", "", 0)` for EduPage).
  - `SchoolConnectionStore.all/get/put/update/remove/clearAll` handle both prefixes: a record with `edupage != null` is written under `EDUPAGE_CONNECTION_PREFIX` by `EdupageConnectionCodec` (new `object`, `@InternalSharedApi`, JSON `{"v":1,"system":"edupage",…,"password","cookies":{…},"studentId","deviceToken"?}`), the rest exactly as today.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `an edupage record round-trips with its secrets`()
@Test fun `an edupage record is stored under its own prefix and not under the bakalari one`() {
    store.put(edupageRecord)
    assertTrue(prefs.keysWithPrefix("school_connection_").isEmpty())
    assertEquals(1, prefs.keysWithPrefix("school_edupage_connection_").size)
}
@Test fun `all lists bakalari and edupage connections of the owner`()
@Test fun `clearAll removes both kinds`()
@Test fun `the secrets never print the password`() { assertFalse("hunter2" in edupageSecrets.toString()) }
@Test fun `a bakalari record is written byte for byte as before`()   // compare with a literal captured from today's encode
```

- [ ] **Step 2: Run** `./gradlew :shared-data:testAndroidHostTest --tests '*EdupageConnectionStoreTest*'` — Expected: FAIL.
- [ ] **Step 3: Implement** the codec by hand over `JsonObject` like `SchoolConnectionCodec`; `SchoolConnectionCodec` itself is unchanged. Add the new prefix to `EncryptedPreferences.clear()`'s kept prefixes beside `school_connection_`.
- [ ] **Step 4: Run** the test plus `--tests '*SchoolConnectionStore*'` and the app's `GsonParityTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(school): store EduPage connections under their own key`.

---

### Task 3: The EduPage HTTP client

**Files:**
- Create: `shared-data/src/commonMain/kotlin/com/coparently/app/data/school/edupage/EdupageClient.kt`
- Create: `shared-data/src/commonMain/kotlin/com/coparently/app/data/school/edupage/EdupageException.kt`
- Create: `shared-data/src/commonMain/kotlin/com/coparently/app/data/school/edupage/EdupageSession.kt`
- Modify: `shared-data/src/commonMain/kotlin/com/coparently/app/data/http/FollowUps.kt` (add `internal suspend fun HttpClient.sendOnce(outgoing: Outgoing): Answer` — one request, no redirect following)
- Test: `shared-data/src/commonTest/kotlin/com/coparently/app/data/school/edupage/EdupageClientTest.kt`, `EdupageFixtures.kt`

**Interfaces:**
- Produces:
  - `data class EdupageSession(val host: String, val cookies: Map<String, String>, val gsecHash: String, val userhome: JsonObject)` — `host` like `zsx.edupage.org`, taken from the final URL of the sign-in.
  - `sealed class EdupageException : Exception() { BadCredentials; Captcha; SecondFactor(val session: Map<String,String>, val host: String); SessionExpired; Network(cause); Server(status: Int); NotEdupage }`
  - `class EdupageClient(private val http: HttpClient)` with:
    - `suspend fun signIn(host: String, username: String, password: String): EdupageSession` — host defaults to `"login1.edupage.org"` at the caller.
    - `suspend fun resume(host: String, cookies: Map<String, String>): EdupageSession` — GET `/user`, parse `userhome(`; a page with no `userhome(` (redirect to `/login`) → `SessionExpired`.
    - `suspend fun switchChild(session: EdupageSession, studentId: String): EdupageSession` — GET `/login/switchchild?studentid=…`, body `OK` or `Server`.
    - `suspend fun timetableJson(session: EdupageSession, studentId: String, from: LocalDate, to: LocalDate): String` — POST JSON to `/timetable/server/currenttt.js?__func=curentttGetData` with `{"__args":[null,{"year":<dp.year>,"datefrom":"…","dateto":"…","table":"students","id":"<studentId>","showColors":true,"showIgroupsInClasses":true,"showOrig":true,"log_module":"CurrentTTView"}],"__gsh":"<gsecHash>"}`.
    - `suspend fun timelineJson(session: EdupageSession, from: LocalDate): String` — POST form `datefrom=<yyyy-MM-dd>` to `/timeline/?module=todo&filterTab=&akcia=getData&filterTab=messages`.
- Sign-in sequence (form variant only; the RPC `akcia=getToken/login` variant is not built): GET `https://<host>/login/?cmd=MainLogin` → read `"csrftoken":"…"` (missing → `NotEdupage`) → POST form `csrfauth=…&username=…&password=…` to `/login/edubarLogin.php` → follow 3xx with `sendOnce`, at most 10 hops, carrying every `Set-Cookie` name=value into the jar, switching host when a redirect does → final URL containing `bad=1` → `BadCredentials`; `cap=1` or `lerr=b43b43` → `Captcha`; `twofactor` → `SecondFactor`; else parse `userhome(` … `);` (tabs and newlines stripped) as JSON and `ASC.gsechash="…"` → `EdupageSession`.

- [ ] **Step 1: Write the failing tests** (all on `RecordingServer`, fixtures inferred):

```kotlin
@Test fun `sign-in follows the redirect to the school and reads userhome and the gsec hash`() // final host zsx.edupage.org
@Test fun `sign-in carries the session cookie across hosts`()      // PHPSESSID from login1 sent to zsx
@Test fun `bad=1 is BadCredentials`()
@Test fun `cap=1 is Captcha`()
@Test fun `a twofactor redirect is SecondFactor`()
@Test fun `a page without a csrf token is NotEdupage`()
@Test fun `resume with an expired cookie is SessionExpired`()
@Test fun `the password is sent only in the edubarLogin form`()    // no request URL or other body contains it
@Test fun `the timetable request carries the year the student and the gsec hash`()
```

- [ ] **Step 2: Run** `./gradlew :shared-data:testAndroidHostTest --tests '*EdupageClientTest*'` — Expected: FAIL.
- [ ] **Step 3: Implement** with `Outgoing`/`sendOnce`, `User-Agent` as Ktor's default (EduPage has no allow-list to match), form bodies through the existing form encoder in `data/http/`. Timeouts `HttpTimeouts.SCHOOL`.
- [ ] **Step 4: Run** the test and `:shared-data:compileKotlinIosSimulatorArm64`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(school): an EduPage client for sign-in, timetable and timeline`.

---

### Task 4: EduPage parsers

**Files:**
- Create: `shared-data/src/commonMain/kotlin/com/coparently/app/data/school/edupage/EdupageAccountParser.kt`
- Create: `shared-data/src/commonMain/kotlin/com/coparently/app/data/school/edupage/EdupageTimetableParser.kt`
- Create: `shared-data/src/commonMain/kotlin/com/coparently/app/data/school/edupage/EdupageTimelineParser.kt`
- Test: `shared-data/src/commonTest/kotlin/com/coparently/app/data/school/edupage/EdupageParsersTest.kt`

**Interfaces:**
- Consumes: `EdupageSession` (Task 3), `SchoolTask` (Task 1), `SchoolWeek`, `SchoolDay`, `SchoolDayType`, `SchoolEvent`, `SchoolEventTime`, `SchoolStudent`.
- Produces:
  - `data class EdupageChild(val studentId: String, val name: String, val className: String)`
  - `data class EdupageAccount(val schoolName: String, val displayName: String, val isParent: Boolean, val children: List<EdupageChild>, val year: Int)`
  - `object EdupageAccountParser { fun parse(userhome: JsonObject): EdupageAccount }` — `userid` starting `Rodic` → parent, children from `dbi.students` (id, `firstname`+`lastname`, class via `dbi.classes[classid].short`); `Student…` → one child, itself. `year` from `dp.year`.
  - `object EdupageTimetableParser { fun weeks(json: String, from: LocalDate, to: LocalDate): List<SchoolWeek> }` — `r.ttitems`; a lesson is an item with `type` `"lesson"` (not `removed`); per date the earliest `starttime` and latest `endtime` (`"24:00"` read as 23:59); a weekday in range with no lesson → `SchoolDay(type = WORK_DAY, hasAtoms = false, …)` with null times (no hours event, no day off from the timetable alone); `r.error` → `EdupageException.Server(200)`.
  - `object EdupageTimelineParser { data class Result(val events: List<SchoolEvent>, val tasks: List<SchoolTask>, val daysOff: List<SchoolDay>); fun parse(json: String, child: EdupageChild): Result }` — reads `timelineItems`; skips `removed == "1"`; keeps an item only when `user_meno` is `"*"`, `"Celá škola"`, the child's name or the child's class name (case and spaces ignored); `typ` in `{event, schoolevent, trip, excursion, culture, parentsevening, ctevent}` → `SchoolEvent` (date and time from `cas_udalosti`; `00:00:00` → whole day), `typ` in `{homework, bexam, sexam, oexam, rexam, pexam, testing}` with a `cas_udalosti` → `SchoolTask`, `typ` in `{freeday, holiday, sholiday}` → `SchoolDay(type = HOLIDAY, description = text)`; every other `typ` ignored. Title: `text`, else `data.nazov`; a task's title is prefixed `"<subject>: "` when `data.predmet` names one.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `a parent account lists its children with their classes`()
@Test fun `a student account is its own single child`()
@Test fun `a school day runs from the first lesson to the last`()       // 08:00–13:30
@Test fun `a removed lesson does not count`()
@Test fun `a day with no lessons gives no hours and no day off`()
@Test fun `a trip for the child's class is an event`()
@Test fun `an event for another class is dropped`()
@Test fun `an event for another pupil is dropped`()
@Test fun `a whole-school event is kept`()
@Test fun `a test with a date is a task titled by subject`()            // "Matematika: Písomka"
@Test fun `a grade and a message are ignored`()
@Test fun `a free day is a day off`()
@Test fun `a removed item is ignored`()
```

- [ ] **Step 2: Run** `./gradlew :shared-data:testAndroidHostTest --tests '*EdupageParsersTest*'` — Expected: FAIL.
- [ ] **Step 3: Implement** over `JsonElement` by hand, lenient like `BakalariJson` (a missing field is skipped, never a crash).
- [ ] **Step 4: Run** the test. Expected: PASS.
- [ ] **Step 5: Commit** `feat(school): read EduPage accounts, timetables and timelines`.

---

### Task 5: The source seam and the importer

**Files:**
- Create: `shared-data/src/commonMain/kotlin/com/coparently/app/data/school/SchoolSource.kt`
- Create: `shared-data/src/commonMain/kotlin/com/coparently/app/data/school/BakalariSource.kt`
- Create: `shared-data/src/commonMain/kotlin/com/coparently/app/data/school/edupage/EdupageSource.kt`
- Modify: `shared-data/src/commonMain/kotlin/com/coparently/app/data/school/SchoolImporter.kt`
- Modify: `shared-data/src/commonMain/kotlin/com/coparently/app/di/koin/DataModules.kt`, `app/src/main/java/com/coparently/app/di/koin/DataModule.kt` (bind `EdupageClient(get<HttpClient>(SCHOOL_HTTP_CLIENT))`), `shared-data/src/iosMain/kotlin/com/coparently/app/di/koin/PlatformDataModule.ios.kt`
- Test: `shared-data/src/androidHostTest/kotlin/com/coparently/app/data/school/EdupageSourceTest.kt`, `SchoolImporterEdupageTest.kt`

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces:

```kotlin
interface SchoolSource {
    /** Weeks from [monday], [SchoolImportPlanner.TIMETABLE_WEEKS] of them; empty when the account may not read them. */
    suspend fun weeks(uid: String, connectionId: String, monday: LocalDate): List<SchoolWeek>
    suspend fun events(uid: String, connectionId: String, today: LocalDate): List<SchoolEvent>
    suspend fun tasks(uid: String, connectionId: String, today: LocalDate): List<SchoolTask>
}
sealed class SchoolSourceFailure : Exception() { NeedsPassword; Gone; Network; Server }
```

  - `BakalariSource(authorizer, client)` wraps today's `timetableWeeks`/`schoolEvents` bodies (incl. the `Forbidden` → empty rule) and returns no tasks; `BakalariException` maps to `SchoolSourceFailure` exactly as `SchoolImporter.failure` maps it today.
  - `EdupageSource(client, store)`: one session per update — `resume` with the stored cookies; on `SessionExpired` `signIn` with the stored password and store the new cookies **before** reading; `switchChild` when the account is a parent; timeline read once from `today - 7 days` and split into events, tasks and days off (days off merged into the weeks it returns); `BadCredentials`, `Captcha` or `SecondFactor` during a background sign-in → mark `NEEDS_PASSWORD`, keep the password, throw `NeedsPassword`.
  - `SchoolImporter`'s primary constructor takes `sources: Map<SchoolSystem, SchoolSource>` in place of `authorizer` and `client`; the public secondary constructor and `on(...)` keep their Bakaláři parameters and build the map with `BakalariSource` alone plus an optional `edupage: SchoolSource? = null` last parameter, so existing tests compile unchanged. `SchoolImportTarget` is built with `system` and `studentKey`; `SchoolImportInput.tasks` filled from the source.

- [ ] **Step 1: Write the failing tests**

```kotlin
// EdupageSourceTest (MockK over EdupageClient, an in-memory store)
@Test fun `an expired cookie signs in again with the stored password and keeps going`()
@Test fun `the new cookies are stored before the timetable is read`()
@Test fun `a refused password marks the connection NEEDS_PASSWORD and keeps the password`()
@Test fun `a second factor during a background sign-in marks NEEDS_PASSWORD`()
@Test fun `a parent account switches to the connection's child first`()
// SchoolImporterEdupageTest (fake SchoolSource)
@Test fun `an edupage connection imports hours events tasks and days off`()
@Test fun `a second parent's edupage connection for the same child creates nothing new`()   // existing ids found in Room → updates 0, creates 0
@Test fun `a bakalari connection still goes through the bakalari source`()
```

- [ ] **Step 2: Run** `./gradlew :shared-data:testAndroidHostTest --tests '*EdupageSourceTest*' --tests '*SchoolImporter*'` — Expected: FAIL.
- [ ] **Step 3: Implement** as specified; `KoinGraphTest` must still pass (add the new definitions to `schoolDataModule` and the app's `dataModule`; iPhone binds the same `EdupageClient` over its school client).
- [ ] **Step 4: Run** the tests above plus `./gradlew :app:testDebugUnitTest --tests '*KoinGraphTest*' --tests '*School*'`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(school): import from EduPage through a school source seam`.

---

### Task 6: Connecting an EduPage account, behind the flag

**Files:**
- Modify: `app/build.gradle.kts` (`buildConfigField("boolean", "EDUPAGE_IMPORT", …)`: property `COPLANLY_EDUPAGE_IMPORT`, default `true` for debug, `false` for release)
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/platform/AppBuildValues.kt` (add `val edupageImport: Boolean`), `app/src/main/java/com/coparently/app/presentation/platform/AndroidPlatform.kt` (`androidBuildValues`), the iPhone's `iosPlatformServices` build values and `PlatformServicesTest`
- Modify: `shared-data/src/commonMain/kotlin/com/coparently/app/data/school/SchoolAccounts.kt` (`signInEdupage`, `connectEdupage`, `reconnectEdupage`)
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/school/ConnectSchoolViewModel.kt`, `ConnectSchoolScreen.kt`, `SchoolImportScreen.kt` (system name on each connection row)
- Modify: `shared-ui/src/commonMain/composeResources/values*/school_strings.xml` (five locales)
- Test: `shared-ui/src/androidHostTest/kotlin/com/coparently/app/presentation/school/ConnectSchoolEdupageTest.kt`, `shared-data/src/androidHostTest/kotlin/com/coparently/app/data/school/SchoolAccountsEdupageTest.kt`

**Interfaces:**
- Consumes: `EdupageClient`, `EdupageAccountParser`, `EdupageSecrets`, `SchoolSystem`.
- Produces:
  - `data class EdupageSignIn(val session: EdupageSession, val username: String, val password: String, val account: EdupageAccount) { override fun toString() = "EdupageSignIn(${session.host})" }`
  - `SchoolAccounts.signInEdupage(schoolAddress: String?, username: String, password: String): EdupageSignIn` — blank address → `login1.edupage.org`; an address is normalised (`zsx`, `zsx.edupage.org`, `https://zsx.edupage.org/…` → `zsx.edupage.org`; anything not under `.edupage.org` → `EdupageException.NotEdupage`).
  - `SchoolAccounts.connectEdupage(signIn: EdupageSignIn, child: EdupageChild, childId: String, familyId: String?): SchoolConnection?` — stores the record, schedules the daily job, as `connect` does.
  - `SchoolAccounts.reconnectEdupage(connectionId: String, password: String): SchoolConnection?`
  - `ConnectStep` gains `SYSTEM` (first step, only when `edupageImport`), `EDUPAGE_CREDENTIALS`, `EDUPAGE_CHILD`; `ConnectSchoolUiState` gains `system: SchoolSystem`, `edupageChildren: List<EdupageChild>`, `edupageChild: String?`.
  - `ConnectSchoolViewModel` takes `buildValues: AppBuildValues` (Koin `get()`), so with `edupageImport = false` it starts on `TOWN` exactly as today.

- [ ] **Step 1: Write the failing tests**

```kotlin
// ConnectSchoolEdupageTest
@Test fun `with the flag off the flow starts on the town as today`()
@Test fun `with the flag on the flow starts on the system choice`()
@Test fun `an edupage sign-in with two children asks which child`()
@Test fun `an edupage sign-in with one child preselects it`()
@Test fun `a wrong edupage password says so`()                       // school_connect_wrong_password
@Test fun `a second factor says to approve it in edupage`()          // school_edupage_second_factor
// SchoolAccountsEdupageTest
@Test fun `an address is normalised to the school host`()            // "zsx", "zsx.edupage.org", "https://zsx.edupage.org/user" → "zsx.edupage.org"
@Test fun `an address outside edupage is refused`()
@Test fun `connect stores the password and the child and schedules the job`()
```

- [ ] **Step 2: Run** `./gradlew :shared-ui:testAndroidHostTest --tests '*ConnectSchoolEdupageTest*'` and `:shared-data:testAndroidHostTest --tests '*SchoolAccountsEdupageTest*'` — Expected: FAIL.
- [ ] **Step 3: Implement.** Strings (all five locales): `school_system_title` ("Which system does the school use?"), `school_system_bakalari` ("Bakaláři"), `school_system_edupage` ("EduPage"), `school_edupage_address_label` ("School address (optional)"), `school_edupage_address_hint` ("Only if sign-in cannot find your school, e.g. zsx.edupage.org"), `school_edupage_second_factor` ("This account asks for a second confirmation. Approve the sign-in in the EduPage app or e-mail, then try again."), `school_edupage_password_kept` ("CoPlanly keeps this password on this phone, encrypted, so the import can sign in each day. It is sent only to EduPage."). The password field reuses Bakaláři's. The `school_edupage_password_kept` line is shown under the EduPage password field.
- [ ] **Step 4: Run** the tests, `node tools/check-invariants.js`, and the app's `StringsParityTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(school): connect an EduPage account, hidden in release`.

---

### Task 7: Documents, probe and checks

**Files:**
- Modify: `docs/legal/PRIVACY-POLICY.md`, `docs/legal/RECORDS-OF-PROCESSING.md`, `docs/legal/DATA-SAFETY.md`, then regenerate `web/privacy/` (per `web/README.md`)
- Modify: `docs/DEVICE-CHECKLIST.md` (new EduPage section), `tools/manual-test-plan.js` `RULES` (map `data/school/edupage/` to it)
- Modify: `app/src/r8Test/java/com/coparently/app/r8probe/HttpImportProbe.kt` (an EduPage case: sign-in, timetable and timeline through the minified pipeline on an in-memory engine) and `tools/check-r8-probe.js` (require it)
- Modify: `CLAUDE.md` (item 35: EduPage is the second source, the stored password, the separate prefix, the per-child ids, the release flag) and `docs/ROADMAP.md` (MON-8's EduPage line)

- [ ] **Step 1:** Add the probe case `"EduPage sign-in, timetable and timeline through the minified Ktor pipeline"` and require it in `tools/check-r8-probe.js`; run `node --test tools/test/*.test.js`. Expected: PASS.
- [ ] **Step 2:** Write the legal lines: the EduPage password is stored on the device, encrypted with the device key, only to sign in to the school's EduPage each day, sent only to `*.edupage.org`, deleted on disconnect, sign-out and account deletion. Regenerate `web/privacy/`. Run `node tools/check-invariants.js`. Expected: PASS.
- [ ] **Step 3:** Device checklist section: connect Zuzana's account (debug build or `-PCOPLANLY_EDUPAGE_IMPORT=true`), see hours, events, tests and homework on both phones; record the real responses into the fixtures (replacing the inferred ones); change the EduPage password and see "needs your password"; a second-factor account. Add the `RULES` mapping and run `node --test tools/test/manual-test-plan.test.js`. Expected: PASS.
- [ ] **Step 4:** Update CLAUDE.md item 35 and the ROADMAP line.
- [ ] **Step 5: Commit** `docs: EduPage import in the legal texts, the checklist and the probe`, push, open the PR (draft), add `full-ci` at the end.
