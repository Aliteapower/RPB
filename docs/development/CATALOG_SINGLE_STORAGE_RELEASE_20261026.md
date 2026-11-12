# 单商品单条码与寄存商品筛选发布验收 — 2026-10-26

## 发布结果
GO，已按用户明确授权提交、推送及部署。功能提交 `00854affcdadcfa2ae82c76931dab0ce5de1f511`，分支 `codex/paynow-payment-product-line-staging`，仓库 `Aliteapower/RPB`。仅提交 17 个任务文件；8 个原钱包组件及 4 个无关未跟踪文件的 SHA-256 均保持不变。

范围：商品编辑区一个可编辑条码框，去掉添加按钮，扫码直接替换；独立条码写入和统一编辑均阻止第二条。商户/顾客新存酒严格使用可寄存商品，原申请按 ID 独立核验，收存提交重新验证。已有存酒快照及领取流程保留。

## 构建与本地验证
- 复用同一代码状态的 63 项后端测试、267 项前端回归，均零失败。使用 D:/RPB/target/local-postgres-current.txt 指向的隔离 PostgreSQL 测试 schema。
- 从功能提交 git archive 导出独立构建目录；逐项比对 Git 对象共 2738 文件，仅允许 Windows CRLF/LF 差异。npm ci、vue-tsc、Vite 及 Maven 生产打包通过。
- 新旧 JAR 业务条目比对仅涉及 CatalogBarcodeService、StorageProductCatalog（含 Options 记录）、StorageQueryService、StorageStaffController。无无关业务类变化。
- 沿用的依赖审计仍有既有 7 项 high；未修改依赖或锁文件，本次没有声称修复这些问题。

## 生产边界及保护
目标仅 `43.134.69.75 /opt/rpb`、服务 `rpb-backend`、端口 8080、数据库 `rpb_acceptance`。发布目录 `/opt/rpb/releases/20261026-catalog-single-00854aff`。
备份 `/opt/rpb/backups/20261026-catalog-single-00854aff-before`：应用、前端及 18,260,781 字节数据库备份。当前备份成功恢复到临时演练库（兼容既有 SQL 函数的 search_path 设置），演练库完成核验后删除。无数据库迁移，Flyway 始终 `080|80|0`。没有新权限或环境变量。
启动/就绪、在线验收、指纹或邻居进程检查失败会回退应用和前端；没有触发回退。无需数据库回滚；不回退真实存取酒或财务数据。回退目标 f9588621 仍支持现有 raw_barcode 和寄存快照。

## 在线业务验收
独立 QA 租户 `43d4b2f4-b3f6-4883-9e6e-474de2985011`，104 次带身份 API 请求按预期通过：
- 第二条码独立接口返回 CATALOG_SINGLE_BARCODE / 409；统一编辑提交两条同样拒绝，名称、版本和条码保持原值。
- 14 位原样值改成 13 位后回读准确；同键重试保持同一结果；显式替换后数据库只有一条映射。
- 顾客/商户搜索、分页候选、扫码选品、单商品核验覆盖草稿、storage 开关关闭/恢复、门店停用、下架、分类停用/恢复、归档；服务类即使开放 storage 也被排除。
- 原钱包余额、卡余额及历史已完成核销回读正常，未执行真实财务或存取酒扣减。
- 仅在 QA 创建测试商品和分类，测试商品结束后归档；临时 staff/customer 会话已撤销。未修改真实商品条码或渠道。

## 发布后检查
- 4 个域名共 32 个页面路由、18 个静态资源字节一致，60 项匿名 API 访问均 401（含新增单商品资格接口）。
- 148 项真实业务表指纹一致，包含完整 raw_barcode；排除 QA 租户及明确的会话/验证码/访问审计等动态表，不能理解成全库每行完全不变。
- 服务 active，PID `1828536`、InvocationID `bad696e5e1744a45a2c1ea3884cf513a` 在观察期与最终核验稳定，新进程无 ERROR 日志。nginx、PostgreSQL、Redis、Docker 进程身份和状态不变。
- JAR SHA-256 `61bff9be9038724df0d2a80c6a3d3047d7cab72fc6ccbd8724933a113fec02ab`；前端 index SHA-256 `29c47788ab106c6e16943a3b6e26ba88f9ea469b6cb71b187cf527a701d7bca8`；后端和前端 RELEASE_SHA 均为功能提交。
- 本轮扫码 UI 用组件测试替身和上线资源核验；未声称真实 iPhone 相机已测试。

证据：`target/catalog-single-release-20261026/` 内 scope.json、source-proof.json、jar-comparison.json、artifacts.json、deploy.log、final-check.json、live-readback.json、editor-live.json。服务器同名证据目录 `/home/ubuntu/catalog-single-release-20261026`。
