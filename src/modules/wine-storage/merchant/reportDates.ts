/** Report queries always use calendar dates, never timezone-shifted timestamps. */
export function validReportDate(value:string):boolean {
 if(!value)return true
 if(!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value)||value.startsWith('0000-'))return false
 const date=new Date(value+'T00:00:00Z')
 return Number.isFinite(date.getTime())&&date.toISOString().slice(0,10)===value
}
