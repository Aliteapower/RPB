import { test, before, beforeEach, after } from 'node:test'
import assert from 'node:assert/strict'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, h, nextTick, reactive } from 'vue'
import { createPinia } from 'pinia'
import { routeLocationKey, routerKey } from 'vue-router'
import { createI18n } from 'vue-i18n'
let StaffMenu, AccountMenu, messages, authMessages, authModule
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
function mount(locale='zh-CN',staff=false) {
 const root={children:[]},props=reactive({store:'A',storeName:'示例门店',loggingOut:false,error:''}),events=[]
 const i18n=createI18n({legacy:false,locale,messages:{'zh-CN':{wallet:messages.walletZh,customerAuth:authMessages.customerAuthZh},'en-SG':{wallet:messages.walletEn,customerAuth:authMessages.customerAuthEn}}})
 const pinia=createPinia();const auth=authModule.useAuthSessionStore(pinia);auth.user={accountId:'staff-1',username:'cashier01',displayName:'收银员甲',roles:[],permissions:[],storeIds:['A']};const navigation=[];
 const app=renderer.createApp({setup:()=>()=>h(staff?StaffMenu:AccountMenu,{...props,onLogout:()=>events.push('logout')})});app.use(pinia);app.provide(routerKey,{replace:async target=>navigation.push(target)})
 app.use(i18n);app.mount(root)
 return{root,props,events,auth,navigation,unmount:()=>app.unmount()}
}
before(async()=>{
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document=Object.assign(new Document(),{activeElement:null,visibilityState:'visible'})
 globalThis.window={setInterval:()=>1,clearInterval:()=>{},setTimeout,clearTimeout}
 const {build}=await import('esbuild'),{mkdir,writeFile,readFile}=await import('node:fs/promises'),{pathToFileURL}=await import('node:url')
 const result=await build({stdin:{contents:`export {default as StaffMenu} from './src/modules/wallet/components/WalletStaffAccountMenu.vue';export * as authModule from './src/stores/authSession.ts';export {default as AccountMenu} from './src/modules/wallet/components/WalletCustomerAccountMenu.vue';export * as messages from './src/modules/wallet/locales.ts';export * as authMessages from './src/components/customer-auth/locales.ts'`,resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,format:'esm',platform:'node',packages:'external',plugins:[{name:'vue-test',setup(b){b.onLoad({filter:/\.vue$/},async({path})=>{const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:'test-'+path,inlineTemplate:true}).content,loader:'ts'}})}}]})
 const dir=process.cwd()+'/target/wallet-account-menu-tests';await mkdir(dir,{recursive:true});await writeFile(dir+'/entry.mjs',result.outputFiles[0].contents)
 ;({StaffMenu,AccountMenu,messages,authMessages,authModule}=await import(pathToFileURL(dir+'/entry.mjs').href+'?'+Date.now()))
})
after(()=>{globalThis.fetch=originalFetch;globalThis.window=originalWindow;globalThis.document=originalDocument;globalThis.Document=originalDocumentClass;globalThis.ShadowRoot=originalShadowRoot})
const principal={tenantId:'tenant-A',customerId:'customer-A',displayName:'顾客甲',phoneE164:'+6591234567',email:'guest@example.test'}
const me=()=>({ok:true,status:200,text:async()=>JSON.stringify({success:true,principal}),json:async()=>({success:true,principal})})
const staffMe=()=>({ok:true,status:200,text:async()=>JSON.stringify({success:true,user:{accountId:'staff-1',username:'cashier01',displayName:'收银员甲'}})})
beforeEach(()=>{globalThis.fetch=async()=>me()})
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
 let resolve,calls=[];globalThis.fetch=(url,options)=>{if(url.includes('/customer-auth/me'))return Promise.resolve(me());calls.push({url,options});return new Promise(r=>resolve=r)}
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


