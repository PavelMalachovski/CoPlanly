# CoPlanly — Legal and GDPR review, September 2026

> **What this is.** A review of the app as built, the way a data-protection and consumer lawyer
> would read it before a first public release in the EU. It covers the GDPR, the Czech
> implementing act, Czech consumer and contract law, the Digital Services Act, and Google Play's
> policies. It was read from the code, the security rules and the Cloud Functions, not from the
> product description. Each finding says what the law requires, what the app did, and what was
> changed or is still owed.
>
> **Scope and limits.** The review assumes the owner's answers of 25 September 2026:
> - the controller is a Czech *s.r.o.*;
> - the market is the EU, with Czech law as the base;
> - the parent's own medical profile is removed;
> - a departed parent's chat is kept 30 days for the co-parent;
> - export receipts are kept 10 years;
> - consent to a child's health data is asked for in a dialog before the first entry;
> - Cloud Functions move to `europe-west3`.
>
> It is written to be handed to the company's Czech attorney (*advokát*). The documents below are
> drafts they can sign off quickly, not a substitute for that sign-off. Health data about children
> is the one area where an hour of local counsel is always money well spent.

**Companion documents** (all in `docs/legal/`):

| Document | What it is | GDPR article |
| --- | --- | --- |
| `PRIVACY-POLICY.md` | The notice to users | Art. 12–14 |
| `TERMS-OF-SERVICE.md` | The contract with users | — (civil and consumer law, DSA Art. 14) |
| `DPIA.md` | Data protection impact assessment | Art. 35 |
| `RECORDS-OF-PROCESSING.md` | Record of processing activities | Art. 30 |
| `BREACH-PROCEDURE.md` | What to do in the first 72 hours of a breach | Art. 33–34 |
| `DATA-SAFETY.md` | The Play Console data safety answers | — (Google Play policy) |

## 1. The shape of the processing, in one paragraph

Two separated parents each hold an account and link them. Between them they share:
- a custody schedule and a calendar;
- expenses;
- records about their children, including a **medical profile**: allergies, medications,
  conditions, blood group, vaccinations, doctors' notes and photographs;
- an immutable chat, with files;
- a document vault.

They can also:
- admit a guest, a calendar friend or, with both parents' consent, a professional;
- export a verifiable record for a court.

Data sits in Firebase: Firestore, Storage, Auth, Cloud Functions and FCM, with analytics and crash
reporting behind consent. The child never has an account.

**Three features make this processing high-risk under the GDPR at once:**
- **special-category data** (Art. 9): health;
- **vulnerable data subjects**: children, and parents in conflict;
- **systematic records meant for use in legal proceedings.**

## 2. Findings

Severity is the lawyer's view of exposure, not of engineering effort:
- **Critical:** unlawful now, fix before any release.
- **High:** a regulator or a court would object.
- **Medium:** an obligation not yet met.
- **Low:** good practice.

| # | Severity | Finding | Status |
| --- | --- | --- | --- |
| L-1 | Critical | The adult parent's own medical profile was shared with the ex-partner and never disclosed | **Fixed**: feature removed |
| L-2 | High | A child's health data had no demonstrable explicit consent | **Fixed**: consent dialog, record, withdrawal |
| L-3 | High | Every Cloud Function ran in the USA | **Fixed**: `europe-west3`. Firestore's own region: **Ops** |
| L-4 | High | Medical, pet, receipt and event photos: any signed-in account may read, overwrite or delete them, and download URLs outlive revocation | **Open**: see §3 |
| L-5 | High | Deleting an account erased the other parent's own messages at once | **Fixed**: 30-day window with notice |
| L-6 | Medium | Export receipts were kept forever | **Fixed**: 10 years; unregistered reservations 7 days |
| L-7 | Medium | Invitations that were never accepted were never deleted | **Fixed**: swept |
| L-8 | Medium | No DPIA | **Written**: `DPIA.md` |
| L-9 | Medium | No record of processing activities | **Written**: `RECORDS-OF-PROCESSING.md` |
| L-10 | Medium | No breach procedure | **Written**: `BREACH-PROCEDURE.md` |
| L-11 | Medium | Privacy policy: legal bases, recipients, retention, transfers, third parties | **Rewritten** |
| L-12 | Medium | Terms of service: consumer law, DSA, notice at sign-up | **Rewritten**, and the app now shows the notice |
| L-13 | Medium | The telemetry consent called pseudonymous data "anonymous" | **Fixed**: five locales |
| L-14 | Low | DPO: not appointed, and the decision not recorded | **Recorded**: §2.14 |
| L-15 | Low | Console settings a regulator will ask about | **Ops**: §4 checklist |
| L-16 | Low | Inactive accounts are kept indefinitely | **Owner decision** |
| L-17 | Low | A departed parent's event revisions go at once, their chat after 30 days | **Owner decision** |
| L-18 | Low | No second factor on sign-in | **Open** |

