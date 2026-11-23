package br.com.neo.acoes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    secondary = Color(0xFF4A635A),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD4A8),
    secondary = Color(0xFFB1CCC1),
)

@Composable
fun AcoesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Cores de alta/baixa com contraste adequado no tema claro e escuro. */
object Market {
    val up: Color
        @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFF4ADE80) else Color(0xFF15803D)
    val down: Color
        @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFFF87171) else Color(0xFFB91C1C)

    @Composable
    @ReadOnlyComposable
    fun colorFor(change: Double?): Color = when {
        change == null || change == 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant
        change > 0 -> up
        else -> down
    }
}
