package me.foxtails.palustris.domain

/**
 * The client-side matcher for one account's filters. Mastodon v1 filters and Misskey word mutes need it, because
 * those servers do not mark matching posts. Build it once per [ContentFilterSet]; it compiles every pattern at
 * construction, so a feed never compiles per post. [withAccounts] adds the account lists without compiling again.
 * It holds no mutable state.
 *
 * Matching reads the content warning, the body, poll options, media descriptions, and the same fields of the
 * quoted post, as one text. The account's own posts never match, as in Misskey web. A filter past its expiry never
 * matches. An expression that does not compile never matches.
 */
class ClientFilterMatcher private constructor(
    private val owner: AccountId?,
    private val compiled: List<CompiledFilter>,
    val accounts: AccountFilter,
    private val clock: () -> Long,
) {
    constructor(owner: AccountId?, filters: List<ContentFilter>, clock: () -> Long = System::currentTimeMillis) :
        this(owner, filters.map { filter -> CompiledFilter(filter, filter.patterns.mapNotNull(::compile)) }, AccountFilter.None, clock)

    /** The same word filters with [accounts] in place of the current account lists. */
    fun withAccounts(accounts: AccountFilter): ClientFilterMatcher = ClientFilterMatcher(owner, compiled, accounts, clock)

    /** The title and action of every filter and account list that matches [post] on [context]. */
    fun matches(post: Post, context: FilterContext): List<Pair<String, PostFilterAction>> =
        wordMatches(post, context) + accounts.matches(post, context)

    private fun wordMatches(post: Post, context: FilterContext): List<Pair<String, PostFilterAction>> {
        if (compiled.isEmpty() || (owner != null && post.author.id == owner)) return emptyList()
        val now = clock()
        val active = compiled.filter { entry ->
            context in entry.filter.contexts && entry.filter.expiresAtEpochMillis.let { it == null || it > now }
        }
        if (active.isEmpty()) return emptyList()
        val text = searchableText(post)
        if (text.isBlank()) return emptyList()
        return active.filter { entry -> entry.patterns.any { it(text) } }.map { it.filter.title to it.filter.action }
    }

    private class CompiledFilter(val filter: ContentFilter, val patterns: List<(String) -> Boolean>)

    companion object {
        val Empty = ClientFilterMatcher(null, emptyList())

        /** A matcher for [set] when the client must match it, or [Empty] when the server already does. */
        fun forSet(owner: AccountId, set: ContentFilterSet?): ClientFilterMatcher =
            if (set == null || !set.clientMatched || set.filters.isEmpty()) Empty else ClientFilterMatcher(owner, set.filters)

        internal fun searchableText(post: Post): String = buildList {
            addPostFields(post)
            post.quote?.let { addPostFields(it) }
        }.filter(String::isNotBlank).joinToString("\n")

        private fun MutableList<String>.addPostFields(post: Post) {
            post.contentWarning?.let(::add)
            add(post.text)
            post.poll?.options?.forEach { add(it.text) }
            post.attachments.forEach { attachment -> attachment.description?.let(::add) }
        }

        private fun compile(pattern: FilterPattern): ((String) -> Boolean)? = when (pattern) {
            is FilterPattern.Words -> {
                val tests = pattern.words.filter(String::isNotEmpty).map { word -> wordTest(word, pattern.wholeWord, pattern.ignoreCase) }
                if (tests.isEmpty()) null else { text -> tests.all { it(text) } }
            }
            is FilterPattern.Expression -> FilterExpressions.compile(pattern.source, pattern.flags)?.let { regex -> { text -> regex.containsMatchIn(text) } }
        }

        /**
         * A whole-word test follows Mastodon: a boundary applies only at an end of the word that is itself a word
         * character. Word characters are letters, marks, decimal digits, and connector punctuation, as in Mastodon's Ruby.
         */
        private fun wordTest(word: String, wholeWord: Boolean, ignoreCase: Boolean): (String) -> Boolean {
            if (!wholeWord) return { text -> text.contains(word, ignoreCase) }
            val start = if (isWordCharacter(word.first())) "(?<!$WORD_CHARACTER)" else ""
            val end = if (isWordCharacter(word.last())) "(?!$WORD_CHARACTER)" else ""
            val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
            // Java needs UNICODE_CASE to fold letters outside ASCII, as Mastodon does.
            val regex = Regex((if (ignoreCase) "(?u)" else "") + start + Regex.escape(word) + end, options)
            return { text -> regex.containsMatchIn(text) }
        }

        private fun isWordCharacter(char: Char): Boolean = Character.isLetterOrDigit(char) || when (Character.getType(char).toByte()) {
            Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK, Character.CONNECTOR_PUNCTUATION -> true
            else -> false
        }

        private const val WORD_CHARACTER = "[\\p{L}\\p{M}\\p{Nd}\\p{Pc}]"
    }
}
