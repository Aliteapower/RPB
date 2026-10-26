# 商品条码操作优化生产发布 — 2026-10-26

## 版本及范围
前后端线上功能版本 c7cdf526bd27c042c3cafe8d35c4892e3e705c98，已推送 origin/codex/paynow-payment-product-line-staging。
以已发布顾客卡二维码版本 3f78ebc5bc0dc29803b17e8ab3802f411402b243 为基线，保留该版本及其验收文档。
本次只提交 9 个条码相关文件；原有 12 项无关改动按 SHA-256 保留。

上线：扫码填入可编辑草稿，核对后保存；已存条码原子替换；条码信息归入当前商品明细；照片为折叠可选辅助，不影响保存；顾客扫描入口一次点击即请求摄像头并并行加载解码器。

## 制品、数据库和回退
从功能提交的 git archive 干净源码构建。发布目录 /opt/rpb/releases/20261026-barcode-usability-c7cdf526；服务 rpb-backend；数据库 rpb_acceptance。
无新迁移，Flyway 保持 079|79|0。新旧 JAR 仅 CatalogBarcodeService.class 及其 Write 请求类型不同，其余业务代码、库、配置、历史迁移字节一致。
备份 /opt/rpb/backups/20261026-barcode-usability-c7cdf526-before：旧 JAR、前端及版本标记、完整数据库 dump（12,457,823 字节）。备份在私有临时库完整恢复验证通过；沿用已验证的 public,pg_catalog 会话搜索路径恢复旧钱包校验函数。临时库已移除，无生产迁移或函数改写。
回退时恢复该备份的应用和前端，保留全部现有数据库数据，不恢复旧库覆盖新交易。

## 验证
- 10 项条码/权限后端测试通过，包含原子替换、冲突保留原码、旧请求 JSON 及幂等兼容。
- 干净源码 262 项前端组合测试通过，包含钱包、卡二维码、存酒、条码；Maven package、vue-tsc/Vite 构建通过。
- 登录态 23 项生产接口检查通过：条码读取、替换参数 400、已失效原码 409、顾客分页/条码查询、钱包、存酒、持卡与历史卡二维码 completed 状态。负向替换使用已确认不存在的条码，未写商品映射或命令分录。
- 既有 QA 钱包余额 3,800 分、原卡剩余 28 次及卡二维码验收卡剩余 2 次不变。没有新建卡、出码或执行核销/支付/存酒扣减。
- 148 项业务表/配置指纹发布前后及二次复核均一致，包含既有条码表与 QA 记录；会话、审计等易变表依原规则排除，不宣称全库逐字节一致。
- 四个生产域名共 32 个页面入口与新 index 一致，18 个资源内容校验一致；44 项匿名鉴权检查均返回 401，包含条码写入、卡出码与核销入口。
- rpb-backend active；PID 1787161、InvocationID fa054f4ed27242bdba5eee114068564c 在观察及二次复核期间稳定，无新增 ERROR。nginx、PostgreSQL、Redis、Docker 状态及进程身份保持不变。

## 限制及证据
390px 中英文页面和无需照片保存已在本地模拟验证。真实 iPhone/微信摄像头、对焦、照片兼容性仍待设备验收。
本地证据 target/catalog-barcode-usability-release-20261026/；服务器 /home/ubuntu/catalog-barcode-usability-release-20261026/。后续文档提交不改变线上功能制品版本。
Production readiness: GO，已部署。
