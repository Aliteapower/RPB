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
let ActivationWorkspace,EmailSettings,Page,Summary,CustomerPage,StaffPage,CatalogPage,useMerchantStorage,useAuthSessionStore,setScanHandoff,ReceiveForm,LocationsPanel,storageApi
const mounted=new Set()
afterEach(()=>{for(const app of mounted)app.unmount();mounted.clear();for(const role of ['true','false'])globalThis.sessionStorage?.removeItem(`rpb.storage.pending:${role}:A`)})
const originalWindow=globalThis.window;const originalFetch=globalThis.fetch,originalStorage=globalThis.sessionStorage,originalDocument=globalThis.document,originalDocumentClass=globalThis.Document,originalShadowRoot=globalThis.ShadowRoot
const renderer=createRenderer({createElement:tag=>({tag,tagName:tag.toUpperCase(),children:[],props:{},value:'',get options(){return this.children.filter(c=>c.tag==='option')},listeners:{},addEventListener(name,fn){this.listeners[name]=fn},removeEventListener(){},setAttribute(){},removeAttribute(){},getRootNode(){return globalThis.document}}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>{n.props[k]=v;if(k==='value'){n._value=v;n.value=v}},insert:(n,p,anchor)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=anchor?p.children.indexOf(anchor):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:n=>{const c=n.parent?.children||[];return c[c.indexOf(n)+1]||null}})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>[n.text||'',...(n.children||[]).map(text)].join(' ')
const tick=async()=>{for(let n=0;n<12;n++){await Promise.resolve();await nextTick()}}
const home={timezone:'Asia/Singapore',storeName:'QA Store',stores:[{id:'A',name:'QA Store'}],newBusinessEnabled:true,permissions:['wine_storage.view','wine_storage.receive','wine_storage.deliver','wine_storage.rules.manage'],rules:[],categories:[],items:[],events:[],intents:[],applications:[{id:'application',customerId:'customer',status:'pending',lines:[],confirmation:null,reason:'',createdAt:'2026-10-25T10:00:00Z'}]}
test('merchant customer query directly selects a unique result without a second choice',async()=>{
 const person={id:'unique-storage',displayName:'唯一存酒顾客',phone:'81234567'};const s=mount(StaffPage,false,'zh-CN',home,url=>url.includes('/customers?')?[person]:url.includes('/product-options')?{items:[],categories:[],hasMore:false}:home);await tick();const form=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-merchant-customer-search'))[0];nodes(form,n=>n.tag==='input')[0].props.onInput({target:{value:'81234567'}});await tick();await form.props.onSubmit({preventDefault(){}});await tick();assert.match(text(s.root),/当前办理顾客/);assert.match(text(s.root),/唯一存酒顾客/);assert.ok(button(s.root,'更换顾客'));assert.ok(button(s.root,'清除顾客'));s.unmount()
})
test('merchant query keeps multiple customer results explicit and clearing removes the selected context',async()=>{
 const people=[{id:'a',displayName:'匹配顾客甲',phone:'81234567'},{id:'b',displayName:'匹配顾客乙',phone:'91234567'}];const s=mount(StaffPage,false,'zh-CN',home,url=>url.includes('/customers?')?people:home);await tick();const form=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-merchant-customer-search'))[0];nodes(form,n=>n.tag==='input')[0].props.onInput({target:{value:'匹配'}});await form.props.onSubmit({preventDefault(){}});await tick();assert.equal(button(s.root,'更换顾客'),undefined);nodes(s.root,n=>n.tag==='button'&&text(n).includes('匹配顾客乙'))[0].props.onClick();await tick();assert.ok(button(s.root,'更换顾客'));assert.match(text(s.root),/匹配顾客乙/);button(s.root,'清除顾客').props.onClick();await tick();assert.equal(button(s.root,'更换顾客'),undefined);assert.equal(nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-merchant-customer-search'))[0].children.flatMap(n=>nodes(n,x=>x.tag==='input'))[0].props.value,'');s.unmount()
})
test('merchant customer lookup ignores late responses after editing or moving to reports',async()=>{
 let finish;const s=mount(StaffPage,false,'zh-CN',home,url=>url.includes('/customers?')?new Promise(r=>finish=r):home);await tick();const form=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-merchant-customer-search'))[0];nodes(form,n=>n.tag==='input')[0].props.onInput({target:{value:'81234567'}});const work=form.props.onSubmit({preventDefault(){}});await tick();s.route.query={task:'records'};await tick();finish([{id:'late',displayName:'迟到顾客',phone:'81234567'}]);await work;await tick();assert.doesNotMatch(text(s.root),/迟到顾客/);assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
})
test('merchant storage returns to shared staff home unless the wallet is explicitly enabled',async()=>{
 for(const walletEnabled of [undefined,false,true]){
  const s=mount(StaffPage,false,'en-SG',{...home,walletEnabled});await tick()
  assert.equal(nodes(s.root,n=>n.tag==='a'&&n.props.href==='/stores/A/wallet').length,walletEnabled===true?1:0)
  assert.equal(nodes(s.root,n=>n.tag==='a'&&n.props.href==='/stores/A/staff').length,walletEnabled===true?0:1)
  s.unmount()
 }
})
const inventory={items:[{id:'inventory-item',customerId:'person',customerName:'张先生',customerPhone:'+6591234567',nameZh:'茅台',nameEn:'Moutai',quantity:5,remaining:2,opened:false,receivedAt:'2026-01-01T00:00:00Z',expiresAt:'2026-02-01T00:00:00Z',status:'expired',locationName:'前台',slot:'A柜',label:'TAG-1',custodianName:'小兰'}],summary:{customerCount:3,bottleCount:12,openedBottleCount:1,expiredBottleCount:2,itemCount:8},page:0,size:50,hasMore:true}
test('explicit merchant history suppresses administrator customer creation even while the product is active',async()=>{
 const actor={accountId:'history-admin',roles:['tenant_admin'],permissions:[...home.permissions,'tenant.admin.manage'],storeIds:['A']}
 const s=mount(StaffPage,false,'en-SG',home,undefined,{actor,query:{task:'records',history:'1'}});await tick()
 assert.equal(button(s.root,'＋'),undefined);assert.equal(button(s.root,'Settings'),undefined)
 assert.equal(nodes(s.root,n=>n.tag==='button'&&n.props['aria-pressed']===true&&text(n).trim()==='Reports').length,1)
 s.route.query={task:'records'};await tick();assert.equal(button(s.root,'＋'),undefined,'reports never create customers');assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
})
test('explicit merchant history cannot be switched to deposit or withdrawal by a scanner handoff',async()=>{
 for(const task of ['deposit','withdraw']){
  const person={id:'handoff-history-customer',displayName:'Should not be selected',phone:'80000000'},actor={accountId:'history-handoff',roles:[],permissions:home.permissions,storeIds:['A']}
  setScanHandoff({store:'A',actor:actor.accountId,target:'storage',code:'WI.'+'A'.repeat(43),customerId:person.id,task})
  const s=mount(StaffPage,false,'en-SG',home,url=>url.endsWith('/customers/'+person.id)?person:home,{actor,query:{task:'records',history:'1'}});await tick()
  assert.ok(!s.calls.some(c=>c.url.endsWith('/customers/'+person.id)));assert.doesNotMatch(text(s.root),/Should not be selected|Register storage for customer|Scan and redeem customer code/);assert.equal(nodes(s.root,n=>n.tag==='button'&&n.props['aria-pressed']===true&&text(n).trim()==='Reports').length,1);assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
 }
})
test('a pending customer handoff cannot leave records after entering same-store explicit history',async()=>{
 const person={id:'slow-history-handoff',displayName:'Late handoff customer',phone:'80000000'},actor={accountId:'slow-history-actor',roles:[],permissions:home.permissions,storeIds:['A']};let finish
 setScanHandoff({store:'A',actor:actor.accountId,target:'storage',code:'WI.'+'A'.repeat(43),customerId:person.id,task:'deposit'})
 const s=mount(StaffPage,false,'en-SG',home,url=>url.endsWith('/customers/'+person.id)?new Promise(resolve=>finish=resolve):home,{actor,query:{task:'records'}});await tick();assert.equal(typeof finish,'function');assert.equal(s.calls.filter(c=>c.url.endsWith('/customers/'+person.id)).length,1)
 s.route.query={task:'records',history:'1'};await tick();finish(person);await tick()
 assert.equal(s.route.query.history,'1');assert.equal(nodes(s.root,n=>n.tag==='button'&&n.props['aria-pressed']===true&&text(n).trim()==='Reports').length,1);assert.doesNotMatch(text(s.root),/Late handoff customer|Register storage for customer|Scan and redeem customer code/);assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
})
test('merchant history composable rejects command callbacks while preserving business capability after return',async()=>{
 let workspace;const harness={setup(){workspace=useMerchantStorage();return()=>h('div')}};const s=mount(harness,false,'en-SG',home,undefined,{query:{task:'records',history:'1'}});await tick();let calls=0
 assert.equal(workspace.can('receive'),false);assert.equal(workspace.can('deliver'),false);await workspace.run(async()=>{calls++});assert.equal(calls,0)
 s.route.query={task:'records'};await tick();assert.equal(workspace.can('receive'),true);await workspace.run(async()=>{calls++});assert.equal(calls,1);assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
})
test('direct registration is immediately usable and pending requests load only in their separate view',async()=>{
 const s=mount(StaffPage,false);await tick()
 assert.match(text(s.root),/1\. 找到顾客/);assert.equal(nodes(s.root,n=>n.tag==='details').length,0);assert.doesNotMatch(text(s.root),/暂无待审核的存酒申请|审核并核对收存/);assert.ok(!s.calls.some(c=>c.url.endsWith('/pending-applications')))
 const inbox=nodes(s.root,n=>n.tag==='button'&&String(n.props['aria-label']).startsWith('顾客提交 · 待审核'))[0];inbox.props.onClick();await tick();assert.equal(s.route.query.task,'inbox');assert.match(text(s.root),/暂无待审核的存酒申请/);assert.doesNotMatch(text(s.root),/1\. 找到顾客/);assert.ok(s.calls.some(c=>c.url.endsWith('/pending-applications')))
 button(s.root,'直接登记').props.onClick();await tick();assert.equal(s.route.query.task,'deposit');assert.match(text(s.root),/1\. 找到顾客/);assert.doesNotMatch(text(s.root),/暂无待审核的存酒申请/);s.unmount()
})
test('pending request route survives settings and store navigation and remains permission gated',async()=>{
 const s=mount(StaffPage,false,'en',home,undefined,{query:{task:'inbox'}});await tick();assert.match(text(s.root),/No storage requests awaiting review/);assert.doesNotMatch(text(s.root),/1\. Find customer/)
 button(s.root,'Settings').props.onClick();await tick();nodes(s.root,n=>n.tag==='button'&&text(n).includes('Back to storage'))[0].props.onClick();await tick();assert.equal(s.route.query.task,'inbox');assert.match(text(s.root),/No storage requests awaiting review/)
 s.route.params.storeId='B';await tick();assert.ok(s.calls.some(c=>c.url==='/api/v1/stores/B/wine-storage/pending-applications'));s.unmount()
 for(const data of [{...home,permissions:['wine_storage.view']},{...home,newBusinessEnabled:false}]){
  const denied=mount(StaffPage,false,'zh-CN',data,undefined,{query:{task:'inbox'}});await tick();assert.ok(!denied.calls.some(c=>c.url.endsWith('/pending-applications')));assert.doesNotMatch(text(denied.root),/审核并核对收存/);denied.unmount()
 }
})
function mockDownload(){const create=URL.createObjectURL,revoke=URL.revokeObjectURL,element=document.createElement,body=document.body,downloads=[];URL.createObjectURL=()=> 'blob:test';URL.revokeObjectURL=()=>{};document.body={appendChild(){}};document.createElement=()=>({remove(){},click(){downloads.push({href:this.href,name:this.download})}});return{downloads,restore(){URL.createObjectURL=create;URL.revokeObjectURL=revoke;document.createElement=element;document.body=body}}}
test('inventory exports applied filters across all pages and blocks unqueried draft filters',async()=>{
 const env=mockDownload();try{
  const s=mount(StaffPage,false,'zh-CN',home,url=>url.includes('/inventory?')?inventory:home,{query:{task:'inventory'}});await tick()
  const input=nodes(s.root,n=>n.tag==='input'&&n.props.maxlength==='120')[0];input.props['onUpdate:modelValue']('小兰');await tick();assert.equal(button(s.root,'导出 Excel').props.disabled,true);assert.match(text(s.root),/先点击查询/)
  nodes(s.root,n=>n.tag==='form'&&nodes(n,c=>c===input).length)[0].props.onSubmit({preventDefault(){}});await tick();button(s.root,'下一页').props.onClick();await tick()
  const requests=[];globalThis.fetch=async(url,options)=>{requests.push({url,options});return{ok:true,blob:async()=>new Blob(['xlsx'])}}
  await button(s.root,'导出 Excel').props.onClick();await tick();assert.equal(requests.length,1);assert.ok(requests[0].url.includes('/inventory/export?'));assert.ok(requests[0].url.includes('query='+encodeURIComponent('小兰')));assert.ok(requests[0].url.includes('locale=zh-CN'));assert.ok(!requests[0].url.includes('page='));assert.equal(env.downloads.length,1);assert.match(env.downloads[0].name,/\.xlsx$/);s.unmount()
 }finally{env.restore()}
})
test('inventory export ignores late responses after switching stores and suppresses double clicks',async()=>{
 const env=mockDownload();try{
  const s=mount(StaffPage,false,'en',home,url=>url.includes('/inventory?')?inventory:home,{query:{task:'inventory'}});await tick();let finish;let requests=0;globalThis.fetch=async()=>{requests++;return new Promise(resolve=>finish=resolve)}
  const exportButton=button(s.root,'Export Excel');const work=exportButton.props.onClick();exportButton.props.onClick();await tick();assert.equal(requests,1)
  s.unmount();finish({ok:true,blob:async()=>new Blob(['xlsx'])});await work;await tick();assert.equal(env.downloads.length,0)
 }finally{env.restore()}
})
test('export failure is visible and never downloads a JSON error as Excel',async()=>{
 const env=mockDownload();try{
  const s=mount(StaffPage,false,'en',home,url=>url.includes('/inventory?')?inventory:home,{query:{task:'inventory'}});await tick();globalThis.fetch=async()=>({ok:false,status:422,json:async()=>({code:'STORAGE_EXPORT_TOO_LARGE',message:'Narrow filters'})});await button(s.root,'Export Excel').props.onClick();await tick();assert.match(text(s.root),/Narrow filters/);assert.equal(env.downloads.length,0);assert.match(text(s.root),/张先生/);assert.equal(button(s.root,'Export Excel').props.disabled,false);globalThis.fetch=async()=>({ok:false,status:403,json:async()=>({code:'STORAGE_FORBIDDEN',message:'Access revoked'})});await button(s.root,'Export Excel').props.onClick();await tick();assert.match(text(s.root),/Access revoked/);assert.doesNotMatch(text(s.root),/张先生/);assert.equal(env.downloads.length,0);s.unmount()
 }finally{env.restore()}
})

