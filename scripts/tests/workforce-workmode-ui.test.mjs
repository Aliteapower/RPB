import test from 'node:test'
import assert from 'node:assert/strict'
import {createRenderer,defineComponent,h,nextTick,ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {routeLocationKey} from 'vue-router'
import {workforceKey} from '../../src/modules/workforce/context.ts'
import WorkModePage from '../../src/modules/workforce/pages/WorkModePage.vue'
import {groupAssignments} from '../../src/modules/workforce/workModeDisplay.ts'

const assignment=(version,date,mode='field')=>({id:`a${version}`,employeeId:'e1',version,effectiveFrom:date,mode,origin:'position',positionId:'PRIVATE_POSITION_UUID',positionVersion:2,allowedModes:[mode]})
const assignments=[assignment(1,'2026-10-30','home'),assignment(2,'2026-10-31'),assignment(3,'2026-11-01'),assignment(4,'2026-11-02')]
const data={employeeId:'e1',employeeName:'张三',canManage:true,canReview:true,serverDate:'2026-10-31',assignments,policy:{mode:'field',assignmentId:'a2',options:[{mode:'field'}]},reviewerNames:{PRIVATE_REVIEWER_UUID:'李经理'},addresses:[{id:'h1',employeeId:'e1',point:{label:'Home',latitude:1.3,longitude:103.8,radiusMeters:100},effectiveFrom:'2026-11-01',status:'approved',version:2,applicantId:'worker',reviewerId:'PRIVATE_REVIEWER_UUID',reviewNote:'同意',createdAt:'2026-10-30T00:00:00Z'}]}
test('consecutive equal policies are compacted without merging current and future or losing versions',()=>{
 const groups=groupAssignments(assignments,'2026-10-31')
 assert.deepEqual(groups.map(g=>[g.status,g.entries.map(a=>a.version)]),[['upcoming',[3,4]],['current',[2]],['past',[1]]])
 assert.equal(assignments.length,4)
 assert.equal(groupAssignments([...assignments, {...assignment(5,'2026-11-03'),allowedModes:['field','home']}],'2026-10-31').length,4)
 assert.equal(groupAssignments([assignment(1,'2026-11-01')],'2026-10-31')[0].status,'upcoming')
 assert.equal(groups[2].until,'2026-10-31')
 assert.equal(groups[1].until,'2026-11-01')
 assert.equal(groups[0].until,null)
 const differentSource={...assignment(5,'2026-11-03'),positionVersion:3}
 assert.equal(groupAssignments([...assignments,differentSource],'2026-10-31').length,4)
})
function nodes(root,predicate){const result=[];function walk(n){if(predicate(n))result.push(n);for(const c of n.children||[])walk(c)}walk(root);return result}
function textOf(root){return nodes(root,n=>n.text).map(n=>n.text).join(' ')}
const tick=async()=>{await new Promise(r=>setTimeout(r,0));await nextTick()}
async function mount(value=data,{self=false,locale='zh-CN',put=async()=>{},get=async()=>structuredClone(value)}={}){
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document=Object.assign(new Document(),{activeElement:null})
 const root={children:[]},renderer=createRenderer({createElement:tag=>({tag,children:[],props:{},addEventListener(){},removeEventListener(){},getRootNode(){return globalThis.document},get options(){return this.children.filter(n=>n.tag==='option')}}),createText:text=>({text}),createComment:comment=>({comment}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},parentNode:n=>n?.parent||null,nextSibling:()=>null,insert:(n,p,a)=>{if(n.parent)n.parent.children=n.parent.children.filter(x=>x!==n);n.parent=p;const i=a?p.children.indexOf(a):-1;i<0?p.children.push(n):p.children.splice(i,0,n)},remove:n=>{if(n.parent)n.parent.children=n.parent.children.filter(x=>x!==n)},patchProp:(n,k,p,v)=>n.props[k]=v})
 const app=renderer.createApp(defineComponent({setup:()=>()=>h(WorkModePage)}))
 app.provide(workforceKey,{store:ref('store1'),home:ref({employeeManage:!self,myself:self?{id:'e1',accountId:'worker'}:null}),api:()=>({get,put}),refresh:async()=>{}})
 app.provide(routeLocationKey,{params:self?{}:{employeeId:'e1'},query:{}})
 app.use(createI18n({legacy:false,locale,messages:{[locale]:{}}}));app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:p.to},slots.default?.())});app.mount(root);await tick();return {root,app}
}
async function switchTo(m,key){nodes(m.root,n=>n.props?.['data-workmode-tab']===key)[0].props.onClick();await tick()}
test('functional pages isolate actions, mark effective policy and hide raw IDs',async()=>{
 const m=await mount();assert.equal(nodes(m.root,n=>n.tag==='form').length,1);assert.match(textOf(m.root),/当前执行/);assert.match(textOf(m.root),/待生效/);assert.doesNotMatch(textOf(m.root),/同意|PRIVATE_/)
 await switchTo(m,'addresses');assert.equal(nodes(m.root,n=>n.tag==='form').length,0);assert.match(textOf(m.root),/李经理/);assert.doesNotMatch(textOf(m.root),/PRIVATE_|当前执行/)
 await switchTo(m,'history');assert.equal(nodes(m.root,n=>n.tag==='form').length,0);assert.equal(nodes(m.root,n=>n.props?.['data-policy-group']).length,3);assert.doesNotMatch(textOf(m.root),/PRIVATE_/);m.app.unmount()
})
test('missing reviewer name never falls back to an ID; employee cannot edit policy or review own address',async()=>{
 const m=await mount({...data,canManage:false,canReview:false,reviewerNames:{}},{self:true});assert.equal(nodes(m.root,n=>n.tag==='form').length,0)
 await switchTo(m,'addresses');assert.equal(nodes(m.root,n=>n.tag==='form').length,1);assert.match(textOf(m.root),/姓名暂不可用/);assert.doesNotMatch(textOf(m.root),/PRIVATE_/);m.app.unmount()
})
test('English empty state and read failure stay usable',async()=>{
 let m=await mount({...data,assignments:[],addresses:[],policy:null},{locale:'en'});assert.match(textOf(m.root),/Current policy|No records/);await switchTo(m,'history');assert.match(textOf(m.root),/No records/);m.app.unmount()
 m=await mount(data,{get:async()=>{throw new Error('failed')}});assert.equal(nodes(m.root,n=>n.tag==='form').length,0);assert.ok(nodes(m.root,n=>n.props?.class==='wf-error').length);m.app.unmount()
})
test('policy submission preserves latest version, disables duplicate requests and retries the same command',async()=>{
 const calls=[];let reject
 const m=await mount(data,{put:async(path,body)=>{calls.push({path,body});return new Promise((resolve,no)=>{reject=no})}})
 const form=nodes(m.root,n=>n.tag==='form')[0]
 await form.props.onSubmit({preventDefault(){}});await tick()
 assert.equal(calls.length,1);assert.equal(calls[0].body.expectedVersion,4);assert.equal(calls[0].body.effectiveFrom,'2026-11-03');assert.equal(calls[0].body.inheritPosition,true)
 await form.props.onSubmit({preventDefault(){}});assert.equal(calls.length,1)
 reject(new Error('network'));await tick()
 const retry=nodes(m.root,n=>n.tag==='button'&&n.text==='重试原请求')[0];assert.ok(retry)
 retry.props.onClick();await tick();assert.deepEqual(calls[0],calls[1]);m.app.unmount()
})
test('self-review and expired request approval remain blocked in address page',async()=>{
 const pending={...data.addresses[0],status:'pending',reviewerId:null,effectiveFrom:'2026-10-30'}
 let m=await mount({...data,addresses:[pending]});await switchTo(m,'addresses');assert.match(textOf(m.root),/已无法批准/)
 nodes(m.root,n=>n.tag==='input'&&n.props.maxlength==='500')[0].props['onUpdate:modelValue']('核对过');await tick()
 const approve=nodes(m.root,n=>n.tag==='button'&&n.text==='批准')[0];assert.equal(approve.props.disabled,true);m.app.unmount()
 m=await mount({...data,canManage:false,canReview:false,addresses:[pending]},{self:true});await switchTo(m,'addresses');assert.match(textOf(m.root),/本人不能审批/);assert.equal(nodes(m.root,n=>n.tag==='button'&&n.text==='批准').length,0);m.app.unmount()
})
