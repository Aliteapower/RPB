# 共享顾客与手机号密码生产发布

项目 `D:\RPB`，2026-10-23（Asia/Singapore）。用户明确授权“提交部署”。

## Production Readiness Report

### Checklist
- 复用本轮相同代码的 133 项后端、22 项前端及生产构建成功证据；包含原生 PostgreSQL 登录、改密、并发、租户隔离、钱包原账户／资金及权限测试。详见 [实现验收](SHARED_CUSTOMER_PASSWORD_ACCEPTANCE_20261023.md)。
- 目标为 ubuntu@43.134.69.75 的 `/opt/rpb`、`rpb-backend`、8080、`rpb_acceptance` 数据库和 booking.yumstone.sg 系列域名。chuanxu-prod 是另一个项目服务器，首次只读探测确认无 RPB 后未作任何变更。
- 发布前线上版本 c2a82659f9dadc7d23dc17f579dfc8b9ef2eb770，jar SHA256 69581e6bfc94892851cd68ac3a241e6a715e0df58e885ec5a7791f14d39cbf7c；服务 active/running、MainPID 746271、InvocationID 673a46b901c04d3a808baec025c0f44d、NRestarts 0。Flyway V057 / 57 成功 / 0 失败；约 19GB 可用。
- 本轮包含 V058：新增密码凭据／限流表、认证邮箱 nullable，无顾客／资金重写。不新增短信服务、依赖、密钥、权限或生产配置。旧邮箱 API 保留兼容。
- 仅提交本轮代码、测试和文档。私人资料 `rpb信息.txt` 与既有 `PRODUCT_LINE_ACCESS_BROWSER_ACCEPTANCE_20261022.md` 保留本地，哈希分别为 17121CD0A39ED1A14F70901091D92C5501A79F104315708656CC7FF3EF168726、88646E36D7B6C02CD04180FEDB2B0A0B7881FD7929C39EBFA56D8A6511C8F34B。
- 从提交的干净归档构建并比对 Git blob；发布前备份旧 jar、完整前端、版本标记及数据库 dump，验证 dump 可读。只重启 RPB，核对相邻基础服务不变。

### Blocking Issues
无已知实现阻塞；干净构建、备份、V058 和线上验证完成前不宣称发布成功。

### Non-blocking Issues
初始密码按用户要求为手机号后六位，可由手机号推知，界面提示自行改密。历史无号档案先核实补号；不会自动合并或转移钱包数据。

### Rollback Plan
应用就绪失败或进程不稳定时恢复本次备份 jar、前端 index 和 RELEASE_SHA 并重启 RPB；保留新旧静态资源和 V058，不删除密码凭据或恢复旧数据库覆盖合法业务。数据库 dump 仅用于受控灾难恢复。旧界面回退后不支持新增手机号密码入口。

### Decision
GO，执行用户授权的提交、推送、备份和部署。实际结果在完成后补记。
