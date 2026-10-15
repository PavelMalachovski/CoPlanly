package com.coparently.app.data.repository

import com.coparently.app.data.local.entity.CustodyModelEntity
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A stored custody row as the domain model. The row's family (schema 45) stays on the row: the
 * model is the pattern, and which family it answers for is [ActiveCustodyRow]'s question.
 */
internal fun CustodyModelEntity.toDomainModel(): CustodyModel {
    val momDays = momDaysPattern
        .removeSurrounding("[", "]")
        .split(",")
        .filter { it.isNotBlank() }
        .map { it.trim().toInt() }
        .toSet()

    val layers = SeasonalLayerJson.decode(seasonalLayersJson)
    val children = ChildOverrideJson.decode(childOverridesJson)
    return CustodyModel(
        id = id,
        modelType = CustodyModelType.fromString(modelType),
        patternDays = patternDays,
        momDayIndices = momDays,
        startDate = LocalDate.parse(startDate),
        isActive = isActive,
        contactWindows = ContactWindowJson.decode(contactWindowsJson),
        seasonalLayers = layers.layers,
        unreadableLayers = layers.unreadable,
        childOverrides = children.overrides,
        unreadableChildOverrides = children.unreadable
    )
}

/**
 * The domain model as a stored custody row, naming no family: the caller stamps the family the
 * row is the schedule of (schema 45), because only the caller knows which family that is.
 *
 * @param createdAt ISO date-time to stamp; defaults to now for a locally created model and
 *   is supplied from the document when mirroring, so a synced row keeps the pair's dates.
 * @param lastModifiedAtMillis Instant of the change, epoch millis; defaults to now. Not a
 *   wall clock, and not derived from [createdAt]: this is the value that decides which
 *   phone's schedule survives, so it has to mean the same thing in both zones (SEC-4).
 */
internal fun CustodyModel.toEntity(
    createdAt: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
    lastModifiedAtMillis: Long = System.currentTimeMillis(),
    dayOverrides: Map<String, DayOverride> = emptyMap()
): CustodyModelEntity {
    val momDaysJson = momDayIndices.sorted().joinToString(",", "[", "]")

    return CustodyModelEntity(
        id = id,
        modelType = CustodyModelType.toString(modelType),
        patternDays = patternDays,
        momDaysPattern = momDaysJson,
        startDate = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
        isActive = isActive,
        repeatYearly = true,
        createdAt = createdAt,
        lastModifiedAtMillis = lastModifiedAtMillis,
        // Null rather than "{}" for none, so a row that has never carried a swap is
        // byte-identical to one written before the column existed — which the equality
        // guard in `mirrorIntoRoom` depends on to stay quiet.
        dayOverridesJson = DayOverrideJson.encode(dayOverrides),
        // Null for none, for the same reason.
        contactWindowsJson = ContactWindowJson.encode(contactWindows),
        seasonalLayersJson = SeasonalLayerJson.encode(seasonalLayers, unreadableLayers),
        childOverridesJson = ChildOverrideJson.encode(childOverrides, unreadableChildOverrides)
    )
}
