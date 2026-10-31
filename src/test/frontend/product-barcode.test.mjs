import {test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {writeFile,mkdir} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {normalizeGtin} from '../../modules/shared-commerce/barcode/gtin.ts'
test('GTIN preserves leading zeros and validates retail check digits',()=>{
 assert.equal(normalizeGtin('036000291452'),'00036000291452');assert.equal(normalizeGtin('0036000291452'),'00036000291452');assert.equal(normalizeGtin('036000291453'),null);assert.equal(normalizeGtin('WS.'+'A'.repeat(43)),null)
})
test('real EAN image pixels decode locally with JavaScript fallback',async()=>{
 // Standard EAN-13 fixture: first digit 6 has LGGGLL parity, quiet zones on both sides.
 const code='6901035601030',L=['0001101','0011001','0010011','0111101','0100011','0110001','0101111','0111011','0110111','0001011'],G=['0100111','0110011','0011011','0100001','0011101','0111001','0000101','0010001','0001001','0010111'],R=L.map(v=>[...v].map(c=>c==='0'?'1':'0').join(''))
 const bits='0'.repeat(12)+'101'+[...code.slice(1,7)].map((d,i)=>('LGGGLL'[i]==='L'?L:G)[Number(d)]).join('')+'01010'+[...code.slice(7)].map(d=>R[Number(d)]).join('')+'101'+'0'.repeat(12),w=bits.length*4,h=160,pixels=new Uint8ClampedArray(w*h*4)
 for(let y=0;y<h;y++)for(let x=0;x<w;x++){const pos=(y*w+x)*4,v=bits[Math.floor(x/4)]==='1'?0:255;pixels[pos]=pixels[pos+1]=pixels[pos+2]=v;pixels[pos+3]=255}
 const previous=globalThis.document;globalThis.document={createElement:()=>({getContext:()=>({drawImage(){},getImageData:()=>({data:pixels})})})}
 try{const out=await build({entryPoints:['src/modules/shared-commerce/barcode/decoder.ts'],bundle:true,write:false,format:'esm',platform:'node'});await mkdir('target/product-barcode-test',{recursive:true});await writeFile('target/product-barcode-test/decoder.mjs',out.outputFiles[0].contents);const {createBarcodeDecoder}=await import(pathToFileURL(process.cwd()+'/target/product-barcode-test/decoder.mjs').href);assert.equal(createBarcodeDecoder().image({naturalWidth:w,naturalHeight:h}),code)}finally{globalThis.document=previous}
})
