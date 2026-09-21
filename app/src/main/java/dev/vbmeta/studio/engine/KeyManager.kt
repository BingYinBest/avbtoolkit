package dev.vbmeta.studio.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class KeyEntry(
    val name: String,
    val bits: Int,
    val createdAt: Long,
    val privateKeyPath: String,
)

/**
 * AVB 签名密钥管理。密钥以 PEM 私钥形式存于 filesDir/keys/<name>/private.pem，
 * 每个密钥目录附带 meta.json（位数、创建时间）。
 */
class KeyManager(context: Context, private val toolchain: ToolchainManager) {

    private val keysDir: File = File(context.filesDir, "keys")

    private fun keyDir(name: String): File = File(keysDir, name)

    private fun metaFile(name: String): File = File(keyDir(name), "meta.json")

    fun listKeys(): List<KeyEntry> {
        if (!keysDir.exists()) return emptyList()
        return keysDir.listFiles()?.filter { it.isDirectory && File(it, "private.pem").isFile }
            ?.mapNotNull { dir ->
                val meta = try {
                    JSONObject(metaFile(dir.name).readText())
                } catch (_: Exception) {
                    null
                }
                KeyEntry(
                    name = dir.name,
                    bits = meta?.optInt("bits", 0) ?: 0,
                    createdAt = meta?.optLong("created_at", 0) ?: 0,
                    privateKeyPath = File(dir, "private.pem").absolutePath,
                )
            }
            ?.sortedByDescending { it.createdAt } ?: emptyList()
    }

    fun privateKeyPath(name: String): String? {
        val f = File(keyDir(name), "private.pem")
        return if (f.isFile) f.absolutePath else null
    }

    /** 用 openssl 生成 RSA 密钥对并落盘。 */
    suspend fun generate(name: String, bits: Int): Boolean = withContext(Dispatchers.IO) {
        val dir = keyDir(name)
        dir.mkdirs()
        val pem = File(dir, "private.pem")
        val exit = CommandRunner.run(
            listOf(
                toolchain.paths().openssl, "genpkey",
                "-algorithm", "RSA",
                "-pkeyopt", "rsa_keygen_bits:$bits",
                "-out", pem.absolutePath,
            ),
            toolchain.env(),
        )
        if (exit != 0) {
            dir.deleteRecursively()
            return@withContext false
        }
        val meta = JSONObject()
            .put("bits", bits)
            .put("created_at", System.currentTimeMillis())
        metaFile(name).writeText(meta.toString())
        true
    }

    /** 校验 PEM 私钥是否有效。 */
    suspend fun check(pemPath: String): Boolean = withContext(Dispatchers.IO) {
        val (exit, _) = CommandRunner.runCapture(
            listOf(toolchain.paths().openssl, "rsa", "-check", "-noout", "-in", pemPath),
            toolchain.env(),
        )
        exit == 0
    }

    /** 从 PEM 导入为命名密钥（复制到密钥库）。 */
    suspend fun import(name: String, pemPath: String): Boolean = withContext(Dispatchers.IO) {
        if (!check(pemPath)) return@withContext false
        val dir = keyDir(name)
        dir.mkdirs()
        val pem = File(dir, "private.pem")
        if (pem.exists()) return@withContext false // 同名已存在
        try {
            File(pemPath).copyTo(pem)
        } catch (_: Exception) {
            return@withContext false
        }
        val (exit, out) = CommandRunner.runCapture(
            listOf(toolchain.paths().openssl, "rsa", "-in", pem.absolutePath, "-noout", "-text"),
            toolchain.env(),
        )
        val bits = if (exit == 0) {
            Regex("Private-Key: \\((\\d+) bit").find(out)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        } else 0
        val meta = JSONObject()
            .put("bits", bits)
            .put("created_at", System.currentTimeMillis())
        metaFile(name).writeText(meta.toString())
        true
    }

    fun delete(name: String) {
        keyDir(name).deleteRecursively()
    }

    /** 导出 PEM 公钥。 */
    suspend fun exportPublicKey(pemPath: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        val exit = CommandRunner.run(
            listOf(
                toolchain.paths().openssl, "pkey",
                "-in", pemPath,
                "-pubout",
                "-out", outputPath,
            ),
            toolchain.env(),
        )
        exit == 0
    }

    /** 计算公钥指纹（SubjectPublicKeyInfo DER 的 SHA-1，冒号分隔大写十六进制）。 */
    suspend fun fingerprint(pemPath: String): String? = withContext(Dispatchers.IO) {
        val (exit, der) = CommandRunner.runCaptureBytes(
            listOf(
                toolchain.paths().openssl, "pkey",
                "-in", pemPath,
                "-pubout", "-outform", "DER",
            ),
            toolchain.env(),
        )
        if (exit != 0) return@withContext null
        val digest = MessageDigest.getInstance("SHA-1").digest(der)
        digest.joinToString(":") { "%02X".format(it) }
    }
}
