# The tester's redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Zuzana's items 1–5 in the next Google Play build: a softer theme, the calendar as the start screen, a compact day swap sheet, Home variant A, and a child's photo.

**Architecture:** The look changes through the theme's roles and one root ground, so every screen follows without being redrawn; five surfaces are then redrawn to the boards (calendar header and month cell, swap sheet, Home, child card). The child's photo reuses the L-4 record photo pipeline with a new prefix and one new nullable column.

**Tech Stack:** Kotlin 2.4, Compose Multiplatform 1.9.3 (`:shared-ui`), Material 3 1.4, Room KMP (`:shared-data`), Firestore via GitLive, Firebase Storage rules, Cloud Functions (Node), Roborazzi screenshots.

**Spec:** `docs/superpowers/specs/2026-11-12-zuzana-redesign-design.md`. Boards: https://claude.ai/artifact/Hwk2CAH86zVQtmUeHBTLXf (the "after" boards are the visual source of truth for every value this plan does not pin).

## Global Constraints

- Every screen, component and ViewModel goes in `shared-ui/src/commonMain/` (hard rule "The Android app is a thin shell"); `ShellBoundaryTest` must stay green.
- Never "Mom"/"Dad" on screen; parents by name or "you" (`ParentNames.labelOrYou`).
- Colours from `MaterialTheme.colorScheme` or `ParentColors`, never a literal hex in a screen; corners from `MaterialTheme.shapes`/`CoPlanlyCorners` (detekt `ForbiddenImport` refuses `RoundedCornerShape` outside `presentation/theme/`); spacing from `Spacing`; icon sizes from `IconSizes`.
- New strings in all five locales (`shared-ui/src/commonMain/composeResources/values{,-cs,-de,-ru,-uk}/`) in the same commit; apostrophe as `'`; sentence case; `node tools/check-invariants.js` passes.
- Money is never cut off; side-by-side pairs stack from font scale 1.3 (`STACK_*_FONT_SCALE`).
- `ParentColorsTest`, `ContrastSchemesTest` pass; `ContrastSchemes.kt` is regenerated, never hand-edited.
- This cloud session has no Android SDK: Gradle verification runs in CI on the pull request (mark it ready or add `full-ci` for the emulator jobs). Screenshot baselines and the Room schema `47.json` come only from the Regenerate workflow (touch `.github/regenerate-request`).
- One pull request per part (theme → calendar first → swap sheet → Home → child photo), each with its `CLAUDE.md` text updated in the same commit as the code that changes the rule.

## Review Focus

1. **A waiting request on first open.** App opens on Calendar with a split proposal waiting: the Home tab shows a badge "1"; tapping Home shows the pop-up. Test in Task 3.
2. **Font scale 2.0 in German** on the new calendar header and Home cards: nothing clipped, the header's second line left out from 1.3. Screenshot variants in Tasks 4 and 7.
3. **No children / one child / four children** in the header avatars and "Together, for …": none → the line and avatars are left out; four → three avatars and "+1". Test in Task 4.
4. **Dark theme** on the new ground: card and text pairs hold 4.5:1. `ContrastSchemesTest` and a new pairs test in Task 1.
5. **A child photo seen by a guest or calendar friend**: never drawn (`RecordPhotoAccess.viewable` null); a legacy or foreign reference draws the initial. Test in Task 9.

---

### Task 1: Theme tokens and the root ground

**Files:**
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/theme/Color.kt` (light/dark roles, `custodyTintAlpha`)
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/theme/Theme.kt` (`LightColorScheme`, `DarkColorScheme`)
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/theme/AppGround.kt`
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/navigation/NavGraph.kt` (root Scaffold draws the ground)
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/DesignSystem.kt` (`SectionGroup` shape `large`, 1 dp `outlineVariant` border), `InlineBanner.kt` (shape `large`)
- Regenerate: `.../theme/ContrastSchemes.kt` via `python3 tools/generate-contrast-schemes.py`
- Test: `shared-ui/src/androidHostTest/kotlin/com/coparently/app/presentation/theme/ThemeTokensTest.kt`

**Interfaces:**
- Produces: `object AppGround { @Composable fun brush(): Brush }` (vertical gradient `background` → `CoPlanlyColors.LightGroundEnd` `#EEE9FA` / dark partner); `Modifier.appGround(): Modifier`; `val ScreenContainerColor: Color = Color.Transparent` used as every screen Scaffold's `containerColor`.

