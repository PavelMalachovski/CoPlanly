package com.coparently.app.domain.custody

/**
 * An answer a day swap was given before the day was offered again (tester feedback, October
 * 2026: "Zuzana agreed Lena would be with Igor, then fell ill and wants to propose a change — and
 * the agreement must stay documented").
 *
 * The override map holds one entry per date, so a new offer for a day replaces the entry that
 * recorded its last answer. That answer moves into the new entry's [DayOverride.earlier] as one
 * of these, and the inbox's History keeps showing it beside the new offer. The chat's card and
 * the export keep it too, as they always did.
 *
 * @property toParent The slot the earlier offer moved the day to.
 * @property requestedBy Who offered it.
 * @property requestedAt When, as the offer stored it.
 * @property status How it was answered — never pending.
 * @property decidedBy Who answered.
 * @property decidedAt When.
 * @property groupId The earlier offer's group, so its days stay one row in History.
 */
data class DayOverrideDecision(
    val toParent: String,
    val requestedBy: String,
    val requestedAt: String,
    val status: DayOverrideStatus,
    val decidedBy: String?,
    val decidedAt: String?,
    val groupId: String?
)

/**
 * The wire form of a [DayOverrideDecision]: `d1|toParent|requestedBy|requestedAt|status|decidedBy|
 * decidedAt|groupId`, an empty field for a missing value.
 *
 * A string, like the project's other codecs (`ContactWindowCodec`, `SeasonalLayerCodec`), never
 * Gson over the data class: [DayOverride] is stored as Gson JSON in Room and kept by name for R8,
 * and a list of strings adds no new class for either to know about. None of the fields can hold a
 * `|`: slots, uids, ISO times and UUIDs.
 */
object DayOverrideDecisionCodec {

    private const val VERSION = "d1"
    private const val SEPARATOR = "|"
    private const val FIELDS = 8

    /** [decision] on the wire. */
    fun encode(decision: DayOverrideDecision): String = listOf(
        VERSION,
        decision.toParent,
        decision.requestedBy,
        decision.requestedAt,
        decision.status.name,
        decision.decidedBy.orEmpty(),
        decision.decidedAt.orEmpty(),
        decision.groupId.orEmpty()
    ).joinToString(SEPARATOR)

    /** [wire] as a decision, or null when this build cannot read it (another version, a pending one). */
    @Suppress("ReturnCount") // one early answer per way a string can fail to be a decision
    fun decode(wire: String): DayOverrideDecision? {
        val parts = wire.split(SEPARATOR)
        if (parts.size != FIELDS || parts[0] != VERSION) return null
        val status = DayOverrideStatus.entries.firstOrNull { it.name == parts[4] } ?: return null
        if (status == DayOverrideStatus.PENDING || parts[1].isBlank() || parts[2].isBlank()) return null
        return DayOverrideDecision(
            toParent = parts[1],
            requestedBy = parts[2],
            requestedAt = parts[3],
            status = status,
            decidedBy = parts[5].ifBlank { null },
            decidedAt = parts[6].ifBlank { null },
            groupId = parts[7].ifBlank { null }
        )
    }

    /** [override]'s own answer, as it moves into the entry that replaces it. */
    fun of(override: DayOverride): DayOverrideDecision = DayOverrideDecision(
        toParent = override.toParent,
        requestedBy = override.requestedBy,
        requestedAt = override.requestedAt,
        status = override.status,
        decidedBy = override.decidedBy,
        decidedAt = override.decidedAt,
        groupId = override.groupId
    )
}
