package com.coparently.app.qa

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.R
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.SeasonalLayer
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.e2e.EmulatorParent
import com.coparently.app.presentation.navigation.BottomNavDestination
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Changing the schedule itself: Zuzana's Christmas — Lena with Igor in Prague on the 24th and
 * 25th, back with Zuzana from the 26th — as a seasonal layer the other parent accepts, and a new
 * base pattern answered on the other screen (`…AsIgor`). Neither ever changes the calendar until
 * it is accepted.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CustodyScenarioTest : QaOnScreenTest() {

    @Inject
    lateinit var custodyRepository: CustodyModelRepository

    @Test
    fun christmasLayer() {
        val year = if (LocalDate.now().monthValue == 12 && LocalDate.now().dayOfMonth > 24) {
            LocalDate.now().year + 1
        } else {
            LocalDate.now().year
        }
        val eve = LocalDate.of(year, 12, 24)
        val boxingDay = LocalDate.of(year, 12, 26)
        lateinit var slots: Pair<String, String>
        report.step("pattern", "Igor sets week on, week off", "Zuzana's phone holds the pattern") {
            slots = runBlocking { slots() }
            runBlocking { coParentSetsFirstPattern(zuzanaFirst = true) }
        }
        report.step("proposal_popup", "Igor proposes \"Vánoce v Praze\": 24 and 25 December with him", "Zuzana's Home asks her, naming Igor; her calendar is unchanged until she answers") {
            runBlocking {
                val layer = SeasonalLayer.allWith(LAYER_ID, LAYER_NAME, eve..eve.plusDays(1), slots.second)
                val submitted = bob.custodyRepository.submitSeasonalLayers(listOf(layer))
                check(submitted == PatternSubmission.PROPOSED) { "the layer was $submitted, not proposed" }
                val before = checkNotNull(custodyRepository.getActiveModelSync())
                check(before.seasonalLayers.none { it.id == LAYER_ID }) { "the layer changed the calendar before it was accepted" }
            }
            awaitDelivered(this, "the proposal pop-up", proposalDialog(), expectsListener = true)
        }
        report.step("grid_follows", "Zuzana accepts", "24 December is Igor's, 26 December is Zuzana's, on both phones") {
            tap(dialogButton(R.string.custody_proposal_accept))
            val mine = runBlocking { awaitPattern(custodyRepository) { model -> model.seasonalLayers.any { it.id == LAYER_ID } } }
            val theirs = runBlocking { awaitPattern(bob.custodyRepository) { model -> model.seasonalLayers.any { it.id == LAYER_ID } } }
            listOf("Zuzana" to mine, "Igor" to theirs).forEach { (who, model) ->
                check(model.getCustodyFor(eve) == slots.second) { "$who's phone gives 24 December to ${model.getCustodyFor(eve)}" }
                check(model.getCustodyFor(boxingDay) == slots.first) { "$who's phone gives 26 December to ${model.getCustodyFor(boxingDay)}" }
            }
            openTab(BottomNavDestination.CALENDAR)
            repeat(ChronoUnit.MONTHS.between(LocalDate.now().withDayOfMonth(1), eve.withDayOfMonth(1)).toInt()) {
                driver.pageForward()
            }
        }
        report.step("layer_editor_on_screen", "Zuzana builds a layer in the editor herself", "The editor sends a proposal") {
            couldNotRun("the seasonal layer editor is not automated yet; the device checklist keeps it")
        }
    }

    @Test
    fun patternAnswerOnScreenAsIgor() {
        report.step("pattern", "$coParentName sets week on, week off", "Igor's phone holds the pattern") {
            runBlocking { coParentSetsFirstPattern(zuzanaFirst = false) }
        }
        report.step("proposal_popup", "$coParentName proposes 2-2-3", "Igor's Home asks him, naming $coParentName") {
            runBlocking {
                val submitted = bob.custodyRepository.createTwoTwoThree(LocalDate.now(), momStartsFirst = true)
                check(submitted == PatternSubmission.PROPOSED) { "the pattern was $submitted, not proposed" }
            }
            awaitDelivered(this, "the proposal pop-up", proposalDialog(), expectsListener = true)
        }
        report.step("both_follow", "Igor accepts", "Both phones hold 2-2-3") {
            tap(dialogButton(R.string.custody_proposal_accept))
            runBlocking {
                awaitPattern(custodyRepository) { it.modelType == CustodyModelType.TWO_TWO_THREE }
                awaitPattern(bob.custodyRepository) { it.modelType == CustodyModelType.TWO_TWO_THREE }
            }
        }
    }

    private fun proposalDialog() =
        hasAnyAncestor(isDialog()) and hasText(string(R.string.custody_proposal_inbox_body, coParentName), substring = true)

    /** (on-screen parent's slot, co-parent's slot). */
    private suspend fun slots(): Pair<String, String> {
        val mine = checkNotNull(bob.userRepository.getRemoteUserProfile(aliceUid)).role
        val theirs = checkNotNull(bob.userRepository.getRemoteUserProfile(bob.uid)).role
        check(mine != theirs) { "the pair still shares one slot" }
        return mine to theirs
    }

    private suspend fun coParentSetsFirstPattern(zuzanaFirst: Boolean): CustodyModel {
        val (mine, _) = slots()
        val onScreenFirst = if (onScreenName == ZUZANA) zuzanaFirst else !zuzanaFirst
        val submission = bob.custodyRepository.createWeekOnWeekOff(
            LocalDate.now(),
            momFirst = (mine == ContactWindow.SLOT_ONE) == onScreenFirst
        )
        check(submission == PatternSubmission.ACTIVATED) { "the first pattern was $submission" }
        val model = checkNotNull(bob.custodyRepository.getActiveModelSync())
        awaitPattern(custodyRepository) { it.startDate == model.startDate }
        return model
    }

    private suspend fun awaitPattern(repository: CustodyModelRepository, condition: (CustodyModel) -> Boolean): CustodyModel =
        withTimeout(EmulatorParent.WAIT_MS) {
            checkNotNull(repository.getActiveModel().first { it != null && condition(it) })
        }

    private companion object {
        const val LAYER_ID = "qa-christmas"
        const val LAYER_NAME = "Vánoce v Praze"
    }
}
