package com.coparently.app.data.http

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What OkHttp 4.12 made of these addresses, path segments and form fields, the version the school
 * import shipped with before MON-24, held as literals so the iPhone simulator is held to them too
 * (as `StoredJsonVectorsTest` holds `StoredJson` to Gson).
 *
 * Each expected text was printed by running OkHttp 4.12's `toHttpUrlOrNull`,
 * `HttpUrl.Builder.addPathSegments`/`addPathSegment` and `FormBody.Builder` on the JVM, and pasted.
 * The same comparison was run over two hundred thousand generated addresses with no difference
 * (MON-24's Task 5 report). OkHttp itself is not the oracle in a test here: the Ktor engine brings
 * OkHttp 5, which encodes a form's space as `+` and maps `ß` differently, so it would hold this
 * code to bytes the app never sent.
 */
class WebAddressVectorsTest {

    @Test
    fun `addresses are read as OkHttp 4 read them`() {
        for ((input, expected) in ADDRESSES) {
            val address = WebAddress.parse(input)
            val actual = address?.let {
                listOf(
                    it.isHttps.toString(),
                    it.host,
                    it.port.toString(),
                    it.encodedPathSegments.joinToString("/"),
                    // OkHttp carried a user name, query or fragment into the address; WebAddress drops
                    // them (see its KDoc), so only the parse is compared for those.
                    if (expected?.endsWith("|-") == true) "-" else it.withPathSegments("api/3/user")
                ).joinToString("|")
            }
            assertEquals(expected, actual, "for [$input]")
        }
    }

    @Test
    fun `a path segment is encoded as addPathSegment encoded it`() {
        val directory = checkNotNull(WebAddress.parse("https://sluzby.bakalari.cz/api/v1/municipality"))
        for ((segment, expected) in SEGMENTS) {
            assertEquals(expected, directory.withPathSegment(segment), "for [$segment]")
        }
    }

    @Test
    fun `a form field is encoded as FormBody encoded it`() {
        for ((value, expected) in FORM_FIELDS) {
            assertEquals(expected, PercentEncoding.formField(value), "for [$value]")
        }
    }

    @Test
    fun `a host label is written in Punycode as RFC 3492 and the JVM IDN write it`() {
        assertEquals("kola-f6a", Punycode.encode("škola"))
        assertEquals("ikov-kola-p3b72ab", Punycode.encode("žižkov-škola"))
        assertEquals("bcher-kva", Punycode.encode("bücher"))
    }

    private companion object {
        /** An address, and `isHttps|host|port|segments|with api/3/user` — or null for no address. */
        val ADDRESSES = listOf(
            "https://zavodi.bakalari.cz" to "true|zavodi.bakalari.cz|443||https://zavodi.bakalari.cz/api/3/user",
            "https://Skola.CZ:443/x" to "true|skola.cz|443|x|https://skola.cz/x/api/3/user",
            "HTTPS://SKOLA.cz/Bakaweb/" to "true|skola.cz|443|Bakaweb/|https://skola.cz/Bakaweb/api/3/user",
            "  https://skola.cz/a b  " to "true|skola.cz|443|a%20b|https://skola.cz/a%20b/api/3/user",
            "https://\u0161kola.cz/bakaweb" to
                "true|xn--kola-f6a.cz|443|bakaweb|https://xn--kola-f6a.cz/bakaweb/api/3/user",
            "https://\u017Di\u017Ekov-\u0161kola.cz" to
                "true|xn--ikov-kola-p3b72ab.cz|443||https://xn--ikov-kola-p3b72ab.cz/api/3/user",
            "https://H%C5%A1kola.cz/" to "true|xn--hkola-vdb.cz|443||https://xn--hkola-vdb.cz/api/3/user",
            "https://%41.cz/" to "true|a.cz|443||https://a.cz/api/3/user",
            "https://skola.cz:444/bakaweb/next/login.aspx" to
                "true|skola.cz|444|bakaweb/next/login.aspx|https://skola.cz:444/bakaweb/next/login.aspx/api/3/user",
            "https://skola.cz:080" to "true|skola.cz|80||https://skola.cz:80/api/3/user",
            "https://skola.cz:" to "true|skola.cz|443||https://skola.cz/api/3/user",
            "https://h:+44/" to "true|h|44||https://h:44/api/3/user",
            "https:\\\\skola.cz\\a\\b" to "true|skola.cz|443|a/b|https://skola.cz/a/b/api/3/user",
            "https:skola.cz/a" to "true|skola.cz|443|a|https://skola.cz/a/api/3/user",
            "https:///skola.cz/a" to "true|skola.cz|443|a|https://skola.cz/a/api/3/user",
            "https://user:pw@skola.cz/p" to "true|skola.cz|443|p|-",
            "https://a@b@skola.cz/p" to "true|skola.cz|443|p|-",
            "https://h/./x/../y/" to "true|h|443|y/|https://h/y/api/3/user",
            "https://h/x/%2e%2E/y" to "true|h|443|y|https://h/y/api/3/user",
            "https://h/a/.." to "true|h|443||https://h/api/3/user",
            "https://h/.." to "true|h|443||https://h/api/3/user",
            "https://h/a%zzb" to "true|h|443|a%zzb|https://h/a%zzb/api/3/user",
            "https://h/a%41b" to "true|h|443|a%41b|https://h/a%41b/api/3/user",
            "https://h/a\tb" to "true|h|443|ab|https://h/ab/api/3/user",
            "https://h/\u010D/\u0159?x=1#y" to "true|h|443|%C4%8D/%C5%99|-",
            "https://h/a\"<>^`{}|b" to
                "true|h|443|a%22%3C%3E%5E%60%7B%7D%7Cb|https://h/a%22%3C%3E%5E%60%7B%7D%7Cb/api/3/user",
            "https://h/!\$&'()*+,;=:@[]~" to "true|h|443|!\$&'()*+,;=:@[]~|https://h/!\$&'()*+,;=:@[]~/api/3/user",
            "http://skola.cz" to "false|skola.cz|80||http://skola.cz/api/3/user",
            "https://a.b." to "true|a.b.|443||https://a.b./api/3/user",
            "https://." to "true|.|443||https://./api/3/user",
            "https://a..b/" to null,
            "https://.cz" to null,
            "https://h:0/" to null,
            "https://h:65536/" to null,
            "https://h:12a/" to null,
            "ftp://skola.cz" to null,
            "skola.cz" to null,
            "https://" to null,
            "https:// h" to null,
            "https://h h" to null,
            "https://a%zz.cz" to null,
            "https://[::1]/" to null, // OkHttp read an IPv6 literal; WebAddress refuses one
        )

        /** A town, and the directory address OkHttp built for it. */
        val SEGMENTS = listOf(
            "Ostrava-Mar" to "https://sluzby.bakalari.cz/api/v1/municipality/Ostrava-Mar",
            "\u00DAst\u00ED nad Labem" to "https://sluzby.bakalari.cz/api/v1/municipality/%C3%9Ast%C3%AD%20nad%20Labem",
            "Praha 1" to "https://sluzby.bakalari.cz/api/v1/municipality/Praha%201",
            "A/B" to "https://sluzby.bakalari.cz/api/v1/municipality/A%2FB",
            "a\\b" to "https://sluzby.bakalari.cz/api/v1/municipality/a%5Cb",
            "a?b#c" to "https://sluzby.bakalari.cz/api/v1/municipality/a%3Fb%23c",
            "%41" to "https://sluzby.bakalari.cz/api/v1/municipality/%2541",
            "x+y" to "https://sluzby.bakalari.cz/api/v1/municipality/x+y",
            "" to "https://sluzby.bakalari.cz/api/v1/municipality/",
            "." to "https://sluzby.bakalari.cz/api/v1/municipality",
            ".." to "https://sluzby.bakalari.cz/api/v1/",
            "Fr\u00FDdek-M\u00EDstek" to "https://sluzby.bakalari.cz/api/v1/municipality/Fr%C3%BDdek-M%C3%ADstek",
            "a\tb" to "https://sluzby.bakalari.cz/api/v1/municipality/a%09b",
        )

        /** A form value, and how OkHttp's `FormBody` wrote it. */
        val FORM_FIELDS = listOf(
            "novak" to "novak",
            "Nov\u00E1k Jan" to "Nov%C3%A1k%20Jan",
            "a-b.c_d*e~f" to "a-b.c_d*e%7Ef",
            "p@ss w+rd&x=y" to "p%40ss%20w%2Brd%26x%3Dy",
            "%20" to "%2520",
            "!\"#\$%&'()+,/:;<=>?@[\\]^`{|}" to
                "%21%22%23%24%25%26%27%28%29%2B%2C%2F%3A%3B%3C%3D%3E%3F%40%5B%5C%5D%5E%60%7B%7C%7D",
            "\u00E9\u20AC\uD83D\uDE00" to "%C3%A9%E2%82%AC%F0%9F%98%80",
            "\u0001\u007F" to "%01%7F",
            "" to "",
        )
    }
}
