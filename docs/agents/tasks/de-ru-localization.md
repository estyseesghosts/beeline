# Objective

Add the first glossary-based German and Russian strings from `docs/i18n/DE_RU.csv`
without inventing new vocabulary. Russian gets a new catalog. German gains only
missing keys. No existing string changes.

# Invariants

- Every added string keeps the default kind and placeholders.
- Every added word comes from the glossary term or minimal grammar glue.
- No existing German string changes. No sentence translation without a term.
- `LocalizationResourceTest` stays green. Unrelated worktree changes stay intact.

# Decisions

- Russian catalog holds the 17 existing `docs/i18n/values-ru` strings verbatim
  plus short label, placeholder, and count keys built from `ru_suggested` terms.
  Final: 118 keys.
- German additions use `de_suggested` terms on the same short keys. Final: 396
  keys, no existing string changed.
- Allowed glue: inflection of the provided term, articles, prepositions,
  conjunctions, punctuation, negation prefixes, standard loanword spellings
  (эмодзи, аватар, бот, текст), and placeholder order changes for German
  verb-last order. New content words are out of scope.
- User decisions, 2026-10-13: likes stay a separate feature, so like keys use
  the like terms (Gefällt mir, Нравится). German repost is Boost, so the two
  short repost labels are Boosts. Russian keeps репост.
- Resolved by owner: `Local only` is Nur lokal and Только локальная. The
  social channel is Soziale Aktivität and Социальная активность. The follow
  count is %1$s folgt in German. The message action composes the DM noun with
  the handle. PM is PN in German and ЛС in Russian. `Post now` is Jetzt
  veröffentlichen and Опубликовать сейчас. Unblock composes the block term.
  `Quoted post` in Russian is Цитируемый пост. Post text is Beitragstext and
  Текст поста. Emoji pin states compose the pin term with the Emoji loanword.
- A mechanical scan added every remaining key whose English text is exactly one
  glossary word (action buttons, tab labels, settings rows, section titles).
  That filled post, share, reply, repost, draft, poll, quote, mention, hashtag,
  home, and local labels in Russian, plus the last exact German gap.
- Still skipped: sentence keys without a composable term, German repost
  sentences, like sentences, publish sentences, unmute handles, `Remove poll`
  and other keys needing verbs the glossary does not give, and the keys outside
  the glossary file.
- `nav_home` stays Главная from the provided 17 strings. `timeline_home` is
  Домашняя лента from the glossary. A human resolves the difference later.

# Completed

- S1 implemented, uncommitted: `app/src/main/res/values-ru/strings.xml`, 118
  keys. Static check green: every key exists in the default catalog with
  matching kind and placeholders, no duplicates.
- S2 implemented, uncommitted: 71 appended keys in
  `app/src/main/res/values-de/strings.xml` (325 to 396). Same static check
  green. No existing German string changed.

# Current slice

Both slices wait on a fresh `:app:testDebugUnitTest` run over the final files.
The earlier run (2609 tests) failed only on Indonesian
`a11y_mention_description`: an unrelated draft `values-in-rID` catalog landed
on disk mid-run after resources merged, so the test parsed the new file but
resolved English at runtime. That file is not part of this task and stays
untouched. A second background run died without results (shell lost), so no
suite has validated the current files yet.
A third run never reached tests: the Kotlin daemon incremental cache was
corrupt, almost certainly fallout from the killed run. Remedy applied:
`--stop` plus removal of the build-output-only `app/build/kotlin` cache. No
source touched. A fourth run was then killed mid-compile by an external stop
command, so another Gradle user is active in this shared worktree. A fifth run
is now in background after confirming no Java or Gradle process remains.

# Files involved

- `app/src/main/res/values-ru/strings.xml` (new, S1).
- `app/src/main/res/values-de/strings.xml` (append, S2).
- `docs/agents/tasks/de-ru-localization.md` (this file).
- `docs/agents/handoff.md` (update at slice boundary).

# Verification

- Static XML parity script green for both final catalogs.
- Fresh full unit suite running in background. Remaining gate after green:
  `tools/tests`, architecture audit, lint, ktlint, assemble tasks.
- Review: `problem_solver` in review mode once tests pass.

# Next

Finish the gate, run review, then commit S1 and S2 separately.

# Blockers

- Human translation needed later for the skipped sentence keys and the keys
  outside the glossary file.
- No native speaker reviewed the text.
- Concurrent Indonesian catalog work shares this worktree. Reruns can race it.

# Last safe commit

`0417f5f7` Add Arabic feed, publish, drafts and placeholder strings.
