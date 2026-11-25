package com.coparently.app.presentation.common

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.files.RecordPhoto
import com.coparently.app.domain.files.RecordPhotoCodec
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Pet
import com.coparently.app.presentation.platform.ProvideAndroidPlatform
import com.coparently.app.presentation.theme.CoPlanlyTheme
import com.coparently.app.presentation.theme.ContrastLevel
import com.coparently.app.testing.RobolectricTestApplication
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * On Home, where the header's avatars only show, a tap on a child's or a pet's avatar opens its
 * photo full screen. On the Calendar (a filter is passed) the avatars stay the filter's toggles.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class FamilyAvatarsPhotoTest {

    @get:Rule
    val rule = createComposeRule()

    private val stamp = LocalDateTime(2026, 5, 1, 9, 0)

    private fun reference(prefix: String, recordId: String) = RecordPhotoCodec.encode(
        RecordPhoto(
            storagePath = "$prefix/solo_uid/$recordId/p.jpg",
            contentType = "image/jpeg",
            sizeBytes = 1_000L,
            sha256 = "a".repeat(64)
        )
    )

    private fun child(name: String, photo: String?) = FamilyMember(
        ref = FamilyMemberRef.Child("c-$name"),
        name = name,
        child = ChildInfo(
            id = "c-$name",
            childName = name,
            dateOfBirth = null,
            createdAt = stamp,
            updatedAt = stamp,
            photo = photo
        )
    )

    private fun pet(name: String, photo: String?) = FamilyMember(
        ref = FamilyMemberRef.Pet("p-$name"),
        name = name,
        pet = Pet(
            id = "p-$name",
            name = name,
            photos = listOfNotNull(photo),
            createdAt = stamp,
            updatedAt = stamp
        )
    )

    private val withPhoto get() = child("Lenička", reference("child_photos", "c-Lenička"))

    private fun show(members: List<FamilyMember>, filter: MemberFilter? = null) {
        rule.setContent {
            ProvideAndroidPlatform {
                CoPlanlyTheme(darkTheme = false, contrastLevel = ContrastLevel.STANDARD) {
                    CompositionLocalProvider(LocalPhotoViewerUid provides "uid") {
                        FamilyAvatarsAndGear(members = members, onSettings = null, filter = filter)
                    }
                }
            }
        }
    }

    @Test
    fun `a member with a viewable photo is a button that opens it`() {
        show(listOf(withPhoto))

        rule.onNodeWithContentDescription("Open Lenička’s photo")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        rule.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun `a pet with a viewable photo opens it too`() {
        show(listOf(pet("Rex", reference("pet_photos", "p-Rex"))))

        rule.onNodeWithContentDescription("Open Rex’s photo").performClick()

        rule.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun `a member without a photo has no click action`() {
        show(listOf(withPhoto, pet("Rex", null)))

        rule.onNodeWithContentDescription("Rex").assert(!hasClickAction())
        rule.onAllNodesWithContentDescription("Open Rex’s photo").assertCountEquals(0)
    }

    @Test
    fun `members with no photo at all keep the row's one description and no click`() {
        show(listOf(child("Anna", null), pet("Rex", null)))

        rule.onNodeWithContentDescription("Children: Anna, Rex").assert(!hasClickAction())
    }

    @Test
    fun `on the Calendar a single member stays a picture even with a photo`() {
        show(listOf(withPhoto), filter = MemberFilter(selected = emptyList(), onToggle = {}))

        rule.onAllNodesWithContentDescription("Open Lenička’s photo").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Close").assertCountEquals(0)
    }

    @Test
    fun `the overflow disc names the hidden members once a photo splits the row`() {
        // Three slots, the last one "+n": Lenička and Anna are drawn, Rex and Bára are not.
        show(listOf(withPhoto, child("Anna", null), pet("Rex", null), child("Bára", null)))

        rule.onNodeWithContentDescription("Children: Rex, Bára").assert(!hasClickAction())
    }
}
