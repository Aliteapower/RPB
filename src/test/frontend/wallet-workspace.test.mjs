import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createRenderer, h, reactive, ref, nextTick } from 'vue'
import { createPinia } from 'pinia'
import { routeLocationKey, routerKey } from 'vue-router'
let vite, historyModule, recordModule, staffModule, customerModule, authModule
const oldFetch=globalThis.fetch,oldWindow=globalThis.window,oldDocument=globalThis.document
const timers=new Map();let timer=0
const response=data=>({ok:true,status:200,json:async()=>data})
const tick=async()=>{for(let i=0;i<8;i++){await Promise.resolve();await nextTick()}}
const renderer=createRenderer({createElement:tag=>({tag,children:[]}),createText:text=>({text}),createComment:()=>({}),setText:()=>{},setElementText:()=>{},patchProp:()=>{},insert:(child,parent)=>parent.children.push(child),remove:()=>{},parentNode:()=>null,nextSibling:()=>null})
function mount(setup,route={params:{storeId:'A'},hash:'',query:{}},pinia=createPinia(),router={push:async()=>{},replace:async()=>{}}){
  let state;const app=renderer.createApp({setup(){state=setup();return()=>h('div')}})
  app.use(pinia);app.provide(routeLocationKey,route);app.provide(routerKey,router);app.mount({children:[]})
  return{state,unmount:()=>app.unmount()}
}
before(async()=>{
  globalThis.window={setInterval:fn=>{timers.set(++timer,fn);return timer},clearInterval:id=>timers.delete(id),location:{origin:'http://127.0.0.1:5178'}}
  globalThis.document={visibilityState:'visible'}
  vite=await createServer({configFile:false,cacheDir:'target/wallet-ui-test-cache',server:{middlewareMode:true},appType:'custom',optimizeDeps:{noDiscovery:true,entries:[]}})
  historyModule=await vite.ssrLoadModule('/src/modules/wallet/useWalletHistory.ts')
  recordModule=await vite.ssrLoadModule('/src/modules/wallet/useWalletRecord.ts')
  staffModule=await vite.ssrLoadModule('/src/modules/wallet/useStaffWallet.ts')
  customerModule=await vite.ssrLoadModule('/src/modules/wallet/useCustomerWallet.ts')
  authModule=await vite.ssrLoadModule('/src/stores/authSession.ts')
})
after(async()=>{await vite?.close();globalThis.fetch=oldFetch;globalThis.window=oldWindow;globalThis.document=oldDocument})
test('history filters load immediately and late responses from the previous filter cannot restore old rows',async()=>{
  const requests=[];globalThis.fetch=(url)=>new Promise(resolve=>requests.push({url,resolve}))
  const status=ref('all'),scope=mount(()=>historyModule.useWalletHistory(ref('A'),ref('/bills'),status))
  await tick();status.value='pending';await tick();assert.equal(requests.length,2)
  requests[1].resolve(response([{id:'current-filter'}]));await tick()
  requests[0].resolve(response([{id:'stale-filter'}]));await tick()
  assert.deepEqual(scope.state.rows.value,[{id:'current-filter'}]);assert.equal(scope.state.busy.value,false)
  scope.unmount()
})

test('switching a detail route during loading cannot display the old bill after the new bill is denied',async()=>{
  const requests=[];globalThis.fetch=url=>new Promise(resolve=>requests.push({url,resolve}))
  const path=ref('/bills/old'),scope=mount(()=>recordModule.useWalletRecord(ref('A'),path))
  await tick();path.value='/bills/denied';await tick();assert.equal(requests.length,2)
  requests[1].resolve({ok:false,status:404,json:async()=>({message:'Bill not found'})});await tick()
  requests[0].resolve(response({id:'old-private-bill'}));await tick()
  assert.equal(scope.state.record.value,null);assert.equal(scope.state.error.value,'Bill not found')
  assert.equal(scope.state.ready.value,true);assert.equal(scope.state.busy.value,false);scope.unmount()
})
test('switching store clears selected customer and code and ignores a late scanned identity',async()=>{
  let finishScan
  globalThis.fetch=async(url)=>url.endsWith('/resolve')?new Promise(resolve=>{finishScan=resolve}):response({store:{storeId:url.includes('/A/')?'A':'B'},program:{},transactionsEnabled:true})
  const route=reactive({params:{storeId:'A'},hash:'',query:{}}),pinia=createPinia()
  const auth=authModule.useAuthSessionStore(pinia);auth.user={permissions:['wallet.view','wallet.charge.create']}
  const scope=mount(()=>staffModule.provideStaffWallet(),route,pinia);await tick()
  const scanning=scope.state.scan('WP.opaque-test');await tick()
  route.params.storeId='B';await tick()
  finishScan(response({account:{id:'old-account'},customer:{id:'old-customer'}}));await scanning;await tick()
  assert.equal(scope.state.account.value,null);assert.equal(scope.state.customer.value,null);assert.equal(scope.state.customerCode.value,'')
  assert.equal(scope.state.context.value.store.storeId,'B');scope.unmount()
})
test('customer legacy receipt resumes after login and cross-store scans navigate without reading the wrong store',async()=>{
  let loggedIn=false;const calls=[],routes=[]
  globalThis.fetch=async(url)=>{calls.push(url);return loggedIn?response({context:{},accounts:[],pendingBills:[]}):{ok:false,status:401,json:async()=>({code:'WALLET_UNAUTHENTICATED',message:'login'})}}
  const route=reactive({params:{storeId:'A'},query:{},hash:'#receipt=original'})
  const scope=mount(()=>customerModule.provideCustomerWallet(),route,undefined,{push:async path=>routes.push(path),replace:async path=>{routes.push(path);route.hash=''}})
  await tick();assert.equal(scope.state.needsLogin.value,true)
  loggedIn=true;await scope.state.authenticated();await tick()
  assert.ok(routes.includes('/wallet/A/bills/original'));assert.equal(scope.state.needsLogin.value,false)
  const before=calls.length,target='20000000-0000-0000-0000-000000000983',code='WB.'+'A'.repeat(43)
  await scope.state.scan(`http://127.0.0.1:5178/wallet/${target}#bill=${code}`)
  assert.equal(calls.length,before);assert.ok(routes.at(-1).startsWith(`/wallet/${target}#bill=`))
  await assert.rejects(scope.state.scan(`https://foreign.example/wallet/${target}#bill=${code}`))
  scope.unmount()
})
test('legacy merchant links replace history and an expired link is not re-submitted by background home polling',async()=>{
  let expired=false;const routes=[],calls=[]
  globalThis.fetch=async(url)=>{calls.push(url);if(url.endsWith('/bill-code'))return expired?{ok:false,status:404,json:async()=>({message:'expired code'})}:response({id:'original-bill'});return response({context:{},accounts:[],pendingBills:[]})}
  const code='WB.'+'A'.repeat(43),route=reactive({params:{storeId:'A'},query:{},hash:'#bill='+code})
  const scope=mount(()=>customerModule.provideCustomerWallet(),route,undefined,{push:async path=>routes.push(['push',path]),replace:async path=>{routes.push(['replace',path]);route.hash=''}})
  await tick();assert.deepEqual(routes,[['replace','/wallet/A/bills/original-bill']])
  expired=true;route.hash='#bill=WB.'+'B'.repeat(43);await tick()
  const attempts=calls.filter(p=>p.endsWith('/bill-code')).length
  await scope.state.refresh();await tick()
  assert.equal(calls.filter(p=>p.endsWith('/bill-code')).length,attempts)
  assert.equal(scope.state.error.value,'expired code');scope.unmount()
})