test('merchant inventory is store-wide, summarizes all matches and displays receipt details without customer lookup',async()=>{
 const s=mount(StaffPage,false,'zh-CN',home,url=>url.includes('/inventory?')?inventory:home,{query:{task:'inventory'}});await tick()
 assert.match(text(s.root),/当前寄存/);assert.match(text(s.root),/3.*寄存人数/);assert.match(text(s.root),/12.*剩余瓶数/)
 for(const value of ['茅台','张先生','前台','A柜','小兰','TAG-1','已过期','01/01/2026','01/02/2026'])assert.ok(text(s.root).includes(value),value)
 assert.doesNotMatch(text(s.root),/1\. 找到顾客|请先查找并确认一位顾客/);assert.ok(s.calls.some(c=>c.url.includes('/inventory?')));assert.ok(s.calls.every(c=>c.method==='GET'))
 button(s.root,'下一页').props.onClick();await tick();assert.ok(s.calls.some(c=>c.url.includes('page=1')))
 s.unmount()
})
test('merchant inventory empty and retry states never present failed or stale totals as zero stock',async()=>{
 let failed=true
 const s=mount(StaffPage,false,'en',home,url=>{if(url.includes('/inventory?')){if(failed)throw new Error('Inventory unavailable');return{...inventory,items:[],summary:{customerCount:0,bottleCount:0,openedBottleCount:0,expiredBottleCount:0,itemCount:0},hasMore:false}}return home},{query:{task:'inventory'}});await tick()
 assert.match(text(s.root),/Inventory unavailable/);assert.doesNotMatch(text(s.root),/No bottles currently stored/)
 failed=false;button(s.root,'Retry').props.onClick();await tick();assert.match(text(s.root),/No bottles currently stored/)
 s.unmount()
})
test('inventory search submits encoded filter and resets pagination; navigation persists inventory task',async()=>{
 const s=mount(StaffPage,false,'zh-CN',home,url=>url.includes('/inventory?')?inventory:home);await tick();button(s.root,'当前寄存').props.onClick();await tick();assert.equal(s.route.query.task,'inventory')
 const input=nodes(s.root,n=>n.tag==='input'&&n.props.maxlength==='120')[0];input.props['onUpdate:modelValue']('小兰%_')
 const form=nodes(s.root,n=>n.tag==='form'&&nodes(n,c=>c===input).length)[0];form.props.onSubmit({preventDefault(){}});await tick()
 assert.ok(s.calls.some(c=>c.url.includes('query='+encodeURIComponent('小兰%_'))&&c.url.includes('page=0')))
 s.route.params.storeId='B';await tick();assert.ok(s.calls.some(c=>c.url.startsWith('/api/v1/stores/B/wine-storage/inventory?')));s.unmount()
})
test('inventory ignores previous-store responses and clears totals when authorization is lost',async()=>{
 let resolveA
 const s=mount(StaffPage,false,'zh-CN',home,url=>url.includes('/inventory?')?(url.includes('/stores/A/')?new Promise(resolve=>resolveA=resolve):{...inventory,items:[{...inventory.items[0],nameZh:'B店酒'}]}):home,{query:{task:'inventory'}});await tick()
 assert.match(text(s.root),/正在加载|加载中/);assert.doesNotMatch(text(s.root),/茅台/)
 s.route.params.storeId='B';await tick();assert.match(text(s.root),/B店酒/);resolveA(inventory);await tick();assert.doesNotMatch(text(s.root),/茅台/)
 globalThis.fetch=async()=>({ok:false,status:403,json:async()=>({message:'无查看权限'})});button(s.root,'刷新').props.onClick();await tick();assert.match(text(s.root),/无查看权限/);assert.doesNotMatch(text(s.root),/B店酒/);s.unmount()
})
before(async()=>{
 globalThis.window={addEventListener(){},removeEventListener(){}}
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document=Object.assign(new Document(),{activeElement:null,documentElement:{lang:''},visibilityState:'visible',listeners:new Map(),addEventListener(name,fn){if(!this.listeners.has(name))this.listeners.set(name,new Set());this.listeners.get(name).add(fn)},removeEventListener(name,fn){this.listeners.get(name)?.delete(fn)}})
 const memory=new Map();globalThis.sessionStorage={getItem:k=>memory.get(k)??null,setItem:(k,v)=>memory.set(k,v),removeItem:k=>memory.delete(k)}
 const result=await build({stdin:{contents:"export {useMerchantStorage} from './src/modules/wine-storage/merchant/useMerchantStorage';export {default as ActivationWorkspace} from './src/pages/TenantAdminCustomerActivationPage.vue';export {default as EmailSettings} from './src/pages/TenantAdminCustomerEmailPage.vue';export {default as ReceiveForm} from './src/modules/wine-storage/StorageReceiveForm.vue';export {default as LocationsPanel} from './src/modules/wine-storage/merchant/MerchantStorageLocations.vue';export {storageApi} from './src/modules/wine-storage/api';export {setScanHandoff} from './src/modules/staff-scan/handoff';export {useAuthSessionStore} from './src/stores/authSession';export {default as CatalogPage} from './src/modules/catalog/CatalogPage.vue';export {default as CustomerPage} from './src/modules/wine-storage/WineStorageCustomerPage.vue';export {default as StaffPage} from './src/modules/wine-storage/WineStorageStaffPage.vue';export {default as Page} from './src/modules/wine-storage/WineStoragePage.vue';export {default as Summary} from './src/modules/wine-storage/WalletStorageSummary.vue'",resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router','vue-i18n','pinia','qrcode','pdf-lib'],plugins:[{name:'storage-page-test',setup(b){b.onLoad({filter:/[\\/]nameQr\.ts$/},()=>({contents:"export async function renderTenantEntryQr(){return 'data:image/png;base64,QA'}",loader:'js'}));b.onLoad({filter:/[\\/]entryPdf\.ts$/},()=>({contents:"export async function appendGuideEntries(){globalThis.__guidePdfEntered=true;if(globalThis.__guidePdfGate)await globalThis.__guidePdfGate;return new Uint8Array([37,80,68,70])}",loader:'js'}));b.onLoad({filter:/\.pdf$/},()=>({contents:"export default '/assets/wine-storage-guide-test.pdf'",loader:'js'}));b.onLoad({filter:/\.css$/},()=>({contents:'',loader:'js'}));b.onLoad({filter:/\.vue$/},async({path})=>{if(path.endsWith('TenantNameQr.vue'))return{contents:"import {h} from 'vue';export default {props:['value','tenantName','caption'],setup:p=>()=>h('figure',{'data-entry-url':p.value},p.tenantName+' '+p.caption)}",loader:'js'};if(path.endsWith('DownloadableQrCode.vue'))return{contents:"export default {render(){return null}}",loader:'js'};if(path.endsWith('ProductBarcodeScanner.vue'))return{contents:"import {h} from 'vue';export default {props:['disabled','autoStart','showManual'],emits:['code'],setup:(p,{emit})=>()=>h('button',{'data-barcode':true,'data-auto-start':p.autoStart,onClick:()=>emit('code','00036000291452')},'test-barcode')}",loader:'js'};if(path.endsWith('StoragePhotos.vue'))return{contents:"import {h} from 'vue';export default {props:['modelValue'],emits:['update:modelValue'],setup:(p,{emit})=>()=>h('button',{type:'button','data-test-photo':true,onClick:()=>emit('update:modelValue',['photo-id'])},'test-photo')}",loader:'js'};if(path.endsWith('WalletQrDisplay.vue'))return{contents:"import {h} from 'vue';export default {props:['value'],setup:p=>()=>h('figure',{'data-code':p.value})}",loader:'js'};if(path.endsWith('WalletQrScanner.vue'))return{contents:"import {h} from 'vue';export default {props:['disabled'],emits:['code'],setup:(p,{emit})=>()=>h('button',{'data-storage-scan':true,disabled:p.disabled,onClick:()=>emit('code','WS.'+'S'.repeat(43))},'test-storage-scan')}",loader:'js'};if(/WalletCustomerLogin\.vue$/.test(path))return{contents:"export default {render(){return null}}",loader:'js'};const{descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:'storage-test-'+path,inlineTemplate:true}).content,loader:'ts'}})}}]})
 const dir='target/wine-storage-page-test';await mkdir(dir,{recursive:true});await writeFile(dir+'/entry.mjs',result.outputFiles[0].contents);({ActivationWorkspace,EmailSettings,Page,Summary,CustomerPage,StaffPage,CatalogPage,useMerchantStorage,useAuthSessionStore,setScanHandoff,ReceiveForm,LocationsPanel,storageApi}=await import(pathToFileURL(process.cwd()+'/'+dir+'/entry.mjs').href+'?'+Date.now()))
})
after(()=>{globalThis.window=originalWindow;globalThis.fetch=originalFetch;globalThis.sessionStorage=originalStorage;globalThis.document=originalDocument;globalThis.Document=originalDocumentClass;globalThis.ShadowRoot=originalShadowRoot})
function mount(component,customer,locale='zh-CN',data=home,respond,lists={}){
 const calls=[],navigation=[],root={children:[]};globalThis.fetch=async(url,options)=>{calls.push({url,method:options?.method??'GET',body:typeof options?.body==='string'?JSON.parse(options.body):undefined});const response={ok:true,status:200,json:async()=>url.includes('/wine-storage/reports')?(lists.reportRespond?lists.reportRespond(url):{items:[],summary:{recordCount:0,completedCount:0,bottleCount:0},page:0,size:20,hasMore:false}):url.endsWith('/locations')&&(!options?.method||options.method==='GET')?(lists.locations??[{id:'front-desk',name:'前台寄存处',defaultLocation:true,version:0}]):url.endsWith('/pending-applications')?[...(lists.pending??[])]:url.includes('/custodians')&&(!options?.method||options.method==='GET')?[...(lists.people??[])]:respond?respond(url,options):url.includes('/products')?[]:data};response.text=async()=>JSON.stringify(await response.json());response.arrayBuffer=async()=>new Uint8Array([1]).buffer;return response}
 const route=reactive({params:{storeId:'A'},hash:'',query:lists.query??{}});const app=renderer.createApp({render:()=>h(component,{customer,store:'A',...lists.props})});const pinia=createPinia();app.use(pinia);if(lists.actor)useAuthSessionStore(pinia).user=lists.actor;const i18n=createI18n({legacy:false,locale,messages:{}});app.use(i18n);app.provide(routeLocationKey,route);app.provide(routerKey,{push:async target=>{navigation.push(target);if(target?.query)route.query=target.query},replace:async target=>{if(target?.hash!==undefined)route.hash=target.hash}});app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:p.to},slots.default?.())});app.mount(root);mounted.add(app);return{root,calls,navigation,i18n,route,auth:useAuthSessionStore(pinia),unmount:()=>{app.unmount();mounted.delete(app)}}
}
test('customer storage only links back to a commercially enabled wallet and tracks activation and closure',async()=>{
 const s=mount(CustomerPage,true,'zh-CN',{...home,walletEnabled:false});await tick()
 const walletBack=()=>nodes(s.root,n=>n.tag==='a'&&n.props.href==='/wallet/A')
 assert.equal(walletBack().length,0)
 globalThis.fetch=async()=>({ok:true,json:async()=>({...home,walletEnabled:true})});await nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='刷新')[0].props.onClick();await tick();assert.equal(walletBack().length,1)
 globalThis.fetch=async()=>({ok:true,json:async()=>({...home,walletEnabled:false})});await nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='刷新')[0].props.onClick();await tick();assert.equal(walletBack().length,0)
})

test('storage customer and merchant entry titles and API scopes are distinct',async()=>{
 for(const customer of [true,false]){
  const s=mount(customer?CustomerPage:StaffPage,customer,'zh-CN',{...home,walletEnabled:true});await tick();assert.match(text(s.root),customer?/我的存酒卡/:/酒水寄存管理/)
  assert.equal(s.calls[0].url,`/api/v1/${customer?'public/':''}stores/A/wine-storage/home`)
  assert.equal(nodes(s.root,n=>n.tag==='a')[0].props.href,customer?'/wallet/A':'/stores/A/wallet')
  assert.ok(nodes(s.root,n=>n.props?.class?.includes('cards-page')).length)
  assert.equal(nodes(s.root,n=>n.tag==='button'&&n.props['aria-pressed']===true&&!n.props.class?.includes('frontend-locale-switcher')).length,1)
  if(customer){assert.match(text(s.root),/我要存酒/);assert.doesNotMatch(text(s.root),/选择同一顾客|搜索酒名、编号、位置或标签/)}
  assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
 }
})
test('customer pages never expose merchant receipt or rule editing even if response permissions include them',async()=>{
 for(const customer of [true,false]){
  const s=mount(customer?CustomerPage:StaffPage,customer);await tick()
  if(customer){assert.match(text(s.root),/我要存酒/);assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='保存规则').length,0);s.unmount();continue}
  assert.match(text(s.root),/存酒.*取酒.*报表/)
  assert.match(text(s.root),/直接登记.*待审核/);assert.match(text(s.root),/1\. 找到顾客/);assert.doesNotMatch(text(s.root),/请先查找并确认一位顾客/)
  assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='确认实际收存').length,0)
  assert.equal(nodes(s.root,n=>n.tag==='form'&&nodes(n,k=>k.tag==='button'&&text(k)==='保存新版本').length>0).length,0);button(s.root,'设置').props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='form'&&nodes(n,k=>k.tag==='button'&&text(k)==='保存新版本').length>0).length,1)
  s.unmount()
 }
})
test('customer tasks show one workflow at a time and cannot collect without bottles',async()=>{
 const s=mount(CustomerPage,true);await tick()
 assert.match(text(s.root),/选择酒品/);assert.doesNotMatch(text(s.root),/申请领取所选物品|生成顾客扫码授权意图/)
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='我要取酒')[0].props.onClick();await tick()
 assert.match(text(s.root),/暂无可领取的存酒/);assert.doesNotMatch(text(s.root),/选择同一顾客|搜索中英文酒名或编号/)
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
 await (await preparePickupCart(s.root)).props.onClick();await tick()
 const writes=s.calls.filter(c=>c.method==='POST');assert.equal(writes.length,1);assert.equal(writes[0].url,'/api/v1/public/stores/A/wine-storage/intents');assert.deepEqual(writes[0].body.selections,[{itemId:item.id,quantity:1}]);assert.equal('customerId'in writes[0].body,false)
 assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-code']===code).length,1);assert.match(text(s.root),/取酒码已生成/)
 globalThis.fetch=async()=>({ok:false,status:401,json:async()=>({message:'Sign in'})})
 await nodes(s.root,n=>n.tag==='button'&&(text(n).trim()==='刷新'||n.props['aria-label']==='刷新'))[0].props.onClick();await tick()
 globalThis.fetch=async()=>({ok:true,json:async()=>({...home,items:[],intents:[]})})
 await nodes(s.root,n=>n.tag==='button'&&(text(n).trim()==='刷新'||n.props['aria-label']==='刷新'))[0].props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-code']===code).length,0)
 s.unmount()
})
test('cart removal clears quantities and switching stores never carries an old pickup cart',async()=>{
 const item={id:'scope-cart',snapshot:{nameZh:'酒',timezone:'Asia/Singapore'},remaining:3,opened:false,status:'stored',expiresAt:null},data={...home,items:[item]},s=mount(CustomerPage,true,'zh-CN',data,url=>url.includes('/product-options')?{items:[],categories:[],hasMore:false}:{...data},{query:{view:'withdraw'}});await tick()
 await preparePickupCart(s.root,2);button(s.root,'移出取酒车').props.onClick();await tick();assert.equal(button(s.root,'提交取酒').props.disabled,true);button(s.root,'继续选酒').props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.type==='number')[0].props.value,0)
 await preparePickupCart(s.root,2);s.route.params.storeId='B';await tick();assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.type==='number')[0].props.value,0);assert.equal(button(s.root,'查看取酒车').props.disabled,true);assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
})
test('same-store customer ownership change clears the old pickup cart and cached row snapshots',async()=>{
 const item={id:'owner-old',customerId:'owner-a',snapshot:{nameZh:'旧顾客存酒',timezone:'Asia/Singapore'},remaining:2,opened:false,status:'stored',expiresAt:null},data={...home,items:[item]},s=mount(CustomerPage,true,'zh-CN',data,url=>url.includes('/product-options')?{items:[],categories:[],hasMore:false}:{...data},{query:{view:'withdraw'}});await tick();await preparePickupCart(s.root,1)
 data.items=[{...item,id:'owner-new',customerId:'owner-b',snapshot:{...item.snapshot,nameZh:'新顾客存酒'}}];await nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='刷新')[0].props.onClick();await tick();assert.doesNotMatch(text(s.root),/旧顾客存酒/);assert.match(text(s.root),/新顾客存酒/);assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.type==='number')[0].props.value,0);assert.equal(button(s.root,'查看取酒车').props.disabled,true);s.unmount()
})
test('merchant cart directly submits a complete multi-item batch once in either storage mode',async()=>{
 for(const operationMode of ['customer_verification','staff_registration']){
  const item={id:'cart-a',customerId:'customer',snapshot:{nameZh:'酒甲',timezone:'Asia/Singapore'},remaining:3,opened:false,status:'stored',expiresAt:null},other={...item,id:'cart-b'},data={...home,operationMode,items:[item,other,{...item,id:'foreign',customerId:'someone-else'}],intents:[]},person={id:'customer',displayName:'批量顾客',phone:'8000'};let finish
  const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>url.includes('/customers?')?[person]:options?.method==='POST'?new Promise(resolve=>finish=resolve):{...data},{query:{task:'withdraw'}});await tick();const form=nodes(s.root,n=>n.tag==='form')[0];nodes(form,n=>n.tag==='input')[0].props.onInput({target:{value:'8000'}});await form.props.onSubmit({preventDefault(){}});await tick();assert.ok(button(s.root,'更换顾客'));await tick()
  const inputs=nodes(s.root,n=>n.tag==='input'&&n.props.type==='number');assert.equal(inputs.length,2);inputs[0].props.onInput({target:{value:'2'}});inputs[1].props.onInput({target:{value:'1'}});await tick();const submit=await preparePickupCart(s.root);assert.equal(s.calls.filter(c=>c.method==='POST').length,0);assert.equal(button(s.root,'确认实物已交付并扣减'),undefined);submit.props.onClick();submit.props.onClick();await tick()
  const posts=s.calls.filter(c=>c.method==='POST');assert.equal(posts.length,1);assert.ok(posts[0].url.endsWith('/direct-deliver'));assert.equal(posts[0].body.customerId,'customer');assert.deepEqual(posts[0].body.selections,[{itemId:item.id,quantity:2},{itemId:other.id,quantity:1}]);finish({id:'batch',customerId:'customer',origin:'staff',status:'completed',selections:posts[0].body.selections,code:null});await tick();assert.ok(nodes(s.root,n=>n.tag==='input'&&n.props.type==='number').every(n=>n.props.value===0));assert.equal(nodes(s.root,n=>n.tag==='figure').length,0);s.unmount()
 }
})
test('customer cart batches holdings into one code, prevents duplicates and hides code after redemption',async()=>{
 const item={id:'batch-one',customerId:'customer',snapshot:{nameZh:'啤酒',timezone:'Asia/Singapore'},remaining:3,opened:false,status:'stored',expiresAt:null},other={...item,id:'batch-two'},selections=[{itemId:item.id,quantity:2},{itemId:other.id,quantity:1}],intent={id:'batch',origin:'customer',status:'authorized',code:'WS.'+'Q'.repeat(43),expiresAt:new Date(Date.now()+300000).toISOString(),selections},data={...home,items:[item,other],intents:[]};let resolve
 const s=mount(CustomerPage,true,'zh-CN',data,(url,options)=>options?.method==='POST'?new Promise(done=>resolve=done):url.includes('/product-options')?{items:[],categories:[],hasMore:false}:{...data});await tick();button(s.root,'我要取酒').props.onClick();await tick()
 const inputs=nodes(s.root,n=>n.tag==='input'&&n.props.type==='number');inputs[0].props.onInput({target:{value:'2'}});inputs[1].props.onInput({target:{value:'1'}});await tick();assert.equal(s.calls.filter(c=>c.method==='POST').length,0)
 const submit=await preparePickupCart(s.root);submit.props.onClick();submit.props.onClick();await tick();const posts=s.calls.filter(c=>c.method==='POST');assert.equal(posts.length,1);assert.deepEqual(posts[0].body.selections,selections);assert.equal('customerId'in posts[0].body,false)
 data.intents=[{...intent,code:null}];resolve(intent);await tick();assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-code']===intent.code).length,1);assert.match(text(s.root),/整车取酒码/)
 data.intents=[{...intent,status:'completed',code:null}];await Promise.all([...globalThis.document.listeners.get('visibilitychange')].map(fn=>fn()));await tick();assert.equal(nodes(s.root,n=>n.tag==='figure').length,0);assert.equal(s.calls.filter(c=>c.method==='POST').length,1);s.unmount()
})

test('quick pickup excludes expired holdings, reports rejected commands and supports cancellation in English',async()=>{
 const item={id:'quick-cancel',snapshot:{nameZh:'啤酒',nameEn:'Beer',timezone:'Asia/Singapore'},remaining:2,opened:false,status:'stored',expiresAt:new Date(Date.now()+86400000).toISOString()},intent={id:'quick-cancel-code',origin:'customer',status:'authorized',code:'WS.'+'C'.repeat(43),expiresAt:new Date(Date.now()+300000).toISOString(),selections:[{itemId:item.id,quantity:1}]},data={...home,items:[item,{...item,id:'expired',expiresAt:new Date(Date.now()-1000).toISOString()}],intents:[]}
 const s=mount(CustomerPage,true,'en',data,(url,options)=>options?.method==='POST'?(url.endsWith('/cancel')?(data.intents=[{...intent,code:null,status:'cancelled'}],data.intents[0]):(data.intents=[{...intent,code:null}],intent)):url.includes('/product-options')?{items:[],categories:[],hasMore:false}:{...data});await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='Collect bottles')[0].props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='article'&&n.props.class==='pickup-row').length,1)
 const fetchOk=globalThis.fetch;globalThis.fetch=async(url,options)=>options?.method==='POST'?{ok:false,status:409,json:async()=>({code:'STORAGE_CONFLICT',message:'Bottle availability changed'})}:fetchOk(url,options)
 await (await preparePickupCart(s.root)).props.onClick();await tick()
 assert.match(text(s.root),/Bottle availability changed/);assert.equal(nodes(s.root,n=>n.tag==='figure').length,0)
 globalThis.fetch=fetchOk
 await (await preparePickupCart(s.root)).props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='figure').length,1)
 await nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='Cancel')[0].props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='figure').length,0);assert.doesNotMatch(text(s.root),/Your collection code is ready/);assert.equal(s.calls.filter(c=>c.url.endsWith('/intents/quick-cancel-code/cancel')).length,1);s.unmount()
})

test('cancelling from pickup or records retains the code on failure and clears notice only after success',async()=>{
 for(const fromRecords of [false,true]){
  const item={id:'cancel-item',snapshot:{nameZh:'验收酒',timezone:'Asia/Singapore'},remaining:2,opened:false,status:'stored',expiresAt:null},intent={id:'cancel-intent',origin:'customer',status:'authorized',code:'WS.'+'N'.repeat(43),expiresAt:new Date(Date.now()+300000).toISOString(),selections:[{itemId:item.id,quantity:1}]},data={...home,items:[item],intents:[]}
  let failCancel=true,finishCancel
  const s=mount(CustomerPage,true,'en',data,(url,options)=>options?.method==='POST'?(url.endsWith('/cancel')?new Promise(resolve=>finishCancel=()=>{data.intents=[{...intent,code:null,status:'cancelled'}];resolve(data.intents[0])}):(data.intents=[{...intent,code:null}],intent)):url.includes('/product-options')?{items:[],categories:[],hasMore:false}:{...data});await tick()
  button(s.root,'Collect bottles').props.onClick();await tick();await (await preparePickupCart(s.root)).props.onClick();await tick();assert.match(text(s.root),/Your collection code is ready/)
  if(fromRecords){button(s.root,'My records').props.onClick();await tick();button(s.root,'Collections').props.onClick();await tick()}
  const okFetch=globalThis.fetch
  globalThis.fetch=async(url,options)=>url.endsWith('/cancel')&&failCancel?{ok:false,status:409,json:async()=>({code:'STORAGE_CONFLICT',message:'Cancel rejected'})}:okFetch(url,options)
  await button(s.root,'Cancel').props.onClick();await tick();assert.match(text(s.root),/Cancel rejected/);assert.match(text(s.root),/Your collection code is ready/);assert.equal(nodes(s.root,n=>n.tag==='figure').length,1)
  failCancel=false;const work=button(s.root,'Cancel').props.onClick();button(s.root,'Cancel').props.onClick();await tick();assert.match(text(s.root),/Your collection code is ready/);assert.equal(s.calls.filter(c=>c.url.endsWith('/cancel')).length,1)
  finishCancel();await work;await tick();assert.doesNotMatch(text(s.root),/Your collection code is ready|Cancel rejected/);assert.equal(nodes(s.root,n=>n.tag==='figure').length,0);assert.equal(data.items[0].remaining,2);s.unmount()
 }
})

