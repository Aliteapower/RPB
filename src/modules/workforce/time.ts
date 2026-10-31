import type {ShiftWrite,BreakInterval} from './types'
export function localInput(instant:string,zone:string){
 const p=new Intl.DateTimeFormat('en-CA',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date(instant))
 const get=(k:string)=>p.find(v=>v.type===k)?.value
 return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}`
}
/** Resolve a wall clock input uniquely. Reject DST gaps and repeated wall times. */
export function localToInstant(local:string,zone:string){
 if(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(local))throw new Error('invalid date')
 const wall=Date.parse(local+'Z'),matches=new Set<number>()
 // Gather offsets on both sides of a possible daylight-saving transition.
 for(const hours of [-36,-12,0,12,36]){
  const sample=wall+hours*3600000,offset=Date.parse(localInput(new Date(sample).toISOString(),zone)+'Z')-sample
  const candidate=wall-offset
  if(localInput(new Date(candidate).toISOString(),zone)===local)matches.add(candidate)
 }
 if(matches.size!==1)throw new Error('ambiguous or nonexistent store-local time')
 return new Date([...matches][0]!).toISOString()
}
export function addDays(day:string,n:number){const time=Date.parse(day+'T12:00:00Z')+n*86400000;return Number.isFinite(time)?new Date(time).toISOString().slice(0,10):''}
export function weekMonday(day:string){const d=new Date(day+'T12:00:00Z');return addDays(day,-((d.getUTCDay()+6)%7))}
export function storeToday(zone='Asia/Singapore'){return localInput(new Date().toISOString(),zone).slice(0,10)}
export function validateShift(s:Pick<ShiftWrite,'name'|'startTime'|'endTime'|'nextDay'|'breakMode'|'breakMinutes'|'breakAfterMinutes'|'breakStartOffset'|'breakEndOffset'>){
 const minutes=(v:string)=>{if(!/^([01]\d|2[0-3]):[0-5]\d$/.test(v))return NaN;const [h,m]=v.split(':').map(Number);return h!*60+m!}
 const total=minutes(s.endTime)-minutes(s.startTime)+(s.nextDay?1440:0)
 if(!s.name.trim()||s.name.length>80||!(total>0&&total<=1440))return false
 if(s.breakMode==='duration')return Number.isInteger(s.breakMinutes)&&Number.isInteger(s.breakAfterMinutes)&&s.breakMinutes>0&&s.breakAfterMinutes>=s.breakMinutes&&s.breakAfterMinutes<=total
 if(s.breakMode==='window')return Number.isInteger(s.breakStartOffset)&&Number.isInteger(s.breakEndOffset)&&s.breakStartOffset>=0&&s.breakEndOffset>s.breakStartOffset&&s.breakEndOffset<=total
 return s.breakMode==='none'||s.breakMode==='manual'
}
export function validateCorrection(c:{inAt:string;outAt:string;reason:string;breaks:BreakInterval[]},now:string){
 const start=Date.parse(c.inAt),end=Date.parse(c.outAt),current=Date.parse(now)
 if(!c.reason.trim()||c.reason.length>500||!(start<end&&end<=current&&end-start<=36*3600000))return false
 let previous=start
 for(const b of [...c.breaks].sort((a,b)=>Date.parse(a.start)-Date.parse(b.start))){const s=Date.parse(b.start),e=Date.parse(b.end);if(!(s>=previous&&s<e&&e<=end))return false;previous=e}
 return true
}
export function hours(seconds:number|null){return seconds===null?'—':`${Math.floor(seconds/3600)}:${String(Math.floor(seconds%3600/60)).padStart(2,'0')}`}
export function displayTime(value:string|null|undefined,zone:string,english=false){return value?new Intl.DateTimeFormat(english?'en-GB':'zh-CN',{timeZone:zone,month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(new Date(value)):'—'}
