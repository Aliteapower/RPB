import {test,before,after} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {parse,compileScript} from '@vue/compiler-sfc'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {createRenderer,h,nextTick} from 'vue'
import {createPinia} from 'pinia'
import {createI18n} from 'vue-i18n'
let Page
const original={window:globalThis.window,fetch:globalThis.fetch,storage:globalThis.sessionStorage,Document:globalThis.Document,ShadowRoot:globalThis.ShadowRoot}
const renderer=createRenderer({createElement:tag=>({tag,props:{},children:[],get options(){return this.children.filter(n=>n.tag==='option')},addEventListener(){},getRootNode(){return {} }}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>n.props[k]=v,insert:(n,p)=>{n.parent=p;p.children.push(n)},remove:n=>{if(n.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:()=>null})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>(n.text||'')+(n.children||[]).map(text).join('')
const tick=async()=>{for(let i=0;i<12;i++)await nextTick()}
before(async()=>{
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.window={location:{origin:'https://test.local'},setInterval:()=>1,clearInterval(){}}
 globalThis.sessionStorage={getItem:()=>null,removeItem(){}}
 const result=await build({entryPoints:['src/modules/cards/CardOperations.vue'],bundle:true,write:false,format:'esm',platform:'node',external:['vue','pinia','vue-i18n'],plugins:[{name:'vue',setup(b){
 b.onLoad({filter:/WalletQr(Scanner|Display)\.vue$/},()=>({contents:"import {h} from 'vue';export default {props:['purpose'],setup:p=>()=>h('div',{'data-scanner-purpose':p.purpose})}",loader:'js'}))
 b.onLoad({filter:/\.vue$/},async({path})=>{const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return {contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}})
 }}]});await mkdir('target',{recursive:true});await writeFile('target/card-operations-ui-test.mjs',result.outputFiles[0].contents);Page=(await import(pathToFileURL(process.cwd()+'/target/card-operations-ui-test.mjs'))).default
})
after(()=>{globalThis.window=original.window;globalThis.fetch=original.fetch;globalThis.sessionStorage=original.storage;globalThis.Document=original.Document;globalThis.ShadowRoot=original.ShadowRoot})
function mount(locale='zh-CN',disabled=false){const root={children:[]},events=[],app=renderer.createApp({render:()=>h(Page,{store:'store-A',plans:[],holdings:[],disabled,can:()=>true,onChanged:()=>events.push('changed')})});app.use(createPinia());app.use(createI18n({legacy:false,locale,messages:{}}));app.mount(root);return {root,events,app}}
test('compact search submits via form, keeps query store-scoped and populates customer choices',async()=>{
 const calls=[];globalThis.fetch=async(url)=>{calls.push(url);return {ok:true,json:async()=>[{id:'customer-A',displayName:'测试顾客',phone:'1234'}]}}
 const s=mount();try{const field=nodes(s.root,n=>n.tag==='input'&&n.props.placeholder==='搜索手机号或姓名')[0];assert.equal(field.props['aria-label'],'搜索手机号或姓名');field.props['onUpdate:modelValue']('张 & 李');await tick();await nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.deepEqual(calls,['/api/v1/stores/store-A/wallet/cards/customers?query='+encodeURIComponent('张 & 李')]);assert.match(text(s.root),/测试顾客1234|测试顾客 1234/);assert.deepEqual(s.events,['changed'])}finally{s.app.unmount()}
})
test('English compact search and scan controls remain distinct, with disabled actions preserved',async()=>{
 const calls=[];globalThis.fetch=async url=>{calls.push(url);throw new Error('disabled form must not query')};const s=mount('en-SG',true);try{const search=nodes(s.root,n=>n.tag==='button'&&text(n)==='Search')[0],scan=nodes(s.root,n=>n.tag==='button'&&text(n)==='Scan · Customer')[0];assert.equal(search.props.disabled,true);assert.equal(scan.props.disabled,true);assert.equal(scan.props.type,'button');await nodes(s.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});assert.equal(calls.length,0);assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='Merchant QR')[0].props.disabled,true)}finally{s.app.unmount()}
})
test('customer identity scanner still opens with the identity purpose',async()=>{
 const s=mount();try{nodes(s.root,n=>n.tag==='button'&&text(n)==='扫描 · 顾客')[0].props.onClick();await tick();assert.equal(nodes(s.root,n=>n.props?.['data-scanner-purpose']==='identity').length,1)}finally{s.app.unmount()}
})
