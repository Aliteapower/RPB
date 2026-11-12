# 打卡至工资付款完整流程优化方案

日期：2026-10-30（Asia/Singapore）。项目：D:\RPB。

状态：用户已授权按方案分阶段本地实现。阶段1员工薪资关联/岗位公司默认已通过本地验证与独立审查，阶段2工时及项目来源已完成并通过独立审查，阶段3工作台已完成本地验证和逐阶段独立审查；最终集成审查发现的两项必要问题已修复并完成限定复核，Spec/Quality均APPROVE。新优化不提交、不推送、不部署。各接口已实现范围以对应API契约及开发记录为准，未完成设计不代表已支持。

## 1. 发布前置与本轮边界

上一批管理员改进 f67338c0、员工编号登录及停用账号改密 54fa032f 已部署；发布验收与备份清理记录已随 7de36226 提交。依据：[联合发布](../../development/WORKFORCE_ADMIN_BATCH_PRODUCTION_RELEASE_20261030.md)、[最终补充发布](../../development/WORKFORCE_LOGIN_FOLLOWUP_20261030.md)。

本轮开始前通过 SSH 只读执行既有 preflight.py：前后端均为 54fa032fc2ea3d7ee205c018da3d9142dd7b2eae，JAR SHA256 为 842d86cf1c33cfcea5aa2a66bb31afb957384afa67cd415339a37eea1223f534，index SHA256 为 34fcfa5b996ad398316aee94dd36cf4a1c9d671ada918ce568a328dd87e7f946；Flyway 106/106/0；rpb-backend active、PID 3410303、NRestarts=0、InvocationID c118a12988f94edd8e471b43b2df4684，与最终发布证据一致。仅核对版本、哈希、迁移和服务，没有重发、重启、改配置或写业务数据；本轮未重做生产页面、真机或银行验收。

上一批发布已收尾，可以进入本轮方案工作。现有钱包改动、临时脚本、私有资料及其他未跟踪文件保留，不纳入本轮。

## 2. 当前实现核对

下表是源码核对结果；已读取相关测试用例，但本轮没有重新运行应用测试，不能当作新一轮测试通过证据。

| 链路 | 已有能力及拥有者 | 本轮缺口 |
| --- | --- | --- |
| 员工资料 | EmployeeForm.vue 维护档案、岗位、账号及证件；PayProfileForm.vue 独立维护个人方案 | 员工编辑页没有薪资方案摘要及准确的员工/公司上下文跳转 |
| 岗位 | PositionDtos/PositionService 提供租户岗位、启停及默认考勤方式 | 没有按发薪公司关联默认薪资模板；考勤默认不是薪资默认 |
| 个人方案 | PayrollConfigurationService 保存生效日期、templateId/templateVersion、独立 rule、扣缴及付款偏好；已冻结引用方案禁止修改 | 来源展示不足；尚无岗位默认来源和个人覆盖的明确证据 |
| 打卡/补卡 | AttendanceCalculator/BreakCalculator 计算实际净工时；CorrectionService 保留申请流程 | 页面易把实际、核准、计薪口径混作一个“工时” |
| 异常/核准 | AttendanceJudgement 产生判定；ApprovalSources/ApprovalService 保存核准来源、版本、决定及秒数 | excused 不补回未工作秒数；异常决定与工资影响缺少明确解释 |
| 月度生成 | PayrollSources 捕获公司/月完整来源；PayrollGeneration 生成月薪/日薪单位；自由 fixed 模式全额 | 来源过期原因较粗；缺少统一资料缺失清单和可行动入口 |
| 差异/重算 | PayrollProgressService、PayrollChanges、PayrollIncremental 具备待重算、上次计算/上月双基线、行复用 | 待重算原因主要是 source/global/added/removed/baseline_unavailable；未细分证件、方案、核准等来源 |
| 跨店 | 公司/月批次唯一；按 scheduleId 捕获来源；PayCalculator 先合并同日工时再判日/周加班 | 缺少项目级溯源、显式重复来源诊断及跨店明细；现有保护不能直接等同任意重叠来源都已去重 |
| 工资项目 | Item 为 label/kind/amount/proration；Component 为 label/kind/amount；工资行包含 profile/input/sources | 没有稳定项目编号、逐项目来源、公式输入和重复引用判定 |
| 状态/付款 | draft/calculated/confirmed/published/voided；部分付款、上限、幂等、单次整笔冲正 | 工资状态与付款进度展示未统一；付款冲正不等于已发布工资金额更正 |

主要代码入口（均相对 D:\RPB）：

- 前端：src/modules/workforce/pages/{EmployeeForm,PositionList,PayProfileForm,PayrollBatchDetail}.vue；components/{PayRuleFields,PayGenerationFields,PayrollApprovalReadiness,PayrollChangesPanel,PayrollChangeComparison}.vue。
- 考勤/核准：src/main/java/com/rpb/reservation/workforce/{attendance,approval}/，重点 ApprovalSources、ApprovalService、ApprovalReadinessService。
- 工资：src/main/java/com/rpb/reservation/workforce/payroll/，重点 PayrollConfigurationService、PayrollSources、PayrollGeneration、PayCalculator、PayrollBatchService、PayrollProgressService、PayrollPaymentService、PayslipService。
- 数据约束：src/main/resources/db/migration/V091__workforce_payroll.sql；计算缓存及命令证据 V105；核准说明 V106。

## 3. 目标、取舍与保留规则

采用“现有模块内补关联、来源证据和工作台投影”的方案，这是本轮建议；不新建第二套员工/岗位/考勤/工资系统。仅加页面跳转无法补齐来源和防重；重建统一大服务会重复现有事务、权限与快照逻辑，因此不采用。

用户明确保留的规则：

1. 自由员工免打卡、免定位、免工时核准；当月有有效雇佣及有效方案，基本工资全额，月中入离职及无薪假不折算。整月无雇佣不生成。不虚构工作秒数、不自动生成迟到/旷工扣款或加班工资。
2. 方案配置跨月沿用，一次性项目不串月；模板变更不联动覆盖个人方案；个人例外保留。
3. 租户、公司、门店、AppGate、本人限制及员工工资隐私沿用。employeeManage 不等于薪资权限。
4. 已确认/发布工资及核准历史快照不回写。月中多个薪资方案、跨自由/普通类型等现有阻断保持，不悄悄采用月末规则覆盖整月。

不在本轮引入银行转账、CPF自动申报、通用公式脚本或新法规算法。本文描述合同口径和软件流程，不变更现有扣缴政策。

## 4. 员工编辑页关联个人方案

