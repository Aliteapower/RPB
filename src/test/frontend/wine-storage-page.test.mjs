import {test,before,after,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {parse,compileScript} from '@vue/compiler-sfc'
import {createRenderer,h,nextTick,reactive} from 'vue'
import {createI18n} from 'vue-i18n'
import {createPinia} from 'pinia'
import {routeLocationKey,routerKey} from 'vue-router'
import {build} from 'esbuild'
import {readFile,mkdir,writeFile} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
let Page,Summary,CustomerPage,StaffPage,CatalogPage,useAuthSessionStore,setScanHandoff,ReceiveForm,LocationsPanel,storageApi
const mounted=new Set()
afterEach(()=>{for(const app of mounted)app.unmount();mounted.clear();for(const role of ['true','false'])globalThis.sessionStorage?.removeItem(`rpb.storage.pending:${role}:A`)})
const originalFetch=globalThis.fetch,originalStorage=globalThis.sessionStorage,originalDocument=globalThis.document,originalDocumentClass=globalThis.Document,originalShadowRoot=globalThis.ShadowRoot
const renderer=createRenderer({createElement:tag=>({tag,tagName:tag.toUpperCase(),children:[],props:{},value:'',get options(){return this.children.filter(c=>c.tag==='option')},listeners:{},addEventListener(name,fn){this.listeners[name]=fn},removeEventListener(){},setAttribute(){},removeAttribute(){},getRootNode(){return globalThis.document}}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>{n.props[k]=v;if(k==='value'){n._value=v;n.value=v}},insert:(n,p,anchor)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=anchor?p.children.indexOf(anchor):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:n=>{const c=n.parent?.children||[];return c[c.indexOf(n)+1]||null}})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>[n.text||'',...(n.children||[]).map(text)].join(' ')
const tick=async()=>{for(let n=0;n<12;n++){await Promise.resolve();await nextTick()}}
const home={timezone:'Asia/Singapore',storeName:'QA Store',stores:[{id:'A',name:'QA Store'}],newBusinessEnabled:true,permissions:['wine_storage.view','wine_storage.receive','wine_storage.deliver','wine_storage.rules.manage'],rules:[],categories:[],items:[],events:[],intents:[],applications:[{id:'application',customerId:'customer',status:'pending',lines:[],confirmation:null,reason:'',createdAt:'2026-10-25T10:00:00Z'}]}
before(async()=>{
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document=Object.assign(new Document(),{activeElement:null,visibilityState:'visible',listeners:new Map(),addEventListener(name,fn){if(!this.listeners.has(name))this.listeners.set(name,new Set());this.listeners.get(name).add(fn)},removeEventListener(name,fn){this.listeners.get(name)?.delete(fn)}})
 const memory=new Map();globalThis.sessionStorage={getItem:k=>memory.get(k)??null,setItem:(k,v)=>memory.set(k,v),removeItem:k=>memory.delete(k)}
 const result=await build({stdin:{contents:"export {default as ReceiveForm} from './src/modules/wine-storage/StorageReceiveForm.vue';export {default as LocationsPanel} from './src/modules/wine-storage/merchant/MerchantStorageLocations.vue';export {storageApi} from './src/modules/wine-storage/api';export {setScanHandoff} from './src/modules/staff-scan/handoff';export {useAuthSessionStore} from './src/stores/authSession';export {default as CatalogPage} from './src/modules/catalog/CatalogPage.vue';export {default as CustomerPage} from './src/modules/wine-storage/WineStorageCustomerPage.vue';export {default as StaffPage} from './src/modules/wine-storage/WineStorageStaffPage.vue';export {default as Page} from './src/modules/wine-storage/WineStoragePage.vue';export {default as Summary} from './src/modules/wine-storage/WalletStorageSummary.vue'",resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router','vue-i18n','pinia'],plugins:[{name:'storage-page-test',setup(b){b.onLoad({filter:/\.css$/},()=>({contents:'',loader:'js'}));b.onLoad({filter:/\.vue$/},async({path})=>{if(path.endsWith('ProductBarcodeScanner.vue'))return{contents:"import {h} from 'vue';export default {props:['disabled','autoStart','showManual'],emits:['code'],setup:(p,{emit})=>()=>h('button',{'data-barcode':true,'data-auto-start':p.autoStart,onClick:()=>emit('code','00036000291452')},'test-barcode')}",loader:'js'};if(path.endsWith('StoragePhotos.vue'))return{contents:"import {h} from 'vue';export default {props:['modelValue'],emits:['update:modelValue'],setup:(p,{emit})=>()=>h('button',{type:'button','data-test-photo':true,onClick:()=>emit('update:modelValue',['photo-id'])},'test-photo')}",loader:'js'};if(path.endsWith('WalletQrDisplay.vue'))return{contents:"import {h} from 'vue';export default {props:['value'],setup:p=>()=>h('figure',{'data-code':p.value})}",loader:'js'};if(path.endsWith('WalletQrScanner.vue'))return{contents:"import {h} from 'vue';export default {props:['disabled'],emits:['code'],setup:(p,{emit})=>()=>h('button',{'data-storage-scan':true,disabled:p.disabled,onClick:()=>emit('code','WS.'+'S'.repeat(43))},'test-storage-scan')}",loader:'js'};if(/WalletCustomerLogin\.vue$/.test(path))return{contents:"export default {render(){return null}}",loader:'js'};const{descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:'storage-test-'+path,inlineTemplate:true}).content,loader:'ts'}})}}]})
 const dir='target/wine-storage-page-test';await mkdir(dir,{recursive:true});await writeFile(dir+'/entry.mjs',result.outputFiles[0].contents);({Page,Summary,CustomerPage,StaffPage,CatalogPage,useAuthSessionStore,setScanHandoff,ReceiveForm,LocationsPanel,storageApi}=await import(pathToFileURL(process.cwd()+'/'+dir+'/entry.mjs').href+'?'+Date.now()))
})
after(()=>{globalThis.fetch=originalFetch;globalThis.sessionStorage=originalStorage;globalThis.document=originalDocument;globalThis.Document=originalDocumentClass;globalThis.ShadowRoot=originalShadowRoot})
function mount(component,customer,locale='zh-CN',data=home,respond,lists={}){
 const calls=[],root={children:[]};globalThis.fetch=async(url,options)=>{calls.push({url,method:options?.method??'GET',body:typeof options?.body==='string'?JSON.parse(options.body):undefined});return{ok:true,json:async()=>url.endsWith('/locations')&&(!options?.method||options.method==='GET')?(lists.locations??[{id:'front-desk',name:'前台寄存处',defaultLocation:true,version:0}]):url.endsWith('/pending-applications')?[...(lists.pending??[])]:url.includes('/custodians')&&(!options?.method||options.method==='GET')?[...(lists.people??[])]:respond?respond(url,options):url.includes('/products')?[]:data}}
 const route=reactive({params:{storeId:'A'},hash:'',query:{}});const app=renderer.createApp({render:()=>h(component,{customer,store:'A',...lists.props})});const pinia=createPinia();app.use(pinia);if(lists.actor)useAuthSessionStore(pinia).user=lists.actor;const i18n=createI18n({legacy:false,locale,messages:{}});app.use(i18n);app.provide(routeLocationKey,route);app.provide(routerKey,{push:async target=>{if(target?.query)route.query=target.query},replace:async target=>{if(target?.hash!==undefined)route.hash=target.hash}});app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:p.to},slots.default?.())});app.mount(root);mounted.add(app);return{root,calls,i18n,unmount:()=>{app.unmount();mounted.delete(app)}}
}
test('storage customer and merchant entry titles and API scopes are distinct',async()=>{
 for(const customer of [true,false]){
  const s=mount(customer?CustomerPage:StaffPage,customer);await tick();assert.match(text(s.root),customer?/我的存酒卡/:/酒水寄存管理/)
  assert.equal(s.calls[0].url,`/api/v1/${customer?'public/':''}stores/A/wine-storage/home`)
  assert.equal(nodes(s.root,n=>n.tag==='a')[0].props.href,customer?'/wallet/A':'/stores/A/wallet')
  assert.ok(nodes(s.root,n=>n.props?.class?.includes('cards-page')).length)
  assert.equal(nodes(s.root,n=>n.tag==='button'&&n.props['aria-pressed']===true).length,1)
  if(customer){assert.match(text(s.root),/我要存酒/);assert.doesNotMatch(text(s.root),/选择同一顾客|搜索酒名、编号、位置或标签/)}
  assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
 }
})
test('customer pages never expose merchant receipt or rule editing even if response permissions include them',async()=>{
 for(const customer of [true,false]){
  const s=mount(customer?CustomerPage:StaffPage,customer);await tick()
  if(customer){assert.match(text(s.root),/我要存酒/);assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='保存规则').length,0);s.unmount();continue}
  assert.match(text(s.root),/帮顾客存酒.*帮顾客取酒.*存取酒记录/)
  assert.match(text(s.root),/顾客提交 · 待审核/);assert.doesNotMatch(text(s.root),/请先查找并确认一位顾客/)
  assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='确认实际收存').length,0)
  assert.equal(nodes(s.root,n=>n.tag==='form'&&nodes(n,k=>k.tag==='button'&&text(k)==='保存新版本').length>0).length,1)
  s.unmount()
 }
})
test('customer tasks show one workflow at a time and cannot collect without bottles',async()=>{
 const s=mount(CustomerPage,true);await tick()
 assert.match(text(s.root),/选择酒品/);assert.doesNotMatch(text(s.root),/申请领取所选物品|生成顾客扫码授权意图/)
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='我要取酒')[0].props.onClick();await tick()
 assert.match(text(s.root),/暂无可领取的酒品/);assert.doesNotMatch(text(s.root),/选择同一顾客|搜索中英文酒名或编号/)
 assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n).includes('核对领取清单')&&!n.props.disabled).length,0)
 assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
})
test('wallet storage summaries link only to their own role entry',async()=>{
 for(const customer of [true,false]){
  const s=mount(Summary,customer);await tick();assert.equal(nodes(s.root,n=>n.tag==='a')[0].props.href,customer?'/wallet/A/wine-storage':'/stores/A/wine-storage');assert.match(text(s.root),customer?/我的存酒卡/:/酒水寄存管理/);s.unmount()
 }
})
test('dedicated role entry cannot be flipped by a forwarded customer attribute',async()=>{
 for(const [component,customer] of [[CustomerPage,true],[StaffPage,false]]){
  const s=mount(component,!customer);await tick()
  assert.equal(s.calls[0].url,`/api/v1/${customer?'public/':''}stores/A/wine-storage/home`)
  s.unmount()
 }
})
test('customer directly generates a scoped code without duplicate selection or review steps',async()=>{
 const item={id:'my-bottle',applicationId:'a',customerId:'already-authenticated',snapshot:{productId:'p',code:'WINE',nameZh:'我的白酒',nameEn:'My spirit',categoryId:'c',rule:{name:'6个月',months:6,version:1},timezone:'Asia/Singapore',note:'',applicationPhotos:[],receiptPhotos:[]},quantity:3,remaining:2,opened:false,source:'customer',location:'',label:'',receivedAt:new Date().toISOString(),expiresAt:new Date(Date.now()+86400000).toISOString(),status:'stored'}
 const code='WS.'+'A'.repeat(43),data={...home,items:[item]},intent={id:'intent',status:'authorized',origin:'customer',expiresAt:new Date(Date.now()+300000).toISOString(),selections:[{itemId:item.id,quantity:1}],code}
 const s=mount(CustomerPage,true,'zh-CN',data,(url,options)=>options?.method==='POST'?(data.intents=[{...intent,code:null}],intent):url.includes('/products')?[]:{...data});await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='我要取酒')[0].props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox').length,0)
 assert.doesNotMatch(text(s.root),/下一步：核对领取清单/)
 assert.equal(s.calls.filter(c=>c.method==='POST').length,0)
 await nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='出码取酒')[0].props.onClick();await tick()
 const writes=s.calls.filter(c=>c.method==='POST');assert.equal(writes.length,1);assert.equal(writes[0].url,'/api/v1/public/stores/A/wine-storage/intents');assert.deepEqual(writes[0].body.selections,[{itemId:item.id,quantity:1}]);assert.equal('customerId'in writes[0].body,false)
 assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-code']===code).length,1);assert.match(text(s.root),/取酒码已生成/)
 globalThis.fetch=async()=>({ok:false,status:401,json:async()=>({message:'Sign in'})})
 await nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='刷新')[0].props.onClick();await tick()
 globalThis.fetch=async()=>({ok:true,json:async()=>({...home,items:[],intents:[]})})
 await nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='刷新')[0].props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-code']===code).length,0)
 s.unmount()
})
test('customer bottle corner generates a quantity-scoped pickup code and hides it after redemption',async()=>{
 const item={id:'quick-bottle',customerId:'customer',snapshot:{nameZh:'青岛啤酒',nameEn:'Beer',timezone:'Asia/Singapore'},remaining:3,opened:false,status:'stored',expiresAt:new Date(Date.now()+86400000).toISOString()},code='WS.'+'Q'.repeat(43),intent={id:'quick',origin:'customer',status:'authorized',code,expiresAt:new Date(Date.now()+300000).toISOString(),selections:[{itemId:item.id,quantity:2}]},data={...home,items:[item],intents:[]}
 let resolve
 const s=mount(CustomerPage,true,'zh-CN',data,(url,options)=>options?.method==='POST'?new Promise(r=>resolve=r):url.includes('/product-options')?{items:[],categories:[],hasMore:false}:{...data});await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='我要取酒')[0].props.onClick();await tick()
 const quick=()=>nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='出码取酒')[0]
 assert.ok(quick());assert.equal(s.calls.filter(c=>c.method==='POST').length,0)
 const quantity=()=>nodes(s.root,n=>n.tag==='input'&&n.props.type==='number')[0]
 quantity().props.onInput({target:{value:'0'}});await tick();assert.equal(quantity().props.value,1)
 quantity().props.onInput({target:{value:'4'}});await tick();assert.equal(quantity().props.value,3)
 quantity().props.onInput({target:{value:'1.5'}});await tick();assert.equal(quantity().props.value,1)
 quantity().props.onInput({target:{value:'2'}});await tick();assert.equal(quick().props.disabled,false)
 const first=quick().props.onClick();quick().props.onClick();await tick();assert.equal(quick().props.disabled,true)
 const writes=s.calls.filter(c=>c.method==='POST');assert.equal(writes.length,1);assert.equal(writes[0].url,'/api/v1/public/stores/A/wine-storage/intents');assert.deepEqual(writes[0].body.selections,[{itemId:item.id,quantity:2}]);assert.equal('customerId'in writes[0].body,false)
 data.intents=[{...intent,code:null}];resolve(intent);await first;await tick()
 const card=nodes(s.root,n=>n.tag==='article'&&String(n.props.class).includes('storage-customer-bottle'))[0]
 assert.equal(nodes(card,n=>n.tag==='figure'&&n.props['data-code']===code).length,1);assert.match(text(card),/扫码即核销/);assert.match(text(s.root),/1. 选择要取的酒/)
 data.intents=[{...intent,status:'completed',code:null}];data.items=[{...item,remaining:2}]
 await Promise.all([...globalThis.document.listeners.get('visibilitychange')].map(fn=>fn()));await tick()
 assert.equal(nodes(s.root,n=>n.tag==='figure').length,0);assert.match(text(s.root),/该码已失效/);assert.equal(s.calls.filter(c=>c.method==='POST').length,1);s.unmount()
})

