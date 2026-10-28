import {before,test} from 'node:test'
import assert from 'node:assert/strict'
import {readFile,mkdir,writeFile} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {parse,compileScript} from '@vue/compiler-sfc'
import {createSSRApp,createRenderer,h,nextTick} from 'vue'
import {renderToString} from 'vue/server-renderer'
import {createI18n} from 'vue-i18n'
import {createPinia} from 'pinia'
import {routeLocationKey,routerKey} from 'vue-router'
let ledgerMessages,Header,LedgerHeader,useAuthSessionStore,caption,walletHeaderTitleKey
before(async()=>{
 const {build}=await import('esbuild')
 const result=await build({stdin:{contents:`export {ledgerMessages} from './src/modules/daily-ledger/locales/zh-CN.ts';export {default as Header} from './src/components/common/ProductPageHeader.vue';export {default as LedgerHeader} from './src/modules/daily-ledger/components/LedgerPageHeader.vue';export {useAuthSessionStore} from './src/stores/authSession.ts';export {uniqueHeaderCaption as caption} from './src/components/common/headerCaption.ts';export {walletHeaderTitleKey} from './src/modules/wallet/headerTitle.ts'`,resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,format:'esm',platform:'node',packages:'external',plugins:[{name:'vue',setup(b){b.onLoad({filter:/\.vue$/},async({path})=>{const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}})}}]})
 const dir='target/product-page-header-tests';await mkdir(dir,{recursive:true});await writeFile(dir+'/entry.mjs',result.outputFiles[0].contents)
 ;({ledgerMessages,Header,LedgerHeader,useAuthSessionStore,caption,walletHeaderTitleKey}=await import(pathToFileURL(process.cwd()+'/'+dir+'/entry.mjs').href+'?'+Date.now()))
})
test('ledger keeps tenant custom branding and logo once, single-store switch hidden, current user and currency visible',async()=>{
 const app=setup(createSSRApp({render:()=>h(LedgerHeader,{branding:{store_name:'自定义招牌',store_name_en:'自定义招牌',logo_data:'data:image/png;base64,AA=='},storeId:'A',tab:'history',currency:'CNY'})}))
 const pinia=createPinia();app.use(pinia);const auth=useAuthSessionStore(pinia)
 auth.user={displayName:'值班经理',storeIds:['A']};auth.authorizedStores=[{storeId:'A',storeName:'平台门店名'}]
 app.provide(routeLocationKey,{params:{storeId:'A'}});app.provide(routerKey,{})
 const html=await renderToString(app),visible=html.replace(/<[^>]*>/g,'')
 assert.equal(visible.split('自定义招牌').length-1,1);assert.ok(!visible.includes('平台门店名'));assert.ok(visible.includes('值班经理'));assert.ok(visible.includes('CNY'));assert.ok(visible.includes('历史账目'));assert.ok(html.includes('data:image/png;base64,AA=='));assert.ok(!html.includes('<select'))
})
const setup=app=>{app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:p.to},slots.default?.())});app.use(createI18n({legacy:false,locale:'zh-CN',messages:{'zh-CN':{dailyLedger:{messages:ledgerMessages},common:{localeSwitcher:{aria:'语言',zhCN:'中文',enSG:'EN'}}}}}));return app}
test('store branding deduplicates trimmed names and preserves distinct translated/custom names',()=>{
 assert.equal(caption(' 食刻 ','食刻',null,''),'食刻')
 assert.equal(caption('自定义招牌','Custom Brand'),'自定义招牌 · Custom Brand')
 assert.equal(caption(undefined,''),'')
})
test('shared header renders one title and store caption, correct back link and full long caption hint',async()=>{
 const long='非常长的自定义门店名 Marina Bay Flagship'
 const html=await renderToString(setup(createSSRApp({render:()=>h(Header,{title:'酒水寄存',caption:long,backTo:'/wallet/A',backLabel:'返回',refreshLabel:'刷新'})})))
 assert.equal((html.match(/<h1/g)||[]).length,1);assert.ok(html.includes('href="/wallet/A"'));assert.ok(html.includes('title="'+long+'"'));assert.ok(html.includes('酒水寄存'));assert.ok(html.includes('语言'))
})
test('refresh busy gate, settings slot and title-only card route resolution stay independent of business commands',async()=>{
 const renderer=createRenderer({createElement:tag=>({tag,children:[],props:{}}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>n.props[k]=v,insert:(n,p)=>p.children.push(n),remove:()=>{},parentNode:()=>null,nextSibling:()=>null})
 const root={children:[]};let refreshes=0
 const app=setup(renderer.createApp({render:()=>h(Header,{title:'存酒',refreshLabel:'刷新',refreshDisabled:true,onRefresh:()=>refreshes++},{actions:()=>h('button','设置')})}));app.mount(root);await nextTick()
 const nodes=n=>[n,...(n.children||[]).flatMap(nodes)],buttons=nodes(root).filter(n=>n.tag==='button'),refresh=buttons.find(n=>n.props['aria-label']==='刷新')
 assert.equal(refresh.props.disabled,true);refresh.props.onClick();assert.equal(refreshes,0);assert.ok(buttons.some(n=>n.text==='设置'));app.unmount()
 assert.equal(walletHeaderTitleKey('wallet-cards'),'wallet.ui.cardsEntry')
 assert.equal(walletHeaderTitleKey('wallet-customer-cards',true),'wallet.ui.myCards')
 assert.equal(walletHeaderTitleKey('wallet-collect'),'wallet.ui.staffWallet')
 assert.equal(walletHeaderTitleKey('wallet-customer',true),'wallet.customerWallet')
})
