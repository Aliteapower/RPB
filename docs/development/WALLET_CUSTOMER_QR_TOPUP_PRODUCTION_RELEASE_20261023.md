# 钱包顾客二维码充值生产发布

2026-10-23，D:\RPB。用户明确授权“提交部署”。

## Production Readiness Report

### Checklist
- 本次仅发布顾客充值码、充值扫码选顾客、用途权限及相关中英文交互。无迁移、配置、依赖、新权限项或其他产品业务变更。
- 复用同代码已核实的 66 项后端、38 项前端测试和构建结果；真实 PostgreSQL 测试使用本工作区 runtime 指针的独立临时 schema。
- 目标 ubuntu@43.134.69.75，/opt/rpb，rpb-backend，8080，数据库 rpb_acceptance。当前运行 ce595f9e911a25c0a9c7fd0ad29f770aa95e64a3，jar SHA256 0b650f50fba939612ed00e91daa21c235ca26c3f3c9b542e822cdca5d7e4b189。
- 当前服务 active，PID 769319，InvocationID 420c18c271754cb789786da4e86f051c，NRestarts 0；可用空间约 18GB。nginx、PostgreSQL、Redis、Docker 为邻接边界，仅记录和核对，不重启。
- 只提交指定文件，保留原有无关验收文档和 rpb信息.txt。从干净 Git 归档核对源码 blob，独立构建，再上传校验制品。
- 切换前备份旧 jar、前端、RELEASE_SHA、数据库，验证 dump 可读；发布后核对业务指纹、Flyway、服务进程与公网资源。

### Blocking Issues
无。实际发布完成及验证前不宣称部署成功。

### Non-blocking Issues
手机摄像头实机验收尚未执行。生产不执行真实充值、扣款、退款或修改顾客密码；使用匿名身份边界、制品一致性和只读核对验证发布。

### Rollback Plan
就绪失败或新进程异常时恢复本次备份的 jar、前端 index 和 RELEASE_SHA，只重启 rpb-backend；保留新旧资源和业务数据库，不覆盖已完成交易。无需逆向迁移。

### Decision
GO，执行提交、推送、备份、部署和验证。实际结果完成后追加。

## 实际发布结果

2026-10-23 14:01（Asia/Singapore）完成切换，运行提交 `9ccccaa2c66ce8dc26d0671ad20b77821d6456e4` 已推送至 `codex/paynow-payment-product-line-staging`。后续记录提交仅更新文档。

### 制品、备份及回退保护
- 干净 Git 归档的 2,184 个文件逐个与提交 blob 一致；独立 Maven package、npm ci、Vue 类型检查与 Vite 构建通过。复用同实现的 66 项后端与 38 项前端测试结果。
- jar SHA256 `532156c158801bfdfea9fea55a4cbf602ed0aaf6e1cba99b319e9c739bbd8392`；前端包 SHA256 `ed0a682fab9a910447208c76db4f8f18046661712ffcc9accd9c40aac38aea4c`。上传、发布目录和运行 jar 核对一致；jar 包含充值识别权限，前端包含扫码充值文案。
- 发布目录 `/opt/rpb/releases/20261023-wallet-qr-9ccccaa2`；备份 `/opt/rpb/backups/20261023-wallet-qr-9ccccaa2-before`，含旧 jar、完整前端、版本文件和数据库 dump（805,442 字节，pg_restore -l 可读）。未触发回滚。
- npm ci 仍有 7 项既有高危告警，本次未修改依赖或锁文件。

### 服务、数据及公网验证
- rpb-backend active/running，MainPID `790048`、InvocationID `ff5ff1a47ede4805a150244f4aab517d`、NRestarts 0；就绪到最终检查进程身份一致，新进程 ERROR 日志 0。
- 默认 profile，Flyway 验证 58 项成功，仍为 V058、0 失败，未执行迁移。
- 92 张既有业务/配置表在发布前、切换后、最终验证的指纹完全一致；排除正常变化的会话、验证码、访问审计和限流等，Flyway 单独核对。顾客、钱包余额、资金流水及其他业务数据保持。
- nginx、PostgreSQL、Redis、Docker 的 PID、InvocationID、状态和重启数与发布前一致。
- 4 个公网域名 HTML 与构建逐字节一致，176 个静态资源均 200 且内容一致，旧资源保留；两家门店管理入口匿名查询仍为 401。
- 9 项原公开接口状态正常。初始严格 JSON 对比发现 3 个预约 context 的 `14:00` 时段 `selectable` 从 true 变 false；发布前采样 13:59:46、发布后 14:01:28，时段恰在期间过期。已与既有 `startAt.isAfter(now)` 规则及两个采样时刻交叉验证，只修正这 3 个预期的时间字段后所有 JSON 完全一致，无其他差异。
- 新增用途的签码和识别接口共 8 项匿名边界检查通过：同源带钱包头为 401，缺钱包头、浏览器 cross-site/same-site 均为 403。未进行真实充值、扣款、退款或凭据修改；手机实机摄像头验收仍未执行。

### 证据与保留文件
本地 target/wallet-qr 同前缀保存源码 blob、制品哈希、构建日志、部署日志、业务指纹检查、原公开接口采样及时间变化比较、静态资源和权限验证。原有未跟踪验收文档 SHA256 `88646E36D7B6C02CD04180FEDB2B0A0B7881FD7929C39EBFA56D8A6511C8F34B` 和 rpb信息.txt SHA256 `17121CD0A39ED1A14F70901091D92C5501A79F104315708656CC7FF3EF168726` 保持，未提交或上传。

结论：已提交、推送并部署，备份、运行版本、服务稳定性、资源、权限及数据检查通过。
