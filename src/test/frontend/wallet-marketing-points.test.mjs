import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, h, nextTick, reactive } from 'vue'
import { createPinia } from 'pinia'
import { routeLocationKey, routerKey } from 'vue-router'
import { createI18n } from 'vue-i18n'
let CashierTabs,CashierTools,TopupDetail,SelfRegistrationView,StaffCustomers, sharedCustomerModule, ReversalPage,ReversalAction,SettingsPanel,ChannelSettings,StaffSettings,BottomNav,CustomerOrder,MerchantCode,CustomerRedemption,NextCustomer,StaffHome,personalCodeModule,PersonalCode,RechargePage,CustomerHome,StaffMore,StaffCollect,StaffTopup,ExistingCharge,chargeComponentsModule,LedgerPanel, ReportPanel, EntryAllocations, MarketingPage, RefundComponent, StaffPoints, OfferComponent, CustomerTopups, messages, customerModule, staffModule, authModule, offersModule, pointsModule, apiModule
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
 let workspace;const navigation=[],replacements=[];
 const root={children:[]},i18n=createI18n({legacy:false,locale:'zh-CN',messages:{'zh-CN':{wallet:messages.walletZh}}}),pinia=createPinia()
 authModule.useAuthSessionStore(pinia).user={accountId:'cashier-A',roles:[],storeIds:['A'],permissions:permissions??['wallet.view','wallet.topup.create','wallet.points.view','wallet.points.grant','wallet.points.adjust','wallet.points.redeem','wallet.points.reverse']}
 const app=renderer.createApp({setup(){workspace=kind==='customer'?customerModule.provideCustomerWallet():staffModule.provideStaffWallet();return()=>h(component,props)}})
 app.use(pinia);app.use(i18n);app.provide(routeLocationKey,route);app.provide(routerKey,{push:async target=>{navigation.push(target)},replace:async target=>{replacements.push(target);if(target&&typeof target==='object'&&target.query)route.query=target.query}})
 app.component('RouterLink',{props:['to'],setup:(props,{slots})=>()=>h('a',{href:props.to},slots.default?.())});app.mount(root)
 return{root,navigation,replacements,get auth(){return authModule.useAuthSessionStore(pinia)},get workspace(){return workspace},unmount:()=>app.unmount()}
}
const response=body=>({ok:true,status:200,json:async()=>body,text:async()=>JSON.stringify(body)})
const storageHome={timezone:'Asia/Singapore',storeName:'QA Store',stores:[],newBusinessEnabled:true,permissions:[],rules:[],categories:[],items:[],applications:[],intents:[],events:[]}
const allText=root=>[root.text||'',...(root.children||[]).map(allText)].join(' ')

before(async()=>{
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document=Object.assign(new Document(),{activeElement:null,visibilityState:'visible',documentElement:{},addEventListener(){},removeEventListener(){}})
 globalThis.window={setInterval:()=>1,clearInterval:()=>{},addEventListener(){},removeEventListener(){}}
 const storage=new Map();globalThis.sessionStorage={getItem:k=>storage.get(k)??null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)}
 const {build,transform}=await import('esbuild'),{mkdir,writeFile}=await import('node:fs/promises'),{pathToFileURL}=await import('node:url')
 const entry=`export {default as CashierTabs} from './src/modules/wallet/components/WalletCashierTabs.vue';export {default as CashierTools} from './src/modules/wallet/components/WalletCashierTools.vue';export {default as TopupDetail} from './src/modules/wallet/pages/WalletStaffTopupDetail.vue';export {default as ReversalPage} from './src/modules/wallet/pages/WalletPointsReversal.vue';export {default as ReversalAction} from './src/modules/wallet/components/WalletPointsReversalAction.vue';export {default as SettingsPanel} from './src/modules/wallet/components/WalletSettingsPanel.vue';export {default as ChannelSettings} from './src/modules/wallet/components/WalletChannelSettings.vue';export {default as StaffSettings} from './src/modules/wallet/pages/WalletStaffSettings.vue';export {default as BottomNav} from './src/modules/wallet/components/WalletBottomNav.vue';export {default as CustomerOrder} from './src/modules/wallet/pages/WalletCustomerOrder.vue';export {default as MerchantCode} from './src/modules/wallet/pages/WalletMerchantCode.vue';export {default as CustomerRedemption} from './src/modules/wallet/pages/WalletCustomerRedemption.vue';export {default as NextCustomer} from './src/modules/wallet/components/WalletNextCustomer.vue';export {default as StaffHome} from './src/modules/wallet/pages/WalletStaffHome.vue';export * as personalCodeModule from './src/modules/wallet/usePersonalCode.ts';export {default as PersonalCode} from './src/modules/wallet/components/WalletPersonalCode.vue';export {default as RechargePage} from './src/modules/wallet/pages/WalletStaffRecharge.vue';export {default as CustomerHome} from './src/modules/wallet/pages/WalletCustomerHome.vue';export {default as StaffMore} from './src/modules/wallet/pages/WalletStaffMore.vue';export {default as StaffCollect} from './src/modules/wallet/pages/WalletStaffCollect.vue';export {default as StaffTopup} from './src/modules/wallet/pages/WalletStaffTopup.vue';export {default as ExistingCharge} from './src/modules/wallet/components/WalletExistingBillCharge.vue';export * as chargeComponentsModule from './src/modules/wallet/useChargeComponents.ts';export {default as LedgerPanel} from './src/modules/wallet/components/WalletLedgerPanel.vue';export {default as ReportPanel} from './src/modules/wallet/components/WalletReportPanel.vue';export {default as EntryAllocations} from './src/modules/wallet/components/WalletEntryAllocations.vue';export {default as MarketingPage} from './src/modules/wallet/pages/WalletMarketing.vue';export {default as RefundComponent} from './src/modules/wallet/components/WalletRefundComponents.vue';export {default as StaffPoints} from './src/modules/wallet/pages/WalletPoints.vue';export {default as OfferComponent} from './src/modules/wallet/components/WalletTopupOffers.vue';export {default as CustomerTopups} from './src/modules/wallet/pages/WalletCustomerTopup.vue';export * as messages from './src/modules/wallet/locales.ts';export * as customerModule from './src/modules/wallet/useCustomerWallet.ts';export * as staffModule from './src/modules/wallet/useStaffWallet.ts';export * as authModule from './src/stores/authSession.ts';export * as offersModule from './src/modules/wallet/useTopupOffers.ts';export * as pointsModule from './src/modules/wallet/usePoints.ts';`
 const result=await build({stdin:{contents:"export {default as SelfRegistrationView} from './src/modules/customers/CustomerSelfRegistrationForm.vue';export {default as StaffCustomers} from './src/modules/wallet/pages/WalletStaffCustomers.vue';export * as sharedCustomerModule from './src/modules/customers/useCustomerManagement.ts';"+entry,resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,platform:'node',format:'esm',external:['vue','vue-router','vue-i18n','pinia'],plugins:[{name:'vue-runtime-test',setup(build){build.onLoad({filter:/\.vue$/},async({path})=>{
  if(path.endsWith('WalletQrScanner.vue'))return {contents:`import {h} from 'vue';export default {props:['purpose'],emits:['code'],setup:(p,{emit})=>()=>h('button',{'data-scan':true,onClick:()=>emit('code',((p.purpose==='identity'||p.purpose==='personal')?'WI.':'WC.')+'A'.repeat(43))},'scan')}`,loader:'js'}
  if(path.endsWith('WalletQrDisplay.vue')||path.endsWith('DownloadableQrCode.vue'))return {contents:`import {h} from 'vue';export default {props:['value'],setup:p=>()=>h('figure',{'data-code':p.value})}`,loader:'js'}
  const {readFile}=await import('node:fs/promises');const source=await readFile(path,'utf8'),{descriptor}=parse(source,{filename:path}),script=compileScript(descriptor,{id:'test-'+path,inlineTemplate:true})
  return {contents:script.content,loader:'ts'}
 })}}]})
 const dir='target/wallet-marketing-component-tests';await mkdir(dir,{recursive:true});await writeFile(dir+'/entry.mjs',result.outputFiles[0].contents)
 const loaded=await import(pathToFileURL(process.cwd()+'/'+dir+'/entry.mjs').href+'?'+Date.now())
 TopupDetail=loaded.TopupDetail;
 ;({CashierTabs,CashierTools,SelfRegistrationView,StaffCustomers,sharedCustomerModule,ReversalPage,ReversalAction,SettingsPanel,ChannelSettings,StaffSettings,BottomNav,CustomerOrder,MerchantCode,CustomerRedemption,NextCustomer,StaffHome,personalCodeModule,PersonalCode,RechargePage,CustomerHome,StaffMore,StaffCollect,StaffTopup,ExistingCharge,chargeComponentsModule,LedgerPanel,ReportPanel,EntryAllocations,MarketingPage,RefundComponent,StaffPoints,OfferComponent,CustomerTopups,messages,customerModule,staffModule,authModule,offersModule,pointsModule}=loaded)
})
after(async()=>{globalThis.sessionStorage=originalStorage;globalThis.fetch=originalFetch;globalThis.window=originalWindow;globalThis.document=originalDocument;globalThis.Document=originalDocumentClass;globalThis.ShadowRoot=originalShadowRoot})
const offers={offers:[{id:'offer',version:2,principalCents:10000,bonusCents:1000,creditTotalCents:11000}],campaignId:'campaign',channels:['cash','paynow'],deductionPolicy:'proportional',selfServiceEnabled:true}

test('wallet logout revokes the customer session only and ignores a late wallet response',async()=>{
 const writes=[];let late,finishLogout,reads=0;const home={context:staffContext,accounts:[{id:'account-A',balanceCents:12000,reservedCents:0}],pendingBills:[]}
 globalThis.fetch=async(url,options={})=>{if(url.endsWith('/wine-storage/home'))return response(storageHome);if(url.endsWith('/customer-auth/logout')){writes.push({url,options});return new Promise(resolve=>finishLogout=resolve)}if(url.endsWith('/wallet/me')){if(++reads===1)return response(home);return new Promise(resolve=>late=resolve)}return response({balance:25})}
 const scope=mount(CustomerHome,'customer',reactive({params:{storeId:'A'},query:{},hash:''}));await tick();assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='退出登录').length,0)
 const refresh=scope.workspace.refresh();await tick();const logout=scope.workspace.logout();await tick();assert.equal(scope.workspace.loggingOut.value,true);await scope.workspace.logout();assert.equal(writes.length,1)
 finishLogout(response({success:true,principal:null}));await logout;late(response(home));await refresh;await tick();assert.equal(scope.workspace.home.value,null);assert.equal(scope.workspace.account.value,null);assert.equal(scope.workspace.needsLogin.value,true);assert.equal(writes[0].url,'/api/v1/public/customer-auth/logout');assert.equal(writes[0].options.credentials,'include');assert.equal(scope.auth.user.accountId,'cashier-A');scope.unmount()
})

