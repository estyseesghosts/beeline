package me.foxtails.palustris.data.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftWriteAuthorityTest {
    private val account = AccountId(Connection("https://draft-authority.example", Protocol.MISSKEY), "user")

    @Test
    fun removalRevokesWriterAndReleasesRecord() = runTest {
        val authority = DraftWriteAuthority()
        val generation = authority.activate(account)

        authority.invalidateAndDelete(account) {}

        assertFalse(authority.isCurrent(account, generation))
        assertNull(authority.commitIfCurrent(account, generation) { Unit })
        assertFalse(authority.hasRecordForTest(account))
    }

    @Test
    fun removalWaitsForInFlightCommitAndDoesNotRecreateRows() = runTest {
        val authority = DraftWriteAuthority()
        val generation = authority.activate(account)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        // The waiter holds the retiring record while removal waits on the lock.
        val writer = async {
            authority.commitIfCurrent(account, generation) {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val removal = async { authority.invalidateAndDelete(account) {} }
        release.complete(Unit)
        writer.await()
        removal.await()

        var ran = false
        assertNull(
            authority.commitIfCurrent(account, generation) {
                ran = true
            },
        )
        assertFalse(ran)
        assertFalse(authority.hasRecordForTest(account))
    }

    @Test
    fun lateCommitAfterRemovalCreatesNoRecord() = runTest {
        val authority = DraftWriteAuthority()
        val generation = authority.activate(account)
        authority.invalidateAndDelete(account) {}

        var ran = false
        assertNull(
            authority.commitIfCurrent(account, generation) {
                ran = true
            },
        )

        assertFalse(ran)
        assertFalse(authority.isCurrent(account, generation))
        assertFalse(authority.hasRecordForTest(account))
    }

    @Test
    fun waiterDuringRemovalStaysOnRetiringRecord() = runTest {
        val authority = DraftWriteAuthority()
        val generation = authority.activate(account)
        val holderEntered = CompletableDeferred<Unit>()
        val holderRelease = CompletableDeferred<Unit>()
        // The holder keeps the record alive while removal retires it.
        val holder = async {
            authority.commitIfCurrent(account, generation) {
                holderEntered.complete(Unit)
                holderRelease.await()
            }
        }
        holderEntered.await()
        val removal = async { authority.invalidateAndDelete(account) {} }
        runCurrent()
        // The waiter attaches to the retiring record and cannot run its block.
        var ran = false
        val waiter = async {
            authority.commitIfCurrent(account, generation) {
                ran = true
            }
        }
        runCurrent()
        holderRelease.complete(Unit)
        holder.await()
        removal.await()

        assertNull(waiter.await())
        assertFalse(ran)
        assertFalse(authority.hasRecordForTest(account))
    }

    @Test
    fun cancelledWaiterReleasesItsUserReference() = runTest {
        val authority = DraftWriteAuthority()
        val generation = authority.activate(account)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = launch {
            authority.commitIfCurrent(account, generation) {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val waiter = launch { authority.commitIfCurrent(account, generation) { Unit } }
        waiter.cancelAndJoin()
        release.complete(Unit)
        writer.join()

        authority.invalidateAndDelete(account) {}
        assertFalse(authority.isCurrent(account, generation))
        assertFalse(authority.hasRecordForTest(account))
    }

    @Test
    fun cancellationInsideCommitReleasesTheRecord() = runTest {
        val authority = DraftWriteAuthority()
        val generation = authority.activate(account)
        val entered = CompletableDeferred<Unit>()
        val inside = launch {
            authority.commitIfCurrent(account, generation) {
                entered.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            }
        }
        entered.await()
        inside.cancelAndJoin()

        authority.invalidateAndDelete(account) {}
        assertFalse(authority.isCurrent(account, generation))
        assertFalse(authority.hasRecordForTest(account))
    }

    @Test
    fun activationAfterRemovalKeepsGenerationMonotonic() = runTest {
        val authority = DraftWriteAuthority()
        val oldGeneration = authority.activate(account)
        authority.invalidateAndDelete(account) {}
        assertFalse(authority.hasRecordForTest(account))

        val newGeneration = authority.activate(account)

        assertFalse(authority.isCurrent(account, oldGeneration))
        assertTrue(newGeneration > oldGeneration)
        assertNull(authority.commitIfCurrent(account, oldGeneration) { Unit })
        assertTrue(authority.isCurrent(account, newGeneration))
        assertTrue(authority.hasRecordForTest(account))
    }

    @Test
    fun removalDoesNotBlockAnotherAccount() = runTest {
        val authority = DraftWriteAuthority()
        val other = account.copy(localId = "other")
        val generation = authority.activate(account)
        val otherGeneration = authority.activate(other)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = launch {
            authority.commitIfCurrent(account, generation) {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val removal = launch { authority.invalidateAndDelete(account) {} }
        val otherResult = authority.commitIfCurrent(other, otherGeneration) { "ok" }

        assertEquals("ok", otherResult)
        release.complete(Unit)
        writer.join()
        removal.join()

        assertFalse(authority.hasRecordForTest(account))
        assertTrue(authority.hasRecordForTest(other))
        assertTrue(authority.isCurrent(other, otherGeneration))
    }
}