新增独立薪资摘要组件，由 payroll 应用服务提供员工/公司/指定日期的最小投影。复用 PayRuleFields、PayGenerationFields、PayProfileForm 及现有校验，不把个人工资字段塞入 EmployeeWrite 或 WorkforceEmployeeService。

摘要展示：发薪公司、当前有效方案、下一条未来方案、生效起止、模板来源名称及保存的版本、岗位默认/个人选择来源、个人覆盖项目、计薪方式、基本工资、固定津贴/扣款、加班规则及付款偏好。起止日期按同员工同公司下一条 effectiveFrom 推导，末条显示长期有效；按历史公司关系查，不以员工当前公司替代旧月份。

“工资明细”区分方案金额与当月计算结果。方案页显示规则金额；跳转月度工资单才显示已计算金额和状态，缺方案/缺计算不显示为0元。

有薪资权限才请求及显示金额；仅员工管理权限不发出工资数据请求，显示受限提示。摘要接口仍在后端执行 PayrollAccess，不能靠前端隐藏。普通员工继续只能查看本人已发布工资单，不能借编辑页读取个人方案内部对象。

新员工先保存档案，再进入绑定 employeeId/companyId 的方案表单；档案成功但方案未保存时显示“薪资方案未完成”，可继续办理，不伪称原子保存成功。跳转返回应保留已有员工草稿；表单保存仍使用个人方案独立 expectedVersion。调岗/调公司仅提示可能需要新方案，不自动改工资。

## 5. 岗位 × 发薪公司默认模板

在 payroll configuration 内维护 tenantId + positionId + companyId + effectiveFrom 的版本化默认映射，引用本租户同公司模板身份。映射维护时记录所见模板版本供审计，但不据此固定后续应用版本；每次应用预览读取最新启用模板并捕获完整快照及版本。岗位模块继续拥有岗位身份及考勤默认；工资模块拥有薪资金额和映射权限。岗位列表的工资设置只对有权公司提供入口，不能通过岗位 GET 泄露模板金额。

新建个人方案时：已有有效个人方案优先；无方案且有岗位/公司默认时预填模板；否则由有权管理员选择。预填后须补齐个人金额、扣缴资料、生效日期并显式保存为个人快照，单纯 GET、选岗位或保存员工不创建工资方案。当前 PayProfileForm 默认第一名员工/第一个模板的逻辑必须改为校验传入上下文，缺目标时不能误选他人。

个人可覆盖基本工资及固定项目；保留应用时模板名称/规则快照、默认映射ID/版本和个人差异证据，不能用“当前模板”反推旧覆盖。应用保存须校验预览的 expectedTemplateVersion 和 expectedDefaultVersion，服务端在同事务读取并保存模板基线、映射和个人规则差异；版本改变返回409并要求重新预览，不能将服务端新版本号与客户端旧规则拼接为来源。现有 saveProfile 只记录当前 template.version 与请求 rule，实施时须补这一契约；不能信任客户端自报的来源快照。只编辑既有个人方案而未重新应用模板时保留原来源，不偷偷换为当前模板。旧方案没有保存模板名称或应用时规则基线时，明确标记旧来源证据不完整，不拿当前模板冒充原始值。改默认/改模板只影响后续应用，既有个人方案不联动。批量覆盖不作为默认操作；以后需要批量应用时另有员工选择、差异预览及生效日期。

模板停用后不能新应用，旧引用仍展示；岗位停用/改名不改变工资历史。相同公司、岗位、日期不得多条默认；映射保存采用版本冲突、幂等、同事务审计和来源写锁。首次交付不回填旧员工、不根据同名岗位或工资金额猜默认。

## 6. 实际、核准与计薪工时

| 口径 | 来源 | 用途 |
| --- | --- | --- |
| 实际净工时 | 打卡/已批准补卡后的 IN/OUT，按冻结班次规则扣休息 | 呈现事实；另保留原始打卡事件及补卡历史 |
| 核准工时 | 有效 Approval 的 workSeconds，关联核准版本、来源指纹、核准人及异常决定 | 作为普通员工工资来源；未核准或过期不能冒充有效核准 |
| 计薪口径 | 核准工作秒数、带薪假、方案日/周阈值、月/日薪单位及本月调整 | 生成正常/加班秒数、计薪日与金额；无薪假单列 |

月/日薪以计薪日及比例表达，不强行给月薪编造“计薪小时”；时薪显示正常计薪秒数、带薪假秒数、加班秒数并分别解释倍率。自由员工显示“固定全额，工时不适用”，不展示内部1/1为实际出勤。

异常处理原则：

- LATE/EARLY/BREAK_OVER 等判定用于复核，不再从已经减少的实际/核准净工时重复减秒，也不自动生成同一缺勤的货币扣款。
- confirmed 表示异常认定；excused 表示异常豁免及资格处理，沿用现状，不自动补工时或认定带薪。要更改时间事实须走补卡/请假及重新核准，不能通过豁免暗改工资。
- MISSING 不能自动认定旷工；按现有授权及决定明确处理。休息/请假与打卡重叠继续阻断，不能靠工资端静默裁剪。
- 人工当月扣款须给出独立原因及来源；涉及已折减工时的同一事件时提示并阻止未解释的重复扣减。既有无来源扣款保留为“旧人工项目”，不自动删除或推测重复；新来源型扣款按事件引用检查。是否允许另行处罚、补薪或带薪豁免属于额外业务规则，本轮不自动启用。
- 核准后补卡、请假、班次/规则等来源变化，核准过期→重核准→重算；不能只刷新工作台即恢复有效。

例：计划8小时，实际工作7.5小时且无带薪假；核准7.5小时，时薪20，基本工资150。迟到确认不会再扣0.5小时或10元；豁免仍为150，除非有另外获批的带薪来源。纯月薪手工模式继续显示人工核定单位与说明，不升级成自动扣款。

## 7. 月度工作台

沿用公司/月选择及现有批次页，独立组件整合现有 readiness、changes、工资行异常和付款汇总，不新造另一批次真相源。

| 区域 | 内容及动作 |
| --- | --- |
| 资料缺失 | 缺/未生效方案、同月多个方案、扣缴/政策/证件待复核、工作方式不匹配；定位到有权维护入口 |
| 待核准 | 门店、员工、班次、缺卡/待补卡/待请假、待判定/过期；分开展示人数与班次数，自由员工标免核准 |
| 本月变化 | 新入/离职、调公司、方案/当月项目/核准/政策变化；独立于金额差异，金额未变也可有来源变化 |
| 金额差异 | 对上次计算和上月已确认/发布快照两套基线；显示增减员工、基本工资/应发/实发/公司成本差额；基线缺失显示不可比较 |
| 计算有效性 | 当前版本、计算时间、基线版本、需重算人数及结构化原因；点击定位来源 |
| 发布/付款 | 批次状态、员工可见性、应付/净登记已付/余额及付款冲正历史 |

