import {test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
const result=await build({entryPoints:['src/modules/wine-storage/format.ts'],bundle:true,write:false,format:'esm',platform:'node'})
const {storageLocationText}=await import('data:text/javascript;base64,'+Buffer.from(result.outputFiles[0].contents).toString('base64'))
test('location display uses the frozen name, optional slot and original legacy text',()=>{
 assert.equal(storageLocationText({snapshot:{storageLocation:{id:'place',name:'旧位置名称'}},location:'A-08'}),'旧位置名称 · A-08')
 assert.equal(storageLocationText({storageLocation:{id:'place',name:'前台寄存处'},location:''}),'前台寄存处')
 assert.equal(storageLocationText({snapshot:{},location:'历史自由填写内容'}),'历史自由填写内容')
 assert.equal(storageLocationText({snapshot:{},location:''}),'—')
})
