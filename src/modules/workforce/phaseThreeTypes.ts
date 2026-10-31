import type {Schedule,Session,Correction,BreakSummary} from './types'
export interface LeaveType {id:string;name:string;paid:boolean;enabled:boolean;version:number}
export interface Leave {id:string;scheduleId:string;employeeId:string;employeeName:string;typeId:string;typeName:string;paid:boolean;startAt:string;endAt:string;reason:string;status:'pending'|'approved'|'rejected'|'withdrawn'|'cancel_requested'|'cancelled';applicantId:string;reviewerId:string|null;reviewNote:string|null;version:number;createdAt:string;medicalReference?:string|null}
export interface CalendarEvent {id:string;date:string;kind:'public_holiday'|'store_rest';name:string;enabled:boolean;version:number;scope?:'tenant'|null;originDate?:string|null;observed?:boolean|null;year?:number|null;englishName?:string|null;tentative?:boolean|null}
export interface CalendarPage {restSwaps?:import("./restSwap").RestSwapLink[];items:CalendarEvent[];days:{date:string;schedules:number;attended:number}[];timezone:string}
export interface ApprovalSource {schedule:Schedule;session:Session|null;corrections:Correction[];leaves:Leave[];calendar:CalendarEvent[]}
export interface Finding {code:string;name:string;seconds:number;status:'pending'|'confirmed'|'excused'|'absence'|'non_work';affectsBonus:boolean}
export interface ReviewDecision {code:string;outcome:'confirmed'|'excused'|'absence'|'non_work';note:string}
export interface ApprovalWrite {expectedVersion:number;sourceFingerprint:string;note:string;decisions?:ReviewDecision[]}
export interface Approval {decisions?:ReviewDecision[];scheduleId:string;version:number;sourceFingerprint:string;workSeconds:number;paidLeaveSeconds:number;unpaidLeaveSeconds:number;snapshot:ApprovalSource;reviewerId:string;note:string;approvedAt:string}
export interface ApprovalRow {exempt?:boolean;breakSummary?:BreakSummary[];findings?:Finding[];fullAttendanceStatus?:'pending'|'eligible'|'ineligible';schedule:Schedule;storeId:string;workSeconds:number|null;paidLeaveSeconds:number;unpaidLeaveSeconds:number;issues:string[];sourceFingerprint:string;approval:Approval|null;requiresReview:boolean}
export interface ApprovalDetail {row:ApprovalRow;history:Approval[];canReview?:boolean;reviewBlock?:string|null}
export interface SummaryRow {employeeId:string;employeeName:string;companyId:string;storeId:string;storeName:string;schedules:number;approved:number;pending:number;workSeconds:number;paidLeaveSeconds:number;unpaidLeaveSeconds:number}
export type LeaveDecision='approved'|'rejected'|'withdrawn'|'cancel_requested'|'cancelled'|'cancel_rejected'


