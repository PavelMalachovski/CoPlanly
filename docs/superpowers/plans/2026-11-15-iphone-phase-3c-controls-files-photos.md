# iPhone phase 3, part C: the system's controls, files and photos — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On the iPhone every switch, segmented choice, date and time picker, option menu,
confirmation, in-screen sheet and grouped list is the system's control. Calendar and Home lead with
the redesign's header. Photos, files, the camera, sharing, QR codes, receipt reading and the
exported record work on the iPhone, through the same shared code Android runs. Android draws and
does exactly what it does today.

**Architecture:** A new composition local, `LocalNativeControls: NativeControls?`, is provided only
by the iPhone shell (`ScreenController`), as `LocalBarHost` is. Each adaptive control is a common
composable that draws today's Material component when the local is null and asks the
`NativeControls` implementation (`iosMain`, UIKit through Compose Multiplatform's `UIKitView` or a
presented view controller) when it is not. So a screen never branches on the platform, and a
Robolectric test reaches the iPhone branch with a recording fake. The files-and-photos half replaces
the iPhone's `No…` stand-ins (ROADMAP MON-24, "Still stand-ins on the iPhone after part B", Part C)
with real `iosMain` implementations behind the interfaces Android already answers.

**Tech Stack:** As part B. Kotlin 2.4.21, Compose Multiplatform 1.9.3 (`UIKitView`,
`ComposeUIViewController`), GitLive 2.7.0 with `dev.gitlive:firebase-storage:2.7.0` added, Firebase
iOS SDK 11.8.0 (the pod and the Swift package `FirebaseStorage` added), Apple Vision, PDFKit's
`UIGraphicsPDFRenderer`, CoreImage's `CIQRCodeGenerator`, PhotosUI's `PHPickerViewController`,
`UIDocumentPickerViewController`, `UIImagePickerController`, `UIActivityViewController`.

