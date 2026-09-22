package dev.vbmeta.studio.engine

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * AVB 镜像信息解析器（info_image 功能核心）。
 *
 * 解析三层结构：
 *  1. Footer（镜像末尾 64 字节，magic "AVBf"）
 *  2. VBMeta 头（256 字节，magic "AVB0"）
 *  3. Descriptors（VBMeta 辅助块中的描述符序列）
 *
 * 全部字段均为大端序。结构与 AOSP external/avb 的
 * avb_footer.h / avb_vbmeta_image.h / avb_descriptor.h 保持一致。
 */

data class FooterInfo(
    val versionMajor: Int,
    val versionMinor: Int,
    val originalImageSize: Long,
    val vbmetaOffset: Long,
    val vbmetaSize: Long,
) {
    val version: String get() = "$versionMajor.$versionMinor"
}

data class VbmetaInfo(
    val requiredLibavbVersion: String,
    val headerBlock: Long,
    val authBlock: Long,
    val auxBlock: Long,
    val algorithmType: Int,
    val publicKeySha1: String?,
    val publicKeyBlob: ByteArray?,
    val rollbackIndex: Long,
    val flags: Int,
    val rollbackIndexLocation: Int,
    val releaseString: String,
    val publicKeyMetadataSize: Long,
    val descriptors: List<DescriptorInfo>,
) {
    val algorithmName: String get() = AVB_ALGORITHMS[algorithmType] ?: "UNKNOWN($algorithmType)"
}

sealed class DescriptorInfo {
    data class Hash(
        val imageSize: Long,
        val hashAlgorithm: String,
        val partitionName: String,
        val salt: ByteArray,
        val digest: ByteArray,
        val flags: Int,
    ) : DescriptorInfo()

    data class Hashtree(
        val dmVerityVersion: Int,
        val imageSize: Long,
        val treeOffset: Long,
        val treeSize: Long,
        val dataBlockSize: Int,
        val hashBlockSize: Int,
        val fecNumRoots: Int,
        val fecOffset: Long,
        val fecSize: Long,
        val hashAlgorithm: String,
        val partitionName: String,
        val salt: ByteArray,
        val rootDigest: ByteArray,
        val flags: Int,
    ) : DescriptorInfo()

    data class Property(val key: String, val value: String) : DescriptorInfo()

    data class KernelCmdline(val flags: Int, val cmdline: String) : DescriptorInfo()

    data class ChainPartition(
        val rollbackIndexLocation: Int,
        val partitionName: String,
        val publicKey: ByteArray,
    ) : DescriptorInfo()

    data class Unknown(val tag: Int) : DescriptorInfo()
}

data class InfoImageData(
    val hasFooter: Boolean,
    val footer: FooterInfo? = null,
    val hasVbmeta: Boolean = false,
    val vbmeta: VbmetaInfo? = null,
)

// 与 avbtool ALGORITHMS 表一致
private val AVB_ALGORITHMS = mapOf(
        0 to "NONE",
        1 to "SHA256_RSA2048",
        2 to "SHA256_RSA4096",
        3 to "SHA256_RSA8192",
        4 to "SHA512_RSA2048",
        5 to "SHA512_RSA4096",
        6 to "SHA512_RSA8192",
        7 to "MLDSA65",
        8 to "MLDSA87",
    )

/** info_image 解析器：纯 Kotlin 实现，不依赖外部命令，便于扩展。 */
object InfoImageParser {

    private const val FOOTER_MAGIC = "AVBf"
    private const val VBMETA_MAGIC = "AVB0"
    private const val FOOTER_SIZE = 64L
    private const val VBMETA_HEADER_SIZE = 256L

    // descriptor tags（与 avb_descriptor.h 一致）
    private const val TAG_PROPERTY = 0
    private const val TAG_HASH = 1
    private const val TAG_HASHTREE = 2
    private const val TAG_KERNEL_CMDLINE = 3
    private const val TAG_CHAIN_PARTITION = 4

    fun parse(file: File): InfoImageData {
        RandomAccessFile(file, "r").use { raf ->
            val footer = readFooter(raf, file.length())
            if (footer == null) return InfoImageData(hasFooter = false)

            val vbmeta = readVbmeta(raf, footer.vbmetaOffset, footer.vbmetaSize)
            return InfoImageData(
                hasFooter = true,
                footer = footer,
                hasVbmeta = vbmeta != null,
                vbmeta = vbmeta,
            )
        }
    }

