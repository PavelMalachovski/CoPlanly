# `web/` — the pages that have to exist outside the app

Two things Google Play requires of CoPlanly are URLs, not screens: a privacy policy and a route to
delete an account that works **without the app installed**. This directory holds both, plus the
terms of service: `delete-account/`, `privacy/` and `terms/`, one self-contained `index.html`
each. A fourth page, `verify/`, is not a Play requirement but belongs with them: it is where a
lawyer or a court checks an exported record (MON-16). A fifth, `pair/`, is where a co-parent's
invitation link lands, and `index.html` is the site's home page, which Google's verification of
the OAuth consent screen asks for. The legal texts are still drafts (REL-4) — every page carries a banner saying so until the
last `{{PLACEHOLDER}}` is gone — but the pages exist so that hosting them is one command once a
lawyer has been through the markdown.

## `index.html` — the home page

The page at `/`. Google's verification of the OAuth consent screen (`docs/AUDIT-2026-09-play-final.md`
A-7, E-2) asks for a public home page, on the same domain as the privacy policy, that says what
the app is, what it does, and what it does with Google user data. This one does, and links the
policy, the terms, the deletion page and the verification page beside it. It is also simply what
somebody finds who types the site's address. Self-contained like the others, and indexable,
unlike `pair/`.

- **English is written in the markup**, so the page reads without JavaScript and to a reviewer;
  the script reads it back as its English table before translating anything. Czech, German,
  Russian and Ukrainian are the `TEXT` table, following the browser, behind the same five buttons
  as `pair/`. The feature list is the Play listing's approved captions
  (`docs/play-listing/README.md`), under the same rule: only what the app does today.
- **It names the one permission the app asks of Google Calendar**, the one
  `GoogleCalendarAccess.SCOPE` requests, and carries Google's Limited Use disclosure verbatim in
  English, linked to the policy it names. `tools/check-invariants.js` (check 6) fails when the app's
  scope and the scope this page, the privacy policy or its hosted copy name part, so a wider scope
  cannot ship with texts that promise a narrower one.
- **The status line says the app is in testing.** Change it in all five languages when CoPlanly
  reaches Google Play — and link the listing then, not before: a Play link that 404s is design
  rule #8's affordance-promising-nothing.
- **Tested** by `web-tests/home-page.spec.js` in the `web` job: no request leaves the page, it is
  not kept out of search, the Calendar section and the disclosure, every link resolving to a page
  that exists, the same English with and without JavaScript, five complete languages, and 375 px
  with no sideways scroll.

### Before submitting the consent screen for verification

1. **Verify the host in Search Console** with an account that is an owner or editor of the Google
   Cloud project: a *URL-prefix* property for `https://coparently-a39c9.web.app/`, verified with
   the **HTML tag** method — add the `<meta name="google-site-verification" content="…">` line it
   gives to the `<head>` of this file and deploy hosting. The HTML-file method is the less sure
   one here: `cleanUrls` answers `/google….html` with a redirect.
2. **On the consent screen**, set the home page to `https://coparently-a39c9.web.app/`, the
   privacy policy to `…/privacy/` and the terms to `…/terms/`, and add `coparently-a39c9.web.app`
   to the authorised domains.
3. **The open question is the domain itself.** Google wants the home page "on a verified domain
   you own". Whether it accepts a `*.web.app` host for that is not settled — a developer reported
   verification blocked on "Homepage website is not registered" with one, and nothing confirms
   either answer ⚠. A domain of your own is the sure way: connect it to Firebase Hosting (the same
   `web/` directory is served unchanged — every link on the page is relative), verify it in Search
   Console as a *Domain* property through DNS, and put it on the consent screen and in the legal
   documents instead. That decision, and its cost, is the owner's.

## `privacy/index.html` and `terms/index.html`

**Generated — do not edit.** The source of truth is `docs/legal/PRIVACY-POLICY.md` and
`docs/legal/TERMS-OF-SERVICE.md`; `tools/wrap-legal-page.js` wraps the rendered markdown in the
same one-file style as the deletion page (no external CSS, fonts, scripts or images), and adds the
draft banner automatically when a placeholder is still in the body. Regenerate both in the same
commit as any edit to the markdown, from the repository root:

```bash
npx marked docs/legal/PRIVACY-POLICY.md   | node tools/wrap-legal-page.js privacy > web/privacy/index.html
npx marked docs/legal/TERMS-OF-SERVICE.md | node tools/wrap-legal-page.js terms   > web/terms/index.html
```

`marked` is fetched by `npx` on first use; the wrapper itself has no dependencies. The three
pages link to each other from their footers, by relative path, so they work under any host and
any base path.

## `delete-account/index.html`

A single self-contained file: no external CSS, fonts, scripts or images. That is deliberate — it
has to be hostable on anything, and a deletion page that fails because a CDN is down is worse than
not having one. The two languages are stacked rather than behind a toggle for the same reason:
nothing to fail, and a Play reviewer reading English finds it without interacting.

