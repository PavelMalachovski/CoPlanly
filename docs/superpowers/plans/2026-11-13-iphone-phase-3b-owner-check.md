# iPhone phase 3, part B: the owner's check

**For:** the owner, alone, on their Mac. Nothing here needs a cloud session.
**Why:** part B (plan `2026-11-13-iphone-phase-3b-every-screen.md`) puts every destination under a
system bar. It makes every form a sheet, gives the back button a screen's inner steps, opens
`coplanly://` links, and lets a parent delete their account on the iPhone. CI proves the mechanics
on the emulators. How it feels, and the rest of the checks below, are the owner's to judge. Part A's
own check (`2026-11-10-iphone-phase-3a-owner-check.md`) is still open. Run it first, or alongside
this one: its §7.2 and §7.3 answers (large titles, content under the bars) still decide what part B
left as it was.
**The look to compare with:** the Design canvas "CoPlanly для iPhone",
https://claude.ai/artifact/732JJxDHeHtUp8zLFAdUTH. The boards bind the frame, the controls and the
style, not a screen's content.

Write each answer next to its item: **yes / no / notes**. A screenshot (⌘S in the simulator) helps
with any "no".

## 0. What CI shows

**Not yet confirmed.** On 14 November 2026 the owner ruled that CI is not awaited per task. One run
on the branch head comes after part B's last task. Until it is green, read the items below as what
the walk and the tour are written to prove. The run is the `ios-app` job, "iPhone app — build and
launch (macOS)".

- **The walk** (`ShellUITests.testShellWalk`, artifact `ios-shell`), in this order:
  - sign-in by e-mail;
  - the four tabs; the Chat thread's bar with the co-parent's name and its overflow menu; the
    calendar's bar with the month and Today, Filters, Settings and "+";
  - Family → Expenses, a back swipe, a swipe let go of;
  - Add expense as a sheet with Save in its bar, and a dirty swipe down asking "Discard changes?";
  - CustodySetup with a part open: the edge swipe is refused and Back closes the part;
  - Settings → Data sources, a cancelled swipe over it, and a tab tap popping two screens;
  - New event as a sheet whose Save waits for a title, closing without asking;
  - the keyboard under New event's description and the chat composer;
  - a `coplanly://pair` link;
  - sign-out and sign-in to fresh tabs;
  - last, deleting the account.
- **The tour** (`ShellUITests.testTour`, artifact `ios-tour`) is a screenshot of every destination
  it reaches, and `tour.txt`, every route with "reached" or the reason it was skipped. Section 3
  lists what it is written to reach.
- **The write gate and the iPhone's emulator tests**, as in part A, `IosAccountDeletionTest`
  included.

What CI cannot show: how any of it feels, a real Google account, a real project, memory and speed,
and the co-parent's phone at the same moment.

## 1. Set up

As part A's check §1 and §2: Xcode 26, the JDKs and Node, XcodeGen, `xcodegen generate`, and the
emulators running in a second window. Seed the family **with the tour's records**. They add an event
today with a change request about it, a child (Emma), a pet (Max), an expense and a document, so the
screens have something to show:

```bash
node tools/ios/seed-shell-family.js --tour
```

It prints Alice's e-mail and password. Run the app (⌘R) and sign in with them. A screen can also be
opened by a link while the app runs:

```bash
xcrun simctl openurl booted "coplanly://pair?code=ABC234"
```

## 2. Against the emulators

