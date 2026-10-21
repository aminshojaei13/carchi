package app.carchi

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Bg = Color(0xFFF4F5F7)
internal val Ink = Color(0xFF0F172A)
internal val Muted = Color(0xFF6B7280)
internal val Line = Color(0xFFE5E7EB)
internal val Brand = Color(0xFF2563EB)
internal val BrandSoft = Color(0xFFEEF2FF)
internal val OkColor = Color(0xFF059669)
internal val NearColor = Color(0xFFD97706)
internal val OverColor = Color(0xFFDC2626)

@Composable
fun CarchiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Brand,
            onPrimary = Color.White,
            background = Bg,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Muted,
            outline = Line,
            error = OverColor,
        ),
        content = content,
    )
}
