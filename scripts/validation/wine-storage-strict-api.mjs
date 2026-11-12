import {readFileSync,writeFileSync} from 'node:fs'
import {randomUUID} from 'node:crypto'
import assert from 'node:assert/strict'
const dir='target/wine-storage-validation',f=JSON.parse(readFileSync(`${dir}/fixture.json`,'utf8'))
const pointer=Object.fromEntries(readFileSync('target/local-postgres-current.txt','utf8').split(/\r?\n/).filter(x=>x.includes('=')).map(x=>x.split(/=(.*)/s).slice(0,2)))
assert.equal(f.pgPort,pointer.port);assert.equal(f.backendPort,8086);assert.match(f.schema,/^wallet_browser_[a-f0-9]{32}$/)
const store=f.stores[1],base=`http://127.0.0.1:8086/api/v1/public/stores/${store}/wine-storage`
async function call(url,body,staff=false,expected=200){const r=await fetch(url,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json','X-RPB-Storage':'1',Cookie:staff?`RPB_SESSION=${f.staffToken}`:`RPB_CUSTOMER_SESSION=${f.customerToken}`},body:body===undefined?undefined:JSON.stringify(body)});const data=await r.json();assert.equal(r.status,expected,JSON.stringify(data));return data}
const before=await call(base+'/home'),item=before.items.find(i=>i.status==='stored')
assert.ok(item)
for(const [bytes,type] of [[new Uint8Array(5*1024*1024+1),'image/png'],[new TextEncoder().encode('<svg/>'),'image/svg+xml']]){
 const body=new FormData();body.set('image',new Blob([bytes],{type}),'invalid-image');const response=await fetch(base+'/photos',{method:'POST',headers:{'X-RPB-Storage':'1',Cookie:`RPB_CUSTOMER_SESSION=${f.customerToken}`},body});assert.equal(response.status,400)
}
for(const quantity of [1.5,'1',null,2147483648])await call(base+'/intents',{selections:[{itemId:item.id,quantity}],idempotencyKey:randomUUID()},false,400)
await call(`http://127.0.0.1:8086/api/v1/stores/${store}/wine-storage/rules`,{categoryId:before.categories[0].id,name:'小数期限必须拒绝',months:1.5,idempotencyKey:randomUUID()},true,400)
const intent=await call(base+'/intents',{selections:[{itemId:item.id,quantity:1}],idempotencyKey:randomUUID()})
await call(base+`/intents/${intent.id}/cancel`,{idempotencyKey:randomUUID()})
const after=await call(base+'/home');assert.equal(after.items.find(i=>i.id===item.id).remaining,item.remaining);assert.deepEqual(after.rules,before.rules)
const result={checks:['oversized and unsupported-type photos return 400','fractional/string/null/overflow bottle counts return 400','fractional calendar months return 400','valid integer authorizes and cancels normally','no bottle debit and no rule version created'],schema:f.schema,store}
writeFileSync(`${dir}/strict-api.json`,JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2))
