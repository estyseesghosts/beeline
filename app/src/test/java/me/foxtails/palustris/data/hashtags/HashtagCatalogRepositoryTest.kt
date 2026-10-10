package me.foxtails.palustris.data.hashtags

import java.io.ByteArrayInputStream
import java.io.IOException
import me.foxtails.palustris.domain.hashtags.HashtagRelation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HashtagCatalogRepositoryTest {
    @Test
    fun realAssetHoldsEveryGroupAndMember() {
        val catalog = RealHashtagCatalog.catalog
        assertEquals(74, catalog.groups.size)
        assertEquals(1814, catalog.memberCount)
        catalog.groups.forEach { group ->
            assertEquals(
                "${group.id} needs exactly one head",
                1,
                group.members.count { it.relation == HashtagRelation.Head },
            )
        }
    }

    @Test
    fun realAssetReferencesOnlyExistingGroups() {
        val catalog = RealHashtagCatalog.catalog
        catalog.groups.forEach { group ->
            (group.excludedGroups + group.childGroups).forEach { id ->
                assertTrue("${group.id} references unknown group $id", catalog.group(id) != null)
            }
        }
    }

    @Test
    fun identityIndexAndPrefixLookupWork() {
        val catalog = RealHashtagCatalog.catalog
        assertEquals("photography", catalog.entries("foto").single().group.id)
        assertTrue(catalog.entries("nonexistenttagname").isEmpty())
        val names = catalog.prefixEntries("pho").map { it.member.name }
        assertTrue(names.isNotEmpty() && names.all { it.startsWith("pho") })
        assertTrue(catalog.prefixEntries("").isEmpty())
        assertTrue(catalog.prefixEntries("zzzzzzzz").isEmpty())
    }

    @Test
    fun repositoryReadsTheAssetOnceAndKeepsTheInstance() {
        var opened = 0
        val bytes = RealHashtagCatalog.file.readBytes()
        val repository = HashtagCatalogRepository {
            opened++
            ByteArrayInputStream(bytes)
        }
        assertSame(repository.catalog, repository.catalog)
        assertEquals(1, opened)
        assertEquals(74, repository.catalog.groups.size)
    }

    @Test
    fun missingAssetGivesAnEmptyCatalog() {
        val catalog = HashtagCatalogRepository { throw IOException("missing") }.catalog
        assertTrue(catalog.groups.isEmpty())
    }

    @Test
    fun invalidAssetsGiveAnEmptyCatalog() {
        listOf("not json", "{}", """{"v": 2, "groups": []}""", """{"v": 1, "groups": [{"id": "a"}]}""").forEach { text ->
            val catalog = HashtagCatalogRepository { ByteArrayInputStream(text.toByteArray()) }.catalog
            assertTrue(text, catalog.groups.isEmpty())
        }
    }

    @Test
    fun unknownRelationsAreDroppedAndNamesAreLowercased() {
        val text = """{"v":1,"groups":[{"id":"a","m":[["A",["en"],"h",1.0],["b",["en"],"q",1.0]]}]}"""
        val group = HashtagCatalogParser.parse(text).groups.single()
        assertEquals(listOf("a"), group.members.map { it.name })
    }
}
