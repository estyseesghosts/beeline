package me.foxtails.palustris.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.shell.LocalPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShellNavigatorRestorationTest {
    @get:Rule val compose = createComposeRule()

    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val accountId = AccountId(connection, "owner")
    private val account = Account(accountId, "Owner", "@owner@example.org")
    private val otherId = AccountId(Connection("https://other.example", Protocol.MASTODON), "other")

    private fun host(
        currentId: AccountId?,
        revision: Long,
        holder: Array<ShellNavigator?>,
    ) {
        compose.setContent {
            holder[0] = rememberShellNavigator(
                accountId = currentId,
                sessionRevision = revision,
                initialRoute = null,
                availableTimelines = Timeline.entries.toSet(),
                selectedHomeTimeline = null,
                reducedMotion = true,
                onClearTransient = {},
                onSearch = {},
                onStartConversation = {},
            )
        }
    }

    @Test
    fun matchingSessionRestoresQueryAfterFirstEffects() {
        val holder = arrayOfNulls<ShellNavigator>(1)
        val restoration = StateRestorationTester(compose)
        var currentId: AccountId? = accountId
        var revision = 7L
        restoration.setContent {
            holder[0] = rememberShellNavigator(
                accountId = currentId,
                sessionRevision = revision,
                initialRoute = null,
                availableTimelines = Timeline.entries.toSet(),
                selectedHomeTimeline = null,
                reducedMotion = true,
                onClearTransient = {},
                onSearch = {},
                onStartConversation = {},
            )
        }
        compose.runOnIdle {
            holder[0]!!.searchQuery = "photography"
            holder[0]!!.searchCategory = 2
            holder[0]!!.searchPrefill = "photo"
            holder[0]!!.page = LocalPage.Drafts
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals("photography", holder[0]!!.searchQuery)
        assertEquals(2, holder[0]!!.searchCategory)
        assertEquals("photo", holder[0]!!.searchPrefill)
        assertEquals(LocalPage.Drafts, holder[0]!!.page)
    }

    @Test
    fun differentAccountClearsRestoredQueryBeforeDisplay() {
        val holder = arrayOfNulls<ShellNavigator>(1)
        val restoration = StateRestorationTester(compose)
        var currentId: AccountId? = accountId
        restoration.setContent {
            holder[0] = rememberShellNavigator(
                accountId = currentId,
                sessionRevision = 7L,
                initialRoute = null,
                availableTimelines = Timeline.entries.toSet(),
                selectedHomeTimeline = null,
                reducedMotion = true,
                onClearTransient = {},
                onSearch = {},
                onStartConversation = {},
            )
        }
        compose.runOnIdle {
            holder[0]!!.searchQuery = "photography"
            holder[0]!!.searchCategory = 2
            holder[0]!!.searchPrefill = "photo"
            holder[0]!!.page = LocalPage.Drafts
        }
        currentId = otherId
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals("", holder[0]!!.searchQuery)
        assertEquals(0, holder[0]!!.searchCategory)
        assertEquals("", holder[0]!!.searchPrefill)
        assertNull(holder[0]!!.page)
    }

    @Test
    fun sameAccountNewRevisionClearsRestoredQuery() {
        val holder = arrayOfNulls<ShellNavigator>(1)
        val restoration = StateRestorationTester(compose)
        var revision = 7L
        restoration.setContent {
            holder[0] = rememberShellNavigator(
                accountId = accountId,
                sessionRevision = revision,
                initialRoute = null,
                availableTimelines = Timeline.entries.toSet(),
                selectedHomeTimeline = null,
                reducedMotion = true,
                onClearTransient = {},
                onSearch = {},
                onStartConversation = {},
            )
        }
        compose.runOnIdle {
            holder[0]!!.searchQuery = "photography"
            holder[0]!!.searchPrefill = "photo"
        }
        revision = 8L
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals("", holder[0]!!.searchQuery)
        assertEquals("", holder[0]!!.searchPrefill)
    }

    @Test
    fun ordinaryRecompositionKeepsQuery() {
        val holder = arrayOfNulls<ShellNavigator>(1)
        var tick by mutableStateOf(0)
        compose.setContent {
            tick.let {
                holder[0] = rememberShellNavigator(
                    accountId = accountId,
                    sessionRevision = 7L,
                    initialRoute = null,
                    availableTimelines = Timeline.entries.toSet(),
                    selectedHomeTimeline = null,
                    reducedMotion = true,
                    onClearTransient = {},
                    onSearch = {},
                    onStartConversation = {},
                )
            }
        }
        compose.runOnIdle { holder[0]!!.searchQuery = "photography" }
        tick = 1
        compose.waitForIdle()
        assertEquals("photography", holder[0]!!.searchQuery)
    }

    @Test
    fun previewIdentityClearsRestoredQuery() {
        val holder = arrayOfNulls<ShellNavigator>(1)
        val restoration = StateRestorationTester(compose)
        var currentId: AccountId? = accountId
        restoration.setContent {
            holder[0] = rememberShellNavigator(
                accountId = currentId,
                sessionRevision = 7L,
                initialRoute = null,
                availableTimelines = Timeline.entries.toSet(),
                selectedHomeTimeline = null,
                reducedMotion = true,
                onClearTransient = {},
                onSearch = {},
                onStartConversation = {},
            )
        }
        compose.runOnIdle { holder[0]!!.searchQuery = "photography" }
        currentId = null
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals("", holder[0]!!.searchQuery)
    }
}
