import test from 'node:test'
import assert from 'node:assert/strict'
import {createRenderer,defineComponent,h,nextTick,ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {routeLocationKey,routerKey} from 'vue-router'
import {workforceKey} from '../../src/modules/workforce/context.ts'
import EmployeeForm from '../../src/modules/workforce/pages/EmployeeForm.vue'

const employee={id:'e1',employeeNo:'EMP001',name:'测试员工',englishName:'Test',birthDate:null,phone:'123',email:'',jobTitle:'服务员',positionId:'p1',hireDate:'2026-01-01',leaveDate:null,status:'active',enabled:true,companyId:'c1',companyEffectiveDate:'2026-01-01',mainStoreId:'s1',storeIds:['s1','s2'],accountId:'account-private',version:7,editable:true}
function nodes(root,predicate){const found=[];function walk(n){if(predicate(n))found.push(n);for(const c of n.children||[])walk(c)}walk(root);return found}
function text(root){return nodes(root,n=>n.text).map(n=>n.text).join(' ')}
const tick=async()=>{await new Promise(r=>setTimeout(r,0));await nextTick()}
async function mount({existing=true,record=employee,manage=true,locale='zh-CN',put=async()=>{},fail=false}={}){
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document=Object.assign(new Document(),{activeElement:null})
 const root={children:[]},renderer=createRenderer({createElement:tag=>({tag,children:[],props:{},addEventListener(){},removeEventListener(){},getRootNode(){return globalThis.document},get options(){return this.children.filter(n=>n.tag==='option')}}),createText:text=>({text}),createComment:comment=>({comment}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},parentNode:n=>n?.parent||null,nextSibling:n=>{if(!n?.parent)return null;const a=n.parent.children;return a[a.indexOf(n)+1]||null},insert:(n,p,a)=>{if(n.parent)n.parent.children=n.parent.children.filter(x=>x!==n);n.parent=p;const i=a?p.children.indexOf(a):-1;i<0?p.children.push(n):p.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children=n.parent.children.filter(x=>x!==n)},patchProp:(n,k,p,v)=>n.props[k]=v})
 const calls=[],pushes=[],api={get:async p=>{calls.push(p);if(fail)throw new Error('offline');if(p==='/employees/e1')return structuredClone(record);if(p==='/positions')return [{id:'p1',name:'服务员',enabled:true,version:1}];if(p.endsWith('/login-account'))return {username:'EMP001',status:'active',version:3,canReset:true,canChangeStatus:true,workforceAccess:true,employeeNo:'EMP001'};if(p.endsWith('/documents'))return {types:[],items:[]};throw new Error(p)},put}
 const app=renderer.createApp(defineComponent({setup:()=>()=>h(EmployeeForm)}));app.provide(workforceKey,{store:ref('s1'),home:ref({employeeManage:manage,headOffice:true,stores:[{id:'s1',name:'一店',companyId:'c1'}],companies:[{id:'c1',name:'测试公司'}],accounts:[]}),payrollHome:ref({companies:[]}),api:()=>api,refresh:async()=>{}});app.provide(routeLocationKey,{params:existing?{employeeId:'e1'}:{},query:{}});app.provide(routerKey,{push:async p=>pushes.push(p)});app.use(createI18n({legacy:false,locale,messages:{[locale]:{}}}));app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:typeof p.to==='string'?p.to:p.to.path},slots.default?.())});app.mount(root);await tick();return {root,app,calls,pushes}
}
function section(m,key){return nodes(m.root,n=>n.tag==='details'&&n.props['data-employee-task']===key)[0]}
async function toggle(m,key,open=true){const n=section(m,key);assert.ok(n,`missing ${key}`);n.props.onToggle({currentTarget:{open}});await tick()}
function field(m,key){return nodes(m.root,n=>n.props?.['data-field']===key)[0]}
test('existing profile focuses on business identity; secondary tasks stay folded and load only on demand',async()=>{
 const m=await mount();assert.deepEqual(m.calls,['/employees/e1','/positions']);assert.equal(section(m,'basic').props.open,true);for(const key of ['employment','login','pay','documents'])assert.equal(section(m,key).props.open,false);assert.equal(nodes(m.root,n=>n.props?.role==='tab').length,0);assert.match(text(m.root),/测试员工/);assert.doesNotMatch(text(m.root),/account-private/)
 await toggle(m,'login');assert.equal(m.calls.filter(p=>p.endsWith('/login-account')).length,1)
 const password=nodes(m.root,n=>n.tag==='input'&&n.props.type==='password')[0];password.props.onInput({target:{value:'abc123'}});await tick();await toggle(m,'login',false);await toggle(m,'login');assert.equal(m.calls.filter(p=>p.endsWith('/login-account')).length,1);assert.equal(nodes(m.root,n=>n.tag==='input'&&n.props.type==='password')[0].props.value,'abc123');m.app.unmount()
})
test('profile save retains employee version, store scope and account; secondary tasks do not submit',async()=>{
 const writes=[],m=await mount({put:async(p,b)=>writes.push({p,b})});await toggle(m,'employment');field(m,'name').props['onUpdate:modelValue']('新姓名');field(m,'status').props['onUpdate:modelValue']('left');await tick();field(m,'leaveDate').props['onUpdate:modelValue']('2026-10-31');await tick();await nodes(m.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.equal(writes.length,1);assert.equal(writes[0].p,'/employees/e1');assert.equal(writes[0].b.name,'新姓名');assert.equal(writes[0].b.expectedVersion,7);assert.equal(writes[0].b.leaveDate,'2026-10-31');assert.deepEqual(writes[0].b.storeIds,['s1','s2']);assert.equal(writes[0].b.accountId,'account-private');assert.deepEqual(m.pushes,['/stores/s1/workforce/employees']);m.app.unmount()
})
test('new employee onboarding opens required employment fields and avoids unsaved secondary requests',async()=>{
 const m=await mount({existing:false});assert.equal(section(m,'employment').props.open,true);assert.ok(field(m,'companyId'));assert.ok(field(m,'mainStoreId'));assert.ok(section(m,'account'));for(const k of ['pay','login','documents'])assert.equal(section(m,k),undefined);assert.deepEqual(m.calls,['/positions']);m.app.unmount()
})
test('invalid fields reveal their folded business section before focus',async()=>{
 const m=await mount();const outer={open:false,parentElement:null},inner={open:false,parentElement:{closest:()=>outer}},target={closest:()=>inner};nodes(m.root,n=>n.tag==='form')[0].props.onInvalidCapture({target});assert.equal(inner.open,true);assert.equal(outer.open,true);m.app.unmount()
})
test('permissions and read failures do not expose editor or secondary tasks; English has business labels',async()=>{
 for(const opts of [{manage:false},{record:{...employee,editable:false}},{fail:true}]){const m=await mount(opts);assert.equal(nodes(m.root,n=>n.tag==='form').length,0);assert.equal(nodes(m.root,n=>n.tag==='details').length,0);assert.ok(nodes(m.root,n=>n.props?.role==='alert').length);m.app.unmount()}
 const m=await mount({locale:'en'});assert.match(text(m.root),/Employee profile|Personal details/);m.app.unmount()
})
test('account setup retains draft when folded and saves employee-number login with profile',async()=>{
 const writes=[],m=await mount({record:{...employee,accountId:null},put:async(p,b)=>writes.push({p,b})});await toggle(m,'account');const mode=nodes(section(m,'account'),n=>n.tag==='select')[0];mode.props['onUpdate:modelValue']('create');await tick();field(m,'employeeNo').props['onUpdate:modelValue']('EMP002');await tick();nodes(section(m,'account'),n=>n.tag==='input'&&n.props.type==='password')[0].props.onInput({target:{value:'123abc'}});await tick();await toggle(m,'account',false);await toggle(m,'account');assert.equal(nodes(section(m,'account'),n=>n.tag==='input'&&n.props.type==='password')[0].props.value,'123abc');await nodes(m.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.equal(writes.length,1);assert.equal(writes[0].b.username,'EMP002');assert.equal(writes[0].b.password,'123abc');assert.equal(section(m,'login'),undefined);m.app.unmount()
})
test('documents load once on opening; pay restrictions remain intact without salary scope',async()=>{
 const m=await mount();assert.equal(m.calls.some(p=>p.endsWith('/documents')),false);await toggle(m,'documents');assert.equal(m.calls.filter(p=>p.endsWith('/documents')).length,1);assert.equal(nodes(section(m,'documents'),n=>n.tag==='h2').length,0);await toggle(m,'documents',false);await toggle(m,'documents');assert.equal(m.calls.filter(p=>p.endsWith('/documents')).length,1);await toggle(m,'pay');assert.equal(m.calls.some(p=>p.includes('pay-summary')),false);assert.equal(nodes(section(m,'pay'),n=>n.tag==='select').length,0);m.app.unmount()
})
test('failed profile save keeps the business draft for retry without changing target employee',async()=>{
 const writes=[],m=await mount({put:async(p,b)=>{writes.push({p,b});if(writes.length===1)throw new Error('offline')}});field(m,'name').props['onUpdate:modelValue']('保留录入');await tick();const form=nodes(m.root,n=>n.tag==='form')[0];await form.props.onSubmit({preventDefault(){}});await tick();assert.equal(field(m,'name').value,'保留录入');assert.deepEqual(m.pushes,[]);assert.ok(nodes(m.root,n=>n.props?.role==='alert').length);await form.props.onSubmit({preventDefault(){}});await tick();assert.deepEqual(writes[0],writes[1]);assert.equal(writes[1].p,'/employees/e1');m.app.unmount()
})
