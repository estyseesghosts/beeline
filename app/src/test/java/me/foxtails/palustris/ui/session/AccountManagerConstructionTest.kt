package me.foxtails.palustris.ui.session

import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountManagerConstructionTest {
    @Test
    fun hasOnlyTheInjectedConstructor() {
        val constructors = AccountManager::class.java.declaredConstructors

        assertEquals(1, constructors.size)
        assertTrue(constructors.single().isAnnotationPresent(Inject::class.java))
    }
}