- [ ] **Step 1:** Write `ThemeTokensTest`: `LightColorScheme.background == Color(0xFFF5F2FC)`, `surfaceContainer == Color.White`, `surfaceContainerLow == Color(0xFFF1EEFA)`, `primaryContainer == Color(0xFFE4DEFA)`, `onPrimaryContainer == Color(0xFF2A1F70)`, `outlineVariant == Color(0xFFECE8F6)`, `primary == Color(0xFF4F46E5)`; for both schemes, `contrast(onSurface, surfaceContainer) >= 4.5`, `contrast(onSurfaceVariant, background) >= 4.5`, `contrast(onPrimaryContainer, primaryContainer) >= 4.5` (reuse the luminance helper `ParentColorsTest` uses).
- [ ] **Step 2:** Push; CI `build-test` fails on the new test.
- [ ] **Step 3:** Set the light values above; choose dark values (ground ≈ `#16141F`, cards `#211E2C`, `primaryContainer` ≈ `#3A3470`) that pass the test; lower light `custodyTintAlpha` from 0.20 to 0.14 **only if** `ParentColorsTest` passes, else leave it and note it in the PR. Add `AppGround`; in `NavGraph`'s outer Scaffold set `containerColor = Color.Transparent` and wrap it in `Box(Modifier.appGround())`; replace each tab and pane screen's `Scaffold(` with `containerColor = ScreenContainerColor` (grep `Scaffold(` under `presentation/`). Regenerate `ContrastSchemes.kt`.
- [ ] **Step 4:** Push; CI green (`build-test`, `static`, `detekt`, `shared`). Touch `.github/regenerate-request`; review the bot's screenshot diff (every screen changes ground and card colour; nothing else should move).
- [ ] **Step 5:** Update `CLAUDE.md` design item 1 (cards are `large`, white on the lavender ground) and the design system note; commit `feat(theme): the softer lavender look (tester redesign, part 1)`.

### Task 2: Brand header and child avatar

**Files:**
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/BrandHeader.kt`
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/ChildAvatar.kt`
- Strings: `common_strings.xml` ×5 — `brand_wordmark` (`coplanly`, translatable="false"), `children_avatars_more` (`+%1$d`), `children_avatars_description` (`Children: %1$s`)
- Test: `shared-ui/src/commonTest/kotlin/com/coparently/app/presentation/common/AvatarStackTest.kt`; screenshots `app/src/test/java/com/coparently/app/screenshots/BrandHeaderScreenshots.kt`