test('refresh clears stale pickup notices when a code was cancelled elsewhere',async()=>{
 const item={id:'refresh-cancel-item',snapshot:{nameZh:'验收酒',timezone:'Asia/Singapore'},remaining:2,opened:false,status:'stored',expiresAt:null},intent={id:'refresh-cancel-intent',origin:'customer',status:'authorized',code:'WS.'+'R'.repeat(43),expiresAt:new Date(Date.now()+300000).toISOString(),selections:[{itemId:item.id,quantity:1}]},data={...home,items:[item],intents:[]}
 const s=mount(CustomerPage,true,'en',data,(url,options)=>options?.method==='POST'?(data.intents=[{...intent,code:null}],intent):url.includes('/product-options')?{items:[],categories:[],hasMore:false}:{...data});await tick();button(s.root,'Collect bottles').props.onClick();await tick();await (await preparePickupCart(s.root)).props.onClick();await tick()
 data.intents=[{...intent,code:null,status:'cancelled'}];await Promise.all([...globalThis.document.listeners.get('visibilitychange')].map(fn=>fn()));await tick();assert.doesNotMatch(text(s.root),/Your collection code is ready/);assert.equal(nodes(s.root,n=>n.tag==='figure').length,0);s.unmount()
})

test('customer selects a catalog wine, adds photos and submits the application as the signed-in customer',async()=>{
 const product={id:'wine',nameZh:'可寄存白酒',nameEn:'Spirit',code:'WINE'},s=mount(CustomerPage,true,'zh-CN',home,(url,options)=>url.includes('/product-options')?{items:[product],categories:[],hasMore:false}:url.endsWith('/products/wine')?product:url.includes('/product-options')?{items:[product],categories:[],hasMore:false,page:0}:url.includes('/products')?[product]:options?.method==='POST'?{id:'applied',lines:[],status:'pending'}:home);await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).includes('可寄存白酒'))[0].props.onClick();await tick()
 const submit=()=>nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='提交寄存申请')[0]
 assert.equal(submit().props.disabled,true)
 nodes(s.root,n=>n.props?.['data-test-photo'])[0].props.onClick();await tick()
 const accepted=nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0];accepted.checked=true;accepted.listeners.change({target:accepted});await tick()
 assert.equal(submit().props.disabled,false)
 await nodes(s.root,n=>n.tag==='form'&&n.props.class==='storage-customer-deposit')[0].props.onSubmit({preventDefault(){}});await tick()
 const writes=s.calls.filter(c=>c.method==='POST');assert.equal(writes.length,1);assert.equal(writes[0].url,'/api/v1/public/stores/A/wine-storage/applications');assert.equal(writes[0].body.lines[0].productId,'wine');assert.deepEqual(writes[0].body.lines[0].photos,['photo-id']);assert.equal('customerId'in writes[0].body,false);assert.equal('localKey'in writes[0].body.lines[0],false);assert.match(text(s.root),/寄存申请已提交/);s.unmount()
})
test('quick mode scopes a customer bottles and records handover without a customer confirmation code',async()=>{
 const item={id:'a-bottle',applicationId:'application',customerId:'customer',snapshot:{nameZh:'甲客白酒',nameEn:'A wine',timezone:'Asia/Singapore',receiptPhotos:[]},quantity:3,remaining:2,opened:false,status:'stored',location:'A1',label:'L1',expiresAt:new Date(Date.now()+86400000).toISOString()},other={...item,id:'b-bottle',customerId:'other',snapshot:{...item.snapshot,nameZh:'乙客啤酒'}}
 const data={...home,items:[item,other],intents:[]},done={id:'done',customerId:'customer',origin:'staff',status:'completed',completedAt:new Date().toISOString(),code:null,selections:[{itemId:item.id,quantity:1}]}
 const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>url.includes('/customers?')?[{id:'customer',displayName:'甲顾客',phone:'+6580000000'}]:options?.method==='POST'?done:data);await tick();assert.doesNotMatch(text(s.root),/甲客白酒|乙客啤酒/)
 const form=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-merchant-customer-search'))[0];nodes(form,n=>n.tag==='input')[0].props.onInput({target:{value:'8000'}});await form.props.onSubmit({preventDefault(){}});await tick();assert.ok(button(s.root,'更换顾客'));await tick();button(s.root,'取酒').props.onClick();await tick()
 assert.match(text(s.root),/甲客白酒/);assert.doesNotMatch(text(s.root),/乙客啤酒/);assert.equal(button(s.root,'生成顾客确认码'),undefined)
 ;(await preparePickupCart(s.root)).props.onClick();await tick()
 const writes=s.calls.filter(c=>c.method==='POST');assert.equal(writes.length,1);assert.ok(writes[0].url.endsWith('/direct-deliver'));assert.equal(writes[0].body.customerId,'customer');assert.deepEqual(writes[0].body.selections,done.selections);assert.equal(nodes(s.root,n=>n.tag==='figure').length,0)
})

test('merchant directly registers bottles for the chosen customer with photos and explicit attestation',async()=>{
 const product={id:'wine',categoryId:'category',nameZh:'白酒',nameEn:'Spirit',code:'WINE'},data={...home,applications:[],rules:[{id:'rule',categoryId:'category',name:'保存三个月',months:3,version:1}],categories:[{id:'category',nameZh:'白酒'}]}
 const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>url.includes('/customers?')?[{id:'customer',displayName:'甲顾客',phone:'+6580000000'}]:url.endsWith('/products/wine')?product:url.includes('/product-options')?{items:[product],categories:[],hasMore:false,page:0}:url.includes('/products')?[product]:options?.method==='POST'?{id:'registered',customerId:'customer',status:'stored',lines:[]}:{...data});await tick()
 const search=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-merchant-customer-search'))[0]
 nodes(search,n=>n.tag==='input')[0].props.onInput({target:{value:'8000'}});await search.props.onSubmit({preventDefault(){}});await tick()
 assert.ok(button(s.root,'更换顾客'));await tick()
 assert.doesNotMatch(text(s.root),/2\. 审核待收存申请|这位顾客没有待审核申请/)
 const form=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-panel'))[0]
 const submit=()=>nodes(form,n=>n.tag==='button'&&text(n).trim()==='确认实物已收存')[0]
 assert.equal(submit().props.disabled,true)
 nodes(form,n=>n.tag==='button'&&text(n).includes('选择此酒品'))[0].props.onClick();await tick()
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
 const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>url.endsWith('/products/wine')?product:url.includes('/product-options')?{items:[product],categories:[],hasMore:false,page:0}:url.includes('/products')?[product]:options?.method==='POST'?{...application,status:'stored'}:{...data},{...lists,query:{task:'inbox'}});await tick()
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
 const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>options?.method==='POST'?(people.push({id:'new-person',...JSON.parse(options.body),version:0}),people.at(-1)):{...data},{people});await tick();button(s.root,'设置').props.onClick();await tick()
 const panel=nodes(s.root,n=>n.tag==='details'&&nodes(n,c=>c.tag==='summary'&&text(c).includes('酒妹／保管员管理')).length)[0];panel.props.onToggle({target:{open:true}});await tick()
 const form=nodes(panel,n=>n.tag==='form')[0];nodes(form,n=>n.tag==='input'&&n.props.maxlength==='100')[0].props['onUpdate:modelValue']('小兰');await tick();await form.props.onSubmit({preventDefault(){}});await tick()
 const writes=s.calls.filter(c=>c.method==='POST');assert.equal(writes.length,1);assert.equal(writes[0].url,'/api/v1/stores/A/wine-storage/custodians');assert.equal(writes[0].body.name,'小兰');assert.equal(writes[0].body.kind,'hostess');assert.equal(writes[0].body.version,null);assert.match(text(panel),/小兰/);s.unmount()
 const denied=mount(StaffPage,false);await tick();assert.doesNotMatch(text(denied.root),/酒妹／保管员管理/);assert.ok(!denied.calls.some(c=>c.url.includes('includeDisabled')));denied.unmount()
})

test('merchant rejection requires an explicit customer-visible reason and never posts a receipt',async()=>{
 const application={id:'reject-inbox',customerId:'customer',status:'pending',lines:[],createdAt:new Date().toISOString(),reason:'',confirmation:null},lists={pending:[{application,customer:{id:'customer',displayName:'待审核顾客',phone:'+6580000000'}}]}
 const s=mount(StaffPage,false,'zh-CN',{...home,applications:[application]},(url,options)=>url.includes('/products')?[]:options?.method==='POST'?{...application,status:'rejected'}:home,{...lists,query:{task:'inbox'}});await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='审核并核对收存')[0].props.onClick();await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='拒绝申请')[0].props.onClick();await tick()
 const form=nodes(s.root,n=>n.tag==='form'&&nodes(n,b=>b.tag==='button'&&text(b).trim()==='确认拒绝申请').length)[0];await form.props.onSubmit({preventDefault(){}});assert.equal(s.calls.filter(c=>c.method==='POST').length,0)
 nodes(form,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('尚未收到实物，请核对后重新申请');await tick();await form.props.onSubmit({preventDefault(){}});await tick()
 const writes=s.calls.filter(c=>c.method==='POST');assert.equal(writes.length,1);assert.equal(writes[0].url,'/api/v1/stores/A/wine-storage/applications/reject-inbox/reject');assert.equal(writes[0].body.reason,'尚未收到实物，请核对后重新申请');assert.match(text(s.root),/申请已拒绝/);s.unmount()
})

test('merchant pickup cards show only selected customer photos and enlargement never registers delivery',async()=>{
 const item={id:'merchant-photo',applicationId:'application',customerId:'customer',snapshot:{nameZh:'核对啤酒',nameEn:'Check beer',timezone:'Asia/Singapore',receiptPhotos:['receipt-check'],applicationPhotos:[]},quantity:2,remaining:2,opened:false,status:'stored',expiresAt:null,label:'A'},data={...home,items:[item,{...item,id:'without-photo',snapshot:{...item.snapshot,receiptPhotos:[]}},{...item,id:'foreign',customerId:'another-customer',snapshot:{...item.snapshot,receiptPhotos:['foreign-photo']}}],events:[{sourceId:item.id,action:'photos.added',details:{photos:['followup-check']}}]}
 const s=mount(StaffPage,false,'zh-CN',data,undefined,{query:{task:'withdraw'}});await tick()
 nodes(s.root,n=>n.tag==='input')[0].props.onInput({target:{value:'张'}});globalThis.fetch=async()=>({ok:true,json:async()=>[{id:'customer',displayName:'张',phone:'80000000'}]});await nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.ok(button(s.root,'更换顾客'));await tick()
 const photos=nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='放大查看照片');assert.equal(photos.length,2)
 assert.deepEqual(nodes(s.root,n=>n.tag==='img').map(n=>n.props.src),['/api/v1/stores/A/wine-storage/photos/receipt-check','/api/v1/stores/A/wine-storage/photos/followup-check'])
 await photos[0].props.onClick({currentTarget:{focus(){}}});await tick();const dialog=nodes(s.root,n=>n.props?.role==='dialog')[0];assert.ok(dialog);assert.equal(nodes(dialog,n=>n.tag==='img')[0].props.src,'/api/v1/stores/A/wine-storage/photos/receipt-check');button(dialog,'继续放大').props.onClick();await tick();assert.ok(nodes(s.root,n=>String(n.props?.class).includes('is-zoomed')).length);button(dialog,'关闭').props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.props?.role==='dialog').length,0);assert.ok(s.calls.every(c=>c.method==='GET'));assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='确认登记取酒').length,0);s.unmount()
})
test('customer pickup shows staff receipt and later photos, enlarges safely and refreshes without commands',async()=>{
 const item={id:'photo-bottle',applicationId:'application',customerId:'customer',snapshot:{nameZh:'青岛啤酒',nameEn:'Beer',timezone:'Asia/Singapore',receiptPhotos:['staff-photo'],applicationPhotos:[]},quantity:2,remaining:2,opened:false,status:'stored',expiresAt:null}
 const data={...home,items:[item],events:[{sourceId:item.id,action:'photos.added',details:{photos:['extra-photo']}},{sourceId:'another-bottle',action:'photos.added',details:{photos:['not-this-bottle']}}]}
 const s=mount(CustomerPage,true,'zh-CN',data,url=>url.includes('/product-options')?{items:[],categories:[],hasMore:false}:structuredClone(data));await tick();button(s.root,'我要取酒').props.onClick();await tick()
 const thumbs=()=>nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='放大查看照片')
 assert.equal(thumbs().length,2);assert.deepEqual(nodes(s.root,n=>n.tag==='img').map(n=>n.props.src),['/api/v1/public/stores/A/wine-storage/photos/staff-photo','/api/v1/public/stores/A/wine-storage/photos/extra-photo'])
 await thumbs()[0].props.onClick({currentTarget:{focus(){}}});await tick();let dialog=nodes(s.root,n=>n.props?.role==='dialog')[0];assert.ok(dialog);assert.equal(dialog.props['aria-label'],'照片预览');assert.equal(nodes(dialog,n=>n.tag==='img')[0].props.src,'/api/v1/public/stores/A/wine-storage/photos/staff-photo')
 button(dialog,'继续放大').props.onClick();await tick();assert.ok(nodes(s.root,n=>String(n.props?.class).includes('is-zoomed')).length)
 ;[].concat(dialog.props.onKeydown).forEach(handler=>handler({key:'Escape',preventDefault(){}}));await tick();assert.equal(nodes(s.root,n=>n.props?.role==='dialog').length,0)
 data.events.push({sourceId:item.id,action:'photos.added',details:{photos:['new-followup']}});await nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='刷新')[0].props.onClick();await tick();assert.equal(thumbs().length,3);assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
})
test('customer stored and past records reuse scoped photo enlargement',async()=>{
 const item={id:'past-photo',applicationId:'application',customerId:'customer',snapshot:{nameZh:'啤酒',nameEn:'Beer',timezone:'Asia/Singapore',receiptPhotos:['receipt-photo'],applicationPhotos:[]},quantity:2,remaining:0,opened:false,status:'collected',expiresAt:null},data={...home,items:[item],events:[]}
 for(const customer of [true]){
  const s=mount(customer?CustomerPage:StaffPage,customer,'zh-CN',data,undefined,{query:customer?{}:{task:'records'}});await tick()
  if(customer)button(s.root,'我的记录').props.onClick();else{nodes(s.root,n=>n.tag==='input')[0].props.onInput({target:{value:'张'}});globalThis.fetch=async()=>({ok:true,json:async()=>[{id:'customer',displayName:'张',phone:'80000000'}]});await nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.ok(button(s.root,'更换顾客'));await tick()}
  await tick();if(!customer)button(s.root,'酒品明细').props.onClick();await tick()
  const thumbnails=nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='放大查看照片');assert.ok(thumbnails.length)
  await thumbnails[0].props.onClick({currentTarget:{focus(){}}});await tick();const dialog=nodes(s.root,n=>n.props?.role==='dialog')[0];assert.equal(nodes(dialog,n=>n.tag==='img')[0].props.src,`/api/v1/${customer?'public/':''}stores/A/wine-storage/photos/receipt-photo`);button(dialog,'关闭').props.onClick();await tick()
  if(customer){button(s.root,'已结束的寄存').props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='放大查看照片').length,1)}
  assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
 }
})
test('customer pickup without photos renders no empty gallery or preview button',async()=>{
 const item={id:'plain',snapshot:{nameZh:'啤酒',timezone:'Asia/Singapore'},remaining:1,opened:false,status:'stored',expiresAt:null},s=mount(CustomerPage,true,'zh-CN',{...home,items:[item]});await tick();button(s.root,'我要取酒').props.onClick();await tick();assert.equal(nodes(s.root,n=>String(n.props?.class).includes('storage-photos')).length,0);assert.equal(nodes(s.root,n=>n.props?.role==='dialog').length,0);s.unmount()
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
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='取酒')[0].props.onClick();await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='扫描顾客取酒码并核销')[0].props.onClick();await tick()
 const scanner=nodes(s.root,n=>n.props?.['data-storage-scan'])[0];scanner.props.onClick();scanner.props.onClick();await tick()
 assert.equal(s.calls.filter(c=>c.method==='POST').length,1);assert.equal(s.calls.find(c=>c.method==='POST').url,'/api/v1/stores/A/wine-storage/scan-deliver');assert.equal(s.calls.find(c=>c.method==='POST').body.code,'WS.'+'S'.repeat(43))
 data.intents=[done];resolve(done);await tick();assert.match(text(s.root),/扫码核销成功/);assert.match(text(s.root),/该取酒码已失效/)
 assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='确认实物已交付').length,0);assert.equal(nodes(s.root,n=>n.tag==='figure').length,0);assert.ok(!s.calls.some(c=>c.url.includes('/customers/')||c.url.endsWith('/deliver')));s.unmount()
})

test('customer code is automatically hidden after server completion when returning to the page',async()=>{
 const code='WS.'+'R'.repeat(43),item={id:'bottle',customerId:'customer',applicationId:'app',snapshot:{productId:'wine',nameZh:'啤酒',nameEn:'Beer',timezone:'Asia/Singapore',receiptPhotos:[]},quantity:2,remaining:2,opened:false,status:'stored',expiresAt:new Date(Date.now()+86400000).toISOString()},intent={id:'pickup',customerId:'customer',origin:'customer',status:'authorized',code,expiresAt:new Date(Date.now()+300000).toISOString(),selections:[{itemId:'bottle',quantity:1}]},data={...home,items:[item],intents:[]}
 const s=mount(CustomerPage,true,'zh-CN',data,(url,options)=>options?.method==='POST'?(data.intents=[{...intent,code:null}],intent):url.includes('/products')?[]:{...data});await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='我要取酒')[0].props.onClick();await tick();await (await preparePickupCart(s.root)).props.onClick();await tick()
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
 assert.ok(s.calls.some(c=>c.url.includes('barcode=00036000291452')));assert.match(text(s.root),/2. 填写寄存信息/);assert.match(text(s.root),/已添加/)
})

test('unlisted barcode retains identifier in application note and requests wine name',async()=>{
 const s=mount(CustomerPage,true,'zh-CN',home,(url,options)=>url.includes('/product-options')?{items:[],categories:[],hasMore:false}:options?.method==='POST'?{id:'applied',lines:[],status:'pending'}:home);await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n)==='扫瓶身条码选酒')[0].props.onClick();await tick();nodes(s.root,n=>n.props?.['data-barcode'])[0].props.onClick();await tick();assert.match(text(s.root),/条码尚未收录/)
 const input=nodes(s.root,n=>n.tag==='input'&&n.props.maxlength==='200')[0];input.value='新酒';(input.listeners.input??input.props.onInput)({target:input});await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n)==='添加这款酒')[0].props.onClick();await tick();assert.match(nodes(s.root,n=>n.tag==='textarea')[0].value,/00036000291452/)
})


