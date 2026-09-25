# CoPlanly — Privacy Policy

> **DRAFT, not yet published.** Reviewed against the GDPR, the Czech implementing act and the
> code in `LEGAL-REVIEW-2026-09.md` (September 2026), which also lists what blocks publication.
> It still needs the company's Czech counsel to sign it off — this app processes **a child's
> health data** (GDPR Art. 9) — and every `{{PLACEHOLDER}}` filled in.
>
> Written from the actual data model — the Firestore collections, the Storage buckets and the
> third parties the code really talks to — rather than from a template. If the code changes,
> this changes with it. See `docs/legal/DATA-SAFETY.md` for the same facts in the shape the
> Play Console asks for.
>
> ### Owner must fill
>
> Everything else in this document was filled from the code (September 2026). These are the
> facts only the owner can supply; each is a `{{PLACEHOLDER}}` in the text below.
>
> | Placeholder | What goes in |
> | --- | --- |
> | `{{DATE}}` (twice) | The publication date, and the date the policy takes effect |
> | `{{LEGAL_ENTITY_NAME}}`, `{{REGISTERED_ADDRESS}}`, `{{COMPANY_ID}}` | The controller: a person or a company, its address, and its registration number (IČO) |
> | `{{PRIVACY_CONTACT_EMAIL}}` | An address somebody actually reads — it is the only route for a person who has uninstalled the app |
> | `{{FIRESTORE_REGION}}` | The Firestore and Cloud Storage location of the production project, from the Firebase console (Firestore → Settings). It must be an EU location — see `LEGAL-REVIEW-2026-09.md` L-3. The functions' region is already stated (`europe-west3`) |
> | `{{WEB_DELETION_URL}}` | Where `web/delete-account/` is hosted |
>
> The controller is a Czech *s.r.o.* (owner decision, September 2026). No DPO is appointed at
> launch; the reasoning is `LEGAL-REVIEW-2026-09.md` L-14. Every section below was reviewed there.
>
> Delete this whole box before publishing; `tools/wrap-legal-page.js` keeps a draft banner on
> the page until the last placeholder is gone.

**Last updated:** {{DATE}}
**Effective:** {{DATE}}

## Who we are

CoPlanly ("the app") is operated by {{LEGAL_ENTITY_NAME}}, {{REGISTERED_ADDRESS}}
({{COMPANY_ID}}). We are the **data controller** for the personal data described here.

Contact for any privacy question, including the rights listed below: {{PRIVACY_CONTACT_EMAIL}}.

We have not appointed a Data Protection Officer; the privacy contact above is read by a named
person at the company, who handles every request.

## What CoPlanly is for

CoPlanly is a shared calendar for parents raising a child in two homes. Two parents each hold
an account, link them to one another, and share a custody schedule, events, expenses, records
about their child, and a private message thread.

That shape has a consequence worth stating plainly, because it is unusual: **most of what you
enter is deliberately visible to your co-parent.** Sharing is the product, not a side effect.
Where something is *not* shared, we say so below.

## What we collect, and why

### Your account

- Email address and display name; a profile photo if you sign in with Google.
- Optionally, your date of birth and phone number.
- An authentication identifier from Firebase Authentication.
- Which parent slot you occupy in your family, and who your linked co-parent is.
- Your country and, where it matters for holidays, your region.
- If you agreed to it, when you gave consent to entering health details about your child, and
  to which version of the wording (see below).

We do **not** ask for or keep any health information about you, the parent. (Earlier test
versions of the app had an optional medical section on a parent's own profile; it has been
removed and what was entered in it deleted.)

**Why:** to give you an account, to show your co-parent who they are linked with, and to
decide what each of you may read. **Legal basis:** performance of our contract with you
(Art. 6(1)(b)). The consent record is kept to show that you gave consent (Art. 7(1)).

### What you enter about your family

