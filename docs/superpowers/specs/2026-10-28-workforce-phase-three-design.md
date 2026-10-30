# 第三阶段设计：请假、日历、工时核准与跨店汇总

日期：2026-10-28；D:\RPB。用户已授权继续第三阶段，方案转写不新增审批节点。不提交、不推送、不部署。保留第一、二阶段及用户工作区内容。

## 目标与简化
建立排班→事实出勤/已批请假→门店核准→按发薪公司跨店汇总的链路。独立页面、复用现有 Auth、AppGate、workforce 委派和组织体系。年假余额、年资政策、法定加班及工资/CPF不在本阶段。
采用每个排班一张请假单，整班默认、可改部分时段；跨日/多日按涉及排班分别申请，避免复杂日额度换算。请假类型按门店维护，类型名称/是否计薪冻结在申请中；不预置或宣称法定待遇。历史审批不受政策编辑影响。

## 模块及页面
后端 leave（类型/申请/审批）、calendar（日历事件）、approval（来源计算/核准/汇总），独立 controller/service/repository，沿用 JDBC 和事务审计。
前端 pages/LeaveList、LeaveForm、LeaveDetail、LeaveTypeList/Form、CalendarList/Form、ApprovalList/Detail、WorkSummary。管理端请假/考勤设置/工时核准/汇总分开入口；员工我的请假与日历。全部双语、紧凑表格、短表单、状态/空值/错误/加载，CSS局限新模块。

## 请假
申请只允许本人，引用本店本人 scheduled 排班、enabled 类型，区间包含在排班起止内。微秒统一；原因1..500字；一个排班仅一张 pending 单，可有多张不重叠 approved 单。employee advisory lock串行，与原打卡/补卡/排班同锁。
状态pending→approved/rejected/withdrawn；pending本人可撤回，批准/拒绝须本店管理员及意见，禁止本人审批；approved撤回须本人发起新状态cancel_requested，再由另一管理员决定cancelled或退回approved（决定名cancel_rejected）；原始批准记录和审计保留。不直接删除已批请假。pending/cancel_requested 阻止核准；重叠批准请假拒绝。待审/已批/待销假请假阻止排班修改/取消。
新建UUID重试同原参数返回已有；不同参数409。决策expectedVersion，已达到相同状态/同reviewer/同note重试返回；其他409。可销假需employee仍有账号绑定，不要求仍当前分配以保留历史处理。

## 日历
按门店维护日期事件 public_holiday/store_rest，名称1..100，enabled开关、expectedVersion、同日同类型唯一。公众假期由管理员按官方资料人工维护，本阶段不硬编码法定日期/补休日或自动抓取。门店休息日是日历标记，不自动删除已有排班、扣薪或当作员工请假。列表支持31天并附排班与出勤计数；员工仅本人计数，无其他员工名单；设置写入仅管理员。
整班以排班workDate归属月/日，跨午夜不拆成两班，时长按真实Instant。公众假期/休息日以workDate标记，后续工资阶段才判断法定待遇。

## 核准与时长
每排班核准，月列表以from/to（最多31天）展示，已取消不纳入。详情同时展示事实工时、已批带薪/无薪请假、异常、最新核准及历史版本。核准只确认现有来源，不能凭空改工时；缺卡通过补卡，差异通过请假/规则源修复。
纯计算器：整班计划工时复用AttendanceCalculator；带薪休息不扣；window请假扣实际休息窗口交集；duration没有休息时间位置，按请假毛秒/排班毛秒比例分摊固定休息（向下取整到秒），界面说明；manual未计划具体休息，计划请假按毛时长，实际休息仍按打卡/补卡。多请假合并后计算总量再按带薪区间划分，保证总额不超计划计薪秒数。
实际IN/OUT区间与请假任意交集标记LEAVE_ATTENDANCE_OVERLAP（包括休息，采取保守人工核对）；未结束/超长/缺卡、未结束排班、待审补卡/请假/销假均不可核准。整班已批请假可无打卡；部分请假无任何出勤仍缺卡。休息日有排班仅提示，不阻断。不给工资金额或法定加班结论。
保存核准快照（员工/公司/门店/排班、workSeconds、paidLeaveSeconds、unpaidLeaveSeconds、日历标记、sourceFingerprint、reviewer/note/time/version）；SHA256来源包含排班/有效session/全部补卡版本/全部请假版本/日历版本。核准后任何来源变化即requiresReview，旧快照不改且汇总明确排除。工资未来只读有效核准。
核准必须另一管理员，expectedVersion+sourceFingerprint防过期。先store calendar lock，再employee lock，再重算来源。相同版本/来源/审核者/意见重试返回；重核准新增版本留历史。

## 汇总和隔离
store视图本店管理员；company视图仅现有总部且companyAllowed，按schedule.company_id归属（历史快照），包括该公司员工跨店支援记录。每个涉及门店都须当前可管理且AppGate通过，否则拒绝整个公司汇总，不静默遗漏。员工看本人核准详情，不能访问管理汇总。
汇总按employee+company+store分组，明确排班数、有效核准数、需核准数、有效核准事实/带薪请假/无薪请假秒数；未核准/过期不混入合计。不同公司不混算、不涉及公司间结算。当前阶段不再做额外公司最终锁定批次，留待第四阶段工资批次。

## 数据库/API审查
V090仅新增workforce_leave_types、workforce_leaves、workforce_calendar_events、workforce_approvals四表。均tenant/store复合FK；leaves/approvals关联排班复合FK（含employee），类型关联复合FK，审批版本history主键(schedule_id,version)。状态check/时间check/唯一pending与calendar日期类型，索引覆盖范围查询。无旧表数据重写、订阅/权限默认授予变更。
API详见docs/api/2026-10-28-workforce-phase-three.md；沿用private,no-store、同源header、JSON错误，读写均验证AppGate/tenant/store。不新增宽泛security allowlist。
架构审查：项目无architecture-review技能文件，依据现有模块边界和code-review检查单评估；新模块只读取考勤来源，不耦合其他产品；phase2排班used检查加入leave引用是必要接口变化。

## 必须验证
真实pointer PG隔离schema：整班/部分/跨夜/无薪请假、权限/自审/重放/重叠/销假、policy快照、日历变动过期、补卡后过期、并发核准与变更、审计回滚、跨店同公司/不同公司汇总及受限门店/AppGate拒绝。API同源及错误，前端双语/空态/加载/切店/重复点击。阶段1/2及原权限/AppGate回归。仅本地验证，不宣称真实设备/现场或薪资合规验证。
