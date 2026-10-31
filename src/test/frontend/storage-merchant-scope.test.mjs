import {test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
const result=await build({entryPoints:['src/modules/wine-storage/merchant/scope.ts'],bundle:true,write:false,format:'esm',platform:'node'})
const {customerStorage,merchantSelection}=await import('data:text/javascript;base64,'+Buffer.from(result.outputFiles[0].contents).toString('base64'))
const now=Date.parse('2026-10-25T12:00:00Z'),one={id:'one',customerId:'A',status:'stored',remaining:3,opened:false,expiresAt:'2027-01-01T00:00:00Z'},two={...one,id:'two',customerId:'B'}
test('merchant view keeps applications, bottles, collections and events scoped to the explicitly chosen customer',()=>{
 const home={applications:[{id:'a',customerId:'A'},{id:'b',customerId:'B'}],items:[one,two],intents:[{id:'ia',customerId:'A'},{id:'ib',customerId:'B'}],events:[{sourceId:'one'},{sourceId:'b'},{sourceId:'ia'}]}
 const view=customerStorage(home,'A');assert.deepEqual(view.items,[one]);assert.equal(view.applications.length,1);assert.equal(view.intents.length,1);assert.deepEqual(view.events,[{sourceId:'one'},{sourceId:'ia'}]);assert.equal(home.items.length,2)
})
test('merchant cannot submit an empty or mixed-customer collection, expired item or excess amount',()=>{
 for(const values of [{},{two:1},{one:1,two:1},{one:0},{one:4},{one:1.5}])assert.equal(merchantSelection('A',[one,two],values,now),null)
 assert.equal(merchantSelection('A',[{...one,expiresAt:new Date(now).toISOString()}],{one:1},now),null)
 assert.deepEqual(merchantSelection('A',[one,two],{one:2},now),{customerId:'A',selections:[{itemId:'one',quantity:2}]})
})
