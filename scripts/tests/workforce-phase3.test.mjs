import test from 'node:test'
import assert from 'node:assert/strict'
import {dateRangeValid,monthEnd,leaveActions,approvalAllowed,leavePeriodValid} from '../../src/modules/workforce/timekeepingUi.ts'

test('date range is inclusive 31 days and rejects empty or invalid dates',()=>{
 assert.equal(monthEnd('2026-11-01'),'2026-11-30')
 assert.equal(monthEnd('2028-02-01'),'2028-02-29')
 assert.equal(dateRangeValid('2026-10-01','2026-10-31'),true)
 assert.equal(dateRangeValid('2026-10-01','2026-11-01'),false)
 assert.equal(dateRangeValid('','2026-10-31'),false)
 assert.equal(dateRangeValid('2026-02-30','2026-03-01'),false)
})
test('leave decisions restrict own review and require reviewed cancellation',()=>{
 const item={status:'pending',applicantId:'a1',employeeId:'e1'}
 assert.deepEqual(leaveActions(item,{employeeManage:true,myself:{id:'e1',accountId:'a1'}}),['withdrawn'])
 assert.deepEqual(leaveActions({...item,status:'approved'},{employeeManage:false,myself:{id:'e1',accountId:'a1'}}),['cancel_requested'])
 assert.deepEqual(leaveActions({...item,status:'cancel_requested'},{employeeManage:true,myself:null}),['cancelled','cancel_rejected'])
 assert.deepEqual(leaveActions(item,{employeeManage:true,myself:{id:'e1',accountId:'other'}}),[])
})
test('approval requires manager, complete sources and a different employee',()=>{
 const row={issues:[],schedule:{employeeId:'e1'},approval:null,requiresReview:false}
 assert.equal(approvalAllowed(row,{employeeManage:true,myself:null}),true)
 assert.equal(approvalAllowed(row,{employeeManage:true,myself:{id:'e1'}}),false)
 assert.equal(approvalAllowed({...row,issues:['MISSING_IN']},{employeeManage:true,myself:null}),false)
 assert.equal(approvalAllowed({...row,approval:{version:1}},{employeeManage:true,myself:null}),false)
 assert.equal(approvalAllowed({...row,approval:{version:1},requiresReview:true},{employeeManage:true,myself:null}),true)
})
test('leave period must fit the selected scheduled shift and may cross midnight',()=>{
 const s={status:'scheduled',startAt:'2026-10-28T14:00:00Z',endAt:'2026-10-28T22:00:00Z'}
 assert.equal(leavePeriodValid(s,'2026-10-28T14:00:00Z','2026-10-28T22:00:00Z','reason'),true)
 assert.equal(leavePeriodValid(s,'2026-10-28T13:00:00Z','2026-10-28T22:00:00Z','reason'),false)
 assert.equal(leavePeriodValid(s,s.startAt,s.startAt,'reason'),false)
})

