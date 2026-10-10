package me.foxtails.palustris.data.hashtags

import java.io.File
import me.foxtails.palustris.domain.hashtags.HashtagCatalog

/** Loads the bundled asset from the source tree so tests exercise the real data. */
internal object RealHashtagCatalog {
    val file: File = sequenceOf(
        File("app/src/main/assets/hashtag-catalog.json"),
        File("src/main/assets/hashtag-catalog.json"),
    ).first { it.isFile }

    val catalog: HashtagCatalog by lazy { HashtagCatalogParser.parse(file.readText(Charsets.UTF_8)) }
}
