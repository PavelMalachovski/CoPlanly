package com.coparently.app.data.school.bakalari

import com.coparently.app.data.school.bakalari.BakalariJson.objects
import com.coparently.app.data.school.bakalari.BakalariJson.text
import com.coparently.app.di.SchoolHttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A town in Bakaláři's school directory.
 *
 * @property name The town as the directory writes it.
 * @property schoolCount How many schools it lists there.
 */
data class SchoolTown(val name: String, val schoolCount: Int)

/**
 * A school in Bakaláři's directory.
 *
 * @property name The school's name.
 * @property baseUrl Its server's address, normalised by [BakalariUrls.normalize].
 */
data class DirectorySchool(val name: String, val baseUrl: String)

/**
 * Bakaláři's public directory of schools, `sluzby.bakalari.cz` (MON-8): pick a town, then a
 * school, both fetched live. Unauthenticated, and asked for JSON explicitly — without
 * `Accept: application/json` it answers in XML.
 *
 * The directory is not complete: some schools host their own server on their own domain, which
 * is why the connect flow also takes an address typed by hand. Nothing here is cached; a third
 * party's mirror of the list is deliberately not used.
 */
@Singleton
class BakalariSchoolDirectory internal constructor(
    private val http: OkHttpClient,
    private val directoryUrl: HttpUrl,
    private val io: CoroutineDispatcher
) {

    /** The production directory. */
    @Inject
    constructor(@SchoolHttpClient http: OkHttpClient) : this(http, DIRECTORY.toHttpUrl(), Dispatchers.IO)

    /** Every town with at least one school, in the directory's order. The unnamed bucket is skipped. */
    suspend fun towns(): List<SchoolTown> =
        BakalariJson.arrayOf(fetch(directoryUrl)).mapNotNull { element ->
            val town = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = town.text("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SchoolTown(name, town.text("schoolCount")?.toIntOrNull() ?: 0)
        }

    /**
     * The schools in [town].
     *
     * A town whose name holds a dot answers 404 unless only the part before the dot is sent
     * ("Ostrava-Mar.Hory" → "Ostrava-Mar"), which the directory's own documentation describes.
     */
    suspend fun schools(town: String): List<DirectorySchool> {
        val url = directoryUrl.newBuilder().addPathSegment(town.substringBefore('.').trim()).build()
        return BakalariJson.objectOf(fetch(url)).objects("schools").mapNotNull { school ->
            val name = school.text("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val baseUrl = BakalariUrls.normalize(school.text("schoolUrl")) ?: return@mapNotNull null
            DirectorySchool(name, baseUrl)
        }
    }

    private suspend fun fetch(url: HttpUrl): String = withContext(io) {
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        val (status, body) = try {
            http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw BakalariException.Network(e)
        }
        if (status != HTTP_OK) throw BakalariException.Http(status)
        body
    }

    private companion object {
        const val DIRECTORY = "https://sluzby.bakalari.cz/api/v1/municipality"
        const val HTTP_OK = 200
    }
}

/** How a Bakaláři server's address is written everywhere in the app (MON-8). */
object BakalariUrls {

    /**
     * [input] as `https://host[:port][/path]` with no trailing slash, query or login page — the
     * form the event ids hash, so one school is always one address — or null when it is not a
     * web address or asks for anything but https.
     *
     * Takes what a parent copies from their school's login page ("zavodi.bakalari.cz",
     * "https://www.example.com:444/login", "https://skola.cz/bakaweb/next/login.aspx/"). An address
     * with no scheme is read as https; `http://` is refused rather than upgraded, because the
     * parent should know their school's page was not encrypted.
     */
    fun normalize(input: String?): String? {
        val text = input?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val withScheme = if ("://" in text) text else "https://$text"
        val url = runCatching { withScheme.toHttpUrl() }.getOrNull()?.takeIf { it.isHttps } ?: return null
        val segments = url.encodedPathSegments.filter { it.isNotEmpty() }.toMutableList()
        while (segments.lastOrNull()?.lowercase()?.let { it in LOGIN_SEGMENTS } == true) {
            segments.removeAt(segments.lastIndex)
        }
        val port = if (url.port == DEFAULT_HTTPS_PORT) "" else ":${url.port}"
        val path = segments.joinToString("") { "/$it" }
        return "https://${url.host}$port$path"
    }

    private const val DEFAULT_HTTPS_PORT = 443
    private val LOGIN_SEGMENTS = setOf("login", "login.aspx", "next")
}
