# 第三阶段 API 合约

基址 /api/v1/stores/{storeId}/workforce；所有响应 private,no-store，所有PUT X-RPB-Workforce:1。同源保护/身份/AppGate与现有产品一致。400 WORKFORCE_INVALID；401 WORKFORCE_UNAUTHENTICATED；403 WORKFORCE_FORBIDDEN（或AppGate原拒绝码）；404 WORKFORCE_NOT_FOUND；409 WORKFORCE_CONFLICT、WORKFORCE_LEAVE_OVERLAP、WORKFORCE_APPROVAL_BLOCKED；500 WORKFORCE_PERSISTENCE_ERROR。

统一决策 DecisionWrite {expectedVersion,status,note}。新建expectedVersion=0，后续传当前版本。from/to均ISO日期、含首尾、最多31天。

|方法|路径|请求/返回|
|---|---|---|
|GET|/leave-types|LeaveType[]，员工也可读|
|PUT|/leave-types/{id}|{expectedVersion,name,paid,enabled} → LeaveType|
|GET|/leaves?from&to|Leave[]；管理员本店，员工本人；最多1000，超过拒绝要求缩小日期|
|PUT|/leaves/{id}|{scheduleId,typeId,startAt,endAt,reason} → Leave|
|PUT|/leaves/{id}/decision|DecisionWrite → Leave|
|GET|/calendar?from&to|CalendarPage {items:CalendarEvent[],days:CalendarDay[],timezone}|
|PUT|/calendar/{id}|{expectedVersion,date,kind,name,enabled} → CalendarEvent|
|GET|/approvals?from&to|ApprovalRow[]，本店管理/本人；最多1000，超过拒绝|
|GET|/approvals/{scheduleId}|ApprovalDetail {row,history:Approval[]}|
|PUT|/approvals/{scheduleId}|{expectedVersion,sourceFingerprint,note} → Approval|
|GET|/work-summary?from&to&companyId(可选)|Summary {items:SummaryRow[]}；无companyId本店，有则整公司（校验全部来源店）|

LeaveType {id,name,paid,enabled,version}。
Leave {id,scheduleId,employeeId,employeeName,typeId,typeName,paid,startAt,endAt,reason,status,applicantId,reviewerId,reviewNote,version,createdAt}。status pending/approved/rejected/withdrawn/cancel_requested/cancelled；决策另支持cancel_rejected返回approved。待审本人withdrawn；已批本人cancel_requested；另一管理员处理approved/rejected/cancelled/cancel_rejected。GET无单张，详情从日期范围列表匹配（路由保留from/to）。
CalendarEvent {id,date,kind,name,enabled,version}；CalendarDay {date,schedules,attended}，统计取消班不计；员工统计仅本人。kind public_holiday/store_rest。
Approval {scheduleId,version,sourceFingerprint,workSeconds,paidLeaveSeconds,unpaidLeaveSeconds,snapshot,reviewerId,note,approvedAt}；snapshot为ApprovalSource {schedule,session,corrections,leaves,calendar}的历史完整来源。ApprovalRow {schedule,storeId,workSeconds,paidLeaveSeconds,unpaidLeaveSeconds,issues:string[],sourceFingerprint,approval:Approval|null,requiresReview:boolean}；ApprovalDetail {row,history}。
SummaryRow {employeeId,employeeName,companyId,storeId,storeName,schedules,approved,pending,workSeconds,paidLeaveSeconds,unpaidLeaveSeconds}；只累计未过期且无阻断的有效核准。

重放：新建id且同参数返回原记录（type snapshot不因后来编辑改变）。相同id不同参数409。设置写入expectedVersion保护，相同最后写入可重试；决策只接受同审核者/意见/目标状态重试。核准相同fingerprint/审核者/意见可重试，其他版本冲突409。
