package me.foxtails.palustris.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileIdentityTest {
    @Test
    fun splitsAFederatedHandleBetweenNameAndDomain() {
        assertEquals(listOf("@longusername", "@longdoma.in"), splitHandle("@longusername@longdoma.in"))
    }

    @Test
    fun aLocalHandleHasNothingToSplit() {
        assertNull(splitHandle("@local"))
        assertNull(splitHandle(""))
    }
}
