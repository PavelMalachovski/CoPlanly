# Chat: the newest message in view, and replies — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The chat thread always opens on its newest message, above the composer. Parents can reply to a specific message, and the reply is quoted in the thread and in the export.

**Architecture:**
- The thread becomes a reversed `LazyColumn`: item 0 is the newest message, so the bottom stays anchored. A pure counter drives a "↓" button.
- A reply is the existing `Message.replyToMessageId`. A pure `ReplyQuote` decides what a quote shows.
- The ViewModel holds the chosen reply beside the draft. The bubble gains a swipe, a long-press menu and the quote.
- The export resolves the reply from the messages it already reads.

**Tech Stack:** Kotlin Multiplatform; Compose Multiplatform 1.9.3 in `:shared-ui`; Koin; kotlinx-datetime; JUnit, Robolectric and MockK on the JVM; kotlin.test in commonTest.

**Spec:** `docs/superpowers/specs/2026-11-25-chat-newest-and-replies-design.md`. The design board is "Чат: всегда снизу, «↓» и ответ на сообщение" on the canvas.

## Global Constraints

- No Room schema change, no Firestore field change and no `firestore.rules` change: `replyToMessageId` already exists end to end.
- A reply names only a message of the same conversation. A foreign or unknown id draws "Message unavailable".
- Every new user-facing string goes in all five locales (`values`, `-cs`, `-de`, `-ru`, `-uk`, under `shared-ui/src/commonMain/composeResources/`). Write an apostrophe as `'`, use no `%s`, and use `%1$s` and `%1$d` arguments.
- Icons go through `AppIcon`. A new icon needs a row in `SF_SYMBOLS` or a keep-Material reason (`SfSymbolTableTest`).
- Insets are applied once, at the NavHost (design item 14). Add no `imePadding()` in the thread.
- Messages are immutable. Nothing edits or deletes a message.
- detekt orders imports ASCII-lexicographically. A backticked commonTest name has no comma and no parentheses.
- Verify with JDK 17: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`.

## Review Focus

1. **A held message (Pause before sending) keeps its reply.** Undo gives the text back *and* the reply bar. The test goes in Task 3.
2. **Switching family or conversation clears a chosen reply that belongs to the other thread.** A reply must never be sent into another conversation. The test goes in Task 3.
3. **A day header sits above its day's first message in the reversed list.** The test goes in Task 4: `BuildThreadTest`, reversed order.
4. **The swipe only moves horizontally.** A vertical scroll that starts on a bubble still scrolls, and a swipe short of 56 dp starts no reply. The test goes in Task 5 (Robolectric gesture test).
5. **A quote whose original lies outside the loaded window.** Tapping it widens the window and then reveals the original; the quote reads the original's text from Room, not from the window. Tests go in Task 1 (resolution) and Task 5 (the tap calls `onQuoteClick` with the id).

---

### Task 1: What a quote shows (`ReplyQuote`)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/coparently/app/domain/chat/ReplyQuote.kt`
- Test: `shared/src/commonTest/kotlin/com/coparently/app/domain/chat/ReplyQuoteTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  sealed interface ReplyQuote {
      data class Shown(val messageId: String, val authorName: String, val excerpt: ReplyExcerpt) : ReplyQuote
      data object Unavailable : ReplyQuote
      companion object { fun of(replyToId: String?, original: Message?, conversationId: String): ReplyQuote? }
  }
  sealed interface ReplyExcerpt {
      data class Text(val line: String) : ReplyExcerpt
      data class File(val name: String) : ReplyExcerpt
      data object Sticker : ReplyExcerpt
  }
  ```
  `of` returns null when `replyToId` is null.
- Also produces `fun ReplyExcerpt.plain(max: Int): String`, used by the export, which needs no UI words. For a `Sticker` it returns `""`; the export prints its own word.