### Before hosting it

Fill the same placeholders the legal documents use, so one pass covers all three files:

| Placeholder | What goes in |
| --- | --- |
| `{{PRIVACY_CONTACT_EMAIL}}` | The address that will actually be read. It is the only route for somebody who has uninstalled the app. |
| `{{LEGAL_ENTITY_NAME}}` | The controller. |
| `{{REGISTERED_ADDRESS}}` | The controller's address — also required for EU trader status. |

```bash
grep -o '{{[A-Z_]*}}' web/delete-account/index.html | sort -u   # nothing left before publishing
```

### Two things for the lawyer, not for a developer

1. **"Within 30 days"** is the outer limit GDPR Art. 12(3) allows for responding to an erasure
   request. It is written that way rather than as a shorter promise on purpose — committing to
   less than the law requires creates an obligation nobody has staffed. Confirm it, and confirm
   that the identity check the page describes is the one you will actually perform.
2. **The retention answer is "nothing on our servers".** The page says no copy of a deleted
   account is kept for later restoration. That matches `deleteAccountDataImpl`, which
   hard-deletes rather than tombstoning — the tombstones of records deleted earlier included,
   since they still carry the author's uid — and, since September 2026, deletes the Storage
   files of those records too (before then it left them in the bucket). Whether the hosting
   provider's own backups make that sentence exactly true is a question about the Firebase DPA,
   and it is the kind of sentence a regulator reads closely. What the page does **not** claim is
   that the co-parent's phone forgets: the app never reconciles by absence (CLAUDE.md item 14),
   so a record already downloaded there stays until they delete it, and the page says so.

### Keep three files in step

