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
 const names=['LeaveList']
 ui=Object.fromEntries(await Promise.all(names.map(async name=>[name,(await import('../../../src/modules/workforce/pages/'+name+'.vue')).default])))
 ui.workforceKey=(await import('../../../src/modules/workforce/context.ts')).workforceKey
})
afterEach(()=>{mounted.splice(0).forEach(app=>app.unmount());globalThis.fetch=originalFetch})
async function mount(Page,path='/stores/A/workforce/management',home={employeeManage:false,headOffice:false,myself:{id:'E'}}){
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/stores/:storeId/workforce/leave-types/new',component:Page},{path:'/stores/:storeId/workforce/:page/:id?',component:Page}]});await router.push(path);await router.isReady()
 const root=element('root'),app=renderer.createApp({render:()=>h(Page)})
 app.use(router);app.use(createI18n({legacy:false,locale:'zh-CN',messages:{}}))
 app.provide(ui.workforceKey,{store:ref('A'),home:ref(home),payrollHome:ref({companies:[]}),payrollFailure:ref(''),current:()=>true,api:()=>({get:async path=>(await globalThis.fetch(path)).json(),put:async(path,body)=>(await globalThis.fetch(path,{method:'PUT',body:JSON.stringify(body)})).json()}),refresh:async()=>{}})
 app.mount(root);mounted.push(app);await settle();return {root,router}
}


