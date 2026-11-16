# iPhone phase 3, part C: the owner's check

**For:** the owner, on their Mac, and then on an iPhone. Nothing here needs a cloud session.
**Why:** part C (plan `2026-11-15-iphone-phase-3c-controls-files-photos.md`) makes every switch,
segmented choice, date and time picker, option menu, question, in-screen sheet and grouped list the
system's own. Calendar and Home now lead with the redesign's header, with no system bar over them.
Photos, files, the camera, sharing, payment codes, receipt reading and the export work on the
iPhone. Android draws and does what it did. CI proves the mechanics on the simulator. How it looks
and feels, and what the simulator cannot do, are the owner's to judge. The checks of part A
(`2026-11-10-iphone-phase-3a-owner-check.md`) and part B (`2026-11-13-iphone-phase-3b-owner-check.md`)
are still open. Run them first or alongside; part B's 2.6 points to device check §3.37, which part C
replaced with §3.40's first group.
**The look to compare with:** the Design canvas "CoPlanly для iPhone",
https://claude.ai/artifact/732JJxDHeHtUp8zLFAdUTH. The third row holds the eight control boards you
approved on 15 November 2026, each Android today on the left and the iPhone on the right. The first
row holds the Home and Calendar boards redrawn on the November redesign. You ruled the same day that
the iPhone keeps the redesign's look (the lavender ground, the white cards with a hairline, the
indigo tint, the wordmark header). Only the controls are the system's. Part D brings SF Pro, Dynamic
Type, SF Symbols in content and Increase Contrast, so the type and icons inside the screens are
still the app's own here.

Write each answer next to its item: **yes / no / notes**. A screenshot (⌘S in the simulator, or the
side and volume-up buttons on a phone) helps with any "no".

## 0. What CI shows

**Not yet confirmed.** CI is not awaited per task (your ruling of 14 November 2026). One run on the
branch head comes after part C's last task. Until it is green, read this list as what the walk and
the tour are written to prove. The job is `ios-app`, "iPhone app — build and launch (macOS)".

- **The walk** (`ShellUITests.testShellWalk`, artifact `ios-shell`) adds, to part B's steps:
  - Calendar leads with the redesign's header: no bar, with Settings, Filters, Today and "Add
    event" in content (`17-calendar-header`).
  - New event's Starts date is the system's pill, and the day picked in its calendar is the day it
    reads (`10c`, `10d`).
  - New event's Repeat is the system's pop-up menu, and the option picked is what it reads (`10e`,
    `10f`).
  - Jump to date is a sheet that a swipe closes and that opens again (`17b`).
  - Deleting an event asks in an action sheet, and Cancel keeps the event (`17c`).
  - An event's preview is the system's half-height sheet with the calendar above it in reach. It
    is swiped away and opened again (`17d`, `17e`).
  - Documents' Add a file opens the document picker, Cancel returns, and it opens a second time.
  - An invitation's Share opens the share sheet, and it closes.
- **The tour** (`ShellUITests.testTour`, artifact `ios-tour`) shows every destination it reaches,
  as in part B. It also shows six controls in use, shot as `tour-control-…`: a switch (with
  `tour-control-switch-on`), a segment, the date sheet, an option menu, an action sheet and the
  event preview sheet. `tour.txt` lists each one as reached or skipped, with the reason.
- **The iPhone's emulator tests and the write gate.** `NativeControlsIosTest` covers a value set
  from outside, which never reports a change. `PickerDatesIosTest` checks the day at UTC+14 and
  UTC−11. Storage, the pickers' copies, record photos, Vision on a drawn receipt and the export's
  CSV and PDF each have their own test. The write gate also stores a vault document and a pet's
  photo and compares each object with what Android stores.

What CI cannot show: how any of it looks beside the boards, a camera, a real receipt, a banking app,
Files and Messages on a phone, another time zone set by hand, and VoiceOver.

## 1. Set up

As part B's check §1: Xcode 26, the JDKs and Node, XcodeGen, `xcodegen generate`, and the emulators
in a second window, with the Storage emulator on 9199 among them. Seed the tour's family:

```bash
node tools/ios/seed-shell-family.js --tour
```

