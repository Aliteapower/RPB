import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, h, nextTick, reactive } from 'vue'
import { createPinia } from 'pinia'
import { routeLocationKey, routerKey } from 'vue-router'
import { createI18n } from 'vue-i18n'
let StaffHome,personalCodeModule,PersonalCode,RechargePage,CustomerHome,StaffMore,StaffCollect,StaffTopup,ExistingCharge,chargeComponentsModule,LedgerPanel, ReportPanel, EntryAllocations, MarketingPage, RefundComponent, StaffPoints, OfferComponent, CustomerTopups, messages, customerModule, staffModule, authModule, offersModule, pointsModule, apiModule
const originalStorage=globalThis.sessionStorage
const originalWindow=globalThis.window, originalFetch=globalThis.fetch, originalDocument=globalThis.document, originalDocumentClass=globalThis.Document, originalShadowRoot=globalThis.ShadowRoot
const tick=async()=>{for(let i=0;i<8;i++){await Promise.resolve();await nextTick()}}
const renderer=createRenderer({
 createElement:tag=>({tag,tagName:tag.toUpperCase(),children:[],props:{},value:'',get options(){return this.children.filter(c=>c.tag==='option')},listeners:{},addEventListener(name,fn){this.listeners[name]=fn},removeEventListener(){},setAttribute(){},removeAttribute(){},getRootNode(){return globalThis.document}}),createText:text=>({text}),createComment:()=>({}),
 setText:(node,text)=>{node.text=text},setElementText:(node,text)=>{node.text=text},
 patchProp:(node,key,old,value)=>{node.props[key]=value;if(key==='value'){node._value=value;node.value=value}},
 insert:(child,parent,anchor)=>{if(child.parent){const i=child.parent.children.indexOf(child);if(i>=0)child.parent.children.splice(i,1)}child.parent=parent;const i=anchor?parent.children.indexOf(anchor):-1;if(i<0)parent.children.push(child);else parent.children.splice(i,0,child)},
 remove:node=>{if(node.parent){const i=node.parent.children.indexOf(node);if(i>=0)node.parent.children.splice(i,1);node.parent=null}},
 parentNode:node=>node.parent,nextSibling:node=>{const c=node.parent?.children||[];return c[c.indexOf(node)+1]||null}
})
function nodes(root,predicate){let out=[];if(predicate(root))out.push(root);for(const c of root.children||[])out.push(...nodes(c,predicate));return out}

function mount(component,kind,route,props={},permissions) {
 let workspace;
 const root={children:[]},i18n=createI18n({legacy:false,locale:'zh-CN',messages:{'zh-CN':{wallet:messages.walletZh}}}),pinia=createPinia()
 authModule.useAuthSessionStore(pinia).user={accountId:'cashier-A',permissions:permissions??['wallet.view','wallet.topup.create','wallet.points.view','wallet.points.grant','wallet.points.adjust','wallet.points.redeem','wallet.points.reverse']}
 const app=renderer.createApp({setup(){workspace=kind==='customer'?customerModule.provideCustomerWallet():staffModule.provideStaffWallet();return()=>h(component,props)}})
 app.use(pinia);app.use(i18n);app.provide(routeLocationKey,route);app.provide(routerKey,{push:async()=>{},replace:async target=>{if(target&&typeof target==='object'&&target.query)route.query=target.query}})
 app.component('RouterLink',{props:['to'],setup:(props,{slots})=>()=>h('a',{href:props.to},slots.default?.())});app.mount(root)
 return{root,get auth(){return authModule.useAuthSessionStore(pinia)},get workspace(){return workspace},unmount:()=>app.unmount()}
}
const response=body=>({ok:true,status:200,json:async()=>body})
const allText=root=>[root.text||'',...(root.children||[]).map(allText)].join(' ')

before(async()=>{
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document=Object.assign(new Document(),{activeElement:null,visibilityState:'visible'})
 globalThis.window={setInterval:()=>1,clearInterval:()=>{}}
 const storage=new Map();globalThis.sessionStorage={getItem:k=>storage.get(k)??null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)}
 const {build,transform}=await import('esbuild'),{mkdir,writeFile}=await import('node:fs/promises'),{pathToFileURL}=await import('node:url')
 const entry=`export {default as StaffHome} from './src/modules/wallet/pages/WalletStaffHome.vue';export * as personalCodeModule from './src/modules/wallet/usePersonalCode.ts';export {default as PersonalCode} from './src/modules/wallet/components/WalletPersonalCode.vue';export {default as RechargePage} from './src/modules/wallet/pages/WalletStaffRecharge.vue';export {default as CustomerHome} from './src/modules/wallet/pages/WalletCustomerHome.vue';export {default as StaffMore} from './src/modules/wallet/pages/WalletStaffMore.vue';export {default as StaffCollect} from './src/modules/wallet/pages/WalletStaffCollect.vue';export {default as StaffTopup} from './src/modules/wallet/pages/WalletStaffTopup.vue';export {default as ExistingCharge} from './src/modules/wallet/components/WalletExistingBillCharge.vue';export * as chargeComponentsModule from './src/modules/wallet/useChargeComponents.ts';export {default as LedgerPanel} from './src/modules/wallet/components/WalletLedgerPanel.vue';export {default as ReportPanel} from './src/modules/wallet/components/WalletReportPanel.vue';export {default as EntryAllocations} from './src/modules/wallet/components/WalletEntryAllocations.vue';export {default as MarketingPage} from './src/modules/wallet/pages/WalletMarketing.vue';export {default as RefundComponent} from './src/modules/wallet/components/WalletRefundComponents.vue';export {default as StaffPoints} from './src/modules/wallet/pages/WalletPoints.vue';export {default as OfferComponent} from './src/modules/wallet/components/WalletTopupOffers.vue';export {default as CustomerTopups} from './src/modules/wallet/pages/WalletCustomerTopup.vue';export * as messages from './src/modules/wallet/locales.ts';export * as customerModule from './src/modules/wallet/useCustomerWallet.ts';export * as staffModule from './src/modules/wallet/useStaffWallet.ts';export * as authModule from './src/stores/authSession.ts';export * as offersModule from './src/modules/wallet/useTopupOffers.ts';export * as pointsModule from './src/modules/wallet/usePoints.ts';`
 const result=await build({stdin:{contents:entry,resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,platform:'node',format:'esm',external:['vue','vue-router','vue-i18n','pinia'],plugins:[{name:'vue-runtime-test',setup(build){build.onLoad({filter:/\.vue$/},async({path})=>{
  if(path.endsWith('WalletQrScanner.vue'))return {contents:`import {h} from 'vue';export default {props:['purpose'],emits:['code'],setup:(p,{emit})=>()=>h('button',{'data-scan':true,onClick:()=>emit('code',((p.purpose==='identity'||p.purpose==='personal')?'WI.':'WC.')+'A'.repeat(43))},'scan')}`,loader:'js'}
  if(path.endsWith('WalletQrDisplay.vue'))return {contents:`import {h} from 'vue';export default {props:['value'],setup:p=>()=>h('figure',{'data-code':p.value})}`,loader:'js'}
  const {readFile}=await import('node:fs/promises');const source=await readFile(path,'utf8'),{descriptor}=parse(source,{filename:path}),script=compileScript(descriptor,{id:'test-'+path,inlineTemplate:true})
  return {contents:script.content,loader:'ts'}
 })}}]})
 const dir='target/wallet-marketing-component-tests';await mkdir(dir,{recursive:true});await writeFile(dir+'/entry.mjs',result.outputFiles[0].contents)
 const loaded=await import(pathToFileURL(process.cwd()+'/'+dir+'/entry.mjs').href+'?'+Date.now())
 ;({StaffHome,personalCodeModule,PersonalCode,RechargePage,CustomerHome,StaffMore,StaffCollect,StaffTopup,ExistingCharge,chargeComponentsModule,LedgerPanel,ReportPanel,EntryAllocations,MarketingPage,RefundComponent,StaffPoints,OfferComponent,CustomerTopups,messages,customerModule,staffModule,authModule,offersModule,pointsModule}=loaded)
})
after(async()=>{globalThis.sessionStorage=originalStorage;globalThis.fetch=originalFetch;globalThis.window=originalWindow;globalThis.document=originalDocument;globalThis.Document=originalDocumentClass;globalThis.ShadowRoot=originalShadowRoot})
const offers={offers:[{id:'offer',version:2,principalCents:10000,bonusCents:1000,creditTotalCents:11000}],campaignId:'campaign',channels:['cash','paynow'],deductionPolicy:'proportional',selfServiceEnabled:true}
function composable(setup){let state;const app=renderer.createApp({setup(){state=setup();return()=>h('div')}});app.mount({children:[]});return{state,unmount:()=>app.unmount()}}

