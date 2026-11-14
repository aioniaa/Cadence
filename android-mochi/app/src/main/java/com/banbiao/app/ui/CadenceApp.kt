package com.banbiao.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiTabItem
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.content.MochiThemeRevealHost
import ui.feice.mochi.effect.content.revealIfShadeChanges
import ui.feice.mochi.effect.content.rememberMochiThemeRevealState
import ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.ios.iosColorScheme
import ui.feice.mochi.ios.iosMaterials
import ui.feice.mochi.navigation.MochiNavHost
import ui.feice.mochi.navigation.MochiTabScaffold
import ui.feice.mochi.navigation.rememberMochiNavigator
import ui.feice.mochi.navigation.rememberMochiTabScaffoldState
import ui.feice.mochi.picker.MochiTime
import ui.feice.mochi.theme.MochiColorScheme
import ui.feice.mochi.theme.MochiTextStyles
import ui.feice.mochi.theme.defaultTextStyles
import ui.feice.mochi.theme.MochiTheme
import com.banbiao.app.data.AppInfo
import com.banbiao.app.data.AppState
import com.banbiao.app.data.ShiftDef
import com.banbiao.app.data.StateStore
import com.banbiao.app.core.IslandNotifier
import com.banbiao.app.data.ThemeMode

internal val LightAccent = Color(0xFFB56576)
internal val DarkAccent = Color(0xFFE7A6B4)
internal val APP_VERSION = AppInfo.version