const catalogProducts=['wine-a','wine-b'].map((id,i)=>({id,categoryId:'cat',code:id,nameZh:'酒品'+(i+1),nameEn:'Wine '+(i+1),descriptionZh:'',descriptionEn:'',imageUrl:'',priceCents:100,currency:'SGD',taxMode:'inclusive',taxCategory:'not_applicable',taxRate:0,channels:['storage'],kind:'physical',unit:'瓶',status:'active',listingEnabled:true,listingVersion:0,version:0}))
const catalogActor={roles:['tenant_admin'],permissions:['catalog.view','catalog.manage','catalog.publish'],storeIds:['A']}
const emailSettingsFixture={success:true,enabled:true,fromEmail:'qa@example.test',fromName:'QA',smtpHost:'smtp.example.test',smtpPort:587,smtpUsername:'qa-user',smtpStartTls:true,secretConfigured:true}
test('standalone email settings loads only shared SMTP configuration and keeps existing passwords on save',async()=>{
 const actor={...catalogActor,permissions:['tenant.admin.manage']},s=mount(EmailSettings,false,'zh-CN',home,()=>emailSettingsFixture,{actor});await tick()
 assert.equal(s.calls.length,1);assert.equal(s.calls[0].url,'/api/v1/stores/A/tenant-admin/customer-auth/email-settings');assert.equal(nodes(s.root,n=>n.tag==='form').length,1);assert.ok(nodes(s.root,n=>n.tag==='input'&&n.value==='smtp.example.test').length)
 const password=nodes(s.root,n=>n.tag==='input'&&n.props.type==='password')[0];assert.equal(password.value,'');const form=nodes(s.root,n=>n.tag==='form')[0];form.props.onSubmit({preventDefault(){}});form.props.onSubmit({preventDefault(){}});await tick();assert.equal(s.calls.filter(c=>c.method==='PUT').length,1);const body=s.calls.find(c=>c.method==='PUT').body;assert.equal(body.smtpPassword,null);assert.equal(body.smtpHost,'smtp.example.test');assert.equal(password.value,'');s.unmount()
})
test('standalone email page ignores old store responses and hides forms without administrator access',async()=>{
 for(const actor of [{...catalogActor,permissions:[]},{...catalogActor,roles:['store_manager'],permissions:['tenant.admin.manage']},{...catalogActor,storeIds:['B'],permissions:['tenant.admin.manage']}]){const denied=mount(EmailSettings,false,'zh-CN',home,()=>emailSettingsFixture,{actor});await tick();assert.equal(denied.calls.length,0);assert.equal(nodes(denied.root,n=>n.tag==='form').length,0);denied.unmount()}
 let finish;const actor={...catalogActor,storeIds:['A','B'],permissions:['tenant.admin.manage']},s=mount(EmailSettings,false,'zh-CN',home,url=>url.includes('/stores/A/')?new Promise(resolve=>finish=resolve):{...emailSettingsFixture,smtpHost:'b.example.test'},{actor});await tick();s.route.params.storeId='B';await tick();finish({...emailSettingsFixture,smtpHost:'a.example.test'});await tick();assert.ok(nodes(s.root,n=>n.tag==='input'&&n.value==='b.example.test').length);assert.equal(nodes(s.root,n=>n.tag==='input'&&n.value==='a.example.test').length,0);s.unmount()
})
test('email configuration load failures show retry without an editable default form',async()=>{
 const actor={...catalogActor,permissions:['tenant.admin.manage']};let fail=true
 const failed=mount(EmailSettings,false,'zh-CN',home,()=>{if(fail)throw new Error('offline');return emailSettingsFixture},{actor});await tick();assert.equal(nodes(failed.root,n=>n.tag==='form').length,0);assert.ok(button(failed.root,'重新加载'));fail=false;button(failed.root,'重新加载').props.onClick();await tick();assert.equal(nodes(failed.root,n=>n.tag==='form').length,1);failed.unmount()
})

test('storage settings expose shared activation and email entries only for scoped tenant administrators',async()=>{
 for(const [actor,allowed] of [[{...catalogActor,permissions:['tenant.admin.manage']},true],[{...catalogActor,permissions:[]},false],[{...catalogActor,roles:['store_manager'],permissions:['tenant.admin.manage']},false],[{...catalogActor,storeIds:['B'],permissions:['tenant.admin.manage']},false]]){
  const s=mount(StaffPage,false,'zh-CN',home,catalogResponse,{actor});await tick();button(s.root,'设置').props.onClick();await tick();const links=nodes(s.root,n=>n.tag==='a'&&String(n.props.href).includes('accessSource='));assert.equal(links.length,allowed?2:0);if(allowed){assert.ok(links.some(n=>n.props.href.includes('/stores/A/admin/customer-activation?')));assert.ok(links.some(n=>n.props.href.includes('/stores/A/admin/customer-email?')))}s.unmount()
 }
})

test('embedded wine workspace keeps one title and groups search and actions without changing the full catalog',async()=>{
 const actor={...catalogActor,permissions:[...catalogActor.permissions,'tenant.admin.manage']}
 const s=mount(StaffPage,false,'zh-CN',home,url=>url.includes('/storage-products?')?{items:catalogProducts,total:2,page:0,size:12,hasMore:false}:catalogResponse(url),{actor});await tick();button(s.root,'设置').props.onClick();await tick();button(s.root,'管理寄存酒商品').props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='h2'&&text(n).trim()==='存酒设置').length,0);assert.equal(nodes(s.root,n=>n.tag==='h2'&&text(n).trim()==='商品列表').length,0);assert.equal(nodes(s.root,n=>n.tag==='h2'&&text(n).trim()==='酒品管理').length,1)
 const toolbar=nodes(s.root,n=>String(n.props?.class).includes('catalog-storage-toolbar'))[0];assert.ok(toolbar);assert.ok(nodes(toolbar,n=>n.tag==='input').length);assert.match(text(toolbar),/匹配商品/);assert.ok(button(s.root,'＋ 新增酒品'));assert.ok(nodes(s.root,n=>n.tag==='details'&&nodes(n,c=>c.tag==='summary').length).length);s.unmount()
 const generic=mount(CatalogPage,false,'zh-CN',home,catalogResponse,{actor:catalogActor});await tick();assert.ok(button(generic.root,'分类管理'));assert.equal(nodes(generic.root,n=>n.tag==='h2'&&text(n).trim()==='商品列表').length,1);generic.unmount()
})

test('new storage wine defaults to enabled wine category and lets the server allocate a short code',async()=>{
 const category={productLines:['wine_storage'],id:'wine-default',code:'WINE',nameZh:'酒水',nameEn:'Wine',enabled:true,version:0,sortOrder:0,productLines:['wallet','wine_storage']}
 const s=mount(CatalogPage,false,'zh-CN',home,(url,options)=>url.includes('/storage-products?')?{items:[],total:0,page:0,size:12,hasMore:false}:url.endsWith('/categories')?[{...category,id:'disabled',enabled:false},category]:catalogResponse(url,options),{actor:catalogActor,props:{storageOnly:true,embedded:true}});await tick();button(s.root,'＋ 新增酒品').props.onClick();await tick()
 const code=nodes(s.root,n=>n.tag==='input'&&n.props.maxlength==='80')[0];assert.equal(code.props.required,false);assert.equal(code.value,'');assert.match(text(s.root),/留空自动生成/);assert.ok(nodes(s.root,n=>n.tag==='option'&&n.props.value==='wine-default').length)
 const name=nodes(nodes(s.root,n=>n.tag==='label'&&text(n).trim()==='中文名称')[0],n=>n.tag==='input')[0];name.props['onUpdate:modelValue']('自动编号酒');nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.equal(s.calls.find(c=>c.method==='POST').body.product.code,'');assert.equal(s.calls.find(c=>c.method==='POST').body.product.categoryId,'wine-default');s.unmount()
})

test('storage editor defaults to bottles and preserves advanced values through collapse and save',async()=>{
 const s=mount(CatalogPage,false,'zh-CN',home,(url,options)=>url.includes('/storage-products?')?{items:[],total:0,page:0,size:12,hasMore:false}:catalogResponse(url,options),{actor:catalogActor,props:{storageOnly:true,embedded:true}});await tick();button(s.root,'＋ 新增酒品').props.onClick();await tick()
 const field=label=>nodes(nodes(s.root,n=>n.tag==='label'&&(text(n).trim()||n.props['aria-label']||'').startsWith(label))[0],n=>n.tag==='input')[0]
 assert.equal(field('单位').value,'瓶');const extra=nodes(s.root,n=>n.tag==='details'&&String(n.props.class).includes('catalog-editor-extra'))[0];assert.ok(extra);assert.equal(extra.props.open,false);assert.ok(nodes(extra,n=>n.tag==='input'&&n.props.inputmode==='decimal').length)
 nodes(s.root,n=>n.tag==='form')[0].props.onInvalidCapture();await tick();assert.equal(extra.props.open,true);field('价格 (SGD)').props['onUpdate:modelValue']('12.50');field('编号').props['onUpdate:modelValue']('NEW-BOTTLE');field('中文名称').props['onUpdate:modelValue']('快速新增');nodes(s.root,n=>n.tag==='select')[0].props['onUpdate:modelValue']('cat');extra.props.onToggle({target:{open:false}});await tick();nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick()
 const body=s.calls.find(c=>c.method==='POST').body;assert.equal(body.product.unit,'瓶');assert.equal(body.product.priceCents,1250);assert.deepEqual(body.product.channels,['storage']);s.unmount()
})

test('storage settings group compact product, mode and secondary settings without changing save commands',async()=>{
 const actor={...catalogActor,permissions:[...catalogActor.permissions,'tenant.admin.manage']},data={...home,permissions:[...home.permissions,'wine_storage.rules.manage','wine_storage.custodians.manage']}
 const s=mount(StaffPage,false,'zh-CN',data,catalogResponse,{actor});await tick();button(s.root,'设置').props.onClick();await tick();assert.ok(nodes(s.root,n=>String(n.props?.class).includes('storage-settings-grid')).length);assert.ok(nodes(s.root,n=>String(n.props?.class).includes('storage-settings-product-entry')).length)
 const form=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-mode-form'))[0];assert.ok(form);nodes(form,n=>n.tag==='select')[0].props['onUpdate:modelValue']('staff_registration');await tick();form.props.onSubmit({preventDefault(){}});await tick();assert.deepEqual(s.calls.find(c=>c.url.endsWith('/settings')&&c.method==='POST').body.operationMode,'staff_registration');s.unmount()
})

test('wine manager creates through the original editor with storage selected and blocks duplicate saves',async()=>{
 let created=null,finish
 const actor={...catalogActor,permissions:[...catalogActor.permissions,'tenant.admin.manage']}
 const s=mount(StaffPage,false,'zh-CN',home,(url,options)=>url.includes('/storage-products?')?{items:created?[created]:[],total:created?1:0,page:0,size:12,hasMore:false}:url.endsWith('/products/editor')&&options?.method==='POST'?new Promise(resolve=>finish=()=>{const body=JSON.parse(options.body);created={...catalogProducts[0],...body.product,id:'new-wine'};resolve({product:created,barcodes:body.barcodes})}):catalogResponse(url,options),{actor});await tick()
 button(s.root,'设置').props.onClick();await tick();button(s.root,'管理寄存酒商品').props.onClick();await tick();assert.ok(button(s.root,'＋ 新增酒品'));button(s.root,'＋ 新增酒品').props.onClick();await tick()
 assert.deepEqual(nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox'&&n.checked).map(n=>n.props.value),['storage']);assert.equal(button(s.root,'返回存酒设置').props.disabled,true)
 const field=label=>nodes(nodes(s.root,n=>n.tag==='label'&&(text(n).trim()||n.props['aria-label']||'').startsWith(label))[0],n=>n.tag==='input')[0]
 field('编号').props['onUpdate:modelValue']('NEW-WINE');field('中文名称').props['onUpdate:modelValue']('新增寄存酒');nodes(s.root,n=>n.tag==='select')[0].props['onUpdate:modelValue']('cat');await tick()
 const form=nodes(s.root,n=>n.tag==='form')[0];form.props.onSubmit({preventDefault(){}});form.props.onSubmit({preventDefault(){}});await tick();assert.equal(s.calls.filter(c=>c.method==='POST').length,1);assert.equal(button(s.root,'保存商品与条码').props.disabled,true)
 const command=s.calls.find(c=>c.method==='POST');assert.equal(command.url,'/api/v1/stores/A/catalog/products/editor');assert.deepEqual(command.body.product.channels,['storage']);assert.equal(command.body.product.nameZh,'新增寄存酒');assert.ok(command.body.idempotencyKey);finish();await tick();assert.match(text(s.root),/商品信息与条码已保存/)
 button(s.root,'← 返回商品列表').props.onClick();await tick();assert.match(text(s.root),/新增寄存酒/);assert.ok(button(s.root,'＋ 新增酒品'));assert.ok(!s.calls.some(c=>c.url.endsWith('/catalog/products')));s.unmount()
})

test('wine creation defaults do not override existing channels and cancelling a clean new draft needs no discard',async()=>{
 const existing={...catalogProducts[0],unit:'杯',channels:['storage','shopping']}
 const s=mount(CatalogPage,false,'en-SG',home,(url,options)=>url.includes('/storage-products?')?{items:[existing],total:1,page:0,size:12,hasMore:false}:url.endsWith('/editor')?{product:existing,barcodes:[]}:catalogResponse(url,options),{actor:catalogActor,props:{storageOnly:true,embedded:true}});await tick()
 button(s.root,'＋ New wine').props.onClick();await tick();assert.deepEqual(nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox'&&n.checked).map(n=>n.props.value),['storage']);button(s.root,'← Back to products').props.onClick();await tick();assert.equal(nodes(s.root,n=>n.props?.role==='dialog').length,0)
 button(s.root,'Edit').props.onClick();await tick();assert.ok(nodes(s.root,n=>n.tag==='input'&&n.value==='杯').length);assert.deepEqual(nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox'&&n.checked).map(n=>n.props.value),['shopping','storage']);assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
})

test('storage settings open an embedded storage-only catalog and reuse editing and store enable commands',async()=>{
 const actor={...catalogActor,permissions:[...catalogActor.permissions,'tenant.admin.manage']};let disabled=false;const working=catalogProducts.map((p,i)=>({...p,listingEnabled:i===0}))
 const s=mount(StaffPage,false,'zh-CN',home,(url,options)=>url.includes('/storage-products?')?{items:working.map(p=>({...p})),total:2,page:0,size:12,hasMore:false}:url.endsWith('/listing')?(disabled=true,{...working[0],listingEnabled:false,listingVersion:1}):url.endsWith('/editor')&&options?.method==='PUT'?(working[0]={...working[0],...JSON.parse(options.body).product,version:1,listingVersion:1},{product:working[0],barcodes:JSON.parse(options.body).barcodes}):catalogResponse(url,options),{actor});await tick();button(s.root,'设置').props.onClick();await tick()
 assert.ok(button(s.root,'管理寄存酒商品'));assert.ok(!s.calls.some(c=>c.url.includes('/catalog/')))
 button(s.root,'管理寄存酒商品').props.onClick();await tick();assert.ok(s.calls.some(c=>c.url.includes('/stores/A/catalog/storage-products?')));assert.ok(!s.calls.some(c=>c.url.endsWith('/catalog/products')));assert.match(text(s.root),/酒品1|酒品2/);assert.ok(button(s.root,'分类管理'));assert.equal(button(s.root,'＋ 新增商品'),undefined);assert.ok(button(s.root,'＋ 新增酒品'))
 button(s.root,'编辑').props.onClick();await tick();assert.ok(s.calls.some(c=>c.url.endsWith('/products/wine-a/editor')));assert.equal(button(s.root,'返回存酒设置').props.disabled,true)
 const editor=nodes(s.root,n=>n.tag==='form'&&nodes(n,l=>l.tag==='label'&&text(l).includes('中文名称')).length)[0];const nameLabel=nodes(editor,n=>n.tag==='label'&&text(n).includes('中文名称'))[0];nodes(nameLabel,n=>n.tag==='input')[0].props['onUpdate:modelValue']('维护后酒名');editor.props.onSubmit({preventDefault(){}});await tick();assert.equal(s.calls.find(c=>c.method==='PUT').body.product.nameZh,'维护后酒名');assert.equal(working[0].nameZh,'维护后酒名');button(s.root,'← 返回商品列表').props.onClick();await tick();assert.match(text(s.root),/维护后酒名/);button(s.root,'上下架管理').props.onClick();await tick();button(s.root,'本店停用').props.onClick();await tick();button(s.root,'确认').props.onClick();await tick();assert.ok(disabled);const write=s.calls.find(c=>c.url.endsWith('/listing'));assert.deepEqual(write.body.channels,['storage']);assert.equal(write.body.enabled,false);assert.equal(write.body.expectedVersion,1);s.unmount()
})
test('wine catalog entry requires tenant administrator catalog access and keeps original catalog scope intact',async()=>{
 for(const actor of [{...catalogActor,roles:['store_manager']},{...catalogActor,permissions:['tenant.admin.manage']}]){
  const s=mount(StaffPage,false,'zh-CN',home,catalogResponse,{actor});await tick();button(s.root,'设置').props.onClick();await tick();assert.equal(button(s.root,'管理寄存酒商品'),undefined);assert.ok(!s.calls.some(c=>c.url.includes('/catalog/')));s.unmount()
 }
 const s=mount(CatalogPage,false,'zh-CN',home,catalogResponse,{actor:catalogActor});await tick();assert.ok(s.calls.some(c=>c.url.endsWith('/catalog/products')));assert.ok(!s.calls.some(c=>c.url.includes('/storage-products')));s.unmount()
})
test('embedded wine search paginates on the server and resets store context without showing unrelated products',async()=>{
 const actor={...catalogActor,storeIds:['A','B'],permissions:[...catalogActor.permissions,'tenant.admin.manage']}
 const respond=(url,options)=>url.includes('/storage-products?')?{items:[catalogProducts[0]],total:13,page:Number(new URL(url,'http://localhost').searchParams.get('page')),size:12,hasMore:!url.includes('page=1')}:catalogResponse(url,options)
 const s=mount(StaffPage,false,'zh-CN',home,respond,{actor});await tick();button(s.root,'设置').props.onClick();await tick();button(s.root,'管理寄存酒商品').props.onClick();await tick()
 const form=nodes(s.root,n=>n.tag==='form'&&nodes(n,b=>b.tag==='button'&&text(b)==='查询').length)[0];nodes(form,n=>n.tag==='input')[0].props['onUpdate:modelValue']('酒%_');form.props.onSubmit({preventDefault(){}});await tick();assert.ok(s.calls.some(c=>c.url.includes('query='+encodeURIComponent('酒%_'))&&c.url.includes('page=0')))
 nodes(form,n=>n.tag==='input')[0].props['onUpdate:modelValue']('尚未查询');button(s.root,'下一页').props.onClick();await tick();assert.ok(s.calls.some(c=>c.url.includes('query='+encodeURIComponent('酒%_'))&&c.url.includes('page=1')))
 s.route.params.storeId='B';await tick();assert.doesNotMatch(text(s.root),/酒品1/);button(s.root,'管理寄存酒商品').props.onClick();await tick();assert.ok(s.calls.some(c=>c.url.startsWith('/api/v1/stores/B/catalog/storage-products?')&&c.url.includes('query=&page=0')));s.unmount()
})
test('storage product manager remains available with storage closed and clears failed results without claiming zero matches',async()=>{
 const actor={...catalogActor,permissions:['catalog.view']};let fail=true
 const s=mount(StaffPage,false,'zh-CN',{...home,newBusinessEnabled:false},url=>url.includes('/storage-products?')?{items:catalogProducts,total:2,page:0,size:12,hasMore:false}:catalogResponse(url),{actor});await tick();button(s.root,'设置').props.onClick();await tick();const normalFetch=globalThis.fetch;globalThis.fetch=async(url,options)=>fail&&url.includes('/storage-products?')?{ok:false,status:503,json:async()=>({code:'TEMP',message:'查询失败'})}:normalFetch(url,options);button(s.root,'管理寄存酒商品').props.onClick();await tick();await tick();assert.match(text(s.root),/查询失败/);assert.doesNotMatch(text(s.root),/匹配商品: 0/)
 fail=false;const refreshes=nodes(s.root,n=>n.tag==='button'&&(text(n)==='刷新'||n.props['aria-label']==='刷新'));refreshes.at(-1).props.onClick();await tick();assert.match(text(s.root),/酒品1/);assert.equal(button(s.root,'编辑'),undefined);assert.equal(button(s.root,'＋ 新增酒品'),undefined);button(s.root,'上下架管理').props.onClick();await tick();assert.equal(button(s.root,'本店停用'),undefined);s.unmount()
})
function catalogResponse(url,options){if(url.endsWith('/categories'))return[{id:'cat',code:'C',nameZh:'酒类',nameEn:'Wine',enabled:true,version:0,sortOrder:0,productLines:['wallet','wine_storage']}];if(url.endsWith('/products'))return catalogProducts;if(url.endsWith('/editor')){const body=options?.body?JSON.parse(options.body):null;return{product:body?{...catalogProducts[0],...body.product,version:1,listingVersion:1}:catalogProducts[0],barcodes:body?body.barcodes:['06956367311355']}}return home}
function button(root,label){return nodes(root,n=>n.tag==='button'&&(text(n).trim()||n.props['aria-label']||'').startsWith(label))[0]}
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
 const s=mount(StaffPage,false,'zh-CN',data,url=>url.includes('/products')?[]:data,{...lists,query:{task:'inbox'}});await tick();const original=globalThis.fetch;globalThis.fetch=async(url,options)=>url.endsWith('/products/wine')?{ok:false,status:409,json:async()=>({code:'CATALOG_UNAVAILABLE',message:'unavailable'})}:original(url,options)
 button(s.root,'审核并核对收存').props.onClick();await tick();assert.match(text(s.root),/原选商品未开启寄存酒或已停用/);assert.equal(nodes(s.root,n=>n.tag==='option'&&n.props.value==='wine').length,0);assert.equal(button(s.root,'确认实际收存').props.disabled,true);assert.equal(s.calls.filter(c=>c.method==='POST').length,0)
})


test('eligible original wine outside the keyword result is verified by id before preserving selection',async()=>{
 const product={id:'wine',categoryId:'category',nameZh:'仍可寄存的酒',nameEn:'Eligible wine',code:'WINE'},application={id:'valid-request',customerId:'customer',status:'pending',lines:[{id:'line',productId:'wine',nameZh:'旧申请名称',nameEn:'Old name',quantity:1,opened:false,source:'customer',photos:[],note:''}],createdAt:new Date().toISOString(),reason:'',confirmation:null},data={...home,applications:[application],rules:[{id:'rule',categoryId:'category',name:'规则',months:3,version:1}],categories:[{id:'category',nameZh:'酒品'}]},lists={pending:[{application,customer:{id:'customer',displayName:'顾客',phone:'+6580000000'}}]}
 const s=mount(StaffPage,false,'zh-CN',data,url=>url.endsWith('/products/wine')?product:url.includes('/products')?[]:data,{...lists,query:{task:'inbox'}});await tick();button(s.root,'审核并核对收存').props.onClick();await tick();assert.ok(s.calls.some(c=>c.url.endsWith('/products/wine')));assert.ok(nodes(s.root,n=>String(n.props?.class).includes('storage-selected-wine')).length);assert.match(text(s.root),/仍可寄存的酒/);assert.doesNotMatch(text(s.root),/原选商品未开启寄存酒或已停用/)
})

test('legacy multiple barcodes require explicit selection and preserve the full save snapshot',async()=>{
 const old=['06956367311355','036000291452'],s=mount(CatalogPage,false,'zh-CN',home,(url,options)=>url.endsWith('/editor')&&(!options?.method||options.method==='GET')?{product:catalogProducts[0],barcodes:old}:catalogResponse(url,options),{actor:catalogActor});await tick();button(s.root,'编辑').props.onClick();await tick();assert.match(text(s.root),/只能保留一个条码/);nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.equal(s.calls.filter(c=>c.method!=='GET').length,0);nodes(s.root,n=>n.tag==='button'&&text(n).includes('保留此条码'))[1].props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.inputmode==='numeric').length,1);nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();const save=s.calls.find(c=>c.method==='PUT');assert.deepEqual(save.body.barcodes,['036000291452']);assert.deepEqual(save.body.expectedBarcodes,old)
})

