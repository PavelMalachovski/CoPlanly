package com.coparently.app.ios

import com.coparently.app.data.notification.checkedAccount
import com.coparently.app.data.platform.logger
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.serialization.StoredJson
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.widget.WidgetClock
import com.coparently.app.presentation.widget.WidgetSnapshot
import com.coparently.app.presentation.widget.WidgetSnapshotBuilder
import com.coparently.app.presentation.widget.WidgetSnapshotInput
import com.coparently.app.pushwording.WordingStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

/**
 * Asks the home screen to draw the Today widget again (iPhone phase 4 part D). The app's Swift side
 * implements it (`WidgetReloadBridge`) with `WidgetCenter.shared.reloadAllTimelines()` and hands it to
 * `IosApp.start`.
 */
interface WidgetReload {
    /** Reloads every placed widget's timeline. */
    fun reloadAll()
}

/**
 * What [WidgetSnapshotWriter] reads, narrow so that a test needs neither Room nor Firebase. The
 * app's half is [IosWidgetSources]: this phone's Room rows and nothing else, as Android's widget
 * reads them.
 */
interface WidgetSources {
    /** The signed-in account, or null when nobody is signed in. */
    fun signedInUid(): String?

    /**
     * The last account the account check ran for (`LastSignedInUid`, which `IosAccountSwitch` writes
     * once it has wiped a previous account's rows), or null.
     */
    fun lastCheckedUid(): String?

    /** Today in the phone's zone: the snapshot's first day. */
    fun today(): LocalDate

    /** Now, epoch milliseconds: the snapshot's `writtenAtMillis`. */
    fun nowMillis(): Long

    /** [day]'s events, recurring series expanded, as `EventRepository.getEventsByDateRange` answers for it. */
    suspend fun eventsOn(day: LocalDate): List<Event>

    /** The family on screen's stored custody pattern and its accepted swaps (`storedCustody`). */
    suspend fun custody(): Pair<CustodyModel?, Map<String, DayOverride>>

    /** The parents' names the app remembered for [uid] (`TodayWidgetNames.recall`), or null. */
    fun parents(uid: String): Parents?
}

/**
 * The language a snapshot is worded in and the words and clock for it. The app's half is
 * [IosWidgetWording]: the owner's language rule over the phone's first language, the generated
 * `PushStrings` in the app's bundle and Foundation's dates.
 */
interface WidgetWording {
    /** The BCP 47 tag the app speaks now (`readerLanguageTag`). */
    fun language(): String

    /** The strings of [language]. */
    fun strings(language: String): WordingStrings

    /** The dates and times of [language]. */
    fun clock(language: String): WidgetClock
}

/**
 * Writes the Today widget's [WidgetSnapshot] into the App Group, where the widget extension reads it
 * (iPhone phase 4 part D, spec §7.2), and asks the home screen to draw it again.
 *
 * The snapshot is built by [WidgetSnapshotBuilder] (the Android widget's own model and wording) from
 * [sources], worded through [wording], encoded through `StoredJson`, and written to
 * `<directory>/today.json` through `today.json.part` and a rename, so the extension never reads half
 * a file. A snapshot that equals the stored one but for `writtenAtMillis` is not written again and
 * asks for no reload: most triggers change nothing the widget draws.
 *
 * One write at a time ([Mutex]): a sync, a Room change, a sign-out and the foreground may all ask at
 * once, and two writers of one `.part` file would interleave.
 *
 * @param directory Where `today.json` lives: `<App Group container>/widget` in the app, a temporary
 *   directory in a test.
 * @param reload What asks WidgetKit to reload ([WidgetReload]).
 * @param sources What a snapshot is built from.
 * @param wording The language, words and clock it is worded with.
 */
