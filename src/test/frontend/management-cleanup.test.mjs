import {test,before,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import {parse,compileScript} from '@vue/compiler-sfc'
import {createRenderer,h,nextTick,ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {createRouter,createMemoryHistory} from 'vue-router'
const mounted=[],originalFetch=globalThis.fetch
let ui
function element(tag){const n={tag,props:{},children:[],addEventListener(){},removeEventListener(){},getRootNode(){return {}},style:{}};Object.defineProperty(n,'options',{get(){return nodes(n,x=>x.tag==='option')}});return n}
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>(n.text||'')+(n.children||[]).map(text).join(' ')
const renderer=createRenderer({createElement:element,createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},patchProp:(n,k,o,v)=>{n.props[k]=v;if(k==='value')n.value=v},insert:(n,p,a)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:n=>{const a=n.parent?.children||[];return a[a.indexOf(n)+1]||null}})
const settle=async()=>{for(let i=0;i<30;i++){await Promise.resolve();await nextTick()}}
const response=(data,status=200)=>({ok:status<400,status,json:async()=>data})

const payroll={companies:[{id:'C',name:'Company C'},{id:'D',name:'Company D'}]}
before(async()=>{
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document={}
 const names=['WorkforceManagement','SalaryTemplateList','PayProfileList','PositionList','PositionPayPlans']
 ui=Object.fromEntries(await Promise.all(names.map(async name=>[name,(await import('../../../src/modules/workforce/pages/'+name+'.vue')).default])))
 ui.workforceKey=(await import('../../../src/modules/workforce/context.ts')).workforceKey
})
afterEach(()=>{mounted.splice(0).forEach(app=>app.unmount());globalThis.fetch=originalFetch})
async function mount(Page,path='/stores/A/workforce/management',payrollData=payroll,basicHome={employeeManage:true,headOffice:true}){
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/stores/:storeId/workforce/:page/:id?',component:Page}]});await router.push(path);await router.isReady()
 const root=element('root'),app=renderer.createApp({render:()=>h(Page)})
 app.use(router);app.use(createI18n({legacy:false,locale:'zh-CN',messages:{}}))
 app.provide(ui.workforceKey,{store:ref('A'),home:ref(basicHome),payrollHome:ref(payrollData),payrollFailure:ref(''),current:()=>true,api:()=>({get:async path=>(await globalThis.fetch(path)).json()}),refresh:async()=>{}})
 app.mount(root);mounted.push(app);await settle();return {root,router}
}
test('management removes company selector and keeps authorized payroll entries and position management',async()=>{
 const {root}=await mount(ui.WorkforceManagement)
 assert.equal(nodes(root,n=>n.tag==='select').length,0)
 for(const path of ['salary-templates','pay-profiles','positions'])assert.ok(nodes(root,n=>n.tag==='a'&&n.props.href==='/stores/A/workforce/'+path).length,path)
 const denied=await mount(ui.WorkforceManagement,undefined,{companies:[]})
 assert.equal(nodes(denied.root,n=>n.tag==='a'&&String(n.props.href).includes('salary-templates')).length,0)
})
test('management preserves authorized return-company context without adding a selector',async()=>{
 const {root}=await mount(ui.WorkforceManagement,'/stores/A/workforce/management?companyId=D')
 assert.equal(nodes(root,n=>n.tag==='select').length,0)
 for(const path of ['salary-templates','pay-profiles'])assert.ok(nodes(root,n=>n.tag==='a'&&n.props.href==='/stores/A/workforce/'+path+'?companyId=D').length,path)
 const denied=await mount(ui.WorkforceManagement,'/stores/A/workforce/management?companyId=OTHER')
 for(const path of ['salary-templates','pay-profiles'])assert.ok(nodes(denied.root,n=>n.tag==='a'&&n.props.href==='/stores/A/workforce/'+path).length,path)
})
for(const [page,endpoint] of [['SalaryTemplateList','salary-templates'],['PayProfileList','pay-profiles']]){
 test(page+' switches authorized company and preserves create context',async()=>{
  const requests=[]
  globalThis.fetch=async path=>{requests.push(path);const row={id:'row',name:'Template',employeeName:'Employee',effectiveFrom:'2026-10-01',rule:{basis:'hourly',rate:'10'},enabled:true};return response(path==='/payroll-home'?payroll:endpoint==='salary-templates'?[row]:{items:[row],employees:[]})}
  const {root,router}=await mount(ui[page],'/stores/A/workforce/'+endpoint+'?companyId=D')
  assert.ok(requests.includes('/'+endpoint+'?companyId=D'))
  const select=nodes(root,n=>n.tag==='select')[0]
  assert.deepEqual(nodes(select,n=>n.tag==='option').map(n=>n.props.value),['C','D'])
  select.props['onUpdate:modelValue']('C');select.props.onChange();await settle()
  assert.ok(requests.includes('/'+endpoint+'?companyId=C'))
  assert.equal(router.currentRoute.value.query.companyId,'C')
  if(page==='SalaryTemplateList'){
   assert.ok(nodes(root,n=>n.tag==='a'&&n.props.href==='/stores/A/workforce/pay-template-library?companyId=C').length,'authorized company context reaches the published template library')
   assert.equal(nodes(root,n=>n.tag==='a'&&n.props.href==='/stores/A/workforce/salary-templates/new?companyId=C').length,0,'legacy shared-template creation was replaced by tenant master publication')
  }else assert.ok(nodes(root,n=>n.tag==='a'&&n.props.href==='/stores/A/workforce/'+endpoint+'/new?companyId=C').length)
  assert.ok(nodes(root,n=>n.tag==='a'&&n.props.href==='/stores/A/workforce/'+endpoint+'/row?companyId=C').length)
 })
 test(page+' retains company selection after list failure and clears previous company rows',async()=>{
  let fail=false
  const row={id:'row',name:'Template C',employeeName:'Employee C',effectiveFrom:'2026-10-01',rule:{basis:'hourly',rate:'10'},enabled:true}
  globalThis.fetch=async path=>{if(path==='/payroll-home')return response(payroll);if(fail)throw Error('offline');return response(endpoint==='salary-templates'?[row]:{items:[row],employees:[]})}
  const {root}=await mount(ui[page],'/stores/A/workforce/'+endpoint)
  assert.ok(text(root).includes(endpoint==='salary-templates'?'Template C':'Employee C'))
  fail=true;const select=nodes(root,n=>n.tag==='select')[0];select.props['onUpdate:modelValue']('D');select.props.onChange();await settle()
  assert.equal(nodes(root,n=>n.tag==='select').length,1);assert.ok(nodes(root,n=>n.props?.role==='alert').length)
  assert.ok(!text(root).includes(endpoint==='salary-templates'?'Template C':'Employee C'))
 })
 test(page+' rejects company outside payroll grants without fetching records',async()=>{
  const requests=[];globalThis.fetch=async path=>{requests.push(path);return response(payroll)}
  const {root}=await mount(ui[page],'/stores/A/workforce/'+endpoint+'?companyId=OTHER')
  assert.deepEqual(requests,['/payroll-home']);assert.equal(nodes(root,n=>n.tag==='select').length,0)
  if(page==='SalaryTemplateList')assert.equal(nodes(root,n=>n.tag==='a'&&String(n.props.href).includes('pay-template-library')).length,0,'unauthorized company must not expose the creation/library action')
  assert.ok(nodes(root,n=>n.props?.role==='alert').length)
 })
}
test('duplicate management navigation is removed and attendance feature navigation retained',async()=>{
 for(const page of ['PositionList','CompanyList','ShiftList','SalaryTemplateList','PayProfileList','CalendarList','LeaveTypeList']){
  const source=await readFile('src/modules/workforce/pages/'+page+'.vue','utf8')
  const {descriptor}=parse(source,{filename:page+'.vue'});assert.ok(compileScript(descriptor,{id:page,inlineTemplate:true}).content)
  assert.doesNotMatch(source,/<nav[^>]*wf-subnav[^>]*>.*?management/s)
  if(['CalendarList','LeaveTypeList'].includes(page)){assert.match(source,/AttendanceSettingsNav/);const nav=await readFile('src/modules/workforce/components/AttendanceSettingsNav.vue','utf8');assert.match(nav,/path:'leave-types'/);assert.match(nav,/path:'public-holidays'/);assert.match(nav,/path:'calendar'/)}
 }
 const position=await readFile('src/modules/workforce/pages/PositionList.vue','utf8')
 for(const action of ['@submit.prevent="save"','@click="edit(p)"','@click="toggle(p)"','@click="remove(p)"'])assert.ok(position.includes(action))
})



test('setup guide shows one step at a time and one pay change entry with company context',async()=>{
 const {root}=await mount(ui.WorkforceManagement,'/stores/A/workforce/management?companyId=D')
 const steps=nodes(root,n=>n.tag==='button'&&n.props['aria-controls'])
 assert.equal(steps.length,4)
 assert.equal(steps[0].props['aria-current'],'step')
 assert.equal(nodes(root,n=>n.tag==='a'&&String(n.props.href).includes('/pay-change-requests')).length,1)
 assert.equal(nodes(root,n=>n.tag==='a'&&String(n.props.href).includes('/pay-change-requests'))[0].props.href,'/stores/A/workforce/pay-change-requests?companyId=D')
 const section=id=>nodes(root,n=>n.props?.id==='setup-step-'+id)[0]
 assert.notEqual(section(1).style.display,'none')
 assert.equal(section(3).style.display,'none')
 steps[2].props.onClick();await settle()
 assert.equal(section(1).style.display,'none')
 assert.notEqual(section(3).style.display,'none')
 assert.equal(steps[2].props['aria-current'],'step')
 const next=nodes(root,n=>n.tag==='button'&&text(n).includes('下一步：'))[0]
 next.props.onClick();await settle()
 assert.notEqual(section(4).style.display,'none')
 assert.ok(nodes(root,n=>n.tag==='a'&&n.props.href==='/stores/A/workforce/payroll?companyId=D').length)
})


test('returning from a salary setting restores step three and invalid steps fall back',async()=>{
 const {root}=await mount(ui.WorkforceManagement,'/stores/A/workforce/management?companyId=D&step=3')
 assert.equal(nodes(root,n=>n.props?.id==='setup-step-1')[0].style.display,'none')
 assert.notEqual(nodes(root,n=>n.props?.id==='setup-step-3')[0].style.display,'none')
 const invalid=await mount(ui.WorkforceManagement,'/stores/A/workforce/management?step=999')
 assert.notEqual(nodes(invalid.root,n=>n.props?.id==='setup-step-1')[0].style.display,'none')
})


test('position setup keeps the selected company for the next business task',async()=>{
 globalThis.fetch=async path=>response(path==='/payroll-home'?payroll:[])
 const {root,router}=await mount(ui.PositionPayPlans,'/stores/A/workforce/position-pay-plans?companyId=C')
 const select=nodes(root,n=>n.tag==='select'&&nodes(n,o=>o.tag==='option'&&o.props.value==='D').length)[0]
 select.props['onUpdate:modelValue']('D');select.props.onChange();await settle()
 assert.equal(router.currentRoute.value.query.companyId,'D')
})


test('settings menu defaults payroll links to the current store company, not the first company',async()=>{
 const {root}=await mount(ui.WorkforceManagement,'/stores/A/workforce/management',payroll,{employeeManage:true,headOffice:true,stores:[{id:'A',name:'Store',companyId:'D'}]})
 for(const page of ['position-pay-plans','salary-templates','pay-profiles'])assert.ok(nodes(root,n=>n.tag==='a'&&n.props.href==='/stores/A/workforce/'+page+'?companyId=D').length)
})
