# 现金充值收据选填生产发布

项目 D:\RPB，2026-10-23（Asia/Singapore）。用户明确授权“提交部署”。

## Production Readiness Report

### Checklist
- 本轮仅普通现金充值收据选填，服务端按充值单生成稳定内部编号，保留资金幂等、权限、审计及实际收款确认；PayNow、信用卡、补付、异常实收仍必填凭证。
- 同码验证：57 项后端通过（含原生 PostgreSQL 48 项资金回归）、5 项前端交互／重试通过、Vue 类型检查和 Vite 构建通过。无需迁移或配置／依赖／权限变更。
- 生产目标 ubuntu@43.134.69.75，rpb-backend、/opt/rpb、8080、rpb_acceptance；旧运行代码 1d61725a57cabc989ab48064184cc93484399d89，jar SHA256 8fe72244db445941aafa853deb1468ff3c166c05fe7f1b96ee3e3517f9326b72。
- 服务 active/running、MainPID 763695、InvocationID e2f9479fb6cf4c9c89a27cdd78bde4bc、NRestarts 0；V058 / 58 成功 / 0 失败，约 18GB 可用空间。
- 只提交本轮文件，保留 rpb信息.txt 和另一份未跟踪验收文档；从提交的干净归档构建，备份原 jar、完整前端、版本标记及数据库并验证 dump 可读，再切换并只读验证。

### Blocking Issues
无实现阻塞；制品、备份及发布后验证完成前不宣称成功。

### Non-blocking Issues
无纸质现金凭证时通过充值单及系统内部编号追溯，仍要求员工确认实际收款。生产不进行真实充值／退款测试。

### Rollback Plan
就绪失败或进程异常时恢复本次备份 jar、前端 index 和 RELEASE_SHA，重启 RPB；保留新旧资源及业务数据库，禁止覆盖已成功交易。无需逆向迁移。

### Decision
GO，按授权执行提交、推送、干净构建、备份和部署，完成后补记结果。

## 实际发布结果

2026-10-23 12:36（Asia/Singapore）完成切换，运行代码 `ce595f9e911a25c0a9c7fd0ad29f770aa95e64a3` 已推送到 `codex/paynow-payment-product-line-staging`。后续发布记录提交不改变运行代码。

### 制品与备份
- 干净 Git 归档的 2,177 个源文件与提交 blob 全部一致。独立目录 Maven package、npm ci、Vue 类型检查和 Vite 构建通过；复用相同业务代码的 57 项后端及 5 项前端通过结果。
- jar SHA256：`0b650f50fba939612ed00e91daa21c235ca26c3f3c9b542e822cdca5d7e4b189`；前端包 SHA256：`10db40ba6b8ec0b344cba40cd911d464857968f1fe803997ae24a400bc8a2215`。本地、上传与运行 jar 哈希一致，jar 内含内部收款编号逻辑，前端包含现金收据选填文案。
- 制品目录 `/opt/rpb/releases/20261023-wallet-cash-ce595f9e`；备份目录 `/opt/rpb/backups/20261023-wallet-cash-ce595f9e-before`，保存旧 jar、完整前端、版本标记、数据库 dump 及三次业务指纹。dump 804,508 字节，pg_restore -l 验证可读。未触发回滚。
- npm ci 仍报告 7 项既有高危依赖告警，本轮依赖和锁文件未修改。

### 服务、数据与边界
- rpb-backend active/running，MainPID `769319`、InvocationID `420c18c271754cb789786da4e86f051c`、NRestarts 0；就绪至最终验证保持同一进程，新进程 ERROR 日志 0。
- 默认 profile 启动，Flyway 验证 58 项成功，仍为 V058、0 失败，无迁移执行；未变更配置或启用本地鉴权。
- 92 张既有业务／配置表（含密码凭据）在发布前、切换后、最终验证三次指纹完全一致，顾客、钱包余额、流水、账单、订阅及其他产品数据保留。仅排除正常变化的会话、验证码、访问审计、限流及单独核对的 Flyway 表。
- nginx、PostgreSQL、Redis、Docker 的 PID／InvocationID／状态与发布前相同，未重启或修改其他服务。
- 4 个公网域名的 HTML 与构建一致，176 个静态资源均 200 且内容逐字节一致，旧资源保留。9 个原接口状态和 JSON 语义与发布前一致；两家门店的管理入口匿名查询保持 401。
- 充值确认权限 4 项通过：带钱包头的同源匿名请求为 401；缺钱包头、浏览器 cross-site／same-site 请求为 403。使用不存在的充值单且不携带登录会话，未进行真实充值、扣款、补付、退款或凭据更改。
- 现金无收据的成功提交、原单重试及其他渠道要求通过本地真实 PostgreSQL 与 Vue 交互验证，生产通过制品一致性、服务和匿名边界核对；不冒充已执行线上资金验收。

### 证据与保留内容
证据位于本地 target 的 wallet-cash 同前缀：release-source-proof、artifacts、production-deploy、final-production-proof、release-assets-proof、public-before/after、auth-production-proof、neighbors-before/after、test-results。

原有 rpb信息.txt SHA256 `17121CD0A39ED1A14F70901091D92C5501A79F104315708656CC7FF3EF168726`、未跟踪验收文档 SHA256 `88646E36D7B6C02CD04180FEDB2B0A0B7881FD7929C39EBFA56D8A6511C8F34B` 保持，未提交或上传。

结论：已提交、推送、部署；制品、备份、服务、V058、权限、原业务数据与其他服务核对通过。
