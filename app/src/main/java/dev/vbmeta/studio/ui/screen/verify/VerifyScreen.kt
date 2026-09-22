package dev.vbmeta.studio.ui.screen.verify

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vbmeta.studio.ui.LocalUiMode
import dev.vbmeta.studio.ui.UiMode
import dev.vbmeta.studio.ui.navigation3.Navigator
import dev.vbmeta.studio.ui.viewmodel.VerifyViewModel

@Composable
fun VerifyPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
) {
    val viewModel = viewModel<VerifyViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.pickImage(uri)
    }

    val actions = VerifyActions(
        onPickImage = { picker.launch("*/*") },
        onSelectMode = viewModel::selectMode,
        onRun = viewModel::run,
        onCopyResult = {
            if (uiState.result.isNotBlank()) {
                clipboard.setText(AnnotatedString(uiState.result))
                android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
            }
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> VerifyPagerMiuix(uiState, actions, bottomInnerPadding)
        UiMode.Material -> VerifyPagerMaterial(uiState, actions, bottomInnerPadding)
    }
}