test('failed wallet logout shows an error and can be retried without claiming success',async()=>{
 let attempts=0;const home={context:staffContext,accounts:[],pendingBills:[]};globalThis.fetch=async url=>{if(url.endsWith('/wine-storage/home'))return response(storageHome);if(url.endsWith('/customer-auth/logout')){if(++attempts===1)throw new Error('offline');return response({success:true,principal:null})}return response(url.endsWith('/wallet/me')?home:{balance:0})}
 const scope=mount(CustomerHome,'customer',reactive({params:{storeId:'A'},query:{},hash:''}));await tick();await scope.workspace.logout();assert.ok(scope.workspace.home.value);assert.equal(scope.workspace.needsLogin.value,false);assert.equal(scope.workspace.loggingOut.value,false);assert.match(scope.workspace.error.value,/退出失败/);await scope.workspace.logout();assert.equal(attempts,2);assert.equal(scope.workspace.needsLogin.value,true);assert.equal(scope.workspace.home.value,null);scope.unmount()
})
test('phone entry requires candidate confirmation before operations and never opens a funding account while searching',async()=>{
 const calls=[];globalThis.fetch=async url=>{calls.push(url);return response(url.includes('/customers?')?[{id:'phone-customer',displayName:'手机号顾客',phone:'+6581234567'}]:staffContext)}
 const route=reactive({name:'wallet-topup',params:{storeId:'A'},query:{entry:'phone'},hash:''}),scope=mount(RechargePage,'staff',route);await tick()
 assert.equal(nodes(scope.root,n=>n.props?.['data-scan']).length,0);const input=nodes(scope.root,n=>n.tag==='input'&&n.props.type==='tel')[0];assert.ok(input)
 input.props['onUpdate:modelValue']('81234567');await tick();await nodes(scope.root,n=>n.tag==='form'&&String(n.props.class).includes('wallet-phone-search'))[0].props.onSubmit(submitEvent());await tick()
 nodes(scope.root,n=>n.props?.['data-phone-candidate']==='phone-customer')[0].props.onClick();await tick();assert.equal(scope.workspace.customer.value,null);assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='钱包充值').length,0)
 await nodes(scope.root,n=>n.tag==='button'&&allText(n)==='确认选中顾客')[0].props.onClick();await tick();assert.equal(scope.workspace.customer.value.id,'phone-customer');assert.equal(scope.workspace.identifiedCustomerId.value,null)
 assert.ok(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='钱包充值').length);assert.ok(!calls.some(u=>u.endsWith('/accounts')||u.endsWith('/topup-offers')));scope.unmount()
})
test('cashier landing directly enters consumption without a second entry click',async()=>{
 globalThis.fetch=async()=>response(staffContext)
 const scope=mount(StaffHome,'staff',reactive({name:'wallet-workbench',params:{storeId:'A'},query:{},hash:''}),{},['wallet.view','wallet.charge.create','wallet.collection.confirm','wallet.topup.create','wallet.points.view']);await tick()
 assert.deepEqual(scope.replacements[0],{path:'/stores/A/wallet/collect'})
 assert.equal(nodes(scope.root,n=>n.tag==='a'&&String(n.props.href).endsWith('/collect')).length,0);scope.unmount()
})

