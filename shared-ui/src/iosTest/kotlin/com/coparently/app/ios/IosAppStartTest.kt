package com.coparently.app.ios

import com.coparently.app.presentation.LaunchLink
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [IosApp]'s two orders that carry a promise (MON-24 phase 3, part B, Task 12): `start` claims its
 * once-per-process guard ([StartOnce]) before any side effect, and a chat link that names a family
 * is armed only once the family switch has returned ([IosApp.openLink], CLAUDE.md item 15).
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
}
