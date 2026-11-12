# EduPage import: lesson hours, school events, tests and homework on the family calendar

Status: design, approved approaches by the owner on 12 November 2026. Source: Zuzana's item 6 from
the tester meeting of 11 November 2026 ("Sync with EduPage. She will give the password if needed.
It is enough to know from when to when the child has lessons and, above all, the events, because
the child has to be prepared for them — given money and the like").

## Intent

Many Slovak and Czech schools run EduPage instead of Bakaláři. A parent whose child is at such a
school today copies the school's events into the calendar by hand, or forgets them. The point is
preparation: a trip that needs money, a test that needs revision, a day off that needs a babysitter.
Both parents should see these on the shared calendar, imported once by whichever parent connects.

The Bakaláři import (MON-8, "Things that are easy to get wrong" item 35) already does exactly this
for one school system. EduPage becomes the second system behind the same importer, the same
screens and the same rules.

## Goals

- **G1 Connect.** A parent connects an EduPage account from the existing *Connect a school* screen,
  picks the child and the family, and the import runs.
- **G2 What is imported**, all for the chosen child only:
  - **Lesson hours**: one event per school day, first lesson's start to last lesson's end, like
    Bakaláři's `hours` kind.
  - **School events** from the timeline (trips, school events, "bring money").
  - **Days off** (holidays and free days the school publishes), merged into runs as for Bakaláři.
  - **Tests and homework that carry a date** (owner decision, 12 November 2026: "if it is there,
    let it be"). One all-day event on the due or test date.
- **G3 Daily, on its own.** The import runs in the existing daily job `school_import_daily` and on
  "Update now", without asking for the password again (see Approaches, sign-in).
- **G4 Both platforms.** The client lives in `shared-data`'s `commonMain` on Ktor, so the iPhone
  gets it with no second implementation.

## Non-goals

- **Grades, teacher messages, attendance, lunches.** Not calendar facts, and a child's grades are
  not the other parent's business through the calendar.
- **Substitutions** as their own events. The hours event follows the timetable the server returns
  for the day; a cancelled lesson that changes the day's first or last lesson changes the hours
  event through the ordinary fingerprint rule, nothing more.
- **Writing anything to EduPage.** Read only.
- **Stickers** (Zuzana's item 7). A separate conversation the owner is having with her.

## Anti-goals

- **The password leaving the phone.** It goes only to the school's EduPage server, as the Bakaláři
  password goes only to the school's server. No Cloud Function, no proxy.
- **Deleting by absence.** EduPage's timeline drops old items and a school may clear its calendar
  in summer. Item 35's rule holds: nothing is deleted because the school no longer lists it. The
  one deletion stays the Bakaláři one: a school-hours event for a day that became a day off.
- **Another child's or another class's items** on this child's calendar (`SchoolEventAudience`'s
  rule: the child, the child's class, or the whole school — nothing else).
- **Copying code from `edupage-api`** (GPL-3.0). The protocol is learned from it and from recorded
  traffic; the client is written from scratch.
- **A silent stop.** When the stored sign-in stops working (password changed, a second-factor
  prompt), the connection shows "needs your password", as Bakaláři's `NEEDS_PASSWORD` does.

## Constraints

- **No public API.** EduPage is reached the way its web app reaches it: per-school host
  `https://<school>.edupage.org`, a CSRF token from `/login/?cmd=MainLogin`, a form POST to
  `/login/edubarLogin.php` (or the newer `akcia=getToken`/`akcia=login` pair), a session cookie
  (`PHPSESSID`), then the timetable (`/timetable/server/currenttt.js?__func=curentttGetData`) and the
  timeline (`/timeline/` and the user home data). Two-factor sign-in redirects to `/login/twofactor`.
  These shapes are taken from the open-source `edupage-api` 0.13.3 and must be confirmed against a
  real account before they are trusted.
- **No live server in CI, and none in the cloud sessions today**: `*.edupage.org` is refused by the
  session's proxy. The client is tested against recorded responses (as `HttpImportProbe` and the
  Bakaláři tests do). Recording needs `portal.edupage.org` and Zuzana's school host allowed in the
  session's environment, plus her test account; until then the fixtures are written from the
  protocol description and marked as such, and the first live run is
  `docs/DEVICE-CHECKLIST.md`'s new EduPage check.
- **A wildcard host cannot be allowed** in an environment, so each school's host is added by name.
- **Stored text is fixed.** The derived event ids, the connection record's JSON and the ledger are
  stored data; Bakaláři's must not change by a byte (`SchoolImportParityTest`).
- Everything item 35 says about families, audiences and quiet writes applies unchanged:
  `announce = false`, `eventType = "school"`, not private, `forMembers = [child:<id>]`, the
  importer's own slot as owner, shared with the event's own family's co-parent
  (`FamilyAudience`), `NeedsFamily` with two families and no hint.

## Approaches

Three were considered on 12 November 2026; the owner chose **A**.

- **A. Own Ktor client in `shared-data`, beside Bakaláři (chosen).** `SchoolImporter` learns more
  than one school system through a small "school source" seam; EduPage is the second source. Both
  platforms, the same tests as Bakaláři, verified live on a phone with Zuzana's account.
- **B. Sign in through a WebView, then the same client.** CAPTCHA and two-factor would pass
  naturally, but the app never sees the password (contradicts the sign-in decision below) and it is
  Android only.
- **C. Fetch from our own server (a Cloud Function).** Rejected: the password and the school's data
  would leave the phone, breaking item 35's "from the phone to the school".

**Sign-in, owner decision (12 November 2026): the password is stored on the phone, encrypted**, in
the connection's record in the sealed preference store (`EncryptedPreferences` on Android, the
Keychain-backed store on the iPhone). EduPage has no refresh token — only a session cookie that
expires — so item 35's "the password is used once and dropped" cannot hold for EduPage. The
alternative (keep only the cookie and ask again when it expires) was rejected because the import
would stop every few days or weeks. Consequences the build carries:

- The password is **EduPage's alone**: a Bakaláři connection still stores no password.
- It is never logged, never in a crash report, never in a `toString`, and is removed with the
  connection (disconnect, sign-out, account switch, account deletion — the three
  `SchoolConnectionStore.clearAll()` calls item 35 lists).
- The legal documents change in the same pull request (Legal item 1): `PRIVACY-POLICY.md`,
  `RECORDS-OF-PROCESSING.md` and `DATA-SAFETY.md` say a school password is kept on the device,
  encrypted, only for EduPage, and sent only to the school's EduPage server; `web/privacy/` is
  regenerated.
- The session cookie is kept too, and reused until EduPage refuses it; only then does the import
  sign in again with the stored password. A refused password or a two-factor prompt during a
  background sign-in sets `NEEDS_PASSWORD`; the parent signs in again from the school screen.

## Shipping before a live recording (owner decision, 12 November 2026)

The client is written now, from the protocol description, with fixtures marked as inferred. **The
EduPage row on the connect screen is hidden in release builds** behind a build flag
(`BuildConfig`, read through `AppBuildValues` like the other flags) until Zuzana has run the
device check with her account; debug builds show it. Until then nothing in a release build offers
EduPage (design item 8). Turning the flag on in release is its own small pull request, after the
device check passes and the fixtures have been replaced or confirmed by recorded traffic.

## Design

### The source seam

`SchoolImporter` today calls `BakalariAuthorizer` and `BakalariClient` directly. It gets one seam in
`data/school/`, named by the builder (for example `SchoolSource`), with what the importer asks of a
school system:

- the timetable for a range of weeks, as `SchoolWeek`s (days with a first and last lesson, and days
  off);
- the school's events, as `SchoolEvent`s;
- the child's dated tasks (tests and homework), new;
- a typed failure that maps to `SchoolConnectionStatus` (`NEEDS_PASSWORD` or `ERROR`).

The Bakaláři source wraps today's authorizer and client, unchanged. The importer picks the source
from the connection's system. Every decision stays in `SchoolImportPlanner`.

### The connection record

- **System.** The stored connection gains a `system` field, `"bakalari"` or `"edupage"`. A record
  with no `system` is Bakaláři (every record on a phone today). `SchoolConnection` (the screen's
  model) gains it too, so the screen can say which system a connection is.
- **EduPage's secrets** in the record: the password and the last session cookie, beside the
  existing ledger and child. Bakaláři's tokens stay as they are.
- **Ids (builder check, my call).** One EduPage parent login can list several children, and each
  parent usually has their own EduPage login. So EduPage's ids name the **child**, not the login:
  - the connection id is `SchoolConnectionId.of(uid, baseUrl, username + "|" + studentId)`, one
    connection per child, as Bakaláři already is in effect;
  - the derived event ids are `"edupage|" + host + "|" + studentId + "|" + kind + "|" + key`, with
    no username, so the two parents' own logins import the **same** event once instead of twice.
  Bakaláři's connection ids and `"bakalari|…"` event ids stay byte for byte.
- **Under a key an older build does not read (builder check, my call).** EduPage records are stored
  under their own key prefix, which `SchoolConnectionStore`'s Bakaláři prefix does not match, so a
  downgraded build never tries Bakaláři's refresh against an EduPage host or rewrites the record
  without its password. `clearAll()` and the exemption from `EncryptedPreferences.clear()` cover
  both prefixes.
- **Kinds.** `SchoolImportKind` gains `task` (a test or homework). Its key is EduPage's own item id.

### What is read from EduPage

- **Timetable** for the import window (four weeks, as Bakaláři), for the chosen child. A day's hours
  event runs from the first lesson's start to the last lesson's end; times come from the school's
  bell schedule when a lesson names only a period.
- **Timeline items**, filtered to the chosen child:
  - school events (EduPage's event type) → `SchoolEvent`, through `SchoolEventAudience` (the child,
    their class, or the whole school);
  - tests and homework with a date → an all-day event on that date, title as EduPage words it
    (prefixed by the subject when the item names one), description the item's text;
  - everything else (grades, messages, attendance) is ignored.
- **Days off** from the timetable's free days or the school's calendar; a day the holiday provider
  already draws is skipped, as for Bakaláři.

The import window, the fingerprint rule ("rewrite only when title, description, start or end
changed, through `copy()`"), the ledger, "nothing before today", "never resurrect a deleted event"
and the merging of days off are the planner's, shared with Bakaláři unchanged.

### Connecting

The *Connect a school* screen gains a system choice (shown only while the release flag allows
EduPage). For EduPage the parent enters the username and password; sign-in goes through EduPage's
own portal (`login1.edupage.org`), which redirects to the school, so the school's address is
optional and asked only when the portal cannot find the school (normalised to
`<school>.edupage.org`).

A parent login and a child's own login are both accepted (my call); a child's login lists one
child. **Second factor (plan-time narrowing):** EduPage confirms a second factor by approval in its
own app or by an e-mailed code. The first build tells the parent to approve the sign-in in EduPage
and try again; a "remember this device" token, if the live account shows one, is stored with the
password under the same legal lines. Whether EduPage asks again on every new session is learnt from
the live account.

Then the existing flow: pick the child (pre-selected when there is one), pick the family at two or
more (never pre-selected), import.

The removed "EduPage — coming soon" row (September 2026) is not brought back as a placeholder; the
real row arrives with this feature (design item 8).

### Checks

- Unit tests on recorded responses: sign-in (success, wrong password, two-factor redirect, expired
  cookie then re-sign-in), timetable parsing, timeline parsing and filtering, the derived ids, the
  connection record's round trip with and without `system`, and Bakaláři's ids and record unchanged.
- The R8 probe gets EduPage's client and parsers through the minified Ktor pipeline, as
  `HttpImportProbe` does for Bakaláři.
- `docs/DEVICE-CHECKLIST.md` gets an EduPage check: connect Zuzana's account, see hours, events,
  tests and homework on both phones, change the password on EduPage and see "needs your password".
- `tools/check-e2e-coverage.js` is not affected: no new Firestore collection, Storage path, push
  type or callable.
- New strings in all five locales.

## Left to the builder

- The seam's name and exact shape, and whether the Bakaláři source is a thin wrapper or the
  authorizer and client implement it directly.
- Which EduPage sign-in variant (the `edubarLogin.php` form or the `getToken`/`login` pair) to use
  first, and whether to fall back to the other; decided from a recorded real exchange when one is
  available.
- How a lesson's time is read when the timetable gives a period number (the school's bell schedule
  is in the same data EduPage's web app loads).
- The exact EduPage timeline item types that count as an event, a test and homework; recorded
  traffic decides, and the fixtures say which were seen live and which were inferred.
- The second-factor screen's wording and layout, following `InlineBanner` and the existing
  connect screen.
