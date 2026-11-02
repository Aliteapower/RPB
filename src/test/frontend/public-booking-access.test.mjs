import {test,before,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {parse,compileScript} from '@vue/compiler-sfc'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {createRenderer,h,reactive,nextTick} from 'vue'
import {routeLocationKey} from 'vue-router'

let Page,PublicBookingApiError
const mounted=[],originalWindow=globalThis.window,originalDocument=globalThis.document,originalDocumentClass=globalThis.Document,originalShadowRoot=globalThis.ShadowRoot
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>(n.text||'')+(n.children||[]).map(text).join('')
function element(tag){const n={tag,tagName:tag.toUpperCase(),props:{},children:[],style:{},listeners:{},getRootNode(){return globalThis.document},addEventListener(k,f){this.listeners[k]=f},removeEventListener(k){delete this.listeners[k]}};Object.defineProperty(n,'options',{get(){return nodes(n,x=>x.tag==='option')}});return n}
const renderer=createRenderer({createElement:element,createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},patchProp:(n,k,o,v)=>{n.props[k]=v;if(['value','checked','selected','type'].includes(k))n[k]=v},insert:(n,p,a)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{const p=n?.parent,i=p?.children.indexOf(n);if(p&&i>=0)p.children.splice(i,1)},parentNode:n=>n?.parent,nextSibling:n=>{const c=n?.parent?.children||[];return c[c.indexOf(n)+1]??null}})
const settle=async()=>{for(let i=0;i<24;i++){await Promise.resolve();await nextTick()}}
const deferred=()=>{let resolve,reject;const promise=new Promise((r,j)=>{resolve=r;reject=j});return{promise,resolve,reject}}
before(async()=>{
 const result=await build({stdin:{contents:"export {default as Page} from './src/pages/PublicBookingPage.vue';export {PublicBookingApiError} from './src/api/publicBookingApi'",resolveDir:process.cwd()},bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router'],plugins:[{name:'booking',setup(b){
  b.onLoad({filter:/publicBookingApi\.ts$/},()=>({contents:`export class PublicBookingApiError extends Error {constructor(status,response){super(response.error);this.status=status;this.response=response}}
   export const getPublicBookingContext=(store,date)=>globalThis.__bookingTest.call('context',store,date)
   export const getCustomerMe=(unused,store)=>globalThis.__bookingTest.call('me',store)
   export const resolveTenantPublicBookingEntry=()=>globalThis.__bookingTest.call('entry')
   export const createPublicBooking=(...args)=>globalThis.__bookingTest.call('POST',...args)`,loader:'js'}))
  b.onLoad({filter:/generatedText\.ts$/},()=>({contents:"export const useGeneratedText=()=>({gt:(key)=>key})",loader:'js'}))
  b.onLoad({filter:/\.vue$/},async({path})=>{
   if(!path.endsWith('PublicBookingPage.vue')){
    const kind=path.endsWith('CustomerPasswordLogin.vue')?'login':path.endsWith('CustomerPasswordChange.vue')?'password-change':'phone'
    return {contents:`import {h} from 'vue';export default {props:['store','modelValue','disabled'],emits:['authenticated','update:modelValue'],setup(p,{emit}){return()=>h('div',{'data-stub':'${kind}',value:p.modelValue,store:p.store,onAuthenticated:value=>emit('authenticated',value),'onUpdate:modelValue':value=>emit('update:modelValue',value)})}}`,loader:'js'}
   }
   const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}
  })
 }}]})
 await mkdir('target/public-booking-access',{recursive:true});await writeFile('target/public-booking-access/entry.mjs',result.outputFiles[0].contents)
 ;({Page,PublicBookingApiError}=await import(pathToFileURL(process.cwd()+'/target/public-booking-access/entry.mjs')))
})
afterEach(()=>{mounted.splice(0).forEach(a=>a.unmount());assert.ok(!(globalThis.__bookingTest?.calls||[]).some(c=>c.kind==='POST'),'read-only availability refresh must never create a booking');globalThis.window=originalWindow;globalThis.document=originalDocument;globalThis.Document=originalDocumentClass;globalThis.ShadowRoot=originalShadowRoot;delete globalThis.__bookingTest})
function browser(t){globalThis.Document=class extends EventTarget{};globalThis.ShadowRoot=class{};const win=new EventTarget(),doc=new globalThis.Document();doc.visibilityState='visible';doc.activeElement=null;globalThis.window=win;globalThis.document=doc;t.mock.timers.enable({apis:['setInterval']});return{win,doc}}
function api(handler){const calls=[];globalThis.__bookingTest={calls,call(kind,...args){calls.push({kind,args});if(kind==='POST')throw Error('Unexpected POST');if(kind==='me')return handler?.(kind,...args)??Promise.resolve({principal:null});return handler(kind,...args)}};return calls}
async function mount(store='A'){const route=reactive({params:{storeId:store}}),root=element('root'),app=renderer.createApp({render:()=>h(Page)});app.provide(routeLocationKey,route);app.mount(root);mounted.push(app);await settle();return{root,route,app}}
function context(store='A',enabled=true,login=false,date=new Date().toISOString().slice(0,10)){return{success:true,store:{storeId:store,storeName:'STORE '+store},settings:{enabled,requireCustomerLogin:login,maxAdvanceDays:30,minLeadMinutes:0},businessDate:date,timeSlots:[{periodKey:'lunch',displayName:'Lunch '+date,localTime:'12:00:00',startAt:date+'T12:00:00Z',selectable:true,nextDay:false}]}}
const dateField=s=>nodes(s.root,n=>n.tag==='input'&&n.props.type==='date')[0]
const form=s=>nodes(s.root,n=>n.tag==='form')[0]
const login=s=>nodes(s.root,n=>n.props?.['data-stub']==='login')[0]
const submit=s=>nodes(s.root,n=>n.tag==='button'&&n.props.type==='submit')[0]
const keyButton=(s,key)=>nodes(s.root,n=>n.tag==='button'&&text(n).includes('generated.public-booking.'+key))[0]
async function chooseAndNext(s){nodes(s.root,n=>n.props?.class?.includes?.('booking-time-card'))[0].props.onClick();await settle();(keyButton(s,'034')??keyButton(s,'016')).props.onClick();await settle()}

