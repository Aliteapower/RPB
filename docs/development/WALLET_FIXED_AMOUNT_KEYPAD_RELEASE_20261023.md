# 钱包固定金额键盘前端发布

2026-10-23，D:\RPB，用户明确授权提交部署。

## 范围及就绪结论

仅前端：钱包消费收款页常驻数字键盘、金额显示、00、退格、清空及快捷金额，复用 payments 本机门店快捷配置，编辑在页内完成。扫码、提交和结果未明时锁定输入，扣款、授权、幂等和原单恢复逻辑不变。

GO：118 项钱包前端回归及最终组件 47 项通过；Vue 类型检查、Vite 构建和本地浏览器检查通过，证据见 WALLET_FIXED_AMOUNT_KEYPAD_ACCEPTANCE_20261023.md。未改 Java、API、权限、环境变量、依赖或数据库，无迁移；不重新运行无变更的后端测试。

## 发布与回退

目标 ubuntu@43.134.69.75，/opt/rpb/frontend；原前后端运行 SHA cb400bf7。仅替换前端页面与静态资源、更新 FRONTEND_RELEASE_SHA，后端 RELEASE_SHA、jar、进程及数据库保持原状，不重启服务。

持 RPB 发布锁，核验预期原版本、index 和上传哈希；备份完整前端和原 FRONTEND_RELEASE_SHA，资源先复制，index 原子替换，验证公网 HTML、资源、权限和服务进程。失败恢复备份前端及前端版本，不恢复数据库。原旧静态资源保留，避免已打开页面加载失败。只提交本轮文件，无关改动和私密材料不纳入制品。

## 风险

真实手机触控、相册及相机尚未重测，生产不代真实顾客收款。快捷金额沿用本机门店配置，不跨设备同步。

## 实际结果

代码提交 `52ace1a0e900c525ce3275ff86621879ce7d1d72` 已推送至 codex/paynow-payment-product-line-staging；生产 FRONTEND_RELEASE_SHA 已为该 SHA。后端 RELEASE_SHA 仍为 `cb400bf7de938d5e85733b513a05353fa565ed78`，符合本轮仅前端发布范围。

- 干净 Git 归档 npm ci、Vue 类型检查及 Vite 构建通过；复用本轮同代码的 118 项前端测试和本地浏览器证据。npm 报告 7 项既有 high 告警，本轮未更新依赖。
- 前端包 SHA256 `ba2d3adf3f26d384f1ae612dba5b35186decf87a0803502a02ed3ffacfb64d3c`，上传与展开前核验一致。
- 发布目录 `/opt/rpb/releases/20261023-wallet-keypad-52ace1a0`；备份 `/opt/rpb/backups/20261023-wallet-keypad-52ace1a0-before`，完整原前端和原 FRONTEND_RELEASE_SHA 已核验可读。旧 assets 保留，没有回退。
- 4 个公网域名 HTML 和全部 184 个静态资源与本轮构建逐字节相同，2 项管理入口匿名拒绝，原订位、支付、日账本、身份等 9 项接口状态检查通过。
- 后端 jar SHA256 仍为 `40b9fccd3358e8f86a177a645f89dceaa5b34dbca6c2e2cc6b4ddaf177d625fa`；PID927854、InvocationID `2dd3d1569e9c494a9cc1af42dd3b0346`、NRestarts0 均保持不变。RPB、nginx、PostgreSQL、Redis、Docker 的进程／InvocationID／状态／重启数前后及最终一致。
- 未执行数据库迁移、配置修改或真实收款；未替顾客授权或扫码付款。真实手机触控和相机仍待人工验证，生产登录后业务交互不在本次发布验证范围。

制品、部署日志和公网核验证据在 D:\RPB\target\wallet-keypad-*；原接口检查在 target/wallet-personal-public-keypad-after*。无关工作区改动保留。发布记录的后续文档提交不改变运行制品。