test('quick pickup excludes expired holdings, reports rejected commands and supports cancellation in English',async()=>{
 const item={id:'quick-cancel',snapshot:{nameZh:'啤酒',nameEn:'Beer',timezone:'Asia/Singapore'},remaining:2,opened:false,status:'stored',expiresAt:new Date(Date.now()+86400000).toISOString()},intent={id:'quick-cancel-code',origin:'customer',status:'authorized',code:'WS.'+'C'.repeat(43),expiresAt:new Date(Date.now()+300000).toISOString(),selections:[{itemId:item.id,quantity:1}]},data={...home,items:[item,{...item,id:'expired',expiresAt:new Date(Date.now()-1000).toISOString()}],intents:[]}
 const s=mount(CustomerPage,true,'en',data,(url,options)=>options?.method==='POST'?(url.endsWith('/cancel')?(data.intents=[{...intent,code:null,status:'cancelled'}],data.intents[0]):(data.intents=[{...intent,code:null}],intent)):url.includes('/product-options')?{items:[],categories:[],hasMore:false}:{...data});await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='Collect bottles')[0].props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='Show pickup QR').length,1)
 const fetchOk=globalThis.fetch;globalThis.fetch=async(url,options)=>options?.method==='POST'?{ok:false,status:409,json:async()=>({code:'STORAGE_CONFLICT',message:'Bottle availability changed'})}:fetchOk(url,options)
 await nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='Show pickup QR')[0].props.onClick();await tick()
 assert.match(text(s.root),/Bottle availability changed/);assert.equal(nodes(s.root,n=>n.tag==='figure').length,0)
 globalThis.fetch=fetchOk
 await nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='Show pickup QR')[0].props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='figure').length,1)
 await nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='Cancel')[0].props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='figure').length,0);assert.equal(s.calls.filter(c=>c.url.endsWith('/intents/quick-cancel-code/cancel')).length,1);s.unmount()
})

