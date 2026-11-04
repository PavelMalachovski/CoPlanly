package com.coparently.app.data.school.bakalari

import com.coparently.app.data.http.RecordingServer
import com.coparently.app.data.http.answer
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Bakaláři's school directory and the address form every connection is keyed by (MON-8). The
 * responses are the samples in `bakalari-api/bakalari-api-v3`'s `schools_list.md`; the addresses
 * asked for are the ones OkHttp's `addPathSegment` wrote before MON-24.
 */
class BakalariSchoolDirectoryTest {

    @Test
    fun `towns are asked for as JSON - and the unnamed bucket is skipped`() = runTest {
        val server = RecordingServer {
            answer(
                """[{"name":"","schoolCount":2},{"name":"Albrechtice","schoolCount":1},""" +
                    """{"name":"Aš","schoolCount":3}]"""
            )
        }

        val towns = BakalariSchoolDirectory(server.client).towns()

        assertEquals(listOf(SchoolTown("Albrechtice", 1), SchoolTown("Aš", 3)), towns)
        val request = server.requests.single()
        assertEquals("https://sluzby.bakalari.cz/api/v1/municipality", request.url.toString())
        assertEquals("application/json", request.headers[HttpHeaders.Accept])
    }

    @Test
    fun `a town with a dot is asked for by the part before it - and school addresses are normalised`() = runTest {
        val server = RecordingServer {
            answer(
                """
                {"name":"beroun","schools":[
                  {"id":"SYDATAAABA","name":"Základní škola, Beroun - Závodí, Komenského 249","schoolUrl":"https://zavodi.bakalari.cz"},
                  {"id":"SYDATAADKO","name":"Manažerská akademie, soukromá střední škola","schoolUrl":"https://maberoun.bakalari.cz/"},
                  {"id":"PLAIN","name":"Insecure","schoolUrl":"http://insecure.example.cz"}
                ]}
                """.trimIndent()
            )
        }

        val schools = BakalariSchoolDirectory(server.client).schools("Ostrava-Mar.Hory")

        assertEquals(
            "https://sluzby.bakalari.cz/api/v1/municipality/Ostrava-Mar",
            server.requests.single().url.toString()
        )
        assertEquals(
            listOf("https://zavodi.bakalari.cz", "https://maberoun.bakalari.cz"),
            schools.map { it.baseUrl }
        )
    }

    @Test
    fun `a town is one encoded path segment as OkHttp wrote it`() = runTest {
        val server = RecordingServer { answer("""{"schools":[]}""") }
        val directory = BakalariSchoolDirectory(server.client)

        directory.schools("Ústí nad Labem")
        directory.schools("A/B")

        assertEquals(
            "https://sluzby.bakalari.cz/api/v1/municipality/%C3%9Ast%C3%AD%20nad%20Labem",
            server.requests[0].url.toString()
        )
        assertEquals("https://sluzby.bakalari.cz/api/v1/municipality/A%2FB", server.requests[1].url.toString())
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
    fun `an address is written as OkHttp wrote it - lowercase and in Punycode`() {
        // The event ids hash this text, so it must not change between builds: these are OkHttp
        // 4.12's readings of the same input, run on the JVM and pasted.
        assertEquals("https://skola.cz/x", BakalariUrls.normalize("https://Skola.CZ:443/x"))
        assertEquals("https://xn--kola-f6a.cz", BakalariUrls.normalize("https://škola.cz/"))
        assertEquals("https://xn--kola-f6a.cz", BakalariUrls.normalize("ŠKOLA.cz/Login.aspx"))
        assertEquals("https://xn--hkola-vdb.cz", BakalariUrls.normalize("https://H%C5%A1kola.cz/"))
        assertEquals("https://h:444/a%20b", BakalariUrls.normalize("https://h:444/a b"))
        assertEquals("https://h/y", BakalariUrls.normalize("https://h/./x/../y/"))
    }

    @Test
    fun `an address that is not https or not an address is refused`() {
        assertNull(BakalariUrls.normalize("http://skola.cz"))
        assertNull(BakalariUrls.normalize("ftp://skola.cz"))
        assertNull(BakalariUrls.normalize(""))
        assertNull(BakalariUrls.normalize("   "))
        assertNull(BakalariUrls.normalize("https://a..b"))
        assertNull(BakalariUrls.normalize("https://skola.cz:0"))
        assertNull(BakalariUrls.normalize("https://skola.cz:65536"))
    }
}
