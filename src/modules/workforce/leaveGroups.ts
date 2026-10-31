import type {Leave,LeaveDecision} from './phaseThreeTypes'
export interface LeaveGroup {id:string;grouped:boolean;items:Leave[];from?:string;to?:string}
export interface LeaveGroupPage {timezone:string;items:LeaveGroup[]}
export interface LeaveGroupDecision {status:LeaveDecision;note:string;items:{id:string;expectedVersion:number}[]}
export function groupSeconds(group:LeaveGroup){return group.items.reduce((sum,item)=>sum+(Date.parse(item.endAt)-Date.parse(item.startAt))/1000,0)}
