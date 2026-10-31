import {test,before,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {createRenderer,h,nextTick,ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {createRouter,createMemoryHistory} from 'vue-router'
import {statutoryPolicyValid,copyPolicyYear,draftFromTemplate,safeSource} from '../../../src/modules/workforce/statutoryPolicy.ts'
const mounted=[],originalFetch=globalThis.fetch
let Page,workforceKey
function element(tag){const n={tag,props:{},children:[],addEventListener(){},removeEventListener(){},getRootNode(){return {}},style:{}};Object.defineProperty(n,'options',{get(){return nodes(n,x=>x.tag==='option')}});return n}
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>(n.text||'')+(n.children||[]).map(text).join(' ')
const renderer=createRenderer({createElement:element,createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},patchProp:(n,k,o,v)=>{n.props[k]=v;if(k==='value')n.value=v},insert:(n,p,a)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:n=>{const a=n.parent?.children||[];return a[a.indexOf(n)+1]||null}})
const settle=async()=>{for(let i=0;i<30;i++){await Promise.resolve();await nextTick()}}
const response=(data,status=200)=>({ok:status<400,status,json:async()=>data})


const config=()=>({schemaVersion:1,owCeiling:'8000',ageBands:[{code:'55',name:'55及以下',upperAge:55},{code:'UP',name:'55以上',upperAge:null}],wageBands:[{code:'LOW',name:'低工资',upperWage:'50',method:'none',rampMultiplier:'0'},{code:'EMPLOYER_ONLY',name:'仅雇主',upperWage:'500',method:'employer_only',rampMultiplier:'0'},{code:'RAMP',name:'渐进',upperWage:'750',method:'ramp',rampMultiplier:'3'},{code:'FULL',name:'标准',upperWage:null,method:'normal',rampMultiplier:'0'}],tables:['T1','T2','T3','T4','T5'].map(code=>({code,name:code,rates:['55','UP'].map(ageCode=>({ageCode,employerRate:'0.17',employeeRate:'0.20'}))})),sdl:{rate:'0.0025',minimum:'2',maximum:'11.25'},totalCpfRounding:'HALF_UP',employeeCpfRounding:'FLOOR',companySdlRounding:'FLOOR',cpfSource:'https://www.cpf.gov.sg/',sdlSource:'https://www.ssg.gov.sg/',ageTiming:'next_month',prTiming:'next_month',notes:''})
const policy=(extra={})=>({id:'P',name:'2026规则',year:2026,currency:'SGD',effectiveFrom:'2026-01-01',effectiveTo:'2026-12-31',enabled:true,version:2,config:config(),...extra})
before(async()=>{globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document={};Page=(await import('../../../src/modules/workforce/pages/StatutoryPolicySettings.vue')).default;workforceKey=(await import('../../../src/modules/workforce/context.ts')).workforceKey})
afterEach(()=>{mounted.splice(0).forEach(a=>a.unmount());globalThis.fetch=originalFetch})
async function mount(admin=true){const router=createRouter({history:createMemoryHistory(),routes:[{path:'/stores/:storeId/workforce/statutory-policies',component:Page}]});await router.push('/stores/A/workforce/statutory-policies');await router.isReady();const root=element('root'),app=renderer.createApp({render:()=>h(Page)});app.use(router);app.use(createI18n({legacy:false,locale:'zh-CN',messages:{}}));app.provide(workforceKey,{store:ref('A'),home:ref({employeeManage:admin,headOffice:admin}),current:()=>true,api:()=>({get:async path=>(await fetch(path)).json(),put:async(path,body)=>(await fetch(path,{body})).json()})});app.mount(root);mounted.push(app);await settle();return root}
const button=(root,label)=>nodes(root,n=>n.tag==='button'&&text(n).includes(label))[0]
async function selectPolicy(root){const select=nodes(root,n=>n.tag==='select')[0];select.props.onChange({target:{value:'P'}});await settle()}
function fixture(){const calls=[];globalThis.fetch=async(path,options)=>{calls.push({path,body:options?.body});if(options?.body)return response({...policy(),...options.body,version:options.body.expectedVersion+1});if(path.includes('/versions'))return response([policy({version:1})]);if(path.startsWith('/statutory-policy-templates/'))return response(policy({enabled:undefined,version:undefined,id:undefined}));return response({items:[policy()],headOffice:true})};return calls}
test('validator accepts five tables, SGD OW configuration and rejects invalid constraints',()=>{
 assert.equal(statutoryPolicyValid(policy()),true)
 for(const modify of [p=>p.currency='USD',p=>p.effectiveFrom='2026-01-02',p=>p.effectiveTo='2026-12-30',p=>p.effectiveTo='2026-99-99',p=>p.effectiveTo='2027-12-31',p=>p.config.ageBands[1].upperAge=65,p=>p.config.ageBands[0].upperAge=-1,p=>p.config.tables.pop(),p=>p.config.tables[0].rates.push(p.config.tables[0].rates[0]),p=>p.config.tables[0].rates[0].employerRate='17',p=>p.config.wageBands[0].method='javascript',p=>p.config.sdl.rate='-0.1',p=>p.config.ageTiming='immediate',p=>p.config.cpfSource='javascript:alert(1)',p=>p.config.cpfSource='',p=>p.config.sdlSource='http://example.org',p=>p.config.wageBands.pop(),p=>p.config.wageBands[2].rampMultiplier='0',p=>p.config.notes='x'.repeat(2001)]){const value=policy();modify(value);assert.equal(statutoryPolicyValid(value),false,JSON.stringify(value))}
 assert.equal(statutoryPolicyValid(policy(),[policy({id:'OTHER'})]),false)
 assert.equal(statutoryPolicyValid(policy({enabled:false}),[policy({id:'OTHER'})]),true)
 assert.equal(statutoryPolicyValid(policy(),[policy({id:'OTHER',year:2027,effectiveFrom:'2027-01-01',effectiveTo:'2027-12-31'})]),true)
})
test('copy and official templates produce fresh disabled drafts preserving rate codes and decimal strings',()=>{
 const source=policy(),draft=copyPolicyYear(source,2027)
 assert.notEqual(draft.id,source.id);assert.equal(draft.version,0);assert.equal(draft.enabled,false);assert.equal(draft.year,2027);assert.equal(draft.effectiveFrom,'2027-01-01');assert.equal(draft.effectiveTo,'2027-12-31');assert.equal(draft.config.cpfSource,'');assert.equal(draft.config.sdlSource,'');assert.match(draft.config.notes,/待核对/);assert.deepEqual(draft.config.ageBands,source.config.ageBands);assert.equal(draft.config.tables[0].rates[0].employerRate,'0.17');assert.equal(draft.config.sdl.rate,'0.0025');assert.equal(source.config.cpfSource,'https://www.cpf.gov.sg/');assert.equal(draftFromTemplate(source).enabled,false);assert.equal(safeSource('javascript:alert(1)'),'')
})
test('non-admin cannot load or edit statutory policies',async()=>{const calls=fixture(),root=await mount(false);assert.equal(calls.length,0);assert.equal(nodes(root,n=>n.tag==='form').length,0);assert.match(text(root),/只有总部管理员/)})
test('template is a disabled local draft and saving requires explicit review with expected version',async()=>{
 const calls=fixture(),root=await mount();button(root,'载入官方年度模板').props.onClick();await settle()
 assert.equal(calls.filter(c=>c.body).length,0)
 const checks=nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox');assert.equal(checks[0].props['onUpdate:modelValue'] instanceof Function,true)
 const submit=nodes(root,n=>n.tag==='button'&&n.props.type==='submit')[0];assert.equal(submit.props.disabled,true)
 checks.at(-1).props['onUpdate:modelValue'](true);await settle();nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
 const put=calls.find(c=>c.body);assert.ok(put);assert.equal(put.body.expectedVersion,0);assert.equal(put.body.enabled,false);assert.equal(put.body.currency,'SGD');assert.equal(put.body.config.tables[0].rates[0].employeeRate,'0.20');assert.equal(put.body.config.sdl.rate,'0.0025');assert.ok(!('version' in put.body));assert.ok(!('id' in put.body))
})
test('current policy saves using server revision and history is read only',async()=>{
 const calls=fixture(),root=await mount();await selectPolicy(root)
 const checks=nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox');checks.at(-1).props['onUpdate:modelValue'](true);await settle();nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();assert.equal(calls.find(c=>c.body).body.expectedVersion,2);assert.match(text(root),/规则已保存/)
 button(root,'版本历史').props.onClick();await settle();button(root,'查看').props.onClick();await settle();assert.match(text(root),/正在查看历史版本/);assert.equal(nodes(root,n=>n.tag==='button'&&n.props.type==='submit')[0].props.disabled,true)
 const before=calls.filter(c=>c.body).length;nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();assert.equal(calls.filter(c=>c.body).length,before)
})
test('rate editor changes built-in configuration and retains all age codes across five tables',async()=>{
 const calls=fixture(),root=await mount();await selectPolicy(root);button(root,'增加年龄档').props.onClick();await settle()
 const ageNames=nodes(root,n=>n.tag==='input'&&String(n.props['aria-label']).startsWith('规则名称 A'));ageNames[0].props['onUpdate:modelValue']('高龄');await settle()
 button(root,'CPF费率表').props.onClick();await settle();assert.match(text(root),/高龄/)
 const employer=nodes(root,n=>n.tag==='input'&&String(n.props['aria-label']).endsWith('雇主比率'))[0];employer.props['onUpdate:modelValue']('0.18')
 const checks=nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox');checks.at(-1).props['onUpdate:modelValue'](true);await settle();nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();const put=calls.find(c=>c.body);assert.ok(put);assert.equal(put.body.config.tables[0].rates[0].employerRate,'0.18');for(const table of put.body.config.tables)assert.equal(table.rates.length,3)
})

test('statutory policies share management breadcrumbs and remain head-office only',async()=>{
 const {managementNavigation}=await import('../../../src/modules/workforce/managementNavigation.ts')
 const route={name:'workforce-statutory-policies',params:{storeId:'A'},query:{}}
 assert.equal(managementNavigation(route,{employeeManage:true,headOffice:false},null,'zh-CN'),null)
 assert.deepEqual(managementNavigation(route,{employeeManage:true,headOffice:true},null,'zh-CN').crumbs.map(c=>c.label),['设置','CPF／SDL／CDAC规则'])
})
for(const [code,expected] of [['WORKFORCE_STATUTORY_OVERLAP','启用规则的生效日期与现有规则重叠'],['WORKFORCE_CONFLICT','规则版本已改变']])test(code+' retains draft and reports policy-specific failure',async()=>{
 const calls=fixture(),original=globalThis.fetch,{WorkforceApiError}=await import('../../../src/modules/workforce/api.ts')
 globalThis.fetch=async(path,options)=>{if(options?.body)throw new WorkforceApiError(409,code);return original(path,options)}
 const root=await mount();await selectPolicy(root)
 nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox').at(-1).props['onUpdate:modelValue'](true);await settle()
 nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();assert.ok(text(root).includes(expected));assert.equal(nodes(root,n=>n.tag==='form').length,1)
 const name=nodes(root,n=>n.tag==='input'&&n.props.maxlength==='120')[0];name.props['onUpdate:modelValue']('修订草稿');await settle();assert.equal(nodes(root,n=>n.tag==='button'&&n.props.type==='submit')[0].props.disabled,true)
})

test('conflict reload replaces selected draft with latest revision before reviewed resubmission',async()=>{
 const {WorkforceApiError}=await import('../../../src/modules/workforce/api.ts'),writes=[]
 let server=policy({version:1,name:'旧版本'}),first=true
 globalThis.fetch=async(path,options)=>{
  if(!options?.body)return response({items:[server],headOffice:true})
  writes.push(options.body)
  if(first){first=false;server=policy({version:2,name:'最新服务器版本'});throw new WorkforceApiError(409,'WORKFORCE_CONFLICT')}
  return response({...server,...options.body,version:3})
 }
 const root=await mount();await selectPolicy(root)
 nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox').at(-1).props['onUpdate:modelValue'](true);await settle()
 nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
 assert.equal(writes[0].expectedVersion,1);assert.match(text(root),/规则版本已改变/)
 button(root,'重新加载').props.onClick();await settle()
 assert.equal(nodes(root,n=>n.tag==='input'&&n.props.maxlength==='120')[0].value,'最新服务器版本')
 assert.equal(nodes(root,n=>n.tag==='button'&&n.props.type==='submit')[0].props.disabled,true)
 nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();assert.equal(writes.length,1)
 nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox').at(-1).props['onUpdate:modelValue'](true);await settle()
 nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
 assert.equal(writes.length,2);assert.equal(writes[1].expectedVersion,2);assert.equal(writes[1].name,'最新服务器版本');assert.match(text(root),/规则已保存/)
})
