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
 const names=['ApprovalList','ApprovalDetail']
 ui=Object.fromEntries(await Promise.all(names.map(async name=>[name,(await import('../../../src/modules/workforce/pages/'+name+'.vue')).default])))
 ui.PayrollReadiness=(await import('../../../src/modules/workforce/components/PayrollApprovalReadiness.vue')).default
 ui.workforceApi=(await import('../../../src/modules/workforce/api.ts')).workforceApi
 ui.workforceKey=(await import('../../../src/modules/workforce/context.ts')).workforceKey
})
afterEach(()=>{mounted.splice(0).forEach(app=>app.unmount());globalThis.fetch=originalFetch})
async function mount(Page,path='/stores/A/workforce/management',home={employeeManage:false,headOffice:false,myself:{id:'E'}}){
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/stores/:storeId/workforce/leave-types/new',component:Page},{path:'/stores/:storeId/workforce/:page/:id?',component:Page}]});await router.push(path);await router.isReady()
 const root=element('root'),app=renderer.createApp({render:()=>h(Page)})
 app.use(router);app.use(createI18n({legacy:false,locale:'zh-CN',messages:{}}))
 app.provide(ui.workforceKey,{store:ref('A'),home:ref({companies:[],stores:[],...home}),payrollHome:ref({companies:[]}),payrollFailure:ref(''),current:()=>true,api:()=>ui.workforceApi('A'),refresh:async()=>{}})
 app.mount(root);mounted.push(app);await settle();return {root,router}
}


