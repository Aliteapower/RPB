# 第三阶段实施记录：请假、日历、核准工时与跨店汇总

日期：2026-10-28。项目：D:\RPB。仅本地开发与隔离验收，未提交、推送或部署。用户原有钱包改动及阶段1/2工作保留。

## 已实现范围
- 请假类型：本店维护名称、带薪/无薪及开关；类型属性在申请时冻结。
- 本人按排班申请整班/部分时段，待审撤回；管理员批准/拒绝，禁止本人审核。
- 已批请假须本人申请销假，再由另一管理员批准或拒绝；不直接删除历史申请。审计记录每个状态的前后快照。
- 考勤日历：公众假期/门店休息日标记，版本及停用状态；按日展示排班/出勤数量，员工仅本人统计。
- 核准：只确认当前来源，不改打卡或手填工时。阻断未结束、缺卡、超长、待审、请假与出勤重叠等来源。
- 保存来源SHA256及完整快照和核准历史版本；补卡、请假、排班或日历变化自动标记过期，旧核准不计有效汇总。
- 门店汇总和总部公司汇总：按员工/发薪公司/实际工作店分组；公司归属取排班历史快照，支援其他公司门店不改变发薪公司。逐店检查权限/AppGate，拒绝静默遗漏受限店。
- 10个独立紧凑双语页面、12条路由，新增请假/考勤设置/工时核准入口，汇总由核准页进入。

## 模块和接口
- `workforce/leave/`：LeaveTypeService/Repository及LeaveService/Repository；DTO独立。
- `workforce/calendar/`：CalendarService/Repository及DTO。
- `workforce/approval/`：纯ApprovalCalculator、ApprovalSources读取和指纹、ApprovalRepository、ApprovalService、WorkSummaryService及DTO。
- 独立WorkforceTimekeepingController，沿用WorkforceEndpointSupport身份、同源保护、no-store和错误映射；仅增加精确GET/PUT本地security路径。
- V090四张新增表，不修改旧产品数据/订阅/默认授权。排班used检查增加待审/已批/待销假请假，防止引用源被修改或取消。
- 前端类型/文案/校验独立phaseThreeTypes.ts、phaseThreeText.ts、timekeepingUi.ts；复用现有phasePage的切店/卸载请求隔离。

设计/API先落盘：
- docs/superpowers/specs/2026-10-28-workforce-phase-three-design.md
- docs/superpowers/plans/2026-10-28-workforce-phase-three.md
- docs/api/2026-10-28-workforce-phase-three.md

## 计算口径
- 时间统一微秒输入，核准时汇总后换算到秒；跨夜按workDate归属，不按午夜重复一班。
- 固定窗口休息按请假实际交集扣除；固定时长休息缺乏具体位置，按请假占计划毛时长比例分摊；带薪休息不扣。
- manual规则的计划请假按毛时长，实际出勤仍按实际休息计算；界面说明口径。
- 整班连续已批请假可无打卡；部分请假无出勤仍缺卡。请假与实际IN/OUT任何交集保守阻断（包括实际休息），避免重算。
- 公众假期/休息日仅标记，不自动取消排班或扣薪，不自动推定法定待遇。

## 权限、并发与数据审查
所有应用入口显式AppGate、tenant/store；管理操作沿用显式委派；公司范围仅总部且现有companyAllowed，包含公司所属门店及实际支援来源门店的逐店校验。
申请/审批与原打卡、补卡、排班同employee advisory transaction lock；日历同store-calendar lock；核准先日历后员工锁，读取来源及保存审计在一个事务中。设置/决策expectedVersion，命令UUID重放；所有新增表tenant/store复合FK与状态/时长/版本约束。核准按(schedule_id,version)留历史。

## 验证证据
前端：
- `node --import ./scripts/tests/workforce-register.mjs --test scripts/tests/workforce-phase2.test.mjs scripts/tests/workforce-phase3.test.mjs`：33项通过（阶段2 18、阶段3 15）。
- 原workforce/staff-permissions/staff-permissions-panel/staff-store-assignment：15项通过。
- `npm run build`通过；日志target/workforce-phase3-ui-build.log。
- 不引入依赖或修改其他产品UI。

真实接口：
- 指针port61644、runtimeWorktree D:\RPB，独立schema `workforce_browser_873a333026a64aef9a0d6a61a8ff2701`，完整V001..V090迁移成功。
- 本机后端8093（隔离打包目录）、loopback静态代理5193；没有停止原阶段后台或改public/原业务schema。
- `node scripts/validation/workforce-phase-three-api.mjs`：39组通过，真实cookie会话及服务过滤器。覆盖整班无打卡核准、3小时请假+5小时出勤、类型修改快照、销假状态、指纹过期、历史和有效汇总、本人/公司权限拒绝。
- 两张过去排班仅由隔离fixture SQL seed（产品API禁止倒排班）；其余流程通过真实API，不宣称真实现场打卡。
- report：target/workforce-phase-three-validation/api-result.json。fixture含测试会话不可打印或提交。