Every factual claim on the page mirrors `deleteAccountDataImpl` in `functions/index.js` and the
"Deleting your account" section of `docs/legal/PRIVACY-POLICY.md`. **If any of the three changes,
all three do.** The one most likely to drift is the list of collections: the callable deletes
`events`, `event_versions` (the revisions the user saved — MON-4), `child_info`, `pets`,
`expenses`, `budgets`, `change_requests`, `request_deadlines` (the clocks of requests the user sent or was asked to answer — the co-parent's copy goes too, since each names both), `conversations`, `messages`, `custody_models`,
`family_settings`, `parenting_plans`, `calendar_friends`, `calendar_feeds`, `friend_profiles`, `google_oauth`,
`invitations`, `notification_queue` and `users`, plus the Storage files `AUTHORED_FILES` maps
(the record photo folders under `event_images/`, `receipts/`, `medical_photos/` and `pet_photos/`
— the family's folder of each authored record and the departing parent's own `solo_` folders),
and adding a collection or a
file layout to the app without adding it there leaves data behind that the page promises is gone.
One collection is **scrubbed rather than deleted**, and all three documents say so:
`export_receipts` (MON-16) keeps the hash of an export already handed to a court, with the
departing account and its family taken out, so erasing one parent does not un-verify the other's
evidence. And one kind of `invitations` stays: a co-parent invitation the departing parent sent and
somebody accepted, with the sender's name and both addresses blanked — the co-parent's record that
the two were a family, and the evidence that keeps their records out of a later family (audit F8,
October 2026).

## `verify/index.html` — checking an exported record (MON-16)

The page a lawyer, a mediator, a court or the other parent opens to check that an exported PDF or
CSV is byte for byte the file that was registered when it was made. Self-contained like the others
(no external script — a verification page whose answer depends on a CDN is not one), in English and
Czech behind a two-button switch, following the browser's language.

- **The file never leaves the browser.** It is read with the File API and hashed with
  `crypto.subtle.digest('SHA-256')`; only the 64-character fingerprint is sent. `crypto.subtle`
  exists only in a secure context, so the page must be served over **https** (Firebase Hosting is),
  and says so if it is not.
- **It calls `verifyExport` without the Firebase SDK**, the way the SDK does it: `POST` JSON
  `{"data": {"sha256": "…"}}` (or `{"recordId": "…"}`) to
  `https://europe-west3-<project>.cloudfunctions.net/verifyExport`, answered with `{"result": …}` or
  `{"error": {"status": …}}`. The base URL is the `PRODUCTION_FUNCTIONS_BASE` constant at the top
  of the script — the only line to change if the project or the functions' region does. CORS is
  handled by the callable itself.
- **It is tested in a real browser** (`web-tests/`, the CI job "Web — verification page and
  calendar feed"): Chromium opens this file against the Functions emulator, registers receipts
  through the real callables and checks every answer, both languages and a 375 px viewport. The
  page reaches the emulator through a `?functions=<base>` parameter that is honoured **only** when
  the page is served from a loopback address and the parameter names one too; on any hosted copy
  it is ignored and the page calls production, exactly as before.
- **A record ID can be looked up without a file**, and typed beside a file so the page also checks
  that the file is the one registered under *that* ID.
- **What the answer contains** is decided by the function, not the page: when, the period, the
  format, the size, and "one of the family's parents" — never a name. Every value is written with
  `textContent`. `docs/DESIGN-court-record.md` §10 says why each field is or is not there.

Its URL is in `publishedExportVerifyUrl` in `app/build.gradle.kts` since hosting went live (October
2026), so every registered export printed by a build after that carries it. Before, a registered
export printed its record ID without an address — deliberately, rather than a link that did not
resolve.

## `pair/index.html` — where an invitation link lands

The app shares an invitation as `https://coparently-a39c9.web.app/pair/CODE` once
`publishedPairingWebLink` in `app/build.gradle.kts` is true — it is, since this page was hosted
(October 2026); `-PCOPLANLY_PAIRING_WEB_LINK=false` turns it off for one build. The custom-scheme link it replaces,
`coplanly://pair?code=`, opens the app but is plain text in WhatsApp and most messengers — beta
testers could not tap it. `firebase.json` rewrites `/pair/**` to this one file and sends it with
`Referrer-Policy: no-referrer` and `X-Robots-Tag: noindex`.

- **On Android** the page shows the code and **Open in CoPlanly**, an `intent://` link that hands
  the code to the app through the custom scheme, bound to `app.coplanly`, and falls back to Google
  Play when the app is not installed. The app then asks "Link with {first name}?" — the name comes
  from `previewPairingInvitation`, after sign-in, never from this page.
- **On an iPhone** it says the app is Android-only and to ask for the calendar link (MON-17); **on a
  computer**, to open the link on the phone. Five languages, following the browser.
- **It never names the inviter and makes no request.** The page is public and anyone with a
  forwarded link can open it; the code is read from the address in the browser.
- **Links are absolute** (`/privacy/`): the rewrite serves the file at `/pair/CODE/`.
- **Tested** by `web-tests/pair-page.spec.js` in the `web` job, through `firebase.json`'s own
  rewrite and headers (`web-tests/support/static-server.js` reads them).
- **It cannot go live alone.** `firebase deploy --only hosting` publishes the whole directory, so
  hosting this page published the draft legal pages beside it (with their banners). The owner
  made that call in October 2026 and linked the drafts from the app as well; see below.

### Opening the app straight from the link (App Links)

The manifest declares the link with `android:autoVerify="true"`. Android opens the app directly,
without this page, once `https://coparently-a39c9.web.app/.well-known/assetlinks.json` names the
SHA-256 fingerprint of the key the installed app is signed with. Until then the page is the step in
between. The file is not committed because the release key does not exist yet (REL-1); when it
does, add `web/.well-known/assetlinks.json`:

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "app.coplanly",
    "sha256_cert_fingerprints": ["AA:BB:…"]
  }
}]
```

with the **Play App Signing** key's fingerprint (Play Console → Test and release → App integrity),
and the upload or debug key's too if testers install builds signed with it
(`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`).
`firebase.json`'s `"**/.*"` ignore does **not** drop it: the pinned CLI (`firestore-tests/`)
lists `.well-known/assetlinks.json` for upload under that pattern, which was checked against its
own `listFiles` before this was written. Android re-verifies on install, so reinstall to test.

### Hosting

Anything that serves a static file. `firebase.json` now carries a `hosting` block that serves
this directory (`README.md` excluded, clean URLs, trailing slashes), so once a Hosting site
exists in the Firebase project:

```bash
firebase deploy --only hosting
```

puts the home page at `https://<site>/` and the five others at `https://<site>/delete-account/`,
`https://<site>/privacy/`, `https://<site>/terms/`, `https://<site>/verify/` and
`https://<site>/pair/`. **Deployed in October
2026** to `https://coparently-a39c9.web.app/`, on the owner's decision, with the privacy policy and
terms still drafts under their banner — a closed test needs them linked, and a page regenerated in
place keeps its URL. The deletion and policy URLs are filled into the legal documents and the
deletion page, and `publishedPrivacyPolicyUrl`, `publishedTermsUrl`, `publishedExportVerifyUrl` and
`publishedPairingWebLink` at the top of `app/build.gradle.kts` are set. Left for the Play Console:
the data-deletion field (`/delete-account/`) and the privacy-policy field (`/privacy/`). A page
changed in this directory is live only after the next `firebase deploy --only hosting`.

**The app links the policy, but only once that value is set.** Settings → Account has a
"Privacy policy" row and the telemetry consent screen a "Read the privacy policy" button, both
behind `PrivacyPolicyLink.url`, which is null while `BuildConfig.PRIVACY_POLICY_URL` is blank. A
row pointing at a dead link is exactly the affordance-promising-nothing that design rule #8 in
`CLAUDE.md` forbids, so the build that ships with the URL is the first one to show either.
`-PCOPLANLY_PRIVACY_POLICY_URL=…` sets it for one build without committing it.