**Spec:** `docs/superpowers/specs/2026-11-10-iphone-phase-3-native-shell-design.md` §6 row C, and
its parent `docs/superpowers/specs/2026-10-27-iphone-app-design.md` §5.3 (the controls) and §5.4
(OCR, PDF export, payment codes). Look: the canvas "CoPlanly для iPhone"
(https://claude.ai/artifact/732JJxDHeHtUp8zLFAdUTH). The owner approved it on 15 November 2026: the
third row's eight control boards, and the seven iPhone boards redrawn on the November redesign
(`docs/superpowers/specs/2026-11-12-zuzana-redesign-design.md`). Part B's plan:
`docs/superpowers/plans/2026-11-13-iphone-phase-3b-every-screen.md`.

## Rulings made while planning

- **The iPhone keeps the redesign's look** (owner, 15 November 2026). Its parts are the lavender
  ground, white cards with 24 dp corners and a hairline, the indigo `#4F46E5` tint, and the
  wordmark header on Calendar and Home. Only the controls are the system's. This replaces part D's
  "system colours for backgrounds" in the spec. Task 13 records it there. Part D keeps SF Pro,
  Dynamic Type and SF Symbols in content.
- **A switch's colour is the system's green**, not the tint (the Switch board).
- **A choice of more than five stays chips** (currencies, countries, contact-window days, the
  "Answer by" offer). Only the nine `SingleChoiceSegmentedButtonRow`s become segmented controls.
- **Dialogs that carry content stay Compose dialogs on the iPhone.** These are the colour picker,
  the region, family kind and split dialogs, Settle up, a proposal and a custody dialog. Only a
  dialog that is a question with buttons becomes an alert or action sheet. Part D restyles the
  rest.
- **A grouped list's hairline starts 16 dp in on the iPhone**, whatever the row's icon. This is the
  system's inset for a row without an icon. Cost if wrong: rows with an icon read slightly off the
  board, where the hairline starts after the icon. A later polish can add an inset per row.
- **PAY by square stays without a code on the iPhone** (parent design §5.4: no LZMA1 encoder until
  phase 4). QR Platba and EPC draw.
- **CI is not awaited per task** (owner, 14 November 2026). Each task ends with the local checks
  and a push. One full CI run on the branch head comes after Task 13 and before the final review.

## Global Constraints

- **Android draws and does exactly what it does today.** The 470 screenshot baselines must not
  change. Every adaptive control's null-local branch is the exact Material call the site made
  before, with the same parameters, modifiers, colours and semantics.
- **An iPhone-only branch is `LocalNativeControls.current != null`** (spelled `nativeControls()`),
  or part B's `shellDrawsBars()` for bars. Never a platform check.
- **Nothing on the iPhone may lead to a stand-in** (design item 8). A button whose capability is
  still `No…` stays hidden. Each task that replaces a stand-in makes its button appear.
- **On the device only** (CLAUDE.md items 10 and 28). Receipt text is read by Apple Vision on the
  phone, and nothing of it is sent anywhere.
- **No download URL, ever** (items 31 and Legal 8). Files go through `StorageBucket` with the
  `uploader` and `sha256` metadata the rules require. Never `downloadURL`.
- **No server, rules or schema change.** The Storage emulator is already in the `ios-app` job's
  emulator set.
- **No string a parent reads without a key in all five `composeResources/values*/` files.**
  `node tools/check-invariants.js` must pass.
- Minimum iOS 17.0, iPhone only, portrait only. Firebase iOS SDK 11.8.0 and GitLive 2.7.0 exactly.
- English only in the repository. KDoc on public declarations. Conventional Commits. Every commit
  ends with the session's two attribution lines. No model identifiers anywhere.
- **No Gradle in the cloud session.** Locally run `node tools/check-invariants.js` and
  `node --test tools/test/*.test.js`, then push to `ccr-ef59168f-16qmlu`. CI runs once on the head
  after Task 13.

## Review Focus

1. **A control's value changed from outside while it is shown.** Examples: a sync turns
   "Pause before sending" off, or the form resets the end date. The `UISwitch`, `UISegmentedControl`
   or `UIDatePicker` must show the new value without firing `onChange`. Owner: Tasks 1, 2 and 3.
   Each `NativeControlsIosTest` case sets the value from outside and asserts no callback.
2. **A sheet or alert dismissed by a swipe or a tap outside** must call `onDismiss` exactly once.
   It must not run the confirm action and must leave no presented controller behind. A second open
   must work. Owner: Tasks 5 and 6, each with an XCUITest step.
3. **A date picker in another time zone.** The picked day is the day the parent saw, as
   `PickerDates` guarantees on Android: no UTC shift in either direction. Owner: Task 3, the
   `PickerDatesIosTest` cases at UTC+14 and UTC−11.
4. **A record photo whose upload or download fails** (offline, refused by the rules, wrong digest)
   shows no broken image. It keeps the record's other fields, and a later retry works. Owner: Tasks
   9 and 10, with the write gate's refused-path case.
5. **A shared or exported file whose name has spaces, accents or a slash.** The share sheet, the
   document picker and the export must keep it readable and never write outside the app's
   directories. Owner: Tasks 11 and 12.

---

### Task 1: The native controls seam and the switch

**Files:**
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/NativeControls.kt`
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/AdaptiveSwitch.kt`
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/controls/IosNativeControls.kt`
- Modify: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/shell/ScreenController.kt` (provide the local beside `LocalBarHost`)
- Modify: the 14 `Switch(` call sites. They are `event/AddEditEventScreen.kt` (1235, 1447, 1506,
  1555), `settings/SettingsScreen.kt` (686, 724, 753, 1117), `support/SupportScreen.kt:375`,
  `custody/CustodySetupScreen.kt` (835, 888), `expenses/AddExpenseScreen.kt` (1031, 1058) and
  `calendar/components/CalendarFilters.kt:226`.
- Modify: `common/DesignSystem.kt` (`SectionRow` gains the row toggle)
- Test: `app/src/test/java/com/coparently/app/presentation/common/NativeControlsTest.kt` (Robolectric), `shared-ui/src/iosTest/kotlin/com/coparently/app/ios/controls/NativeControlsIosTest.kt`

**Interfaces:**
- Produces:
  - `interface NativeControls` holds one `@Composable` member per control. Tasks 1 to 6 each add
    theirs.
  - `val LocalNativeControls: ProvidableCompositionLocal<NativeControls?>` is
    `staticCompositionLocalOf { null }`. `@Composable fun nativeControls(): NativeControls?` reads
    it.
  - `NativeControls.Switch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean, accessibilityLabel: String?, modifier: Modifier)`.
  - `@Composable fun AdaptiveSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true, accessibilityLabel: String? = null, colors: SwitchColors = SwitchDefaults.colors())`.
    It draws Material `Switch` with exactly these arguments when there are no native controls.
  - `SectionRow` gains `onToggle: (() -> Unit)? = null`. It is used only under native controls,
    where the whole row toggles (`Modifier.toggleable`). On Android it is ignored, so the row's
    semantics stay as today.
  - `IosNativeControls.Switch` is a `UIKitView` of `UISwitch`, laid out at the switch's intrinsic
    51×31 pt. Its value is set from Compose in `update` without sending
    `UIControlEventValueChanged`. Its target-action calls `onCheckedChange`.

- [ ] **Step 1: Write the failing Robolectric tests** in `NativeControlsTest`:
  - `without native controls the switch is Material's`: compose
    `AdaptiveSwitch(checked = true, onCheckedChange = {})`. A node with `Role.Switch` and
    `ToggleableState.On` exists, and a recording fake received no call.
  - `with native controls the switch is the fake's`: provide
    `LocalNativeControls provides fake`. The fake's `switches` holds one entry,
    `(checked = true, enabled = true)`. No Material switch node exists.
  - `a section row toggles as a whole only under native controls`: under the fake, a click on the
    row's title calls `onToggle` once. Without the fake, the same click calls nothing.
- [ ] **Step 2: Write the iOS test** `NativeControlsIosTest`:
  - `a value set from outside does not call back`: build `IosNativeControls`' switch view through
    its `internal fun makeSwitch(...)`, set it on, then off, through `updateSwitch`. The callback
    count is 0. Send `UIControlEventValueChanged`, and the count is 1.
- [ ] **Step 3: Implement** the seam, `AdaptiveSwitch` and `IosNativeControls.Switch`. Provide
  `LocalNativeControls provides IosNativeControls` in `ScreenController` and in `FormSheet`'s
  content.
- [ ] **Step 4: Convert the 14 call sites** to `AdaptiveSwitch`, passing the `colors` and
  `Modifier.semantics` each one has today. Convert the five `SectionRow(trailing = { Switch … })`
  rows to also pass `onToggle`.
- [ ] **Step 5:** Run `node tools/check-invariants.js` and push.
- [ ] **Step 6: Commit** `feat: draw switches as the iPhone's own (MON-24)`.

### Task 2: Segmented choices

**Files:**
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/AdaptiveSegmented.kt`
- Modify: `IosNativeControls.kt`
- Modify: the nine `SingleChoiceSegmentedButtonRow`s. They are
  `calendar/components/CalendarHeader.kt:176`, `expenses/ExpenseScreen.kt:743`,
  `expenses/AddExpenseScreen.kt:975`, `pairing/PairingScreen.kt:719`,
  `custody/CustodySetupScreen.kt:518`, `support/SupportScreen.kt:468`,
  `changerequests/SwapDayAnswers.kt:84` and `calendar/components/CalendarFilters.kt:311`.
- Test: `NativeControlsTest`, `NativeControlsIosTest`

**Interfaces:**
- Produces:
  - `NativeControls.Segmented(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier)`.
  - `@Composable fun AdaptiveSegmented(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, material: @Composable () -> Unit)`.
    `material` is the site's existing `SingleChoiceSegmentedButtonRow`, moved as is into the
    lambda, so Android's colours and icons are untouched.
  - iOS: a `UISegmentedControl` filling the width, 32 pt high, with the selected segment set
    without `ValueChanged`. `ParentFilterSegments` loses its per-parent colours on the iPhone (the
    names carry the meaning).

- [ ] **Step 1: Failing tests:**
  - `NativeControlsTest.segmented without native controls draws the site's row`: the `material`
    lambda ran.
  - `NativeControlsTest.segmented with native controls`: the fake got
    `(labels = [Month, Week, Day], selectedIndex = 0)`, and `material` did not run.
  - `NativeControlsIosTest.a segment set from outside does not call back`.
- [ ] **Step 2: Implement** the composable and the iOS control, then convert the nine sites.
- [ ] **Step 3:** Push. Commit `feat: draw segmented choices as the iPhone's own (MON-24)`.

### Task 3: Date and time pickers

**Files:**
- Modify: `common/PickerDates.kt` (`LocalDatePickerDialog`), `components/TimePicker.kt`
  (`TimePickerDialog`), `common/DatePickerField.kt`, and the event form's Starts and Ends rows in
  `event/AddEditEventScreen.kt` and `event/EventFormRows.kt`
- Modify: `IosNativeControls.kt`
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/controls/PickerSheet.kt`
- Test: `NativeControlsTest`, `shared-ui/src/iosTest/kotlin/com/coparently/app/ios/controls/PickerDatesIosTest.kt`, `iosApp/CoPlanlyUITests/ShellUITests.swift`

**Interfaces:**
- Produces:
  - `NativeControls.DateDialog(initial: LocalDate, confirmLabel: String, dismissLabel: String, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit)`.
    It presents a sheet at the medium detent holding a `UIDatePicker` (`.inline` style, date
    mode), with Cancel and Done in its bar. It is shown through `LocalDatePickerDialog`, so all 14
    callers and the child-records wrapper change nowhere.
  - `NativeControls.TimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit)`.
    It works the same way, with `.wheels` style in time mode. The 12- or 24-hour clock follows the
    phone's setting, so `ClockFormat` is not read.
  - `NativeControls.CompactDate(value: LocalDate?, onChange: (LocalDate) -> Unit, label: String, modifier: Modifier)`
    and `CompactTime(value: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier)` are a
    `UIDatePicker` in `.compact` style: the system's pill, which opens the system's own calendar or
    wheel. `DatePickerField` uses `CompactDate` under native controls (its `onClick` is then
    unused). The event form's Starts and Ends rows draw `CompactDate` and `CompactTime` side by
    side, as on the New event board.
  - Days cross the UIKit boundary as the picker's calendar day in the phone's current calendar and
    zone (`NSCalendar.components` of the picked `NSDate`), never through UTC millis.
- [ ] **Step 1: Failing tests:**
  - `NativeControlsTest.the date dialog under native controls asks the fake and does not draw Material's`.
  - `NativeControlsTest.DatePickerField under native controls is the compact picker`.
  - `PickerDatesIosTest`: for `TimeZone` set to `Pacific/Kiritimati` and then `Pacific/Pago_Pago`,
    `nsDateFor(LocalDate(2026, 11, 1))` read back through `localDateOf(...)` equals 2026-11-01. The
    same holds for 2026-03-29 and 2026-10-25, the European DST days.
