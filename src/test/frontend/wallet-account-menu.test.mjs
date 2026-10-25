import { test, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, h, nextTick, reactive } from 'vue'
import { createPinia } from 'pinia'
import { routeLocationKey, routerKey } from 'vue-router'
import { createI18n } from 'vue-i18n'
let AccountMenu, messages, authMessages
const originalWindow=globalThis.window, originalFetch=globalThis.fetch, originalDocument=globalThis.document, originalDocumentClass=globalThis.Document, originalShadowRoot=globalThis.ShadowRoot
const tick=async()=>{for(let i=0;i<8;i++){await Promise.resolve();await nextTick()}}
const renderer=createRenderer({
 createElement:tag=>({tag,tagName:tag.toUpperCase(),showModal(){this.open=true},close(){this.open=false},children:[],props:{},value:'',get options(){return this.children.filter(c=>c.tag==='option')},listeners:{},addEventListener(name,fn){this.listeners[name]=fn},removeEventListener(){},setAttribute(){},removeAttribute(){},getRootNode(){return globalThis.document}}),createText:text=>({text}),createComment:()=>({}),
 setText:(node,text)=>{node.text=text},setElementText:(node,text)=>{node.text=text},
 patchProp:(node,key,old,value)=>{node.props[key]=value;if(key==='value'){node._value=value;node.value=value}},
 insert:(child,parent,anchor)=>{if(child.parent){const i=child.parent.children.indexOf(child);if(i>=0)child.parent.children.splice(i,1)}child.parent=parent;const i=anchor?parent.children.indexOf(anchor):-1;if(i<0)parent.children.push(child);else parent.children.splice(i,0,child)},
 remove:node=>{if(node.parent){const i=node.parent.children.indexOf(node);if(i>=0)node.parent.children.splice(i,1);node.parent=null}},
 parentNode:node=>node.parent,nextSibling:node=>{const c=node.parent?.children||[];return c[c.indexOf(node)+1]||null}
})
function nodes(root,predicate){let out=[];if(predicate(root))out.push(root);for(const c of root.children||[])out.push(...nodes(c,predicate));return out}


