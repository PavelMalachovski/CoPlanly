package com.coparently.app.data.repository

import com.coparently.app.data.family.FamilyOption
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.remote.firebase.DocumentQuery
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.remote.firebase.StoredDocument
import com.coparently.app.domain.professionals.ProfessionalGrant
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The professionals a parent's family screen lists (MON-18): the family on screen's grants only.
 *
 * The one list query the rule admits a parent names every grant over every family they are in, so
 * with two families the screen listed the other family's mediator or lawyer, with Consent and
 * Revoke beside them (isolation audit, October 2026). The list is kept to the family on screen and
 * follows a switch, as the family friends' list does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfessionalRepositoryImplTest {

    /** The grants query's listener: what the test sends, the repository receives. */
    private val snapshots = MutableSharedFlow<List<StoredDocument>>(replay = 1)
    private val family = MutableStateFlow<FamilyOption?>(FamilyOption(BOB_FAMILY, "bob"))

    private val firestore = RecordingFirestoreClient().apply {
        queryFlow = { query ->
            check(query == DocumentQuery("professional_grants").whereArrayContains("familyParents", ME)) {
                "unexpected query $query"
            }
            snapshots
        }
    }
    private val authService = mockk<SignedInUser> {
        every { getCurrentUid() } returns ME
    }
    private val selectedFamilySource = mockk<SelectedFamilySource> {
        every { observe(ME) } returns family
    }

    private val repository = ProfessionalRepositoryImpl(
        firestore = firestore,
        authService = authService,
        pairingFunctions = mockk(relaxed = true),
        selectedFamilySource = selectedFamilySource,
        custodyDataSource = mockk(relaxed = true),
        planDataSource = mockk(relaxed = true)
    )

    @Test
    fun `only the family on screen's professionals are listed, and a switch follows`() = runTest {
        val seen = mutableListOf<List<ProfessionalGrant>>()
        backgroundScope.launch { repository.observeFamilyGrants().toList(seen) }
        runCurrent()

        snapshots.emit(listOf(grant(BOB_FAMILY, "mediator"), grant(CAROL_FAMILY, "lawyer")))
        runCurrent()
        assertEquals(listOf("mediator"), seen.last().map { it.proUid })

        family.value = FamilyOption(CAROL_FAMILY, "carol")
        runCurrent()
        assertEquals(listOf("lawyer"), seen.last().map { it.proUid })
    }

    @Test
    fun `without a family on screen nobody is listed`() = runTest {
        family.value = null
        val seen = mutableListOf<List<ProfessionalGrant>>()
        backgroundScope.launch { repository.observeFamilyGrants().toList(seen) }
        runCurrent()

        snapshots.emit(listOf(grant(BOB_FAMILY, "mediator")))
        runCurrent()

        assertEquals(emptyList(), seen.last())
    }

    private fun grant(familyId: String, proUid: String): StoredDocument {
        val partner = familyId.substringAfter("__")
        val data = mapOf<String, Any>(
            "familyId" to familyId,
            "familyParents" to listOf(ME, partner),
            "proUid" to proUid,
            "role" to "mediator",
            "name" to proUid,
            "invitedBy" to ME,
            "grantedAtMillis" to 1L,
            "expiresAtMillis" to Long.MAX_VALUE,
            "consents" to mapOf(ME to 1L)
        )
        return StoredDocument("${familyId}__$proUid", data)
    }

    private companion object {
        const val ME = "alice"
        const val BOB_FAMILY = "alice__bob"
        const val CAROL_FAMILY = "alice__carol"
    }
}