- [ ] **Step 2: Implement** `PickerSheet` (a `UIViewController` with the picker,
  `sheetPresentationController.detents = [medium]`, `prefersGrabberVisible`, and
  `presentationControllerDidDismiss` calling `onDismiss` once), the two compact pickers, and the
  branches in the four files.
- [ ] **Step 3: XCUITest:** in `testShellWalk`, open New event, tap the Starts date pill, choose
  the 20th in the system calendar, and assert the pill reads the 20th. Then open "Jump to date"
  from the month title, swipe the sheet down, and assert the calendar is unchanged and a second
  open works.
- [ ] **Step 4:** Push. Commit `feat: pick dates and times with the iPhone's own pickers (MON-24)`.

### Task 4: Option menus

**Files:**
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/AdaptiveMenu.kt`
- Modify: `common/ScreenBar.kt` (`BarMenuItem` gains `checked: Boolean = false`), `NativeBars.kt`
  (draws `checked` as `UIMenuElementStateOn`), `IosNativeControls.kt`
- Modify: `event/EventFormRows.kt` (`EventOptionDialog` call sites at `AddEditEventScreen.kt:1364`
  and `:1377`), `settings/SettingsScreen.kt` (`SingleChoiceDialog`'s callers),
  `expenses/CurrencySelector.kt`, the three `ExposedDropdownMenuBox`es
  (`common/MedicalProfileEditor.kt:160`, `expenses/AddExpenseScreen.kt:605` and
  `pets/AddEditPetScreen.kt:522`), and `chat/ChatAttachments.kt` (`ChatComposerMenu`)
- Test: `NativeControlsTest`, `ShellUITests.swift`

**Interfaces:**
- Produces:
  - `NativeControls.MenuAnchor(items: List<BarMenuItem>, modifier: Modifier, content: @Composable () -> Unit)`.
    It draws `content`, with a transparent `UIButton` over it whose `menu` holds the items and
    `showsMenuAsPrimaryAction = true`. The system then shows the menu from the row on a tap.
  - `@Composable fun AdaptiveOptionRow<T>(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, row: @Composable (open: () -> Unit) -> Unit, dialog: @Composable (dismiss: () -> Unit) -> Unit)`.
    On Android it calls `row(open)`, which opens `dialog`, as today. Under native controls it wraps
    `row({})` in a `MenuAnchor` whose items carry `checked` on the selected option.
  - `ReportableBubble`'s long press shows a native action sheet (Task 5's `ActionSheet`) under
    native controls.
- [ ] **Step 1: Failing tests:**
  - `NativeControlsTest.an option row under native controls hands the fake one item per option, the selected one checked, and draws no dialog`.
  - `NativeControlsTest.without native controls tapping the row opens the dialog`.
- [ ] **Step 2: Implement**, then convert each listed site.
- [ ] **Step 3: XCUITest:** in New event, tap Repeat, assert a menu item "Every week" exists, tap
  it, and assert the row's value reads it.
- [ ] **Step 4:** Push. Commit `feat: open option menus from their rows on the iPhone (MON-24)`.

### Task 5: Alerts and action sheets

**Files:**
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/controls/Alerts.kt`
- Modify: `common/ConfirmationDialog.kt`, `IosNativeControls.kt`, `common/DiscardGuard.kt`
  (through `ConfirmationDialog`; no change expected), and the four raw destructive dialogs:
  `event/AddEditEventScreen.kt:1755`, `custody/SeasonalLayerDialog.kt:103` (its delete
  confirmation only), `settings/CalendarFeedScreen.kt:341` and
  `documents/FamilyDocumentsContent.kt:267`
