package com.coparently.app.data.consent

import android.util.Log
import com.coparently.app.domain.consent.HEALTH_CONSENT_VERSION
import com.coparently.app.domain.consent.HealthConsent
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.MedicalProfile
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.RecordPhotoStorage
import com.coparently.app.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How a withdrawal of the child-health consent ended.
 */
enum class HealthConsentWithdrawal {
    /** The consent is cleared and every child record this parent created holds no health data. */
    WITHDRAWN,

    /**
     * Nothing was withdrawn, because at least one medical photograph could not be deleted.
     *
     * The photographs that *were* deleted are already off their records — their objects are gone,
     * so a reference to them would point at nothing. Everything else, the consent included, is as
     * it was, so the parent can simply try again: withdrawing while keeping an image in the bucket
     * would break `RecordPhotoStorage`'s rule that a reference is never dropped before its object.
     */
    PHOTOS_NOT_DELETED,

    /** Nobody is signed in. */
    SIGNED_OUT,

    /**
     * Something failed part-way — a local write, most likely. Some records may already be
     * cleared; the consent may not be. Reported so the parent tries again rather than believing
     * it done.
     */
    FAILED
}

/**
 * The child-health consent (GDPR Art. 9(2)(a)): whether this parent has it, recording it, and
 * withdrawing it together with the health data it covered.
 *
 * One place for all three, shared by the child editor, the onboarding child step and Settings, so
 * the gate cannot mean one thing on one screen and another on the next.
 *
 * @param userRepository Holds the consent on the signed-in parent's profile.
 * @param childInfoRepository The child records whose health data a withdrawal clears; its normal
 *   update path is used, so each cleared record syncs to the co-parent like any edit.
 * @param photoStorage Deletes the medical photographs a withdrawal removes.
 */
@Singleton
class HealthConsentManager @Inject constructor(
    private val userRepository: UserRepository,
    private val childInfoRepository: ChildInfoRepository,
    private val photoStorage: RecordPhotoStorage
) {

    /**
     * The signed-in parent's consent, or null when never given, withdrawn or signed out. Follows
     * the Room row, so a consent another device gave arrives with the next profile refresh.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<HealthConsent?> = userRepository.observeCurrentUserId()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(null) else userRepository.observeUserById(uid).map { it?.healthConsent }
        }
        .distinctUntilChanged()

    /**
     * Records that the parent agreed to the dialog as it is worded now, at this moment.
     *
     * @return the consent recorded
     */
    suspend fun grant(): HealthConsent {
        val consent = HealthConsent(version = HEALTH_CONSENT_VERSION, atMillis = System.currentTimeMillis())
        userRepository.setHealthConsent(consent)
        return consent
    }

    /**
     * Withdraws the consent and deletes the health data it covered.
     *
     * Only records **this parent created** are cleared (`createdByFirebaseUid` is the signed-in
     * uid): a record the co-parent created rests on the co-parent's own consent, and one parent's
     * withdrawal must not erase what the other entered under theirs. On each of this parent's
     * records the medications, allergies, medical profile, medical notes and medical photographs
     * are cleared through [ChildInfoRepository.upsertChildInfo], so the change syncs like any edit.
     *
     * Photographs go first, and a failure stops the withdrawal — see
     * [HealthConsentWithdrawal.PHOTOS_NOT_DELETED]. The records are then written concurrently,
     * because each upload waits for the server and an offline phone must still clear every one of
     * them locally.
     */
    suspend fun withdraw(): HealthConsentWithdrawal {
        val uid = userRepository.getCurrentUserId() ?: return HealthConsentWithdrawal.SIGNED_OUT
        val mine = childInfoRepository.getAllChildInfo().first()
            .filter { it.createdByFirebaseUid == uid }

        var photosKept = false
        val withoutDeletedPhotos = mine.map { child ->
            val kept = child.medicalPhotos.filterNot { deletePhoto(it, child.familyId) }
            if (kept.isNotEmpty()) photosKept = true
            child to kept
        }
        if (photosKept) {
            withoutDeletedPhotos
                .filter { (child, kept) -> kept.size != child.medicalPhotos.size }
                .forEach { (child, kept) -> save(child.copy(medicalPhotos = kept), uid) }
            return HealthConsentWithdrawal.PHOTOS_NOT_DELETED
        }

        coroutineScope {
            launch { userRepository.setHealthConsent(null) }
            mine.filter { it.hasHealthData() }.forEach { child ->
                launch { save(child.withoutHealthData(), uid) }
            }
        }
        return HealthConsentWithdrawal.WITHDRAWN
    }

    /** Writes [child] as this parent's edit, queued for upload. */
    private suspend fun save(child: ChildInfo, uid: String) {
        childInfoRepository.upsertChildInfo(
            child.copy(lastModifiedBy = uid, syncedToFirestore = false, updatedAt = LocalDateTime.now())
        )
    }

    /** @return true when the photograph's object is gone and its reference may be dropped. */
    private suspend fun deletePhoto(reference: String, familyId: String?): Boolean = try {
        photoStorage.delete(reference, familyId)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        Log.e(TAG, "Deleting a medical photo while withdrawing health consent failed", e)
        false
    }

    private companion object {
        const val TAG = "HealthConsentManager"
    }
}

/**
 * Whether [this] record holds any of the health data the consent covers.
 */
internal fun ChildInfo.hasHealthData(): Boolean =
    medications.isNotEmpty() ||
        allergies.isNotEmpty() ||
        !medicalNotes.isNullOrBlank() ||
        medicalProfile != MedicalProfile() ||
        medicalPhotos.isNotEmpty()

/**
 * [this] record with every field the child-health consent covers cleared, and nothing else
 * touched: name, activities, emergency contacts, school and guests stay.
 */
internal fun ChildInfo.withoutHealthData(): ChildInfo = copy(
    medications = emptyList(),
    allergies = emptyList(),
    medicalNotes = null,
    medicalProfile = MedicalProfile(),
    medicalPhotos = emptyList()
)
