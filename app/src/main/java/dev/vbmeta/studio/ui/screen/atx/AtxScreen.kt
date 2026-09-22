package dev.vbmeta.studio.ui.screen.atx

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
import dev.vbmeta.studio.ui.viewmodel.AtxViewModel

@Composable
fun AtxPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
) {
    val viewModel = viewModel<AtxViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var pickKind by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && pickKind.isNotEmpty()) viewModel.pickFile(pickKind, uri)
        pickKind = ""
    }

    val actions = AtxActions(
        onSubjectChange = viewModel::subjectChange,
        onPickSubjectKey = { pickKind = "subject"; picker.launch("*/*") },
        onPickAuthorityKey = { pickKind = "authority"; picker.launch("*/*") },
        onPickRootAuthorityKey = { pickKind = "root"; picker.launch("*/*") },
        onProductIdChange = viewModel::productIdChange,
        onPickIntermediateCert = { pickKind = "inter"; picker.launch("*/*") },
        onPickProductCert = { pickKind = "productCert"; picker.launch("*/*") },
        onPickUnlockCert = { pickKind = "unlockCert"; picker.launch("*/*") },
        onChallengeChange = viewModel::challengeChange,
        onPickUnlockKey = { pickKind = "unlockKey"; picker.launch("*/*") },
        onMakeCertificate = viewModel::makeCertificate,
        onMakePermAttrs = viewModel::makePermAttrs,
        onMakeMetadata = viewModel::makeMetadata,
        onMakeUnlock = viewModel::makeUnlock,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> AtxPagerMiuix(uiState, actions, bottomInnerPadding)
        UiMode.Material -> AtxPagerMaterial(uiState, actions, bottomInnerPadding)
    }
}