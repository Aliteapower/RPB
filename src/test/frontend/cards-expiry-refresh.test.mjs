import {test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {writeFile,mkdir} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {createRenderer,h,nextTick,reactive} from 'vue'
import {routeLocationKey} from 'vue-router'
import {createPinia} from 'pinia'
const renderer=createRenderer({createElement:tag=>({tag,children:[]}),createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp(){},insert:(n,p)=>p.children.push(n),remove(){},parentNode:()=>null,nextSibling:()=>null})
const tick=async()=>{for(let i=0;i<25;i++){await Promise.resolve();await nextTick()}}
test('open customer workspace refreshes server balances after the frozen business day changes, skips recovery, and clears timer',async()=>{
 const original={Date:globalThis.Date,window:globalThis.window,fetch:globalThis.fetch,storage:globalThis.sessionStorage};let now=original.Date.parse('2026-10-25T15:59:00Z'),poll,cleared=false,workspace;const calls=[],storage=new Map()
 try{
  globalThis.Date=class extends original.Date{constructor(...args){if(args.length)super(...args);else super(now)}static now(){return now}}
  globalThis.window={setInterval:fn=>{poll=fn;return 7},clearInterval:id=>{cleared=id===7}}
  globalThis.sessionStorage={getItem:k=>storage.get(k)??null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)}
  globalThis.fetch=async(url,o)=>{calls.push({url,method:o.method});return {ok:true,json:async()=>url.endsWith('/holdings')?[{id:'A',timezone:'Asia/Singapore',remaining:now<original.Date.parse('2026-10-25T16:00:00Z')?30:29}]:[]}}
  const built=await build({entryPoints:['src/modules/cards/useCardsWorkspace.ts'],bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router','pinia']});await mkdir('target',{recursive:true});await writeFile('target/card-expiry-workspace-test.mjs',built.outputFiles[0].contents);const {useCardsWorkspace}=await import(pathToFileURL(process.cwd()+'/target/card-expiry-workspace-test.mjs'))
  const app=renderer.createApp({setup(){workspace=useCardsWorkspace(true);return()=>h('div')}});app.use(createPinia());app.provide(routeLocationKey,reactive({params:{storeId:'A'}}));app.mount({children:[]})
  try{await tick();assert.equal(workspace.holdings.value[0].remaining,30);assert.equal(calls.length,4);poll();await tick();assert.equal(calls.length,4)
   now=original.Date.parse('2026-10-25T16:00:01Z');storage.set('rpb.cards.pending:customer:A',JSON.stringify({operation:'request.present',key:'lost'}));workspace.pending.value=true;poll();await tick();assert.equal(calls.length,4)
   storage.clear();workspace.pending.value=false;poll();await tick();assert.equal(calls.length,8);assert.equal(workspace.holdings.value[0].remaining,29);assert.ok(calls.every(c=>c.method==='GET'));poll();await tick();assert.equal(calls.length,8)
  }finally{app.unmount()}
  assert.equal(cleared,true)
 }finally{globalThis.Date=original.Date;globalThis.window=original.window;globalThis.fetch=original.fetch;globalThis.sessionStorage=original.storage}
})