Sign in with the e-mail and password it prints. For §3, install the same build on an iPhone
(Xcode → your phone as the run destination). Against the emulators the phone needs your Mac's
address in place of 127.0.0.1; with a real `GoogleService-Info.plist` it uses the real project, so
use a **test account**.

## 2. Board by board, on the simulator

| # | Board | What to tap | What to expect | Answer |
| --- | --- | --- | --- | --- |
| 2.1 | **Home and Calendar** (first row) | Open the Home tab, then the Calendar tab. On each, tap the gear. | No system bar over either. The "coplanly." wordmark, the children's avatars and the gear sit under the status bar. On the Calendar, "Your family calendar" and the Month/Week/Day control follow, then the grid's white card with the month, Today, "⇄" and "+" in its own row. Settings opens with its own bar, and its back button reads "Calendar" or "Home". **Known difference:** Home no longer shows the date under its title, which part A's bar had and Android never did. | Same as the board? yes / no / notes |
| 2.2 | **Switch** | New event (the card's "+") → All day. Settings → Pause before sending. Calendar → Filters → a switch there. | The system's switch in its green, not the indigo, on the card's white with no dark corners. A tap on the switch flips it, and so does a tap on the rest of the row. | Same as the board? yes / no / notes |
| 2.3 | **Segments (Month/Week/Day)** | Calendar → Week, then Day, then Month. Add expense → "Who paid?". | The system's segmented control, filling the width. The grid follows each tap. A tap on the Calendar tab returns to Month on today. | Same as the board? yes / no / notes |
| 2.4 | **Date and time** | New event → Starts: tap the date, then the time. The month title on the grid's card → "Jump to date": pick a day and tap Done; open it again and swipe it down. A child's date of birth (Family → Child information → Emma → Edit). | The date is the system's compact pill and opens its calendar. The time opens wheels on the Mac's 12- or 24-hour clock. "Jump to date" is a half-height sheet with the inline calendar, Cancel and Done. Done moves the grid; a swipe down moves nothing, and it opens again. | Same as the board? yes / no / notes |
| 2.5 | **Menus (Repeat, Reminder, ⋯)** | New event → Repeat, then Reminder. Add expense → Category and the currency. Family → Pets → Max → Species. The chat's "+" beside the composer. | Each row ends in the system's pop-up button: the chosen value in grey and the up-and-down chevron, beside the row's own title. The menu checks the chosen option; a pick closes it and the row reads the new value. The chat's "+" opens attach a file, request a change and the templates. **Known differences:** VoiceOver reads an option row twice ("Repeat", then "Repeat, Weekly"); the chat's "+" is smaller than 44 points to touch; Settings' currency reads "CZK  Kč" with a double space. | Same as the board? yes / no / notes |
| 2.6 | **In-screen sheet** | Calendar → today's "Tour dentist" in the grid, then the event in Day view. Calendar → Filters. A long press on a day, then the day swap's review (needs a custody schedule). Family → Child information → Emma → Invite a guest. Chat → "+" → the templates. | The system's sheet with its grabber. Under the event's preview and the day swap the grid stays usable; under the others it is dimmed. A swipe down closes each, and each opens again. Switch to another tab while one is up and come back: it has closed. | Same as the board? yes / no / notes |
| 2.7 | **Confirmation and action sheet** | Edit "Tour dentist" → Delete event in the bar, then Cancel. Family → Documents → a document → Delete, then Cancel. Add expense → type an amount → swipe the sheet down. In the chat, long-press a message. | A destructive action asks in the action sheet from the bottom: the question, a red action and a separate Cancel. Cancel does nothing, and the question opens again. "Discard changes?" is a centred alert with "Keep editing" and "Discard". A long press offers "Report message" in an action sheet. **Known difference:** the seasonal layer dialog (Custody schedule → Seasonal schedules) deletes a layer without asking, as on Android. It had no question to convert, and adding one is a product decision. | Same as the board? yes / no / notes |
| 2.8 | **Grouped rows** | Settings; the Family tab. | Inset-grouped lists: a small grey header over each group, rows about 50 points tall, a hairline between rows that starts 16 points in whatever the row's icon, and one chevron on each row that opens something. Google Calendar's row keeps its expand arrow. The cards stay white on the lavender ground. | Same as the board? yes / no / notes |
| 2.9 | **Share, file, photo** | Family → Documents → Add a file → a PDF from Files → back. Family → Pets → Max → add a photo from Photos. Family → "Linked: Bob" → Add another co-parent → Share. Family → Export the record → CSV, then PDF. | The system's document picker, the photo picker (no library permission asked) and the share sheet, each closing back to the screen it came from. The document and the photo appear on the screen. The export opens the share sheet with the file. On the simulator, "Take a photo" is not offered, because it has no camera. | Same as the board? yes / no / notes |
| 2.10 | **A day swap answered** (`SwapDayAnswers`) | With a family that has a custody schedule and a waiting swap of two days or more: the request's card → "Answer day by day". Pick an answer for one day, then try to take it back. | One segmented choice per day. **Known difference:** once a day is answered, its choice cannot be cleared again on the iPhone, while Android lets you un-pick it. Judge whether that matters before sending. | Acceptable? yes / no / notes |

## 3. On an iPhone

| # | What to do | What to expect | Answer |
| --- | --- | --- | --- |
| 3.1 | **The camera.** Add expense → photograph a real receipt held straight; then one held at about 20°; then one turned sideways. Family → Documents → Take a photo. | The camera is asked for once, in your language. The receipt's amount and date fill in. For the tilted and sideways ones, the total is found, or the form says it could not read the receipt and keeps the photo. You never see a wrong amount made from two printed rows read as one. The document's photo is stored the right way up. | Works? yes / no / notes |
| 3.2 | **Photos between the phones.** Add a photo to Max and to an event. Open both on the co-parent's phone (Android, or a second iPhone). | Each is there, the same way up. A HEIC photo arrives as a JPEG, at the size Android would store it. | Works? yes / no / notes |
| 3.3 | **A vault file with an awkward name.** Add a PDF named "Rozsudek září 2026.pdf" from Files. | It keeps its name, with spaces and accents, and opens on the co-parent's phone. | Works? yes / no / notes |
| 3.4 | **Sharing and the export.** Share an invitation to Messages. Export the record as PDF and as CSV to Files. Open both. Sign out and back in. | The message arrives intact. The PDF opens with the statement first and a footer on every page; the CSV opens in Numbers with Czech and Cyrillic text intact. After signing in again, no exported or picked file is left in the app. | Works? yes / no / notes |
| 3.5 | **Another time zone.** Settings → General → Date & Time: turn off Set Automatically, choose Kiritimati (UTC+14). In New event, pick a date. Then do the same with Pago Pago (UTC−11). | The day saved is the day you picked, in both zones. | Works? yes / no / notes |
| 3.6 | **The payment code.** Run device check §3.25 with the payer on the iPhone. | A Czech account's QR Platba and another euro account's EPC code are read by the banking app. A Slovak account shows no code on the iPhone yet (PAY by square waits for phase 4). The IBAN is shown but cannot be copied yet (no clipboard until phase 4). | Works? yes / no / notes |
| 3.7 | **VoiceOver** (Settings → Accessibility). Walk 2.2 to 2.8 with it on. | Each switch, segment, date pill and pop-up button is read with its name and value, and each can be used. Note every control that reads nothing, or reads twice. | Works? yes / no / notes |
| 3.8 | **Account pictures.** Sign in with a Google account that has a picture. | The iPhone draws the account's initial, not the picture: it never fetches a web address. A record photo is drawn. | Acceptable? yes / no / notes |
| 3.9 | **The rest of the device check.** Run `docs/DEVICE-CHECKLIST.md` §3.40. | Each of its items. | Works? yes / no / notes |

## 4. Handing back

Send back the answers, a screenshot of each "no" and the first run's `tour.txt` (from the
`ios-tour` artifact), in the session that plans part D. Each "no" becomes a fix, or a decision for
you to make first. The known differences in 2.1, 2.5, 2.7 and 2.10 are recorded in ROADMAP MON-24
("Open after part C"). For each, say whether to fix it or keep it.