test('phone points-only entry confirms explicitly, cannot use short searches, and clears on a new store',async()=>{
 const calls=[];globalThis.fetch=async url=>{calls.push(url);return response(url.includes('/customers?')?[{id:'phone-points',displayName:'积分顾客',phone:'+6581234567'}]:url.endsWith('/points/context')?{...staffContext,program:null}:{balance:10,version:1})}
 const route=reactive({name:'wallet-topup',params:{storeId:'A'},query:{entry:'phone'},hash:''}),scope=mount(RechargePage,'staff',route,{},['wallet.points.view','wallet.points.grant']);await tick()
 const input=nodes(scope.root,n=>n.tag==='input'&&n.props.type==='tel')[0],lookup=()=>nodes(scope.root,n=>n.tag==='form'&&String(n.props.class).includes('wallet-phone-search'))[0]
 input.props['onUpdate:modelValue']('123');await tick();await lookup().props.onSubmit(submitEvent());await tick();assert.ok(!calls.some(u=>u.includes('/customers?')))
 input.props['onUpdate:modelValue']('81234567');await tick();await lookup().props.onSubmit(submitEvent());await tick();assert.equal(scope.workspace.customer.value,null)
 nodes(scope.root,n=>n.props?.['data-phone-candidate']==='phone-points')[0].props.onClick();await tick();await nodes(scope.root,n=>n.tag==='button'&&allText(n)==='确认选中顾客')[0].props.onClick();await tick()
 await nodes(scope.root,n=>n.tag==='button'&&allText(n)==='积分增减 / 兑换')[0].props.onClick();await tick();assert.match(allText(scope.root),/积分余额: 10/);assert.equal(scope.workspace.phoneConfirmedCustomerId.value,'phone-points');assert.equal(scope.workspace.identifiedCustomerId.value,null)
 assert.ok(!calls.some(u=>u.endsWith('/wallet/context')||u.endsWith('/accounts')||u.endsWith('/topup-offers')))
 route.params.storeId='B';await tick();assert.equal(scope.workspace.customer.value,null);assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='积分增减 / 兑换').length,0);scope.unmount()
})
test('next customer preserves phone entry without carrying identity or a scan authorization',async()=>{
 globalThis.fetch=async()=>response(staffContext)
 const scope=mount(NextCustomer,'staff',reactive({params:{storeId:'A'},query:{entry:'phone'},hash:''}));await tick();scope.workspace.customer.value={id:'old'};scope.workspace.customerCode.value='not-an-authorization'
 nodes(scope.root,n=>n.tag==='button')[0].props.onClick();await tick();assert.deepEqual(scope.navigation[0].query,{entry:'phone'});assert.equal(scope.workspace.customer.value,null);assert.equal(scope.workspace.customerCode.value,'');scope.unmount()
})
test('hero recharge deep link prepares the account only after a valid identity scan',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push(url);return response(url.endsWith('/customer-codes/resolve')?{id:'scanned-c',displayName:'扫码顾客',phone:'+6581234567'}:url.endsWith('/customers/scanned-c/accounts')?[{...funded,customerId:'scanned-c'}]:url.endsWith('/topup-offers')?offers:staffContext)}
 const route=reactive({name:'wallet-topup',params:{storeId:'A'},query:{scan:'1',kind:'money'},hash:''}),scope=mount(RechargePage,'staff',route);await tick();assert.equal(scope.workspace.account.value,null)
 nodes(scope.root,n=>n.props?.['data-scan'])[0].props.onClick();await tick();assert.equal(scope.workspace.account.value.id,funded.id);assert.ok(calls.some(u=>u.endsWith('/customers/scanned-c/accounts')));scope.unmount()
})
test('customer recharge names the active campaign and explicitly explains ordinary 1:1 fallback',async()=>{
 for(const campaign of [true,false]){
  globalThis.fetch=async url=>response(url.endsWith('/me')?{accounts:[],pendingBills:[],context:{transactionsEnabled:true,paynowConfigured:true,store:{timezone:'Asia/Singapore'}}}:url.endsWith('/topup-offers')?{...offers,campaignId:campaign?'campaign':null,campaignName:campaign?'夏日充值礼':null,offers:campaign?offers.offers:[{...offers.offers[0],bonusCents:0,creditTotalCents:10000}]}:[])
  const scope=mount(CustomerTopups,'customer',reactive({params:{storeId:'A'},query:{},hash:''}));await tick()
  assert.match(allText(scope.root),campaign?/夏日充值礼/:/普通充值.*1:1/)
  assert.match(allText(scope.root),campaign?/活动充值.*每次只能选择一档/:/暂无生效的充值活动/)
  assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n).includes('获取报价')).length,0)
  scope.unmount()
 }
})
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
 await confirm.props.onClick();await tick()
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
async function merchantRecharge(post,context=staffContext){
 const calls=[];globalThis.fetch=async(url,options)=>{const body=options?.body&&JSON.parse(options.body);calls.push({url,body});return options?.method==='POST'?post(url,body):response(url.endsWith('/topup-offers')?offers:context)}
 const route=reactive({params:{storeId:'A'},query:{entry:'phone'},hash:''}),scope=mount(StaffTopup,'staff',route,{embedded:true});await tick();scope.workspace.account.value={id:'merchant-account'};await tick()
 return {...scope,route,calls,select:async(value)=>{const radio=nodes(scope.root,n=>n.tag==='input'&&n.props.value===value)[0];radio.listeners.change({target:radio});await tick()},submit:()=>nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit(submitEvent()),confirm:()=>nodes(scope.root,n=>n.tag==='button'&&allText(n).trim()==='确定充值')[0]}
}
test('merchant selects one tier and confirms directly with server quote and PayNow, without quote UI or collection',async()=>{
 const scope=await merchantRecharge(url=>response(url.endsWith('/topup-quotes')?rechargeQuote():{id:'pending-merchant',status:'pending_collection'}))
 assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n).includes('获取报价')).length,0);assert.ok(scope.confirm());assert.equal(scope.confirm().props.disabled,true)
 await scope.select('offer');await scope.select('paynow');assert.equal(scope.confirm().props.disabled,false);await scope.submit();await tick()
 assert.deepEqual(scope.calls.find(c=>c.url.endsWith('/topup-quotes')).body,{accountId:'merchant-account',offerId:'offer',offerVersion:2,channel:'paynow'})
 const body=scope.calls.find(c=>c.body&&c.url.endsWith('/topups')).body;assert.equal(body.quoteId,'automatic-quote');assert.equal(body.accountId,'merchant-account');assert.equal(body.amount,undefined)
 assert.equal(scope.navigation[0].path,'/stores/A/wallet/topups/pending-merchant');assert.deepEqual(scope.navigation[0].query,{entry:'phone',kind:'money'});assert.ok(!scope.calls.some(c=>c.url.includes('confirm-collection')));assert.doesNotMatch(allText(scope.root),/报价有效至/);scope.unmount()
})
test('merchant automatic quote failure does not create a topup',async()=>{
 const scope=await merchantRecharge(()=>({ok:false,status:409,json:async()=>({code:'WALLET_OFFER_CHANGED',message:'充值活动已调整'})}));await scope.select('offer');await scope.submit();await tick();assert.equal(scope.calls.filter(c=>c.body&&c.url.endsWith('/topups')).length,0);assert.match(allText(scope.root),/充值活动已调整/);scope.unmount()
})
test('merchant direct confirmation blocks duplicates and keeps quote and retry key after lost response',async()=>{
 let resolveQuote,attempts=0;const scope=await merchantRecharge(url=>url.endsWith('/topup-quotes')?new Promise(resolve=>{resolveQuote=resolve}):(++attempts===1?Promise.reject(Error('merchant network lost')):response({id:'recovered'})))
 await scope.select('offer');const first=scope.submit();await scope.submit();await tick();assert.equal(scope.calls.filter(c=>c.url.endsWith('/topup-quotes')).length,1);resolveQuote(response(rechargeQuote()));await first;await tick();assert.match(allText(scope.root),/merchant network lost/)
 await scope.submit();await tick();const commands=scope.calls.filter(c=>c.body&&c.url.endsWith('/topups'));assert.equal(commands.length,2);assert.deepEqual(commands[0].body,commands[1].body);assert.equal(scope.calls.filter(c=>c.url.endsWith('/topup-quotes')).length,1);scope.unmount()
})
test('merchant changing account during automatic quote cannot create for the previous customer',async()=>{
 let resolveQuote;const scope=await merchantRecharge(()=>new Promise(resolve=>{resolveQuote=resolve}));await scope.select('offer');const pending=scope.submit();await tick();scope.workspace.account.value={id:'different-account'};await tick();resolveQuote(response(rechargeQuote()));await pending;await tick();assert.equal(scope.calls.filter(c=>c.body&&c.url.endsWith('/topups')).length,0);scope.unmount()
})
test('merchant cannot submit PayNow when it is not configured',async()=>{
 const scope=await merchantRecharge(()=>{throw Error('must not post')},{...staffContext,paynowConfigured:false});await scope.select('offer');await scope.select('paynow');assert.equal(scope.confirm().props.disabled,true);await scope.submit();assert.equal(scope.calls.filter(c=>c.body).length,0);scope.unmount()
})
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
async function enterNumber(root,value){const input=nodes(root,n=>n.tag==='input'&&n.props.type==='number')[0];if(input){input.value=String(value);input.listeners.input({target:input});return input}const key=value=>nodes(root,n=>n.props?.['data-amount-key']===value)[0];key('clear').props.onClick();await tick();for(const digit of String(value)){key(digit).props.onClick();await tick()}return key('1')}
test('wallet collection uses a permanent keypad, decimals and presets before scanning',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,body:options?.body&&JSON.parse(options.body)});return response(staffContext)}
 const scope=mount(StaffCollect,'staff',reactive({params:{storeId:'keypad-store'},query:{},hash:''}),{},permissions);await tick()
 const key=value=>nodes(scope.root,n=>n.props?.['data-amount-key']===value)[0],display=()=>allText(nodes(scope.root,n=>n.tag==='output')[0])
 assert.equal(nodes(scope.root,n=>n.tag==='input'&&n.props.type==='number').length,0);assert.match(display(),/0\.00/)
 assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n).includes('扫描个人钱包码并收款'))[0].props.disabled,true)
 for(const value of ['1','2','.','3','4','5']){key(value).props.onClick();await tick()}assert.match(display(),/12\.34/)
 key('backspace').props.onClick();await tick();assert.match(display(),/12\.30/);key('clear').props.onClick();await tick();assert.match(display(),/0\.00/)
 key('5').props.onClick();await tick();key('00').props.onClick();await tick();assert.match(display(),/500\.00/)
 nodes(scope.root,n=>n.props?.['data-amount-preset']==='20')[0].props.onClick();await tick();assert.match(display(),/20\.00/)
 await enterNumber(scope.root,'999999999999');assert.match(display(),/99999999999\.00/)
 assert.ok(!calls.some(c=>c.body));nodes(scope.root,n=>n.tag==='form'&&n.props.class==='wallet-amount-card')[0].props.onSubmit(submitEvent());await tick();assert.equal(key('1').props.disabled,true);scope.unmount()
})
test('wallet keypad edits the existing per-store presets inline without starting payment',async()=>{
 const storage=globalThis.window.localStorage,map=new Map();globalThis.window.localStorage={getItem:k=>map.get(k)??null,setItem:(k,v)=>map.set(k,v)}
 globalThis.fetch=async()=>response(staffContext)
 let scope
 try{
  scope=mount(StaffCollect,'staff',reactive({params:{storeId:'preset-keypad'},query:{},hash:''}),{},permissions);await tick()
  nodes(scope.root,n=>n.tag==='button'&&allText(n)==='编辑')[0].props.onClick();await tick();const input=nodes(scope.root,n=>n.tag==='textarea')[0];input.props['onUpdate:modelValue']('15, 35.50');await tick();nodes(scope.root,n=>n.tag==='button'&&allText(n)==='保存')[0].props.onClick();await tick()
  assert.ok(nodes(scope.root,n=>n.props?.['data-amount-preset']==='35.5').length||nodes(scope.root,n=>n.props?.['data-amount-preset']==='35.50').length);assert.ok(map.size>0);scope.unmount();scope=null
  scope=mount(StaffCollect,'staff',reactive({params:{storeId:'preset-keypad'},query:{},hash:''}),{},permissions);await tick();assert.ok(nodes(scope.root,n=>n.props?.['data-amount-preset']==='15').length)
 }finally{scope?.unmount();globalThis.window.localStorage=storage}
})
const submitEvent=()=>({preventDefault(){}})
test('staff payment requires a fresh scan after amount, ignores prior customer and queries an uncertain result',async()=>{
 const calls=[];let resolveWrite
 globalThis.fetch=async(url,options)=>{const body=options?.body&&JSON.parse(options.body);calls.push({url,body});if(url.endsWith('/personal-scan-charges'))return new Promise((resolve,reject)=>{resolveWrite=()=>reject(Error('response lost'))});if(url.includes('/personal-scan-charges/result'))return response({status:'paid',bill:{id:'paid'}});return response(staffContext)}
 const scope=mount(StaffCollect,'staff',reactive({params:{storeId:'fresh-scan'},query:{},hash:''}),{},permissions);await tick();scope.workspace.account.value={...funded};scope.workspace.customerCode.value=debitCode;await enterNumber(scope.root,55);await tick()
 assert.equal(calls.filter(c=>c.body).length,0)
 const form=nodes(scope.root,n=>n.tag==='form'&&n.props.class==='wallet-amount-card')[0];form.props.onSubmit(submitEvent());await tick();const scanner=nodes(scope.root,n=>n.props?.['data-scan'])[0];scanner.props.onClick();scanner.props.onClick();await tick();assert.equal(calls.filter(c=>c.url.endsWith('/personal-scan-charges')).length,1)
 const body=calls.find(c=>c.url.endsWith('/personal-scan-charges')).body;assert.equal(body.customerCode,'WI.'+'A'.repeat(43));assert.equal(body.accountId,undefined);assert.equal(body.customerId,undefined);assert.equal(body.amount,'55')
 resolveWrite();await tick();assert.match(allText(scope.root),/结果尚未确认/);assert.equal(nodes(scope.root,n=>n.props?.['data-amount-key']==='1')[0].props.disabled,true)
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
 const scope=mount(StaffTopup,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),{},permissions);await tick();scope.workspace.account.value={...funded};await tick();await enterNumber(scope.root,20);await tick();await nodes(scope.root,n=>n.tag==='form'&&n.props.class==='wallet-amount-card')[0].props.onSubmit(submitEvent());await new Promise(r=>setTimeout(r,10));await tick();const body=calls.find(c=>c.url.endsWith('/topups')).body;assert.equal(body.amount,'20');assert.equal(typeof body.amount,'string');scope.unmount()
})
test('disabled points preserve dedicated original-entry reversal and never reintroduce the operation-page form',async()=>{
 const calls=[];globalThis.fetch=async url=>{calls.push(url);return response(url.endsWith('/context')?{...staffContext,transactionsEnabled:false,program:null}:url.includes('/transactions/')?{transaction:originalPointsRow,related:[],events:[]}:{accountId:'p',balance:100,version:2})}
 const scope=mount(ReversalPage,'staff',reactive({name:'wallet-points-reverse',params:{storeId:'A',entryId:'entry-original'},query:{},hash:''}),{},['wallet.view','wallet.points.view','wallet.points.reverse']);await tick();assert.equal(nodes(scope.root,n=>n.tag==='form').length,1);assert.equal(nodes(scope.root,n=>n.tag==='input'&&n.props.type!=='checkbox').length,1);assert.ok(!calls.some(u=>u.endsWith('/entries')));scope.unmount()
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
 globalThis.fetch=async url=>response(url.endsWith('/wine-storage/home')?storageHome:url.endsWith('/me')?{accounts:[],pendingBills:[],context:{transactionsEnabled:true,store:{timezone:'Asia/Singapore'}}}:url.includes('/entries')?entries:{balance:960,version:3})
 const scope=mount(CustomerHome,'customer',reactive({params:{storeId:'A'},query:{},hash:''}));await tick();assert.match(allText(scope.root),/积分余额/);assert.match(allText(scope.root),/960/);assert.equal(nodes(scope.root,n=>n.tag==='a'&&n.props.href==='/wallet/A/wine-storage').length,1);
 for(const [section,category] of [['wallet-balance-hero','money'],['wallet-points-summary','points'],['wallet-shortcuts','all']]){
  const card=nodes(scope.root,n=>String(n.props?.class??'').split(' ').includes(section))[0];assert.ok(card,section)
  assert.equal(nodes(card,n=>n.tag==='a'&&n.props.href===`/wallet/A/transactions?category=${category}`).length,1)
 }
 const nav=mount(BottomNav,'customer',reactive({params:{storeId:'A'},path:'/wallet/A',query:{},hash:''}),{store:'A',customer:true});await tick();assert.ok(nodes(nav.root,n=>n.tag==='a'&&n.props.href==='/wallet/A/transactions?category=all').length);nav.unmount();scope.unmount()
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

test('recharge scans consecutive customers and clears the previous wallet and operation',async()=>{
 let count=0;globalThis.fetch=async url=>response(url.endsWith('/customer-codes/resolve')?{id:'customer-'+(++count),displayName:'顾客'+count}:staffContext)
 const route=reactive({name:'wallet-topup',params:{storeId:'A'},query:{scan:'1'},hash:''}),scope=mount(RechargePage,'staff',route);await tick()
 nodes(scope.root,n=>n.props?.['data-scan'])[0].props.onClick();await tick();assert.equal(scope.workspace.customer.value.id,'customer-1')
 route.query={kind:'money'};scope.workspace.account.value={id:'old-account'};await tick()
 nodes(scope.root,n=>n.tag==='button'&&allText(n)==='扫下一位顾客')[0].props.onClick();await tick()
 assert.equal(scope.workspace.customer.value,null);assert.equal(scope.workspace.account.value,null);assert.equal(route.query.kind,'money')
 nodes(scope.root,n=>n.props?.['data-scan'])[0].props.onClick();await tick();assert.equal(scope.workspace.customer.value.id,'customer-2');assert.equal(scope.workspace.account.value,null);scope.unmount()
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
 const scope=mount(PersonalCode,'customer',reactive({params:{storeId:'A'},query:{},hash:''}));await tick();assert.match(allText(scope.root),/尚未设置/);assert.match(allText(scope.root),/支付上限设置.*尚未启用/);assert.ok(allText(scope.root).indexOf('支付上限设置')<allText(scope.root).indexOf('120 秒'));assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='同意并启用')[0].props.disabled,true);assert.equal(nodes(scope.root,n=>n.tag==='figure').length,1);assert.ok(!calls.some(c=>c.body?.accepted));scope.unmount()
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
 const route=reactive({params:{storeId:'switch-A'},query:{},hash:''}),scope=mount(StaffCollect,'staff',route,{},permissions);await tick();await enterNumber(scope.root,5);await tick();nodes(scope.root,n=>n.tag==='form'&&n.props.class==='wallet-amount-card')[0].props.onSubmit(submitEvent());await tick();nodes(scope.root,n=>n.props?.['data-scan'])[0].props.onClick();await tick();route.params.storeId='switch-B';await tick();assert.equal(nodes(scope.root,n=>n.props?.['data-amount-key']==='1')[0].props.disabled,false);resolveWrite(response({status:'paid',bill:{id:'old'}}));await tick();assert.equal(nodes(scope.root,n=>n.props?.['data-amount-key']==='1')[0].props.disabled,false);scope.unmount()
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


test('cashier landing falls back to topup or points according to real permissions',async()=>{
 globalThis.fetch=async()=>response(staffContext)
 for(const [rights,kind] of [[['wallet.view','wallet.topup.create'],'money'],[['wallet.points.view'],'points']]){
  const scope=mount(StaffHome,'staff',reactive({name:'wallet-workbench',params:{storeId:'A'},query:{},hash:''}),{},rights);await tick()
  assert.deepEqual(scope.replacements[0],{path:'/stores/A/wallet/topup',query:{kind,scan:'1'}});scope.unmount()
 }
})

test('cashier login change keeps original pending key, blocks recovery and ignores late payment result',async()=>{
 let release,commands=0;globalThis.fetch=async(url)=>{if(url.endsWith('/personal-scan-charges')){commands++;return await new Promise(r=>release=r)};return response(staffContext)}
 const scope=mount(StaffCollect,'staff',reactive({params:{storeId:'actor-change'},query:{},hash:''}),{},permissions);await tick();await enterNumber(scope.root,5);await tick();nodes(scope.root,n=>n.tag==='form'&&n.props.class==='wallet-amount-card')[0].props.onSubmit(submitEvent());await tick();nodes(scope.root,n=>n.props?.['data-scan'])[0].props.onClick();await tick();const saved=sessionStorage.getItem('rpb.wallet.personal-attempt.actor-change');assert.ok(saved)
 scope.auth.user={...scope.auth.user,accountId:'cashier-B'};await tick();assert.match(allText(scope.root),/原收银员账号/);assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n).includes('查询 / 重试原交易'))[0].props.disabled,true)
 release(response({status:'paid',bill:{id:'old'}}));await tick();assert.equal(sessionStorage.getItem('rpb.wallet.personal-attempt.actor-change'),saved);assert.match(allText(scope.root),/原收银员账号/);assert.equal(commands,1);scope.unmount()
})


