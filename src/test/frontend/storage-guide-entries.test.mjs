import {before,test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {PDFDocument,PDFName,PDFString} from 'pdf-lib'
import QRCode from 'qrcode'
import {pathToFileURL} from 'node:url'
let guideEntries,appendGuideEntries
before(async()=>{globalThis.window={__RPB_HOST_PREFIX_BASE_HOST__:'booking.yumstone.sg'};for(const [name,file] of [['guideEntries','entries'],['appendGuideEntries','entryPdf']]){const r=await build({entryPoints:['src/modules/wine-storage/guide/'+file+'.ts'],bundle:true,write:false,format:'esm',platform:'node',external:['pdf-lib'],define:{'import.meta.env':'{}'}});const dir='target/storage-guide-entry-tests';await mkdir(dir,{recursive:true});const path=dir+'/'+file+'.mjs';await writeFile(path,r.outputFiles[0].contents);const m=await import(pathToFileURL(process.cwd()+'/'+path).href);if(name==='guideEntries')guideEntries=m[name];else appendGuideEntries=m[name]}})
const store={storeId:'11111111-1111-4111-8111-111111111111',tenantId:'tenant-a',tenantCode:'mw',storeName:'[QA] 核对门店',storeCode:'A',status:'active'},loc={hostname:'mw.booking.yumstone.sg',protocol:'https:',origin:'https://mw.booking.yumstone.sg',port:''}
test('guide URLs reuse shared product routes and stable host for the selected store and tenant',()=>{
 const entries=guideEntries(store,'tenant-a',true,loc),staff=new URL(entries.staff);assert.equal(staff.origin,'https://booking.yumstone.sg');assert.equal(staff.searchParams.get('entry'),'staff');assert.equal(staff.searchParams.get('tenantCode'),'mw');assert.equal(staff.searchParams.get('redirect'),'/stores/'+store.storeId+'/wine-storage');assert.equal(entries.customer,'https://booking.yumstone.sg/wallet/'+store.storeId+'/wine-storage');assert.equal(staff.username,'');assert.equal(staff.password,'');
 for(const code of ['new-tenant','90000001'])assert.equal(new URL(guideEntries({...store,tenantCode:code},'tenant-a',true,loc).staff).hostname,'booking.yumstone.sg')
 assert.equal(guideEntries({...store,storeId:'store-b'},'tenant-a',true,loc).customer,'https://booking.yumstone.sg/wallet/store-b/wine-storage')
})
test('unavailable products, foreign tenants and inactive stores never produce guide entries',()=>{for(const result of [guideEntries(store,'foreign',true,loc),guideEntries(store,'tenant-a',false,loc),guideEntries(undefined,'tenant-a',true,loc),guideEntries({...store,status:'inactive'},'tenant-a',true,loc)])assert.equal(result,null);assert.throws(()=>guideEntries({...store,tenantCode:'bad.code'},'tenant-a',true,loc))})
test('store PDF preserves two guide pages and appends exact staff/customer URLs and clickable links',async()=>{
 const entries=guideEntries(store,'tenant-a',true,loc),base=new Uint8Array(await readFile('src/modules/wine-storage/guide/guide-zh.pdf')),image=async url=>'data:image/png;base64,'+(await QRCode.toBuffer(url,{width:1024,margin:4,errorCorrectionLevel:'H'})).toString('base64'),bytes=await appendGuideEntries(base,entries,{staff:await image(entries.staff),customer:await image(entries.customer)},new Uint8Array(await readFile('src/modules/wine-storage/guide/entry-template.pdf'))),pdf=await PDFDocument.load(bytes);assert.equal(pdf.getPageCount(),3);const annotations=pdf.getPage(2).node.Annots().asArray().map(r=>pdf.context.lookup(r).lookup(PDFName.of('A')).lookup(PDFName.of('URI'),PDFString).decodeText());assert.deepEqual(annotations,[entries.staff,entries.customer]);assert.match(pdf.getTitle(),/核对门店/);await writeFile('target/storage-guide-entry-tests/store-guide-qa.pdf',bytes);await writeFile('target/storage-guide-entry-tests/entries.json',JSON.stringify(entries))
})
