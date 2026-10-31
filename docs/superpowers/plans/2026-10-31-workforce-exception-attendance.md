# 月度异常考勤与四类计薪方式落地计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 四类员工共用异常月表，正常事项自动形成有效来源，合同与个人假期政策准确进入工资链路，保留自由免考勤业务类型，新版启用后保留自身审计与确认历史。旧工资不迁移、不兼容。

**Architecture:** 在现有workforce内新增独立考勤策略与统一有效来源投影，不扩展定位枚举承担计薪含义。复用原权限、请假审批、薪资经济变更、日周加班、指纹和冻结机制；GET无副作用。

**Tech Stack:** Java 21、Spring Boot 3、PostgreSQL/Flyway、Vue 3/TypeScript、现有JUnit/PG与Node组件测试。

**Spec:** [最终业务版](../specs/2026-10-31-workforce-exception-attendance-design.md)。本文是待执行计划，不是代码已经实现的报告；不存在因转写计划而新增的业务审批节点。

## Global Constraints

- 同时支持打卡核对、默认正常登记例外、免打卡按合同计发、原自由免考勤。
- 门店默认和员工覆盖按日期生效；旧自由员工不因门店默认变更自动改制。
- 合同计发不生成虚拟出勤或工时核准；加班完全按有效合同约定。
- 现场/外勤/居家定位仍由workmode拥有；不新增同义自由模式作为合同捷径。
- 默认正常依赖有效安排和雇佣期间；缺依据、未来、未结束、真实0和不适用分清。
- 用户已授权清理线上、线下旧工资测试数据及旧实现；不做旧工资迁移/解码/API回退。保留员工/账号/合同/考勤/请假/法定政策及其他产品；生产新版发布仍需相应授权。
- 保留D:/RPB已有无关修改和未跟踪文件，不清理、覆盖或回滚。
- 本地数据库使用D:/RPB/target/local-postgres-current.txt的当前端口、postgres空密码和隔离合成schema；指针失效按项目重启指南修复。
- 新API先写契约并做api-review；迁移先做database-review；按行为先写失败测试再实现，结束做code-review和release-note。
- 新策略在对应事实、请假、计薪链路及验证完成前，不允许真实采用；后端明确拒绝尚未支持组合，前端不展示为可用。分阶段交付不能让未完成模式悄悄走旧工资公式。

## Review Focus

1. 同一员工跨店但只授权部分门店：正常工时、加班上下文及合同敏感内容不能泄露，也不能截断后假算准确结果。
2. 来源在自动判定/月底核对/工资确认之间改变：旧结果必须失效，不重复计算，不写回冻结历史。
3. 没有排班的合同员工与自由员工：月表不能漏人，请假不能要求创建假排班，工时不能显示0冒充不适用。
4. 策略月中切换或门店默认改变：不会以当前配置覆盖历史，尚不支持的分段明确阻断受影响工资。
5. 同日部分假、休息、额外工作及跨月周同时存在：精确分段、单位明确、与工资结果同源且不重复扣算。

## 实施状态（本节替代下方原始逐步清单的状态推断）

下方清单保留实施前的检查路径，不能把未逐项签收的扩展验收当作已完成。实际交付如下：

| 任务 | 实际状态 |
| --- | --- |
| 0 精确清理 | 工具及8项真实PG测试完成；42表显式清单，默认预览。用户追加执行后已清线上48表29行、本地9旧验收schema741行，保留对象不变，新版工资验收数据保留 |
| 1 四类策略 | 本地实现、权限/日期/快照/并发测试完成；来源快照固定采用门店，不根据当前主门店反推历史 |
| 2 有效来源 | 每日版本、显式刷新、自动任务、来源失效和统一读取已实现；自动范围当天及前7天 |
| 3 例外和假期 | 无排班区间申请/审批/销假、原排班申请双向冲突校验、年度人工授额/结转/调整完成；按工龄自动计提未定义，未实现 |
| 4 合同和加班 | 统一逐日分配、新版来源证据、明确合同条款及月工资链路已实现；无旧工资解码层 |
| 5 月表和员工月历 | 本地页面及组件交互测试完成；桌面DOM检查完成，320/390px及完整语言/员工视觉仍未验收 |
| 6 验证 | 隔离合成schema实际工资创建→测算→全量重算→确认→发布→四名员工查看已验证；真实付款/银行/申报、生产新版发布未执行；旧工资实际清理及两项基线修复已完成，详见实际执行记录 |

