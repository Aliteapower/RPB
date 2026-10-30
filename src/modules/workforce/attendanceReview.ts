import type {Home,Shift} from './types'
import type {ApprovalRow,ReviewDecision} from './phaseThreeTypes'
export function punchBreaks(shift:Shift|undefined){return shift?.rules?.breaks.filter(b=>b.punchMode!=='none')||[]}
export function absenceAllowed(row:ApprovalRow){const anomalies=row.schedule.shift?.rules?.anomalies;return !anomalies||anomalies.some(a=>a.code==='ABSENCE'&&a.enabled)}
export function reviewEditable(row:ApprovalRow,home:Pick<Home,'employeeManage'|'myself'>|null){return !row.exempt&&!!home?.employeeManage&&home.myself?.id!==row.schedule.employeeId&&(!row.approval||row.requiresReview||!!row.findings)}
export function reviewDecisionsValid(row:ApprovalRow,decisions:ReviewDecision[]){
 const findings=row.findings?.filter(f=>f.code!=='LEAVE'&&(f.status==='pending'||['LATE','EARLY','MISSING','BREAK_OVER','BREAK_SHORT'].includes(f.code)))||[]
 return findings.every(f=>{
  const d=decisions.filter(d=>d.code===f.code)
  return d.length===1&&!!d[0]!.note.trim()&&d[0]!.note.length<=500&&(f.code==='MISSING'?absenceAllowed(row)&&d[0]!.outcome==='absence':['LATE','EARLY','BREAK_OVER','BREAK_SHORT'].includes(f.code)&&['confirmed','excused'].includes(d[0]!.outcome))
 })
}
export function reviewAllowed(row:ApprovalRow,home:Pick<Home,'employeeManage'|'myself'>|null,decisions:ReviewDecision[]){
 if(!reviewEditable(row,home)||!reviewDecisionsValid(row,decisions))return false
 const absence=!!row.findings?.some(f=>f.code==='MISSING')&&decisions.some(d=>d.code==='MISSING'&&d.outcome==='absence')
 return row.issues.every(code=>code==='MISSING_IN'&&absence)
}
export function restoreReviewDecisions(row:ApprovalRow):ReviewDecision[]{
 const saved=row.approval?.sourceFingerprint===row.sourceFingerprint?row.approval.decisions||[]:[]
 return (row.findings||[]).filter(f=>['LATE','EARLY','MISSING','BREAK_OVER','BREAK_SHORT'].includes(f.code)).map(f=>{const old=saved.find(d=>d.code===f.code);return old?{...old}:{code:f.code,outcome:'' as ReviewDecision['outcome'],note:''}})
}
const words={absenceDisabled:['本班次未启用旷工判定，缺卡只能先补卡或请假。','Absence decisions are disabled for this shift. Correct missing punches or request leave first.'],breakSummary:['休息明细','Break summary'],planned:['计划','Planned'],actual:['已休','Taken'],remaining:['剩余','Remaining'],over:['超额','Over'],short:['不足','Short'],unrecorded:['未记录','Not recorded'],missing:['需补休息卡','Break correction required'],paid:['计薪','Paid'],unpaid:['不计薪','Unpaid'],review:['异常判定','Attendance review'],confirmed:['确认异常','Confirm finding'],excused:['豁免','Excuse'],absence:['确认为旷工','Confirm absence'],pending:['待判定','Pending'],eligible:['符合','Eligible'],ineligible:['不符合','Ineligible'],bonus:['全勤资格','Attendance bonus eligibility'],bonusHint:['仅记录全勤资格，不自动扣减工资。','Eligibility only; no automatic wage deduction.'],absenceHint:['缺卡需先补卡或请假；仅可确认为旷工，事实工时将为0，不自动扣款。','Correct missing punches or request leave first. Confirming absence sets attendance hours to 0 without automatic deductions.'],breakMissingHint:['休息缺卡或尚未结束，请先补卡；不会按标准时长猜测实际休息。','Correct missing or open break punches first; actual breaks are never inferred from planned duration.'],selectBreak:['所属休息段','Break rule'],legacyBreak:['旧记录（未关联休息段）','Legacy record (unassigned)'],explanation:['判定说明','Decision explanation'],choose:['请选择','Select'],retry:['重试原核准请求','Retry original approval'],retryHint:['提交结果尚未确认，表单已锁定；重试将发送同一核准请求。','Approval result unconfirmed. Draft locked; retry sends the identical request.'],window:['固定时段','Fixed window'],actualMode:['实际休息','Actual break'],quota:['额度休息','Break quota'],leaveConfirmed:['请假已审批','Leave approved']} as const
export function reviewText(key:keyof typeof words,english=false){return words[key][english?1:0]}
export function issueText(code:string,english=false){const labels:Record<string,[string,string]>={BREAK_RECORD_MISSING:['休息缺卡，需补卡','Missing break punch; correction required'],BREAK_MISSING:['休息缺卡，需补卡','Missing break punch; correction required'],OPEN_BREAK:['休息尚未结束，需补卡','Break still open; correction required']};return labels[code]?.[english?1:0]||code}

