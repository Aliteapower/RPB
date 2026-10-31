import type {WorkAssignment} from './workMode'

export type PolicyStatus='current'|'upcoming'|'past'
export interface AssignmentGroup {assignment:WorkAssignment;entries:WorkAssignment[];status:PolicyStatus;until:string|null}

/** Compact consecutive equivalent records for display only; preserve every dated version. */
export function groupAssignments(assignments:WorkAssignment[],serverDate:string):AssignmentGroup[]{
 const sorted=[...assignments].sort((a,b)=>a.effectiveFrom.localeCompare(b.effectiveFrom)||a.version-b.version)
 const current=sorted.filter(a=>a.effectiveFrom<=serverDate).at(-1)
 const signature=(a:WorkAssignment)=>JSON.stringify([a.mode,[...(a.allowedModes??[a.mode])].sort(),a.origin,a.positionId,a.positionVersion])
 const groups:AssignmentGroup[]=[]
 for(const a of sorted){
  const status:PolicyStatus=a.effectiveFrom>serverDate?'upcoming':a.id===current?.id?'current':'past'
  const last=groups.at(-1)
  if(last&&last.status===status&&signature(last.assignment)===signature(a))last.entries.push(a)
  else groups.push({assignment:a,entries:[a],status,until:null})
 }
 groups.forEach((g,i)=>{g.until=groups[i+1]?.assignment.effectiveFrom??null})
 return groups.reverse()
}

const zh={settings:'考勤设置',addresses:'居家地址',history:'变更历史',current:'当前执行',upcoming:'待生效',past:'已结束',currentPolicy:'当前执行策略',upcomingPolicies:'后续生效安排',editPolicy:'调整考勤策略',savePolicy:'保存考勤调整',settingsHint:'当前策略继续执行，后续安排按生效日期自动切换。',historyHint:'相同内容的连续记录已合并展示；展开可查看全部原始日期和版本。',noPolicy:'沿用旧方式（兼容）',until:'执行至（不含）',from:'起',records:'条记录',details:'查看原始记录',positionVersion:'岗位版本',unknownReviewer:'姓名暂不可用',addressHistory:'已处理地址记录',activeAddress:'当前有效地址',olderAddress:'已被后续地址替代',coordinates:'定位详情',addressHint:'待办可在此审批；已处理申请展开查看。居家打卡使用最新已生效的批准地址。'}
const en:Record<keyof typeof zh,string>={settings:'Attendance settings',addresses:'Home addresses',history:'Change history',current:'In effect',upcoming:'Upcoming',past:'Ended',currentPolicy:'Current policy',upcomingPolicies:'Upcoming policies',editPolicy:'Change attendance policy',savePolicy:'Save policy change',settingsHint:'The current policy remains in effect until the next scheduled effective date.',historyHint:'Consecutive equivalent records are grouped. Expand to view every original date and version.',noPolicy:'Legacy mode',until:'Until (exclusive)',from:'onwards',records:'records',details:'View original records',positionVersion:'Position version',unknownReviewer:'Name unavailable',addressHistory:'Reviewed address records',activeAddress:'Current effective address',olderAddress:'Replaced by a later address',coordinates:'Location details',addressHint:'Review pending requests here; expand reviewed requests for history. Home clocking uses the latest effective approved address.'}
export function workModeDisplayText(english:boolean){return english?en:zh}