    private fun readFooter(raf: RandomAccessFile, fileSize: Long): FooterInfo? {
        if (fileSize < FOOTER_SIZE) return null
        raf.seek(fileSize - FOOTER_SIZE)
        val magic = ByteArray(4)
        raf.readFully(magic)
        if (String(magic) != FOOTER_MAGIC) return null
        val versionMajor = raf.readInt()
        val versionMinor = raf.readInt()
        val originalImageSize = raf.readLong()
        val vbmetaOffset = raf.readLong()
        val vbmetaSize = raf.readLong()
        return FooterInfo(versionMajor, versionMinor, originalImageSize, vbmetaOffset, vbmetaSize)
    }

    private fun readVbmeta(raf: RandomAccessFile, offset: Long, size: Long): VbmetaInfo? {
        if (offset < 0 || size < VBMETA_HEADER_SIZE) return null
        raf.seek(offset)
        val magic = ByteArray(4)
        raf.readFully(magic)
        if (String(magic) != VBMETA_MAGIC) return null

        val versionMajor = raf.readInt()
        val versionMinor = raf.readInt()
        val authBlock = raf.readLong()
        val auxBlock = raf.readLong()
        val algorithmType = raf.readInt()
        val hashOffset = raf.readLong()
        val hashSize = raf.readLong()
        val signatureOffset = raf.readLong()
        val signatureSize = raf.readLong()
        val publicKeyOffset = raf.readLong()
        val publicKeySize = raf.readLong()
        val publicKeyMetadataOffset = raf.readLong()
        val publicKeyMetadataSize = raf.readLong()
        val descriptorsOffset = raf.readLong()
        val descriptorsSize = raf.readLong()
        val rollbackIndex = raf.readLong()
        val flags = raf.readInt()
        val rollbackIndexLocation = raf.readInt()
        val releaseBytes = ByteArray(47)
        raf.readFully(releaseBytes)
        val releaseString = releaseBytes.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)

        // 公钥 SHA-1（位于辅助块内）
        var publicKeySha1: String? = null
        var publicKeyBlob: ByteArray? = null
        if (publicKeySize > 0 && publicKeyOffset + publicKeySize <= size) {
            raf.seek(offset + publicKeyOffset)
            val keyBlob = ByteArray(publicKeySize.toInt())
            raf.readFully(keyBlob)
            publicKeyBlob = keyBlob
            publicKeySha1 = MessageDigest.getInstance("SHA-1").digest(keyBlob)
                .joinToString("") { "%02x".format(it) }
        }

        // 描述符（位于辅助块内）
        val descriptors = mutableListOf<DescriptorInfo>()
        if (descriptorsSize > 0 && descriptorsOffset + descriptorsSize <= size) {
            raf.seek(offset + descriptorsOffset)
            var pos = 0L
            val total = descriptorsSize
            while (pos + 16 <= total) {
                val tag = raf.readInt()
                val numBytesFollowing = raf.readLong()
                if (numBytesFollowing < 0 || pos + 16 + numBytesFollowing > total) break
                val data = ByteArray(numBytesFollowing.toInt())
                raf.readFully(data)
                descriptors.add(parseDescriptor(tag, data))
                pos += 16 + numBytesFollowing
            }
        }

