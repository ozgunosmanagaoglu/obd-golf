package app.obdgolf.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import app.obdgolf.core.Lang
import app.obdgolf.core.Msg
import app.obdgolf.core.Str
import app.obdgolf.core.Strings

/** The texts in the user's chosen language, provided at the app root. */
val LocalStrings = staticCompositionLocalOf { Strings(Lang.EN) }

/** Text for [key] in the current language; {0}, {1}… are filled from [args]. */
@Composable
@ReadOnlyComposable
fun s(key: Str, vararg args: Any): String = LocalStrings.current.format(key, *args)

@Composable
@ReadOnlyComposable
fun s(msg: Msg): String = LocalStrings.current.of(msg)
