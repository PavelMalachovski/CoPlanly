package com.coparently.app.data.repository

import com.coparently.app.data.remote.firebase.CallableCode
import com.coparently.app.data.remote.firebase.CallableException
import com.coparently.app.data.remote.firebase.RecordingCallableFunctions
import com.coparently.app.domain.feed.CalendarFeedLimitException
import com.coparently.app.domain.feed.CalendarFeedLink
import com.coparently.app.domain.feed.CreatedCalendarFeed
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The calendar feed's three callables (MON-17) as the repository makes them since it went through
 * [com.coparently.app.data.remote.firebase.CallableFunctions] (MON-24 phase 2, Task 8d): the same
 * names and payloads, the same reading of their answers, and the one refusal it names.
 */
class CalendarFeedRepositoryImplTest {

    private val functions = RecordingCallableFunctions()
    private val repository = CalendarFeedRepositoryImpl(functions)

    @Test
    fun `a link is created for the family in the reader's language`() = runTest {
        functions.answer = { _, _ ->
            mapOf("feedId" to "f1", "url" to "https://x/f", "webcalUrl" to "webcal://x/f")
        }

        val created = repository.create(familyId = "a__b", language = "cs").getOrThrow()

        assertEquals(CreatedCalendarFeed("f1", "https://x/f", "webcal://x/f"), created)
        assertEquals<List<Pair<String, Map<String, Any>>>>(
            listOf("createCalendarFeed" to mapOf("familyId" to "a__b", "locale" to "cs")),
            functions.calls
        )
    }

    @Test
    fun `an answer without its URL is a failure`() = runTest {
        functions.answer = { _, _ -> mapOf("feedId" to "f1") }

        assertTrue(repository.create("a__b", "en").isFailure)
    }

    @Test
    fun `the links are listed with their numbers as JSON gives them and an entry without an id dropped`() =
        runTest {
            functions.answer = { _, _ ->
                mapOf(
                    "feeds" to listOf(
                        mapOf(
                            "feedId" to "f1",
                            "familyId" to "a__b",
                            "createdAtMillis" to 5,
                            "lastUsedAtMillis" to 7.0
                        ),
                        mapOf("feedId" to " ", "familyId" to "a__b"),
                        "not a map"
                    )
                )
            }

            val links = repository.list().getOrThrow()

            assertEquals(listOf(CalendarFeedLink("f1", "a__b", 5L, 7L)), links)
            assertEquals(listOf("listCalendarFeeds"), functions.calls.map { it.first })
            assertEquals(emptyMap(), functions.calls.single().second)
        }

    @Test
    fun `no answer at all lists nothing`() = runTest {
        assertEquals(emptyList(), repository.list().getOrThrow())
    }

    @Test
    fun `a revoke names the link`() = runTest {
        assertTrue(repository.revoke("f1").isSuccess)
        assertEquals<List<Pair<String, Map<String, Any>>>>(
            listOf("revokeCalendarFeed" to mapOf("feedId" to "f1")),
            functions.calls
        )
    }

    @Test
    fun `the server's limit is the limit's own failure`() = runTest {
        functions.answer = { _, _ -> throw RecordingCallableFunctions.refusal(CallableCode.RESOURCE_EXHAUSTED) }

        assertIs<CalendarFeedLimitException>(repository.create("a__b", "en").exceptionOrNull())
    }

    @Test
    fun `any other refusal is handed on as it came`() = runTest {
        val refusal = RecordingCallableFunctions.refusal(CallableCode.PERMISSION_DENIED)
        functions.answer = { _, _ -> throw refusal }

        val failure = repository.revoke("f1").exceptionOrNull()

        assertSame<Throwable?>(refusal, failure)
        assertIs<CallableException>(failure)
    }
}