### L-1. The parent's own medical profile (Critical, fixed)

**What the app did.** `User.medicalProfile` and `User.allergies` held the adult parent's own
allergies, medications and conditions. They were stored on `users/{uid}`, which the co-parent may
read (`isPartnerOf`), and `ProfileScreen` rendered them to the co-parent. The privacy policy never
mentioned them.

**Why it matters.**
- **Health data** (Art. 9(1)) about an adult was disclosed to their former partner, in the one
  relationship where that is most likely to be used against them.
- **No legal basis.** There was no explicit consent (Art. 9(2)(a)), and no other Art. 9(2) ground
  fits.
- **No purpose.** The co-parent has no purpose for which the data is necessary (Art. 5(1)(b),
  (c)), and the processing was never disclosed (Art. 13(1)(c), (e)).

A supervisory authority would treat this as the most serious finding in the app.

**What changed.**
- The feature is gone from every screen.
- The Room columns are cleared by migration 43→44.
- The app writes `FieldValue.delete()` for both keys on the next profile save.
- `firestore.rules` refuses a profile write that carries either key.
- The operator callable `purgeParentHealthFields` erases what older builds left on the server.
  **It must be run once after deploy** (§4).

### L-2. Consent to a child's health data (High, fixed)

**What the law requires.** The legal basis is explicit consent (Art. 9(2)(a)), given by a parent
as the child's legal representative. Parental responsibility includes representing the child; see
§ 858 and § 892 of the Civil Code. Entering the child's allergies into a tool the two parents
share is an ordinary matter that either parent may decide alone (§ 876(3)).

The controller must be able to **demonstrate** the consent (Art. 7(1)). It must be specific and
informed (Art. 4(11)), and as easy to withdraw as to give (Art. 7(3)).

**What the app did.** The policy relied on consent, but nothing in the app asked for it or
recorded it.

**What changed.** The first time a parent opens a child's medical section, a dialog explains:
- that it is health data about their child;
- that the co-parent can read and edit it, and any guest they admit can read it;
- that it is stored on the company's servers;
- that it is optional;
- how to withdraw.

"I agree" records `healthDataConsent {version, atMillis}` on the parent's profile, in Room and
Firestore. A new wording bumps the version and asks again. Settings shows the consent and
withdraws it. Withdrawal clears the medical details of the children **this parent** created,
and only once every medical photo in them has been deleted — all or nothing, so a withdrawal never
leaves a photo behind while saying it is done. Records the co-parent created rest on the
co-parent's own consent.

**Residual point for counsel.** When the two parents disagree, the medical section holds what
either of them entered. The app cannot resolve a dispute over parental responsibility, and the
terms (§6) say so. A court order restricting one parent's access to health information is a
matter for the parents and the court, not for this processor. If served with one, the controller
should act on it (see `BREACH-PROCEDURE.md` §7, "court orders").

### L-3. Transfers to the United States (High, fixed for functions; Ops for the database)

**What the app did.** All 30 Cloud Functions ran in `us-central1`. Those functions:
- send pushes, with chat context;
- react to a child's medical profile changing;
- delete accounts;
- mint calendar feeds.

Every call was therefore a transfer to a third country (Chapter V).

Google LLC is certified under the EU–US Data Privacy Framework, and Google's data processing terms
carry the Standard Contractual Clauses. The transfer was lawful. But:
- it rests on an adequacy decision that has been annulled twice before (*Schrems I* and *II*);
- a DPIA must weigh the risk of US government access to children's health data (Art. 35(7)(c)).

**What changed.**
- Every function is declared with `functions.region('europe-west3')` (Frankfurt).
- The app and `web/verify/` call that region.
- The functions README gives the one-time move for the live project.

**Still owed (Ops).**
- **Confirm the Firestore and Storage location** of `coparently-a39c9` in the console (Firestore
  → Settings). A Firestore location cannot be changed.
- If it is `nam5` or any `us-*` location, create a new project in `eur3` or `europe-west3`
  **before** the first public release, while there is no user data to move.
- The privacy policy states the region in `{{FIRESTORE_REGION}}`, which must be filled with what
  the console says.

Some transfers remain even so and are disclosed:
- FCM delivery;
- Firebase Authentication;
- Analytics and Crashlytics, after consent;
- the Google Calendar API, if connected.

Google operates these globally under the DPF and SCCs.

