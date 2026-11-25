package app.obdgolf

import androidx.compose.runtime.Composable
import app.obdgolf.core.CarData
import app.obdgolf.ui.Garage3DScreen

/**
 * The owner's own build: Golf Mk6 branding, the 3D garage and the VW-only rows.
 * Never uploaded to a store (VW trademarks, and the 3D model's CC BY licence is for personal use here).
 * The store build has its own Edition.kt in src/store with the same members.
 */
object Edition {
    const val HAS_GARAGE = true
    const val SHOW_VW_EXTRAS = true
    const val BADGE = "G6"
    const val TITLE = "GOLF MK6"
    const val SUBTITLE = "1.4 TSI · DSG"

    @Composable
    fun Garage(d: CarData, link: LinkState, demo: Boolean) = Garage3DScreen(d, link, demo)
}
