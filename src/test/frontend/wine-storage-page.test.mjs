import {test,before,after,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {parse,compileScript} from '@vue/compiler-sfc'
import {createRenderer,h,nextTick,reactive} from 'vue'
import {createI18n} from 'vue-i18n'
import {routeLocationKey,routerKey} from 'vue-router'
import {build} from 'esbuild'
import {readFile,mkdir,writeFile} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
let Page,Summary,CustomerPage,StaffPage
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
 const result=await build({stdin:{contents:"export {default as CustomerPage} from './src/modules/wine-storage/WineStorageCustomerPage.vue';export {default as StaffPage} from './src/modules/wine-storage/WineStorageStaffPage.vue';export {default as Page} from './src/modules/wine-storage/WineStoragePage.vue';export {default as Summary} from './src/modules/wine-storage/WalletStorageSummary.vue'",resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router','vue-i18n','pinia'],plugins:[{name:'storage-page-test',setup(b){b.onLoad({filter:/\.css$/},()=>({contents:'',loader:'js'}));b.onLoad({filter:/\.vue$/},async({path})=>{if(path.endsWith('StoragePhotos.vue'))return{contents:"import {h} from 'vue';export default {props:['modelValue'],emits:['update:modelValue'],setup:(p,{emit})=>()=>h('button',{type:'button','data-test-photo':true,onClick:()=>emit('update:modelValue',['photo-id'])},'test-photo')}",loader:'js'};if(path.endsWith('WalletQrDisplay.vue'))return{contents:"import {h} from 'vue';export default {props:['value'],setup:p=>()=>h('figure',{'data-code':p.value})}",loader:'js'};if(path.endsWith('WalletQrScanner.vue'))return{contents:"import {h} from 'vue';export default {props:['disabled'],emits:['code'],setup:(p,{emit})=>()=>h('button',{'data-storage-scan':true,disabled:p.disabled,onClick:()=>emit('code','WS.'+'S'.repeat(43))},'test-storage-scan')}",loader:'js'};if(/WalletCustomerLogin\.vue$/.test(path))return{contents:"export default {render(){return null}}",loader:'js'};const{descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:'storage-test-'+path,inlineTemplate:true}).content,loader:'ts'}})}}]})
 const dir='target/wine-storage-page-test';await mkdir(dir,{recursive:true});await writeFile(dir+'/entry.mjs',result.outputFiles[0].contents);({Page,Summary,CustomerPage,StaffPage}=await import(pathToFileURL(process.cwd()+'/'+dir+'/entry.mjs').href+'?'+Date.now()))
})
after(()=>{globalThis.fetch=originalFetch;globalThis.sessionStorage=originalStorage;globalThis.document=originalDocument;globalThis.Document=originalDocumentClass;globalThis.ShadowRoot=originalShadowRoot})
function mount(component,customer,locale='zh-CN',data=home,respond,lists={}){
 const calls=[],root={children:[]};globalThis.fetch=async(url,options)=>{calls.push({url,method:options?.method??'GET',body:typeof options?.body==='string'?JSON.parse(options.body):undefined});return{ok:true,json:async()=>url.endsWith('/pending-applications')?[...(lists.pending??[])]:url.includes('/custodians')&&(!options?.method||options.method==='GET')?[...(lists.people??[])]:respond?respond(url,options):url.includes('/products')?[]:data}}
 const route=reactive({params:{storeId:'A'},hash:'',query:{}});const app=renderer.createApp({render:()=>h(component,{customer,store:'A'})});app.use(createI18n({legacy:false,locale,messages:{}}));app.provide(routeLocationKey,route);app.provide(routerKey,{push:async target=>{if(target?.query)route.query=target.query},replace:async target=>{if(target?.hash!==undefined)route.hash=target.hash}});app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:p.to},slots.default?.())});app.mount(root);mounted.add(app);return{root,calls,unmount:()=>{app.unmount();mounted.delete(app)}}
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
test('customer reviews their selected bottles before one scoped command generates a code, without selecting a customer',async()=>{
 const item={id:'my-bottle',applicationId:'a',customerId:'already-authenticated',snapshot:{productId:'p',code:'WINE',nameZh:'我的白酒',nameEn:'My spirit',categoryId:'c',rule:{name:'6个月',months:6,version:1},timezone:'Asia/Singapore',note:'',applicationPhotos:[],receiptPhotos:[]},quantity:3,remaining:2,opened:false,source:'customer',location:'',label:'',receivedAt:new Date().toISOString(),expiresAt:new Date(Date.now()+86400000).toISOString(),status:'stored'}
 const code='WS.'+'A'.repeat(43),data={...home,items:[item]},intent={id:'intent',status:'authorized',origin:'customer',expiresAt:new Date(Date.now()+300000).toISOString(),selections:[{itemId:item.id,quantity:1}],code}
 const s=mount(CustomerPage,true,'zh-CN',data,(url,options)=>options?.method==='POST'?(data.intents=[{...intent,code:null}],intent):url.includes('/products')?[]:{...data});await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='我要取酒')[0].props.onClick();await tick()
 const next=()=>nodes(s.root,n=>n.tag==='button'&&text(n).includes('核对领取清单'))[0]
 assert.equal(next().props.disabled,true)
 nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0].props.onChange({target:{checked:true}});await tick()
 assert.equal(next().props.disabled,false);next().props.onClick();await tick()
 assert.equal(s.calls.filter(c=>c.method==='POST').length,0)
 assert.match(text(s.root),/核对领取清单/)
 await nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='生成取酒码')[0].props.onClick();await tick()
 const writes=s.calls.filter(c=>c.method==='POST');assert.equal(writes.length,1);assert.equal(writes[0].url,'/api/v1/public/stores/A/wine-storage/intents');assert.deepEqual(writes[0].body.selections,[{itemId:item.id,quantity:1}]);assert.equal('customerId'in writes[0].body,false)
 assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-code']===code).length,1);assert.match(text(s.root),/取酒码已生成/)
 globalThis.fetch=async()=>({ok:false,status:401,json:async()=>({message:'Sign in'})})
 await nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='刷新')[0].props.onClick();await tick()
 globalThis.fetch=async()=>({ok:true,json:async()=>({...home,items:[],intents:[]})})
 await nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='刷新')[0].props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-code']===code).length,0)
 s.unmount()
})
test('customer selects a catalog wine, adds photos and submits the application as the signed-in customer',async()=>{
 const product={id:'wine',nameZh:'可寄存白酒',nameEn:'Spirit',code:'WINE'},s=mount(CustomerPage,true,'zh-CN',home,(url,options)=>url.includes('/products')?[product]:options?.method==='POST'?{id:'applied',lines:[],status:'pending'}:home);await tick()
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
 nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0].props.onChange({target:{checked:true}});await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).includes('核对领取清单'))[0].props.onClick();await tick()
 await nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='生成顾客确认码')[0].props.onClick();await tick()
 const writes=s.calls.filter(c=>c.method==='POST');assert.equal(writes.length,1);assert.equal(writes[0].body.customerId,'customer');assert.deepEqual(writes[0].body.selections,[{itemId:item.id,quantity:1}])
 assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='确认实物已交付').length,0)
 data.intents=[{...intent,status:'authorized'}]
 await nodes(s.root,n=>n.tag==='button'&&text(n)==='刷新顾客授权状态')[0].props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='确认实物已交付').length,1)
 assert.equal(s.calls.filter(c=>c.url.endsWith('/deliver')).length,0);s.unmount()
})