- **Calendar events** — titles, times, locations, notes, event types, and optional photos.
  Every saved version of an event you share is kept, with who saved it and when, so the two of
  you can export the history of your calendar. Neither parent can edit or delete a saved
  version; the versions you saved are removed when you delete your account. Private events
  have no saved versions.
- **The custody schedule** — the pattern you agree and any one-off day swaps.
- **Expenses and budgets** — amounts, currencies, categories, and optional receipt photos.
- **Records about your child** — name, date of birth, school and activity details, emergency
  contacts, and a medical profile: allergies, medications, conditions, blood group,
  vaccinations, doctors' notes and photographs you attach to them.
- **Records about a pet**, in the same shape.
- **Messages** between you and your co-parent, and the **photos and PDF files** either of you
  sends in them. A file sent in a message stays in the thread like the message itself: neither of
  you can edit or delete it afterwards.
- **Family documents** — files you add to the family's document store (a court order, a school
  letter, a scan of an identity document), with the name and category you give them. **A
  document is always shared with your co-parent**: there are no private documents. Only the
  parent who added a document can rename or delete it; a deleted document disappears from both
  of your lists at once, and its file is removed from our servers 90 days later.

For every shared file we also keep its size, its type and a **SHA-256 fingerprint** of its
contents, so that the app can check a downloaded file is the one that was shared, and so that an
export can list the files a message carried by name and fingerprint (the files themselves are
never put into an export). Files are stored in Cloud Storage under your family, and only the two
parents of that family can download them; we never create a public link to one.

**Photographs attached to a record** — an event's photo, a receipt, and the photographs on a
child's medical notes or a pet's record — are stored the same way: in Cloud Storage under your
family, with their size, type and SHA-256 fingerprint, downloadable by the two parents of that
family only, and never through a public link. **A guest, a calendar friend or a professional
never sees them**, even where they can read the record itself. A photograph you add before you
have linked a co-parent is stored in a folder only you can read; when you link, our server moves
it into your family's folder so your co-parent can see it too.

**Why:** these are the contents of the service. **Legal basis:** performance of our contract
with you (Art. 6(1)(b)).

**Your child's health details** are health data (Art. 9 GDPR). We process them only with your
**explicit consent** (Art. 9(2)(a)), which you give as your child's parent. The first time you
open a child's medical section, the app tells you what it is, who will see it (your co-parent,
and any guest you let see that child's record) and that it is optional, and asks you to agree. We
record when you agreed and to which wording; if the wording changes, we ask again. The app works
fully without these details.

You can **withdraw your consent** at any time in Settings. Withdrawing deletes the medical details
of the children's records you created. Details your co-parent entered stay: they rest on your
co-parent's own consent, and they can withdraw it themselves. Withdrawing does not affect what was
lawful before (Art. 7(3)).

**People named in records.** When you record your child's doctor, school, a grandparent or an
emergency contact, you give us information about someone who does not use CoPlanly. We use it
only to show it to you and your co-parent. We do not contact these people, and we rely on this
policy being public to inform them (Art. 14(5)(b)). Enter only what the two of you need.

### Exports you make

You can export the record of what you and your co-parent wrote and recorded as a PDF or CSV
file. **The file is made on your phone and is not sent to us.** When your phone can reach our
server at that moment, we keep a **receipt** for it: a record ID printed on the file, a SHA-256
fingerprint of the file (a one-way value from which the file cannot be reconstructed), its size
and format, the period it covers, when it was registered, your account, and the family it was
made for. Anyone who holds the file — or only its record ID — can ask our verification page
whether it was registered and when. The answer gives the time, the period, the format and the
size, and says the file was made by "one of the family's parents"; it never names you or
identifies your account. The verification page fingerprints the file in the checker's own
browser and sends us only the fingerprint.

