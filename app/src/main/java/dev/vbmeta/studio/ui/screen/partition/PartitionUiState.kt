package dev.vbmeta.studio.ui.screen.partition

import androidx.compose.runtime.Immutable

@Immutable
data class PartitionUiState(
    val miscImagePath: String? = null,
    val miscImageName: String? = null,
    val slotData: String = "boot,slot=0",
    val kernelImagePath: String? = null,
    val kernelImageName: String? = null,
    val hashtreeDisabled: Boolean = false,
    val running: Boolean = false,
    val result: String = "",
)

@Immutable
data class PartitionActions(
    val onPickMiscImage: () -> Unit,
    val onSlotDataChange: (String) -> Unit,
    val onPickKernelImage: () -> Unit,
    val onHashtreeDisabledChange: (Boolean) -> Unit,
    val onSetAbMetadata: () -> Unit,
    val onCalcKernelCmdline: () -> Unit,
)