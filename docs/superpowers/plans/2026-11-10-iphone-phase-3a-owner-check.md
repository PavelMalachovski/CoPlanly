# iPhone phase 3, part A: the owner's check

**For:** the owner, alone, on their Mac. Nothing here needs a cloud session.
**Why:** part A (plan `2026-11-10-iphone-phase-3a-proof-and-skeleton.md`) is built and its CI is
green, but how the shell *feels* is the owner's to judge (spec
`docs/superpowers/specs/2026-11-10-iphone-phase-3-native-shell-design.md` §2). The answers below
settle the open points of the spec's §7; the ones on §7.2 and §7.3 decide whether part B keeps the
system's large title or falls back.
**The look to compare with:** the Design canvas "CoPlanly для iPhone",
https://claude.ai/artifact/732JJxDHeHtUp8zLFAdUTH (the Home, Family, Expenses, New event and Settings
boards). The boards bind the frame, the controls and the style, not a screen's content (spec §2).

Write each answer next to its item: **yes / no / notes**. "Same as the board?" applies where a board
shows the thing; elsewhere the question is "works?". A screenshot (⌘S in the simulator) helps with
any "no".

## 0. What CI has already shown

So the check can spend its time on feel rather than on whether things work at all. Every item is in
CI run 43316189630 (commit `0dbc7ed9`), job "iPhone app — build and launch (macOS)", on an iPhone 17
Pro simulator on iOS 26.5 with Xcode 26.6; its screenshots are the run's `ios-shell` artifact.

- The app builds, starts and draws; signing in by e-mail lands on the four tabs.
- Home, Family and Settings show large titles; Expenses an inline title with the back chevron.
- A back swipe pops Expenses, and a swipe let go of halfway leaves Expenses (and, over a screen
  with its own Compose bar, Data sources) where it was, with one bar.
- Tapping the selected Family tab pops Settings and Data sources together.
- Add expense opens as a sheet; swiping it down over a typed title asks "Discard changes?", "Keep
  editing" keeps it, the close button asks again, and Discard closes it.
- A form with its own Compose bar (New event, from Home) shows exactly one bar.
- Signing out shows the sign-in screen with no tab bar; signing in again opens fresh tabs.
- The iPhone writes every kind of shared document the way Android does (the write gate), on the
  emulators with the real rules.

What CI cannot show: how any of it feels, large titles collapsing, the edge of the bars over
scrolling content, the keyboard, memory and speed, the real Google sheet, a real Firebase project,
Keychain autofill, a reinstall, a time-zone change while the app runs.

## 1. Set up (once)

1. **Xcode 26** from the App Store, opened once so it installs its components and an iOS 26
   simulator runtime (Xcode → Settings → Components). A newer Xcode prints a compatibility warning
   from Kotlin and has worked in CI.
2. **A JDK 17** for Gradle and a **JDK 21** for the Firebase emulators
   (`brew install --cask temurin@17 temurin@21`), and **Node 22**.
3. **XcodeGen**: `brew install xcodegen`.
4. From the repository's root, on this branch:

   ```bash
   npm ci --prefix functions
   npm ci --prefix firestore-tests
   cd iosApp
   xcodegen generate
   open CoPlanly.xcodeproj
   ```

   Run `xcodegen generate` again after any change to `project.yml`, and after adding or removing the
   `GoogleService-Info.plist` (section 4).
5. In Xcode pick **an iPhone simulator on iOS 26** (the CI picks iPhone 17 Pro; an iPhone 16 is
   fine). The first **Run** (⌘R) takes a while: it fetches the Firebase and Google Sign-In packages
   and builds the shared Kotlin framework.

## 2. Against the emulators

In a second Terminal window, from the repository's root, start the emulators as CI does (JDK 21 on
the `PATH` for this window only):

```bash
export PATH="$(/usr/libexec/java_home -v 21)/bin:$PATH"
firestore-tests/node_modules/.bin/firebase emulators:start \
  --only auth,firestore,functions,storage \
  --project demo-coplanly
```

Leave it running. With no `GoogleService-Info.plist` in `iosApp/CoPlanly/`, the app talks to these
emulators. In a third window, seed a paired family (Alice and Bob) as CI's walk does:

```bash
node tools/ios/seed-shell-family.js
```

