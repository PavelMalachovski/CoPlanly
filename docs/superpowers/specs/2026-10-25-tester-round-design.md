# Tester round, 25 October 2026: isolated families, money states, alimony, per-family slots

The owner installed the build from PR #148 and reported five things, plus one new feature. The
owner answered the open questions on 25 October 2026. Each answer is recorded below as an owner
decision.

1. **Stickers are not visible in the chat.** Owner decision: a sent sticker also appears as a
   card in the pair's thread. The Family tab keeps the full list.
2. **Two parents who both already have other families cannot link.** Owner decision: the parent
   slot becomes per family. It is done now, in this PR.
3. **Does a document in the vault stay with the other parent?** Owner decision: yes. After an
   unpair, both parents keep read access, in the archive of the earlier family.
4. **An ended family's expenses showed in the next family.** Owner decision: full isolation. An
   ended family's records are hidden from every live screen. They are reachable only under
   Family → Earlier families, read-only, for reading and export.
5. **Settle up should say that the month is settled.** Owner decision: the payer marks "I paid",
   and the receiver confirms "Money arrived". The month then reads "Settled" on both phones.
6. **Alimony (child support).** Owner decision:
   - a fixed amount once a month, with a currency, a day of the month and the payer;
   - each month is paid, waiting for confirmation or unpaid, with the same two steps as item 5;
   - an optional reminder to the payer, a chosen number of days before the day;
   - separate from expenses, never in their balance;
   - a change of amount is proposed by one parent and accepted by the other.

All of it ships in one PR (owner decision).

## A. Earlier families

### Records

- **What changes.** `FamilyView.shows` admits only `ON_SCREEN` and `ONLY_ME`. `EARLIER_FAMILY`
  joins `OTHER_FAMILY` as "not drawn". That hides an ended family's events, expenses, children and
  pets from every screen that reads the repositories. The journal follows the same rule.
- **What does not change.** Room keeps every row. Nothing is deleted, and nothing is re-stamped
  into another family (CLAUDE.md item 18).
- **Unfiltered reads.** The repositories gain `…InFamily(familyId)` reads. The archive and the
  export use them. They read one named family, whether live or ended.

### Which families ended

- **Where the list comes from.** A family ended when a familyId in Room names this uid and is not
  one of the live families. Room is the evidence, as for `FamilyIdBackfill.hasEarlierFamily`.
- **New table, `known_families` (schema 46).** It holds one row per family this phone has seen:
  `familyId`, `coParentUid`, `coParentName`, `mySlot`, `coParentSlot`, `updatedAtMillis`.
- **How the table is filled.** It is written while the family is live:
  - the name, from the co-parent's profile listener (`CoParentOnScreen`);
  - the slots, from `families/{id}` (part E).
- **What the archive needs from it.** It names an ended family by the co-parent's name, whose
  profile the parent can no longer read. If a family ended before this build, its row has no name,
  and the archive shows "Earlier family" and the end date of its records.
- **Clearing.** The table is cleared by `clearAllTables`, like every table.

### Archive screen

- **Entry.** Family → Earlier families, as a row shown only while at least one family ended.
- **What it shows.** One section per ended family:
  - its events (a list, not a grid);
  - its expenses, with the totals of that family alone;
  - its children and pets;
  - its documents;
  - Export.
- **Read-only.** Nothing in the archive can be edited, added or answered.

### Vault after an unpair

- **Server.**
  - `unpairCoParent` no longer narrows `family_documents`. The list
    `SHARED_AUDIENCE_COLLECTIONS` loses it.
  - Account deletion keeps scrubbing it through its own list, so a deleted account still loses
    access.
  - The Storage rule already lets both parents of the path read, so nothing changes there.
- **Rules.** A `family_documents` create now needs a live family (`isLiveFamilyMember`), so
  nobody files into an ended family.
- **Client.** The archive reads the ended family's vault through the existing
  `FamilyDocumentRepository.observe(familyId)`, which already accepts any family the uid is in. It
  does so without the Add action.
- **Already narrowed.** Documents narrowed by an unpair before this deploy are not restored: the
  server cannot tell an unpair's narrowing from anything else. This is a recorded cost.

### Export of an ended family

- **How it is opened.** `ExportViewModel` takes a `family` argument (`export?family=`).
- **What it reads.** The counterpart is the ex's uid, which is the familyId's other half, and the
  name comes from `known_families`. Events and expenses come from the unfiltered reads, scoped by
  `RecordScope.families = {familyId}`. Messages come from the conversation, which the rules keep.
