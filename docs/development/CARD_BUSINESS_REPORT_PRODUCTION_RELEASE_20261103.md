# 卡业务报表生产发布（2026-11-03）

## 结果 / 范围

功能提交`3abca5b1431ec800e573f9b214f9c84afdd87a35`已推送origin/codex/paynow-payment-product-line-staging，前后端生产标记均为此版本。仅D:/RPB卡业务报表相关22个文件纳入功能提交；39个既有无关修改及未跟踪文件发布前后逐一核对原始字节，不回滚、不清理、不纳入提交。

## 制品 / 验证

干净Git归档4,231个blob逐一匹配。独立npm ci、44项卡业务前端测试、vue-tsc和Vite构建通过，后端完整Maven package通过。复用同一业务源码已通过的421项相邻前端、45项后端回归及最终10项报表专项，不重复声称这些测试在生产执行。

在生产基包上仅替换/增加干净构建的16个相关类，与最终已测试class逐字节一致。3,064个无关条目保持原字节，包含全部历史SQL、配置和依赖；JAR工具模式list-layers通过。增量传输约1.15MB的变更及头部字节，在服务器精确重建93,906,439字节JAR，SHA256为`ab0703a168a2a13e71a4306e6c96460ef960a0c07b55f0b583d90e471c9f8c65`。

线上453个静态文件与制品哈希一致；5个域名完成115项页面、5项版本/TLS、15项匿名401保护及10项指南PDF检查。13项生产QA认证检查通过，包含20条业务记录的分页、全量汇总、顾客/方案/业务筛选、按顾客分组、16条额度过期记录分区、非法日期400、匿名和顾客401、跨店403，以及同租户另一空门店数据隔离。临时QA会话finally撤销，不创建账号、不改凭证或真实订阅，不发卡、消费或冲正。

历史报表沿用既有CardAccess记录读取规则：校验租户、真实门店归属、本人门店及wallet.cards.view；不额外要求当前商业订阅。工作台入口与新业务另有App Gate。验收初次误将另一自有空门店的历史读取预期为403，源代码核实后改为检查200且零记录；没有修改业务权限实现或弱化跨租户检查。

## 数据 / 服务 / 回退

无迁移、依赖、环境、权限新增，Flyway保持`128|128|0`。生产切换前后卡流水、持卡、日用量、购卡单、资金关联五张表指纹不变；认证QA查询前后同样不变。环境、Nginx配置、Nginx/PostgreSQL/Redis/Docker服务身份不变。后端active/running，MainPID=684265、NRestarts=0、InvocationID=7375f705e734484fbdfdf9fcdeb74b07，启动及验收后无ERROR。

持有既有发布锁，仅重启rpb-backend。回退目录`/opt/rpb/backups/20261103-card-business-report-3abca5b1-before`保存旧JAR硬链接、旧前端入口及版本标记；候选位于`/opt/rpb/releases/20261103-card-business-report-3abca5b1`。旧后端c06b4b5e、旧前端92e2bcfe。应用回退须配对恢复JAR、index.html、frontend-version.json、RELEASE_SHA和FRONTEND_RELEASE_SHA并启动后端；保留现有数据库和静态资源，不恢复旧数据库覆盖业务。

服务器空间紧张，未清理任何既有文件。完整pg_dump以二进制流保存本地`target/card-business-report-release-20261103/database-before.dump`，27,816,271字节，SHA256`999a7d25efadf5193d5d9bdb2a5db4a4cf8e7091f2655b8505eba362ccc4561b`，pg_restore目录检查通过；此次无schema变化，未另做恢复库演练。服务器回退目录记录外部备份位置和摘要。发布后余量约52.6MiB，应单独审计扩容或清理，不能把后续大型发布视为已具备空间。

## Production Readiness

GO：当前范围测试、隔离、API兼容、制品、数据、回退、空间预算及线上查询验收通过。实体手机、生产真人账号页面交互和大规模压力未验收；不将合成QA查询及本地浏览器证明扩大为这些结论。源实现的本地浏览器桌面/390px中文/320px英文证据仍有效。

私有证据位于`target/card-business-report-release-20261103/`及服务器同名验收目录，不纳入Git。后续发布记录提交只更新文档，不改变线上功能SHA。
