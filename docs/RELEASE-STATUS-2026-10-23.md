# Release status — 23 October 2026 (Android)

A one-page answer to "can CoPlanly go to Google Play?". The full, ordered owner checklist is
`docs/AUDIT-2026-09-play-final.md` §4 (phases A–E); `docs/ROADMAP.md` §3 tracks each item. This page
does not repeat them. It says where things stand and what to do first. iPhone is out of scope.

## Verdict

**The code is ready for a closed test. The release is held by work outside the code.** The design
audit grades every area at B or better (`AUDIT-2026-10-release.md` §1). CI covers the app on three
emulator levels, a two-parent e2e suite, an upgrade over the previous build and an R8 runtime probe.
Nothing in the code blocks an upload. What blocks it is accounts, deploys, a lawyer and a fortnight
of testers. None of that can be done from a cloud session.

## Done in code since the last audit (this branch)

- **The invited parent's first minute** (`docs/superpowers/specs/2026-10-23-invited-parent-first-run-design.md`):
  - the code survives a Play install;
  - an invitation screen names the inviter and states four rules both parents share;
  - a short path goes from the link to the calendar;
  - the first screens' copy now says what the app solves.
- The store listing already carries the same message (`docs/play-listing/`, `LAUNCH-PLAYBOOK.md`
  §3.3). Keep the two in step.

## What blocks publication, in the order to do it

| # | Step | Who | Checklist |
| --- | --- | --- | --- |
| 1 | Developer account (personal or organisation), identity and EU trader status | Owner, Play Console | A-1 |
| 2 | Confirm Firestore is in an EU location (irreversible once data exists) | Owner, Firebase | A-2 |
| 3 | Fill the policy placeholders (controller, address, mailbox) and send the policy, terms and DPIA to counsel — the longest pole | Owner, lawyer | A-3, E-1 |
| 4 | Recruit six real co-parent pairs (12 testers) | Owner | A-4 |
| 5 | Register `app.coplanly` in Firebase, new `google-services.json`, OAuth client package and SHA-1 | Owner, consoles | B-1 |
| 6 | Server: `functions/.env`, functions to `europe-west3`, the backfills, then rules and indexes, then **`firebase deploy --only storage`** (photos, vault and chat files fail live until then) | Owner, CLI | B-3 – B-7 |
| 7 | Host privacy, terms, deletion and home pages; build their URLs into the app | Owner, CLI + one small PR | C-1, C-2 |
| 8 | Upload keystore, five repository secrets, run *Release bundle* | Owner | C-3 |
| 9 | A device session: upgrade over real data, first run, REL-7 on the release build | Owner, phone | C-5 |
| 10 | Play Console: create the app (category Parenting), listing, app content and Data safety, the reviewer's demo family, internal testing, then app-signing SHA-1 into Firebase | Owner | D-1 – D-5 |
| 11 | Closed test, 14 days, ≥ 12 opted-in testers | Owner, testers | D-6, D-7 |
| 12 | Counsel's sign-off, Calendar scope verified by Google (or kept under 100 users), production | Owner | E-1 – E-5 |

Steps 1–4 can start today in parallel. Steps 3 and 11 decide the launch date: a lawyer's calendar,
and a fortnight that cannot be compressed.

## New on the device checklist from this branch

`DEVICE-CHECKLIST.md` §5.7 has three new checks:

- the invitation screen;
- the code surviving a real Play install, which needs the internal-testing track (step 10);
- the short path.

The install check is the only part of this branch CI cannot prove.

## Selling it: what the first minute now says

- **Who opens the app first.** It is usually the parent who was invited, often wary that the app
  is meant against them.
- **What they see first.** "{Name} invites you to CoPlanly". Then:
  - one calendar for the children's two homes;
  - the same calendar for both parents, with nobody in charge of it;
  - schedule changes wait for both parents' yes;
  - messages nobody can rewrite;
  - unlink at any time.

  Then the calendar the other parent already set up, in three taps.
- **Native review needed.** The Czech, German, Russian and Ukrainian wording of the new strings
  should be read by native speakers before the closed test.
