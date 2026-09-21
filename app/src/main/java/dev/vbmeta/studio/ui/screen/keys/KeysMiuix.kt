package dev.vbmeta.studio.ui.screen.keys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vbmeta.studio.R
import dev.vbmeta.studio.engine.KeyEntry
import dev.vbmeta.studio.ui.component.miuix.EditText
import dev.vbmeta.studio.ui.theme.LocalEnableBlur
import dev.vbmeta.studio.ui.util.BlurredBar
import dev.vbmeta.studio.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val KEY_SIZES = listOf(2048, 4096)

@Composable
fun KeysPagerMiuix(
    state: KeysUiState,
    actions: KeysActions,
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
                    title = stringResource(R.string.tab_keys),
                    actions = {
                        IconButton(onClick = actions.onImport, modifier = Modifier.padding(end = 8.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.FileOpen,
                                tint = colorScheme.onSurface,
                                contentDescription = stringResource(R.string.keys_import),
                            )
                        }
                    },
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
                        GenerateCard(state, actions)
                        if (state.keys.isEmpty()) {
                            Text(
                                text = stringResource(R.string.keys_empty),
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        } else {
                            state.keys.forEach { key ->
                                KeyCard(state, actions, key)
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
private fun GenerateCard(
    state: KeysUiState,
    actions: KeysActions,
) {
    var name by remember { mutableStateOf("") }
    var bitsIndex by remember { mutableStateOf(1) }
    Card(modifier = Modifier.fillMaxWidth(), showIndication = false) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.keys_generate),
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            EditText(
                title = stringResource(R.string.keys_name),
                value = name,
                onValueChange = { name = it },
                textHint = "avb-key",
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.keys_bits),
                summary = "${KEY_SIZES[bitsIndex]} bit",
                items = KEY_SIZES.map { "$it bit" },
                selectedIndex = bitsIndex,
                onSelectedIndexChange = { bitsIndex = it.coerceIn(0, KEY_SIZES.lastIndex) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.generating) {
                    Text(
                        text = stringResource(R.string.keys_generating),
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    TextButton(text = stringResource(R.string.keys_generate), onClick = {
                        actions.onGenerate(name, KEY_SIZES[bitsIndex])
                        name = ""
                    })
                }
            }
        }
    }
}

@Composable
private fun KeyCard(
    state: KeysUiState,
    actions: KeysActions,
    key: KeyEntry,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Card(modifier = Modifier.fillMaxWidth(), showIndication = false) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            BasicComponent(
                title = key.name,
                summary = "${key.bits} bit · ${stringResource(R.string.keys_created)} ${formatTime(key.createdAt)}",
                endActions = {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        tint = colorScheme.primary,
                        contentDescription = null,
                    )
                },
                onClick = { },
            )
            state.fingerprints[key.name]?.let { fp ->
                Text(
                    text = "${stringResource(R.string.keys_fingerprint)}: $fp",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                )
            }
            Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                TextButton(text = stringResource(R.string.keys_export_public), onClick = { actions.onExportPublic(key) })
                TextButton(text = stringResource(R.string.keys_export_avb), onClick = { actions.onExportAvbKey(key) })
                TextButton(
                    text = stringResource(if (confirmDelete) R.string.keys_confirm_delete else R.string.keys_delete),
                    onClick = {
                        if (confirmDelete) {
                            actions.onDelete(key.name)
                        } else {
                            confirmDelete = true
                            scope.launch {
                                delay(3000)
                                confirmDelete = false
                            }
                        }
                    },
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
