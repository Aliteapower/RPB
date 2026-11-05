import {test,before,after,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {createServer} from 'vite'
import {createRenderer,h,reactive,nextTick} from 'vue'
import {routeLocationKey,routerKey} from 'vue-router'
import {createI18n} from 'vue-i18n'
let vite,hooks
const originalFetch=globalThis.fetch,mounted=new Set()
const tick=async()=>{for(let i=0;i<20;i++){await Promise.resolve();await nextTick()}}
const renderer=createRenderer({createElement:tag=>({tag,children:[]}),createText:text=>({text}),createComment:()=>({}),setText:()=>{},setElementText:()=>{},patchProp:()=>{},insert:(c,p)=>p.children.push(c),remove:()=>{},parentNode:()=>null,nextSibling:()=>null})
const response=(status,data={})=>({status,ok:status===200,json:async()=>data})
before(async()=>{
 vite=await createServer({configFile:false,cacheDir:'target/customer-store-entry-session-cache',server:{middlewareMode:true,watch:null},appType:'custom',optimizeDeps:{noDiscovery:true,entries:[]}})
 const wallet=await vite.ssrLoadModule('/src/modules/wallet/useCustomerWallet.ts'),storage=await vite.ssrLoadModule('/src/modules/wine-storage/customer/useCustomerStorage.ts')
 hooks={wallet:wallet.provideCustomerWallet,storage:storage.useCustomerStorage}
})
afterEach(()=>{for(const app of mounted)app.unmount();mounted.clear();globalThis.fetch=originalFetch})
after(async()=>{await vite?.close()})
function mount(kind){
 const route=reactive({params:{storeId:'A'},query:{},hash:''});let state
 const app=renderer.createApp({setup(){state=hooks[kind]();return()=>h('div')}})
 app.provide(routeLocationKey,route);app.provide(routerKey,{replace:async()=>{},push:async()=>{}})
 app.use(createI18n({legacy:false,locale:'en',messages:{en:{}},missingWarn:false,fallbackWarn:false}));app.mount({children:[]});mounted.add(app)
 return {state,route,app,login:()=>kind==='wallet'?state.needsLogin.value:state.login.value}
}
test('unopened wallet state is explicit and activation clears it without reloading the customer page',async()=>{
 globalThis.fetch=async url=>url.includes('/customer-auth/me')?response(200,{success:true}):response(403,{code:'WALLET_PRODUCT_UNAVAILABLE',message:'Wallet not activated'})
 const s=mount('wallet');await tick();assert.equal(s.state.unavailable.value,true);assert.equal(s.login(),false);assert.equal(s.state.home.value,null)
 globalThis.fetch=async()=>response(200,{accounts:[],context:{denialReason:null}});await s.state.refresh();await tick();assert.equal(s.state.unavailable.value,false);assert.ok(s.state.home.value)
})
for(const kind of ['wallet','storage']){
 test(`${kind}: another tenant's customer session opens target-store login without logging out or replaying commands`,async()=>{
  for(const status of [403,404]){
   const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,options});return url.includes('/customer-auth/me')?response(401):response(status,{code:'SCOPE_DENIED',message:'wrong store'})}
   const s=mount(kind);await tick();assert.equal(s.login(),true);assert.equal(s.state.home.value,null)
   assert.equal(calls.length,2);assert.equal(calls[1].url,'/api/v1/public/customer-auth/me?storeId=A');assert.equal(calls[1].options.cache,'no-store')
   assert.ok(calls.every(c=>!c.options.method||c.options.method==='GET'));s.app.unmount();mounted.delete(s.app)
  }
 })
 test(`${kind}: valid target-store session keeps product denials and missing data as business errors`,async()=>{
  globalThis.fetch=async url=>url.includes('/customer-auth/me')?response(200,{success:true}):response(403,{code:'APP_DISABLED',message:'Product disabled'})
  const s=mount(kind);await tick();assert.equal(s.login(),false);assert.equal(s.state.error.value,'Product disabled');assert.equal(s.state.home.value,null)
 })
 test(`${kind}: failed session probe preserves the original error and never invents an authentication result`,async()=>{
  globalThis.fetch=async url=>{if(url.includes('/customer-auth/me'))throw Error('offline');return response(404,{code:'MISSING',message:'Not available'})}
  const s=mount(kind);await tick();assert.equal(s.login(),false);assert.equal(s.state.error.value,'Not available')
 })
 test(`${kind}: a slow previous-store session probe cannot replace a new store's loaded page`,async()=>{
  let oldResolve;globalThis.fetch=async url=>url.includes('/customer-auth/me')?new Promise(done=>oldResolve=done):response(404,{message:'wrong store'})
  const s=mount(kind);await tick();assert.ok(oldResolve)
  const home={accounts:[],intents:[],items:[]};globalThis.fetch=async()=>response(200,home);s.route.params.storeId='B';await tick();assert.ok(s.state.home.value)
  oldResolve(response(401));await tick();assert.equal(s.login(),false);assert.deepEqual(JSON.parse(JSON.stringify(s.state.home.value)),home)
 })
}
