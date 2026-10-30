import type {PayProfile,PayRule,SalaryTemplate} from './payrollTypes'
export interface ProfileOrigin {templateName:string;templateRule:PayRule;defaultMappingId:string|null;defaultVersion:number|null}
export interface PositionSalaryDefault {id:string;companyId:string;positionId:string;effectiveFrom:string;templateId:string;templateVersion:number;enabled:boolean;version:number}
export interface ProfilePreview {template:SalaryTemplate|null;defaultMapping:PositionSalaryDefault|null}
export interface ProfilePeriod {profile:PayProfile;effectiveTo:string|null;state:'current'|'future'|'history';sourceComplete:boolean}
export interface EmployeePaySummaryData {employeeId:string;companyId:string;on:string;profiles:ProfilePeriod[]}
export function eligibleCompany(companies:{id:string}[],requested:string){return companies.some(c=>c.id===requested)?requested:''}
export function profileEmployee(employees:{id:string}[],requested:string){return employees.some(e=>e.id===requested)?requested:''}

export function ruleOverrides(profile:PayProfile){const original=profile.origin?.templateRule;if(!original)return [];return (['basis','rate','dayHours','weekHours','overtimeEnabled','overtimeMultiplier','items','generation'] as const).filter(key=>JSON.stringify(key==='items'?economicItems(profile.rule.items):profile.rule[key]??null)!==JSON.stringify(key==='items'?economicItems(original.items):original[key]??null))}

function economicItems(items:PayRule['items']){return items.map(i=>({label:i.label,kind:i.kind,amount:i.amount,proration:i.proration??null}))}