test('new product always has one editable optional barcode field without add buttons',async()=>{
 const s=mount(CatalogPage,false,'zh-CN',home,catalogResponse,{actor:catalogActor});await tick();button(s.root,'＋ 新增商品').props.onClick();await tick();const fields=nodes(s.root,n=>n.tag==='input'&&n.props.inputmode==='numeric');assert.equal(fields.length,1);assert.equal(fields[0].value,'');assert.ok(button(s.root,'扫码填写'));assert.ok(!button(s.root,'＋ 添加条码'));assert.deepEqual(nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox'&&n.checked).map(n=>n.props.value),['cards']);assert.ok(nodes(s.root,n=>n.tag==='input'&&n.value==='件').length);assert.equal(nodes(s.root,n=>n.tag==='details'&&String(n.props?.class).includes('catalog-editor-extra')).length,0)
})

test('customer wallet hides unopened empty storage but retains closed product history',async()=>{
 const empty={...home,newBusinessEnabled:false,items:[],applications:[],intents:[],events:[]};const hidden=mount(Summary,true,'zh-CN',empty);await tick();assert.equal(nodes(hidden.root,n=>n.tag==='a').length,0);hidden.unmount();
 for(const kind of ['items','applications','intents','events']){const shown=mount(Summary,true,'zh-CN',{...empty,[kind]:kind==='items'?[{id:'old',status:'ended',remaining:0}]:[{id:'old',status:'completed'}]});await tick();assert.equal(nodes(shown.root,n=>n.tag==='a').length,1);shown.unmount()}
})

 test('universal scanner hands a customer to bottle collection without scanning again',async()=>{
 const person={id:'handoff-customer',displayName:'扫码顾客',phone:'80000000'}
 setScanHandoff({store:'A',actor:'handoff-staff',target:'storage',code:'WI.'+'A'.repeat(43),customerId:person.id,task:'withdraw'})
 const s=mount(StaffPage,false,'zh-CN',home,url=>url.endsWith('/customers/'+person.id)?person:home,{actor:{accountId:'handoff-staff',roles:[],permissions:home.permissions}});await tick()
 assert.match(text(s.root),/扫码顾客/);assert.equal(nodes(s.root,n=>n.tag==='button'&&n.props['aria-pressed']===true&&!n.props.class?.includes('frontend-locale-switcher')&&text(n).trim()==='取酒').length,1)
 assert.equal(s.calls.filter(c=>c.url.endsWith('/customers/'+person.id)).length,1);assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
 })


test('closed merchant storage opens records and removes new-receipt controls while retaining pickup history',async()=>{
 const s=mount(StaffPage,false,'zh-CN',{...home,newBusinessEnabled:false});await tick()
 assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='存酒').length,0)
 assert.doesNotMatch(text(s.root),/直接帮顾客登记存酒/)
 assert.match(text(s.root),/记录/)
 assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
})


test('pickup cards replace duplicate selection steps with bounded plus and minus controls on both sides',async()=>{
 const item={id:'stepper-bottle',applicationId:'application',customerId:'customer',snapshot:{nameZh:'青岛啤酒',nameEn:'Beer',timezone:'Asia/Singapore',receiptPhotos:[]},quantity:2,remaining:2,opened:false,status:'stored',location:'W',label:'08',expiresAt:new Date(Date.now()+86400000).toISOString()},person={id:'customer',displayName:'测试顾客',phone:'8000'}
 for(const customer of [true,false]){
  const s=mount(customer?CustomerPage:StaffPage,customer,'zh-CN',{...home,items:[item]},url=>url.includes('/customers?')?[person]:url.includes('/product-options')?{items:[],categories:[],hasMore:false}:{...home,items:[item]})
  await tick()
  if(!customer){const form=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-merchant-customer-search'))[0];nodes(form,n=>n.tag==='input')[0].props.onInput({target:{value:'8000'}});await form.props.onSubmit({preventDefault(){}});await tick();assert.ok(button(s.root,'更换顾客'));await tick()}
  nodes(s.root,n=>n.tag==='button'&&text(n).trim()===(customer?'我要取酒':'取酒'))[0].props.onClick();await tick()
  assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox').length,0)
  assert.doesNotMatch(text(s.root),/下一步：核对领取清单|请先选择要取的酒品和有效瓶数/)
  const minus=()=>nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='减少瓶数')[0],plus=()=>nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='增加瓶数')[0],input=()=>nodes(s.root,n=>n.tag==='input'&&n.props.type==='number')[0]
  assert.equal(minus().props.disabled,true);assert.equal(input().props.value,0);plus().props.onClick();await tick();assert.equal(input().props.value,1);plus().props.onClick();await tick();assert.equal(input().props.value,2);assert.equal(plus().props.disabled,true);minus().props.onClick();await tick();assert.equal(input().props.value,1)
  assert.equal(button(s.root,'查看取酒车').props.disabled,false)
  s.unmount()
 }
})


test('cart preserves a now-invalid quantity after stock refresh and requires explicit adjustment',async()=>{
 const item={id:'limit-bottle',snapshot:{nameZh:'啤酒',timezone:'Asia/Singapore'},remaining:3,opened:false,status:'stored',expiresAt:null},data={...home,items:[item]};const s=mount(CustomerPage,true,'zh-CN',data,url=>url.includes('/product-options')?{items:[],categories:[],hasMore:false}:{...data});await tick();button(s.root,'我要取酒').props.onClick();await tick()
 const submit=await preparePickupCart(s.root,3);assert.equal(submit.props.disabled,false);data.items=[{...item,remaining:1}];await nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='刷新')[0].props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.type==='number')[0].props.value,3);assert.equal(button(s.root,'提交取酒').props.disabled,true);assert.match(text(s.root),/数量或状态已变化/)
 nodes(s.root,n=>n.tag==='input'&&n.props.type==='number')[0].props.onInput({target:{value:'1'}});await tick();assert.equal(button(s.root,'提交取酒').props.disabled,false);data.items=[{...item,opened:true}];await nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='刷新')[0].props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.type==='number')[0].props.max,1);s.unmount()
})

test('default quick mode binds quantity and prevents duplicate direct handovers without customer scanning',async()=>{
 const item={id:'merchant-quick',applicationId:'application',customerId:'customer',snapshot:{nameZh:'啤酒',nameEn:'Beer',timezone:'Asia/Singapore'},quantity:3,remaining:3,opened:false,status:'stored',expiresAt:new Date(Date.now()+86400000).toISOString()},person={id:'customer',displayName:'快捷取酒顾客',phone:'8000'},data={...home,operationMode:'customer_verification',items:[item],intents:[]}
 let resolve;const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>url.includes('/customers?')?[person]:options?.method==='POST'?new Promise(r=>resolve=r):data);await tick()
 const form=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-merchant-customer-search'))[0];nodes(form,n=>n.tag==='input')[0].props.onInput({target:{value:'8000'}});await form.props.onSubmit({preventDefault(){}});await tick();assert.ok(button(s.root,'更换顾客'));await tick();button(s.root,'取酒').props.onClick();await tick()
 const confirm=await preparePickupCart(s.root,2);confirm.props.onClick();confirm.props.onClick();await tick()
 const posts=s.calls.filter(c=>c.method==='POST');assert.equal(posts.length,1);assert.ok(posts[0].url.endsWith('/direct-deliver'));assert.equal(posts[0].body.customerId,'customer');assert.deepEqual(posts[0].body.selections,[{itemId:item.id,quantity:2}]);assert.ok(posts[0].body.idempotencyKey)
 resolve({id:'done',customerId:'customer',origin:'staff',status:'completed',code:null,selections:posts[0].body.selections});await tick();assert.match(text(s.root),/已完成实物交付/);assert.equal(nodes(s.root,n=>n.tag==='figure').length,0)
})

test('pending legacy merchant batch recovery allows staff handover without displaying an authorization QR',async()=>{
 const item={id:'legacy-one',applicationId:'application',customerId:'customer',snapshot:{nameZh:'啤酒',nameEn:'Beer',timezone:'Asia/Singapore'},quantity:3,remaining:3,opened:false,status:'stored',expiresAt:new Date(Date.now()+86400000).toISOString()},other={...item,id:'legacy-two'},person={id:'customer',displayName:'旧单顾客',phone:'8000'},intent={id:'legacy-batch',customerId:'customer',origin:'staff',status:'awaiting_authorization',code:'WS.'+'L'.repeat(43),expiresAt:new Date(Date.now()+300000).toISOString(),selections:[{itemId:item.id,quantity:1},{itemId:other.id,quantity:1}]},data={...home,items:[item,other],intents:[{...intent,code:null}]}
 globalThis.sessionStorage.setItem('rpb.storage.pending:false:A',JSON.stringify({path:'/intents',body:{customerId:'customer',selections:intent.selections,idempotencyKey:'original-batch-key'}}))
 const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>url.endsWith('/customers/customer')?person:options?.method==='POST'?intent:data);await tick();await nodes(s.root,n=>n.tag==='button'&&text(n)==='查询上次操作结果')[0].props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='figure').length,0);assert.equal(s.calls.filter(c=>c.method==='POST').length,1);assert.equal(s.calls.find(c=>c.method==='POST').body.idempotencyKey,'original-batch-key');assert.equal(button(s.root,'确认实物已交付').props.disabled,false);assert.doesNotMatch(text(s.root),/下一步：核对领取清单/);button(s.root,'确认实物已交付').props.onClick();await tick();assert.ok(s.calls.some(c=>c.method==='POST'&&c.url.endsWith('/intents/legacy-batch/deliver')));assert.ok(!s.calls.some(c=>c.url.endsWith('/authorize')));s.unmount()
})


test('receipt defaults to the front desk and accepts an empty shelf or an explicitly cleared location',async()=>{
 const product={id:'wine',categoryId:'category',nameZh:'白酒',nameEn:'Spirit',code:'WINE'},data={...home,rules:[{id:'rule',categoryId:'category',name:'三个月',months:3,version:1}],categories:[{id:'category',nameZh:'白酒'}]},application={id:'',customerId:'customer',status:'pending',lines:[{id:'line',productId:'wine',nameZh:'白酒',nameEn:'Spirit',quantity:1,opened:false,source:'customer',photos:[],note:''}]}
 for(const cleared of [false,true]){
  const s=mount(ReceiveForm,false,'zh-CN',data,(url,options)=>url.endsWith('/products/wine')?product:url.includes('/product-options')?{items:[product],categories:[],hasMore:false,page:0}:url.includes('/products')?[product]:options?.method==='POST'?{id:'received'}:data, {props:{api:storageApi('A',false),application,home:data,disabled:false,direct:true}});await tick()
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
  const s=mount(StaffPage,false,'zh-CN',home,undefined,{actor:{accountId:'actor',roles:[role],permissions:home.permissions}});await tick();button(s.root,'设置').props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='summary'&&text(n)==='常用存放位置管理').length,role==='tenant_admin'?1:0);s.unmount()
 }
})


test('mode settings are tenant-admin only and submit scoped versioned selection',async()=>{
 for(const role of ['tenant_admin','store_manager']){
  const data={...home,operationMode:'customer_verification',settingsVersion:0},s=mount(StaffPage,false,'zh-CN',data,undefined,{actor:{accountId:'actor',roles:[role],permissions:home.permissions}});await tick()
  assert.equal(button(s.root,'保存存取酒模式'),undefined);button(s.root,'设置').props.onClick();await tick();
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
 const form=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-merchant-customer-search'))[0];nodes(form,n=>n.tag==='input')[0].props.onInput({target:{value:'8000'}});await form.props.onSubmit({preventDefault(){}});await tick();assert.ok(button(s.root,'更换顾客'));await tick();button(s.root,'取酒').props.onClick();await tick()
 assert.equal(button(s.root,'扫描顾客取酒码并核销'),undefined);assert.equal(button(s.root,'生成顾客确认码'),undefined)
 const confirm=await preparePickupCart(s.root,2);assert.equal(s.calls.filter(c=>c.method==='POST').length,0);
 confirm.props.onClick();confirm.props.onClick();await tick()
 const post=s.calls.filter(c=>c.method==='POST');assert.equal(post.length,1);assert.ok(post[0].url.endsWith('/direct-deliver'));assert.equal(post[0].body.customerId,'customer');assert.deepEqual(post[0].body.selections,[{itemId:item.id,quantity:2}]);assert.ok(post[0].body.idempotencyKey)
 data.items=[{...item,remaining:1}];resolve({id:'done',customerId:'customer',origin:'staff',status:'completed',code:null,selections:post[0].body.selections});await tick();assert.match(text(s.root),/已完成实物交付/);assert.equal(nodes(s.root,n=>n.tag==='figure').length,0);s.unmount()
})
test('customer direct mode shows staff service notice and records without new self service forms',async()=>{
 const s=mount(CustomerPage,true,'zh-CN',{...home,operationMode:'staff_registration'});await tick();assert.match(text(s.root),/无需您扫码核销/)
 button(s.root,'我要取酒').props.onClick();await tick();assert.equal(button(s.root,'出码取酒'),undefined);assert.match(text(s.root),/联系门店办理/)
 button(s.root,'我的记录').props.onClick();await tick();assert.doesNotMatch(text(s.root),/无需您扫码核销/);s.unmount()
})

test('receipt without photos saves after required wine and consent, and explains missing fields',async()=>{
 const product={id:'wine',categoryId:'category',nameZh:'白酒',nameEn:'Spirit',code:'WINE'},data={...home,rules:[{id:'rule',categoryId:'category',name:'三个月',months:3,version:1}],categories:[{id:'category',nameZh:'白酒'}]},application={id:'',customerId:'customer',status:'pending',lines:[{id:'line',productId:null,nameZh:'',nameEn:'',quantity:1,opened:false,source:'customer',photos:[],note:''}]}
 const s=mount(ReceiveForm,false,'zh-CN',data,(url,options)=>url.endsWith('/products/wine')?product:url.includes('/product-options')?{items:[product],categories:[],hasMore:false,page:0}:url.includes('/products')?[product]:options?.method==='POST'?{status:'stored'}:data,{props:{home:data,api:storageApi('A',false),application,direct:true,disabled:false}});await tick()
 assert.match(text(s.root),/选择酒品|选择已有酒品/);assert.match(text(s.root),/还需完成/)
 nodes(s.root,n=>n.tag==='button'&&text(n).includes('选择此酒品'))[0].props.onClick();await tick()
 const consent=nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox'&&n.props.required!==undefined)[0];consent.checked=true;consent.listeners.change({target:consent});await tick()
 const submit=button(s.root,'确认实物已收存');assert.equal(submit.props.disabled,false)
 await nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick()
 const post=s.calls.find(c=>c.method==='POST');assert.ok(post.url.endsWith('/registrations'));assert.deepEqual(post.body.lines[0].photos,[]);s.unmount()
})
test('customer may apply without a photograph after confirming storage terms',async()=>{
 const product={id:'wine',nameZh:'可寄存白酒',nameEn:'Spirit',code:'WINE'},s=mount(CustomerPage,true,'zh-CN',home,(url,options)=>url.includes('/product-options')?{items:[product],categories:[],hasMore:false}:url.includes('/product-options')?{items:[product],categories:[],hasMore:false,page:0}:url.includes('/products')?[product]:options?.method==='POST'?{id:'applied',lines:[],status:'pending'}:home);await tick()
 nodes(s.root,n=>n.tag==='button'&&text(n).includes('可寄存白酒'))[0].props.onClick();await tick()
 const accepted=nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0];accepted.checked=true;accepted.listeners.change({target:accepted});await tick();assert.equal(button(s.root,'提交寄存申请').props.disabled,false)
 await nodes(s.root,n=>n.tag==='form'&&n.props.class==='storage-customer-deposit')[0].props.onSubmit({preventDefault(){}});await tick();assert.deepEqual(s.calls.find(c=>c.method==='POST').body.lines[0].photos,[]);s.unmount()
})

