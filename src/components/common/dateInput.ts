export type DateInputType='date'|'month'|'datetime-local'
export function validDateInput(value:string,type:DateInputType='date'):boolean {
 if(!value)return true
 const datePart=type==='month'?value+'-01':value.slice(0,10)
 if(!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(datePart)||datePart.startsWith('0000-'))return false
 const date=new Date(datePart+'T00:00:00Z')
 if(!Number.isFinite(date.getTime())||date.toISOString().slice(0,10)!==datePart)return false
 if(type==='date')return value===datePart
 if(type==='month')return /^[0-9]{4}-[0-9]{2}$/.test(value)
 return /^[0-9]{4}-[0-9]{2}-[0-9]{2}T(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9](?:\.[0-9]{1,3})?)?$/.test(value)
}
export function displayDateInput(value:string,type:DateInputType):string{return type==='datetime-local'?value.replace('T',' '):value}
export function parseDateInput(value:string,type:DateInputType):string{return type==='datetime-local'?value.trim().replace(' ','T'):value.trim()}
export function dateInputPlaceholder(type:DateInputType,english:boolean):string {
 const date=english?'YYYY-MM-DD':'年-月-日'
 return type==='month'?(english?'YYYY-MM':'年-月'):type==='datetime-local'?date+(english?' HH:mm':' 时:分'):date
}
