import {test,before,after} from 'node:test'
import assert from 'node:assert/strict'
import {createServer} from 'vite'
import {createRenderer,h,ref,nextTick} from 'vue'
let vite,useTenantProducts
const originalFetch=globalThis.fetch,tick=async()=>{for(let i=0;i<12;i++){await Promise.resolve();await nextTick()}}
const renderer=createRenderer({createElement:tag=>({tag,children:[]}),createText:text=>({text}),createComment:()=>({}),setText:()=>{},setElementText:()=>{},patchProp:()=>{},insert:(child,parent)=>parent.children.push(child),remove:()=>{},parentNode:()=>null,nextSibling:()=>null})
const mount=store=>{let state;const app=renderer.createApp({setup(){state=useTenantProducts(store);return()=>h('div')}});app.mount({children:[]});return{state,unmount:()=>app.unmount()}}
const row=(enabled=true,canEnable=true)=>({appKey:'wallet',enabled,canEnable,unavailableReason:canEnable?null:'STORE_APP_EXPIRED'})
const response=products=>({ok:true,json:async()=>({success:true,products})})
before(async()=>{vite=await createServer({configFile:false,cacheDir:'target/tenant-products-cache',server:{middlewareMode:true},appType:'custom',optimizeDeps:{noDiscovery:true,entries:[]}});({useTenantProducts}=await vite.ssrLoadModule('/src/modules/tenant-products/useTenantProducts.ts'))})
after(async()=>{await vite?.close();globalThis.fetch=originalFetch})
test('explicit disable and reenable save scoped state without optimistic changes',async()=>{
 const requests=[];globalThis.fetch=async(url,options)=>{requests.push({url,options});return response([row(requests.length===1||JSON.parse(options.body).enabled)])}
 const s=mount(ref('A'));await tick();assert.equal(s.state.products.value[0].enabled,true)
 await s.state.toggle(s.state.products.value[0]);assert.equal(s.state.products.value[0].enabled,false)
 await s.state.toggle(s.state.products.value[0]);assert.equal(s.state.products.value[0].enabled,true)
 assert.ok(requests[1].url.endsWith('/A/tenant-admin/product-lines/wallet'));assert.equal(requests[1].options.method,'PUT');assert.equal(requests[1].options.credentials,'same-origin');assert.equal(JSON.parse(requests[1].options.body).enabled,false);s.unmount()
})
test('failed saves preserve state and show retryable error',async()=>{
 globalThis.fetch=async()=>response([row()]);const s=mount(ref('A'));await tick()
 globalThis.fetch=async()=>({ok:false,json:async()=>({error:{code:'PRODUCT_LINE_UNAVAILABLE'}})})
 await s.state.toggle(s.state.products.value[0]);assert.equal(s.state.products.value[0].enabled,true);assert.equal(s.state.error.value,'tenantProducts.saveFailed');assert.equal(s.state.saving.value,'');s.unmount()
})
test('expired closed products cannot be enabled and duplicate clicks do not send extra requests',async()=>{
 let calls=0;globalThis.fetch=async()=>{calls++;return response([row(false,false)])};const s=mount(ref('A'));await tick()
 await s.state.toggle(s.state.products.value[0]);assert.equal(calls,1)
 globalThis.fetch=async()=>response([row()]);await s.state.refresh()
 let resolve;globalThis.fetch=()=>{calls++;return new Promise(r=>resolve=r)}
 const pending=s.state.toggle(s.state.products.value[0]);await s.state.toggle(s.state.products.value[0]);assert.equal(calls,2)
 resolve(response([row(false)]));await pending;s.unmount()
})
test('switching stores discards late list and save responses from the previous store',async()=>{
 const requests=[];globalThis.fetch=(url,options)=>new Promise(resolve=>requests.push({url,options,resolve}))
 const store=ref('A'),s=mount(store);await tick();requests[0].resolve(response([row()]));await tick()
 const oldSave=s.state.toggle(s.state.products.value[0]);store.value='B';await tick();assert.deepEqual(s.state.products.value,[])
 requests[2].resolve(response([row()]));await tick();requests[1].resolve(response([row(false)]));await oldSave
 assert.equal(s.state.products.value[0].enabled,true);assert.equal(s.state.saving.value,'');s.unmount()
})
test('failed reads clear old data and remain refreshable',async()=>{
 globalThis.fetch=async()=>{throw Error('offline')};const s=mount(ref('A'));await tick();assert.equal(s.state.error.value,'tenantProducts.loadFailed');assert.deepEqual(s.state.products.value,[])
 globalThis.fetch=async()=>response([row(false)]);await s.state.refresh();assert.equal(s.state.error.value,'');assert.equal(s.state.products.value[0].enabled,false);s.unmount()
})

test('refreshing provisioned products also invalidates the side menu visibility query',async()=>{
 globalThis.fetch=async()=>response([row()]);const s=mount(ref('A'));await tick();const before=s.state.changeCount.value;globalThis.fetch=async()=>response([]);await s.state.refresh();assert.deepEqual(s.state.products.value,[]);assert.equal(s.state.changeCount.value,before+1);s.unmount()
})