const button=(root,label)=>nodes(root,n=>n.tag==='button'&&text(n)===label)[0]
const admin={employeeManage:true,headOffice:true,myself:null}
const source='f'.repeat(64)
function entry(i=0,category='normal',canReview=true){const start='2026-10-28T01:00:00Z',end='2026-10-28T09:00:00Z';return {row:{schedule:{id:'S'+i,employeeId:'E'+i,employeeName:'Employee '+i,employeeNo:String(i).padStart(4,'0'),workDate:'2026-10-28',startAt:start,endAt:end,timezone:'Asia/Singapore',shift:{name:'Day',breakMode:'none',rules:null}},storeId:'A',workSeconds:28800,paidLeaveSeconds:0,unpaidLeaveSeconds:0,issues:[],findings:[],sourceFingerprint:source,approval:null,requiresReview:false,breakSummary:[]},inAt:start,outAt:end,category,canReview,reviewBlock:canReview?null:'WORKFORCE_MANAGER_ADMIN_REVIEW',managerEmployee:category==='manager'}}
const candidate=e=>({scheduleId:e.row.schedule.id,employeeName:e.row.schedule.employeeName,employeeNo:e.row.schedule.employeeNo,date:e.row.schedule.workDate,expectedVersion:0,sourceFingerprint:source,workSeconds:28800,paidLeaveSeconds:0,unpaidLeaveSeconds:0})
function fixture(rows,options={}){
 const calls=[],commits=[];let detailRow=rows.find(e=>e.category==='exception')||rows[0]
 globalThis.fetch=async(url,optionsRequest={})=>{
  const u=new URL(String(url),'http://localhost'),path=u.pathname.replace('/api/v1/stores/A/workforce','');calls.push(path+u.search)
  if(path==='/approval-board'&&!optionsRequest.body){const c=u.searchParams.get('category')||'all',offset=Number(u.searchParams.get('offset')||0),filtered=rows.filter(e=>c==='all'||e.category===c);const counts=Object.fromEntries(['all','normal','manager','exception','waiting','stale','current'].map(c=>[c,c==='all'?rows.length:rows.filter(e=>e.category===c).length]));return response({items:filtered.slice(offset,offset+50),total:filtered.length,limit:50,offset,counts,eligible:filtered.filter(e=>['normal','manager'].includes(e.category)&&e.canReview&&!e.row.approval).length,positions:[],timezone:'Asia/Singapore',days:[{date:'2026-10-28',total:rows.length,current:counts.current}]})}
  if(path==='/approval-board/preview'){const c=JSON.parse(optionsRequest.body),picked=rows.filter(e=>e.canReview&&['normal','manager'].includes(e.category)&&(c.all?!c.excluded.includes(e.row.schedule.id):c.ids.includes(e.row.schedule.id))).map(candidate);options.onPreview?.(c);return response({items:picked,workSeconds:picked.length*28800,paidLeaveSeconds:0,unpaidLeaveSeconds:0})}
  if(path==='/approval-board/commit'){const c=JSON.parse(optionsRequest.body);commits.push(c);if(options.loseFirst&&commits.length===1)throw Error('lost response');const result=c.items.map(item=>({scheduleId:item.scheduleId,success:item.scheduleId!==options.failId,code:item.scheduleId===options.failId?'WORKFORCE_CONFLICT':'OK'}));result.filter(r=>r.success).forEach(r=>{const e=rows.find(e=>e.row.schedule.id===r.scheduleId);e.category='current';e.row.approval={version:1};});return response({items:result})}
  if(path.startsWith('/approvals/')){if(optionsRequest.body){detailRow.category='current';detailRow.row.approval={version:1};detailRow.row.findings=detailRow.row.findings.map(f=>({...f,status:'confirmed'}));return response({version:1})}return response({row:detailRow.row,history:[],canReview:detailRow.canReview,reviewBlock:detailRow.reviewBlock})}
  if(path.startsWith('/calendar'))return response({items:[],timezone:'Asia/Singapore'})
  if(path==='/approval-readiness'){if(options.denySalary)return response({code:'WORKFORCE_FORBIDDEN'},403);return response({companyId:'C',month:'2026-10',ready:false,total:2,current:1,pending:1,stores:[{storeId:'A',storeName:'A store',total:2,current:1,pending:1,workSeconds:28800,paidLeaveSeconds:0,unpaidLeaveSeconds:0}]})}
  throw Error('Unexpected '+path)
 }
 return {calls,commits}
}
async function normalPreview(root){button(root,'选择筛选范围全部正常记录').props.onClick();await settle();button(root,'预览批量核准').props.onClick();await settle()}
async function confirm(root){nodes(root,n=>n.tag==='textarea').at(-1).props['onUpdate:modelValue']('Verified normal records');await settle();button(root,'确认核准').props.onClick();await settle()}
test('cross-page all selection excludes a later-page item and per-item failures remain visible',async()=>{
 let selection;const f=fixture(Array.from({length:65},(_,i)=>entry(i)),{onPreview:c=>selection=c,failId:'S0'});const {root}=await mount(ui.ApprovalList,'/stores/A/workforce/approvals',admin)
 button(root,'选择筛选范围全部正常记录').props.onClick();button(root,'下一页').props.onClick();await settle()
 const box=nodes(root,n=>n.tag==='input'&&n.props['aria-label']==='0050 2026-10-28')[0];box.props.onChange({target:{checked:false}});await settle();button(root,'预览批量核准').props.onClick();await settle();assert.equal(selection.all,true);assert.deepEqual(selection.excluded,['S50']);await confirm(root)
 assert.equal(f.commits[0].items.length,64);assert.ok(!f.commits[0].items.some(i=>i.scheduleId==='S50'));assert.match(text(root),/核准成功\s*63/);assert.match(text(root),/未核准\s*1/);assert.match(text(root),/Employee 0/)
})
test('unknown first chunk freezes controls and retries same 100 before processing remaining chunks',async()=>{
 const f=fixture(Array.from({length:205},(_,i)=>entry(i)),{loseFirst:true});const {root}=await mount(ui.ApprovalList,'/stores/A/workforce/approvals',admin);await normalPreview(root);await confirm(root)
 assert.equal(f.commits.length,1);assert.equal(nodes(root,n=>n.tag==='input'&&n.props.type==='date')[0].props.disabled,true);assert.ok(button(root,'重试原批次'));button(root,'重试原批次').props.onClick();await settle()
 assert.deepEqual(f.commits.map(c=>c.items.length),[100,100,100,5]);assert.deepEqual(f.commits[1],f.commits[0]);assert.match(text(root),/核准成功\s*205/)
})
test('manager-only row cannot be selected or approved through inline legacy detail',async()=>{
 fixture([entry(0,'manager',false)]);const {root}=await mount(ui.ApprovalList,'/stores/A/workforce/approvals',{employeeManage:true,headOffice:false,myself:null})
 const box=nodes(root,n=>n.tag==='input'&&n.props['aria-label']==='0000 2026-10-28')[0];assert.equal(box.props.disabled,true);button(root,'展开核准').props.onClick();await settle();assert.match(text(root),/经理工时须管理员核准/);assert.equal(button(root,'确认工时'),undefined)
})
test('exception can be judged and approved inline without losing workbench filter and query context',async()=>{
 const e=entry(0,'exception');e.row.findings=[{code:'LATE',name:'Late',seconds:600,status:'pending',affectsBonus:true}];e.row.schedule.shift.rules={breaks:[],anomalies:[{code:'LATE',enabled:true}]};const f=fixture([e]);const {root,router}=await mount(ui.ApprovalList,'/stores/A/workforce/approvals?from=2026-10-28&to=2026-10-28',admin)
 button(root,'展开核准').props.onClick();await settle();const select=nodes(root,n=>n.tag==='select'&&nodes(n,x=>x.tag==='option'&&x.props.value==='confirmed').length)[0];select.props['onUpdate:modelValue']('confirmed');const notes=nodes(root,n=>n.tag==='textarea');notes[0].props['onUpdate:modelValue']('Checked lateness');notes[1].props['onUpdate:modelValue']('Hours confirmed');await settle();await nodes(root,n=>n.tag==='form').at(-1).props.onSubmit({preventDefault(){}});await settle();assert.equal(router.currentRoute.value.path,'/stores/A/workforce/approvals');assert.ok(f.calls.some(path=>path.startsWith('/approvals/S0')));assert.match(text(root),/有效核准/)
})
test('salary readiness aggregates pending hours and respects permission failures',async()=>{
 fixture([]);const {root}=await mount({render:()=>h(ui.PayrollReadiness,{companyId:'C',month:'2026-10'})},'/stores/A/workforce/payroll',admin);assert.match(text(root),/待处理工时/);assert.match(text(root),/A store/)
 fixture([],{denySalary:true});const denied=await mount({render:()=>h(ui.PayrollReadiness,{companyId:'C',month:'2026-10'})},'/stores/A/workforce/payroll',admin);assert.ok(nodes(denied.root,n=>n.props?.role==='alert').length);assert.doesNotMatch(text(denied.root),/A store/)
})