test('settings are separated from storage tasks and return preserves customer work',async()=>{
 const s=mount(StaffPage,false);await tick();assert.doesNotMatch(text(s.root),/存取酒模式|保存规则设置/)
 button(s.root,'设置').props.onClick();await tick();assert.match(text(s.root),/存酒设置/);assert.equal(button(s.root,'查找顾客'),undefined)
 button(s.root,'← 返回存取酒').props.onClick();await tick();assert.ok(button(s.root,'查找顾客'));assert.doesNotMatch(text(s.root),/保存规则设置/);s.unmount()
})
test('add customer uses shared creation and selects the saved customer for storage',async()=>{
 const actor={accountId:'admin',roles:['tenant_admin'],permissions:[...home.permissions,'tenant.admin.manage'],storeIds:['A']},created={id:'new-customer',displayName:'新顾客',phoneE164:'+6581234567',status:'active'}
 const notice={version:'customer-profile-v2',textZh:'统一隐私告知',textEn:'Shared privacy notice',labelZh:'商户已取得同意',labelEn:'Consent confirmed'}
 const s=mount(StaffPage,false,'zh-CN',home,(url,options)=>url.endsWith('/customer-privacy-notice')?notice:url.endsWith('/tenant-admin/customers')&&options?.method==='POST'?{success:true,customer:created}:home,{actor});await tick()
 const search=nodes(s.root,n=>n.tag==='input'&&n.props.autocomplete==='off')[0];search.props.onInput({target:{value:'81234567'}});await tick();button(s.root,'＋ 新增顾客').props.onClick();await tick()
 assert.match(text(s.root),/统一隐私告知/);assert.ok(!s.calls.some(c=>c.url.includes('/tenant-admin/customers?')))
 const form=nodes(s.root,n=>n.tag==='form'&&n.props.class==='customer-quick-create-form')[0];nodes(form,n=>n.tag==='input'&&n.props.name==='displayName')[0].props.onUpdateModelValue?.('新顾客')
 await form.props.onSubmit({preventDefault(){}});await tick()
 const post=s.calls.find(c=>c.method==='POST');assert.ok(post.url.endsWith('/tenant-admin/customers'));assert.equal(post.body.phoneE164,'+6581234567');assert.deepEqual(post.body.privacyConsent,{accepted:true,noticeVersion:'customer-profile-v2',entrySource:'tenant_admin'})
 assert.match(text(s.root),/当前办理顾客.*新顾客/);assert.equal(button(s.root,'保存并选用顾客'),undefined);s.unmount()
})
test('staff and closed products do not gain customer creation rights',async()=>{
 for(const [role,enabled] of [['store_staff',true],['tenant_admin',false]]){
  const actor={roles:[role],permissions:[...home.permissions,'tenant.admin.manage'],storeIds:['A']};const s=mount(StaffPage,false,'zh-CN',{...home,newBusinessEnabled:enabled},undefined,{actor});await tick();assert.equal(button(s.root,'＋ 新增顾客'),undefined);s.unmount()
 }
})

test('quick customer creation preserves consent and rejects duplicate clicks while saving',async()=>{
 const actor={accountId:'admin',roles:['tenant_admin'],permissions:[...home.permissions,'tenant.admin.manage'],storeIds:['A']},notice={version:'v2',textZh:'告知',textEn:'Notice',labelZh:'已同意',labelEn:'Agreed'}
 let resolve
 const s=mount(StaffPage,false,'zh-CN',home,(url,options)=>url.endsWith('/customer-privacy-notice')?notice:options?.method==='POST'?new Promise(r=>resolve=r):home,{actor});await tick()
 nodes(s.root,n=>n.tag==='input'&&n.props.autocomplete==='off')[0].props.onInput({target:{value:'81234567'}});await tick();button(s.root,'＋ 新增顾客').props.onClick();await tick()
 const form=nodes(s.root,n=>n.tag==='form'&&n.props.class==='customer-quick-create-form')[0],check=nodes(form,n=>n.tag==='input'&&n.props.type==='checkbox')[0]
 check.checked=false;check.listeners.change({target:check});await tick();await form.props.onSubmit({preventDefault(){}});await tick();assert.equal(s.calls.filter(c=>c.method==='POST').length,0)
 check.checked=true;check.listeners.change({target:check});await tick();form.props.onSubmit({preventDefault(){}});form.props.onSubmit({preventDefault(){}});await tick();assert.equal(s.calls.filter(c=>c.method==='POST').length,1)
 assert.equal(button(s.root,'设置').props.disabled,true);resolve({success:true,customer:{id:'created',displayName:'新顾客',phoneE164:'+6581234567'}});await tick();assert.match(text(s.root),/当前办理顾客.*新顾客/);assert.ok(!s.calls.some(c=>/accounts|topups|bills/.test(c.url)));s.unmount()
})
test('late customer creation response cannot select a customer after changing stores',async()=>{
 const actor={accountId:'admin',roles:['tenant_admin'],permissions:[...home.permissions,'tenant.admin.manage'],storeIds:['A','B']}
 let resolve
 const s=mount(StaffPage,false,'zh-CN',home,(url,options)=>url.endsWith('/customer-privacy-notice')?{version:'v2',textZh:'告知',textEn:'Notice',labelZh:'同意',labelEn:'Agree'}:options?.method==='POST'?new Promise(r=>resolve=r):home,{actor});await tick()
 nodes(s.root,n=>n.tag==='input'&&n.props.autocomplete==='off')[0].props.onInput({target:{value:'81234567'}});await tick();button(s.root,'＋ 新增顾客').props.onClick();await tick()
 nodes(s.root,n=>n.tag==='form'&&n.props.class==='customer-quick-create-form')[0].props.onSubmit({preventDefault(){}});await tick();s.route.params.storeId='B';await tick()
 resolve({success:true,customer:{id:'old-store-created',displayName:'原店顾客',phoneE164:'+6581234567'}});await tick();assert.doesNotMatch(text(s.root),/原店顾客/);s.unmount()
})

test('duplicate phone keeps quick-create details and allows lookup of the existing customer',async()=>{
 const actor={accountId:'admin',roles:['tenant_admin'],permissions:[...home.permissions,'tenant.admin.manage'],storeIds:['A']}
 const s=mount(StaffPage,false,'zh-CN',home,url=>url.endsWith('/customer-privacy-notice')?{version:'v2',textZh:'告知',textEn:'Notice',labelZh:'同意',labelEn:'Agree'}:home,{actor});await tick()
 nodes(s.root,n=>n.tag==='input'&&n.props.autocomplete==='off')[0].props.onInput({target:{value:'81234567'}});await tick();button(s.root,'＋ 新增顾客').props.onClick();await tick()
 globalThis.fetch=async()=>({ok:false,status:409,text:async()=>JSON.stringify({success:false,error:{code:'CUSTOMER_PHONE_CONFLICT',messageKey:'conflict'}})})
 await nodes(s.root,n=>n.tag==='form'&&n.props.class==='customer-quick-create-form')[0].props.onSubmit({preventDefault(){}});await tick()
 assert.ok(button(s.root,'按此手机号查找已有顾客'));const requests=[]
 globalThis.fetch=async url=>{requests.push(url);return{ok:true,json:async()=>[{id:'existing',displayName:'已有顾客',phone:'+6581234567'}]}}
 button(s.root,'按此手机号查找已有顾客').props.onClick();await tick();assert.ok(requests.some(url=>url.includes('/wine-storage/customers?query=%2B6581234567')));assert.match(text(s.root),/已有顾客/);assert.equal(button(s.root,'保存并选用顾客'),undefined);s.unmount()
})

test('settings URL stays on settings after page initialization and supports closed-product rule history',async()=>{
 const s=mount(StaffPage,false,'zh-CN',{...home,newBusinessEnabled:false},undefined,{query:{task:'settings'}});await tick()
 assert.match(text(s.root),/存酒设置/);assert.equal(button(s.root,'查找顾客'),undefined);assert.equal(button(s.root,'保存存取酒模式'),undefined);assert.ok(nodes(s.root,n=>n.tag==='summary'&&text(n)==='保存规则设置').length)
 button(s.root,'← 返回存取酒').props.onClick();await tick();assert.ok(button(s.root,'报表'));assert.equal(button(s.root,'存酒'),undefined);s.unmount()
})




test('compact activation workspace searches the shared directory and isolates selected customer by store',async()=>{
 const actor={accountId:'admin',roles:['tenant_admin'],permissions:['tenant.admin.manage'],storeIds:['A','B']}
 const person={id:'activation-customer',activationState:'required',displayName:'测试顾客',customerCode:'C100',phoneE164:'+6591234567',status:'active'}
 const s=mount(ActivationWorkspace,false,'zh-CN',home,url=>({customers:[person],page:{limit:20,offset:0,total:1}}),{actor,query:{accessSource:'storage'}});await tick()
 assert.match(text(s.root),/到店激活码/);assert.match(text(s.root),/测试顾客/);assert.equal(nodes(s.root,n=>n.tag==='table').length,0)
 assert.equal(nodes(s.root,n=>n.tag==='a')[0].props.href,'/stores/A/wine-storage?task=settings')
 assert.ok(s.calls.every(c=>c.url.includes('/tenant-admin/customers')));assert.ok(!s.calls.some(c=>c.url.includes('privacy')))
 const search=nodes(s.root,n=>n.tag==='input')[0];search.props['onUpdate:modelValue']('91234567');await tick();nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick()
 assert.ok(s.calls.some(c=>c.url.includes('keyword=91234567')))
 button(s.root,'核实并激活').props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox').length,1);assert.ok(s.calls.every(c=>c.method==='GET'))
 s.route.params.storeId='B';await tick();assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox').length,0);assert.ok(s.calls.some(c=>c.url.includes('/stores/B/')));s.unmount()
 for(const candidate of [{...actor,storeIds:['B']},{...actor,roles:['store_staff']},{...actor,permissions:[]}]){
  const denied=mount(ActivationWorkspace,false,'zh-CN',home,()=>({customers:[],page:{limit:20,offset:0,total:0}}),{actor:candidate});await tick();assert.equal(denied.calls.length,0);assert.equal(nodes(denied.root,n=>n.tag==='form').length,0);denied.unmount()
 }
})

test('compact activation workspace shows loading error retry empty and English states',async()=>{
 const actor={accountId:'admin',roles:['tenant_admin'],permissions:['tenant.admin.manage'],storeIds:['A']};let fail=true
 const s=mount(ActivationWorkspace,false,'en-SG',home,()=>{if(fail)throw new Error('offline');return{customers:[],page:{limit:20,offset:0,total:0}}},{actor});await tick()
 assert.equal(nodes(s.root,n=>n.props?.role==='alert').length,1);fail=false;button(s.root,'Reload').props.onClick();await tick();assert.match(text(s.root),/No customers found/);assert.match(text(s.root),/6 digits/);s.unmount()
})


test('successful first issuance immediately marks verified even when refreshing the directory fails',async()=>{
 const actor={accountId:'admin',roles:['tenant_admin'],permissions:['tenant.admin.manage'],storeIds:['A']};let issued=false
 const person={id:'activation-flow',activationState:'required',displayName:'Test',customerCode:'C',phoneE164:'+6591234567',status:'active'}
 const s=mount(ActivationWorkspace,false,'zh-CN',home,(url,options)=>{
  if(url.endsWith('/activation-codes')){issued=true;return{success:true,code:'001234',expiresAt:new Date(Date.now()+600000).toISOString()}}
  if(issued)throw new Error('refresh offline')
  return{customers:[person],page:{limit:20,offset:0,total:1}}
 },{actor});await tick()
 button(s.root,'核实并激活').props.onClick();await tick()
 const check=nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0];check.checked=true;check.listeners.change({target:check});await tick()
 nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick()
 assert.ok(issued);button(s.root,'customerAuth.closeActivation').props.onClick();await tick()
 const verified=button(s.root,'customerAuth.alreadyVerified');assert.ok(verified);assert.equal(verified.props.disabled,true)
 assert.ok(button(s.root,'customerAuth.reissueActivationShort'));assert.equal(button(s.root,'核实并激活'),undefined);s.unmount()
})


test('compact storage header shows the current store once and switches only within the provided store list',async()=>{
 const s=mount(StaffPage,false,'zh-CN',{...home,stores:[{id:'A',name:'QA Store'},{id:'B',name:'Branch B'}]});await tick()
 const header=nodes(s.root,n=>n.tag==='header')[0]
 assert.equal(text(header).split('QA Store').length-1,1)
 const select=nodes(header,n=>n.tag==='select'&&n.props.class==='storage-header-switch')[0]
 assert.ok(select);assert.equal(select.props.value,'A');assert.match(text(select),/切换门店/);assert.doesNotMatch(text(select),/QA Store/)
 await select.props.onChange({target:{value:'B'}});assert.deepEqual(s.navigation,['/stores/B/wine-storage']);s.unmount()
})

function entryBrowser(t){const oldWin=globalThis.window,oldDoc=globalThis.document,win=new EventTarget(),doc=new EventTarget();doc.visibilityState='visible';globalThis.window=win;globalThis.document=doc;t.after(()=>{for(const app of mounted)app.unmount();mounted.clear();globalThis.window=oldWin;globalThis.document=oldDoc});t.mock.timers.enable({apis:['setInterval']});return{win,doc}}
test('wallet storage entry auto-syncs activation and closure, preserving only existing history for each identity',async t=>{
 const{win,doc}=entryBrowser(t)
 for(const customer of [true,false]){
  let data={...home,newBusinessEnabled:false,items:[],applications:[],intents:[],events:[]};const s=mount(Summary,customer,'zh-CN',undefined,()=>data);await tick();assert.equal(nodes(s.root,n=>n.tag==='a').length,0)
  data={...data,newBusinessEnabled:true};t.mock.timers.tick(30000);await tick();assert.equal(nodes(s.root,n=>n.tag==='a').length,1)
  doc.visibilityState='hidden';data={...data,newBusinessEnabled:false};t.mock.timers.tick(30000);await tick();assert.equal(nodes(s.root,n=>n.tag==='a').length,1)
  doc.visibilityState='visible';doc.dispatchEvent(new Event('visibilitychange'));await tick();assert.equal(nodes(s.root,n=>n.tag==='a').length,0)
  data={...data,events:[{id:'old'}]};win.dispatchEvent(new Event('focus'));await tick();assert.equal(nodes(s.root,n=>n.tag==='a').length,1)
  const count=s.calls.length;s.unmount();win.dispatchEvent(new Event('focus'));t.mock.timers.tick(30000);await tick();assert.equal(s.calls.length,count)
 }
})
test('storage entry clears after failed permission read and recovers, without overlapping or accepting late responses after unmount',async t=>{
 const{win}=entryBrowser(t);const s=mount(Summary,true);await tick();assert.equal(nodes(s.root,n=>n.tag==='a').length,1)
 let reject,calls=0;globalThis.fetch=()=>{calls++;return new Promise((_,no)=>reject=no)};win.dispatchEvent(new Event('focus'));await tick();t.mock.timers.tick(60000);win.dispatchEvent(new Event('focus'));await tick();assert.equal(calls,1)
 reject(Error('denied'));await tick();assert.equal(nodes(s.root,n=>n.tag==='a').length,0)
 globalThis.fetch=async()=>({ok:true,json:async()=>home});win.dispatchEvent(new Event('focus'));await tick();assert.equal(nodes(s.root,n=>n.tag==='a').length,1);s.unmount()
})

test('closed storage shortcuts open existing record views rather than a new deposit task',async()=>{
 const closed={...home,newBusinessEnabled:false};for(const customer of [true,false]){const s=mount(Summary,customer,'zh-CN',closed);await tick();assert.equal(nodes(s.root,n=>n.tag==='a')[0].props.href,customer?'/wallet/A/wine-storage?view=records':'/stores/A/wine-storage?task=records&history=1');s.unmount()}
})
test('customer storage refreshes commercial state while preserving historical records on closure',async t=>{
 const{win}=entryBrowser(t);let data=home;const s=mount(CustomerPage,true,'zh-CN',undefined,()=>data);await tick();assert.ok(nodes(s.root,n=>n.tag==='form').length)
 data={...home,newBusinessEnabled:false};win.dispatchEvent(new Event('focus'));await tick();assert.equal(nodes(s.root,n=>n.tag==='form').length,0);assert.match(text(s.root),/已关闭|停用|不可|未开通/);s.unmount()
})

test('storage shortcut clears during store and identity changes and rejects late previous-scope results',async()=>{
 const props=reactive({store:'A',customer:true}),s=mount(Summary,true,'zh-CN',home,undefined,{props});await tick();assert.equal(nodes(s.root,n=>n.tag==='a').length,1)
 const requests=[];globalThis.fetch=url=>new Promise(resolve=>requests.push({url,resolve}));props.store='B';await tick();assert.equal(nodes(s.root,n=>n.tag==='a').length,0);props.customer=false;await tick();assert.equal(requests.length,2)
 requests[1].resolve({ok:true,json:async()=>({...home,newBusinessEnabled:false,items:[],applications:[],intents:[],events:[]})});await tick();requests[0].resolve({ok:true,json:async()=>home});await tick();assert.equal(nodes(s.root,n=>n.tag==='a').length,0);assert.match(requests[0].url,/public\/stores\/B/);assert.doesNotMatch(requests[1].url,/public/);s.unmount()
})

test('rule editor saves unlimited explicitly and restores monthly draft in both languages',async()=>{
 for(const locale of ['zh-CN','en']){
  const data={...home,categories:[{id:'cat',nameZh:'酒水',nameEn:'Wine'}],rules:[{id:'rule',categoryId:'cat',name:'Rule',months:0,version:2}]}
  const s=mount(StaffPage,false,locale,data);await tick();button(s.root,locale==='en'?'Settings':'设置').props.onClick();await tick()
  assert.match(text(s.root),locale==='en'?/No time limit/:/不限时间/)
  const form=nodes(s.root,n=>n.tag==='form'&&nodes(n,k=>k.tag==='button'&&text(k)===(locale==='en'?'Save new version':'保存新版本')).length)[0]
  const selectValue=async(select,value)=>{select.options.forEach(o=>o.selected=o.props.value===value);select.listeners.change({target:select});await tick()}
  await selectValue(nodes(form,n=>n.tag==='select')[0],'cat')
  const name=nodes(form,n=>n.tag==='input'&&String(n.props.maxlength)==='200')[0];name.value='Rule';name.listeners.input({target:name});await tick()
  const months=nodes(form,n=>n.tag==='input'&&n.props.type==='number')[0];months.value='6';months.listeners.input({target:months});await tick()
  const period=nodes(form,n=>n.tag==='select')[1];await selectValue(period,true)
  assert.equal(nodes(form,n=>n.tag==='input'&&n.props.type==='number').length,0)
  await form.props.onSubmit({preventDefault(){}});await tick()
  assert.equal(s.calls.find(c=>c.method==='POST'&&c.url.endsWith('/rules')).body.months,0)
  await selectValue(period,false)
  assert.equal(nodes(form,n=>n.tag==='input'&&n.props.type==='number')[0].value,6)
  s.unmount()
 }
})