过期原因在现有依赖键上细分：employee/employment、profile、monthly_input、attendance/correction、leave、approval、schedule/rules/calendar、workmode、statutory_policy、engine、baseline_unavailable。每项带 employeeId、相关来源ID、旧/新版本或指纹摘要、影响范围及动作入口，不输出隐私原始载荷。无法确定精确字段时显示“来源变化，需复核”，不杜撰具体原因。

资料缺失和阻断按原因汇总，卡片人数按 employeeId 去重；门店/班次计数另列。没有批次也能看到准备情况，创建后复用同一查询规则。付款资料缺失影响付款办理，不一概阻止工资计算；扣缴依据缺失则继续阻断确认。冻结批次的“当前来源已变”仅提示后续复核，不把历史工资标成可直接重算。

readiness 只是准备度，不能替代 PayrollBatchService 的确认条件。确认仍须月底之后、来源完整且有效、全量复算一致、expectedVersion 正确、权限重新验证及非本人限制。查询用一致读快照并标版本；跨组件数据版本不齐时提示刷新，不能把页面拼出的“齐全”当后端授权。

## 8. 工资项目溯源与跨门店归集

新增项目来源证据设计：稳定 itemId、origin（profile/monthly_input/approved_work/paid_leave/statutory）、sourceId/version、来源月份、涉及门店/班次、公式类型及版本、数量/单位/单价/倍率/折算策略、原金额/舍入结果。基本工资、加班、固定津贴、当月奖金/报销/扣款、适用扣缴都可展开。金额继续十进制字符串、工时整数秒，复用 PayCalculator 公式和既有舍入顺序。

旧项目没有 itemId 时不按 label 合并；历史快照保持原字节，显示“旧快照未保存项目级来源”，仅使用原 profile/input/sources 解释。新编辑版本再赋稳定编号；新增固定项目和当月项目可同名但来源独立。项目消费键按公司/月/员工 + origin + 来源ID/版本 + itemId或源事件明细ID划分，不能只以profileId去重而吞掉同方案的多项津贴，也不能因为同名同金额拒绝合法两笔奖金。基本工资/加班等派生项目另有固定类型键，不因展开多个来源而多生成金额。

归集边界为 tenantId/companyId/employeeId/month：一个公司一份有效常规月批次，一人跨店形成一条工资行。SourceRef 按 scheduleId 和核准版本唯一消费；来源属于历史排班 companyId，不用门店当前归属重分历史。

同日多店先合并正常工作事实再判日/周加班；上一月周上下文只消耗阈值，payable=false 不再付钱；月薪/固定津贴按个人公司方案生成一次，不按门店复制。门店成本分摊仅为报表投影，所有分摊合计须等于工资原金额，不生成第二次应付。

重复同一 schedule 引用应显式报告并阻止确认；不同 schedule 发生时间重叠应诊断并回到排班/核准处理，不擅自删掉一条或按总秒数猜交集。同公司跨店防重与跨公司雇佣分开：现有 PayrollSources 在单公司多段雇佣时阻断，但同月由A转B、各公司各有一个有效区间和fixed方案，可能分别生成全额基本工资，并不存在统一跨公司防重。本轮不改变全额规则，也不把该情形标为已解决；跨公司分配/阻断须另行确定合同归属规则，在真实工资办理前明确复核，不能由门店归集算法猜测。

项目溯源存入新计算版本快照，缓存引擎键随结构/算法变更升级；旧缓存缺证据则回退全算。员工工资单只暴露本人项目的必要计算解释，内部审批备注、他店员工、地址、完整CPF资料及账号信息继续剔除。

## 9. 计算、确认、发布、付款及更正

工资批次与付款进度为两个维度，不能用 paid 替代 published。

| 状态/动作 | 当前契约与目标展示 |
| --- | --- |
| draft | 可编辑当月输入；尚无有效计算结果；自动方案新建时现有逻辑可能直接生成 calculated |
| calculated | 有计算预览，可重算；存在阻断或来源过期时不可确认 |
| confirm → confirmed | 全量核对后冻结金额、来源和计算时间；不向员工发布；之后来源变化不回写 |
| publish → published | 只允许已确认批次，员工可读本人冻结工资单；发布不等于付款 |
| void → voided | 现有仅 draft/calculated/confirmed 且无付款；保留原批次及审计后可重建；published 不可通过此命令作废 |
| 付款登记 | 仅 published，正金额、不可超付、非本人、客户端paymentId幂等；属于登记，无真实转账 |
| 付款冲正 | 沿用原付款同额、同方式/银行、一次整笔冲正，再登记正确款项；不删除旧付款、不改变工资净额 |

付款进度按员工计算：未付款/部分付款/已付清；净已付=原登记减冲正，余额=冻结净额减净已付；批次以员工分布汇总。净额0单列“无需付款”，不虚构银行完成。响应丢失继续沿用原 commandId/paymentId 恢复，不能新建ID盲重试。

更正必须分开：

1. 未确认工资：修正来源→必要时重新核准→重算→确认。
2. 已确认未发布：现有可作废保留旧快照并重建；需明确理由及版本，不直接解冻覆盖。
3. 已发布工资金额错误：当前没有补发/追回或替代工资单契约。界面必须明确“金额更正尚未支持”，不能建议付款冲正代替工资更正。

后续更正建议采用关联原批次/员工/项目的差额单，独立草稿→计算→确认→发布，原工资单并存且可追溯；正差额补发，负差额进入待处理追回，禁止自动从未来工资扣款或产生负付款。本轮仅记录这一建议，不实施新差额批次。实施前必须明确补发归属月、扣缴重算/申报、追回授权和员工可见规则，并设计与当前公司/月唯一索引兼容的数据约束；不能把未确定规则留给代码猜测。

## 10. 模块、接口与数据演进边界

| 拥有者 | 扩展职责 | 保留边界 |
| --- | --- | --- |
| payroll configuration | 员工薪资摘要、岗位公司默认、个人来源快照 | 不修改员工档案/考勤事实 |
| attendance / approval | 三种口径的事实与核准证据、异常说明 | 不计算货币处罚 |
| PayrollSources / Generation | 唯一来源消费、来源诊断、计薪单位解释 | 不发布或登记付款 |
| PayCalculator / CalculationService | 公式及项目证据，保留金额/舍入 | 不持数据库锁或访问权限系统 |
| PayrollProgressService / Changes | 准备度及结构化过期/差异投影 | 不写业务事实，不替代确认 |
| Batch / Payment / Payslip | 状态守卫、付款聚合、脱敏展示 | 冻结历史不变；付款不等于转账 |

