package com.coparently.app.data.family

import com.coparently.app.data.local.dao.KnownFamilyDao
import com.coparently.app.data.local.entity.KnownFamilyEntity
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.family.FamilySlots
import com.coparently.app.domain.family.FamilySlotsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [FamilySlotsSource] over `known_families` (schema 46), which `KnownFamilies` fills from
 * `families/{id}.slots` on every sync. A row answers only with both slots, and only when they
 * differ — two parents in one slot cannot be told apart, and the profile's answer is then no worse.
 */
@Singleton
class KnownFamilySlots @Inject constructor(private val dao: KnownFamilyDao) : FamilySlotsSource {

    override fun observe(familyId: String): Flow<FamilySlots?> =
        dao.observe(familyId).map { it?.let(::slotsOf) }.distinctUntilChanged()

    override suspend fun get(familyId: String): FamilySlots? = dao.get(familyId)?.let(::slotsOf)

    private fun slotsOf(row: KnownFamilyEntity): FamilySlots? {
        val me = FamilyKey.membersOf(row.familyId)?.toList()?.singleOrNull { it != row.coParentUid }
        val slots = listOfNotNull(row.mySlot, row.coParentSlot).distinct()
        return if (me == null || slots.size != 2) {
            null
        } else {
            FamilySlots(mapOf(me to slots[0], row.coParentUid to slots[1]))
        }
    }
}
