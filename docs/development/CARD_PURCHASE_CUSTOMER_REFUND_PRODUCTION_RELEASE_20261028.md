# 购卡订单顾客信息及退卡保护生产发布

2026-10-28，新加坡时间。项目 D:\RPB，分支 `codex/paynow-payment-product-line-staging`。前后端均已发布功能版本 `b3542f051d8279c4671710a40d47116ba25f5a73`；本次只提交 17 个指定文件，原有 12 个未提交文件 SHA256 保持一致。

## 结果与验证

- 购卡订单显示真实顾客姓名、电话，未提供资料时明确提示；不再显示截断 UUID。
- 核销过一次即不可退卡，冲正不恢复退款资格。员工列表由服务端给出资格，页面禁用操作；实际退款仍在卡行锁内重新验证。未用日额度过期不冒充消费。
- 隔离 git archive 的 2,944 个源文件逐一校验 Git blob，29 项前端测试、14 项后端定向测试、Vue 类型检查、Vite 构建、Maven package 均通过。后端验证使用原工作区指针指定的 PostgreSQL；初次因本地实例停止而失败，按指南启动原数据目录后重跑通过。
- 新旧 JAR 只有 10 个相关 class 条目变化，其中 2 个嵌套类经 javap 验证字节码指令相同（调试行号变化）；依赖、配置及迁移字节一致。既有员工权限功能保留。
- 线上只读验证 3 笔 QA 订单，3 笔顾客姓名/电话与同租户有效客户资料一致，2 张有核销记录的卡均不可退。验证空后续分页、顾客端响应字段不变、顾客身份拒绝员工接口、跨店拒绝及钱包/存酒/员工管理邻近读取。
- 四个域名的 32 个页面入口、8 个相关资源校验通过，4 项匿名订单查询均为 401。全部 279 个新前端文件与线上一致，8,734 个旧资源 inode 保持。
- Flyway 保持 `087|87|0`，无新增迁移。153 项业务表/配置指纹前后一致，沿用既有 QA 租户、会话及审计排除口径，不宣称全库字节一致。没有执行实际退款、收款、核销或更改真实顾客资料。临时验证会话已撤销。
- rpb-backend active，PID `2565489`，InvocationID `d0a08561b44644b786380dc751715c1c`，NRestarts=0；观察期内进程身份稳定、无新增 ERROR。nginx、PostgreSQL、Redis、Docker 身份及环境配置不变。

## 生产准备与回退

Production readiness：GO，已部署。服务器 `43.134.69.75`，服务 `rpb-backend`，数据库 `rpb_acceptance`；应用 `/opt/rpb/app/reservation-platform.jar`，前端 `/opt/rpb/frontend`。

发布制品：`/opt/rpb/releases/20261028-card-purchase-refund-b3542f05`。发布与自动清理共用 `/home/ubuntu/rpb-ledger-release.lock`。

完整回退备份：`/opt/rpb/backups/20261028-card-purchase-refund-b3542f05-before`，包含旧 JAR、完整前端、版本标记、环境配置及 `database-at-switch.dump`；pg_restore 目录校验通过（1,284 行）。

若需回退，恢复备份 JAR、前端 index 和版本标记，仅重启 rpb-backend。前后端旧版本均为 `af97cd60fb93e4aa20f7426ec3bdda517268aaab`。保留 V087 及所有已发生的业务事实，不恢复旧数据库覆盖新交易，不删除现有资源或审计。未新增 API 权限、配置项或数据库结构。

## 制品摘要与证据

- JAR SHA256：`984eaadefb81b4b4755b5ec7141dcd602d5da4c8a044fe7fa20ecdafead0f23c`
- 前端归档 SHA256：`b3255c2f0810ea0462fba824b348e1d1fb3c5ae8da88ccee01e1f37829a9d559`
- index SHA256：`cfa07dea29acb0d6a2e36cb96e2c55a230199fe0ab9e2e23c6a05aec83eb81f1`

本地证据：`target/card-purchase-refund-release-20261028/`。服务器证据：`/home/ubuntu/card-purchase-refund-release-20261028/`，包括 source-proof、jar-differences、测试/构建日志、deploy.log、live-check.json、public-check.json、final-check.json 及业务/服务前后快照。
