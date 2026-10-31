import {test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
const result=await build({entryPoints:['src/modules/wine-storage/customer/selection.ts'],bundle:true,write:false,format:'esm',platform:'node'})
const {collectibleItems,withdrawalSelection}=await import('data:text/javascript;base64,'+Buffer.from(result.outputFiles[0].contents).toString('base64'))
const now=Date.parse('2026-10-25T12:00:00Z'),item={id:'mine',status:'stored',remaining:3,opened:false,expiresAt:'2026-10-26T12:00:00Z'}
test('only live positive holdings are offered for customer collection',()=>{
 assert.deepEqual(collectibleItems([item,{...item,id:'expired',expiresAt:new Date(now).toISOString()},{...item,id:'empty',remaining:0},{...item,id:'done',status:'collected'}],now),[item])
})
test('empty, stale, fractional, zero, negative and excess selections never produce a collection command',()=>{
 for(const selected of [{},{unknown:1},{mine:0},{mine:-1},{mine:1.5},{mine:4}])assert.equal(withdrawalSelection([item],selected,now),null)
 assert.equal(withdrawalSelection([{...item,expiresAt:new Date(now).toISOString()}],{mine:1},now),null)
})
test('opened bottles allow one bottle and customer payload contains only exact item quantities',()=>{
 assert.equal(withdrawalSelection([{...item,opened:true}],{mine:2},now),null)
 assert.deepEqual(withdrawalSelection([{...item,opened:true}],{mine:1},now),[{itemId:'mine',quantity:1}])
 assert.deepEqual(withdrawalSelection([item],{mine:2},now),[{itemId:'mine',quantity:2}])
})
