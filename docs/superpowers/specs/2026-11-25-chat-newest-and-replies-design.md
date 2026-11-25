# Chat: the newest message always in view, and replies

Owner's request and approval, 25 November 2026. The canvas board is "Чат: всегда снизу, «↓» и ответ на сообщение" (`ChatReply.dc.html`).

## Why

Parents read the thread to answer it. Two things go wrong today:

- A thread does not reliably open on its newest message.
  - It jumps to the newest message once per screen life. Coming back to the Chat tab restores the old position.
  - While "Load earlier" is drawn, the jump lands one row short (the button's row is not counted). The newest message can then sit under the composer.
- A parent cannot say which message they answer. In a thread of short messages ("yes", "ok, 3"), that makes the record ambiguous both for the other parent and in the export a court may read.

## Goals

1. **Opening shows the newest message, just above the composer.** This holds in every case: entering the Chat tab, a push, coming back from another screen, after a rotation. The newest message stays in view when the keyboard opens and when the composer grows over several lines or shows a reply bar.
2. **New messages while reading history.** When the reader is scrolled up and a new message arrives, the list does not move. A "↓" button shows the number of new messages, and a tap brings the newest into view. The button also shows, without a count, whenever the reader is far from the newest. Sending one's own message always brings the newest into view.
3. **Starting a reply.**
   - Swipe a drawn message to the right. Past a threshold an arrow shows, the phone gives a light haptic, and letting go starts the reply.
   - Or long-press the message: a menu offers **Reply** and **Copy text**.
   - TalkBack and VoiceOver get **Reply** as a custom action on every message.
4. **The reply bar.** While a reply is chosen, a bar over the composer shows "Reply to {name}", the first line of that message, and ✕ to cancel. The chosen reply survives leaving the chat, the same way the draft does.
5. **A sent reply.** The bubble carries a quote over its text: a bar, the author's name, and the first line of the original.
   - Tapping the quote scrolls to the original and highlights it, as a search result is highlighted (MON-15). It widens the loaded window if the original is older.
   - When the original is not in this phone's copy of the thread, the quote reads "Message unavailable".
6. **The export.**
   - PDF: a reply's entry starts with "In reply to {name}'s message of {time}: "{excerpt}"".
   - CSV: a new column, "In reply to", holds the same text.

## Non-goals

- Editing or deleting messages. Messages stay immutable (`firestore.rules`, item 25's pin).
- Threads within the thread, or more than one quoted message.
- Replying to activity cards. The thread does not draw them (design item 8), so there is nothing to swipe.
- Any change to pushes. A reply's push is an ordinary message's push.

## Constraints

- **No schema or wire change.** `Message.replyToMessageId` already exists in the model, in Room and in the Firestore document (`ChatMappers`). The messages `create` rule does not restrict keys. An older build carries the field and draws the reply as a plain message, which is acceptable.
- A reply may only name a message of the **same conversation**. The client sets the field only from a message it drew in that thread. A reader that finds a foreign or unknown id draws "Message unavailable".
- Every new string goes in all five locales (the Localization rules).
- Every message is drawn through the existing components, with no new bubble anatomy:
  - Android uses Material's `DropdownMenu`.
  - The iPhone uses the system's menu (`NativeControls.ActionSheet`, or a member added for it).
- Design item 14 still applies: insets are applied once, at the NavHost. The reversed list must not add its own IME padding.

## Approaches (owner chose A)

- **A. A reversed list (chosen).**
  - `LazyColumn(reverseLayout = true)`, the entries ordered newest-first, so item 0 is the newest message, anchored above the composer.
  - Opening, a new arrival while at the bottom, the keyboard and a growing composer all keep the bottom in view with no scroll code.
  - Replaces `ChatScrollPolicy` and `FollowNewestWhileKeyboardOpens`.
  - Costs more change: "Load earlier", search reveal and the tests move to the new order.
- **B. Patch today's scroll.** Fix the off-by-one and jump again on every entry and on send. Less change, but every new case (the reply bar, a multi-line composer) needs its own scroll fix, and this approach is where the defect came from.

## Decided by the owner

- New messages while reading history: the "↓" button with a count. Own send scrolls down.
- Starting a reply: swipe right and a long-press menu.
- The look: a quote in the bubble, which jumps to the original when tapped.
- The export: a reply reference in the PDF and a CSV column.

## Calls made here (tell me if any is wrong)

- **Messages a reply may answer.** Any message the thread draws: the parents' text, files and stickers.
- **The quote's text.**
  - A text message: its first line, cut to one line with an ellipsis.
  - A file: its name.
  - A sticker: the sticker's phrase.
- **When the "↓" button shows.** The list is more than a few messages (`NEAR_NEWEST` items) away from the newest. The count is the messages that arrived since the reader left the bottom, and it resets when they reach it.
- **The long-press menu.** It is the only long-press action on a bubble. "Copy text" goes through the platform clipboard (`SensitiveClipboard`, so the copy is not offered to other devices). It is absent for a file or a sticker.
- **The swipe threshold.** 56 dp, with the haptic at the threshold. The swipe is horizontal only and never moves the list.
- **The chosen reply is stored beside the draft.** It uses `SecurePreferences`, keyed per conversation, and is cleared on send or ✕.
- **Mark-read.** Unchanged: a message is marked read only while the screen is visible (`LocalScreenVisible`).
- **The export excerpt.** The first 80 characters of the original's text, its line breaks collapsed to spaces (or the file name), and the original's time in the record's zone. A reply whose original is not in the exporting phone's data prints "In reply to an earlier message not in this record".

## What is left to the builder

- Exact composable boundaries, the dp values on the 4 dp grid, the reverse-order index math for "load earlier" and search reveal, and test names.