test('quote contains exactly one server offer, no client amounts, and selection change invalidates it',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,options,body:options?.body&&JSON.parse(options.body)});return response(url.endsWith('/topup-offers')?offers:{quoteId:'q',principalCents:10000,bonusCents:1000,creditTotalCents:11000,expiresAt:new Date(Date.now()+300000).toISOString()})}
 const scope=composable(()=>offersModule.useTopupOffers(reactive({value:'store'}),true));await tick()
 scope.state.selected.value='offer';await tick();await scope.state.getQuote();assert.deepEqual(calls.at(-1).body,{offerId:'offer',offerVersion:2,channel:'cash'})
 assert.equal(calls.at(-1).options.headers['X-RPB-Wallet-Model'],'components-v2');assert.equal(scope.state.expired.value,false)
 scope.state.channel.value='paynow';await tick();assert.equal(scope.state.quote.value,null);scope.unmount()
})

test('a late quote cannot survive selection changes and a late store response cannot replace new offers',async()=>{
 const pending=[];globalThis.fetch=(url,options)=>new Promise(resolve=>pending.push({url,options,resolve}))
 const store=reactive({value:'A'}),scope=composable(()=>offersModule.useTopupOffers(store,true));await tick()
 store.value='B';await tick();pending[1].resolve(response({...offers,campaignId:'B'}));await tick();pending[0].resolve(response({...offers,campaignId:'A'}));await tick();assert.equal(scope.state.offers.value.campaignId,'B')
 scope.state.selected.value='offer';await tick();const promise=scope.state.getQuote();await tick();scope.state.selected.value='other';await tick();pending[2].resolve(response({quoteId:'late',expiresAt:new Date(Date.now()+300000).toISOString()}));await promise;assert.equal(scope.state.quote.value,null);scope.unmount()
})

test('customer without funding account can select an offer and create a request using only quoteId',async()=>{
 const calls=[];const topup={id:'topup',amountCents:10000,principalCents:10000,bonusCents:1000,creditTotalCents:11000,channel:'cash',status:'pending_collection',createdAt:new Date().toISOString(),origin:'customer'}
 globalThis.fetch=async(url,options)=>{const body=options?.body&&JSON.parse(options.body);calls.push({url,body});if(url.endsWith('/me'))return response({accounts:[],pendingBills:[],context:{transactionsEnabled:true,paynowConfigured:false,store:{timezone:'Asia/Singapore'}}});if(url.endsWith('/topup-offers'))return response(offers);if(url.endsWith('/topup-quotes'))return response({quoteId:'quote',principalCents:10000,bonusCents:1000,creditTotalCents:11000,expiresAt:new Date(Date.now()+300000).toISOString()});if(url.endsWith('/cancel'))return response({...topup,status:'cancelled'});if(url.endsWith('/declare-paid'))return response({...topup,customerDeclaredPaidAt:new Date().toISOString()});if(options?.method==='POST')return response(topup);return response([])}
 const scope=mount(CustomerTopups,'customer',reactive({params:{storeId:'A'},query:{},hash:''}));await tick()
 assert.match(allText(scope.root),/选择一档/)
 const radio=nodes(scope.root,n=>n.tag==='input'&&n.props.value==='offer')[0];radio.listeners.change({target:radio});await tick()
 assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n).includes('获取报价')).length,0)
 const confirm=nodes(scope.root,n=>n.tag==='button'&&allText(n).includes('确认充值'))[0];assert.equal(confirm.props.disabled,false)
 confirm.props.onClick();await new Promise(r=>setTimeout(r,30));await tick()
 const request=calls.find(c=>c.url.endsWith('/topups')&&c.body);assert.ok(request);assert.deepEqual(Object.keys(request.body).sort(),['idempotencyKey','quoteId']);assert.equal(request.body.quoteId,'quote')
 assert.match(allText(scope.root),/等待商户核实|到店交付现金/)
 nodes(scope.root,n=>n.tag==='button'&&allText(n).includes('我已付款'))[0].props.onClick();await new Promise(r=>setTimeout(r,10));await tick();assert.ok(calls.some(c=>c.url.endsWith('/declare-paid')));assert.match(allText(scope.root),/顾客已声明付款/)
 nodes(scope.root,n=>n.tag==='button'&&allText(n).includes('取消'))[0].props.onClick();await new Promise(r=>setTimeout(r,10));await tick();assert.ok(calls.some(c=>c.url.endsWith('/cancel')));assert.match(allText(scope.root),/已取消/);assert.ok(!calls.some(c=>c.url.endsWith('/confirm')||c.url.endsWith('/accounts')));scope.unmount()
})


