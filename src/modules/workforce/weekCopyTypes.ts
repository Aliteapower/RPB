export interface WeekCopyRequest {sourceWeek:string;targetWeek:string}
export interface WeekCopyItem {sourceId:string;employeeId:string;employeeNo:string;employeeName:string;sourceDate:string;date:string;shiftId:string;shiftName:string;startAt:string|null;endAt:string|null;status:'ready'|'existing'|'conflict';reason:string}
export interface WeekCopyPreview {fingerprint:string;sourceWeek:string;targetWeek:string;employeeCount:number;total:number;ready:number;existing:number;conflicts:number;timezone:string;items:WeekCopyItem[]}
