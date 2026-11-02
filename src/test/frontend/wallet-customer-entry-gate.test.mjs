import {test,before,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {parse,compileScript} from '@vue/compiler-sfc'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {createRenderer,h,ref,reactive,nextTick} from 'vue'
import {routeLocationKey} from 'vue-router'
import {createI18n} from 'vue-i18n'
let Page,walletZh,walletEn
const mounted=[],renderer=createRenderer({createElement:tag=>({tag,props:{},children:[]}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},patchProp:(n,k,o,v)=>n.props[k]=v,insert:(n,p,anchor)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=anchor?p.children.indexOf(anchor):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children=n.parent.children.filter(c=>c!==n)},parentNode:n=>n?.parent,nextSibling:n=>{const c=n?.parent?.children??[];return c[c.indexOf(n)+1]??null}})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))],text=n=>(n.text||'')+(n.children||[]).map(text).join('')
const tick=async()=>{for(let i=0;i<12;i++){await Promise.resolve();await nextTick()}}
before(async()=>{
 const result=await build({stdin:{contents:"export {default as Page} from './src/modules/wallet/WalletCustomerPage.vue';export {walletZh,walletEn} from './src/modules/wallet/locales'",resolveDir:process.cwd()},bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router','vue-i18n'],plugins:[{name:'customer-gate',setup(b){
  b.onLoad({filter:/useCustomerWallet\.ts$/},()=>({contents:'export function provideCustomerWallet(){return globalThis.__walletEntryGate}',loader:'js'}))
  b.onLoad({filter:/\.css$/},()=>({contents:'',loader:'js'}))
  b.onLoad({filter:/\.vue$/},async({path})=>{
   if(!/(WalletCustomerPage|WalletProductUnavailable)\.vue$/.test(path))return{contents:"import {h} from 'vue';export default {setup:(p,{slots})=>()=>h('div',slots.default?.())}",loader:'js'}
   const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}
  })
 }}]});await mkdir('target/wallet-customer-entry-gate',{recursive:true});await writeFile('target/wallet-customer-entry-gate/page.mjs',result.outputFiles[0].contents);({Page,walletZh,walletEn}=await import(pathToFileURL(process.cwd()+'/target/wallet-customer-entry-gate/page.mjs')))
})
afterEach(()=>{mounted.forEach(a=>a.unmount());mounted.length=0;delete globalThis.__walletEntryGate})
async function mount({closed=false,unavailable=false,name='wallet-customer',locale='zh-CN'}={}){
 const home=ref(unavailable?null:{context:{store:{storeName:'QA'},denialReason:closed?'STORE_APP_EXPIRED':null,transactionsEnabled:!closed},accounts:[]})
 globalThis.__walletEntryGate={store:ref('A'),home,unavailable:ref(unavailable),needsLogin:ref(false),error:ref(unavailable?'WALLET_PRODUCT_UNAVAILABLE':''),loading:ref(false),refresh(){},authenticated(){},logout(){},loggingOut:ref(false)}
 const root={children:[]},route=reactive({name,params:{storeId:'A'},query:{},hash:''}),app=renderer.createApp({render:()=>h(Page)})
 app.provide(routeLocationKey,route);app.use(createI18n({legacy:false,locale,messages:{'zh-CN':{wallet:walletZh},en:{wallet:walletEn}},missingWarn:false,fallbackWarn:false}));app.component('RouterView',{render:()=>h('div',{'data-business':true},'Wallet operational panel')});app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:p.to},slots.default?.())});app.mount(root);mounted.push(app);await tick();return {root,home,route}
}
test('never-opened wallet renders an unavailable state without mounting customer business panels',async()=>{
 const s=await mount({unavailable:true});assert.equal(nodes(s.root,n=>n.props?.['data-business']).length,0);assert.match(text(s.root),/储值钱包.*未开通|储值钱包.*不可用/)
})
test('unavailable wallet explanation is translated in English',async()=>{
 const s=await mount({unavailable:true,locale:'en'});assert.match(text(s.root),/Stored-value wallet unavailable/);assert.doesNotMatch(text(s.root),/储值钱包/)
})
test('expired wallet root offers explicit history instead of balance, points, cards or top-up panels',async()=>{
 const s=await mount({closed:true});assert.equal(nodes(s.root,n=>n.props?.['data-business']).length,0);assert.ok(nodes(s.root,n=>n.tag==='a'&&n.props.href==='/wallet/A/transactions?history=1').length)
})
test('activation restores the wallet root and closure removes its business child without reloading',async()=>{
 const s=await mount({closed:true});s.home.value.context.denialReason=null;await tick();assert.equal(nodes(s.root,n=>n.props?.['data-business']).length,1)
 s.home.value.context.denialReason='STORE_APP_NOT_ENABLED';await tick();assert.equal(nodes(s.root,n=>n.props?.['data-business']).length,0)
})
test('existing history and shared customer registration remain accessible when wallet operations are closed',async()=>{
 for(const name of ['wallet-customer-transactions','wallet-customer-register']){const s=await mount({closed:true,name});assert.equal(nodes(s.root,n=>n.props?.['data-business']).length,1)}
 const s=await mount({unavailable:true,name:'wallet-customer-register'});assert.equal(nodes(s.root,n=>n.props?.['data-business']).length,1)
})
test('shared registration returns to the originating storage page without introducing a wallet back link',async()=>{
 const s=await mount({unavailable:true,name:'wallet-customer-register'});s.route.query.returnTo='/wallet/A/wine-storage';await tick()
 assert.equal(nodes(s.root,n=>n.tag==='a'&&n.props.href==='/wallet/A/wine-storage').length,1)
 assert.equal(nodes(s.root,n=>n.tag==='a'&&n.props.href==='/wallet/A').length,0)
})
test('closing the wallet root preserves existing card and original-order subpages under their own controls',async()=>{
 for(const name of ['wallet-customer-cards','wallet-customer-order']){
  const s=await mount({closed:true,name});assert.equal(nodes(s.root,n=>n.props?.['data-business']).length,1)
 }
 const s=await mount({unavailable:true,name:'wallet-customer-cards'});assert.equal(nodes(s.root,n=>n.props?.['data-business']).length,0)
})
