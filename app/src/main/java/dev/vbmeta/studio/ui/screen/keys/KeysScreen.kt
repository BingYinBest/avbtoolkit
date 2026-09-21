package dev.vbmeta.studio.ui.screen.keys

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vbmeta.studio.engine.KeyEntry
import dev.vbmeta.studio.ui.LocalUiMode
import dev.vbmeta.studio.ui.UiMode
import dev.vbmeta.studio.ui.navigation3.Navigator
import dev.vbmeta.studio.ui.viewmodel.KeysViewModel
import android.provider.OpenableColumns

@Composable
fun KeysPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
) {
    val viewModel = viewModel<KeysViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    var pendingExport by remember { mutableStateOf<KeyEntry?>(null) }
    var pendingAvbExport by remember { mutableStateOf<KeyEntry?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            var name: String? = null
            runCatching {
                androidx.compose.ui.platform.LocalContext.current.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) name = c.getString(0) }
            }
            viewModel.importKey(uri, name)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-pem-file")
    ) { uri ->
        val key = pendingExport
        if (uri != null && key != null) viewModel.exportPublicKey(key, uri)
        pendingExport = null
    }

    val avbExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val key = pendingAvbExport
        if (uri != null && key != null) viewModel.exportAvbPublicKey(key, uri)
        pendingAvbExport = null
    }

    val actions = KeysActions(
        onGenerate = viewModel::generate,
        onImport = { importLauncher.launch(arrayOf("application/x-pem-file", "text/plain", "*/*")) },
        onDelete = viewModel::delete,
        onExportPublic = { key ->
            pendingExport = key
            exportLauncher.launch("${key.name}.pub.pem")
        },
        onExportAvbKey = { key ->
            pendingAvbExport = key
            avbExportLauncher.launch("${key.name}.avb_pub.bin")
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> KeysPagerMiuix(uiState, actions, bottomInnerPadding)
        UiMode.Material -> KeysPagerMaterial(uiState, actions, bottomInnerPadding)
    }
}
