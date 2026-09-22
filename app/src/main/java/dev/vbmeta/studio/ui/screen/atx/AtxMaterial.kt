package dev.vbmeta.studio.ui.screen.atx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.vbmeta.studio.R

@Composable
fun AtxPagerMaterial(
    state: AtxUiState,
    actions: AtxActions,
    bottomInnerPadding: Dp,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.atx_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Title("make_certificate")
                            OutlinedTextField(state.subject, actions.onSubjectChange, label = { Text(stringResource(R.string.atx_subject)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            PickerRow(state.subjectKeyName ?: stringResource(R.string.atx_subject_key), actions.onPickSubjectKey)
                            PickerRow(state.authorityKeyName ?: stringResource(R.string.atx_authority_key), actions.onPickAuthorityKey)
                            Button(onClick = actions.onMakeCertificate) { Text(stringResource(R.string.verify_run)) }
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Title("make_cert_permanent_attributes")
                            PickerRow(state.rootAuthorityKeyName ?: stringResource(R.string.atx_authority_key), actions.onPickRootAuthorityKey)
                            OutlinedTextField(state.productId, actions.onProductIdChange, label = { Text(stringResource(R.string.atx_product_id)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(onClick = actions.onMakePermAttrs) { Text(stringResource(R.string.verify_run)) }
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Title("make_cert_metadata")
                            PickerRow(state.intermediateCertName ?: stringResource(R.string.atx_intermediate_cert), actions.onPickIntermediateCert)
                            PickerRow(state.productCertName ?: stringResource(R.string.atx_product_cert), actions.onPickProductCert)
                            Button(onClick = actions.onMakeMetadata) { Text(stringResource(R.string.verify_run)) }
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Title("make_cert_unlock_credential")
                            PickerRow(state.intermediateCertName ?: stringResource(R.string.atx_intermediate_cert), actions.onPickIntermediateCert)
                            PickerRow(state.unlockCertName ?: stringResource(R.string.atx_unlock_cert), actions.onPickUnlockCert)
                            PickerRow(state.unlockKeyName ?: stringResource(R.string.atx_unlock_key), actions.onPickUnlockKey)
                            OutlinedTextField(state.challenge, actions.onChallengeChange, label = { Text(stringResource(R.string.atx_challenge)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(onClick = actions.onMakeUnlock) { Text(stringResource(R.string.verify_run)) }
                        }
                    }
                    if (state.result.isNotBlank()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = state.result,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PickerRow(title: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(title, fontWeight = FontWeight.Medium)
    }
}