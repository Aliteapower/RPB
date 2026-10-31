# 月度考勤与区间例外 API（2026-10-31）

基础路径 `/api/v1/stores/{storeId}/workforce`，统一 workforce AppGate。GET 无写入。

- GET `/attendance-month?month=2026-10&employeeId=&search=&positionId=&limit=30&offset=0`：按员工分页；本人只读自己，有 employee_manage 权限可读任职门店人员。返回 MonthPage(month,employees,total,limit,offset,canManage)，employees 为 EmployeeMonth(employeeId,employeeNo,name,days,totals)。日期完整，days 使用 EffectiveDay。仅返回当前门店日来源；其他店来源仅显示 scope_restricted，不泄露工时/快照。
- PUT `/attendance-month/refresh`：RefreshWrite(month,employeeIds)，必填明确 employeeIds（1..100，不由分页扩大范围），管理权限；同指纹不追加版本，变化追加。
- GET `/attendance-exceptions?from=&to=&employeeId=`：本人或门店管理范围，返回 IntervalException 列表。
- PUT `/attendance-exceptions/{id}`：ExceptionWrite(employeeId,companyId,workDate,kind,typeId,startAt,endAt,reason)，kind=leave/absence/extra_work，服务端从假别确定 paid/name，禁止提交 paid。本人申报或合法管理员登记，无排班也可提交，ID 为重放键，相同人员/操作者/载荷返回原响应，否则409。
- PUT `/attendance-exceptions/{id}/decision`：DecisionWrite(expectedVersion,status,note)，状态 approved/rejected/withdrawn/cancel_requested/cancelled/cancel_rejected。本人禁止审核；经理由总部审核；撤回/销假申请由原申请人操作。相同版本后续重试同动作/操作者/备注返回原结果，否则409。

EffectiveDay(sourceId,storeId,workDate,mode,state,sourceType,sourceFingerprint,version,actualSeconds,workSeconds,paidLeaveSeconds,unpaidLeaveSeconds,extraSeconds,snapshot,issues)。state=system_valid/human_valid/pending/stale/future/rest/unarranged/not_applicable/outside_employment。sourceType=actual/arrangement/not_applicable。actualSeconds 对安排来源及免考勤为 null；workSeconds 对不适用或未知为 null。snapshot 为经脱敏的服务端来源证据（不含 GPS、金额、地址）；月表 scope_restricted 快照清空。未配置权益政策时余额 unknown，不返回虚假剩余。

MonthTotals(workSeconds,paidLeaveSeconds,unpaidLeaveSeconds,absenceSeconds,extraWorkSeconds,pendingCount)，workSeconds 不确定时为 null，其他为事实时长；extraWorkSeconds 仅批准额外区间，不称为计薪加班。

错误：400 WORKFORCE_INVALID（参数/区间不合法）；403 WORKFORCE_FORBIDDEN/WORKFORCE_SELF_REVIEW/WORKFORCE_MANAGER_ADMIN_REVIEW；404 WORKFORCE_NOT_FOUND（跨租户/门店资源）；409 WORKFORCE_CONFLICT/WORKFORCE_EXCEPTION_OVERLAP。有效系统记录仍绑定来源指纹；读取发现来源变化返回 stale，不写判定。

实现验证清单：四类方式/未来/无安排/雇佣期、正常源自动生效与经理专审、GET无写入、重复刷新、来源变化、区间交集与未决阻断、本人禁审、总部经理审核、租户门店隔离、员工分页、重放状态机、PG迁移与约束。默认工作安排只有休息时长且无具体窗口时，部分例外与无薪休息交集未知，返回 BREAK_WINDOW_REQUIRED 阻断，不猜休息发生时间。

PUT `/attendance-days/{id}/review`：DayReviewWrite(employeeId,companyId,workDate,expectedVersion,sourceFingerprint,note)，禁止本人；经理专审仅总部可核准，特殊日工作可由有权管理员核准。只接收 WORKFORCE_MANAGER_ADMIN_REVIEW / CALENDAR_REVIEW_REQUIRED 候选；其他未决事项409。明确来源版本/指纹，重试同版本/同操作者/同备注幂等；来源变化409，后续读取显示stale。追加human_valid版本，不伪造排班。
月表snapshot仅包含timezone/companyId/absenceSeconds/phase/leaveBalanceState=unknown/exceptionSummaries（kind,typeName,status）；不含申请原因、审核人或原始位置。

