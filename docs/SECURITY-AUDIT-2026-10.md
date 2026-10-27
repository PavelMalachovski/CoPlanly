# Security and data-leak audit — October 2026

Done on 27 October 2026, against `main` at `9b22afc`. The app holds children's medical profiles,
photos, schedules and the parents' chat, so the question was one thing above all: **can anybody
read or change a family's data who should not?**

Six reviews ran side by side, each tracing code rather than guessing:

1. `firestore.rules`;
2. `storage.rules` and file handling;
3. `functions/index.js`;
4. the AI, calendar-feed, export-receipt and revision modules, plus `web/`;
5. the Android client;
6. secrets, CI and dependencies.

The owner confirmed that the Firestore rules, the Storage rules and the functions in the repository
are the ones deployed, and that only testers' data exists so far.

**Verdict.** No finding lets a stranger read a family's records directly. The rules hold against
every direct read and query the reviews tried, and nothing anywhere mints a download URL. Two
findings were how a stranger could *become* a member of a family, and both are fixed in this
change (§1). The second pass (§3) fixed the court-record and on-phone findings. What remains is
low-severity hardening (§4), what a push may say (§5), and actions only the owner can take in a
console (§2).

## 1. Fixed in the first pass (High)

| # | Severity | What | Fix |
|---|---|---|---|
| F-1 | **High** | **Invitation codes could be brute-forced.** A code is 6 characters of 31 (≈ 8.9×10⁸). The four redeem callables had no limit at all, and the preview had only a per-instance memory limit. A signed-in account could loop `acceptPairingInvitation({code})` until a code fitted. A pairing hit makes the caller a co-parent at once: children's records, medical profiles, chat and money, with the audience backfill re-sharing history. A guest hit opens one child's medical record. | `functions/redeem-guard.js`: every miss (`not-found`) counts per account in `redeem_attempts/{uid}`, which is server-only and deleted with the account. Past 10 misses in an hour, all five callables refuse with `resource-exhausted` / `too-many-attempts` before reading any invitation. Finding one of a hundred live codes now takes about a thousand account-years. Tests: `functions/test/redeem-guard.test.js`, plus the account-deletion case. |
| F-2 | **High** | **An invitation's sender could be forged.** The create rule pinned `fromUserId` only, so `fromUserEmail` and `fromUserName` were free text. A stranger who knew a parent's address could send an invitation "from" the ex's real e-mail. The incoming-invite card shows that address beside **Accept**, which links the stranger as co-parent. | `firestore.rules`: `fromUserEmail` must be blank or the caller's own sign-in address (`invitationSenderTruthful`), and `fromUserName` is bounded to 256 characters. Tests in `firestore-tests/rules/invitations.test.js`. |
| F-3 | Medium | **An invitation could live for ever.** `expiresAt` had no upper bound (year 9999 was accepted), which keeps a code guessable for that long and outside every sweep. | `expiresAt` may be at most 8 days ahead. The app's longest is 7. Tested. |
| F-4 | **High** (deps) | `npm audit --omit=dev` in `functions/`: 1 critical (`proxy-addr`) and 2 high (`@fastify/busboy`, `body-parser`) in the request path of the public `calendarFeed` and `verifyExport`. | `npm audit fix` (lock file only, no major bump). 8 moderate remain; they need `firebase-admin` 14 (§3). |
| F-5 | Low | `regenerate.yml` put `${{ github.ref_name }}` straight into a shell that holds a push token. Git allows `$ ( ) ;` in branch names. | The name goes through `env` and quotes. `ci.yml` now defaults the token to `contents: read`. |
| F-6 | Medium (docs) | The privacy policy and the ROPA said pushes carry no message text. A chat push in fact carries the **first 120 characters** of the message through Firebase Cloud Messaging (`CHAT_MESSAGE_PREVIEW_LENGTH`). | Both documents now say so; `web/privacy/` is regenerated. Whether to keep the preview at all is decision D-2 below. |

Verified in the first pass: `functions` mocha (578 passing) and eslint; the whole `firestore-tests` suite on
the emulators (975 passing); `check-invariants.js`; `check-e2e-coverage.js`; `tools/test`.
**After merge, deploy:** `firebase deploy --only firestore:rules,functions,hosting` (hosting for
`web/verify/` and `web/privacy/`).

## 2. Actions only the owner can take

1. **Rotate the Google OAuth client secret and revoke the old Gemini API key.** Both shipped in
   every APK before SEC-1 and MON-7 (`AUDIT-2026-07.md`, `AUDIT-2026-08.md`). The rotation box in
   `REVIEW-2026-07-23.md` is still unchecked, and nothing records that either was done. The
   repository is **public**, and any old APK still holds both values.
