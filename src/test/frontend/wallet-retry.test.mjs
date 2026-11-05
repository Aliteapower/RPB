import { test, beforeEach, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { selectedStoreBillingRetry } from '../../api/selectedStoreBillingRetry.ts'
import { walletApi } from '../../modules/wallet/api.ts'
let storage, originalFetch
beforeEach(() => {
  storage = new Map(); originalFetch = globalThis.fetch
  globalThis.sessionStorage = { getItem: key => storage.get(key) ?? null, setItem: (key,value) => storage.set(key,value), removeItem: key => storage.delete(key) }
})
afterEach(() => { globalThis.fetch = originalFetch })
test('a lost wallet billing response reuses the original command, while a changed store selection gets its own key', async () => {
  const input = { idempotencyKey:'first', appKey:'wallet', billingCycle:'monthly',durationCount:1,version:3,storeIds:['B','A'],paymentNote:'private note' }
  const calls=[]; let applied=0;const committed=new Map()
  const server = async body => {calls.push(body);if(!committed.has(body.idempotencyKey)){applied++;committed.set(body.idempotencyKey,{applied})}if(calls.length===1)throw new Error('response lost after commit');return committed.get(body.idempotencyKey)}
  await assert.rejects(selectedStoreBillingRetry('/wallet/renew',input,server),/response lost/)
  assert.equal([...storage.values()].join(','),'first');assert.ok(![...storage.keys()].join().includes('private note'))
  const result = await selectedStoreBillingRetry('/wallet/renew',{...input,idempotencyKey:'replacement',storeIds:['A','B']},server)
  assert.equal(result.applied,1);assert.equal(calls[1].idempotencyKey,'first');assert.equal(applied,1);assert.equal(storage.size,0)
  await selectedStoreBillingRetry('/wallet/renew',{...input,idempotencyKey:'new-selection',storeIds:['C']},server)
  assert.equal(applied,2)
})
test('wallet money commands survive response loss, use same-origin cookies, and keep store retries separate', async () => {
  const bodies=[];globalThis.fetch=async (url,options)=>{bodies.push({url,options,body:JSON.parse(options.body)});if(bodies.length===1)throw new Error('offline');return {ok:true,status:200,json:async()=>({credited:true})}}
  await assert.rejects(walletApi('storeA').command('/topups/id/confirm',{amount:'50',receiptReference:'PRIVATE-REF'}),/offline/)
  await walletApi('storeA').command('/topups/id/confirm',{amount:'50',receiptReference:'PRIVATE-REF'})
  assert.equal(bodies[0].body.idempotencyKey,bodies[1].body.idempotencyKey);assert.equal(bodies[1].options.credentials,'same-origin');assert.equal(bodies[1].options.headers['X-RPB-Wallet'],'1')
  await walletApi('storeB').command('/topups/id/confirm',{amount:'50',receiptReference:'PRIVATE-REF'})
  assert.notEqual(bodies[1].body.idempotencyKey,bodies[2].body.idempotencyKey);assert.equal(storage.size,0)
})
