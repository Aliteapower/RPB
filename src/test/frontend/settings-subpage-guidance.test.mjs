import {test} from 'node:test'
import assert from 'node:assert/strict'
import {settingsSubpageGuidance} from '../../../src/modules/workforce/settingsSubpageGuidance.ts'
const admin={employeeManage:true,headOffice:true,companyIds:['C']}
test('salary setup follows templates, position defaults, employee terms and monthly payroll',()=>{
 for(const [page,next] of [['salary-templates','position-pay-plans'],['position-pay-plans','pay-profiles'],['pay-profiles','payroll']]){
  const g=settingsSubpageGuidance(page,admin,'C',false,false)
  assert.equal(g.next.path,next)
  assert.equal(g.next.companyId,'C')
  assert.equal(g.steps.length,3)
 }
})
test('guidance preserves company permission and does not expose head-office or salary tasks to store-only managers',()=>{
 const store={employeeManage:true,headOffice:false,companyIds:[]}
 assert.equal(settingsSubpageGuidance('pay-template-library',store,'C',false,false).next.path,'management')
 assert.equal(settingsSubpageGuidance('positions',store,'C',false,false).next.path,'clock-site')
 const delegate={employeeManage:false,headOffice:false,companyIds:['C']}
 assert.equal(settingsSubpageGuidance('salary-templates',delegate,'OTHER',false,true).next.companyId,'C')
 assert.equal(settingsSubpageGuidance('salary-templates',delegate,'C',true,true).next,null,'forms must not encourage leaving an unsaved draft')
})
test('every settings section has business instructions and unknown routes have none',()=>{
 for(const page of ['position-pay-plans','pay-template-library','pay-change-requests','attendance-rules','review-explanations','clock-site','statutory-policies','document-expiries','document-types','positions','companies','administrators','shifts','calendar','public-holidays','leave-types','salary-templates','pay-profiles'])assert.equal(settingsSubpageGuidance(page,admin,'C',false,false).steps.length,3,page)
 assert.equal(settingsSubpageGuidance('payroll',admin,'C',false,false),null)
})


test('store company is preferred when entering the template journey without explicit company context',()=>{
 const multi={...admin,companyIds:['C','D'],storeCompanyId:'D'}
 assert.equal(settingsSubpageGuidance('pay-template-library',multi,'',false,false).next.companyId,'D')
 assert.equal(settingsSubpageGuidance('pay-template-library',multi,'C',false,false).next.companyId,'C')
 assert.equal(settingsSubpageGuidance('pay-template-library',{...multi,companyIds:['C']},'',false,false).next.companyId,'C')
})
