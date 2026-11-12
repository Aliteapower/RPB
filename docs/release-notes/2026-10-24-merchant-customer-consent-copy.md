# 商户新增顾客同意提示 — 2026-10-24

- 顾客记录入口统一为“顾客同意（PDPA）”；说明为“商户新增顾客，即确认已取得顾客同意”。
- 商户登记说明改为“顾客同意，由商户新增时登记”，已有记录状态为“顾客同意已登记”；英文同步。
- 沿用现有新增时默认勾选、提交时原子保存 merchant_attestation 同意记录的流程，保留实际操作人、时间及告知版本。编辑、取消、顾客自行注册、资金授权及历史数据均未改动；无API、迁移、权限或依赖变化。
- 相关前端85项回归通过，新增表述的现有记录面板断言先失败后通过；构建结果见 `target/customer-merchant-consent-copy-build.log`。未提交或生产发布。
- 回退仅恢复本次双语文案与对应测试断言，不改数据库。

## 生产发布结果

用户随后明确授权“提交部署”。功能提交 `5637bbb2a0b0d72225ab8399c4be288abff819a4` 已推送并于2026-10-24发布前端；此前“未提交或生产发布”为实施阶段状态。本段记录另行提交，不改变运行制品。

- Production Readiness：GO。仅4个预期文件纳入功能提交，其他8个组件换行状态、未跟踪资料及私密文件保留，未读取私密文件。
- 干净目录 `target/consent-copy-build-5637bbb2` 按Git blob构建，2454个文件校验通过；1975个源码/配置文件与验证工作区一致。相较当前后端c3ce328e，Java、资源和pom无差异，故仅发布前端。
- 干净目录相关前端124/124、vue-tsc及Vite构建通过。npm ci保留原锁文件，仍提示已有7项high漏洞，未调整依赖。
- 目标为 `ubuntu@43.134.69.75` 的 `/opt/rpb`。持既有发布锁，备份完整前端及前端版本标记到 `/opt/rpb/backups/20261024-consent-copy-5637bbb2-before`，制品保存于 `/opt/rpb/releases/20261024-consent-copy-5637bbb2`；保留旧assets，原子替换index。失败可自动恢复旧index及前端标记，无需恢复数据库。
- 前端版本标记 `5637bbb2`；后端仍为 `c3ce328e`，jar哈希保持 `7bda25de3c0c044415af341ebd44a039146b676d28ce889a7133e878e2c3ae45`。无数据库写入、迁移或重启，Flyway保持 `070|70|0`。
- booking、platform、20000000、sx四域名及12条页面返回新index；219个静态资源与干净制品逐字节一致，公网新i18n资源包含新的中英文同意文案，原“尚无登记记录，不代表已经同意”不在该新资源中。
- 六个原有匿名/公开API响应与发布前一致，15项原有接口保护检查通过。未操作生产顾客或真实资金，未做已登录手机界面验收。
- rpb-backend、nginx、PostgreSQL、Redis、Docker进程身份及状态保持不变；后端PID1161303、InvocationID `a7334ce9cd594515851ff77b5c9a8352`、NRestarts0，最终日志观察无ERROR。
- 新index SHA256：`e2a5c5decc9383f1755cf17d6a7fd136559eac6fc1bb6da3291eb3356723778e`。前端包SHA256：`d8794bde0a5b6bf74c4a250536804ae363c71ff8e0dca335f8e3d3c39ee50735`。
- 本地证据：`target/consent-copy-source-proof.json`、`consent-copy-artifacts.json`、`consent-copy-clean-tests.log`、`consent-copy-clean-build.log`、`consent-copy-deploy.log`、`consent-copy-assets-proof.json`、`consent-copy-api-proof.json`、`consent-copy-final.log`。
