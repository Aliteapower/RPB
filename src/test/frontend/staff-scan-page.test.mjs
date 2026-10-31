import {test,before,beforeEach,after,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {parse,compileScript} from '@vue/compiler-sfc'
import {readFile,mkdir,writeFile} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {createRenderer,h,nextTick,reactive} from 'vue'
import {createPinia} from 'pinia'
import {createI18n} from 'vue-i18n'
import {routeLocationKey,routerKey} from 'vue-router'
let Page,Entry,Recharge,CardOperations,provideStaffWallet,useAuthSessionStore,takeScanHandoff,setScanHandoff
const original={fetch:globalThis.fetch,window:globalThis.window,sessionStorage:globalThis.sessionStorage,document:globalThis.document,Document:globalThis.Document,ShadowRoot:globalThis.ShadowRoot},mounted=new Set()
const store='20000000-0000-0000-0000-000000000983',code=p=>p+'.'+'B'.repeat(43),permissions=['wallet.view','wallet.charge.create','wallet.collection.confirm','wallet.topup.create','wallet.cards.view','wallet.cards.redeem','wine_storage.view','wine_storage.receive','wine_storage.deliver']
const renderer=createRenderer({createElement:tag=>({tag,tagName:tag.toUpperCase(),children:[],props:{},value:'',get options(){return this.children.filter(c=>c.tag==='option')},listeners:{},addEventListener(name,fn){this.listeners[name]=fn},removeEventListener(){},getRootNode(){return globalThis.document}}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>{n.props[k]=v;if(k==='value'){n._value=v;n.value=v}},insert:(n,p,a)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:n=>{const c=n.parent?.children||[];return c[c.indexOf(n)+1]||null}})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))],text=n=>[n.text||'',...(n.children||[]).map(text)].join(' ')
const tick=async()=>{for(let i=0;i<24;i++){await Promise.resolve();await nextTick()}}
const button=(s,label)=>nodes(s.root,n=>n.tag==='button'&&text(n).trim()===label)[0]
before(async()=>{
 const result=await build({stdin:{contents:"export {default as Recharge} from './src/modules/wallet/pages/WalletStaffRecharge.vue';export {default as CardOperations} from './src/modules/cards/CardOperations.vue';export {provideStaffWallet} from './src/modules/wallet/useStaffWallet';export {setScanHandoff} from './src/modules/staff-scan/handoff';export {default as Page} from './src/modules/staff-scan/StaffScanPage.vue';export {default as Entry} from './src/modules/staff-scan/StaffScanEntry.vue';export {useAuthSessionStore} from './src/stores/authSession';export {takeScanHandoff} from './src/modules/staff-scan/handoff'",resolveDir:process.cwd()},bundle:true,write:false,format:'esm',platform:'node',external:['vue','pinia','vue-i18n','vue-router'],plugins:[{name:'scan-page',setup(b){b.onLoad({filter:/\.css$/},()=>({contents:'',loader:'js'}));b.onLoad({filter:/\.vue$/},async({path})=>{
  if(path.endsWith('WalletQrScanner.vue'))return{contents:"import {h} from 'vue';export default {props:['purpose','disabled'],emits:['code'],setup:(p,{emit})=>()=>h('button',{'data-scanner':p.purpose,disabled:p.disabled,onClick:()=>emit('code',globalThis.staffScanTestCode)},'scan-test')}",loader:'js'}
  if(path.endsWith('WalletAmountKeypad.vue'))return{contents:"import {h} from 'vue';export default {props:['modelValue','disabled'],emits:['update:modelValue'],setup:(p,{emit})=>()=>h('input',{'data-amount':true,disabled:p.disabled,onInput:e=>emit('update:modelValue',e.target.value)})}",loader:'js'}
  if(path.endsWith('WalletLocaleControl.vue')||path.endsWith('WalletIcon.vue')||path.endsWith('WalletQrDisplay.vue'))return{contents:'export default {render(){return null}}',loader:'js'}
  const{descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}
 })}}]});await mkdir('target/staff-scan-page-test',{recursive:true});await writeFile('target/staff-scan-page-test/entry.mjs',result.outputFiles[0].contents)
 ;({Page,Entry,Recharge,CardOperations,provideStaffWallet,useAuthSessionStore,takeScanHandoff,setScanHandoff}=await import(pathToFileURL(process.cwd()+'/target/staff-scan-page-test/entry.mjs').href+'?'+Date.now()))
})
beforeEach(()=>{const memory=new Map();globalThis.sessionStorage={getItem:k=>memory.get(k)??null,setItem:(k,v)=>memory.set(k,v),removeItem:k=>memory.delete(k)};globalThis.window={location:{origin:'https://booking.test'},setInterval:()=>0,clearInterval(){}};globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document=Object.assign(new Document(),{activeElement:null,visibilityState:'visible',addEventListener(){},removeEventListener(){}})})
afterEach(()=>{for(const app of mounted)app.unmount();mounted.clear();delete globalThis.staffScanTestCode})
after(()=>{Object.assign(globalThis,original)})
function mount(respond,perms=permissions,locale='zh-CN',component=Page,props={}){
 const root={children:[]},calls=[],navigation=[],route=reactive({name:component===Recharge?'wallet-topup':'staff-scan',params:{storeId:store},query:{},hash:''})
 globalThis.fetch=async(url,options)=>{calls.push({url,method:options?.method??'GET',body:options?.body?JSON.parse(options.body):undefined});const data=await respond(url,options);return{ok:data?.httpStatus?false:true,status:data?.httpStatus??200,json:async()=>data}}
 const pinia=createPinia(),auth=useAuthSessionStore(pinia);auth.user={accountId:'cashier',roles:[],storeIds:[store],permissions:perms}
 let workspace;const app=renderer.createApp({setup(){if(component===Recharge)workspace=provideStaffWallet();return()=>h(component,{store,...props})}});app.use(pinia);app.use(createI18n({legacy:false,locale,messages:{}}));app.provide(routeLocationKey,route);app.provide(routerKey,{push:async target=>navigation.push(target)});app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:p.to},slots.default?.())});app.mount(root);mounted.add(app)
 return{root,calls,navigation,route,auth,get workspace(){return workspace},async scan(value){globalThis.staffScanTestCode=value;nodes(root,n=>n.props?.['data-scanner'])[0].props.onClick();await tick()},unmount(){app.unmount();mounted.delete(app)}}
}
test('one merchant scanner redeems an authorised card once and shows actual quantity',async()=>{
 let resolve;const s=mount(()=>new Promise(r=>resolve=r));await tick();assert.equal(nodes(s.root,n=>n.props?.['data-scanner']==='staff').length,1)
 await s.scan(code('CA'));await s.scan(code('CA'));assert.equal(s.calls.length,1);assert.match(s.calls[0].url,/cards\/authorized-redeem$/)
 resolve({id:'card-request',kind:'redeem',status:'completed',quantity:3});await tick();assert.match(text(s.root),/核销完成/);assert.match(text(s.root),/数量:.*3/);assert.match(text(s.root),/卡核销/);assert.doesNotMatch(text(s.root),/取酒核销/);assert.equal(nodes(s.root,n=>n.props?.['data-scanner']).length,0)
 button(s,'继续扫一扫').props.onClick();await tick();assert.equal(nodes(s.root,n=>n.props?.['data-scanner']).length,1)
})
test('storage scan deducts only the authorised selection and server denial stays an error',async()=>{
 const s=mount(()=>({status:'completed',selections:[{itemId:'item',quantity:4}]}));await s.scan(code('WS'));assert.match(s.calls[0].url,/wine-storage\/scan-deliver$/);assert.deepEqual(Object.keys(s.calls[0].body).sort(),['code','idempotencyKey']);assert.match(text(s.root),/取酒核销/);assert.match(text(s.root),/数量:.*4/)
 s.unmount();const denied=mount(()=>({httpStatus:409,code:'STORAGE_CONFLICT',message:'已使用或过期'}));await denied.scan(code('WS'));assert.match(text(denied.root),/已使用或过期/);assert.doesNotMatch(text(denied.root),/核销完成/)
})
test('customer is resolved once and handoff carries no credential in URL',async()=>{
 const s=mount(()=>({id:'customer',displayName:'顾客甲',phone:'80000000'}));await s.scan(code('WI'));assert.equal(s.calls.length,1);assert.match(text(s.root),/顾客甲/)
 await button(s,'充值 / 积分').props.onClick();await tick();assert.equal(s.navigation[0],`/stores/${store}/wallet/topup`);assert.equal(takeScanHandoff(store,'cashier','recharge').code,code('WI'));assert.equal(takeScanHandoff(store,'cashier','recharge'),null)
})
test('consumption needs an amount and recovers response loss without a second debit',async()=>{
 const s=mount((url,options)=>{
  if(url.endsWith('/customer-codes/resolve'))return{id:'customer',displayName:'顾客甲'}
  if(url.endsWith('/context'))return{transactionsEnabled:true,program:{id:'program'}}
  if(url.endsWith('/personal-scan-charges'))throw Error('connection lost')
  if(url.includes('/personal-scan-charges/result'))return{status:'confirmation_required',bill:{id:'original'}}
  throw Error(url)
 });await s.scan(code('WI'));button(s,'消费收款').props.onClick();await tick()
 assert.equal(button(s,'确认消费').props.disabled,true);assert.equal(s.calls.filter(c=>c.url.endsWith('/personal-scan-charges')).length,0)
 nodes(s.root,n=>n.props?.['data-amount'])[0].props.onInput({target:{value:'12.50'}});await tick();assert.equal(button(s,'确认消费').props.disabled,false)
 const form=nodes(s.root,n=>n.tag==='form')[0];await form.props.onSubmit({preventDefault(){}});await tick();assert.match(text(s.root),/结果未确定/);assert.equal(button(s,'继续扫一扫').props.disabled,true)
 await form.props.onSubmit({preventDefault(){}});await tick();assert.equal(s.calls.filter(c=>c.url.endsWith('/personal-scan-charges')).length,1);assert.equal(s.navigation.at(-1),`/stores/${store}/wallet/bills/original`)
 assert.ok(!String(sessionStorage.getItem('rpb.wallet.personal-attempt.'+store)).includes(code('WI')))
})
test('switching actors or stores discards late results and no-permission screens cannot transact',async()=>{
 let resolve;const s=mount(()=>new Promise(r=>resolve=r));await s.scan(code('WI'));s.auth.user={...s.auth.user,accountId:'other'};await tick();resolve({id:'private',displayName:'不应显示'});await tick();assert.doesNotMatch(text(s.root),/不应显示/);assert.equal(nodes(s.root,n=>n.props?.['data-scanner']).length,1)
 s.unmount();const denied=mount(()=>{throw Error('should not fetch')},[]);await tick();assert.match(text(denied.root),/暂无可用/);assert.equal(nodes(denied.root,n=>n.props?.['data-scanner']).length,0);assert.equal(denied.calls.length,0)
})
test('a known not-submitted payment is explained and never automatically charged again',async()=>{
 const s=mount(url=>{
  if(url.endsWith('/customer-codes/resolve'))return{id:'customer'}
  if(url.endsWith('/context'))return{transactionsEnabled:true,program:{id:'program'}}
  if(url.endsWith('/personal-scan-charges'))throw Error('connection lost')
  return{status:'not_submitted',bill:null,account:null,storeId:store}
 });await s.scan(code('WI'));button(s,'消费收款').props.onClick();await tick();nodes(s.root,n=>n.props?.['data-amount'])[0].props.onInput({target:{value:'5'}});await tick()
 const form=nodes(s.root,n=>n.tag==='form')[0];await form.props.onSubmit({preventDefault(){}});await tick();await form.props.onSubmit({preventDefault(){}});await tick()
 assert.match(text(s.root),/未提交成功/);assert.equal(s.calls.filter(c=>c.url.endsWith('/personal-scan-charges')).length,1);assert.equal(s.navigation.length,0);assert.equal(button(s,'确认消费').props.disabled,false)
})
test('unsupported and cross-store URLs cannot navigate, while English entry uses the shared route',async()=>{
 const s=mount(()=>{throw Error('should not fetch')});await s.scan('https://evil.test');assert.equal(s.calls.length,0);assert.equal(s.navigation.length,0);assert.match(text(s.root),/请扫描本店/)
 s.unmount();const entry=mount(()=>({}),permissions,'en',Entry);await tick();assert.match(text(entry.root),/Scan/);assert.equal(nodes(entry.root,n=>n.tag==='a')[0].props.href,`/stores/${store}/scan`)
})
test('uncertain pickup blocks another scan and only the original cashier can recover it',async()=>{
 let calls=0;const s=mount(()=>{if(++calls===1)throw Error('response lost');return{status:'completed',selections:[{quantity:2}]}})
 await s.scan(code('WS'));assert.match(text(s.root),/结果未确定/);assert.equal(nodes(s.root,n=>n.props?.['data-scanner']).length,0)
 s.auth.user={...s.auth.user,accountId:'other'};await tick();assert.equal(button(s,'查询上次操作结果').props.disabled,true)
 s.auth.user={...s.auth.user,accountId:'cashier'};await tick();assert.equal(button(s,'查询上次操作结果').props.disabled,false)
 await button(s,'查询上次操作结果').props.onClick();await tick();assert.match(text(s.root),/核销完成/);assert.equal(calls,2)
})

 test('recharge consumes a scoped identity handoff without asking for another scan',async()=>{
 setScanHandoff({store,actor:'cashier',target:'recharge',code:code('WI')})
 const s=mount(url=>url.endsWith('/customer-codes/resolve')?{id:'preselected',displayName:'交接顾客'}:{store:{storeId:store,timezone:'Asia/Singapore'},transactionsEnabled:true,program:{id:'program'}},permissions,'zh-CN',Recharge);await tick()
 assert.equal(s.workspace.customer.value.id,'preselected');assert.equal(s.workspace.identifiedCustomerId.value,'preselected');assert.equal(s.calls.filter(c=>c.url.endsWith('/customer-codes/resolve')).length,1)
 assert.equal(nodes(s.root,n=>n.props?.['data-scanner']).length,0)
 })
 test('card operations consume the original identity and load only that customer holdings',async()=>{
 setScanHandoff({store,actor:'cashier',target:'cards',code:code('WI')})
 const s=mount(url=>url.endsWith('/identity')?{id:'card-owner',displayName:'卡顾客',phone:'80000000'}:[],permissions,'zh-CN',CardOperations,{plans:[],holdings:[],disabled:false,can:()=>true});await tick()
 assert.equal(s.calls.filter(c=>c.url.endsWith('/identity')).length,1);assert.ok(s.calls.some(c=>c.url.includes('/holdings?customerId=card-owner')));assert.match(text(s.root),/卡顾客/)
 assert.ok(s.calls.every(c=>c.url.endsWith('/identity')||c.url.includes('/holdings?customerId=card-owner')))
 })