test('customer selects a catalog wine, adds photos and submits the application as the signed-in customer',async()=>{
 const product={id:'wine',nameZh:'可寄存白酒',nameEn:'Spirit',code:'WINE'},s=mount(CustomerPage,true,'zh-CN',home,(url,options)=>url.includes('/product-options')?{items:[product],categories:[],hasMore:false}:url.endsWith('/products/wine')?product:url.includes('/products')?[product]:options?.method==='POST'?{id:'applied',lines:[],status:'pending'}:home);await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).includes('可寄存白酒'))[0].props.onClick();await tick()
 const submit=()=>nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='提交寄存申请')[0]
 assert.equal(submit().props.disabled,true)
 nodes(s.root,n=>n.props?.['data-test-photo'])[0].props.onClick();await tick()
 const accepted=nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0];accepted.checked=true;accepted.listeners.change({target:accepted});await tick()
 assert.equal(submit().props.disabled,false)
 await nodes(s.root,n=>n.tag==='form'&&n.props.class==='storage-customer-deposit')[0].props.onSubmit({preventDefault(){}});await tick()
 const writes=s.calls.filter(c=>c.method==='POST');assert.equal(writes.length,1);assert.equal(writes[0].url,'/api/v1/public/stores/A/wine-storage/applications');assert.equal(writes[0].body.lines[0].productId,'wine');assert.deepEqual(writes[0].body.lines[0].photos,['photo-id']);assert.equal('customerId'in writes[0].body,false);assert.equal('localKey'in writes[0].body.lines[0],false);assert.match(text(s.root),/寄存申请已提交/);s.unmount()
})
test('merchant finds one customer, scopes their bottles and waits for authorization before actual delivery',async()=>{
 const item={id:'a-bottle',applicationId:'application',customerId:'customer',snapshot:{nameZh:'甲客白酒',nameEn:'A wine',timezone:'Asia/Singapore',receiptPhotos:[]},quantity:3,remaining:2,opened:false,status:'stored',location:'A1',label:'L1',expiresAt:new Date(Date.now()+86400000).toISOString()},other={...item,id:'b-bottle',customerId:'other',snapshot:{...item.snapshot,nameZh:'乙客啤酒'}}
 const intent={id:'staff-intent',customerId:'customer',origin:'staff',status:'awaiting_authorization',expiresAt:new Date(Date.now()+300000).toISOString(),selections:[{itemId:item.id,quantity:1}],code:'WS.'+'B'.repeat(43)},data={...home,items:[item,other],intents:[]}
 const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>url.includes('/customers?')?[{id:'customer',displayName:'甲顾客',phone:'+6580000000'}]:options?.method==='POST'?intent:{...data,intents:data.intents.slice()});await tick()
 assert.doesNotMatch(text(s.root),/甲客白酒|乙客啤酒/)
 const searchForm=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-merchant-customer-search'))[0]
 nodes(searchForm,n=>n.tag==='input')[0].props.onInput({target:{value:'8000'}});await tick();await searchForm.props.onSubmit({preventDefault(){}});await tick()
 assert.ok(s.calls.some(c=>c.url==='/api/v1/stores/A/wine-storage/customers?query=8000'))
 nodes(s.root,n=>n.tag==='button'&&text(n).includes('甲顾客'))[0].props.onClick();await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='帮顾客取酒')[0].props.onClick();await tick()
 assert.match(text(s.root),/甲客白酒/);assert.doesNotMatch(text(s.root),/乙客啤酒/)
 await nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='生成顾客确认码')[0].props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-code']===intent.code).length,1)
 const writes=s.calls.filter(c=>c.method==='POST');assert.equal(writes.length,1);assert.equal(writes[0].body.customerId,'customer');assert.deepEqual(writes[0].body.selections,[{itemId:item.id,quantity:1}])
 assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='确认实物已交付').length,0)
 data.intents=[{...intent,status:'authorized'}]
 await Promise.all([...globalThis.document.listeners.get('visibilitychange')].map(fn=>fn()));await tick()
 assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='确认实物已交付').length,1)
 assert.equal(s.calls.filter(c=>c.url.endsWith('/deliver')).length,0);s.unmount()
})

test('merchant directly registers bottles for the chosen customer with photos and explicit attestation',async()=>{
 const product={id:'wine',categoryId:'category',nameZh:'白酒',nameEn:'Spirit',code:'WINE'},data={...home,applications:[],rules:[{id:'rule',categoryId:'category',name:'保存三个月',months:3,version:1}],categories:[{id:'category',nameZh:'白酒'}]}
 const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>url.includes('/customers?')?[{id:'customer',displayName:'甲顾客',phone:'+6580000000'}]:url.endsWith('/products/wine')?product:url.includes('/products')?[product]:options?.method==='POST'?{id:'registered',customerId:'customer',status:'stored',lines:[]}:{...data});await tick()
 const search=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-merchant-customer-search'))[0]
 nodes(search,n=>n.tag==='input')[0].props.onInput({target:{value:'8000'}});await search.props.onSubmit({preventDefault(){}});await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).includes('甲顾客'))[0].props.onClick();await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='直接帮顾客登记存酒')[0].props.onClick();await tick()
 const form=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-panel'))[0]
 const submit=()=>nodes(form,n=>n.tag==='button'&&text(n).trim()==='确认实物已收存')[0]
 assert.equal(submit().props.disabled,true)
 nodes(form,n=>n.tag==='select'&&n.props.onChange)[0].props.onChange({target:{value:'wine'}});await tick()
 nodes(form,n=>n.props?.['data-test-photo'])[0].props.onClick();await tick()
 assert.equal(submit().props.disabled,true)
 const consent=nodes(form,n=>n.tag==='input'&&n.props.type==='checkbox'&&n.props.required!==undefined)[0];consent.checked=true;consent.listeners.change({target:consent});await tick()
 assert.equal(submit().props.disabled,false)
 await form.props.onSubmit({preventDefault(){}});await tick()
 const writes=s.calls.filter(c=>c.method==='POST');assert.equal(writes.length,1);assert.equal(writes[0].url,'/api/v1/stores/A/wine-storage/registrations');assert.equal(writes[0].body.customerId,'customer');assert.equal(writes[0].body.rulesConfirmed,true);assert.equal(writes[0].body.custodianId,null);assert.equal(writes[0].body.lines[0].productId,'wine');assert.deepEqual(writes[0].body.lines[0].photos,['photo-id']);assert.match(text(s.root),/已完成实际收存/);s.unmount()
})