- [ ] **Step 1: Write the failing tests**
  - `no reply id gives no quote`: `of(null, …)` is null.
  - `an unknown original is unavailable`: `of("x", null, "c")` is `Unavailable`.
  - `an original of another conversation is unavailable`: the original's `conversationId` is `"other"`; expect `Unavailable`.
  - `a text original quotes its first line`: content `"Can you take her\non Friday?"`; expect `Text("Can you take her")`.
  - `a file original quotes its name`: the attachment is an `att1|…|Trip-consent-form.pdf` codec string and the content is blank; expect `File("Trip-consent-form.pdf")`.
  - `a sticker card quotes a sticker`: a message whose activity is `STICKER_SENT`; expect `Sticker`.
  - `plain cuts at max with an ellipsis`: `Text("a".repeat(100)).plain(80)` has length 81 and ends in `"…"`.
- [ ] **Step 2: Run the tests and see them fail**: `./gradlew :shared:testAndroidHostTest --tests '*ReplyQuoteTest*'`. Expected: FAIL, unresolved `ReplyQuote`.
- [ ] **Step 3: Implement** `ReplyQuote.of` and `plain` in `ReplyQuote.kt`.
  - Read the file name with `ChatAttachmentCodec`, as `RecordFormat.messageText` does.
  - Detect a sticker with the same activity check as `isShownInThread` (`ThreadMessages.kt`).
- [ ] **Step 4: Run the tests and see them pass.**
- [ ] **Step 5: Commit**: `feat(chat): ReplyQuote, what a reply's quote shows`.

### Task 2: The reply in the export

**Files:**
- Modify:
  - `shared/src/commonMain/kotlin/com/coparently/app/domain/export/CommunicationRecordBuilder.kt` (`MessageInput`, `messages()`)
  - `CommunicationRecord.kt` (`RecordMessage`)
  - `CommunicationRecordCsv.kt` (columns and `messageRow`)
  - `RecordLayout.kt` (message block)
  - `RecordFormat.kt`
  - the labels type (wherever `RecordLabels`/`columns` live)
  - `shared-data/src/commonMain/kotlin/com/coparently/app/data/export/CommunicationRecordSource.kt` (pass `replyToMessageId`)
  - the strings for the export's labels (where `export_*` live), in all five locales
- Test: the existing builder, CSV and layout tests in `shared/src/commonTest/kotlin/com/coparently/app/domain/export/`

**Interfaces:**
- Consumes: `ReplyExcerpt.plain(max)` (Task 1).
- Produces:
  - `MessageInput.replyToMessageId: String? = null`
  - `RecordMessage.replyTo: RecordReply? = null`
  - `data class RecordReply(val authorName: String, val sentAtMillis: Long, val excerpt: String)`, where `excerpt` holds at most 80 characters and an ellipsis.
  - A new label `inReplyTo`: the localised template "In reply to %1$s’s message of %2$s: “%3$s”" (key `export_in_reply_to`, five locales). The sticker word is key `export_reply_sticker`, "a sticker".
  - A CSV column label with key `export_col_in_reply_to`, "In reply to", appended as the **last** column.

- [ ] **Step 1: Write the failing tests**
  - Builder, `a reply names its original's author, time and excerpt`: two inputs, B replying to A. Expect `RecordReply(authorName = A's name, sentAtMillis = A.sentAtMillis, excerpt = A's first 80 characters plus "…")`.
  - Builder, `a reply to a message before the period still resolves`: A falls before `from`, B inside. Expect B's `replyTo` filled, A absent from the messages.
  - Builder, `a reply to an unknown message has no reference`: expect `replyTo == null`.
  - CSV, `the in-reply-to column is last and filled only for a reply`: the header's last cell is the label, every row has the same width, and only B's last cell holds the formatted text.
  - Layout: B's block contains the in-reply-to line before the message text.