**Why:** so that a lawyer, a mediator or a court can check that an export you handed over has not
been altered. **Legal basis:** performance of our contract with you (Art. 6(1)(b)) — verification
is part of the export you asked for. After your account is deleted, the receipt is kept without
your account or family in it, on the basis of our and the other parent's legitimate interest in
an export remaining verifiable (Art. 6(1)(f)); see "How long we keep it".

### Data about a child

A child does not hold an account and never signs in. What is recorded about them is entered by
a parent, and only their parents — and anyone a parent explicitly grants access to — can read
it. CoPlanly is for adults: accounts are for people aged 18 or over, and the sign-in screen says
so. We rely on the parents' authority to act for their child, not on the child's own consent.

**The child's own rights.** The information is about the child, so the rights below are the
child's. While the child is a minor, their parents exercise them. A young person who wants to see
or have deleted what was recorded about them — or an adult who was that child — can write to
{{PRIVACY_CONTACT_EMAIL}}; we will verify who they are and answer within one month.

### People you invite

- **Your co-parent**: nothing, until they accept. An invitation is a code you pass on
  yourself; it carries your name and email address so that the person redeeming it can see who
  invited them, and we never ask for theirs.
- **A guest** (for example a grandparent) whom you grant time-limited access to one child's
  record — without its photographs.
- **A calendar friend** whom you grant time-limited read access to the family calendar.
- **A professional** — a mediator, lawyer, guardian ad litem or therapist — whom **both** parents
  let read the family calendar, custody schedule and parenting plan, read-only, for at most 180
  days. They never see your messages, expenses or records about your child. Access starts only
  once each parent has consented in the app, and either parent can end it alone at any time. The
  professional sees the two parents' names; you see theirs and, if they signed in with Google,
  their profile picture. What a professional reads becomes part of their own work, under their
  own professional duties (for a lawyer, confidentiality): for that they are a controller in
  their own right, not our processor.

Every such grant carries an expiry, is visible to both parents, and can be revoked at any time.

### Calendar links you create

You can create a **read-only calendar link** (Settings → Sync) so that a calendar app — for
example Apple Calendar on an iPhone — can show your family's custody days and shared events.
Anyone who has the link can read what it shows, so share it only with the person it is for. It
never includes private events, deleted events, chat, expenses or children's records. We store a
one-way fingerprint (a SHA-256 hash) of the link, never the link itself, together with the family
it belongs to, who created it and when a calendar last fetched it. You can revoke a link at any
time; a link no calendar has fetched for **90 days** is deleted automatically, and every link into
a family ends when the co-parents unlink or either account is deleted.

### Technical data

- **A push notification token**, so we can notify you about changes your co-parent makes.
- **Crash reports** (Firebase Crashlytics) and **usage analytics** (Firebase Analytics) in
  release builds. These carry no message content, no event titles and no records about your
  child. They record which screens are opened and which actions succeed or fail, with your
  device model and Android version, under a **random identifier of your installation** — not your
  name, email or account. That makes them pseudonymous, not anonymous, which is why we ask.
  Both are **off until you agree** on the screen shown before sign-in, and you can change your
  answer at any time in Settings → App. **Legal basis:** your consent (Art. 6(1)(a)). Analytics
  data is kept for 2 months and crash reports for 90 days.
- **The verification page's rate limit.** When somebody checks an export on the verification
  page, our server uses their IP address to limit how many checks one connection can make. The
  address is held in the server's memory for at most ten minutes and is never written to our
  database or logs.

### Google Calendar, if you connect it

If you connect a Google account, we request access to your calendars so events can be
imported and exported. The resulting access and refresh tokens are **stored encrypted on your
device only**. To obtain and renew them, the one-time authorisation code and, on each renewal,
the refresh token pass through our server (a Google Cloud Function), which forwards them to
Google and does not keep them. The server keeps only a one-way fingerprint (a SHA-256 hash) of
your refresh token, linked to your account, so that a token stolen from somebody else cannot be
renewed through our service. Disconnecting in Settings deletes the tokens from your device.

