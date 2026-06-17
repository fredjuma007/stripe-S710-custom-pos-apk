package community.rafiki.pay.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object RafikiColors {
    val Red = Color(0xFFD84343)
    val DeepRed = Color(0xFFB93232)
    val Green = Color(0xFF329C58)
    val Orange = Color(0xFFF4A62A)
    val Coral = Color(0xFFE45B55)
    val Black = Color(0xFF111111)
    val Ink = Color(0xFF2B2B2B)
    val White = Color(0xFFFFFFFF)
    val Soft = Color(0xFFF7F4F1)
}

private val RafikiLightScheme = lightColorScheme(
    primary = RafikiColors.Red,
    onPrimary = RafikiColors.White,
    secondary = RafikiColors.Green,
    onSecondary = RafikiColors.White,
    background = RafikiColors.White,
    onBackground = RafikiColors.Black,
    surface = RafikiColors.White,
    onSurface = RafikiColors.Ink,
    error = RafikiColors.DeepRed,
)

@Composable
fun RafikiPayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RafikiLightScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