- Test: `NativeControlsTest`, `ShellUITests.swift`

**Interfaces:**
- Produces:
  - `NativeControls.Confirm(title: String, message: String, confirmText: String, dismissText: String, destructive: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit)`.
    A non-destructive confirm is a `UIAlertController` alert. A destructive one is an action sheet,
    with the confirm action in `.destructive` style and Cancel in `.cancel` style, and the message
    as its message. The alert is presented once per composition entry and dismissed when the
    composable leaves. Each action calls its lambda once.
  - `NativeControls.ActionSheet(title: String?, items: List<BarMenuItem>, onDismiss: () -> Unit)`
    is for `ReportableBubble` (Task 4).
  - `ConfirmationDialog` calls `Confirm` under native controls. The four raw dialogs call it under
    native controls and keep their `AlertDialog` otherwise.
  - `DiscardGuard`'s "Discard changes?" is an alert, not an action sheet. The spec's rule is that a
    question with two answers is an alert. It passes `destructive = true` only to colour Discard
    red. So `Confirm` takes `asActionSheet: Boolean = destructive`, and `DiscardGuard` passes
    `false`.
- [ ] **Step 1: Failing tests:**
  - `NativeControlsTest.a destructive confirmation under native controls is an action sheet`: the
    fake records `asActionSheet = true`.
  - `NativeControlsTest.discard changes is an alert`: the record shows `asActionSheet = false`.
  - `NativeControlsTest.without native controls ConfirmationDialog is Material's`: the title node
    is displayed.