const allText=root=>[root.text||'',...(root.children||[]).map(allText)].join(' ')
const button=(root,label)=>nodes(root,n=>n.tag==='button'&&allText(n).trim()===label)[0]
function mount(locale='zh-CN') {
 const root={children:[]},props=reactive({store:'A',storeName:'示例门店',loggingOut:false,error:''}),events=[]
 const i18n=createI18n({legacy:false,locale,messages:{'zh-CN':{wallet:messages.walletZh,customerAuth:authMessages.customerAuthZh},'en-SG':{wallet:messages.walletEn,customerAuth:authMessages.customerAuthEn}}})
 const app=renderer.createApp({setup:()=>()=>h(AccountMenu,{...props,onLogout:()=>events.push('logout')})})
 app.use(i18n);app.mount(root)
 return{root,props,events,unmount:()=>app.unmount()}
}
before(async()=>{
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document=Object.assign(new Document(),{activeElement:null,visibilityState:'visible'})
 globalThis.window={setInterval:()=>1,clearInterval:()=>{}}
 const {build}=await import('esbuild'),{mkdir,writeFile,readFile}=await import('node:fs/promises'),{pathToFileURL}=await import('node:url')
 const result=await build({stdin:{contents:`export {default as AccountMenu} from './src/modules/wallet/components/WalletCustomerAccountMenu.vue';export * as messages from './src/modules/wallet/locales.ts';export * as authMessages from './src/components/customer-auth/locales.ts'`,resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,format:'esm',platform:'node',packages:'external',plugins:[{name:'vue-test',setup(b){b.onLoad({filter:/\.vue$/},async({path})=>{const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:'test-'+path,inlineTemplate:true}).content,loader:'ts'}})}}]})
 const dir=process.cwd()+'/target/wallet-account-menu-tests';await mkdir(dir,{recursive:true});await writeFile(dir+'/entry.mjs',result.outputFiles[0].contents)
 ;({AccountMenu,messages,authMessages}=await import(pathToFileURL(dir+'/entry.mjs').href+'?'+Date.now()))
})
after(()=>{globalThis.fetch=originalFetch;globalThis.window=originalWindow;globalThis.document=originalDocument;globalThis.Document=originalDocumentClass;globalThis.ShadowRoot=originalShadowRoot})
const submitEvent=()=>({preventDefault(){},stopPropagation(){}})
async function open(view){await nodes(view.root,n=>n.props?.['aria-haspopup']==='dialog')[0].props.onClick();await tick()}
async function edit(view){await button(view.root,'修改登录密码').props.onClick();await tick()}
function fill(view,current='old-password',password='new-password',confirm=password){const inputs=nodes(view.root,n=>n.tag==='input');[current,password,confirm].forEach((value,i)=>inputs[i].props['onUpdate:modelValue'](value))}
test('header opens a native account dialog; Escape closes and reopening clears password fields',async()=>{
 const view=mount();assert.equal(nodes(view.root,n=>n.tag==='dialog').length,0);assert.match(allText(view.root),/我的钱包/);assert.match(allText(view.root),/示例门店/)
 await open(view);const dialog=nodes(view.root,n=>n.tag==='dialog')[0];assert.equal(dialog.open,true);assert.equal(dialog.props['aria-modal'],'true');assert.match(allText(dialog),/账户设置/)
 await edit(view);fill(view);dialog.props.onCancel(submitEvent());await tick();assert.equal(nodes(view.root,n=>n.tag==='dialog').length,0)
 await open(view);await edit(view);assert.ok(nodes(view.root,n=>n.tag==='input').every(n=>!n.value));view.unmount()
})
test('password validation and server errors stay in dialog; submission locks closing and duplicate commands',async()=>{
 let resolve,calls=[];globalThis.fetch=(url,options)=>{calls.push({url,options});return new Promise(r=>resolve=r)}
 const view=mount();await open(view);await edit(view);fill(view,'old-password','new-password','mismatch');await tick()
 const form=nodes(view.root,n=>n.tag==='form')[0];await form.props.onSubmit(submitEvent());await tick();assert.equal(calls.length,0);assert.match(allText(view.root),/两次输入/)
 fill(view);await tick();const pending=form.props.onSubmit(submitEvent());await tick();await form.props.onSubmit(submitEvent());assert.equal(calls.length,1)
 assert.equal(calls[0].url,'/api/v1/public/customer-auth/password-change');assert.equal(calls[0].options.credentials,'include');assert.deepEqual(JSON.parse(calls[0].options.body),{currentPassword:'old-password',newPassword:'new-password'})
 const dialog=nodes(view.root,n=>n.tag==='dialog')[0];dialog.props.onCancel(submitEvent());await tick();assert.equal(nodes(view.root,n=>n.tag==='dialog').length,1);assert.equal(button(view.root,'×').props.disabled,true)
 resolve({ok:false,status:400,json:async()=>({success:false,error:'invalid_credentials'})});await pending;await tick();assert.match(allText(view.root),/手机号或密码不正确/);assert.equal(button(view.root,'×').props.disabled,false)
 globalThis.fetch=async()=>({ok:true,status:200,json:async()=>({success:true})});await form.props.onSubmit(submitEvent());await tick();assert.match(allText(view.root),/密码已修改/);assert.equal(nodes(view.root,n=>n.tag==='input').length,0);view.unmount()
})
test('logout busy/error stays visible; store changes discard account dialog; English labels are translated',async()=>{
 const view=mount();await open(view);button(view.root,'退出登录').props.onClick();assert.deepEqual(view.events,['logout']);view.props.loggingOut=true;await tick();assert.equal(button(view.root,'×').props.disabled,true);assert.equal(button(view.root,'修改登录密码').props.disabled,true)
 view.props.loggingOut=false;view.props.error='退出失败，请重试';await tick();assert.match(allText(nodes(view.root,n=>n.tag==='dialog')[0]),/退出失败/)
 view.props.store='B';await tick();assert.equal(nodes(view.root,n=>n.tag==='dialog').length,0);view.unmount()
 const en=mount('en-SG');await open(en);assert.match(allText(en.root),/Account settings/);assert.match(allText(en.root),/Change password/);assert.match(allText(en.root),/Sign out/);en.unmount()
})