test('merchant reviews a pending customer request immediately without searching or selecting a customer',async()=>{
 const product={id:'wine',categoryId:'category',nameZh:'顾客提交啤酒',nameEn:'Beer',code:'WINE'},application={id:'pending-inbox',customerId:'inbox-customer',status:'pending',lines:[{id:'line',productId:'wine',nameZh:product.nameZh,nameEn:product.nameEn,quantity:12,opened:false,source:'customer',photos:[],note:''}],createdAt:new Date().toISOString(),reason:'',confirmation:null},person={id:'custodian',name:'保管员A',kind:'custodian',enabled:true,version:0}
 const data={...home,applications:[application],rules:[{id:'rule',categoryId:'category',name:'啤酒规则',months:3,version:1}],categories:[{id:'category',nameZh:'酒水'}]},lists={pending:[{application,customer:{id:'inbox-customer',displayName:'申请顾客',phone:'+6580000000'}}],people:[person]}
 const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>url.endsWith('/products/wine')?product:url.includes('/products')?[product]:options?.method==='POST'?{...application,status:'stored'}:{...data},lists);await tick()
 assert.match(text(s.root),/顾客提交 · 待审核/);assert.match(text(s.root),/申请顾客/);assert.match(text(s.root),/12/)
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='审核并核对收存')[0].props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-merchant-customer-search')).length,0)
 assert.ok(!s.calls.some(c=>c.url.includes('/customers')))
 nodes(s.root,n=>n.tag==='input'&&n.props.type==='number')[0].props['onUpdate:modelValue'](10);await tick()
 nodes(s.root,n=>n.props?.['data-test-photo'])[0].props.onClick();await tick()
 nodes(s.root,n=>n.tag==='textarea'&&n.props.required!==undefined)[0].props['onUpdate:modelValue']('实物核对为10瓶');await tick()
 const select=nodes(s.root,n=>n.tag==='select'&&nodes(n,o=>o.tag==='option'&&o.props.value===person.id).length)[0];select.props.onChange({target:{value:person.id}});await tick()
 const form=nodes(s.root,n=>n.tag==='form'&&nodes(n,b=>b.tag==='button'&&text(b).trim()==='确认实际收存').length)[0]
 await form.props.onSubmit({preventDefault(){}});await tick()
 const writes=s.calls.filter(c=>c.method==='POST');assert.equal(writes.length,1);assert.equal(writes[0].url,'/api/v1/stores/A/wine-storage/applications/pending-inbox/receive');assert.equal(writes[0].body.custodianId,person.id);assert.equal(writes[0].body.lines[0].quantity,10);assert.equal(application.lines[0].quantity,12);assert.equal('customerId'in writes[0].body,false);assert.match(text(s.root),/已完成实际收存/);s.unmount()
})

test('custodian management is permission gated and stores an optional local name without creating a login account',async()=>{
 const data={...home,permissions:[...home.permissions,'wine_storage.custodians.manage']},people=[]
 const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>options?.method==='POST'?(people.push({id:'new-person',...JSON.parse(options.body),version:0}),people.at(-1)):{...data},{people});await tick()
 const panel=nodes(s.root,n=>n.tag==='details'&&nodes(n,c=>c.tag==='summary'&&text(c).includes('酒妹／保管员管理')).length)[0];panel.props.onToggle({target:{open:true}});await tick()
 const form=nodes(panel,n=>n.tag==='form')[0];nodes(form,n=>n.tag==='input'&&n.props.maxlength==='100')[0].props['onUpdate:modelValue']('小兰');await tick();await form.props.onSubmit({preventDefault(){}});await tick()
 const writes=s.calls.filter(c=>c.method==='POST');assert.equal(writes.length,1);assert.equal(writes[0].url,'/api/v1/stores/A/wine-storage/custodians');assert.equal(writes[0].body.name,'小兰');assert.equal(writes[0].body.kind,'hostess');assert.equal(writes[0].body.version,null);assert.match(text(panel),/小兰/);s.unmount()
 const denied=mount(StaffPage,false);await tick();assert.doesNotMatch(text(denied.root),/酒妹／保管员管理/);assert.ok(!denied.calls.some(c=>c.url.includes('includeDisabled')));denied.unmount()
})

test('merchant rejection requires an explicit customer-visible reason and never posts a receipt',async()=>{
 const application={id:'reject-inbox',customerId:'customer',status:'pending',lines:[],createdAt:new Date().toISOString(),reason:'',confirmation:null},lists={pending:[{application,customer:{id:'customer',displayName:'待审核顾客',phone:'+6580000000'}}]}
 const s=mount(StaffPage,false,'zh-CN',{...home,applications:[application]},(url,options)=>url.includes('/products')?[]:options?.method==='POST'?{...application,status:'rejected'}:home,lists);await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='审核并核对收存')[0].props.onClick();await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='拒绝申请')[0].props.onClick();await tick()
 const form=nodes(s.root,n=>n.tag==='form'&&nodes(n,b=>b.tag==='button'&&text(b).trim()==='确认拒绝申请').length)[0];await form.props.onSubmit({preventDefault(){}});assert.equal(s.calls.filter(c=>c.method==='POST').length,0)
 nodes(form,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('尚未收到实物，请核对后重新申请');await tick();await form.props.onSubmit({preventDefault(){}});await tick()
 const writes=s.calls.filter(c=>c.method==='POST');assert.equal(writes.length,1);assert.equal(writes[0].url,'/api/v1/stores/A/wine-storage/applications/reject-inbox/reject');assert.equal(writes[0].body.reason,'尚未收到实物，请核对后重新申请');assert.match(text(s.root),/申请已拒绝/);s.unmount()
})

test('customer sees original and received counts separately and can enlarge original photos without writing',async()=>{
 const line={id:'line',productId:'wine',nameZh:'啤酒',nameEn:'Beer',quantity:12,opened:false,source:'customer',photos:['original-photo'],note:'原申请说明'},application={id:'application',customerId:'customer',status:'stored',lines:[line],reason:'实物核对10瓶',createdAt:new Date().toISOString(),confirmation:{lines:[{...line,lineId:'line',quantity:10,photos:['receipt-photo']}],reason:'实物核对10瓶'}},data={...home,applications:[application]}
 const s=mount(CustomerPage,true,'zh-CN',data);await tick();nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='我的记录')[0].props.onClick();await tick()
 assert.match(text(s.root),/原申请瓶数\s+12/);assert.match(text(s.root),/实际收存瓶数\s+10/);assert.match(text(s.root),/实物核对10瓶/);assert.match(text(s.root),/原申请说明/)
 await nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='放大查看照片')[0].props.onClick({currentTarget:{focus(){}}});await tick()
 const dialog=nodes(s.root,n=>n.props?.role==='dialog')[0];assert.ok(dialog);assert.equal(nodes(dialog,n=>n.tag==='img')[0].props.src,'/api/v1/public/stores/A/wine-storage/photos/original-photo')
 nodes(dialog,n=>n.tag==='button'&&text(n).trim()==='继续放大')[0].props.onClick();await tick();assert.ok(nodes(dialog,n=>String(n.props?.class).includes('is-zoomed')).length)
 nodes(dialog,n=>n.tag==='button'&&text(n).trim()==='关闭')[0].props.onClick();await tick();assert.equal(nodes(s.root,n=>n.props?.role==='dialog').length,0);assert.ok(s.calls.every(c=>c.method==='GET'));assert.equal(line.quantity,12);s.unmount()
})

test('merchant scans once to redeem and duplicate decoder callbacks cannot create a second command',async()=>{
 const item={id:'bottle',customerId:'customer',applicationId:'app',snapshot:{nameZh:'啤酒',nameEn:'Beer',timezone:'Asia/Singapore',receiptPhotos:[]},quantity:2,remaining:2,opened:false,status:'stored',expiresAt:new Date(Date.now()+86400000).toISOString()},done={id:'picked',customerId:'customer',origin:'customer',status:'completed',code:null,expiresAt:new Date(Date.now()+300000).toISOString(),completedAt:new Date().toISOString(),selections:[{itemId:'bottle',quantity:1}]},data={...home,items:[item],intents:[]}
 let resolve;const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>url.endsWith('/scan-deliver')?new Promise(r=>resolve=r):{...data});await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='帮顾客取酒')[0].props.onClick();await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='扫描顾客取酒码并核销')[0].props.onClick();await tick()
 const scanner=nodes(s.root,n=>n.props?.['data-storage-scan'])[0];scanner.props.onClick();scanner.props.onClick();await tick()
 assert.equal(s.calls.filter(c=>c.method==='POST').length,1);assert.equal(s.calls.find(c=>c.method==='POST').url,'/api/v1/stores/A/wine-storage/scan-deliver');assert.equal(s.calls.find(c=>c.method==='POST').body.code,'WS.'+'S'.repeat(43))
 data.intents=[done];resolve(done);await tick();assert.match(text(s.root),/扫码核销成功/);assert.match(text(s.root),/该取酒码已失效/)
 assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='确认实物已交付').length,0);assert.equal(nodes(s.root,n=>n.tag==='figure').length,0);assert.ok(!s.calls.some(c=>c.url.includes('/customers/')||c.url.endsWith('/deliver')));s.unmount()
})

