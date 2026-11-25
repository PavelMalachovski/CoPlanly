package com.coparently.app.presentation.changerequests

import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.data.family.testFamilyViewSource
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.presentation.common.FamilyMembersSource
import com.coparently.app.presentation.common.testFamilyMembersSource
import io.mockk.coEvery
import io.mockk.mockk

/**
 * A real [SwapChildrenSource] over [members] and [views]; the active custody model has no child of
 * its own schedule unless [custody] says otherwise. Signed out by default, so every child counts.
 */
fun testSwapChildrenSource(
    members: FamilyMembersSource = testFamilyMembersSource(),
    views: FamilyViewSource = testFamilyViewSource(),
    custody: CustodyModelRepository = mockk(relaxed = true) { coEvery { getActiveModelSync() } returns null }
): SwapChildrenSource = SwapChildrenSource(members, views, custody)