2. **Turn on GitHub secret scanning and push protection** (free on a public repository), and run
   gitleaks or trufflehog over a full clone. This session's clone is shallow, so history before
   2026-09-24 was not scanned.
3. **Enforce Firebase App Check** (Play Integrity) on the callables, at least the four redeem
   callables and the preview. F-1 is a per-account limit. App Check is what stops a sweep spread
   over many free accounts.
4. **Move the release-signing secrets into a protected GitHub Environment.** `release.yml` is a
   `workflow_dispatch` any writer can run from a branch whose Gradle scripts they changed. Limit
   it to `main` and require a reviewer.
5. **Personal data in a public repository.** Two real Gmail addresses (the owner's test accounts)
   with partial UIDs are in `docs/TEST-PLAN-2026-08.md`. A real tester's first name and personal
   circumstances are in the `*zuzana*` specs and plans under `docs/superpowers/` and in
   `DESIGN-stickers-and-subscription.md`. Decision D-1.
6. **Rename the `.firebaserc` alias `coparently-staging`**: it points at the production project.

## 3. Fixed in the second pass (Medium)

| What | Fix |
|---|---|
| **A revision could be suppressed or left out.** The server compared only the write key (`saved\|<updatedAt>`), and nothing pins `updatedAt`. An edit that kept the old stamp read as "unchanged". A phone revision carrying that key over invented content made the server stand down. | `recordServerEventRevision` also compares title, description, location, start and end (`CONTENT_FIELDS`). The editor is deliberately not compared: the app does not stamp `lastModifiedBy` reliably, so the server would record revisions under the wrong name. |
| **Messages could be backdated.** Nothing bounded `timestamp` at create. | A numeric `timestamp` must lie between 30 days ago and 5 minutes ahead. 30 days because the outbox keeps the time an offline message was written. The server's own receipt time is the document's `createTime`: Firestore keeps it, no client can set it, and an operator can read it. Nothing is stamped on the message, because a retried `set()` of a landed message would then be refused. |
| **A creator could forge the co-parent's answer.** `acceptance` and `acceptedBy` were open to the creator. | `answerWrittenByItsParent`: an answer that changes must name its writer, and the writer must not be the creator. The creator may still re-save the stored answer, or set the event back to PENDING to ask again. Cost: a creator's second phone that still holds an older answer than the server has will have its upload refused until it syncs. |
| **A receipt said "one of the family's parents" for any account, and a copy could shadow the original.** | Reservation records `familyBound` (a `families/{id}` document existed). Otherwise the page reads "a CoPlanly account (not linked to a family when it was registered)" in English and Czech. A hash registers under one ID only, and a lookup by file answers with the earliest receipt. A family is deliberately not required: a parent exporting after an unpair is the case that matters most. |
| **A parenting plan stayed writable after unpair.** | Create and update need `isLiveFamilyMember`. Reading is unchanged. |
| **A change request could be hard-deleted.** | `allow delete: if false`. No client deletes one; a withdrawal is the `CANCELLED` status. |
| **Calendar feed:** an unescaped `UID:${doc.id}`; PENDING and DECLINED events served; no instance cap. | The feed serves only ids matching `^[A-Za-z0-9_-]{1,128}$` (what the app writes), skips PENDING and DECLINED, and is capped at 20 instances. |
| **Decrypted child files outlived the account on the phone.** Medical, pet, receipt and event photos, vault documents, chat attachments, captures, the last export and payment codes were all plaintext outside SQLCipher. | `data/session/DeviceTraces`. Sign-out removes the plaintext copies and cancels the notifications on screen. It keeps `files/chat_outbox`, which unsent messages in Room still need. An account switch or deletion removes the outbox too. `DeviceTracesTest` (Robolectric). |
| The IBAN was copied without the sensitive flag. | It goes through `copySensitive`, as the invite code does. |

Verified here: `functions` mocha (587 passing) and eslint; `firestore-tests` (985 passing);
`web-tests` against the emulators (41 passing). **The Kotlin changes are verified by CI only**,
because this session has no Android SDK.

## 4. Open findings

Severity is the reviewers', checked against the code. "Device" means it needs a phone, or somebody
holding the unlocked phone or its files. None of these lets a stranger read a family's records.

### On the phone (device)

- **What the lock screen shows is the system's choice, so the fix is what a push says.** The
  review called this Medium, and its proposed fix (`VISIBILITY_PRIVATE` plus a public version)
  changes nothing: `VISIBILITY_PRIVATE` is already Android's default. With Android's "hide
  sensitive content" switched on, the lock screen already reads "Contents hidden". With the
  default "show all content", the full text shows, as for every app. To keep chat text and
  children's names off the lock screen by default, the push must not carry them. That is D-2.
- Recents thumbnails are hidden only on API 33+ (`setRecentsScreenshotEnabled`). On API 26–32 the
  app switcher shows the last screen. `FLAG_SECURE` is deliberately off.
- A camera capture or receipt photo stays in the cache until sign-out rather than until upload.
  The widget can show the previous account's day for up to an hour after sign-out.
- A message the outbox held for more than 30 days is now refused by the timestamp bound and stays
  unsent.

### Rules and functions

- **`child_info` guests can be written by a client.** On create, guest keys are subtracted from
  the audience before it is bounded. So any account can put a child record into other users' sync
  (spam they cannot delete), and a parent can grant a guest without `acceptGuestInvitation`'s
  checks. Not a leak: only a parent in `sharedWith` can add a guest to a real child. **Not fixed
  on purpose:** a rule cannot iterate a map, and "clients may only remove guests" would refuse
  every save from a phone whose copy still holds a guest the daily sweep removed. That record
  would then never sync again. Fix it with a client change first (upload the server's `guests`
  map, not Room's), then the rule.
- The calendar feed's limiter is keyed on whatever token the caller sends (the 20-instance cap
  bounds it now).
- A co-parent's phone revision of a save with the true content but their own `editorUid` still
  stands in for the server's. The content is right; the editor named may be wrong.
- `aiAssist` puts the co-parent's profile name (up to 60 characters) into the system prompt, and
  reads the thread before the quota check. Off in release until MON-11.
- `deleteAccount` needs only a valid ID token (no recent sign-in). It can leave `families/{id}` if
  unpair's transaction fails inside it. Guest grants have no server-side ceiling on their end.
- `conversations` updates are not tied to a live pairing and do not bound their keys.
  `notification_queue.data` takes extra keys. Several free-text fields have no size bound.
- A co-parent can read the whole `users/{uid}` profile: `partnerIds` (which reveals a second
  family's co-parent uid), `fcmToken`, the consents. A minimisation note.
- Storage: `isOneOfPair` accepts any `a__b` id that names the caller (free file hosting, no
  reach into a real family); a stored `file:`/`content:` photo entry is opened from local disk.
- `npm`: 8 moderate (`uuid` via `google-gax`), fixed only by `firebase-admin` 14 /
  `firebase-functions` 7. `firestore-tests/`' dev dependencies have more (CI only).
- Hosting sets no CSP, `frame-ancestors` or `nosniff` except on `/pair/`.
- Third-party actions are pinned by tag, not SHA.

## 5. Decisions for the owner

- **D-1.** Strip the tester's name and circumstances and the real addresses from `docs/`, and
  rewrite history if it matters. The repository is public.
- **D-2.** Keep the first 120 characters of a chat message in the push (they pass through
  Firebase Cloud Messaging and show on a lock screen that shows all content), or send none and let
  the phone write "New message from Bob"? The same question applies to a child's name in
  `child_info_updated` and an event's title in a reminder.

## 6. Checked and sound

- **Firestore rules:**
  - Default deny.
  - A user cannot widen their own audience: `role`, `partnerId(s)` and `pendingRevocationOf` are
    pinned, and a profile cannot be deleted.
  - Events, child records and pets are bounded to their own family.
  - Expenses and budgets are read only by a live family.
  - Friends and professionals are kept to their family and their expiry, and never reach chat or
    child records.
  - `invitations` cannot be looked up by code.
  - `notification_queue` is limited to self or a live co-parent and the allowed types.
  - Closed collections: receipts, feeds, OAuth fingerprints, AI quota.
- **Storage rules:** no `list`, no overwrite, uploader metadata required. No download URL anywhere.
  Every download's SHA-256 is checked. File names are sanitised against path traversal.
- **Functions:**
  - Every callable takes the uid from the auth context.
  - Unpair can end only one of the caller's own families.
  - Operator callables are closed unless `BACKFILL_ADMIN_UIDS` names the caller.
  - The OAuth secret stays on the server.
  - Feed tokens are 256-bit and only their hash is stored.
  - Export IDs are 80-bit.
  - Logs carry no names, e-mails, message text, codes or tokens.
- **Android:**
  - Backup and device transfer are off for every domain.
  - Only `MainActivity` and the widget receiver are exported, and every launch extra is validated.
  - A pairing code from a link is only prefilled and needs a confirmation.
  - PendingIntents are immutable and explicit. There is no cleartext traffic and no WebView.
  - Release builds strip `Log.d/i/v`, and the remaining `w/e` lines carry ids only.
  - No secrets in source or `BuildConfig`.
- **Web:** `web/pair/` and `web/verify/` write every value with `textContent`. The `?functions=`
  override applies only on loopback.
