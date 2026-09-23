# `web/` — the pages that have to exist outside the app

Two things Google Play requires of CoPlanly are URLs, not screens: a privacy policy and a route to
delete an account that works **without the app installed**. This directory holds both, plus the
terms of service: `delete-account/`, `privacy/` and `terms/`, one self-contained `index.html`
each. A fourth page, `verify/`, is not a Play requirement but belongs with them: it is where a
lawyer or a court checks an exported record (MON-16). The legal texts are still drafts (REL-4) — every page carries a banner saying so until the
last `{{PLACEHOLDER}}` is gone — but the pages exist so that hosting them is one command once a
lawyer has been through the markdown.

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
| `{{PRIVACY_POLICY_URL}}` | Where the privacy policy ends up. |
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
`expenses`, `budgets`, `change_requests`, `conversations`, `messages`, `custody_models`,
`family_settings`, `parenting_plans`, `calendar_friends`, `calendar_feeds`, `friend_profiles`, `google_oauth`,
`invitations`, `notification_queue` and `users`, plus the Storage files `AUTHORED_FILES` maps
(`event_images/`, `receipts/`, `medical_photos/`, `pet_photos/`), and adding a collection or a
file layout to the app without adding it there leaves data behind that the page promises is gone.
One collection is **scrubbed rather than deleted**, and all three documents say so:
`export_receipts` (MON-16) keeps the hash of an export already handed to a court, with the
departing account and its family taken out, so erasing one parent does not un-verify the other's
evidence.

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
  `https://us-central1-<project>.cloudfunctions.net/verifyExport`, answered with `{"result": …}` or
  `{"error": {"status": …}}`. The base URL is the `FUNCTIONS_BASE` constant at the top of the
  script — the only line to change if the project or the functions' region does. CORS is handled
  by the callable itself.
- **A record ID can be looked up without a file**, and typed beside a file so the page also checks
  that the file is the one registered under *that* ID.
- **What the answer contains** is decided by the function, not the page: when, the period, the
  format, the size, and "one of the family's parents" — never a name. Every value is written with
  `textContent`. `docs/DESIGN-court-record.md` §10 says why each field is or is not there.

Once hosted, its URL goes into `publishedExportVerifyUrl` in `app/build.gradle.kts`, and every
registered export printed after that carries it. Until then a registered export prints its record
ID without an address — deliberately, rather than a link that does not resolve.

### Hosting

Anything that serves a static file. `firebase.json` now carries a `hosting` block that serves
this directory (`README.md` excluded, clean URLs, trailing slashes), so once a Hosting site
exists in the Firebase project:

```bash
firebase deploy --only hosting
```

puts the four pages at `https://<site>/delete-account/`, `https://<site>/privacy/`,
`https://<site>/terms/` and `https://<site>/verify/`. **Nothing has been deployed yet**, and deploying is a decision, not a
build step: the privacy policy in particular must not go live with a draft banner on it. The
URLs then go in **three** places: the Play Console's data-deletion and privacy-policy fields,
`{{WEB_DELETION_URL}}` and `{{PRIVACY_POLICY_URL}}` in the legal documents, and
`publishedPrivacyPolicyUrl` at the top of `app/build.gradle.kts`.

**The app links the policy, but only once that value is set.** Settings → Account has a
"Privacy policy" row and the telemetry consent screen a "Read the privacy policy" button, both
behind `PrivacyPolicyLink.url`, which is null while `BuildConfig.PRIVACY_POLICY_URL` is blank. A
row pointing at a dead link is exactly the affordance-promising-nothing that design rule #8 in
`CLAUDE.md` forbids, so the build that ships with the URL is the first one to show either.
`-PCOPLANLY_PRIVACY_POLICY_URL=…` sets it for one build without committing it.
