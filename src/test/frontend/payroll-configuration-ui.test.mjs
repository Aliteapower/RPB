import {test,before,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {createRenderer,h,nextTick,ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {createRouter,createMemoryHistory,RouterView} from 'vue-router'
const mounted=[],originalFetch=globalThis.fetch
let ui={}
function element(tag){const n={tag,props:{},children:[],addEventListener(){},removeEventListener(){},getRootNode(){return {}},style:{}};Object.defineProperty(n,'options',{get(){return nodes(n,x=>x.tag==='option')}});return n}
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>(n.text||'')+(n.children||[]).map(text).join(' ')
const renderer=createRenderer({createElement:element,createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},patchProp:(n,k,o,v)=>{n.props[k]=v;if(k==='value')n.value=v},insert:(n,p,a)=>{if(n.parent){const i=n.parent.children.indexOf(n);if(i>=0)n.parent.children.splice(i,1)}n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:n=>{const a=n.parent?.children||[];return a[a.indexOf(n)+1]||null}})
const settle=async()=>{for(let i=0;i<30;i++){await Promise.resolve();await nextTick()}}
const response=(data,status=200)=>({ok:status<400,status,json:async()=>data})


before(async()=>{globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document={};
 for(const name of ['PayProfileForm','PayrollLineForm','PayslipDetail','EmployeeForm'])ui[name]=(await import('../../../src/modules/workforce/pages/'+name+'.vue')).default
 for(const name of ['PayItemFields','PayGenerationFields','PayRuleFields','PayRuleSummary','EmployeePaySummary','PositionSalaryDefaults','GuidedPayrollBoard'])ui[name]=(await import('../../../src/modules/workforce/components/'+name+'.vue')).default
 ui.key=(await import('../../../src/modules/workforce/context.ts')).workforceKey;ui.api=(await import('../../../src/modules/workforce/api.ts')).workforceApi
})
afterEach(()=>{mounted.splice(0).forEach(a=>a.unmount());globalThis.fetch=originalFetch})
async function mount(Page,path='/stores/A/workforce/pay-profiles/new',props={},salaryCompanies=[],locale='zh-CN'){const router=createRouter({history:createMemoryHistory(),routes:[{path:'/stores/:storeId/workforce/employees/:employeeId/edit',component:Page},{path:'/stores/:storeId/workforce/employees/new',component:Page},{path:'/stores/:storeId/workforce/pay-profiles/new',component:Page},{path:'/stores/:storeId/workforce/pay-profiles',component:{render:()=>h('div')}},{path:'/stores/:storeId/workforce/payroll-batches/:id/inputs/:employeeId',component:Page},{path:'/stores/:storeId/workforce/payroll-batches/:id/payslips/:employeeId',component:Page},{path:'/stores/:storeId/workforce/payroll-batches/:id',component:{render:()=>h('div')}}]});await router.push(path);await router.isReady();const root=element('root'),app=renderer.createApp({render:()=>h(Page,props)});app.use(router);app.use(createI18n({legacy:false,locale,messages:{}}));app.provide(ui.key,{store:ref('A'),home:ref({headOffice:true,employeeManage:true,companies:[{id:'C',name:'Company'}],stores:[{id:'A',name:'Store',companyId:'C'}],accounts:[]}),payrollHome:ref({companies:salaryCompanies}),current:()=>true,api:()=>ui.api('A')});app.mount(root);mounted.push(app);await settle();return {root,router}}
const rule={basis:'hourly',rate:'20',dayHours:'8',weekHours:'44',overtimeEnabled:false,overtimeMultiplier:'1.5',items:[]}
const home={companies:[{id:'C',name:'Company'}],employeeId:null}
function select(root,value){return nodes(root,n=>n.tag==='select'&&nodes(n,x=>x.tag==='option'&&x.props.value===value).length)[0]}
const submit=async root=>{nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()}

test('profile creation requires explicit employee context and never picks first employee',async()=>{const requests=[];globalThis.fetch=async(url)=>{requests.push(String(url));if(String(url).includes('/payroll-home'))return response(home);if(String(url).includes('/salary-templates'))return response([{id:'T',name:'Template',enabled:true,version:1,rule}]);return response({items:[],employees:[{id:'E',name:'First'},{id:'E2',name:'Target'}]})};const {root}=await mount(ui.PayProfileForm);const employee=nodes(root,n=>n.tag==='select'&&n.props['aria-label']==='员工')[0];assert.ok(employee);assert.equal(employee.props.modelValue??employee.value??'', '');assert.match(text(root),/选择员工|请选择员工/)});

test('employee salary summary with no salary company never requests amounts',async()=>{const urls=[];globalThis.fetch=async url=>{urls.push(String(url));return response({})};const {root}=await mount(ui.EmployeePaySummary,undefined,{employeeId:'E2',companyId:'C',saved:true});assert.deepEqual(urls,[]);assert.match(text(root),/无此公司薪资权限/)});
test('saved employee summary binds exact employee and allowed company',async()=>{const urls=[];globalThis.fetch=async url=>{urls.push(String(url));return response({employeeId:'E2',companyId:'C',on:'2026-10-30',profiles:[]})};const {root}=await mount(ui.EmployeePaySummary,undefined,{employeeId:'E2',companyId:'C',saved:true},[{id:'C',name:'Company'}]);assert.equal(urls.length,1);assert.match(urls[0],/employees\/E2\/pay-summary\?companyId=C&on=/);assert.match(text(root),/薪资方案未完成/);const link=nodes(root,n=>n.tag==='a'&&String(n.props.href).includes('pay-profiles/new'))[0];assert.match(link.props.href,/employeeId=E2/);assert.match(link.props.href,/companyId=C/);assert.equal(link.props.target,'_blank');assert.match(text(root),/保留当前员工草稿/)});
test('unsaved employee summary does not read or create salary plans',async()=>{const urls=[];globalThis.fetch=async url=>{urls.push(String(url));return response({})};const {root}=await mount(ui.EmployeePaySummary,undefined,{employeeId:'new',companyId:'C',saved:false},[{id:'C',name:'Company'}]);assert.deepEqual(urls,[]);assert.match(text(root),/先保存员工档案/)});
test('position defaults with no salary rights render no maintenance and fetch nothing',async()=>{const urls=[];globalThis.fetch=async url=>{urls.push(String(url));return response([])};const {root}=await mount(ui.PositionSalaryDefaults,undefined,{positionId:'P'});assert.deepEqual(urls,[]);assert.doesNotMatch(text(root),/岗位薪资默认/)});
test('profile creation binds provided employee and applies preview version',async()=>{const writes=[],urls=[];globalThis.fetch=async(url,opt)=>{urls.push(String(url));if(opt?.method==='PUT'){writes.push(JSON.parse(opt.body));return response({})}if(String(url).includes('/payroll-home'))return response(home);if(String(url).includes('/pay-profile-preview'))return response({template:{id:'T',name:'Template',enabled:true,version:7,rule},defaultMapping:{id:'D',version:3}});if(String(url).includes('/salary-templates'))return response([{id:'T',name:'Template',enabled:true,version:7,rule}]);return response({items:[],employees:[{id:'E',name:'First'},{id:'E2',name:'Target'}],employeeBirthDates:{E2:'1990-01-01'}})};const {root}=await mount(ui.PayProfileForm,'/stores/A/workforce/pay-profiles/new?employeeId=E2&companyId=C');await settle();select(root,'citizen').props['onUpdate:modelValue']('citizen');select(root,'not_applicable').props['onUpdate:modelValue']('not_applicable');await settle();for(let i=0;i<2;i++){nodes(root,n=>n.tag==='button'&&text(n).includes('下一步'))[0].props.onClick();await settle()}await submit(root);assert.equal(writes.length,1);assert.equal(writes[0].employeeId,'E2');assert.equal(writes[0].companyId,'C');assert.equal(writes[0].expectedTemplateVersion,7);assert.equal(writes[0].defaultMappingId,'D');assert.equal(writes[0].expectedDefaultVersion,3)});

test('salary maintenance opens another tab and preserves the real employee form draft',async()=>{globalThis.fetch=async url=>{const path=String(url);if(path.includes('/pay-summary'))return response({profiles:[]});if(path.endsWith('/documents'))return response({types:[],items:[]});if(path.endsWith('/positions'))return response([]);return response({id:'E2',employeeNo:'002',name:'Saved Name',jobTitle:'',phone:'',email:'',englishName:'',enabled:true,status:'active',companyId:'C',companyEffectiveDate:'2026-10-01',mainStoreId:'A',storeIds:['A'],accountId:null,version:1,editable:true,hireDate:'2026-10-01'})};const path='/stores/A/workforce/employees/E2/edit';const {root,router}=await mount(ui.EmployeeForm,path,{},[{id:'C',name:'Company'}]);const name=nodes(root,n=>n.tag==='input'&&n.props.maxlength==='100')[0];name.props['onUpdate:modelValue']('Unsaved Draft');await settle();assert.equal(nodes(root,n=>n.tag==='a'&&String(n.props.href).includes('pay-profiles/new')).length,0);nodes(root,n=>n.tag==='details'&&n.props['data-employee-task']==='pay')[0].props.onToggle({currentTarget:{open:true}});await settle();const link=nodes(root,n=>n.tag==='a'&&String(n.props.href).includes('pay-profiles/new'))[0];assert.equal(link.props.target,'_blank');link.props.onClick({button:0,currentTarget:{getAttribute:()=>'_blank'},preventDefault(){throw Error('must not replace current route')}});await settle();assert.equal(router.currentRoute.value.fullPath,path);assert.equal(name.value,'Unsaved Draft')});
test('tracking item identity alone does not count as a personal economic override',async()=>{const {ruleOverrides}=await import('../../../src/modules/workforce/payrollConfigurationTypes.ts');const original={basis:'monthly',rate:'3000',dayHours:'8',weekHours:'44',overtimeEnabled:false,overtimeMultiplier:'1.5',items:[{label:'Meal',kind:'ow_allowance',amount:'10',proration:'full'}]};const profile={rule:{...original,items:[{...original.items[0],itemId:'new-id'}]},origin:{templateRule:original}};assert.deepEqual(ruleOverrides(profile),[]);profile.rule.items[0].amount='20';assert.deepEqual(ruleOverrides(profile),['items']);})

for(const [locale,expectedBasis,expectedPayment] of [['zh-CN',['时薪','日薪','月薪'],['现金','银行','PayNow','其他']],['en',['Hourly','Daily','Monthly'],['Cash','Bank','PayNow','Other']]])test('employee pay summary translates basis and payment labels in '+locale,async()=>{
 const profiles=['hourly','daily','monthly','hourly'].map((basis,i)=>({state:'current',effectiveTo:null,sourceComplete:false,profile:{id:'P'+i,effectiveFrom:'2026-09-01',rule:{...rule,basis},payment:{method:['cash','bank','paynow','other'][i],bank:i===1?'DBS':''}}}));
 globalThis.fetch=async()=>response({profiles});
 const {root}=await mount(ui.EmployeePaySummary,undefined,{employeeId:'E2',companyId:'C',saved:true},[{id:'C',name:'Company'}],locale);
 const visible=text(root);for(const label of [...expectedBasis,...expectedPayment])assert.ok(visible.includes(label),locale+': missing '+label+' in '+visible);
 if(locale==='zh-CN')assert.doesNotMatch(visible,/\b(hourly|daily|monthly|cash|bank|paynow|other)\b/);
 assert.match(visible,/DBS/);
});

test('position salary setup chooses basis before template and sends that basis to the default resolver',async()=>{
 const requests=[];globalThis.fetch=async(url)=>{const u=String(url);requests.push(u);if(u.includes('/salary-templates'))return response([{id:'H',name:'Hourly standard',enabled:true,version:1,rule},{id:'M',name:'Monthly standard',enabled:true,version:1,rule:{...rule,basis:'monthly'}}]);if(u.includes('/position-salary-defaults'))return response([]);return response({})};
 const {root}=await mount(ui.PositionSalaryDefaults,undefined,{positionId:'P'},[{id:'C',name:'Company'}]);
 select(root,'C').props['onUpdate:modelValue']('C');await settle();
 const basis=nodes(root,n=>n.tag==='select'&&n.props['aria-label']==='计薪方式')[0];assert.ok(basis,'basis selection is available');
 basis.props['onUpdate:modelValue']('monthly');await settle();
 assert.ok(requests.some(u=>u.includes('/position-salary-defaults?')&&u.includes('basis=monthly')));
 assert.ok(select(root,'M'));assert.equal(select(root,'H'),undefined);
});

test('new salary uses guided steps and does not advance without choosing an employee',async()=>{
 globalThis.fetch=async(url)=>String(url).includes('/payroll-home')?response(home):String(url).includes('/salary-templates')?response([]):response({items:[],employees:[{id:'E',name:'员工'}]});
 const {root}=await mount(ui.PayProfileForm);assert.match(text(root),/确定待遇/);const next=nodes(root,n=>n.tag==='button'&&text(n).includes('下一步：必要资料'))[0];assert.ok(next);next.props.onClick();await settle();assert.match(text(root),/请选择员工/);
});

test('real RouterView returns from salary setup to the original payroll month filter and employee then reloads',async()=>{
 let boardReads=0,profileWrite=null;globalThis.fetch=async(url,opt)=>{const u=String(url);if(opt?.method==='PUT'){profileWrite=JSON.parse(opt.body);return response({})}if(u.includes('/employee-payroll-board')){boardReads++;return response({companyId:'C',month:'2026-09',batchId:null,status:null,issues:['PROFILE_MISSING'],rows:[{employeeId:'E',employeeName:'张三',employeeNo:'1001',positionName:'服务员',suggestedEffectiveFrom:'2026-09-15',issues:['PROFILE_MISSING'],net:null,gross:null,deductions:null,breakdown:null}],totals:null})}if(u.includes('/payroll-home'))return response(home);if(u.includes('/pay-profile-preview'))return response({template:{id:'T',name:'Standard',version:1,enabled:true,rule}});if(u.includes('/salary-templates'))return response([{id:'T',name:'Standard',version:1,enabled:true,rule}]);return response({items:[],employees:[{id:'E',name:'张三'}]})};
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/stores/:storeId/workforce/payroll',component:{render:()=>h(ui.GuidedPayrollBoard,{companyId:'C',month:'2026-09'})}},{path:'/stores/:storeId/workforce/pay-profiles/new',component:ui.PayProfileForm},{path:'/:pathMatch(.*)*',component:{render:()=>h('div')}}]});await router.push('/stores/A/workforce/payroll?companyId=C&month=2026-09&paySearch=张三&payEmployee=E');const root=element('root'),app=renderer.createApp({render:()=>h(RouterView,{key:router.currentRoute.value.path})});app.use(router);app.use(createI18n({legacy:false,locale:'zh-CN',messages:{}}));app.provide(ui.key,{store:ref('A'),home:ref({headOffice:true,employeeManage:true,companies:[{id:'C'}],stores:[{id:'A',companyId:'C'}]}),payrollHome:ref({companies:[{id:'C'}]}),current:()=>true,api:()=>ui.api('A')});app.mount(root);mounted.push(app);await settle();
 const link=nodes(root,n=>n.tag==='a'&&text(n).includes('配置薪资'))[0];assert.ok(link);await router.push(link.props.href);await settle();select(root,'foreign').props['onUpdate:modelValue']('foreign');select(root,'not_applicable').props['onUpdate:modelValue']('not_applicable');await settle();for(let i=0;i<2;i++){nodes(root,n=>n.tag==='button'&&text(n).includes('下一步'))[0].props.onClick();await settle()}await submit(root);await settle();assert.equal(router.currentRoute.value.path,'/stores/A/workforce/payroll');assert.equal(router.currentRoute.value.query.month,'2026-09');assert.equal(router.currentRoute.value.query.paySearch,'张三');assert.equal(router.currentRoute.value.query.payEmployee,'E');assert.ok(boardReads>=2);assert.equal(profileWrite.effectiveFrom,'2026-09-15')
});


test('position defaults explain missing templates and preserve company scope when opening template setup',async()=>{
 globalThis.fetch=async()=>response([])
 const {root}=await mount(ui.PositionSalaryDefaults,undefined,{positionId:'P',companyId:'C'},[{id:'C',name:'Company'}])
 assert.match(text(root),/暂无匹配的启用模板/)
 const link=nodes(root,n=>n.tag==='a'&&String(n.props.href).includes('salary-templates'))[0]
 assert.equal(link.props.href,'/stores/A/workforce/salary-templates?companyId=C')
 assert.equal(link.props.target,'_blank','template setup must retain the current position draft')
 assert.equal(nodes(root,n=>n.tag==='button'&&text(n)==='保存默认')[0].props.disabled,true)
})
test('position template preview is read-only and selecting it does not write payroll data',async()=>{
 const writes=[]
 globalThis.fetch=async(url,options)=>{if(options?.method==='PUT'){writes.push(url);return response({})}return response(String(url).includes('salary-templates')?[{id:'T',name:'时薪模板',enabled:true,version:1,rule}]:[])}
 const {root}=await mount(ui.PositionSalaryDefaults,undefined,{positionId:'P',companyId:'C'},[{id:'C',name:'Company'}])
 const basis=nodes(root,n=>n.tag==='select'&&n.props['aria-label']==='计薪方式')[0]
 basis.props['onUpdate:modelValue']('hourly');await settle()
 const template=nodes(root,n=>n.tag==='select'&&n.props['aria-label']==='模板')[0]
 template.props['onUpdate:modelValue']('T');await settle()
 assert.match(text(root),/查看所选模板金额、津贴与加班依据/)
 assert.ok(nodes(root,n=>n.tag==='section'&&n.props['aria-label']==='工资规则明细（只读）').length)
 assert.equal(nodes(root,n=>n.tag==='button'&&text(n).includes('添加项目')).length,0)
 assert.equal(writes.length,0)
})


test('read-only pay rule details retain amounts, attendance conditions and source labels without editable controls',async()=>{
 const value={...rule,generation:{mode:'attendance',workingWeekdays:[1,3,5]},items:[{label:'全勤',kind:'ow_attendance',amount:'100',proration:'full'},{label:'补充事件',kind:'wage_event',amount:'20'}]}
 const before=JSON.stringify(value);let calls=0;globalThis.fetch=async()=>{calls++;return response({})}
 const {root}=await mount(ui.PayRuleSummary,undefined,{rule:value})
 assert.match(text(root),/周一、周三、周五/);assert.match(text(root),/全勤奖按当月核准结果判断/);assert.match(text(root),/工资事件/)
 assert.equal(nodes(root,n=>['input','select','button'].includes(n.tag)).length,0)
 assert.equal(JSON.stringify(value),before);assert.equal(calls,0)
})


test('advanced personal differences keep hours editable without duplicating pay basis and rate',async()=>{
 const {root}=await mount(ui.PayRuleFields,undefined,{rule:{...rule},basisLocked:true,hideItems:true,hidePayTerms:true})
 assert.equal(nodes(root,n=>n.tag==='input'&&n.props.inputmode==='decimal').length,2,'advanced details expose day and week hours, not a second pay rate')
 assert.doesNotMatch(text(root),/基本工资标准/)
 assert.match(text(root),/标准日小时/)
})
