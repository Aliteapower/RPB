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
let Bottom,Shell,Cards,listQueueTickets;const mounted=[]
const renderer=createRenderer({createElement:tag=>({tag,children:[],props:{}}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>n.props[k]=v,insert:(n,p)=>{n.parent=p;p.children.push(n)},remove:n=>{if(n.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:()=>null})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))],text=n=>(n.text||'')+(n.children||[]).map(text).join(''),tick=async()=>{for(let i=0;i<8;i++)await nextTick()}
before(async()=>{
 const result=await build({stdin:{contents:"export {default as Bottom} from './src/modules/wallet/components/WalletBottomNav.vue';export {default as Shell} from './src/modules/daily-ledger/components/LedgerShell.vue';export {default as Cards} from './src/modules/cards/CardsStaffPage.vue';export {listQueueTickets} from './src/api/queueTicketListApi'",resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router','vue-i18n','pinia'],plugins:[{name:'history-surfaces',setup(b){
 b.onLoad({filter:/\.css$/},()=>({contents:'',loader:'js'}))
 b.onLoad({filter:/useCardsWorkspace\.ts$/},()=>({contents:"import {ref} from 'vue';export function useCardsWorkspace(){return {store:ref('A'),busy:ref(false),error:ref(''),notice:ref(''),plans:ref([]),holdings:ref([]),entries:ref([]),purchases:ref([]),enabled:ref(true),pending:ref(false),can:()=>true,api:()=>({}),run:async f=>f(),load:async()=>{},recover:async()=>{},loadMore:async()=>{},moreEntries:ref(false),morePurchases:ref(false)}}",loader:'ts'}))
 b.onLoad({filter:/\.vue$/},async({path})=>{
 if(/(CardOperations|CardPlanEditor|CardPurchasePanel|CardHistory|CardReport)\.vue$/.test(path))return{contents:`import {h} from 'vue';export default {props:['can','canReverse'],setup:p=>()=>h('section',{'data-panel':'${path.split(/[\\/]/).pop()}','data-can-write':p.can?.('wallet.cards.sell'),'data-can-reverse':p.canReverse})}`,loader:'js'}
 if(/(StoreSwitcher|FrontendLocaleSwitcher|WalletIcon|LedgerIcon)\.vue$/.test(path))return{contents:'export default {render(){return null}}',loader:'js'}
 const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}
 })}}]});await mkdir('target/history-surfaces',{recursive:true});await writeFile('target/history-surfaces/entry.mjs',result.outputFiles[0].contents);({Bottom,Shell,Cards,listQueueTickets}=await import(pathToFileURL(process.cwd()+'/target/history-surfaces/entry.mjs').href+'?'+Date.now()))
})
afterEach(()=>{for(const app of mounted)app.unmount();mounted.length=0})
function mount(component,props={},query={}){const root={children:[]},app=renderer.createApp({render:()=>h(component,props)});app.use(createPinia());app.use(createI18n({legacy:false,locale:'zh-CN',messages:{},missingWarn:false,fallbackWarn:false}));app.provide(routeLocationKey,reactive({params:{storeId:'A'},path:'/stores/A/wallet/transactions',query}));app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:p.to},slots.default?.())});app.mount(root);mounted.push(app);return root}
test('closed wallet navigation contains transactions only; active and customer navigation remain usable',()=>{
 const closed=mount(Bottom,{store:'A',historyOnly:true});assert.deepEqual(nodes(closed,n=>n.tag==='a').map(n=>n.props.href),['/stores/A/wallet/transactions'])
 assert.equal(nodes(mount(Bottom,{store:'A'}),n=>n.tag==='a').length,3)
 assert.equal(nodes(mount(Bottom,{store:'A',customer:true}),n=>n.tag==='a').length,2)
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