实现文件以实际源码为准：`attendance/daily`拥有每日与月度查询；`ContractPayCalculator`拥有合同计算；`EmployeeAttendancePolicySettings.vue`拥有新策略编辑器。假期台账单独置于`leave/entitlement`。不为匹配原计划的暂定类名添加重复空壳。

证据、失败及限制见[验收记录](../../development/2026-10-31-workforce-exception-attendance-validation.md)，已完成行为和待办边界见[最终业务版第14节](../specs/2026-10-31-workforce-exception-attendance-design.md#14-本地实施对齐2026-10-31)。

## Task 0: 精确清理旧工资与直接切换准备

**Files:** 工资重置工具及测试、[清理记录](../../development/2026-10-31-workforce-payroll-development-reset.md)、相关部署切换记录。

- [ ] 依据V091/V103/V105/V107–V112实际表及FK编制显式清单，覆盖工资模板/方案/默认、批次/工资单/付款、经济变更、工资证据/年度工资及补发结算专用数据；共享对象保留。
- [ ] 先写失败测试：默认仅预览、未知外部FK拒绝、目标schema/数据库不符拒绝、清理原子性及保留对象不变；在自己创建的隔离合成schema验证，不清空既有业务库。
- [ ] 实现事务与显式表TRUNCATE，不使用CASCADE，不改Flyway历史或完整性触发器；旧模型专用代码随新版替换删除，不删除有复用价值的算法及法定规则。
- [ ] 新版验证完成后，在已核实的线上/线下RPB实例暂停旧工资写入，预览目标和影响并执行已授权清理；无需重复索要删除许可。记录实际结果；未执行不能标完成。

## Task 1: 独立考勤策略、工作安排与新版解析

**Files:**
- Create: `src/main/java/com/rpb/reservation/workforce/attendancepolicy/AttendancePolicyDtos.java`、`AttendancePolicyRules.java`、`AttendancePolicyResolver.java`、`AttendancePolicyService.java`、`AttendancePolicyRepository.java`。
- Create: `src/main/java/com/rpb/reservation/workforce/api/WorkforceAttendancePolicyController.java`。
- Modify: `workmode/WorkModeClock.java`、`workmode/WorkModePayroll.java`、`api`对应精确方法白名单；不改旧定位枚举含义。
- Create: 经编号复核后的追加迁移、`docs/api/2026-10-31-workforce-attendance-policy.md`。
- Test: `AttendancePolicyRulesTest.java`、`WorkforceAttendancePolicyPostgresTest.java`、`WorkforceAttendancePolicyLocalRuntimeSecurityTest.java`。

**Interfaces:**
- Produces: `EffectiveAttendancePolicy resolve(UUID tenant, UUID company, UUID employee, LocalDate date)`；包含mode、策略ID/版本、来源门店、来源类型、安排ID/版本及来源凭据。
- Produces: `List<ExpectedInterval> expected(UUID tenant, UUID company, UUID employee, LocalDate from, LocalDate to)`；区间含归属工作日、start/end、标准净秒数、休息及来源版本。
- modes: `clock / default_normal / contract / free`；无策略明确待配置，不为工资做旧模型回退；定位与员工原始资料不因清理工资被删除。

- [ ] 先写接口契约：门店默认、员工策略、采用/解除覆盖、安排、影响预览；明确次日生效、幂等命令ID、expectedVersion、400/403/404/409与精确白名单。
- [ ] 失败测试：`freeModeIsNotContractMode`、`crossStoreWorkUsesDatedEmploymentDefault`、`personalOverrideSurvivesDefaultChange`、`futureAssignmentDoesNotRewriteHistory`、`replayAndConcurrentAdoptionAreSafe`；覆盖越权、跨租户、AppGate关闭、真实不存在对象。
- [ ] 新增日期化任职主门店关联与采用策略时的来源快照；`currentMainStoreChangeDoesNotReinterpretHistory`验证当前main_store_id被修改后旧日期策略不变，历史不足不虚构回填。主门店变更不自动更换策略。
- [ ] 运行上述JUnit，核对确因缺功能失败，再实现解析、持久化和API；数据库约束包含租户复合FK、作用范围、生效日、版本唯一及审计字段。
- [ ] 验证旧WorkMode/Clock位置、居家地址、自由豁免及工资适配测试，新增策略不能让新免打卡员工仍可日常打卡。

## Task 2: 系统正常判定与统一有效来源

**Files:**
- Create: `approval/EffectiveAttendanceSource.java`、`EffectiveAttendanceResolver.java`、`NormalAttendanceService.java`、`NormalAttendanceRepository.java`。
- Modify: `ApprovalSources.java`、`ApprovalReadinessService.java`、`WorkSummaryService.java`；旧人工核准命令继续可用。PayrollSources在Task 4完成新版来源模型后接入，不能在这里先放宽工资守卫。
- Test: `NormalAttendanceTest.java`、`WorkforceNormalAttendancePostgresTest.java`、相关审批/readiness/工资来源回归。

**Interfaces:**
- Consumes: Task 1 `resolve`、`expected`，原打卡、请假、异常、人工核准和来源指纹。
- Produces: `EffectiveAttendanceSource resolve(AttendanceSourceInput input)`，包括来源类型actual/arrangement/not_applicable、有效状态、work/paid/unpaidSeconds、issues、sourceFingerprint；工时不适用使用空值。
- Task 2定义输入区间接口（workDate、start/end、类型、paid、批准状态、sourceId/version）；先由既有排班请假适配，Task 3新增员工区间例外按同一接口接入，不让Task 2依赖尚未定义的Task 3类型。合同单位来源由Task 4扩展，不能当work区间传入。
- Produces: 显式刷新命令及任务调用的同一评估服务；系统记录独立标记，不伪造人类reviewer。

- [ ] 契约明确显式刷新范围、任务租户范围、幂等键（来源身份＋指纹＋算法版本）、每条失败结果；GET不创建记录。
- [ ] 先写失败测试：正常完整班次系统有效；未结束/缺卡/开放休息/待审/经理专审不自动放行；全班已批请假无需再次审批；默认正常工作/部分假/休息交集；原始事件不变。
- [ ] 明确系统资格服务不调用需要人工审核人身份的block函数冒充总部；`managerNeedsHumanHeadOfficeEvenWhenNormal`及`selfReadDoesNotBlockSystemNormal`分别验证经理专审与员工本人查询语义。
- [ ] 加入Review Focus 2/4/5：来源改变、未知响应重试、重复任务、两个任务并发、月底核对后补卡、月中切换；新版确认工资哈希不变。
- [ ] 实现系统正常记录及统一解析，锁顺序复用payroll.source协调；替换readiness/汇总内部重复有效性判断。工资接入保留到Task 4，自动正常策略暂不允许启用，readiness不得把当前工资仍不支持的来源误报为可发薪。
- [ ] 运行审批、工时汇总及来源指纹回归；旧工资守卫暂不改变。Task 4完成后补同一来源所有消费者状态、秒数与确认结果一致的链路验证。

## Task 3: 有/无排班请假、额外工作及个人假期政策

**Files:**
- Modify: `leave/LeaveDtos.java`、`LeaveService.java`、`LeaveRepository.java`、`LeaveGroupService.java`、对应控制器与前端LeaveForm/Detail。
- Create: 独立员工区间例外服务与假期权益台账类；按净时长/政策/账务职责分文件，不扩张LeaveService承担全部计算。
- Create: `docs/api/2026-10-31-workforce-employee-exceptions.md`、追加迁移及PG/组件测试。

**Interfaces:**
- Consumes: 有效策略、公司与任职范围、Task 1安排；Task 2接受批准例外来源。
- Produces: `EmployeeException`（employee/company、区间、workDate、类型、单位、状态、版本、审批证据）；旧按scheduleId申请保持兼容。
- Produces: `LeaveEntitlementSnapshot`（政策版本、额度、待审占用、批准使用、释放/更正、余额及单位）；无完整政策返回余额未知。

- [ ] API先区分旧排班申请与新员工区间申请、登记额外工作、审批和销假；客户端不能自行提交最终有效秒数或最终加班金额。
- [ ] 先写失败测试：四种方式都能按权限请假，无排班不需假排班；同日部分假＋额外工作、跨夜、休息交集、跨店重叠、重复批准和销假释放。
- [ ] 权益测试覆盖年度结转/到期/服务期政策、待审占用、跨店去重、并发余额、重复销假及调整留痕；缺单位换算不假设8小时一天。
- [ ] 实现申请/审批/撤销与台账，不因有请假记录改变旧自由员工全额规则；余额不足处理依照显式政策。
- [ ] 验证旧请假、批量请假、销假、工时核准及权限回归。

## Task 4: 合同计薪与逐日加班共用计算

**Files:**
- Modify: `payroll/PayCalculator.java`、`PayrollSources.java`、`PayrollGeneration.java`、`PayrollCalculationService.java`、`PayrollDtos.java`、`PayrollLineage.java`、工资项目来源引用及验证所有者、`workmode/WorkModePayroll.java`。
- Create: `payroll/ContractGeneration.java`、`payroll/DailyWorkAllocation.java`及合同条款DTO/验证（置于个人有效方案和既有经济变更治理内）。
- Test: `DailyWorkAllocationTest.java`、`ContractGenerationTest.java`、对应PG工资链路及历史回归。

**Interfaces:**
- Produces: `List<DailyWorkAllocation> allocate(PayCalculator.Rule rule, List<PayCalculator.WorkDay> days)`，每项含workDate、normalSeconds、overtimeSeconds、paidLeaveSeconds、payable；由原PayCalculator消费同一函数，不在月表复制算法。
- Consumes: Task 2有效来源、Task 3例外、有效合同条款与对应来源版本。
- Produces: 合同计薪单位/项目及解释、条款缺失或不支持的明确issues；不生成出勤SourceRef充数。
- Produces: 新版来源证据（sourceType/sourceId、策略/安排/合同版本、自动或人工认定版本、指纹、可空actualSeconds）；直接替换旧工资SourceRef，不做旧工资快照解码，不把无排班来源填入scheduleId。

- [ ] 契约先明确合同结构化条款及治理：计薪单位、入离职、请假、加班包含/另计/不适用、适用项目；只支持明确实现的策略，缺失不默认为全额或零。
- [ ] 写逐日加班失败测试：同公司跨店、日周重叠、前月周上下文、不带薪假参与阈值、前序待核准导致暂计；拆分前后原月工资结果一致。
- [ ] 写合同测试：免打卡且无工时来源仍可按完整有效合同计发；包含不重复、另计有依据、缺条款阻断；月中变更未支持时准确阻断；旧自由规则不变。
- [ ] 写新版证据失败测试：安排来源没有scheduleId也能生成lineage；actualSeconds空值不显示实打0；项目source批准校验、重算指纹和重复来源检查统一使用新类型；删除旧工资兼容分支及只为旧解码存在的测试。
- [ ] 实现共用逐日分配和合同生成器，接入既有金额审批、指纹、确认冻结及补发/提前结薪边界。
- [ ] 运行原PayCalculator、PayrollGeneration、WorkModePayroll及工资治理/确认测试，验证新版创建并确认的快照不被后续操作改写。

## Task 5: 管理员月表、员工月历与异常办理

**Files:**
- Create: `attendance/month/AttendanceMonthDtos.java`、`AttendanceMonthService.java`、`AttendanceMonthRepository.java`和`api/WorkforceAttendanceMonthController.java`。
- Create: `src/modules/workforce/attendanceMonth.ts`、`components/AttendanceMonthGrid.vue`、`components/MyAttendanceCalendar.vue`、`pages/AttendanceMonth.vue`。
- Modify: `routes.ts`、`pages/AttendanceList.vue`、`pages/ApprovalList.vue`及所属导航，仅衔接本次入口。
- Test: `WorkforceAttendanceMonthPostgresTest.java`、精确安全白名单测试、`src/test/frontend/attendance-month-ui.test.mjs`。

**Interfaces:**
- Consumes: 有效员工/任职、Task 1方式/安排、Task 2来源状态、Task 3请假/权益、Task 4逐日加班；普通考勤权限不能读取工资金额或合同敏感明细。
- Produces: `GET /attendance-month`按员工分页，每位员工完整月数据；不按排班分页、不前端拉全量拼月表；合计范围明确。
- Frontend: `AttendanceMonth`拥有月份/筛选/分页及请求新旧保护；两个显示组件只接受DTO、发出选日/员工事件，业务核准复用原命令。

- [ ] 编写API契约：普通员工仅本人，管理员授权店范围；合法跨店聚合独立核验，范围不足明确未知；分页上限、扫描上限和错误不能静默截断。
- [ ] 先写失败测试：无排班但在职人员可见；部分店权限不泄露数据；分页不漏/重复员工；完整月及月份天数；正常留白、未安排、未来、进行中、缺卡、合同/自由不适用、同日多项分别显示。
- [ ] UI测试覆盖请求竞态、切店、异常办理后返回原筛选、批量部分失败、来源变化、空/无权/错误/加载和中英文。
- [ ] 实现月表与单人月历，管理端默认突出有异常人员并允许看全部；说明筛选只影响视图，不改变工资确认范围。
- [ ] 登录比较ScheduleList与新页公共对齐；验证桌面、实际约390/320px、长姓名/多标签/详情、中英文；记录真实尺寸和截图，不声称实体手机/GPS验收。

## Task 6: 链路验证、审查与交付

**Files:** 新增对应`docs/development`验收记录与`docs/release-notes`，更新API契约与本计划实际完成项。

- [ ] 使用运行时指针及隔离schema搭建四类合成员工、两店、完整合同/缺合同、部分假、跨月周与旧自由历史。
- [ ] 走通策略→日常判定→例外/请假→月表→月度核对→工资测算/重算/确认→本人查看；验证新版工资/付款确认保护；旧工资按清单清理后为空且员工、考勤及其他产品数据不变。
- [ ] 运行任务新增JUnit/PG、安全测试与既有相关回归，`node --import ./scripts/tests/workforce-register.mjs --test`指定本次及相关组件测试，`npm run build`、`git diff --check`；记录命令、数量、失败、未验范围。
- [ ] 按api-review/database-review/tdd-review/code-review记录审查结论，修复必要问题后只复核受影响范围。
- [ ] 按release-note写实际完成内容、验证和限制；未实现项保持待办，不写成已上线。不提交、不推送、不部署，除非用户另有明确授权。

## 本次文档落地审计

- 已依据本地源码确认：正常批量不等于自动正常；工资仅消费有效核准；日周加班在PayCalculator；fixed不等于新合同方式；LeaveWrite强制排班；个人额度台账尚需新增。
- 已把四类方式与原工资总表方案的重复/冲突语义对齐；保留旧自由规则与金额治理。
- 原始清单不因文档写入自动标为完成；本次实际实施状态见文首状态表。实施时每任务必须补全契约、读相关测试并取得新验证证据，不复用旧文档测试数量冒充本次验证。
- 依赖检查：Task 1策略/安排 → Task 2通用区间有效来源 → Task 3无排班例外适配 → Task 4新版来源及工资接入 → Task 5月表 → Task 6完整验收。权益台账跟随Task 3独立维护；新模式采用在链路完成前关闭，未定义后续类型不被提前消费。


## 迁移协调补充

用户要求处理线上编号冲突：正式V114–V120从已发布e2457db5补入，本轮4份未发布迁移顺延V121–V124。没有覆盖正式SQL或使用repair改写历史。新回归验证空库、V113和V120升级，旧开发验收库按冻结快照保留；见[迁移对齐记录](../../development/2026-10-31-workforce-migration-reconciliation.md)。应用层整合与生产发布不由迁移协调自动代办。
