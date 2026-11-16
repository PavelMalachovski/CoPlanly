# iPhone phase 3, part D: the owner's check

**For:** the owner, on their Mac, and then on an iPhone. Nothing here needs a cloud session.
**Why:** part D (plan `2026-11-16-iphone-phase-3d-theme.md`) gives the iPhone the system's type,
icons and theme. Text is SF Pro, with the "coplanly." wordmark kept in Onest. The icons inside the
screens are SF Symbols, except the twelve you agreed to keep as Material. Settings' theme choice
(System, Dark, Light) now reaches the window, so the system's bars, menus, pickers, sheets and the
status bar follow it, and the tint is the indigo in light and the pale lavender in dark. Increase
Contrast raises the app to the medium contrast scheme. Android draws what it drew. The checks of
parts A, B and C (`2026-11-10-iphone-phase-3a-owner-check.md`,
`2026-11-13-iphone-phase-3b-owner-check.md`, `2026-11-15-iphone-phase-3c-owner-check.md`) are still
open. Run them first or alongside.
**The look to compare with:** the Design canvas "CoPlanly для iPhone",
https://claude.ai/artifact/732JJxDHeHtUp8zLFAdUTH. The fourth row holds what you approved on
16 November 2026: the seven boards in dark, the type (Onest before, SF Pro after, the wordmark in
Onest), the icon table drawn as pairs with the twelve keep-Material rows marked, and one screen at
the standard and the medium contrast level.

Write each answer next to its item: **yes / no / notes**. A screenshot (⌘S in the simulator, or the
side and volume-up buttons on a phone) helps with any "no".

## 0. What CI shows

**Not yet confirmed.** CI is not awaited per task (your ruling of 14 November 2026). One run on the
branch head comes after part D's last task. Until it is green, read this list as what the tests and
the tour are written to prove. The job is `ios-app`, "iPhone app — build and launch (macOS)".

- **The iPhone's emulator tests**: `WindowAppearanceTest` (System leaves the window's style the
  system's, Dark and Light force it, and the tint is the scheme's `primary` in light, dark and at the
  medium level), `IosContrastLevelTest` (Increase Contrast is the medium level, and a change is
  followed), `IosTypographyTest` (the text is the system family, the wordmark Onest),
  `SfSymbolIconTest` (every symbol in the table loads, a missing one falls back to Material, the tint
  is applied, the label is kept) and `DynamicTypeTest`.
- **The appearance tour** (`ShellUITests.testTourAppearance`, artifact `ios-tour`, folder
  `appearance/`): the seven board screens (Home, Calendar, Chat, New event, Family, Expenses,
  Settings) shot three times, in dark (`tour-dark-…`), with Increase Contrast (`tour-contrast-…`) and
  at the accessibility-extra-large text size (`tour-large-…`). Each variant's `tour.txt` lists every
  screen as reached or skipped, with the reason.
- **Android**: the 470 screenshots are unchanged, and `AppIconGuardTest` and `SfSymbolTableTest` run
  with the unit tests.

What CI cannot show: how it looks beside the boards, a theme switched while the app runs, a real
phone's text sizes, and VoiceOver.

## 1. Set up

As part C's check §1: Xcode 26, the JDKs and Node, XcodeGen, `xcodegen generate`, and the emulators
in a second window. Seed the tour's family:

```bash
node tools/ios/seed-shell-family.js --tour
```

Sign in with the e-mail and password it prints. For the phone, install the same build on an iPhone
(Xcode → your phone as the run destination), as in part C's check.

## 2. On the simulator, and then on the phone

| # | What to do | What to expect | Answer |
| --- | --- | --- | --- |
| 2.1 | **Switch the theme in Settings.** Settings → Theme → Dark, then Light, then System. Then, on System, switch iOS itself (simulator: Features → Toggle Appearance, ⇧⌘A; phone: Control Centre). Do it once with an event's preview sheet open and once with "Discard changes?" up. | Each change applies at once, with no relaunch. The screens, the tab bar, the navigation bars, menus, pickers, sheets, alerts and the status bar always agree: never a light bar over dark content, or the reverse. On Light over a dark iOS everything stays light; on Dark over a light iOS everything stays dark. Buttons and chevrons are the indigo in light and the pale lavender in dark. | Same as the boards? yes / no / notes |
| 2.2 | **Turn on Increase Contrast** (Settings → Accessibility → Display & Text Size; on the simulator, Xcode's Environment Overrides or the simulator's Settings app). Keep the app open while you switch it, in light and in dark. | Text, icons and outlines darken at once to the medium level; the lavender ground, the white cards and the parent colours stay. Switched off, it returns. | Same as the contrast board? yes / no / notes |
| 2.3 | **Use the largest text size** (Larger Text, the largest Accessibility size). Open Home, Expenses, New event, Settings and the Family tab. | Nothing is cut: amounts wrap rather than end in "…", pairs stack, the form's Save can be reached, the bars and the date pill fit. **Known difference:** Compose stops growing the text at 1.8 times, so at the largest sizes it reads smaller than the system's own screens. Say whether that is acceptable. | Acceptable? yes / no / notes |
| 2.4 | **Compare with the fourth row of the canvas.** Walk the seven board screens in dark; look at the type on one screen and at the icons on Settings, the Family tab, the chat's composer and the calendar's card. | The dark boards match. The text is SF Pro and the wordmark Onest Bold. Each icon is the SF Symbol its pair shows; the twelve keep-Material icons (among them the gavel, the scales, the double tick and the PDF document) are still Material. No icon is missing or has changed meaning. | Same as the boards? yes / no / notes |
| 2.5 | **Compare with the `ios-tour` artifact's appearance shots** (the run on the branch head, `appearance/dark`, `appearance/contrast`, `appearance/large`). Read each folder's `tour.txt`. | Each shot looks like what you saw in 2.1 to 2.3. Name every screen `tour.txt` says was skipped, with its reason. | Same as what you saw? yes / no / notes |
| 2.6 | **VoiceOver and languages, on the phone.** Run device check §3.41 (`docs/DEVICE-CHECKLIST.md`). | Each of its items, among them VoiceOver reading the icons' names as before and ₴ and № in SF Pro with the phone in Ukrainian. | Works? yes / no / notes |

## 3. Handing back

Send back the answers, a screenshot of each "no" and the appearance shots' `tour.txt` files, in the
session that plans the next step. Each "no" becomes a fix, or a decision for you to make first. The
known differences in 2.3 and ROADMAP MON-24's "Open after part D" are recorded; for each, say whether
to fix it or keep it.
