import type {Schedule,ShiftWrite} from './types'
import {addDays} from './time'
type ShiftTimes=Pick<ShiftWrite,'startTime'|'endTime'|'nextDay'|'startNextDay'>
export function restaurantText(english=false){return english?{preset:'Quick shift setup',custom:'Custom shift',early:'Early shift',middle:'Middle shift',late:'Late shift',hint:'Only the name and times are filled. Review breaks and attendance rules before saving.',business:'Business date is the selected roster date. A shift starting the following day or crossing midnight stays on that business date, including month-end.',midnight:'24:00 is 00:00 on the following calendar day.',actual:'Actual shift interval'}:{preset:'快捷设置班次',custom:'自定义班次',early:'早班',middle:'中班',late:'晚班',hint:'快捷填写仅设置名称和起止时间；休息及考勤规则保留，请核对后保存。',business:'次日开始或跨午夜的整班工时归排班选定的营业日期；月底夜班也不按结束日重复计薪。起止时间按门店时区计算。',midnight:'24:00即开始日之后一天的00:00，请勾选“次日结束（相对实际开始日）”。',actual:'实际起止时间'}}
export function restaurantPreset(key:string,english=false):Pick<ShiftWrite,'name'|'startTime'|'endTime'|'nextDay'|'startNextDay'>|null{
 const text=restaurantText(english)
 switch(key){case 'early':return {name:text.early,startTime:'11:00',endTime:'23:00',nextDay:false,startNextDay:false};case 'middle':return {name:text.middle,startTime:'16:00',endTime:'00:00',nextDay:true,startNextDay:false};case 'late':return {name:text.late,startTime:'23:00',endTime:'06:00',nextDay:true,startNextDay:false};default:return null}
}
export function restaurantShiftRange(shift:ShiftTimes,english=false){const start=shift.startNextDay?1:0,end=start+(shift.nextDay?1:0),prefix=(day:number)=>day===0?'':day===1?(english?'next day ':'次日'):(english?'2 days later ':'后日');return `${prefix(start)}${shift.startTime}–${end===1&&!start&&shift.endTime==='00:00'?'24:00':prefix(end)+shift.endTime}`}
export function restaurantSchedulePreview(day:string,shift:ShiftTimes){if(!/^\d{4}-\d{2}-\d{2}$/.test(day)||addDays(day,0)!==day)return '';const start=shift.startNextDay?1:0;return `${addDays(day,start)} ${shift.startTime} – ${addDays(day,start+(shift.nextDay?1:0))} ${shift.endTime}`}
// Existing schedule instants remain authoritative, including legacy snapshots.
export function restaurantScheduleRange(row:Pick<Schedule,'workDate'|'startAt'|'endAt'|'timezone'>,english=false){
 const formatter=new Intl.DateTimeFormat('en-GB',{timeZone:row.timezone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'})
 function edge(value:string){const parts=formatter.formatToParts(new Date(value)),part=(key:string)=>parts.find(p=>p.type===key)!.value;const date=`${part('year')}-${part('month')}-${part('day')}`,offset=Math.round((Date.parse(date)-Date.parse(row.workDate))/86400000);return {time:`${part('hour')}:${part('minute')}`,offset}}
 const start=edge(row.startAt),end=edge(row.endAt)
 const label=(value:ReturnType<typeof edge>)=>`${value.offset===0?'':value.offset===1?(english?'next day ':'次日'):value.offset===2?(english?'2 days later ':'后日'):`+${value.offset} `}${value.time}`
 return `${label(start)}–${start.offset===0&&end.offset===1&&end.time==='00:00'?'24:00':label(end)}`
}
