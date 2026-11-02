package com.example.multivideodownloader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val md_theme_light_primary = Color(0xFF006C4C)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFF75F8C8)
val md_theme_light_onPrimaryContainer = Color(0xFF002115)
val md_theme_light_secondary = Color(0xFF4C6357)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFCFE9D8)
val md_theme_light_onSecondaryContainer = Color(0xFF0A1F16)
val md_theme_light_tertiary = Color(0xFF3F6375)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFC2E8FD)
val md_theme_light_onTertiaryContainer = Color(0xFF001F2B)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = Color(0xFFFBFDF9)
val md_theme_light_onBackground = Color(0xFF191C1A)
val md_theme_light_surface = Color(0xFFFBFDF9)
val md_theme_light_onSurface = Color(0xFF191C1A)
val md_theme_light_surfaceVariant = Color(0xFFDBE5DE)
val md_theme_light_onSurfaceVariant = Color(0xFF404943)
val md_theme_light_outline = Color(0xFF707973)
val md_theme_light_inverseOnSurface = Color(0xFFEEF1ED)
val md_theme_light_inverseSurface = Color(0xFF2E312F)
val md_theme_light_inversePrimary = Color(0xFF55DBAD)
val md_theme_light_surfaceDim = Color(0xFFDCDFDBC)
val md_theme_light_surfaceBright = Color(0xFFFBFDF9)
val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow = Color(0xFFF6F8F4)
val md_theme_light_surfaceContainer = Color(0xFFF0F3EF)
val md_theme_light_surfaceContainerHigh = Color(0xFFEAECEB)
val md_theme_light_surfaceContainerHighest = Color(0xFFE4E7E5)

val md_theme_dark_primary = Color(0xFF55DBAD)
val md_theme_dark_onPrimary = Color(0xFF003826)
val md_theme_dark_primaryContainer = Color(0xFF005238)
val md_theme_dark_onPrimaryContainer = Color(0xFF75F8C8)
val md_theme_dark_secondary = Color(0xFFB3CCBD)
val md_theme_dark_onSecondary = Color(0xFF1F352B)
val md_theme_dark_secondaryContainer = Color(0xFF354C40)
val md_theme_dark_onSecondaryContainer = Color(0xFFCFE9D8)
val md_theme_dark_tertiary = Color(0xFFA6CCE1)
val md_theme_dark_onTertiary = Color(0xFF0B3444)
val md_theme_dark_tertiaryContainer = Color(0xFF264B5C)
val md_theme_dark_onTertiaryContainer = Color(0xFFC2E8FD)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF191C1A)
val md_theme_dark_onBackground = Color(0xFFE1E3DF)
val md_theme_dark_surface = Color(0xFF191C1A)
val md_theme_dark_onSurface = Color(0xFFE1E3DF)
val md_theme_dark_surfaceVariant = Color(0xFF404943)
val md_theme_dark_onSurfaceVariant = Color(0xFFC0C9C2)
val md_theme_dark_outline = Color(0xFF89938C)
val md_theme_dark_inverseOnSurface = Color(0xFF191C1A)
val md_theme_dark_inverseSurface = Color(0xFFE1E3DF)
val md_theme_dark_inversePrimary = Color(0xFF006C4C)
val md_theme_dark_surfaceDim = Color(0xFF191C1A)
val md_theme_dark_surfaceBright = Color(0xFF404943)
val md_theme_dark_surfaceContainerLowest = Color(0xFF0E1412)
val md_theme_dark_surfaceContainerLow = Color(0xFF1D2522)
val md_theme_dark_surfaceContainer = Color(0xFF212926)
val md_theme_dark_surfaceContainerHigh = Color(0xFF2C3431)
val md_theme_dark_surfaceContainerHighest = Color(0xFF373F3B)


// Aquí movemos tu esquema de color 'brutalista' original, con nombre más descriptivo.
fun brutaloaderColorScheme(): ColorScheme {
    return darkColorScheme(
        primary = Color(0xFFFFFFFF), // Blanco
        secondary = Color(0xFFCCCCCC), // Gris claro para elementos secundarios
        tertiary = Color(0xFF333333), // Gris oscuro para terciario
        background = Color(0xFF000000), // Negro puro
        surface = Color(0xFF1A1A1A), // Superficies un poco más claras que el fondo
        onPrimary = Color(0xFF000000), // Texto negro sobre primary blanco
        onSecondary = Color(0xFF000000), // Texto negro sobre secondary gris claro
        onTertiary = Color(0xFFFFFFFF), // Texto blanco sobre tertiary gris oscuro
        onBackground = Color(0xFFFFFFFF), // Texto blanco sobre background negro
        onSurface = Color(0xFFFFFFFF), // Texto blanco sobre surface oscuro
        error = Color(0xFFFF3333), // Rojo brillante para errores
        onError = Color(0xFF000000), // Texto negro sobre error rojo
        primaryContainer = Color(0xFF333333), // Contenedor primario gris oscuro
        onPrimaryContainer = Color(0xFFFFFFFF), // Texto blanco sobre contenedor primario
        secondaryContainer = Color(0xFF222222), // Contenedor secundario más oscuro
        onSecondaryContainer = Color(0xFFFFFFFF), // Texto blanco sobre contenedor secundario
        tertiaryContainer = Color(0xFF444444), // Contenedor terciario
        onTertiaryContainer = Color(0xFFFFFFFF), // Texto blanco sobre contenedor terciario
        outline = Color(0xFF666666), // Contorno gris medio
        inverseOnSurface = Color(0xFF000000), // Invertido onSurface
        inverseSurface = Color(0xFFFFFFFF), // Invertido surface
        inversePrimary = Color(0xFF000000), // Invertido primary
        surfaceTint = Color(0xFFFFFFFF), // Tono de superficie blanco
        outlineVariant = Color(0xFF444444), // Variante de contorno
        scrim = Color(0xFF000000) // Scrim negro
    )
}

val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inverseSurface = md_theme_light_inverseSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceDim = md_theme_light_surfaceDim,
    surfaceBright = md_theme_light_surfaceBright,
    surfaceContainerLowest = md_theme_light_surfaceContainerLowest,
    surfaceContainerLow = md_theme_light_surfaceContainerLow,
    surfaceContainer = md_theme_light_surfaceContainer,
    surfaceContainerHigh = md_theme_light_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_light_surfaceContainerHighest,
)

val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceDim = md_theme_dark_surfaceDim,
    surfaceBright = md_theme_dark_surfaceBright,
    surfaceContainerLowest = md_theme_dark_surfaceContainerLowest,
    surfaceContainerLow = md_theme_dark_surfaceContainerLow,
    surfaceContainer = md_theme_dark_surfaceContainer,
    surfaceContainerHigh = md_theme_dark_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_dark_surfaceContainerHighest,
)
