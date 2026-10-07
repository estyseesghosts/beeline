package me.foxtails.palustris.ui.posts

import me.foxtails.palustris.domain.ContentWarningDecision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostContentPolicyTest {
    @Test
    fun postWithoutWarningIsVisibleUnlessHidden() {
        assertTrue(isPostContentVisible(ContentWarningDecision.entries.first { it != ContentWarningDecision.Hidden }, false, false))
        assertFalse(isPostContentVisible(ContentWarningDecision.Hidden, false, true))
    }

    @Test
    fun warnedPostNeedsExpansionUnlessExpandedByDefault() {
        val collapsed = ContentWarningDecision.entries.first { it != ContentWarningDecision.Hidden && it != ContentWarningDecision.ExpandedByDefault }
        assertFalse(isPostContentVisible(collapsed, true, false))
        assertTrue(isPostContentVisible(collapsed, true, true))
        assertTrue(isPostContentVisible(ContentWarningDecision.ExpandedByDefault, true, false))
    }

    @Test
    fun hiddenNeverShowsEvenWhenExpanded() {
        assertFalse(isPostContentVisible(ContentWarningDecision.Hidden, true, true))
    }
}
