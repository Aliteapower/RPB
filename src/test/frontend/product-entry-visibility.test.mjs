import {test,before,after,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {parse,compileScript} from '@vue/compiler-sfc'
import {build} from 'esbuild'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {createRenderer,h,nextTick,reactive} from 'vue'
import {createPinia} from 'pinia'
import {routeLocationKey,routerKey} from 'vue-router'
import {createI18n} from 'vue-i18n'
let Nav,Tools,useAuthSessionStore;const mounted=new Set(),originalFetch=globalThis.fetch
const renderer=createRenderer({createElement:tag=>({tag,children:[],props:{}}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>n.props[k]=v,insert:(n,p,a)=>{n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:()=>null})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))],tick=async()=>{for(let i=0;i<16;i++){await Promise.resolve();await nextTick()}}
const appEntry={appKey:'wine_storage',appName:'Bottle storage',status:'active',entryRoute:'/stores/A/wine-storage',entryVisible:true,permissions:['wine_storage.view']}
const response=(apps,historyApps=[])=>({ok:true,status:200,text:async()=>JSON.stringify({success:true,apps,historyApps})})
before(async()=>{
 const result=await build({stdin:{contents:"export {default as Nav} from './src/components/tenant-admin/TenantAdminNav.vue';export {default as Tools} from './src/modules/wallet/components/WalletCashierTools.vue';export {useAuthSessionStore} from './src/stores/authSession'",resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router','vue-i18n','pinia'],plugins:[{name:'entry-visibility',setup(b){
 b.onLoad({filter:/useStaffWallet\.ts$/},()=>({contents:"import {computed} from 'vue';import {useRoute} from 'vue-router';import {useAuthSessionStore} from '../../stores/authSession';export function useStaffWallet(){const route=useRoute(),auth=useAuthSessionStore();return {store:computed(()=>String(route.params.storeId)),context:computed(()=>null),can:p=>auth.user?.permissions.includes(p)===true}}",loader:'ts'}))
 b.onLoad({filter:/\.vue$/},async({path})=>{if(/(StoreSwitcher|WalletIcon|WalletCustomerManagementEntry)\.vue$/.test(path))return{contents:'export default {render(){return null}}',loader:'js'};const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}})
 } }]});await mkdir('target/product-entry-visibility',{recursive:true});await writeFile('target/product-entry-visibility/entry.mjs',result.outputFiles[0].contents);({Nav,Tools,useAuthSessionStore}=await import(pathToFileURL(process.cwd()+'/target/product-entry-visibility/entry.mjs').href+'?'+Date.now()))
})
afterEach(()=>{for(const app of mounted)app.unmount();mounted.clear()});after(()=>{globalThis.fetch=originalFetch})
function mount(component,permissions=['wine_storage.view']){const route=reactive({params:{storeId:'A'}}),root={children:[]},app=renderer.createApp({render:()=>h(component)}),pinia=createPinia();app.use(pinia);useAuthSessionStore(pinia).user={username:'QA',roles:['tenant_admin'],permissions,storeIds:['A','B'],defaultStoreId:'A'};app.use(createI18n({legacy:false,locale:'zh-CN',missingWarn:false,fallbackWarn:false,messages:{'zh-CN':{}}}));app.provide(routeLocationKey,route);app.provide(routerKey,{push:async()=>{}});app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:p.to},slots.default?.())});app.mount(root);mounted.add(app);return{root,route,app}}
const wineLinks=root=>nodes(root,n=>n.tag==='a'&&String(n.props.href).endsWith('/wine-storage'))
test('permissions alone never show an unprovisioned storage link in either staff surface',async()=>{globalThis.fetch=async()=>response([]);for(const component of [Nav,Tools]){const s=mount(component);assert.equal(wineLinks(s.root).length,0);await tick();assert.equal(wineLinks(s.root).length,0)}})
test('both staff surfaces require a visible AppGate entry and the employee view permission',async()=>{for(const component of [Nav,Tools]){globalThis.fetch=async()=>response([appEntry]);const shown=mount(component);await tick();assert.equal(wineLinks(shown.root).length,1);const denied=mount(component,[]);await tick();assert.equal(wineLinks(denied.root).length,0);globalThis.fetch=async()=>response([{...appEntry,entryVisible:false}]);const hidden=mount(component);await tick();assert.equal(wineLinks(hidden.root).length,0)}})
test('loading and failed visibility reads never reveal a storage link',async()=>{for(const component of [Nav,Tools]){let reject;globalThis.fetch=()=>new Promise((_,no)=>reject=no);const s=mount(component);await tick();assert.equal(wineLinks(s.root).length,0);assert.equal(nodes(s.root,n=>n.tag==='a'&&/admin\/(payment|tables|settings)/.test(String(n.props.href))).length,0);reject(Error('offline'));await tick();assert.equal(wineLinks(s.root).length,0)}})
test('late previous-store entries cannot leak into the new store menu',async()=>{for(const component of [Nav,Tools]){const requests=[];globalThis.fetch=url=>new Promise(resolve=>requests.push({url,resolve}));const s=mount(component);await tick();s.route.params.storeId='B';await tick();assert.equal(wineLinks(s.root).length,0);requests[0].resolve(response([appEntry]));await tick();assert.equal(wineLinks(s.root).length,0);assert.equal(requests.length,2);requests[1].resolve(response([]));await tick();assert.equal(wineLinks(s.root).length,0)}})


