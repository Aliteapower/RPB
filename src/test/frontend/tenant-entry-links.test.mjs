import {before,after,test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {initialLoginEntry,entryTenantCode} from '../../utils/loginEntry.ts'
let buildTenantEntries
const context={success:true,identity:{tenantId:'tenant-a',tenantCode:'tenant-a',tenantName:'统一租户名称',tenantStatus:'active',storeId:'store-a',storeCode:'A',storeName:'门店甲',storeStatus:'active'},publicBookingEnabled:true,products:['wallet','reservation_queue','payment','daily_ledger'].map(appKey=>({appKey,available:true,denialReason:null}))}
const loc=(hostname='booking.yumstone.sg')=>({hostname,protocol:'https:',port:'',origin:'https://'+hostname})
before(async()=>{globalThis.window={__RPB_HOST_PREFIX_BASE_HOST__:'booking.yumstone.sg'};const result=await build({entryPoints:['src/modules/tenant-entry-links/catalog.ts'],bundle:true,write:false,format:'esm',platform:'node',define:{'import.meta.env':'{}'}});({buildTenantEntries}=await import('data:text/javascript;base64,'+Buffer.from(result.outputFiles[0].contents).toString('base64')))})
after(()=>{delete globalThis.window})

test('every generated URL identifies the correct tenant and selected store and contains no credentials',()=>{
 for(const host of ['booking.yumstone.sg','platform.booking.yumstone.sg','other.booking.yumstone.sg','tenant-a.booking.yumstone.sg']){
  const entries=buildTenantEntries(context,loc(host));assert.equal(entries.length,7)
  for(const e of entries){const url=new URL(e.url);assert.equal(url.origin,'https://tenant-a.booking.yumstone.sg');assert.ok(e.available);assert.equal(url.username,'');assert.equal(url.password,'');assert.equal(url.hash,'')}
  assert.equal(entries[0].url,'https://tenant-a.booking.yumstone.sg/wallet/store-a')
  assert.equal(entries[1].url,'https://tenant-a.booking.yumstone.sg/book/store-a')
  const staff=new URL(entries.find(e=>e.key==='staff').url);assert.equal(staff.searchParams.get('entry'),'staff');assert.equal(staff.searchParams.get('redirect'),'/stores/store-a/staff')
 }
})
test('local development keeps its actual origin and port and supplies only a tenant hint for login',()=>{
 const rows=buildTenantEntries(context,{protocol:'http:',hostname:'127.0.0.1',port:'5178',origin:'http://127.0.0.1:5178'})
 assert.equal(new URL(rows[0].url).origin,'http://127.0.0.1:5178');assert.equal(new URL(rows[2].url).searchParams.get('tenantCode'),'tenant-a')
})

test('new product lines expose store-scoped wine and workforce entry links without opening unavailable products',()=>{
 const products=['wallet','reservation_queue','payment','daily_ledger','wine_storage','workforce'].map(appKey=>({appKey,available:true,denialReason:null}))
 const rows=buildTenantEntries({...context,products},loc())
 for(const key of ['wineCustomer','wineStaff','workforce','workforceClock','workforceAdmin'])assert.ok(rows.some(e=>e.key===key),key)
 assert.equal(new URL(rows.find(e=>e.key==='wineCustomer').url).pathname,'/wallet/store-a/wine-storage')
 for(const key of ['wineStaff','workforce','workforceClock','workforceAdmin'])assert.match(new URL(rows.find(e=>e.key===key).url).searchParams.get('redirect'),/^\/stores\/store-a\//)
 const closed=buildTenantEntries({...context,products:products.map(p=>p.appKey==='workforce'?{...p,available:false}:p)},loc())
 assert.ok(closed.every(e=>!e.key.startsWith('workforce')))
})
test('unavailable products, closed booking and inactive stores never become distributable entry codes',()=>{
 const c={...context,publicBookingEnabled:false,products:[{appKey:'wallet',available:false,denialReason:'STORE_APP_EXPIRED'}]}
 const rows=buildTenantEntries(c,loc());assert.equal(rows.some(e=>e.key==='walletCustomer'||e.key==='booking'||e.key==='payment'),false);assert.equal(rows.find(e=>e.key==='staff').available,true)
 assert.ok(buildTenantEntries({...context,identity:{...context.identity,storeStatus:'inactive'}},loc()).every(e=>!e.available))
})
test('switching a store changes public paths and protected redirects and unsafe tenant domains are rejected',()=>{
 const second=buildTenantEntries({...context,identity:{...context.identity,storeId:'store-b'}},loc())
 assert.ok(second.every(e=>!e.url.includes('store-a')));assert.ok(second[0].url.endsWith('/wallet/store-b'))
 for(const tenantCode of ['evil.com','platform','x@evil.com','../other','javascript:'])assert.throws(()=>buildTenantEntries({...context,identity:{...context.identity,tenantCode}},loc()))
})
test('staff and admin links preselect a permitted login tab without changing platform or tenant identity',()=>{
 assert.equal(initialLoginEntry('tenant','staff'),'tenant-staff');assert.equal(initialLoginEntry('legacy','tenant_admin'),'tenant-admin')
 assert.equal(initialLoginEntry('platform','staff'),'platform-admin');assert.equal(initialLoginEntry('tenant','platform_admin'),'tenant-admin')
 assert.equal(initialLoginEntry('tenant',undefined),'tenant-admin');assert.equal(initialLoginEntry('legacy',undefined),'tenant-staff')
 assert.equal(entryTenantCode('tenant','tenant-a','tenant-b','fallback'),'tenant-a');assert.equal(entryTenantCode('legacy','','tenant-b','fallback'),'tenant-b')
 assert.equal(entryTenantCode('legacy','','evil.com','fallback'),'fallback')
 assert.equal(entryTenantCode('legacy','','tenant-from-link','remembered-other-tenant'),'tenant-from-link')
 assert.equal(entryTenantCode('legacy','',undefined,'remembered-other-tenant'),'remembered-other-tenant')
})
