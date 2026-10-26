import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, h, nextTick, reactive } from 'vue'
import { createI18n } from 'vue-i18n'
let Login, Change, Reset, Binding, Activation, Issue, api, messages
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
 props=reactive(props)
 const root={children:[]},i18n=createI18n({legacy:false,locale:'zh-CN',messages:{'zh-CN':{customerAuth:messages.customerAuthZh}}})
 const app=renderer.createApp({render:()=>h(component,props)});app.use(i18n);app.component('RouterLink',{props:['to'],setup:(props,{slots})=>()=>h('a',{href:props.to},slots.default?.())});app.mount(root)
 return {root,props,unmount:()=>app.unmount()}
}
const response=(status,body)=>({ok:status<400,status,json:async()=>body,text:async()=>JSON.stringify(body)})
const signedOutFetch=handler=>(url,options)=>String(url).includes('/customer-auth/me')?Promise.resolve(response(401,{success:false,error:'unauthenticated'})):handler(url,options)
before(async()=>{
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document=Object.assign(new Document(),{activeElement:null})
 const {build}=await import('esbuild'),{mkdir,writeFile,readFile}=await import('node:fs/promises'),{pathToFileURL}=await import('node:url')
 const result=await build({stdin:{contents:`export {default as Login} from './src/components/customer-auth/CustomerPasswordLogin.vue';export {default as Change} from './src/components/customer-auth/CustomerPasswordChange.vue';export {default as Reset} from './src/components/customer-auth/CustomerPasswordReset.vue';export {default as Binding} from './src/components/customer-auth/CustomerRecoveryEmail.vue';export {default as Activation} from './src/components/customer-auth/CustomerActivation.vue';export {default as Issue} from './src/modules/customers/CustomerActivationIssue.vue';export * as api from './src/api/customerAuthApi.ts';export * as messages from './src/components/customer-auth/locales.ts'`,resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,format:'esm',platform:'node',packages:'external',plugins:[{name:'vue-test',setup(b){b.onLoad({filter:/\.vue$/},async({path})=>{const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:'test-'+path,inlineTemplate:true}).content,loader:'ts'}})}}]})
 const dir=process.cwd()+'/target/customer-password-tests';await mkdir(dir,{recursive:true});await writeFile(dir+'/entry.mjs',result.outputFiles[0].contents)
 ;({Login,Change,Reset,Binding,Activation,Issue,api,messages}=await import(pathToFileURL(dir+'/entry.mjs').href+'?'+Date.now()))
})
after(()=>{globalThis.fetch=originalFetch;globalThis.document=originalDocument;globalThis.Document=originalDocumentClass;globalThis.ShadowRoot=originalShadowRoot})
test('shared login sends normalized phone and password, blocks double submit, clears password on success',async()=>{
 const calls=[],signed=[];let finish
 globalThis.fetch=signedOutFetch((url,options)=>{calls.push({url,...options});return new Promise(resolve=>{finish=resolve})})
 const view=mount(Login,{store:'store-A',onAuthenticated:value=>signed.push(value)})
 const input=nodes(view.root,n=>n.tag==='input')
 input.find(n=>n.props.type==='tel').props.onInput({target:{value:'91234567'}})
 input.find(n=>n.props.type==='password').props['onUpdate:modelValue']('chosen-password')
 const form=nodes(view.root,n=>n.tag==='form')[0]
 form.props.onSubmit({preventDefault(){}});form.props.onSubmit({preventDefault(){}});await tick()
 assert.equal(calls.length,1);assert.equal(calls[0].url,'/api/v1/public/customer-auth/password-login')
 assert.deepEqual(JSON.parse(calls[0].body),{storeId:'store-A',phoneE164:'+6591234567',password:'chosen-password'})
 finish(response(200,{success:true,principal:{customerId:'shared-customer'}}));await tick()
 assert.equal(signed[0].customerId,'shared-customer')
 assert.equal(nodes(view.root,n=>n.tag==='input'&&n.props.type==='password').at(-1).value,'')
 view.unmount()
})
test('incorrect password is translated and does not emit an authenticated customer',async()=>{
 const signed=[];globalThis.fetch=signedOutFetch(async()=>response(401,{success:false,error:'invalid_credentials'}))
 const view=mount(Login,{store:'store-A',onAuthenticated:v=>signed.push(v)})
 const input=nodes(view.root,n=>n.tag==='input')
 input.find(n=>n.props.type==='tel').props.onInput({target:{value:'91234567'}})
 input.find(n=>n.props.type==='password').props['onUpdate:modelValue']('wrong')
 nodes(view.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick()
 assert.equal(signed.length,0)
 assert.ok(nodes(view.root,n=>n.props?.role==='alert').some(n=>n.text.includes('手机号或密码不正确')))
 view.unmount()
})

test('an old restricted login response never grants business access and offers in-person activation',async()=>{
 const signed=[]
 globalThis.fetch=async()=>response(200,{success:true,principal:{customerId:'shared',passwordChangeRequired:true}})
 const view=mount(Login,{store:'A',onAuthenticated:v=>signed.push(v)})
 let input=nodes(view.root,n=>n.tag==='input')
 input.find(n=>n.props.type==='tel').props.onInput({target:{value:'91234567'}})
 input.find(n=>n.props.type==='password').props['onUpdate:modelValue']('234567')
 nodes(view.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick()
 assert.equal(signed.length,0)
 assert.equal(nodes(view.root,n=>n.tag==='input'&&n.props.autocapitalize==='characters').length,1)
 view.unmount()
})

test('recovery requests matching details and resets without treating the response as a login',async()=>{
 const calls=[],done=[]
 globalThis.fetch=async(url,options)=>{calls.push({url,body:JSON.parse(options.body)});return response(200,{success:true})}
 const view=mount(Reset,{store:'A',onDone:()=>done.push(true)})
 let input=nodes(view.root,n=>n.tag==='input')
 input.find(n=>n.props.type==='tel').props.onInput({target:{value:'91234567'}})
 input.find(n=>n.props.type==='email').props['onUpdate:modelValue']('verified@example.com')
 nodes(view.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick()
 assert.deepEqual(calls[0].body,{storeId:'A',phoneE164:'+6591234567',email:'verified@example.com'})
 input=nodes(view.root,n=>n.tag==='input')
 input.find(n=>n.props.autocomplete==='one-time-code').props['onUpdate:modelValue']('123456')
 const passwordInputs=input.filter(n=>n.props.type==='password');passwordInputs[0].props['onUpdate:modelValue']('safe-password');passwordInputs[1].props['onUpdate:modelValue']('different')
 nodes(view.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.equal(calls.length,1)
 passwordInputs[1].props['onUpdate:modelValue']('safe-password');nodes(view.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick()
 assert.equal(calls[1].url,'/api/v1/public/customer-auth/password-reset');assert.equal(calls[1].body.newPassword,'safe-password');assert.equal(done.length,0)
 assert.equal(nodes(view.root,n=>n.tag==='input'&&n.props.type==='password').length,0)
 view.unmount()
})

test('recovery email binding saves only after code confirmation and prevents duplicate requests',async()=>{
 const calls=[];let finish
 globalThis.fetch=(url,options)=>{calls.push({url,body:JSON.parse(options.body)});return calls.length===1?new Promise(resolve=>finish=resolve):Promise.resolve(response(200,{success:true}))}
 const view=mount(Binding,{store:'A'})
 nodes(view.root,n=>n.tag==='button')[0].props.onClick();await tick()
 nodes(view.root,n=>n.tag==='input')[0].props['onUpdate:modelValue']('verified@example.com')
 const submit=()=>nodes(view.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}})
 submit();submit();await tick();assert.equal(calls.length,1)
 finish(response(200,{success:true}));await tick()
 assert.equal(calls[0].url,'/api/v1/public/customer-auth/recovery-email-code')
 nodes(view.root,n=>n.tag==='input'&&n.props.autocomplete==='one-time-code')[0].props['onUpdate:modelValue']('123456')
 submit();await tick();assert.deepEqual(calls[1].body,{storeId:'A',email:'verified@example.com',code:'123456'})
 assert.equal(nodes(view.root,n=>n.tag==='form').length,0)
 view.unmount()
})
test('password change checks confirmation locally and posts only old and new passwords',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,body:JSON.parse(options.body)});return response(200,{success:true,principal:{customerId:'shared'}})}
 const view=mount(Change)
 nodes(view.root,n=>n.tag==='button')[0].props.onClick();await tick()
 let input=nodes(view.root,n=>n.tag==='input')
 input[0].props['onUpdate:modelValue']('old-password');input[1].props['onUpdate:modelValue']('new-password');input[2].props['onUpdate:modelValue']('different')
 nodes(view.root,n=>n.tag==='form').at(-1).props.onSubmit({preventDefault(){}});await tick();assert.equal(calls.length,0)
 input[2].props['onUpdate:modelValue']('new-password');nodes(view.root,n=>n.tag==='form').at(-1).props.onSubmit({preventDefault(){}});await tick()
 assert.deepEqual(calls,[{url:'/api/v1/public/customer-auth/password-change',body:{currentPassword:'old-password',newPassword:'new-password'}}])
 assert.ok(nodes(view.root,n=>n.props?.role==='status').some(n=>n.text.includes('密码已修改')))
 view.unmount()
})

