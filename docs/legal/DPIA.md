# CoPlanly — Data protection impact assessment (GDPR Art. 35)

> **Status: draft for sign-off.** Prepared September 2026 from the code, alongside
> `LEGAL-REVIEW-2026-09.md`. The controller's director signs §9 after counsel has read it.
>
> **Review it again:**
> - when a feature changes what is collected, who can read it, or where it is processed;
> - when the app enters a new market;
> - at the latest, **12 months after signature**.

## 1. Why this assessment is required

The EDPB criteria (WP248 rev.01), which the ÚOOÚ's list under Art. 35(4) follows, say two criteria
normally make a DPIA necessary. CoPlanly meets four:

| Criterion | How CoPlanly meets it |
| --- | --- |
| Sensitive data (Art. 9) | A child's medical profile: allergies, medications, conditions, blood group, vaccinations, notes, photographs of medical documents |
| Vulnerable data subjects | Children, who cannot exercise their rights themselves; and parents in conflict, where one may use data against the other |
| Data concerning children | Every family record is about a child |
| Systematic recording for legal use | An immutable chat, immutable event revisions, and an export with a server-registered fingerprint that is designed to be shown in court |

## 2. Description of the processing

**Nature.** A mobile app (Android) for two separated parents to share the following, with Firebase
as the backend:
- a custody schedule and a calendar;
- expenses;
- records about their children and pets;
- a chat with files;
- a document vault.

The backend services are Cloud Firestore, Cloud Storage, Authentication, Cloud Functions in
`europe-west3`, and Cloud Messaging. Analytics and Crashlytics run only with consent. The full
inventory of data, recipients, periods and measures is `RECORDS-OF-PROCESSING.md`; it is not
repeated here.

**Scope.**
- **Data subjects:** parents (users); children; third parties named in records (grandparents,
  doctors, teachers, emergency contacts); guests, calendar friends and professionals admitted by
  the parents.
- **Scale:** expected to be thousands of families in the first year, mainly in Czechia, Slovakia,
  Germany and Austria.
- **Duration:** the life of the account, then the periods in the ROPA.

**Context.**
- **Relationship.** The two users are former partners. The relationship between them is the
  defining risk factor: the app is built for people who may not trust each other.
- **Evidence.** Some will use the app's records in custody proceedings.
- **Who enters the data.** The children do not use the app. Everything about them is entered by a
  parent.

**Purposes.**
- Coordinating the care of a child between two homes.
- Sharing what both parents need to know, including health information needed in an emergency.
- Keeping an honest record of what was agreed and said.

## 3. Necessity and proportionality

| Question | Answer |
| --- | --- |
| Is each data category necessary for the purpose? | Yes for the shared calendar, custody, expenses and chat, which are the service itself. The **child's medical profile** is optional, off until consented to (L-2), and exists so both parents hold the same emergency information. The **adult's own medical profile** was not necessary and has been removed (L-1). |
| Is there a less intrusive way? | Private events stay on the device. The journal never syncs. Receipt OCR is on-device. Pushes carry no content. These are the less intrusive choices, already made. |
| Legal bases | The contract (Art. 6(1)(b)) for the service. Explicit consent (Art. 9(2)(a)) for health data. Legitimate interests (Art. 6(1)(f)) for tombstones, receipts and a departed parent's chat, each balanced in the ROPA. Consent (Art. 6(1)(a)) for telemetry. |
| Storage limitation | Every category has a period, and the periods are enforced by scheduled sweeps (`functions/index.js`). See the ROPA. |
| Transparency | A privacy policy written from the code. A consent dialog at the point of collection. The sign-in screen names the age limit and the terms. The export states on its face what it is and is not. |
| Rights | Access, rectification and erasure are in the app. Portability is the export (PDF/CSV). Objection and restriction go through the privacy contact, answered within one month. |
| Processors | Google (Firebase / Google Cloud) under the Cloud Data Processing Addendum. No other processor. |
| Transfers | Functions in the EU (L-3). Firestore and Storage in the location confirmed in the console (L-3, Ops). FCM, Auth, Analytics and Crashlytics under the DPF and SCCs. |

## 4. Risk register

**Likelihood** and **severity** are each rated 1 (low) to 4 (very high). **Risk** is their product
before measures; **residual** is the risk left after them. "Measures" names what exists in the
code today.

