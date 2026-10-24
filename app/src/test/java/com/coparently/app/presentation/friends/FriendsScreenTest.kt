package com.coparently.app.presentation.friends

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.coparently.app.R
import com.coparently.app.domain.friends.CalendarFriendGrant
import com.coparently.app.domain.repository.FriendRepository
import com.coparently.app.presentation.common.testParentsSource
import com.coparently.app.presentation.theme.CoPlanlyTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The family friend's side of the Friends screen (final review of phase 2, finding 4).
 *
 * A grandmother admitted by one family is sent a second family's code, and the message tells her
 * where to type it: Family → Family friends → "I have a code from the parents". Her side of the
 * screen listed only her families and her profile, so the row was not there, and a friend of two
 * families — which the grants support since L-5 — could not be admitted to the second in the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h851dp")
class FriendsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `a family friend can type a second family's code`() {
        val repository = mockk<FriendRepository> {
            every { observeFamilyFriends() } returns flowOf(emptyList())
            every { observeMyGrants() } returns flowOf(listOf(grandmasGrant))
            every { observeMyProfile() } returns flowOf(null)
        }
        // Built outside the composition, as lint's ViewModelConstructorInComposable asks.
        val viewModel = FriendViewModel(repository, testParentsSource())
        rule.setContent {
            CoPlanlyTheme {
                FriendsScreen(
                    onNavigateUp = {},
                    onOpenFriend = {},
                    onOpenMyProfile = {},
                    onOpenFriendCalendar = {},
                    viewModel = viewModel
                )
            }
        }

        // Her side of the screen, not a parent's: her family's calendar, and no invitation to send.
        rule.onNodeWithText(context.getString(R.string.friend_calendar_title)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.friend_invite_action)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.friend_redeem_row_title)).assertExists()
    }

    private companion object {
        val grandmasGrant = CalendarFriendGrant(
            friendUid = "f1",
            name = "Grandma",
            familyParents = listOf("u1", "u2"),
            grantedBy = "u1",
            grantedAtMillis = 1L,
            expiresAtMillis = Long.MAX_VALUE,
            familyId = "u1__u2"
        )
    }
}
