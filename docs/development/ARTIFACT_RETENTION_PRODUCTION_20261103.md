# 自动制品清理生产启用（2026-11-03）

## 版本与结果

主功能提交c97ab8fcef375ddd3706bb517f03f3d76b634ac9、失败快照保护补丁9634f0362608138f0844dec47e37c2747aa026b3均已推送origin/codex/paynow-payment-product-line-staging。实际运维源码版本为9634f036，记录于/var/lib/rpb-backup-retention/POLICY_RELEASE_SHA。业务前后端仍为3abca5b1431ec800e573f9b214f9c84afdd87a35，未重新发布或重启业务应用。

只提交本任务文件；40个原有无关修改及未跟踪文件原始字节哈希不变。没有数据库/API/业务权限/依赖修改。

## 验证

最终57项测试在服务器Linux隔离临时目录全部通过，无跳过。覆盖原16项备份回归、真实文件逐项删除及重复执行、同后端不同前端、最近2组版本、手动固定版本的回退依赖、原件哈希保护、未知内容/日志/符号链接、注册权限、发布锁、dry-run环境开关、预算及失败快照固定保留。测试未操作真实业务数据库或生产制品。新snapshot --database的完整生产恢复演练本轮未执行；现有真实备份使用pg_restore目录验证，应用/数据库迁移未发生。

源包从提交逐个Git blob导出，核对18个源文件；已安装脚本/模块、兼容入口、systemd单元及策略逐一核对哈希。最终辅助命令补丁另核对全部运行文件，清理核心、策略和systemd与已dry-run版本完全相同，复用其验收证据。systemd-analyze verify通过；现有tat_agent.service的旧/var/run PIDFile警告与本次无关，未扩大修改。

## dry-run后启用

1. 持发布锁备份旧运维文件，临时停止清理timer；业务服务不变。安装时添加RPB_RETENTION_DRY_RUN=1覆盖，即使ExecStart带--apply也不能删除。
2. 只登记经原始构建哈希、生产标记和发布回执核实的4个bundle：当前3abca5b1版本、上一c06b4b5e版本及各自-before回退点。不按目录名推断其他历史包成功。
3. 人工dry-run及实际systemd单元dry-run均成功：备份候选0、受管制品候选0、blocked=0、capacity=ok；当前版本、上一组、两份配对回退及固定迁移恢复点均受保护。
4. 核验清单后移除仅本次创建的强制dry-run覆盖，启用timer，并主动执行一次--apply。实际执行成功，旧备份删除0、受管制品删除0；两类未知内容不转为删除目标。
5. 固定保留wine-storage-26947810-before及workforce-exception-49839ab8-before；136个未登记历史发布目录、11个来源未充分确认的根目录JAR、全部日志仍保留。

已启用每天03:30 Asia/Singapore；验证时下次为2026-11-04 03:30。systemd Result=success、ExecMainStatus=0；本机容量状态ok，磁盘72%、约11G可用。已接入本机alert.json与OnFailure/journal；没有对外发送邮件或消息，也没有配置新的外部通知账号。

## 生产保护

首次执行前后/opt/rpb/backups、releases、app、frontend及media全部节点元数据摘要一致；当前JAR及453个发布前端文件内容哈希一致。136个未知目录、11个根目录未知JAR保持原样。Nginx/PostgreSQL/Redis/Docker及rpb-backend的PID、InvocationID和NRestarts均不变；后端PID684265、NRestarts=0、active，验收窗口ERROR=0。环境配置哈希不变，数据库只读检查Flyway128|128|0。

公网453项静态文件、115项页面、5域名版本/TLS、15项匿名401保护和10项指南PDF检查通过。没有以真实顾客发卡、消费、退款或核销验证；本轮检查服务、资源及清理保护边界。

## GO / 回退

GO：测试、源文件/部署哈希、两次dry-run、首次实际执行、定时状态和保护边界验证均通过。后续新应用发布必须遵循ARTIFACT_RETENTION_OPERATIONS.md及AGENTS.md使用受管staging、版本快照、预算和完成登记；未登记/失败/未知资料继续保留，不能期待按名称自动清扫。生产数据及日志增长仍由容量监控和扩容处理。

原运维恢复点：/opt/rpb/backups/20261103-artifact-retention-c97ab8fc-before，含rollback.json、旧脚本/单元/策略及.rpb-keep。按操作规范先停timer、等待任务结束、持锁恢复原文件；删除本次新增模块前核对最终运行文件清单，并保留必要backup pins。恢复旧timer不涉及应用或数据库回滚。新登记库、结果和未知资料继续保存，不清空记录以绕过保护。

私有证据：本地target/artifact-retention-20261103/；服务器/var/lib/rpb-backup-retention/deployments/c97ab8fc与9634f036。包含完整dry-run、实际执行回执、前后保护摘要、最终运行文件哈希及版本。后续发布记录提交只改文档，不改变已启用运维源码SHA。