### L-4. Photos behind unguessable URLs, not behind rules (High, open)

**What the app does.** `storage.rules` lets **any signed-in account** read and write:
- `medical_photos/{childInfoId}/{fileName}`;
- `pet_photos/…`;
- `receipts/…`;
- `event_images/…`.

The app stores each photo's **download URL**, which carries a token that bypasses the rules
entirely. As a result:
- a guest whose access expired keeps every medical photo URL they ever loaded;
- an ex-partner keeps the URLs after unpairing;
- any account that learns a path can overwrite or delete the file.

MON-23's vault and chat attachments already show the right shape: the path names the family, the
rules check the reader is one of that family's parents, and no download URL is ever minted.

**Why it matters.** Art. 32(1) requires security appropriate to the risk, and Art. 25(1) requires
data protection by design. For photographs of a child's medical documents, "unguessable" is not
access control. A breach here would be notifiable (Art. 33) and very likely reportable to the
families (Art. 34), because the data is health data about children.

**What to do.**
1. Move all four prefixes to family-keyed paths, gated like `family_documents/`.
2. Download as the reader rather than through URLs.
3. Migrate the existing objects.
4. Deploy `storage.rules`. The live bucket still runs its July 2026 rules; see `CLAUDE.md` known
   issues.

This is the one High finding not fixed in this change, because it rewrites how four features store
files. **It should block the public release.** It is the next engineering item (`ROADMAP.md`,
SEC-6).

### L-5. The chat after one parent deletes their account (High, fixed)

**What the app did.** `deleteAccount` deleted every conversation the departing parent was in,
including the other parent's own messages and files, at once and without notice. For a separated
parent that thread is often the evidence of what was agreed.

**The balance.** The departing parent has the right to erasure (Art. 17(1)). Their messages are
also the other parent's personal data, as correspondence the other parent received and answered.
The other parent has a legitimate interest in keeping a record they may need in proceedings
(Art. 6(1)(f); and Art. 17(3)(e) where a claim is in view). Erasing without notice fails the
fairness principle (Art. 5(1)(a)) towards the remaining parent.

**What changed.**
- The conversation is marked `retainedUntilMillis` (30 days) instead of being deleted.
- The remaining parent gets a push (`coparent_account_deleted`, which replaces the plain "unlinked"
  push for them) and a banner in the thread. Both name the date, and the banner opens the export.
- They can read and export the thread read-only; nobody can write to it.
- A daily sweep deletes it when the 30 days end.
- If the remaining parent deletes their own account first, it goes at once.
- Everything else of the departing parent's goes at once, as before.

The privacy policy states all of this under "Deleting your account".

### L-6. Export receipts (Medium, fixed)

A receipt (SHA-256, period, format, size, registration time) survives account deletion, scrubbed
of the account and family. That is right: erasing one parent must not un-verify evidence the other
has filed.

It was, however, kept **forever**, which storage limitation (Art. 5(1)(e)) does not allow.

After deletion the basis is Art. 6(1)(f): the legitimate interest of the family and of courts in
verifying a record. Keeping a hash and a date weighs lightly against the data subject, because
nothing left identifies them.

The period is **10 years from registration**. That is the objective limitation period (§ 629(2) of
the Civil Code); family proceedings and any claims arising from them fall inside it. Reservations
that never received a hash are deleted after 7 days.

### L-7. Invitations never accepted (Medium, fixed)

An invitation carries the inviter's name and email. Unaccepted ones were never deleted. They are
now swept 30 days after expiry, or 90 days after creation when they have no expiry.

Accepted co-parent invitations are kept. They are the evidence `stampOwnBlankFamilyIds` and
`hadAnotherCoParent` rely on to avoid assigning records to the wrong household. That is a data
accuracy purpose (Art. 5(1)(d)), and it is recorded in `RECORDS-OF-PROCESSING.md`.

### L-8. DPIA (Medium, written)

Art. 35(1) requires one where processing is likely to result in a high risk. The EDPB's criteria
(WP248 rev.01), which the ÚOOÚ's list under Art. 35(4) follows, count here:
- sensitive data;
- vulnerable data subjects;
- systematic evaluation or recording used in legal contexts;
- data concerning children.

Two criteria are normally enough, and this app meets four. `DPIA.md` is the assessment. Its
conclusion is that, with L-4 fixed, the residual risk is acceptable and no prior consultation
(Art. 36) is needed. **Without L-4 fixed, that conclusion does not hold.**

### L-9. Record of processing activities (Medium, written)

Art. 30(5) exempts organisations under 250 people **unless** they process special categories.
This app does, so the record is mandatory from day one. It is `RECORDS-OF-PROCESSING.md`.

