# 每日营业记录本地验证

## 环境

- 工作区：`D:\RPB`。保留原未跟踪文件 `rpb信息.txt`。
- 数据库由 `target/local-postgres-current.txt` 指定，数据库 `postgres`、用户 `postgres`、空密码；本轮端口 61644。
- 独立 schema：`daily_ledger_validation_20261022`。原 public schema 及原订位测试数据保留。
- 指针原内容备份：`target/daily-ledger/local-postgres-before.txt`；本轮本地运行目录切换至 D:\RPB，备用前端端口改为 5178。
- 后端：8081；构建预览：`http://127.0.0.1:5178/login`。5177 的 Vite 在当前机器请求超时，本轮用最终 dist 的本地预览验证入口，未改 Vite 产品配置。
- Windows JDK 临时 Unix socket 连接异常可通过启动参数 `-Djdk.net.unixdomain.tmpdir=<本工作区target/daily-ledger>` 修复，已用 SelectorProbe 验证；本地 HTTP client factory 设 simple。不进入生产配置。

## 来源与导入

源站点 `https://yumstone-daily-ledger.yesmycup.chatgpt.site` v21；已与 Sites source helper 核实本地源码 HEAD `92ee3d15e8eb5ea13c312fe177740e950c01db8c`。

只读完整快照：`target/daily-ledger/source-export.json`，SHA-256 `99bca2678c69a0f0c53d7b6fce4bb356615c063f006305d483fd0f30274a9dab`。包含配置、Logo、公共员工信息、4个日期、7条工作记录、6份提交快照、89条审计。未导出会话和密码哈希。源站点没有修改。

导入器 `dailyledger/migration` 仅在 local 或显式 ledger-import profile 且设置 import-file 时执行；事务内调用现有租户与订阅服务，拒绝覆盖同名租户。真实数据不放入 Flyway 或 Git。发布导入使用独立进程、正常认证配置、127.0.0.1随机端口；ledger-import完成后关闭自己的上下文，不启用local profile，不给正常线上服务配置导入参数。导入失败抛异常，事务回滚。

已建立蜀香测试租户 `sx`，管理员用户名 `sx`；密码按用户指定设置。原员工保留姓名/员工号与历史署名，因旧站 PBKDF2 与 RPB bcrypt 不兼容，新账号初始停用，管理员重置为 RPB 密码后可启用。

- tenantId：`09d1ad20-08ec-46d5-98c7-315fa510bb03`
- storeId：`a44eef20-34d0-42b5-b235-8e4dc8aae45a`
- 入口：`/stores/a44eef20-34d0-42b5-b235-8e4dc8aae45a/daily-ledger`
- 测试订阅：monthly / SGD 30 / active；备注明确“测试租户开通，非真实收款”。

## 重启

依赖 classpath 已保存于 target/daily-ledger。若 pom 改变，重新生成：

```powershell
mvn -q dependency:build-classpath '-Dmdep.outputFile=target/daily-ledger/classpath.txt'
./ops/start-daily-ledger-local.ps1
```

不再传 ImportFile，否则导入器会按设计拒绝覆盖 sx。首次显式导入通过进程环境变量 `RPB_LEDGER_SEED_PASSWORD` 提供密码，禁止把它写进源文件或日志。

当前构建预览脚本 `target/daily-ledger/serve-rpb.mjs` 仅监听127.0.0.1:5178，并将 `/api/` 代理到8081；重启前核实该端口的现有进程身份，不终止其他任务的服务。

## 自动验证

```powershell
mvn -q '-Dtest=LedgerRulesTest,LedgerPostgresTest,LedgerApiTest,AppGateServiceTest,AppGateGuardIntegrationTest,StoreStaffHomePageAppGateRuntimeValidationTest' test
node --test ops/tests/daily-ledger.test.mjs
npm run build
git diff --check
```

本轮 Java 41项通过（13项新产品测试及28项 AppGate/首页回归），无失败或跳过。PostgreSQL测试使用指针数据库中的随机独立 schema，实际执行 V001–V056，再仅清理本测试创建的 schema。

