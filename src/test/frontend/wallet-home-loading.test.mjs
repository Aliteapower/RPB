import {test,before,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {parse,compileScript} from '@vue/compiler-sfc'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {createRenderer,h,ref,nextTick} from 'vue'
import {createI18n} from 'vue-i18n'
import {createRouter,createMemoryHistory} from 'vue-router'
let Home,recoveryLocales
const mounted=[],renderer=createRenderer({createElement:tag=>({tag,props:{},children:[]}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>n.props[k]=v,insert:(n,p)=>{n.parent=p;p.children.push(n)},remove:n=>{if(n.parent)n.parent.children=n.parent.children.filter(c=>c!==n)},parentNode:n=>n.parent,nextSibling:()=>null})
const text=n=>(n.text||'')+(n.children||[]).map(text).join(''),nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const tick=async()=>{for(let i=0;i<25;i++){await Promise.resolve();await nextTick()}}
before(async()=>{
 const b=await build({stdin:{contents:"export {default} from './src/modules/wallet/pages/WalletStaffHome.vue';export {walletZh,walletEn} from './src/modules/wallet/locales.ts'",resolveDir:process.cwd()},bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router','vue-i18n'],plugins:[{name:'home',setup(b){
 b.onLoad({filter:/useStaffWallet\.ts$/},()=>({contents:'export function useStaffWallet(){return globalThis.__walletHomeWorkspace}',loader:'js'}))
 b.onLoad({filter:/\.vue$/},async({path})=>{const {descriptor,errors}=parse(await readFile(path,'utf8'),{filename:path});assert.deepEqual(errors,[]);return{contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}})
 }}]});await mkdir('target',{recursive:true});await writeFile('target/wallet-home-loading-test.mjs',b.outputFiles[0].contents);recoveryLocales=await import(pathToFileURL(process.cwd()+'/target/wallet-home-loading-test.mjs'));Home=recoveryLocales.default
})
afterEach(()=>{mounted.forEach(app=>app.unmount());mounted.length=0;delete globalThis.__walletHomeWorkspace})
async function mount(guard,permissions=['wallet.charge.create','wallet.collection.confirm','wallet.view','wallet.topup.create','wallet.points.view'],initialContext={program:{},transactionsEnabled:true},history=false){
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/stores/:storeId/wallet',name:'wallet-workbench',component:Home},{path:'/stores/:storeId/wallet/collect',name:'wallet-collect',component:{}},{path:'/stores/:storeId/wallet/topup',name:'wallet-topup',component:{}},{path:'/stores/:storeId/wallet/settings',name:'wallet-settings',component:{}},{path:'/stores/:storeId/wallet/transactions',name:'wallet-staff-transactions',component:{}},{path:'/other',name:'other',component:{}}]});await router.push('/stores/A/wallet'+(history?'?history=1':''));await router.isReady();if(guard)router.beforeEach(guard)
 const context=ref(initialContext),access=ref(permissions);globalThis.__walletHomeWorkspace={store:ref('A'),context,can:p=>access.value.includes(p)}
 router.onError(()=>{});const root={children:[]},app=renderer.createApp({render:()=>h(Home)});app.use(router);app.use(createI18n({legacy:false,locale:'en-SG',messages:{'en-SG':{wallet:{loading:'Loading',noRights:'No rights',navigationFailed:'Unable to open cashier. Retry.',navigationRetry:'Retry'}}}}));app.mount(root);mounted.push(app);await tick();return{router,context,root,app,access}
}
test('five-second context polling cannot repeatedly cancel a slow cashier navigation',async()=>{
 const guards=[];const s=await mount(to=>to.name==='wallet-collect'?new Promise(resolve=>guards.push(resolve)):true)
 try{assert.equal(guards.length,1);for(let i=0;i<3;i++){s.context.value={program:{},transactionsEnabled:true};s.access.value=[...s.access.value];await tick()};assert.equal(guards.length,1,'same destination must keep its original pending navigation')}
 finally{guards.forEach(resolve=>resolve(true));await tick()}
 assert.equal(s.router.currentRoute.value.name,'wallet-collect')
})
test('aborted navigation stops loading and permits explicit retry without context polling restarting it',async()=>{
 let allow=false,calls=0;const s=await mount(()=>{calls++;return allow});assert.match(text(s.root),/Unable to open cashier/);assert.doesNotMatch(text(s.root),/Loading/)
 s.context.value={program:{},transactionsEnabled:true};await tick();assert.equal(calls,1)
 allow=true;await nodes(s.root,n=>n.tag==='button'&&text(n)==='Retry')[0].props.onClick();await tick();assert.equal(s.router.currentRoute.value.name,'wallet-collect');assert.equal(calls,2)
})
test('rejected lazy navigation is handled with retry, and a manual tab can leave while opening',async()=>{
 let reject=true;const s=await mount(()=>{if(reject)throw Error('chunk unavailable');return true});s.router.onError(()=>{});assert.match(text(s.root),/Unable to open cashier/)
 reject=false;await s.router.push('/stores/A/wallet/topup?kind=money');await tick();s.context.value={program:{},transactionsEnabled:true};await tick();assert.equal(s.router.currentRoute.value.name,'wallet-topup')
})
test('no-permission and history-only states do not attempt an unusable cashier route',async()=>{
 let calls=0;const s=await mount(()=>{calls++;return true},[]);assert.equal(calls,0);assert.match(text(s.root),/No rights/)
 const history=await mount(()=>{calls++;return true},['wallet.view','wallet.topup.create'],{program:{},transactionsEnabled:false});assert.equal(calls,0);assert.match(text(history.root),/No rights/)
})
test('manual cashier tabs can supersede a slow automatic navigation without being sent back',async()=>{
 let finish;const s=await mount(to=>to.name==='wallet-collect'?new Promise(resolve=>finish=resolve):true)
 await s.router.push('/stores/A/wallet/topup?kind=money');await tick();finish(true);await tick();s.context.value={program:{},transactionsEnabled:true};s.access.value=[...s.access.value];await tick();assert.equal(s.router.currentRoute.value.name,'wallet-topup');assert.equal(s.router.currentRoute.value.query.kind,'money')
})
test('production recovery messages are available in Chinese and English',()=>{
 assert.match(recoveryLocales.walletZh.navigationFailed,/重试/);assert.equal(recoveryLocales.walletZh.navigationRetry,'重试');assert.match(recoveryLocales.walletEn.navigationFailed,/Unable to open/);assert.equal(recoveryLocales.walletEn.navigationRetry,'Retry');assert.doesNotMatch(recoveryLocales.walletEn.navigationFailed,/[\u4e00-\u9fff]/)
})
test('a hung navigation shows recovery after fifteen seconds and a retry can supersede it',async(t)=>{
 t.mock.timers.enable({apis:['setTimeout']});const guards=[];const s=await mount(()=>new Promise(resolve=>guards.push(resolve)))
 assert.match(text(s.root),/Loading/);t.mock.timers.tick(15000);await tick();assert.match(text(s.root),/Unable to open cashier/)
 const retry=nodes(s.root,n=>n.tag==='button'&&text(n)==='Retry')[0];retry.props.onClick();await tick();assert.equal(guards.length,2)
 guards[1](true);guards[0](true);await tick();assert.equal(s.router.currentRoute.value.name,'wallet-collect')
})

test('newly purchased wallet without a program routes an administrator to setup rather than disabled points',async()=>{
 const s=await mount(undefined,['wallet.view','wallet.settings.manage','wallet.points.view'],{program:null,transactionsEnabled:false,denialReason:null});assert.equal(s.router.currentRoute.value.name,'wallet-settings')
})

test('explicit history mode never redirects a new-wallet administrator into writable setup',async()=>{
 const s=await mount(undefined,['wallet.view','wallet.settings.manage','wallet.points.view'],{program:null,transactionsEnabled:false,denialReason:null},true);assert.equal(s.router.currentRoute.value.name,'wallet-staff-transactions');assert.equal(s.router.currentRoute.value.query.history,'1')
})
