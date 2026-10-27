# RPB 备份自动保留与清理

日期：2026-10-27。范围仅 `/opt/rpb/backups`，不清理应用、数据库实例、上传文件、发布包或其他项目。

## 保留规则

- 每天新加坡时间 03:30 执行，服务器停机错过后由 systemd 补执行。
- 保留最近 15 × 24 小时内的所有备份，以目录及内部文件的最新修改时间判断，不只看名称。
- 超出 15 天仍保留最新两份完整回退备份（RPB JAR、前端、可通过 `pg_restore -l` 校验的数据库 dump），以及最新两份前端快照。
- 当前线上前后端版本对应的发布前回退点、当前文件实际引用位置、当前构件最新代表副本继续保留。同一 SHA 的其他旧副本不会无限累积。
- `/etc/rpb/backup-retention.json` 的 `pinned_backups` 可指定目录名；目录内 `.rpb-keep` 也可固定保留。
- 未识别内容、符号链接、跨文件系统对象、Git 资料、PDF/Office/字体原件不自动删除，报告待人工核查。
- 不足两份可校验的完整回退备份、版本标记异常、待删内容变化等情况，停止删除。

## 运行与审计

源代码在 `ops/rpb_backup_retention.py`，配置模板在 `ops/backup-retention.policy.json`，定时任务在 `ops/systemd/`。

生产安装位置：

- `/usr/local/sbin/rpb-backup-retention.py`
- `/etc/rpb/backup-retention.json`
- `/etc/systemd/system/rpb-backup-retention.service`
- `/etc/systemd/system/rpb-backup-retention.timer`

```bash
# 只预览；没有 --apply 不删除
sudo python3 /usr/local/sbin/rpb-backup-retention.py
systemctl list-timers rpb-backup-retention.timer
sudo journalctl -u rpb-backup-retention.service --since '7 days ago'
sudo cat /var/lib/rpb-backup-retention/last-run.json
```

清理与发布共用 `/home/ubuntu/rpb-ledger-release.lock`，发布占用时返回 75，15 分钟后重试。删除前重新校验全部候选的内容元数据指纹和版本。服务以低 CPU/IO 优先级运行，内存限制 256MB；文件系统写入权限限于备份、锁和审计状态。

日志记录保留理由、删除对象、预计独占空间和实际磁盘空闲变化。空闲低于 2GiB 或磁盘的 10% 时返回 2，在 systemd 中标为失败，不擅自缩短 15 天保留期；没有配置外部消息告警。此任务不是数据库定期备份任务，也不能限制其他目录或最近 15 天备份增长。

## 验证与回退

本机及生产 Linux 的 16 项测试覆盖 15 天边界、完整/前端回退点、当前版本保护、长期旧副本过期、人工固定、未知资料与路径越界、并发变更拒删、真实删除与只预览。数据库校验仅离线读取 dump，不连接业务数据库。生产额外检查发布锁互斥、systemd 单元和首次执行。

停用后不再自动删除；已删过期备份不可恢复：

```bash
sudo systemctl disable --now rpb-backup-retention.timer
sudo systemctl stop rpb-backup-retention.service
```

## 发布审查

Production readiness：GO，限上述运维任务。没有 API、迁移、App Gate 或租户权限变化，不部署或重启应用。代码审查关注路径约束、备份可识别性、版本回退保护、发布互斥和失败关闭；16 项测试通过。Reservation、Queue、Walk-in、Seating、Cleaning 业务逻辑不变。

## 首次生产执行记录

2026-10-27 安装并启用；timer 为 enabled/active，下一次为 2026-10-28 03:30 Asia/Singapore。服务 `Result=success`、`ExecMainStatus=0`。正式服务在 256MB 限制内完成；独立实测峰值 RSS 41,644 KiB。发布锁占用试验返回 75，未删除备份。

首次清理 5 项：`20260706-125244-host-prefix-login`、`20260707-1104-ce1576dc`、`20260707-1249-9ba007ab`、`20260804-112523-4a816daf-queue-groups`、`20260810-2010-056b92f2-paynow-staff-daily-report`。实际空闲增加 456,986,624 bytes，执行后 5,970,223,104 bytes（约 5.56 GiB），磁盘使用率 86%。

保留完整回退 `20261027-customer-cards-daily-expiry-f3f35ca9-before`、`20261027-customer-activation-state-11778be1-before`；保留前端回退 `20261027-storage-customer-compact-a40e5e5f-before`、`20261027-card-workspace-ui-a184c563-before`。三个包含旧 nginx 链接/配置或 branding media 原件的对象跳过，未按日期盲删。

安装前后前后端 SHA（f3f35ca9 / a40e5e5f）及 rpb-backend、nginx、PostgreSQL、Redis、Docker 的 MainPID/InvocationID/NRestarts 快照一致。12 个既有本地改动文件 SHA256 不变。服务器脚本 SHA256：`a6c4545371ff0de2bcf3ad1043a06fd074f6e8be915b92928dad995fe198b197`，与本地一致。

执行证据：服务器 `/home/ubuntu/backup-retention-setup-20261027/` 的 `dry-run.json`、`first-run.json`、`install.log` 及 services/versions 前后快照；最新运行结果亦保存在 `/var/lib/rpb-backup-retention/last-run.json`。