- [ ] **Step 2: Implement** and convert.
- [ ] **Step 3: XCUITest:** open an event's preview, choose Delete, assert an action sheet with
  "Delete event" and "Cancel", and tap Cancel. The event is still listed. Then edit a form and
  swipe its sheet down. Assert an alert "Discard changes?", tap "Keep editing", and assert the form
  is still shown.
- [ ] **Step 4:** Push. Commit `feat: ask and confirm with the iPhone's alerts and action sheets (MON-24)`.

### Task 6: In-screen sheets

**Files:**
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/common/AdaptiveSheet.kt`
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/ios/controls/ComposeSheet.kt`
- Modify: the 11 `ModalBottomSheet` sites (the list in the call-site map below)
- Test: `NativeControlsTest`, `ShellUITests.swift`

**Interfaces:**
- Produces:
  - `enum class SheetHeight { Medium, Large }`.
  - `NativeControls.Sheet(height: SheetHeight, onDismiss: () -> Unit, content: @Composable () -> Unit)`.
    It presents a `ComposeUIViewController` as a sheet with `detents = [medium, large]` (`Medium`)
    or `[large]`, with the grabber, from the top-most presented controller.
  - The content is composed under the caller's locals: `LocalViewModelStoreOwner`, the Koin scope,
    `ProvideIosShell`'s platform, the theme and `LocalNativeControls`. `LocalBarHost` is null
    inside, as `DaySwapSheet` already sets it. `ComposeSheet` captures them with
    `currentCompositionLocalContext` and re-provides them, so a `koinViewModel()` inside resolves
    the caller's instance.
  - `@Composable fun AdaptiveSheet(onDismiss: () -> Unit, height: SheetHeight, material: @Composable () -> Unit, content: @Composable () -> Unit)`.
    `material` is the site's existing `ModalBottomSheet { content() }`, moved as is.
  - Sites: `DaySwapSheet` and `EventPreviewSheet` take `Medium`. Every other site takes `Large`.
- [ ] **Step 1: Failing tests:**
  - `NativeControlsTest.a sheet under native controls hands its content to the fake and draws no ModalBottomSheet`.
  - `NativeControlsTest.dismissing the fake's sheet calls onDismiss once`.
- [ ] **Step 2: Implement** `ComposeSheet` (dismissal through
  `UIAdaptivePresentationControllerDelegate`, a programmatic close when the composable leaves, and
  no second presentation while one is up) and convert the sites. The call-site map:
  `stickers/StickerViews.kt:175`, `professionals/ProfessionalInviteSheet.kt:70`,
  `event/EventPreviewSheet.kt:98`, `friends/FriendCalendarScreen.kt:129`,
  `friends/FriendInviteSheet.kt:84`, `chat/MessageTemplatesBottomSheet.kt:50`,
  `chat/ChatScreen.kt:662`, `childinfo/components/GuestInviteSheet.kt:78`,
  `calendar/components/CalendarFilters.kt:96`, `calendar/components/DaySwapSheet.kt:145` and
  `parentingplan/PlanQuestionSheet.kt:98`.
- [ ] **Step 3: XCUITest:** open an event from the month grid. Assert the preview sheet shows the
  event's title above the grid, which is still hittable at the top. Swipe the sheet down, then open
  it again.
- [ ] **Step 4:** Push. Commit `feat: open in-screen sheets as the iPhone's own (MON-24)`.

### Task 7: Grouped rows, inset-grouped

**Files:**
- Modify: `common/DesignSystem.kt` (`SectionGroup`, `SectionGroupScopeImpl.Divider`, `SectionRow`, `GroupLabel`)
- Test: `NativeControlsTest`

**Interfaces:**
- Produces: these change under native controls only (the Grouped board).
  - `SectionGroup` keeps its white fill, 24 dp corners and hairline border, which the redesign
    keeps.
  - `Divider()` is 0.5 dp thick, `outlineVariant`, with a 16 dp start inset.
  - `SectionRow` with `onClick` draws a trailing chevron (`>`, `onSurfaceVariant`, 13 dp) after
    `trailing`. Its minimum height is 50 dp.
  - `GroupLabel` is `bodySmall` in `onSurfaceVariant`, uppercased, with a 20 dp start padding. Its
    `heading()` semantics are unchanged.
- [ ] **Step 1: Failing tests:**
  - `NativeControlsTest.a clickable row shows a chevron only under native controls`: the node with
    the content description `"chevron"` exists only under the fake.
  - `NativeControlsTest.the group divider is inset under native controls`: under the fake, the
    divider's left equals the group's left plus 16 dp. Without it, they are equal.
- [ ] **Step 2: Implement.** Push. Commit `feat: draw grouped rows inset-grouped on the iPhone (MON-24)`.

### Task 8: Calendar and Home lead with the redesign's header on the iPhone