test('customer code is automatically hidden after server completion when returning to the page',async()=>{
 const code='WS.'+'R'.repeat(43),item={id:'bottle',customerId:'customer',applicationId:'app',snapshot:{productId:'wine',nameZh:'啤酒',nameEn:'Beer',timezone:'Asia/Singapore',receiptPhotos:[]},quantity:2,remaining:2,opened:false,status:'stored',expiresAt:new Date(Date.now()+86400000).toISOString()},intent={id:'pickup',customerId:'customer',origin:'customer',status:'authorized',code,expiresAt:new Date(Date.now()+300000).toISOString(),selections:[{itemId:'bottle',quantity:1}]},data={...home,items:[item],intents:[]}
 const s=mount(CustomerPage,true,'zh-CN',data,(url,options)=>options?.method==='POST'?(data.intents=[{...intent,code:null}],intent):url.includes('/products')?[]:{...data});await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='我要取酒')[0].props.onClick();await tick();await nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='出码取酒')[0].props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-code']===code).length,1)
 data.intents=[{...intent,status:'completed',code:null,completedAt:new Date().toISOString()}];data.items=[{...item,remaining:1}]
 await Promise.all([...globalThis.document.listeners.get('visibilitychange')].map(fn=>fn()));await tick()
 assert.equal(nodes(s.root,n=>n.tag==='figure').length,0);assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='取消').length,0);assert.match(text(s.root),/该码已失效/);assert.equal(s.calls.filter(c=>c.method==='POST').length,1);s.unmount();assert.equal(globalThis.document.listeners.get('visibilitychange').size,0)
})


test('customer picker pages and filters on server and barcode chooses a product',async()=>{
 const product={id:'wine',categoryId:'cat',nameZh:'可寄存白酒',nameEn:'Spirit',code:'WINE'}
 const s=mount(CustomerPage,true,'zh-CN',home,url=>url.includes('/product-options')?{items:[product],categories:[{id:'cat',nameZh:'白酒',nameEn:'Spirits'}],hasMore:!url.includes('page=1')}:home);await tick()
 assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n).includes('选择 ＋')).length,1)
 nodes(s.root,n=>n.tag==='button'&&text(n)==='下一页')[0].props.onClick();await tick();assert.ok(s.calls.some(c=>c.url.includes('page=1')))
 const select=nodes(s.root,n=>n.tag==='select')[0];select.children.forEach(o=>o.selected=o.props?.value==='cat');select.listeners.change({target:select});await tick();assert.ok(s.calls.some(c=>c.url.includes('categoryId=cat')&&c.url.includes('page=0')))
 nodes(s.root,n=>n.tag==='button'&&text(n)==='扫瓶身条码选酒')[0].props.onClick();await tick();assert.equal(nodes(s.root,n=>n.props?.['data-barcode'])[0].props['data-auto-start'],true);nodes(s.root,n=>n.props?.['data-barcode'])[0].props.onClick();await tick()
 assert.ok(s.calls.some(c=>c.url.includes('barcode=00036000291452')));assert.match(text(s.root),/2. 填写瓶数并拍照/);assert.match(text(s.root),/已添加/)
})

test('unlisted barcode retains identifier in application note and requests wine name',async()=>{
 const s=mount(CustomerPage,true,'zh-CN',home,(url,options)=>url.includes('/product-options')?{items:[],categories:[],hasMore:false}:options?.method==='POST'?{id:'applied',lines:[],status:'pending'}:home);await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n)==='扫瓶身条码选酒')[0].props.onClick();await tick();nodes(s.root,n=>n.props?.['data-barcode'])[0].props.onClick();await tick();assert.match(text(s.root),/条码尚未收录/)
 const input=nodes(s.root,n=>n.tag==='input'&&n.props.maxlength==='200')[0];input.value='新酒';(input.listeners.input??input.props.onInput)({target:input});await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n)==='添加这款酒')[0].props.onClick();await tick();assert.match(nodes(s.root,n=>n.tag==='textarea')[0].value,/00036000291452/)
})


const catalogProducts=['wine-a','wine-b'].map((id,i)=>({id,categoryId:'cat',code:id,nameZh:'酒品'+(i+1),nameEn:'Wine '+(i+1),descriptionZh:'',descriptionEn:'',imageUrl:'',priceCents:100,currency:'SGD',taxMode:'inclusive',taxCategory:'not_applicable',taxRate:0,channels:['storage'],kind:'physical',unit:'瓶',status:'active',listingEnabled:true,listingVersion:0,version:0}))
const catalogActor={roles:['tenant_admin'],permissions:['catalog.view','catalog.manage','catalog.publish'],storeIds:['A']}
function catalogResponse(url,options){if(url.endsWith('/categories'))return[{id:'cat',code:'C',nameZh:'酒类',nameEn:'Wine',enabled:true,version:0,sortOrder:0}];if(url.endsWith('/products'))return catalogProducts;if(url.endsWith('/editor')){const body=options?.body?JSON.parse(options.body):null;return{product:body?{...catalogProducts[0],...body.product,version:1,listingVersion:1}:catalogProducts[0],barcodes:body?body.barcodes:['06956367311355']}}return home}
function button(root,label){return nodes(root,n=>n.tag==='button'&&text(n).trim()===label)[0]}
function typeField(input,value){input.value=value;(input.listeners.input??input.props.onInput)({target:input})}

test('catalog operation areas are separate and list hides publication and editor actions',async()=>{
 const s=mount(CatalogPage,false,'zh-CN',home,catalogResponse,{actor:catalogActor});await tick();assert.ok(button(s.root,'分类管理'));assert.ok(button(s.root,'上下架管理'));assert.equal(nodes(s.root,n=>n.tag==='form').length,0);assert.equal(button(s.root,'归档'),undefined)
 button(s.root,'分类管理').props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='h3'&&text(n)==='酒品1').length,0);assert.equal(button(s.root,'保存商品与条码'),undefined)
 button(s.root,'上下架管理').props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='form').length,0);assert.ok(button(s.root,'归档'));button(s.root,'归档').props.onClick();await tick();assert.equal(s.calls.filter(c=>c.method!=='GET').length,0);assert.ok(nodes(s.root,n=>n.props?.role==='dialog').length)
})

test('product and barcode inputs share one editor and one atomic save keeps exact 13 digits',async()=>{
 const s=mount(CatalogPage,false,'zh-CN',home,catalogResponse,{actor:catalogActor});await tick();button(s.root,'编辑').props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='form').length,1);assert.equal(nodes(s.root,n=>n.tag==='article').length,0);assert.equal(button(s.root,'分类管理'),undefined)
 const barcode=nodes(s.root,n=>n.tag==='input'&&n.props.inputmode==='numeric')[0];assert.equal(barcode.value,'06956367311355');typeField(barcode,'6956367311355');const name=nodes(s.root,n=>n.tag==='input'&&n.value==='酒品1')[0];typeField(name,'修改后的酒品');assert.equal(s.calls.filter(c=>c.method!=='GET').length,0)
 nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();const save=s.calls.find(c=>c.method==='PUT');assert.ok(save.url.endsWith('/products/wine-a/editor'));assert.deepEqual(save.body.barcodes,['6956367311355']);assert.deepEqual(save.body.expectedBarcodes,['06956367311355']);assert.equal(save.body.product.nameZh,'修改后的酒品');assert.equal(save.body.photos,undefined);assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.inputmode==='numeric')[0].value,'6956367311355');assert.match(text(s.root),/商品信息与条码已保存/)
})