新增摘要/默认映射读取与写入入口由 payroll 应用服务负责，经现有 Controller/EndpointSupport 及 PayrollAccess；既有 personal profile、changes、readiness、actions、payments 接口优先兼容扩展。具体URL、错误码和DTO须在实施前补 API 契约，不把本方案字段名当成已发布接口。

预计默认映射需要新增迁移，项目来源可扩展版本化快照；当前最大迁移V106，实施时重新核对序号，不提前占用。新增表使用 tenant/company 外键、唯一约束及版本保护。旧JSON可选字段缺省、旧快照不重序列化、旧来源指纹不重写。默认映射更改若未应用到个人方案，不使既有个人工资错误失效；应用后的个人快照才是工资依赖。

继续使用现有来源共享/排他锁、事务外有限并行计算、提交时全量来源核对及同事务审计/回执。新增投影须批量读取，避免员工N+1；精确过期原因复用捕获证据，不为每个字段另跑全公司查询。

## 11. 建议实施顺序及验收矩阵（未执行）

1. 员工薪资摘要与上下文关联：抽出可复用组件，验证无薪资权不请求金额、新员工分步保存、员工/公司绑定准确、旧方案与未来方案并列。
2. 岗位公司默认：新表/契约/校验、显式应用、个人覆盖证据；覆盖跨公司/租户拒绝、停用、并发版本、模板或映射在预览后更新返回冲突、只编辑个人方案保留来源、默认变更不覆盖个人及历史。
3. 工时与项目来源：优先补真实场景测试再改投影；覆盖迟到/早退/休息/无薪假只影响一次、豁免不补秒、补卡重新核准、带薪假与实际重叠阻断、旧人工模式兼容。
4. 月度工作台：复用准备度与变化组件，覆盖未建批次、缺资料不伪零、双基线缺失、金额不变但来源过期、自由豁免、历史来源权限撤销、刷新竞态及窄屏。
5. 归集与付款状态：跨店同日/跨日/跨月周上下文、重复来源/重叠、固定项目只生成一次、同名合法项目、增量/全量等价；部分付款/超付/重复/冲正/丢响应恢复；已发布金额更正入口明确不支持。

持续回归：自由员工月中入离职/无薪假基本工资全额、整月无雇佣不生成、一次性不串月、模板不联动、同月多方案阻断、确认与来源并发、本人确认/付款限制、历史冻结、员工脱敏、跨租户/公司/店及AppGate拒绝。

优先复用已有测试：AttendanceCalculatorTest、AttendanceJudgementTest、WorkforceAttendanceRulesPostgresTest、WorkforceApprovalPostgresTest、PayCalculatorTest、WorkforceRecurringPayrollPostgresTest、WorkforcePayrollConfigurationPostgresTest、WorkforcePayrollPostgresTest、WorkforcePayrollProgressPostgresTest、WorkforcePayrollIncrementalPostgresTest，以及 payroll-recurring/progress/breakdown 前端测试。新增测试验证端到端行为，不仅匹配源码字符串。

本地PG验证须先读 D:\RPB\target\local-postgres-current.txt，按当前 port 连接127.0.0.1/postgres、postgres/空密码，使用隔离schema；指针缺失/失效先按本项目快速恢复指南修复，不替换到系统5432或其他工作区数据库。测试结果按实际记录，不将方案验收矩阵勾为完成。

## 12. 首次方案核对交付（历史记录）

- 已核对发布收尾、实际在线版本/制品/迁移/服务及现有源码/测试覆盖位置。
- 已记录六项缺口、复用边界、保留业务规则、状态和后续验收要求。
- 独立方案审查发现的模板版本竞态已补充服务端版本校验设计；跨公司自由员工可能分别全额已列为现有待定合同边界，未错误宣称已有统一阻断。
- 首次方案核对时只修改本方案及原长期方案的导航/状态说明；当时没有业务代码、接口、迁移、依赖或运行配置修改，没有提交、推送或部署。随后实施授权及状态见第13节。
- 已发布工资金额更正需要独立业务决定，不能作为本轮已实现能力；真机、真实银行付款及正式工资签收仍无本轮证据。

## 13. 后续实施授权与进度

用户随后授权先独立修复账号启停并提交部署，再按本方案本地分阶段实现；跨公司工资归属及已发布工资金额更正保持现状，不新增业务规则。账号启停607a53c7已上线，发布收尾52d20557已完成，见[账号状态发布记录](../../development/WORKFORCE_LOGIN_STATUS_RELEASE_20261030.md)。其后HEAD不变，不延用此次发布授权提交薪资优化。

[分阶段实施计划](../plans/2026-10-30-workforce-end-to-end-payroll.md)：阶段1已实现员工摘要、岗位公司默认、预览版本校验及个人来源证据，API见[配置契约](../../api/2026-10-30-workforce-payroll-configuration.md)；本地后端去重81项、前端去重769项、类型检查及独立审查通过（多次聚焦结果按未变代码复用，不宣称一次全量）。阶段2已完成工时/来源证据、事件防重、跨店/上下文保护及旧读取对照，见[来源契约](../../api/2026-10-30-workforce-payroll-lineage.md)；最终聚焦38项、专项UI18项、相关113项及补充35项执行有成功证据，按未变状态复用，不冒充单次全量。阶段3工作台、精确原因与付款状态已完成本地后端59项/最终投影9项、串行前端783项/入口专项23项及类型/构建验证，逐阶段独立审查通过；总体验收见[开发验收记录](../../development/WORKFORCE_END_TO_END_20261030.md)，最后跨阶段审查进行中。


## 13. 后续授权：租户模板发布、采用与金额审批（2026-10-30）

本节为用户本轮完整要求及执行依据，优先于前文只描述已实现能力的范围。目标为租户发布→现有/新增门店可见→显式启用可选→门店派生及个人金额变更审批→计薪采用实际有效批准版本。此轮仍不提交、不推送、不部署；保留前轮全部工作、钱包改动、个人资料、合成数据和历史工资。基线HEAD52d20557、1291份相关文件的当前字节已保存在target/workforce-template-governance-20261030/baseline，不能以git HEAD代替未提交基线。

### 13.1 已核实现状与归属

