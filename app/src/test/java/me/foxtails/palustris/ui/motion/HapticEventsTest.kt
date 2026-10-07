package me.foxtails.palustris.ui.motion

import android.os.Build
import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticEventsTest {
    @Test
    fun everyEventMapsToAPlatformConstantOnEachSupportedSdk() {
        for (sdk in listOf(Build.VERSION_CODES.Q, Build.VERSION_CODES.R, 35)) {
            val constants = HapticEvent.entries.map { it.platformConstant(sdk) }
            assertTrue(constants.all { it >= 0 })
        }
    }

    @Test
    fun api29UsesOnlyConstantsAvailableBeforeApi30() {
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, HapticEvent.Commit.platformConstant(Build.VERSION_CODES.Q))
        assertEquals(HapticFeedbackConstants.LONG_PRESS, HapticEvent.DestructiveConfirm.platformConstant(Build.VERSION_CODES.Q))
    }

    @Test
    fun api30UsesConfirmAndRejectForCommitAndDestructive() {
        assertEquals(HapticFeedbackConstants.CONFIRM, HapticEvent.Commit.platformConstant(Build.VERSION_CODES.R))
        assertEquals(HapticFeedbackConstants.REJECT, HapticEvent.DestructiveConfirm.platformConstant(Build.VERSION_CODES.R))
    }

    @Test
    fun commitIsDistinctFromSelectionAndThreshold() {
        val sdk = Build.VERSION_CODES.R
        val commit = HapticEvent.Commit.platformConstant(sdk)
        assertTrue(commit != HapticEvent.Selection.platformConstant(sdk))
        assertTrue(commit != HapticEvent.Threshold.platformConstant(sdk))
    }

    @Test
    fun performEmitsExactlyOneConstantPerCall() {
        val sent = mutableListOf<Int>()
        val haptics = PalustrisHaptics(sdk = Build.VERSION_CODES.R) { sent += it }
        haptics.perform(HapticEvent.Commit)
        assertEquals(listOf(HapticFeedbackConstants.CONFIRM), sent)
        haptics.perform(HapticEvent.LongPress)
        assertEquals(2, sent.size)
    }

    @Test
    fun defaultPerformerIsSilent() {
        PalustrisHaptics.None.perform(HapticEvent.Commit)
    }
}
