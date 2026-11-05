import {test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
const result=await build({entryPoints:['src/modules/cards/format.ts'],bundle:true,write:false,format:'esm',platform:'node'})
const {cardCode,businessDay,cents,displayName,dateTime}=await import('data:text/javascript;base64,'+Buffer.from(result.outputFiles[0].contents).toString('base64'))
test('card merchant and authorization QR have separate purposes and reject foreign origins',()=>{const token='CK.'+'A'.repeat(43),url=`https://rpb.test/wallet/20000000-0000-0000-0000-000000000983/cards#card=${token}`;assert.equal(cardCode(url,'https://rpb.test'),token);assert.equal(cardCode(url,'https://evil.test'),null);assert.equal(cardCode(token,'https://rpb.test','CA'),null);assert.equal(cardCode('WI.'+'A'.repeat(43),'https://rpb.test'),null)})
test('card day uses the frozen zone, cents reject fractional minor units and business names retain entered data',()=>{assert.equal(businessDay('Asia/Singapore',new Date('2026-10-25T16:00:00Z')),'2026-10-26');assert.equal(cents('200.05'),20005);assert.throws(()=>cents('1.005'));assert.equal(displayName({nameZh:'咖啡',nameEn:''},'en-SG'),'咖啡')})

test('display dates follow the card timezone and fixed DD-MM-YYYY format',()=>{assert.equal(dateTime('2026-10-25T16:00:00Z','Asia/Singapore'),'26-10-2026 00:00')})
