# 每日营业记录品牌去重与英文国际化发布

2026-10-22，发布范围为 D:\RPB 每日营业记录模块、双语目录挂载及租户资料 Logo 锚点。此前独立两轮本地实现合并发布；不包含工作区私人资料。

## 发布检查

- Production Readiness：GO。47 项 Java 定向测试通过，无失败或跳过；覆盖 PostgreSQL 品牌迁移幂等、清空不复活、租户边界、API/App Gate、金额及导入回归。
- 13 项前端行为测试、Vue 类型检查、生产构建通过。中英文桌面/手机布局及确认弹窗已检查，CSV 和界面语言切换不改金额/原始业务文本。
- 生产基线：程序 `6ddda2816e37`，本地提交基线 `1ebb40e8`。发布前线上服务 active、NRestarts=0；完整数据库备份已用 pg_restore 列表验证。
- 备份：`/opt/rpb/backups/20261022-ledger-branding-i18n-before`，包含 database.dump、旧 jar、旧 RELEASE_SHA、frontend、media 及业务表指纹。
- 无表结构迁移和新权限；一次性 `ledger-branding-import` 仅指定 `sx`。将原 Logo 纳入已有租户共享媒体并标记兼容状态，不修改营业记录。
- 前端保留旧哈希资源以兼容已打开的页面。其他产品线配置及业务记录以发布前后 27 张表指纹和公开接口检查为准。

## 回退

如新程序未就绪，恢复备份 jar 并重启 rpb-backend。若已切换前端，同步恢复备份 index.html 与 RELEASE_SHA；保留共享 Logo 媒体和原始 branding JSON，不删除新媒体或覆盖营业数据。若需回退兼容标记，必须单独核对租户设置版本及发布后修改，不能整库覆盖。

## 明细

参见 [品牌入口去重](2026-10-22-daily-ledger-branding-unification.md)、[英文国际化](2026-10-22-daily-ledger-english-i18n.md) 和 [品牌接口契约](../api/DAILY_LEDGER_BRANDING_CONTRACT.md)。线上结果在发布完成后补记。
