# 钱包收银台营业报表生产发布

2026-10-25，D:\RPB；用户明确授权“提交部署”。

## 版本与范围

功能提交 `008340b91957e0b76c0d2be5c3f8513c9762d3f5`，已推送 `origin/codex/paynow-payment-product-line-staging`，前后端 RELEASE_SHA 均为该提交。仅 26 个本轮相关文件进入功能提交。原有 8 个组件改动、3 份验收文档和私人文件保留，未带入制品。

新增日、月、年报表及消费/充值/退款与冲正/积分大项筛选；无交易项目和科目隐藏；卡片、明细、CSV 同步过滤。只读查询，不开户、不改交易。无迁移、新依赖、环境变量或 AppGate 权限变化。

## Production Readiness：GO

- 从功能提交创建干净 Git 归档；2,011 个源码文件与已验证工作区一致（忽略 CRLF）。
- 干净制品 215 项钱包前端测试、类型检查、Vite 构建、Maven package 通过。
- 复用源码一致的本轮后端 42 项测试，以及补充退款/积分报表断言后的对应 2 项复验。使用指针 PostgreSQL 61644 的隔离 schema，无线上测试写入。
- 发现 72 个既有资源仅换行格式不同；在干净构建目录恢复线上制品的原有资源字节后重新打包。最终 JAR 仅新增 5 个 WalletBusinessReport 类条目，修改 WalletErrorAdvice.class 和 LocalRuntimeSecurityConfiguration.class；其余类、迁移、配置、依赖逐字节一致。

## 备份、切换与回退

- 目标：ubuntu@43.134.69.75，/opt/rpb，rpb-backend:8080，数据库 rpb_acceptance。
- 发布目录 `/opt/rpb/releases/20261025-wallet-report-008340b9`。
- 备份目录 `/opt/rpb/backups/20261025-wallet-report-008340b9-before`，含原 JAR、完整前端、版本标记和 1,087,071 字节数据库 dump，pg_restore 目录验证通过。
- 持现有发布锁，暂时隔离 nginx 到 RPB 的请求；停应用后备份并采集 116 张业务表指纹。原子切换 JAR，就绪后核验业务指纹，再复制资源并原子切换 index、版本标记。保留旧 assets，未触发回退。
- 回退：持同一锁、隔离 RPB 请求并停服务，从备份恢复 JAR、index 和标记，启动就绪后恢复入口；不覆盖恢复数据库，不回滚用户后续业务记录。

JAR SHA256：`b51fbf0b0e429e4937b9ba9e73e5f1a6ec14576b77a0e00500d3405cb86a2ac6`。

前端归档 SHA256：`af926db8bc74c0b2d313181f06d13196a137e5e1c88534cc6af4bf6766bb0b6e`。

index SHA256：`64526e10ddf9295311141280b4e9ec3d33d230acdb624e9304f9105086a28332`。

## 线上验证

- 4 个域名、24 个 SPA 入口匹配新 index，含新报表入口。228 个公网资源逐字节匹配制品。
- 员工 me、日报/月报/年报匿名请求均 401，新接口保持 private/no-store。
- Chrome 现有商户会话只读验证：日报实际数据、充值大项过滤、月报按天、年报按月、积分大项及双向冲正项目、2025 无交易空态及隐藏大项通过。未提交业务操作。日消费 400 分、日充值本金 10000 分、月充值本金 240501 分与数据库独立只读汇总匹配。
- 发布停机窗口前后 116 张业务表指纹一致；浏览器检查后再比对仍一致。
- 服务 active，PID 1465167，InvocationID `6edf9fded3554ab09992d0a7224aadce`，NRestarts=0；独立复验身份稳定，无新增 ERROR / Exception；Flyway 保持 070/70/0。
- nginx、PostgreSQL、Redis、Docker 状态及进程身份不变，临时隔离规则已撤销。

截图：`target/wallet-report-online-year.png`。构建、制品、部署、公网、独立汇总及最终稳定证据位于 `target/wallet-report-*`。

剩余边界：未做生产规模压力测试；CSV 已覆盖生成与安全转义测试，本次线上浏览器未实际下载文件。
