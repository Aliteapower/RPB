import {before,afterEach,test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {parse,compileScript} from '@vue/compiler-sfc'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {createRenderer,h,reactive} from 'vue'
import {createPinia} from 'pinia'
import {createI18n} from 'vue-i18n'
import {routeLocationKey} from 'vue-router'
let More,Cards,useAuthSessionStore
const mounted=[]
const renderer=createRenderer({createElement:tag=>({tag,props:{},children:[]}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>n.props[k]=v,insert:(n,p)=>{n.parent=p;p.children.push(n)},remove:n=>{if(n?.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n?.parent,nextSibling:n=>{const list=n?.parent?.children||[];return list[list.indexOf(n)+1]||null}})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
before(async()=>{
  const result=await build({stdin:{contents:"export {default as More} from './src/modules/wallet/pages/WalletStaffMore.vue';export {default as Cards} from './src/modules/cards/CardsStaffPage.vue';export {useAuthSessionStore} from './src/stores/authSession'",resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router','pinia','vue-i18n'],plugins:[{name:'catalog-entry',setup(b){
    b.onLoad({filter:/\.css$/},()=>({contents:'',loader:'js'}))
    b.onLoad({filter:/useStaffWallet\.ts$/},()=>({contents:"import {computed} from 'vue';import {useRoute} from 'vue-router';import {useAuthSessionStore} from '../../stores/authSession';export function useStaffWallet(){const route=useRoute(),auth=useAuthSessionStore();return {store:computed(()=>String(route.params.storeId)),can:p=>auth.user?.permissions.includes(p)===true}}",loader:'ts'}))
    b.onLoad({filter:/useCardsWorkspace\.ts$/},()=>({contents:"import {ref} from 'vue';import {useAuthSessionStore} from '../../stores/authSession';export function useCardsWorkspace(){const auth=useAuthSessionStore();return {store:ref('A'),busy:ref(false),error:ref(''),notice:ref(''),enabled:ref(true),pending:ref(false),plans:ref([]),holdings:ref([]),entries:ref([]),purchases:ref([]),can:p=>auth.user?.permissions.includes(p)===true,run:async f=>f(),load:async()=>{}}}",loader:'ts'}))
    b.onLoad({filter:/\.vue$/},async({path})=>{
      if(!/(WalletStaffMore|CardsStaffPage)\.vue$/.test(path))return {contents:'export default {render(){return null}}',loader:'js'}
      const {descriptor}=parse(await readFile(path,'utf8'),{filename:path})
      return {contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}
    })
  }}]})
  await mkdir('target',{recursive:true});await writeFile('target/staff-catalog-entry-test.mjs',result.outputFiles[0].contents)
  ;({More,Cards,useAuthSessionStore}=await import(pathToFileURL(process.cwd()+'/target/staff-catalog-entry-test.mjs')))
})
afterEach(()=>mounted.splice(0).forEach(app=>app.unmount()))
function catalogLinks(Page,roles,permissions){
  const root={children:[]},app=renderer.createApp({render:()=>h(Page)}),pinia=createPinia();app.use(pinia)
  useAuthSessionStore(pinia).user={roles,permissions,storeIds:['A']}
  app.use(createI18n({legacy:false,locale:'zh-CN',messages:{},missingWarn:false,fallbackWarn:false}))
  app.provide(routeLocationKey,reactive({params:{storeId:'A'},query:{}}))
  app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:p.to},slots.default?.())})
  app.mount(root);mounted.push(app)
  return nodes(root,n=>n.tag==='a'&&n.props.href==='/stores/A/catalog')
}
test('delegated catalog read access supports card workflows without advertising administrator catalog routes',()=>{
  for(const Page of [More,Cards]) assert.equal(catalogLinks(Page,['store_staff'],['catalog.view','wallet.cards.manage']).length,0)
})
test('catalog management entry requires both tenant administration and catalog viewing permission',()=>{
  for(const Page of [More,Cards]) {
    assert.equal(catalogLinks(Page,['tenant_admin'],['tenant.admin.manage']).length,0)
    assert.equal(catalogLinks(Page,['tenant_admin'],['tenant.admin.manage','catalog.view']).length,1)
  }
})
