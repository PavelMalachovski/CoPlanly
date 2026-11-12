# The tester's look: a softer theme, the calendar first, a compact swap sheet, Home's updates and a child's photo

Status: design approved by the owner on 12 November 2026 (Home variant A). Boards: the Design canvas
"CoPlanly — Zuzana 11.11 redesign" (https://claude.ai/artifact/Hwk2CAH86zVQtmUeHBTLXf), one row per
part, before on the left and after on the right. Source: the owner's notes from the tester meeting of
11 November 2026 (Zuzana's items 1–7) and a Dribbble shot she chose as the look she wants.

## Intent

Zuzana, the tester who has used the app longest, finds it correct but cold, and says a parent opens
it to see the calendar, not a dashboard. She also wants to know, first thing, what the other parent
agreed to. Five of her seven items ship in this Android release; two are deferred (below).

1. The day swap sheet covers the whole screen.
2. The app should look like the shot she chose: a soft lavender ground, white cards with large
   corners, a lowercase wordmark, the children's photos in the header.
3. A child can have a photo.
4. The app opens on the calendar.
5. Home starts with what the co-parent did — above all what they accepted.

## Goals

- **G1 Theme.** Every screen takes the new look through the theme alone: no screen is redrawn to get
  the colours, the ground or the corners. Light and dark both.
- **G2 Calendar first.** After sign-in and onboarding the app opens on Calendar, Month, today. The
  calendar's header is the board "1 · Calendar — after".
- **G3 Swap sheet.** The swap review sheet opens at about 40 % of the screen and leaves the grid
  visible above it.
- **G4 Home.** Home is variant A: Updates, open requests, this week, the next handover.
- **G5 Child photo.** One photo per child, set on the child's card, shown on the card, in the
  calendar header, in the children's filter chips and on Home's "with you today" lines. Only the
  two parents see it.

## Non-goals

- **Stickers awarded by the app** (Zuzana's item 7). Deferred by the owner on 12 November 2026; the
  owner decision of 24 October 2026 (no app awards, item 47) stands until a separate design.
- **EduPage** (item 6). A separate project after this release: a new client (EduPage has no public
  API), the test account from Zuzana, and the Bakaláři import's rules (item 35) reused.
- No notifications screen: the shot's bell is left out, since there is nothing behind it (design
  item 8).
- No change to what any record stores beyond the child's photo; no change to the request, swap or
  custody logic.

## Anti-goals

- **"Mom"/"Dad" on screen.** The shot labels days "With Dad"; the app keeps names and "you" (hard
  rule; design item 22).
- **Custody drawn as labels instead of the wash.** The owner chose to keep the wash (12 November).
  The weekend stays the base layer under it (design item 10), and every pair that `ParentColorsTest`
  holds to AA still passes.
- **A gradient that lowers contrast.** If the ground is a gradient, text on it still meets 4.5:1 at
  both ends.
- **Money cut off** by the new pills or cards (design item 15).

## Approaches

The owner was offered three scopes on 12 November and chose the first.

1. **The whole theme (chosen).** Re-tone the `ColorScheme` roles, the shapes and the root ground, so
   every screen follows; then redraw only the calendar header, the month cell, the swap sheet, Home
   and the child card, the five surfaces the boards show. Fast, consistent, and the iPhone inherits it
   through `:shared-ui`.
2. **Calendar and Home only.** Redraw two screens and leave the rest. Rejected: two looks side by side.
3. **Every screen redrawn.** Weeks of work, and it would hold back the Google build. Rejected.

## The parts

### 1. Theme (`presentation/theme/`)

- **Light scheme** (`Theme.kt`, `Color.kt`): `background` lavender `#F5F2FC`; `surface` and
  `surfaceContainerLowest` white; `surfaceContainer`, the role `SectionGroup` draws on, becomes
  **white**, so a group reads as a white card on lavender (the boards' look). `surfaceContainerLow`
  `#F1EEFA` (a quiet tint inside a card). `primaryContainer` `#E4DEFA` and `onPrimaryContainer`
  `#2A1F70` (the selected segment and the selected tab). `primary` stays `#4F46E5`. `outlineVariant`
  `#ECE8F6`, the hairline around a card.
- **Dark scheme:** the same structure. Ground a deep violet-grey, cards one step lighter. Exact
  values are the builder's, held to the same contrast targets the standard dark scheme meets.
- **The ground** is drawn once, at the root `Scaffold` in `NavGraph` (a vertical gradient from
  `background` to a slightly deeper lavender, `#EEE9FA` in light). Screens' own `Scaffold`s take
  `containerColor = Color.Transparent` through one helper, so nothing paints over it. If the
  gradient costs more than it gives, a solid `background` is the fallback; the boards read the same.
- **Shapes:** `large` 24 stays; a card (`SectionGroup`, `InlineBanner`, the Home cards) moves from
  `medium` 16 to `large` 24, as the boards show. The scale itself does not change.
- **Contrast schemes:** `ContrastSchemes.kt` is regenerated with `tools/generate-contrast-schemes.py`
  (design item 17). `ContrastSchemesTest` and `ParentColorsTest` pass unchanged.
- **Custody wash:** the alpha drops a little in light (from 20 % to about 14 %, `custodyTintAlpha`)
  to match the softer ground. That needs `ParentColorsTest` to pass; if it does not, the alpha stays.
- **The widget** reads the public `LightColorScheme`/`DarkColorScheme` and takes the new tones with
  them (the builder's call: one look on the home screen and in the app).
- **Wordmark:** "coplanly." in Onest Bold, the dot in `primary`, drawn as text (no new asset).

### 2. Calendar as the start screen (`navigation/NavGraph.kt`, `calendar/`)

- `startDestinationFor` answers `Screen.Calendar.route` where it answered `Screen.Home.route`.
  The bottom bar keeps its order (Home, Calendar, Chat, Family); Calendar is selected on launch.
- **Home's asks must not be lost** (owner, 12 November). Home's pop-ups (`HomeAskQueue`) stay on
  Home, and the calendar banner keeps item 40's rules (no split, no agreements). Instead **the Home
  tab carries a badge**: the number Home's Requests row shows (`RequestInbox.count` with the
  agreements, the same source), drawn as the Chat tab's unread badge is, in the bar and the rail.
  It is a count of what waits on this parent's answer, never a score, and it is 0 (no badge) when
  nothing waits.
- **The back stack's root is Calendar** (the builder's call, recorded): `navigateToTab` pops to
  `Screen.Calendar.route` where it popped to Home, so Back from Home, Chat or Family returns to
  Calendar and Back on Calendar leaves the app; every place that finished onboarding or a pairing
  onto Home (`NavGraph`) lands on Calendar. `CalendarTabTaps` is unchanged.
- A push's deep link and a pending invitation code still take precedence over the start screen.
- **Header** (replaces design item 5's one row): row 1, the wordmark, the children's photos (at most
  three overlapping, then "+n"), the gear; row 2, "Your family calendar" and "Together, for Emma and
  Leo" (children's first names, `title-large` and `body-medium`; with no children, the line is left
  out); row 3, the Month/Week/Day segmented control (`SingleChoiceSegmentedButtonRow` or pills) and
  Filters. The grid sits in a white card whose header holds the month title, Today and the month
  arrows, then the legend row with the swap button. The member filter strip (design item 5) stays
  where it is, over the grid.
- **Month cell:** rounded corners (10 dp) and a 3 dp gap between cells (as approved on the board:
  the custody band still runs edge to edge, borrowed days included, now as a row of tiles — design
  item 10's rule is about the pattern crossing the month boundary, and tiles keep it; the handover
  diagonal stays inside its tile); an event is a white pill with
  a parent-coloured edge mark and the title on one line, ellipsised (it is a title, not money). One
  pill and "+n" when there are more. The wash, today's disc, the holiday red, the school-vacation
  line, contact-window corners and swap marks keep their rules (design items 10, 23; UX item 3).
- **Today card** over the tab bar: "Today · Thu 12 Nov", "Emma and Leo are with you", the next
  event's time and title; it opens Day view on today. It reads the same `HomeWeek.todayOf` Home and
  the widget read (design item 18). On a wide window (two panes) it is left out.
- **Header height at large font:** from font scale 1.3 row 2's second line is left out.
- The rules this replaces in `CLAUDE.md` design item 5 are rewritten in the same pull request.

### 3. Day swap sheet (`calendar/components/DaySwapSheet.kt`)

- `skipPartiallyExpanded = false`, opening partially expanded; the sheet can still be dragged up.
- Body: title "Swap 3 days with Bob"; one tonal block with a row per direction — a parent dot, the
  dates (`swapDateList`), "→ Bob" / "→ you" in the receiver's text colour (design item 23's
  `SwapSides`); two outlined pills "Add a note" (expands the note field in place) and "Answer by …"
  (opens the existing `AnswerByField` choice); "Send to Bob" pinned at the bottom.
- The two mini months leave the sheet: the grid above it shows the same striped marks. The
  `SwapSideGroup`/`SwapMonths` code stays for the inbox card and Home's pop-up, which still draw them.
- The selection header (`SwapSelectionHeader`) and the snackbar after sending are unchanged.

### 4. Home, variant A (`home/HomeScreen.kt`)

Order, top to bottom:

1. The wordmark header (as on Calendar, without row 2 and 3).
2. **Updates** (`title-large`), one white card: the co-parent's feed, `HomeFeed.items`, as it is today
   (their answers to this parent's requests — accepted, declined — the events they created or
   changed, their change requests), newest first, at most five rows, each with a status disc:
   accepted green check, declined a cross, added a plus, changed a pencil. "See all" opens the
   Requests inbox's History. Empty: "Nothing new from Bob" in the card, not an `EmptyState`.
3. **Open requests**, one card: "Waiting for you" with Review (the existing `RequestsRow` count and
   its action) and "Waiting for Bob" with the first item. Left out when neither waits.
4. **This week**: seven day tiles in a row, each in the day's custody wash, today ringed, a dot when
   the day has events; a tap opens Day view on that day. Replaces the week list.
5. **Next handover**: one compact card, "Handover in 4 days · Mon 16 Nov · Emma and Leo go to you"
   (`handoverHeadline`, design item 23's "you").
6. **Expenses and chat**, two tiles side by side as in variant B (owner, 12 November): this month's
   spend (`MonthMoney`, opens Expenses) and unread chat; they stack from font scale 1.3 (design
   item 15). Then the role question and the stickers stay where they are.

From 840 dp (two panes, design item 19; the builder's call): the left pane holds Updates, open
requests and the handover, the right one this week and the tiles.

The handover hero and the today card leave the top: the hero becomes part 5; the today card moves to
the Calendar (part 2). Home's one-ask-per-visit rule (item 21) and the pop-ups are unchanged.

### 5. A child's photo

- **Model:** `ChildInfo.photo: String?`, a `RecordPhotoCodec` reference (`ph1|…`), null for none.
  Room: `child_info.photo` TEXT NULL — schema 47, the first migration written in `commonMain`
  against `SQLiteConnection` ("Things that are easy to get wrong" item 1), exported by the Regenerate
  workflow. Firestore: a top-level `photo` field on `child_info/{id}`, written by both writers
  (`ChildInfoRepositoryImpl`, `SyncService`), read through `ChildInfoMappers`.
- **Not health data.** A portrait is not a medical photo: it is not behind the health consent and is
  not cleared when the consent is withdrawn. It is personal data about a child, so the privacy
  policy, the records of processing and `DATA-SAFETY.md` say so in the same pull request (Legal
  item 1).
- **Storage:** a new `RecordPhotoKind.CHILD("child_photos")`, the L-4 shape unchanged:
  `child_photos/{familyId | solo_uid}/{childId}/{random}.jpg`, read by the two parents only
  (`RecordPhotoAccess.viewable`, so no guest, friend or professional sees it), under 10 MB, never a
  download URL. `storage.rules` gains the block, `firestore-tests/rules/storage-record-photos.test.js`
  its cases; `functions/` adds the prefix to the tombstone sweep, account deletion
  (`AUTHORED_FILES`) and `movePhotosToFamily`. Live only after `firebase deploy --only storage,functions`.
- **Older builds:** an older build rewrites `child_info` with `set()` and drops the unknown `photo`
  key (item 5). Accepted: testers update both phones, and the photo is set again. The wire contract
  declares it in `CurrentWrite.olderBuildsMayDrop`, with fixtures.
- **UI:** the child card (`ChildDetailScreen`) leads with a 120 dp circle and a camera button
  (`FilePicker.rememberImagePicker`; left out where unavailable), "Only you and Bob see this photo";
  the child form gets the same picker. `ChildAvatar(child)` — the photo through `AppImage`, else the
  initial on `primaryContainer` (`AccountAvatar`'s fallback) — is the one component every other place
  uses: the calendar header, `FamilyMemberFilterStrip`'s chips, `ChildrenTodayLines`, the Family
  tab's children row.

## Shipping order

1. One branch from `main`, one pull request per part in this order: theme (1), calendar first (2),
   swap sheet (3), Home (4), child photo (5). Each regenerates the screenshot baselines through the
   Regenerate workflow; the UI tour runs once at the end for review.
2. Merge to `main`, deploy `storage` and `functions` (part 5), build the Google Play closed-test
   release.
3. The iPhone phase 3 work then continues on the new `main`. The UI is in `:shared-ui`, so the
   iPhone inherits all five parts; only unfinished phase-3 branches rebase.

## Left to the builder

- Exact dark-scheme values, within the contrast targets.
- Whether the ground is a gradient or solid, by how it renders in the screenshot matrix.
- The segmented control's component (Material's segmented buttons or pills).
- Names and string keys; all five languages in the same commit (Localization).
- Tests: the start destination, the sheet's initial state, Home's order, the photo's wire contract,
  migration 46→47, storage rules, the R8 probe case if a new `@Serializable` model appears (none is
  planned).
