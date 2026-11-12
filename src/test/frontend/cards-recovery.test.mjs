import {test,beforeEach,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
const bundle=await build({entryPoints:['src/modules/cards/api.ts'],bundle:true,write:false,format:'esm',platform:'node'})
const {cardApi}=await import('data:text/javascript;base64,'+Buffer.from(bundle.outputFiles[0].contents).toString('base64'))
let oldFetch
beforeEach(()=>{oldFetch=globalThis.fetch;const values=new Map();globalThis.sessionStorage={getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)}})
afterEach(()=>{globalThis.fetch=oldFetch})
test('response loss retains only a recovery reference, blocks new commands and recovers original response',async()=>{const api=cardApi('store'),calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,options});throw Error('lost')};await assert.rejects(api.command('/authorized-redeem',{authorization:'CA.secret'},'request.authorized'),/lost/);assert.ok(api.pending());assert.ok(!JSON.stringify(api.pending()).includes('secret'));await assert.rejects(api.command('/stamps',{},'stamp'),/Recover/);globalThis.fetch=async()=>({ok:true,status:200,json:async()=>({id:'original',status:'completed'})});assert.deepEqual(await api.recover(),{id:'original',status:'completed'});assert.equal(api.pending(),null)})
test('missing original result is closed by serialized reconcile before clearing pending state',async()=>{const api=cardApi('store',true);globalThis.fetch=async()=>{throw Error('lost')};await assert.rejects(api.command('/enroll',{planId:'p'},'enroll'));const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,options});return calls.length===1?{ok:false,status:404,json:async()=>({code:'CARD_NOT_FOUND'})}:{ok:true,status:200,json:async()=>({closed:true})}};assert.deepEqual(await api.recover(),{closed:true});assert.equal(calls[1].options.method,'POST');assert.match(calls[1].url,/reconcile$/);assert.equal(api.pending(),null)})
