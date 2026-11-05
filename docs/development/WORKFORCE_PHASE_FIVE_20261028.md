# 第五阶段实施与验收：工资报表、导出和试运行对账

日期2026-10-28，项目D:\RPB；用户授权继续第五阶段。仅本地开发/隔离验收，未提交、推送、部署。复用未提交前三/四阶段；其他钱包、产品目录与页面改动保留，无生产迁移。

## 实现
- 独立reporting模块：PayrollReportDtos（最小响应）、PayrollReportRepository（租户/公司/月批次选择）、PayrollReportValues（精确汇总/边界）、PayrollReportService（scope/一致读取/指纹）、PayrollCsv（固定列/文本安全）、PayrollReconciliationService（只读差额）。独立WorkforceReportController，不把报表塞入工资状态命令或考勤服务。
- 明确1..50家所选授权公司，逐个整体验证；复用工资公司委派、公司完整门店和冻结/当前支援来源门店scope及AppGate。无权限公司不静默跳过，不宣称只授权的部分公司等于全租户集团。普通员工与普通员工管理权限不能取得工资报表。
- 公司月批次状态完整展示；confirmed/published冻结汇总与calculated预计算分离，draft/missing显示无金额，voided不重复计数。集团同时显示去重员工人数和工资行数，支持同一人同月不同公司分别负债。
- 后端BigDecimal处理毛薪、净薪、双方CPF、公司SDL、雇主成本、登记付款和余额；冲销为反向付款。公司SDL使用冻结批次的公司取整值，员工SDL原值及员工估计成本在CSV列名和页面说明中单独标识。
- GET报表及导出使用REPEATABLE_READ只读事务；最多10000工资行/20000付款，超限413，不返回截断合计。报表指纹包括内部批次版本和完整当前capture指纹以及投影和付款；不将私密来源字段暴露给响应。
- CSV三类：公司汇总、员工工资、付款流水。UTF-8 BOM/CRLF/RFC4180转义，危险文本前置单引号，保留付款原额、带符号计入额和冲销引用。排除生日/PR/联系方式/工资规则/管理说明/付款说明/操作者；金额由后端生成。员工编号在表格软件中按文本导入，CSV不提供Excel单元格类型。
- 独立试运行对账页：人工录入外部八项公司汇总，返回系统减外部的逐项精确差额。全部输入必填，允许零，拒绝指数/负值/超精度；仅confirmed/published允许。无持久化、无工资调整或银行行为。
- 两个独立双语页面/routes：payroll-reports、payroll-reconciliation。工资首页只向有可管公司的用户展示管理卡片；选择变化立即清旧数据，晚到响应不能写回；下载附当前指纹，服务器变化后409要求刷新。下载同源会话/no-store、类型检查、blob URL释放；不把薪资存到sessionStorage。
- LocalRuntimeSecurityConfiguration只补2条精确GET报表路径和1条精确PUT对账路径；真实actor/AppGate仍由应用服务验证。原产品权限、订阅、依赖、工资算法未扩展；没有新迁移。

## 合约和范围
设计、计划与接口分别见：
- docs/superpowers/specs/2026-10-28-workforce-phase-five-design.md
- docs/superpowers/plans/2026-10-28-workforce-phase-five.md
- docs/api/2026-10-28-workforce-phase-five.md

报表沿用2026年SGD普通工资范围，不新增法定规则。对账是人工公司汇总核对，不含员工CSV导入/自动匹配或真实历史工资资料验收。零差异仅表示八项汇总相等，不能证明员工分配、真实转账、CPF申报或法律合规。未做大规模负载、真实手机/微信或生产验证。

## 测试和审查
先写PG/CSV测试，缺模块导致编译RED：target/workforce-phase5-red.log；前端缺页面RED：target/workforce-phase5-ui-red.log。

独立只读审查提出1项必要问题：已显示sourceChanged=true后连续来源变化/草稿输入变化可能不更新报表指纹。真实PG用冻结后公司名称连续变化复现RED（target/workforce-phase5-revision-red.log），修复为批次version+完整当前capture fingerprint纳入hash；增加连续来源变更和未计算草稿输入回归。保留冻结及当前来源门店检查。修复后相关PG/CSV/HTTP37项通过（其中报表PG33项包括继承的工资既有场景），后续综合回归包含新增草稿场景。

UI浏览器发现语言切换后旧完成提示不翻译，实组件RED→GREEN，限定两个新页面采用响应式提示标签；不修改用户业务文本。日志target/workforce-phase5-ui-locale-red.log。当前阶段2/3/4/5组件56项、原workforce/员工权限15项合计71项通过，npm run build成功。日志target/workforce-phase5-ui-all.log、workforce-phase5-permissions.log、workforce-phase5-build.log。

