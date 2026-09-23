# CoPlanly vs AppClose — competitive comparison (September 2026)

Date: 2026-09-23. Scope: an objective feature comparison with **AppClose**, the highest-rated
co-parenting app on Google Play, with brief context on OurFamilyWizard and TalkingParents.
Market facts in general live in `docs/AUDIT-2026-08.md` §10 (verified in §10.10); this document
is the feature-by-feature view.

**How the sources were reached.** This environment's network policy blocked every primary page
(appclose.com, both app stores, press-release sites). Every AppClose fact is therefore what a
search index shows a page saying, not the page opened directly.
- **Index**: the vendor's own page or store listing appeared in search results saying the fact.
- **Secondary**: only a third-party page says it.

Confidence:
- **H**: an index hit on a vendor page or store listing.
- **M**: a vendor press release, or a secondary source agreeing with an index hit.
- **L**: a secondary source only, or sources that conflict.

Confirm store figures on a phone before quoting them publicly.

**CoPlanly is not published.** It has no Play listing, no price and no users, so every "CoPlanly
has" below means "in the code", not "proven with users".

---

## 1. AppClose — facts

| # | Fact | Source | Conf. |
|---|---|---|---|
| 1 | Android, iOS and web (web since May 2025); AppClose Pro, a web app for professionals | prnewswire.com release of 29 May 2025; appclose.com/pro (index) | H |
| 2 | Google Play 4.6★ from about 25,500 reviews. Downloads "3.1M+" per the index, ">1M on Play" per a Nov 2025 release | Play listing `com.appclose.androidapp` (index); finance.yahoo.com syndication | H rating / M downloads |
| 3 | App Store about 4.7★ from 22.8K–33K ratings (dates differ); vendor claims "58,000+ five-star reviews" | splitmetrics.com, appbrain.com (secondary); appclose.com (index) | M |
| 4 | Recent App Store reviews are 81.5% 1–2★. Top themes: removed free tier (26%), support (25%), bugs (17%), cost (11%) | parentingpath.net (secondary; it sells a competing app) | L–M |
| 5 | "2.4M+ parents since 2016", "court-ordered in every US county", plus CA/UK/IE/AU/NZ | appclose.com, Play listing (index; marketing claim) | H as a claim |
| 6 | English; Spanish since 29 May 2026. **No evidence of Czech or German** | prnewswire.com release of 29 May 2026 (index) | M |
| 7 | Free plan ended 1 Jan 2026. One plan: **$7.99/parent/month on the web, $8.99 in the app**, 60-day trial. Prepaid (web only): $83.88 for one year, $143.76 for two | support.appclose.com article 45803225192091 (index); parentingpath (secondary) | H for $8.99 and the trial, M for the rest |
| 8 | **Each parent pays separately.** One parent can buy the other's subscription on the web. An unpaid account goes **read-only**, and the co-parent is told that parent is not receiving messages | support.appclose.com 45803225192091 (index) | H |
| 9 | Fee waiver for hardship or domestic-violence survivors, one year, renewable. 25,600+ granted since 1 Jan 2026 | support FAQ (index) | H / M count |
| 10 | Schedule templates 2-2-3, 2-2-5-5, 3-4-4-4 plus custom. **Summer and holiday layers override the regular schedule** (holiday > summer > regular) | support.appclose.com 27282580816027 (index) | H |
| 11 | "Swap Days" requests, on a Parenting Calendar kept separate from an "Events & Activities" calendar | support.appclose.com 27579820894491, 32779991788955 (index) | H |
| 12 | Hourly or partial-day custody | not found | — |
| 13 | Events can be shared with a co-parent who is not connected, or with a third party | support.appclose.com 32779991788955 (index) | H |
| 14 | Syncs with the phone's calendar; no native Google Calendar or ICS feed found | support.appclose.com 27272940713371 (index) | M |
| 15 | Pick-up and drop-off requests with a location and every response on record | support.appclose.com 23302455653915 (index) | H |
| 16 | Messages time-stamped, encrypted, unalterable. One-on-one and group chats, read time, search, export by date or keyword, documents | appclose.com/pro/features; App Store listing (index) | H |
| 17 | **Co-Parent Assist**: optional AI tone and clarity check before sending, shown only to the sender, never changes the message. A private model; no third-party AI; no training on messages | appclose.com/co-parent-assist; release of 29 May 2026 (index) | H |
| 18 | AI assistant for professionals in AppClose Pro (communication analysis, trial preparation) | release of 29 May 2025 (index) | M |
| 19 | Audio and video calls, permission-based per contact; no phone numbers or location shown; every call logged | support.appclose.com 4608885248027 (index) | H |
| 20 | Call recording and transcription, with both parties' consent | support.appclose.com 37255412808859 (index) | H |
| 21 | Expenses with categories and documents; reimbursement requests with a discussion thread | support.appclose.com 23354304989211, 360022976133 (index) | H |
| 22 | **ipayou** money transfers: a verified US identity is required (address, date of birth, last four of the Social Security number). Free for 6 months, then $2.50 per transfer | appclose.com/ipayou (index) | M |
| 23 | **Certified Electronic Business Records**: exports generated from the stored originals, with a record ID and a certification timestamp, unlimited; an affidavit is available on subpoena | release of 21 Nov 2025 (index); appclose.com/pro/records-and-subpoenas (index) | H |
| 24 | Export covers messages, calls and their recordings or transcripts, expenses, requests, check-ins, notes and session logs | appclose.com/pro/features (index) | H |
| 25 | "Co-Parent Hub": allergies, medications, school, emergency contacts; unlimited file storage | App Store listing; appclose.com (index) | M–H |
| 26 | Journal and notes: private, or shared with the co-parent or a professional | appclose.com/pro/features (index) | M |
| 27 | Pets: vet visits, expenses, check-ins | support.appclose.com 23254293335067 (index) | M |
| 28 | GPS-verified check-in (arrival and departure), coordinates not editable | support.appclose.com 30863888335387 (index) | H |
| 29 | "Circle": unlimited third parties (lawyers, mediators, guardians ad litem, grandparents…), permission-controlled | support.appclose.com 30317731665563 (index) | H |
| 30 | **AppClose Pro is free** for lawyers, guardians ad litem, mediators and courts | appclose.com/pro (index) | H |
| 31 | BrAC Verified: breathalyzer and remote alcohol monitoring | release of 29 May 2025 (index) | M |
| 32 | Encryption in transit and at rest. **Data is transferred to the US**; there is a GDPR section; no EU residency found | appclose.com/privacy.html (index) | H |
| 33 | Complaints: the paywall stalls court-ordered communication; weak support; unreliable notifications; bugs and crashes | ttsattorneys.com, avvo.com, review snippets (secondary) | M |

