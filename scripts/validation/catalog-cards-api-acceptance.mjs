// Local isolated acceptance only. Uses this task's fixture and pointer PostgreSQL, never production.
import {readFileSync,writeFileSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {randomUUID,randomBytes,createHash} from 'node:crypto'
import assert from 'node:assert/strict'
const dir='target/catalog-cards-validation',f=JSON.parse(readFileSync(`${dir}/fixture.json`,'utf8'))
const pointer=Object.fromEntries(readFileSync('target/local-postgres-current.txt','utf8').split(/\r?\n/).filter(x=>x.includes('=')).map(x=>x.split(/=(.*)/s).slice(0,2)))
assert.match(f.schema,/^wallet_browser_[a-f0-9]{32}$/);assert.equal(f.pgPort,pointer.port);assert.equal(f.backendPort,8084)
const store=f.stores[0],base=`/api/v1/stores/${store}`,staff=base+'/wallet/cards',customer=`/api/v1/public/stores/${store}/wallet/cards`,catalog=base+'/catalog',checks=[]
const managerId=randomUUID(),managerToken=randomBytes(32).toString('base64url')
const key=()=>randomUUID(),day=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Singapore',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date())
async function api(method,path,body,role='staff',expected=200){const headers={'Content-Type':'application/json','X-RPB-Wallet':'1','X-RPB-Wallet-Model':'components-v2','X-RPB-Catalog':'1',Cookie:role==='customer'?`RPB_CUSTOMER_SESSION=${f.customerToken}`:role==='none'?'':role==='manager'?`RPB_SESSION=${managerToken}`:`RPB_SESSION=${f.staffToken}`};const r=await fetch(`http://127.0.0.1:${f.backendPort}`+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body)});const value=await r.json().catch(()=>({}));assert.equal(r.status,expected,`${method} ${path}: ${JSON.stringify(value)}`);return value}
function sql(text){return execFileSync('C:/Program Files/PostgreSQL/17/bin/psql.exe',['-h','127.0.0.1','-p',f.pgPort,'-U','postgres','-d','postgres','-Atq','-v','ON_ERROR_STOP=1'],{input:text,encoding:'utf8',env:{...process.env,PGCLIENTENCODING:'UTF8',PGOPTIONS:`-c search_path=${f.schema},public`}}).trim()}
const baseline=JSON.parse(sql(`select json_build_object('received',coalesce(sum(amount_cents) filter(where kind='payment'),0),'refunded',coalesce(sum(amount_cents) filter(where kind='refund'),0)) from card_payment_links where tenant_id='${f.tenant}'`))
sql(`insert into auth_accounts(id,tenant_id,username,display_name,actor_type,status,password_hash,default_store_id) values('${managerId}','${f.tenant}','manager-${managerId}','A店目录管理员','staff','active','unusable-test-password','${store}');
insert into auth_account_roles(account_id,role_code) values('${managerId}','store_manager');
insert into auth_account_permissions(account_id,permission_code) select '${managerId}',p from unnest(array['catalog.view','catalog.manage','catalog.publish']) p;
insert into auth_account_store_access(account_id,tenant_id,store_id) values('${managerId}','${f.tenant}','${store}');
insert into auth_user_sessions(account_id,tenant_id,session_hash,status,expires_at) values('${managerId}','${f.tenant}','${createHash('sha256').update(managerToken).digest('hex')}','active',now()+interval '2 hours');`)
await api('GET',catalog+'/categories',undefined,'none',401)
await api('PUT',staff+'/settings',{enabled:true})
const category=await api('POST',catalog+'/categories',{code:'CC-'+key().slice(0,8),nameZh:'卡权益产品',nameEn:'Card benefits',enabled:true,sortOrder:0,idempotencyKey:key()})
let product=await api('POST',catalog+'/products',{code:'COFFEE-'+key().slice(0,8),categoryId:category.id,nameZh:'精品咖啡',nameEn:'Specialty coffee',descriptionZh:'指定饮品权益',descriptionEn:'Selected beverage benefit',kind:'physical',unit:'杯',imageUrl:'',priceCents:500,taxMode:'inclusive',taxCategory:'taxable',taxRate:'0.09',channels:['cards','shopping'],idempotencyKey:key()})
product=await api('POST',catalog+`/products/${product.id}/status`,{status:'active',expectedVersion:product.version,idempotencyKey:key()})
const quoted=await api('POST',catalog+`/products/${product.id}/quote`,{quantity:2,channel:'cards',expectedVersion:product.version});assert.equal(quoted.netCents+quoted.taxCents,quoted.grossCents);assert.equal(quoted.grossCents,1000)
checks.push('bilingual categorized catalog and tax-inclusive exact-cent quotation')
const plan=async(kind,extra={})=>api('POST',staff+'/plans',{nameZh:kind==='stamp'?'集十赠一':'吃货卡',nameEn:kind==='stamp'?'Ten stamps, one reward':'Food pass',kind,productId:product.id,total:kind==='count'?30:null,dailyLimit:kind==='stamp'?20:2,validDays:30,stampTarget:kind==='stamp'?10:0,repeatable:true,rewardDays:7,priceCents:kind==='stamp'?0:20000,taxMode:'inclusive',taxRate:'0',idempotencyKey:key(),...extra})
const stampPlan=await plan('stamp'),paidPlan=await plan('count')
const holding=await api('POST',customer+'/enroll',{planId:stampPlan.id,idempotencyKey:key()},'customer')
const wi=await api('POST',`/api/v1/public/stores/${store}/wallet/personal-codes`,{},'customer');assert.equal((await api('POST',staff+'/identity',{code:wi.code})).id,f.customer)
for(let i=0;i<10;i++)await api('POST',staff+'/stamps',{cardId:holding.id,source:`acceptance:${key()}`,idempotencyKey:key()})
let holdings=await api('GET',customer+'/holdings',undefined,'customer');const reward=holdings.find(h=>h.kind==='reward'&&h.planId===stampPlan.id);assert.ok(reward);assert.equal(holdings.find(h=>h.id===holding.id).round,1)
async function request(card,quantity=1,kind='redeem'){const r=await api('POST',staff+'/requests',{kind,planId:card.planId,cardId:card.id,quantity,idempotencyKey:key()});const code=await api('POST',staff+`/requests/${r.id}/code`,{});assert.equal((await api('POST',customer+'/scan',{code:code.code},'customer')).status,'pending');return r}
const r=await request(reward);const authorized=await api('POST',customer+`/requests/${r.id}/authorize`,{cardId:reward.id,businessDay:day},'customer');assert.equal((await api('POST',staff+'/authorized-redeem',{authorization:authorized.code,idempotencyKey:key()})).status,'completed')
const stampRequest=await request(holding,1,'stamp');await api('POST',customer+`/requests/${stampRequest.id}/confirm`,{cardId:holding.id,businessDay:day,idempotencyKey:key()},'customer')
checks.push('no stored-value account needed; merchant scans identity, ten stamps issue reward; customer confirms merchant code and authorizes exact reward')
const buy=()=>api('POST',customer+'/purchases',{planId:paidPlan.id,idempotencyKey:key()},'customer')
const payment=order=>({channel:'cash',account:'',terminal:'ACCEPTANCE',receipt:key(),amountCents:order.amountCents,verified:true,idempotencyKey:key()})
const purchase=await buy(),receipt=payment(purchase);const issued=await api('POST',staff+`/purchases/${purchase.id}/confirm`,receipt);assert.equal((await api('POST',staff+`/purchases/${purchase.id}/confirm`,receipt)).cardId,issued.cardId)
holdings=await api('GET',customer+'/holdings',undefined,'customer');const count=holdings.find(h=>h.id===issued.cardId);const consume=await request(count,2);await api('POST',customer+`/requests/${consume.id}/confirm`,{cardId:count.id,businessDay:day,idempotencyKey:key()},'customer')
assert.equal((await api('GET',customer+'/holdings',undefined,'customer')).find(h=>h.id===count.id).remaining,28)
const excess=await request(count);await api('POST',customer+`/requests/${excess.id}/confirm`,{cardId:count.id,businessDay:day,idempotencyKey:key()},'customer',409)
await api('POST',staff+`/purchases/${purchase.id}/refund`,{reason:'Used card',receipt:key(),verified:true,idempotencyKey:key()},'staff',409)
const refundable=await buy();await api('POST',staff+`/purchases/${refundable.id}/confirm`,payment(refundable));assert.equal((await api('POST',staff+`/purchases/${refundable.id}/refund`,{reason:'未使用退卡',receipt:key(),verified:true,idempotencyKey:key()})).status,'refunded')
checks.push('paid card issued exactly once; 30 uses become 28, daily cap blocks excess; unused refund works and used refund is denied')
const exceptionOrder=await buy();await api('POST',staff+`/purchases/${exceptionOrder.id}/exception`,{payment:payment(exceptionOrder),reason:'本地虚构的异常实收',idempotencyKey:key()});assert.equal((await api('POST',staff+`/purchases/${exceptionOrder.id}/refund`,{reason:'异常款退回',receipt:key(),verified:true,idempotencyKey:key()})).status,'exception_refunded')
await api('POST',catalog+`/products/${product.id}/status`,{status:'inactive',expectedVersion:product.version,idempotencyKey:key()},'manager',403)
await api('GET',`/api/v1/stores/${f.stores[1]}/catalog/products`,undefined,'manager',403)
checks.push('store manager cannot change tenant-wide lifecycle or access an unassigned sibling store')
const siblingCatalog=`/api/v1/stores/${f.stores[1]}/catalog`;await api('POST',siblingCatalog+`/products/${product.id}/listing`,{priceCents:600,channels:['cards'],enabled:true,expectedVersion:0,idempotencyKey:key()})
await api('POST',catalog+`/products/${product.id}/listing`,{priceCents:500,channels:['cards','shopping'],enabled:false,expectedVersion:product.listingVersion,idempotencyKey:key()},'manager')
await api('POST',customer+'/purchases',{planId:paidPlan.id,idempotencyKey:key()},'customer',409)
const siblingProduct=(await api('GET',siblingCatalog+'/products')).find(p=>p.id===product.id);assert.equal(siblingProduct.listingEnabled,true)
await api('POST',siblingCatalog+`/products/${product.id}/quote`,{quantity:1,channel:'cards',expectedVersion:siblingProduct.version})
const summary=await api('GET',staff+`/summary?from=${day}&to=${day}`);assert.equal(summary.money.receivedCents,baseline.received+60000);assert.equal(summary.money.refundedCents,baseline.refunded+40000)
assert.equal(sql(`select count(*) from wallet_accounts where tenant_id='${f.tenant}'`),'0');assert.equal(sql(`select count(*) from wallet_entries where tenant_id='${f.tenant}'`),'0')
checks.push('original exceptional receipt retained and refunded; store listing isolation; gross receipts counted once with independent card ledger')
writeFileSync(`${dir}/api-acceptance.json`,JSON.stringify({schema:f.schema,store,product:product.id,stampPlan:stampPlan.id,paidPlan:paidPlan.id,checks,summary},null,2));console.log(JSON.stringify({schema:f.schema,passed:checks.length,receivedCents:summary.money.receivedCents,refundedCents:summary.money.refundedCents}))
