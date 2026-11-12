import {before, test} from 'node:test'
import assert from 'node:assert/strict'
import {readFile, mkdir, writeFile} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {parse, compileScript, compileTemplate} from '@vue/compiler-sfc'
import {createSSRApp, h, createRenderer, nextTick} from 'vue'
import {renderToString} from 'vue/server-renderer'
import {createPinia} from 'pinia'
import {createI18n} from 'vue-i18n'
import {routeLocationKey, routerKey} from 'vue-router'

let Header, Switcher, walletStoreCaption, useAuthSessionStore
before(async()=>{
  const {build}=await import('esbuild')
  const result=await build({stdin:{contents:`export {default as Header} from './src/modules/wallet/components/WalletPageHeader.vue';export {default as Switcher} from './src/components/store/StoreSwitcher.vue';export {walletStoreCaption} from './src/modules/wallet/storeCaption.ts';export {useAuthSessionStore} from './src/stores/authSession.ts'`,resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,format:'esm',platform:'node',packages:'external',plugins:[{name:'vue',setup(b){b.onLoad({filter:/\.vue$/},async({path})=>{
    const {descriptor}=parse(await readFile(path,'utf8'),{filename:path})
    if(!descriptor.scriptSetup) return {contents:compileTemplate({source:descriptor.template.content,filename:path,id:path}).code+'\nexport default {render}',loader:'ts'}
    return {contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}
  })}}]})
  const dir='target/wallet-header-tests';await mkdir(dir,{recursive:true});await writeFile(`${dir}/entry.mjs`,result.outputFiles[0].contents)
  ;({Header,Switcher,walletStoreCaption,useAuthSessionStore}=await import(pathToFileURL(`${process.cwd()}/${dir}/entry.mjs`).href+'?'+Date.now()))
})

test('store caption deduplicates trimmed names while preserving a distinct issuing entity',()=>{
  assert.equal(walletStoreCaption({storeName:' 食刻 ',operatingEntityName:'食刻'},'fallback'),'食刻')
  assert.equal(walletStoreCaption({storeName:'食刻',operatingEntityName:'餐饮公司'},'fallback'),'食刻 · 餐饮公司')
  assert.equal(walletStoreCaption(undefined,'当前门店'),'当前门店')
})

test('shared header renders identity, navigation and actions once in workflow order',async()=>{
  const html=await renderToString(createSSRApp({render:()=>h(Header,null,{default:()=>h('h1','钱包'),navigation:()=>h('a','首页'),actions:()=>[h('button','扫码'),h('button','刷新'),h('button','EN')]})}))
  for(const text of ['钱包','首页','扫码','刷新','EN']) assert.equal(html.split(text).length-1,1)
  assert.ok(html.indexOf('钱包')<html.indexOf('首页'))
  assert.ok(html.indexOf('首页')<html.indexOf('扫码'))
})

const stores=[{storeId:'A',storeName:'门店甲',operatingEntityName:'经营主体'},{storeId:'B',storeName:'门店乙'}]
function setup(app,count=1,locale='zh-CN'){
  const pinia=createPinia();app.use(pinia)
  const auth=useAuthSessionStore(pinia);auth.authorizedStores=stores.slice(0,count);auth.ensureAuthorizedStores=async()=>{}
  const pushes=[]
  app.provide(routeLocationKey,{params:{storeId:'A'},path:'/stores/A/wallet/collect',query:{entry:'phone'},hash:'#amount'})
  app.provide(routerKey,{push:async path=>pushes.push(path)})
  app.use(createI18n({legacy:false,locale,messages:{'zh-CN':{storeSwitcher:{label:'切换店面'}},'en-SG':{storeSwitcher:{label:'Switch store'}}}}))
  return pushes
}
test('compact switcher hides single-store repetition; default callers still show the store',async()=>{
  for(const compact of [true,false]){
    const app=createSSRApp({render:()=>h(Switcher,{compact})});setup(app)
    const html=await renderToString(app)
    assert.equal(html.includes('门店甲'),!compact)
  }
})
test('multi-store compact switcher preserves routing and translates the current option',async()=>{
  const renderer=createRenderer({createElement:tag=>({tag,children:[],props:{}}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>n.props[k]=v,insert:(n,p)=>p.children.push(n),remove:()=>{},parentNode:()=>null,nextSibling:()=>null})
  for(const locale of ['zh-CN','en-SG']){
    const root={children:[]},app=renderer.createApp({render:()=>h(Switcher,{compact:true})}),pushes=setup(app,2,locale)
    app.mount(root);await nextTick()
    const find=(n,tag)=>n.tag===tag?n:(n.children||[]).map(c=>find(c,tag)).find(Boolean)
    const select=find(root,'select')
    assert.equal(find(select,'option').text,locale==='zh-CN'?'切换店面':'Switch store')
    await select.props.onChange({target:{value:'B'}})
    assert.deepEqual(pushes,['/stores/B/wallet/collect?entry=phone#amount'])
    app.unmount()
  }
})
