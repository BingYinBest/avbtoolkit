package dev.vbmeta.studio.engine

/**
 * avbtool 子命令封装。参数与 AOSP external/avb avbtool.py 一一对应。
 * 所有 --image 操作都是原地修改，调用方需先在工作目录复制镜像。
 */
class AvbTool(private val toolchain: ToolchainManager) {

    data class HashFooterParams(
        val imagePath: String,
        val partitionSize: Long,
        val partitionName: String,
        val keyPath: String?,
        val algorithm: String,
        val rollbackIndex: Long = 0,
        val hashAlgorithm: String = "sha256",
        val salt: String? = null,
    )

    data class HashtreeFooterParams(
        val imagePath: String,
        val partitionSize: Long,
        val partitionName: String,
        val keyPath: String?,
        val algorithm: String,
        val rollbackIndex: Long = 0,
        val hashAlgorithm: String = "sha256",
        /** null=使用 avbtool 默认(2)；0=不生成 FEC（--do_not_generate_fec） */
        val fecNumRoots: Int? = null,
        val salt: String? = null,
    )

    data class VbmetaParams(
        val output: String,
        val keyPath: String?,
        val algorithm: String,
        val rollbackIndex: Long = 0,
        val flags: Int = 0,
        val paddingSize: Int = 4096,
        /** (分区名, rollback 位置, AVB 公钥路径) */
        val chainPartitions: List<Triple<String, Long, String>> = emptyList(),
        /** 从中提取 descriptor 的镜像路径列表 */
        val includeDescriptorsFromImages: List<String> = emptyList(),
    )

    data class ImageInfo(
        val hasFooter: Boolean,
        val footerVersion: String? = null,
        val imageSize: Long? = null,
        val originalImageSize: Long? = null,
        val vbmetaOffset: Long? = null,
        val vbmetaSize: Long? = null,
        val algorithm: String? = null,
        val rollbackIndex: Long? = null,
        val flags: Int? = null,
        val releaseString: String? = null,
        /** 简化的 descriptor 段落标题列表 */
        val descriptors: List<String> = emptyList(),
    )

    private fun base(): List<String> = listOf(toolchain.paths().python, toolchain.paths().avbtool)

    private fun env() = toolchain.env()