test('commercial context rejection and disabled booking expose no date, login, or submit form',async t=>{
 browser(t);api(async(kind,store)=>{if(store==='DENIED')throw new PublicBookingApiError(404,{error:'store_not_found'});return context(store,false,true)})
 for(const store of ['DENIED','DISABLED']){const s=await mount(store);assert.equal(dateField(s),undefined);assert.equal(login(s),undefined);assert.equal(form(s),undefined);assert.equal(submit(s),undefined);assert.ok(nodes(s.root,n=>n.props?.role==='alert').length)}
})

test('30 second activation restores forms and closure removes mounted login and contact steps without POST',async t=>{
 browser(t);let enabled=false;const calls=api(async(kind,store,date)=>context(store,enabled,true,date));const s=await mount();assert.equal(dateField(s),undefined)
 enabled=true;t.mock.timers.tick(30000);await settle();assert.ok(dateField(s));await chooseAndNext(s);assert.ok(login(s))
 enabled=false;t.mock.timers.tick(30000);await settle();assert.equal(login(s),undefined);assert.equal(dateField(s),undefined);assert.equal(form(s),undefined)
 enabled=true;t.mock.timers.tick(30000);await settle();assert.ok(login(s));login(s).props.onAuthenticated({customerId:'C',tenantId:'T',displayName:'Guest'});await settle();assert.ok(form(s))
 enabled=false;t.mock.timers.tick(30000);await settle();assert.equal(form(s),undefined);assert.equal(submit(s),undefined);assert.ok(calls.every(c=>c.kind!=='POST'))
})

