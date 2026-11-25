package com.personal.myfiles.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.personal.myfiles.R
import com.personal.myfiles.core.AppSettings
import com.personal.myfiles.core.FileCategory

val Readex = FontFamily(
    Font(R.font.readex_regular, FontWeight.Normal),
    Font(R.font.readex_medium, FontWeight.Medium),
    Font(R.font.readex_semibold, FontWeight.SemiBold),
    Font(R.font.readex_bold, FontWeight.Bold),
)

private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Readex),
    displayMedium = base.displayMedium.copy(fontFamily = Readex),
    displaySmall = base.displaySmall.copy(fontFamily = Readex),
    headlineLarge = base.headlineLarge.copy(fontFamily = Readex, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Readex, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Readex, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Readex, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Readex, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Readex, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontFamily = Readex),
    bodyMedium = base.bodyMedium.copy(fontFamily = Readex),
    bodySmall = base.bodySmall.copy(fontFamily = Readex),
    labelLarge = base.labelLarge.copy(fontFamily = Readex, fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontFamily = Readex, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = Readex),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

data class Accent(
    val name: String,
    val swatch: Color,
    val lp: Color, val lop: Color, val lc: Color, val loc: Color,
    val dp: Color, val dop: Color, val dc: Color, val doc: Color,
)

val Accents = listOf(
    Accent("زمردي", Color(0xFF0B6E6E),
        Color(0xFF0B6E6E), Color.White, Color(0xFFCFEAE7), Color(0xFF063F3F),
        Color(0xFF7FD6CF), Color(0xFF003735), Color(0xFF00504D), Color(0xFF9EF2EA)),
    Accent("أزرق", Color(0xFF2E5FD0),
        Color(0xFF2E5FD0), Color.White, Color(0xFFDCE6FF), Color(0xFF0F2F7A),
        Color(0xFFB2C5FF), Color(0xFF0B2B6B), Color(0xFF26438F), Color(0xFFDCE6FF)),
    Accent("بنفسجي", Color(0xFF6A4BC4),
        Color(0xFF6A4BC4), Color.White, Color(0xFFE8E1FB), Color(0xFF2A1468),
        Color(0xFFCDBDFF), Color(0xFF341C7E), Color(0xFF4D33A0), Color(0xFFE8E1FB)),
    Accent("برتقالي", Color(0xFFA9500B),
        Color(0xFFA9500B), Color.White, Color(0xFFFFDCC6), Color(0xFF4A2000),
        Color(0xFFFFB68A), Color(0xFF542200), Color(0xFF7A3700), Color(0xFFFFDCC6)),
)

private val LightBase = lightColorScheme(
    secondary = Color(0xFF4A6361), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E5), onSecondaryContainer = Color(0xFF051F1E),
    tertiary = Color(0xFF8E4D00), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC2), onTertiaryContainer = Color(0xFF2E1500),
    background = Color(0xFFF5F8F7), onBackground = Color(0xFF151A1C),
    surface = Color(0xFFF5F8F7), onSurface = Color(0xFF151A1C),
    surfaceVariant = Color(0xFFDDE5E4), onSurfaceVariant = Color(0xFF4E585C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF3F2),
    surfaceContainer = Color(0xFFE9EFEE),
    surfaceContainerHigh = Color(0xFFE3EBEA),
    surfaceContainerHighest = Color(0xFFDDE5E4),
    outline = Color(0xFF6F7D81), outlineVariant = Color(0xFFC5CFCF),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFDE2DC), onErrorContainer = Color(0xFF5C0C04),
)

private val DarkBase = darkColorScheme(
    secondary = Color(0xFFB0CCC9), onSecondary = Color(0xFF1B3533),
    secondaryContainer = Color(0xFF324B49), onSecondaryContainer = Color(0xFFCCE8E5),
    tertiary = Color(0xFFFFB77C), onTertiary = Color(0xFF4C2700),
    tertiaryContainer = Color(0xFF6C3A00), onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF0E1415), onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF0E1415), onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF3F4949), onSurfaceVariant = Color(0xFFBEC8C9),
    surfaceContainerLowest = Color(0xFF090F10),
    surfaceContainerLow = Color(0xFF161D1E),
    surfaceContainer = Color(0xFF1A2122),
    surfaceContainerHigh = Color(0xFF252B2C),
    surfaceContainerHighest = Color(0xFF2F3637),
    outline = Color(0xFF889394), outlineVariant = Color(0xFF3F4949),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