    suspend fun addHashFooter(
        params: HashFooterParams,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("add_hash_footer")
            add("--image"); add(params.imagePath)
            add("--partition_size"); add(params.partitionSize.toString())
            add("--partition_name"); add(params.partitionName)
            add("--hash_algorithm"); add(params.hashAlgorithm)
            add("--algorithm"); add(params.algorithm)
            add("--rollback_index"); add(params.rollbackIndex.toString())
            params.keyPath?.let { add("--key"); add(it) }
            params.salt?.let { add("--salt"); add(it) }
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    suspend fun addHashtreeFooter(
        params: HashtreeFooterParams,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("add_hashtree_footer")
            add("--image"); add(params.imagePath)
            add("--partition_size"); add(params.partitionSize.toString())
            add("--partition_name"); add(params.partitionName)
            add("--hash_algorithm"); add(params.hashAlgorithm)
            add("--algorithm"); add(params.algorithm)
            add("--rollback_index"); add(params.rollbackIndex.toString())
            when (params.fecNumRoots) {
                null -> {}
                0 -> add("--do_not_generate_fec")
                else -> {
                    add("--fec_num_roots")
                    add(params.fecNumRoots.toString())
                }
            }
            params.keyPath?.let { add("--key"); add(it) }
            params.salt?.let { add("--salt"); add(it) }
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    suspend fun makeVbmetaImage(
        params: VbmetaParams,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("make_vbmeta_image")
            add("--output"); add(params.output)
            add("--algorithm"); add(params.algorithm)
            add("--rollback_index"); add(params.rollbackIndex.toString())
            add("--flags"); add(params.flags.toString())
            if (params.paddingSize > 0) {
                add("--padding_size"); add(params.paddingSize.toString())
            }
            params.keyPath?.let { add("--key"); add(it) }
            params.chainPartitions.forEach { (name, rollback, keyPath) ->
                add("--chain_partition"); add("$name:$rollback:$keyPath")
            }
            params.includeDescriptorsFromImages.forEach { image ->
                add("--include_descriptors_from_image"); add(image)
            }
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    /** 校验镜像 AVB 状态。exit==0 且无 error 视为通过。 */
    suspend fun verifyImage(
        imagePath: String,
        keyPath: String? = null,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("verify_image")
            add("--image"); add(imagePath)
            keyPath?.let { add("--key"); add(it) }
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    /** 解析镜像 AVB 信息；无 footer/解析失败时 hasFooter=false。 */
    suspend fun infoImage(imagePath: String): ImageInfo {
        val sb = StringBuilder()
        val exit = CommandRunner.run(
            buildList {
                addAll(base())
                add("info_image")
                add("--image"); add(imagePath)
            },
            env(),
            onLine = { sb.appendLine(it) },
        )
        if (exit != 0) return ImageInfo(hasFooter = false)
        return parseInfo(sb.toString())
    }

    /** 提取 AVB 格式公钥（用于 --chain_partition 场景）。 */
    suspend fun extractPublicKey(
        keyPath: String,
        outputPath: String,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("extract_public_key")
            add("--key"); add(keyPath)
            add("--output"); add(outputPath)
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    /** 打印分区摘要（vbmeta digest 校验用）。 */
    suspend fun printPartitionDigests(
        imagePath: String,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("print_partition_digests")
            add("--image"); add(imagePath)
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    /** 把 vbmeta 追加到分区镜像尾部。 */
    suspend fun appendVbmetaImage(
        imagePath: String,
        partitionSize: Long,
        vbmetaImagePath: String,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("append_vbmeta_image")
            add("--image"); add(imagePath)
            add("--partition_size"); add(partitionSize.toString())
            add("--vbmeta_image"); add(vbmetaImagePath)
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    /** 擦除镜像上的 AVB footer（可选保留哈希树）。 */
    suspend fun eraseFooter(
        imagePath: String,
        keepHashtree: Boolean = false,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("erase_footer")
            add("--image"); add(imagePath)
            if (keepHashtree) add("--keep_hashtree")
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    /** 清零镜像中的哈希树与 FEC 数据（保留 footer）。 */
    suspend fun zeroHashtree(
        imagePath: String,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("zero_hashtree")
            add("--image"); add(imagePath)
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    /** 从带 footer 的镜像中提取 vbmeta 到新文件。 */
    suspend fun extractVbmetaImage(
        imagePath: String,
        outputPath: String,
        paddingSize: Int? = null,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("extract_vbmeta_image")
            add("--image"); add(imagePath)
            add("--output"); add(outputPath)
            paddingSize?.let { add("--padding_size"); add(it.toString()) }
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    /** 调整带 footer 镜像的目标分区大小。 */
    suspend fun resizeImage(
        imagePath: String,
        partitionSize: Long,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("resize_image")
            add("--image"); add(imagePath)
            add("--partition_size"); add(partitionSize.toString())
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    /** 提取公钥 SHA-256 摘要。 */
    suspend fun extractPublicKeyDigest(
        keyPath: String,
        outputPath: String,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("extract_public_key_digest")
            add("--key"); add(keyPath)
            add("--output"); add(outputPath)
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    /** 计算 vbmeta 摘要（多镜像链校验）。 */
    suspend fun calculateVbmetaDigest(
        imagePath: String,
        outputPath: String? = null,
        hashAlgorithm: String = "sha256",
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("calculate_vbmeta_digest")
            add("--image"); add(imagePath)
            add("--hash_algorithm"); add(hashAlgorithm)
            outputPath?.let { add("--output"); add(it) }
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    /** 生成 dm-verity 内核命令行。 */
    suspend fun calculateKernelCmdline(
        imagePath: String,
        outputPath: String? = null,
        hashtreeDisabled: Boolean = false,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("calculate_kernel_cmdline")
            add("--image"); add(imagePath)
            if (hashtreeDisabled) add("--hashtree_disabled")
            outputPath?.let { add("--output"); add(it) }
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    /** 设置 A/B 槽位元数据。 */
    suspend fun setAbMetadata(
        miscImagePath: String,
        slotData: String,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("set_ab_metadata")
            add("--misc_image"); add(miscImagePath)
            add("--slot_data"); add(slotData)
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    /** 创建 ATX 证书（avb_cert 扩展）。 */
    suspend fun makeCertificate(
        outputPath: String,
        subject: String,
        subjectKeyPath: String,
        subjectKeyVersion: Long = 1,
        subjectIsIntermediateAuthority: Boolean = false,
        usage: List<String> = emptyList(),
        usageForUnlock: Boolean = false,
        authorityKeyPath: String? = null,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("make_certificate")
            add("--output"); add(outputPath)
            add("--subject"); add(subject)
            add("--subject_key"); add(subjectKeyPath)
            add("--subject_key_version"); add(subjectKeyVersion.toString())
            if (subjectIsIntermediateAuthority) add("--subject_is_intermediate_authority")
            usage.forEach { add("--usage"); add(it) }
            if (usageForUnlock) add("--usage_for_unlock")
            authorityKeyPath?.let { add("--authority_key"); add(it) }
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    /** 创建 ATX 设备永久属性。 */
    suspend fun makeCertPermanentAttributes(
        outputPath: String,
        rootAuthorityKeyPath: String,
        productId: ByteArray? = null,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("make_cert_permanent_attributes")
            add("--output"); add(outputPath)
            add("--root_authority_key"); add(rootAuthorityKeyPath)
            productId?.let {
                val hex = it.joinToString("") { "%02x".format(it) }
                add("--product_id"); add(hex)
            }
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    /** 创建 ATX 证书元数据。 */
    suspend fun makeCertMetadata(
        outputPath: String,
        intermediateKeyCertificatePath: String,
        productKeyCertificatePath: String,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("make_cert_metadata")
            add("--output"); add(outputPath)
            add("--intermediate_key_certificate"); add(intermediateKeyCertificatePath)
            add("--product_key_certificate"); add(productKeyCertificatePath)
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    /** 创建 ATX 解锁凭证。 */
    suspend fun makeCertUnlockCredential(
        outputPath: String,
        intermediateKeyCertificatePath: String,
        unlockKeyCertificatePath: String,
        challenge: String,
        unlockKeyPath: String,
        onLine: (String) -> Unit = {},
    ): Int {
        val cmd = buildList {
            addAll(base())
            add("make_cert_unlock_credential")
            add("--output"); add(outputPath)
            add("--intermediate_key_certificate"); add(intermediateKeyCertificatePath)
            add("--unlock_key_certificate"); add(unlockKeyCertificatePath)
            add("--challenge"); add(challenge)
            add("--unlock_key"); add(unlockKeyPath)
        }
        return CommandRunner.run(cmd, env(), onLine)
    }

    companion object {
        private val DESCRIPTOR_HEADS = listOf(
            "Hash descriptor:",
            "Hashtree descriptor:",
            "Chain Partition descriptor:",
            "Property descriptor:",
            "Kernel Cmdline descriptor:",
        ).joinToString("|")

        internal fun parseInfo(text: String): ImageInfo {
            var footerVersion: String? = null
            var imageSize: Long? = null
            var originalImageSize: Long? = null
            var vbmetaOffset: Long? = null
            var vbmetaSize: Long? = null
            var algorithm: String? = null
            var rollbackIndex: Long? = null
            var flags: Int? = null
            var releaseString: String? = null
            val descriptors = mutableListOf<String>()

            for (line in text.lineSequence()) {
                // 主字段均为顶格行；descriptor 内部字段有前导空格，避免误覆盖
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    val head = DESCRIPTOR_HEADS.split("|").firstOrNull { line.trim().startsWith(it) }
                    if (head != null) {
                        descriptors.add(head.removeSuffix(":"))
                    }
                    continue
                }
                when {
                    line.startsWith("Footer version:") -> footerVersion = line.substringAfter(':').trim()
                    line.startsWith("Image size:") -> imageSize = line.substringAfter(':').trim().removeSuffix(" bytes").trim().toLongOrNull()
                    line.startsWith("Original image size:") -> originalImageSize = line.substringAfter(':').trim().removeSuffix(" bytes").trim().toLongOrNull()
                    line.startsWith("VBMeta offset:") -> vbmetaOffset = line.substringAfter(':').trim().toLongOrNull()
                    line.startsWith("VBMeta size:") -> vbmetaSize = line.substringAfter(':').trim().toLongOrNull()
                    line.startsWith("Algorithm:") -> algorithm = line.substringAfter(':').trim()
                    line.startsWith("Rollback Index:") -> rollbackIndex = line.substringAfter(':').trim().toLongOrNull()
                    line.startsWith("Flags:") -> flags = line.substringAfter(':').trim().toIntOrNull()
                    line.startsWith("Release String:") -> releaseString = line.substringAfter(':').trim()
                }
            }

            return ImageInfo(
                hasFooter = footerVersion != null,
                footerVersion = footerVersion,
                imageSize = imageSize,
                originalImageSize = originalImageSize,
                vbmetaOffset = vbmetaOffset,
                vbmetaSize = vbmetaSize,
                algorithm = algorithm,
                rollbackIndex = rollbackIndex,
                flags = flags,
                releaseString = releaseString,
                descriptors = descriptors,
            )
        }
    }
}
