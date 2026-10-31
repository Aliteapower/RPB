export interface EffectiveDay {sourceId:string;storeId:string;workDate:string;mode:string;state:string;sourceType:string;sourceFingerprint:string;version:number;actualSeconds:number|null;workSeconds:number|null;paidLeaveSeconds:number;unpaidLeaveSeconds:number;extraSeconds:number;overtimeSeconds?:number|null;overtimeState?:string;snapshot:Record<string,unknown>;issues:string[]}
export interface MonthTotals {workSeconds:number|null;paidLeaveSeconds:number;unpaidLeaveSeconds:number;absenceSeconds:number;extraWorkSeconds:number;pendingCount:number;overtimeSeconds?:number|null;pendingOvertimeSeconds?:number|null}
export interface EmployeeMonth {employeeId:string;employeeNo:string;name:string;days:EffectiveDay[];totals:MonthTotals}
export interface MonthPage {month:string;employees:EmployeeMonth[];total:number;limit:number;offset:number;canManage:boolean}
export interface IntervalException {id:string;employeeId:string;companyId:string;storeId:string;workDate:string;kind:string;typeId:string|null;typeName:string;paid:boolean;startAt:string;endAt:string;reason:string;medicalReference?:string|null;status:string;applicantId:string;reviewerId:string|null;reviewNote:string|null;version:number}
export function secondsText(n:number|null|undefined,en=false){return n==null?(en?'Unknown':'未知'):`${Number((n/3600).toFixed(2))} ${en?'h':'小时'}`}
export function dayBadges(d:Pick<EffectiveDay,'state'|'issues'|'paidLeaveSeconds'|'unpaidLeaveSeconds'|'extraSeconds'>&Partial<Pick<EffectiveDay,'overtimeSeconds'|'overtimeState'|'snapshot'>>,en=false){
 const labels:Record<string,string>={pending:en?'Pending':'待处理',stale:en?'Review required':'待复核',future:en?'Future':'未到日',in_progress:en?'In progress':'进行中',rest:en?'Rest':'休',unarranged:en?'Unarranged':'未安排',outside_employment:en?'Outside employment':'非任职期',scope_restricted:en?'Scope restricted':'范围不足'}
 const a:string[]=[];if(d.state==='future'&&d.snapshot?.phase==='in_progress')a.push(en?'In progress':'进行中');else if(labels[d.state])a.push(labels[d.state]!);else if(!['system_valid','human_valid','not_applicable'].includes(d.state))a.push(en?'Unknown — review required':'未知，须核对')
 if(Number(d.snapshot?.absenceSeconds)>0)a.push(`${en?'Verified absence':'已核实缺勤'} ${secondsText(Number(d.snapshot?.absenceSeconds),en)}`)
 for(const item of (Array.isArray(d.snapshot?.exceptionSummaries)?d.snapshot.exceptionSummaries:[]) as {kind:string;typeName?:string;status:string}[]){if(['pending','cancel_requested'].includes(item.status))a.push(`${item.kind==='leave'?(item.typeName||exceptionLabel('leave',en)):exceptionLabel(item.kind,en)} · ${exceptionLabel(item.status,en)}`)}
 for(const issue of d.issues||[])a.push(/MISSING|OPEN|PUNCH/.test(issue)?(en?'Missing punch — review':'缺卡待核对'):/SCOPE/.test(issue)?(en?'Scope restricted':'范围不足'):(en?'Review required':'待复核'))
 if(d.paidLeaveSeconds)a.push(`${en?'Paid leave':'带薪假'} ${secondsText(d.paidLeaveSeconds,en)}`)
 if(d.unpaidLeaveSeconds)a.push(`${en?'Unpaid leave':'无薪假'} ${secondsText(d.unpaidLeaveSeconds,en)}`)
 if(d.extraSeconds)a.push(`${en?'Approved extra work':'批准额外工作'} ${secondsText(d.extraSeconds,en)}`)
 if(d.overtimeSeconds)a.push(`${d.overtimeState==='current'?(en?'Overtime':'计薪加班'):(en?'Provisional overtime':'暂计加班')} ${secondsText(d.overtimeSeconds,en)}`)
 if(d.overtimeState==='unavailable')a.push(en?'Overtime unknown':'加班待依据')
 return [...new Set(a)]
}
export function exceptionLabel(v:string,en=false){const labels:Record<string,[string,string]>={leave:['请假','Leave'],extra_work:['额外工作','Extra work'],absence:['缺勤','Absence'],pending:['待审','Pending'],approved:['已批准','Approved'],rejected:['已拒绝','Rejected'],withdrawn:['已撤回','Withdrawn'],cancel_requested:['销假待审','Cancellation pending'],cancelled:['已取消','Cancelled'],cancel_rejected:['销假拒绝','Cancellation rejected']};return labels[v]?.[en?1:0]||(en?'Unknown':'未知')}
