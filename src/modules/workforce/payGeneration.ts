import type {PayRule} from './payrollTypes'

export function generationMode(rule:PayRule){return rule.generation?.mode||'manual'}
export function validGeneration(rule:PayRule){
 const g=rule.generation
 if(!g)return true
 if(!['manual','attendance','fixed'].includes(g.mode)||!Array.isArray(g.workingWeekdays)||g.workingWeekdays.some(d=>!Number.isInteger(d)||d<1||d>7)||new Set(g.workingWeekdays).size!==g.workingWeekdays.length)return false
 if(g.mode==='fixed')return rule.basis==='monthly'&&!rule.overtimeEnabled
 return g.mode!=='attendance'||rule.basis!=='monthly'||g.workingWeekdays.length>0
}
