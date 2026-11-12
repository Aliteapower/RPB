# 考勤至付款分阶段实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. 不提交，不推送，不部署。

**Goal:** 在既有员工、岗位、核准、工资模块内补齐操作衔接、来源证据及月度办理工作台。

**Architecture:** 继续 Controller→应用服务→仓储，独立小组件承载摘要/来源/状态。沿用个人方案、工资快照、公司月批次、来源锁、增量引擎及付款台账。

**Tech Stack:** Spring Boot/Java21、PostgreSQL/Flyway、Vue3/TypeScript、node:test。

**Spec:** docs/superpowers/specs/2026-10-30-workforce-end-to-end-payroll-design.md；2026-10-30当前用户要求覆盖其后续金额更正建议，本轮不实现跨公司归属或已发布金额更正规则。

## Global Constraints

- 本轮不提交、推送或部署。第一部分607a53c7已发布，记录52d20557已完成收尾，此后HEAD保持不变。
- 自由员工当月有效雇佣及方案下基本工资全额，月中入离职/无薪假不折算；整月无雇佣不生成。跨公司工资归属及已发布工资金额更正保持现状。
- 权限隔离和历史快照保留；employeeManage 不等于工资权；模板更改不覆盖个人例外。
- 本地数据库仅用D:\RPB\target\local-postgres-current.txt指向运行时，隔离测试schema；不操作生产，不回写既有schema。
- 保留原钱包、临时脚本、私人资料和已有工作，不回滚、不清理。当前codex分支原地工作，发布制品已隔离构建；本轮不创建/切换工作区以免错用数据库。
- 每阶段先行为测试复现缺口，再最小实现，必要回归及独立审查；所有生成的字段缺省兼容旧JSON，保留旧构造签名。

## Review Focus

1. 查询公司/员工上下文变化、未来方案、模板更新竞态不能误绑或伪造来源（Task1）。
2. 同名项目合法、同事件重复消费不合法；原工资快照不回填（Task2）。
3. 过期核准不作为有效工时；迟到豁免不补秒、不重复扣款（Task2）。
4. 历史/当前来源权限撤销和无基线时不能泄漏/伪零（Task3）。
5. 付款冲正后余额与状态可恢复，零净额不伪造付款（Task3）。

### Task 1: 员工薪资关联与岗位公司默认

**Files:** payroll/{PayrollDtos,PayrollConfigurationService,PayrollConfigurationRepository}.java；新增聚焦默认映射/员工摘要服务与Controller；V107新迁移（开始前确认无同号）；LocalRuntimeSecurityConfiguration；EmployeeForm/PayProfileForm/PositionList.vue；新增EmployeePaySummary与PositionSalaryDefaults组件及独立types/text；相关PG/API/UI测试。

**Interfaces:** 延用个人方案API，扩展可选 ProfileOrigin（模板名称、版本、rule基线、默认映射ID/版本）与预览版本校验字段，保留旧构造。新默认映射API在payroll模块内；GET /employees/{id}/pay-summary?companyId=&on=；GET/PUT /position-salary-defaults（具体ID路径按项目风格在API文档确定）。摘要含有效/未来/历史方案及来源完整性、有效起止，必须经PayrollAccess。前端按payrollHome.companies筛可见公司，无权限不请求金额。

- [x] 读取规格4/5/10节及现有个人方案/岗位/API/测试，先落API与数据契约。默认映射按租户/公司/岗位/生效日期唯一，同租户公司模板FK、version及审计，不能GET写入。
- [x] 添加并运行失败行为测试：员工指定上下文、无权不请求薪资、模板或映射预览后变化409、个人覆盖及仅编辑个人方案保留原来源、跨租户/公司/停用模板拒绝、未来日期选择、历史冻结。
- [x] 实现摘要与默认映射，显式预览应用才创建个人快照，服务端捕获模板基线。旧客户端可兼容但不声称完整新来源；新表单必须提供预览版本。修改来源时用原共享来源锁/配置锁及一致事务。映射模板版本只记录维护时证据，应用取当前启用模板并校验保存版本。
- [x] 员工编辑页独立摘要/维护入口；新员工先保存再维护方案，准确绑定employeeId/companyId；不默认第一名员工。岗位列表仅有薪资权展示对应公司默认维护入口；无工资权不泄露金额。复用PayRuleFields/PayGenerationFields。
- [x] 运行聚焦PG/API/UI、vue-tsc；报告真实红绿命令与结果，补文档，独立任务审查。不得commit。

### Task 2: 工时口径、工资项目来源与同公司跨店防重

