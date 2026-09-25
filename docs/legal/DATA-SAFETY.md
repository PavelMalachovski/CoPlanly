# Play Data Safety — draft answers

> **Derived from the code, not from memory.** Every row below was checked against the
> Firestore collections, the Storage rules and the SDKs the app actually initialises. Re-check
> it against the code before submitting: a wrong Data Safety declaration is a policy
> violation, not a typo, and Google compares it against what the binary does.
>
> Sources: `firestore.rules`, `storage.rules`, `data/local/entity/`, `di/FirebaseModule.kt`,
> `data/analytics/AnalyticsManager.kt`, `data/crashlytics/CrashlyticsManager.kt`,
> `data/mlkit/`, `functions/index.js`.
>
> ### Owner must fill
>
> | Placeholder | What goes in |
> | --- | --- |
> | `{{WEB_DELETION_URL}}` | Where `web/delete-account/` is hosted — Play asks for it in the data-deletion section |

## Summary answers

| Question | Answer | Basis |
| --- | --- | --- |
| Does your app collect or share any of the required user data types? | **Yes** | |
| Is all data encrypted in transit? | **Yes** | Firebase SDKs use TLS throughout |
| Is data encrypted at rest on the device? | **Yes, for the app's database** | Room opens through SQLCipher; the passphrase is wrapped by an Android Keystore key (SEC-2). The Firebase SDK's offline cache and Coil's image cache are plaintext files under Android's file-based encryption only — the privacy policy says so (September 2026). Play does not ask this question — the row is here because the privacy policy makes the claim and something has to say what backs it |
| Do you provide a way for users to request that their data is deleted? | **Yes** | Settings → Account → Delete account, backed by the `deleteAccount` callable, which also removes the Storage files of every record it deletes (September 2026). The web route Play requires is `web/delete-account/`, at {{WEB_DELETION_URL}} |
| Is some data kept after a deletion request? | **Yes, for a bounded time** | A record deleted *individually* is a tombstone for 90 days (`TOMBSTONE_RETENTION_DAYS`) before the sweep removes it. An *account* deletion hard-deletes at once, tombstones included. Copies already downloaded to the co-parent's phone stay there — the privacy policy says so |
| Is data collection required, or can users choose? | **Required** for the account and shared content; **optional** for the medical profile, photos, Google Calendar, and — since REL-5 — analytics and crash reporting |
| Have you committed to Play's Families policy? | **No.** Target audience **18+ only**, **not** in the Families programme, no child imagery or "kids" wording in the listing. Settled by the terms of service (adults only) and the sign-in screen's age line (`LEGAL-REVIEW-2026-09.md` L-12) |

## Data types

"Shared" in Play's sense means sent to a third party. The co-parent is not a third party for
this purpose — they are another user of the same account family — but transfers to Google as
our processor are declared.

