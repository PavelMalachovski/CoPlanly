package com.coparently.app.domain.custody

import com.coparently.app.domain.model.CustodyModel
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Seasonal layers (MON-14): the wire form, and how a layer changes whose day it is.
 *
 * The resolution fixtures are **the same strings** `functions/test/calendar-feed.test.js` uses
 * for the calendar feed's port, so the app and the feed are pinned to one answer.
 */
class SeasonalLayerTest {

    private val start = LocalDate.of(2026, 9, 7)
    private val base = CustodyModel.weekOnWeekOff(id = "m1", startDate = start)

    private fun decoded(vararg wire: String) = SeasonalLayerCodec.decodeAll(wire.toList())

    private fun withLayers(vararg wire: String): CustodyModel {
        val layers = decoded(*wire)
        return base.copy(seasonalLayers = layers.layers, unreadableLayers = layers.unreadable)
    }

    private fun date(iso: String) = LocalDate.parse(iso)

    private fun windowsOf(model: CustodyModel) =
        CustodyResolver.contactWindowsResolver(model, CustodyResolver.resolver(model, emptyMap()) { null })

    // ---- the wire form ------------------------------------------------------

    @Test
    fun `a layer round-trips through its wire string, name and windows included`() {
        val layer = SeasonalLayer(
            id = "summer-26",
            name = "Letní; 50/50, léto",
            fromDate = date("2026-07-01"),
            toDate = date("2026-08-31"),
            patternDays = 14,
            momDayIndices = (0..6).toSet(),
            contactWindows = listOf(ContactWindow(9, LocalTime.of(15, 0), LocalTime.of(19, 0), "mom")),
            priority = -3
        )
        val wire = SeasonalLayerCodec.encode(layer)

        assertEquals(10, wire.split(';').size, "a name can never add a field")
        assertTrue(wire.endsWith(";Letn%C3%AD%3B%2050%2F50%2C%20l%C3%A9to"))
        assertEquals(layer, SeasonalLayerCodec.decode(wire))
    }

    @Test
    fun `the canonical list is sorted, de-duplicated and keeps unreadable entries verbatim`() {
        val decoded = decoded(CHRISTMAS_DAD, "L2;from-the-future", JULY_MUM_AUGUST_DAD, CHRISTMAS_DAD)

        assertEquals(2, decoded.layers.size)
        assertEquals(listOf("L2;from-the-future"), decoded.unreadable)
        val encoded = SeasonalLayerCodec.encodeAll(decoded.layers, decoded.unreadable)
        assertEquals(listOf(CHRISTMAS_DAD, JULY_MUM_AUGUST_DAD, "L2;from-the-future").sorted(), encoded)
        // A device that decoded the list and wrote it back cannot have changed it.
        val again = SeasonalLayerCodec.decodeAll(encoded)
        assertEquals(encoded, SeasonalLayerCodec.encodeAll(again.layers, again.unreadable))
    }

    @Test
    fun `entries the feed port refuses are unreadable here too`() {
        listOf(
            "L2;summer-26;0;2026-07-01;2026-08-31;2026-07-01;62;0;;Summer",
            "L1;summer 26;0;2026-07-01;2026-08-31;2026-07-01;62;0;;Summer",
            "L1;s;0;2026-08-31;2026-07-01;2026-07-01;62;0;;Summer",
            "L1;s;0;2026-01-01;2027-01-02;2026-01-01;1;0;;Too long",
            "L1;s;0;2026-07-01;2026-08-31;2026-07-01;14;14;;Index past the cycle",
            "L1;s;0;2026-07-01;2026-08-31;2026-07-01;14;0;14|15:00|19:00|dad;Window past the cycle",
            "L1;s;5000;2026-07-01;2026-08-31;2026-07-01;14;0;;Priority",
            "L1;s;0;2026-07-01;2026-08-31;2026-07-01;+14;0;;Signed",
            "L1;s;0;2026-07-01;2026-08-31;2026-07-01;14;0;;Raw space"
        ).forEach { assertNull(SeasonalLayerCodec.decode(it), it) }
        assertNotNull(SeasonalLayerCodec.decode("L1;s;0;2026-07-01;2026-08-31;2026-07-01;14;0;;"))
    }

    // ---- resolution (the calendar feed's fixtures) --------------------------

    @Test
    fun `a layer replaces the base pattern inside its dates, and only there`() {
        val model = withLayers(JULY_MUM_AUGUST_DAD)

        assertEquals("mom", model.getCustodyFor(date("2026-07-06")))
        assertEquals("dad", model.getCustodyFor(date("2026-08-10")))
        assertEquals("dad", model.getCustodyFor(date("2026-08-31")))
        assertEquals("dad", model.getCustodyFor(date("2026-09-01")))
        assertEquals("mom", model.getCustodyFor(date("2026-09-07")))
        assertEquals("dad", model.getCustodyFor(date("2026-06-24")))
        // The base answer is still there for whoever needs it.
        assertEquals("dad", model.baseCustodyFor(date("2026-07-06")))
    }

    @Test
    fun `overlapping layers resolve by priority, then the later start`() {
        val model = withLayers(WINTER_MUM, CHRISTMAS_DAD)

        assertEquals("mom", model.getCustodyFor(date("2026-12-23")))
        assertEquals("dad", model.getCustodyFor(date("2026-12-24")))
        assertEquals("dad", model.getCustodyFor(date("2026-12-26")))
        assertEquals("mom", model.getCustodyFor(date("2026-12-27")))

        val tie = withLayers(WINTER_MUM, CHRISTMAS_DAD.replaceFirst(";1;", ";0;"))
        assertEquals("dad", tie.getCustodyFor(date("2026-12-25")))
    }

