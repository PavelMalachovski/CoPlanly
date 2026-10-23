# The invited parent's first minute — design

Date: 2026-10-23. Owner conversation in session `kuplenly-readiness-review`.

## Intent

CoPlanly is usually opened first by the parent who was **invited by the other parent**, not by one
who went looking. Testers report that this parent arrives wary — partly apathy ("why another app"),
partly a quiet fear that the app is a tool against them, maybe for court. Today nothing in their
first minute answers either:

1. The invitation message is a code and two links. With no app installed, the code is **lost through
   the Play install** (no install referrer; `web/pair/` is not hosted, `PAIRING_WEB_LINK` is false),
   so they dig it out of the messenger and type it.
2. The sign-in screen says only "Shared calendar for co-parenting" / "One shared calendar for both
   of you".
3. The wizard opens on "Link with your co-parent", then "A few questions before you start" — about
   blood groups and a grandparent's phone. Then children, schedule, split… even though the
   co-parent already entered them.
4. The one moment that sells the app — *everything is already set up for you* — comes last.

**Goal:** within one minute the invited parent knows who invited them, what the app is, that the
rules are the same for both parents, and that there is almost nothing for them to do.

## Goals

- G1. The invitation code survives a Play install: the invited parent never types it.
- G2. Before linking, the invited parent sees an **invitation screen**: "{Name} invites you", one
  sentence on what CoPlanly is, four true facts about fairness, and the link button.
- G3. After linking, a **short path** to the calendar: they can open the calendar as soon as what
  the co-parent entered is shown, instead of walking every wizard step.
- G4. The first-run copy says what problem the app solves (sign-in, intro step, Home's unpaired
  prompt, the invitation message the inviter sends).
- G5. A short, current release-readiness status for the owner (separate document).

## Non-goals

- Welcome slides for a parent who arrives on their own (option C, declined for now).
- iPhone.
- Any change to what pairing does, the rules, or the server.

## Anti-goals (would make this a failure even if it works)

- **Promising what the code does not do.** Every fact on the invitation screen must be true today
  (design item 8). In particular: an event marked *important* on the co-parent's day lands without
  an answer, so the screen may say "a schedule change or a day swap waits for your yes", **never**
  "nothing changes without your yes".
- **Conflict or court framing** ("protect yourself", "evidence"). `LAUNCH-PLAYBOOK.md` §3.5.
- **Redeeming a code without the parent's yes.** A code from the referrer is only a prefill; it goes
  through `previewPairingInvitation` and the parent's tap like every other code (item 37).
- **"Mom"/"Dad"** anywhere. Names only.

## Approaches considered

- **A. Copy only.** Cheap, but the code is still typed and the full wizard still walked; apathy is
  untouched.
- **B. Invited-parent path + copy (chosen by the owner).** Referrer, invitation screen, short path,
  plus A's copy.
- **C. B + welcome slides for self-arrivals.** Duplicates the already-good Play listing for the rarer
  arrival; deferred.

## Design

### 1. The code survives the install (G1)

- The Play link in the invitation carries the code:
  `https://play.google.com/store/apps/details?id=app.coplanly&referrer=pair%3D<CODE>`
  (`PLAY_STORE_URL` becomes a function of the code; the professional and friend invitations keep
  the bare link — their codes go to other callables).
- `web/pair/`'s Play fallback (the `intent:` link's `S.browser_fallback_url` and the plain Play
  button) carries the same referrer, so it is ready when the page is hosted.
- On launch the app reads the Play Install Referrer **once** (`com.android.installreferrer`), parses
  `pair=<code>`, validates it with `PairingUri`'s code rules, and hands it to the existing
  `MainActivity._pendingPairingCode`. The existing `PairingDeepLinkEffect` then opens the pairing
  screen with the code as soon as the parent is signed in.
- "Once": a flag in `EncryptedPreferences` records that the referrer was read, so a later launch,
  sign-out or second account never re-opens it. A referrer older than the code's validity is
  harmless: the preview refuses an expired code with its normal message.
- Nothing leaves the phone. Still, it is a new data source → `PRIVACY-POLICY.md`,
  `DATA-SAFETY.md`, `RECORDS-OF-PROCESSING.md` get a line in the same PR, and `web/privacy/` is
  regenerated (Legal item 1).

### 2. The invitation screen (G2)

The existing confirmation — `PairingViewModel.redeemCode` → `previewPairingInvitation` →
`InviteConfirmation` → the "Link with {name}?" dialog — becomes a **full-screen invitation view**
inside `PairingScreen`. It is the one point every code already passes through (typed, scanned,
deep link, referrer), so the invited parent sees it however the code arrived, in onboarding or from
Settings.