- workforce_pay_templates是tenant/company共享可变记录；PayrollConfigurationService.saveTemplate可由公司工资管理员调用，没有发布、门店作用域、不可变release或采用状态。模板变化不自动改已有PayProfile.rule，但会影响以后预览及应用。
- workforce_position_salary_defaults是tenant/company/position/effectiveFrom映射，显式预览后保存个人快照，映射变更不自动改员工；新机制必须保持这一点。
- PayProfile长期按日期选择；未批准草稿不可进入该表成为有效记录。PayrollSources.capture→PayrollIncremental→PayrollCalculationService是计算真相；PayrollBatchService确认有月底/完整来源/全量一致/非本人守卫；确认发布及付款快照必须保留。
- 金额写入口：PayrollConfigurationService.saveProfile；PayrollBatchService.input；CdacBulkService.apply直接configs.saveProfile；模板saveTemplate；PositionSalaryDefaultsService.save；所有调用这些方法的Controller、批量、重试及潜在导入均须审计。当前未找到独立工资导入写接口，不能据此假定未来绕过安全。
- 考勤RuleTemplateService、ShiftService、排班/补卡/请假/工时核准、WorkMode及生日/CPF事实会影响计薪；考勤模板与工资模板不是同一种作用域。法定政策仍租户集中管理。事实核准不等于合同金额审批，不能混状态或自创二次工时批准。

### 13.2 架构选择与权限

继续Controller→独立应用服务→仓储，新增治理子模块在workforce/payroll内，发布/采用、申请生命周期、影响预览、有效证据/写守卫、批量分别负责。复用WorkforceAccess、PayrollAccess、sourceWrite锁、WorkforceAudit、WorkforceJson、现有校验及计算器，不建立另一个工资计算器。

采用新不可变发布与请求表，保留原有效配置表及其JSON；不把共享可变模板当发布版本，也不把待审批方案写入原有效表。直接向原表加status会让旧读取/缓存容易混入草稿，不采用；为各门店复制一份无来源模板会失去母版升级/冲突语义，不采用。

租户发布/审批复用WorkforceAccess.headOffice（含混合store_manager角色的假总部身份仍不能通过）。门店模板可见/启用只面向已有门店管理权且AppGate/当前门店/公司作用域合法者；它与个人工资访问独立。个人及月度申请/影响预览继续要求已有PayrollAccess公司完整工资权，不以employeeManage或模板同步扩大薪资权限。审批时重检申请门店与公司全部必需来源权限。平台管理员不能借租户接口进入。

用户未要求禁止申请人同时具有总部审批身份；不新增自创的同人禁止规则，也不自动批准。所有有覆盖的变更都走显式提交/批准动作并留存两种身份。原工时/工资本人限制继续原规则。

### 13.3 发布与门店采用

发布母版属于tenant，Rule复用原DTO并通过原校验标准化；draft可编辑，published release不可编辑。母版可发布多版本，companyIds限定适用公司，storeIds固定选择现有门店；includeFutureStores仅使发布时间以后新建且公司匹配的门店可见，不把既有未选门店猜成新增。可见范围动态读取门店目录，无需自动为新店复制/启用。对新增门店仍须actor.canAccessStore/AppGate，发布不授账号权限。

门店预览明确母版id/version、完整规则、来源与适用范围后显式enable，记录不可变采用快照、门店版本与materializedTemplateId（原公司模板的治理派生引用）。重复同命令启用不复制；不同门店的派生模板id独立。采用仅为候选，不回填员工、不联动岗位映射。公司薪资页面仅列本店已启用治理模板及原合法legacy模板，不泄漏其他门店派生内容。

员工原样采用适用的已发布且本店已启用固定版本，后台比较标准化经济规则（不因新itemId等纯身份元数据误判覆盖），无需再次审批；有规则/金额覆盖必须申请。首次建立方案的身份/生日等事实仍按既有合法性校验，不把法定事实当自定义工资金额；已有方案的影响扣缴设置变更须纳入守卫审计，不能借CPF/CDAC入口修改净额。

发布新版本只给updateAvailable，不覆盖采用快照/个人方案/工资。门店可保留旧版；纯未修改采用升级也必须显式预览接受，且不自动更改员工。门店派生以baseReleaseVersion+patch+完整结果快照保存；新母版三方比较old base / local result / new base，列升级差异与双方同一经济字段不同结果的冲突；不自动合并后生效。有本店修改的升级形成审批申请，固定目标版本；后续母版发布不会偷偷改该申请。

### 13.4 申请聚合与不可变内容

个人聚合键为tenant/company/employee/effectiveDate；同日基本工资、固定项目及对应月份的手工金额在一份开放申请中合并展示与审批，不建每个字段/每月重复审批。门店派生模板申请以采用id为subject独立；不会把修改一个门店模板写到母版或其他采用。

状态draft→submitted→approved/rejected；submitted可withdraw，已提交内容及该revision固定。撤回后编辑产生新revision并保留原提交快照/操作历史。批准与生效为不同维度：approved未来日期为批准待生效，日期到达才由现有日期解析选用；不由后台定时任务重写历史。未变有效长期方案无需重复审批。

提交固定基础母版/采用版本、原有效方案及输入基线、员工/公司/生效日期、标准化patch及完整结果、理由、申请人/时间、revision及内容hash。审核页面显示before/after、员工、受影响月份、来源版本与所有修改项。审批重检当前权限/归属、原有效版本、日期与规则、已冻结引用和目标作用域；基线变化写stale状态及原因，不应用半份变更。单次事务写审批决定、所有有效版本/来源证明与审计，任一失败整体回滚；请求/审批命令按actor+原payload+commandId幂等。

不允许前端伪造approvalId后更换内容。有效写守卫只接受精确匹配服务器已批准内容的写入，且与批准落库同事务；普通旧入口不能凭“管理员身份”绕过审批。有多个原表写入时统一sourceWrite→配置/员工/批次锁的顺序，避免与确认锁倒置。

### 13.5 生效解析、兼容与统一写守卫

迁移只新增治理记录/证明表；把安装当时已有效的模板、个人方案及已有月度输入登记为legacy-valid基线证据，保留原JSON/版本，不伪造approvedBy/历史审批。原旧模板可保留选用历史，新建覆盖不能假装旧发布批准；要直接无覆盖采用须经真实发布/启用。

