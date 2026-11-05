import test from 'node:test'
import assert from 'node:assert/strict'
import {createPinia} from 'pinia'
import WeekCopyDialog from '../../src/modules/workforce/components/WeekCopyDialog.vue'
import {localToInstant,localInput,weekMonday,validateShift,validateCorrection} from '../../src/modules/workforce/time.ts'
test('store-local time is independent of host timezone and rejects DST ambiguity',()=>{
 assert.equal(localToInstant('2026-10-28T09:30','Asia/Singapore'),'2026-10-28T01:30:00.000Z')
 assert.equal(localInput('2026-10-28T01:30:00Z','Asia/Singapore'),'2026-10-28T09:30')
 assert.throws(()=>localToInstant('2026-03-08T02:30','America/New_York'))
 assert.throws(()=>localToInstant('2026-11-01T01:30','America/New_York'))
 assert.equal(weekMonday('2026-11-01'),'2026-10-26')
})
test('shift validation requires explicit overnight and consistent break rules',()=>{
 const s={name:'Night',startTime:'22:00',endTime:'06:00',nextDay:false,breakMode:'duration',breakMinutes:30,breakAfterMinutes:360,breakStartOffset:0,breakEndOffset:0}
 assert.equal(validateShift(s),false);s.nextDay=true;assert.equal(validateShift(s),true)
 s.breakAfterMinutes=20;assert.equal(validateShift(s),false)
 s.breakMode='window';s.breakStartOffset=470;s.breakEndOffset=490;assert.equal(validateShift(s),false)
 s.breakEndOffset=480;assert.equal(validateShift(s),true)
})
test('correction validation rejects overlapping and out-of-session actual breaks',()=>{
 const c={inAt:'2026-10-28T01:00:00Z',outAt:'2026-10-28T09:00:00Z',reason:'Forgot',breaks:[{start:'2026-10-28T03:00:00Z',end:'2026-10-28T03:30:00Z'}]}
 assert.equal(validateCorrection(c,'2026-10-28T10:00:00Z'),true)
 c.breaks.push({start:'2026-10-28T03:15:00Z',end:'2026-10-28T04:00:00Z'});assert.equal(validateCorrection(c,'2026-10-28T10:00:00Z'),false)
})
import {createRenderer,defineComponent,h,nextTick,ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {workforceKey} from '../../src/modules/workforce/context.ts'
import ClockPage from '../../src/modules/workforce/pages/ClockPage.vue'
function treeRenderer(){const root={children:[]};const renderer=createRenderer({createElement:tag=>({tag,tagName:tag.toUpperCase(),children:[],props:{},addEventListener(){},removeEventListener(){},getRootNode(){return globalThis.document||{}},get options(){return this.children.filter(n=>n.tag==='option')},getAttribute(k){return this.props[k]}}),createText:text=>({text}),createComment:text=>({comment:text}),setText:(n,text)=>n.text=text,setElementText:(n,text)=>{n.text=text;n.children=[]},parentNode:n=>n?.parent||null,nextSibling:n=>{if(!n?.parent)return null;const a=n.parent.children;return a[a.indexOf(n)+1]||null},insert:(n,parent,anchor)=>{if(!parent)return;if(n?.parent)n.parent.children=n.parent.children.filter(v=>v!==n);n.parent=parent;const i=anchor?parent.children.indexOf(anchor):-1;if(i<0)parent.children.push(n);else parent.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children=n.parent.children.filter(v=>v!==n)},patchProp:(n,key,prev,value)=>n.props[key]=value});return {root,renderer}}
function nodes(root,predicate){const result=[];function walk(n){if(predicate(n))result.push(n);for(const c of n.children||[])walk(c)}walk(root);return result}
function textOf(root){return nodes(root,n=>n.text).map(n=>n.text).join(' ')}
const schedule={id:'s1',employeeId:'e1',employeeName:'One',employeeNo:'001',shiftId:'t1',workDate:'2026-10-28',startAt:'2026-10-28T01:00:00Z',endAt:'2026-10-28T10:00:00Z',timezone:'Asia/Singapore',status:'scheduled',shift:{id:'t1',name:'Day',startTime:'09:00',endTime:'18:00',nextDay:false,breakMode:'manual',breakMinutes:0,breakAfterMinutes:0,breakStartOffset:0,breakEndOffset:0,breakPaid:false,enabled:true,version:0},companyId:'c1',version:0}
const tick=async()=>{await new Promise(r=>setTimeout(r,0));await nextTick()}
async function mountClock(api,storage=new Map()){
 globalThis.sessionStorage={getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)}
 const {root,renderer}=treeRenderer(),store=ref('store1'),home=ref({myself:{id:'e1',accountId:'a1'}})
 const app=renderer.createApp(defineComponent({setup(){return()=>h(ClockPage)}}));app.provide(workforceKey,{store,home,api:()=>api,refresh:async()=>{}});app.use(createI18n({legacy:false,locale:'en',messages:{en:{}}}));app.component('RouterLink',{props:['to'],setup(props,{slots}){return()=>h('a',{href:props.to},slots.default?.())}});app.mount(root);await tick();return {root,app,store,storage}
}
test('clock blocks double submission and restores an uncertain command without a new UUID',async()=>{
 let calls=[],rejectRequest,resolveRequest;const api={get:async()=>({schedules:[schedule],active:null,attendance:null,actions:['IN'],serverTime:'2026-10-28T01:00:00Z'}),put:async(path,body)=>{calls.push({path,body});return new Promise((resolve,reject)=>{resolveRequest=resolve;rejectRequest=reject})}}
 const m=await mountClock(api);const select=nodes(m.root,n=>n.tag==='select')[0];select.props['onUpdate:modelValue']('s1');await tick();let button=nodes(m.root,n=>n.tag==='button'&&n.text==='Clock in')[0];button.props.onClick();button.props.onClick();await tick();assert.equal(calls.length,1);assert.equal(m.storage.size,1)
 rejectRequest(new Error('network'));await tick();assert.match(textOf(m.root),/uncertain/);const recover=nodes(m.root,n=>n.tag==='button'&&n.text==='Recover last action')[0];recover.props.onClick();await tick();assert.equal(calls.length,2);assert.deepEqual(calls[0],calls[1]);resolveRequest({id:'session1'});await tick();assert.equal(m.storage.size,0);m.app.unmount()
})
test('clock from another store offers original-store link and no clock actions',async()=>{
 const m=await mountClock({get:async()=>({schedules:[schedule],active:{scheduleId:'else',storeId:'store2',inAt:'2026-10-28T01:00:00Z',outAt:null,breakStartedAt:null},attendance:null,actions:['OUT'],serverTime:'2026-10-28T01:00:00Z'})});assert.match(textOf(m.root),/another store/);assert.equal(nodes(m.root,n=>n.tag==='a'&&n.props.href==='/stores/store2/workforce/clock').length,1);assert.equal(nodes(m.root,n=>n.tag==='button'&&n.text==='Clock out').length,0);m.app.unmount()
})
import {routeLocationKey,routerKey} from 'vue-router'
import ShiftForm from '../../src/modules/workforce/pages/ShiftForm.vue'
import CorrectionDetail from '../../src/modules/workforce/pages/CorrectionDetail.vue'
async function mountPage(component,api,{route={params:{},query:{}},profile={id:'e1',accountId:'a1'},locale='en'}={}){
 globalThis.Document=class Document {};globalThis.ShadowRoot=class ShadowRoot {};globalThis.document=Object.assign(new globalThis.Document(),{activeElement:null});const {root,renderer}=treeRenderer(),store=ref('store1'),home=ref({employeeManage:true,myself:profile,stores:[{id:'store1',name:'Store one'}]}),app=renderer.createApp(defineComponent({setup(){return()=>h(component)}}));app.provide(workforceKey,{store,home,api:()=>api,refresh:async()=>{}});app.provide(routeLocationKey,route);app.provide(routerKey,{push:async()=>{}});app.use(createI18n({legacy:false,locale,messages:{[locale]:{}}}));app.component('RouterLink',{props:['to'],setup(props,{slots}){return()=>h('a',{href:props.to},slots.default?.())}});app.mount(root);await tick();return {root,app,store,home}
}
test('shift form switches mode-specific fields and rejects implicit overnight before writing',async()=>{
 const calls=[],m=await mountPage(ShiftForm,{put:async(path,body)=>calls.push({path,body})});let inputs=nodes(m.root,n=>n.tag==='input');inputs[0].props['onUpdate:modelValue']('Night');inputs[1].props['onUpdate:modelValue']('22:00');inputs[2].props['onUpdate:modelValue']('06:00');const form=nodes(m.root,n=>n.tag==='form')[0];await form.props.onSubmit({preventDefault(){}});await tick();assert.equal(calls.length,0);assert.match(textOf(m.root),/required fields/)
 const mode=nodes(m.root,n=>n.tag==='select'&&nodes(n,o=>o.tag==='option'&&o.props.value==='duration').length)[0];mode.props['onUpdate:modelValue']('duration');await tick();assert.match(textOf(m.root),/Attendance threshold/);assert.doesNotMatch(textOf(m.root),/minutes from shift start/)
 mode.props['onUpdate:modelValue']('window');await tick();assert.doesNotMatch(textOf(m.root),/Attendance threshold/);assert.match(textOf(m.root),/minutes from shift start/)
 inputs=nodes(m.root,n=>n.tag==='input');nodes(m.root,n=>n.tag==='label'&&textOf(n)==='Ends the day after the actual start date')[0].children.find(n=>n.tag==='input').props['onUpdate:modelValue'](true);await form.props.onSubmit({preventDefault(){}});await tick();assert.equal(calls.length,1);assert.equal(calls[0].body.nextDay,true);assert.equal(calls[0].body.breakMode,'window');assert.equal(calls[0].body.breakMinutes,0);m.app.unmount()
})
test('clock discards responses captured under a previous store',async()=>{
 let finish;const m=await mountClock({get:()=>new Promise(resolve=>{finish=resolve})});m.store.value='store2';finish({schedules:[schedule],active:null,attendance:null,actions:['IN'],serverTime:'2026-10-28T01:00:00Z'});await tick();assert.equal(nodes(m.root,n=>n.tag==='select').length,0);m.app.unmount()
})
test('a manager cannot approve their own correction and may withdraw as applicant',async()=>{
 const correction={id:'c1',scheduleId:'s1',employeeId:'e1',employeeName:'One',basedSessionVersion:1,inAt:'2026-10-28T01:00:00Z',outAt:'2026-10-28T09:00:00Z',breaks:[],overrideBreak:false,reason:'Forgot',status:'pending',applicantId:'a1',reviewerId:null,reviewNote:null,version:0,createdAt:'2026-10-28T10:00:00Z'}
 const m=await mountPage(CorrectionDetail,{get:async()=>({attendance:{schedule,session:null,grossSeconds:null,breakSeconds:null,workSeconds:null,anomalies:['NOT_CLOCKED']},events:[],corrections:[correction]})},{route:{params:{id:'c1'},query:{schedule:'s1'}}});assert.match(textOf(m.root),/cannot review your own/);assert.equal(nodes(m.root,n=>n.tag==='button'&&n.text==='Approve').length,0);assert.equal(nodes(m.root,n=>n.tag==='button'&&n.text==='Withdraw').length,1);m.app.unmount()
})
import CorrectionForm from '../../src/modules/workforce/pages/CorrectionForm.vue'
test('correction form submits multiple actual breaks as explicit store-zone instants',async()=>{
 const old={...schedule,workDate:'2026-10-27',startAt:'2026-10-27T01:00:00Z',endAt:'2026-10-27T10:00:00Z'},calls=[]
 const m=await mountPage(CorrectionForm,{get:async()=>({attendance:{schedule:old,session:null,grossSeconds:null,breakSeconds:null,workSeconds:null,anomalies:['NOT_CLOCKED']},events:[],corrections:[]}),put:async(path,body)=>calls.push({path,body})},{route:{params:{},query:{schedule:'s1'}}})
 let add=nodes(m.root,n=>n.tag==='button'&&n.text==='Add break interval')[0];add.props.onClick();add.props.onClick();await tick();let inputs=nodes(m.root,n=>n.tag==='input'&&n.props.type==='datetime-local');const values=['2026-10-27T09:00','2026-10-27T18:00','2026-10-27T12:00','2026-10-27T12:30','2026-10-27T15:00','2026-10-27T15:10'];inputs.forEach((n,i)=>n.props['onUpdate:modelValue'](values[i]));nodes(m.root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('Forgot all clocks');await tick();await nodes(m.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.equal(calls.length,1);assert.equal(calls[0].body.inAt,'2026-10-27T01:00:00.000Z');assert.deepEqual(calls[0].body.breaks,[{start:'2026-10-27T04:00:00.000Z',end:'2026-10-27T04:30:00.000Z'},{start:'2026-10-27T07:00:00.000Z',end:'2026-10-27T07:10:00.000Z'}]);assert.equal(calls[0].body.basedSessionVersion,0);assert.equal(calls[0].body.overrideBreak,true);m.app.unmount()
})
test('clock recovery survives remount and does not expose another action first',async()=>{
 const storage=new Map([['rpb.workforce.clock:a1:store1',JSON.stringify({commandId:'prior-command',scheduleId:'s1',action:'IN'})]]),calls=[]
 const m=await mountClock({get:async()=>({schedules:[schedule],active:null,attendance:null,actions:['IN'],serverTime:'2026-10-28T01:00:00Z'}),put:async(path,body)=>calls.push({path,body})},storage);assert.equal(nodes(m.root,n=>n.tag==='button'&&n.text==='Clock in').length,0);nodes(m.root,n=>n.tag==='button'&&n.text==='Recover last action')[0].props.onClick();await tick();assert.equal(calls[0].path,'/clock/prior-command');assert.deepEqual(calls[0].body,{scheduleId:'s1',action:'IN'});assert.equal(storage.size,0);m.app.unmount()
})
test('turning actual-break override off sends no stale manual intervals',async()=>{
 const shift={...schedule.shift,breakMode:'duration',breakMinutes:30,breakAfterMinutes:360},old={...schedule,shift,workDate:'2026-10-27',startAt:'2026-10-27T01:00:00Z',endAt:'2026-10-27T10:00:00Z'},calls=[]
 const session={id:'x',scheduleId:'s1',employeeId:'e1',storeId:'store1',inAt:'2026-10-27T01:00:00Z',outAt:'2026-10-27T10:00:00Z',breakStartedAt:null,breaks:[{start:'2026-10-27T04:00:00Z',end:'2026-10-27T04:30:00Z'}],overrideBreak:true,version:2}
 const m=await mountPage(CorrectionForm,{get:async()=>({attendance:{schedule:old,session,grossSeconds:32400,breakSeconds:1800,workSeconds:30600,anomalies:[]},events:[],corrections:[]}),put:async(path,body)=>calls.push(body)},{route:{params:{},query:{schedule:'s1'}}});nodes(m.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0].props['onUpdate:modelValue'](false);nodes(m.root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('Use shift rule');await tick();await nodes(m.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.deepEqual(calls[0].breaks,[]);m.app.unmount()
})
test('head-office manager without a linked employee can review another employee',async()=>{
 const c={id:'other-c',scheduleId:'s1',employeeId:'e1',employeeName:'One',basedSessionVersion:0,inAt:'2026-10-27T01:00:00Z',outAt:'2026-10-27T09:00:00Z',breaks:[],overrideBreak:false,reason:'Forgot',status:'pending',applicantId:'a1',reviewerId:null,reviewNote:null,version:3,createdAt:'2026-10-27T10:00:00Z'},calls=[]
 const m=await mountPage(CorrectionDetail,{get:async()=>({attendance:{schedule,session:null,grossSeconds:null,breakSeconds:null,workSeconds:null,anomalies:['NOT_CLOCKED']},events:[],corrections:[c]}),put:async(path,body)=>calls.push({path,body})},{route:{params:{id:'other-c'},query:{schedule:'s1'}},profile:null});assert.equal(nodes(m.root,n=>n.tag==='button'&&n.text==='Approve').length,1);assert.equal(nodes(m.root,n=>n.tag==='button'&&n.text==='Withdraw').length,0);nodes(m.root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('Checked roster');await tick();await nodes(m.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.deepEqual(calls[0],{path:'/corrections/other-c/decision',body:{expectedVersion:3,status:'approved',note:'Checked roster'}});m.app.unmount()
})
test('Chinese clock page explains empty roster and renders no unavailable actions',async()=>{
 const m=await mountPage(ClockPage,{get:async()=>({schedules:[],active:null,attendance:null,actions:[],serverTime:'2026-10-28T01:00:00Z'})},{locale:'zh-CN'});assert.match(textOf(m.root),/今日打卡/);assert.match(textOf(m.root),/当前没有可打卡排班/);assert.equal(nodes(m.root,n=>n.tag==='button').length,1);m.app.unmount()
})
import {addDays} from '../../src/modules/workforce/time.ts'
test('clearing a date filter keeps rendered date bounds safe',()=>{assert.equal(addDays('',30),'');assert.equal(weekMonday(''),'')})
import ScheduleList from '../../src/modules/workforce/pages/ScheduleList.vue'
import {WorkforceApiError} from '../../src/modules/workforce/api.ts'
test('copy conflict permits a fresh preview while uncertain commit replays the immutable command',async()=>{
 let fail=new WorkforceApiError(409,'WORKFORCE_COPY_CHANGED');const calls=[]
 const preview={fingerprint:'f'.repeat(64),sourceWeek:'2026-10-26',targetWeek:'2026-11-02',employeeCount:1,total:1,ready:1,existing:0,conflicts:0,timezone:'Asia/Singapore',items:[]}
 const Page=defineComponent({setup(){return()=>h(WeekCopyDialog,{sourceWeek:preview.sourceWeek,targetWeek:preview.targetWeek})}})
 const m=await mountPage(Page,{put:async(path,body)=>{if(path.endsWith('/preview'))return preview;calls.push({path,body});throw fail}})
 const submit=()=>nodes(m.root,n=>n.tag==='button'&&n.props.class==='primary')[0]
 const close=()=>nodes(m.root,n=>n.tag==='button'&&n.props.class==='wf-dialog-close')[0]
 await submit().props.onClick();await tick();assert.equal(close().props.disabled,false);assert.equal(submit(),undefined)
 fail=new Error('network');await nodes(m.root,n=>n.tag==='button'&&n.props.onClick?.name==='check')[0].props.onClick();await tick()
 await submit().props.onClick();await tick();assert.equal(close().props.disabled,true)
 await submit().props.onClick();await tick();assert.notEqual(calls[0].path,calls[1].path);assert.deepEqual(calls[1],calls[2]);m.app.unmount()
})
import WorkforceLayout from '../../src/modules/workforce/WorkforceLayout.vue'
async function mountLayout(home,path='/stores/store1/workforce/clock'){
 const originalFetch=globalThis.fetch,calls=[],navigated=[]
 globalThis.fetch=async(url)=>{calls.push(url);return {ok:true,json:async()=>home}}
 const {root,renderer}=treeRenderer(),route=ref({params:{storeId:'store1'},path,fullPath:path}),app=renderer.createApp(defineComponent({setup(){return()=>h(WorkforceLayout)}}));app.use(createPinia());app.provide(routeLocationKey,route.value);app.provide(routerKey,{push:async target=>navigated.push(target)});app.use(createI18n({legacy:false,locale:'en',messages:{en:{}},missingWarn:false,fallbackWarn:false}));app.component('RouterLink',{props:['to'],setup(props,{slots}){return()=>h('a',{href:props.to},slots.default?.())}});app.component('RouterView',{setup(){return()=>null}});app.mount(root);await tick();return {root,calls,navigated,route,unmount(){app.unmount();globalThis.fetch=originalFetch}}
}
test('ordinary employee header switches only between their own authorized clock stores',async()=>{
 const home={headOffice:false,employeeManage:false,stores:[],clockStores:[{id:'store1',name:'My first store',companyId:'c1'},{id:'store2',name:'My second store',companyId:'c1'}],companies:[],accounts:[],administratorAccounts:[],myself:{id:'e1',accountId:'a1'}}
 const m=await mountLayout(home);try{const select=nodes(m.root,n=>n.tag==='select')[0];assert.ok(select,'ordinary employee receives a store selector');assert.deepEqual(select.children.filter(n=>n.tag==='option').map(n=>n.props.value),['store1','store2']);select.props.onChange({target:{value:'store2'}});await tick();assert.deepEqual(m.navigated,['/stores/store2/workforce']);assert.deepEqual(m.calls.sort(),['/api/v1/stores/store1/workforce/home','/api/v1/stores/store1/workforce/payroll-home']);assert.doesNotMatch(textOf(m.root),/Other employee/)}finally{m.unmount()}
})
test('manager scheduling retains management scope while clock uses their personal stores',async()=>{
 const home={headOffice:true,employeeManage:true,stores:[{id:'store1',name:'Managed first',companyId:'c1'},{id:'managed-other',name:'Other employee management store',companyId:'c1'}],clockStores:[{id:'store1',name:'My first',companyId:'c1'},{id:'store2',name:'My second',companyId:'c1'}],companies:[],accounts:[],administratorAccounts:[],myself:{id:'e1',accountId:'a1'}}
 const m=await mountLayout(home,'/stores/store1/workforce/schedule');try{const ids=()=>nodes(m.root,n=>n.tag==='select')[0].children.filter(n=>n.tag==='option').map(n=>n.props.value);assert.deepEqual(ids(),['store1','managed-other']);m.route.value.path='/stores/store1/workforce/clock';m.route.value.fullPath=m.route.value.path;await tick();assert.deepEqual(ids(),['store1','store2'])}finally{m.unmount()}
})
test('clock distinguishes today’s completed shift from an empty roster and an older completed shift in store time',async()=>{
 for(const locale of ['en','zh-CN'])for(const c of [{workDate:'2026-10-28',serverTime:'2026-10-28T11:00:00Z',completed:true},{workDate:'2026-10-27',serverTime:'2026-10-28T11:00:00Z',completed:false},{workDate:'2026-10-28',serverTime:'2026-10-28T17:00:00Z',completed:false},{workDate:'2026-10-29',serverTime:'2026-10-28T17:00:00Z',completed:true}]){
  const roster={...schedule,workDate:c.workDate},session={id:'closed',scheduleId:'s1',employeeId:'e1',storeId:'store1',inAt:'2026-10-28T01:00:00Z',outAt:'2026-10-28T10:00:00Z',breakStartedAt:null,breaks:[],overrideBreak:false,version:2}
  const m=await mountPage(ClockPage,{get:async()=>({schedules:[],active:null,attendance:{schedule:roster,session,grossSeconds:32400,breakSeconds:0,workSeconds:32400,anomalies:[]},actions:[],serverTime:c.serverTime})},{locale});try{const text=textOf(m.root),finished=locale==='en'?'Clocked out for this shift':'本班已下班',empty=locale==='en'?'No eligible schedule. Contact an administrator.':'当前没有可打卡排班，请联系管理员。';assert.equal(text.includes(finished),c.completed,`${locale} ${c.workDate} ${c.serverTime}`);assert.equal(text.includes(empty),!c.completed)}finally{m.app.unmount()}
 }
})
