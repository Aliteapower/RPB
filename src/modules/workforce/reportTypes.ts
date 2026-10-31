import type {PayBreakdown} from './payrollTypes'
export interface ReportTotals {gross:string;net:string;employeeCpf:string;employerCpf:string;sdl:string;employerCost:string;paid:string;remaining:string}
export type ReportField=keyof ReportTotals
export interface ReportAggregate {companyCount:number;employeeCount:number;payrollRows:number;totals:ReportTotals;breakdown?:PayBreakdown}
export interface ReportCompany {companyId:string;companyName:string;batchId:string|null;status:'missing'|'draft'|'calculated'|'confirmed'|'published';sourceChanged:boolean;employeeCount:number;totals:ReportTotals|null;issues:string[];breakdown?:PayBreakdown|null}
export interface ReportEmployee {companyId:string;companyName:string;batchId:string;status:string;employeeId:string;employeeNo:string;employeeName:string;workSeconds:number;overtimeSeconds:number;totals:ReportTotals;breakdown?:PayBreakdown;paymentMethod?:string;bank?:string}
export interface ReportPayment {companyId:string;companyName:string;batchId:string;employeeId:string;employeeNo:string;employeeName:string;paymentId:string;paymentDate:string;method:string;bank?:string|null;reference:string;amount:string;signedAmount:string;reversesId:string|null}
export interface PayrollReport {month:string;currency:string;companyIds:string[];companies:ReportCompany[];employees:ReportEmployee[];payments:ReportPayment[];frozen:ReportAggregate;forecast:ReportAggregate;fingerprint:string;generatedAt:string}
export interface ReportComparison {report:PayrollReport;matched:boolean;items:{field:ReportField;expected:string;actual:string;difference:string}[]}
