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

参见 [品牌入口去重](2026-10-22-daily-ledger-branding-unification.md)、[英文国际化](2026-10-22-daily-ledger-english-i18n.md) 和 [品牌接口契约](../api/DAILY_LEDGER_BRANDING_CONTRACT.md)。

## 线上结果

- 已推送并部署代码 `a5ce76700e89eda99b02cbcf851fe1d399b1ce01`。线上 RELEASE_SHA 一致，jar SHA-256 为 `c7592373e214dcd72f5988aad3b18a8664f98a81479ca1b892769536cc63034e`，公网 index.html 与本地构建逐字节一致。
- sx 品牌迁移采用 1 条门店设置，共享 Logo 图片与原图逐字节一致。仅执行指定 sx 租户的兼容迁移。
- sx 原指定账号密码接口登录通过，产品入口和管理权限正常，跨门店访问 403，匿名 auth/me 返回 401；订阅仍为 monthly / 30.00 / active。
- 4 个营业日期，现有 8 条工作记录、6 份提交快照、92 条营业审计。原迁入的 7 条记录及 89 条审计保留且内容一致；新增的晚餐草稿和 3 条审计是发布前用户已有修改，未用原快照覆盖。
- 发布前后以及浏览器验收后 27 张业务表的行数和完整行指纹均一致，包括账本记录、日期、保存幂等记录、审计、订位、排队、收款、开通配置和订阅。
- 线上浏览器原会话正常；EN 切换、Dinner 草稿 Version 3、共享 Logo、独立 Ledger settings 无重复品牌编辑、History 报表均可见。营业额 10,947.90、现金交款 3,576.90 保持不变。本次没有提交或修改营业账目。
- lcd：booking-entry 200、门店 booking/context 200；lsc106：booking-entry 409（现有多门店需选择门店）、门店 booking/context 200。
- 最终进程 PID 416745 / InvocationID `74c392b42efc4176b4fc519ac6e71c7d`，active/running，NRestarts=0；该进程无 error 级日志。
- 首次脚本经 Windows 管道传输时末尾 CR 字符触发保护性应用回退；随后改为上传 LF 脚本文件重新执行成功。未回滚或覆盖数据库，最终版本已重新核验。
- 本地证据：忽略目录 `target/daily-ledger/production-branding-i18n-verification.json`、`production-english-ledger.png`、`production-english-settings.png`；服务端最终业务指纹在上述备份目录 `business-final.jsonl`。
