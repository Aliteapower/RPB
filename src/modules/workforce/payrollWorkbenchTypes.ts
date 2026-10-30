import type {PayrollChangeReport} from './payrollProgressTypes'
export interface WorkbenchMissing {employeeId:string|null;employeeName:string;category:string;code:string;profileId?:string|null;action:string}
export interface WorkbenchApproval {employeeId:string;employeeName:string;scheduleId:string;storeId:string;workDate:string;state:string;context:boolean}
export interface WorkbenchReason {employeeId:string|null;kind:string;sourceId:string|null;oldVersion:string|null;newVersion:string|null;scope:string;action:string;storeId?:string|null;workDate?:string|null}
export interface PaymentLine {employeeId:string;employeeName:string;netPay:string;paidAmount:string;remainingAmount:string;state:'unpaid'|'partial'|'settled'|'zero'}
export interface PaymentSummary {available:boolean;due:string|null;paid:string|null;remaining:string|null;unpaid:number;partial:number;settled:number;zero:number;lines:PaymentLine[]}
export interface PayrollWorkbenchData {companyId:string;month:string;batchId:string|null;batchVersion:number|null;status:string|null;asOf:string;calculatedAt:string|null;frozen:boolean;missingEmployees:number;missing:WorkbenchMissing[];pendingEmployees:number;pendingShifts:number;contextShifts:number;exemptEmployees:number;approvals:WorkbenchApproval[];reasons:WorkbenchReason[];changes:PayrollChangeReport|null;payments:PaymentSummary}
