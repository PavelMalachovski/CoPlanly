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
| Google Ireland Ltd / Google LLC (Firebase, Google Cloud) | Firestore, Cloud Storage, Cloud Functions, Authentication, Cloud Messaging, Analytics, Crashlytics, Cloud Logging | Google Cloud Data Processing Addendum; Firebase Data Processing and Security Terms; SCCs incorporated | Functions: `europe-west3`. Firestore/Storage: {{FIRESTORE_REGION}}. FCM, Auth, Analytics, Crashlytics: global |

Google's sub-processors are listed by Google and accepted under the Addendum. No other processor
is used: no email service, no support desk, no advertising or attribution SDK, no AI service.

## Processing activities

Security measures (Art. 30(1)(g)) common to every activity are in the last section; each row names
only what is specific to it.

### P1. Accounts and linking

| | |
| --- | --- |
| Purpose | Provide an account; link two co-parents; decide who may read what |
| Data subjects | Parents |
| Data | Name, email, optional profile photo (Google), optional date of birth and phone, auth identifier, parent slot, partner uids, FCM token, country and region, onboarding state, whether the family cares for children, pets or both (`caresFor`), health-data consent record `{version, atMillis}` |
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
| Where | `events`, `custody_models`, `expenses`, `budgets`, `parenting_plans`, `change_requests`, `family_settings`; Storage `event_images/`, `receipts/` |

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
| Where | `child_info`, `pets`; Storage `medical_photos/`, `pet_photos/` |
| Note | Photos currently protected by unguessable paths only — LEGAL-REVIEW L-4, to be fixed before public release |

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

### P14. Privacy requests and breaches

| | |
| --- | --- |
| Purpose | Answer rights requests; keep the breach register (Art. 33(5)) |
| Data | Requester's email and request; breach facts |
| Legal basis | Art. 6(1)(c) legal obligation |
| Retention | Requests: 3 years after closure (limitation period, § 629(1) Civil Code); breach register: 5 years |

## Transfers to third countries (Art. 30(1)(e))

Functions run in the EU (`europe-west3`). Firestore and Storage: {{FIRESTORE_REGION}} — must be an
EU location (LEGAL-REVIEW L-3). FCM, Firebase Authentication, Analytics and Crashlytics are global
Google services: Google LLC is certified under the EU–US Data Privacy Framework, and the SCCs in
Google's data processing terms apply as a fallback.

## Security measures (Art. 32; Art. 30(1)(g))

- **Access control on the server**: `firestore.rules` and `storage.rules`, tested offline on every
  change (`firestore-tests/`); every shared record gated on its family and live pairing; closed
  collections for receipts, feeds and OAuth fingerprints.
- **Encryption**: TLS in transit; Google's encryption at rest; on the device, SQLCipher with a
  Keystore-wrapped key, sealed preferences, backup and device transfer disabled.
- **Minimisation by design**: private events and the journal never leave the device; on-device OCR;
  pushes without content; telemetry off until consent.
- **Integrity**: immutable messages and revisions; server timestamps; SHA-256 on every shared file
  and export.
- **Deletion**: scheduled sweeps for every period in this record; server-side account teardown.
- **Organisational**: breach procedure (`BREACH-PROCEDURE.md`); this record; the DPIA; admin access
  to the Firebase project limited to named people with two-step verification on their Google
  accounts.
