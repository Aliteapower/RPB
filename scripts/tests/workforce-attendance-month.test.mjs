import test from 'node:test'
import assert from 'node:assert/strict'
import {dayBadges,secondsText} from '../../src/modules/workforce/attendanceMonth.ts'
import {createRenderer,defineComponent,h,nextTick,ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {routeLocationKey} from 'vue-router'
import {workforceKey} from '../../src/modules/workforce/context.ts'
import AttendanceMonth from '../../src/modules/workforce/pages/AttendanceMonth.vue'
import PayrollEvidence from '../../src/modules/workforce/components/PayrollEvidence.vue'
import AttendancePolicySettings from '../../src/modules/workforce/components/EmployeeAttendancePolicySettings.vue'
import PayGenerationFields from '../../src/modules/workforce/components/PayGenerationFields.vue'
import {validGeneration} from '../../src/modules/workforce/payGeneration.ts'
import AttendanceIntervalExceptions from '../../src/modules/workforce/components/AttendanceIntervalExceptions.vue'
const tick=async()=>{await new Promise(r=>setTimeout(r,0));await nextTick()}
function nodes(root,p){const a=[];function walk(n){if(p(n))a.push(n);for(const c of n.children||[])walk(c)}walk(root);return a}
const text=root=>nodes(root,n=>n.text).map(n=>n.text).join(' ')
async function mount(component,{get,put=async()=>{},manager=true,locale='zh-CN',routeQuery={}}={}){
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document=Object.assign(new Document(),{activeElement:null})
 const root={children:[]},store=ref('s1'),renderer=createRenderer({createElement:tag=>({tag,children:[],props:{},addEventListener(){},removeEventListener(){},getRootNode(){return globalThis.document},get options(){return this.children.filter(n=>n.tag==='option')}}),createText:text=>({text}),createComment:comment=>({comment}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},parentNode:n=>n?.parent||null,nextSibling:n=>{const a=n?.parent?.children||[];return a[a.indexOf(n)+1]||null},insert:(n,p,a)=>{if(n.parent)n.parent.children=n.parent.children.filter(x=>x!==n);n.parent=p;const i=a?p.children.indexOf(a):-1;i<0?p.children.push(n):p.children.splice(i,0,n)},remove:n=>{if(n.parent)n.parent.children=n.parent.children.filter(x=>x!==n)},patchProp:(n,k,p,v)=>n.props[k]=v})
 const app=renderer.createApp(defineComponent({setup:()=>()=>h(component)}));app.provide(routeLocationKey,{query:routeQuery});app.provide(workforceKey,{store,home:ref({employeeManage:manager,myself:{id:'e1',accountId:'a1'}}),api:()=>({get,put}),refresh:async()=>{}});app.use(createI18n({legacy:false,locale,messages:{[locale]:{}}}));app.component('RouterLink',{props:['to'],setup:(p,{slots})=>()=>h('a',{href:p.to},slots.default?.())});app.mount(root);await tick();return {root,app,store}
}
const totals={workSeconds:0,paidLeaveSeconds:0,unpaidLeaveSeconds:0,absenceSeconds:0,extraWorkSeconds:0,pendingCount:0,overtimeSeconds:null,pendingOvertimeSeconds:null}
const monthData={month:'2026-10',employees:[{employeeId:'e1',employeeNo:'001',name:'张三',days:[{workDate:'2026-10-01',mode:'contract',state:'not_applicable',issues:[],paidLeaveSeconds:0,unpaidLeaveSeconds:0,extraSeconds:0}],totals}],total:1,offset:0,limit:30,canManage:true}
test('normal is blank but unknown, missing punch and future remain explicit',()=>{
 const day={state:'system_valid',issues:[],paidLeaveSeconds:0,unpaidLeaveSeconds:0,extraSeconds:0}
 assert.deepEqual(dayBadges(day,false),[])
 assert.match(dayBadges({...day,state:'pending',issues:['MISSING_PUNCH']},false).join(),/缺卡/)
 assert.match(dayBadges({...day,state:'unarranged'},true).join(),/Unarranged/)
 assert.notEqual(secondsText(null,false),secondsText(0,false))
})
test('same day retains leave, extra work and pending facts',()=>{
 const badges=dayBadges({state:'pending',issues:[],paidLeaveSeconds:7200,unpaidLeaveSeconds:3600,extraSeconds:3600},false)
 assert.ok(badges.length>=4)
 assert.match(badges.join(),/待处理/)
})
test('month GET never evaluates; explicit update binds displayed employee IDs',async()=>{
 const writes=[];const m=await mount(AttendanceMonth,{get:async path=>path==='/positions'?[]:structuredClone(monthData),put:async(path,body)=>writes.push({path,body})})
 assert.equal(writes.length,0);assert.match(text(m.root),/不适用/);assert.match(text(m.root),/未知/)
 nodes(m.root,n=>n.tag==='button'&&n.text==='更新本页本月判定')[0].props.onClick();await tick();await tick();assert.equal(writes.length,1);assert.equal(writes[0].path,'/attendance-month/refresh');assert.deepEqual(writes[0].body.employeeIds,['e1']);m.app.unmount()
})
test('employee view cannot show manager controls or refresh commands',async()=>{
 const m=await mount(AttendanceMonth,{manager:false,get:async()=>({...monthData,canManage:false})});assert.doesNotMatch(text(m.root),/更新本页本月判定|单人月历|异常核准/);assert.match(text(m.root),/我的考勤月历/);m.app.unmount()
})
test('stale month responses after store switch never render old employees',async()=>{
 let resolve;const m=await mount(AttendanceMonth,{get:path=>path==='/positions'?Promise.resolve([]):new Promise(r=>resolve=r)});m.store.value='s2';resolve(monthData);await tick();assert.doesNotMatch(text(m.root),/张三/);m.app.unmount()
})
test('permission failures expose retry and English empty states remain usable',async()=>{
 let m=await mount(AttendanceMonth,{get:async()=>{throw Error('forbidden')}});assert.ok(nodes(m.root,n=>n.props?.role==='alert').length);assert.ok(nodes(m.root,n=>n.text==='重试查询').length);m.app.unmount()
 m=await mount(AttendanceMonth,{locale:'en',get:async p=>p==='/positions'?[]:{...monthData,employees:[]}});assert.match(text(m.root),/No employees in this range/);m.app.unmount()
})
test('policy self read is readonly; manager retry preserves exact dated command',async()=>{
 const page={employeeId:'e1',canManage:false,today:'2026-10-31',assignments:[],effective:{mode:'contract',timezone:'Asia/Singapore'}}
 let m=await mount(AttendancePolicySettings,{manager:false,get:async()=>page});assert.equal(nodes(m.root,n=>n.tag==='form').length,0);assert.match(text(m.root),/合同计发/);m.app.unmount()
 const writes=[];let reject;m=await mount(AttendancePolicySettings,{get:async p=>p==='/attendance-policy-defaults'?[]:{...page,canManage:true,effective:{mode:'clock',timezone:'Asia/Singapore'}},put:(path,body)=>{writes.push({path,body});return new Promise((_,r)=>reject=r)}})
 const form=nodes(m.root,n=>n.tag==='form')[0];form.props.onSubmit({preventDefault(){}});await tick();assert.equal(writes.length,1);assert.equal(writes[0].body.effectiveFrom,'2026-11-01');assert.equal(writes[0].body.arrangement.unpaidBreakStartOffsetMinutes,240);form.props.onSubmit({preventDefault(){}});assert.equal(writes.length,1);reject(Error('network'));await tick();nodes(m.root,n=>n.text==='重试原请求')[0].props.onClick();await tick();assert.deepEqual(writes[0],writes[1]);m.app.unmount()
})
test('adopting a dated default sends only adoption fields; personal overrides do not send default IDs',async()=>{
 const page={employeeId:'e1',canManage:true,today:'2026-10-31',assignments:[],effective:{mode:'clock',timezone:'Asia/Singapore'}},writes=[]
 const options={get:async p=>p==='/attendance-policy-defaults'?[{id:'default-v3',version:3,effectiveFrom:'2026-10-01',mode:'default_normal'}]:page,put:async(path,body)=>{writes.push({path,body:JSON.parse(JSON.stringify(body))})}}
 let m=await mount(AttendancePolicySettings,options)
 nodes(m.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0].props['onUpdate:modelValue'](true);await tick()
 nodes(m.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();await tick()
 assert.match(writes[0].path,/\/employees\/e1\/attendance-policies\//)
 assert.deepEqual(writes[0].body,{expectedVersion:0,effectiveFrom:'2026-11-01',adoptDefault:true,defaultId:'default-v3'})
 m.app.unmount();m=await mount(AttendancePolicySettings,options)
 nodes(m.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();await tick()
 assert.deepEqual(Object.keys(writes[1].body).sort(),['adoptDefault','arrangement','effectiveFrom','expectedVersion','mode'])
 assert.equal(writes[1].body.adoptDefault,false);assert.equal(writes[1].body.mode,'clock');assert.equal(writes[1].body.arrangement.standardWorkMinutes,480)
 m.app.unmount()
})
test('contract generation requires explicit terms; only full monthly no deduction can omit weekdays',()=>{
 const rule={basis:'monthly',overtimeEnabled:false,generation:{mode:'contract',workingWeekdays:[]}}
 assert.equal(validGeneration(rule),false)
 rule.generation.contractTerms={employmentProration:'full',unpaidLeaveTreatment:'no_deduction',overtimeTreatment:'included'}
 assert.equal(validGeneration(rule),true)
 rule.generation.contractTerms.unpaidLeaveTreatment='deduct';assert.equal(validGeneration(rule),false)
 rule.generation.workingWeekdays=[1,2,3,4,5];assert.equal(validGeneration(rule),true)
 rule.overtimeEnabled=true;assert.equal(validGeneration(rule),false)
})
test('switching to contract disables automatic overtime without guessing agreement',async()=>{
 const rule=ref({basis:'monthly',rate:'2000',dayHours:'8',weekHours:'44',overtimeMultiplier:'1.5',overtimeEnabled:true,items:[]})
 const Wrapper=defineComponent({setup:()=>()=>h(PayGenerationFields,{rule:rule.value})})
 const m=await mount(Wrapper,{get:async()=>{}});nodes(m.root,n=>n.tag==='select')[0].props['onUpdate:modelValue']('contract');await tick();assert.equal(rule.value.overtimeEnabled,false);assert.equal(validGeneration(rule.value),false);assert.match(text(m.root),/明确选择|合同条款/);m.app.unmount()
})
test('interval leave uses source timezone/company, stays pending, and cannot self approve',async()=>{
 const writes=[],item={id:'x1',employeeId:'e1',kind:'leave',status:'pending',applicantId:'a1',startAt:'2026-10-01T01:00:00Z',endAt:'2026-10-01T02:00:00Z',typeName:'年假',version:1}
 const Wrapper=defineComponent({setup:()=>()=>h(AttendanceIntervalExceptions,{employeeId:'e1',date:'2026-10-01',timezone:'Asia/Singapore',companyId:'company1'})})
 const m=await mount(Wrapper,{get:async p=>p==='/leave-types'?[{id:'annual',name:'年假',enabled:true}]:[item],put:async(path,body)=>writes.push({path,body})});assert.doesNotMatch(text(m.root),/批准销假|^批准$/);assert.match(text(m.root),/待审/)
 nodes(m.root,n=>n.props?.['data-field']==='medicalReference')[0].props['onUpdate:modelValue']('MC-REF-1');await tick();nodes(m.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();await tick();assert.equal(writes.length,1);assert.equal(writes[0].body.medicalReference,'MC-REF-1');assert.equal(writes[0].body.companyId,'company1');assert.equal(Date.parse(writes[0].body.startAt),Date.parse('2026-10-01T01:00:00Z'));assert.equal(writes[0].body.typeId,'annual');assert.equal('paid' in writes[0].body,false);assert.equal('status' in writes[0].body,false);m.app.unmount()
})

test('opening attendance from payroll readiness keeps the requested month',async()=>{
 const requests=[];const m=await mount(AttendanceMonth,{routeQuery:{month:'2026-09'},get:async path=>{requests.push(path);return path==='/positions'?[]:{...structuredClone(monthData),month:'2026-09'}}})
 assert.ok(requests.some(p=>p.startsWith('/attendance-month?')&&new URLSearchParams(p.split('?')[1]).get('month')==='2026-09'))
 m.app.unmount()
})

test('contract pay evidence is distinct from free full fixed salary',async()=>{
 const line={components:[],rule:{basis:'monthly',generation:{mode:'contract'}},hours:{approvalState:'not_applicable',basis:'contract',actualSeconds:null,approvedSeconds:null,workingDays:'22',payableDays:'21.5',overtimeSeconds:7200}}
 const wrapper=defineComponent({setup:()=>()=>h(PayrollEvidence,{line,hideGovernance:true})})
 const m=await mount(wrapper,{get:async()=>({})})
 assert.match(text(m.root),/按合同计发/)
 assert.doesNotMatch(text(m.root),/固定全额/)
 m.app.unmount()
})


test('source calendar month stays separate from payroll return month',async()=>{const requests=[];const m=await mount(AttendanceMonth,{routeQuery:{month:'2026-09',attendanceMonth:'2026-08'},get:async path=>{requests.push(path);return path==='/positions'?[]:{...structuredClone(monthData),month:'2026-08'}}});assert.ok(requests.some(p=>p.startsWith('/attendance-month?')&&new URLSearchParams(p.split('?')[1]).get('month')==='2026-08'));m.app.unmount()})


test('payroll employee target reaches the monthly query and managers can clear the range',async()=>{const requests=[];const m=await mount(AttendanceMonth,{routeQuery:{month:'2026-09',employeeId:'e1'},get:async p=>{requests.push(p);return p==='/positions'?[]:structuredClone(monthData)}});assert.ok(requests.some(p=>p.startsWith('/attendance-month?')&&new URLSearchParams(p.split('?')[1]).get('employeeId')==='e1'));nodes(m.root,n=>n.tag==='button'&&n.text==='显示所有员工')[0].props.onClick();await tick();assert.equal(new URLSearchParams(requests.filter(p=>p.startsWith('/attendance-month?')).at(-1).split('?')[1]).has('employeeId'),false);m.app.unmount()})


for(const mode of ['contract','free'])test(mode+' leave measurement remains optional and stores only explicit arrangements',async()=>{const writes=[],page={employeeId:'e1',canManage:true,today:'2026-10-31',assignments:[],effective:{mode,arrangement:null,timezone:'Asia/Singapore'}},m=await mount(AttendancePolicySettings,{get:async p=>p==='/attendance-policy-defaults'?[]:page,put:async(path,body)=>writes.push({path,body})});assert.match(text(m.root),/不产生出勤工时、不要求打卡/);assert.equal(nodes(m.root,n=>n.tag==='input'&&n.props.type==='time').length,0);nodes(m.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();await tick();assert.equal(writes[0].body.mode,mode);assert.equal(writes[0].body.arrangement,null);nodes(m.root,n=>n.props?.['data-field']==='leaveMeasurement')[0].props['onUpdate:modelValue'](true);await tick();assert.equal(nodes(m.root,n=>n.tag==='input'&&n.props.type==='time')[0].value,'');nodes(m.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();assert.equal(writes.length,1);nodes(m.root,n=>n.tag==='input'&&n.props.type==='checkbox'&&n.props.value===1)[0].props['onUpdate:modelValue']([1,2,3,4,5]);nodes(m.root,n=>n.tag==='input'&&n.props.type==='time')[0].props['onUpdate:modelValue']('10:30');const numeric=nodes(m.root,n=>n.tag==='input'&&n.props.type==='number');numeric[0].props['onUpdate:modelValue'](420);numeric[1].props['onUpdate:modelValue'](30);numeric[2].props['onUpdate:modelValue'](180);await tick();nodes(m.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();await tick();assert.deepEqual(writes[1].body.arrangement,{weekdays:[1,2,3,4,5],startTime:'10:30',standardWorkMinutes:420,unpaidBreakMinutes:30,unpaidBreakStartOffsetMinutes:180});assert.equal(writes[1].body.mode,mode);m.app.unmount()})

for(const mode of ['contract','free'])test(mode+' saved measurement survives loading and adoption excludes private fields',async()=>{const writes=[],arrangement={weekdays:[2,4,6],startTime:'20:00',standardWorkMinutes:360,unpaidBreakMinutes:0,unpaidBreakStartOffsetMinutes:null},page={employeeId:'e1',canManage:true,today:'2026-10-31',assignments:[{id:'P',version:4,mode,effectiveFrom:'2026-10-01',arrangement}],effective:{mode,arrangement,timezone:'Asia/Singapore'}},m=await mount(AttendancePolicySettings,{get:async p=>p==='/attendance-policy-defaults'?[{id:'D',version:2,mode:'clock',effectiveFrom:'2026-10-01',arrangement:null}]:page,put:async(path,body)=>writes.push(body)});assert.equal(nodes(m.root,n=>n.props?.['data-field']==='leaveMeasurement')[0].checked,true);nodes(m.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();await tick();assert.deepEqual(writes[0].arrangement,arrangement);nodes(m.root,n=>n.tag==='input'&&n.props.type==='checkbox')[0].props['onUpdate:modelValue'](true);await tick();nodes(m.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();await tick();assert.deepEqual(writes[1],{expectedVersion:4,effectiveFrom:'2026-11-01',adoptDefault:true,defaultId:'D'});m.app.unmount()})

test('optional leave measurement validates explicit time, weekdays and breaks before writing',async()=>{const writes=[],page={employeeId:'e1',canManage:true,today:'2026-10-31',assignments:[],effective:{mode:'free',arrangement:null}},m=await mount(AttendancePolicySettings,{get:async p=>p==='/attendance-policy-defaults'?[]:page,put:async(path,body)=>writes.push(body)});nodes(m.root,n=>n.props?.['data-field']==='leaveMeasurement')[0].props['onUpdate:modelValue'](true);await tick();const form=nodes(m.root,n=>n.tag==='form')[0],numeric=nodes(m.root,n=>n.tag==='input'&&n.props.type==='number');nodes(m.root,n=>n.tag==='input'&&n.props.type==='checkbox'&&n.props.value===1)[0].props['onUpdate:modelValue']([1,2,3,4,5]);nodes(m.root,n=>n.tag==='input'&&n.props.type==='time')[0].props['onUpdate:modelValue']('09:00');numeric[0].props['onUpdate:modelValue'](1440);await tick();form.props.onSubmit({preventDefault(){}});await tick();assert.equal(writes.length,0);numeric[0].props['onUpdate:modelValue'](480);nodes(m.root,n=>n.tag==='input'&&n.props.type==='time')[0].props['onUpdate:modelValue']('bad');await tick();form.props.onSubmit({preventDefault(){}});await tick();assert.equal(writes.length,0);nodes(m.root,n=>n.tag==='input'&&n.props.type==='time')[0].props['onUpdate:modelValue']('09:00');numeric[2].props['onUpdate:modelValue'](481);await tick();form.props.onSubmit({preventDefault(){}});await tick();assert.equal(writes.length,0);numeric[2].props['onUpdate:modelValue'](240);nodes(m.root,n=>n.tag==='input'&&n.props.type==='checkbox'&&n.props.value===1)[0].props['onUpdate:modelValue']([]);await tick();form.props.onSubmit({preventDefault(){}});await tick();assert.equal(writes.length,0);assert.match(text(m.root),/计量安排/);m.app.unmount()})


for(const mode of ['clock','default_normal'])test(mode+' continues requiring and preserving its actual work arrangement',async()=>{const writes=[],arrangement={weekdays:[1,3,5],startTime:'08:15',standardWorkMinutes:450,unpaidBreakMinutes:45,unpaidBreakStartOffsetMinutes:210},page={employeeId:'e1',canManage:true,today:'2026-10-31',assignments:[],effective:{mode,arrangement,timezone:'Asia/Singapore'}},m=await mount(AttendancePolicySettings,{get:async p=>p==='/attendance-policy-defaults'?[]:page,put:async(path,body)=>writes.push(body)});assert.equal(nodes(m.root,n=>n.props?.['data-field']==='leaveMeasurement').length,0);assert.equal(nodes(m.root,n=>n.tag==='input'&&n.props.type==='time').length,1);nodes(m.root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await tick();await tick();assert.equal(writes[0].mode,mode);assert.deepEqual(writes[0].arrangement,arrangement);m.app.unmount()})


test('switching from clock to optional leave measurement clears generic defaults and preserves an explicit draft',async()=>{const page={employeeId:'e1',canManage:true,today:'2026-10-31',assignments:[],effective:{mode:'clock',arrangement:null}},m=await mount(AttendancePolicySettings,{get:async p=>p==='/attendance-policy-defaults'?[]:page});nodes(m.root,n=>n.tag==='select'&&n.children.some(c=>c.tag==='option'&&c.props.value==='contract'))[0].props['onUpdate:modelValue']('contract');await tick();const toggle=nodes(m.root,n=>n.props?.['data-field']==='leaveMeasurement')[0];toggle.props['onUpdate:modelValue'](true);await tick();assert.equal(nodes(m.root,n=>n.tag==='input'&&n.props.type==='time')[0].value,'');assert.equal(nodes(m.root,n=>n.tag==='input'&&n.props.type==='number')[0].value,'');assert.equal(nodes(m.root,n=>n.tag==='input'&&n.props.type==='checkbox'&&Number.isInteger(n.props.value)&&n.checked).length,0);nodes(m.root,n=>n.tag==='input'&&n.props.type==='time')[0].props['onUpdate:modelValue']('11:15');nodes(m.root,n=>n.tag==='input'&&n.props.type==='number')[0].props['onUpdate:modelValue'](390);toggle.props['onUpdate:modelValue'](false);await tick();toggle.props['onUpdate:modelValue'](true);await tick();assert.equal(nodes(m.root,n=>n.tag==='input'&&n.props.type==='time')[0].value,'11:15');assert.equal(Number(nodes(m.root,n=>n.tag==='input'&&n.props.type==='number')[0].value),390);m.app.unmount()})