### Your child's school system (Bakaláři), if you connect it

You can connect your child's **Bakaláři** account (Settings → Sync) so that the school's events
and the times your child is at school appear in the family calendar. It works like this:

- **You sign in on your phone, and your phone talks to the school's Bakaláři server directly.**
  Your Bakaláři username and password never reach us. The password is used once, to sign in, and
  is **not stored anywhere** — not on our servers and not on your phone.
- What your phone keeps, **encrypted and on the device only**, is the sign-in token Bakaláři
  issues, the school's address, your username, and your child's name and class as Bakaláři shows
  them, so that it can refresh the calendar once a day. When the token expires you are asked for
  the password again. Signing out, disconnecting the school or deleting your account removes all
  of it from the phone.
- From the school's server the app reads **only** your child's timetable for the next four weeks
  and the school's events. It keeps, for each school day, the time the first lesson starts and the
  last one ends, days without lessons, and the events for your child's class or for the whole
  school. It does **not** read or keep grades, homework, absences, messages or individual lessons.
- What it imports becomes **ordinary events in the family you chose**, about the child you chose
  — visible to your co-parent like any event you enter yourself, and kept and deleted like them.
- To help you find your school, the app asks Bakaláři's public school list
  (`sluzby.bakalari.cz`) for the schools in the town you type. That request carries only the
  town's name.

**Why:** so both parents see the school's calendar without retyping it. **Legal basis:**
performance of our contract with you (Art. 6(1)(b)) — you asked for the import; the school's own
processing of your child's data in Bakaláři is the school's, under its own privacy notice.

## What happens on your device and goes nowhere

- **Receipt scanning.** When you photograph a receipt, the text is recognised **entirely on
  your device**. The photograph and the recognised text are not sent to any text-recognition
  or AI service.
- **Voice typing in chat.** If you use the microphone in the chat composer, your phone's own
  speech recognition turns what you say into text **on the device**. The app offers the
  microphone only on phones that can do this on the device, and never uses an online speech
  service instead. The audio is not recorded, stored or sent to us or anyone else; only the text,
  once you choose to send it, becomes an ordinary chat message. The app asks for microphone
  access the first time you tap the microphone, and you can withdraw it in your phone's settings.
- **Private events.** An event you mark private never leaves your device. It is not uploaded,
  not synced, and not visible to your co-parent.

## Who else sees your data

We do not sell personal data, and we do not use it for advertising.

| Recipient | What they process | Why |
| --- | --- | --- |
| **Your co-parent** | Everything you share — which is most of it | The purpose of the service |
| **Guests and calendar friends you invite** | Only the record or calendar you granted, until the grant expires — never its photographs | Because you granted it |
| **Whoever holds a calendar link you created** | Custody days and shared events, read-only, until you revoke the link | Because you created and shared it |
| **Anyone holding an export you made, or its record ID** | Whether it was registered, when, the period, the format and the size — never who made it | So the export can be verified |
| **A professional both parents admit** | The calendar, custody schedule and parenting plan of that one family, read-only, until the grant expires | Because both of you consented |
| **Your co-parent, after you delete your account** | The message thread, read-only, for 30 days | So they keep their own correspondence (see "Deleting your account") |
| Google (Firebase), **our processor** | Account data, all synced content, files, push tokens, crash and usage data | Our hosting, database, file storage and messaging provider, acting only on our instructions |
| Google (Calendar API) | Only your calendar, only if you connect it | The integration you enabled — for your Google Calendar, Google is responsible under its own privacy policy |

**Where your data is processed.** Our database and file storage are located in
{{FIRESTORE_REGION}}. Our server functions — which link co-parents, send notifications, renew
Google Calendar access, register exports and delete accounts — run in Google's `europe-west3`
region in Frankfurt, Germany.