- [ ] **Step 2: Run** `./gradlew :shared:testAndroidHostTest --tests '*CommunicationRecord*' --tests '*RecordLayout*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - Resolve from the **unfiltered** input list (a map by id) before the window filter.
  - The source passes `message.replyToMessageId`.
  - The CSV pads every non-message row with `""` for the new column, keeping "one width for every record".
  - The layout prints the line in the message block in the secondary style.
- [ ] **Step 4: Run** the step 2 command and `node tools/check-invariants.js`. Expected: PASS and "All invariants hold".
- [ ] **Step 5: Commit**: `feat(export): a reply names the message it answers`.

### Task 3: The chosen reply in `ChatViewModel`

**Files:**
- Modify:
  - `shared-data/src/commonMain/kotlin/com/coparently/app/data/platform/SecurePreferences.kt`: add `putChatReplyDraft(conversationId: String, messageId: String)` and `getChatReplyDraft(conversationId: String): String`, where `""` means none.
  - `SealedSecurePreferences.kt`
  - `app/src/main/java/com/coparently/app/data/local/preferences/EncryptedPreferences.kt`: the key is `chat_reply_draft_<conversationId>`, beside `chat_draft_`, and is cleared with the drafts.
  - `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/chat/ChatViewModel.kt`
  - `SendHold.kt` (restore)
- Test: `shared-ui/src/androidHostTest/kotlin/com/coparently/app/presentation/chat/ChatViewModel*Test.kt`, plus both `SecurePreferences` contract tests (`SecurePreferencesContractTest`, `EncryptedPreferencesContractTest`).

**Interfaces:**
- Consumes: `ReplyQuote` (Task 1) and `MessageRepository` (it reads the original by id; use the existing by-id read, or add `suspend fun getMessageById(id: String): Message?` to the repository and DAO if there is none).
- Produces, on `ChatViewModel`:
  - `val replyTarget: StateFlow<ReplyQuote.Shown?>`
  - `fun startReply(message: Message)`
  - `fun cancelReply()`
  - `fun quoteFor(message: Message): Flow<ReplyQuote?>`, the quote a drawn bubble shows, read from Room by id.
  - `sendMessage(...)` stamps `replyToMessageId` from `replyTarget` and clears it.
  - `undoHeldSend` gives the reply back with the text.

- [ ] **Step 1: Write the failing tests**
  - `startReply then send stamps the reply and clears it`: the sent `Message.replyToMessageId == original.id`, and `replyTarget` is null afterwards.
  - `a reply survives reopening the conversation`: the reply is chosen, a new ViewModel is built over the same preferences, and `replyTarget` is restored.
  - `opening another conversation drops a reply that is not its own`: Review Focus 2.
  - `undoing a held send restores the reply`: Review Focus 1.
  - The contract tests: `a reply draft round-trips and clears`.
- [ ] **Step 2: Run** `./gradlew :shared-ui:testAndroidHostTest --tests '*ChatViewModel*' :shared-data:testAndroidHostTest --tests '*SecurePreferences*' :app:testDebugUnitTest --tests '*EncryptedPreferencesContract*'`. Expected: FAIL.
- [ ] **Step 3: Implement.** The reply is keyed per conversation, like `drafts`. It is written immediately, since it is not typed.
- [ ] **Step 4: Run** the step 2 command. Expected: PASS.
- [ ] **Step 5: Commit**: `feat(chat): the chosen reply, kept beside the draft`.

### Task 4: The reversed thread and the "↓" button

**Files:**
- Create: `shared/src/commonMain/kotlin/com/coparently/app/domain/chat/NewestButton.kt`, a pure counter.
- Modify: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/chat/MessagesList.kt`.
- Delete: `shared/src/commonMain/kotlin/com/coparently/app/domain/chat/ChatScrollPolicy.kt` and its test. Also delete `FollowNewestWhileKeyboardOpens` and the `initialJumpDone` logic.
- Test: `shared/src/commonTest/kotlin/com/coparently/app/domain/chat/NewestButtonTest.kt`, `shared-ui/src/commonTest/kotlin/com/coparently/app/presentation/chat/BuildThreadTest.kt`, and a Robolectric `app/src/test/java/com/coparently/app/presentation/chat/MessagesListTest.kt`.