test('closed products appear only in separate history links and stale history clears when switching stores',async()=>{
 const history={...appEntry,status:'history',entryRoute:'/stores/A/wine-storage?task=records&history=1'}
 globalThis.fetch=async()=>response([],[history]);const s=mount(Nav);await tick();
 assert.equal(wineLinks(s.root).length,0)
 assert.equal(nodes(s.root,n=>n.tag==='a'&&String(n.props.href).includes('history=1')).length,1)
 let resolve;globalThis.fetch=()=>new Promise(done=>resolve=done);s.route.params.storeId='B';await tick();
 assert.equal(nodes(s.root,n=>n.tag==='a'&&String(n.props.href).includes('history=1')).length,0)
 resolve(response([]));await tick()
 assert.equal(nodes(s.root,n=>n.tag==='a'&&String(n.props.href).includes('history=1')).length,0)
})

test('malformed history results fail closed rather than leaving prior operational entries',async()=>{
 globalThis.fetch=async()=>({ok:true,status:200,text:async()=>JSON.stringify({success:true,apps:[appEntry],historyApps:{wrong:true}})})
 const s=mount(Nav);await tick();assert.equal(wineLinks(s.root).length,0)
})

const workforceEntry={...appEntry,appKey:'workforce',appName:'员工考勤',entryRoute:'/stores/A/workforce',permissions:['workforce.view']}
const workforceLinks=root=>nodes(root,n=>n.tag==='a'&&String(n.props.href).endsWith('/workforce'))
function browser(t){
 const oldWindow=globalThis.window,oldDocument=globalThis.document
 const win=new EventTarget(),doc=new EventTarget();doc.visibilityState='visible'
 globalThis.window=win;globalThis.document=doc
 t.after(()=>{for(const app of mounted)app.unmount();mounted.clear();if(oldWindow===undefined)delete globalThis.window;else globalThis.window=oldWindow;if(oldDocument===undefined)delete globalThis.document;else globalThis.document=oldDocument})
 return {win,doc}
}
test('tenant navigation includes authorized catalog products and preserves existing grouped product menus',async()=>{
 const catalog=JSON.parse(await readFile('src/main/resources/product-line-entries.json','utf8')).filter(p=>p.appKey)
 const entries=catalog.map(p=>({...appEntry,appKey:p.appKey,entryRoute:p.value.replace(':storeId','A'),permissions:p.permissions}))
 globalThis.fetch=async()=>response(entries)
 const s=mount(Nav,['wine_storage.view','workforce.view']);await tick()
 for(const entry of entries)assert.equal(nodes(s.root,n=>n.tag==='a'&&n.props.href===entry.entryRoute).length,['payment','reservation_queue'].includes(entry.appKey)?0:1,entry.appKey)
 assert.equal(nodes(s.root,n=>n.tag==='section'&&n.props.class==='nav-group').length,2)
})
test('same-store subscription activation and expiry refresh operational and historical links automatically',async t=>{
 browser(t);t.mock.timers.enable({apis:['setInterval']})
 let apps=[],history=[];globalThis.fetch=async()=>response(apps,history)
 const s=mount(Nav,['workforce.view']);await tick();assert.equal(workforceLinks(s.root).length,0)
 apps=[workforceEntry];t.mock.timers.tick(30000);await tick();assert.equal(workforceLinks(s.root).length,1)
 apps=[];history=[{...appEntry,status:'history',entryRoute:'/stores/A/wine-storage?history=1'}]
 t.mock.timers.tick(30000);await tick();assert.equal(workforceLinks(s.root).length,0)
 assert.equal(nodes(s.root,n=>n.tag==='a'&&String(n.props.href).includes('history=1')).length,1)
})
test('focus and returning to a visible page refresh access, skip hidden polling and clean up on unmount',async t=>{
 const {win,doc}=browser(t);t.mock.timers.enable({apis:['setInterval']})
 let calls=0;globalThis.fetch=async()=>{calls++;return response([workforceEntry])}
 const s=mount(Nav,['workforce.view']);await tick();assert.equal(calls,1)
 doc.visibilityState='hidden';t.mock.timers.tick(60000);await tick();assert.equal(calls,1)
 doc.visibilityState='visible';doc.dispatchEvent(new Event('visibilitychange'));await tick();assert.equal(calls,2)
 win.dispatchEvent(new Event('focus'));await tick();assert.equal(calls,3)
 s.app.unmount();mounted.delete(s.app);win.dispatchEvent(new Event('focus'));t.mock.timers.tick(60000);await tick();assert.equal(calls,3)
})
test('automatic refresh fails closed, recovers on focus and never overlaps a pending same-store read',async t=>{
 const {win}=browser(t);t.mock.timers.enable({apis:['setInterval']})
 globalThis.fetch=async()=>response([workforceEntry]);const s=mount(Nav,['workforce.view']);await tick();assert.equal(workforceLinks(s.root).length,1)
 let calls=0,reject;globalThis.fetch=()=>{calls++;return new Promise((_,no)=>reject=no)}
 t.mock.timers.tick(30000);await tick();win.dispatchEvent(new Event('focus'));t.mock.timers.tick(60000);await tick();assert.equal(calls,1)
 reject(Error('offline'));await tick();assert.equal(workforceLinks(s.root).length,0)
 globalThis.fetch=async()=>response([workforceEntry]);win.dispatchEvent(new Event('focus'));await tick();assert.equal(workforceLinks(s.root).length,1)
})
test('navigation uses the authorized configured route and bypasses the browser HTTP cache',async()=>{
 globalThis.fetch=async(_url,options)=>{assert.equal(options.cache,'no-store');return response([{...workforceEntry,entryRoute:'/stores/A/workforce/management'}])}
 const s=mount(Nav,['workforce.view']);await tick()
 assert.equal(nodes(s.root,n=>n.tag==='a'&&n.props.href==='/stores/A/workforce/management').length,1)
 globalThis.fetch=async()=>response([{...workforceEntry,entryVisible:false}]);s.route.params.storeId='B';await tick()
 assert.equal(nodes(s.root,n=>n.tag==='a'&&String(n.props.href).includes('/workforce')).length,0)
})
test('an automatic refresh from the previous store never overwrites the current store entries',async t=>{
 browser(t);t.mock.timers.enable({apis:['setInterval']})
 globalThis.fetch=async()=>response([workforceEntry]);const s=mount(Nav,['workforce.view']);await tick()
 let oldResolve;globalThis.fetch=()=>new Promise(done=>oldResolve=done);t.mock.timers.tick(30000);await tick()
 globalThis.fetch=async()=>response([]);s.route.params.storeId='B';await tick()
 oldResolve(response([workforceEntry]));await tick();assert.equal(workforceLinks(s.root).length,0)
})
