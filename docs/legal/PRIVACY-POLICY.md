# CoPlanly — Privacy Policy

> **DRAFT. Not yet reviewed by a lawyer, and not yet published.**
>
> Every `{{PLACEHOLDER}}` must be filled in. More importantly: this app processes **a child's
> health data**, which is special-category data under GDPR Art. 9, and no template survives
> that unread. Have a lawyer review it before it goes anywhere near a store listing.
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
> | `{{DPO_PARAGRAPH_IF_APPOINTED}}` | Whether a Data Protection Officer is appointed. Large-scale processing of health data can make one mandatory (Art. 37(1)(c)); this is the lawyer's question. Delete the line if none is |
> | `{{FIRESTORE_REGION}}` | The Firestore and Cloud Storage location of the production project, from the Firebase console (Firestore → Settings). The functions' region is already stated: they run in `us-central1`, because `functions/index.js` names no other |
> | `{{WEB_DELETION_URL}}` | Where `web/delete-account/` is hosted |
>
> Delete this whole box before publishing; `tools/wrap-legal-page.js` keeps a draft banner on
> the page until the last placeholder is gone.

**Last updated:** {{DATE}}
**Effective:** {{DATE}}

## Who we are

CoPlanly ("the app") is operated by {{LEGAL_ENTITY_NAME}}, {{REGISTERED_ADDRESS}}
({{COMPANY_ID}}). We are the **data controller** for the personal data described here.

Contact for any privacy question, including the rights listed below: {{PRIVACY_CONTACT_EMAIL}}.

{{DPO_PARAGRAPH_IF_APPOINTED}}

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
- An authentication identifier from Firebase Authentication.
- Which parent slot you occupy in your family, and who your linked co-parent is.

**Why:** to give you an account, to show your co-parent who they are linked with, and to
decide what each of you may read. **Legal basis:** performance of our contract with you
(Art. 6(1)(b)).

### What you enter about your family

- **Calendar events** — titles, times, locations, notes, event types, and optional photos.
- **The custody schedule** — the pattern you agree and any one-off day swaps.
- **Expenses and budgets** — amounts, currencies, categories, and optional receipt photos.
- **Records about your child** — name, date of birth, school and activity details, emergency
  contacts, and a medical profile: allergies, medications, conditions, blood group,
  vaccinations, doctors' notes and photographs you attach to them.
- **Records about a pet**, in the same shape.
- **Messages** between you and your co-parent.

**Why:** these are the contents of the service. **Legal basis:** performance of our contract
with you (Art. 6(1)(b)). For the medical profile, which is health data under Art. 9, we rely
on your **explicit consent** (Art. 9(2)(a)): the medical fields are optional, you choose
whether to fill them, and you can delete them at any time. The app works without them.

### Data about a child

A child does not hold an account and never signs in. What is recorded about them is entered by
a parent, and only their parents — and anyone a parent explicitly grants access to — can read
it. In the Czech Republic the digital-consent age is 15 (§ 7 of zákon č. 110/2019 Sb.); we do
not offer accounts to anyone under 18, and we rely on the parent's own authority over their
child's records rather than on the child's consent.

### People you invite

- **Your co-parent**: nothing, until they accept. An invitation is a code you pass on
  yourself; it carries your name and email address so that the person redeeming it can see who
  invited them, and we never ask for theirs.
- **A guest** (for example a grandparent) whom you grant time-limited access to one child's
  record.
- **A calendar friend** whom you grant time-limited read access to the family calendar.
- **A professional** — a mediator, lawyer, guardian ad litem or therapist — whom **both** parents
  let read the family calendar, custody schedule and parenting plan, read-only, for at most 180
  days. They never see your messages, expenses or records about your child. Access starts only
  once each parent has consented in the app, and either parent can end it alone at any time. The
  professional sees the two parents' names; you see theirs and, if they signed in with Google,
  their profile picture.

Every such grant carries an expiry, is visible to both parents, and can be revoked at any time.

### Technical data

- **A push notification token**, so we can notify you about changes your co-parent makes.
- **Crash reports** (Firebase Crashlytics) and **usage analytics** (Firebase Analytics) in
  release builds. These carry no message content, no event titles and no records about your
  child. They record which screens are opened and which actions succeed or fail.
  Both are **off until you agree** on the screen shown before sign-in, and you can change your
  answer at any time in Settings → App.

### Google Calendar, if you connect it

