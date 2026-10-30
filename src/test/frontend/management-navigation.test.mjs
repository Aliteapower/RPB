import {before,test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {readFile} from 'node:fs/promises'
import {parse,compileScript} from '@vue/compiler-sfc'

let managementNavigation
before(async()=>{
 const result=await build({entryPoints:['src/modules/workforce/managementNavigation.ts'],bundle:true,write:false,format:'esm',platform:'node'})
 ;({managementNavigation}=await import('data:text/javascript;base64,'+Buffer.from(result.outputFiles[0].contents).toString('base64')))
})
const manager={employeeManage:true,headOffice:true}
const payroll={companies:[{id:'C',name:'Company C'}]}
const route=(name,companyId)=>({name,params:{storeId:'A B'},query:companyId?{companyId}:{}})
test('shift edit returns to current store management and links its section',()=>{
 const nav=managementNavigation(route('workforce-shift','C'),manager,payroll,'zh-CN')
 assert.equal(nav.backLabel,'← 返回管理')
 assert.deepEqual(nav.backTo,{path:'/stores/A%20B/workforce/management',query:{companyId:'C'}})
 assert.deepEqual(nav.crumbs.map(c=>c.label),['管理','班次管理','编辑班次'])
 assert.equal(nav.crumbs[1].to.path,'/stores/A%20B/workforce/shifts')
 assert.equal(nav.crumbs[2].to,undefined)
})
test('all management lists and forms have a centralized navigation branch',()=>{
 for(const name of ['public-holidays','document-expiries','document-types','positions','companies','company','administrators','administrator','shifts','shift-new','shift','calendar','calendar-new','calendar-edit','leave-types','leave-type-new','leave-type','salary-templates','salary-template-new','salary-template','pay-profiles','pay-profile-new','pay-profile']){
  assert.ok(managementNavigation(route('workforce-'+name,'C'),manager,payroll,'zh-CN'),name)
 }
})
test('operations, payroll processing and management root have no return-management navigation',()=>{
 for(const name of ['management','home','employees','employee-new','schedule','schedule-edit','attendance','approval','work-summary','payroll','payroll-batch','payslip'])assert.equal(managementNavigation(route('workforce-'+name),manager,payroll,'zh-CN'),null,name)
})
test('management return respects employee, head-office and company access separately',()=>{
 const employee={employeeManage:false,headOffice:false}
 assert.equal(managementNavigation(route('workforce-calendar'),employee,null,'zh-CN'),null)
 assert.equal(managementNavigation(route('workforce-shift'),employee,payroll,'zh-CN'),null)
 assert.equal(managementNavigation(route('workforce-companies'),{employeeManage:true,headOffice:false},null,'zh-CN'),null)
 assert.ok(managementNavigation(route('workforce-calendar'),{employeeManage:true,headOffice:false},null,'zh-CN'))
 assert.equal(managementNavigation(route('workforce-salary-template','OTHER'),manager,payroll,'zh-CN'),null)
 assert.equal(managementNavigation(route('workforce-pay-profiles'),manager,null,'zh-CN'),null)
 const nav=managementNavigation(route('workforce-salary-template'),employee,payroll,'en-SG')
 assert.equal(nav.backTo.query.companyId,'C')
 assert.deepEqual(nav.crumbs.map(c=>c.label),['Management','Salary templates','Edit salary template'])
})
test('shared layout renders the accessible breadcrumb and visible return without changing business pages',async()=>{
 const source=await readFile('src/modules/workforce/WorkforceLayout.vue','utf8')
 assert.match(source,/managementNavigation\(/)
 assert.match(source,/managementNav\.backTo/)
 assert.match(source,/aria-current="page"/)
 const {descriptor}=parse(source,{filename:'WorkforceLayout.vue'})
 assert.ok(compileScript(descriptor,{id:'management-navigation-test',inlineTemplate:true}).content)
})


test('pay change return navigation is available for store governance or separate salary authority',()=>{const storeManager={employeeManage:true,headOffice:false},salaryOnly={employeeManage:false,headOffice:false};assert.ok(managementNavigation(route('workforce-pay-change-requests'),storeManager,null,'zh-CN'));assert.ok(managementNavigation(route('workforce-pay-change-requests','C'),salaryOnly,payroll,'en-SG'));assert.equal(managementNavigation(route('workforce-pay-change-requests','OTHER'),salaryOnly,payroll,'zh-CN'),null);assert.equal(managementNavigation(route('workforce-pay-change-requests'),salaryOnly,null,'zh-CN'),null)})
