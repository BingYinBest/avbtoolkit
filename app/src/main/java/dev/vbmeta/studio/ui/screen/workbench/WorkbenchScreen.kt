package dev.vbmeta.studio.ui.screen.workbench

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vbmeta.studio.model.ImageJob
import dev.vbmeta.studio.ui.LocalUiMode
import dev.vbmeta.studio.ui.UiMode
import dev.vbmeta.studio.ui.navigation3.Navigator
import dev.vbmeta.studio.ui.viewmodel.WorkbenchViewModel
import kotlinx.coroutines.delay

@Composable
fun WorkbenchPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
) {
    val viewModel = viewModel<WorkbenchViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshKeys()
        viewModel.refreshToolchain()
        onPauseOrDispose { }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) viewModel.onImagesPicked(uris)
    }

    var exportJob by remember { mutableStateOf<ImageJob?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val job = exportJob
        if (uri != null && job != null) viewModel.writeOutput(job, uri)
    }

    LaunchedEffect(uiState.lastError) {
        val error = uiState.lastError ?: return@LaunchedEffect
        delay(5000)
        viewModel.clearError()
    }

    val actions = WorkbenchActions(
        onPickImages = { pickLauncher.launch("*/*") },
        onSelectJob = viewModel::selectJob,
        onUpdateConfig = viewModel::updateConfig,
        onRemoveJob = viewModel::removeJob,
        onRunJobs = viewModel::runJobs,
        onCancelRun = viewModel::cancelRun,
        onExportJob = { job ->
            exportJob = job
            exportLauncher.launch(job.displayName)
        },
        onCreateVbmeta = viewModel::createVbmeta,
        onClearFinished = viewModel::clearFinished,
        onApplyPreset = viewModel::applyPreset,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> WorkbenchPagerMiuix(uiState, actions, bottomInnerPadding)
        UiMode.Material -> WorkbenchPagerMaterial(uiState, actions, bottomInnerPadding)
    }
}
