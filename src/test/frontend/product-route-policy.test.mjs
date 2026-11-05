import {before,test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
let productForPath,routeAccessDecision
before(async()=>{
 const b=await build({entryPoints:['src/modules/tenant-products/routePolicy.ts'],bundle:true,write:false,format:'esm',platform:'node'})
 ;({productForPath,routeAccessDecision}=await import('data:text/javascript;base64,'+Buffer.from(b.outputFiles[0].contents).toString('base64')))
})
const access=(appKey,businessAllowed=true,historyAllowed=true)=>({appKey,businessAllowed,historyAllowed,historyRoute:appKey==='workforce'?'/stores/A/workforce-history?history=1':appKey==='payment'?'/stores/A/payments/records?history=1':`/stores/A/${appKey==='wine_storage'?'wine-storage':appKey==='daily_ledger'?'daily-ledger':appKey}/transactions?history=1`,denialReason:null})
const route=(path,query={},isPublic=false)=>({path,query,params:{storeId:'A'},meta:{public:isPublic}})
const decide=(r,products,loaded=true,failed=false)=>routeAccessDecision(r,products,loaded,failed)

test('all product business URLs including administration, cached payment display and payroll are owned',()=>{
 const cases={reservation_queue:['/stores/A/reservations/today','/stores/A/admin/public-booking','/stores/A/admin/tables/new','/stores/A/cleaning'],payment:['/stores/A/payments/present/T1','/stores/A/admin/payment/settings'],wallet:['/stores/A/wallet/topup','/stores/A/wallet/cards'],wine_storage:['/stores/A/wine-storage'],workforce:['/stores/A/workforce/payroll','/stores/A/workforce-history'],daily_ledger:['/stores/A/daily-ledger']}
 for(const [key,paths] of Object.entries(cases))for(const path of paths)assert.equal(productForPath(path)?.appKey,key,path)
})
test('shared identity, customer and activation management and universal staff home stay independent',()=>{
 for(const path of ['/stores/A/staff','/stores/A/admin/product-lines','/stores/A/admin/profile','/stores/A/admin/customer-activation','/stores/A/admin/customer-email','/stores/A/catalog','/login'])assert.equal(productForPath(path),undefined,path)
 assert.equal(decide(route('/wallet/A/register',{},true),[]).mode,'unmanaged')
})
test('unknown status and failed reads cannot mount business workspaces; restoration does not require page reload',()=>{
 const r=route('/stores/A/payments/present/T1')
 assert.equal(decide(r,[],false).mode,'loading');assert.equal(decide(r,[],true,true).mode,'failed');assert.equal(decide(r,[]).mode,'denied')
 assert.equal(decide(r,[access('payment')]).mode,'business');assert.equal(decide(r,[access('payment',false)]).mode,'denied');assert.equal(decide(r,[access('payment')]).mode,'business')
})
test('closed product history is normalized to explicit read-only and cannot authorize new forms or settings',()=>{
 const p=[access('wallet',false)]
 assert.equal(decide(route('/stores/A/wallet/transactions'),p).mode,'historyRedirect')
 assert.equal(decide(route('/stores/A/wallet/transactions',{history:'1'}),p).mode,'history')
 for(const path of ['/stores/A/wallet/topup','/stores/A/wallet/settings'])assert.equal(decide(route(path,{history:'1'}),[access('wallet',true)]).mode,'denied')
 assert.equal(decide(route('/stores/A/wallet/transactions',{history:'1'}),[access('wallet',true,false)]).mode,'denied')
})
test('controlled original-order recovery is separate from explicit read-only history',()=>{
 assert.equal(decide(route('/stores/A/wallet/refund-create'),[access('wallet',false)]).mode,'recovery')
 assert.equal(decide(route('/stores/A/wallet/refund-create',{history:'1'}),[access('wallet',false)]).mode,'denied')
})
test('workforce dedicated history is available without granting operational payroll or a customer surface',()=>{
 const p=[access('workforce',false)]
 assert.equal(decide(route('/stores/A/workforce-history',{history:'1'}),p).mode,'history')
 assert.equal(decide(route('/stores/A/workforce/clock'),p).mode,'denied')
 assert.equal(decide(route('/stores/A/workforce-history',{history:'1'}),[]).mode,'denied')
})
test('history links never redirect to another tenant/store, an external site, or a business form',()=>{
 for(const historyRoute of ['https://evil.invalid','//evil.invalid','/stores/B/wallet/transactions?history=1','/stores/A/wallet/topup?history=1']){
  const result=decide(route('/stores/A/wallet/topup'),[{...access('wallet',false),historyRoute}]);assert.equal(result.historyRoute,undefined)
 }
})