@Composable
fun CadenceRoot(initialState: AppState) {
    var state by remember { mutableStateOf(initialState) }
    val context = LocalContext.current
    val store = remember { StateStore(context) }
    val systemDark = isSystemInDarkTheme()
    val dark = when (state.themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    fun update(next: AppState) {
        state = next
        store.save(next)
    }
    var pendingIsland by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingIsland) {
            update(state.copy(islandEnabled = true))
        } else if (!granted) {
            Toast.makeText(context, "需要通知权限才能上岛", Toast.LENGTH_SHORT).show()
        }
        pendingIsland = false
    }
    fun toggleIsland(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingIsland = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            update(state.copy(islandEnabled = enabled))
        }
    }
    val reveal = rememberMochiThemeRevealState()
    fun changeTheme(mode: ThemeMode) {
        val nextDark = when (mode) {
            ThemeMode.System -> systemDark
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
        reveal.revealIfShadeChanges(dark, nextDark) {
            update(state.copy(themeMode = mode))
        }
    }

    val colors = remember(dark) { cadenceColors(dark) }
    val density = LocalDensity.current.density
    val materials = remember(dark, density) {
        iosMaterials(
            isDark = dark,
            glass = true,
            blur = true,
            themeAccent = true,
            density = density,
        )
    }

    LaunchedEffect(
        state.islandEnabled,
        state.assignments,
        state.leaves,
        state.rule,
        state.autoEnabled,
        state.shiftTypes,
    ) {
        IslandNotifier.sync(context, state)
    }

    val monoTextStyles = defaultTextStyles().tabularNumbers()
    MochiTheme(colors = colors, textStyles = monoTextStyles, materials = materials) {
        MochiThemeRevealHost(reveal, Modifier.fillMaxSize()) {
            ProvideMochiOverscroll {
                MochiNavHost(rememberMochiNavigator("root"), Modifier.fillMaxSize()) {
                    val tabState = rememberMochiTabScaffoldState()
                    MochiTabScaffold(
                        items = cadenceTabs(),
                        state = tabState,
                        onSelect = {},
                        swipeBetweenTabs = false,
                        collapseOnScroll = false,
                    ) { index ->
                        when (index) {
                            0 -> HomePage(
                                state = state,
                                update = { next -> update(next) },
                                onOpenShifts = { tabState.select(1) },
                            )
                            1 -> ShiftsPage(
                                state = state,
                                update = { next -> update(next) },
                            )
                            else -> SettingsPage(
                                state = state,
                                changeTheme = ::changeTheme,
                                dark = dark,
                                onToggleIsland = ::toggleIsland,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun cadenceColors(dark: Boolean): MochiColorScheme {
    val base = iosColorScheme(dark)
    val accent = if (dark) DarkAccent else LightAccent
    val onAccent = if (dark) Color(0xFF2A1218) else Color.White
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = base.surfaceContainerHigh,
        onPrimaryContainer = base.onSurface,
        inversePrimary = accent,
        secondary = base.onSurfaceVariant,
        onSecondary = base.surface,
        secondaryContainer = base.surfaceContainerHighest,
        onSecondaryContainer = base.onSurface,
        tertiary = base.onSurfaceVariant,
        onTertiary = base.surface,
        tertiaryContainer = base.surfaceContainer,
        onTertiaryContainer = base.onSurface,
        error = base.onSurface,
        onError = base.surface,
        errorContainer = base.surfaceContainerHigh,
        onErrorContainer = base.onSurface,
        surfaceTint = accent,
    )
}

private fun cadenceTabs(): ImmutableList<MochiTabItem> = persistentListOf(
    MochiTabItem(
        label = "主页",
        selectedIcon = { tint -> MochiIonicon("calendar", tint = tint, size = 22.dp) },
        icon = { tint -> MochiIonicon("calendar-outline", tint = tint, size = 22.dp) },
    ),
    MochiTabItem(
        label = "班次",
        selectedIcon = { tint -> MochiIonicon("repeat", tint = tint, size = 22.dp) },
        icon = { tint -> MochiIonicon("repeat-outline", tint = tint, size = 22.dp) },
    ),
    MochiTabItem(
        label = "设置",
        selectedIcon = { tint -> MochiIonicon("settings", tint = tint, size = 22.dp) },
        icon = { tint -> MochiIonicon("settings-outline", tint = tint, size = 22.dp) },
    ),
)

@Composable
internal fun shiftFill(def: ShiftDef): Color {
    val scheme = MochiTheme.colorScheme
    if (def.rest) return scheme.surfaceContainerLow
    val alpha = when (def.tone) {
        0 -> 1f
        1 -> 0.62f
        else -> 0.30f
    }
    return scheme.onSurface.copy(alpha = alpha)
}

@Composable
internal fun shiftContent(def: ShiftDef): Color {
    val scheme = MochiTheme.colorScheme
    if (def.rest) return scheme.onSurfaceVariant
    return if (def.tone <= 1) scheme.background else scheme.onSurface
}

@Composable
internal fun ShiftBadge(def: ShiftDef, size: Dp = 28.dp) {
    Box(
        Modifier
            .size(size)
            .clip(MochiTheme.shapes.small)
            .background(shiftFill(def)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            def.short,
            style = MochiTheme.textStyles.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = shiftContent(def),
        )
    }
}

internal fun formatSeconds(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(total / 3600, total % 3600 / 60, total % 60)
}

internal fun String.toMochiTime(): MochiTime {
    val parts = split(":")
    return MochiTime(
        parts.getOrNull(0)?.toIntOrNull() ?: 8,
        parts.getOrNull(1)?.toIntOrNull() ?: 0,
        0,
    )
}

internal fun timeText(value: String): String = value.ifBlank { "--:--" }


private fun MochiTextStyles.tabularNumbers(): MochiTextStyles = copy(
    displayLarge = displayLarge.copy(fontFeatureSettings = "tnum"),
    displayMedium = displayMedium.copy(fontFeatureSettings = "tnum"),
    displaySmall = displaySmall.copy(fontFeatureSettings = "tnum"),
    headlineLarge = headlineLarge.copy(fontFeatureSettings = "tnum"),
    headlineMedium = headlineMedium.copy(fontFeatureSettings = "tnum"),
    headlineSmall = headlineSmall.copy(fontFeatureSettings = "tnum"),
    titleLarge = titleLarge.copy(fontFeatureSettings = "tnum"),
    titleMedium = titleMedium.copy(fontFeatureSettings = "tnum"),
    titleSmall = titleSmall.copy(fontFeatureSettings = "tnum"),
    bodyLarge = bodyLarge.copy(fontFeatureSettings = "tnum"),
    bodyMedium = bodyMedium.copy(fontFeatureSettings = "tnum"),
    bodySmall = bodySmall.copy(fontFeatureSettings = "tnum"),
    labelLarge = labelLarge.copy(fontFeatureSettings = "tnum"),
    labelMedium = labelMedium.copy(fontFeatureSettings = "tnum"),
    labelSmall = labelSmall.copy(fontFeatureSettings = "tnum"),
    largeTitle = largeTitle.copy(fontFeatureSettings = "tnum"),
    navTitle = navTitle.copy(fontFeatureSettings = "tnum"),
    body = body.copy(fontFeatureSettings = "tnum"),
    subhead = subhead.copy(fontFeatureSettings = "tnum"),
    footnote = footnote.copy(fontFeatureSettings = "tnum"),
)