test('settled result next customer clears identity and navigates to a fresh scan; busy cannot switch',async()=>{
 globalThis.fetch=async()=>response(staffContext)
 for(const collect of [false,true]){
  const props=reactive({collect,disabled:true}),scope=mount(NextCustomer,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),props);await tick()
  scope.workspace.customer.value={id:'previous'};scope.workspace.identifiedCustomerId.value='previous';scope.workspace.account.value={id:'previous-wallet'}
  const button=nodes(scope.root,n=>n.tag==='button')[0];button.props.onClick();assert.equal(scope.workspace.customer.value.id,'previous');assert.equal(scope.navigation.length,0)
  props.disabled=false;await tick();button.props.onClick();await tick();assert.equal(scope.workspace.customer.value,null);assert.equal(scope.workspace.account.value,null);assert.equal(scope.workspace.identifiedCustomerId.value,null)
  assert.equal(scope.navigation[0].path,collect?'/stores/A/wallet/collect':'/stores/A/wallet/topup');assert.deepEqual(scope.navigation[0].query,collect?{}:{scan:'1',kind:'money'});scope.unmount()
 }
})


test('merchant enters amount directly and creates an unbound order without customer lookup',async()=>{
 const writes=[],reads=[];globalThis.window.location={origin:'https://test.example'};globalThis.fetch=async(url,options={})=>{reads.push(url);if(options.method==='POST')writes.push([url,options.body?JSON.parse(options.body):null]);return response(url.endsWith('/merchant-orders')?{id:'o',kind:'money',amountCents:1200,status:'pending'}:url.endsWith('/code')?{code:'WM.'+'A'.repeat(43)}:staffContext)}
 const scope=mount(MerchantCode,'staff',reactive({name:'wallet-merchant-code',params:{storeId:'A'},query:{kind:'money'},hash:''}),{},['wallet.view','wallet.charge.create']);await tick();assert.equal(nodes(scope.root,n=>n.tag==='input'&&n.props.type==='tel').length,0)
 for(const k of ['1','2']){nodes(scope.root,n=>n.props?.['data-amount-key']===k)[0].props.onClick();await tick()}
 await nodes(scope.root,n=>n.tag==='button'&&allText(n)==='生成二维码')[0].props.onClick();await tick();const body=writes.find(([u])=>u.endsWith('/merchant-orders'))[1];assert.equal(body.amount,'12');assert.equal(body.kind,'money');assert.ok(!('customerId' in body));assert.ok(!('accountId' in body));assert.ok(nodes(scope.root,n=>n.props?.['data-code']?.includes('#order=WM.')).length);assert.ok(!reads.some(u=>u.includes('/customers')||u.includes('/accounts')));scope.unmount()
})
test('customer redemption page reads first and only confirmation posts the displayed account version',async()=>{
 const writes=[],record={id:'r',quantity:20,description:'兑换咖啡',status:'pending',balance:100,expectedVersion:3,entryId:null};globalThis.fetch=async(url,options={})=>{if(options.body)writes.push([url,JSON.parse(options.body)]);return response(url.endsWith('/me')?{context:staffContext,accounts:[],pendingBills:[]}:url.endsWith('/confirm')?{...record,status:'completed',balance:80,entryId:'entry-r'}:record)}
 const scope=mount(CustomerRedemption,'customer',reactive({params:{storeId:'A',redemptionId:'r'},query:{},hash:''}));await tick();assert.equal(writes.length,0);assert.match(allText(scope.root),/兑换咖啡/)
 await nodes(scope.root,n=>n.tag==='button'&&allText(n)==='确认扣减积分并兑换')[0].props.onClick();await tick();assert.equal(writes.length,1);assert.equal(writes[0][1].expectedVersion,3);assert.match(allText(scope.root),/已完成/);assert.ok(nodes(scope.root,n=>n.tag==='a'&&String(n.props.href).includes('transactions/points/entry-r')).length);scope.unmount()
})

test('unbound points code has quantity only, customer scan only previews and confirmation sends own version',async()=>{
 const writes=[],order={id:'o',kind:'points',quantity:5,description:'咖啡',status:'pending',balance:17,expectedVersion:2,sourceId:null};globalThis.window.location={origin:'https://test.example'};globalThis.fetch=async(url,options={})=>{if(options.method==='POST')writes.push([url,options.body?JSON.parse(options.body):null]);return response(url.endsWith('/me')?{context:staffContext,accounts:[],pendingBills:[]}:url.endsWith('/code')?{code:'WM.'+'A'.repeat(43)}:url.endsWith('/confirm')?{...order,status:'completed',balance:12,sourceId:'entry',sourceType:'points'}:{...order,...(url.endsWith('/points/context')?{store:staffContext.store,transactionsEnabled:true}: {})})}
 const scope=mount(MerchantCode,'staff',reactive({name:'wallet-merchant-code',params:{storeId:'A'},query:{kind:'points'},hash:''}),{},['wallet.points.view','wallet.points.redeem']);await tick();assert.equal(nodes(scope.root,n=>n.tag==='input'&&n.props.type==='tel').length,0);nodes(scope.root,n=>n.tag==='input'&&n.props.inputmode==='numeric')[0].props['onUpdate:modelValue']('5');nodes(scope.root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('咖啡');await tick();await nodes(scope.root,n=>n.tag==='button'&&allText(n)==='生成二维码')[0].props.onClick();await tick();assert.equal(writes[0][1].quantity,5);assert.ok(!('customerId' in writes[0][1]));scope.unmount()
 const customer=mount(CustomerOrder,'customer',reactive({params:{storeId:'A',orderId:'o'},query:{},hash:''}));await tick();await customer.workspace.scan('WM.'+'A'.repeat(43));await tick();assert.ok(customer.navigation.includes('/wallet/A/merchant-orders/o'));assert.ok(!writes.some(([u])=>u.endsWith('/confirm')));await nodes(customer.root,n=>n.tag==='button'&&allText(n)==='确认扣减积分并兑换')[0].props.onClick();await tick();assert.equal(writes.find(([u])=>u.endsWith('/confirm'))[1].expectedVersion,2);assert.match(allText(customer.root),/已完成/);customer.unmount()
})

test('customer fixed-amount order blocks insufficient funds then confirms exactly once into original bill',async()=>{
 const writes=[];let balance=1000;const pending=()=>({id:'o',kind:'money',amountCents:1200,description:'消费',status:'pending',balance,expectedVersion:3,sourceId:null});globalThis.fetch=async(url,options={})=>{if(options.method==='POST')writes.push([url,JSON.parse(options.body)]);return response(url.endsWith('/me')?{context:staffContext,accounts:[],pendingBills:[]}:url.endsWith('/confirm')?{...pending(),status:'completed',balance:3800,sourceType:'bill',sourceId:'original-bill'}:pending())}
 const route=reactive({params:{storeId:'A',orderId:'o'},query:{},hash:''});let scope=mount(CustomerOrder,'customer',route);await tick();assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='确认钱包付款')[0].props.disabled,true);assert.equal(writes.length,0);scope.unmount();balance=5000;scope=mount(CustomerOrder,'customer',route);await tick();await nodes(scope.root,n=>n.tag==='button'&&allText(n)==='确认钱包付款')[0].props.onClick();await tick();assert.equal(writes.length,1);assert.deepEqual(Object.keys(writes[0][1]).sort(),['expectedVersion','idempotencyKey']);assert.ok(nodes(scope.root,n=>n.tag==='a'&&String(n.props.href).includes('transactions/bill/original-bill')).length);scope.unmount()
})
test('merchant cancel and next transaction reset amount without returning to a phone lookup',async()=>{
 globalThis.fetch=async(url,options={})=>response(url.endsWith('/merchant-orders')?{id:'o',kind:'money',amountCents:1000,status:'pending'}:url.endsWith('/code')?{code:'WM.'+'A'.repeat(43)}:url.endsWith('/cancel')?{id:'o',kind:'money',status:'cancelled',amountCents:1000}:staffContext)
 const scope=mount(MerchantCode,'staff',reactive({name:'wallet-merchant-code',params:{storeId:'A'},query:{kind:'money'},hash:''}),{},['wallet.view','wallet.charge.create']);await tick();nodes(scope.root,n=>n.props?.['data-amount-key']==='1')[0].props.onClick();await tick();await nodes(scope.root,n=>n.tag==='button'&&allText(n)==='生成二维码')[0].props.onClick();await tick();await nodes(scope.root,n=>n.tag==='button'&&allText(n)==='取消本次办理')[0].props.onClick();await tick();nodes(scope.root,n=>n.tag==='button'&&allText(n)==='下一笔')[0].props.onClick();await tick();assert.equal(nodes(scope.root,n=>n.tag==='input'&&n.props.type==='tel').length,0);assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='生成二维码')[0].props.disabled,true);assert.equal(nodes(scope.root,n=>n.tag==='textarea')[0].value,'');scope.unmount()
})