    @Test
    fun `an accepted swap stays above every layer`() {
        val model = withLayers(JULY_MUM_AUGUST_DAD)
        val swap = DayOverride(
            toParent = "dad",
            requestedBy = "bob",
            requestedAt = "2026-06-01T10:00:00",
            status = DayOverrideStatus.ACCEPTED
        )
        val custodyFor = CustodyResolver.resolver(model, mapOf("2026-07-02" to swap)) { null }

        assertEquals("dad", custodyFor(date("2026-07-02")))
        assertEquals("mom", custodyFor(date("2026-07-03")))
    }

    @Test
    fun `a day's contact windows come from the layer that decides it`() {
        val baseWindows = (0..6).map { ContactWindow(it, LocalTime.of(10, 0), LocalTime.of(12, 0), "dad") }
        val layers = decoded("L1;aug-26;0;2026-08-01;2026-08-31;2026-08-01;1;0;0|15:00|19:00|dad;August")
        val model = base.copy(contactWindows = baseWindows, seasonalLayers = layers.layers)
        val windowsFor = windowsOf(model)

        assertEquals(listOf(LocalTime.of(15, 0)), windowsFor(date("2026-08-10")).map { it.start })
        assertEquals(listOf(LocalTime.of(10, 0)), windowsFor(date("2026-09-07")).map { it.start })
    }

    @Test
    fun `a layer with windows is drawn although the base pattern has none`() {
        val model = withLayers("L1;aug-26;0;2026-08-01;2026-08-31;2026-08-01;1;0;0|15:00|19:00|dad;August")
        val windowsFor = windowsOf(model)

        assertEquals(1, windowsFor(date("2026-08-10")).size)
    }

    @Test
    fun `an unreadable layer decides nothing`() {
        val model = withLayers("L2;future;9;2026-07-01;2026-08-31;x", "L1;broken")

        assertTrue(model.seasonalLayers.isEmpty())
        assertEquals(2, model.unreadableLayers.size)
        assertEquals("dad", model.getCustodyFor(date("2026-07-06")))
    }

    // ---- the model around it ------------------------------------------------

    @Test
    fun `complementing a pattern flips its layers and keeps unreadable entries`() {
        val model = withLayers(JULY_MUM_AUGUST_DAD, "L2;future")
        val flipped = model.complemented()

        assertEquals("dad", flipped.getCustodyFor(date("2026-07-06")))
        assertEquals("mom", flipped.getCustodyFor(date("2026-08-10")))
        assertEquals(listOf("L2;future"), flipped.unreadableLayers)
    }

    @Test
    fun `two models that differ only in a layer are not equivalent`() {
        assertTrue(withLayers(JULY_MUM_AUGUST_DAD).isEquivalentTo(withLayers(JULY_MUM_AUGUST_DAD)))
        assertFalse(base.isEquivalentTo(withLayers(JULY_MUM_AUGUST_DAD)))
        assertFalse(withLayers(WINTER_MUM).isEquivalentTo(withLayers(WINTER_MUM, CHRISTMAS_DAD)))
    }

    @Test
    fun `a layer-only proposal is never described as changing nothing`() {
        // A summer proposed in September moves no day in the next eight weeks.
        val diff = CustodyPatternDiff.of(base, withLayers(JULY_MUM_AUGUST_DAD), from = start)

        assertTrue(diff.seasonalLayersChanged)
        assertFalse(diff.identical)
        assertTrue(diff.movedDays.isEmpty())
    }

    @Test
    fun `a proposal states its layers and accepting it makes them the agreed ones`() {
        val shared = SharedCustody(model = base, lastModifiedBy = "alice", lastModifiedAtMillis = 1L, createdAt = "")
        val proposed = CustodyProposalTransition.propose(
            current = shared,
            model = withLayers(JULY_MUM_AUGUST_DAD),
            repeatYearly = true,
            byUid = "alice",
            atIso = "2026-09-07T10:00:00"
        ).getOrThrow()
        assertEquals(listOf(JULY_MUM_AUGUST_DAD), proposed.proposal?.seasonalLayersWire)
        // The agreed document's own list is untouched by a proposal write.
        assertNull(proposed.seasonalLayersWire)

        val accepted = CustodyProposalTransition.accept(proposed, "bob", "2026-09-08T10:00:00", 2L).getOrThrow()
        assertEquals(listOf(JULY_MUM_AUGUST_DAD), accepted.seasonalLayersWire)
        assertEquals("mom", accepted.model.getCustodyFor(date("2026-07-06")))
    }

    @Test
    fun `the presets cover what they say`() {
        val summer = date("2026-07-01")..date("2026-08-31")
        val half = SeasonalLayer.splitInHalf("s", "Summer", summer, "dad")
        assertEquals("dad", half.custodyFor(date("2026-07-31")))
        assertEquals("mom", half.custodyFor(date("2026-08-01")))

        val weeks = SeasonalLayer.alternatingWeeks("w", "Summer", summer, "mom")
        assertEquals("mom", weeks.custodyFor(date("2026-07-07")))
        assertEquals("dad", weeks.custodyFor(date("2026-07-08")))

        val all = SeasonalLayer.allWith("a", "Christmas", date("2026-12-24")..date("2026-12-26"), "dad")
        assertEquals("dad", all.custodyFor(date("2026-12-25")))
    }

    private companion object {
        val JULY_MUM_AUGUST_DAD = "L1;summer-26;0;2026-07-01;2026-08-31;2026-07-01;62;" +
            (0..30).joinToString(",") + ";;Summer"
        const val WINTER_MUM = "L1;winter-26;0;2026-12-20;2027-01-03;2026-12-20;1;0;;Winter"
        const val CHRISTMAS_DAD = "L1;xmas-26;1;2026-12-24;2026-12-26;2026-12-24;1;;;Christmas"
    }
}