- **Parenting plan.** The plan is printed only if the server still answers. Otherwise the record
  says the plan could not be read.

## B. Sticker card

- **The card.** `StickerStore.send` posts an `ActivityAnnouncement` after the sticker document is
  written:
  - kind `STICKER_SENT`;
  - entity `STICKER`;
  - entityId: the sticker id;
  - title: the kind's wire value.
- **Wording.** The reader's phone words it through `StickerText`:
  - the receiver reads the sentence worded from their own role ("You are a great dad");
  - the sender reads "You sent: …" in the neutral phrase.

  The fallback text "Sent a sticker" is what an older build and the export would print.
- **Push.** `functions/index.js` adds `STICKER_SENT` to `SILENT_CARD_KINDS`, because the sticker
  already has its push, `sticker_received`. Until that deploy, a sticker pushes twice. This is a
  recorded cost.
- **Export.** The export leaves sticker cards out, because the record is meant to be neutral
  (ruling, below).
- **Tapping the card** opens nothing.
- **CLAUDE.md item 47** changes from "not in the chat" to "a card in the chat, never in the
  export".

## C. Payments: "I paid" and "Money arrived"

### The collection

- **Document.** One new top-level collection, `payments/{id}`, holds a single payment between the
  two parents of a family. Its id is `{familyId}__{kind}__{yyyy-MM}__{currency}`, where `kind` is
  `expenses` (a settle-up) or `support` (alimony, part D). The fields are:
  - `familyId`, `participants` (sorted), `kind`, `month`, `currency`;
  - `amount` (a plain two-decimal string), `payerUid`, `payeeUid`;
  - `paidAt` and `paidBy` (the payer);
  - `confirmedAt` and `confirmedBy` (the payee).
- **Rules:**
  - read is for the two participants;
  - create is by the payer, in a live family, with `paidAt == request.time`;
  - the one update is the payee's confirmation, which writes only `confirmedAt == request.time`
    and `confirmedBy == request.auth.uid`;
  - nothing is deleted. Unpair leaves the document as part of the ended family's history, and
    account deletion deletes the departing uid's.
- **No Room table.** It is a Firestore listener per family, the way requests are, so no outbox
  is needed. A write that fails says so on screen.
- **Retention.** Payments are kept for as long as the family's records, and deleted with the
  account. This is added to the privacy policy table.

### Settle-up states

- **When the payer taps "I paid".** The button sits under the settle-up card (the payer's phone
  only) and on the month's header. Tapping it:
  - writes the payment with the amount the card asked;
  - posts `SETTLE_UP_PAID`.
- **When the payee taps "Money arrived".** The button sits on the card and on the header.
  Tapping it:
  - confirms the payment;
  - posts `SETTLE_UP_CONFIRMED`.
- **Balance.** A confirmed payment offsets the month's balance as a transfer from payer to payee.
  So "Settled" is derived (net within `SETTLED_EPSILON`), and an expense added later shows the
  remainder.
- **Header.** A paid, unconfirmed payment shows "Paid, waiting for confirmation" on the header of
  both phones. A confirmed payment that leaves nothing owed shows "Settled".
- **Pushes.** Both cards are ordinary chat cards and push as `chat_message`. No new push type is
  added.

## D. Alimony

### Agreement

- **Document.** `support_agreements/{familyId}` holds the agreement and, at most, one proposal,
  mirroring `family_settings`:
  - `agreement`: `{amount, currency, dayOfMonth (1–28), payerUid}`, or absent;
  - `proposal`: `{amount, currency, dayOfMonth, payerUid, proposedBy, proposedAtMillis}`, or
    absent;
  - `participants`, `lastModifiedBy`.
- **Rules.**
  - A participant may write a proposal of their own.
  - The other parent may accept it. That copies the proposal into the agreement, field for field,
    and clears it.
  - Either parent may decline, and the proposer may withdraw. Both clear the proposal.
  - The first agreement is a proposal too, because money is never set for somebody else.
  - Unpair deletes the document, as it deletes `family_settings`.
  - Its keys are bounded with `hasOnly`.

### Screen

