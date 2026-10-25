import {test,before,after} from 'node:test'
import assert from 'node:assert/strict'
import {parse,compileScript} from '@vue/compiler-sfc'
import {createRenderer,h,nextTick,reactive} from 'vue'
import {createI18n} from 'vue-i18n'
import {routeLocationKey,routerKey} from 'vue-router'
import {build} from 'esbuild'
import {readFile,mkdir,writeFile} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
let Page,Summary,CustomerPage,StaffPage
const originalFetch=globalThis.fetch,originalStorage=globalThis.sessionStorage,originalDocument=globalThis.document,originalDocumentClass=globalThis.Document,originalShadowRoot=globalThis.ShadowRoot
const renderer=createRenderer({createElement:tag=>({tag,tagName:tag.toUpperCase(),children:[],props:{},value:'',get options(){return this.children.filter(c=>c.tag==='option')},addEventListener(){},removeEventListener(){},setAttribute(){},removeAttribute(){},getRootNode(){return globalThis.document}}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>{n.props[k]=v;if(k==='value'){n._value=v;n.value=v}},insert:(n,p)=>{n.parent=p;p.children.push(n)},remove:n=>{if(n?.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:()=>null})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>[n.text||'',...(n.children||[]).map(text)].join(' ')
const tick=async()=>{for(let n=0;n<12;n++){await Promise.resolve();await nextTick()}}
const home={timezone:'Asia/Singapore',storeName:'QA Store',stores:[{id:'A',name:'QA Store'}],newBusinessEnabled:true,permissions:['wine_storage.view','wine_storage.receive','wine_storage.deliver','wine_storage.rules.manage'],rules:[],categories:[],items:[],events:[],intents:[],applications:[{id:'application',customerId:'customer',status:'pending',lines:[],confirmation:null,reason:'',createdAt:'2026-10-25T10:00:00Z'}]}
before(async()=>{
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document=Object.assign(new Document(),{activeElement:null})
 const memory=new Map();globalThis.sessionStorage={getItem:k=>memory.get(k)??null,setItem:(k,v)=>memory.set(k,v),removeItem:k=>memory.delete(k)}
 const result=await build({stdin:{contents:"export {default as CustomerPage} from './src/modules/wine-storage/WineStorageCustomerPage.vue';export {default as StaffPage} from './src/modules/wine-storage/WineStorageStaffPage.vue';export {default as Page} from './src/modules/wine-storage/WineStoragePage.vue';export {default as Summary} from './src/modules/wine-storage/WalletStorageSummary.vue'",resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router','vue-i18n','pinia'],plugins:[{name:'storage-page-test',setup(b){b.onLoad({filter:/\.css$/},()=>({contents:'',loader:'js'}));b.onLoad({filter:/\.vue$/},async({path})=>{if(/Wallet(QrScanner|QrDisplay|CustomerLogin)\.vue$/.test(path))return{contents:"export default {render(){return null}}",loader:'js'};const{descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:'storage-test-'+path,inlineTemplate:true}).content,loader:'ts'}})}}]})
 const dir='target/wine-storage-page-test';await mkdir(dir,{recursive:true});await writeFile(dir+'/entry.mjs',result.outputFiles[0].contents);({Page,Summary,CustomerPage,StaffPage}=await import(pathToFileURL(process.cwd()+'/'+dir+'/entry.mjs').href+'?'+Date.now()))
})
after(()=>{globalThis.fetch=originalFetch;globalThis.sessionStorage=originalStorage;globalThis.document=originalDocument;globalThis.Document=originalDocumentClass;globalThis.ShadowRoot=originalShadowRoot})
function mount(component,customer,locale='zh-CN'){
 const calls=[],root={children:[]};globalThis.fetch=async(url,options)=>{calls.push({url,method:options?.method??'GET'});return{ok:true,json:async()=>home}}
 const app=renderer.createApp({render:()=>h(component,{customer,store:'A'})});app.use(createI18n({legacy:false,locale,messages:{}}));app.provide(routeLocationKey,reactive({params:{storeId:'A'},hash:'',query:{}}));app.provide(routerKey,{push:async()=>{},replace:async()=>{}});app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:p.to},slots.default?.())});app.mount(root);return{root,calls,unmount:()=>app.unmount()}
}
test('storage customer and merchant entry titles and API scopes are distinct',async()=>{
 for(const customer of [true,false]){
  const s=mount(customer?CustomerPage:StaffPage,customer);await tick();assert.match(text(s.root),customer?/我的存酒卡/:/酒水寄存管理/)
  assert.equal(s.calls[0].url,`/api/v1/${customer?'public/':''}stores/A/wine-storage/home`)
  assert.equal(nodes(s.root,n=>n.tag==='a')[0].props.href,customer?'/wallet/A':'/stores/A/wallet')
  assert.ok(nodes(s.root,n=>n.props?.class?.includes('cards-page')).length)
  assert.equal(nodes(s.root,n=>n.tag==='button'&&n.props['aria-pressed']===true).length,1)
  assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
 }
})
test('customer pages never expose merchant receipt or rule editing even if response permissions include them',async()=>{
 for(const customer of [true,false]){
  const s=mount(customer?CustomerPage:StaffPage,customer);await tick()
  assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='申请寄存').length,customer?1:0)
  nodes(s.root,n=>n.tag==='button'&&text(n)==='寄存申请')[0].props.onClick();await tick()
  assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='确认实际收存').length,customer?0:1)
  nodes(s.root,n=>n.tag==='button'&&text(n)==='保存规则')[0].props.onClick();await tick()
  assert.equal(nodes(s.root,n=>n.tag==='form'&&nodes(n,k=>k.tag==='button'&&text(k)==='保存新版本').length>0).length,customer?0:1)
  s.unmount()
 }
})
test('wallet storage summaries link only to their own role entry',async()=>{
 for(const customer of [true,false]){
  const s=mount(Summary,customer);await tick();assert.equal(nodes(s.root,n=>n.tag==='a')[0].props.href,customer?'/wallet/A/wine-storage':'/stores/A/wine-storage');assert.match(text(s.root),customer?/我的存酒卡/:/酒水寄存管理/);s.unmount()
 }
})
test('dedicated role entry cannot be flipped by a forwarded customer attribute',async()=>{
 for(const [component,customer] of [[CustomerPage,true],[StaffPage,false]]){
  const s=mount(component,!customer);await tick()
  assert.equal(s.calls[0].url,`/api/v1/${customer?'public/':''}stores/A/wine-storage/home`)
  s.unmount()
 }
})
