import {test,beforeEach,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
async function load(path){const b=await build({entryPoints:[path],bundle:true,write:false,format:'esm',platform:'node'});return import('data:text/javascript;base64,'+Buffer.from(b.outputFiles[0].contents).toString('base64'))}
const {storageApi}=await load('src/modules/wine-storage/api.ts')
const {normalizeWalletQr}=await load('src/modules/wallet/scanning/walletQr.ts')
let originalFetch,originalStorage
beforeEach(()=>{originalFetch=globalThis.fetch;originalStorage=globalThis.sessionStorage;const values=new Map();globalThis.sessionStorage={getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)}})
afterEach(()=>{globalThis.fetch=originalFetch;globalThis.sessionStorage=originalStorage})
test('storage accepts its own exact authorization code and wallet scanner adapts without accepting identity as storage',()=>{
 const token='WS.'+'A'.repeat(43)
 assert.equal(normalizeWalletQr(token,'storage','https://rpb.test'),token)
 assert.equal(normalizeWalletQr(token,'bill','https://rpb.test'),token)
 for(const prefix of ['WI','WP','WC','CA','CK','WB'])assert.equal(normalizeWalletQr(prefix+'.'+'A'.repeat(43),'storage','https://rpb.test'),null)
 assert.equal(normalizeWalletQr(token,'customer','https://rpb.test'),null)
 assert.equal(normalizeWalletQr('WS.invalid','storage','https://rpb.test'),null)
})
test('response loss retains the exact scoped command for recovery rather than creating a second handover',async()=>{
 const api=storageApi('store-a',true),calls=[]
 globalThis.fetch=async(url,options)=>{calls.push({url,options});if(calls.length===1)throw new Error('response lost');return{ok:true,json:async()=>({id:'intent-a'})}}
 await assert.rejects(api.command('/intents',{selections:[{itemId:'one',quantity:2}]}),/response lost/)
 assert.equal(api.pending().path,'/intents');assert.equal(storageApi('store-b',true).pending(),null)
 assert.throws(()=>api.command('/intents',{}),/Recover/)
 assert.deepEqual(await api.recover(),{id:'intent-a'});assert.equal(api.pending(),null)
 assert.equal(calls[0].options.body,calls[1].options.body)
 assert.equal(calls[0].options.headers['X-RPB-Storage'],'1')
 assert.equal(calls[0].url,'/api/v1/public/stores/store-a/wine-storage/intents')
})
test('a definitive batch rejection clears the pending command and exposes exact problems',async()=>{
 const api=storageApi('store-a',false)
 globalThis.fetch=async()=>({ok:false,status:409,json:async()=>({code:'STORAGE_BATCH_CONFLICT',message:'batch failed',problems:[{itemId:'expired',code:'EXPIRED'}]})})
 await assert.rejects(api.command('/intents/x/deliver',{}),e=>e.status===409&&e.problems[0].itemId==='expired')
 assert.equal(api.pending(),null)
})
