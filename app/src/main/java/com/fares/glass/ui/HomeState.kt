package com.fares.glass.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

/** tvOS-style 6-column grid on a 960×540 dp screen (1080p at density 2). */
object Metrics {
    const val COLS = 6
    val Margin = 48.dp
    val Gap = 22.dp
    val TileW = 126.dp // (960 - 2 × 48 - 5 × 22) / 6
    val TileH = 76.dp // 5:3, like tvOS icons
    val Pitch = 128.dp
    /** First row position at the top: the Top Shelf fills the space above it. */
    val TopRowY = 322.dp
    /** Position of the selected row once you have moved into the grid. */
    val GridRowY = 150.dp
    const val FOCUS_SCALE = 1.13f
}

enum class Mode { Browse, Top, Jiggle, Menu, Control }

/** Last requested move: the tile tilts that way (or "bumps" when blocked). */
data class Nudge(val dx: Int, val dy: Int, val tick: Int)

class HomeState {
    var focus by mutableIntStateOf(0)
    var mode by mutableStateOf(Mode.Browse)
    var menuIndex by mutableIntStateOf(0)
    var controlIndex by mutableIntStateOf(0)
    var topIndex by mutableIntStateOf(0)
    var nudge by mutableStateOf(Nudge(0, 0, 0))
    /** OK held on a tile: it presses in before the app opens. */
    var pressed by mutableStateOf(false)

    // Long press on OK (not Compose state, so it triggers no redraw).
    var centerDownAt = -1L
    var longFired = false

    val row get() = focus / Metrics.COLS

    /** Home pressed while already home: back to the top, as on tvOS. */
    fun goHome() {
        mode = Mode.Browse
        focus %= Metrics.COLS
    }

    fun bump(dx: Int, dy: Int) {
        nudge = Nudge(dx, dy, nudge.tick + 1)
    }

    companion object {
        /** Cell a move lands on, or null when it hits an edge. */
        fun target(from: Int, dx: Int, dy: Int, count: Int): Int? {
            val c = Metrics.COLS
            val col = from % c
            val row = from / c
            return when {
                dx < 0 -> if (col > 0) from - 1 else null
                dx > 0 -> if (col < c - 1 && from + 1 < count) from + 1 else null
                dy < 0 -> if (row > 0) from - c else null
                dy > 0 -> when {
                    from + c < count -> from + c
                    row < (count - 1) / c -> count - 1
                    else -> null
                }
                else -> null
            }
        }
    }
}