**Interfaces:**
- Produces:
  - `object NewestButton { fun shows(firstVisibleIndex: Int): Boolean; fun unseen(newestIdWhenLeft: String?, messagesNewestFirst: List<String>): Int }`. It shows when `firstVisibleIndex > NEAR_NEWEST`, where `NEAR_NEWEST = 3`. `unseen` counts the ids before `newestIdWhenLeft`, which is 0 when that id is null or newest.
  - `internal fun threadNewestFirst(messages: List<Message>): List<ThreadEntry>`: `buildThread(...)` reversed. A day header follows (sits above) its day's oldest message.
  - `MessagesList` gains `onQuoteClick: (String) -> Unit` and `onReply: (Message) -> Unit`, used in Task 5. Keep its other parameters.
- Behaviour:
  - `reverseLayout = true`, item 0 is the newest bubble.
  - "Load earlier" is the **last** item, so it is drawn on top.
  - Search reveal maps an entry index straight to the reversed index.
  - When the newest message is the reader's own and new, `animateScrollToItem(0)`.
  - The "↓" FAB (`SmallFloatingActionButton` over the list, bottom end, `Spacing.L`) shows when `NewestButton.shows`, with a `Badge` holding `unseen` when it is above 0. A tap runs `animateScrollToItem(0)`. Its description is `chat_scroll_to_newest`, "Newest messages".

- [ ] **Step 1: Write the failing tests**
  - `NewestButtonTest`: `hidden near the newest`, `shown far up`, `counts messages after the one left at`, `zero when the reader left at the newest`.
  - `BuildThreadTest`, `newest first puts each day header above its oldest message`: Review Focus 3.
  - `MessagesListTest` (Robolectric):
    - `opens with the newest message displayed` on 60 messages, with `canLoadEarlier = true`: the newest message's text node is displayed. This guards the old off-by-one.
    - `scrolled up the newest button shows and taps back`.