import {createRenderer,defineComponent,h,nextTick,ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {routeLocationKey,routerKey} from 'vue-router'
import {workforceKey} from '../../src/modules/workforce/context.ts'
import LeaveDetail from '../../src/modules/workforce/pages/LeaveDetail.vue'
import LeaveForm from '../../src/modules/workforce/pages/LeaveForm.vue'
import LeaveTypeForm from '../../src/modules/workforce/pages/LeaveTypeForm.vue'
import CalendarList from '../../src/modules/workforce/pages/CalendarList.vue'
import ApprovalDetail from '../../src/modules/workforce/pages/ApprovalDetail.vue'
import WorkSummary from '../../src/modules/workforce/pages/WorkSummary.vue'
function treeRenderer(){const root={children:[]};const renderer=createRenderer({createElement:tag=>({tag,tagName:tag.toUpperCase(),children:[],props:{},addEventListener(){},removeEventListener(){},getRootNode(){return globalThis.document||{}},get options(){return this.children.filter(n=>n.tag==='option')},getAttribute(k){return this.props[k]}}),createText:text=>({text}),createComment:text=>({comment:text}),setText:(n,text)=>n.text=text,setElementText:(n,text)=>{n.text=text;n.children=[]},parentNode:n=>n?.parent||null,nextSibling:n=>{if(!n?.parent)return null;const a=n.parent.children;return a[a.indexOf(n)+1]||null},insert:(n,parent,anchor)=>{if(!parent)return;if(n?.parent)n.parent.children=n.parent.children.filter(v=>v!==n);n.parent=parent;const i=anchor?parent.children.indexOf(anchor):-1;if(i<0)parent.children.push(n);else parent.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children=n.parent.children.filter(v=>v!==n)},patchProp:(n,key,prev,value)=>n.props[key]=value});return {root,renderer}}
function nodes(root,predicate){const result=[];function walk(n){if(predicate(n))result.push(n);for(const c of n.children||[])walk(c)}walk(root);return result}
function textOf(root){return nodes(root,n=>n.text).map(n=>n.text).join(' ')}

const tick=async()=>{await new Promise(r=>setTimeout(r,0));await nextTick()}
async function mountPage(component,api,{route={params:{},query:{}},profile={id:'e1',accountId:'a1'},locale='en',manager=true,headOffice=false,companies=[]}={}){
 globalThis.Document=class Document {};globalThis.ShadowRoot=class ShadowRoot {};globalThis.document=Object.assign(new globalThis.Document(),{activeElement:null});const {root,renderer}=treeRenderer(),store=ref('store1'),home=ref({employeeManage:manager,myself:profile,headOffice,companies}),app=renderer.createApp(defineComponent({setup(){return()=>h(component)}}));app.provide(workforceKey,{store,home,api:()=>api,refresh:async()=>{}});app.provide(routeLocationKey,route);app.provide(routerKey,{push:async()=>{}});app.use(createI18n({legacy:false,locale,messages:{[locale]:{}}}));app.component('RouterLink',{props:['to'],setup(props,{slots}){return()=>h('a',{href:props.to},slots.default?.())}});app.mount(root);await tick();return {root,app,store,home}
}

const schedule={id:'s1',employeeId:'e1',employeeName:'One',employeeNo:'001',workDate:'2026-10-28',startAt:'2026-10-28T01:00:00Z',endAt:'2026-10-28T09:00:00Z',timezone:'Asia/Singapore',status:'scheduled',companyId:'c1',shift:{name:'Day',breakMode:'none'}}
const leave={id:'l1',scheduleId:'s1',employeeId:'e1',employeeName:'One',typeId:'t1',typeName:'Annual',paid:true,startAt:schedule.startAt,endAt:schedule.endAt,reason:'Holiday',status:'pending',applicantId:'a1',reviewerId:null,reviewNote:null,version:1,createdAt:'2026-10-27T01:00:00Z'}
const route={params:{id:'l1'},query:{from:'2026-10-28',to:'2026-10-28'}}
function leaveApi(item=leave,put=async()=>{}){return {get:async p=>p.startsWith('/leaves')?[item]:{attendance:{schedule}},put}}
test('leave detail renders own withdrawal and never review controls',async()=>{
 const m=await mountPage(LeaveDetail,leaveApi(),{route});assert.match(textOf(m.root),/Withdraw/);assert.equal(nodes(m.root,n=>n.tag==='button'&&n.text==='Approve').length,0);assert.equal(nodes(m.root,n=>n.tag==='textarea').length,0);m.app.unmount()
})
test('approved leave exposes cancellation request rather than immediate deletion',async()=>{
 const calls=[],m=await mountPage(LeaveDetail,leaveApi({...leave,status:'approved'},async(p,b)=>calls.push({p,b})),{route});let button=nodes(m.root,n=>n.tag==='button'&&n.text==='Request cancellation')[0];assert.ok(button);assert.equal(button.props.disabled,true);const note=nodes(m.root,n=>n.tag==='textarea')[0];assert.ok(note,'cancellation reason must be editable');note.props['onUpdate:modelValue']('Returned to work');await tick();button=nodes(m.root,n=>n.tag==='button'&&n.text==='Request cancellation')[0];button.props.onClick();button.props.onClick();await tick();assert.equal(calls.length,1);assert.equal(calls[0].b.status,'cancel_requested');assert.equal(calls[0].b.note,'Returned to work');assert.equal(calls[0].b.expectedVersion,1);m.app.unmount()
})
test('another manager must provide note before reviewing cancellation',async()=>{
 const calls=[],m=await mountPage(LeaveDetail,leaveApi({...leave,status:'cancel_requested'},async(p,b)=>calls.push(b)),{route,profile:null});let buttons=nodes(m.root,n=>n.tag==='button');assert.equal(buttons.find(n=>n.text==='Approve cancellation').props.disabled,true);const note=nodes(m.root,n=>n.tag==='textarea')[0];note.props['onUpdate:modelValue']('Checked');await tick();buttons=nodes(m.root,n=>n.tag==='button');buttons.find(n=>n.text==='Approve cancellation').props.onClick();await tick();assert.equal(calls[0].status,'cancelled');assert.equal(calls[0].note,'Checked');m.app.unmount()
})
test('leave form submits exact full-shift endpoints including seconds',async()=>{
 const s={...schedule,startAt:'2026-10-28T01:00:45Z',endAt:'2026-10-28T09:00:32Z'},calls=[]
 const api={get:async p=>p==='/leave-types'?[{id:'t1',name:'Annual',paid:true,enabled:true}]:{items:[s],timezone:'Asia/Singapore'},put:async(p,b)=>calls.push(b)}
 const m=await mountPage(LeaveForm,api,{route:{params:{},query:{date:'2026-10-28'}}});nodes(m.root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('Holiday');await tick();await nodes(m.root,n=>n.tag==='form')[1].props.onSubmit({preventDefault(){}});await tick();assert.equal(calls.length,1);assert.equal(calls[0].startAt,s.startAt);assert.equal(calls[0].endAt,s.endAt);m.app.unmount()
})
test('leave form filters other employees even if manager schedule API includes them',async()=>{
 const api={get:async p=>p==='/leave-types'?[{id:'t1',name:'Annual',paid:true,enabled:true}]:{items:[{...schedule,employeeId:'e2'}],timezone:'Asia/Singapore'},put:async()=>assert.fail('no write')};const m=await mountPage(LeaveForm,api,{route:{params:{},query:{date:'2026-10-28'}}});assert.match(textOf(m.root),/No eligible shifts/);assert.equal(nodes(m.root,n=>n.tag==='textarea').length,0);m.app.unmount()
})
test('type form is unavailable to employees and has no write controls',async()=>{
 const m=await mountPage(LeaveTypeForm,{get:async()=>[],put:async()=>assert.fail('no write')},{manager:false});assert.match(textOf(m.root),/Access denied/);assert.equal(nodes(m.root,n=>n.tag==='input').length,0);m.app.unmount()
})
test('calendar preserves employee privacy controls and rejects cleared range without request',async()=>{
 let calls=0;const m=await mountPage(CalendarList,{get:async()=>{calls++;return {items:[{id:'c1',date:'2026-10-28',kind:'store_rest',name:'休息',enabled:true}],days:[{date:'2026-10-28',schedules:1,attended:0}],timezone:'Asia/Singapore'}},put:async()=>{}},{manager:false,locale:'zh-CN'});assert.match(textOf(m.root),/门店休息/);assert.equal(nodes(m.root,n=>n.tag==='a'&&String(n.props.href).includes('/calendar/new')).length,0);nodes(m.root,n=>n.tag==='input')[0].props['onUpdate:modelValue']('');await nextTick();await nodes(m.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.equal(calls,1);assert.match(textOf(m.root),/最多31天/);m.app.unmount()
})
function approvalApi(row,put=async()=>{}){return {get:async p=>p.startsWith('/approvals/')?{row,history:[]}:({items:[],days:[],timezone:'Asia/Singapore'}),put}}
const row={schedule,storeId:'store1',workSeconds:28800,paidLeaveSeconds:0,unpaidLeaveSeconds:0,issues:[],sourceFingerprint:'abc123',approval:null,requiresReview:false}
test('approval detail blocks self-approval and allows another manager with source version',async()=>{
 let m=await mountPage(ApprovalDetail,approvalApi(row),{route:{params:{scheduleId:'s1'},query:{}}});assert.match(textOf(m.root),/cannot approve your own/);assert.equal(nodes(m.root,n=>n.tag==='textarea').length,0);m.app.unmount()
 const calls=[];m=await mountPage(ApprovalDetail,approvalApi(row,async(p,b)=>calls.push(b)),{route:{params:{scheduleId:'s1'},query:{}},profile:null});nodes(m.root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('Checked');await tick();await nodes(m.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.deepEqual(calls[0],{expectedVersion:0,sourceFingerprint:'abc123',note:'Checked'});m.app.unmount()
})
test('stale approval is visibly separated and blocking issue keeps confirmation disabled',async()=>{
 const m=await mountPage(ApprovalDetail,approvalApi({...row,issues:['MISSING_IN'],requiresReview:true,approval:{version:1}}),{profile:null,route:{params:{scheduleId:'s1'},query:{}}});assert.match(textOf(m.root),/Sources changed; review required/);assert.match(textOf(m.root),/Missing attendance/);assert.equal(nodes(m.root,n=>n.tag==='button'&&n.props.class==='primary')[0].props.disabled,true);m.app.unmount()
})
test('employee cannot load management summary, company selector is headquarters-only',async()=>{
 let calls=0;let m=await mountPage(WorkSummary,{get:async()=>{calls++;return {items:[]}},put:async()=>{}},{manager:false});assert.equal(calls,0);assert.match(textOf(m.root),/Access denied/);m.app.unmount()
 m=await mountPage(WorkSummary,{get:async()=>({items:[]}),put:async()=>{}},{headOffice:false});assert.equal(nodes(m.root,n=>n.tag==='select').length,0);m.app.unmount()
 m=await mountPage(WorkSummary,{get:async()=>({items:[]}),put:async()=>{}},{headOffice:true,companies:[{id:'c1',name:'Company'}]});assert.equal(nodes(m.root,n=>n.tag==='select').length,1);m.app.unmount()
})
test('unmounted calendar ignores a delayed previous-store response',async()=>{
 let resolve;const m=await mountPage(CalendarList,{get:()=>new Promise(r=>resolve=r),put:async()=>{}});m.app.unmount();resolve({items:[],days:[{date:'2026-10-28',schedules:999,attended:999}],timezone:'Asia/Singapore'});await tick();assert.doesNotMatch(textOf(m.root),/999/)
})