        return VbmetaInfo(
            requiredLibavbVersion = "$versionMajor.$versionMinor",
            headerBlock = VBMETA_HEADER_SIZE,
            authBlock = authBlock,
            auxBlock = auxBlock,
            algorithmType = algorithmType,
            publicKeySha1 = publicKeySha1,
            publicKeyBlob = publicKeyBlob,
            rollbackIndex = rollbackIndex,
            flags = flags,
            rollbackIndexLocation = rollbackIndexLocation,
            releaseString = releaseString,
            publicKeyMetadataSize = publicKeyMetadataSize,
            descriptors = descriptors,
        )
    }

    private fun parseDescriptor(tag: Int, data: ByteArray): DescriptorInfo {
        return when (tag) {
            TAG_PROPERTY -> parseProperty(data)
            TAG_HASH -> parseHash(data)
            TAG_HASHTREE -> parseHashtree(data)
            TAG_KERNEL_CMDLINE -> parseKernelCmdline(data)
            TAG_CHAIN_PARTITION -> parseChainPartition(data)
            else -> DescriptorInfo.Unknown(tag)
        }
    }

    private fun parseProperty(data: ByteArray): DescriptorInfo {
        // key（utf8 以 0 结尾）+ value（utf8，余下全部）
        val keyEnd = data.indexOf(0).takeIf { it >= 0 } ?: data.size
        val key = String(data, 0, keyEnd, Charsets.UTF_8)
        val value = String(data, (keyEnd + 1).coerceAtMost(data.size), data.size - (keyEnd + 1).coerceAtMost(data.size), Charsets.UTF_8)
        return DescriptorInfo.Property(key, value)
    }

    private fun parseHash(data: ByteArray): DescriptorInfo {
        var off = 0
        val imageSize = readLong(data, off); off += 8
        val hashAlgorithm = readString(data, off, 32); off += 32
        val partitionName = readString(data, off, 32); off += 32
        val salt = data.copyOfRange(off, off + 32); off += 32
        val digest = data.copyOfRange(off, off + 32); off += 32
        val flags = readInt(data, off)
        return DescriptorInfo.Hash(imageSize, hashAlgorithm, partitionName, salt, digest, flags)
    }

    private fun parseHashtree(data: ByteArray): DescriptorInfo {
        var off = 0
        val dmVerityVersion = readInt(data, off); off += 4
        val imageSize = readLong(data, off); off += 8
        val treeOffset = readLong(data, off); off += 8
        val treeSize = readLong(data, off); off += 8
        val dataBlockSize = readInt(data, off); off += 4
        val hashBlockSize = readInt(data, off); off += 4
        val fecNumRoots = readInt(data, off); off += 4
        val fecOffset = readLong(data, off); off += 8
        val fecSize = readLong(data, off); off += 8
        val hashAlgorithm = readString(data, off, 32); off += 32
        val partitionName = readString(data, off, 32); off += 32
        val salt = data.copyOfRange(off, off + 32); off += 32
        val rootDigest = data.copyOfRange(off, off + 32); off += 32
        val flags = readInt(data, off)
        return DescriptorInfo.Hashtree(
            dmVerityVersion, imageSize, treeOffset, treeSize, dataBlockSize, hashBlockSize,
            fecNumRoots, fecOffset, fecSize, hashAlgorithm, partitionName, salt, rootDigest, flags,
        )
    }

    private fun parseKernelCmdline(data: ByteArray): DescriptorInfo {
        val flags = readInt(data, 0)
        val cmdline = String(data, 4, data.size - 4, Charsets.UTF_8).trimEnd('\u0000')
        return DescriptorInfo.KernelCmdline(flags, cmdline)
    }

    private fun parseChainPartition(data: ByteArray): DescriptorInfo {
        val rollbackIndexLocation = readInt(data, 0)
        val nameEnd = (4 until data.size).firstOrNull { data[it] == 0.toByte() } ?: data.size
        val partitionName = String(data, 4, nameEnd - 4, Charsets.UTF_8)
        val publicKey = data.copyOfRange((nameEnd + 1).coerceAtMost(data.size), data.size)
        return DescriptorInfo.ChainPartition(rollbackIndexLocation, partitionName, publicKey)
    }

    /** 按用户指定的格式输出完整信息。 */
    fun format(data: InfoImageData, showAtx: Boolean = false): String {
        val sb = StringBuilder()
        if (data.hasFooter) {
            val f = data.footer ?: return sb.toString()
            sb.appendLine("Footer version: ${f.version}")
            sb.appendLine("Image size: ${f.originalImageSize + 0} bytes")
            sb.appendLine("Original image size: ${f.originalImageSize} bytes")
            sb.appendLine("VBMeta offset: ${f.vbmetaOffset}")
            sb.appendLine("VBMeta size: ${f.vbmetaSize} bytes")
        } else {
            sb.appendLine("未检测到 AVB footer（镜像末尾无 'AVBf' magic）")
            return sb.toString()
        }

        val v = data.vbmeta ?: run {
            sb.appendLine("---")
            sb.appendLine("未找到有效的 VBMeta（magic 'AVB0'）")
            return sb.toString()
        }

        sb.appendLine("---")
        sb.appendLine("Minimum libavb version: ${v.requiredLibavbVersion}")
        sb.appendLine("Header Block: ${v.headerBlock} bytes")
        sb.appendLine("Authentication Block: ${v.authBlock} bytes")
        sb.appendLine("Auxiliary Block: ${v.auxBlock} bytes")
        sb.appendLine("Public key (sha1): ${v.publicKeySha1 ?: "（无公钥）"}")
        sb.appendLine("Algorithm: ${v.algorithmName}")
        sb.appendLine("Rollback Index: ${v.rollbackIndex}")
        sb.appendLine("Flags: ${v.flags}")
        sb.appendLine("Rollback Index Location: ${v.rollbackIndexLocation}")
        sb.appendLine("Release String: '${v.releaseString}'")
        if (showAtx && v.publicKeyMetadataSize > 0) {
            sb.appendLine("avb_cert: public key metadata ${v.publicKeyMetadataSize} bytes")
        }

        if (v.descriptors.isEmpty()) {
            sb.appendLine("Descriptors: （无）")
            return sb.toString()
        }
        sb.appendLine("Descriptors:")
        v.descriptors.forEach { d ->
            when (d) {
                is DescriptorInfo.Hash -> {
                    sb.appendLine("    Hash descriptor:")
                    sb.appendLine("        Image Size: ${d.imageSize} bytes")
                    sb.appendLine("        Hash Algorithm: ${d.hashAlgorithm}")
                    sb.appendLine("        Partition Name: ${d.partitionName}")
                    sb.appendLine("        Salt: ${d.salt.toHex()}")
                    sb.appendLine("        Digest: ${d.digest.toHex()}")
                    sb.appendLine("        Flags: ${d.flags}")
                }

                is DescriptorInfo.Hashtree -> {
                    sb.appendLine("    Hashtree descriptor:")
                    sb.appendLine("        Version of dm-verity: ${d.dmVerityVersion}")
                    sb.appendLine("        Image Size: ${d.imageSize} bytes")
                    sb.appendLine("        Tree Offset: ${d.treeOffset}")
                    sb.appendLine("        Tree Size: ${d.treeSize} bytes")
                    sb.appendLine("        Data Block Size: ${d.dataBlockSize} bytes")
                    sb.appendLine("        Hash Block Size: ${d.hashBlockSize} bytes")
                    sb.appendLine("        FEC num roots: ${d.fecNumRoots}")
                    sb.appendLine("        FEC offset: ${d.fecOffset}")
                    sb.appendLine("        FEC size: ${d.fecSize} bytes")
                    sb.appendLine("        Hash Algorithm: ${d.hashAlgorithm}")
                    sb.appendLine("        Partition Name: ${d.partitionName}")
                    sb.appendLine("        Salt: ${d.salt.toHex()}")
                    sb.appendLine("        Root Digest: ${d.rootDigest.toHex()}")
                    sb.appendLine("        Flags: ${d.flags}")
                }

                is DescriptorInfo.Property -> sb.appendLine("    Prop: ${d.key} -> '${d.value}'")

                is DescriptorInfo.KernelCmdline -> sb.appendLine("    Kernel Cmdline: '${d.cmdline}'")

                is DescriptorInfo.ChainPartition -> {
                    sb.appendLine("    Chain Partition descriptor:")
                    sb.appendLine("        Partition Name: ${d.partitionName}")
                    sb.appendLine("        Rollback Index Location: ${d.rollbackIndexLocation}")
                    sb.appendLine("        Public key (sha1): ${MessageDigest.getInstance("SHA-1").digest(d.publicKey).joinToString("") { "%02x".format(it) }}")
                }

                is DescriptorInfo.Unknown -> sb.appendLine("    Unknown descriptor (tag ${d.tag})")
            }
        }
        return sb.toString()
    }

    private fun readInt(data: ByteArray, off: Int): Int =
        ((data[off].toInt() and 0xFF) shl 24) or ((data[off + 1].toInt() and 0xFF) shl 16) or
            ((data[off + 2].toInt() and 0xFF) shl 8) or (data[off + 3].toInt() and 0xFF)

    private fun readLong(data: ByteArray, off: Int): Long =
        ((data[off].toLong() and 0xFF) shl 56) or ((data[off + 1].toLong() and 0xFF) shl 48) or
            ((data[off + 2].toLong() and 0xFF) shl 40) or ((data[off + 3].toLong() and 0xFF) shl 32) or
            ((data[off + 4].toLong() and 0xFF) shl 24) or ((data[off + 5].toLong() and 0xFF) shl 16) or
            ((data[off + 6].toLong() and 0xFF) shl 8) or (data[off + 7].toLong() and 0xFF)

    private fun readString(data: ByteArray, off: Int, maxLen: Int): String {
        val end = minOf(off + maxLen, data.size)
        val raw = data.copyOfRange(off, end)
        return raw.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}