It prints Alice's e-mail and password. Run the app (⌘R) and sign in with them. The family starts
empty: add five or six expenses (Family → money row → "+") and a few events (Home → "Add event"), so
that lists scroll.

| # | What to do | What to look at | Answer |
| --- | --- | --- | --- |
| 2.1 | Look at the tab bar on Home, then on Expenses (pushed). | Liquid Glass tab bar; four tabs with their symbols; the bar stays on pushed screens (spec §2). | Same as the board? yes / no / notes |
| 2.2 | **§7.2** On Home and Family, scroll the content up and down. | Part A shows the large title **always**: the scroll view that would collapse it as the Compose list scrolls (§7.2's first try) was not built. Is a fixed large title acceptable for part B, or should part B build the first try, or the fallback (the large title as the first line of content, the small title in the bar once it scrolls away)? | Same as the board? yes / no / notes — and which of the three |
| 2.3 | **§7.3** On Expenses with several expenses, scroll the list. | Part A pads each screen's content below the bar, so the list does not run under the glass and iOS 26's soft edge has nothing to blur. Is that acceptable, or must the content run under the bars (part B, with §7.2's scroll view)? | Same as the board? yes / no / notes |
| 2.4 | **§7.1** Family → Expenses; go back with the back button. Then open Expenses again and go back with an edge swipe. | One clean pop each time; the bar and title change with the screen. | Same as the board? yes / no / notes |
| 2.5 | **§7.1** Open Expenses; start an edge swipe, stop halfway, and let go so it springs back. Do the same on Settings → Data sources. | The screen stays, with exactly one bar (native on Expenses, its own Compose bar on Data sources), and its back button still works. | Works? yes / no / notes |
| 2.6 | **§7.5** Expenses → "+" (Add expense). Then type a title and swipe the sheet down. | A page sheet with a grabber, Close and the title in the bar (the New event board's frame). The swipe asks "Discard changes?"; "Keep editing" keeps the sheet and the title; Discard closes it. With nothing typed, the swipe closes it at once. | Same as the board? yes / no / notes |
| 2.7 | **§7.4** In Add expense, tap the amount and then the description. | The keyboard does not cover the focused field or the bar's Save; the sheet's content scrolls to it. | Works? yes / no / notes |
| 2.8 | **§7.4** Chat tab → the thread with Bob; tap the composer and type two lines; send. | The composer sits above the keyboard, the newest message stays in view, and the thread's header stays on screen. | Works? yes / no / notes |
| 2.9 | **§7.6** Family → Expenses → Add expense (open, then close it), and Family → Settings → Data sources. Switch Home ↔ Calendar ↔ Chat ↔ Family ten times. In Xcode, watch the Debug navigator's Memory and CPU gauges. | Each tab comes back where it was left; switching feels immediate; memory settles rather than climbing with each switch (note the figure). | Works? yes / no / notes — memory: ___ MB |
| 2.10 | **§7.1** With Family → Settings → Data sources open, tap the Family tab. | Both screens pop together to Family. Tapping Calendar always opens Month on today. | Works? yes / no / notes |
| 2.11 | Settings → "Sign out of app" (at the bottom). On the sign-in screen tap "Create account" and sign up as a new account (any address, e.g. `carol@example.test`). | **Review Focus 5:** the sign-in screen has no tab bar; after sign-up none of Alice's expenses, events, chat or drafts appear anywhere (the new-event form starts empty), and the tabs open at their first screens. Then sign out and back in as Alice: her data returns from the emulators. | Works? yes / no / notes |
| 2.12 | **Review Focus 4.** With an event at 18:00 today and an expense dated today on screen, change the Mac's time zone while the app runs (System Settings → General → Date & Time: turn off "Set time zone automatically" and pick a city several hours away), then return to the app. | The event still says 18:00 and the expense the same date: the wall-clock value the data holds, never shifted. If the simulator does not follow the Mac, quit the app, set the scheme's environment variable `TZ` (Product → Scheme → Edit Scheme → Run → Arguments), e.g. `America/New_York`, run again, and compare. Optional: an event at 02:30 on 28 March 2027 with `TZ=Europe/Prague` (a spring-forward night) shows 02:30. Put the time zone back afterwards. | Works? yes / no / notes |
| 2.13 | **Review Focus 3.** Delete the app from the simulator (long-press its icon → Remove App, or `xcrun simctl uninstall booted app.coplanly`), then Run again. | The app starts at the sign-in screen, not signed in. After signing in as Alice, her expenses and events arrive in full (no day's worth missing). | Works? yes / no / notes |

## 3. Keychain password autofill

| # | What to do | What to look at | Answer |
| --- | --- | --- | --- |
| 3.1 | In the simulator, add a password in the Passwords app (or Settings → Passwords on an older runtime): Alice's address and password, for any website. Sign out of CoPlanly and tap the e-mail field. | The keyboard's bar offers "Passwords" (the key) and fills both fields from the Keychain. There is no separate "saved password" button on the screen (spec §5.6). On sign-up, iOS does **not** yet suggest a strong password: Compose Multiplatform 1.9.3 does not pass the new-password hint to iOS. | Works? yes / no / notes |

## 4. Against the real project, with a test pair

Only after section 2. **Use a test pair of accounts** made for this (two addresses that are not
your family's, paired with each other on the Android app or on the iPhone): spec §5.6 puts your own
family after the test pair.

1. In the Firebase console, register the iOS app `app.coplanly` if it is not there, and check
   Authentication → Sign-in method → Google is enabled.
2. Download `GoogleService-Info.plist` for `app.coplanly`. Check it holds `CLIENT_ID` and
   `REVERSED_CLIENT_ID`; if not, make sure Google Cloud console → APIs & Services → Credentials has
   an iOS client for bundle `app.coplanly`, and download it again (`iosApp/README.md`, "Google
   sign-in").
3. Put it in `iosApp/CoPlanly/` and run `cd iosApp && xcodegen generate`. **Never commit it**
   (`.gitignore` covers it; check `git status` shows nothing new).
4. Stop the emulators, delete the app from the simulator, and Run. The app reaches the real project
   only with no emulator host configured: `COPLANLY_EMULATOR_HOST` unset in the scheme's
   environment and blank in the target's build settings (as committed).

| # | What to do | What to look at | Answer |
| --- | --- | --- | --- |
| 4.1 | After the build, in Terminal: `plutil -p ~/Library/Developer/Xcode/DerivedData/CoPlanly-*/Build/Products/Debug-iphonesimulator/CoPlanly.app/Info.plist \| grep -A6 CFBundleURLTypes` | `CFBundleURLTypes` holds one URL scheme equal to the plist's `REVERSED_CLIENT_ID` (`com.googleusercontent.apps.…`). The build log's phase "Register Google's URL scheme" says it registered it. | Works? yes / no / notes |
| 4.2 | On the sign-in screen, tap "Continue with Google", then cancel the sheet (Cancel, or swipe it down). | Google's own sheet opens; cancelling returns to the sign-in screen with no error message. | Works? yes / no / notes |
| 4.3 | "Continue with Google" again, and sign in as the first test account. | The sheet completes and the app opens the tabs (or onboarding, for a new account) of that account. | Works? yes / no / notes |
| 4.4 | Walk Home, Calendar, Chat, Family → Expenses → Add expense; add an expense and an event; send a chat message. | The test family's real data appears; the co-parent's Android phone receives the expense, the event and the message (their push may come from the chat card or the expense's own type, CLAUDE.md item 15). | Works? yes / no / notes |
| 4.5 | Repeat 2.1–2.4 and 2.6 on real data, where it looks different from the emulators. | As in section 2. | Same as the board? yes / no / notes |
| 4.6 | Settings → "Sign out of app", then "Continue with Google". | The app signs out. Google's own session is kept (the app does not sign out of Google Sign-In, as Android keeps its credential too), so the sheet may offer the same Google account at once; choose the second test account if you have one on Google, or the same one. | Works? yes / no / notes |
| 4.7 | Delete the app and Run again (Review Focus 3 on the real project). | Starts signed out; "Continue with Google" signs in again and the data arrives in full. | Works? yes / no / notes |

Afterwards, remove the plist from `iosApp/CoPlanly/` (or keep it out of every commit) and run
`xcodegen generate` again.

## 5. Handing back

Send the answers (and any screenshots of a "no") back in the session that plans part B. Its plan
starts from them: §7.2 and §7.3 decide the large title and the content under the bars, and every
"no" elsewhere becomes a fix in part B or a decision for you first.