test('failed atomic save retains product and barcode draft and retries exactly the same command',async()=>{
 const s=mount(CatalogPage,false,'zh-CN',home,catalogResponse,{actor:catalogActor});await tick();button(s.root,'编辑').props.onClick();await tick();const field=nodes(s.root,n=>n.tag==='input'&&n.props.inputmode==='numeric')[0];typeField(field,'6956367311355');await tick()
 const requests=[];globalThis.fetch=async(url,options)=>{requests.push(JSON.parse(options.body));return{ok:requests.length>1,status:requests.length===1?503:200,json:async()=>requests.length===1?{code:'TEMP',message:'Retry'}:{product:{...catalogProducts[0],version:1},barcodes:['6956367311355']}}}
 nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.equal(field.value,'6956367311355');assert.ok(field.props.disabled);assert.ok(sessionStorage.getItem('rpb.catalog.edit:/api/v1/stores/A/catalog:wine-a'));button(s.root,'恢复待确认操作').props.onClick();await tick();assert.deepEqual(requests[0],requests[1]);assert.equal(sessionStorage.getItem('rpb.catalog.edit:/api/v1/stores/A/catalog:wine-a'),null)
})

test('store managers edit only listing and cannot change product barcodes',async()=>{
 const actor={...catalogActor,roles:['store_manager']},s=mount(CatalogPage,false,'zh-CN',home,(url,options)=>options?.method==='POST'?{...catalogProducts[0],listingVersion:1}:catalogResponse(url,options),{actor});await tick();button(s.root,'编辑').props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.inputmode==='numeric')[0].props.disabled,true);assert.ok(!button(s.root,'＋ 添加条码'));nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();const post=s.calls.find(c=>c.method==='POST');assert.ok(post.url.endsWith('/listing'));assert.equal(post.body.barcodes,undefined)
})


test('cancel with unsaved product and barcode changes requires a deliberate choice',async()=>{
 const s=mount(CatalogPage,false,'zh-CN',home,catalogResponse,{actor:catalogActor});await tick();button(s.root,'编辑').props.onClick();await tick();typeField(nodes(s.root,n=>n.tag==='input'&&n.props.inputmode==='numeric')[0],'6956367311355');await tick();button(s.root,'取消编辑').props.onClick();await tick();assert.equal(nodes(s.root,n=>n.props?.role==='dialog').length,1);button(s.root,'继续编辑').props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.inputmode==='numeric')[0].value,'6956367311355');button(s.root,'取消编辑').props.onClick();await tick();button(s.root,'放弃修改').props.onClick();await tick();assert.ok(button(s.root,'分类管理'));assert.equal(s.calls.filter(c=>c.method!=='GET').length,0)
})


test('single barcode scan opens camera immediately and replaces the draft until product save',async()=>{
 const s=mount(CatalogPage,false,'zh-CN',home,catalogResponse,{actor:catalogActor});await tick();button(s.root,'编辑').props.onClick();await tick();assert.ok(!button(s.root,'＋ 添加条码'));assert.ok(!button(s.root,'扫描添加条码'));button(s.root,'扫码填写').props.onClick();await tick();const scan=nodes(s.root,n=>n.props?.['data-barcode'])[0];assert.equal(scan.props['data-auto-start'],true);scan.props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.inputmode==='numeric').length,1);assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.inputmode==='numeric')[0].value,'00036000291452');assert.equal(s.calls.filter(c=>c.method!=='GET').length,0)
})


test('unavailable original product is not backfilled as a selectable wine and cannot be received',async()=>{
 const application={id:'off-request',customerId:'customer',status:'pending',lines:[{id:'line',productId:'wine',nameZh:'未开启寄存商品',nameEn:'Off wine',quantity:1,opened:false,source:'customer',photos:[],note:''}],createdAt:new Date().toISOString(),reason:'',confirmation:null},data={...home,applications:[application],rules:[{id:'rule',categoryId:'category',name:'规则',months:3,version:1}],categories:[{id:'category',nameZh:'酒品'}]},lists={pending:[{application,customer:{id:'customer',displayName:'顾客',phone:'+6580000000'}}]}
 const s=mount(StaffPage,false,'zh-CN',data,url=>url.includes('/products')?[]:data,lists);await tick();const original=globalThis.fetch;globalThis.fetch=async(url,options)=>url.endsWith('/products/wine')?{ok:false,status:409,json:async()=>({code:'CATALOG_UNAVAILABLE',message:'unavailable'})}:original(url,options)
 button(s.root,'审核并核对收存').props.onClick();await tick();assert.match(text(s.root),/原选商品未开启寄存酒或已停用/);assert.equal(nodes(s.root,n=>n.tag==='option'&&n.props.value==='wine').length,0);assert.equal(button(s.root,'确认实际收存').props.disabled,true);assert.equal(s.calls.filter(c=>c.method==='POST').length,0)
})


test('eligible original wine outside the keyword result is verified by id before preserving selection',async()=>{
 const product={id:'wine',categoryId:'category',nameZh:'仍可寄存的酒',nameEn:'Eligible wine',code:'WINE'},application={id:'valid-request',customerId:'customer',status:'pending',lines:[{id:'line',productId:'wine',nameZh:'旧申请名称',nameEn:'Old name',quantity:1,opened:false,source:'customer',photos:[],note:''}],createdAt:new Date().toISOString(),reason:'',confirmation:null},data={...home,applications:[application],rules:[{id:'rule',categoryId:'category',name:'规则',months:3,version:1}],categories:[{id:'category',nameZh:'酒品'}]},lists={pending:[{application,customer:{id:'customer',displayName:'顾客',phone:'+6580000000'}}]}
 const s=mount(StaffPage,false,'zh-CN',data,url=>url.endsWith('/products/wine')?product:url.includes('/products')?[]:data,lists);await tick();button(s.root,'审核并核对收存').props.onClick();await tick();assert.ok(s.calls.some(c=>c.url.endsWith('/products/wine')));assert.equal(nodes(s.root,n=>n.tag==='option'&&n.props.value==='wine').length,1);assert.match(text(s.root),/仍可寄存的酒/);assert.doesNotMatch(text(s.root),/原选商品未开启寄存酒或已停用/)
})

test('legacy multiple barcodes require explicit selection and preserve the full save snapshot',async()=>{
 const old=['06956367311355','036000291452'],s=mount(CatalogPage,false,'zh-CN',home,(url,options)=>url.endsWith('/editor')&&(!options?.method||options.method==='GET')?{product:catalogProducts[0],barcodes:old}:catalogResponse(url,options),{actor:catalogActor});await tick();button(s.root,'编辑').props.onClick();await tick();assert.match(text(s.root),/只能保留一个条码/);nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.equal(s.calls.filter(c=>c.method!=='GET').length,0);nodes(s.root,n=>n.tag==='button'&&text(n).includes('保留此条码'))[1].props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.inputmode==='numeric').length,1);nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();const save=s.calls.find(c=>c.method==='PUT');assert.deepEqual(save.body.barcodes,['036000291452']);assert.deepEqual(save.body.expectedBarcodes,old)
})

test('new product always has one editable optional barcode field without add buttons',async()=>{
 const s=mount(CatalogPage,false,'zh-CN',home,catalogResponse,{actor:catalogActor});await tick();button(s.root,'＋ 新增商品').props.onClick();await tick();const fields=nodes(s.root,n=>n.tag==='input'&&n.props.inputmode==='numeric');assert.equal(fields.length,1);assert.equal(fields[0].value,'');assert.ok(button(s.root,'扫码填写'));assert.ok(!button(s.root,'＋ 添加条码'))
})

test('customer wallet hides unopened empty storage but retains closed product history',async()=>{
 const empty={...home,newBusinessEnabled:false,items:[],applications:[],intents:[],events:[]};const hidden=mount(Summary,true,'zh-CN',empty);await tick();assert.equal(nodes(hidden.root,n=>n.tag==='a').length,0);hidden.unmount();
 for(const kind of ['items','applications','intents','events']){const shown=mount(Summary,true,'zh-CN',{...empty,[kind]:kind==='items'?[{id:'old',status:'ended',remaining:0}]:[{id:'old',status:'completed'}]});await tick();assert.equal(nodes(shown.root,n=>n.tag==='a').length,1);shown.unmount()}
})

 test('universal scanner hands a customer to bottle collection without scanning again',async()=>{
 const person={id:'handoff-customer',displayName:'扫码顾客',phone:'80000000'}
 setScanHandoff({store:'A',actor:'handoff-staff',target:'storage',code:'WI.'+'A'.repeat(43),customerId:person.id,task:'withdraw'})
 const s=mount(StaffPage,false,'zh-CN',home,url=>url.endsWith('/customers/'+person.id)?person:home,{actor:{accountId:'handoff-staff',roles:[],permissions:home.permissions}});await tick()
 assert.match(text(s.root),/扫码顾客/);assert.equal(nodes(s.root,n=>n.tag==='button'&&n.props['aria-pressed']===true&&text(n).trim()==='帮顾客取酒').length,1)
 assert.equal(s.calls.filter(c=>c.url.endsWith('/customers/'+person.id)).length,1);assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
 })


