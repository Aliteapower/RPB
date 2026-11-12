# 统一充值积分入口及顾客积分展示生产发布

2026-10-23，D:\RPB。用户明确授权“提交部署”。

## Production Readiness Report

- 范围：顾客首页积分余额/最近收支/完整历史，商户统一充值与积分选项、同店切换保留顾客、旧积分地址兼容。只涉及前端与测试，无后端、迁移、环境变量或权限变更。
- 同代码77项钱包前端测试、最终类型检查及Vite构建通过；隔离浏览器完成统一入口加30/兑换扣20、顾客可见及切回钱包保留顾客，资金不变。详见WALLET_UNIFIED_RECHARGE_POINTS_20261023.md。
- 生产RPB /opt/rpb，rpb-backend 8080，rpb_acceptance；后端提交379b6d419f8050e4b8c79d7869c9bd5c58e4f0a0、jar a99fd91796fa3b3a4ece0af1ae866b5f8c764a1346f7ff1fa4151e01d5bbbfc8，PID843251、InvocationID b297c9030cb64029b8b0f750af7096f9，V062/62项/0失败。
- 发布前只读基线：钱包2账户合计93000分=本金93000+赠送0，积分1账户合计1000。只核对真实现状，不改动商户已经开启的配置、积分及历史。
- 指定文件提交推送，从干净归档构建前端；不替换或重启后端，不执行迁移。备份完整旧前端、版本和数据库；发布目录不可变，新资源先复制，index.html原子替换并保留旧资源。
- 发布后逐字节核对公网HTML与静态资源，检查既有公开接口、业务指纹、后端jar/SHA、Flyway及所有服务PID/InvocationID不变。

阻塞项：无；干净构建及实际发布验证通过前不宣称发布完成。npm既有依赖告警不在本次变更范围。

回退：仅恢复本次备份的旧index.html和前端版本元数据，保留新旧静态资源，不回滚业务数据库、不切换后端。真实业务在发布期间仍可继续，数据指纹变化须只读核对，不能为回退前端覆盖业务快照。

决定：GO，执行已授权前端发布，实际结果随后追加。


## 实际发布结果

2026-10-23完成前端发布。前端运行版本 `205bc959b9f392b98a9754a4b62c237d42b24de0` 已推送至 `codex/paynow-payment-product-line-staging`；后续提交仅补充文档。后端仍运行 `379b6d419f8050e4b8c79d7869c9bd5c58e4f0a0`，未更换jar或重启。

### 制品及回退保护

- 干净Git归档2260个文件逐个与提交blob一致；npm ci、Vue类型检查、Vite生产构建通过。复用同实现77项前端测试及已完成的隔离浏览器流程验证。
- 前端压缩包SHA256 `45d1cd5dde9e38f6e112cf8dc5d85a8d686db13b6373d2337f7ccde167bdf016`，上传校验通过。发布目录 `/opt/rpb/releases/20261023-wallet-unified-205bc959`。
- 备份目录 `/opt/rpb/backups/20261023-wallet-unified-205bc959-before` 包含完整旧前端、后端版本和数据库dump（905970字节，pg_restore目录可读）。旧资源保留，index.html原子替换；前端版本记录在 `/opt/rpb/app/FRONTEND_RELEASE_SHA`，后端RELEASE_SHA保持。
- 无迁移、配置、权限或依赖变更，未触发回退。npm ci仍有7项既有high告警，本次未扩大范围更新依赖。

### 线上核对

- 4个公网域名HTML、185个静态资源与构建逐字节一致；2家门店管理入口匿名仍401；9项既有公开接口状态及JSON语义完全一致。
- 营销/积分/顾客申请接口15项匿名及跨站保护检查通过。
- 110张业务/配置表在发布前、发布后和最终只读核对的指纹一致（排除正常会话、验证码、审计、限流、Flyway及账号登录时刻等运行字段）。钱包2账户合计93000分=本金93000+赠送0；积分1账户合计1000，保持用户现有积分数据和配置。
- Flyway仍为V062、62项、0失败。rpb-backend PID843251、InvocationID b297c9030cb64029b8b0f750af7096f9、NRestarts0，jar哈希仍a99fd91796fa3b3a4ece0af1ae866b5f8c764a1346f7ff1fa4151e01d5bbbfc8。后端、nginx、PostgreSQL、Redis、Docker的PID/InvocationID/状态/重启数与发布前一致。
- 最终观察最近5分钟后端ERROR为0；未进行生产充值、积分增减、兑换或其他业务写入。新增交互的功能流程证据来自发布前本地隔离浏览器，生产采用静态资源、匿名权限和真实数据只读核对。

### 证据及保留文件

D:\RPB\target的wallet-unified前缀保留源码blob证明、干净构建、制品哈希、发布日志、公开接口/静态资源/权限证明及最终生产核对。原PRODUCT_LINE_ACCESS_BROWSER_ACCEPTANCE_20261022.md、WALLET_QR_END_TO_END_ACCEPTANCE_20261023.md和rpb信息.txt继续保留，未提交或上传。

结论：已提交、推送并完成前端部署；服务无重启、业务数据不变，顾客积分展示和商户统一入口已随新前端发布。
