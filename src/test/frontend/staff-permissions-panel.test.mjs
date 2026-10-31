import {before,after,afterEach,test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {parse,compileScript} from '@vue/compiler-sfc'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {createRenderer,h,nextTick,reactive} from 'vue'
import {createI18n} from 'vue-i18n'

let Panel
const originalFetch=globalThis.fetch, mounted=[]
const renderer=createRenderer({createElement:tag=>({tag,props:{},children:[],addEventListener(){},getRootNode(){return {}}}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>n.props[k]=v,insert:(n,p,a)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n?.parent,nextSibling:n=>{const list=n?.parent?.children||[];return list[list.indexOf(n)+1]||null}})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>(n.text||'')+(n.children||[]).map(text).join('')
const settle=async()=>{for(let i=0;i<15;i++){await Promise.resolve();await nextTick()}}
const button=(r,label)=>nodes(r,n=>n.tag==='button'&&text(n)===label)[0]
const choice=(code,requires=[],grantable=true)=>({code,labelZh:code==='wallet.view'?'查看钱包':'收款',labelEn:code,requires,grantable})
const snapshot=(store='A')=>({success:true,version:2,inherited:true,permissions:['wallet.view'],groups:[{key:'wallet',appKey:'wallet',labelZh:'钱包',labelEn:'Wallet',provisioned:true,enabled:store==='A',permissions:[choice('wallet.view'),choice('wallet.collection.confirm',['wallet.view'])]}]})
const response=(body,status=200)=>({ok:status<400,status,json:async()=>body})
before(async()=>{
  const result=await build({entryPoints:['src/modules/staff-permissions/StaffPermissionsPanel.vue'],bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-i18n'],plugins:[{name:'vue',setup(b){b.onLoad({filter:/\.vue$/},async({path})=>{const {descriptor,errors}=parse(await readFile(path,'utf8'),{filename:path});assert.deepEqual(errors,[]);return {contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}})}}]})
  await mkdir('target',{recursive:true});await writeFile('target/staff-permissions-panel-test.mjs',result.outputFiles[0].contents)
  Panel=(await import(pathToFileURL(process.cwd()+'/target/staff-permissions-panel-test.mjs'))).default
})
afterEach(()=>{mounted.splice(0).forEach(app=>app.unmount());globalThis.fetch=originalFetch})
after(()=>{globalThis.fetch=originalFetch})
function mount(locale='zh-CN'){
  const props=reactive({staffId:'employee',initialStoreId:'A',stores:[{storeId:'A',label:'门店 A'},{storeId:'B',label:'门店 B'}]})
  const root={children:[]},app=renderer.createApp({render:()=>h(Panel,props)})
  app.use(createI18n({legacy:false,locale,messages:{}}));app.mount(root);mounted.push(app)
  return {root,props}
}

test('rendered switches save explicit empty revocation separately from the employee form',async()=>{
  const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,options});return response(options.method==='GET'?snapshot():{...snapshot(),version:3,inherited:false,permissions:[]})}
  const {root}=mount();await settle()
  assert.match(text(root),/产品线与模块授权/);assert.match(text(root),/当前沿用账号原有权限/)
  const checkboxes=nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox')
  checkboxes[0].props.onChange({target:{checked:false}});await settle()
  await button(root,'保存本门店权限').props.onClick();await settle()
  assert.deepEqual(JSON.parse(calls[1].options.body),{expectedVersion:2,permissions:[]})
  assert.match(text(root),/权限已保存/)
  assert.equal(nodes(root,n=>n.tag==='form').length,0)
})

test('conflicting save retains checkbox choices and requires explicit reload before reconciling',async()=>{
  globalThis.fetch=async(url,options)=>response(options.method==='GET'?snapshot():{success:false,error:{code:'PERMISSION_VERSION_CONFLICT'}},options.method==='GET'?200:409)
  const {root}=mount();await settle()
  nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox')[1].props.onChange({target:{checked:true}});await settle()
  await button(root,'保存本门店权限').props.onClick();await settle()
  assert.match(text(root),/你的选择已保留/)
  assert.equal(nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox')[1].props.checked,true)
  assert.equal(nodes(root,n=>n.tag==='select')[0].props.disabled,true)
  await button(root,'重新加载').props.onClick();await settle()
  assert.equal(nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox')[1].props.checked,false)
})

test('store selection scopes loads and disabled product remains revocable with English labels',async()=>{
  const calls=[];globalThis.fetch=async(url)=>{calls.push(url);return response(snapshot(url.includes('/B/')?'B':'A'))}
  const {root}=mount('en-SG');await settle()
  nodes(root,n=>n.tag==='select')[0].props.onChange({target:{value:'B'}});await settle()
  assert.equal(calls.at(-1),'/api/v1/stores/B/tenant-admin/staff/employee/permissions')
  assert.match(text(root),/New operations currently unavailable/)
  assert.match(text(root),/Wallet/)
})

test('load errors do not leave a saveable stale snapshot from another store',async()=>{
  globalThis.fetch=async(url)=>url.includes('/A/')?response(snapshot()):response({success:false,error:{code:'FORBIDDEN'}},403)
  const {root}=mount();await settle()
  nodes(root,n=>n.tag==='select')[0].props.onChange({target:{value:'B'}});await settle()
  assert.match(text(root),/无权管理该员工或门店的权限/)
  assert.equal(nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox').length,0)
  assert.equal(button(root,'保存本门店权限').props.disabled,true)
})
