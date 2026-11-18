package com.coparently.app.ios

import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.widget.TodayWidgetNames
import com.coparently.app.presentation.widget.WidgetClock
import com.coparently.app.pushwording.BundleWordingStrings
import com.coparently.app.pushwording.WordingStrings
import com.coparently.app.utils.readerLanguageTag
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.io.files.Path
import platform.Foundation.NSFileManager
import kotlin.time.Clock

/**
 * [WidgetSources] in the app: this phone's Room rows and the names the app remembered, never a
 * Firestore listener, as Android's `TodayWidget.contentState` reads them (CLAUDE.md design item 18).
 * A day's events are `EventRepository.getEventsByDateRange` from its midnight to the next, the call
 * the Android widget makes for its one day; the custody pattern is `storedCustody()`, the row of the
 * family on screen, which the mirror keeps.
 */
internal class IosWidgetSources(
    private val signedIn: SignedInUser,
    private val events: EventRepository,
    private val custodyModels: CustodyModelRepository,
    private val names: TodayWidgetNames,
) : WidgetSources {

    override fun signedInUid(): String? = signedIn.getCurrentUid()

    override fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    override fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    override suspend fun eventsOn(day: LocalDate): List<Event> = events.getEventsByDateRange(
        LocalDateTime(day, MIDNIGHT),
        LocalDateTime(day.plus(1, DateTimeUnit.DAY), MIDNIGHT)
    ).first()

    override suspend fun custody(): Pair<CustodyModel?, Map<String, DayOverride>> = custodyModels.storedCustody()

    override fun parents(uid: String): Parents? = names.recall(uid)

    private companion object {
        val MIDNIGHT = LocalTime(0, 0)
    }
}

/**
 * [WidgetWording] in the app: the language the app speaks ([readerLanguageTag], the owner's rule
 * over the phone's first language, which `AppGroupShare` shares with the notification extension),
 * the generated `PushStrings` the app's bundle carries ([BundleWordingStrings], as the local
 * reminders are worded, `appReminderWording`) and Foundation's dates ([IosWidgetClock]). Read afresh
 * for each snapshot, so a language changed in the phone's Settings is followed.
 */
internal object IosWidgetWording : WidgetWording {
    override fun language(): String = readerLanguageTag()

    override fun strings(language: String): WordingStrings = BundleWordingStrings(language)

    override fun clock(language: String): WidgetClock = IosWidgetClock(language)
}

/**
 * Where the snapshot lives: `<App Group container>/widget`, the folder the widget extension reads
 * (`group.app.coplanly`, [AppGroupShare.SUITE]), or null when the app has no container for the group
 * (a build signed without the App Group entitlement), logged, and then no snapshot is written.
 */
internal fun appGroupWidgetDirectory(): Path? {
    val container = NSFileManager.defaultManager
        .containerURLForSecurityApplicationGroupIdentifier(AppGroupShare.SUITE)
        ?.path
    if (container == null) {
        logger.w("IosWidget", "No App Group container: the Today widget's snapshot is not written")
        return null
    }
    return Path(container, WIDGET_DIRECTORY)
}

/** The App Group container's folder the snapshot is written to. */
private const val WIDGET_DIRECTORY = "widget"