test('background context refresh preserves contact fields and selected date without resubmitting',async t=>{
 const {win}=browser(t);const calls=api(async(kind,store,date)=>context(store,true,false,date));const s=await mount();await chooseAndNext(s);assert.ok(form(s))
 nodes(s.root,n=>n.props?.name==='customerName')[0].props['onUpdate:modelValue']('Typed guest')
 nodes(s.root,n=>n.props?.name==='customerEmail')[0].props['onUpdate:modelValue']('guest@example.test')
 nodes(s.root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('Window seat')
 const phone=nodes(s.root,n=>n.props?.['data-stub']==='phone')[0];phone.props['onUpdate:modelValue']('91234567');await settle();assert.equal(submit(s).props.disabled,false)
 t.mock.timers.tick(30000);await settle();win.dispatchEvent(new Event('focus'));await settle()
 assert.equal(nodes(s.root,n=>n.props?.name==='customerName')[0].value,'Typed guest');assert.equal(nodes(s.root,n=>n.props?.name==='customerEmail')[0].value,'guest@example.test');assert.equal(nodes(s.root,n=>n.tag==='textarea')[0].value,'Window seat');assert.equal(nodes(s.root,n=>n.props?.['data-stub']==='phone')[0].props.value,'91234567');assert.equal(submit(s).props.disabled,false)
 assert.ok(calls.filter(c=>c.kind==='context').length>=3);assert.equal(new Set(calls.filter(c=>c.kind==='context').map(c=>c.args[1])).size,1);assert.ok(calls.every(c=>c.kind!=='POST'))
})

test('late context responses from an old store or old date cannot restore stale booking availability',async t=>{
 const {win}=browser(t);const oldStore=deferred(),oldDate=deferred();let pendingStore=false,pendingDate='';const calls=api((kind,store,date)=>{if(kind==='me')return {principal:null};if(store==='A'&&pendingStore)return oldStore.promise;if(date===pendingDate)return oldDate.promise;return context(store,true,false,date)})
 const s=await mount();pendingStore=true;win.dispatchEvent(new Event('focus'));await settle();s.route.params.storeId='B';await settle();assert.match(text(s.root),/STORE B/)
 oldStore.resolve(context('STALE STORE',false));await settle();assert.match(text(s.root),/STORE B/);assert.ok(dateField(s))
 const today=dateField(s).value;pendingDate=new Date(Date.parse(today+'T00:00:00Z')+86400000).toISOString().slice(0,10);dateField(s).props['onUpdate:modelValue'](pendingDate);await settle()
 const current=new Date(Date.parse(today+'T00:00:00Z')+2*86400000).toISOString().slice(0,10);dateField(s).props['onUpdate:modelValue'](current);await settle();assert.match(text(s.root),new RegExp('Lunch '+current))
 oldDate.resolve(context('STALE DATE',false,false,pendingDate));await settle();assert.match(text(s.root),/STORE B/);assert.match(text(s.root),new RegExp('Lunch '+current));assert.ok(dateField(s));assert.ok(calls.every(c=>c.kind!=='POST'))
})

test('hidden pages pause polling, visible focus recovers and unmount removes refresh listeners',async t=>{
 const {win,doc}=browser(t);const calls=api(async(kind,store,date)=>context(store,true,false,date));const s=await mount();const count=()=>calls.filter(c=>c.kind==='context').length,start=count();doc.visibilityState='hidden';t.mock.timers.tick(60000);win.dispatchEvent(new Event('focus'));await settle();assert.equal(count(),start)
 doc.visibilityState='visible';doc.dispatchEvent(new Event('visibilitychange'));await settle();assert.equal(count(),start+1);s.app.unmount();mounted.splice(mounted.indexOf(s.app),1);t.mock.timers.tick(30000);win.dispatchEvent(new Event('focus'));await settle();assert.equal(count(),start+1);assert.ok(calls.every(c=>c.kind!=='POST'))
})

test('a later commercial rejection removes the contact form and focus recovery preserves guest input',async t=>{
 const {win}=browser(t);let denied=false;api(async(kind,store,date)=>{if(denied)throw new PublicBookingApiError(404,{error:'store_not_found'});return context(store,true,false,date)});const s=await mount();await chooseAndNext(s);nodes(s.root,n=>n.props?.name==='customerName')[0].props['onUpdate:modelValue']('Pending guest');await settle()
 denied=true;win.dispatchEvent(new Event('focus'));await settle();assert.equal(dateField(s),undefined);assert.equal(login(s),undefined);assert.equal(form(s),undefined);assert.equal(submit(s),undefined)
 denied=false;win.dispatchEvent(new Event('focus'));await settle();assert.ok(form(s));assert.equal(nodes(s.root,n=>n.props?.name==='customerName')[0].value,'Pending guest')
})

test('late old-store customer profile cannot overwrite the current stores contact identity',async t=>{
 browser(t);const old=deferred();api((kind,store,date)=>kind==='me'?(store==='A'?old.promise:{principal:{customerId:'B',tenantId:'TB',displayName:'Current B',email:'b@example.test',phoneE164:'+6592222222'}}):context(store,true,false,date));const s=await mount();s.route.params.storeId='B';await settle();await chooseAndNext(s);assert.ok(form(s));old.resolve({principal:{customerId:'A',tenantId:'TA',displayName:'STALE A',email:'a@example.test',phoneE164:'+6591111111'}});await settle()
 assert.equal(nodes(s.root,n=>n.props?.name==='customerName')[0].value,'Current B');assert.equal(nodes(s.root,n=>n.props?.name==='customerEmail')[0].value,'b@example.test');assert.equal(nodes(s.root,n=>n.props?.['data-stub']==='phone')[0].props.value,'92222222')
})
