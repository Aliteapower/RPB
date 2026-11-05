# 商户员工取酒卡片照片发布（2026-11-02）

项目D:/RPB，分支codex/paynow-payment-product-line-staging。沿用本聊天存酒体验优化的提交、推送、生产发布授权。功能及生产前端 `e6085c85ffca9341132a98a1a81210da3cffc192`；后端保持0559e4ce。

## 改动

商户、员工“取酒 → 选择要取的酒”卡片复用StorageItemPhotos，展示原申请、收存及该物品后续补充照片，支持点击预览、放大/适配、原图和关闭。没有照片不占图库位置。只展示选中顾客的可领取物品；点击图片不选择领取、不改数量、不触发登记或交付。取酒确认、库存扣减和原单权限规则不变。

仅改MerchantStorageWithdrawal.vue及对应行为测试，无API、数据库、迁移、后端或权限配置变更。顾客端和商户历史照片组件沿用上一轮结果。

## 验证

- 新增回归先失败、实现后通过；最终干净归档121项相关前端测试通过，覆盖所选顾客照片、排除其他顾客、无图、收存/补充图、放大关闭及看图无POST。类型检查和完整构建通过；4146个Git blob核对一致，排除原有无关工作区改动。
- 浏览器只读合成数据展示四张取酒卡片，第一张两幅PNG加载成功，其他无图卡不留空图库。验证放大、关闭；1280、390、320视口下实际clientWidth/scrollWidth分别1266、375、305，两者一致，无横向溢出。桌面及完整页面PNG已保存；窄屏为DOM/交互验收，不声称已保存窄屏PNG。合成照片及身份不是生产顾客数据，没有办理取酒或拍照上传。
- 生产444个文件逐字节匹配；5域名85次入口HTML、5次版本及TLS核查通过；7项匿名私有接口401，包括员工和顾客照片地址。没有生产真实登录私有页面交互、实体手机或微信验收。

## 发布与回退

仅前端发布，未重启后端。后端jar仍为 `2c7dc912aefd66621459956f0c1ca32c0d01eb7bc7bcdfb78e6050239681dd36`；MainPID331593、InvocationID c52a4a02d5bb4893a57226669f07d976、active、NRestarts0均保持。未执行生产业务SQL或改动真实照片、订阅、资金、存酒记录。末次可用空间约815MiB，未清理旧备份。

备份 `/opt/rpb/backups/20261102-merchant-pickup-photos-e6085c85-before`。回退只恢复其中index.html、frontend-version.json及FRONTEND_RELEASE_SHA；旧静态资产保留，不需要后端重启或数据库恢复。

证据：`target/merchant-pickup-photos-release-20261102/` 内source-verification、artifacts、tests、build、visual-result、PNG、deploy和public-result。合成服务已停止、标签关闭、视口恢复。原有无关改动与资料保留。
