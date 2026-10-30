import type {CalendarEvent} from './phaseThreeTypes'
import type {Schedule,Shift} from './types'
export interface ScheduleEmployee {id:string;employeeNo:string;name:string;positionId:string|null}
export interface SchedulePosition {id:string;name:string;enabled:boolean}
export interface BulkOptions {items:ScheduleEmployee[];total:number;limit:number;offset:number;positions:SchedulePosition[];shifts:Shift[];timezone:string}
export interface BulkRequest {employeeIds:string[];shiftId:string;from:string;to:string;weekdays:number[]}
export interface BulkPreview {fingerprint:string;employeeCount:number;total:number;ready:number;existing:number;conflicts:number;items:{employeeId:string;employeeNo:string;employeeName:string;date:string;status:string;reason:string}[];timezone:string}
export interface BulkResult {commandId:string;created:number;existing:number;conflicts:number;scheduleIds:string[]}
export interface ScheduleStaffing {shifts:{shiftId:string;name:string;people:number;personDays:number}[];days:{shiftId:string;date:string;people:number}[];dailyTotals?:{date:string;people:number}[]}
export interface ScheduleBoard {restSwaps?:import("./restSwap").RestSwapLink[];calendar?:CalendarEvent[];staffing?:ScheduleStaffing;employees:ScheduleEmployee[];total:number;limit:number;offset:number;items:Schedule[];timezone:string;weekScheduleCount:number;periodScheduleCount?:number;positions:SchedulePosition[]}

export interface StaffingSchedule {id:string;startAt:string;endAt:string;timezone:string;version?:number}
export interface StaffingEmployee {employeeId:string;employeeNo:string;name:string;date:string;shiftName:string;schedules?:StaffingSchedule[]}
export interface StaffingEmployeePage {items:StaffingEmployee[];total:number;people:number;limit:number;offset:number}