综合后端回归exit0：286项执行、0失败/0错误/0跳过（包括继承执行的既有场景，不宣称全项目所有测试）。其中报表PG34项（含继承工资场景）、CSV1项、报告HTTP3项；同时回归前三/四阶段、原账号权限和AppGate。日志target/workforce-phase5-final-backend.log，逐类结果target/workforce-phase-five-validation/backend-summary.json。

```text
mvn -q -Dtest=StatutoryCalculatorTest,PayCalculatorTest,ApprovalCalculatorTest,AttendanceCalculatorTest,Workforce*Test,PayrollReportPostgresTest,PayrollCsvTest,AuthPrincipalStorePermissionsTest,StaffStoreAccessPolicyTest,StaffPermissionsServiceTest,StaffPermissionsPostgresTest,StoreSubscriptionAppGateTest,StoreProductSettingsServiceTest,ProductLineEntryRegistryTest test
```

真实PG覆盖REPEATABLE_READ/read-only事务、冻结与预估/缺批次、集团人数去重、历史支援门店和当前scope/AppGate、公司/租户权限、冲销、冻结金额保留、连续来源变更、草稿输入变更、零值/0.01精度和只读对账。git diff --check、三项Node脚本语法和两项PowerShell脚本解析通过；仅既有LF/CRLF及Mockito/JDK agent警告，无本轮未解决测试失败。

## 隔离真实API
读取target/local-postgres-current.txt：port61644、runtimeWorktree D:\RPB。新建schema workforce_browser_70fa22bea90f401889e79cbf7bd85261，应用V001..V091；原public/其他业务schema及前四阶段fixture均未覆盖。

扩展现有fixture/package/start/proxy脚本的five选项，保留原参数默认行为；后端8095、loopback代理5195，独立target/workforce-phase-five-package产物。fixture.json包含测试会话，不打印或提交。

```text
node scripts/validation/workforce-fixture.mjs phase-five
./scripts/validation/workforce-package.ps1 -Phase five
./scripts/validation/workforce-start.ps1 -Phase five
node scripts/validation/workforce-phase-four-api.mjs phase-five
node scripts/validation/workforce-phase-five-api.mjs
```

第四阶段完整业务链83组通过；报表/对账28组真实请求通过，共111组。验证冻结800毛薪/640净薪、员工CPF160、雇主CPF136、公司SDL2、公司成本938；付款640、余额0。组内保留南公司missing，十月100元预估独立于冻结汇总。权限/未知公司/重复公司拒绝；三类CSV解析核对列数与金额/缺批次空值/反向付款/敏感字段；8项匹配及0.01差异；过期导出/对账拒绝；未确认批次拒绝对账。

为验证付款时效，在此新fixture追加一次合法冲销和补登；原记录保留，最终已登记640、余额0。报告和CSV位于target/workforce-phase-five-validation/api-result.json、report-api-result.json、companies.csv、employees.csv、payments.csv。没有对旧验收fixture做新写入。新fixture用历史排班seed，补卡/核准/工资/付款/报表和对账均真实接口。

## 浏览器
使用真实本地代理页面：先观察到原员工角色没有可管理公司/工资数额，切换本fixture管理员验证工资首页入口、选择月/公司、缺批次、冻结和预估分离。已发布工资来源变化提示保持800/640历史金额。

实际点击公司CSV下载，浏览器保存到Downloads；其SHA256与接口验收companies.csv一致：4236D83A869A5F2D6ED14AA160B09E1E022F1AE22E6CC2C15E03151918674340。员工/付款CSV经真实HTTP解析验收，未分别重复点击浏览器下载。

页面实际输入外部毛薪799.99、其余匹配，差额0.01；改回800后八项一致。中英文切换保留输入业务金额/名称，完成提示也随语言更新。默认桌面、约390CSS像素窄屏均观察；受浏览器缩放影响，实测innerWidth389/391、innerHeight844，document clientWidth=scrollWidth（371/372），无整页横向溢出。表格内部横向滚动；临时viewport已reset。

截图：report-desktop.png、reconciliation-desktop.png（0.01差额）、report-mobile-zh.png/en.png、reconciliation-mobile-zh.png/en.png，均在target/workforce-phase-five-validation。未进行真实手机、Excel打开、真实工资对账或外部申报验收。

## 后续及回退
不提交、不推送、不部署。无新增表或迁移；第五阶段为读取和无持久化比较，可独立移除新增报表路由/控制器/服务，不删任何前三/四阶段薪资表、冻结快照或付款历史。真实员工对账资料、员工级CSV导入及外部申报能力需另行明确需求后实施。
