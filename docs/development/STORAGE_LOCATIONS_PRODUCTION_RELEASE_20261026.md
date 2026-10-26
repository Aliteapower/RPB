# 常用存放位置生产发布记录

2026-10-26，Asia/Singapore。用户明确授权“提交部署”。

## 目标与版本
- 项目 `D:\RPB`，分支 `codex/paynow-payment-product-line-staging`，已推送 `origin`。
- 功能提交与线上前后端标记：`5b8f7884624a7bfecb5ac84b064108d54d161559`；上一版均为 `52a771d5f42d89ce20e4dfecc11073d7da5813bf`。
- 服务器 `43.134.69.75`，`/opt/rpb`，服务 `rpb-backend`，端口 8080，数据库 `rpb_acceptance`。未操作其他项目。
- 不可变制品 `/opt/rpb/releases/20261026-storage-locations-5b8f7884`；运行 JAR `/opt/rpb/app/reservation-platform.jar`，静态资源 `/opt/rpb/frontend`。

## Production Readiness
GO，已部署。复用同一业务代码已通过的后端存酒/PostgreSQL/JSON/API/安全/架构测试及前端 67 项回归，未无依据重跑。独立 Git archive 使用 core.autocrlf=false；2774 个文件逐一与提交 blob 校验一致，独立 npm ci/build 与 Maven package 成功。初次 Windows tar/换行转换不一致的目录没有用于发布。工作区 12 个无关文件 SHA256 保持一致，不在本次提交范围。

## 备份、演练与切换
- 持有既有发布锁 `/home/ubuntu/rpb-ledger-release.lock`，切换前核对原前后端版本、JAR/index 哈希及 Flyway `080|80|0`。
- 备份 `/opt/rpb/backups/20261026-storage-locations-5b8f7884-before`，含旧 JAR、完整前端、版本标记和数据库 dump。完整恢复到独立演练库通过，兼容既有函数 search_path；V081 在恢复库执行成功，两张新表为空，随后移除演练库。
- 服务停止后再次生成 `database-at-switch.dump`，18,274,048 字节，pg_restore 目录验证通过；记录停机边界业务指纹。
- 原子替换运行 JAR，启动完成后核对 Flyway `081|81|0`；新位置和审计表存在且初始零行。前端先复制资源再原子替换 index，保留旧静态资源。
- 自动回退保护覆盖切换至公网验收：失败恢复旧 JAR、前端与版本标记，保留业务数据库及 V081 新增表。

## 生产验证
- 12 项认证只读检查通过，临时会话仅属于既有独立 QA 租户 `43d4b2f4-b3f6-4883-9e6e-474de2985011`，检查后撤销。
- 新 GET locations 返回默认“前台寄存处”；读取前后位置表行数不变，未向真实门店批量创建配置。未在生产执行位置修改或酒水实物交易。
- 原有存酒、待审核、保管员、目录、钱包、持卡和产品发现查询通过；原 QA 余额 3,800 分、旧次数卡剩余 28 次不变。
- 148 项业务/配置指纹一致。比较排除 QA 租户、会话/审计等易变表及 Flyway、新建空表，另完整比较平台产品与权限；不声称全库逐字节相同。
- 四个域名的 52 个页面入口、29 个资源内容校验通过；48 项匿名请求拒绝（36 项 401、12 项既有 App Gate 契约 403），4 项匿名发现返回空目录。
- `rpb-backend` active，PID `1910007`，InvocationID `4d672e8bf1f848efbfa3bc7d4369e407`，观察及最终复核稳定，无新 ERROR。nginx、PostgreSQL、Redis、Docker 的活动状态与进程标识均未变。

## 制品 SHA256
| 制品 | SHA256 |
|---|---|
| 后端 JAR | `48e3a7912877b9b1e625e89d887dd4365a85d3b9379d4f32f4e23e244a8200a1` |
| 前端归档 | `e866c3d69c31a2c66f458655d330a51781740098819c98eaf170c24faef7303e` |
| index.html | `9ebf1078ad3a3401574ad65f7e70518de7fca64e305f188bf08ad7d46bc0dad9` |

## 证据与限制
本地 `target/storage-locations-release-20261026/`，服务器 `/home/ubuntu/storage-locations-release-20261026/`，含源码证明、构建、备份恢复/迁移演练、部署日志、只读与公网验收和最终复核结果。此前表单布局在本地只读夹具验收；没有将其或生产 API 检查称为手机实机验证。实机操作仍待验收。

回退时恢复上述备份的应用与前端，保留 V081 两表和新业务记录，禁止恢复旧库覆盖新交易。旧界面可能仅显示仓位，排查时保留 snapshot.storageLocation。后续文档提交不改变运行功能 SHA。