Some Google services we use run on Google's global infrastructure and may process data outside
the European Economic Area: delivering push notifications (Firebase Cloud Messaging), signing in
(Firebase Authentication), and — only if you agreed — usage statistics and crash reports. Google
LLC is certified under the **EU–US Data Privacy Framework**, for which the European Commission has
adopted an adequacy decision (Art. 45); Google's data processing terms also incorporate the
Commission's **Standard Contractual Clauses** (Art. 46(2)(c)). Google acts as our processor under
the Google Cloud Data Processing Addendum.

A professional both of you admit may work outside the European Economic Area; what they read is
sent to them because you both asked for it (Art. 49(1)(b)).

We disclose data to authorities only where the law requires it.

## How long we keep it

We keep what you enter for as long as your account exists, and delete it as described under
"Deleting your account" when you close it. Every period below is enforced by a job that runs
daily on our servers, not by hand.

| What | How long |
| --- | --- |
| Your account and everything you entered | Until you delete it, or delete the record |
| A single event, expense, child record, pet or family document you delete | Marked deleted at once, so your co-parent's phone learns of it; removed for good **90 days** later, with its file or photographs |
| A photograph you remove or replace | Deleted from our servers when you save the change |
| A guest's, calendar friend's or professional's access | Until the date set when it was granted, or until either parent ends it; professionals at most **180 days** |
| An invitation nobody accepted | Deleted **30 days** after it expired (**90 days** after it was made, if it had no expiry) |
| A read-only calendar link | Until revoked; deleted after **90 days** without use |
| Notifications waiting to be delivered | **30 days** |
| The message thread, after your co-parent deletes their account | **30 days**, so you can export it — see below |
| An export's receipt | **10 years** from registration; a record ID reserved for an export that was never registered, **7 days** |
| Usage statistics / crash reports (only if you agreed) | **2 months** / **90 days** |
| Server logs (which account called which function, and errors — never names, messages or files) | **30 days** |
| Emails you send us about your privacy rights | **3 years** after we close the request |

An export's receipt outlives your account on purpose: a file you or your co-parent already handed
to a lawyer or a court can still be checked. What stays is only the file's fingerprint, its period,
format, size and registration time — nothing that identifies you. Ten years is the longest time
within which a claim arising from the family's affairs can generally still be brought in Czech law
(§ 629(2) of the Civil Code).

## Deleting your account

**Settings → Account → Delete account.** This is irreversible and, once confirmed, it:

- deletes your profile, your events and the saved revisions of events you edited, your expenses
  and budgets, the records you entered about your child and pet, your custody schedule and agreed
  expense split, your parenting plan and your invitations;
- deletes the photographs attached to those records — event photos, receipts, and medical and
  pet photographs — any photograph you added before you linked a co-parent, and the family
  documents you added. A photograph you added to a record your co-parent created stays with that
  record;
- **keeps the message thread with your co-parent for 30 days, then deletes it** — messages and
  files, whichever of you sent them. See "The message thread" below;
- removes you from the audience of anything your co-parent created, and ends any guest,
  calendar-friend or professional access you granted or held;
- deletes every read-only calendar link into your families, whichever of you created it;
- unlinks the two of you, so their access ends immediately;
- deletes the fingerprint of your Google Calendar authorisation, if you connected one, and
  any notifications still queued for you;
- removes your account and your family from the receipts of exports you or your co-parent made.
  The fingerprint, period, format, size and registration time stay, so that an export already
  handed to a lawyer or a court can still be verified — but nothing left in a receipt identifies
  you. Record IDs reserved for exports that were never registered are deleted;
- deletes your authentication account;
- wipes the local copy on the device you did it from.

It happens at once, apart from the message thread. We do not keep a copy of a deleted account to
restore later.

