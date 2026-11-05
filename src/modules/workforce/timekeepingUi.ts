import type {Home,Schedule} from './types'
import type {Leave,LeaveDecision,ApprovalRow} from './phaseThreeTypes'
export function validDay(day:string){return /^\d{4}-\d{2}-\d{2}$/.test(day)&&Number.isFinite(Date.parse(day))&&new Date(day).toISOString().slice(0,10)===day}
export function monthEnd(day:string){if(!validDay(day))return '';const d=new Date(day+'T12:00:00Z');return new Date(Date.UTC(d.getUTCFullYear(),d.getUTCMonth()+1,0,12)).toISOString().slice(0,10)}
export function dateRangeValid(from:string,to:string){const days=(Date.parse(to)-Date.parse(from))/86400000;return validDay(from)&&validDay(to)&&days>=0&&days<=30}
export function leaveActions(item:Pick<Leave,'status'|'applicantId'|'employeeId'>,home:Pick<Home,'employeeManage'|'myself'>|null):LeaveDecision[]{
 const mine=!!home?.myself&&item.applicantId===home.myself.accountId,self=!!home?.myself&&item.employeeId===home.myself.id
 if(mine)return item.status==='pending'?['withdrawn']:item.status==='approved'?['cancel_requested']:[]
 if(!home?.employeeManage||self)return []
 return item.status==='pending'?['approved','rejected']:item.status==='cancel_requested'?['cancelled','cancel_rejected']:[]
}
export function approvalAllowed(row:ApprovalRow,home:Pick<Home,'employeeManage'|'myself'>|null){return !!home?.employeeManage&&home.myself?.id!==row.schedule.employeeId&&!row.issues.length&&(!row.approval||row.requiresReview)}
export function leavePeriodValid(s:Pick<Schedule,'status'|'startAt'|'endAt'>,startAt:string,endAt:string,reason:string){const a=Date.parse(startAt),b=Date.parse(endAt);return s.status==='scheduled'&&!!reason.trim()&&reason.length<=500&&a<b&&a>=Date.parse(s.startAt)&&b<=Date.parse(s.endAt)}
