import type {AttendanceRules} from './restPolicy'
import type {ClockPolicy,WorkMode,ClockLocation} from './workMode'
export type {AttendanceRules,BreakRule,AnomalyRule,AttendanceRuleTemplate} from './restPolicy'
export interface Option {id:string;name:string}
export interface StoreOption extends Option {companyId:string|null}
export interface Employee {
 id:string;employeeNo:string;name:string;phone:string;email:string;jobTitle:string;hireDate:string|null;leaveDate:string|null;
 status:'active'|'left';companyId:string|null;companyEffectiveDate:string|null;mainStoreId:string|null;storeIds:string[];
 accountId:string|null;version:number;editable:boolean;positionId?:string|null;birthDate?:string|null;englishName?:string;enabled?:boolean
}
export interface EmployeeWrite extends Omit<Employee,'id'|'version'|'editable'> {expectedVersion:number;username?:string;password?:string}
export interface Company extends Option {registrationNo:string;payDay:number;enabled:boolean;version:number}
export interface Administrator {accountId:string;name:string;employeeManage:boolean;payrollCompanyId:string|null;version:number}
export interface Position extends Option {enabled:boolean;version:number;attendanceMode?:WorkMode|null;attendanceModes?:WorkMode[]|null}
export interface Home {headOffice:boolean;employeeManage:boolean;stores:StoreOption[];clockStores?:StoreOption[];companies:Option[];accounts:Option[];administratorAccounts:Option[];myself:Employee|null}
export interface Shift {id:string;name:string;startTime:string;endTime:string;startNextDay?:boolean|null;nextDay:boolean;breakMode:'none'|'duration'|'window'|'manual';breakMinutes:number;breakAfterMinutes:number;breakStartOffset:number;breakEndOffset:number;breakPaid:boolean;enabled:boolean;version:number;rules?:AttendanceRules|null}
export interface ShiftWrite extends Omit<Shift,'id'|'version'> {expectedVersion:number}
export interface Schedule {id:string;employeeId:string;employeeName:string;employeeNo:string;shiftId:string;workDate:string;startAt:string;endAt:string;timezone:string;status:'scheduled'|'cancelled';shift:Shift;companyId:string;version:number}
export interface ScheduleResult {items:Schedule[];employees:Option[];timezone:string}
export interface BreakInterval {start:string;end:string;ruleId?:string|null}
export interface BreakSummary {ruleId:string;name:string;mode:'window'|'actual'|'quota';punchMode:'none'|'optional'|'required';paid:boolean;plannedSeconds:number;actualSeconds:number|null;remainingSeconds:number;overSeconds:number;shortSeconds:number;missing:boolean}
export interface Session {id:string;scheduleId:string;employeeId:string;storeId:string;inAt:string;outAt:string|null;breakStartedAt:string|null;breakStartedRuleId?:string|null;breaks:BreakInterval[];overrideBreak:boolean;version:number;clockPolicy?:ClockPolicy|null}
export interface Attendance {exempt?:boolean;schedule:Schedule;session:Session|null;grossSeconds:number|null;breakSeconds:number|null;workSeconds:number|null;anomalies:string[];breakSummary?:BreakSummary[]}
export type ClockAction='IN'|'BREAK_START'|'BREAK_END'|'OUT'
export interface ClockResult {schedules:Schedule[];active:Session|null;attendance:Attendance|null;actions:ClockAction[];serverTime:string;policy?:ClockPolicy|null;policies?:Record<string,ClockPolicy|null>}
export interface Correction {id:string;scheduleId:string;employeeId:string;employeeName:string;basedSessionVersion:number;inAt:string;outAt:string;breaks:BreakInterval[];overrideBreak:boolean;reason:string;status:'pending'|'approved'|'rejected'|'withdrawn';applicantId:string;reviewerId:string|null;reviewNote:string|null;version:number;createdAt:string}
export interface AttendanceDetail {attendance:Attendance;events:{id:string;action:string;occurredAt:string;actorId:string;clockMode?:WorkMode|null;location?:Partial<ClockLocation>|null}[];corrections:Correction[]}

