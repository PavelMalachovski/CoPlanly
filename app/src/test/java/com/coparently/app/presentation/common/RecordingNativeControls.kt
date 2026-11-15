package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * A stand-in for the iPhone's native controls in a Robolectric test: each control records what it
 * was asked to draw and draws a plain tagged box, so a test reaches the shell's branch of an
 * adaptive control without UIKit. Later controls add a list and a member here.
 */
class RecordingNativeControls : NativeControls {

    /** One drawn switch: its value and whether it could be flipped. */
    data class SwitchCall(val checked: Boolean, val enabled: Boolean)

    /** Every switch drawn, in the order of composition. */
    val switches = mutableListOf<SwitchCall>()

    /** The latest switch's callback, for a test that flips it. */
    var lastSwitchChange: ((Boolean) -> Unit)? = null

    /** One drawn segmented control: its titles, chosen index and whether it could be used. */
    data class SegmentedCall(val labels: List<String>, val selectedIndex: Int, val enabled: Boolean)

    /** Every segmented control drawn, in the order of composition. */
    val segmented = mutableListOf<SegmentedCall>()

    /** The latest segmented control's callback, for a test that taps a segment. */
    var lastSegmentSelect: ((Int) -> Unit)? = null

    @Composable
    override fun Switch(
        checked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
        enabled: Boolean,
        accessibilityLabel: String?,
        modifier: Modifier
    ) {
        switches += SwitchCall(checked = checked, enabled = enabled)
        lastSwitchChange = onCheckedChange
        Box(modifier.size(51.dp, 31.dp).testTag(SWITCH_TAG))
    }

    @Composable
    override fun Segmented(
        labels: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        enabled: Boolean,
        modifier: Modifier
    ) {
        segmented += SegmentedCall(labels = labels, selectedIndex = selectedIndex, enabled = enabled)
        lastSegmentSelect = onSelect
        Box(modifier.size(200.dp, 32.dp).testTag(SEGMENTED_TAG))
    }

    companion object {
        /** The test tag of a segmented control this fake drew. */
        const val SEGMENTED_TAG = "native-segmented"

        /** The test tag of a switch this fake drew. */
        const val SWITCH_TAG = "native-switch"
    }
}
