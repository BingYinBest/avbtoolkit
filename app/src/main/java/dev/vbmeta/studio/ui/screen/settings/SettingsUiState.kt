package dev.vbmeta.studio.ui.screen.settings

import androidx.compose.runtime.Immutable
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import dev.vbmeta.studio.ui.UiMode
import dev.vbmeta.studio.ui.util.LatestVersionInfo

@Immutable
data class SettingsUiState(
    val uiMode: String = UiMode.DEFAULT_VALUE,
    val checkUpdate: Boolean = true,
    val themeMode: Int = 0,
    val miuixMonet: Boolean = false,
    val keyColor: Int = 0,
    val colorStyle: String = PaletteStyle.TonalSpot.name,
    val colorSpec: String = ColorSpec.SpecVersion.Default.name,
    val enablePredictiveBack: Boolean = false,
    val enableBlur: Boolean = true,
    val enableFloatingBottomBar: Boolean = true,
    val enableFloatingBottomBarBlur: Boolean = true,
    val pageScale: Float = 1.0f,
    val toolchainReady: Boolean = false,
    val toolchainVersions: Map<String, String> = emptyMap(),
    val latestVersion: LatestVersionInfo = LatestVersionInfo(),
    val checkingUpdate: Boolean = false,
    val currentVersionCode: Long = 0,
)

@Immutable
data class SettingsScreenActions(
    val onSetCheckUpdate: (Boolean) -> Unit,
    val onSetUiModeIndex: (Int) -> Unit,
    val onOpenAbout: () -> Unit,
    val onToolchainInit: () -> Unit,
    val onToolchainSelfCheck: () -> Unit,
    val onToolchainClear: () -> Unit,
    val onCheckUpdate: () -> Unit,
)
