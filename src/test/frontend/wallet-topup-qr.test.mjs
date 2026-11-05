import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, h, nextTick, reactive } from 'vue'
import { createPinia } from 'pinia'
import { routeLocationKey, routerKey } from 'vue-router'
import { createI18n } from 'vue-i18n'
let CodePage, TopupPage, messages, customerModule, staffModule, authModule
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

function mount(component,kind,route) {
 const root={children:[]},i18n=createI18n({legacy:false,locale:'zh-CN',messages:{'zh-CN':{wallet:messages.walletZh}}}),pinia=createPinia()
 authModule.useAuthSessionStore(pinia).user={permissions:['wallet.view','wallet.topup.create']}
 const app=renderer.createApp({setup(){if(kind==='customer')customerModule.provideCustomerWallet();else staffModule.provideStaffWallet();return()=>h(component)}})
 app.use(pinia);app.use(i18n);app.provide(routeLocationKey,route);app.provide(routerKey,{push:async()=>{},replace:async()=>{}})
 app.component('RouterLink',{props:['to'],setup:props=>()=>h('a',{href:props.to})});app.mount(root)
 return{root,unmount:()=>app.unmount()}
}
const response=body=>({ok:true,status:200,json:async()=>body})
const allText=root=>[root.text||'',...(root.children||[]).map(allText)].join(' ')
before(async()=>{
 globalThis.Document=class extends EventTarget {};globalThis.ShadowRoot=class {};globalThis.document=Object.assign(new Document(),{activeElement:null,visibilityState:'visible'})
 globalThis.window=Object.assign(new EventTarget(),{setInterval:()=>1,clearInterval:()=>{}})
 const {build}=await import('esbuild'),{mkdir,writeFile,readFile}=await import('node:fs/promises'),{pathToFileURL}=await import('node:url')
 const source=`export {default as CodePage} from './src/modules/wallet/pages/WalletCustomerCode.vue';export {default as TopupPage} from './src/modules/wallet/pages/WalletStaffTopup.vue';export * as customerModule from './src/modules/wallet/useCustomerWallet.ts';export * as staffModule from './src/modules/wallet/useStaffWallet.ts';export * as authModule from './src/stores/authSession.ts';export * as messages from './src/modules/wallet/locales.ts'`
 const result=await build({stdin:{contents:source,loader:'ts',resolveDir:process.cwd()},bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-i18n','vue-router','pinia'],plugins:[{name:'real-vue-components',setup(build){build.onLoad({filter:/\.vue$/},async({path})=>{
  if(path.endsWith('WalletQrDisplay.vue'))return {contents:`import {h} from 'vue';export default {props:['value'],setup:p=>()=>h('figure',{'data-code':p.value})}`,loader:'js'}
  if(path.endsWith('WalletQrScanner.vue'))return {contents:`import {h} from 'vue';export default {emits:['code'],setup:(p,{emit})=>()=>h('button',{'data-scan':true,onClick:()=>emit('code','WP.'+'A'.repeat(43))},'camera stub')}`,loader:'js'}
  const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return {contents:compileScript(descriptor,{id:'test-'+path,inlineTemplate:true}).content,loader:'ts'}
 })}}]})
 const dir=process.cwd()+'/target/wallet-topup-component-tests';await mkdir(dir,{recursive:true});await writeFile(dir+'/entry.mjs',result.outputFiles[0].contents)
 ;({CodePage,TopupPage,customerModule,staffModule,authModule,messages}=await import(pathToFileURL(dir+'/entry.mjs').href+'?'+Date.now()))
})
after(async()=>{globalThis.fetch=originalFetch;globalThis.window=originalWindow;globalThis.document=originalDocument;globalThis.Document=originalDocumentClass;globalThis.ShadowRoot=originalShadowRoot})
test('old topup query and default route both show unified personal code without account or payment authorization',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{const body=options?.body&&JSON.parse(options.body);calls.push({url,body});return response(url.endsWith('/me')?{context:{transactionsEnabled:true,store:{operatingEntityName:'Issuer'}},accounts:[],pendingBills:[]}:url.endsWith('/personal-payment-consent')?{configured:false,paymentEnabled:false,limitCents:null,policyVersion:0}:{type:'customer_personal',code:'WI.identity',expiresAt:'2099-01-01T00:00:00Z'})}
 const route=reactive({params:{storeId:'A'},query:{purpose:'topup'},hash:''}),view=mount(CodePage,'customer',route);await tick()
 assert.equal(nodes(view.root,n=>n.tag==='figure')[0].props['data-code'],'WI.identity');assert.match(allText(view.root),/此码可用于钱包付款、充值及积分办理，请当面出示。/);assert.match(allText(view.root),/尚未设置/)
 assert.equal(calls.filter(c=>c.body).length,1);assert.ok(!calls.some(c=>c.url.endsWith('/payment-codes')||c.url.endsWith('/accounts')))
 route.query={};await tick();assert.equal(nodes(view.root,n=>n.tag==='figure')[0].props['data-code'],'WI.identity');assert.equal(calls.filter(c=>c.body).length,1);view.unmount()
})

test('merchant topup page scans identity, shows customer and enters amount before creating any topup',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,body:options?.body&&JSON.parse(options.body)});return response(url.endsWith('/topup-offers')?{campaignId:null,offers:[],channels:['cash'],deductionPolicy:'proportional',selfServiceEnabled:true}:url.endsWith('/context')?{transactionsEnabled:true,program:{channels:['cash']},store:{timezone:'Asia/Singapore'}}:{account:{id:'wallet',balanceCents:1000,reservedCents:0},customer:{id:'guest',displayName:'Test Guest',phone:'+6591234567'}})}
 const view=mount(TopupPage,'staff',reactive({params:{storeId:'A'},query:{scan:'1'},hash:''}));await tick()
 nodes(view.root,n=>n.props?.['data-scan'])[0].props.onClick();await tick()
 assert.match(allText(view.root),/Test Guest/);assert.match(allText(view.root),/已扫码选中钱包/)
 assert.ok(nodes(view.root,n=>n.tag==='input'&&n.props.type==='number').length)
 assert.deepEqual(calls.filter(c=>c.body).map(c=>c.body),[{code:'WP.'+'A'.repeat(43),purpose:'topup'}]);view.unmount()
})
