package com.coparently.app.data.family

import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in parent's [FamilyView], following every switch — for a screen that marks or counts
 * records by where they stand ([FamilyView.placeOf]) rather than only filtering them.
 *
 * The repositories already hide another live family's records ([inFamilyOnScreen]); what a screen
 * still needs is to tell the rest apart: a family's totals and balance add up its own records only
 * ([FamilyView.counts]), and a record that is "only mine" or from an earlier family carries a mark.
 */
@Singleton
class FamilyViewSource @Inject constructor(
    private val auth: FirebaseAuthService,
    private val userDao: UserDao
) {

    /** The view, re-emitted when the switcher or the co-parent set changes; everything signed out. */
    fun observe(): Flow<FamilyView> = flow {
        val uid = auth.getCurrentUser()?.uid?.takeIf { it.isNotBlank() }
        if (uid == null) {
            emit(FamilyView.EVERYTHING)
            return@flow
        }
        emitAll(
            userDao.observeUserById(uid)
                .map { FamilyView.of(uid, it) }
                .onStart { emit(FamilyView.of(uid, userDao.getUserById(uid))) }
                .distinctUntilChanged()
        )
    }
}
