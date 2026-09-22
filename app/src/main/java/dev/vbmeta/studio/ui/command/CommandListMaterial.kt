package dev.vbmeta.studio.ui.command

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.vbmeta.studio.R
import dev.vbmeta.studio.ui.viewmodel.CommandUiState

@Composable
fun CommandListMaterial(
    title: String,
    commands: List<CommandDef>,
    state: CommandUiState,
    actions: CommandListActions,
    bottomInnerPadding: Dp,
    header: (@Composable () -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                navigationIcon = {
                    if (state.command != null) {
                        IconButton(onClick = actions.onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val cmd = state.command
                    if (cmd == null) {
                        header?.invoke()
                        commands.forEach { c ->
                            CommandCardMaterial(c, onClick = { actions.onSelect(c) })
                        }
                    } else {
                        CommandFormMaterial(cmd, state, actions)
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

@Composable
private fun CommandCardMaterial(
    cmd: CommandDef,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (cmd.unsupported) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        },
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = cmd.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = cmd.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun CommandFormMaterial(
    cmd: CommandDef,
    state: CommandUiState,
    actions: CommandListActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = cmd.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            cmd.args.forEach { arg ->
                val value = state.form[arg.key] ?: ""
                when (arg.kind) {
                    ArgKind.FILE -> TextButton(onClick = { actions.onPickFile(arg.key) }) {
                        Icon(Icons.Default.FileOpen, null, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            value.substringAfterLast('/').ifEmpty { arg.label },
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    ArgKind.BOOL -> Column {
                        Text(arg.label, style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = value == "true",
                            onCheckedChange = { actions.onFormChange(arg.key, it.toString()) },
                        )
                    }

                    ArgKind.NUMBER -> OutlinedTextField(
                        value = value,
                        onValueChange = { actions.onFormChange(arg.key, it.filter(Char::isDigit)) },
                        label = { Text(arg.label) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ArgKind.TEXT -> OutlinedTextField(
                        value = value,
                        onValueChange = { actions.onFormChange(arg.key, it) },
                        label = { Text(arg.label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.onRun) {
                    Text(stringResource(R.string.verify_run))
                }
                if (state.outputPath != null) {
                    TextButton(onClick = actions.onExport) {
                        Text(stringResource(R.string.workbench_export))
                    }
                }
                if (state.result.isNotBlank()) {
                    TextButton(onClick = actions.onCopyResult) {
                        Text("复制")
                    }
                }
            }
            if (state.result.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        text = state.result,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }
}