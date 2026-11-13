package com.coparently.app.domain.school

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Per-system import ids: Bakaláři's formula is unchanged, EduPage's names the host and the pupil. */
class SchoolImportIdsTest {

    @Test
    fun `bakalari ids keep their formula`() {
        val target = target(system = SchoolSystem.BAKALARI, baseUrl = "https://s.cz/bakaweb", username = "novak")
        assertEquals(
            "school-" +
                SchoolImportIds.sha256Hex("bakalari|https://s.cz/bakaweb|novak|event|42|2026-11-20T08:00").take(32),
            SchoolImportIds.of(target, SchoolImportKind.EVENT, "42|2026-11-20T08:00")
        )
    }

    @Test
    fun `a target without a system is bakalari`() {
        val plain = SchoolImportTarget("https://s.cz", "novak", "anna", "f", "alice", "mom")
        assertEquals(SchoolSystem.BAKALARI, plain.system)
        assertEquals("", plain.studentKey)
    }

    @Test
    fun `edupage ids name the school host and the child and not the login`() {
        val mine = target(
            system = SchoolSystem.EDUPAGE,
            baseUrl = "https://zsx.edupage.org",
            username = "mama",
            studentKey = "-1234"
        )
        val theirs = mine.copy(username = "tata", ownerUid = "other")
        assertEquals(
            SchoolImportIds.of(mine, SchoolImportKind.TASK, "991"),
            SchoolImportIds.of(theirs, SchoolImportKind.TASK, "991")
        )
        assertEquals(
            "school-" + SchoolImportIds.sha256Hex("edupage|zsx.edupage.org|-1234|task|991").take(32),
            SchoolImportIds.of(mine, SchoolImportKind.TASK, "991")
        )
    }

    @Test
    fun `an edupage host ignores scheme case port path and credentials`() {
        val plain = target(system = SchoolSystem.EDUPAGE, baseUrl = "https://zsx.edupage.org", studentKey = "7")
        val noisy = plain.copy(baseUrl = "HTTPS://user:pw@ZSX.edupage.org:8443/some/path?q=1#x")
        assertEquals(
            SchoolImportIds.of(plain, SchoolImportKind.TASK, "1"),
            SchoolImportIds.of(noisy, SchoolImportKind.TASK, "1")
        )
    }

    @Test
    fun `another pupil or kind gives another edupage id`() {
        val one = target(system = SchoolSystem.EDUPAGE, baseUrl = "https://zsx.edupage.org", studentKey = "1")
        val id = SchoolImportIds.of(one, SchoolImportKind.TASK, "5")
        assertNotEquals(id, SchoolImportIds.of(one.copy(studentKey = "2"), SchoolImportKind.TASK, "5"))
        assertNotEquals(id, SchoolImportIds.of(one, SchoolImportKind.EVENT, "5"))
    }

    @Test
    fun `an unknown stored system reads as bakalari`() {
        assertEquals(SchoolSystem.BAKALARI, SchoolSystem.of(null))
        assertEquals(SchoolSystem.BAKALARI, SchoolSystem.of("other"))
        assertEquals(SchoolSystem.EDUPAGE, SchoolSystem.of("edupage"))
        assertEquals(SchoolImportKind.TASK, SchoolImportKind.of("task"))
    }

    private fun target(
        system: SchoolSystem,
        baseUrl: String,
        username: String = "u",
        studentKey: String = ""
    ) = SchoolImportTarget(
        baseUrl = baseUrl,
        username = username,
        childId = "anna",
        familyId = "alice__bob",
        ownerUid = "alice",
        ownerSlot = "mom",
        system = system,
        studentKey = studentKey
    )
}
