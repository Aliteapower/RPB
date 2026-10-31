import type {BatchStatus,BatchSummary} from './payrollTypes'
export interface PayrollLineChange {employeeId:string;employeeName:string;kind:'added'|'removed'|'changed';reasons:string[];basicPayDelta:string|null;grossPayDelta:string|null;netPayDelta:string|null;employeeCpfDelta:string|null;employerCostDelta:string|null}
export interface PayrollComparison {available:boolean;baselineMonth:string|null;baselineVersion:number|null;totalGrossDelta:string|null;totalNetDelta:string|null;totalEmployerCostDelta:string|null;lines:PayrollLineChange[];reasonBasis?:'snapshot_evidence_v1'|'legacy_unverified'|null}
export interface PayrollChangeReport {batchId:string;batchVersion:number;status:BatchStatus;sourceChanged:boolean;lastCalculation:PayrollComparison;previousMonth:PayrollComparison;pending:{employeeId:string;employeeName:string;reason:string}[];recalculated:number;reused:number}
export type PayrollBulkAction='calculate'|'calculate_full'|'confirm'
export interface PayrollBulkWrite {action:PayrollBulkAction;note:string;entries:{batchId:string;expectedVersion:number}[]}
export interface PayrollBulkResult {commandId:string;action:PayrollBulkAction;results:{batchId:string;status:'success'|'failed';code:string|null;batch:BatchSummary|null}[]}
