package app.obdgolf.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.obdgolf.core.Units

/** The user's display units (km/h or mph, °C or °F, kPa / bar / psi), provided at the app root. */
val LocalUnits = staticCompositionLocalOf { Units() }