val LocalIsDark = staticCompositionLocalOf { false }

/** Card colour inside a screen: white on light, raised surface on dark. */
@Composable
fun cardColor(): Color = MaterialTheme.colorScheme.surfaceContainerLowest.let {
    if (LocalIsDark.current) MaterialTheme.colorScheme.surfaceContainerLow else it
}

@Composable
fun categoryColors(cat: FileCategory): Pair<Color, Color> {
    val dark = LocalIsDark.current
    if (cat == FileCategory.FOLDER) return MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
    return if (!dark) when (cat) {
        FileCategory.IMAGE -> Color(0xFFDCE6FF) to Color(0xFF1D46A8)
        FileCategory.VIDEO -> Color(0xFFFCE3D3) to Color(0xFF953A08)
        FileCategory.AUDIO -> Color(0xFFE8E1FB) to Color(0xFF553AA8)
        FileCategory.DOCUMENT -> Color(0xFFD5F0E2) to Color(0xFF1A6641)
        FileCategory.PDF -> Color(0xFFFDE2DC) to Color(0xFFA3261A)
        FileCategory.TEXT -> Color(0xFFE3EBEA) to Color(0xFF3E4A4D)
        FileCategory.ARCHIVE -> Color(0xFFF6E8BC) to Color(0xFF735000)
        FileCategory.APK -> Color(0xFFE4F0CB) to Color(0xFF3D5E10)
        else -> Color(0xFFE3EBEA) to Color(0xFF4E585C)
    } else when (cat) {
        FileCategory.IMAGE -> Color(0xFF26355E) to Color(0xFFB8CBFF)
        FileCategory.VIDEO -> Color(0xFF5A2A10) to Color(0xFFFFB991)
        FileCategory.AUDIO -> Color(0xFF3A2E66) to Color(0xFFD4C6FF)
        FileCategory.DOCUMENT -> Color(0xFF15452F) to Color(0xFFA6E0BF)
        FileCategory.PDF -> Color(0xFF5C1E16) to Color(0xFFFFB4A6)
        FileCategory.TEXT -> Color(0xFF2A3436) to Color(0xFFC4CED0)
        FileCategory.ARCHIVE -> Color(0xFF4D3A00) to Color(0xFFF2D27A)
        FileCategory.APK -> Color(0xFF2E4210) to Color(0xFFC3DE96)
        else -> Color(0xFF2A3436) to Color(0xFFBEC8C9)
    }
}

/** Colours for the storage breakdown bar. */
@Composable
fun segmentColor(cat: FileCategory): Color = categoryColors(cat).let { if (LocalIsDark.current) it.second else it.second }

@Composable
fun AppTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val acc = Accents[settings.accent.coerceIn(0, Accents.lastIndex)]
    var scheme: ColorScheme = if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) {
        DarkBase.copy(primary = acc.dp, onPrimary = acc.dop, primaryContainer = acc.dc, onPrimaryContainer = acc.doc,
            inversePrimary = acc.lp, surfaceTint = acc.dp)
    } else {
        LightBase.copy(primary = acc.lp, onPrimary = acc.lop, primaryContainer = acc.lc, onPrimaryContainer = acc.loc,
            inversePrimary = acc.dp, surfaceTint = acc.lp)
    }
    if (dark && settings.amoled) {
        scheme = scheme.copy(
            background = Color.Black, surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0C1011),
            surfaceContainer = Color(0xFF121718),
            surfaceContainerHigh = Color(0xFF1A1F20),
            surfaceContainerHighest = Color(0xFF232829),
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val ctrl = WindowCompat.getInsetsController(window, view)
            ctrl.isAppearanceLightStatusBars = !dark
            ctrl.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(
        LocalIsDark provides dark,
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
