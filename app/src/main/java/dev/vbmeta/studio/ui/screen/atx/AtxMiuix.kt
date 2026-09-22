package dev.vbmeta.studio.ui.screen.atx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vbmeta.studio.R
import dev.vbmeta.studio.ui.component.miuix.EditText
import dev.vbmeta.studio.ui.theme.LocalEnableBlur
import dev.vbmeta.studio.ui.util.BlurredBar
import dev.vbmeta.studio.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun AtxPagerMiuix(
    state: AtxUiState,
    actions: AtxActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.atx_title),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // make_certificate
                        Card(modifier = Modifier.fillMaxWidth(), showIndication = false) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                SectionTitle("make_certificate")
                                EditText(stringResource(R.string.atx_subject), state.subject, actions.onSubjectChange, textHint = "AVB key")
                                BasicComponent(
                                    title = state.subjectKeyName ?: stringResource(R.string.atx_subject_key),
                                    onClick = actions.onPickSubjectKey,
                                )
                                BasicComponent(
                                    title = state.authorityKeyName ?: stringResource(R.string.atx_authority_key),
                                    onClick = actions.onPickAuthorityKey,
                                )
                                TextButton(text = stringResource(R.string.verify_run), onClick = actions.onMakeCertificate)
                            }
                        }
                        // permanent attributes
                        Card(modifier = Modifier.fillMaxWidth(), showIndication = false) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                SectionTitle("make_cert_permanent_attributes")
                                BasicComponent(
                                    title = state.rootAuthorityKeyName ?: stringResource(R.string.atx_authority_key),
                                    onClick = actions.onPickRootAuthorityKey,
                                )
                                EditText(stringResource(R.string.atx_product_id), state.productId, actions.onProductIdChange)
                                TextButton(text = stringResource(R.string.verify_run), onClick = actions.onMakePermAttrs)
                            }
                        }
                        // metadata
                        Card(modifier = Modifier.fillMaxWidth(), showIndication = false) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                SectionTitle("make_cert_metadata")
                                BasicComponent(
                                    title = state.intermediateCertName ?: stringResource(R.string.atx_intermediate_cert),
                                    onClick = actions.onPickIntermediateCert,
                                )
                                BasicComponent(
                                    title = state.productCertName ?: stringResource(R.string.atx_product_cert),
                                    onClick = actions.onPickProductCert,
                                )
                                TextButton(text = stringResource(R.string.verify_run), onClick = actions.onMakeMetadata)
                            }
                        }
                        // unlock credential
                        Card(modifier = Modifier.fillMaxWidth(), showIndication = false) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                SectionTitle("make_cert_unlock_credential")
                                BasicComponent(
                                    title = state.intermediateCertName ?: stringResource(R.string.atx_intermediate_cert),
                                    onClick = actions.onPickIntermediateCert,
                                )
                                BasicComponent(
                                    title = state.unlockCertName ?: stringResource(R.string.atx_unlock_cert),
                                    onClick = actions.onPickUnlockCert,
                                )
                                BasicComponent(
                                    title = state.unlockKeyName ?: stringResource(R.string.atx_unlock_key),
                                    onClick = actions.onPickUnlockKey,
                                )
                                EditText(stringResource(R.string.atx_challenge), state.challenge, actions.onChallengeChange)
                                TextButton(text = stringResource(R.string.verify_run), onClick = actions.onMakeUnlock)
                            }
                        }
                        if (state.result.isNotBlank()) {
                            Card(modifier = Modifier.fillMaxWidth(), showIndication = false) {
                                Text(
                                    text = state.result,
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}