function customerRecharge(fetchPost,paynowConfigured=true){
 const calls=[];globalThis.fetch=async(url,options)=>{
  const body=options?.body&&JSON.parse(options.body);calls.push({url,body})
  if(options?.method==='POST')return fetchPost(url,body)
  return response(url.endsWith('/me')?{accounts:[],pendingBills:[],context:{transactionsEnabled:true,paynowConfigured,store:{timezone:'Asia/Singapore'}}}:url.endsWith('/topup-offers')?offers:[])
 }
 const route=reactive({params:{storeId:'A'},query:{},hash:''}),scope=mount(CustomerTopups,'customer',route)
 return {...scope,route,calls,select:async(value)=>{const radio=nodes(scope.root,n=>n.tag==='input'&&n.props.value===value)[0];radio.listeners.change({target:radio});await tick()},confirm:()=>nodes(scope.root,n=>n.tag==='button'&&allText(n).includes('确认充值'))[0]}
}
const rechargeQuote=()=>({quoteId:'automatic-quote',principalCents:10000,bonusCents:1000,creditTotalCents:11000,expiresAt:new Date(Date.now()+300000).toISOString()})
test('PayNow choice explains missing setup and is usable with setup; customer confirms once then shows principal payment QR',async()=>{
 let scope=customerRecharge(()=>{throw Error('must not post')},false);await tick()
 const paynow=nodes(scope.root,n=>n.tag==='input'&&n.props.value==='paynow')[0];assert.equal(paynow.props.disabled,true);assert.match(allText(scope.root),/门店 PayNow 收款配置/)
 assert.equal(scope.confirm().props.disabled,true);scope.unmount()
 scope=customerRecharge((url,body)=>response(url.endsWith('/topup-quotes')?{...rechargeQuote(),channel:'paynow'}:{id:'paynow-topup',createdAt:new Date().toISOString(),channel:'paynow',status:'pending_collection',principalCents:10000,bonusCents:1000,creditTotalCents:11000,amountCents:10000}));await tick()
 await scope.select('offer');await scope.select('paynow');scope.confirm().props.onClick();await new Promise(r=>setTimeout(r,15));await tick()
 assert.equal(scope.calls.find(c=>c.url.endsWith('/topup-quotes')).body.channel,'paynow');assert.match(allText(scope.root),/仅支付本金金额/);assert.match(allText(scope.root),/展示 PayNow 收款码/);assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n).includes('获取报价')).length,0);scope.unmount()
})
test('automatic quote failure prevents creation and displays its error',async()=>{
 const scope=customerRecharge(()=>({ok:false,status:409,json:async()=>({code:'WALLET_OFFER_CHANGED',message:'档位已更新'})}));await tick();await scope.select('offer');scope.confirm().props.onClick();await tick()
 assert.equal(scope.calls.filter(c=>c.body&&c.url.endsWith('/topups')).length,0);assert.match(allText(scope.root),/档位已更新/);scope.unmount()
})
test('one-click confirmation blocks duplicates and preserves quote and idempotency key after uncertain submission',async()=>{
 let attempts=0,resolveQuote;const scope=customerRecharge((url)=>url.endsWith('/topup-quotes')?new Promise(resolve=>{resolveQuote=resolve}):(++attempts===1?Promise.reject(Error('network lost')):response({id:'recovered',createdAt:new Date().toISOString(),status:'pending_collection',channel:'cash',amountCents:10000})))
 await tick();await scope.select('offer');scope.confirm().props.onClick();scope.confirm().props.onClick();await tick();assert.equal(scope.calls.filter(c=>c.url.endsWith('/topup-quotes')).length,1)
 resolveQuote(response(rechargeQuote()));await new Promise(r=>setTimeout(r,10));await tick();assert.match(allText(scope.root),/network lost/)
 scope.confirm().props.onClick();await new Promise(r=>setTimeout(r,10));await tick()
 const requests=scope.calls.filter(c=>c.body&&c.url.endsWith('/topups'));assert.equal(requests.length,2);assert.deepEqual(requests[0].body,requests[1].body);assert.equal(scope.calls.filter(c=>c.url.endsWith('/topup-quotes')).length,1);scope.unmount()
})
test('store change while automatic quote is in flight cannot create a top-up',async()=>{
 let resolveQuote;const scope=customerRecharge(()=>new Promise(resolve=>{resolveQuote=resolve}));await tick();await scope.select('offer');scope.confirm().props.onClick();await tick()
 scope.route.params.storeId='B';await tick();resolveQuote(response(rechargeQuote()));await tick()
 assert.equal(scope.calls.filter(c=>c.body&&c.url.endsWith('/topups')).length,0);scope.unmount()
})

test('points deductions use loaded version and do not mutate money APIs',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,body:options?.body&&JSON.parse(options.body)});return response(options?.method==='POST'?{account:{accountId:'p',balance:7,version:4},entry:{id:'e',quantity:3,kind:'manual_deduct'}}:url.includes('/entries')?[]:{accountId:'p',balance:10,version:3})}
 const scope=composable(()=>pointsModule.usePoints(reactive({value:'A'}),false,reactive({value:'c'})));await tick();await scope.state.command('deductions',{quantity:3,reason:'调整'});assert.equal(calls.at(-1).body.expectedVersion,3);assert.equal(scope.state.account.value.balance,7);assert.ok(calls.every(c=>c.url.includes('/points')));scope.unmount()
})

const staffContext={transactionsEnabled:true,paynowConfigured:true,store:{timezone:'Asia/Singapore'},program:{channels:['cash']}}
const defaultSettings={version:4,baseOffers:[offers.offers[0]],deductionPolicy:'proportional',selfServiceEnabled:true,campaignsEnabled:true,pointsEnabled:true,affectedStoreIds:['A']}
test('marketing preserves enabled campaigns and serializes editable base offer amounts as decimal strings',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,body:options?.body&&JSON.parse(options.body)});return response(url.endsWith('/context')?staffContext:url.includes('/campaigns')?{items:[],nextCursor:''}:defaultSettings)}
 const scope=mount(MarketingPage,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),{},['wallet.marketing.view','wallet.marketing.manage']);await tick()
 const form=nodes(scope.root,n=>n.tag==='form')[0];await form.props.onSubmit({preventDefault(){}});await new Promise(r=>setTimeout(r,10));await tick()
 const body=calls.find(c=>c.body&&c.url.endsWith('/marketing/settings')).body;assert.equal(body.campaignsEnabled,true);assert.equal(body.expectedVersion,4);assert.deepEqual(body.baseOffers,[{amount:'100.00',bonus:'10.00'}]);scope.unmount()
})
test('published campaign with newer draft is publishable and management is hidden from viewers',async()=>{
 const c={id:'c',name:'活动',status:'published',version:3,draftVersion:2,currentPublishedRevision:1,startsAt:new Date().toISOString(),endsAt:new Date(Date.now()+3600000).toISOString(),priority:1,offers:offers.offers}
 globalThis.fetch=async url=>response(url.endsWith('/context')?staffContext:url.includes('/campaigns')?{items:[c],nextCursor:''}:defaultSettings)
 const scope=mount(MarketingPage,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),{},['wallet.marketing.view','wallet.marketing.manage']);await tick()
 const publish=nodes(scope.root,n=>n.tag==='button'&&allText(n)==='发布')[0];assert.equal(publish.props.disabled,false);scope.unmount()
 const readOnly=mount(MarketingPage,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),{},['wallet.marketing.view']);await tick();assert.equal(nodes(readOnly.root,n=>n.tag==='form').length,0);assert.equal(nodes(readOnly.root,n=>n.tag==='button'&&allText(n)==='发布').length,0);readOnly.unmount()
})
test('refund preview uses remaining-lot mode without client cash amounts and invalidates after source change',async()=>{
 const calls=[],previews=[];globalThis.fetch=async(url,options)=>{calls.push({url,body:options?.body&&JSON.parse(options.body)});return response(url.endsWith('/context')?staffContext:url.endsWith('/refund-previews')?{amountCents:5000,walletCents:5500,cashPrincipalRefundCents:5000,principalReturnCents:0,bonusReturnCents:0,bonusRevokeCents:500,accountId:'a',accountVersion:7,mode:'remaining_lot_exit'}:[])}
 const props=reactive({store:'A',account:{id:'a',version:7},sourceType:'top_up',sourceId:'t',amount:'50.00',mode:'remaining_lot_exit',reason:'退出',onPreview:v=>previews.push(v)})
 const scope=mount(RefundComponent,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),props);await tick();await nodes(scope.root,n=>n.tag==='button'&&allText(n)==='退款预览')[0].props.onClick();await tick()
 assert.deepEqual(calls.find(c=>c.url.endsWith('/refund-previews')).body,{sourceType:'top_up',sourceId:'t',mode:'remaining_lot_exit'});assert.equal(previews.at(-1).accountVersion,7);assert.match(allText(scope.root),/实退本金/);assert.match(allText(scope.root),/撤销赠送/)
 props.sourceId='other';await tick();assert.equal(previews.at(-1),null);scope.unmount()
})
test('points request denied and stale version are visible and stale commands require refresh',async()=>{
 let writes=0;globalThis.fetch=async(url,options)=>{if(options?.method==='POST'){writes++;return{ok:false,status:409,json:async()=>({code:'POINTS_VERSION_STALE',message:'积分已更新，请刷新'})}}return response(url.includes('/entries')?[]:{accountId:'p',balance:5,version:1})}
 const scope=composable(()=>pointsModule.usePoints(reactive({value:'A'}),false,reactive({value:'c'})));await tick();await scope.state.command('deductions',{quantity:1,reason:'修正'});assert.equal(scope.state.stale.value,true);assert.match(scope.state.error.value,/刷新/);await scope.state.command('deductions',{quantity:1,reason:'修正'});assert.equal(writes,1);await scope.state.refresh();assert.equal(scope.state.stale.value,false);scope.unmount()
 globalThis.fetch=async()=>({ok:false,status:403,json:async()=>({code:'FORBIDDEN',message:'权限不足'})});const denied=composable(()=>pointsModule.usePoints(reactive({value:'A'}),false,reactive({value:'c'})));await tick();assert.equal(denied.state.account.value,null);assert.match(denied.state.error.value,/权限不足/);denied.unmount()
})
test('points customer selection does not create a funding account',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,method:options?.method});return response(url.endsWith('/context')?staffContext:url.includes('/points/entries')?[]:url.endsWith('/points')?{accountId:null,balance:0,version:0}:[])}
 const scope=mount(StaffPoints,'staff',reactive({params:{storeId:'A'},query:{},hash:''}));await tick();await scope.workspace.choose({id:'c',phone:'123',customerCode:'C'},false);await tick();assert.ok(!calls.some(c=>c.method==='POST'));assert.match(allText(scope.root),/积分余额: 0/);scope.unmount()
})