Content (English base; final wording is the builder's within these facts):

- Headline: "**{First name} invites you to CoPlanly**" (no name → "You're invited to CoPlanly").
- One line: "One calendar for your children's two homes — whose day it is, what changed, who paid."
- Four facts, each a row with an icon (`SectionGroup`/`SectionRow` or a plain list in that anatomy):
  1. "You both see the same calendar. Neither of you is in charge of it."
  2. "A schedule change or a day swap waits for your yes."
  3. "Sent messages can't be edited or deleted — by either of you."
  4. "You can unlink at any time in Settings."
- Primary: "Link with {name}" (→ `answerConfirmation(true)`). Text button: "Not now"
  (→ `answerConfirmation(false)`).
- `PairingError.Unknown` (no preview) shows the same screen without a name, as the dialog did.
- **A parent who is already linked** and enters another co-parent's code (Settings → "Add another
  co-parent") keeps today's "Link with {name}?" dialog: "invites you to CoPlanly" and the
  explanation of the app would be wrong for them (builder-check decision, my call).

Built from the existing primitives (`HeroIcon`, `SectionGroup`, `StickyActionBar`); strings in all
five locales; a Roborazzi screenshot and a UI-tour shot. The inviting parent's own screens do not
change.

### 3. The short path after linking (G3)

On onboarding's `CoParent` step, once `CoParentLink.Linked` and the "Already entered by {name}"
summary is shown, add a second action: "**Looks right — open the calendar**" beside the normal
Continue. It walks only what is still needed:

- `Notifications`, unless already granted (pushes are how requests reach this parent — keep it);
- a **short Profile, always** (owner decision, 23 October 2026): the name (prefilled from the
  account, required) and the country, preselected from the device's region when it is one
  `HolidayCountry` has, else the stored value. The region row appears under the same rule as on
  the full step. Colour and date of birth are left out; the parent can set them later in Settings.
  The country decides the holidays and school vacations on the calendar (CLAUDE.md item 8), so a
  parent outside Czechia must not inherit the CZ default unasked;
- then `finish()`.

The skipped steps stay reachable from Settings, as today. Continue keeps the full wizard.

**When the action appears (builder-check decision, my call):** only when the summary found at least
one record (children, pets, custody schedule or split). While the fetch is still running, or when
nothing arrived, only Continue is offered — "looks right" over an empty summary would land the
parent on an empty calendar with setup skipped by both parents.

### 4. Copy (G4)

All five locales; Czech/German/Russian/Ukrainian drafted here, flagged for a native pass.

| Where | Today | New (English) |
| --- | --- | --- |
| `auth_tagline` | Shared calendar for co-parenting | One calendar for children with two homes |
| `auth_sign_up_subtitle` | One shared calendar for both of you | Whose day it is, what changed, who paid — the same for both parents |
| `onboarding_intro_title` / `_body` | A few questions… blood group… | Lead with purpose: a few questions so the calendar is ready (children, schedule, cost split); only the name is required; the emergency details are mentioned as optional, last |
| `onboarding_coparent_body` | If your co-parent already uses CoPlanly… | Shorter: linking puts you both on one calendar; if they set things up already, you see it at once |
| `home_pairing_prompt` | Pair with your co-parent to share the calendar, expenses and messages. | Benefit-first: both of you see the same days, changes are agreed, costs split — once your co-parent joins |
| `pairing_share_message` (+`_web`) | {name} invites you to CoPlanly. Code: … | Calm, two lines: what it is ("one calendar for the children's two homes"), that schedule changes need both to agree, then the install link (code comes along) and the code as a fallback. iPhone line kept |

The exact strings are the builder's, under the anti-goals; `tools/check-invariants.js` must pass
(format arguments unchanged in order and count, or every locale updated together).

### 5. Release-readiness status (G5)

`docs/RELEASE-STATUS-2026-10-23.md`: one page in English — what is done in code, what still blocks
publication (from ROADMAP §3, re-checked against the tree), ordered, each with who can do it
(owner in a console / a phone / code). Not a new audit; it points into `AUDIT-2026-10-release.md`
and `ROADMAP.md`. Summarised to the owner in Russian in chat.

## Constraints

- `CLAUDE.md` rules: Compose only, stateless composables, Hilt, five locales, sentence case,
  theme tokens, KDoc, Conventional Commits, `pane()` unaffected (pairing is already a detail screen).
- `minSdk` 26: the Install Referrer library supports it.
- No Firebase in CI; the referrer reader must be behind an interface so unit tests fake it.
- Screenshot baselines are recorded only by the Regenerate workflow — touch
  `.github/regenerate-request` after the UI lands.
- This session has no Android SDK unless one is installed; whatever cannot be built here is pushed
  for CI to verify, and said so.

## Left to the builder

- Final wording within the facts above; icons; exact layout.
- Where the referrer reader lives (a `data/pairing/InstallReferrerSource` behind a domain
  interface is the expected shape) and its tests.
- Whether the invitation view replaces the dialog in place or is a full-screen state of
  `PairingScreen` (either, as long as every code path reaches it).
- Device-checklist entry: referrer survives a real Play install (only testable once the listing
  exists — internal testing track).
