import {test,before,after,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {createServer} from 'vite'
import {createRenderer,h,ref,nextTick} from 'vue'
import {layoutCenterName,wrapName} from '../../modules/tenant-entry-links/nameLayout.ts'
let vite,useTenantEntries
const oldFetch=globalThis.fetch,tick=async()=>{for(let i=0;i<8;i++){await Promise.resolve();await nextTick()}}
const renderer=createRenderer({createElement:tag=>({tag,children:[]}),createText:text=>({text}),createComment:()=>({}),setText:()=>{},setElementText:()=>{},patchProp:()=>{},insert:(child,parent)=>parent.children.push(child),remove:()=>{},parentNode:()=>null,nextSibling:()=>null})
const mounted=new Set()
const mount=store=>{let state;const app=renderer.createApp({setup(){state=useTenantEntries(store);return()=>h('div')}});app.mount({children:[]});mounted.add(app);return{state,unmount:()=>{app.unmount();mounted.delete(app)}}}
const response=(id,name)=>({ok:true,json:async()=>({success:true,identity:{storeId:id,tenantName:name}})})
before(async()=>{vite=await createServer({configFile:false,cacheDir:'target/tenant-entry-workspace-cache',server:{middlewareMode:true},appType:'custom',optimizeDeps:{noDiscovery:true,entries:[]}});({useTenantEntries}=await vite.ssrLoadModule('/src/modules/tenant-entry-links/useTenantEntries.ts'))})
after(async()=>{await vite?.close();globalThis.fetch=oldFetch})
afterEach(()=>{for(const app of mounted)app.unmount();mounted.clear();delete globalThis.window;delete globalThis.document})

test('common entries track product activation, expiry and recovery automatically without page reload',async t=>{
 t.mock.timers.enable({apis:['setInterval']});globalThis.window=new EventTarget();globalThis.document=Object.assign(new EventTarget(),{visibilityState:'visible'})
 let products=[],calls=0
 globalThis.fetch=async(_url,options)=>{calls++;assert.equal(options.cache,'no-store');return {ok:true,json:async()=>({success:true,identity:{storeId:'A'},products})}}
 const scope=mount(ref('A'));await tick();assert.deepEqual(scope.state.context.value.products,[])
 products=[{appKey:'wine_storage',available:true}];t.mock.timers.tick(30000);await tick();assert.equal(scope.state.context.value.products[0].available,true)
 products=[{appKey:'wine_storage',available:false}];window.dispatchEvent(new Event('focus'));await tick();assert.equal(scope.state.context.value.products[0].available,false)
 products=[{appKey:'wine_storage',available:true}];document.visibilityState='hidden';t.mock.timers.tick(30000);await tick();assert.equal(calls,3)
 document.visibilityState='visible';document.dispatchEvent(new Event('visibilitychange'));await tick();assert.equal(scope.state.context.value.products[0].available,true)
 scope.unmount();window.dispatchEvent(new Event('pageshow'));t.mock.timers.tick(30000);await tick();assert.equal(calls,4)
})

test('background reads do not overlap or discard existing QR identity while pending, but failure clears it',async t=>{
 t.mock.timers.enable({apis:['setInterval']});globalThis.window=new EventTarget();globalThis.document=Object.assign(new EventTarget(),{visibilityState:'visible'})
 globalThis.fetch=async()=>response('A','Tenant A');const scope=mount(ref('A'));await tick()
 let reject,calls=0;globalThis.fetch=()=>{calls++;return new Promise((_,no)=>reject=no)}
 t.mock.timers.tick(30000);await tick();assert.equal(scope.state.context.value.identity.tenantName,'Tenant A')
 window.dispatchEvent(new Event('focus'));t.mock.timers.tick(60000);await tick();assert.equal(calls,1)
 reject(new Error('offline'));await tick();assert.equal(scope.state.context.value,null)
 globalThis.fetch=async()=>response('A','Recovered');window.dispatchEvent(new Event('pageshow'));await tick();assert.equal(scope.state.context.value.identity.tenantName,'Recovered')
})
test('a store switch clears old tenant codes and late data cannot restore the old QR identity',async()=>{
 const requests=[];globalThis.fetch=(url,options)=>new Promise(resolve=>requests.push({url,options,resolve}))
 const store=ref('A'),scope=mount(store);await tick();store.value='B';await tick()
 assert.equal(scope.state.context.value,null);requests[1].resolve(response('B','Tenant B'));await tick();requests[0].resolve(response('A','Tenant A'));await tick()
 assert.equal(scope.state.context.value.identity.tenantName,'Tenant B');assert.equal(scope.state.loading.value,false);assert.equal(requests[1].options.credentials,'same-origin');scope.unmount()
})
test('permissions and mismatched store responses never leave an old distributable QR context',async()=>{
 globalThis.fetch=async()=>response('A','Tenant A');const scope=mount(ref('A'));await tick();assert.ok(scope.state.context.value)
 globalThis.fetch=async()=>({ok:false,json:async()=>({success:false,error:{messageKey:'tenant.admin.forbidden'}})})
 await scope.state.refresh();assert.equal(scope.state.context.value,null);assert.equal(scope.state.error.value,'tenant.admin.forbidden')
 globalThis.fetch=async()=>response('foreign','Other tenant');await scope.state.refresh();assert.equal(scope.state.context.value,null);assert.equal(scope.state.error.value,'tenantEntryLinks.scopeMismatch');scope.unmount()
})
test('a failed network read remains retryable and does not manufacture tenant data',async()=>{
 globalThis.fetch=async()=>{throw new Error('offline')};const scope=mount(ref('A'));await tick();assert.equal(scope.state.context.value,null);assert.equal(scope.state.loading.value,false)
 globalThis.fetch=async()=>response('A','Recovered');await scope.state.refresh();assert.equal(scope.state.error.value,'');assert.equal(scope.state.context.value.identity.tenantName,'Recovered');scope.unmount()
})
test('center names wrap within the protected area; overlong text is explicitly shortened only in the center',()=>{
 const measure=(text,size)=>Array.from(text).length*size*.7
 for(const name of ['食刻租户','Yumstone Restaurant Pte Ltd','张氏餐饮集团 · 新加坡']){
  const layout=layoutCenterName(name,measure,246,116);assert.ok(layout.lines.length<=3);assert.ok(layout.lines.every(line=>measure(line,layout.fontSize)<=246));assert.ok(layout.lines.length*layout.fontSize*1.2<=116)
 }
 const full='统一租户名称'.repeat(30),long=layoutCenterName(full,measure,246,116);assert.equal(long.truncated,true);assert.ok(long.lines.at(-1).endsWith('…'));assert.ok(full.length>long.lines.join('').length)
 assert.equal(wrapName('A😀张三',s=>Array.from(s).length,2).join(''),'A😀张三')
})