**Context — OurFamilyWizard.**
- Price: $110–299.88 per parent per year.
- Features: ToneMeter, unalterable messages, OFWpay, free professional access, a notarised court packet on Premium.
- Trustpilot rating: 2.5★.

**Context — TalkingParents.**
- Price: $7, $16 or $32 per parent per month.
- Free plan removed in March 2026 (one source says June).
- Recorded calls and "Unalterable Records" are Ultimate only.
- Google Play rating: 2.32★.

---

## 2. Side by side

| Area | AppClose | CoPlanly | Verdict |
|---|---|---|---|
| Platforms | Android, iOS, web, a Pro web app | Android only, unpublished | **AppClose, by far** |
| Ratings and scale | 4.6★ Play / ~4.7★ iOS, 2.4M+ parents claimed | none | AppClose |
| Languages | EN, ES; no CS/DE found | EN, CS, DE, RU, UK, checked for completeness in CI | **CoPlanly** for CZ/DE/EU |
| Price model | $7.99–8.99 per parent, no free tier, read-only if unpaid | undecided (MON-1); the recommendation is per family with the second parent free. No billing code | no verdict yet |
| Custody templates | 3 templates + custom; **seasonal override layers** | week-on/week-off, every other weekend, 2-2-3, 3-4-4-3, custom; **contact windows (partial days, MON-6b)**; no seasonal layer | even: each has what the other lacks |
| Schedule changes | swap requests, pick-up/drop-off requests | change requests with honest status, swaps and overrides, a custody proposal with accept/decline, pickup confirmation | even |
| Calendar breadth | parenting calendar and events calendar kept separate | month/week/day with drag to reschedule, recurrence, private events, filters by parent and by child/pet, holidays for 5 countries + German Länder, Czech school vacations | **CoPlanly** |
| External calendar | phone-calendar sync | Google Calendar API (create, update, delete) | even |
| Messaging | unalterable, read time, search, groups, documents | 1:1 only; unalterable by the rules (no test pins it); ticks; templates; local search of the thread (MON-15, since this comparison); images and PDFs in the thread (MON-23, since this comparison, live after the storage deploy); no groups | **AppClose** |
| Tone check / AI | Co-Parent Assist | none (MON-12, P3, only behind a proxy) | **AppClose** |
| Calls | audio and video, recording with consent | none | **AppClose** |
| Expenses | categories, documents, reimbursements | receipts with **on-device OCR**, budgets, per-child/pet tags, an **agreed split ratio frozen per expense**, balances per currency | **CoPlanly** on split logic |
| Payments | ipayou (US identity only) | none (MON-11) | AppClose, though ipayou doesn't reach the EU |
| Court export | **certified** records with a record ID and an affidavit; very broad coverage | MON-3 in PR #99: on-device PDF/CSV, full event versions, device and server clocks, labelled as a communication record, not certified | **AppClose**; in CZ/DE the gap is smaller (free evaluation of evidence) |
| Child info | Co-Parent Hub + unlimited storage | profiles with a medical section, several children, pets, contacts, medical photos (broken until the storage deploy); a family document vault (MON-23, since this comparison, live after the storage deploy) | about even; AppClose's storage is unlimited, ours caps a file at 20 MB |
| Journal | yes | no | **AppClose** |
| GPS check-in | yes | pickup confirmation only | **AppClose** |
| Third parties | unlimited Circle, free Pro for professionals | calendar friend (read-only, expiring, one family); guest access to one child record; multi-family | **AppClose** for professionals |
| Parenting plan | none found | the Czech Rodičovský plán (MON-5), agreement tied to the exact wording | **CoPlanly** in CZ |
| Privacy | encrypted; data transferred to the US | SQLCipher (never run on a device), private events never sync, on-device OCR, telemetry off until consent; Firestore region undecided | CoPlanly is designed better for the EU, but unproven |

