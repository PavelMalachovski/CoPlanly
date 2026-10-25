package com.coparently.app.domain.activity

import com.coparently.app.domain.expenses.FULL_SHARE_BASIS_POINTS
import com.coparently.app.domain.expenses.SplitRatio
import java.math.BigDecimal

/**
 * The plain-text sentence an announcement carries in `Message.content`.
 *
 * **This is not a localisation path, and must not become one.** The real card is composed by the
 * *reading* device from [ActivityAnnouncement], in the reader's own language — that is the whole
 * point of the payload. This text exists for one case: a co-parent on a build that predates
 * `MessageType.ACTIVITY`, whose app degrades the unknown type to a plain text bubble and shows
 * `content`. Without it they would see an empty bubble.
 *
 * It is therefore in the app's **base locale** (English, which is what `values/` is), always, for
 * everyone — not in the sender's locale and not in the reader's. Neither would be more correct:
 * the sender's language is not a property of the message, and the reader's is unknown to the
 * sender. A single fixed language at least makes the fallback predictable, and it disappears
 * entirely once both phones carry this build.
 *
 * It lives here rather than in `res/values` deliberately: a repository has no `Context`, and
 * CLAUDE.md forbids injecting one to reach a string. These are not strings that should have been
 * localised — they are the one place localisation is explicitly not wanted.
 */
object ActivityFallbackText {

    /** The base-locale sentence for [announcement]. */
    fun of(announcement: ActivityAnnouncement): String {
        settleUp(announcement)?.let { return it }
        money(announcement)?.let { return it }
        val prefix = when (announcement.kind) {
            ActivityKind.EVENT_CREATED -> "New event"
            ActivityKind.EVENT_UPDATED -> "Event changed"
            ActivityKind.EVENT_DELETED -> "Event removed"
            ActivityKind.EVENT_ACCEPTED -> "Event accepted"
            ActivityKind.EVENT_DECLINED -> "Event declined"
            ActivityKind.CHANGE_REQUESTED -> "New time proposed"
            ActivityKind.CHANGE_ACCEPTED -> "New time agreed"
            ActivityKind.CHANGE_DECLINED -> "New time turned down"
            ActivityKind.EXPENSE_ADDED -> "New expense"
            ActivityKind.EXPENSE_UPDATED -> "Expense changed"
            ActivityKind.EXPENSE_DELETED -> "Expense removed"
            ActivityKind.EXPENSE_QUESTIONED -> "Expense questioned"
            ActivityKind.DAY_SWAP_OFFERED -> "Day swap offered"
            ActivityKind.DAY_SWAP_ACCEPTED -> "Day swap agreed"
            ActivityKind.DAY_SWAP_DECLINED -> "Day swap turned down"
            ActivityKind.CUSTODY_PROPOSED -> "Custody schedule proposed"
            ActivityKind.CUSTODY_ACCEPTED -> "Custody schedule agreed"
            ActivityKind.CUSTODY_DECLINED -> "Custody proposal turned down"
            ActivityKind.REQUEST_LAST_CHANCE -> "More time to answer"
            ActivityKind.REQUEST_UNANSWERED -> "No answer"
            ActivityKind.SETTLE_UP_OWED, ActivityKind.SETTLE_UP_OWING -> "Settle up"
            ActivityKind.SPLIT_PROPOSED -> "Proposed a new expense split"
            ActivityKind.SPLIT_ACCEPTED -> "Accepted the expense split"
            ActivityKind.SPLIT_DECLINED -> "Declined the expense split"
            ActivityKind.SPLIT_WITHDRAWN -> "Withdrew the expense split proposal"
            ActivityKind.DAY_SWAP_WITHDRAWN -> "Day swap withdrawn"
            ActivityKind.CUSTODY_WITHDRAWN -> "Custody proposal withdrawn"
            // The sticker's kind is an identifier, not a sentence: no title is appended.
            ActivityKind.STICKER_SENT -> return "Sent a sticker"
            ActivityKind.SUPPORT_DECLINED -> return "Declined the child support proposal"
            ActivityKind.SUPPORT_WITHDRAWN -> return "Withdrew the child support proposal"
            // Worded with their amounts by [money]; reached only when one is missing.
            ActivityKind.SETTLE_UP_PAID -> "Paid the settle-up"
            ActivityKind.SETTLE_UP_CONFIRMED -> "The settle-up arrived"
            ActivityKind.SUPPORT_PROPOSED -> return "Proposed child support"
            ActivityKind.SUPPORT_ACCEPTED -> return "Agreed child support"
            ActivityKind.SUPPORT_PAID -> "Paid child support"
            ActivityKind.SUPPORT_CONFIRMED -> "Child support arrived"
        }
        return if (announcement.title.isBlank()) prefix else "$prefix: ${announcement.title}"
    }

