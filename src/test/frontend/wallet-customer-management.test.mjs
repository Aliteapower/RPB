import {test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
const result=await build({stdin:{contents:"export * from './src/modules/customers/navigation.ts'",resolveDir:process.cwd()},bundle:true,write:false,platform:'node',format:'esm'})
const {customerManagementTarget,customerManagementReturnTarget,customerPhonePrefill}=await import('data:text/javascript;base64,'+Buffer.from(result.outputFiles[0].contents).toString('base64'))

test('wallet management and missing-phone creation target the existing tenant directory',()=>{
 assert.equal(customerManagementTarget('A','wallet'),'/stores/A/admin/customers?from=wallet')
 const url=new URL(customerManagementTarget('A','wallet-recharge','+65 81112469',true),'https://test.local')
 assert.equal(url.pathname,'/stores/A/admin/customers');assert.equal(url.searchParams.get('keyword'),'+65 81112469');assert.equal(url.searchParams.get('create'),'1')
})
test('return navigation only accepts named wallet sources and stays in the current store',()=>{
 assert.equal(customerManagementReturnTarget('A','wallet'),'/stores/A/wallet')
 assert.equal(customerManagementReturnTarget('B','wallet-recharge'),'/stores/B/wallet/topup?entry=phone')
 for(const value of ['https://evil.test','//evil.test','/stores/A/wallet',undefined,['wallet']])assert.equal(customerManagementReturnTarget('A',value),null)
})
test('phone prefill uses the existing Singapore formatter and preserves international numbers',()=>{
 assert.equal(customerPhonePrefill('81112469'),'+6581112469');assert.equal(customerPhonePrefill('+86 138-0013-8000'),'+8613800138000')
 for(const value of ['123','wallet customer','81112469 extra','12345678901234567890'])assert.equal(customerPhonePrefill(value),'')
})