test('closed merchant storage opens records and removes new-receipt controls while retaining pickup history',async()=>{
 const s=mount(StaffPage,false,'zh-CN',{...home,newBusinessEnabled:false});await tick()
 assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='帮顾客存酒').length,0)
 assert.doesNotMatch(text(s.root),/直接帮顾客登记存酒/)
 assert.match(text(s.root),/存取酒记录/)
 assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
})


test('pickup cards replace duplicate selection steps with bounded plus and minus controls on both sides',async()=>{
 const item={id:'stepper-bottle',applicationId:'application',customerId:'customer',snapshot:{nameZh:'青岛啤酒',nameEn:'Beer',timezone:'Asia/Singapore',receiptPhotos:[]},quantity:2,remaining:2,opened:false,status:'stored',location:'W',label:'08',expiresAt:new Date(Date.now()+86400000).toISOString()},person={id:'customer',displayName:'测试顾客',phone:'8000'}
 for(const customer of [true,false]){
  const s=mount(customer?CustomerPage:StaffPage,customer,'zh-CN',{...home,items:[item]},url=>url.includes('/customers?')?[person]:url.includes('/product-options')?{items:[],categories:[],hasMore:false}:{...home,items:[item]})
  await tick()
  if(!customer){const form=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-merchant-customer-search'))[0];nodes(form,n=>n.tag==='input')[0].props.onInput({target:{value:'8000'}});await form.props.onSubmit({preventDefault(){}});await tick();nodes(s.root,n=>n.tag==='button'&&text(n).includes('测试顾客'))[0].props.onClick();await tick()}
  nodes(s.root,n=>n.tag==='button'&&text(n).trim()===(customer?'我要取酒':'帮顾客取酒'))[0].props.onClick();await tick()
  assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox').length,0)
  assert.doesNotMatch(text(s.root),/下一步：核对领取清单|请先选择要取的酒品和有效瓶数/)
  const minus=()=>nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='减少瓶数')[0],plus=()=>nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='增加瓶数')[0],input=()=>nodes(s.root,n=>n.tag==='input'&&n.props.type==='number')[0]
  assert.equal(minus().props.disabled,true);assert.equal(input().props.value,1);plus().props.onClick();await tick();assert.equal(input().props.value,2);assert.equal(plus().props.disabled,true);minus().props.onClick();await tick();assert.equal(input().props.value,1)
  assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n).trim()===(customer?'出码取酒':'生成顾客确认码')).length,1)
  s.unmount()
 }
})


test('opened bottles lock both steppers to one and remaining-stock refresh clamps the customer quantity',async()=>{
 const item={id:'limit-bottle',applicationId:'application',customerId:'customer',snapshot:{nameZh:'啤酒',nameEn:'Beer',timezone:'Asia/Singapore'},quantity:3,remaining:3,opened:false,status:'stored',expiresAt:new Date(Date.now()+86400000).toISOString()},data={...home,items:[item]}
 const s=mount(CustomerPage,true,'zh-CN',data,url=>url.includes('/product-options')?{items:[],categories:[],hasMore:false}:{...data});await tick();nodes(s.root,n=>n.tag==='button'&&text(n)==='我要取酒')[0].props.onClick();await tick()
 const input=()=>nodes(s.root,n=>n.tag==='input'&&n.props.type==='number')[0]
 input().props.onInput({target:{value:'3'}});await tick();assert.equal(input().props.value,3)
 data.items=[{...item,remaining:1}];await nodes(s.root,n=>n.tag==='button'&&text(n)==='刷新')[0].props.onClick();await tick();assert.equal(input().props.value,1);assert.equal(nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='增加瓶数')[0].props.disabled,true)
 data.items=[{...item,opened:true}];await nodes(s.root,n=>n.tag==='button'&&text(n)==='刷新')[0].props.onClick();await tick();assert.equal(input().props.value,1);assert.equal(input().props.max,1);assert.equal(input().props.disabled,true);s.unmount()
})

test('merchant corner binds quantity and customer, prevents duplicate submits, then waits for customer confirmation',async()=>{
 const item={id:'merchant-quick',applicationId:'application',customerId:'customer',snapshot:{nameZh:'啤酒',nameEn:'Beer',timezone:'Asia/Singapore'},quantity:3,remaining:3,opened:false,status:'stored',expiresAt:new Date(Date.now()+86400000).toISOString()},person={id:'customer',displayName:'快捷取酒顾客',phone:'8000'},intent={id:'merchant-quick-intent',customerId:'customer',origin:'staff',status:'awaiting_authorization',code:'WS.'+'M'.repeat(43),expiresAt:new Date(Date.now()+300000).toISOString(),selections:[{itemId:item.id,quantity:2}]},data={...home,items:[item],intents:[]}
 let resolve
 const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>url.includes('/customers?')?[person]:options?.method==='POST'?new Promise(r=>resolve=r):{...data,intents:[...data.intents]});await tick()
 const form=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-merchant-customer-search'))[0];nodes(form,n=>n.tag==='input')[0].props.onInput({target:{value:'8000'}});await form.props.onSubmit({preventDefault(){}});await tick();nodes(s.root,n=>n.tag==='button'&&text(n).includes('快捷取酒顾客'))[0].props.onClick();await tick();nodes(s.root,n=>n.tag==='button'&&text(n)==='帮顾客取酒')[0].props.onClick();await tick()
 nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='增加瓶数')[0].props.onClick();await tick()
 const create=nodes(s.root,n=>n.tag==='button'&&text(n)==='生成顾客确认码')[0];const pending=create.props.onClick();create.props.onClick();await tick()
 assert.equal(s.calls.filter(c=>c.method==='POST').length,1);const command=s.calls.find(c=>c.method==='POST');assert.deepEqual(command.body.selections,intent.selections);assert.equal(command.body.customerId,'customer');assert.ok(command.body.idempotencyKey)
 data.intents=[{...intent,code:null}];resolve(intent);await pending;await tick();assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-code']===intent.code).length,1);assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='确认实物已交付').length,0)
 data.intents=[{...intent,code:null,status:'authorized'}];await Promise.all([...globalThis.document.listeners.get('visibilitychange')].map(fn=>fn()));await tick();assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='确认实物已交付').length,1);assert.equal(nodes(s.root,n=>n.tag==='figure').length,0);assert.equal(s.calls.filter(c=>c.method==='POST').length,1);s.unmount()
})


test('pending legacy batch codes remain recoverable after removing the batch creation UI',async()=>{
 const item={id:'legacy-one',applicationId:'application',customerId:'customer',snapshot:{nameZh:'啤酒',nameEn:'Beer',timezone:'Asia/Singapore'},quantity:3,remaining:3,opened:false,status:'stored',expiresAt:new Date(Date.now()+86400000).toISOString()},other={...item,id:'legacy-two'},person={id:'customer',displayName:'旧单顾客',phone:'8000'},intent={id:'legacy-batch',customerId:'customer',origin:'staff',status:'awaiting_authorization',code:'WS.'+'L'.repeat(43),expiresAt:new Date(Date.now()+300000).toISOString(),selections:[{itemId:item.id,quantity:1},{itemId:other.id,quantity:1}]},data={...home,items:[item,other],intents:[{...intent,code:null}]}
 globalThis.sessionStorage.setItem('rpb.storage.pending:false:A',JSON.stringify({path:'/intents',body:{customerId:'customer',selections:intent.selections,idempotencyKey:'original-batch-key'}}))
 const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>url.endsWith('/customers/customer')?person:options?.method==='POST'?intent:data);await tick();await nodes(s.root,n=>n.tag==='button'&&text(n)==='查询上次操作结果')[0].props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-code']===intent.code).length,1);assert.equal(s.calls.filter(c=>c.method==='POST').length,1);assert.equal(s.calls.find(c=>c.method==='POST').body.idempotencyKey,'original-batch-key');assert.doesNotMatch(text(s.root),/下一步：核对领取清单/);s.unmount()
})


