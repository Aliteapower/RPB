import {before,test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
let itemPhotos
before(async()=>{const r=await build({entryPoints:['src/modules/wine-storage/itemPhotos.ts'],bundle:true,write:false,format:'esm',platform:'node'});({itemPhotos}=await import('data:text/javascript;base64,'+Buffer.from(r.outputFiles[0].contents).toString('base64')))})
test('customer gallery combines staff receipt, original and matching follow-up photos without duplicates',()=>{
 const item={id:'bottle',snapshot:{receiptPhotos:['receipt','same'],applicationPhotos:['original','same']}}
 const events=[{sourceId:'bottle',action:'photos.added',details:{photos:['followup','same']}},{sourceId:'other',action:'photos.added',details:{photos:['foreign']}},{sourceId:'bottle',action:'corrected',details:{photos:['wrong-action']}}]
 assert.deepEqual(itemPhotos(item,events),['receipt','same','original','followup'])
})
test('empty or old snapshots and malformed event details produce no placeholder photos',()=>{
 assert.deepEqual(itemPhotos({id:'bottle',snapshot:{}},[{sourceId:'bottle',action:'photos.added',details:{photos:null}}]),[])
 assert.deepEqual(itemPhotos({id:'bottle',snapshot:{receiptPhotos:[]}},[{sourceId:'bottle',action:'photos.added',details:{photos:['',null,3]}}]),[])
})
