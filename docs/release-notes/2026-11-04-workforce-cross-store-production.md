# 跨店排班与打卡生产发布

2026-11-04，RPB 生产前后端已切换至 `570ceb0407a18719100cdc87d8e0ff2a8c0d0970`。功能提交已推送至 `origin/codex/paynow-payment-product-line-staging`。

- 管理员从原班发起整班或余段调店。员工可凭目标班次从原门店打卡页签到签退，系统按实际工作门店计时。周、月看板可按日期打开各店名单。
- Flyway 从 V128 升至 V129，129 条成功记录，0 条失败。V129 新建调班关系表和索引。发布时该表为空。
- 63 项本地后端测试、18 项相关前端测试及生产构建通过。完整前端回归中的 25 项失败在原工作区复现。隔离 Flyway 演练验证升级、重复运行及旧版迁移集兼容性。
- 生产校验了 459 个前端文件、五个域名上的 20 个页面及 3 个匿名接口的 401 响应。后端启动日志无 ERROR，Nginx、PostgreSQL、Redis 和 Docker 服务身份保持。未用真实员工执行生产调班、打卡或手机定位验收。

## 制品与回退

- 版本目录 `/opt/rpb/releases/20261104-workforce-cross-store-570ceb04`
- 受管上传 `/opt/rpb/staging/20261104-workforce-cross-store-570ceb04-upload`
- 完整回退点 `/opt/rpb/backups/20261104-workforce-cross-store-570ceb04-before`，包含经校验的数据库备份
- 三项登记均已独立复核。已有调班业务记录时，回退须先确认旧应用对这些班次和会话的兼容性。保留 V129 和发布后业务数据。
