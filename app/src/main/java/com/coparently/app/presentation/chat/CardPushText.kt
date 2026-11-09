package com.coparently.app.presentation.chat

import android.content.Context
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.presentation.expenses.currencyFormat
import com.coparently.app.utils.DAY_WITH_WEEKDAY
import com.coparently.app.utils.isoDateText
import org.jetbrains.compose.resources.StringResource
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A chat card's sentence for its push, worded on the receiving phone in [context]'s language
 * (notification audit, 9 November 2026), or null when this build cannot word it — the push then
 * shows the card's preview, as a push from an older sender does.
 *
 * Every card used to be pushed with its base-English fallback (`ActivityFallbackText`), so a Czech
 * or Russian parent read "New event: Zubař" on the lock screen and the card in their own language
 * in the thread. This is the thread's sentence — `MessageItem` with `settleUpCardText` and
 * `moneyCardText` — composed without Compose, because a push is worded in a service; both read
 * [ActivityCardText]'s decisions, so a new [ActivityKind] reaches the two at once. A push is always
 * the co-parent's card, so its reader is never the sender.
 *
 * **It stays in the app** (MON-24 phase 2): its one caller is `PushNotifier`, which words every push
 * through the Android resources of a context in the app's language (`inAppLanguage`), and
 * [ActivityCardText] answers Compose Multiplatform `StringResource`s since slice 11b. Each is read
 * here as the app's own `R.string` of the same key ([androidStringId]) — the byte-for-byte copy
 * `stringsToAndroidRes` generates — so the push speaks [context]'s language, not the process's.
 */
internal fun cardPushText(context: Context, card: ActivityAnnouncement): String? {
    val locale = context.resources.configuration.locales[0]
    if (card.kind.isSettleUp) {
        val month = card.whenIso?.let { iso -> runCatching { LocalDate.parse(iso) }.getOrNull() }
            ?.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
            ?: card.title
        val resource = ActivityCardText.settleUpResource(card.kind, readerIsSender = false)
        return context.wording(resource, month, amountText(card, locale))
    }
    val resource = ActivityCardText.resourceFor(card.kind)
    if (ActivityCardText.takesMoney(card.kind)) {
        return when (card.kind) {
            // `title` is the day of the month the agreement names.
            ActivityKind.SUPPORT_PROPOSED, ActivityKind.SUPPORT_ACCEPTED ->
                context.wording(resource, amountText(card, locale), card.title)
            // `title` is the month, `2026-10`.
            else -> context.wording(resource, monthText(card.title, locale), amountText(card, locale))
        }
    }
    return subjectText(context, card, locale)?.let { context.wording(resource, it) }
}

/** What [card]'s sentence is about, worded in [locale]; null when the app has no such noun. */
private fun subjectText(context: Context, card: ActivityAnnouncement, locale: Locale): String? =
    when (val about = ActivityCardText.subjectOf(card)) {
        is CardSubjectText.IsoDate -> isoDateText(about.iso, DAY_WITH_WEEKDAY, locale)
        is CardSubjectText.Res -> context.wording(about.id)
        is CardSubjectText.Raw -> about.text
    }

/**
 * The app's `R.string` id of [resource]'s key, or 0 when the app holds none — a key the release
 * build's resource shrinker removed would read as 0, and its push shows the preview rather than
 * crash. `res/raw/card_push_keep.xml` keeps the keys a card can name.
 */
// A key Compose Multiplatform names is the app's resource name: the app's copy is generated from
// the same files (`stringsToAndroidRes`), so a lookup by name is the one bridge there is.
@Suppress("DiscouragedApi")
internal fun Context.androidStringId(resource: StringResource): Int =
    resources.getIdentifier(resource.key, "string", packageName)

/** [resource] worded with [args] through this context's resources, or null when the app has no such key. */
private fun Context.wording(resource: StringResource, vararg args: Any): String? =
    androidStringId(resource).takeIf { it != 0 }?.let { getString(it, *args) }

/** `2026-10` as [locale] names the month ("October 2026"), or as it is. */
private fun monthText(isoMonth: String, locale: Locale): String =
    runCatching { YearMonth.parse(isoMonth) }.getOrNull()
        ?.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        ?: isoMonth

/** The card's amount in [locale]'s conventions and its own currency, or as it came. */
private fun amountText(card: ActivityAnnouncement, locale: Locale): String {
    val amount = card.amount?.toBigDecimalOrNull() ?: return card.amount.orEmpty()
    val currency = card.currency ?: return amount.toPlainString()
    return currencyFormat(currency, locale).format(amount)
}
