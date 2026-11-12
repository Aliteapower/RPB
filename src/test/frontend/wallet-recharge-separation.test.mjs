import {test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
const b=await build({stdin:{contents:"export * from './src/modules/wallet/pointsOperations.ts'",resolveDir:process.cwd()},bundle:true,write:false,platform:'node',format:'esm'})
const {canReversePoints,rechargeRecordsTarget}=await import('data:text/javascript;base64,'+Buffer.from(b.outputFiles[0].contents).toString('base64'))
const row={id:'e',sourceType:'points',type:'points_grant',status:'completed',customerId:'c'}
test('reversal accepts only unreversed completed original points transactions',()=>{
 assert.equal(canReversePoints({transaction:row,related:[]}),true)
 for(const transaction of [{...row,type:'reversal'},{...row,status:'pending'},{...row,sourceType:'bill'},{...row,customerId:null}])assert.equal(canReversePoints({transaction,related:[]}),false)
 assert.equal(canReversePoints({transaction:row,related:[{type:'reversal',originalId:'e'}]}),false)
})
test('record links use the unified folder and existing phone filter, never customer identity overrides',()=>{
 assert.deepEqual(rechargeRecordsTarget('A','points',{phone:'+6581234567'}),{path:'/stores/A/wallet/transactions',query:{category:'points',customerQuery:'+6581234567'}})
 assert.deepEqual(rechargeRecordsTarget('B','topup',{}),{path:'/stores/B/wallet/transactions',query:{category:'topup'}})
})
