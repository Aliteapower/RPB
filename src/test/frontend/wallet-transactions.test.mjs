import {test,before,after} from 'node:test'
import assert from 'node:assert/strict'
import {parse,compileScript} from '@vue/compiler-sfc'
import {createRenderer,h,nextTick,reactive,ref} from 'vue'
import {routeLocationKey,routerKey} from 'vue-router'
import {createI18n} from 'vue-i18n'
import {transactionEventLabel} from '../../modules/wallet/transactions/eventLabel.ts'
import {transactionsZh,transactionsEn} from '../../modules/wallet/transactions/locales.ts'
import {transactionSummary} from '../../modules/wallet/transactions/summary.ts'
import {walletRoutes} from '../../modules/wallet/routes.ts'
import {readFile,readdir,mkdir,writeFile} from 'node:fs/promises'
let StaffShell,CustomerShell,Page,Detail,Evidence,Row,query,api,composable,messages
const oldFetch=globalThis.fetch,oldDocument=globalThis.document,oldWindow=globalThis.window
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


const text=root=>[root.text||'',...(root.children||[]).map(text)].join(' ')
const response=body=>({ok:true,status:200,json:async()=>body})
function mount(component,route,props={},locale='zh-CN'){
 const root={children:[]},calls=[]
 const router={push:async target=>{calls.push(target);route.query=target.query;route.fullPath=target.path+'?'+new URLSearchParams(target.query)},back:()=>calls.push('back')}
 const app=renderer.createApp({setup:()=>()=>h(component,props)})
 const i18n=createI18n({legacy:false,locale,messages:{'zh-CN':{wallet:messages.walletZh},en:{wallet:messages.walletEn}}});app.use(i18n);app.provide(routeLocationKey,route);app.provide(routerKey,router)
 app.component('RouterView',{setup:()=>()=>h('section',{'data-router-view':true},'query view')});app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:p.to},slots.default?.())});app.mount(root)
 return {root,calls,i18n,unmount:()=>app.unmount()}
}
before(async()=>{
 globalThis.document={};globalThis.window={setInterval:()=>1,clearInterval:()=>{}};const {build}=await import('esbuild'),{pathToFileURL}=await import('node:url')
 const entry=`export {default as StaffShell} from './src/modules/wallet/WalletPage.vue';export {default as CustomerShell} from './src/modules/wallet/WalletCustomerPage.vue';export {default as Page} from './src/modules/wallet/pages/WalletTransactions.vue';export {default as Detail} from './src/modules/wallet/pages/WalletTransactionDetail.vue';export {default as Evidence} from './src/modules/wallet/transactions/WalletTransactionEvidence.vue';export {default as Row} from './src/modules/wallet/transactions/WalletTransactionRow.vue';export * as query from './src/modules/wallet/transactions/query.ts';export * as composable from './src/modules/wallet/transactions/useTransactions.ts';export * as messages from './src/modules/wallet/locales.ts'`
 const result=await build({stdin:{contents:entry,resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,loader:{'.css':'empty'},platform:'node',format:'esm',external:['vue','vue-router','vue-i18n'],plugins:[{name:'vue-test',setup(b){
 b.onLoad({filter:/useStaffWallet\.ts$/},()=>({contents:`import {ref} from 'vue';export function provideStaffWallet(){return {store:ref('A'),context:ref(null),pointsContext:ref(false),busy:ref(false),error:ref('old context denied'),run:async fn=>fn(),refresh:async()=>{},can:()=>false}};export const useStaffWallet=provideStaffWallet;`,loader:'ts'}));
 b.onLoad({filter:/authSession\.ts$/},()=>({contents:`export function useAuthSessionStore(){return {authorizedStores:[{storeId:'A',storeName:'共享授权门店',operatingEntityName:'共享租户'}],ensureAuthorizedStores:async()=>{}}}`,loader:'ts'}));
 b.onLoad({filter:/useCustomerWallet\.ts$/},()=>({contents:`import {ref} from 'vue';export function provideCustomerWallet(){return {store:ref('A'),home:ref(null),needsLogin:ref(false),error:ref('inactive store context'),loading:ref(false),refresh:async()=>{},authenticated:async()=>{}}}`,loader:'ts'}));
 b.onLoad({filter:/\.vue$/},async({path})=>{if(/(StoreSwitcher|WalletCustomerLogin|WalletLocaleControl|WalletBottomNav|WalletIcon)\.vue$/.test(path))return {contents:"import {h} from 'vue';export default {setup:()=>()=>h('i')}",loader:'js'};const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return {contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}})}}]})
 const dir='target/wallet-transactions-component-tests';await mkdir(dir,{recursive:true});await writeFile(dir+'/entry.mjs',result.outputFiles[0].contents);({StaffShell,CustomerShell,Page,Detail,Evidence,Row,query,composable,messages}=await import(pathToFileURL(process.cwd()+'/'+dir+'/entry.mjs').href+'?'+Date.now()))
})
after(()=>{globalThis.fetch=oldFetch;globalThis.document=oldDocument;globalThis.window=oldWindow})
const capabilities={money:true,points:true,refunds:true,reversals:true}
const makePage=(overrides={})=>({items:[],totalCount:321,nextCursor:'cursor-2',summary:{consumptionCents:123400,pointsGranted:800},stores:[{id:'A',name:'甲店',timeZone:'Asia/Singapore'},{id:'B',name:'乙店',timeZone:'America/New_York'}],category:overrides.capabilities?.money===false&&overrides.capabilities?.points?'points':'consumption',period:{mode:'month',startDate:'2026-10-01',endDate:'2026-10-31',timeZone:'Asia/Singapore'},capabilities,...overrides})
const route=(customer=false,extra={})=>reactive({params:{storeId:'A'},meta:{walletWorkspace:customer?'customer':'staff'},query:{},path:customer?'/wallet/A/transactions':'/stores/A/wallet/transactions',fullPath:customer?'/wallet/A/transactions':'/stores/A/wallet/transactions',...extra})
const row=(overrides={})=>({id:'record',sourceType:'bill',type:'payment',status:'completed',occurredAt:'2026-10-22T12:00:00Z',createdAt:'2025-01-01T00:00:00Z',postedAt:'2026-10-22T12:00:00Z',storeId:'B',storeName:'乙店',timeZone:'Asia/Singapore',currency:'SGD',amountCents:10000,quantity:0,direction:'out',walletDeltaCents:-6000,principalCents:0,bonusCents:0,creditTotalCents:0,walletPaidCents:6000,externalPaidCents:4000,refundStatus:'refunded',...overrides})
test('URL query retains period, cross-store filters and cursor; customer identity override is stripped',()=>{
 const input={period:'range',startDate:'2025-12-29',endDate:'2026-01-03',type:'refund',status:'completed',reference:'R 1',customerQuery:'名字',targetStoreId:'all',limit:'25',cursor:'bound:cursor',customerId:'victim',year:['2020']}
 const output=query.transactionQuery(input);assert.equal(output.cursor,'bound:cursor');assert.equal(output.endDate,'2026-01-03');assert.equal(output.targetStoreId,'all');assert.equal(output.customerId,undefined);assert.equal(output.year,undefined)
 assert.equal(query.transactionQuery(input,true).customerQuery,undefined);assert.deepEqual(query.transactionQuery({}),{period:'month',type:'all',status:'all',category:'auto',direction:'all'})
})
test('safe cross-store return preserves exact filters and pagination while rejecting external and wrong-role addresses',()=>{
 const back='/stores/A/wallet/transactions?period=year&year=2025&targetStoreId=all&cursor=original'
 assert.equal(query.safeTransactionReturn(back,'B'),back);assert.deepEqual(query.transactionDetailTarget(row(),false,back),{path:'/stores/B/wallet/bills/record',query:{returnTo:back}})
 for(const value of ['https://evil.test','//evil.test','/wallet/A/transactions','/stores/A/wallet/transactions#evil','/stores/A/wallet/transactions\\evil','/stores/A/wallet/transactions%0a'])assert.equal(query.safeTransactionReturn(value,'B'),'/stores/B/wallet/transactions')
 assert.equal(query.safeTransactionReturn('/wallet/A/transactions?type=points_grant','B',true),'/wallet/A/transactions?type=points_grant')
})
test('old independent lists are absent with no alias or redirect while operation and detail routes stay',()=>{
 for(const workspace of walletRoutes){for(const route of workspace.children){assert.ok(!['bills','history','points','topups','refunds'].includes(route.path));assert.equal(route.alias,undefined);assert.equal(route.redirect,undefined)}assert.ok(workspace.children.some(r=>r.path==='transactions'));assert.ok(workspace.children.some(r=>r.path==='topup'));assert.ok(workspace.children.some(r=>r.path==='bills/:billId'))}
})
test('money and points capabilities independently control filters and summaries',()=>{
 const points={money:false,points:true,refunds:false,reversals:false};assert.ok(!query.availableTypes(points).includes('payment'));assert.ok(query.availableTypes(points).includes('reversal'));assert.ok(query.summaryKeys(points).every(k=>!k.endsWith('Cents')))
 const money={money:true,points:false,refunds:false,reversals:false};assert.ok(!query.availableTypes(money).includes('points_grant'));assert.ok(query.summaryKeys(money).every(k=>k.endsWith('Cents')))
})

test('folder switches retain dates and store, reset type and cursor; flow switches reset cursor',()=>{
 const original={period:'range',startDate:'2025-12-31',endDate:'2026-01-02',targetStoreId:'all',category:'points',type:'points_redemption',direction:'out',cursor:'old'}
 const folder=query.switchTransactionFilter(original,'topup',true);assert.equal(folder.category,'topup');assert.equal(folder.type,'all');assert.equal(folder.direction,'out');assert.equal(folder.cursor,undefined);assert.equal(folder.startDate,original.startDate);assert.equal(folder.targetStoreId,'all')
 const flow=query.switchTransactionFilter(original,'in');assert.equal(flow.category,'points');assert.equal(flow.type,'points_redemption');assert.equal(flow.direction,'in');assert.equal(flow.cursor,undefined)
 assert.deepEqual(query.availableTypes(capabilities,'consumption'),['all','payment','refund']);assert.ok(!query.summaryKeys(capabilities,'topup').includes('pointsGranted'));assert.ok(query.summaryKeys(capabilities,'points').every(k=>!k.endsWith('Cents')))
 assert.equal(query.transactionQuery({category:'all'}).category,'all');assert.deepEqual(query.availableCategories({money:false,points:true,refunds:false,reversals:false}),['points'])
})

test('customer entry categories preserve all records and separate money from points',()=>{
 for(const category of ['money','points','all'])assert.equal(query.transactionQuery({category},true).category,category)
 assert.equal(query.effectiveCategory('all'),'all')
 assert.equal(query.effectiveCategory('money'),'money')
 assert.deepEqual(query.availableTypes(capabilities,'money'),['all','payment','topup','refund','reversal'])
 assert.ok(query.summaryKeys(capabilities,'money').includes('consumptionCents'))
 assert.ok(query.summaryKeys(capabilities,'money').includes('topupPrincipalCents'))
 assert.ok(query.summaryKeys(capabilities,'money').every(k=>k.endsWith('Cents')))
 assert.deepEqual(query.availableCategories(capabilities,true),['all','money','points'])
})

test('component has folder tabs and direction buttons, switches by server query without mixed summary',async()=>{
 const requests=[];globalThis.fetch=async url=>{requests.push(url);const params=new URL('http://local'+url).searchParams,category=params.get('category')==='auto'?'consumption':params.get('category');return response(makePage({category,summary:{consumptionCents:1200,topupPrincipalCents:10000,topupBonusCents:1000,topupCreditCents:11000,pointsGranted:20}}))}
 const r=route(),scope=mount(Page,r);await tick();assert.match(requests[0],/category=auto/);assert.ok(!text(scope.root).includes('充值本金实收'));assert.ok(!text(scope.root).includes('赠送积分'))
 nodes(scope.root,n=>n.tag==='button'&&text(n)==='充值')[0].props.onClick();await tick();assert.equal(r.query.category,'topup');assert.equal(r.query.type,'all');assert.match(requests.at(-1),/category=topup/);assert.match(text(scope.root),/充值本金实收/);assert.ok(!text(scope.root).includes('消费原单金额'))
 nodes(scope.root,n=>n.tag==='button'&&text(n)==='支出')[0].props.onClick();await tick();assert.equal(r.query.direction,'out');assert.match(requests.at(-1),/direction=out/)
 nodes(scope.root,n=>n.tag==='button'&&text(n)==='积分')[0].props.onClick();await tick();assert.equal(r.query.category,'points');assert.equal(r.query.direction,'out');assert.ok(!text(scope.root).includes('SGD'));assert.match(text(scope.root),/积分增加/);assert.equal(nodes(scope.root,n=>n.tag==='details').length,1);scope.unmount()
})

test('customer list switches all, money and points through server queries and retains category on pagination',async()=>{
 const requests=[];globalThis.fetch=async url=>{requests.push(url);const category=new URL('http://local'+url).searchParams.get('category');return response(makePage({category,summary:{consumptionCents:1200,topupPrincipalCents:10000,pointsGranted:20}}))}
 const r=route(true,{query:{category:'all'}}),scope=mount(Page,r);await tick();assert.match(requests[0],/category=all/);assert.match(text(scope.root),/消费付款/);assert.match(text(scope.root),/赠送积分/)
 const folders=nodes(scope.root,n=>n.tag==='nav'&&n.props.class==='wallet-transaction-folders')[0]
 nodes(folders,n=>n.tag==='button'&&text(n)==='钱包')[0].props.onClick();await tick();assert.match(requests.at(-1),/category=money/);assert.match(text(scope.root),/充值本金付款/);assert.ok(!text(scope.root).includes('赠送积分'))
 nodes(scope.root,n=>n.tag==='button'&&text(n)==='下一页')[0].props.onClick();await tick();assert.equal(r.query.category,'money');assert.equal(r.query.cursor,'cursor-2')
 nodes(folders,n=>n.tag==='button'&&text(n)==='积分')[0].props.onClick();await tick();assert.equal(r.query.cursor,undefined);assert.match(requests.at(-1),/category=points/);assert.ok(!text(scope.root).includes('SGD'));scope.unmount()
})
test('component uses one query, server period/full summary, URL pagination and filter reset',async()=>{
 const requests=[];globalThis.fetch=async url=>{requests.push(url);return response(makePage())};const r=route(),scope=mount(Page,r);await tick();assert.equal(requests.length,1);assert.match(requests[0],/transactions\?period=month/);assert.match(text(scope.root),/1,234\.00/);assert.doesNotMatch(text(scope.root),/800 积分/);assert.match(text(scope.root),/321/);assert.match(text(scope.root),/2026-10-01/)
 const next=nodes(scope.root,n=>n.tag==='button'&&text(n)==='下一页')[0];next.props.onClick();await tick();assert.equal(r.query.cursor,'cursor-2');assert.match(requests.at(-1),/cursor=cursor-2/)
 const form=nodes(scope.root,n=>n.tag==='form')[0];await form.props.onSubmit({preventDefault(){}});await tick();assert.equal(r.query.cursor,undefined);assert.equal(requests.length,3);scope.unmount()
})
test('customer list never exposes customer filter, points-only capability never exposes cash statistics',async()=>{
 globalThis.fetch=async()=>response(makePage({capabilities:{money:false,points:true,refunds:false,reversals:false},items:[row({sourceType:'points',type:'points_grant',quantity:13,direction:'in'})]}));const scope=mount(Page,route(true));await tick();assert.ok(!text(scope.root).includes('顾客姓名'));assert.ok(!text(scope.root).includes('充值本金实收'));assert.match(text(scope.root),/13 积分/);assert.ok(!nodes(scope.root,n=>n.tag==='option'&&n.props.value==='payment').length);scope.unmount()
})
test('loading, denied and empty queries retain useful feedback and never display stale rows',async()=>{
 let resolve;globalThis.fetch=()=>new Promise(r=>resolve=r);const scope=mount(Page,route());await tick();assert.match(text(scope.root),/加载中/);resolve({ok:false,status:403,json:async()=>({message:'没有交易查询权限'})});await tick();assert.match(text(scope.root),/没有交易查询权限/);assert.equal(nodes(scope.root,n=>n.props?.class==='wallet-transaction-row').length,0);scope.unmount()
 globalThis.fetch=async()=>response(makePage({items:[],nextCursor:null,totalCount:0}));const empty=mount(Page,route());await tick();assert.match(text(empty.root),/暂无记录/);empty.unmount()
})
test('refund shows cash refund and signed wallet change; reversal never claims cash collection',()=>{
 const refund=mount(Row,route(true),{row:row({sourceType:'refund',type:'refund',amountCents:10000,externalPaidCents:4000,walletDeltaCents:6000}),customer:true,returnTo:'/wallet/A/transactions'});assert.match(text(refund.root),/外部现金退回.*40\.00/);assert.match(text(refund.root),/钱包权益变化 \+\$60\.00/);refund.unmount()
 const reversal=mount(Row,route(),{row:row({sourceType:'money_reversal',type:'reversal',amountCents:500,walletDeltaCents:-500}),customer:false,returnTo:'/stores/A/wallet/transactions'});assert.ok(!text(reversal.root).includes('收款'));assert.match(text(reversal.root),/钱包权益变化 -\$5\.00/);reversal.unmount()
})
test('read-only details use source API independently of default month and preserve safe return',async()=>{
 const requests=[];globalThis.fetch=async url=>{requests.push(url);return response({transaction:row({sourceType:'topup',type:'topup',status:'pending'}),related:[],events:[{type:'created',occurredAt:'2025-01-01T00:00:00Z',label:'创建'}]})};const r=route(true,{params:{storeId:'B',sourceType:'topup',transactionId:'record'},query:{returnTo:'/wallet/A/transactions?period=year&year=2025&cursor=old'}}),scope=mount(Detail,r);await tick();assert.ok(requests[0].endsWith('/transactions/topup/record'));assert.ok(!requests[0].includes('month'));assert.match(text(scope.root),/2025/);assert.ok(nodes(scope.root,n=>n.tag==='a'&&n.props.href==='/wallet/A/transactions?period=year&year=2025&cursor=old').length);assert.equal(nodes(scope.root,n=>n.tag==='form').length,0);scope.unmount()
})

test('historical queries render without legacy money context or customer home, including refund-only and inactive stores',()=>{
 for(const [Shell,customer] of [[StaffShell,false],[CustomerShell,true]]){const r=route(customer,{name:customer?'wallet-customer-transactions':'wallet-staff-transactions'}),scope=mount(Shell,r);assert.ok(nodes(scope.root,n=>n.props?.['data-router-view']).length);assert.ok(!text(scope.root).includes('old context denied'));assert.ok(!text(scope.root).includes('inactive store context'));scope.unmount()}
})

test('direct customer-management entry uses shared authorized store header without a money account',()=>{
 const scope=mount(StaffShell,route(false,{name:'wallet-customers'}));assert.match(text(scope.root),/共享授权门店 · 共享租户/);assert.ok(nodes(scope.root,n=>n.props?.['data-router-view']).length);assert.ok(!text(scope.root).includes('old context denied'));scope.unmount()
})
test('native bill detail falls back to authorized historical read-only query when legacy context is unavailable',async()=>{
 const requests=[];globalThis.fetch=async url=>{requests.push(url);return response(url.includes('/collection-proofs/')?[]:{transaction:row(),related:[],events:[]})};const scope=mount(StaffShell,route(false,{name:'wallet-staff-bill',params:{storeId:'A',billId:'original-bill'}}));await tick();assert.ok(requests[0].endsWith('/transactions/bill/original-bill'));assert.match(text(scope.root),/交易记录/);scope.unmount()
})

test('pending mixed refund shows actual wallet posting without claiming external cash already returned',()=>{
 const scope=mount(Row,route(true),{row:row({sourceType:'refund',type:'refund',status:'pending',externalPaidCents:4000,walletDeltaCents:6000}),customer:true,returnTo:'/wallet/A/transactions'});assert.match(text(scope.root),/外部退款待处理/);assert.match(text(scope.root),/钱包权益变化 \+\$60\.00/);assert.ok(!text(scope.root).includes('外部现金退回'));scope.unmount()
})


test('transaction event labels follow the selected language in customer, staff and embedded views',async()=>{
 const at='2026-10-24T07:43:00Z'
 const events=[{type:'created',label:'创建',occurredAt:at},{type:'posted',label:'完成记账',occurredAt:at},{type:'action',label:'确认充值入账',occurredAt:at},{type:'collection',label:'线下实收确认',occurredAt:at},{type:'refund_part_completed',label:'外部退款完成',occurredAt:at},{type:'wallet_posted',label:'钱包扣款',occurredAt:at}]
 globalThis.fetch=async()=>response({transaction:row({sourceType:'points',type:'points_grant',summary:'充测',customerName:'演示账户 · 张'}),related:[],events})
 for(const [component,customer] of [[Detail,true],[Detail,false],[Evidence,false]]){
  const scope=mount(component,route(customer,{params:{storeId:'A',sourceType:'points',transactionId:'record'}}),{store:'A',sourceType:'points',id:'record'},'en');await tick()
  for(const label of ['Created','Posting completed','Top-up confirmed','Offline collection confirmed','External refund completed','Wallet debited'])assert.ok(text(scope.root).includes(label),label)
  for(const event of events)assert.ok(!text(scope.root).includes(event.label),event.label)
  assert.ok(text(scope.root).includes('充测'))
  scope.i18n.global.locale.value='zh-CN';await tick()
  for(const event of events)assert.ok(text(scope.root).includes(event.label),event.label)
  scope.unmount()
 }
})


test('all system event labels translate with safe fallbacks and posted status distinctions',()=>{
 const translate=key=>transactionsEn.eventLabels[key.split('.').at(-1)]
 for(const [key,label] of Object.entries(transactionsZh.eventLabels)){
  assert.equal(transactionEventLabel({type:'action',label},'completed',translate),transactionsEn.eventLabels[key])
 }
 assert.equal(transactionEventLabel({type:'posted',label:'完成记账'},'pending',translate),'Posted')
 assert.equal(transactionEventLabel({kind:'created'},'completed',translate),'Created')
 assert.equal(transactionEventLabel({type:'action'},'completed',translate),'Transaction activity')
 assert.equal(transactionEventLabel({type:'future',label:'自定义内容'},'completed',translate),'自定义内容')
 assert.equal(transactionEventLabel({type:'future'},'completed',translate),'future')
 assert.equal(transactionEventLabel({type:'action',label:'constructor'},'completed',translate),'constructor')
 assert.equal(transactionEventLabel({},'completed',translate),'')
})


test('default summaries localize across list, detail and embedded evidence while literal business notes stay intact',async()=>{
 for(const sourceType of ['bill','topup']){
  const transaction=row({sourceType,type:sourceType==='bill'?'payment':'topup',summary:null})
  globalThis.fetch=async()=>response({transaction,related:[],events:[]})
  for(const component of [Row,Detail,Evidence]){
   const scope=mount(component,route(true,{params:{storeId:'A',sourceType,transactionId:'record'}}),{row:transaction,customer:true,returnTo:'/wallet/A/transactions',store:'A',sourceType,id:'record'},'en');await tick()
   const paragraphs=()=>nodes(scope.root,n=>n.tag==='p').map(text)
   const en=sourceType==='bill'?'Payment':'Top-up',zh=sourceType==='bill'?'消费':'充值'
   assert.ok(paragraphs().includes(en),`${component.__name} ${en}`);assert.ok(!paragraphs().includes(zh))
   scope.i18n.global.locale.value='zh-CN';await tick();assert.ok(paragraphs().includes(zh));scope.unmount()
  }
 }
 for(const component of [Row,Detail,Evidence]){
  const transaction=row({summary:'消费'});globalThis.fetch=async()=>response({transaction,related:[],events:[]})
  const scope=mount(component,route(true,{params:{storeId:'A',sourceType:'bill',transactionId:'record'}}),{row:transaction,customer:true,returnTo:'/wallet/A/transactions',store:'A',sourceType:'bill',id:'record'},'en');await tick()
  assert.ok(nodes(scope.root,n=>n.tag==='p').map(text).includes('消费'));scope.unmount()
 }
})


test('summary defaults depend on source and never translate user descriptions',()=>{
 const t=key=>({ 'wallet.transactions.payment':'Payment','wallet.transactions.topup':'Top-up' })[key]
 for(const sourceType of ['bill','topup','points','refund','money_reversal']){
  for(const summary of ['消费','充值','自定义说明','', 'Payment'])assert.equal(transactionSummary({sourceType,summary},t),summary)
 }
 for(const sourceType of ['points','refund','money_reversal'])assert.equal(transactionSummary({sourceType,summary:null},t),'')
 assert.equal(transactionSummary({sourceType:'bill',summary:null},t),'Payment')
 assert.equal(transactionSummary({sourceType:'topup',summary:null},t),'Top-up')
})
