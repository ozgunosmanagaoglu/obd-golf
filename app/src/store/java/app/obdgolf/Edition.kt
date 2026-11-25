package app.obdgolf

import androidx.compose.runtime.Composable
import app.obdgolf.core.CarData

/**
 * The public build for Google Play / App Store ("Revora").
 * No car-maker names or logos, no garage. Same members as src/personal/.../Edition.kt.
 */
object Edition {
    const val HAS_GARAGE = false
    const val SHOW_VW_EXTRAS = false
    const val BADGE = "R"
    const val TITLE = "REVORA"
    const val SUBTITLE = "OBD-II"

    @Composable
    @Suppress("UNUSED_PARAMETER")
    fun Garage(d: CarData, link: LinkState, demo: Boolean) = Unit
}
