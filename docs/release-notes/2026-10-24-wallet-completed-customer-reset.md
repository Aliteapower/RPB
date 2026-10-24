# 充值及积分办理成功后清除顾客

日期：2026-10-24。状态：已提交、推送并完成前端生产发布，运行版本 3b17ca17f50cbf0b32b5b98dda7b39c789eae350。

## 行为

- 积分增加、扣减、兑换成功后保留只读结果及记录入口，自动清除顾客、账户、手机号确认、扫码身份及候选列表，重建选择组件以清空查询输入。
- 再次给同一顾客办理也必须重新选择并确认；原数量、原因和确认表单不保留。
- 充值确认成功立即清除顾客，即使后续详情刷新失败。详情首次读取或轮询到已入账／已取消时同样清除，返回办理页面不会继承旧顾客。
- 失败、待收款及结果不明不清除当前操作；既有幂等重试保持不变。

## 验证与审查

- 先补失败回归，再实现；组件套件 95 项通过，包含手机号／扫码、三种积分操作、失败保留、刷新后不恢复、重新选择同一顾客后字段为空、充值终态以及确认成功后读取失败。
- `target/wallet-completed-customer-reset-tests.log` 保存组件验证；前端 vue-tsc 与 Vite 构建通过，见 `target/wallet-completed-customer-reset-build.log`。
- 范围审查：清除动作由页面编排，复用 workspace.clear；积分组件只通知已完成事件；不修改领域记账、授权或幂等机制。
- 现金充值独立测试在原目录 Vite 初始化超时；干净目录复跑揭示旧断言遗漏了凭证变化后重新核对的现有规则。测试加载改为与现有组件套件相同的 esbuild 直接编译，避免创建 Vite 服务，并补上凭证变化后再次确认的断言。四项现金测试通过；WalletCollectionForm 业务代码未改。干净发布目录全部186项钱包测试通过，无超时；vue-tsc及Vite构建通过。
- 未启动后端、连接数据库或执行生产交易；未代替用户执行真实充值或积分操作；iPhone 微信最终交互由现场核对。

## 兼容与回退

无 API、数据库迁移、依赖或权限变更；预约、排队等业务不受本轮代码影响。回退本轮页面事件及终态清除逻辑可恢复旧交互，不需撤销任何已入账数据。工作区原有组件修改保持原状。


## 生产发布

- 功能提交 `3ed51990`，测试修复 `3b17ca17`；均已推送 `codex/paynow-payment-product-line-staging`。
- 从 `3b17ca17` 干净Git归档构建，2456个Git blob逐一验证（仅允许归一化CRLF）。全部186项钱包测试、vue-tsc、Vite通过。原工作区其他组件修改保留。
- 目标 ubuntu@43.134.69.75，`/opt/rpb/frontend`；前端版本 `3b17ca17f50cbf0b32b5b98dda7b39c789eae350`。后端保持 `c3ce328e081dc3e3438057422fa8a11f3908de7a`，JAR哈希 `7bda25de3c0c044415af341ebd44a039146b676d28ce889a7133e878e2c3ae45` 不变。
- 持 `/home/ubuntu/rpb-ledger-release.lock`，核实旧前端5637bbb2与制品哈希后备份完整旧前端及版本标记到 `/opt/rpb/backups/20261024-wallet-customer-reset-3b17ca17-before`。保留旧assets，原子切换index和前端标记；失败自动恢复旧index及标记，无需数据库回退。
- 发布目录 `/opt/rpb/releases/20261024-wallet-customer-reset-3b17ca17`。前端包SHA256 `bfe37f1f1c9ca7c2c2cab0b58484d69f5870b2f84216e370fcbbe1d58fc190b6`；index SHA256 `5803017631275f7e298afdc8b2f61277a806da7afd938a9517a2139012fec834`。
- booking、platform.booking、20000000.booking、sx.booking.yumstone.sg四个域名，12个页面地址和219个静态资源逐字节匹配制品；发布包顾客清除事件及中英文提示已验证。
- rpb-backend PID1161303、InvocationID a7334ce9cd594515851ff77b5c9a8352、NRestarts0保持；nginx、PostgreSQL、Redis、Docker服务身份均不变。auth/me匿名401；Flyway070/70/0保持。独立最终读取无新增错误日志，没有启动迁移或执行生产资金交易。
- npm ci既有7项high依赖告警，依赖未更改。
- 证据：`target/wallet-customer-reset-source-proof.json`、`wallet-customer-reset-clean-tests.log`、`wallet-customer-reset-clean-build.log`、`wallet-customer-reset-artifacts.json`、`wallet-customer-reset-deploy.log`、`wallet-customer-reset-assets-proof.json`、`wallet-customer-reset-final.log`。
