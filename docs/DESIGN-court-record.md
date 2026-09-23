# What a CoPlanly export may honestly claim — MON-4

**Decided on 2026-09-23.** This began as a decision paper: three questions were the owner's, and
MON-3 (export to PDF/CSV, the first paid feature) could not be built until they were answered.
They are answered in §9. The owner took the recommendation on the first and third, and chose
**full event versioning** over the lighter edit trail on the second, so §4 to §7 now describe the
shape that was built rather than the one that was recommended; the trail survives in §4 as the
option not taken, with the reason it lost. Each section still states what the code does, and the
facts about it are checked and cited.

Companion to `docs/ROADMAP.md` MON-4 and `docs/LAUNCH-PLAYBOOK.md` §4.4.

---

## 1. Why this blocks the export rather than following it

Willingness to pay in this category concentrates on documentation you can hand to a lawyer. That is
also the most dangerous thing to sell, because **an export is only worth what the record behind it
is worth**, and the first opposing counsel who reads one closely decides that in public.

The failure mode is specific and cheap to walk into: a parent exports a PDF, hands it to their
lawyer, and the other side points out that the app lets its own users rewrite the entries with no
trace. The document is then worse than nothing — it has been introduced as evidence and discredited,
and the app's name is attached to that.

So the question to settle first is not "what can we export" but **"what can we truthfully say the
export is"**.

---

## 2. What the code guarantees today

Checked against `firestore.rules`, `functions/index.js` and the repositories on `main` at
`494301a`. This is the real starting position, and it is stronger in one place and weaker in
another than the roadmap's one-line summary suggests.

| Record | Can it be changed after the fact? | Ordered by | Notes |
| --- | --- | --- | --- |
| **Chat messages** | **No, and this is enforced server-side.** `allow delete: if false`; update is two disjoint `hasOnly` branches — `isRead` alone, or a constrained `conversationId` re-point. Content, sender, timestamp and attachments cannot be touched by anybody. | `sentAtMillis`, **epoch millis** (schema 13) | `MessageRepositoryImpl.deleteMessage` is Room-only and says so in a comment. A message can vanish from one phone; it cannot vanish from the record. |
| **Activity announcements** | **No** — they *are* chat messages, in the same collection, under the same rules | epoch millis | `ActivityAnnouncement` carries facts, not a sentence, so the reader's device renders it in their own language |
| **Custody schedule** | Yes, by either parent | `lastModifiedAtMillis`, **epoch millis** (SEC-4, schema 29) | Ordering was a naive local date-time until SEC-4, and the winner is *re-pushed over* the loser, so the wrong schedule could win and overwrite |
| **Expense split ratio** | The agreement can be renegotiated; **the price of a recorded expense cannot** | — | `Expense.splitBasisPoints` snapshots the ratio in force when the expense was recorded, deliberately, so renegotiating cannot re-price a settled month |
| **Change requests** | Status changes; the *before* state does not | `createdAt` / `respondedAt`, naive `LocalDateTime` | `currentStartDateTime`/`currentEndDateTime` capture the event as it stood when the request was made — a genuine before-image, already stored |
| **Events** | **Yes, freely, by the creator, with no history** — *as of `494301a`; since 2026-09-23 every saved revision is kept whole in `event_versions` (§4)* | `updatedAt`, **naive `LocalDateTime`** | `createdByFirebaseUid` is immutable and a partner editor cannot change `sharedWith`/`permissions`, but the content is unpinned |
| **Expenses, budgets, child and pet records** | Same as events | naive `LocalDateTime` | |
| **Deletions** | Tombstoned, not removed: `deletedAtMillis` + `deletedBy` | epoch millis | CQ-3. A deletion is itself a dated, attributed record for 90 days |

**Two things worth pulling out of that table.**

**The chat is already the strongest record in the app**, and nobody has been treating it as one.
Neither parent can edit or delete a message server-side; the timestamps are real instants, not local
wall clocks; and the activity feed — every announced change to the calendar, the schedule and the
expenses — flows through the same immutable collection. TalkingParents charges $32/month for a tier
whose headline is "Unalterable Records". CoPlanly has had them since the August 2026 chat work and
has never said so.

