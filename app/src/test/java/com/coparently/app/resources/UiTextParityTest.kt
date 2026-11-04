package com.coparently.app.resources

import android.content.res.Resources
import androidx.compose.ui.test.junit4.createComposeRule
import com.coparently.app.R
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.UiTextResolver
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberUiTextResolver
import com.coparently.app.resources.ResourceParity.LANGUAGES
import com.coparently.app.resources.ResourceParity.androidResources
import com.coparently.app.testing.RobolectricTestApplication
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * A [UiText] reads the same through Compose Multiplatform as it read through Android's resources
 * before it moved to `:shared-ui` (MON-24 phase 2, slice 9c), in each of the five languages: in
 * composition ([asString]) and outside it ([UiTextResolver], the snackbar's path).
 *
 * The samples cover each case and each kind of argument: none, a text, a number through `%1$d`,
 * a nested [UiText], a [UiText.Date] inside a sentence and inside a plural, and the plural forms of
 * the Slavic languages (1, 2, 5, 21). The Android side is what `UiText` resolved to before:
 * `getString`/`getQuantityString` with the arguments resolved first, and `java.time`'s medium date
 * and full day name in the configuration's locale. The language is set as the app sets it: the
 * configuration's and the process's default (`ResolvedAppLocale`), before the activity starts.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class)
class UiTextParityTest(private val language: String) {

    private val composeRule = createComposeRule()

    /** The language before the compose rule launches its activity, as the screenshots set it. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(LanguageRule(language)).around(composeRule)

    @Before
    fun setUp() = ResourceParity.ensureComposeResourcesContext()

    private val locale: Locale = Locale.forLanguageTag(language)

    private fun medium(date: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date.toJavaLocalDate())

    private fun dayName(day: DayOfWeek): String =
        day.toJavaDayOfWeek().getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }

    /** Each sample beside what Android's resources made of it before slice 9c. */
    private fun samples(android: Resources): List<Pair<UiText, String>> = buildList {
        add(UiText.Res(Res.string.common_error_generic) to android.getString(R.string.common_error_generic))
        add(
            UiText.Res(Res.string.sync_google_signed_in_as, listOf("Olya")) to
                android.getString(R.string.sync_google_signed_in_as, "Olya")
        )
        add(
            UiText.Res(Res.string.sync_google_sign_in_refused, listOf(STATUS_CODE)) to
                android.getString(R.string.sync_google_sign_in_refused, STATUS_CODE)
        )
        add(
            UiText.Res(
                Res.string.home_requests_answer_accepted,
                listOf(UiText.Res(Res.string.home_requests_day_yesterday), "Pavel")
            ) to android.getString(
                R.string.home_requests_answer_accepted,
                android.getString(R.string.home_requests_day_yesterday),
                "Pavel"
            )
        )
        add(
            UiText.Res(Res.string.request_history_meta_deadline, listOf(UiText.Date(DAY))) to
                android.getString(R.string.request_history_meta_deadline, medium(DAY))
        )
        for (count in COUNTS) {
            add(
                UiText.Plural(Res.plurals.request_left_days, count) to
                    android.getQuantityString(R.plurals.request_left_days, count, count)
            )
            add(
                UiText.Plural(Res.plurals.home_requests_waiting_on_them, count, listOf(count, "Pavel")) to
                    android.getQuantityString(R.plurals.home_requests_waiting_on_them, count, count, "Pavel")
            )
            val window = listOf(count, UiText.Date(DAY), UiText.Date(LATER))
            add(
                UiText.Plural(Res.plurals.sync_google_synced, count, window) to
                    android.getQuantityString(R.plurals.sync_google_synced, count, count, medium(DAY), medium(LATER))
            )
        }
        add(UiText.Date(DAY) to medium(DAY))
        DayOfWeek.entries.forEach { day -> add(UiText.Weekday(day) to dayName(day)) }
        add(UiText.Raw("Zuzana") to "Zuzana")
    }

    @Test
    fun `a UiText reads as it did through Android's resources, in composition and outside it`() {
        val expected = samples(androidResources(language))
        var composed: List<String> = emptyList()
        var resolver: UiTextResolver? = null
        composeRule.setContent {
            composed = expected.map { (text, _) -> text.asString() }
            resolver = rememberUiTextResolver()
        }
        composeRule.waitForIdle()

        val outside = runBlocking { expected.map { (text, _) -> text.asString(checkNotNull(resolver)) } }

        expected.forEachIndexed { index, (text, android) ->
            assertEquals("$language, in composition: $text", android, composed[index])
            assertEquals("$language, through the resolver: $text", android, outside[index])
        }
    }

    /** The configuration's language and the process's default, then restored. */
    private class LanguageRule(private val language: String) : ExternalResource() {

        private var saved: Locale? = null

        override fun before() {
            saved = Locale.getDefault()
            Locale.setDefault(Locale.forLanguageTag(language))
            RuntimeEnvironment.setQualifiers(language)
        }

        override fun after() {
            saved?.let(Locale::setDefault)
        }
    }

    companion object {

        /** A Google status code, printed through `%1$d`. */
        private const val STATUS_CODE = 10

        /** One, few, many and other in Czech, Russian and Ukrainian; one and other elsewhere. */
        private val COUNTS = listOf(1, 2, 5, 21)

        /** A Saturday. */
        private val DAY = LocalDate(2026, 5, 16)
        private val LATER = LocalDate(2027, 8, 24)

        /** The five languages. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun languages(): List<Array<Any>> = LANGUAGES.map { arrayOf<Any>(it) }
    }
}
