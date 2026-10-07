package me.foxtails.palustris

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductIdentityTest {
    @Test
    fun generatedIdentityUsesBeelineVersion() {
        assertEquals("Beeline", BuildConfig.PRODUCT_NAME)
        assertEquals("Beeline/${BuildConfig.PRODUCT_VERSION} (Android)", ProductIdentity.userAgent)
    }
}
