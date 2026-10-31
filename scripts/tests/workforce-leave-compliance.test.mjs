import test from 'node:test'
import assert from 'node:assert/strict'
import {createRenderer,h,nextTick,ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {createRouter,createMemoryHistory} from 'vue-router'
import {workforceKey} from '../../src/modules/workforce/context.ts'
import PolicyPage from '../../src/modules/workforce/pages/CompliancePolicySettings.vue'
import EmployeeLeaveSetup from '../../src/modules/workforce/pages/EmployeeLeaveSetup.vue'
import EncashmentPage from '../../src/modules/workforce/pages/LeaveEncashmentForm.vue'
import {reviewDecisionsValid} from '../../src/modules/workforce/attendanceReview.ts'
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>(n.text||'')+(n.children||[]).map(text).join(' ')
const settle=async()=>{for(let i=0;i<30;i++){await Promise.resolve();await nextTick()}}
function element(tag){return {tag,props:{},children:[],style:{},addEventListener(){},removeEventListener(){},getRootNode(){return {}},get options(){return nodes(this,n=>n.tag==='option')}}}
const renderer=createRenderer({createElement:element,createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},patchProp:(n,k,o,v)=>{n.props[k]=v;n[k]=v},insert:(n,p,a)=>{if(n.parent)n.parent.children=n.parent.children.filter(c=>c!==n);n.parent=p;const i=a?p.children.indexOf(a):-1;i<0?p.children.push(n):p.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children=n.parent.children.filter(c=>c!==n)},parentNode:n=>n?.parent,nextSibling:n=>{const a=n?.parent?.children||[];return a[a.indexOf(n)+1]||null}})
async function mount(Page,api,home={},path='/stores/S/workforce/employees/E/leave-settings'){
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document={}
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/stores/:storeId/workforce/employees/:employeeId/leave-settings',component:{render:()=>h('div')}},{path:'/:pathMatch(.*)*',component:{render:()=>h('div')}}]});await router.push(path)
 const root=element('root'),app=renderer.createApp({render:()=>h(Page)});app.use(router);app.use(createI18n({legacy:false,locale:'zh-CN',messages:{}}));app.provide(workforceKey,{store:ref('S'),home:ref({employeeManage:true,headOffice:true,companies:[{id:'C',name:'测试公司'}],stores:[],...home}),api:()=>api,refresh:async()=>{}});app.mount(root);await settle();return {root,app,router}
}
const rules={model:'sg-leave-hours-v1',annualDays:[7,8,9,10,11,12,13,14],eligibilityMonths:3,sickTiers:[{months:3,outpatient:5,hospital:15},{months:6,outpatient:14,hospital:60}],partTimeBelowHours:35,normalWeekHours:44,workmanThreshold:4500,nonWorkmanThreshold:2600,overtimeMinimum:1.5,carryMonths:12,partTimePrecision:1,maxDailyHours:12,maxMonthlyOvertimeHours:72,maxContinuousHours:6,minimumMealMinutes:45,shortWeekDailyHours:9,longWeekDailyHours:8,shortWeekMaxDays:5,companyCarryMonths:0,allowContractualEncashment:false,allowCompanyExtraEncashment:false}
const policy={id:'P',version:2,name:'已核对政策',effectiveFrom:'2026-01-01',effectiveTo:'2026-12-31',status:'published',rules,sources:[{code:'official',title:'官方依据',url:'https://www.mom.gov.sg/employment-practices/leave',summary:'可维护的官方规定摘要',checkedOn:'2026-10-31'}]}
test('policy version editing keeps server-owned fields and source summaries without writing or cloning a Vue proxy',async()=>{
 const writes=[],m=await mount(PolicyPage,{get:async()=>[policy],put:async(...p)=>writes.push(p)},{},'/stores/S/workforce/compliance-policies')
 try{const edit=nodes(m.root,n=>n.tag==='button'&&text(n).includes('核对／新增版本'))[0];assert.ok(edit);edit.props.onClick();await settle();assert.ok(nodes(m.root,n=>n.tag==='input'&&(n.value||n.props.value)==='已核对政策').length);assert.ok(nodes(m.root,n=>n.tag==='textarea'&&(n.value||n.props.value)==='可维护的官方规定摘要').length);assert.equal(writes.length,0);assert.equal(nodes(m.root,n=>n.tag==='button'&&text(n).includes('发布新版本'))[0].props.disabled,true)}finally{m.app.unmount()}
})
test('employee sees own balance with no contract or adjustment write controls',async()=>{
 const m=await mount(EmployeeLeaveSetup,{get:async path=>path.includes('employment-contracts')?{employeeId:'E',employeeName:'测试员工',canManage:false,items:[],policies:[]}:{employeeId:'E',employeeName:'测试员工',year:2026,canManage:false,balances:[]}}, {employeeManage:false,myself:{id:'E'}})
 try{assert.match(text(m.root),/尚未采用合同政策/);assert.equal(nodes(m.root,n=>n.tag==='button'&&text(n).includes('确认合同新版本')).length,0);assert.equal(nodes(m.root,n=>n.tag==='textarea').length,0)}finally{m.app.unmount()}
})
test('outside-schedule review needs a reason and never offers a generic excuse for overtime',()=>{
 const row={schedule:{shift:{}},findings:[{code:'OUTSIDE_SCHEDULE',status:'pending'}]}
 assert.equal(reviewDecisionsValid(row,[{code:'OUTSIDE_SCHEDULE',outcome:'excused',note:'No preapproval'}]),false)
 assert.equal(reviewDecisionsValid(row,[{code:'OUTSIDE_SCHEDULE',outcome:'confirmed',note:''}]),false)
 assert.equal(reviewDecisionsValid(row,[{code:'OUTSIDE_SCHEDULE',outcome:'confirmed',note:'Actual work independently verified'}]),true)
})
test('encashment starts with employee and entitlement selection and makes no salary write on mount',async()=>{
 const writes=[],calls=[],m=await mount(EncashmentPage,{get:async path=>{calls.push(path);return path==='/payroll-home'?{companies:[{id:'C',name:'测试公司'}]}:{items:[],employees:[{id:'E',name:'测试员工'}]}},put:async(...args)=>writes.push(args),post:async(...args)=>writes.push(args)},{},'/stores/S/workforce/leave-encashments?companyId=C')
 try{assert.match(text(m.root),/员工与额度/);assert.match(text(m.root),/与加班分账/);assert.deepEqual(calls,['/payroll-home','/pay-profiles?companyId=C']);assert.equal(writes.length,0)}finally{m.app.unmount()}
})