test('customer popup displays the current customer identity and never the staff username',async()=>{
 const view=mount();await open(view);assert.match(allText(view.root),/顾客甲/);assert.match(allText(view.root),/\+6591234567/);assert.match(allText(view.root),/guest@example.test/);assert.doesNotMatch(allText(view.root),/cashier01/);view.unmount()
})
test('customer identity errors block password editing and retry cannot show a previous store identity',async()=>{
 let pending,fail=true
 globalThis.fetch=async url=>{if(fail)throw Error('offline');return new Promise(r=>pending=r)}
 const view=mount();await open(view);assert.equal(button(view.root,'修改登录密码').props.disabled,true);assert.ok(button(view.root,'重试'))
 fail=false;button(view.root,'重试').props.onClick();await tick();view.props.store='B';await tick();pending(me());await tick();assert.equal(nodes(view.root,n=>n.tag==='dialog').length,0);view.unmount()
})
test('merchant popup shows staff identity, uses staff password endpoint, and successful logout clears only staff session',async()=>{
 const calls=[];globalThis.fetch=async(url,options={})=>{if(url==='/api/v1/auth/me')return staffMe();calls.push({url,options});return {ok:true,status:200,text:async()=>JSON.stringify({success:true})}}
 const view=mount('zh-CN',true);await open(view);assert.match(allText(view.root),/cashier01/);assert.match(allText(view.root),/收银员甲/);await edit(view);fill(view,'old123','new456');await tick();await nodes(view.root,n=>n.tag==='form')[0].props.onSubmit(submitEvent());await tick()
 assert.equal(calls[0].url,'/api/v1/auth/password-change');assert.equal(calls[0].options.headers['X-RPB-Account'],'1');assert.deepEqual(JSON.parse(calls[0].options.body),{currentPassword:'old123',newPassword:'new456'})
 button(view.root,'退出登录').props.onClick();await tick();assert.equal(calls[1].url,'/api/v1/auth/logout');assert.equal(view.auth.user,null);assert.deepEqual(view.navigation,[{name:'login'}]);assert.ok(calls.every(c=>!c.url.includes('customer-auth')));view.unmount()
})
test('merchant failed logout retains the current staff and shows error; repeated password submits are blocked',async()=>{
 let resolve;const calls=[];globalThis.fetch=async(url,options)=>{if(url==='/api/v1/auth/me')return staffMe();calls.push({url,options});if(url.endsWith('/logout'))throw Error('offline');return new Promise(r=>resolve=r)}
 const view=mount('zh-CN',true);await open(view);button(view.root,'退出登录').props.onClick();await tick();assert.equal(view.auth.user.username,'cashier01');assert.match(allText(view.root),/退出失败/)
 await edit(view);fill(view,'old123','new456');await tick();const form=nodes(view.root,n=>n.tag==='form')[0],pending=form.props.onSubmit(submitEvent());await tick();await form.props.onSubmit(submitEvent());assert.equal(calls.filter(c=>c.url.endsWith('/password-change')).length,1)
 const dialog=nodes(view.root,n=>n.tag==='dialog')[0];dialog.props.onCancel(submitEvent());await tick();assert.equal(nodes(view.root,n=>n.tag==='dialog').length,1)
 resolve({ok:false,status:401,text:async()=>JSON.stringify({success:false,error:{code:'INVALID_CREDENTIALS',messageKey:'auth.invalid_credentials',details:{}}})});await pending;await tick();assert.match(allText(view.root),/当前密码不正确/);view.unmount()
})


test('merchant identity is refreshed from the real staff session and changed accounts cannot enable password editing',async()=>{
 globalThis.fetch=async()=>({ok:true,status:200,text:async()=>JSON.stringify({success:true,user:{accountId:'other-staff',username:'wrong-user',displayName:'Other'}})})
 const view=mount('en-SG',true);await open(view);assert.equal(button(view.root,'Change password').props.disabled,true);assert.match(allText(view.root),/session expired/);assert.doesNotMatch(allText(view.root),/wrong-user/);view.unmount()
})
