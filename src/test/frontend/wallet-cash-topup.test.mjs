import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { createServer, transformWithEsbuild } from 'vite'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, h, nextTick } from 'vue'
import { createI18n } from 'vue-i18n'
let vite, CollectionForm, messages
const originalFetch=globalThis.fetch, originalDocument=globalThis.document, originalDocumentClass=globalThis.Document, originalShadowRoot=globalThis.ShadowRoot
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
function mount(component,props={}) {
 const root={children:[]},i18n=createI18n({legacy:false,locale:'zh-CN',messages:{'zh-CN':{wallet:messages.walletZh}}})
 const app=renderer.createApp({render:()=>h(component,props)});app.use(i18n);app.mount(root)
 return {root,unmount:()=>app.unmount()}
}
const response=(status,body)=>({ok:status<400,status,json:async()=>body})
before(async()=>{
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document=Object.assign(new Document(),{activeElement:null})
 vite=await createServer({configFile:false,cacheDir:'target/customer-auth-ui-test-cache',server:{middlewareMode:true,hmr:false},appType:'custom',optimizeDeps:{noDiscovery:true,entries:[]},plugins:[{
 name:'test-inline-vue',async transform(source,id){
  if(!id.endsWith('.vue'))return
  const {descriptor}=parse(source,{filename:id})
  const script=compileScript(descriptor,{id:'test-'+id,inlineTemplate:true})
  return transformWithEsbuild(script.content,id+'.ts',{loader:'ts'})
 }
 }]})
 CollectionForm=(await vite.ssrLoadModule('/src/modules/wallet/components/WalletCollectionForm.vue')).default
 messages=await vite.ssrLoadModule('/src/modules/wallet/locales.ts')
})
after(async()=>{await vite?.close();globalThis.fetch=originalFetch;globalThis.document=originalDocument;globalThis.Document=originalDocumentClass;globalThis.ShadowRoot=originalShadowRoot})

const props=(channel,topup=true)=>({amountCents:100000,fixedChannel:channel,channels:[channel],busy:false,paynowConfigured:true,topup})
const receipt=root=>nodes(root,n=>n.tag==='input'&&n.props.maxlength==='160')[0]
const verify=root=>nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox')[0].props['onUpdate:modelValue'](true)
test('cash top-up can submit without a receipt only after confirming actual cash receipt',async()=>{
 const confirmed=[],view=mount(CollectionForm,{...props('cash'),onConfirm:v=>confirmed.push(v)})
 assert.equal(receipt(view.root).props.required,false)
 assert.equal(nodes(view.root,n=>n.tag==='button')[0].props.disabled,true)
 verify(view.root);await tick();assert.equal(nodes(view.root,n=>n.tag==='button')[0].props.disabled,false)
 nodes(view.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick()
 assert.equal(confirmed.length,1);assert.equal(confirmed[0].receiptReference,'');assert.equal(confirmed[0].channel,'cash');assert.equal(confirmed[0].amount,'1000.00');assert.equal(confirmed[0].verified,true)
 assert.ok(nodes(view.root,n=>n.tag==='label').some(n=>(n.text||'').includes('选填')||(n.children||[]).some(c=>(c.text||'').includes('选填'))))
 view.unmount()
})
test('PayNow, credit card and cash bill payments still require a receipt',async()=>{
 for(const [channel,topup] of [['paynow',true],['credit_card',true],['cash',false]]){
  const view=mount(CollectionForm,props(channel,topup));verify(view.root);await tick()
  assert.equal(receipt(view.root).props.required,true)
  assert.equal(nodes(view.root,n=>n.tag==='button')[0].props.disabled,true)
  receipt(view.root).props['onUpdate:modelValue']('paper-reference');await tick()
  assert.equal(nodes(view.root,n=>n.tag==='button')[0].props.disabled,false);view.unmount()
 }
})
test('busy cash confirmation stays disabled with an optional receipt',async()=>{
 const view=mount(CollectionForm,{...props('cash'),busy:true});verify(view.root);await tick()
 assert.equal(nodes(view.root,n=>n.tag==='button')[0].props.disabled,true);view.unmount()
})