test('merchant QR and customer confirmation remain in the cashier or wallet navigation section',async()=>{
 globalThis.fetch=async url=>response(url.endsWith('/me')?{context:staffContext,accounts:[],pendingBills:[]}:staffContext)
 for(const [kind,path,props] of [['staff','/stores/A/wallet/merchant-code',{store:'A'}],['customer','/wallet/A/merchant-orders/o',{store:'A',customer:true}]]){const scope=mount(BottomNav,kind,reactive({path,params:{storeId:'A'},query:{},hash:''}),props);await tick();const active=nodes(scope.root,n=>n.tag==='a'&&String(n.props.class).includes('is-current'));assert.equal(active.length,1);assert.equal(active[0].props.href,kind==='staff'?'/stores/A/wallet':'/wallet/A');scope.unmount()}
})


const settingsContext={...staffContext,store:{...staffContext.store,operatingEntityId:'issuer',operatingEntityName:'测试经营主体'},program:{...staffContext.program,version:3,channels:['cash','paynow']}}
test('settings directory shows status and separate destinations without mounting forms or PayNow reads',async()=>{
 const calls=[];globalThis.fetch=async url=>{calls.push(url);return response(settingsContext)}
 const scope=mount(SettingsPanel,'staff',reactive({name:'wallet-settings',params:{storeId:'A'},query:{},hash:''}),{store:'A',context:settingsContext,allowed:true});await tick()
 for(const href of ['/stores/A/wallet/settings/channels','/stores/A/wallet/settings/paynow','/stores/A/admin/profile'])assert.equal(nodes(scope.root,n=>n.tag==='a'&&n.props.href===href).length,1)
 assert.equal(nodes(scope.root,n=>n.tag==='form').length,0);assert.equal(calls.filter(u=>u.endsWith('/collection-profile')).length,0);assert.match(allText(scope.root),/现金/);assert.match(allText(scope.root),/测试经营主体/);scope.unmount()
})
test('settings deep links deny unauthorized staff without loading the PayNow profile or submitting forms',async()=>{
 const calls=[];globalThis.fetch=async url=>{calls.push(url);return response(settingsContext)}
 for(const section of ['overview','channels','paynow']){const scope=mount(StaffSettings,'staff',reactive({name:'wallet-settings',params:{storeId:'A'},query:{},hash:''}),{section},['wallet.view']);await tick();assert.equal(nodes(scope.root,n=>n.tag==='form').length,0);assert.equal(nodes(scope.root,n=>n.tag==='a'&&String(n.props.href).includes('/settings/')).length,0);scope.unmount()}
 assert.equal(calls.filter(u=>u.endsWith('/collection-profile')).length,0)
})
test('collection subjects save the current issuer version with editable names and order',async()=>{
 const writes=[];globalThis.fetch=async(url,options={})=>{if(options.method==='PUT'){const body=JSON.parse(options.body);writes.push({url,body});return response({version:4,methods:body.methods,affectedStoreIds:['A']})}return response(settingsContext)}
 const scope=mount(ChannelSettings,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),{store:'A',context:settingsContext});await tick()
 assert.equal(nodes(scope.root,n=>n.tag==='form').length,1);assert.equal(nodes(scope.root,n=>n.tag==='select').length,2)
 nodes(scope.root,n=>n.tag==='input')[0].props['onUpdate:modelValue']('柜台现金');await tick()
 nodes(scope.root,n=>n.tag==='button'&&allText(n)==='下移')[0].props.onClick();await tick()
 await nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit(submitEvent());await tick();assert.equal(writes.length,1);assert.equal(writes[0].url,'/api/v1/stores/A/wallet/collection-methods');assert.equal(writes[0].body.version,3);assert.deepEqual(writes[0].body.methods.map(m=>m.code),['paynow','cash']);assert.equal(writes[0].body.methods[1].name,'柜台现金');scope.unmount()
})
test('collection subjects can be added and removed but duplicate names cannot be saved',async()=>{
 const scope=mount(ChannelSettings,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),{store:'A',context:settingsContext});await tick()
 nodes(scope.root,n=>n.tag==='button'&&allText(n)==='新增收款科目')[0].props.onClick();await tick();assert.equal(nodes(scope.root,n=>n.tag==='select').length,3)
 let inputs=nodes(scope.root,n=>n.tag==='input');inputs[2].props['onUpdate:modelValue']('现金');await tick();assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='保存')[0].props.disabled,true)
 inputs[2].props['onUpdate:modelValue']('银行转账');await tick();assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='保存')[0].props.disabled,false)
 nodes(scope.root,n=>n.tag==='button'&&allText(n)==='移除')[2].props.onClick();await tick();assert.equal(nodes(scope.root,n=>n.tag==='select').length,2);scope.unmount()
})

test('an unconfigured store attaches the existing issuer before editing without overwriting shared subjects',async()=>{
 const calls=[],code='other:00000000-0000-0000-0000-000000000001';globalThis.fetch=async(url,options={})=>{if(options.method==='PUT'){calls.push({url,body:JSON.parse(options.body)});return response({...settingsContext.program,version:9,channels:[code],channelLabels:{[code]:'发行主体既有转账'}})}return response(settingsContext)}
 const scope=mount(ChannelSettings,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),{store:'A',context:{...settingsContext,program:null}});await tick();assert.equal(nodes(scope.root,n=>n.tag==='input').length,0);await nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit(submitEvent());await tick();assert.equal(calls.length,1);assert.equal(calls[0].url,'/api/v1/stores/A/wallet/program');assert.equal(nodes(scope.root,n=>n.tag==='input')[0].value,'发行主体既有转账');scope.unmount()
})

test('offer channel labels are server data and custom PayNow subjects still need a configured account',async()=>{
 const code='paynow:00000000-0000-0000-0000-000000000001';const scope=mount(OfferComponent,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),{offers:{...offers,channels:[code],channelLabels:{[code]:'公司转账'}},selected:'offer',channel:code,quote:null,expired:false,busy:false,timezone:'Asia/Singapore',paynowConfigured:false,confirmOnly:true});await tick();assert.match(allText(scope.root),/公司转账/);assert.equal(nodes(scope.root,n=>n.tag==='input'&&n.props.value===code)[0].props.disabled,true);scope.unmount()
})

test('PayNow deep link mounts only its existing account form and preserves shared-account verification',async()=>{
 const writes=[];globalThis.fetch=async(url,options={})=>{if(options.method==='PUT'){writes.push({url,body:JSON.parse(options.body)});return response({version:2,status:'active',paynowType:'uen',paynowUen:'UEN1',merchantName:'店名'})}return response(url.endsWith('/collection-profile')?{version:1,inherited:false,status:'active',paynowType:'uen',paynowUen:'UEN1',merchantName:'店名'}:settingsContext)}
 const scope=mount(StaffSettings,'staff',reactive({name:'wallet-settings-paynow',params:{storeId:'A'},query:{},hash:''}),{section:'paynow'},['wallet.view','wallet.settings.manage']);await tick();assert.equal(nodes(scope.root,n=>n.tag==='form').length,1);assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='保存')[0].props.disabled,true)
 nodes(scope.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0].props['onUpdate:modelValue'](true);await tick();assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='保存')[0].props.disabled,false)
 await nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit(submitEvent());await tick();assert.equal(writes.length,1);assert.equal(writes[0].url,'/api/v1/stores/A/wallet/collection-profile');assert.equal(writes[0].body.issuerVerified,true);assert.equal('inherited' in writes[0].body,false);assert.equal('channels' in writes[0].body,false);scope.unmount()
})


test('channel form prevents empty or unavailable program writes, and displays save conflicts',async()=>{
 let writes=0;globalThis.fetch=async(url,options={})=>{if(options.method==='PUT'){writes++;return {ok:false,status:409,json:async()=>({code:'WALLET_CONFLICT',message:'配置已更新，请刷新后重试'})}}return response(settingsContext)}
 for(const context of [{...settingsContext,program:{...settingsContext.program,channels:[]}},{...settingsContext,denialReason:'STORE_OFF'},{...settingsContext,store:{...settingsContext.store,operatingEntityId:null}}]){const scope=mount(ChannelSettings,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),{store:'A',context});await tick();assert.equal(nodes(scope.root,n=>n.tag==='button'&&['保存','开通配置'].includes(allText(n)))[0].props.disabled,true);await nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit(submitEvent());await tick();scope.unmount()}
 assert.equal(writes,0)
 const scope=mount(ChannelSettings,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),{store:'A',context:settingsContext});await tick();await nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit(submitEvent());await tick();assert.equal(writes,1);assert.match(allText(scope.root),/配置已更新/);scope.unmount()
})


