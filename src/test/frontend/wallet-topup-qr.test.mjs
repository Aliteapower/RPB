import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { createServer, transformWithEsbuild } from 'vite'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, h, nextTick, reactive } from 'vue'
import { createPinia } from 'pinia'
import { routeLocationKey, routerKey } from 'vue-router'
import { createI18n } from 'vue-i18n'
let vite, CodePage, TopupPage, messages, customerModule, staffModule, authModule
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
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document=Object.assign(new Document(),{activeElement:null,visibilityState:'visible'})
 globalThis.window={setInterval:()=>1,clearInterval:()=>{}}
 vite=await createServer({configFile:false,cacheDir:'target/wallet-qr-ui-test-cache',server:{middlewareMode:true,hmr:false},appType:'custom',optimizeDeps:{noDiscovery:true,entries:[]},plugins:[{
 name:'test-inline-vue',async transform(source,id){
  if(id.endsWith('/WalletQrDisplay.vue'))return `import {h} from 'vue';export default {props:['value'],setup:p=>()=>h('figure',{'data-code':p.value})}`
  if(id.endsWith('/WalletQrScanner.vue'))return `import {h} from 'vue';export default {emits:['code'],setup:(p,{emit})=>()=>h('button',{'data-scan':true,onClick:()=>emit('code','WP.'+'A'.repeat(43))},'camera stub')}`
  if(!id.endsWith('.vue'))return
  const {descriptor}=parse(source,{filename:id});const script=compileScript(descriptor,{id:'test-'+id,inlineTemplate:true})
  return transformWithEsbuild(script.content,id+'.ts',{loader:'ts'})
 }
 }]})
 CodePage=(await vite.ssrLoadModule('/src/modules/wallet/pages/WalletCustomerCode.vue')).default
 TopupPage=(await vite.ssrLoadModule('/src/modules/wallet/pages/WalletStaffTopup.vue')).default
 customerModule=await vite.ssrLoadModule('/src/modules/wallet/useCustomerWallet.ts')
 staffModule=await vite.ssrLoadModule('/src/modules/wallet/useStaffWallet.ts')
 authModule=await vite.ssrLoadModule('/src/stores/authSession.ts')
 messages=await vite.ssrLoadModule('/src/modules/wallet/locales.ts')
})
after(async()=>{await vite?.close();globalThis.fetch=originalFetch;globalThis.window=originalWindow;globalThis.document=originalDocument;globalThis.Document=originalDocumentClass;globalThis.ShadowRoot=originalShadowRoot})
test('customer page shows a topup QR immediately and requires explicit action for the charge QR',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{const body=options?.body&&JSON.parse(options.body);calls.push({url,body});return response(url.endsWith('/me')?{context:{transactionsEnabled:true,store:{operatingEntityName:'Issuer'}},accounts:[{id:'wallet',balanceCents:1000,reservedCents:0}],pendingBills:[]}:{type:body.purpose==='topup'?'customer_wallet':'customer_charge',code:body.purpose==='topup'?'WP.identity':'WC.consent',expiresAt:'2099-01-01T00:00:00Z'})}
 const route=reactive({params:{storeId:'A'},query:{purpose:'topup'},hash:''}),view=mount(CodePage,'customer',route);await tick()
 assert.equal(nodes(view.root,n=>n.tag==='figure')[0].props['data-code'],'WP.identity');assert.match(allText(view.root),/不授权扣款/)
 assert.equal(calls.filter(c=>c.body).length,1)
 route.query={};await tick();assert.equal(nodes(view.root,n=>n.tag==='figure').length,0);assert.equal(calls.filter(c=>c.body).length,1)
 nodes(view.root,n=>n.tag==='button')[0].props.onClick();await tick()
 assert.equal(nodes(view.root,n=>n.tag==='figure')[0].props['data-code'],'WC.consent');view.unmount()
})
test('merchant topup page scans identity, shows customer and enters amount before creating any topup',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,body:options?.body&&JSON.parse(options.body)});return response(url.endsWith('/context')?{transactionsEnabled:true,program:{channels:['cash']}}:{account:{id:'wallet',balanceCents:1000,reservedCents:0},customer:{id:'guest',displayName:'Test Guest',phone:'+6591234567'}})}
 const view=mount(TopupPage,'staff',reactive({params:{storeId:'A'},query:{scan:'1'},hash:''}));await tick()
 nodes(view.root,n=>n.props?.['data-scan'])[0].props.onClick();await tick()
 assert.match(allText(view.root),/Test Guest/);assert.match(allText(view.root),/已扫码选中钱包/)
 assert.ok(nodes(view.root,n=>n.tag==='input'&&n.props.type==='number').length)
 assert.deepEqual(calls.filter(c=>c.body).map(c=>c.body),[{code:'WP.'+'A'.repeat(43),purpose:'topup'}]);view.unmount()
})