test('bonus-only exit requires explicit attestation and uses account version without creating a zero cash refund',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,body:options?.body&&JSON.parse(options.body)});return response(url.endsWith('/context')?staffContext:url.endsWith('/funding-lots')?[{id:'lot',accountId:'a',principal:0,bonus:500,reservedP:0,reservedG:0,version:1}]:{})}
 const props=reactive({store:'A',account:{id:'a',version:7},sourceType:'top_up',sourceId:'t',amount:'',mode:'remaining_lot_exit',reason:'放弃赠送'})
 const scope=mount(RefundComponent,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),props);await tick()
 const exit=()=>nodes(scope.root,n=>n.tag==='button'&&allText(n)==='仅赠送权益退出')[0];assert.equal(exit().props.disabled,true)
 const select=nodes(scope.root,n=>n.tag==='select')[0];select.options.forEach(o=>o.selected=o._value==='lot');select.listeners.change({target:select});await tick()
 const checkbox=nodes(scope.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0];checkbox.checked=true;checkbox.listeners.change({target:checkbox});await tick();assert.equal(exit().props.disabled,false);exit().props.onClick();await new Promise(r=>setTimeout(r,10));await tick()
 const body=calls.find(c=>c.url.endsWith('/exit-bonus')).body;assert.equal(body.expectedVersion,7);assert.equal(body.accountId,'a');assert.ok(!calls.some(c=>c.url.endsWith('/refunds')));scope.unmount()
})
test('campaign time input respects store timezone across browser timezone and rejects nonexistent wall times',async()=>{
 const {build}=await import('esbuild'),{writeFile}=await import('node:fs/promises'),{pathToFileURL}=await import('node:url');const result=await build({entryPoints:['src/modules/wallet/marketingTime.ts'],bundle:true,write:false,platform:'node',format:'esm'});const file=process.cwd()+'/target/wallet-marketing-component-tests/time.mjs';await writeFile(file,result.outputFiles[0].contents);const time=await import(pathToFileURL(file).href)
 assert.equal(time.campaignLocalTime('2026-10-23T00:00:00Z','Asia/Singapore'),'2026-10-23T08:00');assert.equal(time.campaignInstant('2026-10-23T08:00','Asia/Singapore'),'2026-10-23T00:00:00.000Z');assert.throws(()=>time.campaignInstant('2026-03-08T02:30','America/New_York'))
})

test('entry allocation details render exact server batches and historical empty allocations never synthesize a lot',async()=>{
 globalThis.fetch=async()=>response(staffContext)
 const route=reactive({params:{storeId:'A'},query:{},hash:''}),props=reactive({entry:{id:'entry',amountCents:11000,principalCents:10000,bonusCents:1000,allocations:[{lotId:'lot-real-one',principalCents:6000,bonusCents:600},{lotId:'lot-real-two',principalCents:4000,bonusCents:400}]}})
 const scope=mount(EntryAllocations,'staff',route,props);await tick();assert.equal(nodes(scope.root,n=>n.tag==='details').length,1);assert.match(allText(scope.root),/lot-real-one/);assert.match(allText(scope.root),/lot-real-two/);assert.match(allText(scope.root),/60.00/);assert.match(allText(scope.root),/6.00/)
 props.entry={id:'legacy',amountCents:5000,principalCents:5000,bonusCents:0,allocations:[]};await tick();assert.equal(nodes(scope.root,n=>n.tag==='details').length,0);assert.match(allText(scope.root),/历史本金（无批次明细）/);assert.doesNotMatch(allText(scope.root),/lot-real/);scope.unmount()
})

test('reconciliation distinguishes cash principal receipts from credited bonus and checks component totals',async()=>{
 const report={topupCashCents:10000,topupPaynowCents:0,topupCardCents:0,creditedTopupCents:11000,walletSpentCents:0,externalBillCents:0,refundsCents:0,pendingTopupCents:0,pendingRefundCents:0,outstandingBillCents:0,programBalanceCents:11000,programReservedCents:0,exceptionReceivedCents:0,creditedPrincipalCents:10000,creditedBonusCents:1000,componentMismatchAccounts:0,fundingMismatchAccounts:0,ledgerMismatchAccounts:0,collections:[]}
 globalThis.fetch=async url=>response(url.endsWith('/context')?staffContext:report)
 const scope=mount(ReportPanel,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),{store:'A'});await tick();nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.match(allText(scope.root),/充值本金入账/);assert.match(allText(scope.root),/充值赠送入账/);assert.match(allText(scope.root),/10.00/);assert.match(allText(scope.root),/资金组件不一致账户数/);scope.unmount()
})
test('points-only staff initializes a narrow context and selects a customer without funds endpoints',async()=>{
 const calls=[];globalThis.fetch=async url=>{calls.push(url);return response(url.endsWith('/points/context')?{...staffContext,program:null}:url.includes('/points/entries')?[]:{accountId:null,balance:0,version:0})}
 const scope=mount(StaffPoints,'staff',reactive({name:'wallet-topup',params:{storeId:'A'},query:{kind:'points'},hash:''}),{},['wallet.points.view','wallet.points.grant']);await tick();assert.ok(scope.workspace.context.value);assert.equal(calls[0],'/api/v1/stores/A/wallet/points/context');await scope.workspace.choose({id:'points-customer',customerCode:'P',phone:'123'},false);await tick();assert.match(allText(scope.root),/积分余额: 0/);assert.ok(!calls.some(url=>url.endsWith('/wallet/context')||url.includes('/accounts')));scope.unmount()
})