**Events are the weak half**, and they are the half a custody argument is actually about. A parent
can move an event and nothing records that it moved, who moved it, or what it said before.

---

## 3. Decision 1 — what the export claims to be

This is the decision the other two follow from.

| Option | The claim | What it needs | The risk |
| --- | --- | --- | --- |
| **A. A truth record** | "This is what happened" | Append-only everything, server-authored timestamps, edits as new versions, probably a signed hash chain | The app cannot make this claim honestly for anything a user types on their own phone, ever. Offline-first means the device is the source of truth for a while, and a determined user controls the device. Selling this is selling something the architecture cannot deliver |
| **B. A communication record** ⭐ | "This is what was said between the two of you, and what each of you was told had changed, and when" | Almost entirely already true (§2) | Narrower than what a parent hopes to buy. Has to be said plainly in the product, or the parent discovers the limit in front of a judge |
| **C. Nothing — no export** | — | — | Leaves the category's most-paid-for feature unbuilt, and MON-3 with it |

**Recommendation: B.**

Not as a compromise but because it is the only one that is *true*, and because it is what the
competitors who charge most actually sell. "Unalterable Records" at TalkingParents, OFW's court
packet — these are records of the **exchange**, not audits of reality. Nobody claims to know
whether a handover happened; they claim to know exactly what each parent said about it and when.

B also has a property A does not: it is honest about the adversary. The thing a court cares about
is whether *the other parent* can alter the record. A cannot protect against the exporting parent
and pretends to; B protects against exactly the party it should and says so.

**What B lets the product say** — and, as decided, what the export's own header says rather than
only the marketing (`export_statement_*` in the five `export_strings.xml`):

> This is a record of what the parents recorded and wrote to each other in CoPlanly, and when. It
> is not a record of what happened, and it does not say whether anything written here is true.
> Messages cannot be edited or deleted by either parent once sent. Every saved revision of a
> calendar entry is kept whole and cannot be changed afterwards; each is shown with the time the
> editing device recorded and the time the server received it. Expenses are shown as they stand
> today.

The earlier draft of this paragraph said *"where an entry was changed, this document records the
notification the other parent received, not the entry's full history"*. That sentence was the
honest limit of the trail; versioning removes it, which is exactly the trade §4 describes.

---

## 4. Decision 2 — which records become append-only

Given B, the question narrows usefully: **what has to become append-only for the export to say
more than "here are the messages"?**

