package dev.vbmeta.studio.ui.viewmodel

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vbmeta.studio.engine.AvbTool
import dev.vbmeta.studio.templateApp
import dev.vbmeta.studio.ui.screen.atx.AtxUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class AtxViewModel : ViewModel() {

    private val app = templateApp
    private val avb = AvbTool(app.toolchainManager)

    private val _uiState = MutableStateFlow(AtxUiState())
    val uiState: StateFlow<AtxUiState> = _uiState.asStateFlow()

    private fun copyToWork(uri: Uri, prefix: String): Pair<String, String>? {
        return try {
            val workDir = File(app.filesDir, "work").apply { mkdirs() }
            val name = queryDisplayName(uri) ?: "${prefix}_${System.currentTimeMillis()}.pem"
            val workFile = File(workDir, "${System.currentTimeMillis()}_$name")
            app.contentResolver.openInputStream(uri)?.use { input ->
                workFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            Pair(workFile.absolutePath, name)
        } catch (_: Exception) {
            null
        }
    }

    fun pickFile(kind: String, uri: Uri) {
        copyToWork(uri, kind)?.let { (path, name) ->
            _uiState.update { s ->
                when (kind) {
                    "subject" -> s.copy(subjectKeyPath = path, subjectKeyName = name)
                    "authority" -> s.copy(authorityKeyPath = path, authorityKeyName = name)
                    "root" -> s.copy(rootAuthorityKeyPath = path, rootAuthorityKeyName = name)
                    "inter" -> s.copy(intermediateCertPath = path, intermediateCertName = name)
                    "productCert" -> s.copy(productCertPath = path, productCertName = name)
                    "unlockCert" -> s.copy(unlockCertPath = path, unlockCertName = name)
                    "unlockKey" -> s.copy(unlockKeyPath = path, unlockKeyName = name)
                    else -> s
                }
            }
        }
    }

    fun subjectChange(v: String) = _uiState.update { it.copy(subject = v) }
    fun productIdChange(v: String) = _uiState.update { it.copy(productId = v) }
    fun challengeChange(v: String) = _uiState.update { it.copy(challenge = v) }

    fun makeCertificate() = runCmd { st ->
        val key = st.subjectKeyPath ?: return@runCmd "未选择主题密钥"
        val sb = StringBuilder()
        val exit = avb.makeCertificate(
            outputPath = File(app.filesDir, "work/atx_cert.bin").absolutePath,
            subject = st.subject,
            subjectKeyPath = key,
            authorityKeyPath = st.authorityKeyPath,
            onLine = { sb.appendLine(it) },
        )
        sb.appendLine("make_certificate 退出码: $exit")
        sb.toString()
    }

    fun makePermAttrs() = runCmd { st ->
        val key = st.rootAuthorityKeyPath ?: return@runCmd "未选择根授权密钥"
        val sb = StringBuilder()
        val productId = st.productId.trim().takeIf { it.isNotEmpty() }?.let {
            try {
                it.chunked(2).map { c -> c.toInt(16).toByte() }.toByteArray()
            } catch (_: Exception) { null }
        }
        val exit = avb.makeCertPermanentAttributes(
            outputPath = File(app.filesDir, "work/atx_perm_attrs.bin").absolutePath,
            rootAuthorityKeyPath = key,
            productId = productId,
            onLine = { sb.appendLine(it) },
        )
        sb.appendLine("make_cert_permanent_attributes 退出码: $exit")
        sb.toString()
    }

    fun makeMetadata() = runCmd { st ->
        val inter = st.intermediateCertPath ?: return@runCmd "未选择中间证书"
        val product = st.productCertPath ?: return@runCmd "未选择产品证书"
        val sb = StringBuilder()
        val exit = avb.makeCertMetadata(
            outputPath = File(app.filesDir, "work/atx_metadata.bin").absolutePath,
            intermediateKeyCertificatePath = inter,
            productKeyCertificatePath = product,
            onLine = { sb.appendLine(it) },
        )
        sb.appendLine("make_cert_metadata 退出码: $exit")
        sb.toString()
    }

    fun makeUnlock() = runCmd { st ->
        val inter = st.intermediateCertPath ?: return@runCmd "未选择中间证书"
        val unlockCert = st.unlockCertPath ?: return@runCmd "未选择解锁证书"
        val unlockKey = st.unlockKeyPath ?: return@runCmd "未选择解锁密钥"
        val sb = StringBuilder()
        val exit = avb.makeCertUnlockCredential(
            outputPath = File(app.filesDir, "work/atx_unlock.bin").absolutePath,
            intermediateKeyCertificatePath = inter,
            unlockKeyCertificatePath = unlockCert,
            challenge = st.challenge,
            unlockKeyPath = unlockKey,
            onLine = { sb.appendLine(it) },
        )
        sb.appendLine("make_cert_unlock_credential 退出码: $exit")
        sb.toString()
    }

    private fun runCmd(block: suspend (AtxUiState) -> String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(running = true) }
            val msg = runCatching { block(_uiState.value) }.getOrElse { e -> "异常: ${e.message}" }
            _uiState.update { it.copy(running = false, result = msg) }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) {
            null
        }
    }
}