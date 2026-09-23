package com.coparently.app.data.sync

import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.data.remote.google.CredentialProvider
import com.coparently.app.data.remote.google.CredentialProviderImpl
import com.coparently.app.data.remote.google.GoogleCalendarApi
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.repository.UserRepository
import com.google.api.client.auth.oauth2.Credential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import com.google.api.services.calendar.model.Event as GoogleEvent

/**
 * Repository for synchronizing events between local database and Google Calendar.
 */
@Singleton
class CalendarSyncRepository @Inject constructor(
    private val eventDao: EventDao,
    private val googleCalendarApi: GoogleCalendarApi,
    private val credentialProvider: CredentialProvider,
    private val userRepository: UserRepository,
    private val crashlyticsManager: CrashlyticsManager
) {
    /**
     * Syncs events from Google Calendar to local database (pull).
     */
    suspend fun syncFromGoogle(
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null
    ): Flow<SyncResult> = flow {
        try {
            emit(SyncResult.Progress(SyncStage.STARTING))

            val credential = credentialProvider.getCredential()
                ?: throw SyncFailureException(SyncFailure.NOT_SIGNED_IN_GOOGLE)
            // Token refresh is now handled automatically in getCredential()

            // A Google Calendar import is created by whoever pulled it in - the same "yours by
            // default" rule AddEditEventScreen applies. UserRepository is the domain interface
            // for exactly this lookup (no pairing subscription, just this device's own Room
            // row); the equivalent UI-layer helper is `ParentsSource.signedInSlot()`, which this
            // class used to reach for instead, across a layer boundary it had no business
            // crossing. Resolved once per sync, not once per event.
            val ownerUid = userRepository.getCurrentUserId()
                ?: throw SyncFailureException(SyncFailure.NOT_SIGNED_IN_APP)
            val owner = userRepository.getUserById(ownerUid)
                ?: throw SyncFailureException(SyncFailure.NOT_SIGNED_IN_APP)
            val ownerSlot = owner.role
            // The family this import belongs to — still stamped, though nothing shares it.
            // See `toEventEntity` for why an import is private, and why that makes the
            // "which family" question stop needing an answer.
            val ownerFamilyId = FamilyKey.orNull(ownerUid, owner.partnerId)

            emit(SyncResult.Progress(SyncStage.FETCHING))

            // Execute API call on IO dispatcher to avoid NetworkOnMainThreadException
            val imported = withContext(Dispatchers.IO) {
                googleCalendarApi.listEvents(
                    credential = credential,
                    timeMin = startDate,
                    timeMax = endDate
                )
            }

            emit(SyncResult.Progress(SyncStage.FOUND, found = imported.events.size))

            val eventsToInsert = mutableListOf<EventEntity>()

            imported.events.forEach { googleEvent ->
                val eventEntity = googleEvent.toEventEntity(ownerSlot, ownerUid, ownerFamilyId)
                eventsToInsert.add(eventEntity)
            }

            if (eventsToInsert.isNotEmpty()) {
                eventDao.insertEvents(eventsToInsert)
            }

            // Says which window was read and whether anything was left behind. "Synced N events"
            // on its own is what a truncated import used to say too, which is how a half-finished
            // import passed for a complete one. The facts, not a sentence: the ViewModel words
            // them in the reader's language (CQ-14).
            emit(
                SyncResult.Success(
                    synced = eventsToInsert.size,
                    from = imported.from.toLocalDate(),
                    until = imported.until.toLocalDate(),
                    truncated = imported.truncated
                )
            )
        } catch (e: SyncFailureException) {
            android.util.Log.e("CalendarSync", "Sync refused: ${e.failure}", e)
            emit(SyncResult.Error(e.failure))
        } catch (e: IllegalStateException) {
            android.util.Log.e("CalendarSync", "Authentication error: ${e.message}", e)
            emit(SyncResult.Error(SyncFailure.AUTHENTICATION))
        } catch (e: android.os.NetworkOnMainThreadException) {
            android.util.Log.e("CalendarSync", "NetworkOnMainThreadException: API call must be on background thread", e)
            emit(SyncResult.Error(SyncFailure.UNKNOWN))
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            // Google API specific errors
            android.util.Log.e("CalendarSync", "Google API error: ${e.statusCode} - ${e.message}", e)
            emit(SyncResult.Error(SyncFailure.forStatus(e.statusCode)))
        } catch (e: com.google.api.client.http.HttpResponseException) {
            // HTTP response errors. This MUST precede the IOException branch below —
            // HttpResponseException extends IOException, so the reverse order (which shipped)
            // made every 401/403/404/500 here unreachable and surfaced as a generic
            // "Network error". Kotlin does not flag an unreachable catch the way Java does.
            android.util.Log.e("CalendarSync", "HTTP error: ${e.statusCode} - ${e.message}", e)
            emit(SyncResult.Error(SyncFailure.forStatus(e.statusCode)))
        } catch (e: java.io.IOException) {
            android.util.Log.e("CalendarSync", "Network error: ${e.message}", e)
            emit(SyncResult.Error(SyncFailure.NETWORK))
        } catch (e: Exception) {
            // Log full error for debugging
            android.util.Log.e("CalendarSync", "Unexpected error: ${e.javaClass.simpleName} - ${e.message}", e)
            // The exception's class and message go to the log above and to Crashlytics; they
            // used to be printed on the Settings screen too, in English, as the whole message.
            crashlyticsManager.recordException(e)
            emit(SyncResult.Error(SyncFailure.UNKNOWN))
        }
    }

    /**
     * Converts Google Calendar Event to EventEntity.
     *
     * @param ownerSlot This device's own slot, attributed to the import - see the call site in
     *   [syncFromGoogle] for why it isn't looked up per event.
     * @param ownerUid This device's Firebase UID, stamped as `createdByFirebaseUid` so the
     *   imported row can actually be uploaded: the Firestore create rule requires
     *   `createdByFirebaseUid == auth.uid`, and a null here made every import a doomed write
     *   that `getUnsyncedEvents()` retried on every sync forever.
     * @param ownerFamilyId The importing parent's family, or null while they are unpaired -
     *   resolved once per sync at the call site for the same reason [ownerSlot] is. Stamped
     *   even though a private event never syncs, for the reason `EventRepositoryImpl` stamps
     *   `createdByFirebaseUid` on one: a field written only on the sync path stays null forever
     *   on exactly the rows that never take it.
     */
    private fun GoogleEvent.toEventEntity(
        ownerSlot: String,
        ownerUid: String,
        ownerFamilyId: String?
    ): EventEntity {
        // An all-day event carries its date in `date`, not `dateTime`. Reading only `dateTime`
        // and falling back to now() stamped a birthday, a school holiday or an all-day custody
        // note onto today's cell and lost its real date. `date` is a date-only value at UTC
        // midnight, so it is read in UTC (not the system zone, which could shift the day).
        val startDateTime = start?.dateTime?.value?.let { epochMillisToLocal(it) }
            ?: start?.date?.value?.let { utcMillisToLocalDate(it) }
            ?: LocalDateTime.now()

        val endDateTime = end?.dateTime?.value?.let { epochMillisToLocal(it) }
            ?: end?.date?.value?.let { utcMillisToLocalDate(it) }

        return EventEntity(
            id = id ?: java.util.UUID.randomUUID().toString(),
            title = summary ?: "Untitled Event",
            description = description,
            startDateTime = startDateTime,
            endDateTime = endDateTime,
            eventType = "google",
            parentOwner = ownerSlot,
            isRecurring = recurrence != null,
            recurrencePattern = recurrence?.firstOrNull()?.toString(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            createdByFirebaseUid = ownerUid,
            familyId = ownerFamilyId,
            // An import is **private**: it stays on the device that pulled it and never
            // reaches a co-parent (owner decision, Aug 2026).
            //
            // A Google Calendar is a personal calendar. Publishing everything on it to the
            // person you separated from is not what "sync my calendar" asks for, and the
            // events it carries — a work meeting, a doctor, a date — are exactly the ones a
            // co-parenting app has no business forwarding. What a parent wants shared, they
            // create in the app.
            //
            // It also dissolves a question rather than answering it: with several families,
            // "which one does an imported event belong to" has no answer a Google Calendar
            // could give. A private event belongs to nobody but its creator, so there is
            // nothing to get wrong.
            //
            // This is a behaviour change — imports used to sync — and `EventRepositoryImpl`
            // already removes a remote copy when an event turns private, so an event imported
            // by an older build leaves Firestore the first time it is edited here.
            isPrivate = true
        )
    }

    /** A timed event's epoch millis as a local date-time in this device's zone. */
    private fun epochMillisToLocal(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault())

    /** An all-day event's UTC-midnight millis as the start of that calendar day. */
    private fun utcMillisToLocalDate(millis: Long): LocalDateTime =
        java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay()
}

/**
 * Result of synchronization operation.
 *
 * Facts, not sentences (CQ-14): the data layer has no `Context` to localise with, so
 * `SyncViewModel` words each of these in the reader's language. Every variant used to carry an
 * English string, and some of them the raw exception text.
 */
sealed class SyncResult {
    /** The import is under way; [found] is the number of events read, for [SyncStage.FOUND]. */
    data class Progress(val stage: SyncStage, val found: Int = 0) : SyncResult()

    /**
     * The import finished: [synced] events over the window [from]..[until]. [truncated] means
     * the window held more than one import can take — it must never read like a complete one.
     */
    data class Success(
        val synced: Int,
        val from: LocalDate,
        val until: LocalDate,
        val truncated: Boolean
    ) : SyncResult()

    /** The import failed, and why. */
    data class Error(val reason: SyncFailure) : SyncResult()
}

/** Where a running Google Calendar import has got to. */
enum class SyncStage {
    STARTING,
    FETCHING,
    FOUND
}

/** Why a Google Calendar import failed, as far as the user can act on it. */
enum class SyncFailure {
    /** No Google credential: the user has to connect Google Calendar again. */
    NOT_SIGNED_IN_GOOGLE,

    /** Nobody is signed in to the app, so there is no one to attribute the import to. */
    NOT_SIGNED_IN_APP,

    /** Google refused the credential (401). */
    AUTHENTICATION,

    /** The account has not granted Calendar access (403). */
    ACCESS_DENIED,

    /** The calendar does not exist (404). */
    CALENDAR_NOT_FOUND,

    /** Too many requests (429). */
    RATE_LIMITED,

    /** Google's side failed (5xx). */
    SERVICE_UNAVAILABLE,

    /** The device could not reach Google. */
    NETWORK,

    /** Anything else; the detail is in the log and in Crashlytics. */
    UNKNOWN;

    companion object {
        /** Maps an HTTP status from the Calendar API to a failure. */
        fun forStatus(statusCode: Int): SyncFailure = when (statusCode) {
            HTTP_UNAUTHORIZED -> AUTHENTICATION
            HTTP_FORBIDDEN -> ACCESS_DENIED
            HTTP_NOT_FOUND -> CALENDAR_NOT_FOUND
            HTTP_TOO_MANY_REQUESTS -> RATE_LIMITED
            in HTTP_SERVER_ERRORS -> SERVICE_UNAVAILABLE
            else -> UNKNOWN
        }

        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val HTTP_SERVER_ERRORS = 500..599
    }
}

/** Ends an import early with a [SyncFailure] the user is shown. */
internal class SyncFailureException(val failure: SyncFailure) : Exception(failure.name)

