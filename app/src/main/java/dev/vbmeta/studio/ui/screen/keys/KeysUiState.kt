package dev.vbmeta.studio.ui.screen.keys

import androidx.compose.runtime.Immutable
import dev.vbmeta.studio.engine.KeyEntry

@Immutable
data class KeysUiState(
    val keys: List<KeyEntry> = emptyList(),
    val fingerprints: Map<String, String> = emptyMap(),
    val generating: Boolean = false,
)

@Immutable
data class KeysActions(
    val onGenerate: (String, Int) -> Unit,
    val onImport: () -> Unit,
    val onDelete: (String) -> Unit,
    val onExportPublic: (KeyEntry) -> Unit,
    val onExportAvbKey: (KeyEntry) -> Unit,
    val onExportDigest: (KeyEntry) -> Unit,
)
