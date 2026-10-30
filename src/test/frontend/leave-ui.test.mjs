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
 const names=['LeaveForm','LeaveTypeForm','WorkforceManagement']
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

const scheduled={id:'S',employeeId:'E',workDate:'2026-10-29',status:'scheduled',version:1,startAt:'2026-10-29T03:00:00Z',endAt:'2026-10-29T15:00:00Z',timezone:'Asia/Singapore',shift:{name:'Day'}}
const enabledType={id:'T',name:'Custom leave',paid:true,enabled:true,version:1}
const button=(root,label)=>nodes(root,n=>n.tag==='button'&&text(n)===label)[0]
const times=root=>nodes(root,n=>n.tag==='input'&&n.props.type==='datetime-local')
const submit=async root=>{nodes(root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('Reason');await settle();await nodes(root,n=>n.tag==='form').at(-1).props.onSubmit({preventDefault(){}});await settle()}
function fixture(items=[scheduled],types=[enabledType,{...enabledType,id:'off',name:'Disabled',enabled:false}]){
 const writes=[];globalThis.fetch=async(path,options)=>{if(options?.method==='PUT'){writes.push(JSON.parse(options.body));return response({})}return response(path.startsWith('/schedule')?{items,timezone:'Asia/Singapore'}:types)};return writes
}
test('leave form displays configured enabled types and quick duration submits existing API fields',async()=>{
 const writes=fixture();const {root}=await mount(ui.LeaveForm,'/stores/A/workforce/leaves/new')
 const type=nodes(root,n=>n.tag==='select')[1];assert.match(text(type),/Custom leave/);assert.doesNotMatch(text(type),/Disabled/)
 button(root,'2小时').props.onClick();await settle()
 assert.deepEqual(times(root).map(n=>n.value),['2026-10-29T11:00','2026-10-29T13:00'])
 await submit(root);assert.deepEqual(writes[0],{scheduleId:'S',typeId:'T',startAt:'2026-10-29T03:00:00.000Z',endAt:'2026-10-29T05:00:00.000Z',reason:'Reason'})
})
test('custom duration uses edited start, invalid duration blocks save, whole shift preserves original bounds',async()=>{
 const writes=fixture();const {root}=await mount(ui.LeaveForm,'/stores/A/workforce/leaves/new')
 button(root,'1小时').props.onClick();await settle()
 times(root)[0].props['onUpdate:modelValue']('2026-10-29T22:00');await settle()
 const hours=nodes(root,n=>n.tag==='input'&&n.props.type==='number')[0];hours.props['onUpdate:modelValue'](2)
 button(root,'应用时长').props.onClick();await settle();assert.ok(nodes(root,n=>n.props?.role==='alert').length)
 await submit(root);assert.equal(writes.length,0)
 hours.props['onUpdate:modelValue'](0.5);button(root,'应用时长').props.onClick();await settle()
 assert.equal(times(root)[1].value,'2026-10-29T22:30')
 const whole=nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox').at(-1);whole.props['onUpdate:modelValue'](true);whole.props.onChange();await settle()
 await submit(root);assert.equal(writes[0].startAt,scheduled.startAt);assert.equal(writes[0].endAt,scheduled.endAt)
})
test('schedule change resets whole shift and quick range; other employees and cancelled shifts excluded',async()=>{
 fixture([scheduled,{...scheduled,id:'S2',startAt:'2026-10-29T14:00:00Z',endAt:'2026-10-29T22:00:00Z'},{...scheduled,id:'OTHER',employeeId:'other'},{...scheduled,id:'CANCEL',status:'cancelled'}]);const {root}=await mount(ui.LeaveForm,'/stores/A/workforce/leaves/new')
 button(root,'4小时').props.onClick();await settle()
 const select=nodes(root,n=>n.tag==='select')[0];assert.equal(nodes(select,n=>n.tag==='option').length,2)
 select.props['onUpdate:modelValue']('S2');select.props.onChange();await settle();assert.equal(times(root).length,0)
 button(root,'4小时').props.onClick();await settle();assert.deepEqual(times(root).map(n=>n.value),['2026-10-29T22:00','2026-10-30T02:00'])
})
test('no enabled types or eligible schedule hides submission; no employee identity makes no request',async()=>{
 fixture([],[]);const {root}=await mount(ui.LeaveForm,'/stores/A/workforce/leaves/new');assert.equal(nodes(root,n=>n.tag==='textarea').length,0)
 let calls=0;globalThis.fetch=async()=>{calls++;return response({})};await mount(ui.LeaveForm,'/stores/A/workforce/leaves/new',{employeeManage:true,myself:null});assert.equal(calls,0)
})
test('management exposes leave type maintenance to existing employee managers, not employees',async()=>{
 fixture();const {root}=await mount(ui.WorkforceManagement,'/stores/A/workforce/management',{employeeManage:true,headOffice:false})
 assert.ok(nodes(root,n=>n.tag==='a'&&n.props.href==='/stores/A/workforce/leave-types').length)
 const denied=await mount(ui.WorkforceManagement);assert.equal(nodes(denied.root,n=>n.tag==='a').length,0)
})
test('leave type maintenance saves custom name, paid status and disabling through existing versioned API',async()=>{
 const writes=fixture();const {root}=await mount(ui.LeaveTypeForm,'/stores/A/workforce/leave-types/new',{employeeManage:true})
 nodes(root,n=>n.tag==='input'&&n.props.maxlength==='100')[0].props['onUpdate:modelValue']('New custom type')
 const checks=nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox');checks[0].props['onUpdate:modelValue'](true);checks[1].props['onUpdate:modelValue'](false)
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
 assert.deepEqual(writes[0],{expectedVersion:0,name:'New custom type',paid:true,enabled:false})
})
test('type load failure leaves an error and no application submit controls',async()=>{
 globalThis.fetch=async path=>{if(path==='/leave-types')throw Error('offline');return response({items:[scheduled],timezone:'Asia/Singapore'})}
 const {root}=await mount(ui.LeaveForm,'/stores/A/workforce/leaves/new')
 assert.ok(nodes(root,n=>n.props?.role==='alert').length);assert.equal(nodes(root,n=>n.tag==='textarea').length,0)
})
test('ordinary employee cannot maintain leave types or issue configuration writes',async()=>{
 let calls=0;globalThis.fetch=async()=>{calls++;return response([])}
 const {root}=await mount(ui.LeaveTypeForm,'/stores/A/workforce/leave-types/new')
 assert.equal(calls,0);assert.equal(nodes(root,n=>n.tag==='form').length,0);assert.ok(nodes(root,n=>n.props?.role==='alert').length)
})

test('full shift then shortcut resets both bounds after editing a previous partial start',async()=>{
 fixture();const {root}=await mount(ui.LeaveForm,'/stores/A/workforce/leaves/new')
 button(root,'1小时').props.onClick();await settle();times(root)[0].props['onUpdate:modelValue']('2026-10-29T18:00')
 const whole=nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox').at(-1);whole.props['onUpdate:modelValue'](true);whole.props.onChange();await settle()
 button(root,'2小时').props.onClick();await settle();assert.deepEqual(times(root).map(n=>n.value),['2026-10-29T11:00','2026-10-29T13:00'])
})

test('shortcut is selected and a changed start recomputes the end; typing custom hours applies immediately',async()=>{
 fixture();const {root}=await mount(ui.LeaveForm,'/stores/A/workforce/leaves/new')
 button(root,'2小时').props.onClick();await settle();assert.equal(button(root,'2小时').props['aria-pressed'],true)
 times(root)[0].props['onUpdate:modelValue']('2026-10-29T18:00');await settle();assert.equal(times(root)[1].value,'2026-10-29T20:00')
 const custom=nodes(root,n=>n.tag==='input'&&n.props.type==='number')[0];custom.props['onUpdate:modelValue'](4);custom.props.onInput();await settle();assert.equal(times(root)[1].value,'2026-10-29T22:00');assert.match(text(root),/已选时长/)
 times(root)[1].props['onUpdate:modelValue']('2026-10-29T21:00');times(root)[1].props.onInput();await settle();assert.equal(button(root,'4小时').props['aria-pressed'],false)
})
async function rangeMount(fail=false){
 const writes=[];globalThis.fetch=async(path,options)=>{
  if(options?.method==='PUT'){writes.push(JSON.parse(options.body));if(fail&&writes.length===1)throw Error('network');return response([])}
  if(path.startsWith('/schedule')||path.startsWith('/leave-batch'))return response({items:[scheduled,{...scheduled,id:'S2',workDate:'2026-10-30',startAt:'2026-10-30T03:00:00Z',endAt:'2026-10-30T15:00:00Z'},{...scheduled,id:'C',workDate:'2026-10-31',status:'cancelled'}],timezone:'Asia/Singapore'})
  if(path.startsWith('/calendar'))return response({items:[],restSwaps:[]})
  if(path.startsWith('/leaves'))return response([])
  return response([enabledType])
 }
 const result=await mount(ui.LeaveForm,'/stores/A/workforce/leaves/new')
 const multi=nodes(result.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0];multi.props['onUpdate:modelValue'](true);multi.props.onChange();await settle()
 const dates=nodes(result.root,n=>n.tag==='input'&&n.props.type==='date');dates[0].props['onUpdate:modelValue']('2026-10-29');dates[1].props['onUpdate:modelValue']('2026-11-01')
 await nodes(result.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
 return {...result,writes}
}
test('multi-day preview skips cancelled and unscheduled dates and sends one frozen batch only after confirmation',async()=>{
 const {root,writes}=await rangeMount();assert.match(text(root),/24:00/);assert.match(text(root),/已取消，跳过/);assert.match(text(root),/无排班，跳过/)
 await submit(root);assert.equal(writes.length,0);assert.ok(button(root,'确认提交多天申请'))
 await nodes(root,n=>n.tag==='form').at(-1).props.onSubmit({preventDefault(){}});await settle()
 assert.equal(writes.length,1);assert.equal(writes[0].items.length,2);assert.deepEqual(writes[0].items.map(i=>i.request.scheduleId),['S','S2']);assert.equal(writes[0].to,'2026-11-01')
})
test('multi-day changed reason invalidates confirmation and lost response retries identical item IDs',async()=>{
 const {root,writes}=await rangeMount(true);await submit(root)
 nodes(root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('Changed');await settle();assert.ok(button(root,'预览多天申请'))
 await nodes(root,n=>n.tag==='form').at(-1).props.onSubmit({preventDefault(){}});await settle()
 await nodes(root,n=>n.tag==='form').at(-1).props.onSubmit({preventDefault(){}});await settle()
 assert.equal(writes.length,1);assert.match(text(root),/原请求已锁定/);assert.equal(nodes(root,n=>n.tag==='fieldset')[0].props.disabled,true)
 await nodes(root,n=>n.tag==='form').at(-1).props.onSubmit({preventDefault(){}});await settle();assert.equal(writes.length,2);assert.deepEqual(writes[1],writes[0])
})
const dayInput=(root,day,key)=>nodes(root,n=>n.tag==='input'&&n.props['aria-label']===day+' '+key)[0]
const dateCheck=(root,day)=>nodes(root,n=>n.tag==='input'&&n.props['aria-label']==='申请日期 '+day)[0]
test('daily edit and date exclusion recompute total, invalidate confirmation and submit only corrected included day',async()=>{
 const {root,writes}=await rangeMount();await submit(root);assert.ok(button(root,'确认提交多天申请'))
 dayInput(root,'2026-10-29','请假开始').props.onInput({target:{value:'2026-10-29T12:00'}})
 dayInput(root,'2026-10-29','请假结束').props.onInput({target:{value:'2026-10-29T14:30'}});await settle()
 assert.match(text(root),/14:30/);assert.ok(button(root,'预览多天申请'))
 dateCheck(root,'2026-10-30').props.onChange({target:{checked:false}});await settle()
 assert.match(text(root),/2:30/);assert.match(text(root),/已取消申请，跳过/);assert.equal(dayInput(root,'2026-10-30','请假开始').props.disabled,true)
 await nodes(root,n=>n.tag==='form').at(-1).props.onSubmit({preventDefault(){}});await settle();assert.equal(writes.length,0)
 await nodes(root,n=>n.tag==='form').at(-1).props.onSubmit({preventDefault(){}});await settle()
 assert.equal(writes[0].items.length,1);assert.equal(writes[0].items[0].request.scheduleId,'S');assert.equal(writes[0].items[0].request.startAt,'2026-10-29T04:00:00.000Z');assert.equal(writes[0].items[0].request.endAt,'2026-10-29T06:30:00.000Z')
})
test('invalid daily period blocks submission, exclusion clears block and restore preserves edits; all excluded cannot submit',async()=>{
 const {root}=await rangeMount()
 dayInput(root,'2026-10-29','请假开始').props.onInput({target:{value:'2026-10-29T10:00'}});await settle();assert.match(text(root),/时段超出班次/)
 const action=button(root,'预览多天申请');assert.equal(action.props.disabled,true)
 dateCheck(root,'2026-10-29').props.onChange({target:{checked:false}});await settle();assert.equal(button(root,'预览多天申请').props.disabled,false)
 dateCheck(root,'2026-10-29').props.onChange({target:{checked:true}});await settle();assert.equal(dayInput(root,'2026-10-29','请假开始').props.value,'2026-10-29T10:00');assert.equal(button(root,'预览多天申请').props.disabled,true)
 dateCheck(root,'2026-10-29').props.onChange({target:{checked:false}});dateCheck(root,'2026-10-30').props.onChange({target:{checked:false}});await settle();assert.equal(button(root,'预览多天申请').props.disabled,true)
})
test('unconfirmed batch disables daily controls and requery resets only the local daily draft',async()=>{
 const {root,writes}=await rangeMount(true)
 dayInput(root,'2026-10-29','请假结束').props.onInput({target:{value:'2026-10-29T15:00'}});await settle()
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();assert.equal(dayInput(root,'2026-10-29','请假结束').props.value,'2026-10-29T23:00')
 await submit(root);await nodes(root,n=>n.tag==='form').at(-1).props.onSubmit({preventDefault(){}});await settle()
 assert.equal(writes.length,1);assert.equal(dayInput(root,'2026-10-29','请假开始').props.disabled,true);assert.equal(dateCheck(root,'2026-10-29').props.disabled,true)
})
