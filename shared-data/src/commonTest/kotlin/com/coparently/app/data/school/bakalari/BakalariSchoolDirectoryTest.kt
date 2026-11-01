package com.coparently.app.data.school.bakalari

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Bakaláři's school directory and the address form every connection is keyed by (MON-8). The
 * responses are the samples in `bakalari-api/bakalari-api-v3`'s `schools_list.md`.
 */
class BakalariSchoolDirectoryTest {

    private val server = MockWebServer()
    private val directory by lazy {
        BakalariSchoolDirectory(OkHttpClient(), server.url("/api/v1/municipality"), Dispatchers.IO)
    }

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `towns are asked for as JSON, and the unnamed bucket is skipped`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"name":"","schoolCount":2},{"name":"Albrechtice","schoolCount":1},{"name":"Aš","schoolCount":3}]"""
            )
        )

        val towns = directory.towns()

        assertEquals(listOf(SchoolTown("Albrechtice", 1), SchoolTown("Aš", 3)), towns)
        assertEquals("application/json", server.takeRequest().getHeader("Accept"))
    }

    @Test
    fun `a town with a dot is asked for by the part before it, and school addresses are normalised`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"name":"beroun","schools":[
                  {"id":"SYDATAAABA","name":"Základní škola, Beroun - Závodí, Komenského 249","schoolUrl":"https://zavodi.bakalari.cz"},
                  {"id":"SYDATAADKO","name":"Manažerská akademie, soukromá střední škola","schoolUrl":"https://maberoun.bakalari.cz/"},
                  {"id":"PLAIN","name":"Insecure","schoolUrl":"http://insecure.example.cz"}
                ]}
                """.trimIndent()
            )
        )

        val schools = directory.schools("Ostrava-Mar.Hory")

        assertEquals("/api/v1/municipality/Ostrava-Mar", server.takeRequest().path)
        assertEquals(
            listOf("https://zavodi.bakalari.cz", "https://maberoun.bakalari.cz"),
            schools.map { it.baseUrl }
        )
    }

    @Test
    fun `an address is normalised to https with no trailing slash or login page`() {
        assertEquals("https://zavodi.bakalari.cz", BakalariUrls.normalize("zavodi.bakalari.cz"))
        assertEquals("https://zavodi.bakalari.cz", BakalariUrls.normalize(" https://zavodi.bakalari.cz/ "))
        assertEquals("https://www.example.com:444", BakalariUrls.normalize("https://www.example.com:444/login"))
        assertEquals("https://skola.cz/bakaweb", BakalariUrls.normalize("https://skola.cz/bakaweb/next/login.aspx"))
        assertEquals("https://skola.cz/bakaweb", BakalariUrls.normalize("https://skola.cz/bakaweb/login?x=1"))
    }

    @Test
    fun `an address that is not https or not an address is refused`() {
        assertNull(BakalariUrls.normalize("http://skola.cz"))
        assertNull(BakalariUrls.normalize("ftp://skola.cz"))
        assertNull(BakalariUrls.normalize(""))
        assertNull(BakalariUrls.normalize("   "))
    }
}
