import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { createServer, transformWithEsbuild } from 'vite'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, h, nextTick } from 'vue'
import { createI18n } from 'vue-i18n'
let vite, Login, Change, api, messages
const originalFetch=globalThis.fetch, originalDocument=globalThis.document, originalDocumentClass=globalThis.Document, originalShadowRoot=globalThis.ShadowRoot
const tick=async()=>{for(let i=0;i<8;i++){await Promise.resolve();await nextTick()}}
const renderer=createRenderer({
 createElement:tag=>({tag,tagName:tag.toUpperCase(),children:[],props:{},value:'',listeners:{},addEventListener(name,fn){this.listeners[name]=fn},removeEventListener(){},setAttribute(){},removeAttribute(){},getRootNode(){return globalThis.document}}),createText:text=>({text}),createComment:()=>({}),
 setText:(node,text)=>{node.text=text},setElementText:(node,text)=>{node.text=text},
 patchProp:(node,key,old,value)=>{node.props[key]=value},
 insert:(child,parent,anchor)=>{if(child.parent){const i=child.parent.children.indexOf(child);if(i>=0)child.parent.children.splice(i,1)}child.parent=parent;const i=anchor?parent.children.indexOf(anchor):-1;if(i<0)parent.children.push(child);else parent.children.splice(i,0,child)},
 remove:node=>{if(node.parent){const i=node.parent.children.indexOf(node);if(i>=0)node.parent.children.splice(i,1);node.parent=null}},
 parentNode:node=>node.parent,nextSibling:node=>{const c=node.parent?.children||[];return c[c.indexOf(node)+1]||null}
})
function nodes(root,predicate){let out=[];if(predicate(root))out.push(root);for(const c of root.children||[])out.push(...nodes(c,predicate));return out}
function mount(component,props={}) {
 const root={children:[]},i18n=createI18n({legacy:false,locale:'zh-CN',messages:{'zh-CN':{customerAuth:messages.customerAuthZh}}})
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
 Login=(await vite.ssrLoadModule('/src/components/customer-auth/CustomerPasswordLogin.vue')).default
 Change=(await vite.ssrLoadModule('/src/components/customer-auth/CustomerPasswordChange.vue')).default
 api=await vite.ssrLoadModule('/src/api/customerAuthApi.ts')
 messages=await vite.ssrLoadModule('/src/components/customer-auth/locales.ts')
})
after(async()=>{await vite?.close();globalThis.fetch=originalFetch;globalThis.document=originalDocument;globalThis.Document=originalDocumentClass;globalThis.ShadowRoot=originalShadowRoot})
test('shared login sends normalized phone and password, blocks double submit, clears password on success',async()=>{
 const calls=[],signed=[];let finish
 globalThis.fetch=(url,options)=>{calls.push({url,...options});return new Promise(resolve=>{finish=resolve})}
 const view=mount(Login,{store:'store-A',onAuthenticated:value=>signed.push(value)})
 const input=nodes(view.root,n=>n.tag==='input')
 input.find(n=>n.props.type==='tel').props.onInput({target:{value:'91234567'}})
 input.find(n=>n.props.type==='password').props['onUpdate:modelValue']('234567')
 const form=nodes(view.root,n=>n.tag==='form')[0]
 form.props.onSubmit({preventDefault(){}});form.props.onSubmit({preventDefault(){}});await tick()
 assert.equal(calls.length,1);assert.equal(calls[0].url,'/api/v1/public/customer-auth/password-login')
 assert.deepEqual(JSON.parse(calls[0].body),{storeId:'store-A',phoneE164:'+6591234567',password:'234567'})
 finish(response(200,{success:true,principal:{customerId:'shared-customer'}}));await tick()
 assert.equal(signed[0].customerId,'shared-customer')
 assert.equal(nodes(view.root,n=>n.tag==='input'&&n.props.type==='password').at(-1).value,'')
 view.unmount()
})
test('incorrect password is translated and does not emit an authenticated customer',async()=>{
 const signed=[];globalThis.fetch=async()=>response(401,{success:false,error:'invalid_credentials'})
 const view=mount(Login,{store:'store-A',onAuthenticated:v=>signed.push(v)})
 const input=nodes(view.root,n=>n.tag==='input')
 input.find(n=>n.props.type==='tel').props.onInput({target:{value:'91234567'}})
 input.find(n=>n.props.type==='password').props['onUpdate:modelValue']('wrong')
 nodes(view.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick()
 assert.equal(signed.length,0)
 assert.ok(nodes(view.root,n=>n.props?.role==='alert').some(n=>n.text.includes('手机号或密码不正确')))
 view.unmount()
})
test('password change checks confirmation locally and posts only old and new passwords',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,body:JSON.parse(options.body)});return response(200,{success:true,principal:{customerId:'shared'}})}
 const view=mount(Change)
 nodes(view.root,n=>n.tag==='button')[0].props.onClick();await tick()
 let input=nodes(view.root,n=>n.tag==='input')
 input[0].props['onUpdate:modelValue']('234567');input[1].props['onUpdate:modelValue']('new-password');input[2].props['onUpdate:modelValue']('different')
 nodes(view.root,n=>n.tag==='form').at(-1).props.onSubmit({preventDefault(){}});await tick();assert.equal(calls.length,0)
 input[2].props['onUpdate:modelValue']('new-password');nodes(view.root,n=>n.tag==='form').at(-1).props.onSubmit({preventDefault(){}});await tick()
 assert.deepEqual(calls,[{url:'/api/v1/public/customer-auth/password-change',body:{currentPassword:'234567',newPassword:'new-password'}}])
 assert.ok(nodes(view.root,n=>n.props?.role==='status').some(n=>n.text.includes('密码已修改')))
 view.unmount()
})
