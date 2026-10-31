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

## 实际发布结果

2026-10-23 12:15（Asia/Singapore）完成切换。已提交、推送并部署运行代码 `1d61725a57cabc989ab48064184cc93484399d89`，分支 `codex/paynow-payment-product-line-staging`。本记录的后续文档提交不改变运行代码。

### 构建与备份
- 干净 Git 归档 2,173 个源文件与提交 blob 逐字节一致，排除私人资料、本地数据库、会话和夹具。独立归档目录的 Maven package、npm ci、Vue 类型检查与 Vite 构建均通过。
- jar SHA256：`8fe72244db445941aafa853deb1468ff3c166c05fe7f1b96ee3e3517f9326b72`；前端包 SHA256：`332f4fcc0dcf7b60601f87c424706b29fb3b5dd0bd3bd7a8fb6677c71bb7d3f6`。本地、上传和运行 jar 一致。
- 不可变制品目录：`/opt/rpb/releases/20261023-shared-customer-1d61725a`，后端与前端分目录保存，jar 不放入公开静态目录。
- 备份：`/opt/rpb/backups/20261023-shared-customer-1d61725a-before`，包含旧 jar、完整前端、RELEASE_SHA、数据库 dump 和三次业务指纹。dump 797,899 字节，`pg_restore -l` 验证可读。未触发回滚。
- npm ci 报告 7 项既有高危依赖告警，与前次发布相同；本轮依赖及锁文件未改动。

### 服务、迁移与数据
- `rpb-backend` active/running，MainPID `763695`，InvocationID `e2f9479fb6cf4c9c89a27cdd78bde4bc`，NRestarts 0；就绪后至最终验证进程一致，新进程 ERROR 日志 0。
- default profile 启动，未启用本地鉴权。Flyway 校验 58 项、成功应用 V058，最终 58 成功／0 失败；认证邮箱 nullable 为 YES，新增密码／限流表存在且均为 0 行。本次没有替顾客初始化密码、登录或修改凭据。
- 91 张既有业务／配置表的发布前、切换后、浏览器核对后指纹完全一致，包含顾客、认证账号、钱包余额／流水／账单、订阅和其他产品业务；Flyway 独立验证，正常运行的会话和访问审计表不参与业务指纹比较。未自动补号或合并档案。
- nginx、PostgreSQL、Redis、Docker 的 PID、InvocationID、active 状态与发布前相同。未修改其他项目、nginx、证书、生产环境配置或依赖。

### 公网与浏览器核对
- booking.yumstone.sg、platform.booking.yumstone.sg、20000000.booking.yumstone.sg、sx.booking.yumstone.sg 的 HTML 与干净构建逐字节一致，176 个静态资源全部 200 且内容一致；旧资源保留。
- 9 个原公开接口的 HTTP 状态和 JSON 语义与发布前一致，含公开预约 context／入口、员工身份、钱包身份、PayNow 和账本；两家门店的管理入口匿名查询仍 401。
- 新密码接口边界 3 项通过：缺少门店、非法手机号均 400 request_invalid；无会话改密 401 unauthenticated。请求在凭据处理前拒绝，没有创建顾客、账户、密码或登录计数。
- 公共钱包入口显示手机号、密码及“初始密码为手机号后 6 位”的登录说明。
- 沿用 20000000 子域名已有顾客会话，只读查看原钱包余额 30.00、冻结 0.00，统一门店与发行主体正常；展开改密表单可见当前密码、新密码、确认新密码。未输入或提交密码，未生成付款码或操作充值、扣款、补付、退款。
- 租户顾客管理入口无员工会话时正确跳转后台登录；没有代做滑块或登录管理员。后台数据权限和成功登录／改密沿用本地真实 PostgreSQL 与 Vue 交互测试证据，不将此写成生产写入验收。
- 本地截图：`target/shared-customer-production-login.png`、`target/shared-customer-production-password-form.png`。其他证据：同前缀 source-proof、artifacts、production-deploy、public-before/after、release-assets-proof、auth-production-proof、final-production-proof、neighbors-before/after。

### 保留内容与结论
原有 `rpb信息.txt` 与未跟踪验收文档继续保留，本次未提交或上传，哈希与发布前一致。仅本记录作后续文档提交。

结论：已提交、推送、部署；V058、服务、制品、备份、静态资源、认证边界、原业务数据与相邻服务核对通过。
