package dev.vbmeta.studio.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vbmeta.studio.engine.AvbTool
import dev.vbmeta.studio.engine.KeyEntry
import dev.vbmeta.studio.templateApp
import dev.vbmeta.studio.ui.screen.keys.KeysUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class KeysViewModel : ViewModel() {

    private val keyManager = templateApp.keyManager
    private val toolchain = templateApp.toolchainManager
    private val app = templateApp

    private val _uiState = MutableStateFlow(KeysUiState())
    val uiState: StateFlow<KeysUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val keys = keyManager.listKeys()
            val fingerprints = mutableMapOf<String, String>()
            keys.forEach { key ->
                runCatching {
                    keyManager.fingerprint(key.privateKeyPath)?.let { fingerprints[key.name] = it }
                }
            }
            _uiState.update { it.copy(keys = keys, fingerprints = fingerprints) }
        }
    }

    fun generate(name: String, bits: Int) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(generating = true) }
            runCatching { keyManager.generate(uniqueName(trimmed), bits) }
            _uiState.update { it.copy(generating = false) }
            refresh()
        }
    }

    fun importKey(uri: Uri, displayName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val tmp = File(app.cacheDir, "import_key.pem")
            app.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: return@launch
            val base = displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "imported"
            runCatching { keyManager.import(uniqueName(base), tmp.absolutePath) }
            tmp.delete()
            refresh()
        }
    }

    fun delete(name: String) {
        keyManager.delete(name)
        refresh()
    }

    fun exportPublicKey(key: KeyEntry, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val tmp = File(app.cacheDir, "pub_${key.name}.pem")
                if (!keyManager.exportPublicKey(key.privateKeyPath, tmp.absolutePath)) return@runCatching
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    tmp.inputStream().use { it.copyTo(out) }
                }
                tmp.delete()
            }
        }
    }

    fun exportAvbPublicKey(key: KeyEntry, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val ok = toolchain.ensureReady()
                if (!ok) return@runCatching
                val tmp = File(app.cacheDir, "avb_pub_${key.name}.bin")
                val exit = AvbTool(toolchain).extractPublicKey(key.privateKeyPath, tmp.absolutePath)
                if (exit != 0) return@runCatching
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    tmp.inputStream().use { it.copyTo(out) }
                }
                tmp.delete()
            }
        }
    }

    /** 导出公钥 SHA-256 摘要（extract_public_key_digest）。 */
    fun exportPublicKeyDigest(key: KeyEntry, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val ok = toolchain.ensureReady()
                if (!ok) return@runCatching
                val tmp = File(app.cacheDir, "pub_digest_${key.name}.bin")
                val exit = AvbTool(toolchain).extractPublicKeyDigest(key.privateKeyPath, tmp.absolutePath)
                if (exit != 0) return@runCatching
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    tmp.inputStream().use { it.copyTo(out) }
                }
                tmp.delete()
            }
        }
    }

    private fun uniqueName(base: String): String {
        val existing = keyManager.listKeys().map { it.name }.toSet()
        if (base !in existing) return base
        var i = 2
        while ("$base-$i" in existing) i++
        return "$base-$i"
    }
}
