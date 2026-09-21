package me.foxtails.palustris.data.notifications

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialEvent
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NotificationSyncOrchestratorTest {
    private val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "receiver")
    private val otherAccount = account.copy(localId = "other")
    private val source = EmptySource()

    @Test
    fun noOpUsesOneAllocatorAcrossAccountsAndRemoval() = runBlocking {
        val controller = NoOpNotificationSyncController()

        assertEquals(1L, controller.register(account, source).generation)
        assertEquals(2L, controller.register(otherAccount, source).generation)
        controller.unregister(account)
        assertEquals(3L, controller.register(account, source).generation)
        controller.removeAccount(otherAccount)
        assertEquals(4L, controller.register(otherAccount, source).generation)
    }

    @Test
    fun registerMarksBothControllersActive() = runBlocking {
        val noOp = NoOpNotificationSyncController()
        noOp.register(account, source)
        assertTrue(noOp.observeAccount(account).value.isActive)

        val controller = NotificationSyncOrchestrator()
        try {
            controller.register(account, source)
            assertTrue(controller.observeAccount(account).value.isActive)
        } finally {
            controller.close()
        }
    }

    @Test
    fun noOpUnregisterClearsActiveEntry() = runBlocking {
        val controller = NoOpNotificationSyncController()
        controller.register(account, source)
        assertTrue(controller.observeAccount(account).value.isActive)

        controller.unregister(account)

        assertFalse(controller.observeAccount(account).value.isActive)
        assertFalse(noOpGenerationsOf(controller).containsKey(account))
    }

    @Test
    fun productionUsesTheSameLifetimeAllocation() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            val first = controller.register(account, source)
            controller.removeAccount(account)
            val second = controller.register(account, source)
            // Removal advances the repository past one allocation, so the
            // replacement retries to a strictly newer generation.
            assertTrue(second.generation > first.generation)
        } finally {
            controller.close()
        }
    }

    @Test
    fun unregisterRejectsLateStreamEventsAndRemovesActiveEntry() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            controller.register(account, source)
            controller.unregister(account)

            assertFalse(controller.accept(Event(account, SocialEvent.Other("late"))))
            assertFalse(controller.observeAccount(account).value.isActive)
        } finally {
            controller.close()
        }
    }

    @Test
    fun noOpRemovalAlsoLeavesNoActiveEntry() = runBlocking {
        val controller = NoOpNotificationSyncController()
        controller.register(account, source)
        assertTrue(controller.observeAccount(account).value.isActive)

        controller.removeAccount(account)

        assertFalse(controller.observeAccount(account).value.isActive)
        assertFalse(noOpGenerationsOf(controller).containsKey(account))
    }

    @Test
    fun noOpRemovalReleasesEntryAndNextRegistrationStaysFresh() = runBlocking {
        val controller = NoOpNotificationSyncController()
        val first = controller.register(account, source)
        val other = controller.register(otherAccount, source)
        assertTrue(controller.observeAccount(account).value.isActive)
        controller.removeAccount(account)

        assertFalse(controller.observeAccount(account).value.isActive)
        assertFalse(noOpGenerationsOf(controller).containsKey(account))
        val replacement = controller.register(account, source)
        assertTrue(replacement.generation > first.generation)
        assertTrue(replacement.generation > other.generation)
        assertTrue(controller.observeAccount(account).value.isActive)
    }

    @Test
    fun productionRemovalRejectsLateEventsAndDropsGenerationEntry() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            val token = controller.register(account, source)
            assertTrue(controller.observeAccount(account).value.isActive)
            controller.removeAccount(account)

            assertFalse(controller.accept(Event(account, SocialEvent.Other("late"))))
            assertFalse(controller.observeAccount(account).value.isActive)
            assertFalse(generationsOf(controller).containsKey(account))

            val replacement = controller.register(account, source)
            assertTrue(replacement.generation > token.generation)
            assertTrue(controller.observeAccount(account).value.isActive)
        } finally {
            controller.close()
        }
    }

    @Test
    fun productionRemovalLeavesOtherAccountActive() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            controller.register(account, source)
            controller.register(otherAccount, source)
            controller.removeAccount(account)

            assertTrue(controller.observeAccount(otherAccount).value.isActive)
            assertTrue(controller.accept(Event(otherAccount, SocialEvent.Other("other"))))
            assertFalse(controller.accept(Event(account, SocialEvent.Other("late"))))
        } finally {
            controller.close()
        }
    }

    @Test
    fun reAddRejectsOldTokenAndAcceptsReplacement() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            val first = controller.register(account, source)
            controller.removeAccount(account)
            val replacement = controller.register(account, source)
            val repository = repositoryOf(controller)

            assertTrue(replacement.generation > first.generation)
            assertEquals(replacement, repository.currentToken(account))
            assertFalse(repository.updateUnreadState(first, NotificationUnreadState.Exact(1)))
            assertTrue(repository.updateUnreadState(replacement, NotificationUnreadState.Exact(1)))
            assertTrue(controller.observeAccount(account).value.isActive)
            assertTrue(controller.accept(Event(account, SocialEvent.Other("new"))))
        } finally {
            controller.close()
        }
    }

    @Test
    fun generationsStayMonotonicAcrossRepeatedRemoval() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            var previous = controller.register(account, source)
            repeat(3) {
                controller.removeAccount(account)
                val next = controller.register(account, source)
                assertTrue(next.generation > previous.generation)
                assertEquals(next, repositoryOf(controller).currentToken(account))
                previous = next
            }
        } finally {
            controller.close()
        }
    }

    @Test
    fun concurrentRemoveAndRegisterLeavesNoStaleResurrection() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            val first = controller.register(account, source)
            val racing = async(Dispatchers.Default) { controller.register(account, source) }
            val removal = async(Dispatchers.Default) { controller.removeAccount(account) }
            val replacement = racing.await()
            removal.await()
            val repository = repositoryOf(controller)

            // The old token never validates again, whatever the winner is.
            assertFalse(repository.updateUnreadState(first, NotificationUnreadState.Exact(1)))
            val active = generationsOf(controller)[account]
            if (active == null) {
                assertFalse(controller.observeAccount(account).value.isActive)
                assertNull(repository.currentToken(account))
                assertFalse(controller.accept(Event(account, SocialEvent.Other("late"))))
            } else {
                assertTrue(active > first.generation)
                assertEquals(replacement.generation, active)
                assertEquals(replacement, repository.currentToken(account))
                assertTrue(controller.observeAccount(account).value.isActive)
                assertTrue(controller.accept(Event(account, SocialEvent.Other("new"))))
            }
        } finally {
            controller.close()
        }
    }

    @Test
    fun staleRegisterDuringRemovalIssuesNoRepositoryChange() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            val first = controller.register(account, source)
            val repository = repositoryOf(controller)
            assertEquals(first, repository.currentToken(account))
            val allocatorBefore = allocatorValue(controller)
            // The gate runs outside locks before activation. Removal finishes
            // fully while the racing registration waits. The store gate cannot
            // do this: it holds the lock that removal needs.
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            controller.preActivateGate = {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "Activate gate never released." }
            }
            val staleCall = async(Dispatchers.Default) { controller.register(account, source) }
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                controller.removeAccount(account)
                assertNull(repository.currentToken(account))
                assertFalse(generationsOf(controller).containsKey(account))
            } finally {
                release.countDown()
            }
            val stale = withTimeout(10_000) { staleCall.await() }
            controller.preActivateGate = null
            // The stale token activates late after removal and undoes itself.
            // No token remains. No active entry remains. The tombstone blocks reuse.
            assertTrue(stale.generation > first.generation)
            assertNull(repository.currentToken(account))
            assertFalse(generationsOf(controller).containsKey(account))
            assertFalse(repository.updateUnreadState(stale, NotificationUnreadState.Exact(1)))
            assertFalse(controller.accept(Event(account, SocialEvent.Other("late"))))
            assertFalse(controller.observeAccount(account).value.isActive)
            assertFalse(jobsOf(controller).containsKey(account))
            assertFalse(repositoryStateKeys(repository).contains(account))
            assertFalse(orchestratorStateKeys(controller).contains(account))
            assertTrue((retiredGenerationsOf(repository)[account] ?: 0L) >= first.generation)
            assertTrue(allocatorValue(controller) - allocatorBefore <= 5)
        } finally {
            controller.preActivateGate = null
            controller.close()
        }
    }

    @Test
    fun forcedRemoveVsRegisterInterleavingNeverOrphans() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            val first = controller.register(account, source)
            val repository = repositoryOf(controller)
            // The gate runs outside locks before activation, so removal runs to
            // completion while the racing registration waits. Removal wins here.
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            controller.preActivateGate = {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "Activate gate never released." }
            }
            val racingCall = async(Dispatchers.Default) { controller.register(account, source) }
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                controller.removeAccount(account)
            } finally {
                release.countDown()
            }
            val replacement = withTimeout(10_000) { racingCall.await() }
            controller.preActivateGate = null
            // Removal wins. The map and the repository agree: both stay empty.
            // No orphan survives on either side.
            assertTrue(replacement.generation > first.generation)
            assertFalse(generationsOf(controller).containsKey(account))
            assertNull(repository.currentToken(account))
            assertFalse(repository.updateUnreadState(replacement, NotificationUnreadState.Exact(1)))
            assertFalse(repository.updateUnreadState(first, NotificationUnreadState.Exact(1)))
            assertFalse(controller.observeAccount(account).value.isActive)
            assertFalse(controller.accept(Event(account, SocialEvent.Other("late"))))
            assertFalse(jobsOf(controller).containsKey(account))
            assertFalse(repositoryStateKeys(repository).contains(account))
            assertFalse(orchestratorStateKeys(controller).contains(account))
        } finally {
            controller.preActivateGate = null
            controller.close()
        }
    }

    @Test
    fun retryExhaustionStaysBoundedAndLeavesNoPoll() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            val repository = repositoryOf(controller)
            // Seed a newer repository owner without retirement. Every activation
            // rejects. The orchestrator map keeps its null baseline. Each
            // rejection allocates a newer generation and retries.
            repository.invalidate(account, 1_000_000L)
            val allocatorBefore = allocatorValue(controller)
            val activationCalls = AtomicLong(0)
            controller.preActivateGate = {
                activationCalls.incrementAndGet()
                Unit
            }
            val token = try {
                controller.register(account, source)
            } finally {
                controller.preActivateGate = null
            }
            // Production allows four activations. The fifth allocation is the
            // inactive terminal token. The loop ran its full bound.
            assertEquals(4L, activationCalls.get())
            assertEquals(5L, allocatorValue(controller) - allocatorBefore)
            // No retry path retired the account. Rejection stayed transient.
            assertFalse(retiredGenerationsOf(repository).containsKey(account))
            // Exhaustion published nothing. No active entry remains. No poll runs.
            assertFalse(generationsOf(controller).containsKey(account))
            assertFalse(jobsOf(controller).containsKey(account))
            assertFalse(repositoryStateKeys(repository).contains(account))
            assertFalse(orchestratorStateKeys(controller).contains(account))
            // The planted owner still holds the repository. The exhausted token
            // never validated, so its writes fail.
            assertTrue((repository.currentToken(account)?.generation ?: 0L) > token.generation)
            assertFalse(repository.updateUnreadState(token, NotificationUnreadState.Exact(1)))
            assertFalse(controller.observeAccount(account).value.isActive)
        } finally {
            controller.preActivateGate = null
            controller.close()
        }
    }

    @Test
    fun retiredAccountSkipsRetryLoop() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            val repository = repositoryOf(controller)
            retirePermanently(repository, account)
            val allocatorBefore = allocatorValue(controller)
            val activationCalls = AtomicLong(0)
            controller.preActivateGate = {
                activationCalls.incrementAndGet()
                Unit
            }
            val token = try {
                controller.register(account, source)
            } finally {
                controller.preActivateGate = null
            }
            // Retirement wins on the first attempt. No retry follows.
            assertEquals(1L, activationCalls.get())
            assertEquals(1L, allocatorValue(controller) - allocatorBefore)
            assertFalse(controller.observeAccount(account).value.isActive)
            assertFalse(generationsOf(controller).containsKey(account))
            assertNull(repository.currentToken(account))
            assertFalse(repository.updateUnreadState(token, NotificationUnreadState.Exact(1)))
            assertFalse(jobsOf(controller).containsKey(account))
            assertFalse(orchestratorStateKeys(controller).contains(account))
        } finally {
            controller.preActivateGate = null
            controller.close()
        }
    }

    @Test
    fun cancelledRegisterPublishesNothing() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            val repository = repositoryOf(controller)
            // Registration publishes only after repository agreement with no
            // provisional insert, so a registration that never runs leaves nothing.
            val pending = async(start = CoroutineStart.LAZY) { controller.register(account, source) }
            pending.cancel()
            try {
                pending.await()
                fail("A cancelled registration must not complete.")
            } catch (cancelled: CancellationException) {
                assertTrue(pending.isCancelled)
            }
            assertFalse(generationsOf(controller).containsKey(account))
            assertFalse(controller.observeAccount(account).value.isActive)
            assertNull(repository.currentToken(account))
            // A later registration still succeeds with repository agreement.
            val replacement = controller.register(account, source)
            assertEquals(replacement, repository.currentToken(account))
            assertTrue(controller.observeAccount(account).value.isActive)
        } finally {
            controller.close()
        }
    }

    @Test
    fun staleActivationAfterRemovalCompletesUndoesItself() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            val first = controller.register(account, source)
            val repository = repositoryOf(controller)
            // Hold the racing registration before activation. The gate runs
            // outside locks, so removal completes while activation waits.
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            controller.preActivateGate = {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "Activate gate never released." }
            }
            val staleCall = async(Dispatchers.Default) { controller.register(account, source) }
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                controller.removeAccount(account)
                assertNull(repository.currentToken(account))
                assertFalse(generationsOf(controller).containsKey(account))
            } finally {
                release.countDown()
            }
            val stale = withTimeout(10_000) { staleCall.await() }
            // The stale token undoes its own late activation. No token
            // remains. No active entry remains. The tombstone blocks reuse.
            assertTrue(stale.generation > first.generation)
            assertNull(repository.currentToken(account))
            assertFalse(generationsOf(controller).containsKey(account))
            assertFalse(repository.updateUnreadState(stale, NotificationUnreadState.Exact(1)))
            assertFalse(controller.accept(Event(account, SocialEvent.Other("late"))))
            assertFalse(repositoryStateKeys(repository).contains(account))
            assertTrue((retiredGenerationsOf(repository)[account] ?: 0L) >= first.generation)
            assertFalse(controller.observeAccount(account).value.isActive)
            assertFalse(jobsOf(controller).containsKey(account))
            assertFalse(orchestratorStateKeys(controller).contains(account))
            // A replacement still succeeds with a strictly newer generation.
            controller.preActivateGate = null
            val replacement = controller.register(account, source)
            assertTrue(replacement.generation > stale.generation)
            assertEquals(replacement, repository.currentToken(account))
            assertTrue(controller.observeAccount(account).value.isActive)
            assertTrue(controller.accept(Event(account, SocialEvent.Other("new"))))
        } finally {
            controller.preActivateGate = null
            controller.close()
        }
    }

    @Test
    fun lateLoserAfterWinnerKeepsWinnerIntact() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            val repository = repositoryOf(controller)
            // Hold only the first activation. The gate runs outside locks, so
            // the winner publishes fully while the loser waits. The loser then
            // activates late and must lose without harm.
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val gatedCalls = AtomicLong(0)
            controller.preActivateGate = {
                if (gatedCalls.getAndIncrement() == 0L) {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS)) { "Activate gate never released." }
                }
            }
            val loserCall = async(Dispatchers.Default) { controller.register(account, source) }
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                val winner = controller.register(account, source)
                assertEquals(winner, repository.currentToken(account))
                assertTrue(controller.observeAccount(account).value.isActive)
                release.countDown()
                val loser = withTimeout(10_000) { loserCall.await() }
                controller.preActivateGate = null
                // The loser stays inactive. The winner still owns the token
                // and the active map. The loser clears nothing.
                assertTrue(winner.generation > loser.generation)
                assertEquals(winner, repository.currentToken(account))
                assertEquals(winner.generation, generationsOf(controller)[account])
                assertFalse(repository.updateUnreadState(loser, NotificationUnreadState.Exact(1)))
                assertTrue(repository.updateUnreadState(winner, NotificationUnreadState.Exact(1)))
                assertTrue(controller.observeAccount(account).value.isActive)
                assertTrue(controller.accept(Event(account, SocialEvent.Other("new"))))
                assertTrue(repositoryStateKeys(repository).contains(account))
                assertFalse(retiredGenerationsOf(repository).containsKey(account))
            } finally {
                release.countDown()
                controller.preActivateGate = null
            }
        } finally {
            controller.preActivateGate = null
            controller.close()
        }
    }

    @Test
    fun removedAccountObserveLeavesNoActiveState() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            val first = controller.register(account, source)
            val repository = repositoryOf(controller)
            controller.removeAccount(account)
            // No active map entry remains. No current token remains.
            // No repository state remains before observe.
            assertFalse(generationsOf(controller).containsKey(account))
            assertNull(repository.currentToken(account))
            assertFalse(repositoryStateKeys(repository).contains(account))
            assertFalse(repository.updateUnreadState(first, NotificationUnreadState.Exact(1)))
            assertFalse(controller.accept(Event(account, SocialEvent.Other("late"))))
            // Observe reports inactive without reviving the account.
            // No generation returns. No token returns. No event accepts.
            // No repository state appears after observe. No display entry appears.
            assertFalse(controller.observeAccount(account).value.isActive)
            assertFalse(repositoryStateKeys(repository).contains(account))
            assertFalse(orchestratorStateKeys(controller).contains(account))
            assertFalse(generationsOf(controller).containsKey(account))
            assertNull(repository.currentToken(account))
            assertFalse(repository.updateUnreadState(first, NotificationUnreadState.Exact(1)))
            assertFalse(controller.accept(Event(account, SocialEvent.Other("late"))))
            assertTrue((retiredGenerationsOf(repository)[account] ?: 0L) >= first.generation)
        } finally {
            controller.close()
        }
    }

    @Test
    fun nullBaselineRemovalDuringActivateNeverRevives() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            val repository = repositoryOf(controller)
            // No active entry exists, so the racing registration uses a null baseline.
            assertFalse(generationsOf(controller).containsKey(account))
            assertNull(repository.currentToken(account))
            // Hold the racing registration after snapshot and allocation but before
            // activation. The gate runs outside locks, so removal can finish fully
            // while activation waits. The store gate cannot do this: it holds the
            // repository lock that removal needs, so removal would only wait.
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            controller.preActivateGate = {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "Activate gate never released." }
            }
            val racing = async(Dispatchers.Default) { controller.register(account, source) }
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                // The racing call already snapshotted the null baseline and the old
                // epoch and allocated its stale generation. Finish removal fully
                // before activation proceeds.
                try {
                    controller.removeAccount(account)
                    assertNull(repository.currentToken(account))
                    assertFalse(generationsOf(controller).containsKey(account))
                    assertTrue(retiredGenerationsOf(repository).containsKey(account))
                    assertTrue(removalEpochsOf(controller).containsKey(account))
                } finally {
                    release.countDown()
                }
                val stale = racing.await()
                // Removal during activation stays inactive. No generation entry remains.
                // No token remains. The tombstone covers the stale generation.
                // No orphan remains. No revival occurs.
                assertFalse(controller.observeAccount(account).value.isActive)
                assertFalse(generationsOf(controller).containsKey(account))
                assertNull(repository.currentToken(account))
                assertFalse(repository.updateUnreadState(stale, NotificationUnreadState.Exact(1)))
                assertFalse(controller.accept(Event(account, SocialEvent.Other("late"))))
                assertFalse(repositoryStateKeys(repository).contains(account))
                assertFalse(orchestratorStateKeys(controller).contains(account))
                assertFalse(jobsOf(controller).containsKey(account))
                assertTrue((retiredGenerationsOf(repository)[account] ?: 0L) >= stale.generation)
                // A fresh re-add after removal completes still succeeds with newer live state.
                controller.preActivateGate = null
                val replacement = controller.register(account, source)
                assertTrue(replacement.generation > stale.generation)
                assertEquals(replacement, repository.currentToken(account))
                assertTrue(controller.observeAccount(account).value.isActive)
                assertTrue(controller.accept(Event(account, SocialEvent.Other("new"))))
            } finally {
                release.countDown()
                controller.preActivateGate = null
            }
        } finally {
            controller.close()
        }
    }

    @Test
    fun noOpAndProductionAgreeOnActiveLifecycle() = runBlocking {
        val noOp = NoOpNotificationSyncController()
        val controller = NotificationSyncOrchestrator()
        try {
            val noOpFirst = noOp.register(account, source)
            val first = controller.register(account, source)
            assertTrue(noOp.observeAccount(account).value.isActive)
            assertTrue(controller.observeAccount(account).value.isActive)

            noOp.unregister(account)
            controller.unregister(account)
            assertFalse(noOp.observeAccount(account).value.isActive)
            assertFalse(controller.observeAccount(account).value.isActive)
            assertFalse(noOpGenerationsOf(noOp).containsKey(account))
            assertFalse(generationsOf(controller).containsKey(account))

            val noOpSecond = noOp.register(account, source)
            val second = controller.register(account, source)
            assertTrue(noOpSecond.generation > noOpFirst.generation)
            assertTrue(second.generation > first.generation)
            assertTrue(noOp.observeAccount(account).value.isActive)
            assertTrue(controller.observeAccount(account).value.isActive)

            noOp.removeAccount(account)
            controller.removeAccount(account)
            assertFalse(noOp.observeAccount(account).value.isActive)
            assertFalse(controller.observeAccount(account).value.isActive)
            assertFalse(noOpGenerationsOf(noOp).containsKey(account))
            assertFalse(generationsOf(controller).containsKey(account))
        } finally {
            controller.close()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun generationsOf(controller: NotificationSyncOrchestrator): Map<AccountId, Long> {
        val field = NotificationSyncOrchestrator::class.java.getDeclaredField("generations").apply {
            isAccessible = true
        }
        return (field.get(controller) as Map<AccountId, Long>).toMap()
    }

    @Suppress("UNCHECKED_CAST")
    private fun orchestratorStateKeys(controller: NotificationSyncOrchestrator): Set<AccountId> {
        val field = NotificationSyncOrchestrator::class.java.getDeclaredField("states").apply {
            isAccessible = true
        }
        return ((field.get(controller) as Map<AccountId, *>).keys.toSet())
    }

    @Suppress("UNCHECKED_CAST")
    private fun noOpGenerationsOf(controller: NoOpNotificationSyncController): Map<AccountId, Long> {
        val field = NoOpNotificationSyncController::class.java.getDeclaredField("generations").apply {
            isAccessible = true
        }
        return (field.get(controller) as Map<AccountId, Long>).toMap()
    }

    private fun repositoryOf(controller: NotificationSyncOrchestrator): NotificationRepository {
        val field = NotificationSyncOrchestrator::class.java.getDeclaredField("repository").apply {
            isAccessible = true
        }
        return field.get(controller) as NotificationRepository
    }

    private fun allocatorValue(controller: NotificationSyncOrchestrator): Long {
        val field = NotificationSyncOrchestrator::class.java.getDeclaredField("generationAllocator").apply {
            isAccessible = true
        }
        return (field.get(controller) as AtomicLong).get()
    }

    @Suppress("UNCHECKED_CAST")
    private fun jobsOf(controller: NotificationSyncOrchestrator): Map<AccountId, Job> {
        val field = NotificationSyncOrchestrator::class.java.getDeclaredField("jobs").apply {
            isAccessible = true
        }
        return (field.get(controller) as Map<AccountId, Job>).toMap()
    }

    @Suppress("UNCHECKED_CAST")
    private fun retirePermanently(repository: NotificationRepository, accountId: AccountId) {
        val field = NotificationRepository::class.java.getDeclaredField("retiredGenerations").apply {
            isAccessible = true
        }
        (field.get(repository) as MutableMap<AccountId, Long>)[accountId] = Long.MAX_VALUE
    }

    @Suppress("UNCHECKED_CAST")
    private fun retiredGenerationsOf(repository: NotificationRepository): Map<AccountId, Long> {
        val field = NotificationRepository::class.java.getDeclaredField("retiredGenerations").apply {
            isAccessible = true
        }
        return ((field.get(repository) as Map<AccountId, Long>).toMap())
    }

    @Suppress("UNCHECKED_CAST")
    private fun repositoryStateKeys(repository: NotificationRepository): Set<AccountId> {
        val field = NotificationRepository::class.java.getDeclaredField("states").apply {
            isAccessible = true
        }
        return ((field.get(repository) as Map<AccountId, *>).keys.toSet())
    }

    @Suppress("UNCHECKED_CAST")
    private fun removalEpochsOf(controller: NotificationSyncOrchestrator): Map<AccountId, Long> {
        val field = NotificationSyncOrchestrator::class.java.getDeclaredField("removalEpochs").apply {
            isAccessible = true
        }
        return ((field.get(controller) as Map<AccountId, Long>).toMap())
    }

    private class EmptySource : SocialSource {
        override val capabilities = ServerCapabilities()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun notifications(
            query: NotificationQuery,
            cursor: me.foxtails.palustris.domain.NotificationCursor?,
        ): NotificationPage = NotificationPage(emptyList())

        override suspend fun fetchNewerNotifications(
            query: NotificationQuery,
            checkpoint: me.foxtails.palustris.domain.NotificationCheckpoint,
        ): NotificationPage = NotificationPage(emptyList())

        override suspend fun fetchOlderNotifications(
            query: NotificationQuery,
            checkpoint: me.foxtails.palustris.domain.NotificationCheckpoint,
        ): NotificationPage = NotificationPage(emptyList())

        override suspend fun notificationUnreadState(): NotificationUnreadState =
            NotificationUnreadState.Unknown
    }
}
