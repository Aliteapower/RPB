import {test} from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import {createHash} from 'node:crypto'
const root='src/modules/wine-storage/guide/'
test('shipped two-page PDF is synchronized with the web guide content',async()=>{
 const content=await readFile(root+'content.json'),pdf=await readFile(root+'guide-zh.pdf'),manifest=JSON.parse(await readFile(root+'guide-zh.manifest.json','utf8'));
 const hash=b=>createHash('sha256').update(b).digest('hex');assert.equal(hash(content),manifest.contentSha256);assert.equal(hash(pdf),manifest.pdfSha256);assert.equal(pdf.subarray(0,5).toString(),'%PDF-');assert.equal(manifest.pages,2);assert.equal(manifest.language,'zh-CN');
 const guide=JSON.parse(content);assert.deepEqual(guide.sections.map(s=>s.id),['deposit','review','pickup','code','reports','settings']);for(const section of guide.sections){assert.ok(section.steps.length>=3&&section.steps.length<=4);for(const step of section.steps)for(const lang of ['zh','en'])assert.ok(step.title[lang]&&step.text[lang])}
 assert.match(guide.sections.find(s=>s.id==='pickup').hint.zh,/立即扣减/);assert.match(guide.sections.find(s=>s.id==='code').steps[0].text.zh,/尚未扣库存/);assert.match(guide.sections.find(s=>s.id==='code').steps[2].text.zh,/扫描成功即/)
})
