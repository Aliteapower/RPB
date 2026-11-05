export type AttendanceMode='clock'|'default_normal'|'contract'|'free'
export interface Arrangement {weekdays:number[];startTime:string;standardWorkMinutes:number;unpaidBreakMinutes:number;unpaidBreakStartOffsetMinutes:number|null}
export interface PolicyVersion {id:string;employeeId:string|null;companyId:string;sourceStoreId:string;effectiveFrom:string;mode:AttendanceMode;origin:string;sourceDefaultId:string|null;sourceDefaultVersion:number|null;arrangement:Arrangement|null;timezone:string;version:number}
export interface PolicyPage {employeeId:string;canManage:boolean;today:string;assignments:PolicyVersion[];effective:{mode:AttendanceMode;origin:string;arrangement:Arrangement|null;timezone:string;sourceReason:string}}
export const attendanceModes:AttendanceMode[]=['clock','default_normal','contract','free']
export function modeLabel(mode:string,en=false){return ({clock:en?'Clock verification':'打卡核对',default_normal:en?'Normal by arrangement; record exceptions':'默认正常、登记例外',contract:en?'Contract based, no clocking':'免打卡按合同计发',free:en?'Free attendance exempt':'自由免考勤'} as Record<string,string>)[mode]||(en?'Unknown':'未知')}
