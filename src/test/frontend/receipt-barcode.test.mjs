import {test,before} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {mkdir,writeFile} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
let addScannedBottle
const product={id:'wine',categoryId:'category',nameZh:'白酒',nameEn:'Spirit'}
const line=(patch={})=>({lineId:crypto.randomUUID(),productId:'wine',categoryId:'category',nameZh:'白酒',nameEn:'Spirit',quantity:1,opened:false,source:'customer',ruleId:'rule',photos:[],note:'',location:'',label:'',storageLocationId:'desk',...patch})
before(async()=>{const r=await build({entryPoints:['src/modules/wine-storage/merchant/receiptBarcode.ts'],bundle:true,write:false,format:'esm',platform:'node'});await mkdir('target/receipt-barcode-test',{recursive:true});await writeFile('target/receipt-barcode-test/entry.mjs',r.outputFiles[0].contents);({addScannedBottle}=await import(pathToFileURL(process.cwd()+'/target/receipt-barcode-test/entry.mjs').href))})
test('50 line cap rejects new products while allowing a bottle on an existing eligible line',()=>{const lines=Array.from({length:50},(_,i)=>line({productId:'wine-'+i}));assert.equal(addScannedBottle(lines,product,'rule','desk'),false);assert.equal(lines.length,50);assert.equal(addScannedBottle(lines,{...product,id:'wine-0'},'rule','desk'),true);assert.equal(lines[0].quantity,2)})
test('quantity cap never increments or splits an existing eligible line',()=>{const lines=[line({quantity:10000})];assert.equal(addScannedBottle(lines,product,'rule','desk'),false);assert.equal(lines.length,1);assert.equal(lines[0].quantity,10000)})
test('a blank line is reused but labels, photos, opened state and custom locations are preserved',()=>{for(const patch of [{opened:true},{label:'keep'},{photos:['photo']},{storageLocationId:'other'},{source:'store'},{ruleId:'custom'},{note:'keep'}]){const original=line(patch),lines=[original];assert.equal(addScannedBottle(lines,product,'rule','desk'),true);assert.equal(lines.length,2);assert.equal(original.quantity,1);assert.equal(lines[1].quantity,1)}const blank=line({productId:null,categoryId:'',nameZh:'',nameEn:'',ruleId:''}),lines=[blank];assert.equal(addScannedBottle(lines,product,'rule','desk'),true);assert.equal(lines.length,1);assert.equal(lines[0].productId,'wine')})