const originalPointsRow={id:'entry-original',sourceType:'points',type:'points_grant',status:'completed',customerId:'customer-original',quantity:3,direction:'in',storeName:'测试店',occurredAt:'2026-10-24T00:00:00Z',timeZone:'Asia/Singapore'}
test('points operations never render history or reversal, review does not post and confirmation posts once',async()=>{
 const writes=[];globalThis.fetch=async(url,options={})=>{if(options.method==='POST'){writes.push({url,body:JSON.parse(options.body)});return response({account:{balance:13,version:4},entry:{id:'new-entry',kind:'manual_grant',quantity:3,beforeBalance:10,afterBalance:13}})}return response(url.endsWith('/context')?staffContext:{accountId:'p',balance:10,version:3})}
 const scope=mount(StaffPoints,'staff',reactive({params:{storeId:'A'},query:{},hash:''}));await tick();await scope.workspace.choose({id:'customer-original',displayName:'当前顾客',phone:'+6581234567',customerCode:'C'},false);await tick()
 assert.equal(nodes(scope.root,n=>n.tag==='form').length,1);assert.doesNotMatch(allText(scope.root),/暂无记录|交易编号|冲正/)
 nodes(scope.root,n=>n.tag==='input'&&n.props.type==='number')[0].props['onUpdate:modelValue']('3');nodes(scope.root,n=>n.tag==='label'&&allText(n).trim().startsWith('原因'))[0].children.find(n=>n.tag==='input').props['onUpdate:modelValue']('本次赠分');await tick()
 await nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit(submitEvent());await tick();assert.equal(writes.length,0);assert.match(allText(scope.root),/当前顾客/);assert.equal(nodes(scope.root,n=>n.tag==='form').length,0)
 const confirm=nodes(scope.root,n=>n.tag==='button'&&allText(n)==='确认增加积分')[0];await confirm.props.onClick();await tick();await confirm.props.onClick();assert.equal(writes.length,1);assert.equal(writes[0].url,'/api/v1/stores/A/wallet/customers/customer-original/points/grants');assert.equal(writes[0].body.reason,'本次赠分');assert.equal(nodes(scope.root,n=>n.tag==='form').length,0);assert.match(allText(scope.root),/办理成功/);assert.ok(nodes(scope.root,n=>n.tag==='a'&&n.props.href?.path==='/stores/A/wallet/transactions/points/new-entry'&&n.props.href.query.returnTo==='/stores/A/wallet/transactions?category=points&customerQuery=%2B6581234567').length);scope.unmount()
})
test('reversal detail entry is hidden without permission or for a reversal or already-reversed transaction',async()=>{
 globalThis.fetch=async()=>response(staffContext)
 for(const [permissions,detail,count] of [[['wallet.view'],{transaction:originalPointsRow,related:[]},0],[['wallet.points.reverse'],{transaction:{...originalPointsRow,type:'reversal'},related:[]},0],[['wallet.points.reverse'],{transaction:originalPointsRow,related:[{type:'reversal',originalId:'entry-original'}]},0],[['wallet.points.reverse'],{transaction:originalPointsRow,related:[]},1]]){const scope=mount(ReversalAction,'staff',reactive({params:{storeId:'A'},query:{},hash:''}),{store:'A',detail,returnTo:'/stores/A/wallet/transactions?category=points'},permissions);await tick();assert.equal(nodes(scope.root,n=>n.tag==='a').length,count);scope.unmount()}
})
test('dedicated reversal confirms the original record without a transaction ID input and preserves current version',async()=>{
 const writes=[];globalThis.fetch=async(url,options={})=>{if(options.method==='POST'){writes.push({url,body:JSON.parse(options.body)});return response({account:{balance:7,version:4},entry:{id:'reversal-new',kind:'reversal',quantity:3}})}return response(url.endsWith('/context')?staffContext:url.includes('/transactions/')?{transaction:originalPointsRow,related:[],events:[]}:{accountId:'p',balance:10,version:3})}
 const scope=mount(ReversalPage,'staff',reactive({params:{storeId:'A',entryId:'entry-original'},query:{returnTo:'/stores/A/wallet/transactions?category=points'},hash:''}));await tick()
 assert.equal(nodes(scope.root,n=>n.tag==='input'&&n.props.type!=='checkbox').length,1)
 const input=nodes(scope.root,n=>n.tag==='input'&&n.props.type!=='checkbox')[0];input.props['onUpdate:modelValue']('撤销原赠分');await tick();assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='确认冲正此笔积分')[0].props.disabled,true)
 nodes(scope.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0].props['onUpdate:modelValue'](true);await tick();await nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit(submitEvent());await tick()
 assert.equal(writes.length,1);assert.equal(writes[0].url,'/api/v1/stores/A/wallet/points/entries/entry-original/reverse');assert.equal(writes[0].body.expectedVersion,3);assert.equal(writes[0].body.reason,'撤销原赠分');assert.equal(scope.navigation.at(-1).path,'/stores/A/wallet/transactions/points/reversal-new');scope.unmount()
})


test('leaving points operations for records never opens a funding account',async()=>{
 const calls=[];globalThis.fetch=async(url,options={})=>{calls.push({url,method:options.method});return response(url.endsWith('/context')?staffContext:url.endsWith('/accounts')?[]:{accountId:'p',balance:10,version:1})}
 const route=reactive({name:'wallet-topup',params:{storeId:'A'},query:{kind:'points'},hash:''});const scope=mount(StaffPoints,'staff',route);await tick();await scope.workspace.choose({id:'points-only',phone:'+6581234567',customerCode:'C'},false);await tick();route.name='wallet-staff-transactions';route.query={category:'points'};await tick();assert.equal(calls.filter(c=>c.url.endsWith('/accounts')).length,0);assert.equal(calls.filter(c=>c.method==='POST').length,0);scope.unmount()
})
test('a dedicated points reversal stays in the transaction-record navigation section',async()=>{
 globalThis.fetch=async()=>response(staffContext);const scope=mount(BottomNav,'staff',reactive({path:'/stores/A/wallet/points/e/reverse',params:{storeId:'A'},query:{},hash:''}),{store:'A'});await tick();const current=nodes(scope.root,n=>n.tag==='a'&&String(n.props.class).includes('is-current'));assert.equal(current.length,1);assert.equal(current[0].props.href,'/stores/A/wallet/transactions');scope.unmount()
})

test('wallet customer maintenance reuses the tenant directory only for authorized tenant administrators',async()=>{
 globalThis.fetch=async()=>response(staffContext)
 for(const component of [CashierTools,RechargePage,StaffMore]){
  const scope=mount(component,'staff',reactive({name:'wallet-topup',params:{storeId:'A'},query:{entry:'phone'},hash:''}));await tick()
  const links=()=>nodes(scope.root,n=>n.tag==='a'&&String(n.props.href).includes('/wallet/customers'))
  assert.equal(links().length,0);scope.auth.user.roles=['tenant_admin'];scope.auth.user.permissions.push('tenant.admin.manage');await tick()
  assert.equal(links().length,1);assert.match(String(links()[0].props.href),/^\/stores\/A\/wallet\/customers\?from=wallet/)
  scope.auth.user.storeIds=['B'];await tick();assert.equal(links().length,0);scope.unmount()
 }
})

test('no phone match offers shared customer creation without creating accounts or customers automatically',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,method:options?.method});return response(url.includes('/customers?')?[]:staffContext)}
 const scope=mount(RechargePage,'staff',reactive({name:'wallet-topup',params:{storeId:'A'},query:{entry:'phone'},hash:''}));scope.auth.user.roles=['tenant_admin'];scope.auth.user.permissions.push('tenant.admin.manage');await tick()
 const input=nodes(scope.root,n=>n.tag==='input'&&n.props.type==='tel')[0];input.props['onUpdate:modelValue']('81112469');await tick();await nodes(scope.root,n=>n.tag==='form'&&String(n.props.class).includes('wallet-phone-search'))[0].props.onSubmit(submitEvent());await tick()
 const link=nodes(scope.root,n=>n.tag==='a'&&allText(n).includes('新增顾客'))[0];assert.ok(link);assert.match(String(link.props.href),/\/wallet\/customers\?/);assert.match(String(link.props.href),/create=1/);assert.match(String(link.props.href),/keyword=81112469/)
 assert.equal(scope.workspace.customer.value,null);assert.ok(!calls.some(c=>c.method==='POST'||c.url.endsWith('/accounts')));scope.unmount()
})

const privacyNoticeFixture={version:'customer-profile-v1',textZh:'顾客请求的服务及交易记录用途',textEn:'Requested services',labelZh:'已向顾客说明并取得同意（商户登记）',labelEn:'Merchant attestation'}
async function managedCustomers(query={},post){
 const calls=[],profile={id:'shared-customer',customerCode:'C-SHARED',displayName:'统一顾客',nickname:'先生',phoneE164:'+6581112470',email:null,status:'active'}
 globalThis.fetch=async(url,options={})=>{const body=options.body&&JSON.parse(options.body);calls.push({url,body,method:options.method});if(options.method==='POST'||options.method==='PATCH')return post?post(url,body):response({success:true,customer:profile});return response(url.endsWith('/customer-privacy-notice')?privacyNoticeFixture:url.endsWith('/privacy-consents')?{records:[]}:url.includes('/tenant-admin/customers?')?{success:true,customers:[profile],page:{limit:20,offset:0,total:1}}:staffContext)}
 const route=reactive({name:'wallet-customers',params:{storeId:'A'},query,hash:''}),scope=mount(StaffCustomers,'staff',route);scope.auth.user.roles=['tenant_admin'];scope.auth.user.permissions.push('tenant.admin.manage');await tick()
 return {...scope,route,calls,profile}
}