| # | What to do | What to look at | Answer |
| --- | --- | --- | --- |
| 2.1 | **Every tab.** Home, Calendar, Chat, Family; on each, open two of its screens and come back. | Every screen is under a system bar: a large title on the tabs' first screens, an inline one with the back chevron on pushed screens. No screen draws a second bar of its own under it, and nothing is drawn under the bar's glass. The calendar's month is the bar's inline title. | Same as the board? yes / no / notes |
| 2.2 | **Each form sheet.** Open, one at a time: New event (Calendar's "+"); Edit event (today's "Tour dentist" → Edit); Request change (from Edit event's bar); Add expense and Edit expense (Family → money row, "+" and the expense); a child's editor (Family → Child information → Emma → Edit); a pet's editor (Family → Pets → Max); a journal entry (Family → Private journal → New entry). | Each is a page sheet with a grabber, Close at the leading edge and Save (or its own primary action) at the trailing edge of its bar, with no second Save under the form. Save is disabled until the form can be saved. Type something and swipe down: each form that keeps an edit asks "Discard changes?"; New event closes without asking (its draft is kept). | Same as the board? yes / no / notes |
| 2.3 | **Back in CustodySetup.** Family → Custody schedule; open one part (for example Contact windows). Try the edge swipe, then the bar's back button, then the edge swipe again. | With a part open, the edge swipe does not leave the screen and does not close the part. The back button returns to the overview, and from there the swipe leaves CustodySetup. Do the same in Settings → Import from Bakaláři → Connect a school after its first step: back returns a step. | Works? yes / no / notes |
| 2.4 | **A `coplanly://` link.** With the app open on another tab, run the `openurl` command above. Then `coplanly://guest?code=ABC234`. Then sign out and run the pair link again, and sign in. | The pair link opens the pairing screen with ABC234 in the code field, and its redeem says no invitation matches. The guest link opens Guest access. A link opened while signed out waits for the sign-in and then opens, as on Android. | Works? yes / no / notes |
| 2.5 | **Account deletion, with a test account only.** Sign up a fresh account (e.g. `carol@example.test`), add an expense, then Settings → Delete account → both confirmations. Then sign up again with another fresh address. | The sign-in screen with no tabs. Signing in with the deleted account's address and password fails. The new account starts empty: none of the deleted one's expense, drafts or chat. | Works? yes / no / notes |
| 2.6 | **The carried checks.** Run `docs/DEVICE-CHECKLIST.md` §3.37, §3.38 and §3.39. | §3.37: the calendar's bar and the swap selection. §3.38: onboarding and the keyboard. §3.39: the hidden Chat tab, a dirty swipe down on every form, Save beside Back, chat search in the title view, any hang, and sign-out, deletion and sign-in. | Works? yes / no / notes, per section |
| 2.7 | **Memory and speed**, as part A's 2.9, now with every screen reachable: open a stack three deep on each tab and switch tabs ten times. | Memory settles; switching is immediate; no moment where the app stops answering (a 30-second hang was seen once in CI and not reproduced). | Works? yes / no / notes — memory: ___ MB |

## 3. The tour's destinations

**To be confirmed against the first `ios-tour` artifact.** This is what `ShellUITests.testTour`
is written to reach, read from its code, against the 51 routes of `AppDestinationCatalog.all`.
`tour.txt` in the artifact is the authority. Replace this section with what it says, and name every
route it did not reach.

Written to reach (38 routes):
- **The tabs, and Home:** `auth` (the sign-in screen, before signing in), `home`, `calendar`,
  `conversations` (the Chat tab), `family`, and `change_requests` (Home → Updates → "See all").
- **From the Family tab:** `expenses`, `support`, `custody_setup`, `parenting_plan`, `child_info`,
  `pets`, `contacts`, `family_documents`, `month_review`, `export`, `journal`, `pairing`,
  `coparent_profile`, `friends`, `professionals` and `stickers`.
- **One level further:** `add_expense`, `edit_expense`, `child_detail`, `edit_child_info`,
  `edit_pet` and `journal_entry`.
- **From Settings:** `settings`, `data_sources`, `calendar_feed`, `school_import`, `school_connect`
  and `my_profile`.
- **From the calendar, and by link:** `add_event`, `edit_event`, `request_change` (from Edit
  event's bar), and `guest_accept` (a `coplanly://guest` link).

Written as skipped, with the reason (13 routes):
- `loading`: shown only until the start destination is known.
- `privacy_consent`: the iPhone collects no telemetry and never asks.
- `onboarding`: the seeded parent has finished it. Section 2.6 (§3.38) covers it with a new account.
- `unassigned_records`, `earlier_families` and `earlier_family`: their rows are drawn only while
  records of no family wait, or after a family ended. The seeded parent has neither.
- `custody_conflict`: only after a pairing whose two schedules disagree.
- `friend_detail`: needs an admitted family friend, which takes a third account.
- `friend_profile`, `friend_calendar`, `professional_calendar` and `professional_plan`: a family
  friend's or a professional's screens, for their accounts.
- `chat`: with one conversation, the Chat tab shows the thread in place (design item 7).

## 4. Handing back

Send the answers, any screenshot of a "no" and the first `tour.txt` back in the session that plans
part C. Every "no" becomes a fix or a decision for you first.
