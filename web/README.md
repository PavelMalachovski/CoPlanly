# `web/` — the pages that have to exist outside the app

Two things Google Play requires of CoPlanly are URLs, not screens: a privacy policy and a route to
delete an account that works **without the app installed**. This directory holds both, plus the
terms of service: `delete-account/`, `privacy/` and `terms/`, one self-contained `index.html`
each. The legal texts are still drafts (REL-4) — every page carries a banner saying so until the
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
2. **The retention answer is "nothing".** The page says no copy of a deleted account is kept for
   later restoration. That matches `deleteAccountDataImpl`, which hard-deletes rather than
   tombstoning. Whether the hosting provider's own backups make that sentence exactly true is a
   question about the Firebase DPA, and it is the kind of sentence a regulator reads closely.

### Keep three files in step

Every factual claim on the page mirrors `deleteAccountDataImpl` in `functions/index.js` and the
"Deleting your account" section of `docs/legal/PRIVACY-POLICY.md`. **If any of the three changes,
all three do.** The one most likely to drift is the list of collections: the callable deletes
`events`, `event_versions` (the revisions the user saved — MON-4), `child_info`, `pets`,
`expenses`, `budgets`, `change_requests`, `conversations`,
`messages`, `custody_models`, `family_settings`, `calendar_friends`, `friend_profiles`,
`invitations`, `notification_queue` and `users`, and adding a collection to the app without adding
it there leaves data behind that the page promises is gone.

### Hosting

Anything that serves a static file. `firebase.json` now carries a `hosting` block that serves
this directory (`README.md` excluded, clean URLs, trailing slashes), so once a Hosting site
exists in the Firebase project:

```bash
firebase deploy --only hosting
```

puts the three pages at `https://<site>/delete-account/`, `https://<site>/privacy/` and
`https://<site>/terms/`. **Nothing has been deployed yet**, and deploying is a decision, not a
build step: the privacy policy in particular must not go live with a draft banner on it. The
URLs then go in **three** places: the Play Console's data-deletion and privacy-policy fields,
`{{WEB_DELETION_URL}}` and `{{PRIVACY_POLICY_URL}}` in the legal documents, and the Settings rows
the next paragraph describes.

**Not wired into the app yet, on purpose.** Settings has no row linking to these URLs because they
do not resolve yet, and a row pointing at a dead link is exactly the affordance-promising-nothing
that design rule #8 in `CLAUDE.md` forbids. Add the rows in the same change that publishes the URLs.
