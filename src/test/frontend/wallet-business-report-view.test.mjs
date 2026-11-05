import {test,before,after} from 'node:test'
import assert from 'node:assert/strict'
import {createRenderer,h,ref,nextTick} from 'vue'
import {createI18n} from 'vue-i18n'
import {parse,compileScript} from '@vue/compiler-sfc'
import {build} from 'esbuild'
import {mkdir,readFile,writeFile} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
let Page,messages
const originalFetch=globalThis.fetch, originalDocument=globalThis.document, originalDocumentClass=globalThis.Document, originalShadowRoot=globalThis.ShadowRoot
const renderer=createRenderer({createElement:tag=>({tag,children:[],props:{},value:'',addEventListener(){},removeEventListener(){},getRootNode(){return globalThis.document}}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>{n.props[k]=v;if(k==='value')n.value=v},insert:(n,p,a)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=a?p.children.indexOf(a):-1;i<0?p.children.push(n):p.children.splice(i,0,n)},remove:n=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}},parentNode:n=>n.parent,nextSibling:n=>{const c=n.parent?.children??[];return c[c.indexOf(n)+1]??null}})
const text=n=>[n.text??'',...(n.children??[]).map(text)].join(' ')
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children??[]).flatMap(x=>nodes(x,p))]
const tick=async()=>{for(let i=0;i<12;i++){await Promise.resolve();await nextTick()}}
const fixture=(rows=[])=>({period:'day',startDate:'2026-10-25',endDate:'2026-10-25',timezone:'Asia/Singapore',currency:'SGD',rows})
before(async()=>{
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document=Object.assign(new Document(),{activeElement:null})
 const result=await build({stdin:{contents:"export {default as Page} from './src/modules/wallet/pages/WalletBusinessReport.vue';export * as messages from './src/modules/wallet/locales.ts'",resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,format:'esm',platform:'node',packages:'external',plugins:[{name:'report-view',setup(b){b.onLoad({filter:/useStaffWallet\.ts$/},()=>({contents:'export const useStaffWallet=()=>globalThis.reportWorkspace',loader:'ts'}));b.onLoad({filter:/\.vue$/},async({path})=>{const {descriptor}=parse(await readFile(path,'utf8'));return {contents:compileScript(descriptor,{id:'report-test',inlineTemplate:true}).content,loader:'ts'}})}}]})
 await mkdir('target/wallet-business-report-tests',{recursive:true});await writeFile('target/wallet-business-report-tests/view.mjs',result.outputFiles[0].contents)
 ;({Page,messages}=await import(pathToFileURL(process.cwd()+'/target/wallet-business-report-tests/view.mjs')))
})
after(()=>{globalThis.fetch=originalFetch;globalThis.document=originalDocument;globalThis.Document=originalDocumentClass;globalThis.ShadowRoot=originalShadowRoot;delete globalThis.reportWorkspace})
function mount(allowed=true,locale='zh-CN'){
 const store=ref('A'),permission=ref(allowed),root={children:[]};globalThis.reportWorkspace={store,context:ref({store:{timezone:'Asia/Singapore'}}),can:()=>permission.value}
 const app=renderer.createApp({render:()=>h(Page)});app.use(createI18n({legacy:false,locale,messages:{'zh-CN':{wallet:messages.walletZh},'en-SG':{wallet:messages.walletEn}}}));app.mount(root)
 return {root,store,permission,close:()=>app.unmount()}
}
test('renders only active metrics, switches daily/monthly/yearly and clears errors without stale totals',async()=>{
 const calls=[];globalThis.fetch=async url=>{calls.push(url);return {ok:true,json:async()=>fixture([{bucket:'2026-10-25',metric:'consumption',channel:'',channelName:'',amount:500,count:1}])}}
 const v=mount();await tick();assert.match(text(v.root),/消费金额/);assert.doesNotMatch(text(v.root),/充值赠送/)
 for(const [label,kind] of [['月报','month'],['年报','year']]){await nodes(v.root,n=>n.tag==='button'&&text(n).trim()===label)[0].props.onClick();await tick();assert.match(calls.at(-1),new RegExp(`period=${kind}`))}
 globalThis.fetch=async()=>({ok:false,status:403,json:async()=>({message:'无权限'})});await nodes(v.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){},stopPropagation(){}});await tick();assert.match(text(v.root),/无权限/);assert.doesNotMatch(text(v.root),/消费金额/);v.close()
})
test('old store response cannot replace new store empty result; denied users never query',async()=>{
 let release;globalThis.fetch=()=>new Promise(r=>release=r);const v=mount();await tick();assert.match(text(v.root),/正在读取/)
 globalThis.fetch=async()=>({ok:true,json:async()=>fixture()});v.store.value='B';await tick();release({ok:true,json:async()=>fixture([{bucket:'2026-10-25',metric:'consumption',channel:'',channelName:'',amount:999,count:1}])});await tick();assert.match(text(v.root),/暂无实际交易/);assert.doesNotMatch(text(v.root),/9\.99/);v.close()
 let calls=0;globalThis.fetch=async()=>{calls++;return {ok:true,json:async()=>fixture()}};const denied=mount(false);await tick();assert.equal(calls,0);denied.close()
})
test('English report labels remain translated',async()=>{globalThis.fetch=async()=>({ok:true,json:async()=>fixture()});const v=mount(true,'en-SG');await tick();assert.match(text(v.root),/Business reports/);assert.match(text(v.root),/No actual transactions/);v.close()})
test('major category filter hides absent categories and shows only selected report items',async()=>{
 const row=(metric,amount)=>({bucket:'2026-10-25',metric,amount,count:1,channel:'',channelName:''})
 globalThis.fetch=async()=>({ok:true,json:async()=>fixture([row('consumption',500),row('topupPrincipal',10000),row('pointsGrant',20)])})
 const v=mount();await tick();assert.equal(nodes(v.root,n=>n.tag==='button'&&text(n).trim()==='退款与冲正').length,0)
 await nodes(v.root,n=>n.tag==='button'&&text(n).trim()==='充值')[0].props.onClick();await tick();assert.match(text(v.root),/充值本金/);assert.doesNotMatch(text(v.root),/消费金额|积分增加/)
 await nodes(v.root,n=>n.tag==='button'&&text(n).trim()==='积分')[0].props.onClick();await tick();assert.match(text(v.root),/积分增加/);assert.doesNotMatch(text(v.root),/充值本金|消费金额/)
 await nodes(v.root,n=>n.tag==='button'&&text(n).trim()==='全部')[0].props.onClick();await tick();assert.match(text(v.root),/充值本金/);assert.match(text(v.root),/消费金额/);v.close()
})
