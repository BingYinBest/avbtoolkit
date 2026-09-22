package dev.vbmeta.studio.ui.viewmodel

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vbmeta.studio.engine.AvbTool
import dev.vbmeta.studio.templateApp
import dev.vbmeta.studio.ui.command.ArgKind
import dev.vbmeta.studio.ui.command.CommandDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CommandUiState(
    val command: CommandDef? = null,
    val form: Map<String, String> = emptyMap(),
    val running: Boolean = false,
    val result: String = "",
    val outputPath: String? = null,
)

/** 统一命令执行器：按 CommandDef.id 分发到 AvbTool 对应方法。 */
class CommandViewModel : ViewModel() {

    private val app = templateApp
    private val avb = AvbTool(app.toolchainManager)

    private val _uiState = MutableStateFlow(CommandUiState())
    val uiState: StateFlow<CommandUiState> = _uiState.asStateFlow()

    fun select(command: CommandDef) {
        _uiState.update { CommandUiState(command = command) }
    }

    fun dismiss() {
        _uiState.update { it.copy(command = null, result = "", outputPath = null) }
    }

    fun updateForm(key: String, value: String) {
        _uiState.update { it.copy(form = it.form + (key to value)) }
    }

    fun pickFileArg(key: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val workDir = File(app.filesDir, "work").apply { mkdirs() }
            val name = queryDisplayName(uri) ?: "${key}_${System.currentTimeMillis()}.bin"
            val workFile = File(workDir, "${System.currentTimeMillis()}_$name")
            try {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    workFile.outputStream().use { output -> input.copyTo(output) }
                }
                updateForm(key, workFile.absolutePath)
            } catch (_: Exception) {
            }
        }
    }

    fun run() {
        val cmd = _uiState.value.command ?: return
        if (_uiState.value.running) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(running = true, result = "", outputPath = null) }
            val (msg, output) = runCatching { execute(cmd) }.getOrElse { e -> "异常: ${e.message}" to null }
            _uiState.update { it.copy(running = false, result = msg, outputPath = output) }
        }
    }

    /** 导出执行产物到 SAF 目标。 */
    fun exportOutput(uri: Uri) {
        val path = _uiState.value.outputPath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    File(path).inputStream().use { it.copyTo(out) }
                }
            }
        }
    }

    private fun workFile(name: String): String = File(File(app.filesDir, "work"), name).absolutePath

    private suspend fun execute(cmd: CommandDef): Pair<String, String?> {
        if (cmd.unsupported) {
            return "该子命令在官方 AOSP avbtool.py 中不存在，无法执行。" to null
        }
        val f = _uiState.value.form
        fun file(key: String): String? {
            val v = f[key]?.takeIf { it.isNotBlank() } ?: return null
            return v
        }
        fun num(key: String): Long? = f[key]?.toLongOrNull()
        fun int(key: String): Int? = f[key]?.toIntOrNull()
        fun bool(key: String): Boolean = f[key] == "true"
        fun missing(key: String): String =
            "缺少文件参数：${cmd.args.firstOrNull { it.key == key }?.label ?: key}"

        val sb = StringBuilder()
        val onLine: (String) -> Unit = { sb.appendLine(it) }

        when (cmd.id) {
            "version" -> {
                avb.version(onLine)
                return sb.toString().ifBlank { "完成" } to null
            }

            "generate_test_image" -> {
                val out = workFile("gen_test.img")
                avb.generateTestImage(out, num("image_size"), int("start_byte"), onLine)
                return sb.toString().ifBlank { "完成 ✓" } to out
            }

            "make_vbmeta_image" -> {
                val out = workFile("vbmeta.img")
                val key = file("key")
                avb.makeVbmetaImage(
                    AvbTool.VbmetaParams(
                        output = out,
                        keyPath = key,
                        algorithm = f["algorithm"]?.takeIf { it.isNotBlank() } ?: "NONE",
                        flags = int("flags") ?: 0,
                        paddingSize = int("padding_size") ?: 4096,
                    ),
                    onLine,
                )
                return sb.toString().ifBlank { "完成 ✓" } to out
            }

            "append_vbmeta_image" -> {
                val image = file("image") ?: return missing("image") to null
                val vb = file("vbmeta_image") ?: return missing("vbmeta_image") to null
                avb.appendVbmetaImage(image, num("partition_size") ?: 0L, vb, onLine)
                return sb.toString().ifBlank { "完成 ✓" } to image
            }

            "add_hash_footer" -> {
                val image = file("image") ?: return missing("image") to null
                val key = file("key")
                avb.addHashFooter(
                    AvbTool.HashFooterParams(
                        imagePath = image,
                        partitionSize = num("partition_size") ?: 0L,
                        partitionName = f["partition_name"] ?: "boot",
                        keyPath = key,
                        algorithm = f["algorithm"]?.takeIf { it.isNotBlank() } ?: "SHA256_RSA4096",
                        rollbackIndex = num("rollback_index") ?: 0,
                        hashAlgorithm = f["hash_algorithm"]?.takeIf { it.isNotBlank() } ?: "sha256",
                    ),
                    onLine,
                )
                return sb.toString().ifBlank { "完成 ✓" } to image
            }

            "add_hashtree_footer" -> {
                val image = file("image") ?: return missing("image") to null
                val key = file("key")
                avb.addHashtreeFooter(
                    AvbTool.HashtreeFooterParams(
                        imagePath = image,
                        partitionSize = num("partition_size") ?: 0L,
                        partitionName = f["partition_name"] ?: "system",
                        keyPath = key,
                        algorithm = f["algorithm"]?.takeIf { it.isNotBlank() } ?: "SHA256_RSA4096",
                        rollbackIndex = num("rollback_index") ?: 0,
                        hashAlgorithm = f["hash_algorithm"]?.takeIf { it.isNotBlank() } ?: "sha256",
                        fecNumRoots = int("fec_num_roots"),
                    ),
                    onLine,
                )
                return sb.toString().ifBlank { "完成 ✓" } to image
            }

            "erase_footer" -> {
                val image = file("image") ?: return missing("image") to null
                avb.eraseFooter(image, bool("keep_hashtree"), onLine)
                return sb.toString().ifBlank { "完成 ✓" } to image
            }

            "zero_hashtree" -> {
                val image = file("image") ?: return missing("image") to null
                avb.zeroHashtree(image, onLine)
                return sb.toString().ifBlank { "完成 ✓" } to image
            }

            "extract_vbmeta_image" -> {
                val image = file("image") ?: return missing("image") to null
                val out = workFile("extracted.vbmeta")
                avb.extractVbmetaImage(image, out, int("padding_size"), onLine)
                return sb.toString().ifBlank { "完成 ✓" } to out
            }

            "resize_image" -> {
                val image = file("image") ?: return missing("image") to null
                avb.resizeImage(image, num("partition_size") ?: 0L, onLine)
                return sb.toString().ifBlank { "完成 ✓" } to image
            }

            "set_ab_metadata" -> {
                val misc = file("misc_image") ?: return missing("misc_image") to null
                avb.setAbMetadata(misc, f["slot_data"] ?: "boot,slot=0", onLine)
                return sb.toString().ifBlank { "完成 ✓" } to null
            }

            "info_image" -> {
                val image = file("image") ?: return missing("image") to null
                val info = avb.infoImage(image)
                if (info.hasFooter) {
                    sb.appendLine("Footer version: ${info.footerVersion}")
                    sb.appendLine("算法: ${info.algorithm}")
                    sb.appendLine("Rollback Index: ${info.rollbackIndex}")
                    sb.appendLine("Flags: ${info.flags}")
                    sb.appendLine("镜像大小: ${info.imageSize}")
                    sb.appendLine("descriptors: ${info.descriptors.joinToString()}")
                } else {
                    sb.appendLine("未检测到 AVB footer")
                }
                return sb.toString() to null
            }

            "verify_image" -> {
                val image = file("image") ?: return missing("image") to null
                val key = file("key")
                val exit = avb.verifyImage(image, key, onLine)
                sb.appendLine(if (exit == 0) "验证通过 ✓" else "验证失败（退出码 $exit）")
                return sb.toString() to null
            }

            "print_partition_digests" -> {
                val image = file("image") ?: return missing("image") to null
                avb.printPartitionDigests(image, onLine)
                return sb.toString().ifBlank { "完成 ✓" } to null
            }

            "calculate_vbmeta_digest" -> {
                val image = file("image") ?: return missing("image") to null
                avb.calculateVbmetaDigest(image, hashAlgorithm = f["hash_algorithm"]?.takeIf { it.isNotBlank() } ?: "sha256", onLine = onLine)
                return sb.toString().ifBlank { "完成 ✓" } to null
            }

            "calculate_kernel_cmdline" -> {
                val image = file("image") ?: return missing("image") to null
                avb.calculateKernelCmdline(image, hashtreeDisabled = bool("hashtree_disabled"), onLine = onLine)
                return sb.toString().ifBlank { "完成 ✓" } to null
            }

            "extract_public_key" -> {
                val key = file("key") ?: return missing("key") to null
                val out = workFile("public_key.bin")
                avb.extractPublicKey(key, out, onLine)
                return sb.toString().ifBlank { "完成 ✓" } to out
            }

            "extract_public_key_digest" -> {
                val key = file("key") ?: return missing("key") to null
                val out = workFile("public_key_digest.bin")
                avb.extractPublicKeyDigest(key, out, onLine)
                return sb.toString().ifBlank { "完成 ✓" } to out
            }

            "make_certificate" -> {
                val key = file("subject_key") ?: return missing("subject_key") to null
                val out = workFile("atx_cert.bin")
                avb.makeCertificate(
                    outputPath = out,
                    subject = f["subject"] ?: "AVB key",
                    subjectKeyPath = key,
                    authorityKeyPath = file("authority_key"),
                    onLine = onLine,
                )
                return sb.toString().ifBlank { "完成 ✓" } to out
            }

            "make_cert_permanent_attributes" -> {
                val key = file("root_authority_key") ?: return missing("root_authority_key") to null
                val out = workFile("atx_perm_attrs.bin")
                val productId = f["product_id"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    try {
                        it.chunked(2).map { c -> c.toInt(16).toByte() }.toByteArray()
                    } catch (_: Exception) {
                        null
                    }
                }
                avb.makeCertPermanentAttributes(out, key, productId, onLine)
                return sb.toString().ifBlank { "完成 ✓" } to out
            }

            "make_cert_metadata" -> {
                val inter = file("intermediate_cert") ?: return missing("intermediate_cert") to null
                val product = file("product_cert") ?: return missing("product_cert") to null
                val out = workFile("atx_metadata.bin")
                avb.makeCertMetadata(out, inter, product, onLine)
                return sb.toString().ifBlank { "完成 ✓" } to out
            }

            "make_cert_unlock_credential" -> {
                val inter = file("intermediate_cert") ?: return missing("intermediate_cert") to null
                val unlockCert = file("unlock_cert") ?: return missing("unlock_cert") to null
                val unlockKey = file("unlock_key") ?: return missing("unlock_key") to null
                val out = workFile("atx_unlock.bin")
                avb.makeCertUnlockCredential(out, inter, unlockCert, f["challenge"] ?: "", unlockKey, onLine)
                return sb.toString().ifBlank { "完成 ✓" } to out
            }

            "check_mldsa_support" -> {
                val avbtoolFile = File(File(app.filesDir, "toolchain"), "avbtool.py")
                val content = if (avbtoolFile.exists()) avbtoolFile.readText() else ""
                val hasMldsa = content.contains("MLDSA") || content.contains("ML_DSA") || content.contains("ML-DSA")
                sb.appendLine(if (hasMldsa) {
                    "当前 avbtool 支持 ML-DSA（后量子签名）"
                } else {
                    "当前 avbtool 不支持 ML-DSA（仅经典 RSA：SHA256/SHA512 × RSA2048/4096/8192）"
                })
                sb.appendLine("说明：AOSP 官方 avbtool 目前未加入后量子算法。")
                return sb.toString() to null
            }

            "resign_image" -> {
                val image = file("image") ?: return missing("image") to null
                val key = file("key") ?: return missing("key") to null
                // 1) 擦除 footer（可选保留原哈希树）
                val e1 = avb.eraseFooter(image, bool("keep_hashtree"), onLine)
                sb.appendLine("erase_footer 退出码: $e1")
                if (e1 != 0) return sb.toString() to image
                // 2) 用新密钥重新签名
                if (bool("hashtree")) {
                    val e2 = avb.addHashtreeFooter(
                        AvbTool.HashtreeFooterParams(
                            imagePath = image,
                            partitionSize = num("partition_size") ?: 0L,
                            partitionName = f["partition_name"] ?: "boot",
                            keyPath = key,
                            algorithm = f["algorithm"]?.takeIf { it.isNotBlank() } ?: "SHA256_RSA4096",
                            rollbackIndex = num("rollback_index") ?: 0,
                            hashAlgorithm = f["hash_algorithm"]?.takeIf { it.isNotBlank() } ?: "sha256",
                            fecNumRoots = int("fec_num_roots"),
                        ),
                        onLine,
                    )
                    sb.appendLine("add_hashtree_footer 退出码: $e2")
                } else {
                    val e2 = avb.addHashFooter(
                        AvbTool.HashFooterParams(
                            imagePath = image,
                            partitionSize = num("partition_size") ?: 0L,
                            partitionName = f["partition_name"] ?: "boot",
                            keyPath = key,
                            algorithm = f["algorithm"]?.takeIf { it.isNotBlank() } ?: "SHA256_RSA4096",
                            rollbackIndex = num("rollback_index") ?: 0,
                            hashAlgorithm = f["hash_algorithm"]?.takeIf { it.isNotBlank() } ?: "sha256",
                        ),
                        onLine,
                    )
                    sb.appendLine("add_hash_footer 退出码: $e2")
                }
                return sb.toString() to image
            }

            else -> return "未知命令：${cmd.id}" to null
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