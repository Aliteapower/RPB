import {test,before,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {parse,compileScript} from '@vue/compiler-sfc'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {createRenderer,h,reactive,nextTick,onMounted,onUnmounted} from 'vue'
import {createPinia} from 'pinia'
import {routeLocationKey,routerKey} from 'vue-router'
import {createI18n} from 'vue-i18n'
let Boundary,useAuthSessionStore
const originalFetch=globalThis.fetch
const mounted=[],renderer=createRenderer({createElement:tag=>({tag,props:{},children:[]}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},patchProp:(n,k,o,v)=>n.props[k]=v,insert:(n,p,anchor)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=anchor?p.children.indexOf(anchor):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children=n.parent.children.filter(c=>c!==n)},parentNode:n=>n?.parent,nextSibling:n=>{const c=n?.parent?.children??[];return c[c.indexOf(n)+1]??null}})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))],text=n=>(n.text||'')+(n.children||[]).map(text).join('')
const tick=async()=>{for(let i=0;i<12;i++){await Promise.resolve();await nextTick()}}
before(async()=>{
 const result=await build({stdin:{contents:"export {default as Boundary} from './src/modules/tenant-products/ProductRouteBoundary.vue';export {useAuthSessionStore} from './src/stores/authSession'",resolveDir:process.cwd()},bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router','vue-i18n','pinia'],plugins:[{name:'boundary',setup(b){
 b.onLoad({filter:/\.vue$/},async({path})=>{if(!path.endsWith('ProductRouteBoundary.vue'))return{contents:"export default {render(){return null}}",loader:'js'};const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}})
 }}]});await mkdir('target/product-route-boundary',{recursive:true});await writeFile('target/product-route-boundary/entry.mjs',result.outputFiles[0].contents);({Boundary,useAuthSessionStore}=await import(pathToFileURL(process.cwd()+'/target/product-route-boundary/entry.mjs').href))
})
afterEach(()=>{mounted.forEach(a=>a.unmount());mounted.length=0;globalThis.fetch=originalFetch})
const access=(businessAllowed,historyAllowed=true)=>({appKey:'payment',businessAllowed,historyAllowed,historyRoute:'/stores/A/payments/records?history=1'})
const response=productAccess=>({ok:true,status:200,text:async()=>JSON.stringify({success:true,apps:[],historyApps:[],productAccess})})
function mount(path='/stores/A/payments/present/T1',query={}){
 const root={children:[]},route=reactive({path,fullPath:path,query,params:{storeId:'A'},meta:{},hash:''}),pinia=createPinia();let mounts=0,unmounts=0
 const child={setup(){onMounted(()=>mounts++);onUnmounted(()=>unmounts++);return()=>h('div',{'data-business':true},'Cached payment QR')}}
 const app=renderer.createApp({render:()=>h(Boundary,null,{default:()=>h(child)})});app.use(pinia)
 const auth=useAuthSessionStore(pinia);auth.user={accountId:'one',tenantId:'tenant',roles:['staff'],permissions:['payment.intent.view'],storeIds:['A','B']}
 app.use(createI18n({legacy:false,locale:'zh-CN',messages:{}}));app.provide(routeLocationKey,route);app.provide(routerKey,{replace:async value=>{route.query=value.query},push:async()=>{}});app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:p.to},slots.default?.())});app.mount(root);mounted.push(app)
 return{root,route,auth,app,get mounts(){return mounts},get unmounts(){return unmounts}}
}
function browser(t){const win=new EventTarget(),doc=new EventTarget();doc.visibilityState='visible';const ow=globalThis.window,od=globalThis.document;globalThis.window=win;globalThis.document=doc;t.after(()=>{globalThis.window=ow;globalThis.document=od});return{win,doc}}
const business=s=>nodes(s.root,n=>n.props?.['data-business']).length

test('unknown, denied and malformed access never mount cached payment workspaces',async()=>{
 let resolve;globalThis.fetch=()=>new Promise(done=>resolve=done);const s=mount();await tick();assert.equal(s.mounts,0)
 resolve(response([]));await tick();assert.equal(s.mounts,0)
 globalThis.fetch=async()=>response(undefined);s.route.params.storeId='B';await tick();assert.equal(s.mounts,0);assert.match(text(s.root),/无法确认/)
})
test('activation restores and expiry unmounts QR; failure closes and visible focus recovers',async t=>{
 const {win}=browser(t);t.mock.timers.enable({apis:['setInterval']});let state=[access(false)];globalThis.fetch=async()=>response(state);const s=mount();await tick();assert.equal(business(s),0)
 state=[access(true)];t.mock.timers.tick(30000);await tick();assert.equal(s.mounts,1);assert.equal(business(s),1)
 state=[access(false)];t.mock.timers.tick(30000);await tick();assert.equal(s.unmounts,1);assert.equal(business(s),0)
 state=[access(true)];win.dispatchEvent(new Event('focus'));await tick();assert.equal(business(s),1)
 globalThis.fetch=async()=>{throw Error('offline')};win.dispatchEvent(new Event('focus'));await tick();assert.equal(business(s),0)
})
test('identity changes clear access and late old identity response cannot restore it',async t=>{
 const {win}=browser(t);globalThis.fetch=async()=>response([access(true)]);const s=mount();await tick();assert.equal(business(s),1)
 let oldResolve;globalThis.fetch=()=>new Promise(done=>oldResolve=done);win.dispatchEvent(new Event('focus'));await tick()
 globalThis.fetch=async()=>response([]);s.auth.user.accountId='two';await tick();assert.equal(business(s),0);oldResolve(response([access(true)]));await tick();assert.equal(business(s),0)
})
test('closed read pages normalize history before child mounting and unsafe forms stay blocked',async()=>{
 globalThis.fetch=async()=>response([access(false)]);const s=mount('/stores/A/payments/records');assert.equal(business(s),0);await tick();assert.equal(s.route.query.history,'1');assert.equal(business(s),1)
 const denied=mount('/stores/A/payments/present/T1',{history:'1'});await tick();assert.equal(denied.mounts,0)
})
test('shared pages do not query staff product permissions',async()=>{
 let calls=0;globalThis.fetch=async()=>{calls++;return response([])};const s=mount('/stores/A/staff');await tick();assert.equal(business(s),1);assert.equal(calls,0)
})
