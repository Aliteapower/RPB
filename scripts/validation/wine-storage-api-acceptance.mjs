// Local-only API acceptance. Never connects to production or writes to an existing business schema.
import {readFileSync,writeFileSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {randomUUID,randomBytes,createHash} from 'node:crypto'
import {deflateSync} from 'node:zlib'
import assert from 'node:assert/strict'
const dir='target/wine-storage-validation',f=JSON.parse(readFileSync(`${dir}/fixture.json`,'utf8'))
const pointer=Object.fromEntries(readFileSync('target/local-postgres-current.txt','utf8').split(/\r?\n/).filter(x=>x.includes('=')).map(x=>x.split(/=(.*)/s).slice(0,2)))
assert.equal(f.pgPort,pointer.port);assert.equal(f.backendPort,8086);assert.match(f.schema,/^wallet_browser_[a-f0-9]{32}$/)
const store=f.stores[0],staff=`/api/v1/stores/${store}/wine-storage`,customer=`/api/v1/public/stores/${store}/wine-storage`,catalog=`/api/v1/stores/${store}/catalog`,checks=[]
const key=()=>randomUUID(),hash=x=>createHash('sha256').update(x).digest('hex')
function sql(text){return execFileSync('C:/Program Files/PostgreSQL/17/bin/psql.exe',['-h','127.0.0.1','-p',f.pgPort,'-U','postgres','-d','postgres','-Atq','-v','ON_ERROR_STOP=1'],{input:text,encoding:'utf8',env:{...process.env,PGCLIENTENCODING:'UTF8',PGOPTIONS:`-c search_path=${f.schema},public`}}).trim()}
// The fixture was created before the evidence-link addition during this development run.
// Apply this exact missing table only to this owned isolated schema. Fresh fixtures already contain it.
if(sql("select to_regclass('wine_storage_photo_links')")===''){
 const migration=readFileSync('src/main/resources/db/migration/V077__wine_storage.sql','utf8');const statement=migration.match(/create table wine_storage_photo_links \([\s\S]*?\n\);/)[0];sql(statement)
}
async function request(path,body,role='staff',expected=200,headersExtra={}){
 const headers={'X-RPB-Storage':'1','X-RPB-Catalog':'1',Cookie:role==='customer'?`RPB_CUSTOMER_SESSION=${f.customerToken}`:role==='other'?`RPB_CUSTOMER_SESSION=${other.token}`:role==='none'?'':`RPB_SESSION=${f.staffToken}`,...headersExtra}
 if(!(body instanceof FormData))headers['Content-Type']='application/json'
 const response=await fetch(`http://127.0.0.1:${f.backendPort}`+path,{method:body===undefined?'GET':'POST',headers,body:body===undefined?undefined:body instanceof FormData?body:JSON.stringify(body)})
 const value=await response.json().catch(()=>({}));assert.equal(response.status,expected,`${path}: ${JSON.stringify(value)}`);return value
}
function crc(buffer){let c=0xffffffff;for(const b of buffer){c^=b;for(let i=0;i<8;i++)c=c&1?0xedb88320^(c>>>1):c>>>1}return(c^0xffffffff)>>>0}
function chunk(type,data){const b=Buffer.alloc(data.length+12);b.writeUInt32BE(data.length);b.write(type,4);data.copy(b,8);b.writeUInt32BE(crc(b.subarray(4,-4)),b.length-4);return b}
const ihdr=Buffer.alloc(13);ihdr.writeUInt32BE(1);ihdr.writeUInt32BE(1,4);ihdr[8]=8;ihdr[9]=2
const png=Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',ihdr),chunk('IDAT',deflateSync(Buffer.from([0,30,80,40]))),chunk('IEND',Buffer.alloc(0))])
async function photo(receipt=false,phase='receipt',atStore=store,owner=f.customer,role=receipt?'staff':'customer'){const form=new FormData();form.set('image',new Blob([png],{type:'image/png'}),'bottle.png');if(receipt){form.set('customerId',owner);form.set('phase',phase)}return(await request(`/api/v1/${receipt?'':'public/'}stores/${atStore}/wine-storage/photos`,form,role)).id}
const other={customer:key(),auth:key(),token:randomBytes(32).toString('base64url')}
sql(`insert into customers(id,tenant_id,customer_code,customer_type,display_name,status) values('${other.customer}','${f.tenant}','other-${other.customer}','regular','其他顾客','active');
insert into customer_auth_accounts(id,tenant_id,customer_id,email,display_name,status,email_verified_at) values('${other.auth}','${f.tenant}','${other.customer}','other-${other.auth}@example.com','其他顾客','active',now());
insert into customer_auth_sessions(tenant_id,auth_account_id,customer_id,session_hash,status,expires_at) values('${f.tenant}','${other.auth}','${other.customer}','${hash(other.token)}','active',now()+interval '4 hours');`)
await request(customer+'/home',undefined,'none',401);await request(staff+'/home',undefined,'none',401)
await request(customer+'/applications',{lines:[]},'customer',403,{'X-RPB-Storage':''})
const cat=await request(catalog+'/categories',{code:'WS-'+key(),nameZh:'啤酒',nameEn:'Beer',enabled:true,sortOrder:0,idempotencyKey:key()})
let product=await request(catalog+'/products',{code:'WS-'+key(),categoryId:cat.id,nameZh:'测试啤酒',nameEn:'Storage Beer',descriptionZh:'',descriptionEn:'',kind:'physical',unit:'瓶',imageUrl:'',priceCents:500,taxMode:'inclusive',taxCategory:'taxable',taxRate:'0.09',channels:['storage'],idempotencyKey:key()})
product=await request(catalog+`/products/${product.id}/status`,{status:'active',expectedVersion:product.version,idempotencyKey:key()})
const rule=await request(staff+'/rules',{categoryId:cat.id,name:'啤酒保存三个月',months:3,idempotencyKey:key()})
for(const q of ['测试','Beer',product.code])assert.ok((await request(customer+'/products?query='+encodeURIComponent(q),undefined,'customer')).some(p=>p.id===product.id))
checks.push('authenticated HTTP, CSRF header, bilingual and code search, AppGate, rule configuration')
async function application(quantity,opened=false,newName=false){return request(customer+'/applications',{lines:[{productId:newName?null:product.id,nameZh:newName?'顾客新酒名':'',nameEn:newName?'Suggested Wine':'',quantity,opened,source:'customer',photos:[await photo()],note:'本地测试照片'}],rulesAccepted:true,idempotencyKey:key()},'customer')}
async function receive(a,quantity,opened=false,newProduct=false){return request(staff+`/applications/${a.id}/receive`,{lines:[{lineId:a.lines[0].id,productId:newProduct?null:product.id,categoryId:cat.id,nameZh:newProduct?'顾客新酒名':'',nameEn:newProduct?'Suggested Wine':'',quantity,opened,source:'customer',ruleId:rule.id,photos:[await photo(true)],note:'现场核对',location:'A-01',label:'WS-'+key()}],reason:'现场核对，顾客可见',idempotencyKey:key()})}
const a=await application(6);assert.equal((await request(customer+'/home',undefined,'customer')).items.length,0);await receive(a,6)
const b=await application(1,true);await receive(b,1,true)
let home=await request(customer+'/home',undefined,'customer'),whole=home.items.find(i=>i.applicationId===a.id),opened=home.items.find(i=>i.applicationId===b.id)
const cancelled=await application(1);assert.equal((await request(customer+`/applications/${cancelled.id}/cancel`,{idempotencyKey:key()},'customer')).status,'cancelled')
const rejected=await application(1);assert.equal((await request(staff+`/applications/${rejected.id}/reject`,{reason:'未带实物',idempotencyKey:key()})).status,'rejected')
checks.push('pending never creates holdings; actual receipt; opened-bottle records; cancellation and rejection')
const selection=[{itemId:whole.id,quantity:2},{itemId:opened.id,quantity:1}],intent=await request(customer+'/intents',{selections:selection,idempotencyKey:key()},'customer')
await request(staff+'/scan',{code:intent.code});await request(customer+'/scan',{code:intent.code},'other',404)
const deliver={idempotencyKey:key()};await request(staff+`/intents/${intent.id}/deliver`,deliver);await request(staff+`/intents/${intent.id}/deliver`,deliver)
home=await request(customer+'/home',undefined,'customer');assert.equal(home.items.find(i=>i.id===whole.id).remaining,4);assert.equal(home.items.find(i=>i.id===opened.id).remaining,0);assert.equal(home.items.find(i=>i.id===whole.id).expiresAt,whole.expiresAt)
const merchant=await request(staff+'/intents',{customerId:f.customer,selections:[{itemId:whole.id,quantity:1}],idempotencyKey:key()})
await request(staff+`/intents/${merchant.id}/deliver`,{idempotencyKey:key()},'staff',409)
await request(customer+'/scan',{code:merchant.code},'customer');await request(customer+`/intents/${merchant.id}/authorize`,{code:merchant.code,idempotencyKey:key()},'customer');await request(staff+`/intents/${merchant.id}/deliver`,{idempotencyKey:key()})
checks.push('bidirectional exact-item QR authorization; explicit customer authorization; batch partial sealed and whole opened handover; replay once')
// Cross-store and cross-customer batch requests must fail without touching valid siblings.
async function scopedHolding(atStore,owner,role){
 const st=`/api/v1/stores/${atStore}/wine-storage`,cu=`/api/v1/public/stores/${atStore}/wine-storage`
 const applied=await request(cu+'/applications',{lines:[{productId:product.id,quantity:1,opened:false,source:'customer',photos:[await photo(false,'application',atStore,owner,role)]}],rulesAccepted:true,idempotencyKey:key()},role)
 const localRule=atStore===store?rule:await request(st+'/rules',{categoryId:cat.id,name:'三个月',months:3,idempotencyKey:key()})
 await request(st+`/applications/${applied.id}/receive`,{lines:[{lineId:applied.lines[0].id,productId:product.id,quantity:1,opened:false,source:'customer',ruleId:localRule.id,photos:[await photo(true,'receipt',atStore,owner)],location:'B1',label:'scope',note:''}],reason:'本地隔离测试',idempotencyKey:key()})
 return(await request(cu+'/home',undefined,role)).items.find(i=>i.applicationId===applied.id)
}
await request(`/api/v1/stores/${f.stores[1]}/catalog/products/${product.id}/listing`,{priceCents:500,channels:['storage'],enabled:true,expectedVersion:0,idempotencyKey:key()})
const siblingHolding=await scopedHolding(f.stores[1],f.customer,'customer'),foreignHolding=await scopedHolding(store,other.customer,'other')
for(const foreign of [siblingHolding,foreignHolding])await request(customer+'/intents',{selections:[{itemId:whole.id,quantity:1},{itemId:foreign.id,quantity:1}],idempotencyKey:key()},'customer',404)
assert.equal((await request(customer+'/home',undefined,'customer')).items.find(i=>i.id===whole.id).remaining,3)
assert.ok(!(await request(customer+'/home',undefined,'customer')).items.some(i=>i.id===foreignHolding.id))
checks.push('actual cross-store and cross-customer batch denial; valid selected sibling stays unchanged; customer home isolates other holdings')
const redeposit=await request(customer+'/applications',{lines:[{productId:product.id,quantity:1,opened:true,source:'customer',photos:[await photo()],note:'再次寄存'}],rulesAccepted:true,withdrawalId:intent.id,withdrawalItemId:whole.id,idempotencyKey:key()},'customer');assert.notEqual(redeposit.id,a.id);assert.equal(redeposit.status,'pending')
const suggestion=await application(1,false,true);assert.equal((await request(customer+'/products?query=Suggested',undefined,'customer')).length,0);await receive(suggestion,1,false,true)
const suggested=(await request(catalog+'/products')).find(p=>p.nameEn==='Suggested Wine');assert.equal(suggested.priceCents,null);assert.deepEqual(suggested.channels,['storage']);assert.equal(suggested.taxRate,null)
await request(catalog+`/products/${suggested.id}/quote`,{quantity:1,channel:'shopping',expectedVersion:suggested.version},'staff',409)
const photoId=a.lines[0].photos[0]
for(const [url,role] of [[customer+`/photos/${photoId}`,'other'],[`/api/v1/public/stores/${f.stores[1]}/wine-storage/photos/${photoId}`,'customer']])await request(url,undefined,role,404)
await request(customer+`/applications/${a.id}/cancel`,{idempotencyKey:key()},'other',404)
const extra=await photo(true,'followup');await request(staff+`/items/${whole.id}/photos`,{photos:[extra],note:'后续核对',idempotencyKey:key()})
await request(staff+`/items/${whole.id}/corrections`,{reason:'标签说明',note:'不修改数量期限',idempotencyKey:key()})
checks.push('new suggestion private until approved; storage-only NULL price/tax product; new redeposit application; photo ownership/store denial; append-only evidence and correction')
const expiring=await request(customer+'/intents',{selections:[{itemId:whole.id,quantity:1}],idempotencyKey:key()},'customer')
sql(`update wine_storage_items set received_at=now()-interval '1 year',expires_at=now() where id='${whole.id}' and tenant_id='${f.tenant}'`)
const failure=await request(staff+`/intents/${expiring.id}/deliver`,{idempotencyKey:key()},'staff',409);assert.equal(failure.code,'STORAGE_BATCH_CONFLICT');assert.equal(failure.problems[0].itemId,whole.id)
assert.equal((await request(customer+'/home',undefined,'customer')).items.find(i=>i.id===whole.id).remaining,3)
checks.push('request before bottle expiry, handover after expiry denied in actual HTTP; no debit')
// Switch the business off; read and valid historic fulfillment still work.
sql(`update store_app_settings set is_enabled=false where tenant_id='${f.tenant}' and store_id='${store}' and app_key='wine_storage'`)
home=await request(customer+'/home',undefined,'customer');assert.equal(home.newBusinessEnabled,false)
await request(customer+'/applications',{lines:[],rulesAccepted:true,idempotencyKey:key()},'customer',403)
const valid=home.items.find(i=>i.snapshot.productId===suggested.id)
await request(catalog+`/products/${suggested.id}/status`,{status:'inactive',expectedVersion:suggested.version,idempotencyKey:key()})
const historical=await request(customer+'/intents',{selections:[{itemId:valid.id,quantity:1}],idempotencyKey:key()},'customer');await request(staff+`/intents/${historical.id}/deliver`,{idempotencyKey:key()})
checks.push('store business off blocks new intake while history and controlled handover remain; product delisting does not block existing storage')
for(const table of ['wallet_accounts','wallet_entries','loyalty_accounts','loyalty_entries','customer_cards','card_entries','card_purchase_orders','card_payment_links'])if(sql(`select to_regclass('${table}')`))assert.equal(sql(`select count(*) from ${table} where tenant_id='${f.tenant}'`),'0')
// Restore only the fixture switch for browser acceptance.
sql(`update store_app_settings set is_enabled=true where tenant_id='${f.tenant}' and store_id='${store}' and app_key='wine_storage'`)
const browserApplication=await application(3);await receive(browserApplication,3)
writeFileSync(`${dir}/api-acceptance.json`,JSON.stringify({schema:f.schema,store,checks,application:a.id,whole:whole.id,opened:opened.id,product:product.id,rule:rule.id},null,2))
console.log(JSON.stringify({passed:checks.length,schema:f.schema,checks},null,2))
