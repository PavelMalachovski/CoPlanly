# Stickers between parents, and cancelling a subscription

Status: **§1 built** (October 2026, from the owner's answers of 23 October, §1.3); **§2 designed,
not built** (it waits for MON-11). Written on 22 October 2026 from the owner's answers to Zuzana's
test notes of 21 October (items 5, 6 and 8).

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

### 1.3 What the owner answered (23 October 2026)

| Question | Answer |
| --- | --- |
| The rule "never Mom or Dad" | An exception **for the sticker's own sentence only**; every other label stays the person's name |
| A third answer | **Dad, Mom or Parent** — "parent" words the sticker neutrally; "mandatory" means answered, not binary |
| Accounts that exist already | Asked **once, on Home, with no "Later"**; only an answer closes the question |
| Who sees a sticker | **The receiver**; the sender sees that it was delivered and seen. Not in the chat, not in the export |
| Where stickers live | A **card on Home** for a new sticker, and a **shelf** under Settings → Family → *My stickers* |
| Pushes | **Only for a sticker from the other parent**; the app's own stickers push nobody |
| Regularity | **Four weeks in a row** with every request answered before its deadline |
| Answering on time | A sticker **at most once a week** |
| Parent to parent | **A fixed set of phrases**, no free text (seven: great parent, thank you, the kids had a great time, a good team, well done, patience, flexibility) |
| Retention | **While the account and the family exist**: unpair deletes the family's stickers, account deletion every sticker naming the parent |

### 1.4 How it is built

- **The role.** `users/{uid}.parentRole`: `"dad" | "mom" | "parent"`, written by its owner alone
  (`firestore.rules`' `parentRoleValid` bounds the value; an older build's merge never touches the
  key). Firestore only, no Room column — the receiving phone reads its own parent's answer
  (`data/stickers/ParentRoles`). Home asks while the **server** says the field is missing
  (`ParentRoleAnswer.Unanswered`); a cached or failed read asks nothing, so the question never
  flashes up and vanishes. *My stickers* changes it.
- **The sticker.** `stickers/{id}`: `{to, from, participants, familyId, kind, createdAt}`, plus
  `seenAt` written once by the receiver. `from` is a uid or `"app"`. The rules
  (`stickerCreateValid`):
  - a parent's sticker names the other parent of a **live** family, one of the seven kinds,
    exactly those keys, and the server's time;
  - an app sticker names its own receiver, an app kind, a blank or own family, and an id that
    spells receiver, kind and ISO week (`app_<uid>_<kind>_<YYYY>-W<ww>`), so a second write of the
    same week is an update and refused — "once a week" is the rules', not the phone's;
  - read by `to` or `from` only, never updated except `seenAt`, never deleted by a client.
  `data/stickers/StickerDocument` is the wire form, `StickerStore` the reads and writes.
- **The app's awards.** `StickerAwarder.onAnswered(clockId)` runs after each answer the requests
  inbox writes (`ChangeRequestViewModel`: change requests, swaps, events, the custody proposal,
  the split): the request's clock (item 42) says who had to answer and by when, and an answer by
  this parent before `answerByMillis` gives the week's on-time sticker. Regularity
  (`StickerAwards.regular`) needs an on-time sticker in each of the four ISO weeks ending this one,
  no regularity sticker already in them, and no clock of the family waiting on this parent that
  ended "No answer" in them. A request with no clock — sent without a deadline, or by an older
  build — earns nothing.
- **The words.** The receiving phone words a sticker from its kind and its own role
  (`presentation/stickers/StickerText`), in five languages; the sender sees the neutral phrase,
  because it never reads the receiver's role. "Great parent", "on time" and "regularity" vary by
  role; the other phrases say the same to everybody.
- **The push.** `sticker_received` carries the sender's name only, in the four places of item 15,
  on the family channel, opening Home.
- **Cleanup.** `unpairCoParent` deletes the ended family's stickers; account deletion every sticker
  whose `participants` name the departing parent.
- **Families.** A phone shows the stickers of the family on screen and the app's that name no
  family or this one (item 46); a push from the other family switches to it on tap.

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