| Record | Decision | Why |
| --- | --- | --- |
| **Chat + announcements** | Already append-only. **Pinned in the rules tests** (`firestore-tests/rules/event-versions.test.js`, last block) and treated as a breaking change if it moves. MON-16 completed the pin: content, timestamp, sender id *and* name, type, reply target and attachments are refused to **both** parents, as are a rewrite smuggled in beside `isRead`, a whole-document `set()`, a delete by either side and any write by a stranger — with the one allowed write (`isRead`) as a control | The guarantee existed but nothing asserted it; a rule edit could have quietly widened `hasOnly(['isRead'])` and no test would have failed |
| **Events** | **Full versioning — every saved revision kept whole** *(owner's choice; the recommendation was a trail)* | See below |
| **Custody schedule** | Already announced through the activity feed; leave the document mutable | Two parents negotiating a schedule need to edit it. What matters is that each change was announced, and it is |
| **Expenses** | Leave mutable; the split snapshot already covers the part that is money | A corrected amount is a correction, not a falsification. The export says expenses are shown as they stand |
| **Child medical profile** | **Leave mutable and out of the export entirely** | Special-category data under GDPR Art. 9. An export that carries a child's medication into a court filing is a harm the product should not make easy |

### Events, as built: `event_versions`

Every create, update and delete of a **non-private** event appends one immutable document holding
the whole event as it was saved.

```
event_versions/{versionId}          versionId: a UUID minted on the device at save time
  eventId          the event this is a revision of
  kind             'created' | 'updated' | 'deleted'
  editorUid        request.auth.uid — the rule refuses anything else
  deviceTimeMillis when the parent saved it, epoch millis, from their device
  recordedAt       FieldValue.serverTimestamp() — the rule requires == request.time
  sharedWith       who may read this revision: the event's audience when it was saved
  familyId         the event's familyId, '' when it had none
  event            the event document, in exactly the wire format EventRepositoryImpl
                   .toFirestoreMap() writes (plus deletedAtMillis/deletedBy on a delete)
  formatVersion    1
```

**Why a top-level collection, and not `events/{id}/versions`.** Three reasons, each sufficient.
The 90-day tombstone sweep deletes event documents, and a subcollection's fate is then tied to a
parent that no longer exists — Firestore keeps orphaned subcollections, but an admin
`recursiveDelete` does not, and "one careless cleanup away from losing the history" is not a
property a court record should have. The export reads **every revision it may see in one query**
(`whereArrayContains("sharedWith", uid)`), where a subcollection needs a collection-group query and
a recursive-wildcard rule over a name as generic as `versions`. And account deletion
(`deleteAccountDataImpl`) removes a departing parent's revisions with one `where('editorUid', …)`.

**Why no stored revision number.** A number has to be assigned somewhere, and neither place
works. Two phones editing one event offline would each mint "revision 4"; under a create-only rule
the second is refused for ever, and resolving that needs a server round trip the offline-first app
does not have. So the order is carried by the two clocks — `recordedAt` first, `deviceTimeMillis`
where the server has not stamped one yet — and the export numbers each event's revisions 1…n when
it renders. The number is a property of the printed document, not of the record.

**Why a Room outbox, and not a write beside the event's.** The event write paths are best-effort:
`EventRepositoryImpl.updateEvent` discards the `Result` of its remote write and nothing re-queues
an edit that failed. A revision written the same way would be lost exactly when the phone was
offline. So a revision goes into Room (`event_version_outbox`, schema 37) in the same call that
saves the event, and `EventVersionRecorder.flush` uploads it — right after the save, and again on
every sync — deleting the row only once the server has it. The document id is the row's id, so a
retry after a lost acknowledgement is recognised by reading the document back, not written twice.

**The rule** (`firestore.rules`, `match /event_versions/{versionId}`): `create` only, and only
when the author is the caller, `recordedAt == request.time`, the key set is exactly the list above,
the audience is the caller's own family (`isMyAudience`, item 23) and includes the caller, the
`familyId` is one the caller is in or blank, and — when the event document exists — the caller may
edit it (its creator, or a co-parent holding `read_write`). **`update` and `delete` are `false` for
everybody.** Read is membership of the revision's own `sharedWith`.

Four decisions inside that, each with its reason:

- **A missing event does not refuse a revision.** The revision is written from the outbox, and the
  event it describes may not have landed yet (created offline), or may already be gone (created
  and deleted offline, or swept 90 days after a delete). Refusing those would strand the outbox
  for ever. Allowing them grants nothing: the author is the caller and the audience is their own
  family, so the most a forged revision can do is put the caller's own words, under their own
  name, in front of their own co-parent — which is what creating an event already does.
- **Calendar friends cannot read revisions.** A friend sees the calendar as it stands; the history
  of how two parents edited it is the parents' communication record, not the family's calendar.
- **Unpair does not narrow a revision's audience.** A revision is what both parents could see when
  it was saved, and the ex-partner keeps it the way they keep the chat thread (`messages` are not
  swept either). Revisions saved *after* unpair carry the narrowed audience, because the device
  computes it from live pairing state when it uploads.
- **Nothing pins the snapshot to the event document.** The rule proves who wrote a revision and
  when the server received it, not that it matches the event: an outbox that flushes after a
  co-parent's later edit would otherwise be refused for ever. What the export can say is therefore
  exactly B's claim — *this is what the parent's device said it saved* — and it says so.

**The trail, and why it lost.** The recommendation was one `event_edits` row per edit carrying
`{from, to}` per changed field: cheaper, and it answers "was this moved, by whom". The owner chose
versions because a trail can only say what changed relative to something the reader cannot see,
and because every revision whole is the simpler thing to explain to a lawyer. The cost the trail
avoided is real and accepted: every event is stored once more per save, for ever.

---

## 5. Decision 3 — whose clock orders writes

Before this work: `sentAtMillis`, `lastModifiedAtMillis` and the tombstones were epoch millis;
`updatedAt`, `createdAt` and `respondedAt` were naive `LocalDateTime` with no zone.

| Option | Assessment |
| --- | --- |
| **Device clock, local wall time** (events, still) | Unusable in an export. Two parents in two zones produce times that cannot be ordered, and a device clock can simply be wrong |
| **Device clock, epoch millis** ⭐ for existing fields | What SEC-4 already did for custody and chat. Orders correctly across zones; still trusts the device |
| **Server timestamp** (`FieldValue.serverTimestamp()`) for the revision | The right answer for a record whose whole purpose is to be evidence — but it cannot be the only timestamp, because the app is offline-first and a queued write must still record when the *parent* acted |

**Decided: both, on the new record, labelled.** `deviceTimeMillis` is when the parent acted, from
their device. `recordedAt`, server-stamped on arrival and pinned by the rule to `request.time`, is
when the system saw it. The export prints both, under those labels, for every revision — a
revision still in this device's outbox prints "not yet received by the server" in the second
column rather than a guess. That is more honest than either alone, and it is the shape
`CustodyTimestamp.kt` already argues for in a narrower case.

**Still owed from this answer: `Event.updatedAt` to epoch millis.** It is compared —
`ConflictResolver` decides a sync conflict on it — so "epoch millis on every compared field"
reaches it. The revisions do not depend on it (they carry their own two clocks), which is why it
was not built with them; it is recorded in ROADMAP MON-4. Read `domain/custody/CustodyTimestamp.kt`
before touching the wire form — it explains why the Firestore field kept both its name *and* its
ISO-string type and only changed the zone it expresses, and the same trap applies: a co-parent on
an older build must keep reading the field.

`ChangeRequest.createdAt`/`respondedAt` can stay naive for now. They are displayed, not compared.

---

## 6. What an edit does to history

**Nothing is rewritten and nothing is hidden.** The event changes in place, a new immutable
revision appears beside it, and the co-parent's notification is already an immutable chat message.

**A revision is never deleted with its event.** When an event is tombstoned its revisions survive,
and a `deleted` revision joins them; when the 90-day sweep removes the tombstone, the revisions
still survive — `event_versions` is deliberately **not** in `TOMBSTONED_COLLECTIONS`, and a
functions test pins that. A deletion is the most interesting edit of all, and a history that
vanished with the thing it describes would be missing exactly the entry a dispute is about.

**Account deletion is the one exception**: `deleteAccountDataImpl` deletes the revisions the
departing parent authored and scrubs their uid from the audience of the co-parent's, or erasure is
incomplete. The co-parent's own revisions of the departing parent's events stay — they are the
co-parent's words.

**Private events never produce a revision** (CLAUDE.md item 3). An event its creator later makes
private keeps the revisions it had while shared and gains none recording the withdrawal: the
content that made it private never leaves the phone. The export's header says so.

---

## 7. What this cost

| Piece | Size | Notes |
| --- | --- | --- |
| `event_versions` collection, rules block, rules tests | S | `firestore-tests/rules/event-versions.test.js` |
| Recording a revision on the three event write paths, through a Room outbox (schema 37) | M | `data/versions/EventVersionRecorder` — the event paths' remote writes are best-effort, so a revision needs its own retry |
| Account deletion reaches revisions; the sweep provably does not | S | `functions/index.js`, two tests |
| Pinning chat immutability in `firestore-tests/` | S | Asserts a guarantee that already holds |
| Excluding the medical profile from anything exportable | S | A decision expressed as an absence: the export reads events, messages and expenses only |
| `Event.updatedAt` → epoch millis, with migration | M | **Not built** — see §5 |
| **Then** MON-3 itself | M | Built: `domain/export`, `presentation/export` |

---

## 8. What not to promise, at any price

- **Not "tamper-proof".** The exporting parent controls their device and their own entries. The
  claim is that the *other* parent cannot alter the record, and that messages and saved revisions
  cannot be altered by anybody.
- **Not a legal opinion on admissibility.** That varies by court and is not the app's to give.
  Say what the record is; let a lawyer say what it proves.
- **Not the child's medical data in an exportable document.** §4.
- **Not a signed or notarised artefact** unless someone has actually built the signing. MON-16
  (§10) is not that either: a registered hash proves a file has not changed since a parent's
  phone registered it, and when — not that the file is an honest rendering of the server's
  record. OFW posts a
  physical court packet; that is a service, not a PDF button, and claiming its weight without its
  work is the single fastest way to lose the credibility this whole feature is for.
- **Not that every edit carries a revision.** The rules do not *require* an event write to be
  accompanied by one — an older build, or a modified client, can still edit an event without
  recording it. Making the events rule demand a matching revision is possible (`existsAfter`), but
  it would refuse every edit from a co-parent on an older build, so it waits until the app can
  require an update. Until then the export says what it holds, not what it caught.

---

## 9. The three answers, in one place

Answered by the owner on **2026-09-23**:

1. **The export is a ☐ truth record / ☑ communication record** — what the parents recorded and
   wrote in the app, and when; not a record of what happened. The export says so on its face.
2. **Append-only:** ☑ chat *(already)*, ☐ a new event edit trail, ☑ **full event versioning** —
   every saved revision of an event kept whole. *(The recommendation was the trail; the owner
   chose versions. §4 records both.)*
3. **Clock:** ☑ epoch millis on every compared field, ☑ a server-stamped `recordedAt` beside the
   device time on each revision — the export shows both, labelled. *(`Event.updatedAt` is the one
   compared field still naive; see §5.)*

---

## 10. A verifiable export — MON-16

**Built on 2026-09-23.** Every export can carry a **record ID**, and the server keeps the SHA-256
of the file's exact bytes under it with the server's time. A lawyer, a mediator, a court or the
other parent opens `web/verify/`, chooses the file, and learns whether it is byte for byte the file
that was registered, and when. AppClose's "certified records" rest on the vendor's affidavit; this
rests on arithmetic anybody can redo.

### What a match proves, and what it does not

A match proves that **these bytes were registered by one of the family's parents at the time
shown, and have not changed since** — not by the other parent, not in an email, not by a
well-meaning paralegal who re-saved the PDF. That is the adversary §3 cares about.

It does **not** prove that the file is a faithful rendering of the server's record. The file is
made on the exporting parent's phone, and a modified client could register bytes it made up; the
receipt would then vouch, truthfully, that *those* bytes have not changed since. Nor does it prove
completeness: the parent chose the period, and a record assembled without reaching the server says
`export_record_incomplete` on its face. Everything §8 refuses to promise stays refused. The
verification page says the narrower thing in its own words: "the fingerprint proves only that the
file has not changed since it was registered".

### The chicken and the egg: the ID must be inside the bytes it vouches for

A record ID printed on the file has to be covered by the hash, or it could be printed on any file.
So the ID exists **before** the file does, and the flow is fixed (`ExportViewModel`):

1. `reserveExportRecordId({familyId, fromDate, toDate, format})` mints the ID — 80 random bits as
   16 Crockford base-32 characters, printed `XXXX-XXXX-XXXX-XXXX`. No uid, family or date in it:
   it travels on a document and must say nothing by itself. The reservation binds the ID to the
   caller, the family, the range and the format, so a hash cannot later be re-labelled.
2. The phone renders the file with the ID and the verification address on its face.
3. It hashes exactly those bytes.
4. `registerExportReceipt({recordId, sha256, byteLength})` records the hash **once**. Only the
   reserving parent, only within an hour, never a second hash; a repeat of the *same* hash (a
   retry after a lost acknowledgement) answers with the original time.
5. The phone saves the same bytes it hashed.

The alternative — a client-generated ID registered afterwards — was rejected because it cannot know
*before rendering* whether the server is reachable, and the file has to say which it is. A
reservation is also that test.

**Offline, the file says so.** A phone that cannot reserve renders the file with
`export_verify_not_registered` under the statement and "not registered" in every PDF footer, and
the screen shows the same words in a dialog before the share sheet opens. A phone that reserved but
failed to register **renders the file again without the ID**: no file ever names an ID the server
holds no hash for. That is design item 8 at its sharpest — a verification promise that fails in
front of a judge is worse than no promise.

**The clock is the server's** (§5): `recordedAt` is the function's own `Timestamp.now()` at
registration, never a client value. The phone's clock appears only as the "Generated" line inside
the file, which the hash then freezes.

### What `verifyExport` tells somebody with no account — and why not more

`verifyExport` is callable **without signing in**: a lawyer has no account, and making them get one
would put a CoPlanly login between a court and a document. So everything it returns is chosen for a
stranger:

| Returned | Why |
| --- | --- |
| `found` | The question |
| `recordId` | So the verifier can see it is the ID printed on the file |
| `recordedAt` | *When* — the point of the exercise, and the server's clock |
| `fromDate`, `toDate`, `format`, `byteLength` | Describe the file registered, so a verifier can tell a near-miss (the right record, re-saved) from a stranger's file |
| `generatedBy: "one of the family's parents"` | Fixed words, not data |

| Never returned | Why not |
| --- | --- |
| The generator's **name** | Anybody holding only a record ID — a photo of one page — could learn who exported. The file already names both parents; the lookup must not add a name for someone who does not have the file. A name is also not stable: it can change after the export, and after erasure there is none |
| `generatorUid`, `familyId` | Account identifiers of real people, useful only for correlating one family's exports; a verifier needs neither |
| Whether the generator's account still exists | Would disclose an erasure to a stranger |
| The hash, on a lookup by ID | Nothing to compare it with; returning it would only help someone forge a claim about a file they do not have |

A reservation that never received a hash is "not found": it vouches for no file.

**Rate limit.** Per address, in memory, per function instance (30 lookups per ten minutes), with the
function deployed at `maxInstances: 10` — which is what turns a per-instance limit into a bound on
the service. No address is written anywhere. Best-effort by construction, and deliberately so: the
data behind it is designed to be harmless to a stranger, so the limit protects the bill, not a
secret. `reserveExportRecordId` is limited per account the same way.

**Rules.** `export_receipts` is closed to every client in both directions
(`firestore-tests/rules/export-receipts.test.js`). A parent who could write one could vouch for a
file the app never made; one who could read one would learn what `verifyExport` refuses to say.

### Account deletion: scrub, never delete

A receipt holds no content, but `generatorUid` is personal data, and so is a `familyId` that spells
two uids. Deleting the receipt would un-verify a file the *other* parent may already have filed —
erasing one parent would damage the other's evidence. So `deleteAccountDataImpl` blanks
`generatorUid` and `familyId` on the departing parent's registered receipts, blanks `familyId` on
the co-parent's receipts for every family it can still name (live partners, surviving threads, and
the families on the departing parent's own receipts), and deletes reservations that never received
a hash. What remains is a hash, a range, a format, a size and two times. Whether a hash of a document
that names people is itself personal data is arguable — whoever holds the document can link them —
and the retention then rests on Art. 17(3)(e) (the establishment, exercise or defence of legal
claims). **That is for the lawyer to confirm** (REL-4); the privacy policy and the deletion page
already say what is kept and why.

### Not built

- **A signature.** A receipt is a server-side registry, not a signed artefact; a verifier trusts
  CoPlanly's database the way they would trust a notary's register. Signing the hash with a
  published key would let a verifier check offline, and is the next step if a court ever asks.
- **A sweep of stale reservations.** A reservation older than an hour can no longer be registered
  and is never "found"; it is deleted with its account, and otherwise lingers harmlessly.
- **Registering a file after the fact.** An export made offline stays unregistered; the parent
  exports again online. Registering the earlier file later would print a server time that is not
  when it was made.
