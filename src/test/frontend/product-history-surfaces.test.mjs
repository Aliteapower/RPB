import {test,before,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {parse,compileScript} from '@vue/compiler-sfc'
import {build} from 'esbuild'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {createRenderer,h,nextTick,reactive} from 'vue'
import {createPinia} from 'pinia'
import {routeLocationKey} from 'vue-router'
import {createI18n} from 'vue-i18n'
let Bottom,Shell,Cards,Notice,productText,listQueueTickets;const mounted=[]
const renderer=createRenderer({createElement:tag=>({tag,children:[],props:{}}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>n.props[k]=v,insert:(n,p,a)=>{if(n.parent){const old=n.parent.children.indexOf(n);if(old>=0)n.parent.children.splice(old,1)}n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:n=>n.parent?.children[n.parent.children.indexOf(n)+1]||null})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))],text=n=>(n.text||'')+(n.children||[]).map(text).join(''),tick=async()=>{for(let i=0;i<8;i++)await nextTick()}
before(async()=>{
 const result=await build({stdin:{contents:"export {default as Bottom} from './src/modules/wallet/components/WalletBottomNav.vue';export {default as Shell} from './src/modules/daily-ledger/components/LedgerShell.vue';export {default as Cards} from './src/modules/cards/CardsStaffPage.vue';export {listQueueTickets} from './src/api/queueTicketListApi';export {default as Notice} from './src/modules/tenant-products/ProductHistoryNotice.vue';export * as productText from './src/modules/tenant-products/locales.ts'",resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router','vue-i18n','pinia'],plugins:[{name:'history-surfaces',setup(b){
 b.onLoad({filter:/\.css$/},()=>({contents:'',loader:'js'}))
 b.onLoad({filter:/useCardsWorkspace\.ts$/},()=>({contents:"import {ref} from 'vue';export function useCardsWorkspace(){return {store:ref('A'),busy:ref(false),error:ref(''),notice:ref(''),plans:ref([]),holdings:ref([]),entries:ref([]),purchases:ref([]),enabled:ref(true),pending:ref(false),can:()=>true,api:()=>({}),run:async f=>f(),load:async()=>{},recover:async()=>{},loadMore:async()=>{},moreEntries:ref(false),morePurchases:ref(false)}}",loader:'ts'}))
 b.onLoad({filter:/\.vue$/},async({path})=>{
 if(/(CardOperations|CardPlanEditor|CardPurchasePanel|CardHistory|CardReport)\.vue$/.test(path))return{contents:`import {h} from 'vue';export default {props:['can','canReverse'],setup:p=>()=>h('section',{'data-panel':'${path.split(/[\\/]/).pop()}','data-can-write':p.can?.('wallet.cards.sell'),'data-can-reverse':p.canReverse})}`,loader:'js'}
 if(/(StoreSwitcher|FrontendLocaleSwitcher|WalletIcon|LedgerIcon)\.vue$/.test(path))return{contents:'export default {render(){return null}}',loader:'js'}
 const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}
 })}}]});await mkdir('target/history-surfaces',{recursive:true});await writeFile('target/history-surfaces/entry.mjs',result.outputFiles[0].contents);({Bottom,Shell,Cards,Notice,productText,listQueueTickets}=await import(pathToFileURL(process.cwd()+'/target/history-surfaces/entry.mjs').href+'?'+Date.now()))
})
afterEach(()=>{for(const app of mounted)app.unmount();mounted.length=0})
function mount(component,props={},query={},locale='zh-CN'){const root={children:[]},app=renderer.createApp({render:()=>h(component,props)});app.use(createPinia());app.use(createI18n({legacy:false,locale,messages:{'zh-CN':{tenantProducts:productText.tenantProductsZh},'en-SG':{tenantProducts:productText.tenantProductsEn}},missingWarn:false,fallbackWarn:false}));app.provide(routeLocationKey,reactive({params:{storeId:'A'},path:'/stores/A/wallet/transactions',query}));app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:p.to},slots.default?.())});app.mount(root);mounted.push(app);return root}
test('closed wallet navigation contains transactions only; active and customer navigation remain usable',()=>{
 const closed=mount(Bottom,{store:'A',historyOnly:true});assert.deepEqual(nodes(closed,n=>n.tag==='a').map(n=>n.props.href),['/stores/A/wallet/transactions?history=1'])
 assert.equal(nodes(mount(Bottom,{store:'A'}),n=>n.tag==='a').length,3)
 assert.equal(nodes(mount(Bottom,{store:'A',customer:true}),n=>n.tag==='a').length,2)
})

test('wallet history navigation preserves its read-only marker across store changes without changing active or customer targets',async()=>{
 const props=reactive({store:'A',historyOnly:true}),root=mount(Bottom,props,{history:'1'})
 const hrefs=()=>nodes(root,n=>n.tag==='a').map(n=>n.props.href)
 assert.deepEqual(hrefs(),['/stores/A/wallet/transactions?history=1'])
 props.store='B';await tick();assert.deepEqual(hrefs(),['/stores/B/wallet/transactions?history=1'])
 props.historyOnly=false;await tick();assert.deepEqual(hrefs(),['/stores/B/wallet','/stores/B/wallet/transactions','/stores/B/wallet/more'])
 const customer=mount(Bottom,{store:'B',customer:true})
 assert.deepEqual(nodes(customer,n=>n.tag==='a').map(n=>n.props.href),['/wallet/B','/wallet/B/transactions?category=all'])
})
test('ledger historical shell removes record and settings navigation',()=>{
 const history=mount(Shell,{storeId:'A',tab:'history',admin:false,historyOnly:true});assert.equal(nodes(history,n=>n.tag==='button').length,1);assert.match(text(history),/dailyLedger.messages.history/)
 assert.equal(nodes(mount(Shell,{storeId:'A',tab:'record',admin:true}),n=>n.tag==='button').length,3)
})
test('card history starts on records and removes operation, settings and recovery controls even for full permission actors',async()=>{
 const root=mount(Cards,{}, {history:'1'});await tick();assert.doesNotMatch(text(root),/启用卡功能/)
 assert.equal(nodes(root,n=>n.props?.['data-panel']==='CardOperations.vue').length,0)
 const history=nodes(root,n=>n.props?.['data-panel']==='CardHistory.vue');assert.equal(history.length,1);assert.equal(history[0].props['data-can-reverse'],false)
 const purchase=nodes(root,n=>n.tag==='button'&&text(n)==='购卡订单')[0];assert.ok(purchase);purchase.props.onClick();await tick()
 assert.equal(nodes(root,n=>n.props?.['data-panel']==='CardPurchasePanel.vue')[0].props['data-can-write'],false)
})
test('queue historical date is explicitly sent with the scoped GET query',async()=>{
 let url,method;await assert.rejects(listQueueTickets('A',{businessDate:' 2026-10-20 '},async(u,o)=>{url=u;method=o.method;return{ok:false,status:403,json:async()=>({success:false,error:{code:'DENIED',messageKey:'denied'}})}}));assert.equal(method,'GET');assert.equal(url,'/api/v1/stores/A/queue-tickets?businessDate=2026-10-20')
})


test('history notice describes a read-only view without claiming an available product has been closed',()=>{
 const zh=text(mount(Notice));assert.match(zh,/只读/);assert.doesNotMatch(zh,/产品已关闭/)
 const en=text(mount(Notice,{}, {},'en-SG'));assert.match(en,/read.only/i);assert.doesNotMatch(en,/product is closed/i)
})