存量未确认批次及其输入在迁移时保留为legacy有效输入；已有计算不静默重算，来源引擎/证据改变时明示过期，确认须重算/校验。新创建的空草稿默认输入不代表已批准手工金额；新增金额/核定单位等经济字段由申请进入有效版本。待审批/驳回继续使用旧有效方案和输入并显示“尚未计入”，没有旧有效方案维持PROFILE_MISSING阻断，不能把未知变更当0。纯说明/核准标记与纯付款偏好等非金额变化也须核对是否影响计薪，守卫不能只看按钮或接口名称。

批准的future Profile可按effectiveFrom保存在原有效配置表，现有日期选择继续适用；月度批准版本必须稳定绑定company/employee/month而非仅临时batchId，避免作废重建/未来批次丢失批准金额或重新审批。输入解析与旧batch inputs版本统一说明；同月多工资标准原有不支持边界保留，不擅自新增拆月计算。

实际计算保存采用/申请id、revision、批准版本等可追溯证据（可选新增字段兼容旧JSON）；个人工资单继续脱敏内部审批理由/账号及证件原资料。来源指纹/增量缓存键纳入有效配置证明，未确认批次显示过期；确认/发布快照及付款台账不回写。发布新母版本身不改变旧采用/个人方案的有效证据。

金额入口须统一校验：旧template/profile/monthly/default路由、Cdac批量、所有仓储调用、导入/复制、重试。岗位映射/采用变更在当前架构只影响候选，若保持不修改现有个人快照则无需假造受影响员工；任何“应用到员工”都必须经过个人无覆盖直接采用或审批，不得批量改快照绕过。

考勤模板/班次规则/工作方式等规则可能改变计薪，阶段3必须逐一分类：事实核准维持原审查；新的可编辑合同计薪配置若未纳入金额批准，至少禁止其成为未批准经济覆盖的旁路并列明剩余入口。不能声称“所有影响工资的配置均已审批”而漏掉规则入口。法定政策保持总部集中且按原版本冻结。

### 13.6 影响预览、风险与批量

前后配置差异用十进制字符串展示。固定基础标准、固定全额项目的可确定标准差额直接给出；实际净额依赖考勤/加班/扣缴，用现有PayrollSources/PayrollCalculationService做只读假设替换，标“预估”并列来源时间/版本、月份、假设。规则或来源阻断时金额为null并列原因，不返回0差异；preview不保存batch、cache或正式工资。不存在批准过期预估后自动改金额的逻辑。

列表优先提示减薪、新增扣款和追溯生效。大幅变动没有授权阈值，显示绝对差额/比例供人工排序，不自行定义百分比阈值或自动批准。批准列表可按门店/月筛选；批量动作每份独立事务、重检、版本及审计，成功/失败/stale逐项返回；重试使用原请求身份，不把部分失败显示全部成功。

采用状态available/enabled/updateAvailable、审批状态draft/submitted/approved/rejected/withdrawn/stale、生效维度future/effective/history分别展示，不共用一个status冒充。

### 13.7 接口及迁移落点

沿用/api/v1/stores/{storeId}/workforce、WorkforceEndpointSupport写保护、401/403/404/409错误约定及LocalRuntimeSecurity精确路径。发布/采用新Controller只调应用服务，申请/影响/批量亦分独立Controller/服务；具体DTO和精确路径由阶段API文档固定后实现，不新增绕过旧安全的宽泛permitAll。

阶段1以V108新增母版草稿/不可变release/采用及幂等证据；阶段2以V109新增统一申请、提交revision、批准有效证明/月份输入等；阶段3必要兼容登记迁移按实际表结构分配下一版本，禁止改已存在V001–V107；阶段4优先复用既有幂等表/机制，不为UI状态新增冗余业务表。FK/唯一键带tenant和公司/门店归属；不对历史JSON批量补字段。迁移实际编号及上线适配以阶段记录为准，本轮只在pointer隔离schema验证。

验收矩阵、入口清单、实现/未覆盖事实由[分阶段治理计划](../plans/2026-10-30-workforce-template-governance.md)与阶段API/开发记录维护；勾选仅依据真实测试和复核。

### 13.8 Builder-check裁定（必须进入阶段实现）

1. 门店治理模板隔离在阶段1同时覆盖原salary-templates列表、受管模板按ID改写、Profile保存、岗位默认预览/保存；legacy公司共享模板与映射仍共享。公司共享映射引用其他店派生模板不能作为授权凭证，当前店不可用时明确拒绝或无候选，不复制/扩权限。
2. 锁顺序采用“审批只写稳定有效配置/月度记录，不在持sourceWrite共享锁期间更新batch”的方案；保留既有batch→source排他确认锁，来源FP让未确认批次自然过期。申请/subject专用聚合锁及唯一键负责并发，sourceWrite共享锁本身不串行化写者。不得引入source共享→batch与batch→source排他死锁。
3. 个人申请为长期日期主体＋可选profile结果＋零到多个month子项；纯monthly在服务器定位合法方案/日期基线，并锁company/employee/month防止改effectiveDate建立第二条并发覆盖。只改monthly不回写profile。stale状态写入须提交，不能随抛异常回滚。
4. legacy登记覆盖安装时已存在的合法未来方案。稳定月份解析优先精确approved有效版本，之后核实的legacy基线，最后无手工金额的缺省；新批准不能被旧batch输入覆盖。原批次legacy输入/冻结快照单独保留；多个作废旧批次有不同输入而无唯一有效基线时不可随意挑一个，应显示证据歧义并阻断受影响未确认工资。安装时唯一未作废batch的输入可登记为同月legacy有效基线；批准月度版本在作废重建/未来批次仍沿用。
5. 影响预览在PayrollSources来源捕获之前注入只读方案/输入假设，包含fixed筛选/加班上下文/自动单位，不能只替换捕获后的profile。新入口不写batch/cache、不放宽原批次未来月份限制；未知净额null。
6. 经济匹配覆盖CPF/CDAC、核定单位、月度项目及所有实际影响金额字段；纯备注/付款偏好/合法事实按实际性质分开。旧公开API不能通过伪approvalId、布尔permit或重放短路放行不同经济结果，仓储必要路径也需证明。
7. 未来门店范围以发布时包含inactive的完整既有门店集合为判据（不在集合且当前company/active匹配）；created_at及publishedAt保留审计，不再双重要求created_at晚于发布时间，以免并发创建事务早开始晚提交造成新店永久漏可见。不能把集合中的旧店重新激活当新增。读取/启用重检actor/AppGate，采用不授工资权。
8. 阶段3交付考勤/Shift/岗位attendanceMode/WorkMode.save与adoptPosition/生日事实/CPF/CDAC/法定政策分类表；原事实核准保持，发现独立合同经济覆盖须同一批准或禁止未批准覆盖计薪，不能只写风险后把必要旁路当完成。没有必要未修入口才能宣称核心金额写入闭合；其他未纳入审批的事实配置范围明确列出。