test('wallet customer cards use central APIs, have no tenant sidebar, and default PDPA checkbox is required',async()=>{
 const scope=await managedCustomers({create:'1',keyword:'81112470',from:'wallet-recharge'});await tick()
 assert.equal(nodes(scope.root,n=>n.tag==='table').length,0);assert.doesNotMatch(allText(scope.root),/租户后台/)
 const check=nodes(scope.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0];assert.ok(check);assert.equal(check.checked,true)
 const form=nodes(scope.root,n=>n.tag==='form')[0];check.checked=false;check.listeners.change({target:check});await tick();assert.equal(nodes(form,n=>n.tag==='button'&&allText(n).trim()==='保存')[0].props.disabled,true);await form.props.onSubmit(submitEvent());await tick();assert.ok(!scope.calls.some(c=>c.method==='POST'))
 check.checked=true;check.listeners.change({target:check});await tick();await form.props.onSubmit(submitEvent());await tick()
 const created=scope.calls.find(c=>c.method==='POST');assert.match(created.url,/\/tenant-admin\/customers$/);assert.deepEqual(created.body.privacyConsent,{accepted:true,noticeVersion:'customer-profile-v1',entrySource:'wallet'});assert.equal(created.body.phoneE164,'+6581112470');assert.ok(!scope.calls.some(c=>c.url.endsWith('/accounts')));scope.unmount()
})

test('profile editing does not manufacture a new privacy confirmation; records open separately',async()=>{
 const scope=await managedCustomers();await tick();nodes(scope.root,n=>n.tag==='button'&&allText(n).trim()==='编辑')[0].props.onClick();await tick();assert.equal(nodes(scope.root,n=>n.tag==='input'&&n.props.type==='checkbox').length,0)
 await nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit(submitEvent());await tick();assert.equal(scope.calls.find(c=>c.method==='PATCH').body.privacyConsent,undefined)
 nodes(scope.root,n=>n.tag==='button'&&allText(n).trim()==='顾客同意（PDPA）')[0].props.onClick();await tick();assert.equal(nodes(scope.root,n=>n.tag==='form').length,0);assert.match(allText(scope.root),/商户新增顾客，即确认已取得顾客同意/);assert.doesNotMatch(allText(scope.root),/尚无.*同意|不代表已经同意/);assert.ok(scope.calls.some(c=>c.url.endsWith('/shared-customer/privacy-consents')));scope.unmount()
})

test('shared customer save blocks duplicates and a late response cannot repaint another store',async()=>{
 let finish;const scope=await managedCustomers({create:'1',keyword:'81112470'},()=>new Promise(resolve=>{finish=resolve}));const form=nodes(scope.root,n=>n.tag==='form')[0];const first=form.props.onSubmit(submitEvent());await form.props.onSubmit(submitEvent());await tick();assert.equal(scope.calls.filter(c=>c.method==='POST').length,1)
 scope.auth.user.storeIds.push('B');scope.route.params.storeId='B';await tick();finish(response({success:true,customer:scope.profile}));await first;await tick();assert.doesNotMatch(allText(scope.root),/保存成功/);assert.equal(scope.calls.filter(c=>c.method==='POST').length,1);scope.unmount()
})

test('unprivileged wallet customer management does not query the central directory',async()=>{
 const calls=[];globalThis.fetch=async url=>{calls.push(url);return response(staffContext)};const scope=mount(StaffCustomers,'staff',reactive({name:'wallet-customers',params:{storeId:'A'},query:{},hash:''}));await tick();assert.ok(!calls.some(url=>url.includes('/tenant-admin/')));assert.equal(nodes(scope.root,n=>n.tag==='form').length,0);scope.unmount()
})

test('customer self-registration confirms PDPA, blocks duplicate clicks, and never submits passwords or wallet commands',async()=>{
 let finish;const calls=[];globalThis.fetch=async(url,options={})=>{const body=options.body&&JSON.parse(options.body);calls.push({url,body});if(url.endsWith('/register'))return new Promise(resolve=>{finish=resolve});return response(url.includes('/registration-notice')?{...privacyNoticeFixture,labelZh:'本人已阅读并确认 PDPA'}:{accounts:[],pendingBills:[],context:staffContext})}
 const scope=mount(SelfRegistrationView,'customer',reactive({params:{storeId:'A'},query:{},hash:''}),{store:'A'});await tick()
 assert.doesNotMatch(allText(scope.root),/商户代为登记/);const input=nodes(scope.root,n=>n.tag==='input'&&n.props.type==='tel')[0];input.value='81112473';input.props.onInput({target:input});await tick()
 const checkbox=nodes(scope.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0];checkbox.checked=false;checkbox.listeners.change({target:checkbox});await tick();const form=nodes(scope.root,n=>n.tag==='form')[0];assert.equal(nodes(form,n=>n.tag==='button'&&allText(n).trim()==='注册顾客')[0].props.disabled,true);await form.props.onSubmit(submitEvent());assert.ok(!calls.some(c=>c.body))
 checkbox.checked=true;checkbox.listeners.change({target:checkbox});await tick();const pending=form.props.onSubmit(submitEvent());await form.props.onSubmit(submitEvent());await tick();assert.equal(calls.filter(c=>c.url.endsWith('/register')).length,1);const body=calls.find(c=>c.url.endsWith('/register')).body;assert.deepEqual(body.privacyConsent,{accepted:true,noticeVersion:'customer-profile-v1'});assert.equal(body.password,undefined);assert.equal(body.storeId,'A');assert.equal(body.phoneE164,'+6581112473')
 finish(response({success:true,customerId:'self-created'}));await pending;await tick();assert.match(allText(scope.root),/注册成功/);assert.match(allText(scope.root),/手机号后 6 位/);assert.ok(!calls.some(c=>c.url.includes('/accounts')||c.url.endsWith('/topups')));scope.unmount()
})


for(const mode of ['phone','scan'])for(const kind of ['grants','deductions','redemptions'])test(`completed ${kind} resets ${mode} customer and requires fresh selection`,async()=>{
 let writes=0,fail=true
 globalThis.fetch=async(url,options={})=>{
  if(options.method==='POST'){writes++;if(fail)return {ok:false,status:500,json:async()=>({message:'retry required'})};return response({account:{balance:13,version:4},entry:{id:'completed-entry',kind:kind==='grants'?'manual_grant':kind==='deductions'?'manual_deduct':'redemption',quantity:3,beforeBalance:10,afterBalance:13}})}
  return response(url.includes('/customers?')?[{id:'completed-c',displayName:'上一位',phone:'+6581234567'}]:url.endsWith('/context')?staffContext:{accountId:'p',balance:10,version:3})
 }
 const route=reactive({name:'wallet-topup',params:{storeId:'A'},query:{kind:'points',...(mode==='phone'?{entry:'phone'}:{})},hash:''}),scope=mount(RechargePage,'staff',route);await tick()
 if(mode==='phone'){
  nodes(scope.root,n=>n.tag==='input'&&n.props.type==='tel')[0].props['onUpdate:modelValue']('81234567');await tick()
  await nodes(scope.root,n=>n.tag==='form'&&String(n.props.class).includes('wallet-phone-search'))[0].props.onSubmit(submitEvent());await tick()
  nodes(scope.root,n=>n.props?.['data-phone-candidate']==='completed-c')[0].props.onClick();await tick()
  await nodes(scope.root,n=>n.tag==='button'&&allText(n)==='确认选中顾客')[0].props.onClick();await tick()
 }else{await scope.workspace.choose({id:'completed-c',displayName:'上一位',phone:'+6581234567'},false);scope.workspace.identifiedCustomerId.value='completed-c';await tick()}
 nodes(scope.root,n=>n.tag==='select')[0].props['onUpdate:modelValue'](kind);await tick()
 nodes(scope.root,n=>n.tag==='input'&&n.props.type==='number')[0].props['onUpdate:modelValue']('3')
 nodes(scope.root,n=>n.tag==='label'&&allText(n).trim().startsWith(kind==='redemptions'?'兑换内容':'原因'))[0].children.find(n=>n.tag==='input').props['onUpdate:modelValue']('本次操作');await tick()
 await nodes(scope.root,n=>n.tag==='form'&&String(n.props.class).includes('wallet-form'))[0].props.onSubmit(submitEvent());await tick()
 const label=kind==='grants'?'确认增加积分':kind==='deductions'?'确认扣减积分':'确认积分兑换'
 const confirm=()=>nodes(scope.root,n=>n.tag==='button'&&allText(n)===label)[0]
 assert.ok(confirm(),allText(scope.root));await confirm().props.onClick();await tick();assert.equal(scope.workspace.customer.value.id,'completed-c');assert.ok(confirm())
 fail=false;await confirm().props.onClick();await tick()
 assert.equal(scope.workspace.customer.value,null);assert.equal(scope.workspace.account.value,null);assert.equal(scope.workspace.phoneConfirmedCustomerId.value,null);assert.equal(scope.workspace.identifiedCustomerId.value,null);assert.equal(scope.workspace.customerCode.value,'');assert.deepEqual(scope.workspace.customers.value,[])
 assert.match(allText(scope.root),/办理成功/);assert.equal(nodes(scope.root,n=>n.tag==='input'&&n.props.type==='number').length,0)
 if(mode==='phone'){assert.equal(nodes(scope.root,n=>n.tag==='input'&&n.props.type==='tel')[0].value,'');assert.equal(nodes(scope.root,n=>n.props?.['data-phone-candidate']).length,0);assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n)==='确认选中顾客').length,0)}
 await scope.workspace.refresh();await tick();assert.equal(scope.workspace.customer.value,null);assert.equal(writes,2);assert.equal(nodes(scope.root,n=>n.tag==='input'&&n.props.type==='number').length,0)
 if(mode==='phone'){
  nodes(scope.root,n=>n.tag==='input'&&n.props.type==='tel')[0].props['onUpdate:modelValue']('81234567');await tick()
  await nodes(scope.root,n=>n.tag==='form'&&String(n.props.class).includes('wallet-phone-search'))[0].props.onSubmit(submitEvent());await tick()
  nodes(scope.root,n=>n.props?.['data-phone-candidate']==='completed-c')[0].props.onClick();await tick();assert.equal(scope.workspace.customer.value,null)
  await nodes(scope.root,n=>n.tag==='button'&&allText(n)==='确认选中顾客')[0].props.onClick();await tick()
 }else{await scope.workspace.choose({id:'completed-c',displayName:'上一位',phone:'+6581234567'},false);scope.workspace.identifiedCustomerId.value='completed-c';await tick()}
 assert.equal(scope.workspace.customer.value.id,'completed-c');assert.equal(nodes(scope.root,n=>n.tag==='input'&&n.props.type==='number')[0].value,'');assert.equal(nodes(scope.root,n=>n.tag==='button'&&allText(n)===label).length,0);assert.equal(writes,2);scope.unmount()
})