---

## 3. What we lack, most important first (CZ/DE/EU)

1. **Availability.** No Play listing, **no iOS**, no web. A two-parent product with one iPhone
   parent does not work, and that outweighs any single feature. (REL-1…7; iOS is not planned at
   all — an owner decision.)
2. **A record a third party trusts.** MON-3 gives a printable PDF with both clocks, which
   reaches the realistic CZ/DE bar ("a printable record that can be notarised", AUDIT-2026-08
   §10.9). What is still missing:
   - a record ID and an integrity statement;
   - a test pinning message immutability;
   - coverage of calls, check-ins and a journal, which do not exist.
3. **A tone check before sending.** Every paid competitor has one. It must never block and never be
   stored, and it needs an EU AI Act review. (MON-12, behind SEC-1's proxy.) The model-free half —
   an undo window and a lexical hint — has since shipped as MON-19.
4. **Chat completeness:** search (local, shipped since as MON-15), attachments (shipped since as MON-23, images and PDFs), a journal and notes, and third-party threads.
5. **Seasonal schedule layers:** a summer or school-holiday schedule that overrides the base
   pattern. It matters in CZ/DE, where summer care is split differently from term time.
6. **Professional access** for mediators, who are the distribution channel (MON-9). AppClose
   and OFW give them free portals.
7. **A document vault** for court orders, school reports and passports — shipped since as MON-23.
8. GPS check-in (lower priority; sensitive under GDPR).
9. Calls with recording (low: consent-based recording is legally delicate in the EU and
   expensive to build).
10. In-app payments (low: ipayou is US-only, and Onward's closure shows payments do not carry a
   product).

## 4. Where we are stronger (backed by code or cited facts)

- **Languages:** CS, DE, RU and UK, with completeness and format arguments checked in CI. We found
  no evidence that AppClose ships any of them. Russian and Ukrainian also reach Ukrainian families
  in CZ/DE.
- **Local holidays:** tables for five countries plus the German Länder, pinned to a reference
  library by a test, and Czech school vacations.
- **Partial-day custody** (contact windows). AppClose shows nothing like it.
- **The Czech parenting plan** (§ 858 OZ, zákon 268/2025 Sb.): agreement is tied to the exact
  wording, so an edit makes it lapse.
- **Expense logic:**
  - the agreed split ratio is frozen per expense;
  - totals are honest per currency;
  - receipt OCR never leaves the device.
- **Several children, pets and families**, with grants that expire.
- **Privacy design for the EU:** SQLCipher, private events that never sync, telemetry off until
  consent, and EU hosting planned where AppClose moves data to the US. On paper — not yet proven
  on a device.
- **Price position (planned):** per family, second parent free. AppClose's top complaint is its
  removed free tier and per-parent billing, whose read-only mode stalls court-ordered
  communication. This is an opportunity, not a strength, until MON-1 and billing exist.

## 5. Not verified

- **AppClose's side:**
  - Store figures come from search snippets.
  - The full language list: a snippet says "8 languages", which conflicts with Spanish only
    arriving in May 2026.
  - Whether it is sold in the CZ/DE stores.
  - The $7.99 web price, the prepaid prices and the ipayou fee.
  - Whether it has partial-day custody or an ICS feed.
  - Whether it hosts data in the EU.
  - Its review percentages, which come from a competitor's sample of 130.
- **TalkingParents:** a source conflict over whether it has a "Sentiment Scanner".
- **CoPlanly's side:**
  - SQLCipher, multi-family and contact windows have never been seen on a device.
  - Pet and medical photos fail until `firebase deploy --only storage`.
  - Chat immutability rests on the rules alone.
