import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,mkdirSync,writeFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';
import ts from 'typescript';
import {createRenderer,h,computed,nextTick,provide} from 'vue';
import {createI18n} from 'vue-i18n';
import {buildSync} from 'esbuild';
const out='target/daily-ledger/frontend-tests';mkdirSync(out,{recursive:true});
for(const name of ['model','api','report','charts','storage','useLedgerEditor']){
  const source=readFileSync(`src/modules/daily-ledger/${name}.ts`,'utf8');
  const compiled=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText.replace(/from '(\.\/[^']+)'/g,"from '$1.mjs'");
  writeFileSync(`${out}/${name}.mjs`,compiled);
}
const model=await import(pathToFileURL(`${process.cwd()}/${out}/model.mjs`));
const {historyReport,csv}=await import(pathToFileURL(`${process.cwd()}/${out}/report.mjs`));
const {useLedgerEditor}=await import(pathToFileURL(`${process.cwd()}/${out}/useLedgerEditor.mjs`));
const {chartData}=await import(pathToFileURL(`${process.cwd()}/${out}/charts.mjs`));
const {storageKey,readLocal,writeLocal}=await import(pathToFileURL(`${process.cwd()}/${out}/storage.mjs`));
for(const name of ['i18n','format','locales/en-SG','locales/zh-CN']) buildSync({entryPoints:[`src/modules/daily-ledger/${name}.ts`],bundle:true,platform:'node',format:'esm',packages:'external',outfile:`${out}/${name}.mjs`});
const {useLedgerFormat,displayKey}=await import(pathToFileURL(`${process.cwd()}/${out}/format.mjs`));
const {useLedgerI18n}=await import(pathToFileURL(`${process.cwd()}/${out}/i18n.mjs`));
const {ledgerApi}=await import(pathToFileURL(`${process.cwd()}/${out}/api.mjs`));
const {ledgerMessages:en}=await import(pathToFileURL(`${process.cwd()}/${out}/locales/en-SG.mjs`));
const {ledgerMessages:zh}=await import(pathToFileURL(`${process.cwd()}/${out}/locales/zh-CN.mjs`));
const config={store_name:'Test',currency:'SGD',base_amount:'500.00',final_period:'dinner',periods:[{id:'lunch',name:'午餐',enabled:true},{id:'dinner',name:'晚餐',enabled:true}],payments:[{id:'paynow',name:'PayNow',enabled:true}],expenses:[]};
const day=()=>({date:'2026-10-22',config:structuredClone(config),records:{},submitted_records:{},last_period:null,locked_config:false});
test('final-only report preserves old submitted snapshot and uses integer cents',()=>{
  const d=day(),r=model.blankRecord(config);r.cash['100'].value='10';r.payments.paynow.value='200.10';r.totals=model.totals(r);r.saved_by='=SUM(A1)';
  d.records={lunch:structuredClone(r),dinner:structuredClone(r)};d.records.dinner.totals.revenue='9999.00';d.submitted_records={dinner:r};d.last_period='dinner';
  const result=historyReport([d]);assert.equal(result.totals.revenue,70010);assert.equal(result.channels[0].amount,20010);assert.match(csv([d]),/'=SUM\(A1\)/);
  d.last_period=null;assert.equal(historyReport([d]).totals.revenue,0);
});
test('source basis tracks every earlier period and has no source on intermediate shift',()=>{
  const d=day();d.records.lunch={version:3};assert.deepEqual(model.basis(d,'dinner'),[{period:'lunch',version:3}]);assert.deepEqual(model.basis(d,'lunch'),[]);
});
test('chart includes missing dates, monthly totals, expense mode and negative adjustments',()=>{
  const d=day(),r=model.blankRecord(config);r.totals={...r.totals,revenue:'-100.00',expenses:'20.00'};d.last_period='dinner';d.submitted_records.dinner=r;
  const chart=chartData([d],'2026-10-21','2026-10-23','income');assert.equal(chart.daily.length,3);assert.equal(chart.daily[0].submitted,false);assert.equal(chart.monthly[0].amount,-10000);assert.equal(chart.categories[0].amount,-10000);
  assert.equal(chartData([d],'2026-10-21','2026-10-23','expense').total,2000);
});
const renderer=createRenderer({createElement:()=>({}),createText:()=>({}),createComment:()=>({}),setText(){},setElementText(){},parentNode:()=>null,nextSibling:()=>null,insert(){},remove(){},patchProp(){}});
function storage(){const map=new Map();return {getItem:key=>map.get(key)??null,setItem:(key,value)=>map.set(key,value),removeItem:key=>map.delete(key)};}
function editor(local=storage(),initial=day(),period='lunch'){globalThis.window=new EventTarget();window.localStorage=local;let result;const app=renderer.createApp({setup(){result=useLedgerEditor('store-a',initial,period,false,'tenant/account');return()=>h('div')}});app.mount({});return {result,close:()=>app.unmount()};}
function response(body){return {ok:true,status:200,json:async()=>body};}
function saved(request){const d=day(),r=model.blankRecord(config);Object.assign(r,{...request,version:request.version+1,status:request.action==='submit'?'submitted':'draft'});r.totals=model.totals(r);d.records.lunch=r;if(request.action==='submit')d.submitted_records.lunch=r;return d;}
test('edits during in-flight autosave survive and are saved serially',async()=>{
  const {result:e,close}=editor();let release;const requests=[];
  globalThis.fetch=async(url,init)=>{const req=JSON.parse(init.body);requests.push({url,req});if(requests.length===1)await new Promise(resolve=>release=resolve);return response(saved(req));};
  e.record.value.cash['100'].value='1';e.changed();const saving=e.save('draft');
  e.record.value.cash['100'].value='2';e.changed();release();assert.equal(await saving,true);
  assert.equal(e.record.value.cash['100'].value,'2');assert.equal(requests.length,2);assert.equal(requests[1].req.version,1);assert.ok(requests.every(r=>r.url.includes('/stores/store-a/')));close();
});
test('network retry preserves exact idempotency body without losing newer edits',async()=>{
  const {result:e,close}=editor();const requests=[];let first=true;
  globalThis.fetch=async(url,init)=>{const req=JSON.parse(init.body);requests.push(req);if(first){first=false;throw Error('network lost');}return response(saved(req));};
  e.record.value.cash['100'].value='1';e.changed();assert.equal(await e.save('draft'),false);
  e.record.value.cash['100'].value='2';e.changed();assert.equal(await e.save('draft'),true);
  assert.deepEqual(requests[0],requests[1]);assert.notEqual(requests[2].client_save_id,requests[0].client_save_id);assert.equal(e.record.value.cash['100'].value,'2');close();
});
test('clear restore preserves current cloud version and source metadata',()=>{
  const backup=model.blankRecord(config);backup.version=2;backup.cash['100'].value='9';
  const current=model.blankRecord(config);current.version=3;current.withdrawal_snapshot=[{id:'s',value:'200'}];
  const restored=model.restoreInputs(current,backup,true);assert.equal(restored.version,3);assert.equal(restored.cash['100'].value,'9');assert.deepEqual(restored.withdrawal_snapshot,current.withdrawal_snapshot);
});
test('durable outbox survives unmount and replays exact pending request',async()=>{
  const local=storage(),firstEditor=editor(local),requests=[];let fail=true;
  globalThis.fetch=async(url,init)=>{const req=JSON.parse(init.body);requests.push(req);if(fail)throw Error('offline');return response(saved(req));};
  firstEditor.result.record.value.cash['100'].value='7';firstEditor.result.changed();assert.equal(await firstEditor.result.save('draft'),false);firstEditor.close();
  const next=editor(local);assert.equal(next.result.record.value.cash['100'].value,'7');fail=false;assert.equal(await next.result.save('draft'),true);assert.deepEqual(requests[0],requests[1]);next.close();
  assert.notEqual(storageKey('tenant/account','store-a','2026-10-22','lunch'),storageKey('tenant/other','store-a','2026-10-22','lunch'));
});
test('400 correction sends edited request while network uncertainty retains identity',async()=>{
  const e=editor();const requests=[];globalThis.fetch=async(url,init)=>{const req=JSON.parse(init.body);requests.push(req);return requests.length===1?{ok:false,status:400,json:async()=>({error:{code:'REQUEST_INVALID',message:'invalid'}})}:response(saved(req));};
  e.result.record.value.cash['100'].value='-1';e.result.changed();assert.equal(await e.result.save('draft'),false);
  e.result.record.value.cash['100'].value='1';e.result.changed();assert.equal(await e.result.save('draft'),true);assert.equal(requests[1].cash['100'].value,'1');assert.notEqual(requests[0].client_save_id,requests[1].client_save_id);e.close();
});
test('offline final-shift cache does not hide changed upstream skimming after reload',()=>{
  const local=storage(),initial=day();initial.records.lunch={...model.blankRecord(config),version:1,withdrawals:[{id:'w',value:'100.00',note:'',entered_at:null}]};
  const first=editor(local,initial,'dinner');first.result.record.value.cash['100'].value='10';first.result.changed();first.close();
  const latest=structuredClone(initial);latest.records.lunch.version=2;latest.records.lunch.withdrawals[0].value='200.00';
  const next=editor(local,latest,'dinner');assert.equal(next.result.dirty.value,false);assert.equal(next.result.record.value.withdrawal_snapshot[0].value,'200.00');assert.equal(next.result.recoveryBackup.value.cash['100'].value,'10');assert.match(next.result.error.value,/本机草稿/);next.close();
});

test('locale switch updates existing statuses and errors without changing ledger inputs or saving',async()=>{
  assert.deepEqual(Object.keys(en).sort(),Object.keys(zh).sort());
  for(const message of Object.values(en))assert.doesNotMatch(message,/[\u4e00-\u9fff]/);
  const i18n=createI18n({legacy:false,locale:'zh-CN',messages:{'zh-CN':{dailyLedger:{messages:zh}},'en-SG':{dailyLedger:{messages:en}}}});
  globalThis.window=new EventTarget();window.localStorage=storage();let e,ui,status,error,requests=0;
  globalThis.fetch=async()=>{requests++;throw Error('unexpected save');};
  const initial=day(),before=structuredClone(initial);
  const app=renderer.createApp({setup(){e=useLedgerEditor('store-i18n',initial,'lunch',false,'tenant/account');ui=useLedgerI18n();status=computed(()=>ui.text(e.status.value));error=computed(()=>ui.errorText(e.error.value));return()=>h('div')}});
  app.use(i18n);app.mount({});e.status.value='草稿已自动保存云端';e.error.value='数量须为非负整数，金额最多两位小数';e.record.value.cash['100'].value='17';e.record.value.cash['100'].note='原始备注';
  i18n.global.locale.value='en-SG';await nextTick();
  assert.equal(status.value,'Draft saved to cloud automatically');assert.match(error.value,/non-negative integers/);
  assert.equal(ui.text('第 {version} 版',{version:12}),'Version 12');assert.equal(ui.itemName({id:'lunch',name:'午餐'}),'Lunch');
  assert.equal(ui.itemName({id:'lunch',name:'午市自定义'}),'午市自定义');assert.equal(ui.itemName({id:'custom',name:'午餐'}),'午餐');
  assert.equal(e.record.value.cash['100'].value,'17');assert.equal(e.record.value.cash['100'].note,'原始备注');assert.deepEqual(initial,before);assert.equal(requests,0);
  assert.match(ui.errorText('Error: 未知后台错误'),/Please retry/);
  i18n.global.locale.value='zh-CN';await nextTick();assert.equal(status.value,'草稿已自动保存云端');app.unmount();
});

test('English CSV and chart labels preserve financial values, custom names and formula escaping',()=>{
  const i18n=createI18n({legacy:false,locale:'en-SG',messages:{'en-SG':{dailyLedger:{messages:en}}}});let ui;
  const app=renderer.createApp({setup(){ui=useLedgerI18n();return()=>h('div')}});app.use(i18n);app.mount({});
  const d=day(),r=model.blankRecord(config);r.cash['100'].value='10';r.payments.paynow.value='200.10';r.totals=model.totals(r);r.saved_by='=SUM(A1)';d.last_period='dinner';d.submitted_records.dinner=r;
  d.config.payments[0].name='自定义渠道';const before=JSON.stringify(d);
  const english=csv([d],ui.text,ui.itemName),chinese=csv([d]);
  assert.match(english,/"Date","Final reporting shift"/);assert.match(english,/"Dinner"/);assert.match(english,/"Search totals"/);assert.match(english,/自定义渠道/);assert.match(english,/'=SUM\(A1\)/);
  const cells=text=>[...text.matchAll(/"(-?\d+\.\d+)"/g)].map(m=>m[1]);assert.deepEqual(cells(english),cells(chinese));assert.equal(JSON.stringify(d),before);
  const chart=chartData([d],d.date,d.date,'income',ui.text);assert.equal(chart.categories.find(c=>c.id==='cash').name,'Cash income');assert.equal(chart.total,historyReport([d]).totals.revenue);app.unmount();
});

test('API errors retain stable status/code with actionable localized session, permission and conflict messages',async()=>{
  const i18n=createI18n({legacy:false,locale:'en-SG',messages:{'en-SG':{dailyLedger:{messages:en}}}});let ui;
  const app=renderer.createApp({setup(){ui=useLedgerI18n();return()=>h('div')}});app.use(i18n);app.mount({});
  for(const [status,code,message,expected] of [
    [401,'UNAUTHORIZED','后台登录失败',/sign in again/],
    [403,'APP_GATE_DENIED','产品线未开通',/permission/],
    [409,'VERSION_CONFLICT','后台版本错误',/record has changed/],
    [409,'SOURCE_CHANGED','当日配置已更新，请重新加载核对',/configuration changed/],
    [400,'REQUEST_INVALID','金额格式无效',/Invalid amount format/],
    [500,'SERVER_ERROR','后台堆栈',/Service unavailable/]
  ]){
    globalThis.fetch=async()=>({ok:false,status,json:async()=>({error:{code,message}})});
    await assert.rejects(ledgerApi('scoped-store').settings(),e=>e.status===status&&e.code===code&&expected.test(ui.errorText(e.message)));
  }app.unmount();
});

test('localized timestamps retain store timezone and configured date/time formats',async()=>{
  const i18n=createI18n({legacy:false,locale:'en-SG',messages:{}});let format;
  const settings={timezone:'Asia/Singapore',locale:'zh-CN',dateFormat:'DD-MM-YYYY',timeFormat:'HH:mm',currency:'SGD'};
  const app=renderer.createApp({setup(){provide(displayKey,computed(()=>settings));return()=>h({setup(){format=useLedgerFormat();return()=>h('div')}})}});app.use(i18n);app.mount({});
  const instant='2026-10-22T16:30:00Z';assert.match(format.time(instant),/23\/10\/2026/);assert.match(format.clock(instant),/00:30:00/);assert.equal(format.date('2026-10-23'),'23-10-2026');
  i18n.global.locale.value='zh-CN';await nextTick();assert.match(format.time(instant),/2026\/10\/23/);assert.equal(format.date('2026-10-23'),'23-10-2026');assert.equal(settings.timezone,'Asia/Singapore');app.unmount();
});