    /**
     * [of], knowing the sending parent's slot, which only an expense split proposal's sentence
     * reads: its card carries slot 1's share, and "I pay" is the sender's own. Every other card
     * reads as [of] words it, and so does a split whose slot or share is unknown.
     *
     * @param senderSlot The sender's slot, `"mom"` or `"dad"`, or null when unknown.
     */
    fun of(announcement: ActivityAnnouncement, senderSlot: String?): String =
        splitShares(announcement, senderSlot) ?: of(announcement)

    /**
     * An expense split proposal in the proposer's first person with both shares, as the export and
     * an older build show it: "Proposed a new expense split: I pay 65 %, you pay 35 %". Null for any
     * other card, and when the share or the sender's slot cannot be read.
     */
    private fun splitShares(announcement: ActivityAnnouncement, senderSlot: String?): String? {
        val ratio = announcement.amount?.toIntOrNull()
            ?.let { SplitRatio.fromStored(it) }
            ?.takeIf { announcement.kind == ActivityKind.SPLIT_PROPOSED }
            ?: return null
        val mine = when (senderSlot) {
            SLOT_ONE -> ratio.momShareBasisPoints
            SLOT_TWO -> ratio.dadShareBasisPoints
            else -> return null
        }
        val yours = FULL_SHARE_BASIS_POINTS - mine
        return "Proposed a new expense split: I pay ${percentOf(mine)}, you pay ${percentOf(yours)}"
    }

    /** [basisPoints] as a percent in the base locale: `35 %`, or `33.33 %` for a share that is not whole. */
    private fun percentOf(basisPoints: Int): String =
        BigDecimal.valueOf(basisPoints.toLong(), PERCENT_SCALE).stripTrailingZeros().toPlainString() + " %"

    /** A settle-up card in the sender's first person, as a message from them reads. */
    private fun settleUp(announcement: ActivityAnnouncement): String? {
        val money = "${announcement.amount.orEmpty()} ${announcement.currency.orEmpty()}".trim()
        return when (announcement.kind) {
            ActivityKind.SETTLE_UP_OWED -> "Settle up for ${announcement.title}: you owe me $money"
            ActivityKind.SETTLE_UP_OWING -> "Settle up for ${announcement.title}: I owe you $money"
            else -> null
        }
    }

    /**
     * A payment or a child support agreement in the sender's first person, with its amount, as the
     * export and an older build show it. Null for any other card, and when the amount is missing.
     */
    private fun money(announcement: ActivityAnnouncement): String? {
        val amount = announcement.amount?.takeIf { it.isNotBlank() } ?: return null
        val money = "$amount ${announcement.currency.orEmpty()}".trim()
        val month = announcement.title
        return when (announcement.kind) {
            ActivityKind.SETTLE_UP_PAID -> "Paid the settle-up for $month: $money"
            ActivityKind.SETTLE_UP_CONFIRMED -> "The settle-up for $month arrived: $money"
            ActivityKind.SUPPORT_PAID -> "Paid child support for $month: $money"
            ActivityKind.SUPPORT_CONFIRMED -> "Child support for $month arrived: $money"
            ActivityKind.SUPPORT_PROPOSED -> "Proposed child support: $money a month, on day ${announcement.title}"
            ActivityKind.SUPPORT_ACCEPTED -> "Agreed child support: $money a month, on day ${announcement.title}"
            else -> null
        }
    }

    /** The schema's two slots, which the app never shows ("Parent colours identify a person"). */
    private const val SLOT_ONE = "mom"
    private const val SLOT_TWO = "dad"

    /** Basis points carry two decimal places of a percent. */
    private const val PERCENT_SCALE = 2
}