test('exception receipt refund requires a preview and submits the preview account version',async()=>{
 const calls=[],sourceId='11111111-1111-1111-1111-111111111111'
 globalThis.fetch=async(url,options)=>{const body=options?.body&&JSON.parse(options.body);calls.push({url,body});return response(url.endsWith('/context')?staffContext:url.endsWith('/refund-previews')?{amountCents:1000,walletCents:0,cashPrincipalRefundCents:0,principalReturnCents:0,bonusReturnCents:0,bonusRevokeCents:0,accountId:'a',accountVersion:9,mode:'normal'}:url.endsWith('/refunds')?{refund:{id:'refund',status:'pending'},parts:[]}:[])}
 const scope=mount(LedgerPanel,'staff',reactive({params:{storeId:'A'},query:{sourceType:'collection_exception',sourceId},hash:''}),{store:'A',account:null,timezone:'Asia/Singapore',canRefund:true,mode:'refund-create'});await tick()
 const form=nodes(scope.root,n=>n.tag==='form')[0],amount=nodes(form,n=>n.tag==='input'&&n.props.type==='number')[0],reason=nodes(form,n=>n.tag==='textarea')[0]
 amount.props['onUpdate:modelValue']('10.00');reason.props['onUpdate:modelValue']('异常实收退回');await tick()
 const create=()=>nodes(form,n=>n.tag==='button'&&n.props.type!=='button')[0]
 assert.equal(create().props.disabled,true)
 await form.props.onSubmit({preventDefault(){}});await tick();assert.equal(calls.filter(c=>c.url.endsWith('/refunds')).length,0)
 const preview=nodes(form,n=>n.tag==='button'&&allText(n)==='退款预览')[0];assert.ok(preview);await preview.props.onClick();await tick();assert.equal(create().props.disabled,false)
 assert.deepEqual(calls.find(c=>c.url.endsWith('/refund-previews')).body,{sourceType:'collection_exception',sourceId,amount:'10.00'})
 await form.props.onSubmit({preventDefault(){}});await new Promise(r=>setTimeout(r,10));await tick()
 const body=calls.find(c=>c.url.endsWith('/refunds')).body;assert.equal(body.expectedAccountVersion,9);assert.equal(body.sourceType,'collection_exception');assert.equal(body.amount,'10.00');assert.equal(body.reason,'异常实收退回');scope.unmount()
})

const debitCode='WC.'+'A'.repeat(43),funded={id:'funded',version:2,balanceCents:11000,reservedCents:0}
const permissions=['wallet.view','wallet.charge.create','wallet.collection.confirm','wallet.topup.create']
function enterNumber(root,value){const input=nodes(root,n=>n.tag==='input'&&n.props.type==='number')[0];input.value=String(value);input.listeners.input({target:input});return input}
const submitEvent=()=>({preventDefault(){}})
test('staff payment requires a fresh scan after amount, ignores prior customer and queries an uncertain result',async()=>{
 const calls=[];let resolveWrite
 globalThis.fetch=async(url,options)=>{const body=options?.body&&JSON.parse(options.body);calls.push({url,body});if(url.endsWith('/personal-scan-charges'))return new Promise((resolve,reject)=>{resolveWrite=()=>reject(Error('response lost'))});if(url.includes('/personal-scan-charges/result'))return response({status:'paid',bill:{id:'paid'}});return response(staffContext)}
 const scope=mount(StaffCollect,'staff',reactive({params:{storeId:'fresh-scan'},query:{},hash:''}),{},permissions);await tick();scope.workspace.account.value={...funded};scope.workspace.customerCode.value=debitCode;enterNumber(scope.root,55);await tick()
 assert.equal(calls.filter(c=>c.body).length,0)
 const form=nodes(scope.root,n=>n.tag==='form'&&n.props.class==='wallet-amount-card')[0];form.props.onSubmit(submitEvent());await tick();const scanner=nodes(scope.root,n=>n.props?.['data-scan'])[0];scanner.props.onClick();scanner.props.onClick();await tick();assert.equal(calls.filter(c=>c.url.endsWith('/personal-scan-charges')).length,1)
 const body=calls.find(c=>c.url.endsWith('/personal-scan-charges')).body;assert.equal(body.customerCode,'WI.'+'A'.repeat(43));assert.equal(body.accountId,undefined);assert.equal(body.customerId,undefined);assert.equal(body.amount,'55')
 resolveWrite();await tick();assert.match(allText(scope.root),/结果尚未确认/);assert.equal(nodes(scope.root,n=>n.tag==='input'&&n.props.type==='number')[0].props.disabled,true)
 await nodes(scope.root,n=>n.tag==='button'&&allText(n).includes('查询 / 重试原交易'))[0].props.onClick();await tick();assert.equal(calls.filter(c=>c.url.endsWith('/personal-scan-charges')).length,1);assert.ok(calls.some(c=>c.url.includes('/result?idempotencyKey='+body.idempotencyKey)));scope.unmount()
})

test('late and failed charge previews cannot enable a changed amount or account',async()=>{
 const pending=[],store=reactive({value:'A'}),account=reactive({value:{...funded}}),amount=reactive({value:'55'}),code=reactive({value:debitCode}),enabled=reactive({value:true})
 globalThis.fetch=async(url,options)=>await new Promise(resolve=>pending.push({url,body:JSON.parse(options.body),resolve}))
 const scope=composable(()=>chargeComponentsModule.useChargeComponents(store,account,amount,code,enabled));await tick();amount.value='66';await tick();pending[0].resolve(response({accountId:'funded',accountVersion:2,principalCents:5000,bonusCents:500}));await tick();assert.equal(scope.state.components.value,null)
 pending[1].resolve({ok:false,status:409,json:async()=>({code:'WALLET_ALLOCATION_STALE',message:'changed'})});await tick();assert.equal(scope.state.components.value,null);assert.match(scope.state.error.value,/changed/)
 account.value={...funded,id:'other'};await tick();store.value='B';await tick();pending[2].resolve(response({accountId:'other',accountVersion:9,principalCents:6000,bonusCents:600}));await tick();assert.equal(scope.state.components.value,null);assert.ok(pending[3].url.includes('/stores/B/'));scope.unmount()
})
test('existing bill previews remaining amount but confirms original bill amount and server account version',async()=>{
 const calls=[],bill={id:'bill',accountId:'funded',amountCents:10000,walletPaidCents:0,externalPaidCents:4500,version:3}
 globalThis.fetch=async(url,options)=>{const body=options?.body&&JSON.parse(options.body);calls.push({url,body});return response(url.endsWith('/payment-codes/resolve')?{account:funded,customer:{id:'c',customerCode:'C'}}:url.endsWith('/charge-previews')?{accountId:'funded',accountVersion:7,principalCents:5000,bonusCents:500,allocations:[]}:url.endsWith('/scan-charges')?{bill:{...bill,status:'paid'}}:staffContext)}
 const scope=mount(ExistingCharge,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),{store:'A',bill},permissions);await tick();await nodes(scope.root,n=>n.props?.['data-scan'])[0].props.onClick();await tick();assert.match(allText(scope.root),/50.00/)
 await nodes(scope.root,n=>n.tag==='button'&&allText(n).includes('确认扣款'))[0].props.onClick();await new Promise(r=>setTimeout(r,10));await tick();assert.equal(calls.find(c=>c.url.endsWith('/charge-previews')).body.amount,'55.00');const body=calls.find(c=>c.url.endsWith('/scan-charges')).body;assert.equal(body.amount,'100.00');assert.equal(body.expectedAccountVersion,7);scope.unmount()
})
test('ordinary topup number input is sent as a JSON decimal string',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{const body=options?.body&&JSON.parse(options.body);calls.push({url,body});return response(url.endsWith('/topup-offers')?{...offers,campaignId:null}:url.endsWith('/topups')?{id:'new-topup'}:staffContext)}
 const scope=mount(StaffTopup,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),{},permissions);await tick();scope.workspace.account.value={...funded};await tick();enterNumber(scope.root,20);await tick();await nodes(scope.root,n=>n.tag==='form'&&n.props.class==='wallet-amount-card')[0].props.onSubmit(submitEvent());await new Promise(r=>setTimeout(r,10));await tick();const body=calls.find(c=>c.url.endsWith('/topups')).body;assert.equal(body.amount,'20');assert.equal(typeof body.amount,'string');scope.unmount()
})
test('disabled points retain original-entry reversal operation without a history list',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push(url);return response(url.endsWith('/points/context')?{...staffContext,transactionsEnabled:false,program:null}:{accountId:'p',balance:100,version:2})}
 const scope=mount(StaffPoints,'staff',reactive({name:'wallet-topup',params:{storeId:'A'},query:{kind:'points'},hash:''}),{},['wallet.view','wallet.points.view','wallet.points.grant','wallet.points.reverse']);await tick();await scope.workspace.choose({id:'c',customerCode:'C'},false);await tick();assert.ok(calls.some(u=>u.endsWith('/points/context')));assert.ok(!calls.some(u=>u.includes('/entries')));const forms=nodes(scope.root,n=>n.tag==='form'&&n.props.class==='wallet-form');assert.equal(forms.length,1);const reverse=nodes(forms[0],n=>n.tag==='button'&&allText(n)==='冲正')[0];assert.equal(reverse.props.disabled,true);const input=nodes(forms[0],n=>n.tag==='input')[0];input.props['onUpdate:modelValue']('entry');await tick();assert.equal(reverse.props.disabled,false);scope.unmount()
})

