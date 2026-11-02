import {before,afterEach,test} from 'node:test'
import assert from 'node:assert/strict'
import {build} from 'esbuild'
import {parse,compileScript} from '@vue/compiler-sfc'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {pathToFileURL} from 'node:url'
import {createRenderer,h,nextTick} from 'vue'
import {createI18n} from 'vue-i18n'
import {createRouter,createMemoryHistory} from 'vue-router'
let Page,historyQuery
const mounted=[],originalFetch=globalThis.fetch
function element(tag){const n={tag,props:{},children:[],style:{},addEventListener(){},removeEventListener(){}};Object.defineProperty(n,'options',{get(){return nodes(n,x=>x.tag==='option')}});return n}
const renderer=createRenderer({createElement:element,createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},patchProp:(n,k,o,v)=>n.props[k]=v,insert:(n,p,a)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:n=>{const a=n.parent?.children||[];return a[a.indexOf(n)+1]||null}})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>(n.text||'')+(n.children||[]).map(text).join('')
const settle=async()=>{for(let i=0;i<25;i++){await Promise.resolve();await nextTick()}}
const response=(data,status=200)=>({ok:status<400,status,json:async()=>data})
before(async()=>{
 const result=await build({stdin:{contents:"export {default as Page} from './src/modules/workforce/pages/WorkforceHistoryPage.vue';export {historyQuery} from './src/modules/workforce/history'",resolveDir:process.cwd(),loader:'ts'},bundle:true,write:false,format:'esm',platform:'node',external:['vue','vue-router','vue-i18n'],loader:{'.css':'empty'},plugins:[{name:'vue',setup(b){b.onLoad({filter:/\.vue$/},async({path})=>{const {descriptor}=parse(await readFile(path,'utf8'),{filename:path});return {contents:compileScript(descriptor,{id:path,inlineTemplate:true}).content,loader:'ts'}})}}]})
 await mkdir('target',{recursive:true});await writeFile('target/workforce-history-ui-test.mjs',result.outputFiles[0].contents)
 ;({Page,historyQuery}=await import(pathToFileURL(process.cwd()+'/target/workforce-history-ui-test.mjs')))
})
afterEach(()=>{mounted.splice(0).forEach(a=>a.unmount());globalThis.fetch=originalFetch})
async function mount(path='/stores/A/workforce-history?history=1&tab=mine&year=2026'){
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/stores/:storeId/workforce-history',component:Page},{path:'/stores/:storeId/staff',component:{render:()=>null}}]});await router.push(path);await router.isReady();const root=element('root'),app=renderer.createApp({render:()=>h(Page)});app.use(router);app.use(createI18n({legacy:false,locale:'zh-CN',messages:{},missingWarn:false,fallbackWarn:false}));app.mount(root);mounted.push(app);await settle();return {root,router}
}
test('historical list uses only historical GETs and retains explicit read-only context in details',async()=>{
 const requests=[];globalThis.fetch=async(url,opt)=>{requests.push({url:String(url),method:opt.method});if(String(url).endsWith('/context'))return response({self:true,employeeManage:false,companies:[]});if(String(url).includes('/payslips?'))return response([{batchId:'b',companyName:'Company',month:'2026-10',netPay:'100',paidAmount:'50',status:'published'}]);return response([])}
 const {root}=await mount();assert.match(text(root),/当前只读/);assert.match(text(root),/Company/);assert.equal(requests.length,4);assert.ok(requests.every(r=>r.method==='GET'&&r.url.includes('/workforce/history/')));assert.ok(!requests.some(r=>/\/home|payroll-home/.test(r.url)))
 const details=nodes(root,n=>n.tag==='a'&&text(n)==='查看只读明细')[0];assert.match(details.props.href,/history=1/);assert.match(details.props.href,/id=b/)
 assert.ok(!nodes(root,n=>n.tag==='button').some(n=>/核准|保存|登记付款|算薪|提交/.test(text(n))))
})
test('denied history clears the old company and amounts instead of showing an empty successful list',async()=>{
 globalThis.fetch=async url=>String(url).endsWith('/context')?response({self:true,employeeManage:false,companies:[]}):String(url).includes('/payslips?')?response([{batchId:'b',companyName:'OLD PRIVATE',month:'2026-10',netPay:'98765',paidAmount:'0'}]):response([])
 const {root,router}=await mount();assert.match(text(root),/OLD PRIVATE/)
 globalThis.fetch=async()=>response({code:'WORKFORCE_FORBIDDEN'},403)
 await router.push('/stores/B/workforce-history?history=1&tab=mine&year=2026');await settle();assert.doesNotMatch(text(root),/OLD PRIVATE|98765/);assert.ok(nodes(root,n=>n.props?.role==='alert').length)
})
test('month history provides pagination with no update judgment or exception commands',async()=>{
 globalThis.fetch=async url=>String(url).endsWith('/context')?response({self:false,employeeManage:true,companies:[]}):response({month:'2026-10',employees:[{employeeId:'e',employeeNo:'E1',name:'Alice',days:[{workDate:'2026-10-01',state:'human_valid',workSeconds:28800,issues:[],snapshot:{}}]}],total:31,offset:0,limit:30,canManage:true})
 const {root}=await mount('/stores/A/workforce-history?history=1&tab=month&month=2026-10');assert.match(text(root),/Alice/);assert.doesNotMatch(text(root),/更新本页本月判定|核准当天有效来源/)
 const next=nodes(root,n=>n.tag==='a'&&text(n)==='下一页')[0];assert.match(next.props.href,/history=1/);assert.match(next.props.href,/offset=30/)
})
test('navigation always restores history=1 and removes detail parameters on return',()=>{
 assert.deepEqual(historyQuery({history:'0',tab:'managed',id:'secret',employeeId:'e'},{id:undefined,employeeId:undefined}),{history:'1',tab:'managed'})
})
test('a slow old-store history response cannot overwrite the current store',async()=>{
 let release;globalThis.fetch=async url=>{const u=String(url);if(u.endsWith('/context'))return response({self:true,employeeManage:false,companies:[]});if(u.includes('/stores/A/')&&u.includes('/payslips?'))return new Promise(resolve=>release=()=>resolve(response([{batchId:'old',companyName:'STALE STORE A',month:'2026-10',netPay:'999',paidAmount:'0'}])));if(u.includes('/payslips?'))return response([{batchId:'new',companyName:'CURRENT STORE B',month:'2026-10',netPay:'123',paidAmount:'0'}]);return response([])}
 const {root,router}=await mount();assert.equal(typeof release,'function');await router.push('/stores/B/workforce-history?history=1&tab=mine&year=2026');await settle();assert.match(text(root),/CURRENT STORE B/);release();await settle();assert.match(text(root),/CURRENT STORE B/);assert.doesNotMatch(text(root),/STALE STORE A/)
})
test('managed payroll details use explicit salary scope and return to historical company list',async()=>{
 const requests=[];globalThis.fetch=async url=>{const u=String(url);requests.push(u);if(u.endsWith('/context'))return response({self:false,employeeManage:false,companies:[{id:'C',name:'Payroll company'}]});return response({companyName:'Company',month:'2026-10',line:{employeeName:'Alice',employeeNo:'E1',netPay:'100'},paidAmount:'0',remainingAmount:'100',payments:[]})}
 const {root}=await mount('/stores/A/workforce-history?history=1&tab=managed&year=2026&companyId=C&id=batch&employeeId=E');assert.ok(requests.some(u=>u.endsWith('/payslips/batch?scope=managed&employeeId=E')));const back=nodes(root,n=>n.tag==='a'&&text(n)==='返回只读列表')[0];assert.match(back.props.href,/history=1/);assert.match(back.props.href,/companyId=C/);assert.doesNotMatch(back.props.href,/employeeId|id=batch/)
})
test('company history includes frozen independent and supplemental slips through salary-scoped GETs only',async()=>{
 const requests=[];globalThis.fetch=async(url,opt)=>{const u=String(url);requests.push({u,method:opt.method});if(u.endsWith('/context'))return response({self:false,employeeManage:false,companies:[{id:'C',name:'Company'}]});if(u.includes('/segments?'))return response([{id:'s',companyName:'Company',employeeName:'Alice segment',from:'2026-10-01',cutoff:'2026-10-15',status:'confirmed',net:'300',paid:'0',remaining:'300',breakdown:{items:[]},payments:[]}]);if(u.includes('/supplements?'))return response([{batchId:'b',companyName:'Company',employeeName:'Bob supplement',originMonth:'2026-09',settlementMonth:'2026-10',status:'published',employeeCashPayable:'50',paidAmount:'0',remainingAmount:'50',items:[{label:'Bonus',classification:'AW',amount:'50'}]}]);return response([])}
 const {root}=await mount('/stores/A/workforce-history?history=1&tab=managed&year=2026&companyId=C');assert.match(text(root),/Alice segment/);assert.match(text(root),/Bob supplement/);assert.match(text(root),/已确认/);assert.ok(requests.filter(r=>/\/segments\?|\/supplements\?/.test(r.u)).every(r=>r.method==='GET'&&r.u.includes('scope=managed')&&r.u.includes('companyId=C')));assert.ok(!nodes(root,n=>n.tag==='button').some(n=>/确认|发布|登记付款|保存/.test(text(n))))
})
