# GSI / 厂商镜像签名

为 system 等文件系统镜像添加 dm-verity 哈希树（hashtree）与 FEC 纠错数据，并生成对应 vbmeta。

## 适用场景

- 刷入 GSI（Generic System Image）时让 dm-verity 正常工作
- 修改过 system/vendor/product 分区内容后重新启用验证
- 需要 FEC（前向纠错）抵御静默位翻转的镜像

## 步骤

1. **生成/选择密钥**（同 KSU 流程，可在「密钥」页生成）。
2. **导入镜像**：在「工坊」导入 system.img（或 vendor.img、product.img）。
3. **配置**：
   - 模式选「哈希树签名」
   - 密钥、算法同上
   - FEC Roots 默认 2（写入约 1/127 的纠错数据）
   - 分区名自动识别为 system；分区大小若不合适可手动填写
4. **运行**：执行会依次计算哈希树 → 生成 FEC → 写入 footer 并签名。镜像越大耗时越长，请保持应用在前台。
5. **验证**：任务卡「导出」前，可把模式改为「验证」重新跑一次确认签名有效。

## 刷入

```sh
fastboot flash system system-signed.img
fastboot flash vbmeta vbmeta.img   # 引用 system 的 vbmeta
fastboot reboot
```

## 提示

- 哈希树与 FEC 会占用分区尾部空间，`--partition_size` 必须 ≥ 镜像大小 + 树 + FEC + footer；填错会报 "Need N bytes of free space" 类错误。
- 未使用 FEC 时可在配置中把 FEC Roots 设为 0（不生成 FEC）。
