import { test, beforeEach, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { billStage, legacyWalletTarget } from '../../modules/wallet/navigation.ts'
import { createWalletBill } from '../../modules/wallet/api.ts'
import { walletRoutes } from '../../modules/wallet/routes.ts'
import { routeViewKey } from '../../router/viewKey.ts'
const store='20000000-0000-0000-0000-000000000983'
test('wallet child navigation preserves its workspace while stores, identities and other products remain isolated', () => {
  const route=(path,workspace='staff',storeId=store)=>({path,meta:{walletWorkspace:workspace},params:{storeId}})
  assert.equal(routeViewKey(route('/collect')),routeViewKey(route('/bills/one')))
  assert.notEqual(routeViewKey(route('/collect')),routeViewKey(route('/collect','customer')))
  assert.notEqual(routeViewKey(route('/collect')),routeViewKey(route('/collect','staff','other-store')))
  assert.equal(routeViewKey(route('/booking',null)), '/booking')
  assert.equal(routeViewKey({path:'/paynow/settings',meta:{},params:{storeId:store}}),'/paynow/settings')
})
test('operation pages, lists and details have distinct bookmarkable routes with inherited customer authentication', () => {
  const staff=walletRoutes[0], customer=walletRoutes[1]
  for(const path of ['collect','topup','bills','bills/:billId','topups/:topupId']) assert.ok(staff.children.some(r=>r.path===path))
  for(const path of ['code','scan','bills','bills/:billId','history']) assert.ok(customer.children.some(r=>r.path===path))
  assert.equal(customer.meta.public,true)
  assert.equal(new Set(customer.children.map(r=>r.name)).size,customer.children.length)
})
test('old receipt and merchant QR links preserve the original bill or target store', () => {
  assert.deepEqual(legacyWalletTarget(store,'#receipt=original-bill'),{type:'receipt',path:`/wallet/${store}/bills/original-bill`})
  const code='WB.'+'A'.repeat(43)
  assert.deepEqual(legacyWalletTarget(store,'#bill='+encodeURIComponent(code)),{type:'scan',code,store})
  assert.equal(legacyWalletTarget(store,''),null)
})
test('zero-balance authorization still shows outstanding payment instead of paid success', () => {
  const b={amountCents:500,walletPaidCents:0,externalPaidCents:0,closed:false,walletApplied:false}
  assert.equal(billStage(b),'waiting')
  assert.equal(billStage({...b,walletApplied:true}),'due')
  assert.equal(billStage({...b,walletPaidCents:100,walletApplied:true}),'due')
  assert.equal(billStage({...b,externalPaidCents:500}),'paid')
  assert.equal(billStage({...b,closed:true}),'closed')
})
let originalFetch
beforeEach(()=>{originalFetch=globalThis.fetch;const values=new Map();globalThis.sessionStorage={getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)}})
afterEach(()=>{globalThis.fetch=originalFetch})
test('new cashier flow sends exactly one atomic bill command and keeps its retry key after response loss',async()=>{
  const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,body:JSON.parse(options.body)});if(calls.length===1)throw Error('response lost');return {ok:true,status:200,json:async()=>({id:'original'})}}
  await assert.rejects(createWalletBill(store,'account','5','WP.test'),/response lost/)
  assert.equal((await createWalletBill(store,'account','5','WP.test')).id,'original')
  assert.equal(calls.length,2);assert.ok(calls.every(c=>c.url.endsWith('/wallet/bills')))
  assert.equal(calls[0].body.customerCode,'WP.test');assert.equal(calls[0].body.idempotencyKey,calls[1].body.idempotencyKey)
})