**The message thread.** Your messages are also your co-parent's correspondence: they read them,
answered them, and may need them — for example in proceedings about your child. Deleting them the
moment you leave would take that record from them without warning. So when you delete your
account, your co-parent is notified, can read the thread (but nobody can write to it) and export
it for **30 days**, and then it is deleted on our servers, with every file sent in it. We keep it
for those 30 days on the basis of your co-parent's legitimate interest in their own correspondence
(Art. 6(1)(f), and Art. 17(3)(e) where a legal claim is in view). If your co-parent deletes their
own account in the meantime, the thread is deleted at once. You can object to this under
"Your rights"; we will weigh your reasons against your co-parent's.

Two consequences, stated plainly because they surprise people. First: **records your co-parent
entered remain in their account, and records you entered disappear from it** — including from
the calendar you shared. Second: **we can only delete what is on our servers and on the phone
you delete from.** A copy your co-parent's phone had already downloaded stays on that phone
until they delete it or uninstall the app, and the same is true of any other phone you were
signed in on.

If you no longer have the app installed, write to {{PRIVACY_CONTACT_EMAIL}} from the address
your account uses and we will delete the account for you within 30 days. The same steps are on
our account-deletion page: {{WEB_DELETION_URL}}.

## Your rights

Under the GDPR you have the right to:

- **access** your data and get a copy (Art. 15);
- have it **corrected** (Art. 16);
- have it **deleted** (Art. 17);
- **restrict** its processing (Art. 18);
- **receive it in a portable form** (Art. 20) — the export in Settings → Family gives you your
  family's record as CSV or PDF;
- **object** to processing we base on our or someone else's legitimate interests (Art. 21) — the
  90-day deletion markers, the message thread kept for your co-parent, and export receipts. We
  then stop unless our reasons override yours or the data is needed for a legal claim;
- **withdraw a consent** at any time — to health details about your child, or to statistics and
  crash reports — in Settings, without affecting what was done before (Art. 7(3)).

Most of these you can exercise directly in the app — everything you entered is visible and
editable, and deletion is one screen away. For anything else, write to
{{PRIVACY_CONTACT_EMAIL}}. We answer within one month (Art. 12(3)); we may ask you to confirm the
request from your account's email address, so that nobody else can ask for your data.

Two limits come from the nature of the app and are not a refusal of your rights. **Messages cannot
be edited or deleted** one by one, by either of you: they are a record of what was written, and
correcting a record of what someone said would make it untrue. And **records your co-parent
entered are theirs**: you can ask us about them, but we will weigh their rights too before
deleting something they wrote.

We make **no decisions about you by automated means** that have legal or similarly significant
effects (Art. 22). The app computes things — whose day it is, who owes whom — from what the two of
you entered, and shows its working; it does not decide anything.

You may also complain to a supervisory authority. In the Czech Republic that is the Office for
Personal Data Protection (Úřad pro ochranu osobních údajů), Pplk. Sochora 27, 170 00 Praha 7,
[uoou.gov.cz](https://uoou.gov.cz).

## Security

Data in transit is encrypted. Our servers are in the European Union. Access to your family's data
is enforced server-side, so another account cannot read it by asking, and our rules are tested
automatically every time they change. The app's own database on your device is encrypted too, with a
key held in the Android Keystore that cannot be copied off the device — so the calendar, messages,
expenses and any medical details you enter are not readable by someone holding the phone. Two
things on the device are protected by Android's own storage encryption rather than by that key:
the offline cache the Firebase library keeps of recently read records, and the cache of images
(photos, receipts) the app has displayed.
Authentication tokens are stored in encrypted storage backed by the same Keystore, and if that
storage cannot be opened, they are held in memory only rather than written unprotected. Device
backup and device-to-device transfer of the app's data are switched off.

No system is perfect. If we discover a breach affecting your rights, we will notify the
supervisory authority within 72 hours and tell you without undue delay where it puts you or your
child at high risk (Art. 33–34).

## Changes

If we change this policy in a way that affects you, we will tell you in the app before the
change takes effect. If a change needs your consent — for example a new use of your child's health
details — we will ask for it, not assume it.
