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
| **Chat + announcements** | Already append-only. **Pinned in the rules tests** (`firestore-tests/rules/event-versions.test.js`, last block) and treated as a breaking change if it moves | The guarantee existed but nothing asserted it; a rule edit could have quietly widened `hasOnly(['isRead'])` and no test would have failed |
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
- **Not a signed or notarised artefact** unless someone has actually built the signing. OFW posts a
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