**Files:**
- Modify: `calendar/components/CalendarHeader.kt` (88–92 and 103–106: draw `BrandHeader`,
  `HeadingLines` and Filters on the iPhone too), `calendar/components/CalendarTopBar.kt` (no
  `CalendarShellBar`: the Calendar root hands the shell an empty bar, below),
  `calendar/components/CalendarMonthBar.kt:125` (Today and "+" stay in the card's bar on the
  iPhone), `home/HomeScreen.kt:209` (`BrandHeader` on the iPhone too)
- Modify: `common/ScreenBar.kt` (`ScreenBar(hidden: Boolean = false)`), `NativeBars.kt` (a hidden
  bar sets `navigationBarHidden` on that controller only, with the content under the status bar's
  safe area)
- Test: `app/src/test/.../presentation/common/AppTopBarTest.kt`, `shared-ui/src/iosTest/.../ios/shell/NativeBarRoutesTest.kt`, `ShellUITests.swift`

**Interfaces:**
- Produces: `ScreenBar.hidden`. When it is true, a host hides the system bar for that screen.
  Android ignores it (no host). The Calendar and Home tab roots pass `hidden = true` under
  `shellDrawsBars()`. Pushed screens are unchanged.
- [ ] **Step 1: Failing tests:**
  - `AppTopBarTest.a hidden bar reaches the host as hidden`.
  - `NativeBarRoutesTest`: the routes `calendar` and `home` are the two routes whose root bar is
    hidden. Every other route keeps its native bar.
- [ ] **Step 2: Implement.** Swap selection still shows `SwapSelectionHeader` in content: its
  close button and arrows are already Compose.
- [ ] **Step 3: XCUITest:** update `testShellWalk`. The Calendar tab now shows the static text
  "Your family calendar" and a "Settings" button in content, not in a navigation bar. "+" is found
  in the month card. Every step that tapped the bar's Today, Filters, gear or "+" on Calendar now
  taps the content's.
- [ ] **Step 4:** Push. Commit `feat: lead the iPhone's Calendar and Home with the redesign's header (MON-24)`.

### Task 9: Images and QR codes on the iPhone

**Files:**
- Create: `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/platform/IosImages.kt`, `IosQrCodes.kt`
- Modify: `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/platform/IosPlatform.kt` (replace `NoImages`, `NoQrCodes`)
- Test: `shared-ui/src/iosTest/kotlin/com/coparently/app/presentation/platform/IosImagesTest.kt`, `IosQrCodesTest.kt`

**Interfaces:**
- Consumes: `PlatformImages.Image(model, contentDescription, modifier, contentScale, fullResolution, placeholder)`
  and `QrCodes.encode(content): QrMatrix?` / `shareImage(matrix, chooserTitle)` (`presentation/platform/`).
  Also `SharedFileCache` and `RecordPhotoCodec`/`RecordPhotoAccess` in `:shared-data`.
- Produces:
  - `IosImages` handles three models. A local file path (`String` starting with `/` or `file://`)
    and a kotlinx-io `Path` decode with `Image.makeFromEncoded` on `Dispatchers.Default`, cached in
    memory by path. A `ph1|…` record-photo reference resolves through the same
    `RecordPhotoAccess.viewable` and `SharedFileCache` that Android's `RecordPhotoFetcher` calls
    (download as the signed-in user, keep only on a digest match). A model it cannot read draws the
    `placeholder`, or nothing. A downscale to the view's size happens unless `fullResolution` is
    set. `available = true`.
  - `IosQrCodes.encode` uses `CIQRCodeGenerator` with correction level `M`, read back into a
    `QrMatrix` one cell per module. `shareImage` renders the matrix to a PNG in `Caches/qr/` and
    hands it to the share sheet (Task 11). `available = true`. Until Task 11 lands the share button
    stays hidden by its own `ShareSheet.available` check.
- [ ] **Step 1: Failing tests:**
  - `IosQrCodesTest.encodes QR Platba text to a square matrix with finder patterns`: the matrix is
    square, at least 25 modules wide, and its three corner 7×7 patterns are set.
  - `IosImagesTest.a ph1 reference outside the viewer's family resolves to nothing`: with a viewer
    uid not in the path's pair, the resolver returns null without a download.
  - `IosImagesTest.a file that is not an image draws the placeholder`.
- [ ] **Step 2: Implement.** Push. Commit `feat: draw photos and payment codes on the iPhone (MON-24)`.

### Task 10: Storage on the iPhone

**Files:**
- Modify: `shared-data/build.gradle.kts` (pod list gains `FirebaseStorage`, on the same one line;
  `iosMain` gains `dev.gitlive:firebase-storage:2.7.0`), `iosApp/project.yml` (Swift package
  product `FirebaseStorage`)
- Create: `shared-data/src/iosMain/kotlin/com/coparently/app/data/files/IosStorageBucket.kt`
- Modify: `shared-data/src/iosMain/kotlin/com/coparently/app/di/koin/PlatformDataModule.ios.kt`
  (bind it; remove `NoStorageBucket`), and the emulator wiring the iPhone app uses for Firestore
  and Auth (point Storage at `localhost:9199` in the same place)
