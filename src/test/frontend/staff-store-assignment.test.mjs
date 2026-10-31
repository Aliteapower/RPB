import {before,after,afterEach,test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {parse,compileScript} from '@vue/compiler-sfc'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {createRenderer,h,nextTick,reactive} from 'vue'
import {createI18n} from 'vue-i18n'
import {routeLocationKey,routerKey} from 'vue-router'
let Page
const mounted=[],originalAuth=globalThis.__staffFormAuth,originalApi=globalThis.__staffFormApi
const renderer=createRenderer({createElement:tag=>({tag,props:{},children:[],get options(){return this.children.filter(n=>n.tag==='option')},addEventListener(){},getRootNode(){return {}}}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>n.props[k]=v,insert:(n,p,a)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n?.parent,nextSibling:n=>{const list=n?.parent?.children||[];return list[list.indexOf(n)+1]||null}})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>(n.text||'')+(n.children||[]).map(text).join('')
const settle=async()=>{for(let i=0;i<15;i++){await Promise.resolve();await nextTick()}}
before(async()=>{globalThis.Document=class {};globalThis.ShadowRoot=class {};
  const result=await build({entryPoints:['src/pages/TenantAdminStaffFormPage.vue'],bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router','vue-i18n'],plugins:[{name:'staff-store-access',setup(b){
    b.onLoad({filter:/authSession\.ts$/},()=>({contents:'export function useAuthSessionStore(){return globalThis.__staffFormAuth}',loader:'js'}))
    b.onLoad({filter:/generatedText\.ts$/},()=>({contents:'export function useGeneratedText(){return {gt:key=>key}}',loader:'js'}))
    b.onLoad({filter:/tenantAdminApi\.ts$/},()=>({contents:"export class TenantAdminApiError extends Error{};"+['clearTenantProfileLogo','createStaff','getCurrentTenantAdminStaff','getStaff','getTenantProfile','updateCurrentTenantAdminStaff','updateStaff','updateTenantProfile','uploadTenantProfileLogo'].map(key=>`export const ${key}=(...args)=>globalThis.__staffFormApi.${key}(...args);`).join(''),loader:'js'}))
    b.onLoad({filter:/\.vue$/},async({path})=>{
      if(!/TenantAdminStaffFormPage\.vue$/.test(path))return {contents:'export default {render(){return null}}',loader:'js'}
      const {descriptor}=parse(await readFile(path,'utf8'),{filename:path})
      return {contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}
    })
  }}]})
  await mkdir('target',{recursive:true});await writeFile('target/staff-store-assignment-test.mjs',result.outputFiles[0].contents)
  Page=(await import(pathToFileURL(process.cwd()+'/target/staff-store-assignment-test.mjs'))).default
})
afterEach(()=>mounted.splice(0).forEach(app=>app.unmount()))
after(()=>{globalThis.__staffFormAuth=originalAuth;globalThis.__staffFormApi=originalApi})
async function mount(locale='zh-CN'){
  const calls=[],assigned={id:'employee',employeeNo:'1234',name:'Employee',status:'active',defaultStoreId:'B',storeIds:['A','B','C']}
  globalThis.__staffFormAuth=reactive({user:{tenantId:'tenant',storeIds:['A'],defaultStoreId:'A'},authorizedStores:[{storeId:'A',storeName:'Store A'}],ensureAuthorizedStores:async()=>{}})
  globalThis.__staffFormApi={getStaff:async()=>({staff:assigned}),updateStaff:async(...args)=>{calls.push(args);return {staff:assigned}}}
  const root={children:[]},app=renderer.createApp({render:()=>h(Page)})
  app.use(createI18n({legacy:false,locale,messages:{},missingWarn:false,fallbackWarn:false}))
  app.provide(routeLocationKey,reactive({name:'tenant-admin-staff-edit',params:{storeId:'A',staffId:'employee'}}))
  app.provide(routerKey,{push:async()=>{}});app.mount(root);mounted.push(app);await settle()
  return {root,calls}
}
test('other-store access is immutable; only the saved out-of-scope default remains selectable',async()=>{
  const {root,calls}=await mount()
  const checks=nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox')
  assert.deepEqual(checks.map(n=>Boolean(n.props.disabled)),[false,true,true])
  const select=nodes(root,n=>n.tag==='select'&&nodes(n,c=>c.tag==='option'&&c.props.value==='B').length)[0]
  let options=nodes(select,n=>n.tag==='option')
  assert.deepEqual(options.map(n=>[n.props.value,Boolean(n.props.disabled)]),[['A',false],['B',false],['C',true]])
  select.props['onUpdate:modelValue']('A');await settle()
  options=nodes(select,n=>n.tag==='option')
  assert.equal(options.find(n=>n.props.value==='B').props.disabled,false)
  checks[1].props.onChange({target:{checked:false}});await settle()
  await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
  assert.deepEqual(calls[0][2].storeIds,['A','B','C']);assert.equal(calls[0][2].defaultStoreId,'A')
  assert.match(text(root),/其他门店的已有访问权保留，由对应管理员管理/)
})
test('store-assignment boundary explanation follows the interface language',async()=>{
  const {root}=await mount('en-SG')
  assert.match(text(root),/Existing access to other stores is retained and managed by their administrators/)
})