前端9项通过：最终班次报表、金额分计算、抽款来源、收入/支出图、保存中继续编辑、网络重试、清空恢复保留版本、跨组件恢复待同步请求、400修正重试、旧缓存与前班变化隔离。

`target/daily-ledger/verify-import.mjs` 已通过真实 HTTP 登录并逐字段比较设置、Logo、每天工作记录、已提交快照及89条审计的payload/署名/时间；验证可见产品入口、测试订阅及越权403。结果在 `target/daily-ledger/verification.json`。该验证仅读取迁入业务数据。

## UI Review Report

后续用户要求UI与旧站一致，已完成原站样式移植与操作复核；最新界面、模块拆分和截图见 [UI对齐验收](DAILY_LEDGER_UI_ALIGNMENT.md)。下述早期三列/单列描述属于对齐前版本，当前现金使用原站明细行布局。

模块按表单、字段、历史、图表、设置、API、计算、缓存拆分。加载/错误/空结果/只读/提交锁定均有状态；表格横向滚动，现金表单桌面三列、手机单列。时间使用门店时区和locale，历史保留旧会计口径，CSV保存UTC时间。

用户自行完成本地登录后，已在5178最终构建预览完成管理员浏览器验收：

- 员工首页进入账本、切换录入/历史/设置、日期与午晚餐切换正常；原设置原值保存成功。
- 使用独立日期2099-12-30进行两餐段测试，备注明确非真实营业账。验证自动/手动保存、清空后刷新恢复、负数错误拦截与更正重试、最终提交和管理员修改审计展开。
- 午班抽款100自动进入晚班，晚班不能重复添加抽款。现金1000、非现金200、现金开支10、底钱500得到营业额810、交款600。
- 管理员将提交后的PayNow草稿改为250时，历史与下载CSV仍读取提交快照200，营业额仍810。验收后已恢复200并再次提交。
- 2026年10月原账本仍为4日期、3个最终班次提交日，营业额10947.90、交款3576.90。2099测试数据保留，不计入该月份。
- 手机内容宽372px：现金单列、表格容器独立横向滚动，页面无横向溢出。发现共用语言切换浮层遮挡金额/门店标识，已通过仅作用于账本页面的CSS改为正常文档流；桌面与手机复核。其余页面样式不受影响。
- 浏览器错误日志无新增错误。样式修正后Vue类型检查和构建通过；业务代码未变，复用上述已通过的行为测试。

截图：`target/daily-ledger/ui-mobile-verified.png`、`ui-mobile-summary-verified.png`、`ui-original-history-verified.png`；真实下载CSV位于当前用户Downloads中的 `daily-ledger-2099-12-30-2099-12-30.csv`。浏览器已恢复原窗口尺寸并返回原账本月份。

## Code Review Report

独立审查指出的三项P2已修复：恢复清空仅恢复可编辑字段；确定4xx清除错误待重试请求并允许显式重载；本机 outbox与清空备份按tenant/account/store/date/period持久化。限定复查新增的“旧缓存抽款显示与最终提交不一致”已通过来源比对进入备份冲突路径，并补行为测试。

API、数据库、TDD复核：显式DTO；tenant取认证身份；每个SQL含tenant/store；门店复合FK；配置行/日期行锁、CAS、永久保存编号及审计在同一事务；已提交快照不受管理员草稿影响；AppGate所有拒绝场景有测试。

真实接口另发现共用拒绝审计的FK错误：非授权/不存在门店请求原先导致500。最小修复为tenant级拒绝审计，尝试门店ID保存在snapshot，实际PostgreSQL回归及真实403均已验证。

## 剩余边界

- 未提交、未推送、未发布；没有执行真实银行扣款。
- 支付开通沿用现有平台后台购买/续费、付款登记与门店订阅授权；没有新增支付网关或租户自助自动扣款。
- 原员工仍待管理员重置启用；本轮浏览器使用sx管理员，未模拟员工身份或执行真实支付。
- 本地JDK/Mockito动态agent警告属既有环境；无相关测试失败。
