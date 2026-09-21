# KernelSU / 自编译内核自签

为自编译内核或 KernelSU 构建的 boot 镜像添加 AVB 签名，并在解锁引导加载器的设备上刷入。

## 前置条件

- 设备已解锁 bootloader（`fastboot flashing unlock`）
- 拥有 boot.img（以及 vendor_boot.img / init_boot.img，视设备而定）
- 私钥（可在「密钥」页生成，建议 RSA 4096）

## 步骤

1. **生成密钥**：进入「密钥」页，生成一把 RSA 4096 密钥（如 `avb-key`）。私钥保存在应用内，请妥善保管——丢失后无法再签发新镜像。
2. **导入镜像**：进入「工坊」，点击右上角 + 选择 boot.img、vendor_boot.img、init_boot.img（可多选）。应用会自动识别分区类型。
3. **配置签名**：
   - 选中镜像卡片，模式选「哈希签名」
   - 密钥选刚才生成的 `avb-key`
   - 算法建议 `SHA256_RSA4096`
   - 分区大小（partition_size）通常取原镜像大小向上取整到 4K；若刷写时报错，可改为设备分区实际大小（`fastboot getvar partition-size:boot`）
4. **执行**：点击「运行」，等待完成。成功后状态变为绿色。
5. **生成禁验 vbmeta**：在「工坊」点击「生成禁验 vbmeta」按钮，产出 `vbmeta-disable.img`（flags=2，跳过验证）。
6. **导出**：点任务卡「导出」，将签名后的 boot 镜像与 vbmeta 存到设备存储。

## 刷入（fastboot）

```sh
fastboot flash boot boot-signed.img
fastboot flash vendor_boot vendor_boot-signed.img   # 如设备有
fastboot flash init_boot init_boot-signed.img        # 如设备有
fastboot flash vbmeta vbmeta-disable.img
fastboot reboot
```

## 说明

- 禁验 vbmeta 会关闭全分区验证，属于刷机玩家的常见操作；如需恢复官方验证，请刷回官方 vbmeta（从官方出厂镜像提取）。
- 签名仅影响验证链，不会修改 boot 内容本身。
