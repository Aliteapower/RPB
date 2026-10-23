import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'vite'
let vite, createPersonalChargeAttempt
before(async()=>{vite=await createServer({configFile:false,cacheDir:'target/wallet-personal-test-cache',server:{middlewareMode:true,hmr:false},appType:'custom',optimizeDeps:{noDiscovery:true,entries:[]}});({createPersonalChargeAttempt}=await vite.ssrLoadModule('/src/modules/wallet/personalCharge.ts'))})
after(async()=>vite?.close())
test('lost response queries original transaction, preserves key without storing QR, blocks concurrent scans',async()=>{
 const old=globalThis.fetch, data=new Map();globalThis.sessionStorage={getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v),removeItem:k=>data.delete(k)}
 const calls=[];const qr='WI.'+'A'.repeat(43)
 globalThis.fetch=async(url,options)=>{calls.push({url,body:options.body&&JSON.parse(options.body)});if(options.method==='POST')throw Error('lost');return{ok:true,json:async()=>({status:'paid',bill:{id:'original'}})}}
 try{const attempt=createPersonalChargeAttempt('store','cashier-A');await assert.rejects(attempt.scan(qr,'5.00','Tea'),/lost/);assert.ok(!JSON.stringify([...data]).includes(qr));const key=calls[0].body.idempotencyKey;const restored=createPersonalChargeAttempt('store','cashier-A');assert.equal((await restored.recover()).bill.id,'original');assert.ok(calls[1].url.includes(key));assert.equal(calls.length,2);assert.equal(data.size,0)}finally{globalThis.fetch=old}
})
test('reload of unsubmitted transaction reconciles and seals original key before permitting fresh scan',async()=>{
 const old=globalThis.fetch,data=new Map();globalThis.sessionStorage={getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v),removeItem:k=>data.delete(k)}
 const calls=[];let first=true
 globalThis.fetch=async(url,options)=>{calls.push({url,body:options.body&&JSON.parse(options.body)});if(url.endsWith('/personal-scan-charges')&&first){first=false;throw Error('offline')};if(url.includes('/result?'))return{ok:false,status:404,json:async()=>({code:'NOT_FOUND'})};return{ok:true,json:async()=>url.endsWith('/reconcile')?{status:'not_submitted',bill:null,account:null,storeId:'store'}:{status:'paid',bill:{id:'new'}}}}
 try{const attempt=createPersonalChargeAttempt('store','cashier-A');await assert.rejects(attempt.scan('WI.'+'A'.repeat(43),5,'Tea'));const original=calls[0].body.idempotencyKey;const reloaded=createPersonalChargeAttempt('store','cashier-A');assert.equal(reloaded.pending,true);await assert.rejects(reloaded.scan('WI.'+'B'.repeat(43),6,''));assert.equal((await reloaded.retry()).status,'not_submitted');assert.equal(calls[2].body.idempotencyKey,original);assert.equal(reloaded.pending,false);await reloaded.scan('WI.'+'C'.repeat(43),6,'Coffee');assert.notEqual(calls[3].body.idempotencyKey,original)}finally{globalThis.fetch=old}
})
test('fresh valid personal scan required, no selected customer or account in payment payload',async()=>{
 const old=globalThis.fetch;globalThis.sessionStorage={getItem:()=>null,setItem:()=>{},removeItem:()=>{}};let release,calls=0
 globalThis.fetch=async(url,options)=>{calls++;const body=JSON.parse(options.body);assert.equal(body.customerId,undefined);assert.equal(body.accountId,undefined);await new Promise(r=>release=r);return{ok:true,json:async()=>({status:'confirmation_required',bill:{id:'bill'}})}}
 try{const attempt=createPersonalChargeAttempt('store','cashier-A');await assert.rejects(attempt.scan('',5,'Tea'));const first=attempt.scan('WI.'+'B'.repeat(43),5,'Tea');await assert.rejects(attempt.scan('WI.'+'C'.repeat(43),5,'Tea'));release();assert.equal((await first).status,'confirmation_required');assert.equal(calls,1)}finally{globalThis.fetch=old}
})
test('unreadable successful response remains pending and cannot silently issue another charge',async()=>{
 const old=globalThis.fetch,data=new Map();globalThis.sessionStorage={getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v),removeItem:k=>data.delete(k)}
 globalThis.fetch=async()=>({ok:true,json:async()=>{throw Error('truncated response')}})
 try{const attempt=createPersonalChargeAttempt('truncated','cashier-A');await assert.rejects(attempt.scan('WI.'+'A'.repeat(43),5,''),/could not be read/);assert.equal(attempt.pending,true);await assert.rejects(attempt.scan('WI.'+'B'.repeat(43),5,''),/pending transaction/);assert.equal(createPersonalChargeAttempt('truncated','cashier-A').pending,true)}finally{globalThis.fetch=old}
})
test('late response from an earlier page instance cannot erase the next unknown operation key',async()=>{
 const old=globalThis.fetch,data=new Map();globalThis.sessionStorage={getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v),removeItem:k=>data.delete(k)}
 let release,writes=0
 globalThis.fetch=async(url)=>{if(url.includes('/result?'))return{ok:true,json:async()=>({status:'paid',bill:{id:'first'}})};if(++writes===1)return await new Promise(r=>release=r);throw Error('second response lost')}
 try{const first=createPersonalChargeAttempt('store','cashier-A'),inflight=first.scan('WI.'+'A'.repeat(43),5,'First');const second=createPersonalChargeAttempt('store','cashier-A');await second.recover();await assert.rejects(second.scan('WI.'+'B'.repeat(43),6,'Second'));const saved=[...data.values()][0];release({ok:true,json:async()=>({status:'paid',bill:{id:'first'}})});await inflight;assert.equal([...data.values()][0],saved);assert.equal(createPersonalChargeAttempt('store','cashier-A').pending,true)}finally{globalThis.fetch=old}
})


