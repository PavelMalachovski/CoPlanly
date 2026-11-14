@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.data.session

import com.coparently.app.data.chat.ChatAttachmentOutbox
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.buildCoPlanlyDatabase
import com.coparently.app.data.local.entity.UserEntity
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.platform.AppDirectories
import com.coparently.app.data.platform.MemoryTextStore
import com.coparently.app.data.platform.SealedSecurePreferences
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.domain.notification.RequestClockScheduler
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.files.Path
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * One phone's worth of what an account leaves behind, for [IosAccountSwitch] and [IosAccountTraces]:
 * a real database on the bundled driver in a folder of its own, the sealed preference store over
 * memory (a bare simulator test process has no Keychain item to spare for it), files in the caches
 * and the chat outbox, and schedulers that count their cancellations.
 */
internal class AccountSwitchFixture(signedInUser: SignedInUser, lastUid: String?) {

    private val root = NSTemporaryDirectory() + "coplanly-switch-" + NSUUID().UUIDString

    /** The phone's folders, under the test's own temporary folder. */
    val directories = object : AppDirectories {
        override val files: Path = Path(root, "files")
        override val cache: Path = Path(root, "cache")
        override val noBackup: Path = Path(root, "no_backup")
    }

    /** The account's rows. */
    val database: CoPlanlyDatabase = buildCoPlanlyDatabase(directories)

    /** The account's preferences. */
    val preferences = SealedSecurePreferences(MemoryTextStore())

    /** Event reminders; counts [ReminderScheduler.cancelAll]. */
    val reminders = CountingReminders()

    /** Request clocks' jobs; counts [RequestClockScheduler.cancelAll]. */
    val clocks = CountingClocks()

    /** The last account seen, in memory. */
    val lastSignedInUid = MemoryLastSignedInUid(lastUid)

    /** The traces under [directories]. */
    val traces = IosAccountTraces(directories)

    /** The check under test. */
    val accountSwitch = IosAccountSwitch(
        database = database,
        auth = signedInUser,
        preferences = preferences,
        reminders = reminders,
        clocks = clocks,
        traces = traces,
        lastUid = lastSignedInUid,
    )

    /** A file each of the traces' directories holds, by its path. */
    val traceFiles: List<String> = listOf(
        "$root/cache/shared_files/view/doc.pdf",
        "$root/cache/receipts/receipt.jpg",
        "$root/cache/exports/record.pdf",
        "$root/cache/payment_codes/code.png",
        "$root/files/${ChatAttachmentOutbox.OUTBOX_DIRECTORY}/message-1/photo.jpg",
    )

    /** A row, ordinary preferences, both school connections, a slot marker and the trace files. */
    suspend fun fillWithThePreviousAccount() {
        database.userDao().insertUser(
            UserEntity(
                id = "previous",
                email = "previous@example.test",
                name = "Previous",
                role = "mom",
                colorCode = "#FF4081"
            )
        )
        preferences.putString(ORDINARY_KEY, "the previous account's")
        preferences.putString(SCHOOL_KEY, "the previous account's school tokens")
        preferences.putString(EDUPAGE_KEY, "the previous account's EduPage password")
        preferences.putString(SLOT_MARKER_KEY, "mom")
        traceFiles.forEach(::writeFile)
    }

    /** Whether [path] exists. */
    fun exists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

    /** Removes the test's folder. */
    fun close() {
        database.close()
        NSFileManager.defaultManager.removeItemAtPath(root, error = null)
    }

    private fun writeFile(path: String) {
        val folder = path.substringBeforeLast('/')
        NSFileManager.defaultManager.createDirectoryAtPath(
            folder,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        NSFileManager.defaultManager.createFileAtPath(path, contents = NSData(), attributes = null)
    }

    companion object {
        /** A key nothing exempts from `clear()`. */
        const val ORDINARY_KEY = "some_setting"

        /** A school connection, which survives `clear()` and must go explicitly. */
        const val SCHOOL_KEY = PreferenceKeys.SCHOOL_CONNECTION_PREFIX + "0123456789abcdef"

        /** An EduPage connection, which survives `clear()` too, under a prefix of its own. */
        const val EDUPAGE_KEY = PreferenceKeys.EDUPAGE_CONNECTION_PREFIX + "fedcba9876543210"

        /** A per-uid parent-slot marker, which survives `clear()` by design on both platforms. */
        const val SLOT_MARKER_KEY = PreferenceKeys.PARENT_SLOT_MARKER_PREFIX + "previous"
    }
}

/** A [LastSignedInUid] in memory. */
internal class MemoryLastSignedInUid(private var uid: String?) : LastSignedInUid {
    override fun get(): String? = uid

    override fun set(uid: String) {
        this.uid = uid
    }
}

/** A [ReminderScheduler] that counts [cancelAll]. */
internal class CountingReminders : ReminderScheduler {
    /** How many times every reminder was cancelled. */
    var cancelledAll = 0

    override fun schedule(event: Event) = Unit

    override fun cancel(eventId: String) = Unit

    override fun cancelAll() {
        cancelledAll++
    }
}

/** A [RequestClockScheduler] that counts [cancelAll]. */
internal class CountingClocks : RequestClockScheduler {
    /** How many times every clock's job was cancelled. */
    var cancelledAll = 0

    override fun scheduleReminder(clockId: String, atMillis: Long) = Unit

    override fun scheduleDeadline(clockId: String, atMillis: Long) = Unit

    override fun scheduleEnd(clockId: String, atMillis: Long) = Unit

    override fun cancelAll() {
        cancelledAll++
    }
}
