# 钱包交易事件国际化

日期：2026-10-24；范围：D:\RPB，本次前端修复。

- 顾客、员工交易详情及内嵌凭证中的创建、记账、充值、收款、退款和钱包权益事件接入中英文语言包，切换语言即时更新。
- 按稳定事件类型、记账状态优先选择译文；既有审计及钱包流水的通用类型通过已知系统标签兼容。未知标签保留原值；备注、账户名称和历史数据保持原样。
- 先增加组件回归并确认英文记账事件失败，修复后交易套件19项通过；vue-tsc和Vite构建通过。审查覆盖语言切换、三个页面入口、全部现有系统标签、未完成记账及未知标签回退。
- 无API、迁移、权限、依赖和环境配置变更，不修改租户／门店授权及记账逻辑。
- 发布仅切换前端静态资源，保留旧assets并备份旧入口及版本标记；异常时恢复备份index和FRONTEND_RELEASE_SHA，无需数据回退。
- 剩余限制：未使用生产账户执行真实交易；未来新增系统事件标签需补入语言包映射。

## 生产发布证据

- 功能提交 `4700f9ce3c6bcf2032285821ecb0b86cee395656` 已推送 `codex/paynow-payment-product-line-staging`，生产前端版本与该提交一致。
- 从Git归档生成干净目录 `target/wallet-event-i18n-build-4700f9ce`，核对2458个Git文件内容，排除工作区原有钱包组件修改及未跟踪文件。
- 干净目录188项钱包测试通过，vue-tsc及Vite构建通过。全套测试需 `node --experimental-transform-types --test --test-concurrency=1 src/test/frontend/wallet-*.test.mjs`；首次strip-only参数不支持旧测试中的TypeScript参数属性，并行下有固定等待时间竞争，改用完整类型转换和串行后全部通过。未修改无关业务代码或测试。
- npm ci报告既有7项high依赖告警；依赖及锁文件未改。
- 目标ubuntu@43.134.69.75，持既有发布锁，核实旧版本和哈希，备份 `/opt/rpb/backups/20261024-wallet-event-i18n-4700f9ce-before`；发布目录 `/opt/rpb/releases/20261024-wallet-event-i18n-4700f9ce`。保留旧assets，原子切换index及前端版本标记，脚本包含失败自动回退。
- 前端包SHA256 `a42b21ccbe6a65a51f82d6a95d15588b352d9a36ff2b84c93ca2abe51e775cc2`；index SHA256 `40b4cabdde72e70f666c74d443d005cb7ebaa03d6efafa6834b8afa540a6d6ec`。
- booking、platform.booking、20000000.booking、sx.booking.yumstone.sg四个域名，12个页面入口及220个静态资源逐字节匹配制品；制品包含中英文事件文案。
- 后端版本仍为 `c3ce328e081dc3e3438057422fa8a11f3908de7a`，JAR SHA256 `7bda25de3c0c044415af341ebd44a039146b676d28ce889a7133e878e2c3ae45`；PID1161303、InvocationID a7334ce9cd594515851ff77b5c9a8352、NRestarts0、active保持。nginx、PostgreSQL、Redis、Docker服务身份未变；Flyway仍为070/70/0。匿名auth/me401，最终近期错误日志为空。
- 未启动本地后端或连接本地数据库，未在生产执行资金、积分交易。实际已登录手机操作未做现场验收。
- 日志：`target/wallet-event-i18n-clean-tests-final.log`、`wallet-event-i18n-clean-build.log`、`wallet-event-i18n-artifacts.json`、`wallet-event-i18n-deploy.log`、`wallet-event-i18n-assets-proof.json`、`wallet-event-i18n-final.log`。