| # | Risk to the people concerned | L | S | Risk | Measures | Residual |
| --- | --- | --- | --- | --- | --- | --- |
| R1 | **An ex-partner keeps reading after separation.** Unpair must end access. | 3 | 3 | 9 | The audience narrows at unpair (`SHARED_AUDIENCE_COLLECTIONS`). The rules require a live pairing for the co-parent branch. Grants are deleted at unpair. Calendar feeds end. The chat is deliberately kept (both parents wrote it). | Low |
| R2 | **Photographs of a child's medical documents are reachable without authorisation**: any account for a known path; a lapsed guest or ex-partner through a kept URL. | 3 | 4 | 12 | Today only unguessable file names protect them. **Fix L-4:** family-keyed paths, rules, no download URLs. | **High until L-4** → Low |
| R3 | **A server-side breach of health data** (misconfigured rules, compromised admin credential). | 2 | 4 | 8 | Rules tested offline on every change (`firestore-tests/`, 692 cases). No client can list other families' data. Admin access limited to the owner's Google account. Google's encryption at rest. EU region. Breach procedure. | Medium |
| R4 | **A lost or shared phone exposes the family's records.** | 3 | 3 | 9 | The database is encrypted with SQLCipher under a Keystore key. Backup and device transfer are off. Tokens are held in encrypted storage. The Firebase cache and image cache rely on Android file-based encryption (disclosed). | Low |
| R5 | **Coercive control**: one parent uses the app to monitor or harass the other. | 3 | 3 | 9 | No location is collected. No read receipts beyond "delivered/read". A tone hint before sending. Messages are immutable, so harassment is recorded, not erased. Acceptable-use terms with suspension. A notice-and-action route (DSA Art. 16). **No block function**: the export and the terms are the remedy, by design, because blocking a co-parent breaks the shared schedule. | Medium (inherent) |
| R6 | **A record used in court is misrepresented or altered.** | 2 | 4 | 8 | Messages and revisions cannot be edited or deleted by clients. Revisions carry the server's clock. The export states it records what was written, not what happened. SHA-256 receipts let a court check a file is unaltered. | Low |
| R7 | **Erasure destroys the other parent's evidence.** | 3 | 3 | 9 | 30-day retention of the chat with notice (L-5). Receipts kept 10 years without identity (L-6). | Low |
| R8 | **Access by a third-country authority** to children's health data. | 1 | 4 | 4 | Functions in the EU. Firestore and Storage in the EU once confirmed (L-3). Google certified under the DPF. No data sent to any US service beyond Google's global infrastructure (FCM, Auth). | Low (if the database is EU) |
| R9 | **Over-broad sharing with invited people.** | 2 | 3 | 6 | Grants are always time-limited. A guest gets one child's record only. A friend gets the calendar only. A professional needs both parents' consent, may keep access for at most 180 days, and is refused the chat, expenses and child records. Every grant is visible to both parents and revocable by either. | Low |
| R10 | **Over-retention.** | 3 | 2 | 6 | Sweeps for: tombstones (90 d); notifications (30 d); lapsed grants; idle calendar feeds (90 d); departed chat (30 d); receipts (10 y); reservations (7 d); stale invitations. Inactive accounts: owner decision (L-16). | Low / Medium |
| R11 | **Account takeover** by a partner who knows the password. | 3 | 3 | 9 | Firebase Auth, password reset, Google sign-in. **No second factor yet** (L-18). | Medium |
| R12 | **Lock-screen disclosure** of the other parent's words. | 3 | 2 | 6 | Pushes carry a type, never text. The wording comes from the receiving phone. | Low |
| R13 | **Telemetry revealing family life.** | 2 | 2 | 4 | Off until consent. No content in events. No user ID set in Analytics. Pseudonymous installation ID, disclosed accurately (L-13). | Low |
| R14 | **Consent not demonstrable or not specific** (health data). | 3 | 3 | 9 | A dialog at the point of collection. Version and time recorded on the profile. Withdrawal in Settings clears what this parent entered (L-2). | Low |
| R15 | **The child's own rights ignored as they grow up.** | 2 | 2 | 4 | The policy says how a child, or a young adult, can exercise their rights through the privacy contact. The parents can delete a child's record at any time. | Low |

## 5. Measures summary

**Technical.**
- Server-enforced access rules, tested offline on every change.
- Device database encryption.
- An EU processing region.
- No content in pushes.
- On-device OCR.
- A private journal that never syncs.
- An immutable record with server clocks.
- Hashed calendar tokens.
- Time-limited grants.
- Scheduled deletion sweeps.
- Consent-gated telemetry, disabled before any code runs.

**Organisational.**
- The privacy contact, answered within one month.
- The breach procedure and register.
- The ROPA.
- This DPIA, reviewed yearly.
- Counsel's sign-off on user-facing texts.
- The rule that code changes to data handling change these documents in the same pull request
  (`CLAUDE.md`).

## 6. Consultation

The views of the data subjects (Art. 35(9)) are sought through the closed test's feedback. The
questions asked of testers:
- whether what the consent dialog says is clear;
- whether the 30-day window after a co-parent's deletion is right.

Record the answers here before the public release.

## 7. Residual risk and conclusion

With **L-4 fixed** and **the Firestore location confirmed in the EU**, no residual risk is high.
Prior consultation of the ÚOOÚ under Art. 36 is then **not required**.

Until L-4 is fixed, **R2 remains high** and the processing should not be offered to the public.

The two medium residual risks, R5 (coercive control, inherent in the use case) and R11 (account
takeover), are accepted, with the second factor planned (L-18).

## 8. Action plan

| Action | Risk | Owner | Due |
| --- | --- | --- | --- |
| Photos behind family-keyed rules, no download URLs; deploy `storage.rules` | R2 | Engineering | Before public release |
| Confirm the Firestore location; new EU project if it is in the US | R3, R8 | Ops | Before public release |
| Run `purgeParentHealthFields` after the L-1 build ships | L-1 | Ops | At release |
| Second factor | R11 | Engineering | Within 6 months of release |
| Inactive-account rule | R10 | Owner | Within 6 months of release |
| Tester consultation recorded in §6 | — | Owner | Before public release |

## 9. Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Controller (director of {{LEGAL_ENTITY_NAME}}) | | | |
| Counsel (advokát) | | | |
| DPO | Not appointed; see LEGAL-REVIEW L-14 | | |
