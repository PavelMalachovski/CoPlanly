package com.coparently.app.data.repository

import com.coparently.app.data.local.dao.CustodyModelDao
import com.coparently.app.data.local.entity.CustodyModelEntity
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing custody model configurations.
 * Handles conversion between entities and domain models.
 */
@Singleton
class CustodyModelRepository @Inject constructor(
    private val custodyModelDao: CustodyModelDao
) {
    /**
     * Gets the active custody model as a Flow.
     */
    fun getActiveModel(): Flow<CustodyModel?> {
        return custodyModelDao.getActiveModel().map { entity ->
            entity?.toDomainModel()
        }
    }

    /**
     * Gets the active custody model synchronously.
     */
    suspend fun getActiveModelSync(): CustodyModel? {
        return custodyModelDao.getActiveModelSync()?.toDomainModel()
    }

    /**
     * Gets all custody models.
     */
    fun getAllModels(): Flow<List<CustodyModel>> {
        return custodyModelDao.getAllModels().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /**
     * Saves a new custody model and sets it as active.
     */
    suspend fun saveAndActivate(model: CustodyModel) {
        val entity = model.toEntity()
        custodyModelDao.deactivateAllModels()
        custodyModelDao.insertModel(entity.copy(isActive = true))
    }

    /**
     * Creates and saves a week-on-week-off custody model.
     *
     * @param startDate The anchor date for the pattern
     * @param momFirst If true, mom has the first week; if false, dad has the first week
     */
    suspend fun createWeekOnWeekOff(startDate: LocalDate, momFirst: Boolean = true) {
        val model = CustodyModel.weekOnWeekOff(
            id = UUID.randomUUID().toString(),
            startDate = startDate,
            momFirst = momFirst
        )
        saveAndActivate(model)
    }

    /**
     * Creates and saves a 2-2-3 custody model.
     */
    suspend fun createTwoTwoThree(startDate: LocalDate, momStartsFirst: Boolean = true) {
        val model = CustodyModel.twoTwoThree(
            id = UUID.randomUUID().toString(),
            startDate = startDate,
            momStartsFirst = momStartsFirst
        )
        saveAndActivate(model)
    }

    /**
     * Creates and saves a 3-4-4-3 custody model.
     */
    suspend fun createThreeFourFourThree(startDate: LocalDate, momStartsFirst: Boolean = true) {
        val model = CustodyModel.threeFourFourThree(
            id = UUID.randomUUID().toString(),
            startDate = startDate,
            momStartsFirst = momStartsFirst
        )
        saveAndActivate(model)
    }

    /**
     * Creates and saves a custom custody model.
     */
    suspend fun createCustom(
        startDate: LocalDate,
        patternDays: Int,
        momDayIndices: Set<Int>
    ) {
        val model = CustodyModel.custom(
            id = UUID.randomUUID().toString(),
            startDate = startDate,
            patternDays = patternDays,
            momDayIndices = momDayIndices
        )
        saveAndActivate(model)
    }

    /**
     * Deletes a custody model.
     */
    suspend fun deleteModel(id: String) {
        custodyModelDao.deleteModelById(id)
    }

    /**
     * Converts CustodyModelEntity to CustodyModel domain model.
     */
    private fun CustodyModelEntity.toDomainModel(): CustodyModel {
        val momDays = momDaysPattern
            .removeSurrounding("[", "]")
            .split(",")
            .filter { it.isNotBlank() }
            .map { it.trim().toInt() }
            .toSet()

        return CustodyModel(
            id = id,
            modelType = CustodyModelType.fromString(modelType),
            patternDays = patternDays,
            momDayIndices = momDays,
            startDate = LocalDate.parse(startDate),
            isActive = isActive
        )
    }

    /**
     * Converts CustodyModel domain model to CustodyModelEntity.
     */
    private fun CustodyModel.toEntity(): CustodyModelEntity {
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val momDaysJson = momDayIndices.sorted().joinToString(",", "[", "]")

        return CustodyModelEntity(
            id = id,
            modelType = CustodyModelType.toString(modelType),
            patternDays = patternDays,
            momDaysPattern = momDaysJson,
            startDate = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            isActive = isActive,
            repeatYearly = true,
            createdAt = now,
            lastModifiedAt = now
        )
    }
}
