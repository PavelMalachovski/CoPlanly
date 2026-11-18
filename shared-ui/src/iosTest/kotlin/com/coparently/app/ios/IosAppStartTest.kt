package com.coparently.app.ios

import com.coparently.app.data.remote.firebase.PushDestination
import com.coparently.app.presentation.LaunchLink
import com.coparently.app.presentation.LaunchLinks
import com.coparently.app.presentation.PushTap
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * [IosApp]'s two orders that carry a promise (MON-24 phase 3, part B, Task 12): `start` claims its
 * once-per-process guard ([StartOnce]) before any side effect, and a chat link that names a family
 * is armed only once the family switch has returned ([IosApp.openLink], CLAUDE.md item 15). Since
 * phase 4 part C a tapped push keeps the same order ([IosApp.openTap]), and opens nothing for
 * another account.
 *
 * `IosApp` itself is a process-wide object whose `start` configures Firebase and starts the global
 * Koin graph, so these drive the two pieces it is built from rather than the object.
 */
class IosAppStartTest {

    @Test
    fun `a second start runs nothing`() {
        val guard = StartOnce("runs once")
        var sideEffects = 0
        fun start() {
            guard.claim()
            sideEffects++
        }

        start()
        assertFailsWith<IllegalStateException> { start() }

        assertEquals(1, sideEffects)
    }

    @Test
    fun `a start that failed part-way still refuses the next`() {
        val guard = StartOnce("runs once")
        var configured = 0
        fun start() {
            guard.claim()
            configured++
            error("Koin refused")
        }

        assertFailsWith<IllegalStateException> { start() }
        assertFailsWith<IllegalStateException> { start() }

        assertEquals(1, configured)
    }

    @Test
    fun `a chat link that names a family is armed after the switch`() = runTest {
        val order = mutableListOf<String>()
        val link = LaunchLink.Chat(familyId = "alice__carol", conversationId = "alice__carol")

        IosApp.openLink(
            link,
            switchTo = { familyId ->
                yield()
                order += "select $familyId"
            },
            arm = { order += "arm ${(it as LaunchLink.Chat).conversationId}" },
        )

        assertEquals(listOf("select alice__carol", "arm alice__carol"), order)
    }

    @Test
    fun `a switch that fails still arms the link`() = runTest {
        val armed = mutableListOf<LaunchLink>()
        val link = LaunchLink.Chat(familyId = "alice__carol", conversationId = null)

        IosApp.openLink(link, switchTo = { error("offline") }, arm = { armed += it })

        assertEquals(listOf<LaunchLink>(link), armed)
    }

    @Test
    fun `a link that names no family switches nothing`() = runTest {
        val order = mutableListOf<String>()

        IosApp.openLink(
            LaunchLink.Pair("4F7K2M"),
            switchTo = { order += "select $it" },
            arm = { order += "arm" },
        )

        assertEquals(listOf("arm"), order)
    }

    @Test
    fun `a push from another family switches to it before its screen is armed`() = runTest {
        val order = mutableListOf<String>()
        val data = mapOf("type" to "expense_added", "targetUserId" to "alice", "familyId" to "alice__carol")
        val tap = checkNotNull(LaunchLinks.forPush(data, signedInUid = "alice"))

        IosApp.openTap(
            tap,
            switchTo = { familyId ->
                yield()
                order += "select $familyId"
            },
            arm = { order += "arm $it" },
            armDestination = { order += "open $it" },
        )

        assertEquals(listOf("select alice__carol", "open EXPENSES"), order)
    }

    @Test
    fun `a chat push switches the family before it arms its thread`() = runTest {
        val order = mutableListOf<String>()
        val data = mapOf(
            "type" to "chat_message",
            "targetUserId" to "alice",
            "familyId" to "alice__carol",
            "conversationId" to "alice__carol",
        )

        IosApp.openTap(
            checkNotNull(LaunchLinks.forPush(data, signedInUid = "alice")),
            switchTo = { order += "select $it" },
            arm = { order += "arm ${(it as LaunchLink.Chat).conversationId}" },
            armDestination = { order += "open $it" },
        )

        assertEquals(listOf("select alice__carol", "arm alice__carol"), order)
    }

    @Test
    fun `a push for another uid opens nothing`() {
        val data = mapOf("type" to "expense_added", "targetUserId" to "bob", "familyId" to "alice__bob")

        assertNull(LaunchLinks.forPush(data, signedInUid = "alice"))
    }

    @Test
    fun `a push whose family switch fails still opens its screen`() = runTest {
        val opened = mutableListOf<PushDestination>()
        val tap = PushTap(familyId = "alice__carol", link = null, destination = PushDestination.CHANGE_REQUESTS)

        IosApp.openTap(tap, switchTo = { error("offline") }, arm = {}, armDestination = { opened += it })

        assertEquals(listOf(PushDestination.CHANGE_REQUESTS), opened)
    }
}
