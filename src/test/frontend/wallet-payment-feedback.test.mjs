import { test } from 'node:test'
import assert from 'node:assert/strict'
import { paymentResult, paymentTransition } from '../../modules/wallet/paymentResult.ts'
const waiting={id:'bill-A',storeId:'store-A',amountCents:8000,walletPaidCents:0,externalPaidCents:0,walletApplied:false,closed:false,refundStatus:'none'}
test('scan and pending bills never claim deduction; partial and zero balance keep a clear shortfall',()=>{
 assert.equal(paymentResult(waiting),null)
 assert.deepEqual(paymentResult({...waiting,walletApplied:true,walletPaidCents:5000}),{kind:'partial',walletCents:5000,dueCents:3000,receivedCents:5000})
 assert.equal(paymentResult({...waiting,walletApplied:true}).kind,'unfunded')
 assert.equal(paymentResult({...waiting,walletApplied:true}).dueCents,8000)
})
test('success requires a settled bill and closed or refunded bills do not show stale success',()=>{
 const paid={...waiting,walletApplied:true,walletPaidCents:5000,externalPaidCents:3000}
 assert.equal(paymentResult(paid).kind,'paid');assert.equal(paymentResult(paid).dueCents,0)
 assert.equal(paymentResult({...paid,closed:true}),null);assert.equal(paymentResult({...paid,refundStatus:'partially_refunded'}),null)
 assert.equal(paymentResult({...waiting,externalPaidCents:8000}).kind,'paid')
})
test('cashier gets one new notice after customer confirmation and another only when the remaining bill settles',()=>{
 const partial={...waiting,walletApplied:true,walletPaidCents:5000},paid={...partial,externalPaidCents:3000}
 assert.equal(paymentTransition(null,paid),null);assert.equal(paymentTransition(waiting,partial).kind,'partial')
 assert.equal(paymentTransition(partial,{...partial}),null);assert.equal(paymentTransition(partial,paid).kind,'paid')
 assert.equal(paymentTransition(paid,{...paid}),null);assert.equal(paymentTransition(waiting,{...partial,id:'another-bill'}),null)
 assert.equal(paymentTransition(waiting,{...partial,storeId:'another-store'}),null)
})