test('another cashier and legacy ownerless record cannot query, reconcile, scan or clear the pending original',async()=>{
 const old=globalThis.fetch,data=new Map();globalThis.sessionStorage={getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v),removeItem:k=>data.delete(k)};let calls=0
 globalThis.fetch=async()=>{calls++;throw Error('lost')}
 try{const a=createPersonalChargeAttempt('store','cashier-A');await assert.rejects(a.scan('WI.'+'A'.repeat(43),5,'Tea'));const saved=[...data.values()][0];const b=createPersonalChargeAttempt('store','cashier-B');assert.equal(b.pending,true);assert.equal(b.ownerMismatch,true);await assert.rejects(b.recover(),/original cashier/);await assert.rejects(b.retry(),/original cashier/);await assert.rejects(b.scan('WI.'+'B'.repeat(43),5,''));assert.equal(calls,1);assert.equal([...data.values()][0],saved)
 const original=JSON.parse(saved);delete original.ownerAccountId;data.set([...data.keys()][0],JSON.stringify(original));const unknown=createPersonalChargeAttempt('store','cashier-A');assert.equal(unknown.ownerMismatch,true);await assert.rejects(unknown.retry(),/original cashier/);assert.equal(calls,1);assert.equal(data.size,1)
 }finally{globalThis.fetch=old}
})

test('actor change between missing-result lookup and reconcile cannot send reconcile under the new login',async()=>{
 const old=globalThis.fetch,data=new Map();globalThis.sessionStorage={getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v),removeItem:k=>data.delete(k)};let actor='cashier-A',calls=0
 globalThis.fetch=async(url)=>{calls++;if(url.includes('/result?')){actor='cashier-B';return{ok:false,status:404,json:async()=>({})}}throw Error('lost')}
 try{const original=createPersonalChargeAttempt('store','cashier-A',()=>actor);await assert.rejects(original.scan('WI.'+'A'.repeat(43),5,''));await assert.rejects(original.retry(),/original cashier/);assert.equal(calls,2);assert.equal(data.size,1)}finally{globalThis.fetch=old}
})
