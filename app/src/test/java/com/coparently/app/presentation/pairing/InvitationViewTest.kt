package com.coparently.app.presentation.pairing

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.coparently.app.R
import com.coparently.app.presentation.theme.CoPlanlyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The invitation an unlinked parent answers before a code is redeemed (spec G2). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h851dp")
class InvitationViewTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val answers = mutableListOf<Boolean>()

    private fun show(name: String?) {
        rule.setContent { CoPlanlyTheme { InvitationView(inviterName = name, onAnswer = { answers += it }) } }
    }

    @Test
    fun `names the inviter and states the four facts`() {
        show("Alice")
        rule.onNodeWithText(context.getString(R.string.pairing_invitation_title, "Alice")).assertIsDisplayed()
        listOf(
            R.string.pairing_invitation_fact_same,
            R.string.pairing_invitation_fact_agree,
            R.string.pairing_invitation_fact_messages,
            R.string.pairing_invitation_fact_unlink
        ).forEach { rule.onNodeWithText(context.getString(it)).performScrollTo().assertExists() }
    }

    @Test
    fun `the primary button links`() {
        show("Alice")
        rule.onNodeWithText(context.getString(R.string.pairing_invitation_link)).performClick()
        assertEquals(listOf(true), answers)
    }

    @Test
    fun `not now declines`() {
        show("Alice")
        rule.onNodeWithText(context.getString(R.string.pairing_invitation_not_now)).performScrollTo().performClick()
        assertEquals(listOf(false), answers)
    }

    @Test
    fun `without a name it still invites`() {
        show(null)
        rule.onNodeWithText(context.getString(R.string.pairing_invitation_title_unnamed)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.pairing_invitation_link)).assertIsDisplayed()
    }

    @Test
    fun `back declines`() {
        show("Alice")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(listOf(false), answers)
    }
}