- [ ] **Step 2: Run** `./gradlew :shared:testAndroidHostTest --tests '*NewestButton*' :shared-ui:testAndroidHostTest --tests '*BuildThread*' :app:testDebugUnitTest --tests '*MessagesListTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement**, then delete `ChatScrollPolicy` and `FollowNewestWhileKeyboardOpens`. Add `chat_scroll_to_newest` in five locales. Add `chat_new_messages` as a plural, "%1$d new messages", used for the badge's description.
- [ ] **Step 4: Run** the step 2 command and `./gradlew :shared-ui:compileKotlinIosSimulatorArm64`. Expected: PASS.
- [ ] **Step 5: Commit**: `feat(chat): the thread opens on its newest message, with a "newest" button`.

### Task 5: Reply from a bubble, the quote and the reply bar

**Files:**
- Create: `shared-ui/src/commonMain/kotlin/com/coparently/app/presentation/chat/ReplyViews.kt`, holding `ReplyQuoteBlock` (in a bubble), `ReplyBar` (over the composer) and `Modifier.swipeToReply`.
- Modify: `MessagesList.kt` (the bubble wraps its content and gets `combinedClickable` for the long press), `ChatScreen.kt` (`ReplyBar` above `MessageInput`, and the wiring of `onReply`, `onQuoteClick` → `revealTarget`), and `common/SfSymbolTable.kt` if a new icon is used. Prefer `Icons.AutoMirrored.Filled.Reply` and `Icons.Default.ContentCopy`, and check that `SF_SYMBOLS` has rows for them.
- Test: `app/src/test/java/com/coparently/app/presentation/chat/ChatReplyTest.kt` (Robolectric).

**Interfaces:**
- Consumes: `ChatViewModel.startReply`, `cancelReply`, `replyTarget` and `quoteFor` (Task 3), `ReplyQuote` (Task 1), and `MessagesList.onReply` and `onQuoteClick` (Task 4).
- Produces strings in five locales:
  - `chat_reply` "Reply"
  - `chat_copy_text` "Copy text"
  - `chat_reply_to` "Reply to %1$s"
  - `chat_cancel_reply` "Cancel reply"
  - `chat_quote_unavailable` "Message unavailable"
  - `chat_quote_file` "File: %1$s"
  - `chat_quote_sticker` "Sticker"
- Behaviour:
  - **Swipe.** Horizontal drag only, to the right, capped at 72 dp. At 56 dp, one `HapticFeedbackType.LongPress` and a reply arrow. On release past 56 dp, `onReply(message)`; then the bubble springs back.
  - **Long press.** Android draws a `DropdownMenu` anchored on the bubble with Reply, and Copy text for text only. The iPhone uses `nativeControls()?.ActionSheet(null, items, onDismiss)`. Copy goes through `LocalSensitiveClipboard`.
  - **Accessibility.** `customActions` get "Reply" on every bubble.
  - **Quote.** A 3 dp bar in the author's role colour (`primary` on the other's bubble, `onPrimary` on one's own), the name in `labelMedium` emphasized, and the excerpt on one line with an ellipsis. A tap calls `onQuoteClick(id)`. `Unavailable` draws the text alone, with no tap.
  - **Reply bar.** `surfaceContainer`, a top corner from `MaterialTheme.shapes.large`, the reply icon, "Reply to {name}", the excerpt, and ✕ (`chat_cancel_reply`).

- [ ] **Step 1: Write the failing tests** in `ChatReplyTest`:
  - `a swipe past the threshold starts a reply`: `performTouchInput { swipeRight(endX = 70.dp.toPx()) }`, and `onReply` is called.
  - `a short or vertical swipe starts none`: Review Focus 4.
  - `long press offers reply and copy for text only`.
  - `tapping a quote asks for its original`: `onQuoteClick("a")` is called.
  - `the reply bar names the author and cancels`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*ChatReplyTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the step 2 command, `:shared-ui:testAndroidHostTest` (`SfSymbolTableTest`), `:shared-ui:compileKotlinIosSimulatorArm64`, `detekt :shared-ui:detekt` and `node tools/check-invariants.js`. Expected: all PASS.
- [ ] **Step 5: Commit**: `feat(chat): reply to a message by swipe or long press, quoted in the thread`.

### Task 6: Docs, device check and the whole-branch verification

**Files:**
- Modify:
  - `CLAUDE.md`:
    - design item 8: replies, the reversed thread, the "↓" button;
    - item 14: the chat thread no longer follows the keyboard by hand, so drop the `FollowNewestWhileKeyboardOpens` sentence and say the reversed list keeps the newest in view;
    - the export, item 26: the in-reply-to column.
  - `docs/DEVICE-CHECKLIST.md`: a new section after the chat checks. It covers a reply between two phones, an older build drawing a reply plainly, and the swipe on a real screen.
  - `tools/manual-test-plan.js` `RULES`, if the section number is new.

- [ ] **Step 1: Write the docs.**
- [ ] **Step 2: Run** the whole set:
  ```bash
  ./gradlew :shared-ui:compileKotlinIosSimulatorArm64 :app:assembleDebug :shared:testAndroidHostTest :shared-data:testAndroidHostTest :shared-ui:testAndroidHostTest :app:testDebugUnitTest detekt :shared-ui:detekt
  node tools/check-invariants.js && node --test tools/test/*.test.js
  ```
  Expected: BUILD SUCCESSFUL, "All invariants hold", node tests pass.
- [ ] **Step 3: Commit** `docs: chat replies and the newest-first thread`. Push the branch, open the PR, and touch `.github/regenerate-request` so CI re-records the screenshot baselines (the chat thread's images change).