**Files:** payroll/{PayrollDtos,PayrollSources,PayrollGeneration,PayrollCalculationService,PayCalculator,PayrollIncremental,PayslipService}.java；新增PayrollLineage/HoursProjection等聚焦类；approval只扩展读投影；PayItemFields/工资单/核准页面、独立组件与types；相关计算/PG/UI测试。

**Interfaces:** 消费Task1可选ProfileOrigin。Item新增可选稳定itemId及明细来源引用，保留旧构造；PayrollLine/Component可选新增lineage/hours证据（或独立版本化快照字段，确定后记录契约）。新增证据仅生成到本轮新计算，缓存引擎键升级，旧快照原样、旧字段无证据可读。员工工资单只投影本人必要解释，不暴露内部备注/其他员工/精确地址/CPF原资料。

- [x] 添加并运行失败测试：实际/有效核准/计薪单位明确；excused不补秒，迟到8→7.5小时/时薪20仅付150不再扣10；自由工资不变；补卡导致核准过期；同日两店加班阈值合并/跨月context不重付。
- [x] 项目逐项证据包含来源类型、稳定编号、配置/输入版本、数量/单位/单价/倍率/折算/金额及舍入，保留现有BigDecimal公式。旧记录无来源显示证据不足，不按label去重。
- [x] 新来源项目消费键细到itemId/源事件明细；多个津贴可引用同一profile，派生基本/加班只生成一次。相同事件已通过工时体现的来源型额外扣款须阻断或明确独立解释；旧无来源人工扣款不猜、不删。
- [x] 同schedule来源重复、不同schedule时间重叠明确阻断确认，不静默删除；同公司跨店一条员工工资、固定津贴一次；不改跨公司现状。
- [x] UI分开展示工时口径和项目溯源，自由显示不适用；复用工资明细/核准组件。运行相关PG/纯计算/增量与全量等价/旧JSON/冻结/本人隐私/UI测试、类型检查并更新契约。独立任务审查，不commit。

### Task 3: 月度办理工作台、过期原因与付款状态

**Files:** PayrollProgressService/PayrollProgressDtos/PayrollChanges/PayrollProgressRepository；新增只读PayrollWorkbench服务/DTO/Controller（需要时）；既有PayrollApprovalReadiness/PayrollChangesPanel/PayrollBatchDetail/PayrollHome/PayrollBatchList及独立组件、类型、文案；PG/API/UI测试。

**Interfaces:** 消费前两阶段来源证据；工作台返回company/month/batchVersion/asOf、缺资料/待核准/变化/双基线差额/结构化过期原因/付款汇总。没批次也有准备度。历史来源再鉴权。批次状态仍五种，付款状态独立按Decimal金额聚合未付/部分/付清/零净额无需付款。优先扩展已有changes/readiness；若新增聚合只读入口由应用服务编排，不复制计算真相。

- [x] 添加并运行失败行为测试：缺方案/政策/证件与待核准分类、人数去重与班次分列、无批次/无比较基线、金额不变但来源过期、冻结当前来源变动只提示、权限撤销/跨租户拒绝、版本刷新竞态。
- [x] 复用现有捕获与依赖键细分employee/employment/profile/monthly_input/attendance/correction/leave/approval/schedule/rules/calendar/workmode/statutory_policy/engine/baseline_unavailable；缺证据用来源变化通用原因，不虚构。
- [x] 新月度面板显示资料缺失、待核准、本月变化、金额差额及过期原因/有权动作入口；无薪资权不泄漏、无计算不伪零；付款偏好缺失不阻断计算。保留后端月底/全量/非本人/版本/来源确认守卫。
- [x] 付款汇总使用原登记减冲正，保持发布/付款分离，确认前可作废重建、发布后金额更正明确不支持；不创建银行转账/差额工资规则。覆盖部分/超付/重复/冲正/响应丢失恢复与零净额。
- [x] 运行聚焦测试、全前端/类型检查/构建，必要后端回归；更新API/开发记录/release note及方案勾选状态。独立任务与最终全范围审查，不commit。

## 完成判定与记录

每阶段在target/workforce-end-to-end-20261030保存红绿日志、测试数量和未验证边界。只在代码和实际验证支持时勾选；用户未授权的跨公司归属、已发布金额更正保持现状。发布后第二部分HEAD始终52d20557；完成时报告本地结果和保留风险，不展示集成菜单。

最终状态（2026-10-30）：三个阶段及最终集成两项必要修复已完成本地验证，限定复核Spec/Quality均APPROVE。HEAD52d20557及空暂存区保持，无新增提交、推送或部署。完整执行证据和未验收边界见[本地验收记录](../../development/WORKFORCE_END_TO_END_20261030.md)。
