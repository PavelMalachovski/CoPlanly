# Stickers between parents, and cancelling a subscription

Status: **design, not built.** Written on 22 October 2026 from the owner's answers to Zuzana's
test notes of 21 October (items 5, 6 and 8). Nothing here is implemented in the pull request that
adds this file; it records what was decided, what it collides with, and what has to be decided
before anyone builds it.

## 1. Stickers: "you are a great dad / mom" (items 6 and 8)

### 1.1 What the owner decided

A parent can receive a small sticker — a drawn badge with a sentence such as "You are a great
dad" — as recognition for how they co-parent. Four occasions, all chosen by the owner:

| Occasion | Who gives it | When |
| --- | --- | --- |
| **Answering on time** | the app | a request (swap, change request, event, proposal, split) answered before its deadline (item 42's clock) |
| **Regularity** | the app | a run of weeks with every request answered and the agreed handovers kept |
| **Support without a reason** | the other parent | any time, from the chat or the parent's card on Home |
| **Parent to parent** | the other parent | in reply to something specific — an answered request, a settled month |

**Gender becomes a mandatory field** of a parent's profile (owner decision), so the sticker can say
"dad" or "mom".

### 1.2 What this collides with

1. **The hard rule "the app never shows the words Mom or Dad"** (CLAUDE.md, "Parent colours
   identify a person, not a role"). Every parent label goes through `ParentLabels.kt` and renders
   a name. A gendered sticker is the first place the app would say "mom" or "dad" about a person.
   The owner's decision makes stickers an **exception to that rule, limited to the sticker's own
   sentence**; names stay names everywhere else. CLAUDE.md must say so in the same pull request
   that builds the feature, or the next reader will "fix" it.
2. **`"mom"` and `"dad"` are slot identifiers, not genders.** Slot 1 is not "the mother". A sticker
   must read the new gender field, **never** the slot — two fathers hold `"mom"` and `"dad"` slots
   between them, and reading the slot would call one of them "mom".
3. **A mandatory field on an existing account.** Every account created before the field exists
   has no answer. The app must ask once (like the telemetry question, which keeps an `UNANSWERED`
   state on purpose) and must not guess — never from the name, never from the slot, never from the
   colour.
4. **Personal data.** Gender is personal data (not special-category under GDPR Art. 9, but it is
   about a person). It needs a line in `PRIVACY-POLICY.md`, `RECORDS-OF-PROCESSING.md` and
   `DATA-SAFETY.md` in the same pull request (Legal item 1), a purpose ("to word the stickers you
   receive"), and who can read it: the co-parent reads `users/{uid}`, so the field is visible to
   them.
5. **Tone in a separated family.** An automatic "great parent" badge can read as the app taking a
   side, or as sarcasm after a dispute. The app-given occasions must be quiet (no push), private to
   the receiver unless they share it, and never counted against the other parent.

### 1.3 Open questions for the owner

- **A third answer.** "Mandatory" with only "male" and "female" leaves out a parent who is neither,
  and a parent who prefers not to say. Proposal: three answers — *dad*, *mom*, *parent* — where
  *parent* words the sticker neutrally ("You are a great parent"). Mandatory then means "answered",
  not "binary".
- **Who sees a sticker.** Only the receiver, or both parents? Proposal: the receiver; the giver
  sees that it was delivered.
- **Where stickers live.** A small shelf on the parent's own profile card, or cards in the chat?
  A chat card is a record both parents and an export read (MON-3); a sticker is probably not
  evidence. Proposal: not in the chat, not in the export.
- **The automatic thresholds.** "On time" per request is easy (the clock says it). "Regularity"
  needs a definition — e.g. four weeks with every request answered before its deadline.
- **Pushes.** A parent-to-parent sticker could push ("Zuzana sent you a sticker"); an automatic one
  should not. A push type then needs the four places of item 15.

### 1.4 Shape when built (sketch)

- `users/{uid}.parentRole`: `"dad" | "mom" | "parent"`, written by the owner only; the rules bound
  the value. Room: one nullable column on `users` (a schema bump and a migration).
- `stickers/{id}`: `{to, from ("app" or a uid), familyId, kind, reason, createdAt}`, readable by
  `to` and by `from` when it is a uid, created by a parent only for the other parent of a live
  family, never updated, deleted with either account. A sweep after a retention period the
  privacy table names.
- The sentence is worded on the receiving phone from its own strings (as pushes are, SEC-3), in
  five languages, from `kind` and the receiver's `parentRole`.

## 2. Cancelling a paid subscription (item 5)

### 2.1 Where it stands

There is no billing yet. MON-1 decided the price (per family, 129 CZK a month or 1,190 CZK a year,
14-day trial from pairing); MON-11 (payments) is not built. Zuzana's note asks that cancelling be
as easy as subscribing — which Google Play's policy requires anyway.

### 2.2 What the owner decided

Cancelling is about the **future paid subscription**, and it goes in this design so MON-11 starts
from it.

### 2.3 Design for MON-11

- **Play is where a subscription is cancelled.** The app does not cancel it itself; Settings →
  Account → **Subscription** shows the plan, who pays, and the renewal date, and a **Manage or
  cancel** row that opens Play's subscription page for this app
  (`https://play.google.com/store/account/subscriptions?sku=<product>&package=app.coplanly`).
- **Per family means one payer.** The parent who subscribed is the only one who can cancel. The
  co-parent sees "Paid by Zuzana until 1 December" and no cancel row, and is told when it will end.
- **Cancelling keeps access to the end of the paid period**, then the family falls back to the
  free tier. Nothing is deleted: a court record (MON-3, MON-4) never depends on a subscription.
- **Unpairing or deleting the account** does not cancel a Play subscription — Play bills the Google
  account. Both screens say so and link to the same Play page.
- **Device check:** subscribe and cancel with a licence-test account, on two phones.

### 2.4 Open questions

- Does the co-parent get a push when the payer cancels? Proposal: a quiet card on Home, no push.
- Can the co-parent take over the subscription before it lapses? (A second purchase by the other
  Google account; the server then names them the payer.)