const member=(id,start,status='pending')=>({id,scheduleId:'S'+id,employeeId:'E',employeeName:'Alice',typeId:'T',typeName:'Annual',paid:true,startAt:start,endAt:new Date(Date.parse(start)+7200000).toISOString(),reason:'Range request',status,applicantId:'worker',reviewerId:null,reviewNote:null,version:1,createdAt:'2026-10-29T02:00:00Z'})
const button=(root,label)=>nodes(root,n=>n.tag==='button'&&(text(n)===label||text(n).startsWith(label+' ·')))[0]
async function setup(home={employeeManage:true,myself:null},fail=false,statuses=['pending','pending']){
 let items=[member('1','2026-10-29T03:00:00Z',statuses[0]),member('2','2026-10-30T03:00:00Z',statuses[1])];const calls=[]
 globalThis.fetch=async(path,options)=>{if(options?.method==='PUT'){const c=JSON.parse(options.body);calls.push({path,c});if(fail&&calls.length===1)throw Error('lost response');items=items.map(l=>(path.includes('leave-groups')?c.items.some(i=>i.id===l.id):path.includes('/'+l.id+'/decision'))?{...l,status:c.status==='cancel_rejected'?'approved':c.status,reviewNote:c.note,version:l.version+1}:l);return response(items)}return response({timezone:'Asia/Singapore',items:[{id:'G',grouped:true,from:'2026-10-29',to:'2026-10-30',items}]})}
 const result=await mount(ui.LeaveList,'/stores/A/workforce/leaves?from=2026-10-29&to=2026-10-30',home)
 const heading=nodes(result.root,n=>n.tag==='button'&&n.props['aria-expanded']!==undefined)[0];heading.props.onClick();await settle()
 return {...result,calls}
}
test('group opens daily details, sends all pending versions and refreshes status after unified approval',async()=>{
 const {root,calls}=await setup();assert.match(text(root),/多天申请/);assert.match(text(root),/4:00/);assert.match(text(root),/2026-10-29 11:00/)
 nodes(root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('Checked');await settle();button(root,'统一批准待审日期').props.onClick();await settle()
 assert.equal(calls.length,1);assert.equal(calls[0].path,'/leave-groups/G/decision');assert.deepEqual(calls[0].c,{status:'approved',note:'Checked',items:[{id:'1',expectedVersion:1},{id:'2',expectedVersion:1}]});assert.match(text(root),/已批准/);assert.equal(button(root,'统一批准待审日期'),undefined)
})
test('single-day approval leaves other day pending, subsequent group review sends remaining day only',async()=>{
 const {root,calls}=await setup();nodes(root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('One day');await settle()
 nodes(root,n=>n.tag==='button'&&text(n)==='批准')[0].props.onClick();await settle();assert.equal(calls[0].path,'/leaves/1/decision');assert.match(text(root),/待审核/)
 nodes(root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('Remaining');await settle();button(root,'统一拒绝待审日期').props.onClick();await settle();assert.deepEqual(calls[1].c.items,[{id:'2',expectedVersion:1}])
})
test('employee and employee-manager cannot approve own group',async()=>{
 for(const home of [{employeeManage:false,myself:{id:'E',accountId:'worker'}},{employeeManage:true,myself:{id:'E',accountId:'worker'}}]){
  const {root}=await setup(home);assert.equal(button(root,'统一批准待审日期'),undefined);assert.equal(nodes(root,n=>n.tag==='button'&&text(n)==='批准').length,0)
 }
})
test('lost group review response locks original command and retries same membership and note',async()=>{
 const {root,calls}=await setup(undefined,true);nodes(root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('Checked');await settle();button(root,'统一批准待审日期').props.onClick();await settle()
 assert.match(text(root),/原请求已锁定/);assert.equal(nodes(root,n=>n.tag==='textarea')[0].props.disabled,true)
 button(root,'重试原操作').props.onClick();await settle();assert.equal(calls.length,2);assert.deepEqual(calls[1],calls[0])
})

const ownHome={employeeManage:false,myself:{id:'E',accountId:'worker'}}
test('owner can withdraw pending group without note, approved dates remain, then cancellation requires reason and stays pending review',async()=>{
 const {root,calls}=await setup(ownHome,false,['pending','approved'])
 assert.ok(button(root,'整组撤回待审日期'));assert.ok(button(root,'整组申请销假已批准日期'));assert.equal(button(root,'整组撤回待审日期').props.disabled,false);assert.equal(button(root,'整组申请销假已批准日期').props.disabled,true)
 button(root,'整组撤回待审日期').props.onClick();await settle();assert.deepEqual(calls[0].c,{status:'withdrawn',note:'',items:[{id:'1',expectedVersion:1}]});assert.match(text(root),/已批准/)
 nodes(root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('Returned to work');await settle();button(root,'整组申请销假已批准日期').props.onClick();await settle()
 assert.deepEqual(calls[1].c,{status:'cancel_requested',note:'Returned to work',items:[{id:'2',expectedVersion:1}]});assert.match(text(root),/待销假审核/);assert.equal(button(root,'统一批准销假'),undefined)
})
test('manager can approve or reject cancellation group, while employee manager cannot review own cancellation',async()=>{
 const {root,calls}=await setup(undefined,false,['cancel_requested','cancel_requested'])
 assert.equal(button(root,'统一批准销假').props.disabled,true);nodes(root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('Keep original leave');await settle();button(root,'统一拒绝销假').props.onClick();await settle()
 assert.equal(calls[0].c.status,'cancel_rejected');assert.equal(calls[0].c.items.length,2);assert.match(text(root),/已批准/)
 const denied=await setup({...ownHome,employeeManage:true},false,['cancel_requested','cancel_requested']);assert.equal(button(denied.root,'统一批准销假'),undefined);assert.equal(button(denied.root,'统一拒绝销假'),undefined)
})
test('single day withdrawal and cancellation request remain available with exact item and version',async()=>{
 const {root,calls}=await setup(ownHome,false,['pending','approved'])
 button(root,'撤回申请').props.onClick();await settle();assert.equal(calls[0].path,'/leaves/1/decision');assert.equal(calls[0].c.status,'withdrawn')
 nodes(root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('Return');await settle();button(root,'申请销假').props.onClick();await settle();assert.deepEqual(calls[1],{path:'/leaves/2/decision',c:{expectedVersion:1,status:'cancel_requested',note:'Return'}})
})
test('unconfirmed owner cancellation freezes original operation and retries same approved members',async()=>{
 const {root,calls}=await setup(ownHome,true,['approved','approved']);nodes(root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('Return');await settle();button(root,'整组申请销假已批准日期').props.onClick();await settle()
 assert.match(text(root),/原请求已锁定/);assert.equal(nodes(root,n=>n.tag==='textarea')[0].props.disabled,true);button(root,'重试原操作').props.onClick();await settle();assert.deepEqual(calls[1],calls[0]);assert.equal(calls[0].c.status,'cancel_requested')
})
