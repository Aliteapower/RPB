import {before,test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
let cartSelection,cartTotals
before(async()=>{const r=await build({entryPoints:['src/modules/wine-storage/pickupCart.ts'],bundle:true,write:false,format:'esm',platform:'node'});({cartSelection,cartTotals}=await import('data:text/javascript;base64,'+Buffer.from(r.outputFiles[0].contents).toString('base64')))})
const item=(id,extra={})=>({id,status:'stored',remaining:3,opened:false,expiresAt:null,...extra})
test('cart submits positive quantities once and treats zero as removed',()=>{assert.deepEqual(cartSelection([item('a'),item('b')],{a:2,b:1,removed:0}),[{itemId:'a',quantity:2},{itemId:'b',quantity:1}]);assert.deepEqual(cartTotals({a:2,b:1,c:0}),{items:2,bottles:3})})
test('changed stock, expiry, deleted item and opened bottle limit reject the entire cart',()=>{for(const items of [[item('a',{remaining:1})],[item('a',{expiresAt:'2000-01-01'})],[],[item('a',{opened:true})]])assert.equal(cartSelection(items,{a:2}),null);assert.equal(cartSelection([item('a')],{}),null)})
test('more than fifty lines never becomes a truncated partial batch',()=>{const items=Array.from({length:51},(_,i)=>item(String(i)));assert.equal(cartSelection(items,Object.fromEntries(items.map(i=>[i.id,1]))),null)})
