import {afterEach,test} from 'node:test'
import assert from 'node:assert/strict'
import {createRenderer,h,nextTick,ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {createRouter,createMemoryHistory} from 'vue-router'
import {workforceKey} from '../../modules/workforce/context.ts'
import {WorkforceApiError} from '../../modules/workforce/api.ts'
import EmployeeLoginSettings from '../../modules/workforce/components/EmployeeLoginSettings.vue'
import EmployeePasswordInput from '../../modules/workforce/components/EmployeePasswordInput.vue'
const apps=[],calls=[]
const element=tag=>({tag,props:{},children:[],style:{},addEventListener(){},removeEventListener(){},getRootNode(){return {}},get options(){return nodes(this,n=>n.tag==='option')}})
const renderer=createRenderer({createElement:element,createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},patchProp:(n,k,o,v)=>{n.props[k]=v;if(k==='value')n.value=v},insert:(n,p,a)=>{if(n.parent)n.parent.children.splice(n.parent.children.indexOf(n),1);n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:n=>{const a=n.parent?.children||[];return a[a.indexOf(n)+1]||null}})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>(n.text||'')+(n.children||[]).map(text).join('')
const settle=async()=>{for(let i=0;i<20;i++){await Promise.resolve();await nextTick()}}
const button=(root,label)=>nodes(root,n=>n.tag==='button'&&text(n)===label)[0]
const checkbox=(root,label)=>nodes(root,n=>n.tag==='label'&&text(n)===label).flatMap(n=>nodes(n,c=>c.tag==='input'))[0]
const home={headOffice:true,employeeManage:true,stores:[],companies:[],accounts:[],administratorAccounts:[],myself:null}
let handler=async()=>({items:[],headOffice:true})
async function mount(Page,{path='/stores/A/workforce/shifts/new',manager=false,props={}}={}){
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document={}
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/stores/:storeId/workforce/management',component:{render:()=>null}},{path:'/stores/:storeId/workforce/shifts/new',component:Page},{path:'/stores/:storeId/workforce/shifts/:id',component:Page},{path:'/stores/:storeId/workforce/shifts',component:{render:()=>null}}]});await router.push(path)
 const root=element('root'),app=renderer.createApp({render:()=>h(Page,props)})
 app.use(router);app.use(createI18n({legacy:false,locale:'zh-CN',messages:{}}));app.provide(workforceKey,{store:ref('A'),home:ref({...home,headOffice:!manager}),api:()=>({get:async path=>{calls.push({path,method:'GET'});return handler(path)},put:async(path,body)=>{calls.push({path,method:'PUT',body:JSON.parse(JSON.stringify(body))});return handler(path,body)}}),refresh:async()=>{}});app.mount(root);apps.push(app);await settle();return root
}
afterEach(()=>{apps.splice(0).forEach(a=>a.unmount());calls.length=0;handler=async()=>({items:[],headOffice:true})})


const info={accountId:'account',username:'clock-alice',status:'active',version:0,workforceAccess:true,canReset:true}
test('employee account shows username and status without any existing password; blank causes no write',async()=>{
 handler=async()=>({...info});const root=await mount(EmployeeLoginSettings,{props:{employeeId:'E'}})
 assert.match(text(root),/clock-alice/);assert.match(text(root),/可登录/);assert.match(text(root),/已授权/)
 assert.equal(nodes(root,n=>n.tag==='input')[0].props.type,'password');assert.equal(nodes(root,n=>n.tag==='input')[0].props.value,'')
 assert.equal(button(root,'保存新密码').props.disabled,true)
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});assert.equal(calls.filter(c=>c.method==='PUT').length,0)
})
test('new password can be revealed and hidden and is saved separately then cleared',async()=>{
 handler=async(path,body)=>({...info,version:body?1:0});const root=await mount(EmployeeLoginSettings,{props:{employeeId:'E'}})
 let input=nodes(root,n=>n.tag==='input')[0];input.props.onInput({target:{value:'Abc987'}});await settle()
 button(root,'显示密码').props.onClick();await settle();assert.equal(nodes(root,n=>n.tag==='input')[0].props.type,'text')
 button(root,'隐藏密码').props.onClick();await settle();assert.equal(nodes(root,n=>n.tag==='input')[0].props.type,'password')
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
 assert.deepEqual(calls.find(c=>c.method==='PUT'),{path:'/employees/E/login-account',method:'PUT',body:{expectedVersion:0,password:'Abc987'}})
 assert.equal(nodes(root,n=>n.tag==='input')[0].props.value,'');assert.match(text(root),/密码已更新/)
})
test('credential conflict retains new input for explicit retry without exposing stored credentials',async()=>{
 handler=async(path,body)=>{if(body)throw new WorkforceApiError(409,'WORKFORCE_CONFLICT');return {...info}}
 const root=await mount(EmployeeLoginSettings,{props:{employeeId:'E'}});nodes(root,n=>n.tag==='input')[0].props.onInput({target:{value:'Abc987'}});await settle()
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();assert.match(text(root),/已被其他人修改/);assert.equal(nodes(root,n=>n.tag==='input')[0].props.value,'Abc987')
})
test('disabled or privileged account has metadata but no password form',async()=>{
 handler=async()=>({...info,status:'disabled',canReset:false});const root=await mount(EmployeeLoginSettings,{props:{employeeId:'E'}})
 assert.match(text(root),/已停用/);assert.equal(nodes(root,n=>n.tag==='form').length,0);assert.equal(nodes(root,n=>n.tag==='input').length,0)
})
test('failed account load offers retry and no editable password form',async()=>{
 handler=async()=>{throw new Error('offline')};const root=await mount(EmployeeLoginSettings,{props:{employeeId:'E'}})
 assert.equal(nodes(root,n=>n.props?.role==='alert').length,1);assert.equal(nodes(root,n=>n.tag==='form').length,0);assert.ok(button(root,'刷新账号'))
})