test('merchant directly registers bottles for the chosen customer with photos and explicit attestation',async()=>{
 const product={id:'wine',categoryId:'category',nameZh:'白酒',nameEn:'Spirit',code:'WINE'},data={...home,applications:[],rules:[{id:'rule',categoryId:'category',name:'保存三个月',months:3,version:1}],categories:[{id:'category',nameZh:'白酒'}]}
 const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>url.includes('/customers?')?[{id:'customer',displayName:'甲顾客',phone:'+6580000000'}]:url.includes('/products')?[product]:options?.method==='POST'?{id:'registered',customerId:'customer',status:'stored',lines:[]}:{...data});await tick()
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
 const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>url.includes('/products')?[product]:options?.method==='POST'?{...application,status:'stored'}:{...data},lists);await tick()
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
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='我要取酒')[0].props.onClick();await tick();nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0].props.onChange({target:{checked:true}});await tick();nodes(s.root,n=>n.tag==='button'&&text(n).includes('核对领取清单'))[0].props.onClick();await tick();await nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='生成取酒码')[0].props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-code']===code).length,1)
 data.intents=[{...intent,status:'completed',code:null,completedAt:new Date().toISOString()}];data.items=[{...item,remaining:1}]
 await Promise.all([...globalThis.document.listeners.get('visibilitychange')].map(fn=>fn()));await tick()
 assert.equal(nodes(s.root,n=>n.tag==='figure').length,0);assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='取消').length,0);assert.match(text(s.root),/该码已失效/);assert.equal(s.calls.filter(c=>c.method==='POST').length,1);s.unmount();assert.equal(globalThis.document.listeners.get('visibilitychange').size,0)
})
