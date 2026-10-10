package me.foxtails.palustris.domain.hashtags

import java.util.Locale
import me.foxtails.palustris.data.hashtags.RealHashtagCatalog
import me.foxtails.palustris.domain.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HashtagExpanderTest {
    private val expander = HashtagExpander(RealHashtagCatalog.catalog)
    private val english = HashtagLanguagePolicy.forLanguage(AppLanguage.English)

    private fun expand(
        tag: String,
        enabled: Boolean = true,
        limit: Int = 100,
        policy: HashtagLanguagePolicy = english,
    ) = expander.expand(tag, enabled, policy, limit)

    @Test
    fun fotoMergesThePhotographyGroup() {
        val applied = expand("foto").applied
        assertTrue(applied.containsAll(listOf("photography", "fotografia", "写真")))
        assertFalse("foto" in applied)
    }

    @Test
    fun techMergesTechnologyWithNarrowerMembers() {
        val applied = expand("Tech").applied
        assertTrue(applied.containsAll(listOf("technology", "computers", "computing")))
    }

    @Test
    fun soccerDoesNotMergeFootballMembers() {
        val applied = expand("soccer").applied
        assertTrue("futbol" in applied)
        val football = RealHashtagCatalog.catalog.group("football")!!.members.map { it.name }.toSet()
        assertTrue("excluded group leaked: ${applied.filter { it in football }}", applied.none { it in football })
    }

    @Test
    fun ambiguousHashtagMergesNothing() {
        assertEquals(HashtagExpansion.None, expand("art"))
        assertEquals(HashtagExpansion.None, expand("#ART"))
    }

    @Test
    fun ambiguousMembersNeverEnterAnotherMerge() {
        RealHashtagCatalog.catalog.groups.forEach { group ->
            val head = group.head?.name ?: return@forEach
            val applied = expand(head).applied
            assertTrue("${group.id} leaked an ambiguous member", applied.none { it in group.ambiguous })
        }
    }

    @Test
    fun narrowerHashtagMergesNothingAndSuggestsTheHead() {
        val expansion = expand("caturday")
        assertTrue(expansion.applied.isEmpty())
        assertTrue("cats" in expansion.related)
    }

    @Test
    fun settingOffMergesNothing() {
        assertEquals(HashtagExpansion.None, expand("foto", enabled = false))
    }

    @Test
    fun unknownAndInvalidHashtagsMergeNothing() {
        assertEquals(HashtagExpansion.None, expand("nonexistenttagname"))
        assertEquals(HashtagExpansion.None, expand("not a tag"))
        assertEquals(HashtagExpansion.None, expand(""))
    }

    @Test
    fun zeroLimitKeepsOnlyRelated() {
        assertTrue(expand("foto", limit = 0).applied.isEmpty())
    }

    @Test
    fun parentGroupsSuggestChildHeadsAndRelatedMembersFilterByLanguage() {
        assertTrue("basketball" in expand("sports").related)
        val french = HashtagLanguagePolicy.forLanguage(AppLanguage.French)
        val catalog = RealHashtagCatalog.catalog
        RealHashtagCatalog.catalog.groups.forEach { group ->
            val head = group.head?.name ?: return@forEach
            expand(head, policy = french).related.forEach { name ->
                val passes = catalog.entries(name).any { french.allows(it.member) }
                assertTrue("$name must pass the French policy", passes)
            }
        }
    }

    @Test
    fun limitKeepsTheBestMemberOfEachLanguageBeforeLowerWeightEnglishMembers() {
        val expansion = expand("photography", limit = 3)
        val catalog = RealHashtagCatalog.catalog
        assertEquals(3, expansion.applied.size)
        val languages = expansion.applied.map { catalog.entries(it).first().member.primaryLanguage }
        assertEquals("one member per first language", languages.toSet().size, languages.size)
        assertFalse("photos" in expansion.applied)
        assertFalse("photographer" in expansion.applied)
    }

    @Test
    fun rankingOrdersHeadThenLanguageLeadersThenSynonymsThenNarrower() {
        fun member(name: String, language: String, relation: HashtagRelation, weight: Double) =
            CatalogMember(name, listOf(language), relation, weight)
        val group = HashtagGroup(
            "g",
            listOf(
                member("head", "en", HashtagRelation.Head, 9.0),
                member("enhigh", "en", HashtagRelation.Synonym, 8.0),
                member("fr1", "fr", HashtagRelation.Synonym, 3.0),
                member("fr2", "fr", HashtagRelation.Synonym, 2.0),
                member("de1", "de", HashtagRelation.Synonym, 4.0),
                member("narrow", "en", HashtagRelation.Narrower, 9.5),
                member("rel", "en", HashtagRelation.Related, 9.9),
            ),
        )
        val small = HashtagExpander(HashtagCatalog(listOf(group)))
        assertEquals(listOf("head", "de1", "fr1", "enhigh", "narrow"), small.expand("fr2", true, english, 10).applied)
        assertEquals(listOf("head", "de1"), small.expand("fr2", true, english, 2).applied)
    }

    @Test
    fun hashtagInTwoGroupsMergesBothHeads() {
        val catalog = RealHashtagCatalog.catalog
        val shared = catalog.groups.flatMap { it.members }.map { it.name }.distinct().filter { name ->
            val entries = catalog.entries(name)
            entries.count { it.member.relation <= HashtagRelation.Synonym } > 1 &&
                entries.none { name in it.group.ambiguous }
        }
        assertTrue("fixture: a hashtag must be an entry point of two groups", shared.isNotEmpty())
        val name = shared.first()
        val applied = expand(name).applied
        catalog.entries(name).forEach { entry ->
            val head = entry.group.head!!.name
            assertTrue("$name should merge the head of ${entry.group.id}", head == name || head in applied)
        }
    }

    @Test
    fun languagePolicyMapsEveryDisplayLanguage() {
        val covered = mapOf(
            AppLanguage.English to "en",
            AppLanguage.German to "de",
            AppLanguage.Spanish to "es",
            AppLanguage.SpanishSpain to "es",
            AppLanguage.SpanishLatinAmerica to "es",
            AppLanguage.French to "fr",
            AppLanguage.Japanese to "ja",
            AppLanguage.ChineseTaiwan to "zh-TW",
            AppLanguage.CantoneseHongKong to "zh-TW",
        )
        val englishMember = CatalogMember("x", listOf("en"), HashtagRelation.Synonym, 1.0)
        val undetermined = CatalogMember("x", listOf("und"), HashtagRelation.Synonym, 1.0)
        AppLanguage.entries.filter { it != AppLanguage.SystemDefault }.forEach { language ->
            assertEquals(language.name, covered[language], HashtagLanguagePolicy.catalogCode(language))
            val policy = HashtagLanguagePolicy.forLanguage(language)
            assertTrue(policy.allows(undetermined))
            val fallsBackToEnglish = covered[language] == null || covered[language] == "en"
            assertEquals(language.name, fallsBackToEnglish, policy.allows(englishMember))
        }
    }

    @Test
    fun systemDefaultFollowsTheDeviceLocale() {
        fun resolve(tag: String) = HashtagLanguagePolicy.resolveSystem(Locale.forLanguageTag(tag))
        assertEquals(AppLanguage.Indonesian, resolve("in-ID"))
        assertEquals(AppLanguage.Indonesian, resolve("id"))
        assertEquals(AppLanguage.German, resolve("de-AT"))
        assertEquals(AppLanguage.ChineseTaiwan, resolve("zh-TW"))
        assertEquals(AppLanguage.PortugueseBrazil, resolve("pt"))
        assertEquals(AppLanguage.English, resolve("sv-SE"))
        val german = HashtagLanguagePolicy.forLanguage(AppLanguage.SystemDefault, Locale.GERMANY)
        assertTrue(german.allows(CatalogMember("x", listOf("de"), HashtagRelation.Synonym, 1.0)))
        assertFalse(german.allows(CatalogMember("x", listOf("en"), HashtagRelation.Synonym, 1.0)))
    }

    @Test
    fun scriptCheckFollowsTheDisplayLanguage() {
        val russian = HashtagLanguagePolicy.forLanguage(AppLanguage.Russian)
        val japanese = HashtagLanguagePolicy.forLanguage(AppLanguage.Japanese)
        assertTrue(english.allowsUnknown("foto2024"))
        assertFalse(english.allowsUnknown("привет"))
        assertTrue(russian.allowsUnknown("привет"))
        assertTrue(russian.allowsUnknown("foto"))
        assertTrue(japanese.allowsUnknown("写真ー"))
        assertTrue(japanese.allowsUnknown("ねこ"))
        assertFalse(english.allowsUnknown("写真"))
    }
}
