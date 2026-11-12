import {test,before,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {parse,compileScript} from '@vue/compiler-sfc'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {createRenderer,h,ref} from 'vue'
import {routeLocationKey,routerKey} from 'vue-router'
import {createI18n} from 'vue-i18n'
let Login,Register
const mounted=[],renderer=createRenderer({createElement:tag=>({tag,props:{},children:[]}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},patchProp:(n,k,o,v)=>n.props[k]=v,insert:(n,p,anchor)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=anchor?p.children.indexOf(anchor):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children=n.parent.children.filter(c=>c!==n)},parentNode:n=>n?.parent,nextSibling:n=>{const c=n?.parent?.children??[];return c[c.indexOf(n)+1]??null}})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
before(async()=>{
 const b=await build({stdin:{contents:"export {default as Login} from './src/modules/wallet/components/WalletCustomerLogin.vue';export {default as Register} from './src/modules/wallet/pages/WalletCustomerRegister.vue'",resolveDir:process.cwd()},bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router','vue-i18n'],plugins:[{name:'registration-return',setup(b){
  b.onLoad({filter:/useCustomerWallet\.ts$/},()=>({contents:'export function useCustomerWallet(){return globalThis.__walletRegistration}',loader:'js'}))
  b.onLoad({filter:/CustomerPasswordLogin\.vue$/},()=>({contents:"import {h} from 'vue';export default {props:['registrationTo'],emits:['authenticated'],setup:(p,{emit})=>()=>h('button',{'data-registration-to':p.registrationTo,onClick:()=>emit('authenticated')},'Sign in')}",loader:'js'}))
  b.onLoad({filter:/CustomerSelfRegistrationForm\.vue$/},()=>({contents:"export default {setup:(p,{slots})=>()=>slots.login?.({registered:true})}",loader:'js'}))
  b.onLoad({filter:/\.vue$/},async({path})=>{const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}})
 }}]});await mkdir('target/wallet-registration-return',{recursive:true});await writeFile('target/wallet-registration-return/components.mjs',b.outputFiles[0].contents);({Login,Register}=await import(pathToFileURL(process.cwd()+'/target/wallet-registration-return/components.mjs')))
})
afterEach(()=>{mounted.forEach(a=>a.unmount());mounted.length=0;delete globalThis.__walletRegistration})
function mount(component,props={},returnTo){
 const pushed=[];globalThis.__walletRegistration={store:ref('A'),authenticated:async()=>{}}
 const app=renderer.createApp({render:()=>h(component,props)}),root={children:[]};app.use(createI18n({legacy:false,locale:'en',messages:{en:{}}}));app.provide(routeLocationKey,{query:{returnTo}});app.provide(routerKey,{push:async p=>pushed.push(p)});app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:p.to},slots.default?.())});app.mount(root);mounted.push(app);return {root,pushed}
}
test('storage login carries its same-store return target into shared customer registration',()=>{
 const s=mount(Login,{store:'A',returnTo:'/wallet/A/wine-storage'});const link=nodes(s.root,n=>n.tag==='button')[0].props['data-registration-to'];assert.equal(new URL(link,'https://example.invalid').searchParams.get('returnTo'),'/wallet/A/wine-storage')
})
test('after registering and logging in from storage, return to storage instead of the unopened wallet',async()=>{
 const s=mount(Register,{},'/wallet/A/wine-storage');await nodes(s.root,n=>n.tag==='button')[0].props.onClick();assert.deepEqual(s.pushed,['/wallet/A/wine-storage'])
})
test('registration cannot redirect to another store, external host or unregistered route',async()=>{
 for(const value of ['https://evil.invalid','//evil.invalid','/wallet/B/wine-storage','/stores/A/wallet','/wallet/A/wine-storage?x=1']){const s=mount(Register,{},value);await nodes(s.root,n=>n.tag==='button')[0].props.onClick();assert.deepEqual(s.pushed,['/wallet/A'])}
})
