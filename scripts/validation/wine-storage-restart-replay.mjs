import {readFileSync,writeFileSync,existsSync} from 'node:fs'
import {randomUUID} from 'node:crypto'
import assert from 'node:assert/strict'
const dir='target/wine-storage-validation',f=JSON.parse(readFileSync(`${dir}/fixture.json`,'utf8'))
const pointer=Object.fromEntries(readFileSync('target/local-postgres-current.txt','utf8').split(/\r?\n/).filter(x=>x.includes('=')).map(x=>x.split(/=(.*)/s).slice(0,2)))
assert.equal(f.pgPort,pointer.port);assert.equal(f.backendPort,8086);assert.match(f.schema,/^wallet_browser_[a-f0-9]{32}$/)
const store=f.stores[1],customer=`http://127.0.0.1:8086/api/v1/public/stores/${store}/wine-storage`,staff=`http://127.0.0.1:8086/api/v1/stores/${store}/wine-storage`
async function api(url,body,employee=false){const r=await fetch(url,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json','X-RPB-Storage':'1',Cookie:employee?`RPB_SESSION=${f.staffToken}`:`RPB_CUSTOMER_SESSION=${f.customerToken}`},body:body===undefined?undefined:JSON.stringify(body)});const data=await r.json();assert.equal(r.status,200,JSON.stringify(data));return data}
const path=`${dir}/restart-replay.json`,mode=process.argv[2]
if(mode==='create'){
 assert.equal(existsSync(path),false,'An owned replay case already exists; verify it instead')
 const home=await api(customer+'/home'),item=home.items.find(i=>i.status==='stored');assert.ok(item)
 const intent=await api(customer+'/intents',{selections:[{itemId:item.id,quantity:1}],idempotencyKey:randomUUID()})
 const body={idempotencyKey:randomUUID()},response=await api(staff+`/intents/${intent.id}/deliver`,body,true)
 assert.deepEqual(await api(staff+`/intents/${intent.id}/deliver`,body,true),response)
 writeFileSync(path,JSON.stringify({schema:f.schema,store,itemId:item.id,intentId:intent.id,body,response,before:item.remaining},null,2));console.log('PASS: committed one handover and same-process replay; ready to restart isolated backend')
}else if(mode==='verify'){
 const saved=JSON.parse(readFileSync(path,'utf8'));assert.equal(saved.schema,f.schema);assert.deepEqual(await api(staff+`/intents/${saved.intentId}/deliver`,saved.body,true),saved.response)
 const home=await api(customer+'/home'),item=home.items.find(i=>i.id===saved.itemId);assert.equal(item.remaining,saved.before-1)
 assert.equal(home.events.filter(e=>e.action==='delivered'&&e.details.intentId===saved.intentId).length,1)
 writeFileSync(`${dir}/restart-replay-result.json`,JSON.stringify({schema:f.schema,store,intentId:saved.intentId,remaining:item.remaining,checks:['same command returns original committed response after a JVM restart','exactly one quantity debit and one handover event']},null,2));console.log('PASS: cross-JVM replay returns original result, with one debit and one event')
}else throw new Error('Use create or verify against the owned isolated local fixture')
