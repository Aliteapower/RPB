import { test, before, after, beforeEach, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { normalizeWalletQr } from '../../modules/wallet/scanning/walletQr.ts'
const code='WC.'+'A'.repeat(43),account={id:'account',version:2,balanceCents:500,reservedCents:200}
let vite,chargePreview,confirmScanCharge,isChargeCode,createScanChargeAttempt
before(async()=>{vite=await createServer({configFile:false,cacheDir:'target/wallet-charge-test-cache',server:{middlewareMode:true},appType:'custom',optimizeDeps:{noDiscovery:true,entries:[]}});({chargePreview,confirmScanCharge,isChargeCode,createScanChargeAttempt}=await vite.ssrLoadModule('/src/modules/wallet/scanCharge.ts'))})
after(async()=>await vite?.close())
let originalFetch
beforeEach(()=>{originalFetch=globalThis.fetch;const storage=new Map();globalThis.sessionStorage={getItem:k=>storage.get(k)??null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)}})
afterEach(()=>{globalThis.fetch=originalFetch})
test('new debit codes and legacy identity codes scan as customers but only the new code enables cashier confirmation',()=>{
 assert.equal(isChargeCode(code),true);assert.equal(isChargeCode(code.replace('WC.','WP.')),false)
 assert.equal(normalizeWalletQr(code,'customer','https://test.example'),code)
 assert.equal(normalizeWalletQr(code,'bill','https://test.example'),null)
 assert.equal(normalizeWalletQr('WC.invalid','customer','https://test.example'),null)
})
test('cashier preview uses exact cents and available funds and never reports the shortfall as received',()=>{
 assert.deepEqual(chargePreview('8.01',300),{totalCents:801,debitCents:300,dueCents:501})
 assert.deepEqual(chargePreview('0.29',29),{totalCents:29,debitCents:29,dueCents:0})
 assert.deepEqual(chargePreview(0.29,29),{totalCents:29,debitCents:29,dueCents:0})
 assert.deepEqual(chargePreview(20,3000),{totalCents:2000,debitCents:2000,dueCents:0})
 assert.deepEqual(chargePreview('5',0),{totalCents:500,debitCents:0,dueCents:500})
 for(const amount of ['0','-1','1.001','1e3','NaN','999999999999999','01',''])assert.equal(chargePreview(amount,500),null)
})
test('cashier sends a single atomic charge with preview versions and keeps the retry key after response loss',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,body:JSON.parse(options.body)});if(calls.length===1)throw new Error('response lost');return {ok:true,json:async()=>({bill:{id:'settled',status:'paid'},account:{...account,balanceCents:0}})}}
 const bill={id:'old-bill',version:7};await assert.rejects(confirmScanCharge('store',account,'5.00',code,bill),/response lost/)
 const result=await confirmScanCharge('store',account,'5.00',code,bill)
 assert.equal(result.bill.status,'paid');assert.equal(calls.length,2);assert.equal(calls[0].url,'/api/v1/stores/store/wallet/scan-charges')
 assert.equal(calls[0].body.expectedAccountVersion,2);assert.equal(calls[0].body.expectedBillVersion,7);assert.equal(calls[0].body.billId,'old-bill')
 assert.equal(calls[0].body.idempotencyKey,calls[1].body.idempotencyKey)
})
test('balance polling after a lost charge response cannot change the original retry payload or cause a second command',async()=>{
 const calls=[],confirm=createScanChargeAttempt();globalThis.fetch=async(url,options)=>{calls.push(JSON.parse(options.body));if(calls.length===1)throw new Error('response lost after commit');return{ok:true,json:async()=>({bill:{id:'original'},account:{...account,version:3}})}}
 await assert.rejects(confirm('store',account,5,code),/response lost/)
 const result=await confirm('store',{...account,version:3,balanceCents:0},5,code)
 assert.equal(result.bill.id,'original');assert.deepEqual(calls[0],calls[1]);assert.equal(calls[1].expectedAccountVersion,2)
})
