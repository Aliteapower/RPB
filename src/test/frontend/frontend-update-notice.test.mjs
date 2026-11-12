import {test,before,after} from 'node:test'
import assert from 'node:assert/strict'
import {parse,compileScript} from '@vue/compiler-sfc'
import {createRenderer,h,nextTick,ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {build} from 'esbuild'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
const a='a'.repeat(40),b='b'.repeat(40)
let Notice
before(async()=>{
 const result=await build({entryPoints:['src/frontend-version/FrontendUpdateNotice.vue'],bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-i18n'],define:{'import.meta.env.VITE_FRONTEND_VERSION':JSON.stringify(a)},plugins:[{name:'notice-test',setup(build){build.onLoad({filter:/\.vue$/},async({path})=>{const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return{contents:compileScript(descriptor,{id:'version-notice',inlineTemplate:true}).content,loader:'ts'}})}}]})
 await mkdir('target/frontend-update-tests',{recursive:true});await writeFile('target/frontend-update-tests/notice.mjs',result.outputFiles[0].contents);Notice=(await import(pathToFileURL(process.cwd()+'/target/frontend-update-tests/notice.mjs'))).default
})
const renderer=createRenderer({createElement:tag=>({tag,children:[],props:{}}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>n.props[k]=v,insert:(n,p,anchor)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=anchor?p.children.indexOf(anchor):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n?.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}},parentNode:n=>n?.parent,nextSibling:n=>{const c=n?.parent?.children??[];return c[c.indexOf(n)+1]??null}})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children??[]).flatMap(c=>nodes(c,p))],text=n=>[n.text??'',...(n.children??[]).map(text)].join(' ')
const tick=async()=>{for(let n=0;n<15;n++){await Promise.resolve();await nextTick()}}
function mount(locale='zh-CN'){
 const old={window:globalThis.window,document:globalThis.document,fetch:globalThis.fetch},win=new EventTarget(),doc=new EventTarget();doc.visibilityState='visible';let reloads=0,version=a,reads=0
 win.location={reload:()=>reloads++};globalThis.window=win;globalThis.document=doc;globalThis.fetch=async()=>{reads++;return{ok:true,headers:new Headers({'Content-Type':'application/json'}),json:async()=>({version})}}
 const draft=ref('QA unsaved wine note'),root={children:[]},app=renderer.createApp({setup:()=>()=>h('main',[h(Notice),h('input',{value:draft.value,onInput:e=>draft.value=e.target.value})])});app.use(createI18n({legacy:false,locale,messages:{}}));app.mount(root)
 return {root,win,doc,version:v=>version=v,reads:()=>reads,reloads:()=>reloads,close(){app.unmount();Object.assign(globalThis,old)}}
}
const button=(root,label)=>nodes(root,n=>n.tag==='button'&&text(n).trim()===label)[0]
test('old open tab retains unsaved work; update always requires explicit second confirmation',async()=>{
 const m=mount();try{await tick();const input=nodes(m.root,n=>n.tag==='input')[0];input.props.onInput({target:{value:'QA 尚未保存的存酒备注'}});await tick();assert.doesNotMatch(text(m.root),/有新版本/)
 m.version(b);m.win.dispatchEvent(new Event('focus'));await tick();assert.match(text(m.root),/有新版本可用/);assert.equal(m.reloads(),0);assert.equal(input.props.value,'QA 尚未保存的存酒备注')
 button(m.root,'更新页面').props.onClick();await tick();assert.match(text(m.root),/清除本页未保存/);assert.equal(m.reloads(),0);button(m.root,'暂不更新').props.onClick();await tick();assert.equal(m.reloads(),0);assert.equal(input.props.value,'QA 尚未保存的存酒备注')
 button(m.root,'更新页面').props.onClick();await tick();button(m.root,'确认更新并重新载入').props.onClick();assert.equal(m.reloads(),1)
 }finally{m.close()}
})
test('visible polling and tab restoration detect updates in English; disposal removes listeners',async t=>{
 t.mock.timers.enable({apis:['setInterval']});const m=mount('en');try{await tick();m.version(b);m.doc.visibilityState='hidden';t.mock.timers.tick(60_000);m.win.dispatchEvent(new Event('focus'));await tick();assert.equal(m.reads(),1)
 m.doc.visibilityState='visible';m.doc.dispatchEvent(new Event('visibilitychange'));await tick();assert.match(text(m.root),/An update is available/);assert.equal(m.reloads(),0)
 const reads=m.reads();t.mock.timers.tick(60_000);await tick();assert.equal(m.reads(),reads+1);m.version(a);m.win.dispatchEvent(new Event('pageshow'));await tick();assert.doesNotMatch(text(m.root),/An update is available/)
 }finally{m.close()}
 const previous=m.reads();m.win.dispatchEvent(new Event('focus'));t.mock.timers.tick(60_000);await tick();assert.equal(m.reads(),previous)
})
