import type {Option} from './types'
import type {CdacSetting,CdacReview,CdacCalculation} from './cdac'
export type PayBasis='monthly'|'daily'|'hourly'
export type ItemKind='ow_allowance'|'ow_additional'|'ow_accommodation'|'reimbursement'|'deduction'|'cdac'|'employer_charge'
export interface PayItem {label:string;kind:ItemKind;amount:string;proration?:'full'|'payable'|null}
export interface PayGeneration {mode:'manual'|'attendance'|'fixed';workingWeekdays:number[]}
export interface PayRule {basis:PayBasis;rate:string;dayHours:string;weekHours:string;overtimeEnabled:boolean;overtimeMultiplier:string;items:PayItem[];generation?:PayGeneration|null}
export interface CpfProfile {citizenship:'citizen'|'spr'|'foreign';birthDate:string|null;prDate:string|null;prMode:'GG'|'FG'|'FF';electionReference:string;prDocumentId?:string|null}
export interface Policy {version:string;year:number;owCeiling:string;cpfSource:string;sdlSource:string;notes:string[]}
export interface PayrollHomeData {companies:Option[];employeeId:string|null;policy:Policy}
export interface SalaryTemplate {id:string;companyId:string;name:string;rule:PayRule;enabled:boolean;version:number}
export type PaymentMethod='cash'|'bank'|'paynow'|'other'
export interface PaymentPreference {method:PaymentMethod;bank:string}
export interface PayProfile {id:string;companyId:string;employeeId:string;employeeName:string;effectiveFrom:string;templateId:string;templateVersion:number;rule:PayRule;cpf:CpfProfile;version:number;payment?:PaymentPreference|null;cdac?:CdacSetting|null}
export interface PrDocument {id:string;typeName:string;number:string;approvedOn:string|null;prEffectiveOn:string;holderName:string}
export interface PayProfilePage {items:PayProfile[];employees:Option[];employeeBirthDates?:Record<string,string>;employeePrDocuments?:Record<string,PrDocument[]>}
export interface StatutoryPolicyRef {id:string;version:number;name:string;year:number;currency:string;effectiveFrom:string;effectiveTo:string|null;config:unknown}
export interface LineInput {employeeId:string;version:number;workingDays:string|null;payableDays:string|null;unitsReviewed:boolean;specialDaysReviewed:boolean;items:PayItem[];note:string;cdacReview?:CdacReview|null;generation?:'fixed'|'attendance'|null}
export type BatchStatus='draft'|'calculated'|'confirmed'|'published'|'voided'
export type BatchAction='calculate'|'calculate_full'|'confirm'|'publish'|'void'
export interface BatchSummary {id:string;companyId:string;companyName:string;month:string;status:BatchStatus;version:number;employeeCount:number;totalGross:string;totalNet:string;totalEmployerCpf:string;totalSdl:string;totalEmployerCost:string;createdAt:string}
export interface SourceRef {scheduleId:string;storeId:string;workDate:string;approvalVersion:number;sourceFingerprint:string;workSeconds:number;paidLeaveSeconds:number;unpaidLeaveSeconds:number;payable:boolean}
export interface Component {label:string;kind:string;amount:string}
export interface WageDisplay {employeeId:string;employeeName:string;employeeNo:string;rule?:PayRule;input:LineInput;workSeconds:number;normalSeconds:number;overtimeSeconds:number;paidLeaveSeconds:number;unpaidLeaveSeconds:number;basicPay:string;overtimePay:string;allowances:string;reimbursements:string;deductions:string;grossPay:string;employeeCpf:string;employerCpf:string;sdl:string;employerCharges:string;netPay:string;cpfRateCode:string;components:Component[]}
export interface PayrollLine extends WageDisplay {profile:PayProfile|null;sources:SourceRef[];issues:string[];cdacCalculation?:CdacCalculation|null}
export interface PayrollSnapshot {sourceFingerprint:string;companyName:string;month:string;policyVersion:string;statutoryPolicy?:StatutoryPolicyRef|null;lines:PayrollLine[];issues:string[];totalGross:string;totalNet:string;totalEmployeeCpf:string;totalEmployerCpf:string;totalSdl:string;totalEmployerCost:string;calculatedAt:string}
export interface Payment {id:string;employeeId:string;amount:string;paymentDate:string;method:PaymentMethod;bank?:string|null;reference:string;note:string;reversesId:string|null;actorId:string;createdAt:string}
export interface BatchDetail {batch:BatchSummary;employees:Option[];inputs:LineInput[];calculation:PayrollSnapshot|null;sourceChanged:boolean;payments:Payment[]}
export interface PayslipSummary {batchId:string;companyName:string;month:string;netPay:string;paidAmount:string;status:string}
export interface Payslip {batchId:string;companyName:string;month:string;status:BatchStatus;line:WageDisplay;payments:Payment[];paidAmount:string;remainingAmount:string;breakdown?:PayBreakdown;payment?:PaymentPreference|null;cdacCalculation?:CdacCalculation|null}

export interface PayBreakdown {basic:string;overtime:string;additional:string;allowance:string;accommodation:string;reimbursements:string;otherDeductions:string;cdac:string;employeeCpf:string;employerCpf:string;sdl:string;employerCharges:string;gross:string;net:string;employerCost:string;items:Component[]}