只读设计评审见target/workforce-template-governance-20261030/design-check.md。均为已授权业务的实现具体化，无需新业务确认。

### 13.9 用户确认的免审边界

用户于本轮明确回答：已有有效方案的员工切换到另一份已发布模板，只要工资/津贴等经济内容变化，须审批；首次原样采用和经济内容未变化免审。直接采用不是现有员工减薪/加薪旁路。此条优先于13.3可能被宽泛理解的“原样采用免审”，必须覆盖切换母版、采用升级、岗位默认选择及旧API等路径的后端测试。首次仍须是适用、已发布、本店已启用、固定版本且无个人经济覆盖的合法采用。

### 13.10 用户追加授权：奖金／AWS／AW及跨期补缴

用户已明确选择同时支持奖金计薪、补充分类与扣缴方案，并追加支持奖金／AWS／AW及跨期补缴。前文“原系统OW-only、不支持这些类型”仍是现状描述，不再是本轮最终排除范围。不能将现有ow_additional普通工资项目冒充AW；不能因原四阶段审批主线完成而宣称整轮目标完成。

本轮在原四阶段之后补充AW/奖金计薪、年度额度/复核及跨期补发/补缴阶段。CPF Board/MOM/SDL官方一手资料核对已完成，分类、工资所属期/决定期、政策版本、年度上下文、低工资带、舍入和工资/CPF登记分离的具体契约见13.11及target/workforce-template-governance-20261030/bonus-aw-arrears-design-check.md；该研究结论不代表实现或验证已完成。

跨期使用独立申请、来源期引用及补发/补缴记录，保留原已确认/已发布工资及付款JSON；不是暗改原单。真实银行转账、自动CPF申报仍不执行；跨公司工资归属不重新分配。工资/年度及已缴资料不完整时必须显示未知并阻断确认，不凭空给0或推算历史审批。

### 13.11 奖金与补缴财务契约（官方核对完成）

2026-10-30官方核对及源码证据见[完整设计核对](2026-10-30-workforce-bonus-aw-arrears-design.md)。实施阶段5涵盖报告F1/F2，阶段6涵盖F3/F4；报告中的必要行为和测试矩阵是本方案补充契约。

