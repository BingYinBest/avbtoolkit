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
