import {test,before,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
const result=await build({entryPoints:['src/modules/workforce/attendanceReview.ts'],bundle:true,write:false,format:'esm',platform:'node'})
const {reviewAllowed,reviewDecisionsValid,punchBreaks}=await import('data:text/javascript;base64,'+Buffer.from(result.outputFiles[0].contents).toString('base64'))
const home={employeeManage:true,myself:{id:'manager'}}
const row=(extra={})=>({schedule:{employeeId:'worker'},issues:[],approval:null,requiresReview:false,...extra})
const finding=(code)=>({code,status:'pending'})
const decision=(code,outcome='confirmed',note='已核实')=>({code,outcome,note})
test('legacy approvals remain compatible and self or employee cannot approve',()=>{assert.equal(reviewAllowed(row(),home,[]),true);assert.equal(reviewAllowed(row(),{employeeManage:false},[]),false);assert.equal(reviewAllowed(row(),{...home,myself:{id:'worker'}},[]),false)})
test('late early and break differences require a permitted decision while explanation is optional',()=>{for(const code of ['LATE','EARLY','BREAK_OVER','BREAK_SHORT']){const r=row({findings:[finding(code)]});assert.equal(reviewAllowed(r,home,[]),false);assert.equal(reviewAllowed(r,home,[decision(code,'confirmed',' ')]),true);assert.equal(reviewAllowed(r,home,[decision(code,'absence')]),false);assert.equal(reviewAllowed(r,home,[decision(code)]),true);assert.equal(reviewAllowed(r,home,[decision(code,'excused')]),true);assert.equal(reviewDecisionsValid(r,[decision(code),decision(code)]),false)}})
test('missing attendance permits only confirmed absence and only missing-in blocker',()=>{const r=row({issues:['MISSING_IN'],findings:[finding('MISSING')]});assert.equal(reviewAllowed(r,home,[decision('MISSING')]),false);assert.equal(reviewAllowed(r,home,[decision('MISSING','absence')]),true);for(const issue of ['MISSING_OUT','OPEN_BREAK','BREAK_RECORD_MISSING','PENDING_LEAVE'])assert.equal(reviewAllowed({...r,issues:[issue]},home,[decision('MISSING','absence')]),false)})
test('missing required break blocks approval even without an issues entry',()=>{assert.equal(reviewAllowed(row({findings:[finding('BREAK_MISSING')]}),home,[decision('BREAK_MISSING')]),false)})
test('approved leave findings need no second decision',()=>{assert.equal(reviewAllowed(row({findings:[{code:'LEAVE',status:'confirmed'}]}),home,[]),true)})
test('clock break selection excludes rules without punches and preserves legacy',()=>{assert.deepEqual(punchBreaks({rules:null}),[]);assert.deepEqual(punchBreaks({rules:{breaks:[{id:'a',punchMode:'none'},{id:'b',punchMode:'required'}]}}).map(b=>b.id),['b'])})

import {parse,compileScript} from '@vue/compiler-sfc'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {createRenderer,h,nextTick,ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {createRouter,createMemoryHistory} from 'vue-router'
let ui
const mounted=[],originalFetch=globalThis.fetch
function element(tag){const n={tag,props:{},children:[],addEventListener(){},removeEventListener(){},getRootNode(){return {}},style:{}};Object.defineProperty(n,'options',{get(){return nodes(n,x=>x.tag==='option')}});return n}
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>(n.text||'')+(n.children||[]).map(text).join(' ')
const renderer=createRenderer({createElement:element,createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},patchProp:(n,k,o,v)=>{n.props[k]=v;if(k==='value')n.value=v},insert:(n,p,a)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:n=>{const a=n.parent?.children||[];return a[a.indexOf(n)+1]||null}})
const settle=async()=>{for(let i=0;i<30;i++){await Promise.resolve();await nextTick()}}
const response=(data,status=200)=>({ok:status<400,status,json:async()=>data})
const schedule={id:'schedule',employeeId:'worker',employeeName:'Alice',employeeNo:'01',workDate:'2026-10-01',companyId:'C',status:'scheduled',timezone:'Asia/Singapore',startAt:'2026-10-01T01:00:00Z',endAt:'2026-10-01T10:00:00Z',shift:{name:'Day',breakMode:'manual',rules:{breaks:[{id:'lunch',name:'午餐',punchMode:'required'}]}}}
const admin={...home,companies:[],stores:[],accounts:[]}
before(async()=>{
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document={}
 const r=await build({stdin:{contents:"export {default as ApprovalDetail} from './src/modules/workforce/pages/ApprovalDetail.vue';export {default as ClockPage} from './src/modules/workforce/pages/ClockPage.vue';export {default as CorrectionForm} from './src/modules/workforce/pages/CorrectionForm.vue';export {default as AttendanceDetail} from './src/modules/workforce/pages/AttendanceDetail.vue';export {default as ApprovalList} from './src/modules/workforce/pages/ApprovalList.vue';export {workforceKey} from './src/modules/workforce/context';export {workforceApi} from './src/modules/workforce/api'",resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router','vue-i18n'],plugins:[{name:'vue',setup(b){b.onLoad({filter:/\.vue$/},async({path})=>{const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return {contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}})}}]})
 await mkdir('target',{recursive:true});await writeFile('target/attendance-review-ui-test.mjs',r.outputFiles[0].contents);ui=await import(pathToFileURL(process.cwd()+'/target/attendance-review-ui-test.mjs'))
})
afterEach(()=>{mounted.splice(0).forEach(a=>a.unmount());globalThis.fetch=originalFetch;delete globalThis.sessionStorage})
async function mount(Page,path,contextHome=admin){const router=createRouter({history:createMemoryHistory(),routes:[{path:'/stores/:storeId/workforce/:page/:scheduleId?',component:Page}]});await router.push(path);await router.isReady();const root=element('root'),app=renderer.createApp({render:()=>h(Page)});app.use(router);app.use(createI18n({legacy:false,locale:'zh-CN',messages:{}}));app.provide(ui.workforceKey,{store:ref('A'),home:ref(contextHome),api:()=>ui.workforceApi('A'),refresh:async()=>{}});app.mount(root);mounted.push(app);await settle();return root}
function approvalFixture(extra={}){return {row:{...row(),schedule,storeId:'A',workSeconds:18000,paidLeaveSeconds:0,unpaidLeaveSeconds:0,sourceFingerprint:'source',...extra},history:[]}}

test('approval submits an explicit finding decision with both explanation and overall note empty',async()=>{
 const puts=[],data=approvalFixture({findings:[{code:'LATE',name:'迟到',seconds:300,status:'pending',affectsBonus:true}]})
 globalThis.fetch=async(url,opt)=>{if(opt.method==='PUT'){puts.push(JSON.parse(opt.body));return response({})}return response(String(url).includes('/review-explanations')?{items:[],headOffice:true}:String(url).includes('/calendar')?{items:[]}:data)}
 const root=await mount(ui.ApprovalDetail,'/stores/A/workforce/approvals/schedule');nodes(root,n=>n.tag==='select')[0].props['onUpdate:modelValue']('excused');await settle()
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();assert.equal(puts.length,1);assert.equal(puts[0].note,'');assert.deepEqual(puts[0].decisions,[{code:'LATE',outcome:'excused',note:''}])
})
test('approval UI locks after lost response and retries exactly the same version fingerprint and decisions',async()=>{
 const puts=[],data=approvalFixture({findings:[{code:'LATE',name:'迟到',seconds:300,status:'pending',affectsBonus:true}],fullAttendanceStatus:'pending'})
 globalThis.fetch=async(url,opt)=>{if(opt.method==='PUT'){puts.push(opt.body);if(puts.length===1)throw Error('lost response');return response({})}return response(String(url).includes('/calendar')?{items:[]}:data)}
 const root=await mount(ui.ApprovalDetail,'/stores/A/workforce/approvals/schedule')
 const select=nodes(root,n=>n.tag==='select')[0],areas=nodes(root,n=>n.tag==='textarea');select.props['onUpdate:modelValue']('excused');areas[0].props['onUpdate:modelValue']('交通延误已核实');areas[1].props['onUpdate:modelValue']('核准');await settle()
 nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();assert.equal(puts.length,1);assert.match(text(root),/提交结果尚未确认/);assert.equal(nodes(root,n=>n.tag==='fieldset')[0].props.disabled,true)
 nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();assert.equal(puts.length,2);assert.equal(puts[0],puts[1]);assert.deepEqual(JSON.parse(puts[0]),{expectedVersion:0,sourceFingerprint:'source',note:'核准',decisions:[{code:'LATE',outcome:'excused',note:'交通延误已核实'}]})
})
test('employee approval detail is read only and missing break actual remains unrecorded',async()=>{
 const data=approvalFixture({findings:[{code:'BREAK_MISSING',name:'午餐缺卡',seconds:0,status:'pending',affectsBonus:true}],breakSummary:[{ruleId:'lunch',name:'午餐',paid:false,plannedSeconds:3600,actualSeconds:null,remainingSeconds:3600,overSeconds:0,shortSeconds:0,missing:true}]})
 globalThis.fetch=async url=>response(String(url).includes('/calendar')?{items:[]}:data)
 const root=await mount(ui.ApprovalDetail,'/stores/A/workforce/approvals/schedule',{...admin,employeeManage:false,myself:{id:'worker'}})
 assert.equal(nodes(root,n=>n.tag==='form').length,0);assert.match(text(root),/未记录/);assert.match(text(root),/请先补卡/)
})
test('clock start sends the selected and active rule and recover the identical command',async()=>{
 const puts=[],active={id:'session',scheduleId:'schedule',storeId:'A',breakStartedAt:null,breakStartedRuleId:null},data={schedules:[schedule],active,actions:['BREAK_START'],serverTime:'2026-10-01T03:00:00Z',attendance:{schedule,session:active,workSeconds:7200}}
 globalThis.sessionStorage={getItem:()=>null,setItem(){},removeItem(){}}
 globalThis.fetch=async(url,opt)=>{if(opt.method==='PUT'){puts.push({url,body:opt.body});if(puts.length===1)throw Error('lost');return response({})}return response(data)}
 const root=await mount(ui.ClockPage,'/stores/A/workforce/clock',{...admin,myself:{id:'worker',accountId:'account'}})
 nodes(root,n=>n.tag==='select')[0].props['onUpdate:modelValue']('lunch');await settle();nodes(root,n=>n.tag==='button'&&text(n)==='开始休息')[0].props.onClick();await settle();nodes(root,n=>n.tag==='button'&&text(n).includes('重试'))[0]?.props.onClick();
 const recover=nodes(root,n=>n.tag==='button'&&text(n).includes('恢复'))[0];if(recover)recover.props.onClick();await settle();assert.equal(puts.length,2);assert.deepEqual(puts[0],puts[1]);assert.equal(JSON.parse(puts[0].body).breakRuleId,'lunch')
})

test('clock ends the active break rule rather than a new selection',async()=>{
 let saved
 const active={id:'session',scheduleId:'schedule',storeId:'A',breakStartedAt:'2026-10-01T04:00:00Z',breakStartedRuleId:'lunch'},data={schedules:[schedule],active,actions:['BREAK_END'],serverTime:'2026-10-01T05:00:00Z',attendance:{schedule,session:active,workSeconds:7200}}
 globalThis.sessionStorage={getItem:()=>null,setItem(){},removeItem(){}}
 globalThis.fetch=async(url,opt)=>{if(opt.method==='PUT'){saved=JSON.parse(opt.body);return response({})}return response(data)}
 const root=await mount(ui.ClockPage,'/stores/A/workforce/clock',{...admin,myself:{id:'worker',accountId:'account'}})
 const button=nodes(root,n=>n.tag==='button'&&text(n)==='结束休息')[0];assert.ok(button);button.props.onClick();await settle();assert.equal(saved.breakRuleId,'lunch');assert.equal(saved.action,'BREAK_END')
})
test('correction keeps assigned break rule and legacy unassigned intervals in request',async()=>{
 let saved
 const session={version:3,inAt:'2026-10-01T01:00:00Z',outAt:'2026-10-01T10:00:00Z',overrideBreak:true,breaks:[{start:'2026-10-01T04:00:00Z',end:'2026-10-01T05:00:00Z',ruleId:'lunch'},{start:'2026-10-01T06:00:00Z',end:'2026-10-01T06:10:00Z'}]}
 const data={attendance:{schedule,session},corrections:[]}
 globalThis.fetch=async(url,opt)=>{if(opt.method==='PUT'){saved=JSON.parse(opt.body);return response({})}return response(data)}
 const root=await mount(ui.CorrectionForm,'/stores/A/workforce/corrections/new?schedule=schedule',{...admin,myself:{id:'worker'}})
 const selects=nodes(root,n=>n.tag==='select');assert.equal(selects.length,2);assert.equal(selects[0].props.required,true);assert.equal(selects[1].props.required,false)
 nodes(root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('核实休息记录');await settle();nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();assert.equal(saved.basedSessionVersion,3);assert.equal(saved.breaks[0].ruleId,'lunch');assert.equal(saved.breaks[1].ruleId,undefined)
})
test('missing attendance UI offers only absence and blocks incomplete decisions',async()=>{
 const data=approvalFixture({issues:['MISSING_IN'],findings:[{code:'MISSING',name:'缺卡',seconds:0,status:'pending',affectsBonus:true}]})
 globalThis.fetch=async url=>response(String(url).includes('/calendar')?{items:[]}:data)
 const root=await mount(ui.ApprovalDetail,'/stores/A/workforce/approvals/schedule')
 const select=nodes(root,n=>n.tag==='select')[0],values=nodes(select,n=>n.tag==='option').map(n=>n.props.value);assert.deepEqual(values,['','absence']);assert.match(text(root),/事实工时将为0，不自动扣款/)
 const submit=nodes(root,n=>n.tag==='button'&&text(n)==='确认工时')[0];assert.equal(submit.props.disabled,true)
 select.props['onUpdate:modelValue']('absence');const areas=nodes(root,n=>n.tag==='textarea');areas[0].props['onUpdate:modelValue']('核实未出勤');areas[1].props['onUpdate:modelValue']('核准');await settle();assert.equal(submit.props.disabled,false)
})

test('current approved findings restore and retain decisions through repeat approval and refresh',async()=>{
 const saved=[{code:'LATE',outcome:'excused',note:'已核实豁免'},{code:'MISSING',outcome:'absence',note:'已核实旷工'}],puts=[]
 const data=approvalFixture({issues:['MISSING_IN'],approval:{version:2,sourceFingerprint:'source',decisions:saved},findings:[{code:'LATE',name:'迟到',seconds:300,status:'excused',affectsBonus:true},{code:'MISSING',name:'旷工',seconds:0,status:'absence',affectsBonus:true}]})
 assert.equal(reviewAllowed(data.row,home,[]),false);assert.equal(reviewAllowed(data.row,home,saved),true)
 globalThis.fetch=async(url,opt)=>{if(opt.method==='PUT'){const body=JSON.parse(opt.body);puts.push(body);data.row.approval={version:body.expectedVersion+1,sourceFingerprint:body.sourceFingerprint,decisions:body.decisions};return response({})}return response(String(url).includes('/calendar')?{items:[]}:data)}
 const root=await mount(ui.ApprovalDetail,'/stores/A/workforce/approvals/schedule')
 const initial=nodes(root,n=>n.tag==='textarea');assert.equal(initial[0].value,'已核实豁免');assert.equal(initial[1].value,'已核实旷工');initial[2].props['onUpdate:modelValue']('再次核准');await settle();nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();assert.deepEqual(puts[0].decisions,saved);assert.equal(puts[0].expectedVersion,2)
 let areas=nodes(root,n=>n.tag==='textarea');assert.equal(areas[0].value,'已核实豁免');assert.equal(areas[1].value,'已核实旷工')
 nodes(root,n=>n.tag==='button'&&text(n)==='刷新')[0].props.onClick();await settle();areas=nodes(root,n=>n.tag==='textarea');areas[0].props['onUpdate:modelValue']('更新豁免说明');areas[2].props['onUpdate:modelValue']('复核');await settle();nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();assert.equal(puts[1].expectedVersion,3);assert.equal(puts[1].decisions[0].note,'更新豁免说明');assert.equal(puts[1].decisions[1].outcome,'absence')
})

test('disabled absence rule blocks missing approval and removes absence option',async()=>{
 const data=approvalFixture({schedule:{...schedule,shift:{...schedule.shift,rules:{...schedule.shift.rules,anomalies:[{code:'ABSENCE',enabled:false}]}}},issues:['MISSING_IN'],findings:[{code:'MISSING',name:'缺卡',seconds:0,status:'pending',affectsBonus:true}]})
 assert.equal(reviewAllowed(data.row,home,[decision('MISSING','absence')]),false)
 globalThis.fetch=async url=>response(String(url).includes('/calendar')?{items:[]}:data)
 const root=await mount(ui.ApprovalDetail,'/stores/A/workforce/approvals/schedule')
 const select=nodes(root,n=>n.tag==='select')[0];assert.equal(select.props.disabled,true);assert.deepEqual(nodes(select,n=>n.tag==='option').map(n=>n.props.value),['']);assert.match(text(root),/未启用旷工判定，缺卡只能先补卡或请假/)
})