test('unlimited expiry renders in customer withdrawal and merchant inventory without date errors',async()=>{
 for(const locale of ['zh-CN','en']){
  const item={id:'unlimited',applicationId:'a',customerId:'customer',snapshot:{nameZh:'白酒',nameEn:'Spirit',rule:{months:0},timezone:'Asia/Singapore',applicationPhotos:[],receiptPhotos:[]},quantity:2,remaining:2,opened:false,source:'customer',location:'',label:'',receivedAt:'2026-01-01T00:00:00Z',expiresAt:null,status:'stored'}
  const data={...home,items:[item]},expected=locale==='en'?/No time limit/:/不限时间/
  const customer=mount(CustomerPage,true,locale,data,undefined,{query:{view:'withdraw'}});await tick();assert.match(text(customer.root),expected);customer.unmount()
  const page={...inventory,items:[{...inventory.items[0],expiresAt:null,status:'stored'}]}
  const staff=mount(StaffPage,false,locale,data,url=>url.includes('/inventory?')?page:data,{query:{task:'inventory'}});await tick();assert.match(text(staff.root),expected);staff.unmount()
 }
})

const receiptProduct={id:'wine',categoryId:'category',nameZh:'清香白酒',nameEn:'Spirit',code:'W001'}
const receiptHome={...home,rules:[{id:'rule',categoryId:'category',name:'不限时间规则',months:0,version:2}],categories:[{id:'category',nameZh:'酒水',nameEn:'Wine'}]}
const emptyReceipt=()=>({id:'',customerId:'customer',status:'pending',lines:[{id:'line',productId:null,nameZh:'',nameEn:'',quantity:1,opened:false,source:'customer',photos:[],note:''}]})
function receiptMount(locale='zh-CN',respond){return mount(ReceiveForm,false,locale,receiptHome,(url,options)=>respond?respond(url,options):url.includes('/product-options?')?{items:[receiptProduct],categories:receiptHome.categories,hasMore:false,page:0}:url.endsWith('/products/wine')?receiptProduct:options?.method==='POST'?{status:'stored'}:receiptHome,{props:{home:receiptHome,api:storageApi('A',false),application:emptyReceipt(),direct:true,disabled:false}})}
function chooseReceiptWine(root){nodes(root,n=>n.tag==='button'&&text(n).includes('选择此酒品'))[0].props.onClick()}
function setReceiptConsent(root){const consent=nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox'&&n.props.required!==undefined)[0];consent.checked=true;consent.listeners.change({target:consent})}
test('receipt search results select wine directly; clear resets identity photos quantity and consent',async()=>{
 const s=receiptMount();await tick();assert.equal(nodes(s.root,n=>n.tag==='select'&&text(n).includes('请选择酒品')).length,0)
 chooseReceiptWine(s.root);await tick();assert.match(text(s.root),/已选酒品.*清香白酒.*W001/)
 const quantity=nodes(s.root,n=>n.tag==='input'&&n.props.type==='number')[0];quantity.value='4';quantity.listeners.input({target:quantity});nodes(s.root,n=>n.props?.['data-test-photo'])[0].props.onClick();setReceiptConsent(s.root);await tick();assert.equal(button(s.root,'确认实物已收存').props.disabled,false)
 button(s.root,'清空酒品').props.onClick();await tick();assert.doesNotMatch(text(s.root),/已选酒品/);assert.equal(button(s.root,'确认实物已收存').props.disabled,true)
 chooseReceiptWine(s.root);await tick();assert.equal(nodes(s.root,n=>n.tag==='input'&&n.props.type==='number')[0].value,1);setReceiptConsent(s.root);await tick();await nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick()
 assert.deepEqual(s.calls.find(c=>c.method==='POST').body.lines[0].photos,[])
})
test('receipt separates new wine entry and clears its fields before returning to existing wine',async()=>{
 const s=receiptMount();await tick();button(s.root,'＋ 新增酒品').props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n).includes('选择此酒品')).length,0)
 const name=nodes(s.root,n=>n.tag==='label'&&text(n).includes('中文酒名'))[0];nodes(name,n=>n.tag==='input')[0].props.onInput({target:{value:'新酒'}})
 const category=nodes(s.root,n=>n.tag==='select'&&nodes(n,o=>o.tag==='option'&&o.props.value==='category').length)[0];category.props.onChange({target:{value:'category'}});await tick();assert.match(text(s.root),/不限时间规则/)
 button(s.root,'已有酒品').props.onClick();await tick();assert.equal(button(s.root,'确认实物已收存').props.disabled,true);button(s.root,'＋ 新增酒品').props.onClick();await tick()
 assert.equal(nodes(s.root,n=>n.tag==='label'&&text(n).includes('中文酒名'))[0].children.find(n=>n.tag==='input').value,'')
})
test('each receipt wine has independent search and a new line starts blank',async()=>{
 const s=receiptMount();await tick();chooseReceiptWine(s.root);await tick();button(s.root,'＋ 再加一款酒').props.onClick();await tick()
 const cards=nodes(s.root,n=>n.tag==='article'&&String(n.props.class).includes('storage-receive-line'));assert.equal(cards.length,2);assert.match(text(cards[0]),/已选酒品/);assert.doesNotMatch(text(cards[1]),/已选酒品/)
 chooseReceiptWine(cards[1]);await tick();button(cards[0],'清空酒品').props.onClick();await tick();assert.match(text(cards[1]),/已选酒品/);assert.doesNotMatch(text(cards[0]),/已选酒品/)
})
test('clearing a customer returns to an empty lookup and discards that customer receipt draft',async()=>{
 const s=mount(StaffPage,false,'zh-CN',receiptHome,url=>url.includes('/customers?')?[{id:'customer',displayName:'甲顾客',phone:'80000000'}]:url.includes('/product-options?')?{items:[receiptProduct],categories:receiptHome.categories,hasMore:false,page:0}:receiptHome);await tick()
 const lookup=()=>nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-merchant-customer-search'))[0]
 nodes(lookup(),n=>n.tag==='input')[0].props.onInput({target:{value:'8000'}});lookup().props.onSubmit({preventDefault(){}});await tick();assert.ok(button(s.root,'更换顾客'));await tick();chooseReceiptWine(s.root);await tick()
 button(s.root,'清除顾客').props.onClick();await tick();assert.equal(nodes(lookup(),n=>n.tag==='input')[0].value,'');assert.doesNotMatch(text(s.root),/当前办理顾客|直接帮顾客登记存酒/);assert.equal(s.calls.filter(c=>c.method==='POST').length,0)
 nodes(lookup(),n=>n.tag==='input')[0].props.onInput({target:{value:'8000'}});lookup().props.onSubmit({preventDefault(){}});await tick();assert.ok(button(s.root,'更换顾客'));await tick();assert.doesNotMatch(text(s.root),/已选酒品/)
})
test('receipt picker exposes English select clear and independent new wine actions',async()=>{
 const s=receiptMount('en');await tick();assert.ok(button(s.root,'Existing wine'));assert.ok(button(s.root,'＋ New wine'));nodes(s.root,n=>n.tag==='button'&&text(n).includes('Select wine'))[0].props.onClick();await tick();assert.match(text(s.root),/Selected wine.*Spirit.*W001/);button(s.root,'Clear wine').props.onClick();await tick();assert.doesNotMatch(text(s.root),/Selected wine/)
})

test('wine search can be cleared and a missing wine name carries into explicit new wine entry',async()=>{
 const s=receiptMount('zh-CN',url=>url.includes('/product-options?')?{items:new URL(url,'http://qa').searchParams.get('query')?[]:[receiptProduct],categories:receiptHome.categories,hasMore:false,page:0}:receiptHome);await tick()
 const query=()=>nodes(s.root,n=>n.tag==='input'&&n.props.placeholder==='输入酒名或商品编号')[0]
 query().value='未收录的酒';query().listeners.input({target:query()});await tick();button(s.root,'查找').props.onClick();await tick();assert.match(text(s.root),/没有找到匹配酒品/)
 button(s.root,'清空查找').props.onClick();await tick();assert.equal(query().value,'');assert.match(text(s.root),/选择此酒品/)
 query().value='未收录的酒';query().listeners.input({target:query()});await tick();button(s.root,'查找').props.onClick();await tick();nodes(s.root,n=>n.tag==='button'&&text(n)==='＋ 新增酒品')[0].props.onClick();await tick()
 const label=nodes(s.root,n=>n.tag==='label'&&text(n).includes('中文酒名'))[0];assert.equal(nodes(label,n=>n.tag==='input')[0].value,'未收录的酒');assert.equal(s.calls.filter(c=>c.method==='POST').length,0)
})
test('wine search failures explain retry rather than suggesting an empty catalog',async()=>{
 let failed=true;const s=receiptMount('zh-CN',url=>{if(url.includes('/product-options?')){if(failed)throw new Error('temporarily unavailable');return{items:[receiptProduct],categories:receiptHome.categories,hasMore:false,page:0}}return receiptHome});await tick();assert.match(text(s.root),/酒品加载失败，请重试查找/);assert.doesNotMatch(text(s.root),/没有找到匹配酒品/);assert.equal(button(s.root,'确认实物已收存').props.disabled,true)
 failed=false;button(s.root,'查找').props.onClick();await tick();assert.match(text(s.root),/选择此酒品/);assert.doesNotMatch(text(s.root),/酒品加载失败/)
})

test('reselecting requires an explicit choice or keeping the previous wine before receipt',async()=>{
 const s=receiptMount();await tick();chooseReceiptWine(s.root);await tick();setReceiptConsent(s.root);await tick();assert.equal(button(s.root,'确认实物已收存').props.disabled,false)
 button(s.root,'重新选择').props.onClick();await tick();assert.equal(button(s.root,'确认实物已收存').props.disabled,true)
 await nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.equal(s.calls.filter(c=>c.method==='POST').length,0)
 button(s.root,'保留原酒品').props.onClick();await tick();assert.equal(button(s.root,'确认实物已收存').props.disabled,false)
})

test('quick mode preserves customer records applications and pickup codes without a customer scan entry',async()=>{
 const item={id:'quick-mode-item',customerId:'customer',snapshot:{nameZh:'白酒',nameEn:'Spirit',timezone:'Asia/Singapore',applicationPhotos:[],receiptPhotos:[]},remaining:2,quantity:2,opened:false,status:'stored',expiresAt:new Date(Date.now()+86400000).toISOString()},data={...home,operationMode:'customer_verification',items:[item]}
 const s=mount(CustomerPage,true,'zh-CN',data);await tick();button(s.root,'我要存酒').props.onClick();await tick();assert.match(text(s.root),/1\. 选择酒品/);assert.doesNotMatch(text(s.root),/本店由门店人员直接登记存取酒/);button(s.root,'我要取酒').props.onClick();await tick();assert.ok(button(s.root,'查看取酒车'));assert.doesNotMatch(text(s.root),/店员已出码？扫描确认/);assert.equal(nodes(s.root,n=>n.props?.['data-storage-scan']).length,0);button(s.root,'我的记录').props.onClick();await tick();assert.match(text(s.root),/取酒记录|寄存申请/)
})

test('customer identity-code handoff opens quick storage registration without a second customer scan',async()=>{
 const person={id:'handoff-deposit-customer',displayName:'扫码存酒顾客',phone:'80000000'}
 setScanHandoff({store:'A',actor:'handoff-staff',target:'storage',code:'WI.'+'D'.repeat(43),customerId:person.id,task:'deposit'})
 const s=mount(StaffPage,false,'zh-CN',{...home,operationMode:'customer_verification'},url=>url.endsWith('/customers/'+person.id)?person:url.includes('/products?')?[]:home,{actor:{accountId:'handoff-staff',roles:[],permissions:home.permissions}});await tick()
 assert.match(text(s.root),/扫码存酒顾客/);assert.match(text(s.root),/直接帮顾客登记存酒/);assert.ok(s.calls.every(c=>c.method==='GET'));assert.equal(nodes(s.root,n=>n.tag==='figure').length,0)
})
test('settings explain quick collection and retain the compatible saved operation mode in both languages',async()=>{
 for(const locale of ['zh-CN','en']){
  const data={...home,operationMode:'customer_verification',settingsVersion:2},s=mount(StaffPage,false,locale,data,undefined,{actor:{accountId:'admin',roles:['tenant_admin'],permissions:home.permissions,storeIds:['A']}});await tick();button(s.root,locale==='en'?'Settings':'设置').props.onClick();await tick()
  assert.match(text(s.root),locale==='en'?/Quick storage \/ collection \(default\)/:/快捷存取（默认）/);assert.match(text(s.root),locale==='en'?/without a customer scan/:/无需顾客扫码授权/);assert.doesNotMatch(text(s.root),/核销模式需顾客授权取酒/)
  const form=nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-mode-form'))[0];assert.ok(nodes(form,n=>n.tag==='option'&&n.props.value==='customer_verification').length);assert.equal(button(s.root,locale==='en'?'Save operation mode':'保存存取酒模式').props.disabled,true);s.unmount()
 }
})


test('receipt wine pages use bounded server queries and filters reset the page',async()=>{
 const requests=[];const s=receiptMount('zh-CN',url=>{if(!url.includes('/product-options?'))return receiptHome;const params=new URL(url,'http://qa').searchParams;requests.push(params);return {items:[{...receiptProduct,id:'p'+params.get('page'),nameZh:'第'+params.get('page')+'页酒'}],categories:receiptHome.categories,page:Number(params.get('page')),hasMore:params.get('page')==='0'}});await tick();
 assert.equal(requests[0].get('size'),'6');assert.equal(button(s.root,'上一页').props.disabled,true);
 button(s.root,'下一页').props.onClick();await tick();assert.match(text(s.root),/第1页酒/);assert.equal(button(s.root,'下一页').props.disabled,true);
 const input=nodes(s.root,n=>n.tag==='input'&&n.props.maxlength==='120')[0];input.props['onUpdate:modelValue']('白酒');await tick();button(s.root,'查找').props.onClick();await tick();assert.equal(requests.at(-1).get('page'),'0');assert.equal(requests.at(-1).get('query'),'白酒');
 const cat=nodes(s.root,n=>n.tag==='select'&&n.props['data-testid']==='wine-category-filter')[0];cat.props.onChange({target:{value:'category'}});await tick();assert.equal(requests.at(-1).get('categoryId'),'category');assert.equal(requests.at(-1).get('page'),'0');
 button(s.root,'清空查找').props.onClick();await tick();assert.equal(requests.at(-1).get('query'),'');assert.equal(requests.at(-1).get('categoryId'),null);
})


test('storage category management scopes options and explicitly submits selected product lines',async()=>{
 const cats=[{id:'wine',code:'WINE',nameZh:'酒水',nameEn:'Wine',enabled:true,sortOrder:0,version:0,productLines:['wine_storage']},{id:'stamp',code:'STAMP',nameZh:'集卡',nameEn:'Stamp',enabled:true,sortOrder:0,version:0,productLines:['wallet']}];
 const s=mount(CatalogPage,false,'zh-CN',home,(url,options)=>url.endsWith('/categories')?cats:url.includes('/storage-products?')?{items:[],total:0,page:0,size:12,hasMore:false}:catalogResponse(url,options),{actor:catalogActor,props:{storageOnly:true,embedded:true}});await tick();button(s.root,'分类管理').props.onClick();await tick();assert.match(text(s.root),/酒水/);assert.doesNotMatch(text(s.root),/集卡|匹配商品|搜索名称或编号/);
 button(s.root,'＋ 新增').props.onClick();await tick();assert.match(text(s.root),/适用产品线/);const storage=nodes(s.root,n=>n.tag==='input'&&n.props.type==='checkbox'&&n.props.value==='wine_storage')[0];assert.ok(storage.checked);
 const fields=nodes(s.root,n=>n.tag==='input'&&n.props.type!=='checkbox');fields[0].props['onUpdate:modelValue']('QA');fields[1].props['onUpdate:modelValue']('QA酒水');await tick();await nodes(s.root,n=>n.tag==='form'&&nodes(n,b=>b===storage).length)[0].props.onSubmit({preventDefault(){}});await tick();assert.deepEqual(s.calls.find(c=>c.method==='POST').body.productLines,['wine_storage']);
})

test('late wine search responses cannot overwrite a newer search or manual wine draft',async()=>{
 let finish;let count=0;const s=receiptMount('en',url=>{if(!url.includes('/product-options?'))return receiptHome;count++;if(count===2)return new Promise(resolve=>finish=resolve);return{items:[receiptProduct],hasMore:false,page:0,categories:receiptHome.categories}});await tick();
 const input=nodes(s.root,n=>n.tag==='input'&&n.props.maxlength==='120')[0];input.props['onUpdate:modelValue']('old');button(s.root,'Search').props.onClick();await tick();button(s.root,'＋ New wine').props.onClick();await tick();finish({items:[{...receiptProduct,nameEn:'OLD RESPONSE'}],hasMore:true,page:0});await tick();assert.doesNotMatch(text(s.root),/OLD RESPONSE|Select wine/);assert.ok(nodes(s.root,n=>n.tag==='input'&&n.props.maxlength==='200').length);
})


test('saving a wine category refreshes storage rule choices without reloading the page',async()=>{
 const actor={...catalogActor,permissions:[...catalogActor.permissions,'tenant.admin.manage']};const data=structuredClone(home);const cats=[{id:'wine',code:'WINE',nameZh:'酒水',nameEn:'Wine',enabled:true,sortOrder:0,version:0,productLines:['wine_storage']}];data.categories=[{id:'wine',nameZh:'酒水',nameEn:'Wine'}];
 const s=mount(StaffPage,false,'zh-CN',data,(url,options)=>{if(url.endsWith('/catalog/categories')){if(options?.method==='POST'){const c={...JSON.parse(options.body),id:'new-wine',version:0};cats.push(c);data.categories.push({id:c.id,nameZh:c.nameZh,nameEn:c.nameEn});return c}return structuredClone(cats)}if(url.includes('/storage-products?'))return{items:[],total:0,page:0,size:12,hasMore:false};return structuredClone(data)},{actor});await tick();button(s.root,'设置').props.onClick();await tick();button(s.root,'管理寄存酒商品').props.onClick();await tick();button(s.root,'分类管理').props.onClick();await tick();button(s.root,'＋ 新增').props.onClick();await tick();
 const count=s.calls.filter(c=>c.url.endsWith('/wine-storage/home')).length;const fields=nodes(s.root,n=>n.tag==='input'&&n.props.type!=='checkbox');fields[0].props['onUpdate:modelValue']('NEW');fields[1].props['onUpdate:modelValue']('新寄存分类');await tick();await nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.ok(s.calls.filter(c=>c.url.endsWith('/wine-storage/home')).length>count);
 button(s.root,'返回存酒设置').props.onClick();await tick();assert.ok(nodes(s.root,n=>n.tag==='option'&&n.props.value==='new-wine').length);s.unmount();
})

async function preparePickupCart(root,quantity){
 const input=nodes(root,n=>n.tag==='input'&&n.props.type==='number')[0]
 if(input&&(quantity!==undefined||Number(input.props.value)===0)){input.props.onInput({target:{value:String(quantity??1)}});await tick()}
 const view=button(root,'查看取酒车')??button(root,'View pickup cart');if(view){view.props.onClick();await tick()}
 return button(root,'提交取酒')??button(root,'Submit and get pickup code')??button(root,'Submit pickup')
}

