package me.foxtails.palustris.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostScript
import me.foxtails.palustris.domain.PostScriptClassifier
import me.foxtails.palustris.domain.postFont
import me.foxtails.palustris.domain.systemLocaleTag

/**
 * The script of the post that owns a subtree. A null [script] means that the post has no class,
 * which differs from a subtree outside every post.
 */
internal class PostScriptScope(val script: PostScript?)

/** Null outside a post. A post row and the single post screen provide the scope of their post. */
internal val LocalPostScriptScope = staticCompositionLocalOf<PostScriptScope?> { null }

/** The font in effect for the application. `PalustrisTheme` provides it. */
internal val LocalAppFont = staticCompositionLocalOf { AppFont.Device }

/** One class for the body and the content warning together, so both render in one font. */
@Composable
internal fun rememberPostScriptScope(post: Post): PostScriptScope =
    remember(post.text, post.contentWarning) {
        PostScriptScope(PostScriptClassifier.classify(post.text + "\n" + post.contentWarning.orEmpty()))
    }

/** Gives [content] the script scope of [post]. A quoted post needs its own scope inside a post. */
@Composable
internal fun PostScriptProvider(post: Post, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPostScriptScope provides rememberPostScriptScope(post), content = content)
}

/**
 * The style for text of a post. Inside a post scope the script of the post decides. Outside one,
 * for example in a notification row, the text decides for itself.
 */
@Composable
internal fun TextStyle.forPostText(text: String): TextStyle {
    val scope = LocalPostScriptScope.current
    val script = if (scope != null) scope.script else remember(text) { PostScriptClassifier.classify(text) }
    return withPostScript(script, LocalAppFont.current, LocalLatinOverrideFonts.current)
}

/**
 * The style for a label that belongs to a post, such as a hashtag bubble. The label text is too
 * short to prove a script, so only the scope of the post decides. Outside a post it is unchanged.
 */
@Composable
internal fun TextStyle.forPostLabel(): TextStyle =
    withPostScript(LocalPostScriptScope.current?.script, LocalAppFont.current, LocalLatinOverrideFonts.current)

/**
 * Applies the one font of [script], with the locale that selects its regional glyph forms. While
 * the Latin override applies, [latinFonts] keeps Inter for the Latin characters, as in the rest
 * of the application.
 */
internal fun TextStyle.withPostScript(script: PostScript?, appFont: AppFont, latinFonts: LatinOverrideFonts?): TextStyle {
    if (script == null) return this
    val font = postFont(script, appFont)
    return copy(
        fontFamily = latinFonts?.family(font) ?: appFontFamily(font),
        localeList = LocaleList(script.systemLocaleTag),
    )
}
