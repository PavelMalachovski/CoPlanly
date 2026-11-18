package com.coparently.app.pushwording

import kotlin.test.Test
import kotlin.test.assertEquals

class PluralQuantityTest {
    private fun quantities(language: String, vararg counts: Int) = counts.map { pluralQuantity(language, it) }

    @Test
    fun englishAndGermanHaveOneAndOther() {
        assertEquals(listOf("other", "one", "other", "other"), quantities("en", 0, 1, 2, 21))
        assertEquals(listOf("other", "one", "other"), quantities("de", 0, 1, 5))
    }

    @Test
    fun czechTakesFewFromTwoToFourAndNeverManyForAWholeNumber() {
        assertEquals(
            listOf("other", "one", "few", "few", "few", "other", "other", "other"),
            quantities("cs", 0, 1, 2, 3, 4, 5, 21, 22),
        )
    }

    @Test
    fun russianAndUkrainianFollowTheLastDigits() {
        val expected = listOf("many", "one", "few", "many", "many", "many", "one", "few", "many", "many")
        assertEquals(expected, quantities("ru", 0, 1, 2, 5, 11, 12, 21, 22, 25, 111))
        assertEquals(expected, quantities("uk", 0, 1, 2, 5, 11, 12, 21, 22, 25, 111))
    }

    @Test
    fun anotherLanguageReadsAsEnglish() {
        assertEquals(listOf("one", "other"), quantities("fr", 1, 2))
    }
}