test('campaign creation sends explicit zero expectedVersion and decimal offer input',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{const body=options?.body&&JSON.parse(options.body);calls.push({url,body});return response(url.endsWith('/context')?staffContext:url.endsWith('/marketing/settings')?defaultSettings:options?.method==='POST'?{id:'new',name:body.name,status:'draft',startsAt:body.startsAt,endsAt:body.endsAt,priority:body.priority,version:1,offers:[],draftVersion:1}:{items:[],nextCursor:''})}
 const scope=mount(MarketingPage,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),{},['wallet.marketing.view','wallet.marketing.manage']);await tick();const form=nodes(scope.root,n=>n.tag==='form')[1],inputs=nodes(form,n=>n.tag==='input');inputs[0].props['onUpdate:modelValue']('new');inputs.find(n=>n.props.type==='datetime-local').props['onUpdate:modelValue']('2026-11-01T00:00');nodes(form,n=>n.tag==='input'&&n.props.type==='datetime-local')[1].props['onUpdate:modelValue']('2026-12-01T00:00');nodes(form,n=>n.tag==='button'&&allText(n).includes('四档示例'))[0].props.onClick();await tick();await form.props.onSubmit(submitEvent());await new Promise(r=>setTimeout(r,10));await tick();const body=calls.find(c=>c.body&&c.url.endsWith('/marketing/campaigns')).body;assert.equal(body.expectedVersion,0);assert.equal(body.offers[0].amount,'100.00');assert.equal(body.offers[0].bonus,'10.00');scope.unmount()
})

test('staff navigation switches points and funding contexts immediately and ignores old results',async()=>{
 const requests=[];globalThis.fetch=async url=>await new Promise(resolve=>requests.push({url,resolve}))
 const route=reactive({name:'wallet-collect',params:{storeId:'A'},query:{},hash:''}),scope=mount(StaffPoints,'staff',route,{},['wallet.view','wallet.points.view']);await tick();requests[0].resolve(response(staffContext));await tick();scope.workspace.account.value={...funded}
 route.name='wallet-topup';route.query={kind:'points'};assert.equal(scope.workspace.context.value,null);assert.equal(scope.workspace.account.value,null);await tick();assert.ok(requests[1].url.endsWith('/points/context'))
 route.name='wallet-collect';route.query={};assert.equal(scope.workspace.context.value,null);await tick();assert.ok(requests[2].url.endsWith('/wallet/context'));requests[1].resolve(response({...staffContext,program:null,transactionsEnabled:false}));await tick();assert.equal(scope.workspace.context.value,null);requests[2].resolve(response(staffContext));await tick();assert.ok(scope.workspace.context.value.program);assert.ok(!requests.some(r=>r.url.includes('/accounts/')));scope.unmount()
})


test('customer wallet home shows points balance and unified transactions entry',async()=>{
 const entries=[{id:'grant',kind:'manual_grant',quantity:1000,beforeBalance:0,afterBalance:1000,createdAt:new Date().toISOString(),reason:'欢迎积分'},{id:'redeem',kind:'redemption',quantity:50,beforeBalance:1000,afterBalance:950,createdAt:new Date().toISOString(),redemptionDescription:'兑换咖啡'},{id:'reverse',kind:'reversal',quantity:10,beforeBalance:950,afterBalance:960,createdAt:new Date().toISOString(),reason:'纠正扣分'}]
 globalThis.fetch=async url=>response(url.endsWith('/me')?{accounts:[],pendingBills:[],context:{transactionsEnabled:true,store:{timezone:'Asia/Singapore'}}}:url.includes('/entries')?entries:{balance:960,version:3})
 const scope=mount(CustomerHome,'customer',reactive({params:{storeId:'A'},query:{},hash:''}));await tick();assert.match(allText(scope.root),/积分余额/);assert.match(allText(scope.root),/960/);assert.ok(nodes(scope.root,n=>n.tag==='a'&&n.props.href==='/wallet/A/transactions').length);scope.unmount()
})

test('unified recharge retains the selected customer when switching to points and back; points mode never opens a wallet',async()=>{
 const calls=[];globalThis.fetch=async url=>{calls.push(url);return response(url.endsWith('/points/context')?{...staffContext,program:null}:url.endsWith('/context')?staffContext:url.endsWith('/topup-offers')?{...offers,campaignId:null}:url.endsWith('/accounts')?[funded]:url.includes('/entries')?[]:{accountId:'points',balance:100,version:1})}
 const route=reactive({name:'wallet-topup',params:{storeId:'A'},query:{},hash:''}),scope=mount(RechargePage,'staff',route);await tick();await scope.workspace.choose({id:'c',displayName:'测试顾客',phone:'+6581234567'});scope.workspace.identifiedCustomerId.value='c';await tick();const before=calls.length
 route.query.kind='points';await tick();assert.equal(scope.workspace.customer.value.id,'c');assert.match(allText(scope.root),/积分余额: 100/);assert.ok(calls.slice(before).some(u=>u.endsWith('/points/context')));assert.ok(!calls.slice(before).some(u=>u.endsWith('/accounts')))
 route.query.kind='money';await tick();assert.equal(scope.workspace.customer.value.id,'c');assert.ok(scope.workspace.account.value);assert.match(allText(scope.root),/金额（SGD）/);route.params.storeId='B';assert.equal(scope.workspace.customer.value,null);assert.equal(scope.workspace.account.value,null);scope.unmount()
})