const reportRow={id:'bill-one',kind:'deposit',customerId:'customer',customerName:'报表顾客',customerPhone:'8000',status:'stored',occurredAt:'2026-11-02T02:00:00Z',quantity:2,winesZh:'青岛黑啤 × 2',winesEn:'Beer × 2',locations:'前台 · A01'}
const reportPage={items:[reportRow],summary:{recordCount:41,completedCount:39,bottleCount:88},page:0,size:20,hasMore:true}
function reportMount(options={}){return mount(StaffPage,false,'zh-CN',home,undefined,{actor:{accountId:'report-admin',roles:['tenant_admin'],permissions:[...home.permissions,'tenant.admin.manage'],storeIds:['A','B']},query:{task:'records'},reportRespond:()=>reportPage,...options})}
function reportForm(s){return nodes(s.root,n=>n.tag==='form'&&String(n.props.class).includes('storage-report-filters'))[0]}
function updateModel(n,value){n.props['onUpdate:modelValue'](value)}
test('merchant report opens all-store results without customer selection or creation and fetches metadata only',async()=>{
 const s=reportMount();await tick();assert.match(text(s.root),/报表顾客.*青岛黑啤/);assert.equal(nodes(s.root,n=>n.tag==='tbody')[0].children.filter(n=>n.tag==='tr').length,1);assert.doesNotMatch(text(s.root),/找到顾客|新增顾客|确认一位顾客|追加纠错|上传实物/);assert.ok(s.calls.some(c=>c.url.endsWith('/home?includeRecords=false')));assert.equal(s.calls.filter(c=>c.url.includes('/customers')).length,0);assert.match(text(s.root),/41.*39.*88/);assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
})
test('report applies date keyword and status filters to server pages and does not mix draft filters with old totals',async()=>{
 const s=reportMount();await tick();const form=reportForm(s),inputs=nodes(form,n=>n.tag==='input'&&n.props.type!=='date'),selects=nodes(form,n=>n.tag==='select');updateModel(inputs[0],'  黑啤  ');updateModel(inputs[1],'2026-11-01');updateModel(inputs[2],'2026-11-02');updateModel(selects[1],'stored');await tick();assert.match(text(s.root),/筛选已更改/);assert.equal(button(s.root,'下一页').props.disabled,true);await form.props.onSubmit({preventDefault(){}});await tick();let call=s.calls.filter(c=>c.url.includes('/reports?')).at(-1);let q=new URL('http://qa'+call.url).searchParams;assert.equal(q.get('query'),'黑啤');assert.equal(q.get('from'),'2026-11-01');assert.equal(q.get('to'),'2026-11-02');assert.equal(q.get('status'),'stored');assert.equal(q.get('page'),'0');await button(s.root,'下一页').props.onClick();await tick();assert.equal(new URL('http://qa'+s.calls.at(-1).url).searchParams.get('page'),'1');updateModel(selects[0],'pickup');await selects[0].props.onChange();await tick();q=new URL('http://qa'+s.calls.at(-1).url).searchParams;assert.equal(q.get('kind'),'pickup');assert.equal(q.get('status'),'all');assert.equal(q.get('page'),'0');s.unmount()
})
test('report details retain original evidence photos and audit names without any storage mutation controls',async()=>{
 const item={id:'report-item',applicationId:'bill-one',customerId:'customer',snapshot:{nameZh:'青岛黑啤',timezone:'Asia/Singapore',receiptPhotos:['receipt-photo'],applicationPhotos:[]},quantity:2,remaining:0,status:'collected',expiresAt:null},application={id:'bill-one',customerId:'customer',status:'stored',lines:[{id:'line',nameZh:'青岛黑啤',quantity:3,photos:[],note:'原申请说明'}],confirmation:{lines:[{lineId:'line',nameZh:'青岛黑啤',quantity:2,photos:[]}],reason:'核实实收两瓶'},createdAt:'2026-11-02T02:00:00Z'},detail={application,intent:null,items:[item],events:[{id:'event',sourceId:'bill-one',action:'received',actorName:'王经理',details:{},createdAt:'2026-11-02T02:00:00Z'}]};
 const s=reportMount({reportRespond:url=>url.includes('/reports/deposit/')?detail:reportPage});await tick();await button(s.root,'详情').props.onClick();await tick();assert.match(text(s.root),/原申请瓶数.*3.*实际收存瓶数.*2/);assert.match(text(s.root),/核实实收两瓶|原申请说明/);assert.match(text(s.root),/王经理/);assert.doesNotMatch(text(s.root),/追加纠错说明|追加现场照片|登记取酒|新增顾客/);const photo=nodes(s.root,n=>n.tag==='button'&&n.props['aria-label']==='放大查看照片')[0];assert.ok(photo);await photo.props.onClick({currentTarget:{focus(){}}});await tick();assert.equal(nodes(s.root,n=>n.props?.role==='dialog').length,1);assert.ok(s.calls.every(c=>c.method==='GET'));s.unmount()
})
test('report switch store and query invalidates stale list and details while retry handles errors',async()=>{
 let finishList,finishDetail;let count=0;const s=reportMount({reportRespond:url=>url.includes('/reports/deposit/')?new Promise(r=>finishDetail=r):++count===1?reportPage:new Promise(r=>finishList=r)});await tick();button(s.root,'详情').props.onClick();await tick();assert.equal(typeof finishDetail,'function');await button(s.root,'下一页').props.onClick();await tick();finishDetail({application:null,intent:null,items:[],events:[]});await tick();assert.equal(nodes(s.root,n=>String(n.props?.class).includes('storage-report-detail')).length,0);const old=finishList;s.route.params.storeId='B';await tick();globalThis.fetch=async()=>({ok:false,status:403,json:async()=>({code:'STORAGE_FORBIDDEN',message:'无权限'})});old({...reportPage,items:[{...reportRow,customerName:'旧店顾客'}]});await tick();assert.doesNotMatch(text(s.root),/旧店顾客/);s.unmount()
})
test('report validates reversed dates and displays empty error retry and English states',async()=>{
 let fail=false;const s=reportMount({reportRespond:()=>{if(fail)throw new Error('network failed');return {...reportPage,items:[],summary:{recordCount:0,completedCount:0,bottleCount:0},hasMore:false}}});await tick();assert.match(text(s.root),/没有符合条件/);const inputs=nodes(reportForm(s),n=>n.tag==='input'&&n.props.type!=='date');updateModel(inputs[1],'2026-11-03');updateModel(inputs[2],'2026-11-02');const count=s.calls.length;await reportForm(s).props.onSubmit({preventDefault(){}});await tick();assert.equal(s.calls.length,count);assert.match(text(s.root),/开始日期不能晚于/);fail=true;updateModel(inputs[1],'');updateModel(inputs[2],'');await reportForm(s).props.onSubmit({preventDefault(){}});await tick();assert.match(text(s.root),/network failed/);fail=false;await button(s.root,'重新查询').props.onClick();await tick();s.i18n.global.locale.value='en-SG';await tick();assert.match(text(s.root),/Report type.*Customer.*Status/);assert.match(text(s.root),/No matching records/);s.unmount()
})

test('storage settings provide a read-only guided manual and downloadable Chinese PDF for staff and administrators',async()=>{
 for(const admin of [false,true]){
  const actor=admin?{...catalogActor,permissions:[...catalogActor.permissions,'tenant.admin.manage']}:undefined;
  const s=mount(StaffPage,false,'zh-CN',home,catalogResponse,{actor});await tick();button(s.root,'设置').props.onClick();await tick();assert.ok(button(s.root,'查看操作指南'));assert.ok(button(s.root,'下载本店指南 PDF'));const writes=s.calls.filter(c=>c.method!=='GET').length;
  button(s.root,'查看操作指南').props.onClick();await tick();assert.match(text(s.root),/选一个任务.*帮顾客存酒/);assert.equal(nodes(s.root,n=>n.tag==='ol'&&String(n.props.class).includes('storage-guide-steps')).length,1);button(s.root,'门店批量取酒').props.onClick();await tick();assert.match(text(s.root),/提交会立即扣减存酒|提交取酒/);button(s.root,'核销顾客取酒码').props.onClick();await tick();assert.match(text(s.root),/此时尚未扣库存/);assert.match(text(s.root),/扫描成功即完成整车核销并扣库存/);button(s.root,'查询库存与历史报表').props.onClick();await tick();assert.match(text(s.root),/每页20笔/);assert.equal(nodes(s.root,n=>n.tag==='input').length,0);assert.equal(s.calls.filter(c=>c.method!=='GET').length,writes);
  s.i18n.global.locale.value='en-SG';await tick();assert.match(text(s.root),/Check stock and reports/);assert.ok(nodes(s.root,n=>n.tag==='a'&&text(n).includes('PDF')).length);button(s.root,'Back to storage settings').props.onClick();await tick();assert.ok(button(s.root,'Read quick guide'));s.unmount()
 }
})

const guideActor={accountId:'guide-actor',tenantId:'tenant-guide',roles:['store_staff'],permissions:home.permissions,storeIds:['A','B']}
const guideStore=id=>({storeId:id,tenantId:'tenant-guide',tenantCode:'mw',storeName:'核对门店 '+id,storeCode:id,status:'active',locale:'zh-CN',defaultStore:id==='A'})
function guideResponse(url,allowed=true){if(url==='/api/v1/me/stores')return {success:true,stores:[guideStore('A'),guideStore('B')]};if(url.startsWith('/api/me/apps?'))return {success:true,apps:[],productAccess:[{appKey:'wine_storage',businessAllowed:allowed,historyAllowed:true,historyRoute:null}]};return home}
function guideMount(respond=url=>guideResponse(url)){globalThis.window.setTimeout=setTimeout;globalThis.window.clearTimeout=clearTimeout;globalThis.window.location={protocol:'https:',hostname:'mw.booking.yumstone.sg',origin:'https://mw.booking.yumstone.sg',port:''};globalThis.window.__RPB_HOST_PREFIX_BASE_HOST__='booking.yumstone.sg';return mount(StaffPage,false,'zh-CN',home,respond,{actor:guideActor,query:{task:'settings'}})}
test('staff guide displays two current-store role URLs and QR payloads without administrator directory access',async()=>{
 const s=guideMount();await tick();await tick();assert.equal(button(s.root,'下载本店指南 PDF').props.disabled,false,text(s.root));button(s.root,'查看操作指南').props.onClick();await tick();const figures=nodes(s.root,n=>n.tag==='figure'&&n.props['data-entry-url']);assert.equal(figures.length,2);const urls=figures.map(n=>n.props['data-entry-url']);assert.equal(new URL(urls[0]).searchParams.get('redirect'),'/stores/A/wine-storage');assert.equal(urls[1],'https://booking.yumstone.sg/wallet/A/wine-storage');assert.match(text(s.root),/员工酒存入口.*顾客存酒入口/);assert.ok(s.calls.every(c=>c.method==='GET'));assert.ok(!s.calls.some(c=>c.url.includes('tenant-admin/entry-links')));s.unmount()
})
test('guide entry checks clear on denial or failure and recover after permission is restored',async()=>{
 let allowed=true,fail=false;const s=guideMount(url=>{if(fail&&url.startsWith('/api/me/apps?'))throw new Error('network');return guideResponse(url,allowed)});await tick();button(s.root,'查看操作指南').props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-entry-url']).length,2);allowed=false;for(const fn of document.listeners.get('visibilitychange')||[])fn();await tick();assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-entry-url']).length,0);assert.equal(button(s.root,'下载本店指南 PDF').props.disabled,true);allowed=true;for(const fn of document.listeners.get('visibilitychange')||[])fn();await tick();assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-entry-url']).length,2);fail=true;for(const fn of document.listeners.get('visibilitychange')||[])fn();await tick();assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-entry-url']).length,0);assert.match(text(s.root),/入口暂时无法校验/);fail=false;button(s.root,'重试入口校验').props.onClick();await tick();assert.equal(nodes(s.root,n=>n.tag==='figure'&&n.props['data-entry-url']).length,2);s.unmount()
})
test('changing store rejects a delayed old guide identity response',async()=>{
 let finish;const s=guideMount(url=>url==='/api/v1/me/stores'?new Promise(r=>finish=r):guideResponse(url));await tick();const old=finish;s.route.params.storeId='B';await tick();old({success:true,stores:[guideStore('A')]});await tick();assert.doesNotMatch(text(s.root),/核对门店 A/);finish({success:true,stores:[guideStore('B')]});await tick();button(s.root,'查看操作指南').props.onClick();await tick();finish({success:true,stores:[guideStore('B')]});await tick();assert.ok(nodes(s.root,n=>n.tag==='figure'&&n.props['data-entry-url']).every(n=>n.props['data-entry-url'].includes('B')));s.unmount()
})
test('guide PDF output is suppressed after store switch during async composition',async()=>{
 const s=guideMount();await tick();await tick();let finish;globalThis.__guidePdfGate=new Promise(r=>finish=r);const downloads=[],oldCreate=document.createElement;document.createElement=()=>({click(){downloads.push(this.download)}});const handler=button(s.root,'下载本店指南 PDF').props.onClick;globalThis.__guidePdfEntered=false;const pendingDownload=handler();await tick();await tick();assert.equal(globalThis.__guidePdfEntered,true,text(s.root));s.route.params.storeId='B';await tick();finish();await pendingDownload;await tick();assert.deepEqual(downloads,[]);delete globalThis.__guidePdfGate;document.createElement=oldCreate;s.unmount()
})

test('guide PDF download revalidates entries and completes a named current-store download',async()=>{
 const s=guideMount();await tick();await tick();const downloads=[],oldCreate=document.createElement;document.createElement=()=>({click(){downloads.push({name:this.download,href:this.href})}});const reads=s.calls.filter(c=>c.url==='/api/v1/me/stores').length;await button(s.root,'下载本店指南 PDF').props.onClick();await tick();assert.equal(downloads.length,1);assert.ok(nodes(s.root,n=>n.tag==='a'&&String(n.props.href).startsWith('blob:')).length);assert.match(downloads[0].name,/核对门店-A/);assert.match(downloads[0].href,/^blob:/);assert.equal(s.calls.filter(c=>c.url==='/api/v1/me/stores').length,reads+1);assert.ok(s.calls.every(c=>c.method==='GET'));document.createElement=oldCreate;s.unmount()
})


test('email settings distinguishes first setup, tenant sharing and legacy conflict in both languages',async()=>{
 for(const locale of ['zh-CN','en-SG'])for(const scope of ['unconfigured','tenant','legacy_store']){
  const actor={...catalogActor,permissions:['tenant.admin.manage']};const s=mount(EmailSettings,false,locale,home,()=>({...emailSettingsFixture,configurationScope:scope}),{actor});await tick()
  const copy=text(s.root);assert.match(copy,locale==='zh-CN'?/整个租户|所有门店|其他门店/:/entire tenant|all stores|Other stores/)
  if(scope==='legacy_store')assert.match(copy,locale==='zh-CN'?/旧门店配置/:/legacy store settings/)
  if(scope==='unconfigured')assert.match(copy,locale==='zh-CN'?/尚未配置/:/Not configured/)
  assert.equal(s.calls.length,1);assert.ok(!s.calls[0].url.includes('public-booking'));s.unmount()
 }
})


test('merchant and employee can sign out directly from storage settings without business mutations',async()=>{
 for(const [locale,roles] of [['zh-CN',['tenant_admin']],['en-SG',['staff']]]){
  const actor={accountId:'staff-session',roles,permissions:home.permissions,storeIds:['A']};let finish
  const s=mount(StaffPage,false,locale,home,(url)=>url==='/api/v1/auth/logout'?new Promise(resolve=>finish=resolve):url.includes('/products')?[]:home,{actor});await tick()
  button(s.root,locale==='zh-CN'?'设置':'Settings').props.onClick();await tick()
  const exit=button(s.root,locale==='zh-CN'?'退出登录':'Sign out');assert.ok(exit);const action=exit.props.onClick
  const first=action();await tick();assert.equal(exit.props.disabled,true);await action();assert.equal(s.calls.filter(c=>c.url==='/api/v1/auth/logout').length,1)
  finish({success:true});await first;await tick();assert.equal(s.auth.user,null);assert.deepEqual(s.navigation.at(-1),{name:'login'});assert.ok(s.calls.filter(c=>c.method!=='GET').every(c=>c.url==='/api/v1/auth/logout'));s.unmount()
 }
})


test('storage settings handles logout failure without a false login navigation or repeated submission',async()=>{
 let fail=true
 const s=mount(StaffPage,false,'zh-CN',home,url=>{if(url==='/api/v1/auth/logout'){if(fail)throw new Error('offline');return {success:true}}return url.includes('/products')?[]:home},{actor:{accountId:'employee',roles:['staff'],permissions:home.permissions,storeIds:['A']},query:{task:'settings'}});await tick()
 await button(s.root,'退出登录').props.onClick();await tick();assert.equal(s.navigation.length,0);assert.equal(s.auth.user,null);assert.ok(nodes(s.root,n=>n.props?.role==='alert').length);assert.equal(button(s.root,'退出登录').props.disabled,false)
 fail=false;await button(s.root,'退出登录').props.onClick();await tick();assert.deepEqual(s.navigation.at(-1),{name:'login'});assert.equal(s.calls.filter(c=>c.url==='/api/v1/auth/logout').length,2);s.unmount()
})


test('receipt empty search keeps exactly one new wine action in the top toolbar',async()=>{
 const s=receiptMount('zh-CN',url=>url.includes('/product-options?')?{items:[],hasMore:false}:receiptHome);await tick()
 assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n).trim()==='＋ 新增酒品').length,1)
 button(s.root,'＋ 新增酒品').props.onClick();await tick();assert.match(text(s.root),/中文酒名/);s.unmount()
})

test('receipt submits existing and newly entered wines without any uploaded photos',async()=>{
 for(const manual of [false,true]){
  const s=receiptMount();await tick()
  if(manual){button(s.root,'＋ 新增酒品').props.onClick();await tick();const label=nodes(s.root,n=>n.tag==='label'&&text(n).includes('中文酒名'))[0];nodes(label,n=>n.tag==='input')[0].props.onInput({target:{value:'无图寄存白酒'}});const category=nodes(s.root,n=>n.tag==='select'&&n.props.required!==undefined)[0];category.props.onChange({target:{value:'category'}})}else chooseReceiptWine(s.root)
  await tick();setReceiptConsent(s.root);await tick();assert.equal(button(s.root,'确认实物已收存').props.disabled,false)
  await nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();const writes=s.calls.filter(c=>c.method==='POST');assert.equal(writes.length,1);assert.deepEqual(writes[0].body.lines[0].photos,[]);assert.equal(writes[0].body.rulesConfirmed,true);assert.equal(s.calls.some(c=>c.url.endsWith('/photos')),false);s.unmount()
 }
})


test('report dates use explicit localized placeholders and keep ISO values from typing or calendar selection',async()=>{
 const s=reportMount();await tick();let form=reportForm(s),dates=nodes(form,n=>n.tag==='input'&&n.props.type==='text'&&n.props.placeholder==='年-月-日');assert.equal(dates.length,2)
 updateModel(dates[0],'2026-02-01');await tick();const calendars=nodes(form,n=>n.tag==='input'&&n.props.type==='date');assert.equal(calendars.length,2);assert.equal(calendars[0].value,'2026-02-01');updateModel(calendars[1],'2026-02-28');await tick();await form.props.onSubmit({preventDefault(){}});await tick();const params=new URL('http://qa'+s.calls.filter(c=>c.url.includes('/reports?')).at(-1).url).searchParams;assert.equal(params.get('from'),'2026-02-01');assert.equal(params.get('to'),'2026-02-28')
 button(s.root,'重置').props.onClick();await tick();assert.ok(nodes(reportForm(s),n=>n.tag==='input'&&n.props.type==='text'&&n.props.placeholder==='年-月-日').every(n=>!n.value));s.i18n.global.locale.value='en-SG';await tick();assert.equal(nodes(reportForm(s),n=>n.tag==='input'&&n.props.placeholder==='YYYY-MM-DD').length,2);s.unmount()
})

test('report rejects malformed or nonexistent typed dates without sending a query',async()=>{
 const s=reportMount();await tick();const form=reportForm(s),date=nodes(form,n=>n.tag==='input'&&n.props.placeholder==='年-月-日')[0];assert.ok(date);const count=s.calls.length
 for(const value of ['2026-02-30','2025-02-29','03/11/2026','2026-13-01','2026-11-']){updateModel(date,value);await tick();await form.props.onSubmit({preventDefault(){}});await tick();assert.equal(s.calls.length,count);assert.match(text(s.root),/请输入有效日期/)}
 updateModel(date,'2024-02-29');await tick();await form.props.onSubmit({preventDefault(){}});await tick();assert.equal(s.calls.length,count+1);s.unmount()
})
