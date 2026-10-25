package com.coparently.app.data.family

import android.util.Log
import com.coparently.app.data.local.dao.KnownFamilyDao
import com.coparently.app.data.remote.firebase.FirestoreFamilyDataSource
import com.coparently.app.data.remote.firebase.FirestoreUserDataSource
import com.coparently.app.domain.family.FamilyKey
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps `known_families` current for every live family: the co-parent's name and the two slots
 * (schema 46).
 *
 * Run from the profile step of every sync (`SyncService`), which already runs on each pairing
 * transition (CLAUDE.md item 39) — so a family is remembered within seconds of forming, while its
 * co-parent's profile and its `families/{id}` document are still readable. Neither changes often:
 * a name when the co-parent renames, the slots never after pairing. What this keeps is what
 * Family → Earlier families shows once the family has ended and both are gone.
 *
 * Every read is best effort, one family at a time: a failed read keeps the row as it was, and
 * never blanks a name or a slot already known.
 */
@Singleton
class KnownFamilies @Inject constructor(
    private val dao: KnownFamilyDao,
    private val users: FirestoreUserDataSource,
    private val families: FirestoreFamilyDataSource
) {

    /** Remembers each of [coParentUids]' family with [myUid]. */
    suspend fun refresh(myUid: String, coParentUids: Collection<String>, nowMillis: Long = System.currentTimeMillis()) {
        coParentUids.filter { it.isNotBlank() && it != myUid }.distinct().forEach { coParent ->
            val familyId = FamilyKey.of(myUid, coParent)
            remember("name") {
                val name = (users.getUserById(coParent)?.get("name") as? String)?.trim()
                if (!name.isNullOrEmpty()) dao.rememberName(familyId, coParent, name, nowMillis)
            }
            remember("slots") {
                val slots = families.getSlots(familyId)
                val mine = slots?.get(myUid)?.takeIf { it in SLOTS }
                val theirs = slots?.get(coParent)?.takeIf { it in SLOTS }
                if (mine != null && theirs != null) {
                    dao.rememberSlots(familyId, coParent, mine, theirs, nowMillis)
                }
            }
        }
    }

    private suspend fun remember(what: String, read: suspend () -> Unit) {
        try {
            read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read a family's $what (${e.javaClass.simpleName}); kept the last one")
        }
    }

    private companion object {
        const val TAG = "KnownFamilies"
        val SLOTS = setOf("mom", "dad")
    }
}
