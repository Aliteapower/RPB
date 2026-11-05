import type {Component,PayBreakdown,WageDisplay} from './payrollTypes'
export const incomeFields=['basic','overtime','additional','allowance','accommodation'] as const
export const deductionFields=['otherDeductions','cdac','employeeCpf'] as const
export const employerFields=['employerCpf','sdl','employerCharges'] as const
export const detailFields=[...incomeFields,'reimbursements',...deductionFields,...employerFields] as const
export function groupItems(items:Component[],group:'income'|'deductions'|'employer'|'reimbursements'){
 const kinds=group==='income'?['basic','overtime','ow_allowance','ow_additional','ow_accommodation','ow_attendance','wage_event']:group==='deductions'?['deduction','cdac']:group==='employer'?['employer_charge']:['reimbursement']
 return items.filter(i=>kinds.includes(i.kind))
}
export function wageBreakdown(line:WageDisplay):PayBreakdown{
 const sum=(...kinds:string[])=>{let total=0n;for(const i of line.components||[])if(kinds.includes(i.kind)){if(i.amount==null)return null;const [a,b='']=i.amount.split('.');total+=BigInt(a!)*100n+BigInt(b.padEnd(2,'0').slice(0,2))}return `${total/100n}.${String(total%100n).padStart(2,'0')}`}
 return {basic:line.basicPay,overtime:line.overtimePay,additional:sum('ow_additional','wage_event'),allowance:sum('ow_allowance','ow_attendance'),accommodation:sum('ow_accommodation'),reimbursements:line.reimbursements,otherDeductions:sum('deduction'),cdac:sum('cdac'),employeeCpf:line.employeeCpf,employerCpf:line.employerCpf,sdl:line.sdl,employerCharges:line.employerCharges,gross:line.grossPay,net:line.netPay,employerCost:'',items:line.components||[]}
}
