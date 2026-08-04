# Release Notes

## Version / Date

2026-08-04 - Default queue group provisioning

## New

- 新建单门店租户的默认门店自动获得 `1-2`、`3-4`、`5-6`、`7+` 四个排队人数分组。
- 集团租户后续新增的每个门店也自动获得同一套默认分组。

## Changed

- 默认排队分组由 Queue 模块统一持久化，平台租户和门店创建流程在原事务内调用该能力。
- 初始化和回填均为幂等操作；已有任何分组历史的门店保持原配置。

## Fixed

- 修复新建租户或门店在现场取号、预约到店排队时返回 `QUEUE_GROUP_NOT_FOUND` 的问题。
- 修复不仅针对 LCD；所有未来新建租户与门店都会持久化获得默认排队分组。

## Migration

- 新增 Flyway `V046__backfill_default_queue_groups.sql`。
- 迁移为所有未删除且从未有过 `queue_groups` 记录的存量门店插入四组默认值，因此可覆盖 LCD 当前缺失数据。
- 迁移不修改表结构、索引、约束、依赖或运行时配置，也不包含租户或门店特例。

## Permission

- 无角色、权限码、App Gate、租户授权或门店开关变更。
- 原有现场取号与预约排队权限校验保持不变。

## Risk

- 数据变更为增加默认配置，风险较低；租户和门店作用域由现有外键及唯一索引约束。
- 有自定义、停用、部分配置或软删除分组历史的门店不会被迁移覆盖。
- API 合约、错误码和前端交互保持兼容。

## Validation

- `mvn "-Dtest=PlatformTenantApiIntegrationTest,DefaultQueueGroupProvisioningMigrationTest,ReservationArrivedToQueueApplicationServiceTest,ReservationArrivedToQueueApiIntegrationTest,ReservationArrivedToQueueControllerTest,WalkInQueueControllerTest" test` 通过：68 项测试。
- 指针 PostgreSQL `61644` 上事务式执行迁移，验证四个默认分组后回滚，未遗留验证数据。
- `npm run build` 通过。
- 全量 `mvn test` 已执行：1198 项测试中 8 项失败、1 项错误；本次新增及排队相关测试均通过。失败来自现有前端中文/实现契约扫描、跨切片文件白名单、一个预约创建断言，以及 Windows 临时 PostgreSQL 日志文件清理错误，均不涉及本次变更文件。

## Deployment

- 2026-08-04 11:25 SGT 将后端提交 `4a816daf` 部署到 `booking.yumstone.sg` 共用生产环境；本次未发布前端。
- 后端 JAR SHA-256 为 `e47482a07b03fee0e48a099ddff34188f24f362ab7c316c918f1550a86826e5e`。
- 切换前备份位于 `/opt/rpb/backups/20260804-112523-4a816daf-queue-groups`，包含旧 JAR、迁移前 `queue_groups` 数据和待回填门店清单。
- Flyway 成功执行 V046；无排队分组的有效门店从 3 个降为 0 个，LCD 门店获得预期的四个有效分组。
- `rpb-backend` 为 `active`，本机及公网未认证健康检查返回 `401`，启动后 ERROR 日志计数为 0；LCD 登录、现场取号和预约排队页面均返回 `200`。

## Rollback Notes

- 应用代码可回滚到上一版本；已写入的默认排队分组与旧版本兼容，无需删除。
- 不建议批量反向删除迁移生成的数据，因为部署后这些分组可能已被商户使用或调整。
- 若迁移尚未部署，回滚只需撤回代码和 `V046`；若迁移已执行，保留新增数据是更安全的回滚策略。
