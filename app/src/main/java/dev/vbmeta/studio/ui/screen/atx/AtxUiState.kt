package dev.vbmeta.studio.ui.screen.atx

import androidx.compose.runtime.Immutable

@Immutable
data class AtxUiState(
    // make_certificate
    val subject: String = "AVB key",
    val subjectKeyPath: String? = null,
    val subjectKeyName: String? = null,
    val authorityKeyPath: String? = null,
    val authorityKeyName: String? = null,
    // permanent attributes
    val rootAuthorityKeyPath: String? = null,
    val rootAuthorityKeyName: String? = null,
    val productId: String = "",
    // metadata
    val intermediateCertPath: String? = null,
    val intermediateCertName: String? = null,
    val productCertPath: String? = null,
    val productCertName: String? = null,
    // unlock credential
    val unlockCertPath: String? = null,
    val unlockCertName: String? = null,
    val challenge: String = "",
    val unlockKeyPath: String? = null,
    val unlockKeyName: String? = null,
    // common
    val running: Boolean = false,
    val result: String = "",
)

@Immutable
data class AtxActions(
    val onSubjectChange: (String) -> Unit,
    val onPickSubjectKey: () -> Unit,
    val onPickAuthorityKey: () -> Unit,
    val onPickRootAuthorityKey: () -> Unit,
    val onProductIdChange: (String) -> Unit,
    val onPickIntermediateCert: () -> Unit,
    val onPickProductCert: () -> Unit,
    val onPickUnlockCert: () -> Unit,
    val onChallengeChange: (String) -> Unit,
    val onPickUnlockKey: () -> Unit,
    val onMakeCertificate: () -> Unit,
    val onMakePermAttrs: () -> Unit,
    val onMakeMetadata: () -> Unit,
    val onMakeUnlock: () -> Unit,
)