既有排班优先于固定周安排，复用当时班次休息窗口；无窗口的时长配额遇部分假返回 BREAK_WINDOW_REQUIRED。店休及调休休息关联可解析为rest；公共假期有工作安排时由day review核对，不自动增加倍率。计薪加班另由同源工资日分配器投影，extraSeconds只代表批准的额外工作区间。

自动判定：AttendanceNormalEvaluationJob 默认每60秒运行（启动延迟60秒），仅扫描已明确采用新考勤政策的员工，使用门店/政策时区，从当地今天前7日至今天按100个员工范围游标分页。跨午夜班次等待来源结束；未来、未结束及未安排不追加有效记录。contract/free 不虚构工时；经理仍保留总部人工核准阻断。系统记录 initiator_kind=system、actor_id=null，系统审计不冒充管理员。相同来源指纹的人工核准保留，相同指纹/状态不追加版本；来源变化重新判定。商业 AppGate 必须通过，单范围失败不阻断其他范围。超过7天的历史来源改动仍使用显式 refresh，GET不写入。

计薪加班投影字段：EffectiveDay追加 `overtimeSeconds`（可空）与 `overtimeState`（current/provisional/unavailable/not_applicable/scope_restricted）；MonthTotals追加 `overtimeSeconds`、`pendingOvertimeSeconds`（均可空）。同公司跨店/跨月周采用工资共用日分配器；无有效个人政策、缺周来源或范围不足不得猜0。
`approval-readiness` 和 `work-summary` 的真实运行服务共用每日解析器；统计计数为员工日数，不再是排班数。ready仅计system_valid/human_valid/rest/not_applicable且issues为空；future/未安排/未决/来源变化不冒充有效核准。

区间归属：请假/缺勤先按workDate已有有效排班、否则该日有效政策工作区间验证；跨夜后半区间保留原工作日，不改填自然日。无工作安排时仅可登记本自然日事实，计量缺依据仍unknown。额外工作可在所选工作日登记；跨到翌日后才开始的区间须直接接续该日跨夜班结束。终点不越该班开始对应的下一日边界或更早的下一工作日安排起点，并禁止侵入另一个workDate工作区间。没有安排的额外区间以开始自然日归属、最长24小时且不得侵入下一日安排。
已批准额外工作结束前仍 `EXTRA_WORK_NOT_ENDED`，不能成为系统有效来源或提前支付；计划范围外若与已经消费的实际打卡区间重叠，同样 `EXTRA_WORK_OVERLAP`。四类模式均保留原排班请假事实；待审/销假待审阻断，批准后用同一净时长helper，月表不暴露理由或审核备注。旧请假与新例外的提交、批准双向检查有效/未决区间重叠。


## 2026-10-31 合并：权益账户及医疗凭证引用

`ExceptionWrite` 增加可选 `medicalReference`（仅请假可用，trim 后1–500字符）；服务端保存在区间例外记录，提交重放须匹配该引用。引用仅用于安全凭证编号/位置，不上传临床内容。已授权的本人/审核者例外列表可读取该引用；公开月表/月历摘要、工资公开响应不返回该字段。病假/住院假按正式假别映射审核，采用合同者批准时必须提供引用，缺少返回409 `LEAVE_MEDICAL_EVIDENCE_REQUIRED`。

已采用雇佣合规合同的带薪区间申请，必须有显式假别映射、同公司同年度且已核对合同版本的正式假期账户。与原排班请假、折现共用 `reserve/use/release` 和员工锁：申请预占，批准转使用，撤回/驳回/最终批准销假释放；销假待审不恢复余额。批准时重新量度已占用数量，来源变化返回409 `LEAVE_SOURCE_REVIEW_REQUIRED`。没有安排/明确休息依据不能猜净小时，返回409 `LEAVE_UNIT_POLICY_REQUIRED`；每日合同小时仅作天/小时换算，不伪造出勤。

跨年区间按门店时区1月1日切分，同一来源在各年度账户分别占用；跨午夜仍用原工作归属日的安排裁剪净区间。正式年结自动发放账户可使用1月1日权益，手工期初截止日仍须避免重复扣减。无合同的显式手工年度额度继续按原申请统计量度；手工额度跨年须拆单，返回409 `LEAVE_CROSS_YEAR_REVIEW_REQUIRED`，不静默扣错年度。