test('activation saves a chosen password once, checks confirmation and returns to login without authenticating',async()=>{
 const calls=[],done=[]
 globalThis.fetch=async(url,options)=>{calls.push({url,body:JSON.parse(options.body),headers:options.headers});return response(200,{success:true})}
 const view=mount(Activation,{store:'A',onDone:()=>done.push(true)})
 const input=nodes(view.root,n=>n.tag==='input')
 input.find(n=>n.props.type==='tel').props.onInput({target:{value:'91234567'}})
 input.find(n=>n.props.autocapitalize==='characters').props['onUpdate:modelValue']('ABCD-EFGH-JKLM')
 const passwords=input.filter(n=>n.props.type==='password')
 passwords[0].props['onUpdate:modelValue']('chosen-password');passwords[1].props['onUpdate:modelValue']('wrong')
 const submit=()=>nodes(view.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}})
 submit();await tick();assert.equal(calls.length,0)
 passwords[1].props['onUpdate:modelValue']('chosen-password');submit();await tick()
 assert.deepEqual(calls[0].body,{storeId:'A',phoneE164:'+6591234567',code:'ABCD-EFGH-JKLM',newPassword:'chosen-password'})
 assert.equal(calls[0].url,'/api/v1/public/customer-auth/activate');assert.equal(calls[0].headers['X-RPB-Customer-Auth'],'1')
 assert.equal(done.length,0);assert.equal(nodes(view.root,n=>n.tag==='input').length,0)
 view.unmount()
})
test('staff issuance requires unchecked in-person attestation, blocks double submit and clears on store switch',async()=>{
 const calls=[];let finish
 globalThis.fetch=(url,options)=>{calls.push({url,body:JSON.parse(options.body)});return new Promise(resolve=>finish=resolve)}
 const view=mount(Issue,{store:'A',customer:{id:'customer-1',displayName:'Test',phoneE164:'+6591234567'}})
 const form=()=>nodes(view.root,n=>n.tag==='form')[0]
 form().props.onSubmit({preventDefault(){}});await tick();assert.equal(calls.length,0)
 const check=nodes(view.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0];assert.ok(!check.checked)
 check.checked=true;check.listeners.change({target:check});await tick()
 form().props.onSubmit({preventDefault(){}});form().props.onSubmit({preventDefault(){}});await tick();assert.equal(calls.length,1)
 assert.deepEqual(calls[0].body,{identityConfirmed:true});assert.match(calls[0].url,/stores\/A\/tenant-admin\/customers\/customer-1\/activation-codes$/)
 view.props.store='B';await tick();finish(response(200,{success:true,code:'ABCD-EFGH-JKLM',expiresAt:new Date(Date.now()+600000).toISOString()}));await tick()
 assert.equal(nodes(view.root,n=>n.tag==='output').length,0)
 view.unmount()
})
test('staff code is displayed only after success and removed when the selected customer changes',async()=>{
 globalThis.fetch=async()=>response(200,{success:true,code:'ABCD-EFGH-JKLM',expiresAt:new Date(Date.now()+600000).toISOString()})
 const view=mount(Issue,{store:'A',customer:{id:'customer-1',displayName:'Test',phoneE164:'+6591234567'}})
 const check=nodes(view.root,n=>n.tag==='input')[0];check.checked=true;check.listeners.change({target:check});await tick()
 nodes(view.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick()
 assert.equal(nodes(view.root,n=>n.tag==='output')[0].text,'ABCD-EFGH-JKLM')
 view.props.customer={id:'customer-2',displayName:'Other',phoneE164:'+6591234568'};await tick()
 assert.equal(nodes(view.root,n=>n.tag==='output').length,0);view.unmount()
})