for(const status of ['credited','cancelled','pending_collection'])test(`topup detail ${status} clears selection only for completed operations`,async()=>{
 let resolveRecord
 globalThis.fetch=async url=>url.endsWith('/topups/reset-topup')?new Promise(resolve=>resolveRecord=()=>resolve(response({id:'reset-topup',accountId:'a',amountCents:100,channel:'cash',status,createdAt:'2026-10-24T00:00:00Z'}))):response(url.endsWith('/context')?staffContext:[])
 const scope=mount(TopupDetail,'staff',reactive({name:'wallet-staff-topup',params:{storeId:'A',topupId:'reset-topup'},query:{entry:'phone'},hash:''}));await tick()
 scope.workspace.customer.value={id:'completed-c'};scope.workspace.account.value={id:'a'};scope.workspace.phoneConfirmedCustomerId.value='completed-c';scope.workspace.identifiedCustomerId.value='completed-c';scope.workspace.customerCode.value='old';scope.workspace.customers.value=[{id:'old'}]
 resolveRecord();await tick()
 assert.equal(scope.workspace.customer.value?.id,status==='pending_collection'?'completed-c':undefined)
 if(status!=='pending_collection'){assert.equal(scope.workspace.account.value,null);assert.equal(scope.workspace.phoneConfirmedCustomerId.value,null);assert.equal(scope.workspace.identifiedCustomerId.value,null);assert.equal(scope.workspace.customerCode.value,'');assert.deepEqual(scope.workspace.customers.value,[])}
 scope.unmount()
})


test('successful collection clears selection even when the subsequent detail refresh fails',async()=>{
 let confirmed=false,fail=true
 const settle=async()=>{for(let i=0;i<100;i++){await new Promise(r=>setTimeout(r,1));await tick();if(!nodes(scope.root,n=>n.tag==='form')[0]?.children.some(n=>n.tag==='button'&&n.props.disabled))break}}
 globalThis.fetch=async(url,options={})=>{
  if(url.endsWith('/topups/confirm-reset/confirm')){if(fail)return {ok:false,status:500,json:async()=>({message:'confirmation failed'})};confirmed=true;return response({status:'credited'})}
  if(url.endsWith('/topups/confirm-reset')){if(confirmed)throw Error('detail refresh offline');return response({id:'confirm-reset',createdAt:'2026-10-24T00:00:00Z',accountId:'a',amountCents:100,channel:'cash',status:'pending_collection'})}
  return response(url.endsWith('/context')?staffContext:[])
 }
 const scope=mount(TopupDetail,'staff',reactive({name:'wallet-staff-topup',params:{storeId:'A',topupId:'confirm-reset'},query:{},hash:''}),{},['wallet.view','wallet.topup.create','wallet.collection.confirm']);await tick()
 scope.workspace.customer.value={id:'completed-c'};scope.workspace.account.value={id:'a'};scope.workspace.phoneConfirmedCustomerId.value='completed-c'
 nodes(scope.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0].props['onUpdate:modelValue'](true);await tick()
 await nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit(submitEvent());await settle();assert.equal(scope.workspace.customer.value.id,'completed-c')
 fail=false;await nodes(scope.root,n=>n.tag==='form')[0].props.onSubmit(submitEvent());await settle();assert.equal(confirmed,true);assert.equal(scope.workspace.customer.value,null);assert.equal(scope.workspace.account.value,null);assert.equal(scope.workspace.phoneConfirmedCustomerId.value,null);scope.unmount()
})


test('customer home keeps the same two action columns with and without a wallet account',async()=>{
 const requests=[]
 for(const accounts of [[],[{...funded}]]){
  globalThis.fetch=async(url,options={})=>{requests.push({url,options});return response(url.endsWith('/wine-storage/home')?storageHome:url.endsWith('/me')?{accounts,pendingBills:[],context:{transactionsEnabled:true,store:{timezone:'Asia/Singapore'}}}:{balance:0})}
  const scope=mount(CustomerHome,'customer',reactive({params:{storeId:'A'},query:{},hash:''}));await tick()
  const actions=nodes(scope.root,n=>String(n.props?.class??'').split(' ').includes('wallet-code-actions'))[0];assert.ok(actions)
  assert.equal(actions.props.class,'wallet-hero-actions wallet-code-actions');assert.equal(nodes(scope.root,n=>n.tag==='a'&&n.props.href==='/wallet/A/wine-storage').length,1)
  assert.deepEqual(nodes(actions,n=>n.tag==='a').map(n=>n.props.href),['/wallet/A/code','/wallet/A/scan'])
  scope.unmount()
 }
 assert.ok(requests.every(r=>!r.options.method||r.options.method==='GET'))
 assert.ok(!requests.some(r=>r.url.endsWith('/accounts')))
})


test('cashier shortcuts directly expose permitted card and storage operations without duplicating transaction navigation',async()=>{
 globalThis.fetch=async()=>response(staffContext)
 for(const permissions of [[],['wallet.cards.view'],['wine_storage.view'],['wallet.cards.view','wine_storage.view']]){
  const route=reactive({name:'wallet-collect',path:'/stores/A/wallet/collect',params:{storeId:'A'},query:{},hash:''})
  const scope=mount(CashierTools,'staff',route,{},permissions);await tick()
  const links=nodes(scope.root,n=>n.tag==='a').map(n=>n.props.href)
  assert.equal(links.includes('/stores/A/wallet/cards'),permissions.includes('wallet.cards.view'))
  assert.equal(links.includes('/stores/A/wine-storage'),permissions.includes('wine_storage.view'))
  assert.equal(links.includes('/stores/A/wallet/transactions'),false)
  assert.ok(links.includes('/stores/A/wallet/transactions?type=topup&status=pending'))
  if(permissions.includes('wallet.cards.view'))assert.match(allText(scope.root),/持卡与核销/)
  if(permissions.includes('wine_storage.view'))assert.match(allText(scope.root),/酒水寄存管理/)
  scope.unmount()
  const more=mount(StaffMore,'staff',route,{},permissions);await tick()
  assert.equal(nodes(more.root,n=>n.tag==='a'&&['/stores/A/wallet/cards','/stores/A/wine-storage'].includes(n.props.href)).length,0)
  more.unmount()
  const nav=mount(BottomNav,'staff',route,{store:'A'},permissions);await tick()
  assert.equal(nodes(nav.root,n=>n.tag==='a'&&n.props.href==='/stores/A/wallet/transactions').length,1)
  nav.unmount()
 }
})

test('cashier tabs directly navigate to operations, clear prior identity, and cannot switch while busy',async()=>{
 globalThis.fetch=async()=>response(staffContext)
 const route=reactive({name:'wallet-collect',params:{storeId:'A'},query:{},hash:''}),props=reactive({disabled:false})
 const scope=mount(CashierTabs,'staff',route,props,['wallet.view','wallet.charge.create','wallet.collection.confirm','wallet.topup.create','wallet.points.view']);await tick()
 assert.equal(nodes(scope.root,n=>n.props?.['data-cashier-tab']).length,3)
 scope.workspace.customer.value={id:'previous'};scope.workspace.identifiedCustomerId.value='previous';scope.workspace.account.value={id:'previous-account'}
 await nodes(scope.root,n=>n.props?.['data-cashier-tab']==='money')[0].props.onClick();await tick()
 assert.deepEqual(scope.navigation[0],{path:'/stores/A/wallet/topup',query:{kind:'money',scan:'1'}});assert.equal(scope.workspace.customer.value,null);assert.equal(scope.workspace.account.value,null);assert.equal(scope.workspace.identifiedCustomerId.value,null)
 props.disabled=true;await tick();await nodes(scope.root,n=>n.props?.['data-cashier-tab']==='points')[0].props.onClick();assert.equal(scope.navigation.length,1)
 props.disabled=false;await tick();await nodes(scope.root,n=>n.props?.['data-cashier-tab']==='points')[0].props.onClick();assert.deepEqual(scope.navigation[1],{path:'/stores/A/wallet/topup',query:{kind:'points',scan:'1'}});scope.unmount()
 const points=mount(CashierTabs,'staff',reactive({name:'wallet-topup',params:{storeId:'A'},query:{kind:'points'},hash:''}),{},['wallet.points.view']);await tick();assert.equal(nodes(points.root,n=>n.props?.['data-cashier-tab']).length,1);points.unmount()
})
test('next recharge customer preserves the chosen kind without carrying customer or receipt query fields',async()=>{
 globalThis.fetch=async()=>response(staffContext)
 for(const kind of ['money','points']){
  const scope=mount(NextCustomer,'staff',reactive({params:{storeId:'A'},query:{kind,receipt:'old',customerId:'previous'},hash:''}));await tick();scope.workspace.customer.value={id:'previous'}
  nodes(scope.root,n=>n.tag==='button')[0].props.onClick();await tick();assert.deepEqual(scope.navigation[0].query,{scan:'1',kind});assert.equal(scope.workspace.customer.value,null);scope.unmount()
 }
})
test('late payment completion after leaving the cashier cannot navigate over the new operation',async()=>{
 let resolve;const writes=[];globalThis.fetch=async(url,options)=>{if(url.endsWith('/personal-scan-charges')){writes.push(options);return new Promise(r=>resolve=r)}return response(staffContext)}
 const scope=mount(StaffCollect,'staff',reactive({params:{storeId:'leave-cashier'},query:{},hash:''}),{},permissions);await tick();await enterNumber(scope.root,5);await tick();nodes(scope.root,n=>n.tag==='form'&&n.props.class==='wallet-amount-card')[0].props.onSubmit(submitEvent());await tick();nodes(scope.root,n=>n.props?.['data-scan'])[0].props.onClick();await tick();assert.equal(writes.length,1);scope.unmount()
 resolve(response({status:'paid',bill:{id:'original-bill'}}));await tick();assert.equal(scope.navigation.length,0)
})


test('direct cashier cannot start the camera before a valid amount is entered',async()=>{
 globalThis.fetch=async()=>response(staffContext)
 const scope=mount(StaffCollect,'staff',reactive({name:'wallet-collect',params:{storeId:'valid-cashier-amount'},query:{},hash:''}),{},permissions);await tick()
 const form=nodes(scope.root,n=>n.tag==='form'&&n.props.class==='wallet-amount-card')[0];form.props.onSubmit(submitEvent());await tick();assert.equal(nodes(scope.root,n=>n.props?.['data-scan']).length,0)
 await enterNumber(scope.root,5);await tick();form.props.onSubmit(submitEvent());await tick();assert.equal(nodes(scope.root,n=>n.props?.['data-scan']).length,1);scope.unmount()
})
