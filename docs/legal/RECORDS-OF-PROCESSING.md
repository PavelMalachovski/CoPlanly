# CoPlanly — Record of processing activities (GDPR Art. 30(1))

> Mandatory from the first user: the Art. 30(5) exemption for organisations under 250 people does
> not apply to processing of special categories, and this app processes a child's health data.
> Kept up to date with the code: a change to what is collected, where it goes or how long it stays
> changes this record in the same pull request (`CLAUDE.md`, "Legal and GDPR"). The authority may
> ask for it at any time (Art. 30(4)).
>
> Written September 2026 from `firestore.rules`, `storage.rules`, `functions/index.js` and the
> app's data layer. See `LEGAL-REVIEW-2026-09.md` for the reasoning behind each period and basis.

## Controller

| | |
| --- | --- |
| Controller | {{LEGAL_ENTITY_NAME}}, {{REGISTERED_ADDRESS}}, IČO {{COMPANY_ID}} |
| Representative (Art. 27) | Not needed: established in the EU |
| Privacy contact | {{PRIVACY_CONTACT_EMAIL}} — read by {{NAMED_PERSON}} |
| DPO | Not appointed (LEGAL-REVIEW L-14); reassess at ~10,000 families |
| Joint controllers | None. Parents using the app for their own family are within the household exemption (Art. 2(2)(c)) for their own use; the company remains controller for the service (Recital 18). A professional admitted by both parents is an **independent** controller of what they read, under their own professional duties. |

## Processor

