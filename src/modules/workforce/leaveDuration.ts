import {localInput,localToInstant} from './time'
import type {Schedule} from './types'
/** Quick durations are elapsed hours, bounded by the selected shift. Never clip silently. */
export function quickLeavePeriod(schedule:Pick<Schedule,'startAt'|'endAt'|'timezone'>,start:string,hours:number){
 const minutes=Math.round(hours*60)
 if(!Number.isFinite(hours)||hours<=0||minutes<1||Math.abs(hours*60-minutes)>1e-8)return null
 try{
  const a=Date.parse(localToInstant(start,schedule.timezone)),b=a+minutes*60000
  if(a<Date.parse(schedule.startAt)||b>Date.parse(schedule.endAt))return null
  const end=localInput(new Date(b).toISOString(),schedule.timezone)
  if(Date.parse(localToInstant(end,schedule.timezone))!==b)return null
  return end
 }catch{return null}
}
