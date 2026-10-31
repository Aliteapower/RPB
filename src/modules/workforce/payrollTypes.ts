import type {WageEventEvidence,AwCalculation,PublicAw} from './annualWages'
import type {ProfileOrigin} from './payrollConfigurationTypes'
import type {Option} from './types'
import type {CdacSetting,CdacReview,CdacCalculation} from './cdac'
export type PayBasis='monthly'|'daily'|'hourly'
export type ItemKind='ow_attendance'|'ow_allowance'|'ow_additional'|'ow_accommodation'|'reimbursement'|'deduction'|'cdac'|'employer_charge'|'wage_event'
export interface PayItem {label:string;kind:ItemKind;amount:string;proration?:'full'|'payable'|null;itemId?:string|null;source?:ItemSource|null;eventEvidence?:WageEventEvidence|null}
export interface ContractTerms {employmentProration:'full'|'working_days'|'';unpaidLeaveTreatment:'no_deduction'|'deduct'|'';overtimeTreatment:'included'|'separate'|'not_applicable'|''}
export interface PayGeneration {mode:'manual'|'attendance'|'fixed'|'contract';workingWeekdays:number[];contractTerms?:ContractTerms|null}
export interface PayRule {basis:PayBasis;rate:string;dayHours:string;weekHours:string;overtimeEnabled:boolean;overtimeMultiplier:string;items:PayItem[];generation?:PayGeneration|null}
export interface CpfProfile {citizenship:'citizen'|'spr'|'foreign';birthDate:string|null;prDate:string|null;prMode:'GG'|'FG'|'FF';electionReference:string;prDocumentId?:string|null}
export interface Policy {version:string;year:number;owCeiling:string;cpfSource:string;sdlSource:string;notes:string[]}
export interface PayrollHomeData {companies:Option[];employeeId:string|null;policy:Policy}
export interface SalaryTemplate {id:string;companyId:string;name:string;rule:PayRule;enabled:boolean;version:number;governanceSource?:{adoptionId:string;storeId:string;masterId:string;releaseVersion:number}|null}
export type PaymentMethod='cash'|'bank'|'paynow'|'other'
export interface PaymentPreference {method:PaymentMethod;bank:string}
export interface PayProfile {id:string;companyId:string;employeeId:string;employeeName:string;effectiveFrom:string;templateId:string;templateVersion:number;rule:PayRule;cpf:CpfProfile;version:number;payment?:PaymentPreference|null;cdac?:CdacSetting|null;origin?:ProfileOrigin|null}
export interface PrDocument {id:string;typeName:string;number:string;approvedOn:string|null;prEffectiveOn:string;holderName:string}
export interface PayProfilePage {items:PayProfile[];employees:Option[];employeeBirthDates?:Record<string,string>;employeePrDocuments?:Record<string,PrDocument[]>}
export interface StatutoryPolicyRef {id:string;version:number;name:string;year:number;currency:string;effectiveFrom:string;effectiveTo:string|null;config:unknown}
export interface LineInput {employeeId:string;version:number;workingDays:string|null;payableDays:string|null;unitsReviewed:boolean;specialDaysReviewed:boolean;items:PayItem[];note:string;cdacReview?:CdacReview|null;generation?:'fixed'|'attendance'|'contract'|null}
export type BatchStatus='draft'|'calculated'|'confirmed'|'published'|'voided'
export type BatchAction='calculate'|'calculate_full'|'confirm'|'publish'|'void'
export interface BatchSummary {id:string;companyId:string;companyName:string;month:string;status:BatchStatus;version:number;employeeCount:number;totalGross:string|null;totalNet:string|null;totalEmployerCpf:string|null;totalSdl:string|null;totalEmployerCost:string|null;createdAt:string}
export interface SourceRef {sourceId:string;sourceType:string;storeId:string;workDate:string;sourceVersion:number;sourceFingerprint:string;workSeconds:number;paidLeaveSeconds:number;unpaidLeaveSeconds:number;payable:boolean;evidence?:SourceEvidence|null}
export interface Component {label:string;kind:string;amount:string|null;lineage?:ItemLineage|null}
export interface WageDisplay {aw?:AwCalculation|null;publicAw?:PublicAw|null;employeeId:string;employeeName:string;employeeNo:string;rule?:PayRule;input:LineInput;workSeconds:number;normalSeconds:number;overtimeSeconds:number;paidLeaveSeconds:number;unpaidLeaveSeconds:number;basicPay:string|null;overtimePay:string|null;allowances:string|null;reimbursements:string|null;deductions:string|null;grossPay:string|null;employeeCpf:string|null;employerCpf:string|null;sdl:string|null;employerCharges:string|null;netPay:string|null;cpfRateCode:string;components:Component[];hours?:PayrollHours|null}
export interface EffectivePayEvidence {kind:'legacy-valid'|'approved'|'direct-adoption'|'economic-unchanged'|'default';subjectId:string;version:number;requestId?:string|null;revision?:number|null;hash?:string|null}
export interface PayrollLine extends WageDisplay {governance?:{profile:EffectivePayEvidence|null;input:EffectivePayEvidence|null}|null;profile:PayProfile|null;sources:SourceRef[];issues:string[];cdacCalculation?:CdacCalculation|null}
export interface PayrollSnapshot {sourceFingerprint:string;companyName:string;month:string;policyVersion:string;statutoryPolicy?:StatutoryPolicyRef|null;lines:PayrollLine[];issues:string[];totalGross:string|null;totalNet:string|null;totalEmployeeCpf:string|null;totalEmployerCpf:string|null;totalSdl:string|null;totalEmployerCost:string|null;calculatedAt:string}
export interface Payment {id:string;employeeId:string;amount:string;paymentDate:string;method:PaymentMethod;bank?:string|null;reference:string;note:string;reversesId:string|null;actorId:string;createdAt:string}
export interface BatchDetail {batch:BatchSummary;employees:Option[];inputs:LineInput[];calculation:PayrollSnapshot|null;sourceChanged:boolean;payments:Payment[]}
export interface PayslipSummary {batchId:string;companyName:string;month:string;netPay:string|null;paidAmount:string;status:string}
export interface Payslip {batchId:string;companyName:string;month:string;status:BatchStatus;line:WageDisplay;payments:Payment[];paidAmount:string;remainingAmount:string;breakdown?:PayBreakdown;payment?:PaymentPreference|null;cdacCalculation?:CdacCalculation|null}

export interface PayBreakdown {basic:string|null;overtime:string|null;additional:string|null;allowance:string|null;accommodation:string|null;reimbursements:string|null;otherDeductions:string|null;cdac:string|null;employeeCpf:string|null;employerCpf:string|null;sdl:string|null;employerCharges:string|null;gross:string|null;net:string|null;employerCost:string|null;items:Component[]}

export interface ItemSource {origin:string;sourceId:string;version:number;eventId:string|null;independentReason:string|null}
export interface SourceEvidence {actualSeconds:number|null;extraSeconds:number;attendanceMode:string;approvalState:string;approvedSeconds:number|null;startAt:string|null;endAt:string|null;eventIds:string[]}
export interface PayrollHours {actualSeconds:number|null;approvedSeconds:number|null;approvalState:string;basis:string;normalSeconds:number|null;paidLeaveSeconds:number|null;overtimeSeconds:number|null;workingDays:string|null;payableDays:string|null}
export interface ItemLineage {origin:string;sourceId:string;version:number;itemId:string;month:string;formula:string;quantity:string;unit:string;rate:string;multiplier:string;proration:string;unroundedAmount:string;rounding:string;roundedAmount:string;sourceKeys:string[]}