class WidgetSnapshotWriter(
    private val directory: Path,
    private val reload: WidgetReload,
    private val sources: WidgetSources,
    private val wording: WidgetWording,
) {
    private val mutex = Mutex()

    /**
     * Writes the snapshot for whoever is signed in now, once the account check has run for them
     * ([checkedAccount], the rule the local reminders follow); the signed-out one when nobody is, or
     * while the check has not run: sign-out keeps Room, so until `IosAccountSwitch` wipes them the
     * previous account's events and schedule are still there, and must not be drawn as the new
     * account's day. The write after the sync that ran the check draws it.
     *
     * @return true when the file changed and a reload was asked; false when nothing changed or the
     *   file could not be written (logged).
     */
    suspend fun write(): Boolean = mutex.withLock {
        store(snapshot(checkedAccount(sources.signedInUid(), sources.lastCheckedUid())))
    }

    /**
     * Writes the signed-out snapshot, whoever is still signed in: what a sign-out, an account
     * deletion and an account switch write **before** the session ends, so the home screen never
     * shows the departing account's day once it has gone.
     */
    suspend fun writeSignedOut() {
        mutex.withLock { store(snapshot(uid = null)) }
    }

    private suspend fun snapshot(uid: String?): WidgetSnapshot {
        val today = sources.today()
        val language = wording.language()
        val custody: Pair<CustodyModel?, Map<String, DayOverride>> =
            if (uid == null) null to emptyMap() else sources.custody()
        val events: Map<LocalDate, List<Event>> = if (uid == null) {
            emptyMap()
        } else {
            (0 until WidgetSnapshotBuilder.DAYS).associate { offset ->
                val day = today.plus(offset, DateTimeUnit.DAY)
                day to sources.eventsOn(day)
            }
        }
        val input = WidgetSnapshotInput(
            today = today,
            writtenAtMillis = sources.nowMillis(),
            language = language,
            userId = uid,
            eventsOn = { day -> events[day].orEmpty() },
            model = custody.first,
            overrides = custody.second,
            parents = uid?.let { sources.parents(it) }
        )
        return WidgetSnapshotBuilder.build(input, wording.strings(language), wording.clock(language))
    }

    /** Writes [snapshot] unless the stored one says the same; true when it wrote and asked for a reload. */
    private suspend fun store(snapshot: WidgetSnapshot): Boolean {
        val file = Path(directory, FILE_NAME)
        val written = try {
            withContext(Dispatchers.IO) {
                val stored = readStored(file)
                if (stored != null && stored.copy(writtenAtMillis = 0) == snapshot.copy(writtenAtMillis = 0)) {
                    false
                } else {
                    val part = Path(directory, PART_NAME)
                    SystemFileSystem.createDirectories(directory)
                    SystemFileSystem.sink(part).buffered().use {
                        it.writeString(StoredJson.encode(WidgetSnapshot.serializer(), snapshot))
                    }
                    SystemFileSystem.atomicMove(part, file)
                    true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // The App Group's folder could not be written: the widget keeps the last snapshot until
            // the next trigger; a failed write must never take the app down with it.
            logger.w(TAG, "Could not write the Today widget's snapshot", e)
            false
        }
        if (written) reload.reloadAll()
        return written
    }

    /** The snapshot stored at [file], or null when there is none or it cannot be read. */
    private fun readStored(file: Path): WidgetSnapshot? {
        if (!SystemFileSystem.exists(file)) return null
        val text = SystemFileSystem.source(file).buffered().use { it.readString() }
        return try {
            StoredJson.decodeOrNull(WidgetSnapshot.serializer(), text)
        } catch (e: IllegalArgumentException) {
            // A file an older shape left, or a torn one: written again whole.
            logger.w(TAG, "The stored Today widget snapshot could not be read", e)
            null
        }
    }

    /** The file names, as the widget extension reads them. */
    companion object {
        /** The snapshot's file in the directory. */
        const val FILE_NAME: String = "today.json"

        /** Where it is written before the rename. */
        const val PART_NAME: String = "today.json.part"

        private const val TAG = "WidgetSnapshotWriter"
    }
}