| Processor | Services | Terms | Location |
| --- | --- | --- | --- |
| Google Ireland Ltd / Google LLC (Firebase, Google Cloud) | Firestore, Cloud Storage, Cloud Functions, Authentication, Cloud Messaging, Analytics, Crashlytics, Cloud Logging; **Vertex AI** (Anthropic's Claude model, served by Google) for P15 once enabled | Google Cloud Data Processing Addendum; Firebase Data Processing and Security Terms; SCCs incorporated; for Vertex AI also Google's Service Specific Terms for generative AI / partner models (**to verify**, see P15) | Functions: `europe-west3`. Vertex AI: `AI_VERTEX_REGION`, an EU region (default `europe-west1`). Firestore/Storage: {{FIRESTORE_REGION}}. FCM, Auth, Analytics, Crashlytics: global |

Google's sub-processors are listed by Google and accepted under the Addendum; whether Anthropic
appears among them for Claude on Vertex AI is for counsel to confirm (P15). No other processor
is used: no email service, no support desk, no advertising or attribution SDK, and no AI service
other than the one in P15, which the company reaches only through Google Cloud.

## Processing activities

Security measures (Art. 30(1)(g)) common to every activity are in the last section; each row names
only what is specific to it.

### P1. Accounts and linking

| | |
| --- | --- |
| Purpose | Provide an account; link two co-parents; decide who may read what |
| Data subjects | Parents |
| Data | Name, email, optional profile photo (Google), optional date of birth and phone, auth identifier, parent slot, partner uids, FCM token, country and region, onboarding state, whether the family cares for children, pets or both (`caresFor`), health-data consent record `{version, atMillis}`, AI-assist consent record `{version, grantedAt}` (P15) |
| Legal basis | Art. 6(1)(b) contract; the consent record is kept under Art. 7(1) to demonstrate consent |
| Recipients | The linked co-parent (name, slot, photo) |
| Retention | Life of the account; deleted at once on account deletion |
| Where | `users/{uid}`, `families/{id}`, Firebase Auth, Room `users` |

### P2. Family records: calendar, custody, expenses, plan, change requests

| | |
| --- | --- |
| Purpose | The shared service |
| Data subjects | Parents; children (named in records); third parties named in event text |
| Data | Events (title, time, place, notes, photo), custody schedule, swaps, seasonal layers, contact windows, expenses and budgets (amount, currency, category, receipt photo), parenting plan answers, change requests |
| Legal basis | Art. 6(1)(b) |
| Recipients | The co-parent; a calendar friend (calendar only, time-limited); a professional with both parents' consent (calendar, custody, plan; ≤ 180 days); whoever holds a calendar-feed link a parent created |
| Retention | Life of the account. An individual deletion is a tombstone for **90 days** (so the other phone learns of it), then swept |
| Where | `events`, `custody_models`, `expenses`, `budgets`, `parenting_plans`, `change_requests`, `family_settings`; Storage `event_images/{familyId}/…`, `receipts/{familyId}/…` (photos: the two parents only — not calendar friends or professionals; `solo_{uid}/…` for the uploader alone before pairing) |

### P3. Event revision history

| | |
| --- | --- |
| Purpose | An honest history of the shared calendar, for the parents' own record and export |
| Data | Each saved version of a shared event, editor uid, device time, server time |
| Legal basis | Art. 6(1)(b) — part of the service the parents use; immutable by design |
| Recipients | Both parents (not friends or professionals) |
| Retention | Life of the account of the parent who saved it; the departing parent's revisions are deleted at once on account deletion (LEGAL-REVIEW L-17: owner decision on aligning with P5) |
| Where | `event_versions` |

### P4. Children's records, including health data

| | |
| --- | --- |
| Purpose | Both parents holding the same information about their child, including what is needed in an emergency |
| Data subjects | Children; third parties (doctors, teachers, emergency contacts) |
| Data | Name, date of birth, school, activities, contacts; **medical profile**: allergies, medications, conditions, blood group, vaccinations, notes, medical document photos |
| Legal basis | Art. 6(1)(b) for the record; **Art. 9(2)(a) explicit consent** of the parent, as the child's legal representative, for the medical profile — recorded with version and time (P1) |
| Recipients | The co-parent; a guest the parent admits to that one child's record, until the grant expires |
| Retention | Life of the record; tombstone 90 days after deletion; on consent withdrawal the medical profile of records this parent created is cleared |
| Where | `child_info`, `pets`; Storage `medical_photos/{familyId}/…`, `pet_photos/{familyId}/…` (`solo_{uid}/…` before pairing) |
| Note | Photos: readable by the family's two parents only, never by a guest; no download URL is minted; digest-checked; swept with the record's tombstone after 90 days (LEGAL-REVIEW L-4, fixed September 2026). Photos stored before L-4 are deleted by the operator-run `purgeLegacyPhotoPaths` |

### P5. Chat and files

| | |
| --- | --- |
| Purpose | Communication between the co-parents, kept as a reliable record |
| Data | Messages, read and delivery times, attachments (PDF, images) with SHA-256 |
| Legal basis | Art. 6(1)(b). After one parent deletes their account: **Art. 6(1)(f)** — the remaining parent's interest in their own correspondence, for **30 days**, with notice |
| Recipients | The two participants only (never friends or professionals) |
| Retention | Life of both accounts; when one is deleted, 30 days (`retainedUntilMillis`), then swept; when both are gone, at once |
| Where | `conversations`, `messages`; Storage `chat_attachments/` |

### P6. Family document vault

| | |
| --- | --- |
| Purpose | Documents both parents need (court orders, school letters) |
| Data | Files, their name, category, size, type, SHA-256 |
| Legal basis | Art. 6(1)(b) |
| Recipients | Both parents of the family |
| Retention | Life of the document; a deleted document's file is swept after 90 days; deleted with the uploader's account |
| Where | `family_documents`; Storage `family_documents/{familyId}/…` |

### P7. Exports and verification receipts

| | |
| --- | --- |
| Purpose | Let a court, lawyer or mediator verify that an exported record is unaltered |
| Data | Record ID, SHA-256 of the file, size, format, period, registration time; the generating uid and family id while the account exists |
| Legal basis | Art. 6(1)(b) while the account exists; **Art. 6(1)(f)** afterwards (scrubbed of uid and family), balanced in LEGAL-REVIEW L-6 |
| Recipients | Anyone holding the file or the record ID — they learn only the receipt, never who made it |
| Retention | **10 years** from registration; unregistered reservations **7 days** |
| Where | `export_receipts` (closed to every client) |
| Note | The export file itself is made on the phone and is never sent to the company |

### P8. Invited people: guests, calendar friends, professionals

| | |
| --- | --- |
| Purpose | Time-limited access a parent grants |
| Data subjects | Guests, friends, professionals (each with their own account) |
| Data | Their profile (name, email, Google photo), the grant, its expiry, consents (professionals) |
| Legal basis | Art. 6(1)(b) (the invited person's own account and the parent's instruction) |
| Retention | Until the grant expires or is revoked, then swept daily; invitations never accepted are swept 30 days after expiry (90 days after creation if they have none); accepted co-parent invitations kept for the life of the account — they prevent records being assigned to the wrong household (Art. 5(1)(d)) |
| Where | `invitations`, `calendar_friends`, `friend_profiles`, `professional_grants` |

### P9. Calendar-feed links

| | |
| --- | --- |
| Purpose | Show the family calendar in another calendar app |
| Data | SHA-256 of the token (never the token), family id, creator, created and last-fetched times |
| Legal basis | Art. 6(1)(b) |
| Recipients | Whoever holds the link — never private or deleted events, chat, expenses or child records |
| Retention | Until revoked; idle 90 days → swept; ends at unpair or account deletion |
| Where | `calendar_feeds` (closed to every client) |

### P10. Notifications

| | |
| --- | --- |
| Purpose | Tell a parent something changed |
| Data | Push type, addressee uid, family id, record id — never message text |
| Legal basis | Art. 6(1)(b) |
| Retention | Queue entries swept after **30 days** |
| Where | `notification_queue`; FCM |

### P11. Google Calendar integration (optional)

| | |
| --- | --- |
| Purpose | Import and export events to the parent's own Google Calendar |
| Data | OAuth tokens (on the device, encrypted); SHA-256 of the refresh token on the server |
| Legal basis | Art. 6(1)(b), at the parent's request |
| Recipients | Google Calendar API |
| Retention | Until disconnected or the account is deleted |
| Where | Device `EncryptedPreferences`; `google_oauth/{uid}` |

### P11a. School import from Bakaláři (optional)

| | |
| --- | --- |
| Purpose | Put the school's events and the child's school hours into the family calendar, at the parent's request |
| Data subjects | The child; the parent (their Bakaláři username) |
| Data | On the device only: Bakaláři refresh/access token, school base URL, username, child's display name and class id. Imported into events: per school day the first-lesson start and last-lesson end, days without lessons, event titles/descriptions/times for the child's class or the whole school. **Not** collected: the password (used once, never stored), grades, homework, absences, messages, individual lessons |
| Legal basis | Art. 6(1)(b) — the parent asked for the import |
| Recipients | The co-parent (as ordinary events of the chosen family). The school's Bakaláři server is contacted by the parent's phone directly; the school is the controller of its own Bakaláři data. `sluzby.bakalari.cz` receives only a town name |
| Retention | Tokens and connection details: until disconnect, sign-out or account deletion (device only). Imported events: as any event (P2) |
| Where | Device `EncryptedPreferences`; imported events in `events` |
| Note | The integration uses the community-documented Bakaláři API v3 (`client_id=ANDR`, as other third-party apps do); Bakaláři has published no terms for third-party clients. Reassess if it does |

### P12. Usage statistics and crash reports (optional)

| | |
| --- | --- |
| Purpose | Find slow screens and real crashes |
| Data | Screens opened, actions succeeding or failing, device model, OS, pseudonymous installation ID; no content, no user ID |
| Legal basis | **Art. 6(1)(a) consent**; § 89(3) Act No. 127/2005 Coll. for device access |
| Recipients | Google (Analytics, Crashlytics) |
| Retention | Analytics: 2 months (console setting — LEGAL-REVIEW §4); Crashlytics: 90 days |
| Transfers | Global, under the DPF and SCCs |

### P13. Server logs

| | |
| --- | --- |
| Purpose | Operating and securing the functions |
| Data | Uids, function names, errors — never names, emails, message text or tokens |
| Legal basis | Art. 6(1)(f) — security of the service (Recital 49) |
| Retention | 30 days (Cloud Logging `_Default`) |

### P15. AI writing help (optional, MON-12)

| | |
| --- | --- |
| Purpose | Draft a neutral reply in the chat, or a short summary of a month, at the requesting parent's request; the parent reads, edits and sends (or discards) the draft |
| Data subjects | The requesting parent; the co-parent (their messages, in a reply suggestion); children and third parties only as far as the messages mention them |
| Data | **Reply:** the last **20** messages of the thread (text, sender as "me"/"co-parent", time), attachment **file names** (never their bytes, paths or digests), the two parents' display names, the requester's optional note (≤ 500 characters) and language. **Summary:** figures the phone computed — days with each parent (by name), handovers, swaps proposed/accepted, event count, expenses and balances per currency, optional holiday fairness — validated to that exact list; **no message text**. **Kept by us:** `ai_usage/{uid}` — a UTC date and a request count; the consent record on `users/{uid}` (P1) |
| Legal basis | **Art. 6(1)(a)** consent of the requesting parent (recorded with its version and time, withdrawable in Settings), for their own data and request. The co-parent's messages: **Art. 6(1)(f)** — the requester's interest in answering correspondence addressed to them, with minimisation to 20 messages, no storage, transparency in the policy and a right to object. **Art. 9:** messages may mention a child's health; whether 9(2)(a) consent of the requester suffices for incidental health data in the co-parent's words is **for counsel** (DPIA R19) |
| Recipients | Google Cloud Vertex AI (processor), running Claude by Anthropic in the configured EU region. Nothing is sent to the co-parent: the draft goes back to the requester only |
| Retention | Prompt and output: **not stored by the company** — held in the function's memory for one call; never logged (logs: uid, task, outcome, token counts, latency, HTTP status). `ai_usage/{uid}`: overwritten daily, deleted with the account. **Google's side — to verify and configure before enabling:** Vertex AI's prompt caching and abuse-monitoring logging for partner models, and whether the project qualifies for zero data retention; record the answer here and in the policy's `{{AI_PROVIDER_RETENTION}}` |
| Where | `functions/ai-assist.js` (`aiAssist` callable, `europe-west3`); Vertex AI `rawPredict` in `AI_VERTEX_REGION` (EU only — the code refuses any other region); `ai_usage` (closed to every client) |
| Note | Off until `AI_ENABLED=true` with a model and an EU region is configured, and behind a client feature flag until billing (MON-11). A per-account daily limit (`AI_DAILY_LIMIT`, default 30) bounds cost and misuse. Receipt OCR stays on-device and never reaches this processing |

### P14. Privacy requests and breaches

| | |
| --- | --- |
| Purpose | Answer rights requests; keep the breach register (Art. 33(5)) |
| Data | Requester's email and request; breach facts |
| Legal basis | Art. 6(1)(c) legal obligation |
| Retention | Requests: 3 years after closure (limitation period, § 629(1) Civil Code); breach register: 5 years |

## Transfers to third countries (Art. 30(1)(e))

Functions run in the EU (`europe-west3`). AI writing help (P15) runs on Vertex AI in an EU region
the code enforces (`europe-…`). Firestore and Storage: {{FIRESTORE_REGION}} — must be an
EU location (LEGAL-REVIEW L-3). FCM, Firebase Authentication, Analytics and Crashlytics are global
Google services: Google LLC is certified under the EU–US Data Privacy Framework, and the SCCs in
Google's data processing terms apply as a fallback.

## Security measures (Art. 32; Art. 30(1)(g))

- **Access control on the server**: `firestore.rules` and `storage.rules`, tested offline on every
  change (`firestore-tests/`); every shared record gated on its family and live pairing; closed
  collections for receipts, feeds, OAuth fingerprints and the AI-assist quota.
- **Encryption**: TLS in transit; Google's encryption at rest; on the device, SQLCipher with a
  Keystore-wrapped key, sealed preferences, backup and device transfer disabled.
- **Minimisation by design**: private events and the journal never leave the device; on-device OCR;
  pushes without content; telemetry off until consent; AI writing help off until consent, capped at
  20 messages or a fixed list of numbers, and never stored or logged.
- **Integrity**: immutable messages and revisions; server timestamps; SHA-256 on every shared file
  and export.
- **Deletion**: scheduled sweeps for every period in this record; server-side account teardown.
- **Organisational**: breach procedure (`BREACH-PROCEDURE.md`); this record; the DPIA; admin access
  to the Firebase project limited to named people with two-step verification on their Google
  accounts.