test('points-only user can enter unified recharge without money permissions or APIs and more has no separate points entry',async()=>{
 const calls=[];globalThis.fetch=async url=>{calls.push(url);return response(url.endsWith('/points/context')?{...staffContext,program:null}:url.includes('/entries')?[]:{balance:0,version:0})}
 const route=reactive({name:'wallet-topup',params:{storeId:'A'},query:{kind:'points'},hash:''}),scope=mount(RechargePage,'staff',route,{},['wallet.points.view','wallet.points.grant']);await tick();await scope.workspace.choose({id:'c',phone:'+6581234567'},false);scope.workspace.identifiedCustomerId.value='c';await tick();assert.match(allText(scope.root),/积分余额: 0/);assert.ok(!calls.some(u=>u.endsWith('/wallet/context')||u.endsWith('/accounts')||u.endsWith('/topup-offers')));scope.unmount()
 const menu=mount(StaffMore,'staff',route,{},['wallet.points.view']);await tick();assert.equal(nodes(menu.root,n=>n.tag==='a'&&n.props.href==='/stores/A/wallet/points').length,0);assert.equal(nodes(menu.root,n=>n.tag==='a'&&n.props.href==='/stores/A/wallet/topup').length,1);menu.unmount()
})


test('wallet viewer without topup permission uses the points context and cannot see money or mutation forms',async()=>{
 const calls=[];globalThis.fetch=async url=>{calls.push(url);return response(url.endsWith('/points/context')?{...staffContext,program:null}:url.includes('/entries')?[]:{balance:12,version:1})}
 const scope=mount(RechargePage,'staff',reactive({name:'wallet-topup',params:{storeId:'A'},query:{kind:'points'},hash:''}),{},['wallet.view','wallet.points.view']);await tick();await scope.workspace.choose({id:'c'},false);scope.workspace.identifiedCustomerId.value='c';await tick();assert.match(allText(scope.root),/积分余额: 12/);assert.equal(nodes(scope.root,n=>n.tag==='form'&&n.props.class==='wallet-form').length,0);assert.ok(!calls.some(u=>u.endsWith('/wallet/context')||u.endsWith('/accounts')));scope.unmount()
})

test('points API failure leaves customer funds visible with a retry action',async()=>{
 globalThis.fetch=async url=>url.endsWith('/me')?response({accounts:[{id:'a',balanceCents:4500,reservedCents:0}],pendingBills:[],context:{store:{timezone:'Asia/Singapore'}}}):({ok:false,status:503,json:async()=>({message:'积分暂时不可用'})})
 const scope=mount(CustomerHome,'customer',reactive({params:{storeId:'A'},query:{},hash:''}));await tick();assert.match(allText(scope.root),/45.00/);assert.match(allText(scope.root),/积分暂时不可用/);assert.ok(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='刷新').length);scope.unmount()
})


test('recharge starts with personal wallet scan, no member lookup or operations before identification',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{const body=options?.body&&JSON.parse(options.body);calls.push({url,body});return response(url.endsWith('/customer-codes/resolve')?{id:'identified',displayName:'扫码顾客',phone:'+6581234567'}:staffContext)}
 const scope=mount(RechargePage,'staff',reactive({name:'wallet-topup',params:{storeId:'A'},query:{},hash:''}));await tick();assert.equal(nodes(scope.root,n=>n.tag==='form'&&n.props.class==='wallet-search').length,0);assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='钱包充值').length,0)
 nodes(scope.root,n=>n.tag==='button'&&allText(n).includes('扫描顾客个人钱包码'))[0].props.onClick();await tick();nodes(scope.root,n=>n.props?.['data-scan'])[0].props.onClick();await tick();assert.match(allText(scope.root),/扫码顾客/);assert.ok(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='钱包充值').length);assert.ok(!calls.some(c=>c.url.endsWith('/accounts')||c.url.endsWith('/topup-offers')));assert.equal(scope.workspace.customerCode.value,'');scope.unmount()
})


test('personal code issues for a customer with no money account and never grants payment consent',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,body:options?.body&&JSON.parse(options.body)});return response(url.endsWith('/me')?{accounts:[],pendingBills:[],context:{transactionsEnabled:true,store:{timezone:'Asia/Singapore'}}}:{type:'customer_identity',code:'WI.'+'A'.repeat(43),expiresAt:new Date(Date.now()+60000).toISOString()})}
 const scope=mount(PersonalCode,'customer',reactive({params:{storeId:'A'},query:{},hash:''}));await tick();assert.equal(nodes(scope.root,n=>n.tag==='figure')[0].props['data-code'],'WI.'+'A'.repeat(43));assert.deepEqual(calls.find(c=>c.url.endsWith('/personal-codes')).body,{});assert.ok(!calls.some(c=>c.url.endsWith('/payment-codes')||c.url.endsWith('/accounts')));scope.unmount()
})


test('manually selected customer from another workflow is not treated as scanned identity',async()=>{
 globalThis.fetch=async()=>response(staffContext)
 const scope=mount(RechargePage,'staff',reactive({name:'wallet-topup',params:{storeId:'A'},query:{kind:'points'},hash:''}));await tick();await scope.workspace.choose({id:'manual',displayName:'手工选客'},false);await tick();assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='钱包充值').length,0);assert.match(allText(scope.root),/扫描顾客个人钱包码/);assert.doesNotMatch(allText(scope.root),/已扫码识别/);scope.unmount()
})


test('entering scan-first from a manual points selection cannot open a money account before identification',async()=>{
 const calls=[];globalThis.fetch=async url=>{calls.push(url);return response(url.endsWith('/points/context')?{...staffContext,program:null}:staffContext)}
 const route=reactive({name:'wallet-topup',params:{storeId:'A'},query:{kind:'points'},hash:''}),scope=mount(RechargePage,'staff',route);await tick();await scope.workspace.choose({id:'manual',phone:'+6581234567'},false);await tick();route.name='wallet-topup';await tick();assert.ok(!calls.some(u=>u.endsWith('/accounts')));assert.match(allText(scope.root),/扫描顾客个人钱包码/);scope.unmount()
})


test('personal code acceptance sends exact current policy, renews after payment, and preserves original bill store',async()=>{
 const calls=[];let issued=0
 globalThis.fetch=async(url,options)=>{const body=options?.body&&JSON.parse(options.body);calls.push({url,body});if(url.endsWith('/personal-codes/status'))return response({state:'used',bill:{id:'paid',storeId:'other-store',amountCents:500}});if(url.endsWith('/personal-codes'))return response({type:'customer_personal',code:'WI.'+String(++issued).repeat(43),paymentEnabled:issued>1,expiresAt:'2099-01-01T00:00:00Z'});return response({configured:true,limitCents:1000,policyVersion:3,ruleVersion:1,paymentEnabled:!!body?.accepted,consentedLimitCents:body?.accepted?1000:null})}
 const store=reactive({value:'A'}),enabled=reactive({value:true}),scope=composable(()=>personalCodeModule.usePersonalCode(store,enabled));await tick()
 assert.equal(issued,1);assert.ok(!calls.some(c=>c.body?.accepted))
 await scope.state.accept(true,1000);await tick();const accepted=calls.find(c=>c.body?.accepted);assert.equal(accepted.body.policyVersion,3);assert.equal(accepted.body.limitCents,1000);assert.equal(issued,2)
 const old=scope.state.code.value.code;await scope.state.check();await tick();assert.equal(scope.state.receipt.value.id,'paid');assert.equal(scope.state.receipt.value.storeId,'other-store');assert.equal(issued,3);assert.notEqual(scope.state.code.value.code,old);scope.unmount()
})