**Interfaces:**
- Produces: `@Composable fun BrandHeader(children: List<ChildInfo>, onSettingsClick: () -> Unit, modifier: Modifier = Modifier)`; `@Composable fun ChildAvatar(child: ChildInfo, size: Dp, modifier: Modifier = Modifier)` (photo through `AppImage` from `child.photo` once Task 8 adds it; until then the initial on `primaryContainer`, as `AccountAvatar`'s fallback); `internal object AvatarStack { fun visible(count: Int): Int; fun overflow(count: Int): Int }` (max 3 shown).

- [ ] **Step 1:** `AvatarStackTest`: `visible(0)==0`, `visible(2)==2`, `visible(4)==3`, `overflow(4)==1`, `overflow(3)==0`.
- [ ] **Step 2:** Implement; wordmark is Onest Bold 24 sp `onPrimaryContainer` with the "." in `primary`; avatars 38 dp overlapping by 12 dp with a 2 dp `surface` ring; nothing drawn for no children; the gear is the existing `SettingsButton` anatomy.
- [ ] **Step 3:** Screenshots for 0, 2 and 4 children in the standard matrix; CI green; commit `feat(common): brand header and child avatar`.

### Task 3: Calendar is the start screen; the Home tab carries a badge

**Files:**
- Modify: `shared-ui/.../presentation/navigation/NavGraph.kt:228,500,1197,1396` (Home → Calendar as start and back-stack root)
- Modify: `shared-ui/.../presentation/navigation/BottomNavDestination.kt:127` (`navigateToTab` pops to `Screen.Calendar.route`), `TabIcon` (badge on HOME)
- Create: `shared-ui/.../presentation/changerequests/RequestsBadgeViewModel.kt`; register in `requestsUiModule` (`di/koin/UiModules.kt`)
- Strings: `navigation.xml` ×5 — plural `home_requests_badge` ("%1$d request waiting for your answer" / "%1$d requests waiting for your answer")
- Test: `shared-ui/src/androidHostTest/.../changerequests/RequestsBadgeViewModelTest.kt`; modify `app/src/test/.../navigation/StartDestinationTest.kt` (or the test that covers `startDestinationFor`)

**Interfaces:**
- Consumes: `RequestsSource.observe(): Flow<RequestsState>`; `RequestsState.Ready.inbox.waitingOnMe.count`.
- Produces: `class RequestsBadgeViewModel(requestsSource: RequestsSource) : ViewModel { val waitingOnMe: StateFlow<Int> }`; `BottomNavigationBar(..., homeBadgeCount: Int = 0)` and the rail's twin.

- [ ] **Step 1:** Tests: `startDestinationFor(GRANTED, false, true, false) == Screen.Calendar.route`; badge VM emits 0 for `Loading`/`NoCoParent`, and `waitingOnMe.count` for `Ready` (a split proposal alone gives 1).
- [ ] **Step 2:** CI red on them.
- [ ] **Step 3:** Implement; the badge reuses the Chat badge's anatomy (`MAX_BADGE_DISPLAY_COUNT`, content description from the new plural); `rememberHomeRequestsCount()` beside `rememberChatUnreadCount()` in `NavGraph`. A push deep link and a pending invite still win (unchanged code paths — check `MainActivity.readLaunchIntent` arms after the start destination as today).
- [ ] **Step 4:** Update the androidTest walks that expect Home first (`grep -rn "Screen.Home\|home_" app/src/androidTest` for start assumptions) and `UiTourDriver`; CI green including `instrumented` (needs `full-ci` on a draft).
- [ ] **Step 5:** `CLAUDE.md`: UX overhaul item 1 (Calendar is the start and back-stack root; Home's badge), design item 5's tab-tap note; `docs/DEVICE-CHECKLIST.md` adds "cold start lands on Calendar; Back exits; Home badge matches the Requests row". Commit `feat(navigation): open on the calendar, badge Home with waiting requests (part 2a)`.

### Task 4: Calendar header, grid card and Today card

**Files:**
- Modify: `shared-ui/.../presentation/calendar/components/CalendarHeader.kt` (rewrite to the board's three rows)
- Modify: `shared-ui/.../presentation/calendar/CalendarScreen.kt` (header call, grid inside a `large` white card with month row + `CustodyLegend`, Today card above the bar on narrow windows)
- Create: `shared-ui/.../presentation/calendar/components/TodayCard.kt`
- Strings: `calendar_strings.xml` ×5 — `calendar_heading` ("Your family calendar"), `calendar_together_for` ("Together, for %1$s"), `calendar_today_card_title` ("Today · %1$s"), reuse `home_*` "with you" wording via `ChildrenTodayLines`' strings where one exists
- Test: `shared-ui/src/commonTest/.../calendar/CalendarHeaderTextTest.kt`; screenshots `CalendarScreenshots` (header + grid, 1.0/1.5/2.0 de)

**Interfaces:**
- Consumes: `BrandHeader`, `ChildAvatar` (Task 2); `HomeWeek.todayOf` (as Home and the widget read it).
- Produces: `fun CalendarHeader(children: List<ChildInfo>, viewMode: CalendarViewMode, onViewModeChange: (CalendarViewMode) -> Unit, onFiltersClick: () -> Unit, filtersActive: Boolean, onSettingsClick: (() -> Unit)?)`; `internal fun CalendarMonthBar(selectedDate: LocalDate, onToday: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onJumpToDate: () -> Unit)`; `@Composable fun TodayCard(today: TodayModel, onOpen: () -> Unit)`.

- [ ] **Step 1:** `CalendarHeaderTextTest`: `togetherFor(emptyList())` is null; one child "Emma"; two "Emma and Leo"; three "Emma, Leo and Max" (a pure `internal fun childrenList(names: List<String>, and: String): String?`, "and" from a string resource).
- [ ] **Step 2:** Implement the header: row 1 `BrandHeader`; row 2 `calendar_heading` (`titleLarge`) and the together line (`bodyMedium`), left out from font scale 1.3; row 3 `SingleChoiceSegmentedButtonRow` (Month/Week/Day, labels from `viewModeLabel`) and Filters. The month title moves into `CalendarMonthBar` inside the grid card; "Jump to date" stays reachable by tapping the month title.
- [ ] **Step 3:** `TodayCard` opens Day view on today; hidden when `rememberTwoPane()` is true.
- [ ] **Step 4:** Screenshots; CI green; Regenerate baselines.
- [ ] **Step 5:** Rewrite `CLAUDE.md` design item 5's header sentence; commit `feat(calendar): the new header and today card (part 2b)`.

### Task 5: Month cells as tiles with event pills

**Files:**
- Modify: `shared-ui/.../presentation/calendar/MonthView.kt` (`DayCell`: 10 dp corners via a new `CoPlanlyCorners.Cell`, 3 dp gaps, pills instead of dots)
- Modify: `shared-ui/.../presentation/theme/Shape.kt` (add `Cell = RoundedCornerShape(10.dp)` with its reason)
- Test: `shared-ui/src/commonTest/.../calendar/MonthCellPillsTest.kt`; `CalendarScreenshots` month grid variants

**Interfaces:**
- Produces: `internal fun pillsFor(events: List<Event>, max: Int = 1): CellPills` with `data class CellPills(val shown: List<Event>, val more: Int)`.

- [ ] **Step 1:** `MonthCellPillsTest`: 0 events → empty, more 0; 1 → shown 1; 3 → shown 1, more 2.
- [ ] **Step 2:** Implement: pill = `surface` fill, `CoPlanlyCorners.Tag`, a 3 dp leading mark in `ParentColors.fill(owner)` (friend teal for a friend's), title `labelSmall` one line ellipsised; "+2" as `labelSmall` `onSurfaceVariant`. Every `DayCellFills` layer, the holiday red, today's disc, the school-vacation line, the contact-window corner and `drawSwapMark` stay; borrowed days keep `ADJACENT_MONTH_TINT_SCALE`. The cell's content description still names the events.
- [ ] **Step 3:** `ParentColorsTest` (day number and pill title on the wash) green; Regenerate baselines.
- [ ] **Step 4:** `CLAUDE.md`: design item 5 ("Month cells carry event pills"), UX item 3, design item 10 (tiles keep the band); commit `feat(calendar): month cells as tiles with event pills (part 2c)`.

### Task 6: Compact day swap sheet

**Files:**
- Modify: `shared-ui/.../presentation/calendar/components/DaySwapSheet.kt:105-` (`skipPartiallyExpanded = false`; body per board)
- Strings: `calendar_strings.xml` ×5 — `day_swap_sheet_title_count` (plural, "Swap %1$d day with %2$s" / "Swap %1$d days with %2$s"), `day_swap_add_note` ("Add a note")
- Test: `app/src/test/.../screenshots/SwapScreenshots` (or the file rendering `DaySwapSheetContent`) and `shared-ui/src/commonTest/.../calendar/SwapSheetRowsTest.kt`

**Interfaces:**
- Consumes: `SwapSides` (receiverOf), `swapDateList(dates)`, `AnswerByField`, `SwapSheetOffer`, `SwapSheetActions` (unchanged types).
- Produces: `internal fun swapSheetRows(sides: SwapSides): List<SwapSheetRow>` with `data class SwapSheetRow(val receiverSlot: String, val dates: List<LocalDate>)`, given-away first.

- [ ] **Step 1:** `SwapSheetRowsTest`: days [13,14 → co-parent, 21 → me] gives two rows in that order; only taking gives one row.
- [ ] **Step 2:** Implement: title plural; one `SectionGroup` with a row per `SwapSheetRow` (parent dot, `swapDateList`, "→ name"/"→ you" in `ParentColors.text`); two `AssistChip`s — "Add a note" expands the existing note field in place, "Answer by …" opens `AnswerByField`'s choice; "Send to {name}" as `StickyActionBar`. Remove `SwapMonths` from the sheet only (inbox and Home's pop-up keep it).
- [ ] **Step 3:** Screenshots; CI green; Regenerate.
- [ ] **Step 4:** `CLAUDE.md` item 23 bullet "The sheet shows both sides and the result" → rows, no mini months; commit `feat(calendar): a compact swap sheet that keeps the grid in view (part 3)`.

### Task 7: Home, variant A

**Files:**
- Modify: `shared-ui/.../presentation/home/HomeScreen.kt` (order and cards), `HomeViewModel.kt` (expose what the new cards read, if not already)
- Create: `shared-ui/.../presentation/home/UpdatesCard.kt`, `WeekTiles.kt`, `HandoverCard.kt`
- Strings: `home_strings.xml` ×5 — `home_updates_title` ("Updates"), `home_updates_empty` ("Nothing new from %1$s"), `home_updates_see_all` ("See all"), `home_open_requests_title` ("Open requests"), `home_waiting_for_you` ("Waiting for you"), `home_waiting_for_coparent` ("Waiting for %1$s"), `home_handover_in_days` (plural, "Handover in %1$d day" / "… days"), `home_handover_today` ("Handover today"), `home_expenses_tile` ("Expenses this month")
- Test: `shared-ui/src/commonTest/.../home/HomeOrderTest.kt`, `UpdateRowKindTest.kt`; `HomeScreenshots`; `TwoPaneTest` case

**Interfaces:**
- Consumes: `HomeFeed.items(...)` (`ActivityItem`, `ActivityKind`, `answer: HistoryEntry?`); `RequestsRowState`; `HomeWeek`; `handoverHeadline`; `MonthMoney`; `BrandHeader` (Task 2).
- Produces: `internal enum class HomeSection { UPDATES, OPEN_REQUESTS, THIS_WEEK, HANDOVER, TILES }` with `internal fun homeSections(hasOpenRequests: Boolean, twoPane: Boolean): Pair<List<HomeSection>, List<HomeSection>>`; `internal fun updateMark(item: ActivityItem): UpdateMark` with `enum class UpdateMark { ACCEPTED, DECLINED, ADDED, CHANGED, ASKED }`.

- [ ] **Step 1:** `HomeOrderTest`: single pane, requests waiting → [UPDATES, OPEN_REQUESTS, THIS_WEEK, HANDOVER, TILES]; nothing waiting → OPEN_REQUESTS left out; two pane → left [UPDATES, OPEN_REQUESTS, HANDOVER], right [THIS_WEEK, TILES]. `UpdateRowKindTest`: ANSWER accepted → ACCEPTED, declined → DECLINED, EVENT_CREATED → ADDED, EVENT_UPDATED → CHANGED, CHANGE_REQUESTED → ASKED.
- [ ] **Step 2:** Implement: `BrandHeader`; Updates card (≤5 rows, status disc — ACCEPTED `tertiary`/check, DECLINED `error`/close, ADDED `primaryContainer`/add, CHANGED/ASKED `primaryContainer`/edit; "See all" opens the inbox at History); Open requests card (existing `RequestsRow` actions); `WeekTiles` (seven tiles in the day's wash, today ringed in `primary`, event dot; tap → Day view); `HandoverCard` (`handoverHeadline`); expenses and chat tiles (existing `StatTiles`, renamed label) — stacking from 1.3 kept. The hero and today agenda leave Home (today card lives on Calendar). `HomeAskQueue` pop-ups, role prompt and stickers unchanged.
- [ ] **Step 3:** Screenshots incl. 2.0 de; `TwoPaneTest`; CI green; Regenerate.
- [ ] **Step 4:** `CLAUDE.md` design item 5's "today card renders on Home" sentence (now Calendar's), item 21's Home notes; commit `feat(home): updates first — variant A (part 4)`.

### Task 8: A child's photo — data, storage and server

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/coparently/app/domain/model/ChildInfo.kt` (`val photo: String? = null`)
- Modify: `shared/src/commonMain/kotlin/com/coparently/app/domain/files/RecordPhoto.kt` (`CHILD("child_photos")`)
- Modify: `shared-data/src/commonMain/kotlin/com/coparently/app/data/local/entity/ChildInfoEntity.kt` (`photo: String?`), `CoPlanlyDatabase.kt` (`version = 47`)
- Create: `shared-data/src/commonMain/kotlin/com/coparently/app/data/local/CommonMigrations.kt` (`MIGRATION_46_47`: `ALTER TABLE child_info ADD COLUMN photo TEXT` on `SQLiteConnection`; `val COMMON_MIGRATIONS: Array<Migration>`)
- Modify: `shared-data/src/androidMain/.../di/DatabaseBuilder.kt:59` and `shared-data/src/iosMain/.../data/local/IosDatabase.kt:45` (add `*COMMON_MIGRATIONS`)
- Modify: `shared-data/src/commonMain/.../data/repository/ChildInfoMappers.kt` (four mappers), `data/sync/SyncService.kt` (upload, conflict and reader maps), `data/sync/ConflictResolver.kt` if it lists fields
- Modify: `shared-data/src/commonTest/.../wire/ChildInfoWireContract.kt` + fixtures in `app/src/test/resources/wire/child_info/` and `wire/current/child_info/` (`olderBuildsMayDrop` gains `photo`)
- Modify: `storage.rules` (block `child_photos/{owner}/{recordId}/{objectName}`, same three lines as `pet_photos`), `firestore-tests/rules/storage-record-photos.test.js`
- Modify: `functions/record-photos.js:30` (a collection maps to a list of `{prefix, field, list}`; `child_info` gets `{prefix:'child_photos', field:'photo', list:false}` beside medical), `functions/test/` cases for sweep, `AUTHORED_FILES` deletion and `movePhotosToFamily`
- Modify: `tools/e2e/coverage.json` if the storage prefix scan requires a test (`TwoParentRecordPhotosTest` gains the child-photo case)
- Test: `shared-data/src/androidHostTest/.../ChildInfoMappersTest`, `app/src/androidTest/.../CoPlanlyDatabaseMigrationTest#migrate46To47` (needs `47.json` from Regenerate)

**Interfaces:**
- Produces: `ChildInfo.photo: String?` (a `RecordPhotoCodec` `ph1|…` reference or null); Firestore field `photo` on `child_info/{id}`; `RecordPhotoKind.CHILD`.

- [ ] **Step 1:** Tests: mapper round trip keeps `photo`; a document without `photo` reads null; `HealthConsentManager` withdrawal leaves `photo` (portrait is not health data); storage rules: parent of the family gets/creates/deletes, a stranger and a guest are refused, overwrite refused, 10 MB cap; functions: tombstone sweep removes `child_photos/{family}/{id}/`, account deletion removes the author's, `movePhotosToFamily` moves a `solo_` portrait.
- [ ] **Step 2:** `cd functions && npm test`, `cd firestore-tests && npm test` red; CI red.
- [ ] **Step 3:** Implement the files above; `firebase.json` unchanged.
- [ ] **Step 4:** `npm test` in both folders green; touch `.github/regenerate-request` for `47.json`; CI green incl. `upgrade` (schema step) and `e2e`.
- [ ] **Step 5:** Docs: `docs/legal/PRIVACY-POLICY.md`, `RECORDS-OF-PROCESSING.md`, `DATA-SAFETY.md` (a child's portrait, who sees it, retention with the record), regenerate `web/privacy/`; `CLAUDE.md` Legal item 8 (five kinds), "Things that are easy to get wrong" item 1 (47 is the first commonMain migration). Commit `feat(children): store a child's photo (part 5a)`.

### Task 9: A child's photo — on screen

**Files:**
- Modify: `shared-ui/.../presentation/childinfo/ChildDetailScreen.kt` (120 dp photo, camera button, "Only you and {name} see this photo"), `AddEditChildInfoScreen.kt` (picker), `ChildInfoViewModel.kt` (`setPhoto(uri)`, `removePhoto()` through `RecordPhotoStorage`, upload-then-delete as Legal item 8)
- Modify: `common/ChildAvatar.kt` (read `child.photo` via `RecordPhotoAccess.viewable` and `AppImage`), `common/FamilyMemberChips.kt` (`FamilyMemberFilterStrip` chips lead with `ChildAvatar` 24 dp), `home/ChildrenTodayLines.kt`, the Family tab's children row
- Strings: `child_info_strings.xml` ×5 — `child_photo_change` ("Change photo"), `child_photo_add` ("Add a photo"), `child_photo_remove` ("Remove photo"), `child_photo_private` ("Only you and %1$s see this photo")
- Test: `shared-ui/src/androidHostTest/.../childinfo/ChildInfoViewModelTest` (photo cases), `common/ChildAvatarTest` (Robolectric in app if it composes); screenshots of the child card with and without a photo

**Interfaces:**
- Consumes: `ChildInfo.photo`, `RecordPhotoKind.CHILD` (Task 8); `RecordPhotoStorage`; `FilePicker.rememberImagePicker`; `LocalPhotoViewerUid`.

- [ ] **Step 1:** ViewModel tests: set uploads under `child_photos/{family}/{childId}/` and saves the reference before deleting the old object; a failed delete keeps the new reference; remove clears the field. Avatar: `viewable == null` (guest, foreign family, legacy URL) → initial.
- [ ] **Step 2:** Implement; the camera button only where `FilePicker.available`.
- [ ] **Step 3:** Screenshots; CI green incl. `e2e` (`TwoParentRecordPhotosTest` child case); Regenerate.
- [ ] **Step 4:** `docs/DEVICE-CHECKLIST.md`: set a photo on one phone, see it on the other's calendar header, filter chips and Home; a guest does not. Commit `feat(children): show a child's photo (part 5b)`.

### Task 10: Release

- [ ] **Step 1:** Run the UI tour (touch `.github/ui-tour-request`); share the gallery with the owner.
- [ ] **Step 2:** Merge the parts to `main` in order after review; owner runs `firebase deploy --only storage,functions` (the plan cannot deploy).
- [ ] **Step 3:** Owner builds the closed-test release for Google Play; `docs/ROADMAP.md` records the release and lists EduPage and app stickers as next.
- [ ] **Step 4:** Rebase any open iPhone phase-3 branch on the new `main`; `:shared-ui:linkDebugFrameworkIosSimulatorArm64` green in `shared-ios`.
