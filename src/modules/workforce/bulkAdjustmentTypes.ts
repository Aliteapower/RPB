export interface AdjustmentTarget {scheduleId:string;expectedVersion:number}
export interface AdjustmentRequest {date:string;sourceShiftId:string;targetShiftId:string;targets:AdjustmentTarget[]}
export interface AdjustmentPreview {fingerprint:string;date:string;targetShiftName:string;timezone:string;total:number;ready:number;unchanged:number;conflicts:number;items:{scheduleId:string;employeeId:string;employeeNo:string;employeeName:string;date:string;version:number;sourceShiftName:string;sourceStartAt:string;sourceEndAt:string;sourceTimezone:string;startAt:string|null;endAt:string|null;status:string;reason:string}[]}
export interface AdjustmentResult {commandId:string;updated:number;unchanged:number;conflicts:number;scheduleIds:string[]}
