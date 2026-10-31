import {computed,type Ref} from 'vue'
import type {Home} from './types'
import type {PayrollHomeData} from './payrollTypes'
import {useWorkforceText} from './text'
import {usePhaseText} from './phaseText'
import {usePhaseThreeText} from './phaseThreeText'
import {usePayrollText} from './payrollText'
import {usePositionText} from './positionText'
export interface WorkforceNavItem {label:string;path:string;matches?:string[]}
/** Existing operations and administration remain separate, with company-scoped payroll access. */
export function useWorkforceNavigation(home:Ref<Home|null>,payrollHome?:Ref<PayrollHomeData|null>){
 const {t}=useWorkforceText(),{p}=usePhaseText(),{q}=usePhaseThreeText(),{r}=usePayrollText(),{j}=usePositionText()
 return computed<WorkforceNavItem[]>(()=>{
  if(!home.value)return []
  const payroll={label:r('payroll'),path:'payroll',matches:['payroll','payroll-segments','payroll-reports','payroll-reconciliation','payroll-batches','payslips','supplemental-payroll','statutory-settlement']}
  const management={label:j('management'),path:'management',matches:['management','clock-site','document-types','document-expiries','public-holidays','administrators','positions','companies','calendar','leave-types','shifts','salary-templates','pay-profiles','position-pay-plans','pay-template-library','pay-change-requests','attendance-rules','review-explanations','statutory-policies']}
  const operations:WorkforceNavItem[]=home.value.employeeManage?[
   {label:t('employees'),path:'employees'},
   {label:p('scheduling'),path:'schedule',matches:['schedule']},
   {label:p('attendance'),path:'attendance',matches:['attendance','corrections']},
   {label:q('leaves'),path:'leaves'},
   {label:q('approvals'),path:'approvals',matches:['approvals','work-summary']},
   management,
   ...(home.value.myself?[{label:p('clock'),path:'clock',matches:['clock','work-mode']}]:[]),payroll
  ]:[{label:p('clock'),path:'clock',matches:['clock','work-mode']},{label:p('myAttendance'),path:'attendance',matches:['attendance','corrections']},{label:q('myLeaves'),path:'leaves'},{label:q('calendar'),path:'calendar'},{label:q('approvals'),path:'approvals'},payroll]
  if(!home.value.employeeManage&&payrollHome?.value?.companies.length)operations.splice(operations.length-1,0,management)
  return [...operations,{label:t('myself'),path:''}]
 })
}

