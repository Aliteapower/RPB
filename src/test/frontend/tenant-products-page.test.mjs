import {test,before,after} from 'node:test'
import assert from 'node:assert/strict'
import {readFile,mkdir,writeFile} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {parse,compileScript} from '@vue/compiler-sfc'
import {build} from 'esbuild'
import {createRenderer,h,nextTick,reactive} from 'vue'
import {routeLocationKey} from 'vue-router'
import {createI18n} from 'vue-i18n'
let Page,messages
const originalFetch=globalThis.fetch
const tick=async()=>{for(let i=0;i<12;i++){await Promise.resolve();await nextTick()}}
const renderer=createRenderer({createElement:tag=>({tag,children:[],props:{}}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>n.props[k]=v,
 insert:(n,p,a)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{const i=n.parent?.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)},parentNode:n=>n.parent,nextSibling:n=>{const list=n.parent?.children||[];return list[list.indexOf(n)+1]||null}})
const text=n=>[n.text||'',...(n.children||[]).map(text)].join(' ')
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
before(async()=>{
 const result=await build({stdin:{contents:"export {default as Page} from './src/modules/tenant-products/TenantProductsPage.vue';export * as messages from './src/modules/tenant-products/locales.ts'",resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,platform:'node',format:'esm',external:['vue','vue-router','vue-i18n'],plugins:[{name:'vue-test',setup(b){
  b.onLoad({filter:/TenantAdminNav\.vue$/},()=>({contents:'export default {render(){return null}}',loader:'js'}))
  b.onLoad({filter:/\.vue$/},async args=>{const {descriptor}=parse(await readFile(args.path,'utf8'),{filename:args.path});const script=compileScript(descriptor,{id:args.path,inlineTemplate:true});return{contents:script.content,loader:'ts'}})
 }}]})
 await mkdir('target',{recursive:true});await writeFile('target/tenant-products-page-test.mjs',result.outputFiles[0].contents);({Page,messages}=await import(pathToFileURL(process.cwd()+'/target/tenant-products-page-test.mjs')))
})
after(()=>{globalThis.fetch=originalFetch})
test('compiled page renders both language states, saves switches and explains expired products',async()=>{
 let wallet=true
 globalThis.fetch=async(url,options)=>{if(options.method==='PUT')wallet=JSON.parse(options.body).enabled;return{ok:true,json:async()=>({success:true,products:[{appKey:'wallet',enabled:wallet,canEnable:true,unavailableReason:null},...options.method==='GET'?[{appKey:'daily_ledger',enabled:false,canEnable:false,unavailableReason:'STORE_APP_EXPIRED'}]:[]]})}}
 const root={children:[]},app=renderer.createApp({setup:()=>()=>h(Page)})
 const i18n=createI18n({legacy:false,locale:'zh-CN',messages:{
  'zh-CN':{tenantProducts:messages.tenantProductsZh,common:{actions:{refresh:'刷新',loading:'加载中'}}},
  en:{tenantProducts:messages.tenantProductsEn,common:{actions:{refresh:'Refresh',loading:'Loading'}}}
 }})
 app.use(i18n);app.provide(routeLocationKey,reactive({params:{storeId:'A'}}));app.mount(root);await tick()
 assert.ok(text(root).includes('钱包积分'));assert.ok(text(root).includes('每日营业记录'));assert.ok(text(root).includes('当前门店订阅已到期'))
 let switches=nodes(root,n=>n.props?.role==='switch');assert.equal(switches.length,2);assert.equal(switches[0].props['aria-checked'],true);assert.equal(switches[1].props.disabled,true)
 await switches[0].props.onClick();await tick();switches=nodes(root,n=>n.props?.role==='switch');assert.equal(switches[0].props['aria-checked'],false);assert.ok(text(root).includes('已保存'))
 i18n.global.locale.value='en';await tick();assert.ok(text(root).includes('Wallet and points'));assert.ok(text(root).includes('Turn on'));assert.ok(text(root).includes('subscription has expired'));app.unmount()
})