- **Entry.** Family → Money → Alimony.
- **What it shows:**
  - the agreement;
  - the waiting proposal, with Accept and Decline, or Withdraw for the proposer;
  - "Propose a change";
  - the last twelve months, each paid, waiting for confirmation or unpaid, from `payments` of kind
    `support`.
- **Who taps what.** The payer taps "I paid" on the current or an earlier month, and the payee
  taps "Money arrived".
- **Months shown.** A month earlier than the agreement's first month is not listed.

### Cards

- **Kinds.** `SUPPORT_PROPOSED`, `SUPPORT_ACCEPTED`, `SUPPORT_DECLINED`, `SUPPORT_WITHDRAWN`,
  `SUPPORT_PAID` and `SUPPORT_CONFIRMED`.
- **Pushes.** They are ordinary chat cards, which push as `chat_message`.
- **Tapping a card** opens the Alimony screen.

### Reminder

- **The setting.** The reminder lives on the Alimony screen and on this phone only. It has a
  switch and a number of days (0, 1, 3 or 7). It is stored in `EncryptedPreferences` per uid and
  family, and is cleared at sign-out.
- **The job.** A unique periodic job, `support_reminder_daily` (daily, `KEEP`), checks, for each
  family where this uid is the payer and the reminder is on, whether today is the reminder day or
  later. If this month has no payment, it posts a local notification on the money channel. It
  posts at most once per month and family, and nothing in the quiet hours (`ReminderPlan.isQuietAt`).
- **The tap** opens the Alimony screen of that family. The notification is worded on the phone,
  so no push type is added.

## E. Per-family parent slots

### The model

- **Where the truth lives.** The slot of each parent lives in `families/{id}.slots`, which
  `acceptPairingInvitation` already writes. From now on it is the truth.
- **What `users/{uid}.role` becomes.** It stays as the mirror older builds read. It is this
  parent's slot in their first family, and is never moved by a later family.

### Server

`slotsForPairing` no longer throws `slot-conflict`. When the accepter co-parents elsewhere and
the inviter co-parents elsewhere in the same slot, the new family takes:

- the inviter's slot for the inviter;
- the other slot for the accepter;
- `users.role` for both left as it is.

That family's `slots` therefore differs from one profile's `role`. The preview runs the same
function, so it stops refusing too. The client keeps the `SlotConflict` wording for an older
server.

### Client

- **Slot source.** A per-family slot source, `FamilySlots`:
  - listens to `families/{familyOnScreen}` (members may `get`);
  - writes the two slots into `known_families`;
  - answers `slotIn(familyId, uid)`, falling back to `users.role` for a family whose document was
    never read.
- **Readers.** These read it instead of `UserEntity.role`, for the family on screen or for a
  record's own family:
  - `ParentsSource` (`me.slot`, `coParent.slot`) and `signedInSlot()`;
  - `EventRepositoryImpl`'s creator slot;
  - `ActivityAnnouncer`;
  - `ExpenseRepositoryImpl` (the co-parent's slot, no longer the complement of mine);
  - `FamilySettingsRepository`, `SchoolImporter`, `CalendarSyncRepository`, `EventViewModel`'s
    pickup, `PairingViewModel` and the widget.

  The calendar feed and the professional grant already read `families.slots`.
- **Re-stamping.** It stays as it is, keyed on `users.role` changes. A family whose slots differ
  from `role` was never re-stamped, because its records are created in it with its own slot. A
  record of no family keeps `role`, which is right for the first family it is stamped into.
- **Older builds.** An older build of either parent in a family whose slots differ from `role`
  draws that family with swapped slots: colour, whose day and the owner of new events. That
  family could not exist before this build, so no existing family changes. The release note asks
  testers to update both phones. This is a recorded cost.

## Rulings made without the owner (each with what it costs if wrong)

- **Sticker cards are left out of the export.** The record is meant to be neutral. If wrong, the
  export lacks warm messages a court would not have weighed anyway.
- **Payments have no Room copy.** Offline, the money state is unknown and the buttons say the
  server cannot be reached. If wrong, a parent who is offline sees no "Settled" until they are
  back online.
- **A confirmed payment offsets the balance; it does not freeze the month.** A later expense
  shows what remains. If wrong, a month the parents considered closed reopens with a small
  remainder, which is true.
- **Documents already narrowed by an earlier unpair are not restored.** If wrong, they can be
  restored once by an operator run, which is not written.
- **The alimony reminder works on this phone only.** If wrong, the payer sets it again on a new
  phone.
