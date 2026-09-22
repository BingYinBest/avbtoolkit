package dev.vbmeta.studio.ui.screen.partition

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vbmeta.studio.ui.LocalUiMode
import dev.vbmeta.studio.ui.UiMode
import dev.vbmeta.studio.ui.navigation3.Navigator
import dev.vbmeta.studio.ui.viewmodel.PartitionViewModel

@Composable
fun PartitionPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
) {
    val viewModel = viewModel<PartitionViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var pending by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            when (pending) {
                "misc" -> viewModel.pickMiscImage(uri)
                "kernel" -> viewModel.pickKernelImage(uri)
            }
        }
        pending = null
    }

    val actions = PartitionActions(
        onPickMiscImage = { pending = "misc"; picker.launch("*/*") },
        onSlotDataChange = viewModel::slotDataChange,
        onPickKernelImage = { pending = "kernel"; picker.launch("*/*") },
        onHashtreeDisabledChange = viewModel::hashtreeDisabledChange,
        onSetAbMetadata = viewModel::setAbMetadata,
        onCalcKernelCmdline = viewModel::calcKernelCmdline,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> PartitionPagerMiuix(uiState, actions, bottomInnerPadding)
        UiMode.Material -> PartitionPagerMaterial(uiState, actions, bottomInnerPadding)
    }
}