- Test: `shared-data/src/iosTest/kotlin/com/coparently/app/data/files/IosStorageBucketEmulatorTest.kt`, `tools/ios/check-ios-writes.js`, `IosWriteGateTest`

**Interfaces:**
- Consumes: `StorageBucket` (`customMetadata`, `putFile`, `putBytes`, `getFile`, `delete`), in
  `shared-data/.../data/files/SharedFileStorage.kt`.
- Produces: `class IosStorageBucket(storage: FirebaseStorage) : StorageBucket`. Each call does what
  the Android `FirebaseStorageBucket` does, with custom metadata keys in the given order, progress
  as a fraction, and a missing object as the same `StorageObjectMissing` failure the common code
  already maps. If GitLive 2.7.0's storage lacks custom metadata on iOS, call the pod's
  `FIRStorageReference` directly through the cinterop GitLive exposes (`dev.gitlive.firebase.storage.ios`).
  That is the implementer's finding to report, not a reason to drop the metadata.
- [ ] **Step 1: Failing emulator tests** (skipped without `COPLANLY_EMULATOR_HOST`, failed by the
  job's skip check as part B set up):
  - `put then read back the uploader and sha256 metadata`.
  - `a second put to the same path is refused` (the rules' `resource == null`).
  - `getFile into a target writes the same bytes`.
- [ ] **Step 2: Extend the write gate:** `IosWriteGateTest` uploads a vault document and a pet
  photo through the production `SharedFileStorage` and `RecordPhotoStorage` paths (Task 11 binds
  the photo storage, so this gate case lands in Task 11's commit). `check-ios-writes.js` reads both
  objects' metadata with the Admin SDK from the Storage emulator and compares the path shape and
  the `uploader` and `sha256` keys with what Android writes.
- [ ] **Step 3: Implement.** Push. Commit `feat: store shared files from the iPhone (MON-24)`.

### Task 11: Pickers, the camera, sharing and record photos

**Files:**
- Create in `shared-ui/src/iosMain/kotlin/com/coparently/app/presentation/platform/`:
  `IosFilePicker.kt`, `IosCameraCapture.kt` and `IosShareSheet.kt`. Modify `IosPlatform.kt`.
- Create in `shared-data/src/iosMain/kotlin/com/coparently/app/data/files/`: `IosPickedFiles.kt`
  and `IosRecordPhotoStorage.kt`. Modify `PlatformDataModule.ios.kt`.
- Modify: `iosApp/Info.plist` (`NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription` is not
  needed for `PHPicker` and is not added), `iosApp/project.yml` if a key needs it
- Test: `shared-data/src/iosTest/.../IosPickedFilesTest.kt`, `IosRecordPhotoStorageTest.kt`, `ShellUITests.swift`

**Interfaces:**
- Consumes: `FilePicker`, `CameraCapture` (`CaptureTarget`, `CaptureResult`), `ShareSheet`,
  `PickedFiles` (`typeOf`, `displayNameOf`, `open`) and `RecordPhotoStorage` (`upload`, `delete`).
- Produces:
  - A picked item's "content URI" on the iPhone is the absolute path of a copy the picker makes in
    `Caches/picked/<uuid>/<sanitised name>`. Sanitising keeps letters, digits, accents, spaces,
    `.`, `-` and `_`, and replaces anything else, `/` included, with `_`.
    `IosPickedFiles.typeOf` answers from the extension through `UTType`, `displayNameOf` from the
    original name, and `open` with a kotlinx-io source of the copy.
  - `IosFilePicker.rememberImagePicker` uses `PHPickerViewController` (images, one).
    `rememberDocumentPicker(contentTypes)` uses `UIDocumentPickerViewController(forOpeningContentTypes:, asCopy: true)`
    with the MIME types mapped through `UTType`.
  - `IosCameraCapture` uses `UIImagePickerController(.camera)`. It answers `NoCameraApp` where
    there is no camera (the simulator), and `available` reports `isSourceTypeAvailable(.camera)`.
    `PermissionDenied` comes from `AVCaptureDevice.authorizationStatus`. The image is saved as JPEG
    0.9 in `Caches/receipts/` (`RECEIPT`) or `Caches/shared_files/capture/` (`SHARED_FILE`), as on
    Android.
  - `IosShareSheet` uses `UIActivityViewController` with the text, or with the file's URL, from
    the top-most presented controller.
  - `IosRecordPhotoStorage` re-encodes the picked image to JPEG (quality 0.85, longest side 2048
    px, as the Android `FirebaseImageStorage` does). It then uses the common `RecordPhotoPaths`
    and `SharedFileStorage` for the path, the metadata, the codec string and the delete.
- [ ] **Step 1: Failing tests:**
  - `IosPickedFilesTest.a name with a slash and accents is kept readable and stays inside picked`:
    `"Účet / září.pdf"` becomes `"Účet _ září.pdf"` under `Caches/picked/`. `displayNameOf` returns
    the original name.
  - `IosPickedFilesTest.typeOf maps pdf, jpeg and heic`.
  - `IosRecordPhotoStorageTest.a 4000 px image is stored at 2048 px as JPEG`.
- [ ] **Step 2: Implement.** Replace `NoFilePicker`, `NoCameraCapture`, `NoShareSheet`,
  `NoPickedFiles` and `NoRecordPhotoStorage`. The receipt, vault, chat attachment, child portrait,
  pet and event photo buttons now appear, by their own `available` checks.
- [ ] **Step 3: XCUITest:** on the simulator, open Family, Documents, Add, and assert the document
  picker appears and Cancel returns. Open an invitation's Share and assert the activity sheet
  appears and closes.
- [ ] **Step 4:** Push. Commit `feat: pick, photograph and share files on the iPhone (MON-24)`.

### Task 12: Reading a receipt and exporting the record on the iPhone

**Files:**
- Create: `shared-data/src/iosMain/kotlin/com/coparently/app/data/receipts/VisionReceiptText.kt`,
  `shared-data/src/iosMain/kotlin/com/coparently/app/data/export/IosRecordFileWriter.kt`
- Modify: `PlatformDataModule.ios.kt` (replace `NoReceiptText`, `NoRecordFiles` and
  `NoExportVerifyAddress`, the last read from the Info.plist key `CoPlanlyExportVerifyUrl`, blank
  by default as `BuildConfig.EXPORT_VERIFY_URL` is), `iosApp/Info.plist`
- Test: `shared-data/src/iosTest/.../VisionReceiptTextTest.kt`, `IosRecordFileWriterTest.kt`

**Interfaces:**
- Consumes: `ReceiptTextRecognizer.recognize(imageUri): List<String>`,
  `RecordFileWriter.render(record, labels, format): ByteArray`, `save(bytes, record, format): ExportedFile`,
  `RecordLayout` and `CommunicationRecordCsv` in `:shared`.
- Produces:
  - `VisionReceiptText` uses `VNRecognizeTextRequest` with `.accurate` level and the languages
    `cs-CZ, de-DE, en-US, ru-RU, uk-UA`. It returns lines top to bottom, the order `ReceiptParser`
    expects from ML Kit. Nothing leaves the phone.
  - `IosRecordFileWriter.render`: CSV is `CommunicationRecordCsv`'s bytes, as on Android. PDF is
    `RecordLayout`'s pages drawn with `UIGraphicsPDFRenderer` at A4 (595×842 pt), in the same
    order and with the same footer as `ExportFileWriter`. `save` writes the bytes under
    `Caches/exports/` and returns `ExportedFile` with the file path as its URI text.
    `available = true`.
- [ ] **Step 1: Failing tests:**
  - `VisionReceiptTextTest.reads the total line of a rendered receipt`: render "CELKEM 249,00 Kč"
    into an image with `UIGraphicsImageRenderer`, recognise it, and assert a line contains "249".
  - `IosRecordFileWriterTest.the csv is byte for byte the common writer's`.
  - `IosRecordFileWriterTest.the pdf starts with %PDF and has one page per layout page`.
  - `IosRecordFileWriterTest.the statement is the first text on page one`: read the page with
    `PDFDocument` and check its first text.
- [ ] **Step 2: Implement.** Push. Commit `feat: read receipts and export the record on the iPhone (MON-24)`.

### Task 13: The tour, the checklist and the record

**Files:**
- Modify: `iosApp/CoPlanlyUITests/ShellUITests.swift` (`testTour`: a screenshot of each control in
  use: a switch, a segment, the date sheet, an option menu, an action sheet and the event preview
  sheet)
- Modify: `CLAUDE.md` (design refresh item 1 and the "Where an iPhone path lives" note:
  `LocalNativeControls` and the adaptive controls, with the rule that a site keeps its Material
  call as the null branch), `docs/ROADMAP.md` MON-24 (part C built; the stand-ins left after part
  C), `docs/DEVICE-CHECKLIST.md` (a new §3 entry: each control, a photo taken and picked, a vault
  file, a share, a receipt read, an export on a real iPhone), the spec's decision table (the
  owner's 15 November rulings: the redesign's look on the iPhone and the approved control boards)
- Create: `docs/superpowers/plans/2026-11-15-iphone-phase-3c-owner-check.md` (what to tap on the
  Mac's simulator and on a phone, board by board)
- Check `docs/legal/PRIVACY-POLICY.md`, `RECORDS-OF-PROCESSING.md` and `DATA-SAFETY.md`: receipt
  reading on the iPhone is Apple Vision on the device. Add one sentence where ML Kit is named.
  Nothing else in the processing changes.
- [ ] **Step 1:** Write the docs and the tour additions.
- [ ] **Step 2:** Run `node tools/check-invariants.js` and `node --test tools/test/*.test.js`.
- [ ] **Step 3:** Push. Commit `docs: record iPhone phase 3 part C (MON-24)`.
- [ ] **Step 4:** Start the branch's one full CI run (`gh workflow run ci.yml --ref ccr-ef59168f-16qmlu`),
  read every job, and fix what is red before the final review.