**分类与日期。** businessType（bonus/aws/retroactive_increment/omitted_wages）和OW/AW法定分类分开，不根据label推断，也不迁移旧ow_additional。事项固定eventId、服务期间、决定日期、合同应付日期、贡献月和结算月，实际工资付款日期单独登记。后来决定追溯加薪计决定产生权利月份的AW；原来已应付但行政漏计按原月原性质补差。AWS先支持合同已核定金额申请，不默认一个月工资或自行生成比例。[CPF分类](https://www.cpf.gov.sg/service/article/what-are-ordinary-wages-ow-and-additional-wages-aw)、[追溯加薪](https://www.cpf.gov.sg/service/article/are-retrospective-salary-increments-classified-as-ordinary-wages-ow-or-additional-wages-aw)、[MOM AWS](https://www.mom.gov.sg/faq/salary/am-i-entitled-to-aws-and-how-is-it-calculated)。

**计算与年度事实。** 复用StatutoryCalculator并增加V2明确rawOW/rawAW、cpfOWBase/cpfAWBase、bandWages和政策能力版本。CPF工资档依据未截顶OW+AW；月OW上限和年度AW额度分别约束基数，先合并同月未舍入CPF再总额四舍五入、员工向下取整、雇主取差。2026年度AW ceiling=102000−全年受CPF约束OW；不把2026政策套历史年。每员工/公司雇主/年度12个月actual/forecast/known-zero/unknown与AW事件顺序可追溯，未知不当0；年终或离职较早月按实际OW复核，保留原AW费率分段。同公司不同店共享额度；不擅自推断不同公司同一法定雇主。旧V1/纯OW结果与JSON保持兼容。[AW ceiling](https://www.cpf.gov.sg/service/article/what-is-the-additional-wage-aw-ceiling)、[2026费率](https://www.cpf.gov.sg/content/dam/web/employer/employer-obligations/documents/CPFcontributionratesfrom1Jan2026.pdf)。

**独立差额及分账。** 新增supplemental批次/项目/冻结快照、稳定wage_events、年度上下文/预测/额度assessment、statutory_liability_entries和remittance/allocations；所有归属FK含tenant/company。原普通批次唯一约束保留；新批准事项只能被消费一次。工资差额、员工现金应付、CPF员工责任、可追回金额、雇主CPF、SDL、CDAC、利息和退款待办分别保存。原工资已算未付继续原付款；原已付且仅少缴CPF不得再造工资。雇主错误遗漏员工扣缴不自动从以后工资追扣；原已扣未缴不再扣；合法追回凭据不足阻断相关操作。负差额留待退款/追回，不自动抵未来工资。利息先支持官方核定值及外部回执，不猜复杂计息、不扣员工净额。[员工份额追回](https://www.cpf.gov.sg/service/article/how-do-i-recover-the-employee-s-share-of-cpf-contributions)。

**月累计。** SDL个人月工资一次应用上下限、公司合计floor，新差额为新累计责任减已入账责任；缴纳余额另减实缴。CDAC复用有效政策/opt-out/override，自动按月全部工资重算差额，manual不重复；例如5000+2500目标2、已扣/缴1.50，差额0.50。纯CPF差额/额度复核不产生新SDL/CDAC。CDAC历史遗漏仅补发现当年1月起，保留原扣缴事实，不套CPF跨年规则。[补充申报示例](https://www.cpf.gov.sg/content/dam/web/employer/making-cpf-contributions/documents/Howtoperformadditionalsubmissionusingeedb.pdf)、[SHG漏缴](https://www.cpf.gov.sg/service/article/what-should-i-do-if-i-have-omitted-or-under-paid-self-help-group-shg-contributions)。

**有效性、接口与并发。** 新收入/补发复用统一审批精确payload/proof；现金与法定缴纳各自登记/冲正/余额。建议API supplemental-batches、statutory-year-contexts、aw-reconciliations、statutory-remittances，最终路径由阶段API文档固定。capture/cache/fingerprint纳入年度/月累计、政策/预测/批准版本，confirm重新验证；批次→source既有锁序保持，新年度/月锁统一顺序并发验证，审批不反向更新批次。缺原期政策、身份、年度事实、分类日期或责任证据时分别返回明确issue和null金额，阻止确认；特殊低工资AW复核/月中SPR缺公式证据的组合明确列出能力限制，不能算成0或正常成功。新模块不得回写原工资JSON或推定工资付款就是CPF已缴。

**公开官方计算器补证。** [研究报告附录B](2026-10-30-workforce-bonus-aw-arrears-design.md)及[官方合成试算证据](../../development/evidence/2026-10-30-cpf-aw-calculator-oracles.json)保留5组公开官方试算请求/HTTP200响应，不作为生产在线依赖。年度复核按每原月完整TW计算整数总/员工/雇主目标责任，减该原月已确认责任，再在结算月汇总整数差额；待缴另减实缴。跨55岁分币oracle为6540/员工3519/雇主3021，跨月原始金额合并后舍入会错1元。总差额0而员工+1/雇主−1仍须保留分项待处理，不能擅自互抵。已证实的T1正常档跨原月/跨龄/分币组合不再泛化阻断；缺原期事实/政策或未核实低工资等组合仍明确列项。[官方AW calculator](https://www.cpf.gov.sg/employer/tools-and-services/calculators/additional-wage-ceiling-calculator/)、[月度与自动年度退款差异](https://www.cpf.gov.sg/service/article/why-does-the-excess-cpf-from-the-additional-wage-ceiling-calculator-differ-from-the-automatic-refund-and-how-do-I-calculate-my-employee-s-share)。

低工资交叉已进一步收窄：按原始TW选档；TW≤50全免，50<TW≤500员工0、按受约束OW+AW及该政策雇主费率计算总额，TW500/基数440为75、TW100/基数40为7（法规联立推导）。未截短的TW500/600/750官方试算总额/员工分别85/0、162/60、278/150。当前只对500<rawTW≤750且AW年度额度实际截短的渐进交叉保留明确unsupported，不猜比例、不将未知计为0；正常档和未截短低工资不受此限制。见[附录C与证据边界](2026-10-30-workforce-bonus-aw-arrears-design.md)及[合成月度响应](../../development/evidence/2026-10-30-cpf-monthly-low-wage-oracles.json)。

**员工份额成因分流。** ordinary_employer_omission、ordinary_non_negligent_recovery、aw_statutory_reassessment、already_deducted_unremitted与未分类分开。CPF Act s7(9)/First Schedule允许符合条件的AW追加缴费在应缴年度结束后六个月内从应付工资收回，不能一概套普通雇主漏扣禁止规则；须有法定适用、原已扣/实际补缴和期限等证据。合法可收回仍不等于本轮自动扣款；保留原现金/快照，不新增自动追收业务。年度自动退款分配与AW月责任重算分开，混合费率退款缺官方明细不得猜。[CPF Act s7](https://sso.agc.gov.sg/Act/CPFA1953?ProvIds=pr7-)、[First Schedule](https://sso.agc.gov.sg/Act/CPFA1953?ProvIds=Sc1-)。

### 13.12 派生模板未来生效的具体解析

派生仍保留申请effectiveDate，不能为了候选模型简单而固定今天。批准可预先生成不可变采用/物化模板及proof；V109新增采用effective_from，存量采用保留最早日兼容且不改原JSON，新采用写Clock当前新加坡日期。latest为最大storeVersion的预留并发基线，currentAt(asOf)按effective_from倒序、同日storeVersion倒序选择当日有效候选；不能仅按最高版本提前切换，也不能让更早生效的高版本永久压住晚日期计划。

原LibraryEntry/Preview.adoption仍是当日当前有效采用，scheduledAdoptions另含未来采用与effectiveDate，界面分别显示当前/批准待生效。读取按当前公司/门店权限隔离；历史来源维护不要求升级。派生申请固定当日基底与latest并发基线；同日新批准取代、未来变化和公司迁移均须重检，已被取代的申请不能冒充当前有效。

现有无日期enable命令没有取消/覆盖未来计划的语义。有未来预留计划时不能仅给出最新版本号就暗中覆盖，后端拒绝并提示从明确申请路径处理；前端不自动以max(current,scheduled)规避冲突。该约束不影响无未来计划时正常启用已发布模板，亦不自动替换员工方案。

### 13.13 首次采用的法定事实与个人覆盖

首次免审同时要求工资Rule无个人经济覆盖，不能只比较基本工资而忽略CPF/CDAC。合法身份、出生日期、PR日期及证件属于既有人事/法定事实渠道；SPR既有标准GG模式可随原样模板采用，个人FG/FF选择须审批。非SPR不因无实际作用的SPR选项造成新的金额豁免路径，保存沿用既有合法规范化与身份校验。

CDAC不根据姓名、国籍或证件号猜族群。首次明确申报适用并按租户集中政策自动计算、或明确申报不适用，是既有标准适用性事实；自行manual/exempt等个人规则须审批。若发布模板已明确包含同额手工CDAC项目，且个人仅配套选择手工模式、规则与金额完全未覆盖，可按该固定源证明原样采用。外籍明确法定不适用不增加重复审批；新资料未声明适用性且无模板项目依据时不能静默按零，不完整资料须明确提示补齐。存量null继续依历史有效机制处理，不自动推断、改写或补造审批。

这一区分不放松后续变更：已有有效方案的CPF选项/CDAC规则或金额发生变化仍走申请，完整规范化内容及依据入固定快照。审批控制不代替原人事事实权限，也不以事实录入之名允许个人手工金额覆盖。


### 13.14 外部年度实际与普通工资源冲突保护

2026-10-31代码核对发现并纳入阶段5修复验收：已登记的外部历史月份及其AW/原责任，不能被随后创建的同月普通工资整月遮蔽，否则会错误释放年度额度并可能重复生成工资。此为保留来源、防重计薪的技术保护，不新增历史工资合并或跨公司归属规则。

- 所有普通工资行均核对公司/员工/月份的opening来源，包含OW-only；来源revision进入指纹。捕获和影响预估给出`ANNUAL_OPENING_PAYROLL_CONFLICT`，相关CPF/净额保持未知。
- 确认在既有source排他锁下重新捕获并拒绝冲突；计算后新增opening也必须令原计算过期。原正向“内部冻结或有效月度批准后不能用opening覆盖”保护保留。
- 外部实际、已占用AW及原责任继续作为年度依据，不自动合并、消除或更换事件编号。补发/补缴按阶段6独立来源链处理真正差额，原工资和付款保持快照。
- 必须用真实PG覆盖反向顺序、不同AW、OW-only、计算后新增opening以及事务交错，核对后续AW资格不增加、原JSON/责任/事件不变。阶段5代码评审在此必要保护通过前不判定完成。