| Data type | Collected | Shared | Optional | Purpose |
| --- | --- | --- | --- | --- |
| Name | Yes | No | No | Account management, app functionality |
| Email address | Yes | No | No | Account management (email invitations were removed in August 2026) |
| Phone number | Yes | No | **Yes** | App functionality — `User.phone` on the profile and onboarding, and the phone numbers of emergency, school, activity and vet contacts (third parties' numbers, entered by a parent) |
| Address | Yes | No | **Yes** | App functionality — addresses on emergency and school contacts in the child's record |
| Other info | Yes | No | **Yes** | App functionality — a child's date of birth, school and activity details |
| Device or other IDs | Yes | No | No | App functionality (the FCM push token stored on `users/{uid}`) and, with consent, analytics/crash reporting (Firebase installation ID) |
| User IDs | Yes | No | No | Account management |
| Photos | Yes | No | Yes | App functionality — receipts, event images, medical and pet photos, photos in the document vault and sent in chat |
| Files and docs | Yes | No | Yes | App functionality — the family document vault and PDFs sent in chat (MON-23). Always shared with the co-parent by design; optional to use |
| Calendar events | Yes | No | No | App functionality |
| Messages (in-app) | Yes | No | No | App functionality |
| Health info | Yes | No | Yes | App functionality — the **child's** medical profile, only after the parent's explicit consent (a dialog, recorded with its version and time; withdrawable in Settings). The parent's own medical profile was removed in September 2026 (`LEGAL-REVIEW-2026-09.md` L-1, L-2) |
| Purchase/financial info | Yes | No | Yes | App functionality — shared expenses and budgets. **Not** payment data: the app processes no payments |
| App interactions | Yes | No | **Yes** | Analytics — consent-gated since REL-5 |
| Crash logs | Yes | No | **Yes** | Diagnostics — consent-gated since REL-5 |
| Diagnostics | Yes | No | **Yes** | Diagnostics — consent-gated since REL-5 |
| Approximate/precise location | **No** | — | — | Re-checked: the manifest declares only INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS and CAMERA, and no location API is called |
| Contacts | **No** | — | — | Re-checked: `ContactsContract` appears nowhere; an emergency contact is typed by hand |
| Payment info | **No** | — | — | No billing exists yet — **revisit when it does** |

## Notes worth writing into the declaration

**Health data.** The child's medical profile is the most sensitive thing the app holds:
allergies, medications, conditions, blood group, vaccinations, notes, and photographs of
documents. It is optional, entered by a parent **after an explicit-consent dialog**, and readable
only by the two parents and by guests they explicitly invite for a limited time. Declare it as
**Health info → collected, optional**. The app collects no health information about the adult
users.

**Where it is processed.** Cloud Functions run in `europe-west3`; Firestore and Storage in the
project's location, which must be in the EU (`LEGAL-REVIEW-2026-09.md` L-3).

**Every photo and file is access-controlled by its family** (L-4, September 2026; MON-23 before
it). Receipts, event photos, medical and pet photos, vault documents and chat attachments are
stored under a path that names the family's two parents, and `storage.rules` lets only those two
download them — not a guest, a calendar friend or a professional. No public download link is ever
created. A photo taken before a parent linked a co-parent is readable by that parent alone until
the server moves it into the family. The one caveat `storage.rules` states: the path still names
an ex-partner after an unpair. This does not change the declaration (*Photos: collected, shared
with the co-parent*); it is what makes "shared" mean "with the other parent" and nobody else.

**The Bakaláři school import collects no credentials.** The parent signs in on the phone, which
talks to the school's server directly; the password is used once and never stored, and only a
refresh token stays on the device, encrypted. What it imports (school events, the day's first and
last lesson times, days without lessons) becomes ordinary *Calendar events*, already declared. Do
not declare the school login as collected: nothing about it reaches us.

**Receipt OCR is on-device.** ML Kit's bundled model recognises receipt text without the
photograph or the text leaving the device. Nothing about it is collected or shared, and it is
worth saying so in the listing — it is a genuine differentiator in this category.

**Private events never leave the device.** Events marked private are excluded from every sync
path. They are not collected in Play's sense.

**Analytics and crash reporting are optional, and off until asked** (REL-5, shipped). Three
things now have to be true at once before either SDK collects anything, and this is what the
declaration should say:

1. Both auto-initialise **switched off**: `firebase_analytics_collection_enabled` and
   `firebase_crashlytics_collection_enabled` are `false` in the manifest, so nothing is collected
   in the window between process start and the app applying an answer.
2. The user has answered **yes** on the first-run screen, which is shown before sign-in and is
   changeable afterwards in Settings → App. An unanswered or declined state collects nothing.
3. The build allows it — release only, as of the August 2026 audit; debug builds never report
   whatever was answered.

**Declare both as optional, not required**, and declare the answer as changeable. The relevant
Play data types are *Crash logs* and *Diagnostics* / *App interactions* under App activity — all
"Collected, not shared", "Optional", purpose: Analytics and App functionality.

**No advertising, no ad IDs, no tracking.** The app declares no advertising SDK and does not
link data to third-party identifiers.

**AI.** No user data reaches a generative model: the AI subsystem was deleted in August 2026
(MON-7). **If any AI feature ships, this declaration must be revisited**, because the
prompts would carry calendar contents and message text to a third party.
