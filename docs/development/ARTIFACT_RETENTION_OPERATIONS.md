# 生产发布制品与自动清理

## 边界与默认规则

原备份策略保留15天、至少2份有效完整恢复点及2份前端快照。新受管发布制品保留15天、至少最近2组成功版本；当前前后端SHA各保留一个有效代表，固定项及回退依赖不受期限影响。同一后端SHA的所有前端历史版本不会被永久固定。

成功上传副本保留24小时，必须存在哈希完全相同且不在本轮删除清单内的原件。原件被上传记录引用时先保护原件，下一轮才可能回收已无引用的过期版本。登记库保留收集记录，重复执行不扩大范围。

只有root登记、完整清单和实际SHA256吻合的版本才进入管理。未登记、未完成、文件新增/变化、符号链接、越界、手动.rpb-keep、缺失回退依赖都不能转成自动删除目标。历史未知内容不自动补登记。目录清理仅逐文件unlink及空目录rmdir；不按名字前缀递归删除。

固定数据位置：/opt/rpb/releases、/opt/rpb/staging、/opt/rpb/backups；登记库/var/lib/rpb-backup-retention/catalog。生产app/frontend/media、数据库/WAL、Redis/Docker、配置、证书、SSH、日志及/home/ubuntu不属于新清理范围。现有备份模块仍只处理原有已识别备份，并纳入登记库提供的回退和原件保护。

## 后续发布必须使用的流程

命令均以root运行，Python加-B，避免生成源码目录缓存。这里管理制品，不替代现有应用切换、数据库迁移演练或业务验收。

1. `rpb_release_artifacts.py incoming --id <upload-id>` 创建受管上传目录；只上传backend.jar（可选）和frontend.tgz到返回路径。不得再向/home/ubuntu散落上传包。
2. `rpb_release_artifacts.py budget --required-bytes <完整预计新增字节>` 检查整个发布预算，必须另留2GiB；prepare还会检查JAR、归档和展开前端的空间。容量不足时先处理容量，不绕过保护条件。
3. `rpb_release_artifacts.py prepare --id <release-id> --upload-id <upload-id> --backend-sha <40位提交> --frontend-sha <40位提交>` 准备不可变候选。frontend.tgz必须来自干净构建，含index.html和frontend-version.json；只复制该版本清单，不复制线上累积assets。JAR匹配已登记原件时使用硬链接，禁止原地修改共享inode。
4. 在切换前，用当前已登记版本执行 `rpb_release_artifacts.py snapshot --id <new-release-id>-before --release-id <current-release-id> --database`。完整数据库备份由pg_dump产生并用pg_restore -l校验；只读操作数据库。若无schema/后端变化且沿用既有完整恢复点，可省略--database，但这种快照不计入完整备份最低数量。外部备份位置说明也不等于本机可验证dump。
5. 完成既有应用切换、迁移与线上验收后，执行 `rpb_release_artifacts.py complete --id <release-id> --rollback <backup-id> --upload-id <upload-id>`。命令核对生产前后端标记、JAR和每个版本前端文件、服务及401 readiness后才登记成功；登记上传副本前核对原件并封存上传目录。--rollback可多次指定，至少一个。

**并发要求**：各CLI命令自身持有/home/ubuntu/rpb-ledger-release.lock。完整部署器应在应用切换到complete期间持同一锁，并直接import `/usr/local/sbin/rpb_release_artifacts.py`调用prepare/snapshot/register_release/register_upload函数；不要在已持锁的进程中通过子命令再次获取锁。注册失败时保留候选和证据，不能伪造success或手改成功时间来允许清理。

已存在的成功历史版本仅在核对原始构建哈希、版本标记、发布回执后，由受审计迁移脚本调用Catalog.register登记；不能遍历目录批量猜测成功。首次启用仅登记本次已知当前/上一版本及必要回退点，其他历史目录维持unknown保护。

## 定时执行与告警

每天03:30 Asia/Singapore执行，沿用发布锁、低CPU/IO优先级和15分钟超时。ProtectHome维持read-only；仅增加releases和staging可写白名单。默认CLI无--apply仅输出计划；`RPB_RETENTION_DRY_RUN=1`即使单元带--apply也不会删除。--record-dry-run只保存运维计划和容量状态，不改制品。

查看：`systemctl list-timers rpb-backup-retention.timer`；`journalctl -u rpb-backup-retention.service`；/var/lib/rpb-backup-retention/last-dry-run.json、last-run.json、alert.json。小于5GiB预警、小于2GiB或完整恢复点不足/校验异常严重告警。退出码2表示空间预警，1为阻止执行，75为发布锁忙。OnFailure单元记录本机告警并写journal；没有向电邮、Slack或其他外部服务发送消息。生产数据和日志持续增长仍需容量监控/扩容。

## 上线、回退及异常处理

安装前备份旧运维脚本、单元和策略。先安装dry-run环境覆盖，分别人工与systemd运行dry-run，核验当前版本、回退引用、未知目录和候选；再移除覆盖并启用timer，验证一次实际执行回执。业务服务不重启。所有CLI/保留程序必须从同一个Git提交安装，保留入口兼容名rpb-backup-retention.py及模块名rpb_backup_retention.py。

回退：先停止清理timer，等待运行中的oneshot结束，再持发布锁恢复备份中的运维文件；恢复旧格式策略时，将本次固定恢复点及当前last-run中受保护的backup引用继续放入pinned_backups，不能用旧空列表取消这些保护。daemon-reload后恢复原timer状态。保留新登记库和结果以便调查，不回滚应用或数据库，不删除未知内容。删除与登记收集状态之间发生崩溃时会保留缺失/不匹配记录并停止猜测，需核对执行回执后手动修复；不能用清空登记库来解除保护。

snapshot从创建起写入.rpb-keep；复制、pg_dump校验或登记失败都会保留该标记，既有备份程序也不能把这类失败快照当作普通过期备份。只有完成验证和登记才解除临时标记，失败证据需人工核对。
