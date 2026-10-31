import {test,before,beforeEach,after} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {mkdir,writeFile} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
let staffScanCode,setScanHandoff,takeScanHandoff,createStaffScanSession
const originalFetch=globalThis.fetch,originalStorage=globalThis.sessionStorage
const store='20000000-0000-0000-0000-000000000983',origin='https://booking.test',token=p=>p+'.'+'A'.repeat(43)
before(async()=>{
 const result=await build({stdin:{contents:"export * from './src/modules/shared-commerce/scanning/staffCode.mjs';export * from './src/modules/staff-scan/handoff';export * from './src/modules/staff-scan/session'",resolveDir:process.cwd()},bundle:true,write:false,format:'esm',platform:'node'})
 await mkdir('target/staff-scan-test',{recursive:true});await writeFile('target/staff-scan-test/entry.mjs',result.outputFiles[0].contents)
 ;({staffScanCode,setScanHandoff,takeScanHandoff,createStaffScanSession}=await import(pathToFileURL(process.cwd()+'/target/staff-scan-test/entry.mjs').href+'?'+Date.now()))
})
beforeEach(()=>{const memory=new Map();globalThis.sessionStorage={getItem:k=>memory.get(k)??null,setItem:(k,v)=>memory.set(k,v),removeItem:k=>memory.delete(k)}})
after(()=>{globalThis.fetch=originalFetch;globalThis.sessionStorage=originalStorage})
const reply=(data,status=200)=>({ok:status<400,status,json:async()=>data})
test('merchant recognises only supported customer credentials and scoped same-origin card URLs',()=>{
 for(const [prefix,kind] of [['WI','identity'],['WP','identity'],['WC','payment'],['CA','card'],['WS','storage']])assert.deepEqual(staffScanCode(' '+token(prefix)+' ',origin,store),{kind,code:token(prefix)})
 assert.equal(staffScanCode(`${origin}/wallet/${store}/cards#card=${token('CA')}`,origin,store).kind,'card')
 for(const value of [token('CK'),token('WB'),token('WR'),token('WM'),token('CA')+'x','https://evil.test/wallet/'+store+'/cards#card='+token('CA'),origin+'/wallet/00000000-0000-0000-0000-000000000000/cards#card='+token('CA'),'javascript:alert(1)','A'.repeat(4097)])assert.equal(staffScanCode(value,origin,store),null,value)
})
test('handoff is one-use and bound to actor, store, destination and expiry without persistence',()=>{
 const value={store,actor:'staff',target:'cards',code:token('WI')}
 setScanHandoff(value);assert.equal(takeScanHandoff(store,'other','cards'),null);assert.equal(takeScanHandoff(store,'staff','cards'),null)
 setScanHandoff(value);assert.equal(takeScanHandoff('other','staff','cards'),null)
 setScanHandoff(value);assert.equal(takeScanHandoff(store,'staff','recharge'),null)
 setScanHandoff(value);assert.equal(takeScanHandoff(store,'staff','cards').code,value.code);assert.equal(takeScanHandoff(store,'staff','cards'),null)
 const now=Date.now;try{setScanHandoff(value);Date.now=()=>now()+31000;assert.equal(takeScanHandoff(store,'staff','cards'),null)}finally{Date.now=now}
})
test('authorised card and storage codes call their original atomic commands with persistent keys',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,body:JSON.parse(options.body)});return reply({status:'completed',id:'done'})}
 const session=createStaffScanSession(store,'staff',()=> 'staff',()=>true)
 assert.equal((await session.scan({kind:'card',code:token('CA')})).kind,'card')
 assert.equal((await session.scan({kind:'storage',code:token('WS')})).kind,'storage')
 assert.match(calls[0].url,/wallet\/cards\/authorized-redeem$/);assert.equal(calls[0].body.authorization,token('CA'))
 assert.match(calls[1].url,/wine-storage\/scan-deliver$/);assert.equal(calls[1].body.code,token('WS'))
 assert.ok(calls.every(c=>c.body.idempotencyKey));assert.equal(session.pending().card,false);assert.equal(session.pending().storage,false)
})
test('duplicate scan callbacks and missing permission cannot submit another transaction',async()=>{
 let resolve,calls=0;globalThis.fetch=async()=>{calls++;await new Promise(r=>resolve=r);return reply({status:'completed'})}
 const session=createStaffScanSession(store,'staff',()=> 'staff',()=>true),pending=session.scan({kind:'storage',code:token('WS')})
 await assert.rejects(session.scan({kind:'storage',code:token('WS')}),/处理中/);assert.equal(calls,1);resolve();await pending
 const denied=createStaffScanSession(store,'staff',()=> 'staff',()=>false)
 await assert.rejects(denied.scan({kind:'card',code:token('CA')}),/权限/);await assert.rejects(denied.scan({kind:'storage',code:token('WS')}),/权限/);assert.equal(calls,1)
})
test('uncertain commands block new codes and recover using the original module, original staff only',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,method:options?.method,body:options?.body});if(calls.length===1)throw Error('response lost');return reply({id:'done',status:'completed'})}
 let actor='staff';const session=createStaffScanSession(store,actor,()=>actor,()=>true)
 await assert.rejects(session.scan({kind:'card',code:token('CA')}),/response lost/);assert.equal(session.pending().card,true)
 await assert.rejects(session.scan({kind:'storage',code:token('WS')}),/恢复/)
 actor='other';const other=createStaffScanSession(store,'other',()=>actor,()=>true);await assert.rejects(other.recover(),/原业务/);assert.equal(calls.length,1)
 actor='staff';assert.equal((await session.recover()).result.status,'completed');assert.match(calls[1].url,/cards\/commands\/request.authorized\//);assert.equal(session.pending().card,false)
 assert.ok(!sessionStorage.getItem('rpb.staff-scan.owner:'+store))
})
test('customer identity scans only resolve identity and server denials are never treated as completion',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,body:JSON.parse(options.body)});return reply({id:'customer',displayName:'顾客'})}
 const session=createStaffScanSession(store,'staff',()=> 'staff',()=>true)
 assert.equal((await session.scan({kind:'identity',code:token('WI')})).customer.id,'customer');assert.equal(calls.length,1);assert.match(calls[0].url,/customer-codes\/resolve$/);assert.deepEqual(calls[0].body,{code:token('WI')})
 globalThis.fetch=async()=>reply({code:'STORAGE_FORBIDDEN',message:'Wrong store'},403)
 await assert.rejects(session.scan({kind:'storage',code:token('WS')}),/Wrong store/);assert.equal(session.pending().storage,false)
})
test('uncertain bottle redemption replays exactly the same command and idempotency key',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,body:JSON.parse(options.body)});if(calls.length===1)throw Error('response lost');return reply({id:'original',status:'completed',selections:[{itemId:'bottle',quantity:3}]})}
 const session=createStaffScanSession(store,'staff',()=> 'staff',()=>true)
 await assert.rejects(session.scan({kind:'storage',code:token('WS')}),/response lost/);assert.equal(session.pending().storage,true)
 const recovered=await session.recover();assert.equal(recovered.result.id,'original');assert.deepEqual(calls[0],calls[1]);assert.equal(session.pending().storage,false)
})