test('receipt defaults to the front desk and accepts an empty shelf or an explicitly cleared location',async()=>{
 const product={id:'wine',categoryId:'category',nameZh:'白酒',nameEn:'Spirit',code:'WINE'},data={...home,rules:[{id:'rule',categoryId:'category',name:'三个月',months:3,version:1}],categories:[{id:'category',nameZh:'白酒'}]},application={id:'',customerId:'customer',status:'pending',lines:[{id:'line',productId:'wine',nameZh:'白酒',nameEn:'Spirit',quantity:1,opened:false,source:'customer',photos:[],note:''}]}
 for(const cleared of [false,true]){
  const s=mount(ReceiveForm,false,'zh-CN',data,(url,options)=>url.endsWith('/products/wine')?product:url.includes('/products')?[product]:options?.method==='POST'?{id:'received'}:data, {props:{api:storageApi('A',false),application,home:data,disabled:false,direct:true}});await tick()
  const label=nodes(s.root,n=>n.tag==='label'&&text(n).includes('存放位置（选填）'))[0],select=nodes(label,n=>n.tag==='select')[0];assert.equal(select.props.value,'front-desk')
  if(cleared){select.children.forEach(o=>o.selected=o.props?.value==='');select.props.onChange({target:{value:''}});await tick()}
  assert.ok(nodes(s.root,n=>n.tag==='label'&&text(n).includes('仓位（选填）')).length)
  nodes(s.root,n=>n.props?.['data-test-photo'])[0].props.onClick();await tick();const consent=nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox').at(-1);consent.checked=true;consent.listeners.change({target:consent});await tick()
  await nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();const write=s.calls.find(c=>c.url.endsWith('/registrations')&&c.method==='POST');assert.ok(write);assert.equal(write.body.lines[0].storageLocationId,cleared?null:'front-desk');assert.equal(write.body.lines[0].location,'');s.unmount()
 }
})

test('tenant administrator can rename the default location and add a reusable place',async()=>{
 const locations=[{id:'front-desk',name:'前台寄存处',defaultLocation:true,version:0}],s=mount(LocationsPanel,false,'zh-CN',home,(url,options)=>{const body=options?.body&&JSON.parse(options.body);if(url.endsWith('/locations/front-desk')){locations[0]={...locations[0],name:body.name,version:1};return locations[0]}if(url.endsWith('/locations')&&options?.method==='POST'){const row={id:'cabinet',name:body.name,defaultLocation:false,version:0};locations.push(row);return row}return home},{locations,props:{api:storageApi('A',false),disabled:false}})
 nodes(s.root,n=>n.tag==='details')[0].props.onToggle({target:{open:true}});await tick();assert.match(text(s.root),/前台寄存处/)
 nodes(s.root,n=>n.tag==='button'&&text(n)==='修改位置')[0].props.onClick();await tick();let input=nodes(s.root,n=>n.tag==='input')[0];input.value='前台冷藏柜';input.listeners.input({target:input});await tick();await nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick()
 let write=s.calls.filter(c=>c.method==='POST')[0];assert.equal(write.url,'/api/v1/stores/A/wine-storage/locations/front-desk');assert.equal(write.body.version,0);assert.match(text(s.root),/前台冷藏柜/)
 input=nodes(s.root,n=>n.tag==='input')[0];input.value='酒柜 A';input.listeners.input({target:input});await tick();await nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();write=s.calls.filter(c=>c.method==='POST')[1];assert.equal(write.body.version,null);assert.match(text(s.root),/酒柜 A/);s.unmount()
})

test('location maintenance appears only for a tenant administrator while normal staff can select on receipt',async()=>{
 for(const role of ['store_staff','store_manager','tenant_admin']){
  const s=mount(StaffPage,false,'zh-CN',home,undefined,{actor:{accountId:'actor',roles:[role],permissions:home.permissions}});await tick();assert.equal(nodes(s.root,n=>n.tag==='summary'&&text(n)==='常用存放位置管理').length,role==='tenant_admin'?1:0);s.unmount()
 }
})


test('mode settings are tenant-admin only and submit scoped versioned selection',async()=>{
 for(const role of ['tenant_admin','store_manager']){
  const data={...home,operationMode:'customer_verification',settingsVersion:0},s=mount(StaffPage,false,'zh-CN',data,undefined,{actor:{accountId:'actor',roles:[role],permissions:home.permissions}});await tick()
  const save=button(s.root,'保存存取酒模式');assert.equal(!!save,role==='tenant_admin')
  if(save){
   const select=nodes(s.root,n=>n.tag==='select'&&n.children?.some(c=>c.props?.value==='staff_registration'))[0]
   select.children.forEach(o=>o.selected=o.props?.value==='staff_registration');select.listeners.change({target:select});await tick()
   const form=nodes(s.root,n=>n.tag==='form'&&nodes(n,k=>k===save).length)[0];await form.props.onSubmit({preventDefault(){}});await tick()
   const post=s.calls.find(c=>c.method==='POST');assert.ok(post.url.endsWith('/wine-storage/settings'));assert.equal(post.body.operationMode,'staff_registration');assert.equal(post.body.version,0)
  }
  s.unmount()
 }
})
test('direct mode requires explicit handover confirmation without customer codes and prevents duplicate submissions',async()=>{
 const item={id:'direct-bottle',applicationId:'a',customerId:'customer',snapshot:{nameZh:'啤酒',nameEn:'Beer',timezone:'Asia/Singapore'},quantity:3,remaining:3,opened:false,status:'stored',expiresAt:new Date(Date.now()+86400000).toISOString()},person={id:'customer',displayName:'直接取酒顾客',phone:'8000'},data={...home,operationMode:'staff_registration',settingsVersion:1,items:[item],intents:[]}
 let resolve
 const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>url.includes('/customers?')?[person]:options?.method==='POST'?new Promise(r=>resolve=r):{...data});await tick()
 const form=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-merchant-customer-search'))[0];nodes(form,n=>n.tag==='input')[0].props.onInput({target:{value:'8000'}});await form.props.onSubmit({preventDefault(){}});await tick();nodes(s.root,n=>n.tag==='button'&&text(n).includes('直接取酒顾客'))[0].props.onClick();await tick();button(s.root,'帮顾客取酒').props.onClick();await tick()
 assert.equal(button(s.root,'扫描顾客取酒码并核销'),undefined);assert.equal(button(s.root,'生成顾客确认码'),undefined)
 nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='增加瓶数')[0].props.onClick();await tick();button(s.root,'登记取酒').props.onClick();await tick();assert.equal(s.calls.filter(c=>c.method==='POST').length,0)
 const confirm=button(s.root,'确认实物已交付并扣减');confirm.props.onClick();confirm.props.onClick();await tick()
 const post=s.calls.filter(c=>c.method==='POST');assert.equal(post.length,1);assert.ok(post[0].url.endsWith('/direct-deliver'));assert.equal(post[0].body.customerId,'customer');assert.deepEqual(post[0].body.selections,[{itemId:item.id,quantity:2}]);assert.ok(post[0].body.idempotencyKey)
 data.items=[{...item,remaining:1}];resolve({id:'done',customerId:'customer',origin:'staff',status:'completed',code:null,selections:post[0].body.selections});await tick();assert.match(text(s.root),/已完成实物交付/);assert.equal(nodes(s.root,n=>n.tag==='figure').length,0);s.unmount()
})
test('customer direct mode shows staff service notice and records without new self service forms',async()=>{
 const s=mount(CustomerPage,true,'zh-CN',{...home,operationMode:'staff_registration'});await tick();assert.match(text(s.root),/无需您扫码核销/)
 button(s.root,'我要取酒').props.onClick();await tick();assert.equal(button(s.root,'出码取酒'),undefined);assert.match(text(s.root),/联系门店办理/)
 button(s.root,'我的记录').props.onClick();await tick();assert.doesNotMatch(text(s.root),/无需您扫码核销/);s.unmount()
})
