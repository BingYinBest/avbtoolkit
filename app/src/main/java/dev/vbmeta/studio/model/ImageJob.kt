package dev.vbmeta.studio.model

import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

enum class PartitionType(val label: String) {
    VBMETA("vbmeta"),
    BOOT("boot"),
    VENDOR_BOOT("vendor_boot"),
    INIT_BOOT("init_boot"),
    DTBO("dtbo"),
    SYSTEM("system"),
    VENDOR("vendor"),
    PRODUCT("product"),
    OTHER("未知"),
}

enum class JobMode(val label: String) {
    HASH_FOOTER("哈希签名"),
    HASHTREE_FOOTER("哈希树签名"),
    APPEND_VBMETA("追加 vbmeta"),
    ERASE_FOOTER("擦除 footer"),
    ZERO_HASHTREE("清零哈希树"),
    EXTRACT_VBMETA("提取 vbmeta"),
    RESIZE_IMAGE("调整大小"),
    INFO("查看信息"),
    VERIFY("验证"),
    FEC_ENCODE("FEC 编码"),
}

enum class JobStatus(val label: String) {
    PENDING("排队中"),
    RUNNING("处理中"),
    SUCCESS("成功"),
    FAILED("失败"),
    CANCELLED("已取消"),
}

data class SignConfig(
    val mode: JobMode = JobMode.HASH_FOOTER,
    val keyName: String? = null,
    val algorithm: String = "SHA256_RSA4096",
    val rollbackIndex: Long = 0,
    val hashAlgorithm: String = "sha256",
    /** 仅哈希树模式：null=默认(2)，0=不生成 FEC */
    val fecNumRoots: Int? = 2,
    /** 仅 vbmeta 模式：0=强制验证，2=禁用验证 */
    val vbmetaFlags: Int = 0,
    /** null=自动按文件大小向上取整到 4K */
    val partitionSize: Long? = null,
    /** null=按分区类型自动推断 */
    val partitionName: String? = null,
    /** 追加 vbmeta 模式：要追加的 vbmeta 镜像工作路径 */
    val extraImagePath: String? = null,
    /** 擦除 footer 模式：保留哈希树 */
    val keepHashtree: Boolean = false,
    /** 提取 vbmeta / 摘要输出模式：输出填充字节数 */
    val paddingSize: Int? = null,
)

data class ImageJob(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val srcUri: String,
    val workPath: String,
    val partitionType: PartitionType,
    val sizeBytes: Long,
    val config: SignConfig = SignConfig(),
    val status: JobStatus = JobStatus.PENDING,
    val log: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val outputUri: String? = null,
)

object ImageDetector {

    private val VBMETA_MAGIC = byteArrayOf(0x41, 0x56, 0x42, 0x30) // "AVB0"
    private val ANDROID_MAGIC = "ANDROID!".toByteArray()
    private val AVB_FOOTER_MAGIC = "AVBf".toByteArray()

    fun detect(file: File, fileName: String): PartitionType {
        val head = readHead(file, 8)
        if (head.startsWith(VBMETA_MAGIC)) return PartitionType.VBMETA
        if (head.startsWith(ANDROID_MAGIC)) {
            return when {
                fileName.contains("vendor_boot") -> PartitionType.VENDOR_BOOT
                fileName.contains("init_boot") -> PartitionType.INIT_BOOT
                fileName.contains("dtbo") -> PartitionType.DTBO
                else -> PartitionType.BOOT
            }
        }
        if (hasAvbFooter(file)) {
            // 有 AVB footer 说明是可签名分区镜像；再按文件名细分
            return detectByName(fileName)
        }
        return detectByName(fileName)
    }

    /** 文件末尾是否带 AVB footer（magic "AVBf"）。 */
    fun hasAvbFooter(file: File): Boolean {
        if (file.length() < 64) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val tailSize = minOf(4096L, file.length())
                raf.seek(file.length() - tailSize)
                val buf = ByteArray(tailSize.toInt())
                raf.readFully(buf)
                buf.contains(AVB_FOOTER_MAGIC)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun detectByName(fileName: String): PartitionType = when {
        fileName.contains("vbmeta") -> PartitionType.VBMETA
        fileName.contains("vendor_boot") -> PartitionType.VENDOR_BOOT
        fileName.contains("init_boot") -> PartitionType.INIT_BOOT
        fileName.contains("dtbo") -> PartitionType.DTBO
        fileName.contains("boot") -> PartitionType.BOOT
        fileName.contains("system") -> PartitionType.SYSTEM
        fileName.contains("vendor") -> PartitionType.VENDOR
        fileName.contains("product") -> PartitionType.PRODUCT
        else -> PartitionType.OTHER
    }

    /** 按分区类型推断默认分区名。 */
    fun defaultPartitionName(type: PartitionType): String = type.label

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    private fun ByteArray.contains(pattern: ByteArray): Boolean {
        if (pattern.isEmpty() || size < pattern.size) return false
        outer@ for (i in 0..size - pattern.size) {
            for (j in pattern.indices) {
                if (this[i + j] != pattern[j]) continue@outer
            }
            return true
        }
        return false
    }

    private fun readHead(file: File, n: Int): ByteArray {
        val buf = ByteArray(n)
        return file.inputStream().use { ins ->
            var off = 0
            while (off < n) {
                val r = ins.read(buf, off, n - off)
                if (r < 0) break
                off += r
            }
            buf.copyOf(off)
        }
    }
}
