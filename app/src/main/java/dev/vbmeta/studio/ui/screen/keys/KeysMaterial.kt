package dev.vbmeta.studio.ui.screen.keys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.vbmeta.studio.R
import dev.vbmeta.studio.engine.KeyEntry
import dev.vbmeta.studio.ui.component.material.SegmentedDropdownItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val KEY_SIZES = listOf(2048, 4096)

@Composable
fun KeysPagerMaterial(
    state: KeysUiState,
    actions: KeysActions,
    bottomInnerPadding: Dp,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_keys)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                actions = {
                    IconButton(onClick = actions.onImport) {
                        Icon(Icons.Default.FileOpen, stringResource(R.string.keys_import))
                    }
                },
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
                    GenerateCard(state, actions)
                    if (state.keys.isEmpty()) {
                        Text(
                            text = stringResource(R.string.keys_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    } else {
                        state.keys.forEach { key ->
                            KeyCard(state, actions, key)
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.keys_generate),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.keys_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SegmentedDropdownItem(
                icon = null,
                title = stringResource(R.string.keys_bits),
                summary = "${KEY_SIZES[bitsIndex]} bit",
                items = KEY_SIZES.map { "$it bit" },
                selectedIndex = bitsIndex,
                onItemSelected = { bitsIndex = it.coerceIn(0, KEY_SIZES.lastIndex) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.generating) {
                    Text(
                        text = stringResource(R.string.keys_generating),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    Button(
                        onClick = {
                            actions.onGenerate(name, KEY_SIZES[bitsIndex])
                            name = ""
                        },
                    ) {
                        Text(stringResource(R.string.keys_generate))
                    }
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = key.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${key.bits} bit · ${stringResource(R.string.keys_created)} ${formatTime(key.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary)
            }
            state.fingerprints[key.name]?.let { fp ->
                Text(
                    text = "${stringResource(R.string.keys_fingerprint)}: $fp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { actions.onExportPublic(key) }) {
                    Text(stringResource(R.string.keys_export_public))
                }
                TextButton(onClick = { actions.onExportAvbKey(key) }) {
                    Text(stringResource(R.string.keys_export_avb))
                }
                TextButton(
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
                ) {
                    Text(
                        stringResource(if (confirmDelete) R.string.keys_confirm_delete else R.string.keys_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