### L-10. Breach procedure (Medium, written)

A breach must be notified to the ÚOOÚ within 72 hours (Art. 33) and, for high risk, to the people
affected (Art. 34). Every breach, notified or not, must be documented (Art. 33(5)). A company that
learns how to do this during its first breach misses the 72 hours. `BREACH-PROCEDURE.md` is the
runbook.

### L-11. Privacy policy (Medium, rewritten)

The draft was unusually good: written from the code, honest about limits. What a lawyer adds:
- **One legal basis per purpose**, including the three purposes that are not the contract:
  - Art. 6(1)(f) for keeping tombstones and receipts;
  - Art. 6(1)(f) for the departed parent's chat;
  - Art. 6(1)(a) for telemetry.
- **The right to object** (Art. 21), which exists wherever 6(1)(f) is relied on.
- **Third parties whose data a parent enters**: grandparents, doctors, teachers and emergency
  contacts. They are told through the published policy (Art. 14(5)(b)).
- **The child as a data subject**: whose rights the parents exercise until the child can, and
  what happens then.
- **Professionals** as independent controllers of what they read.
- A **retention table**.
- **Transfers** named precisely: DPF and SCCs.
- **No automated decision-making** (Art. 22).
- The removal of the parent's medical profile (L-1).
- The consent mechanism (L-2).
- The chat window (L-5).

### L-12. Terms of service (Medium, rewritten; the app shows the notice)

**Consumer law** applies even though the app is free. The user "pays" with personal data, and the
digital-content rules implementing Directive (EU) 2019/770 (§ 2389a ff. of the Civil Code) apply
to that exchange. The draft needed:
- **Liability.** A cap cannot exclude liability to a consumer for harm to their natural rights, or
  caused intentionally or through gross negligence (§ 2898 of the Civil Code). The cap now says so.
- **Changes to the terms.** A unilateral change binds only if the contract foresaw it, the user
  was told in time, and the user may refuse by leaving (§ 1752). The old "continuing means
  acceptance" clause is replaced.
- **Conformity of the digital service** and the updates owed (§ 2389a ff.).
- **Out-of-court dispute resolution.** The trader must name the ADR body, here the Czech Trade
  Inspection Authority (ČOI; Act No. 634/1992 Coll., § 14 and § 20e). The EU ODR platform closed
  in July 2025, so there is no longer an ODR link to give.
- **The Digital Services Act.** The app stores what users give it, so it is a *hosting service*
  (Art. 3(g)(iii)). It shares within a family, not with the public, so it is not an *online
  platform*. It therefore owes:
  - points of contact (Art. 11, 12);
  - content-restriction rules in the terms (Art. 14);
  - a notice-and-action route (Art. 16);
  - statements of reasons when it acts (Art. 17);
  - reporting of threats to life or safety (Art. 18).

**Notice before contract.** The terms must be available before the contract is made (§ 1751,
§ 1820). The app never showed them. The sign-in screen now says:
- CoPlanly is for adults;
- continuing confirms the person is 18 or older;
- once `web/terms/` is hosted, continuing also accepts the terms, with links.

**Consistency between product and terms.** The app sells a *verifiable* export while the terms
said "we do not attest to anything". Both are now true and say so. The company attests to one fact
only: a file with this fingerprint was registered at this time. It attests to nothing about the
file's contents.

### L-13. "Anonymous" telemetry (Medium, fixed)

Analytics and Crashlytics tie data to a random installation identifier. That is pseudonymous
personal data (Recital 26), not anonymous data. A consent given to a wrong description is not
informed (Art. 4(11)). The consent screen and the Settings row now say what is sent and to whom,
in all five locales.

The design of the consent was already right:
- off until answered;
- refusing is as easy as agreeing;
- the answer can be changed in Settings;
- the SDKs are disabled in the manifest before any code runs.

That design also satisfies § 89(3) of Act No. 127/2005 Coll. (the ePrivacy rule on reading from and
writing to a device).

### L-14. Data Protection Officer (Low, decision recorded)

A DPO is mandatory where the **core activities** consist of **large-scale** processing of special
categories (Art. 37(1)(c)).
- **Core activity.** A medical profile is one optional section of a co-parenting calendar, so
  whether it is "core" is arguable.
- **Scale.** At launch the scale is not large. The EDPB guidelines (WP243) look at the number of
  data subjects, the volume, the duration and the geographic extent.

**Decision:** no DPO at launch. The privacy contact is monitored by a named person at the company.
Reassess when the app passes about **10,000 families** or enters a second country's market at
scale, and record that reassessment here.

### L-15. Settings a regulator will ask about (Low, Ops)