浏览器：
- 员工申请销假填写原因，成功pending；另一个门店管理员拒绝销假后恢复approved。
- 核准页显示来源已变更；重新核准后显示有效且新增版本2，版本1保留。
- 汇总明确2班、有效1、待核准1；有效工时5:00、带薪请假3:00、无薪0:00。
- 独立日历表单新增“本地验收店休日”并在日期列表验证；没有影响既有排班。
- 中文/英文窄屏已看图，实际约391×844 CSS像素；document scrollWidth=clientWidth，无整页横向溢出；导航内部允许水平滚动。仅桌面浏览器模拟，未做真实手机/微信验收。临时视口已恢复。
- 截图target/workforce-phase-three-validation/{approval-desktop,summary-desktop,leave-mobile-zh,leave-mobile-en}.png。

## 审查和修复
独立审查提出三项，均接受：
1. 销假缺原因输入导致400：新增真实组件复现，needsNote包括cancel_requested；空原因禁用、双击只提交一次。RED→GREEN及真实浏览器已验证。
2. 微秒分界的连续请假逐段取整少1秒并误报MISSING_IN：新增adjacentMicrosecondLeavesCoverTheWholeShiftWithoutLosingASecond复现；改为微秒总量及连续边界覆盖判断。
3. 员工核准列表先按全店1000上限截断：新增1001他人员工排班+本人1班复现；employeeId下推SQL后再限量。
新增HTTP接入独立补审APPROVE；核心审查其余无必要问题。最终以行为测试和后端记录闭环，不把可选优化当作完成门槛。

## 剩余边界和回退
- 按单班申请，多日按排班分别提交；无年假余额、年资、复杂政策自动判断。
- 查询最多31天/1000条，超量要求缩小范围；员工上限只计算本人记录。
- 工资、法定加班、CPF、工资单和付款仍第四阶段；没有自动法定假期导入/外部申报。
- 不自动锁定公司工资周期；第四阶段基于有效核准建立工资批次快照，提交前再次校验来源。
- 没有生产迁移、全项目所有测试、真实设备或现场验收，不宣称全产品无任何回归。
- V090是新增持久数据，不应通过删表回退真实业务；未部署状态只需保留源码和隔离fixture，不能删除阶段1/2或用户已有工作。

## 后端最终验证闭环
执行：
```text
mvn -q -Dtest=ApprovalCalculatorTest,AttendanceCalculatorTest,Workforce*Test,AuthPrincipalStorePermissionsTest,StaffStoreAccessPolicyTest,StaffPermissionsServiceTest,StaffPermissionsPostgresTest,StoreSubscriptionAppGateTest,StoreProductSettingsServiceTest,ProductLineEntryRegistryTest test
```
综合执行179项：177通过、2项测试夹具错误（无断言失败）。两项原因均已定位且仅修改测试：跨公司支援fixture在公司不变时误传新的companyEffectiveDate，改为沿用原生效日；AppGate mock重新when设桩触发旧Answer空参数，改doAnswer设置。

随后定向重跑：
```text
mvn -q -Dtest=WorkforceApprovalPostgresTest#supportAtAnotherCompanysStoreStillBelongsToPayingCompany+gateDenialOnAnySourceStoreRejectsWholeCompanySummary test
```
2项全通过、exit0。两轮生产源码相同，未为测试更改生产规则或降低校验；综合回归其余177项证据复用。合并为179项覆盖通过，无未解决失败/错误（包含阶段1/2继承执行）。不宣称最后单条Maven命令执行179项全部通过。

关键明细：ApprovalCalculator6项、审批类37项覆盖（其中2项夹具修正后定向验证）、LeavePostgres36项、AttendancePostgres27项、FoundationPostgres11项、HTTP新3项及原权限/AppGate回归。覆盖同公司跨店、其他公司门店支援仍原雇主发薪、公司来源店AppGate拒绝、核准并发只有一个版本、1001他人排班不阻断本人查询、微秒连续整班请假、审计回滚和历史保留。

证据文件：
- target/workforce-phase3-tests.log（初版正常流程）
- target/workforce-phase3-review-red.log（两项审查问题真实复现）
- target/workforce-phase3-final-tests.log（综合179项及夹具问题）
- target/workforce-phase3-fixture-retest.log（修正夹具后2项通过）
- target/workforce-phase-three-validation/backend-summary-before.json、backend-summary.json（逐测试合并证据，无会话信息）

独立打包 `pwsh -File scripts/validation/workforce-package.ps1 -Phase three` exit0；源码与运行包含微秒和scope修复，后续仅修改测试/文档，实际API和浏览器使用这一包。前端构建和git diff --check通过，原有CRLF提示不作为代码失败。所有阶段三计划项已闭环，第四阶段未开始。