test('unset personal limit keeps identity usable and offers customer authorization',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,body:options?.body&&JSON.parse(options.body)});return response(url.endsWith('/me')?{accounts:[],pendingBills:[],context:{transactionsEnabled:true,store:{timezone:'Asia/Singapore'}}}:url.endsWith('/personal-payment-consent')?{configured:false,policyVersion:0,limitCents:null,paymentEnabled:false}:{type:'customer_personal',code:'WI.'+'A'.repeat(43),expiresAt:'2099-01-01T00:00:00Z'})}
 const scope=mount(PersonalCode,'customer',reactive({params:{storeId:'A'},query:{},hash:''}));await tick();assert.match(allText(scope.root),/尚未设置/);assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='同意并启用')[0].props.disabled,true);assert.equal(nodes(scope.root,n=>n.tag==='figure').length,1);assert.ok(!calls.some(c=>c.body?.accepted));scope.unmount()
})

test('authorized customer sees no repeated approval form; editing amount clears the prior check',async()=>{
 const calls=[];let version=1,limit=2500
 const consent=()=>({configured:true,policyVersion:version,limitCents:limit,consentedLimitCents:limit,paymentEnabled:true,requiresConsent:false,ruleVersion:1})
 globalThis.fetch=async(url,options)=>{const body=options?.body&&JSON.parse(options.body);calls.push({url,body});if(url.endsWith('/me'))return response({accounts:[],pendingBills:[],context:{transactionsEnabled:true,store:{timezone:'Asia/Singapore'}}});if(url.endsWith('/personal-codes'))return response({code:'WI.'+'A'.repeat(43),expiresAt:'2099-01-01T00:00:00Z'});if(body){version++;limit=body.limitCents}return response(consent())}
 const scope=mount(PersonalCode,'customer',reactive({params:{storeId:'A'},query:{},hash:''}));await tick();assert.equal(nodes(scope.root,n=>n.tag==='form').length,0);assert.match(allText(scope.root),/已启用/)
 nodes(scope.root,n=>n.tag==='button'&&allText(n)==='修改支付上限')[0].props.onClick();await tick();const form=nodes(scope.root,n=>n.tag==='form')[0],amount=nodes(form,n=>n.tag==='input'&&n.props.inputmode==='decimal')[0],check=nodes(form,n=>n.tag==='input'&&n.props.type==='checkbox')[0],save=nodes(form,n=>n.tag==='button')[0]
 check.props['onUpdate:modelValue'](true);await tick();assert.equal(save.props.disabled,false);amount.props['onUpdate:modelValue']('30.00');await tick();assert.equal(save.props.disabled,true);assert.ok(!calls.some(c=>c.body?.accepted));check.props['onUpdate:modelValue'](true);await tick();await form.props.onSubmit(submitEvent());await tick();assert.equal(calls.find(c=>c.body?.accepted).body.limitCents,3000);assert.equal(nodes(scope.root,n=>n.tag==='form').length,0);scope.unmount()
})

test('store switch during payment clears new-store busy state and cannot navigate with old response',async()=>{
 let resolveWrite;globalThis.fetch=async(url,options)=>url.endsWith('/personal-scan-charges')?await new Promise(r=>resolveWrite=r):response(staffContext)
 const route=reactive({params:{storeId:'switch-A'},query:{},hash:''}),scope=mount(StaffCollect,'staff',route,{},permissions);await tick();enterNumber(scope.root,5);await tick();nodes(scope.root,n=>n.tag==='form'&&n.props.class==='wallet-amount-card')[0].props.onSubmit(submitEvent());await tick();nodes(scope.root,n=>n.props?.['data-scan'])[0].props.onClick();await tick();route.params.storeId='switch-B';await tick();assert.equal(nodes(scope.root,n=>n.tag==='input'&&n.props.type==='number')[0].props.disabled,false);resolveWrite(response({status:'paid',bill:{id:'old'}}));await tick();assert.equal(nodes(scope.root,n=>n.tag==='input'&&n.props.type==='number')[0].props.disabled,false);scope.unmount()
})


test('renewal refreshes personal authorization from another device and late reads cannot undo acceptance',async()=>{
 let reads=0,resolveOld,accepted=false,raised=false
 const policy=()=>({configured:true,policyVersion:raised?4:3,limitCents:raised?2000:1000,consentedLimitCents:accepted?(raised?2000:1000):null,paymentEnabled:accepted,requiresConsent:!accepted,ruleVersion:1})
 globalThis.fetch=async(url,options)=>{
  if(url.endsWith('/personal-codes'))return response({type:'customer_personal',code:'WI.'+'D'.repeat(43),expiresAt:'2099-01-01T00:00:00Z'})
  if(options.method==='POST'){accepted=true;return response(policy())}
  if(++reads===1)return await new Promise(r=>resolveOld=r)
  return response(policy())
 }
 const scope=composable(()=>personalCodeModule.usePersonalCode(reactive({value:'A'}),reactive({value:true})));await tick();assert.equal(scope.state.consent.value.policyVersion,3)
 await scope.state.accept(true,1000);await tick();assert.equal(scope.state.consent.value.paymentEnabled,true);resolveOld(response({configured:true,policyVersion:2,limitCents:500,paymentEnabled:false}));await tick();assert.equal(scope.state.consent.value.policyVersion,3);assert.equal(scope.state.consent.value.paymentEnabled,true)
 raised=true;await scope.state.issue();await tick();assert.equal(scope.state.consent.value.requiresConsent,false);assert.equal(scope.state.consent.value.paymentEnabled,true);assert.equal(scope.state.consent.value.limitCents,2000);scope.unmount()
})


test('merchant home has one amount-first payment entry and scan-first top-up hint',async()=>{
 globalThis.fetch=async()=>response(staffContext)
 const scope=mount(StaffHome,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),{},permissions);await tick()
 const links=nodes(scope.root,n=>n.tag==='a'&&String(n.props.href).includes('/collect'))
 assert.equal(links.length,1);assert.equal(links[0].props.href.endsWith('/collect'),true)
 assert.match(allText(scope.root),/消费收款/);assert.match(allText(scope.root),/先输入消费金额和说明/);assert.match(allText(scope.root),/先扫描个人钱包码，再选择/)
 assert.doesNotMatch(allText(scope.root),/选择顾客收款|识别顾客付款码|员工确认扣款/);scope.unmount()
})


test('cashier login change keeps original pending key, blocks recovery and ignores late payment result',async()=>{
 let release,commands=0;globalThis.fetch=async(url)=>{if(url.endsWith('/personal-scan-charges')){commands++;return await new Promise(r=>release=r)};return response(staffContext)}
 const scope=mount(StaffCollect,'staff',reactive({params:{storeId:'actor-change'},query:{},hash:''}),{},permissions);await tick();enterNumber(scope.root,5);await tick();nodes(scope.root,n=>n.tag==='form'&&n.props.class==='wallet-amount-card')[0].props.onSubmit(submitEvent());await tick();nodes(scope.root,n=>n.props?.['data-scan'])[0].props.onClick();await tick();const saved=sessionStorage.getItem('rpb.wallet.personal-attempt.actor-change');assert.ok(saved)
 scope.auth.user={...scope.auth.user,accountId:'cashier-B'};await tick();assert.match(allText(scope.root),/原收银员账号/);assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n).includes('查询 / 重试原交易'))[0].props.disabled,true)
 release(response({status:'paid',bill:{id:'old'}}));await tick();assert.equal(sessionStorage.getItem('rpb.wallet.personal-attempt.actor-change'),saved);assert.match(allText(scope.root),/原收银员账号/);assert.equal(commands,1);scope.unmount()
})
