import {test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
const bundle=await build({stdin:{contents:"export {normalizeWalletQr} from './src/modules/wallet/scanning/walletQr.ts';export {merchantCode} from './src/modules/wallet/scanning/walletQr.ts'",resolveDir:process.cwd()},bundle:true,write:false,format:'esm',platform:'node'})
const {normalizeWalletQr,merchantCode}=await import('data:text/javascript;base64,'+Buffer.from(bundle.outputFiles[0].contents).toString('base64'))
const origin='https://test.example',store='20000000-0000-0000-0000-000000000983'
test('customer scanner accepts merchant money and redemption codes, never personal or foreign codes',()=>{
 for(const [prefix,hash] of [['WB','bill'],['WR','redeem'],['WM','order']]){const code=prefix+'.'+'A'.repeat(43),url=`${origin}/wallet/${store}#${hash}=${code}`;assert.equal(normalizeWalletQr(code,'bill',origin),code);assert.equal(normalizeWalletQr(url,'bill',origin),url);assert.deepEqual(merchantCode(url,origin),{store,code,kind:hash});assert.equal(normalizeWalletQr(url.replace(origin,'https://evil.example'),'bill',origin),null)}
 assert.equal(normalizeWalletQr('WI.'+'A'.repeat(43),'bill',origin),null)
 assert.throws(()=>merchantCode(`${origin}/wallet/${store}#bill=WR.${'A'.repeat(43)}`,origin))
})