See the checklist in §4.

### L-16. Inactive accounts (Low, owner decision)

An account nobody has opened for years keeps a child's medical profile on the server indefinitely.
Storage limitation (Art. 5(1)(e)) favours a rule. The recommended rule:
- after **24 months** without a sign-in, email the account;
- delete it 60 days later unless the person signs in.

This is an owner decision because it deletes families' records.

### L-17. Revisions and chat on account deletion (Low, owner decision)

On deletion the departing parent's event revisions go at once, while their chat now stays 30 days
for the co-parent. The export prints both, so the co-parent loses part of the record at once and
part a month later.

**Recommendation:** keep the revisions for the same 30 days, under the same basis and notice. It is
a small server change once the owner agrees.

### L-18. Second factor (Low, open)

Accounts hold a child's health data and are a target in hostile separations: a partner who knows
the other's password. Art. 32 favours offering a second factor. Firebase Authentication supports
TOTP and SMS multi-factor on the Identity Platform tier. Recommended before growth, not blocking.

## 3. What blocks a public release, legally

1. **L-4**: photos behind rules, not URLs, and `storage.rules` deployed.
2. **L-3 Ops**: Firestore location confirmed in the EU, or a new EU project.
3. **The controller's identity** filled in the policy and terms: the s.r.o.'s name, registered
   office, IČO and a monitored privacy address. Google Play also shows the developer's address
   publicly.
4. **The policy and terms hosted** (`web/privacy/`, `web/terms/`), with
   `publishedPrivacyPolicyUrl` and `publishedTermsUrl` set in `app/build.gradle.kts`, so the app
   links them.
5. **Counsel's sign-off** on the policy, the terms and the DPIA, and the DPIA signed by the
   company's director.
6. **§4's console checklist** done.

Everything else in §2 is fixed in this change or is a decision that does not block release.

## 4. Operations checklist (console and deploy)

- [ ] Firestore → Settings: record the location. If it is in the US, see L-3.
- [ ] Accept the **Google Cloud Data Processing Addendum** in the Google Cloud console, and the
      **Firebase Data Processing and Security Terms** in Firebase → Project settings → Privacy. Add
      the company's details and the privacy contact.
- [ ] Analytics:
  - data retention set to **2 months** (the minimum);
  - **Google signals off**;
  - ads personalisation off;
  - data sharing with Google products off.
- [ ] Crashlytics: nothing to set (90-day retention), but record it in the ROPA, which already
      says so.
- [ ] Cloud Logging: keep the default 30-day `_Default` bucket, and do not route logs to a longer
      sink. Functions log uids, not names or emails; keep it that way.
- [ ] Move the functions to `europe-west3` (`functions/README.md`, "Region").
- [ ] `firebase deploy --only storage`, after L-4.
- [ ] Run `purgeParentHealthFields` once after the app build carrying L-1 is out (L-1).
- [ ] Host `web/privacy/`, `web/terms/`, `web/delete-account/` and `web/verify/`, then set the URLs
      in `app/build.gradle.kts`.
- [ ] Play Console:
  - Data safety from `DATA-SAFETY.md`;
  - target audience 18+, not in the Families programme;
  - the account-deletion URL;
  - the privacy policy URL.
- [ ] Register the privacy contact mailbox and name who reads it. The 30-day answer period for
      rights requests (Art. 12(3)) starts when an email arrives, not when someone opens it.

## 5. Things that are right and worth keeping

A lawyer's review lists what to protect as well as what to fix. These are strengths, and a change
that undoes one should be treated as a legal regression:
- **Private events never leave the device.**
- **Receipt OCR is on-device.** No photo or text goes to a model.
- **The private journal has no sync path.**
- **Pushes carry a type, never content.** A lock screen shows no message text written by the other
  parent.
- **The database is encrypted at rest on the device**, with a Keystore-bound key. Backup is
  disabled.
- **Grants are always time-limited.** Professionals need both parents' consent and are refused
  the chat.
- **Deletion is real**: server-side teardown, tombstones swept at 90 days, local wipe.
- **The verification page is privacy-preserving.** It hashes in the browser, answers only with
  the receipt, and never names a parent.
- **The export states on its face** that it records what the parents wrote, not what happened.

## 6. Keeping it true

Every one of these documents was written from the code. **A change to what the app collects,
where it sends it, or how long it keeps it changes these documents in the same pull request**:
- the privacy policy's tables;
- `RECORDS-OF-PROCESSING.md`;
- `DATA-SAFETY.md`;
- the DPIA's risk register, if the risk moves.

`CLAUDE.md` ("Legal and GDPR") says the same, so the next contributor sees it.
