import {useI18n} from 'vue-i18n'

export interface BreakRule {id:string;name:string;mode:'window'|'actual'|'quota';startOffset:number|null;endOffset:number|null;quotaMinutes:number|null;punchMode:'none'|'optional'|'required';paid:boolean}
export interface AnomalyRule {code:string;name:string;enabled:boolean;affectsBonus:boolean}
export interface AttendanceRules {version:1;breaks:BreakRule[];lateGraceMinutes:number;earlyGraceMinutes:number;breakGraceMinutes:number;graceMode:'full'|'excess';anomalies:AnomalyRule[];sourceTemplateId?:string|null;sourceTemplateVersion?:number|null}
export interface AttendanceRuleTemplate {id:string;name:string;enabled:boolean;version:number;rules:AttendanceRules}
export interface AttendanceRuleTemplates {items:AttendanceRuleTemplate[];headOffice:boolean}
/** The captured rules take precedence over dormant legacy fields in every shift summary. */
export function shiftRuleSummary(shift:{rules?:AttendanceRules|null},legacyText:string,english=false,duration?:number){
 const rules=shift.rules;if(!rules)return legacyText
 const summary=english?`Multiple breaks · ${rules.breaks.length} periods`:`多段休息 ${rules.breaks.length}段`
 if(!Number.isInteger(duration)||!duration||duration<0||duration>1440)return summary
 // Fixed unpaid windows alone define planned deductions. Quotas are comparison targets.
 let fixed=0,end=0
 for(const b of rules.breaks.filter(b=>b.mode==='window'&&!b.paid&&b.startOffset!==null&&b.endOffset!==null).sort((a,b)=>a.startOffset!-b.startOffset!)){const start=Math.max(0,b.startOffset!),to=Math.min(duration,b.endOffset!);fixed+=Math.max(0,to-Math.max(start,end));end=Math.max(end,to)}
 const time=(minutes:number)=>{const h=Math.floor(minutes/60),m=minutes%60;return english?(h?`${h}h${m?` ${m}min`:''}`:`${m}min`):(h?`${h}小时${m?`${m}分钟`:''}`:`${m}分钟`)}
 const span=english?`Shift span ${time(duration)} · Fixed unpaid breaks ${time(fixed)}`:`班次跨度${time(duration)} · 固定非计薪休息${time(fixed)}`
 const net=rules.breaks.some(b=>b.mode!=='window')?(english?'Final hours require actual punch review; quotas are not fixed deductions':'最终工时按实际打卡核准，额度不作为固定扣除'):(english?`Planned net work ${time(duration-fixed)}`:`计划净工时${time(duration-fixed)}`)
 return `${summary} · ${span} · ${net}`
}
export const anomalyCodes=['LATE','EARLY','MISSING','ABSENCE','BREAK_OVER','BREAK_SHORT','BREAK_MISSING','LEAVE'] as const
const words={
 manageTemplates:['管理模板','Manage templates'],backToShift:['返回班次编辑','Back to shift editor'],saveAndReturn:['保存模板并返回班次','Save template and return'],templateSwitchHint:['返回时保留未保存的班次内容；模板修改不会自动覆盖本班次规则。','Unsaved shift details are kept when returning. Template changes do not overwrite this shift.'],templateSaved:['模板已保存，请在下拉框主动选用；原班次内容已保留。','Template saved. Choose it from the list to apply; your shift details have been kept.'],
 view:['查看','View'], title:['考勤设置','Attendance settings'], enableRules:['启用多段休息与考勤规则','Enable multiple breaks and attendance rules'], legacy:['关闭时沿用原有休息设置；启用后以下规则优先，不会自动转换旧设置。','When disabled, existing break settings apply. Enabling uses the rules below without converting old settings.'],
 templates:['共享规则模板','Shared rule templates'], template:['选用模板（复制为本班次快照）','Use template (copy a snapshot for this shift)'], choose:['请选择模板','Choose a template'], noTemplates:['暂无已启用模板，可直接编辑本班次规则。','No enabled templates. Edit this shift directly.'], copied:['已复制模板；本地调整不修改原模板。','Template copied; local changes do not modify the template.'],
 add:['新增休息段','Add break'], remove:['移除','Remove'], name:['名称','Name'], mode:['休息方式','Break mode'], window:['固定时段','Fixed window'], actual:['按实际休息','Actual break'], quota:['额度休息','Break quota'], start:['开始时间','Start time'], end:['结束时间','End time'], nextDay:['次日','Next day'], quotaMinutes:['额度（分钟）','Quota (minutes)'], punch:['休息打卡','Break punches'], none:['不要求','Not required'], optional:['允许打卡','Optional'], required:['必须打卡','Required'], paid:['休息计薪','Paid break'], optionalWindow:['实际／额度休息可不限定时段；如填写必须同时填开始与结束。','Actual/quota breaks may omit a window. If used, both start and end are required.'],
 late:['迟到宽限（分钟）','Late grace (minutes)'], early:['早退宽限（分钟）','Early grace (minutes)'], breakGrace:['休息偏差宽限（分钟）','Break grace (minutes)'], graceMode:['超过宽限后的统计','After grace is exceeded'], full:['记录全部偏差分钟','Count all deviation minutes'], excess:['仅记录超出宽限分钟','Count excess minutes only'], anomaly:['异常与全勤资格','Anomalies and attendance bonus eligibility'], enabled:['启用','Enabled'], affectsBonus:['影响全勤资格','Affects attendance bonus'], anomalyHint:['异常仅标记及参与全勤资格判断，不自动扣工资。内置异常代码固定，可修改名称。','Anomalies flag records and affect bonus eligibility; they do not automatically deduct wages. Built-in codes are fixed; names are editable.'],
 LATE:['迟到','Late'],EARLY:['早退','Early departure'],MISSING:['漏打卡','Missing punch'],ABSENCE:['缺勤','Absence'],BREAK_OVER:['休息超时','Break overrun'],BREAK_SHORT:['休息不足','Short break'],BREAK_MISSING:['休息漏打卡','Missing break punch'],LEAVE:['请假','Leave'],
 invalid:['请核对规则：时段须在班次内且不重叠，额度为正整数，实际／额度休息须允许或要求打卡；名称及宽限不能为空或超出范围。','Check rules: windows must fit the shift and not overlap; quotas must be positive integers; actual/quota breaks need punches. Names and grace values must be valid.'],
 sample:['载入示例：11–23点，净工作8小时','Load example: 11:00–23:00, net 8 hours'], sampleHint:['示例：午餐13–14点、中场15–17点、晚餐20–21点，均不计薪、不要求打卡。仅主动载入时替换当前规则。','Example: lunch 13–14, mid-shift 15–17, dinner 20–21; all unpaid, no punches required. Replaces the current rules only when explicitly loaded.'], lunch:['午餐','Lunch'], middle:['中场休息','Mid-shift break'], dinner:['晚餐','Dinner'],
 newTemplate:['新增模板','New template'], edit:['编辑','Edit'], save:['保存模板','Save template'], cancel:['取消','Cancel'], readonly:['模板跨门店共享，仅总部管理员可新增、编辑或停用；门店经理可选用。','Templates are shared across stores. Only head office administrators can create, edit or disable; store managers can use them.'], empty:['暂无模板','No templates'], reference:['参考上班时间','Reference shift start'], referenceHint:['模板保存相对上班时间的分钟数，选用时随班次开始时间平移。参考时间仅用于编辑显示；可用范围为开始后24小时。','Templates store minutes relative to shift start, moving with the selected shift. Reference time only controls the editor display; the range is 24 hours from start.'], disabled:['已停用','Disabled'], snapshot:['来源模板版本','Source template version'], breaks:['休息段','Breaks']
} as const
export function useRestPolicyText(){const {locale}=useI18n();return {r:(key:keyof typeof words)=>words[key][locale.value.startsWith('en')?1:0]}}
export function defaultAttendanceRules(english=false):AttendanceRules{return {version:1,breaks:[],lateGraceMinutes:0,earlyGraceMinutes:0,breakGraceMinutes:0,graceMode:'full',anomalies:anomalyCodes.map(code=>({code,name:words[code][english?1:0],enabled:true,affectsBonus:true}))}}
export function copyAttendanceRules(rules:AttendanceRules):AttendanceRules{return JSON.parse(JSON.stringify(rules)) as AttendanceRules}
export function clockMinutes(clock:string){if(!/^([01]\d|2[0-3]):[0-5]\d$/.test(clock))return NaN;const [h,m]=clock.split(':').map(Number);return h!*60+m!}
export function shiftDuration(start:string,end:string,nextDay:boolean){return clockMinutes(end)-clockMinutes(start)+(nextDay?1440:0)}
export function offsetClock(start:string,offset:number|null){if(offset===null||!Number.isFinite(offset))return '';const value=((clockMinutes(start)+offset)%1440+1440)%1440;return `${String(Math.floor(value/60)).padStart(2,'0')}:${String(value%60).padStart(2,'0')}`}
export function offsetNextDay(start:string,offset:number|null){return offset!==null&&clockMinutes(start)+offset>=1440}
export function clockOffset(start:string,time:string,nextDay:boolean){return time?clockMinutes(time)-clockMinutes(start)+(nextDay?1440:0):null}
export function validAttendanceRules(rules:AttendanceRules,duration:number):boolean{
 if(rules.version!==1||!Number.isInteger(duration)||duration<=0||duration>1440||!['full','excess'].includes(rules.graceMode))return false
 if(![rules.lateGraceMinutes,rules.earlyGraceMinutes,rules.breakGraceMinutes].every(v=>Number.isInteger(v)&&v>=0&&v<=1440))return false
 const ids=new Set<string>(),windows:{start:number;end:number}[]=[]
 for(const b of rules.breaks){
  if(!b.id||ids.has(b.id)||!b.name.trim()||b.name.length>80||!['window','actual','quota'].includes(b.mode)||!['none','optional','required'].includes(b.punchMode)||typeof b.paid!=='boolean')return false
  ids.add(b.id)
  if(b.mode!=='window'&&b.punchMode==='none')return false
  const hasWindow=b.startOffset!==null||b.endOffset!==null
  if(b.mode==='window'||hasWindow){if(!Number.isInteger(b.startOffset)||!Number.isInteger(b.endOffset)||b.startOffset!<0||b.endOffset!<=b.startOffset!||b.endOffset!>duration)return false;windows.push({start:b.startOffset!,end:b.endOffset!})}
  if(b.mode==='quota'&&(!Number.isInteger(b.quotaMinutes)||b.quotaMinutes!<=0||b.quotaMinutes!>(hasWindow?b.endOffset!-b.startOffset!:duration)))return false
 }
 windows.sort((a,b)=>a.start-b.start);if(windows.some((w,i)=>i>0&&w.start<windows[i-1]!.end))return false
 return rules.anomalies.length===anomalyCodes.length&&anomalyCodes.every(code=>rules.anomalies.filter(a=>a.code===code&&!!a.name.trim()&&a.name.length<=80&&typeof a.enabled==='boolean'&&typeof a.affectsBonus==='boolean').length===1)
}
export function sampleAttendanceRules(english=false){const rules=defaultAttendanceRules(english);rules.breaks=([['lunch',120,180],['middle',240,360],['dinner',540,600]] as const).map(([key,startOffset,endOffset])=>({id:crypto.randomUUID(),name:words[key][english?1:0],mode:'window',startOffset,endOffset,quotaMinutes:null,punchMode:'none',paid:false}));return rules}
