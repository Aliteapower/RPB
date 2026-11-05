# 员工休息与考勤规则契约

所有路径使用现有 `/api/v1/stores/{storeId}/workforce`，private/no-store、同源写保护、原AppGate与租户/门店范围不变。

GET `/attendance-rule-templates` 返回 `{items:[{id,name,enabled,version,rules}],headOffice}`；PUT `/attendance-rule-templates/{id}` 请求 `{expectedVersion,name,enabled,rules}`。共享模板按租户维护，仅总部管理员写；店管理员/经理在原门店权限下读取。0版新建、乐观版本、相同内容重试、停用保留，审计失败回滚。

Shift/ShiftWrite增加可空rules，旧字段保留；null继续旧模式，非空使用新规则。rules包括version=1、breaks、lateGraceMinutes、earlyGraceMinutes、breakGraceMinutes、graceMode(full/excess)、anomalies及可空sourceTemplateId/sourceTemplateVersion。breaks最多12项：id/name/mode(window/actual/quota)、startOffset/endOffset（自上班开始分钟，可空但必须成对）、quotaMinutes、punchMode(none/optional/required)、paid。固定时段必须有窗口；actual/quota必须允许打卡；quota额度必填。时段在班长内、不重叠，ID唯一，实际区间不得重叠或重复归属。模板最大1440分钟，复制后班次按实际班长校验，不由模板更新联动历史。

anomalies内置代码LATE/EARLY/MISSING/ABSENCE/BREAK_OVER/BREAK_SHORT/BREAK_MISSING/LEAVE，允许维护名称、enabled和affectsBonus；不配置自动罚款。宽限0..1440分钟，full表示超宽限后全计，excess只计超出部分。

ClockWrite追加breakRuleId；BREAK_START绑定可打卡休息项，Session追加breakStartedRuleId，Interval追加ruleId。旧构造器/旧JSON兼容，nullable字段不序列化；相同clock命令但不同ruleId拒绝重放。补卡保留区间ruleId并走原本人申请/他人审批。

Attendance及ApprovalRow追加breakSummary数组：ruleId/name/mode/punchMode/paid/plannedSeconds/actualSeconds（无卡为null）/remainingSeconds/overSeconds/shortSeconds/missing。固定时段扣实际出勤相交的计划区间；实际/额度按真实区间扣除；所有扣除合并求并集，带薪休息不扣。额度仅比较，不伪造实际休息。要求打卡却缺记录或休息未结束时阻断核准，须补卡。

ApprovalRow追加findings和fullAttendanceStatus(pending/eligible/ineligible)。Finding：code/name/seconds/status(pending/confirmed/excused/absence)/affectsBonus。迟到早退只计应工作时间（排除计划休息和已批准请假）；未结束、取消排班不误判旷工。无卡先MISSING，批准请假继续原审批。ApprovalWrite追加decisions:[{code,outcome(confirmed/excused/absence),note}]；LATE/EARLY/BREAK_OVER/SHORT需说明确认或豁免，MISSING只允许明确absence（管理员确认旷工，工时0）或先补卡；未结束/待申请/缺休息记录等其他阻断不能绕过。Approval记录保存decisions，与sourceFingerprint/expectedVersion绑定、原子保存、重试幂等。旧source快照不增加字段或改变现有null序列化形状。

全勤资格独立于事实工时和金额计算；确认异常是否影响资格由快照中affectsBonus决定，未核准结果标为pending。本轮不增加扣薪或奖金金额公式。