If you connect a Google account, we request access to your calendars so events can be
imported and exported. The resulting access and refresh tokens are **stored encrypted on your
device only**. To obtain and renew them, the one-time authorisation code and, on each renewal,
the refresh token pass through our server (a Google Cloud Function), which forwards them to
Google and does not keep them. The server keeps only a one-way fingerprint (a SHA-256 hash) of
your refresh token, linked to your account, so that a token stolen from somebody else cannot be
renewed through our service. Disconnecting in Settings deletes the tokens from your device.

## What happens on your device and goes nowhere

- **Receipt scanning.** When you photograph a receipt, the text is recognised **entirely on
  your device**. The photograph and the recognised text are not sent to any text-recognition
  or AI service.
- **Private events.** An event you mark private never leaves your device. It is not uploaded,
  not synced, and not visible to your co-parent.

## Who else sees your data

We do not sell personal data, and we do not use it for advertising.

| Recipient | What they process | Why |
| --- | --- | --- |
| **Your co-parent** | Everything you share — which is most of it | The purpose of the service |
| **Guests and calendar friends you invite** | Only the record or calendar you granted, until the grant expires | Because you granted it |
| **A professional both parents admit** | The calendar, custody schedule and parenting plan of that one family, read-only, until the grant expires | Because both of you consented |
| Google (Firebase) | Account data, all synced content, files, push tokens, crash and usage data | Our hosting, database, file storage and messaging provider |
| Google (Calendar API) | Only your calendar, only if you connect it | The integration you enabled |

Google processes data both inside and outside the EU. Transfers outside the EEA rely on the
European Commission's Standard Contractual Clauses. Our database and file storage are located
in {{FIRESTORE_REGION}}. Our server functions — which link co-parents, send notifications,
renew Google Calendar access and delete accounts — run in Google's `us-central1` region in the
United States, so the data each of them handles is processed there.

We disclose data to authorities only where the law requires it.

## How long we keep it

We keep what you enter for as long as your account exists. When you delete your account
(below), it is removed as described there.

When you delete a single event, expense, child record or pet, the record is marked as deleted
rather than removed at once, so that your co-parent's phone learns of the deletion the next time
it syncs. A daily job removes it for good **90 days** after you deleted it.

A guest's access to a child's record ends on the date set when it was granted, and a daily job
removes lapsed grants. A calendar friend's and a professional's access also end on their expiry
date, and a daily job removes the lapsed grant. A professional's access also ends when the two
parents unlink. Queued
notifications are deleted after **30 days**.

## Deleting your account

**Settings → Account → Delete account.** This is irreversible and, once confirmed, it:

- deletes your profile, your events, your expenses and budgets, the records you entered about
  your child and pet, your custody schedule and agreed expense split, your parenting plan,
  your invitations, and the whole message thread with your co-parent;
- deletes the photographs attached to those records — event photos, receipts, and medical and
  pet photographs;
- removes you from the audience of anything your co-parent created, and ends any guest,
  calendar-friend or professional access you granted or held;
- unlinks the two of you, so their access ends immediately;
- deletes the fingerprint of your Google Calendar authorisation, if you connected one, and
  any notifications still queued for you;
- deletes your authentication account;
- wipes the local copy on the device you did it from.

It happens at once. We do not keep a copy of a deleted account to restore later.

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

Under the GDPR you may: access your data; correct it; delete it; restrict or object to its
processing; receive it in a portable form; and withdraw a consent you have given, without
affecting what was done before you withdrew it.

Most of these you can exercise directly in the app — everything you entered is visible and
editable, and deletion is one screen away. For anything else, write to
{{PRIVACY_CONTACT_EMAIL}}; we answer within one month.

You may also complain to a supervisory authority. In the Czech Republic that is the Office for
Personal Data Protection (Úřad pro ochranu osobních údajů), Pplk. Sochora 27, 170 00 Praha 7,
[uoou.gov.cz](https://uoou.gov.cz).

## Security

Data in transit is encrypted. Access to your family's data is enforced server-side, so another
account cannot read it by asking. The app's own database on your device is encrypted too, with a
key held in the Android Keystore that cannot be copied off the device — so the calendar, messages,
expenses and any medical details you enter are not readable by someone holding the phone. Two
things on the device are protected by Android's own storage encryption rather than by that key:
the offline cache the Firebase library keeps of recently read records, and the cache of images
(photos, receipts) the app has displayed.
Authentication tokens are stored in encrypted storage backed by the same Keystore, and if that
storage cannot be opened, they are held in memory only rather than written unprotected. Device
backup and device-to-device transfer of the app's data are switched off.

No system is perfect. If we discover a breach affecting your rights, we will notify the
supervisory authority within 72 hours and tell you where the law requires it.

## Changes

If we change this policy in a way that affects you, we will tell you in the app before the
change takes effect.
