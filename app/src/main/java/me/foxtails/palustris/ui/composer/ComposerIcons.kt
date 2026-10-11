package me.foxtails.palustris.ui.composer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Icons only the composer uses. They stay here so the shared icon set does not change. */
internal object ComposerIcons {
    /** A plus sign for the add-post button. */
    val Add: ImageVector = ImageVector.Builder("ComposerAdd", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 13f)
            horizontalLineTo(13f)
            verticalLineTo(19f)
            horizontalLineTo(11f)
            verticalLineTo(13f)
            horizontalLineTo(5f)
            verticalLineTo(11f)
            horizontalLineTo(11f)
            verticalLineTo(5f)
            horizontalLineTo(13f)
            verticalLineTo(11f)
            horizontalLineTo(19f)
            close()
        }
    }.build()

    /** Bars of a poll. */
    val Poll: ImageVector = traced("ComposerPoll", "M5,9.2h3V19H5z M10.6,5h2.8v14h-2.8z M16.2,13H19v6h-2.8z")

    /** A smiling face for the emoji picker. */
    val Emoji: ImageVector = traced(
        "ComposerEmoji",
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z M12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z " +
            "M15.5,11c0.83,0 1.5,-0.67 1.5,-1.5S16.33,8 15.5,8 14,8.67 14,9.5s0.67,1.5 1.5,1.5z M8.5,11c0.83,0 1.5,-0.67 1.5,-1.5S9.33,8 8.5,8 7,8.67 7,9.5 7.67,11 8.5,11z " +
            "M12,17.5c2.33,0 4.31,-1.46 5.11,-3.5H6.89c0.8,2.04 2.78,3.5 5.11,3.5z",
    )

    /** A calendar page for the schedule pill. */
    val Calendar: ImageVector = traced(
        "ComposerCalendar",
        "M19,4h-1V2h-2v2H8V2H6v2H5C3.9,4 3,4.9 3,6v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V6C21,4.9 20.1,4 19,4z M19,20H5V10h14V20z M19,8H5V6h14V8z",
    )

    private fun traced(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = SolidColor(Color.Black),
        ).build()
}
