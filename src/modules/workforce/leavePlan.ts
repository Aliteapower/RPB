import type {Schedule} from './types'
import type {CalendarPage,Leave} from './phaseThreeTypes'
import {addDays,localInput,localToInstant} from './time'
export interface LeavePlanRow {date:string;schedule?:Schedule;startAt:string;endAt:string;status:'ready'|'rest'|'cancelled'|'empty'|'outside'|'conflict'|'excluded';seconds:number}
export interface LeaveDayPeriod {start:string;end:string}
/** No write side effects. Partial multi-day leave keeps the same offset and elapsed duration per shift. */
export function planLeaveRange(from:string,to:string,schedules:Schedule[],calendar:CalendarPage|null,leaves:Leave[],employeeId:string,whole:boolean,anchor:Schedule|undefined,startAt:string,endAt:string):LeavePlanRow[]{
 const offset=anchor?Date.parse(startAt)-Date.parse(anchor.startAt):NaN,duration=Date.parse(endAt)-Date.parse(startAt),rows:LeavePlanRow[]=[]
 for(let day=from;day<=to;day=addDays(day,1)){
  if(rows.length>1000)throw Error('range too large')
  const rest=calendar?.items?.some(e=>e.enabled&&e.kind==='store_rest'&&e.date===day)||calendar?.restSwaps?.some(e=>e.employeeId===employeeId&&e.restDate===day)
  const shifts=schedules.filter(s=>s.employeeId===employeeId&&s.workDate===day)
  if(rest||!shifts.length){rows.push({date:day,startAt:'',endAt:'',seconds:0,status:rest?'rest':'empty'});continue}
  for(const schedule of shifts){
   let status:LeavePlanRow['status']=schedule.status==='scheduled'?'ready':'cancelled'
   const a=whole?Date.parse(schedule.startAt):Date.parse(schedule.startAt)+offset,b=whole?Date.parse(schedule.endAt):a+duration
   if(status==='ready'&&(!Number.isFinite(a)||!Number.isFinite(b)||a<Date.parse(schedule.startAt)||b>Date.parse(schedule.endAt)||b<=a))status='outside'
   if(status==='ready'&&leaves.some(l=>l.scheduleId===schedule.id&&(['pending','cancel_requested'].includes(l.status)||(l.status==='approved'&&Date.parse(l.startAt)<b&&Date.parse(l.endAt)>a))))status='conflict'
   rows.push({date:day,schedule,startAt:Number.isFinite(a)?new Date(a).toISOString():'',endAt:Number.isFinite(b)?new Date(b).toISOString():'',status,seconds:status==='ready'?(b-a)/1000:0})
  }
 }
 return rows
}
export function leaveTimeRange(row:LeavePlanRow){return row.schedule&&row.startAt&&row.endAt?`${localInput(row.startAt,row.schedule.timezone).replace('T',' ')} → ${localInput(row.endAt,row.schedule.timezone).replace('T',' ')}`:'—'}
/** Per-shift edits override the shared template; excluding a date excludes every shift on that date. */
export function adjustLeavePlan(rows:LeavePlanRow[],edits:Record<string,LeaveDayPeriod>,excludedDates:string[],leaves:Leave[]):LeavePlanRow[]{
 return rows.map(row=>{
  const s=row.schedule
  if(!s||s.status!=='scheduled'||row.status==='rest')return row
  let startAt=row.startAt,endAt=row.endAt
  const edit=edits[s.id]
  if(edit){try{startAt=localToInstant(edit.start,s.timezone);endAt=localToInstant(edit.end,s.timezone)}catch{startAt='';endAt=''}}
  let status:LeavePlanRow['status']='ready'
  const a=Date.parse(startAt),b=Date.parse(endAt)
  if(excludedDates.includes(row.date))status='excluded'
  else if(!Number.isFinite(a)||!Number.isFinite(b)||a<Date.parse(s.startAt)||b>Date.parse(s.endAt)||b<=a)status='outside'
  else if(leaves.some(l=>l.scheduleId===s.id&&(['pending','cancel_requested'].includes(l.status)||(l.status==='approved'&&Date.parse(l.startAt)<b&&Date.parse(l.endAt)>a))))status='conflict'
  return {...row,startAt,endAt,status,seconds:status==='ready'?(b-a)/1000:0}
 })
}
