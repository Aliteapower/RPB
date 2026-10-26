import {test,before,after} from 'node:test'
import assert from 'node:assert/strict'
import {readFile,mkdir,writeFile} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {parse,compileScript} from '@vue/compiler-sfc'
import {build} from 'esbuild'
import {createRenderer,h,nextTick} from 'vue'
import {createI18n} from 'vue-i18n'
let Page,poll
const original={window:globalThis.window,fetch:globalThis.fetch,storage:globalThis.sessionStorage}
const tick=async()=>{for(let i=0;i<20;i++){await Promise.resolve();await nextTick()}}
const renderer=createRenderer({createElement:tag=>({tag,children:[],props:{},showModal(){this.open=true},addEventListener(){},removeEventListener(){},getRootNode(){return {activeElement:null}}}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>n.props[k]=v,
 insert:(n,p,a)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{const i=n.parent?.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)},parentNode:n=>n.parent,nextSibling:n=>{const list=n.parent?.children||[];return list[list.indexOf(n)+1]||null}})
const text=n=>[n.text||'',...(n.children||[]).map(text)].join(' ')
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]

before(async()=>{
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.window={setInterval:fn=>{poll=fn;return 1},clearInterval(){}}
 const memory=new Map();globalThis.sessionStorage={getItem:k=>memory.get(k)??null,setItem:(k,v)=>memory.set(k,v),removeItem:k=>memory.delete(k)}
 const result=await build({entryPoints:['src/modules/cards/CardPresentation.vue'],bundle:true,write:false,platform:'node',format:'esm',external:['vue','vue-i18n'],plugins:[{name:'vue-test',setup(b){
 b.onLoad({filter:/WalletQrDisplay\.vue$/},()=>({contents:"import {h} from 'vue';export default {props:['value'],setup:p=>()=>h('figure',{'data-code':p.value})}",loader:'js'}))
 b.onLoad({filter:/\.vue$/},async args=>{const {descriptor}=parse(await readFile(args.path,'utf8'),{filename:args.path});return {contents:compileScript(descriptor,{id:args.path,inlineTemplate:true}).content,loader:'ts'}})
 }}]})
 await mkdir('target',{recursive:true});await writeFile('target/card-presentation-test.mjs',result.outputFiles[0].contents);Page=(await import(pathToFileURL(process.cwd()+'/target/card-presentation-test.mjs'))).default
})
after(()=>{globalThis.window=original.window;globalThis.fetch=original.fetch;globalThis.sessionStorage=original.storage})
function mount(kind='count',locale='zh-CN',fail=false){
 const calls=[],events=[],root={children:[]};let completed=false
 globalThis.fetch=async(url,options)=>{calls.push({url,body:options?.body&&JSON.parse(options.body)});if(fail)return {ok:false,status:409,json:async()=>({code:'CARD_QUOTA_INSUFFICIENT',message:'额度不足'})};return {ok:true,json:async()=>options?.method==='POST'?{requestId:'request',code:'CA.'+'A'.repeat(43),expiresAt:new Date(Date.now()+120000).toISOString()}:{status:completed?'completed':'pending'}}}
 const holding={id:'card',kind,available:3,timezone:'Asia/Singapore',snapshot:{nameZh:'咖啡卡',nameEn:'Coffee card',product:{nameZh:'咖啡',nameEn:'Coffee',unit:'杯'}}}
 const app=renderer.createApp({render:()=>h(Page,{store:'A',holding,onCompleted:()=>events.push('completed'),onPending:()=>events.push('pending')})});app.use(createI18n({legacy:false,locale,messages:{}}));app.mount(root)
 return {app,root,calls,events,complete:()=>completed=true}
}
test('card presentation requires explicit quantity authorization and clears code after merchant completion',async()=>{
 const s=mount();try{await tick();assert.equal(s.calls.length,0);assert.match(text(s.root),/咖啡卡/)
 const input=nodes(s.root,n=>n.tag==='input')[0];input.props['onUpdate:modelValue'](2);await tick()
 await nodes(s.root,n=>n.tag==='button'&&text(n)==='生成本次授权码')[0].props.onClick();await tick()
 assert.equal(s.calls[0].url,'/api/v1/public/stores/A/wallet/cards/holdings/card/presentation');assert.equal(s.calls[0].body.quantity,2);assert.ok(s.calls[0].body.idempotencyKey)
 assert.equal(nodes(s.root,n=>n.props?.['data-code']).length,1);assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='重新生成卡码')[0].props.disabled,true)
 s.complete();await poll();await tick();assert.deepEqual(s.events,['pending','completed']);assert.equal(nodes(s.root,n=>n.props?.['data-code']).length,0);assert.match(text(s.root),/已完成/)
 }finally{s.app.unmount()}
})
test('stamp presentation fixes quantity at one and supports English',async()=>{
 const s=mount('stamp','en-SG');try{await tick();assert.match(text(s.root),/add one stamp/);assert.equal(nodes(s.root,n=>n.tag==='input')[0].props.disabled,true)
 await nodes(s.root,n=>n.tag==='button'&&text(n)==='Create authorization QR')[0].props.onClick();await tick();assert.equal(s.calls[0].body.quantity,1)
 }finally{s.app.unmount()}
})
test('presentation errors show without a QR and release busy state',async()=>{
 const s=mount('count','zh-CN',true);try{await tick();await nodes(s.root,n=>n.tag==='button'&&text(n)==='生成本次授权码')[0].props.onClick();await tick();assert.match(text(s.root),/额度不足/);assert.equal(nodes(s.root,n=>n.props?.['data-code']).length,0);assert.equal(nodes(s.root,n=>n.tag==='button'&&text(n)==='生成本次授权码')[0].props.disabled,false)
 }finally{s.app.unmount()}
})



test('expired presentation permits explicit refresh and never refreshes automatically',async()=>{
 const originalNow=Date.now,s=mount();try{
 await tick();await nodes(s.root,n=>n.tag==='button'&&text(n)==='生成本次授权码')[0].props.onClick();await tick();const later=originalNow()+121000;Date.now=()=>later;await poll();await tick()
 assert.equal(s.calls.filter(c=>c.body).length,1)
 const refresh=nodes(s.root,n=>n.tag==='button'&&text(n)==='重新生成卡码')[0];assert.equal(refresh.props.disabled,false);await refresh.props.onClick();await tick();assert.equal(s.calls.filter(c=>c.body).length,2)
 }finally{Date.now=originalNow;s.app.unmount()}
})
