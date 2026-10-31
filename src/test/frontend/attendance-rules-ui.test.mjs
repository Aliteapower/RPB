// Run: node --import ./scripts/tests/workforce-register.mjs --test src/test/frontend/attendance-rules-ui.test.mjs
import {afterEach,test} from 'node:test'
import assert from 'node:assert/strict'
import {createRenderer,h,nextTick,ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {createRouter,createMemoryHistory} from 'vue-router'
import {workforceKey} from '../../modules/workforce/context.ts'
import ShiftForm from '../../modules/workforce/pages/ShiftForm.vue'
import ShiftList from '../../modules/workforce/pages/ShiftList.vue'
import ScheduleEditor from '../../modules/workforce/components/ScheduleEditor.vue'
import {WorkforceApiError} from '../../modules/workforce/api.ts'
import ReviewExplanationField from '../../modules/workforce/components/ReviewExplanationField.vue'
import ReviewExplanationSettings from '../../modules/workforce/pages/ReviewExplanationSettings.vue'
import PublicHolidayYear from '../../modules/workforce/pages/PublicHolidayYear.vue'
import DailyScheduleStaffing from '../../modules/workforce/components/DailyScheduleStaffing.vue'
import WeekScheduleTable from '../../modules/workforce/components/WeekScheduleTable.vue'
import Settings from '../../modules/workforce/components/AttendancePolicySettings.vue'
import Editor from '../../modules/workforce/components/AttendanceRulesEditor.vue'
import {clockOffset,offsetClock,offsetNextDay,defaultAttendanceRules,sampleAttendanceRules,validAttendanceRules,shiftRuleSummary} from '../../modules/workforce/restPolicy.ts'

const apps=[],calls=[]
const element=tag=>({tag,props:{},children:[],style:{},addEventListener(){},removeEventListener(){},getRootNode(){return {}},get options(){return nodes(this,n=>n.tag==='option')}})
const renderer=createRenderer({createElement:element,createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},patchProp:(n,k,o,v)=>{n.props[k]=v;if(k==='value')n.value=v},insert:(n,p,a)=>{if(n.parent)n.parent.children.splice(n.parent.children.indexOf(n),1);n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:n=>{const a=n.parent?.children||[];return a[a.indexOf(n)+1]||null}})
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>(n.text||'')+(n.children||[]).map(text).join('')
const settle=async()=>{for(let i=0;i<20;i++){await Promise.resolve();await nextTick()}}
const button=(root,label)=>nodes(root,n=>n.tag==='button'&&text(n)===label)[0]
const checkbox=(root,label)=>nodes(root,n=>n.tag==='label'&&text(n)===label).flatMap(n=>nodes(n,c=>c.tag==='input'))[0]
const home={headOffice:true,employeeManage:true,stores:[],companies:[],accounts:[],administratorAccounts:[],myself:null}
let handler=async()=>({items:[],headOffice:true})
async function mount(Page,{path='/stores/A/workforce/shifts/new',manager=false,props={}}={}){
 globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document={}
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/stores/:storeId/workforce/management',component:{render:()=>null}},{path:'/stores/:storeId/workforce/shifts/new',component:Page},{path:'/stores/:storeId/workforce/shifts/:id',component:Page},{path:'/stores/:storeId/workforce/shifts',component:{render:()=>null}}]});await router.push(path)
 const root=element('root'),app=renderer.createApp({render:()=>h(Page,props)})
 app.use(router);app.use(createI18n({legacy:false,locale:'zh-CN',messages:{}}));app.provide(workforceKey,{store:ref('A'),home:ref({...home,headOffice:!manager}),api:()=>({get:async path=>{calls.push({path,method:'GET'});return handler(path)},put:async(path,body)=>{calls.push({path,method:'PUT',body:JSON.parse(JSON.stringify(body))});return handler(path,body)}}),refresh:async()=>{}});app.mount(root);apps.push(app);await settle();return root
}
afterEach(()=>{apps.splice(0).forEach(a=>a.unmount());calls.length=0;handler=async()=>({items:[],headOffice:true})})

test('review explanation field filters enabled matching presets and allows selected manual or empty text',async()=>{
 const emitted=[],items=[{id:'late',code:'LATE',text:'已核实交通延误',enabled:true},{id:'general',code:'GENERAL',text:'主管已核对',enabled:true},{id:'early',code:'EARLY',text:'提前离岗',enabled:true},{id:'off',code:'LATE',text:'已停用',enabled:false}]
 const root=await mount(ReviewExplanationField,{props:{modelValue:'',code:'LATE',items,'onUpdate:modelValue':v=>emitted.push(v)}})
 assert.deepEqual(nodes(root,n=>n.tag==='option').map(n=>n.props.value),['','late','general'])
 const select=nodes(root,n=>n.tag==='select')[0];select.props['onUpdate:modelValue']('late');await settle();select.props.onChange();await settle();assert.equal(emitted.at(-1),'已核实交通延误')
 const area=nodes(root,n=>n.tag==='textarea')[0];assert.equal(area.props.required,undefined);area.props['onUpdate:modelValue']('手写补充');area.props['onUpdate:modelValue']('');assert.equal(emitted.at(-1),'');assert.equal(calls.length,0)
})

test('decision explanation management separates saved rows and new draft and writes with a version',async()=>{
 const items=[{id:'old',code:'LATE',text:'原说明',enabled:true,version:1}]
 handler=async(path,body)=>{if(body){const item={...body,id:path.split('/').at(-1),version:1};items.push(item);return item}return {items,headOffice:true}}
 const root=await mount(ReviewExplanationSettings);assert.match(text(root),/已保存的说明/);assert.equal(nodes(root,n=>n.tag==='form').length,0)
 await button(root,'新增说明').props.onClick();await settle();assert.match(text(root),/新增说明草稿/)
 nodes(root,n=>n.tag==='textarea')[0].props['onUpdate:modelValue']('新说明');await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
 assert.equal(calls.find(c=>c.method==='PUT').body.expectedVersion,0);assert.equal(calls.find(c=>c.method==='PUT').body.text,'新说明');assert.equal(nodes(root,n=>n.tag==='form').length,0);assert.match(text(root),/新说明/)
})

test('store managers can read explanation settings but cannot create or edit tenant presets',async()=>{
 handler=async()=>({items:[{id:'old',code:'LATE',text:'原说明',enabled:true,version:1}],headOffice:false})
 const root=await mount(ReviewExplanationSettings,{manager:true});assert.match(text(root),/原说明/);assert.equal(button(root,'新增说明'),undefined);assert.equal(button(root,'编辑'),undefined)
})

test('annual holiday saved list stays unchanged while a draft is edited discarded or loaded',async()=>{
 const saved={id:'saved',date:'2026-10-30',name:'已存假日',englishName:'Saved',enabled:true,observedOn:null,observedKind:'none',tentative:false}
 let items=[saved]
 handler=async(path,body)=>{if(body){items=body.items;return {year:2026,version:3,items}}if(path.includes('templates'))return {source:'https://example.test/fixture',items:[{...saved,id:'draft',name:'新草稿假日'}]};return {year:2026,version:2,items}}
 const root=await mount(PublicHolidayYear),savedTable=()=>nodes(root,n=>n.tag==='table'&&n.props.class==='wf-saved-holidays')[0]
 assert.match(text(savedTable()),/已存假日/);assert.equal(nodes(root,n=>n.tag==='form').length,1)
 button(root,'编辑当年设置').props.onClick();await settle();assert.match(text(root),/编辑草稿/)
 nodes(root,n=>n.tag==='input'&&n.props.type==='date')[0].props['onUpdate:modelValue']('2026-10-31');await settle();assert.match(text(savedTable()),/2026-10-30/)
 button(root,'放弃本次草稿').props.onClick();await settle();assert.equal(nodes(root,n=>n.tag==='form').length,1);assert.equal(calls.filter(c=>c.method==='PUT').length,0)
 button(root,'载入官方模板（草稿）').props.onClick();await settle();assert.match(text(savedTable()),/已存假日/);assert.ok(nodes(root,n=>n.tag==='input'&&n.value==='新草稿假日').length)
 await nodes(root,n=>n.tag==='form').at(-1).props.onSubmit({preventDefault(){}});await settle();assert.match(text(savedTable()),/新草稿假日/);assert.equal(calls.find(c=>c.method==='PUT').body.expectedVersion,2)
})

test('daily staffing displays unique totals separately from per-shift counts',async()=>{
 const value={shifts:[{shiftId:'early',name:'早班',people:2,personDays:2},{shiftId:'late',name:'晚班',people:4,personDays:4}],days:[{shiftId:'early',date:'2026-10-30',people:2},{shiftId:'late',date:'2026-10-30',people:4}],dailyTotals:[{date:'2026-10-30',people:4}]}
 const root=await mount(DailyScheduleStaffing,{props:{value}});assert.match(text(root),/30\/10\/2026共\s*4\s*人/);assert.match(text(root),/早班\s*2\s*人/);assert.match(text(root),/晚班\s*4\s*人/)
 const table=await mount(WeekScheduleTable,{props:{data:{items:[],employees:[],timezone:'Asia/Singapore',staffing:value},week:'2026-10-30',base:''}})
 const total=nodes(table,n=>n.tag==='tr'&&text(n).includes('每日总人数（去重）'))[0];assert.match(text(total),/4 人/);assert.doesNotMatch(text(total),/6 人/)
})

const dayShift={id:'day',name:'早班',startTime:'11:00',endTime:'23:00',nextDay:false,enabled:true,version:1,breakMode:'none'}
test('schedule options use the selected business date and reject a no-longer-eligible employee',async()=>{
 handler=async path=>path==='/shifts'?[dayShift]:{items:[],employees:path.includes('from=2026-11-02&to=2026-11-02')?[]:[{id:'employee',name:'Alice'}],timezone:'Asia/Singapore'}
 const root=await mount(ScheduleEditor,{props:{employeeId:'employee',workDate:'2026-11-01'}})
 assert.ok(calls.some(c=>c.path==='/schedule?from=2026-11-01&to=2026-11-01'))
 const shift=nodes(root,n=>n.tag==='select')[1];shift.props['onUpdate:modelValue']('day')
 nodes(root,n=>n.tag==='input'&&n.props.type==='date')[0].props['onUpdate:modelValue']('2026-11-02');await settle()
 assert.ok(calls.some(c=>c.path==='/schedule?from=2026-11-02&to=2026-11-02'))
 assert.match(text(root),/所选员工在该营业日期不可排班/)
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
 assert.equal(calls.filter(c=>c.method==='PUT').length,0)
})

test('schedule save displays the specific employee leaving error rather than the 31-day hint',async()=>{
 handler=async(path,body)=>{if(body)throw new WorkforceApiError(400,'WORKFORCE_SCHEDULE_EMPLOYEE_LEFT');return path==='/shifts'?[dayShift]:{items:[],employees:[{id:'employee',name:'Alice'}],timezone:'Asia/Singapore'}}
 const root=await mount(ScheduleEditor,{props:{employeeId:'employee',workDate:'2026-11-01'}})
 nodes(root,n=>n.tag==='select')[1].props['onUpdate:modelValue']('day');await settle()
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
 assert.match(text(root),/排班日期晚于员工离职日期/);assert.doesNotMatch(text(root),/最多31天/)
})

test('schedule creation explains when no employees are eligible for the business date',async()=>{
 handler=async path=>path==='/shifts'?[dayShift]:{items:[],employees:[],timezone:'Asia/Singapore'}
 const root=await mount(ScheduleEditor,{props:{workDate:'2026-11-01'}})
 assert.match(text(root),/该营业日期没有可排班员工/);assert.equal(calls.filter(c=>c.method==='PUT').length,0)
})

test('out-of-order employee lookups cannot replace the options for the latest business date',async()=>{
 const pending=new Map()
 handler=async path=>{if(path==='/shifts')return [dayShift];if(path.includes('from=2026-11-02')||path.includes('from=2026-11-03'))return new Promise(resolve=>pending.set(path,resolve));return {items:[],employees:[{id:'employee',name:'Alice'}],timezone:'Asia/Singapore'}}
 const root=await mount(ScheduleEditor,{props:{employeeId:'employee',workDate:'2026-11-01'}})
 const date=nodes(root,n=>n.tag==='input'&&n.props.type==='date')[0]
 date.props['onUpdate:modelValue']('2026-11-02');await settle()
 assert.equal(nodes(root,n=>n.tag==='select')[0].props.disabled,true)
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});assert.equal(calls.filter(c=>c.method==='PUT').length,0)
 date.props['onUpdate:modelValue']('2026-11-03');await settle()
 pending.get('/schedule?from=2026-11-03&to=2026-11-03')({items:[],employees:[{id:'latest',name:'Latest employee'}],timezone:'Asia/Singapore'});await settle()
 pending.get('/schedule?from=2026-11-02&to=2026-11-02')({items:[],employees:[{id:'stale',name:'Stale employee'}],timezone:'Asia/Singapore'});await settle()
 assert.match(text(root),/Latest employee/);assert.doesNotMatch(text(root),/Stale employee/)
})

test('failed employee lookup blocks saves and can be retried without losing the shift draft',async()=>{
 let fail=true
 handler=async path=>{if(path==='/shifts')return [dayShift];if(path.includes('from=2026-11-02')&&fail)throw new Error('network');return {items:[],employees:[{id:'employee',name:'Alice'}],timezone:'Asia/Singapore'}}
 const root=await mount(ScheduleEditor,{props:{employeeId:'employee',workDate:'2026-11-01'}})
 nodes(root,n=>n.tag==='select')[1].props['onUpdate:modelValue']('day')
 nodes(root,n=>n.tag==='input'&&n.props.type==='date')[0].props['onUpdate:modelValue']('2026-11-02');await settle()
 assert.ok(button(root,'重新加载'));await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});assert.equal(calls.filter(c=>c.method==='PUT').length,0)
 fail=false;await button(root,'重新加载').props.onClick();await settle()
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
 assert.equal(calls.find(c=>c.method==='PUT').body.shiftId,'day');assert.equal(calls.find(c=>c.method==='PUT').body.workDate,'2026-11-02')
})

test('an ineligible employee stays visible in an existing snapshot and the schedule can still be cancelled',async()=>{
 const old={id:'old',employeeId:'employee',employeeName:'Alice',shiftId:'day',workDate:'2026-11-01',status:'scheduled',version:2,shift:dayShift,timezone:'Asia/Singapore'}
 handler=async(path,body)=>body?{...old,...body,version:3}:path==='/shifts'?[dayShift]:{items:[old],employees:[],timezone:'Asia/Singapore'}
 const root=await mount(ScheduleEditor,{props:{id:'old'}})
 assert.match(text(root),/Alice/);assert.match(text(root),/所选员工在该营业日期不可排班/)
 await button(root,'取消排班').props.onClick();await settle()
 const saved=calls.find(c=>c.method==='PUT').body;assert.equal(saved.status,'cancelled');assert.equal(saved.employeeId,'employee');assert.equal(saved.expectedVersion,2)
})

test('template management round trip preserves unsaved shift fields and rule edits without saving the shift',async()=>{
 const root=await mount(ShiftForm)
 nodes(root,n=>n.tag==='input'&&n.props.maxlength==='80')[0].props['onUpdate:modelValue']('未保存的晚班')
 checkbox(root,'启用多段休息与考勤规则').props['onUpdate:modelValue'](true);await settle()
 checkbox(root,'次日开始（仍归所选营业日期）').props['onUpdate:modelValue'](true)
 nodes(root,n=>n.tag==='input'&&n.props.type==='time')[0].props['onUpdate:modelValue']('01:00')
 await button(root,'管理模板').props.onClick();await settle()
 assert.ok(button(root,'返回班次编辑'));await button(root,'返回班次编辑').props.onClick();await settle()
 assert.equal(nodes(root,n=>n.tag==='input'&&n.props.maxlength==='80')[0].value,'未保存的晚班')
 assert.equal(nodes(root,n=>n.tag==='input'&&n.props.type==='time')[0].value,'01:00')
 assert.equal(checkbox(root,'次日开始（仍归所选营业日期）').checked,true)
 assert.equal(calls.filter(c=>c.method==='PUT').length,0)
})

test('new template saves and returns with refreshed options but does not replace the unsaved shift snapshot',async()=>{
 const items=[];handler=async(path,body)=>{if(body){const item={id:path.split('/').at(-1),name:body.name,enabled:true,version:1,rules:body.rules};items.push(item);return item}return {items,headOffice:true}}
 const root=await mount(ShiftForm);checkbox(root,'启用多段休息与考勤规则').props['onUpdate:modelValue'](true);await settle()
 nodes(root,n=>n.tag==='input'&&n.props.maxlength==='80')[0].props['onUpdate:modelValue']('我的班次')
 await button(root,'新增模板').props.onClick();await settle()
 assert.equal(nodes(root,n=>n.tag==='form').length,1,'template form must not be nested in the shift form')
 nodes(root,n=>n.tag==='input'&&n.props.maxlength==='80')[0].props['onUpdate:modelValue']('新的共享模板')
 await button(root,'载入示例：11–23点，净工作8小时').props.onClick();await settle()
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
 assert.equal(button(root,'返回班次编辑'),undefined)
 assert.ok(nodes(root,n=>n.tag==='option'&&text(n).includes('新的共享模板')).length)
 assert.equal(nodes(root,n=>n.tag==='input'&&n.props.maxlength==='80')[0].value,'我的班次')
 assert.match(text(root),/多段休息 0段/,'creating a shared template must not silently replace local rules')
 assert.equal(calls.filter(c=>c.method==='PUT'&&c.path.startsWith('/shifts/')).length,0)
 assert.equal(calls.filter(c=>c.method==='PUT').length,1)
})

test('manager can view templates and return but cannot start a new template',async()=>{
 handler=async()=>({items:[],headOffice:false});const root=await mount(ShiftForm,{manager:true})
 checkbox(root,'启用多段休息与考勤规则').props['onUpdate:modelValue'](true);await settle()
 assert.equal(button(root,'新增模板'),undefined)
 await button(root,'管理模板').props.onClick();await settle()
 assert.equal(button(root,'新增模板'),undefined);assert.equal(nodes(root,n=>n.tag==='form').length,0)
 await button(root,'返回班次编辑').props.onClick();await settle();assert.equal(nodes(root,n=>n.tag==='form').length,1)
 assert.equal(calls.filter(c=>c.method==='PUT').length,0)
})

test('editing an existing shift keeps its original ID, version and unsaved changes after returning',async()=>{
 const shift={id:'original',version:7,name:'原班次',startTime:'01:00',endTime:'06:00',nextDay:false,startNextDay:true,breakMode:'none',breakMinutes:0,breakAfterMinutes:0,breakStartOffset:0,breakEndOffset:0,breakPaid:false,enabled:true,rules:defaultAttendanceRules()}
 handler=async(path,body)=>body?body:path==='/shifts'?[shift]:{items:[],headOffice:true}
 const root=await mount(ShiftForm,{path:'/stores/A/workforce/shifts/original'})
 nodes(root,n=>n.tag==='input'&&n.props.maxlength==='80')[0].props['onUpdate:modelValue']('修改后仍保留')
 await button(root,'管理模板').props.onClick();await settle();await button(root,'返回班次编辑').props.onClick();await settle()
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
 const written=calls.find(c=>c.method==='PUT');assert.equal(written.path,'/shifts/original');assert.equal(written.body.expectedVersion,7);assert.equal(written.body.name,'修改后仍保留');assert.equal(written.body.startNextDay,true)
 assert.equal(calls.filter(c=>c.path==='/shifts'&&c.method==='GET').length,1,'returning must not reload and overwrite the draft')
})

test('failed template loading keeps a return path and the unsaved shift',async()=>{
 const root=await mount(ShiftForm);checkbox(root,'启用多段休息与考勤规则').props['onUpdate:modelValue'](true);await settle()
 nodes(root,n=>n.tag==='input'&&n.props.maxlength==='80')[0].props['onUpdate:modelValue']('保留我的内容')
 handler=async()=>{throw Error('offline')};await button(root,'管理模板').props.onClick();await settle()
 assert.ok(nodes(root,n=>n.props?.role==='alert').length)
 await button(root,'返回班次编辑').props.onClick();await settle()
 assert.equal(nodes(root,n=>n.tag==='input'&&n.props.maxlength==='80')[0].value,'保留我的内容')
})

test('template save in flight disables and guards returning, then returns on success',async()=>{
 let release;handler=async(path,body)=>body?new Promise(resolve=>release=()=>resolve({id:'NEW',...body,version:1})):{items:[],headOffice:true}
 const root=await mount(ShiftForm);checkbox(root,'启用多段休息与考勤规则').props['onUpdate:modelValue'](true);await settle()
 await button(root,'新增模板').props.onClick();await settle()
 nodes(root,n=>n.tag==='input'&&n.props.maxlength==='80')[0].props['onUpdate:modelValue']('保存中的模板')
 const pending=nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
 const back=button(root,'返回班次编辑');assert.equal(back.props.disabled,true)
 await back.props.onClick();await settle();assert.ok(button(root,'返回班次编辑'))
 release();await pending;await settle();assert.equal(button(root,'返回班次编辑'),undefined)
})

test('restaurant shift presets save early middle and overnight shifts using canonical midnight',async()=>{
 for(const [key,name,startTime,endTime,nextDay]of [['early','早班','11:00','23:00',false],['middle','中班','16:00','00:00',true],['late','晚班','23:00','06:00',true]]){
  const root=await mount(ShiftForm),picker=nodes(root,n=>n.tag==='select'&&nodes(n,o=>o.tag==='option'&&o.props.value===key).length)[0];assert.ok(picker,'restaurant preset selector');picker.props.onChange({target:{value:key}});await settle();await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();const saved=calls.filter(c=>c.method==='PUT').at(-1).body;assert.equal(saved.name,name);assert.equal(saved.startTime,startTime);assert.equal(saved.endTime,endTime);assert.equal(saved.nextDay,nextDay)
 }
})
test('midnight and overnight ranges are clear in the shift list and belong to the selected business date',async()=>{
 const middle={id:'mid',name:'中班',startTime:'16:00',endTime:'00:00',nextDay:true,enabled:true,version:1,breakMode:'none'},late={...middle,id:'late',name:'晚班',startTime:'23:00',endTime:'06:00'};handler=async()=>[middle,late];const list=await mount(ShiftList);assert.match(text(list),/16:00.*24:00/);assert.match(text(list),/23:00.*次日06:00/);assert.match(text(list),/营业日期/)
 handler=async path=>path==='/shifts'?[late]:{items:[],employees:[{id:'employee',name:'Alice'}],timezone:'Asia/Singapore'};const editor=await mount(ScheduleEditor,{props:{employeeId:'employee',workDate:'2026-11-01'}});assert.match(text(editor),/排班营业日期/);assert.match(text(editor),/跨午夜.*营业日期/)
})
test('custom shift remains editable after preset and preserves existing break rules and optimistic version',async()=>{
 const old={id:'old',version:3,name:'旧班',startTime:'11:00',endTime:'23:00',nextDay:false,breakMode:'duration',breakMinutes:30,breakAfterMinutes:360,breakStartOffset:0,breakEndOffset:0,breakPaid:true,enabled:true};handler=async path=>path==='/shifts'?[old]:{items:[],headOffice:true};const root=await mount(ShiftForm,{path:'/stores/A/workforce/shifts/old'});const picker=nodes(root,n=>n.tag==='select'&&nodes(n,o=>o.tag==='option'&&o.props.value==='late').length)[0];picker.props.onChange({target:{value:'late'}});await settle();picker.props.onChange({target:{value:'custom'}});await settle();const name=nodes(root,n=>n.tag==='input'&&n.props.maxlength==='80')[0];name.props['onUpdate:modelValue']('自订夜班');const end=nodes(root,n=>n.tag==='input'&&n.props.type==='time')[1];end.props['onUpdate:modelValue']('07:00');await settle();await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();const saved=calls.find(c=>c.method==='PUT').body;assert.equal(saved.name,'自订夜班');assert.equal(saved.endTime,'07:00');assert.equal(saved.nextDay,true);assert.equal(saved.expectedVersion,3);assert.equal(saved.breakMode,'duration');assert.equal(saved.breakPaid,true);assert.equal(saved.breakMinutes,30)
})
test('restaurant time formatting distinguishes midnight from next-day morning in both languages',async()=>{const {restaurantPreset,restaurantShiftRange,restaurantSchedulePreview}=await import('../../modules/workforce/restaurantShifts.ts');assert.equal(restaurantShiftRange(restaurantPreset('middle',true),true),'16:00–24:00');assert.equal(restaurantShiftRange(restaurantPreset('late',true),true),'23:00–next day 06:00');assert.equal(restaurantSchedulePreview('2026-10-31',restaurantPreset('late')),'2026-10-31 23:00 – 2026-11-01 06:00');assert.equal(restaurantPreset('custom'),null)})

test('next-day start shifts keep their business date and display both calendar offsets',async()=>{
 const {restaurantShiftRange,restaurantSchedulePreview}=await import('../../modules/workforce/restaurantShifts.ts')
 const shift={id:'late',name:'凌晨晚班',version:2,startTime:'01:00',endTime:'06:00',startNextDay:true,nextDay:false,enabled:true,breakMode:'none'}
 assert.equal(restaurantShiftRange(shift),'次日01:00–次日06:00');assert.equal(restaurantShiftRange({...shift,nextDay:true}),'次日01:00–后日06:00');assert.equal(restaurantShiftRange({...shift,nextDay:true},true),'next day 01:00–2 days later 06:00')
 assert.equal(restaurantSchedulePreview('2026-10-31',shift),'2026-11-01 01:00 – 2026-11-01 06:00');assert.equal(restaurantSchedulePreview('2026-12-31',{...shift,nextDay:true}),'2027-01-01 01:00 – 2027-01-02 06:00')
 handler=async path=>path==='/shifts'?[shift]:{items:[],employees:[{id:'employee',name:'Alice'}],timezone:'Asia/Singapore'}
 const editor=await mount(ScheduleEditor,{props:{employeeId:'employee',workDate:'2026-11-01'}}),select=nodes(editor,n=>n.tag==='select'&&nodes(n,o=>o.tag==='option'&&o.props.value==='late').length)[0];select.props['onUpdate:modelValue']('late');await settle()
 assert.match(text(editor),/2026-11-02 01:00 – 2026-11-02 06:00/);await nodes(editor,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();assert.equal(calls.find(c=>c.method==='PUT').body.workDate,'2026-11-01')
 const schedule={id:'schedule',employeeId:'employee',workDate:'2026-11-01',shift,startAt:'2026-11-01T17:00:00Z',endAt:'2026-11-01T22:00:00Z',timezone:'Asia/Singapore',status:'scheduled'}
 const grid=await mount(WeekScheduleTable,{props:{data:{items:[schedule],employees:[{id:'employee',name:'Alice'}],timezone:'Asia/Singapore'},week:'2026-11-01',base:''}});assert.match(text(grid),/次日01:00–次日06:00/)
 const month=await mount(WeekScheduleTable,{props:{data:{items:[{...schedule,endAt:'2026-11-02T22:00:00Z',shift:{name:'旧快照'}}],employees:[{id:'employee',name:'Alice'}],timezone:'Asia/Singapore'},week:'2026-11-01',to:'2026-11-30',base:''}});assert.match(text(month),/次日01:00–后日06:00/)
})

test('next-day start saves true, can be cleared, and preset resets it while custom retains breaks',async()=>{
 const old={id:'old',version:3,name:'凌晨班',startTime:'01:00',endTime:'06:00',startNextDay:true,nextDay:false,breakMode:'duration',breakMinutes:30,breakAfterMinutes:120,breakStartOffset:0,breakEndOffset:0,breakPaid:true,enabled:true}
 handler=async path=>path==='/shifts'?[old]:{items:[],headOffice:true}
 const root=await mount(ShiftForm,{path:'/stores/A/workforce/shifts/old'});const start=checkbox(root,'次日开始（仍归所选营业日期）');assert.ok(start)
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();assert.equal(calls.filter(c=>c.method==='PUT').at(-1).body.startNextDay,true)
 start.props['onUpdate:modelValue'](false);await settle();await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();assert.equal(calls.filter(c=>c.method==='PUT').at(-1).body.startNextDay,undefined)
 start.props['onUpdate:modelValue'](true);const picker=nodes(root,n=>n.tag==='select'&&nodes(n,o=>o.tag==='option'&&o.props.value==='late').length)[0];picker.props.onChange({target:{value:'late'}});await settle();picker.props.onChange({target:{value:'custom'}});await settle()
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle();const saved=calls.filter(c=>c.method==='PUT').at(-1).body;assert.equal(saved.startNextDay,undefined);assert.equal(saved.breakMinutes,30);assert.equal(saved.breakPaid,true)
})

test('cross-day clock input round trips and sample yields 8 net hours',()=>{
 assert.equal(clockOffset('22:00','00:30',true),150);assert.equal(offsetClock('22:00',150),'00:30');assert.equal(offsetNextDay('22:00',150),true)
 assert.equal(clockOffset('22:00','00:30',false),-1290);assert.equal(offsetClock('22:00',-1290),'00:30')
 const rules=sampleAttendanceRules();assert.equal(validAttendanceRules(rules,720),true);assert.equal(720-rules.breaks.reduce((n,b)=>n+b.endOffset-b.startOffset,0),480)
 assert.equal(validAttendanceRules(rules,540),false)
})
test('shared shift summary prioritizes rules and calculates only fixed unpaid planned hours',()=>{
 const rules=sampleAttendanceRules();assert.equal(shiftRuleSummary({rules},'无休息'),'多段休息 3段')
 assert.equal(shiftRuleSummary({rules:null},'固定时长'),'固定时长')
 assert.match(shiftRuleSummary({rules},'无休息',false,720),/班次跨度12小时 · 固定非计薪休息4小时 · 计划净工时8小时/)
 assert.match(shiftRuleSummary({rules},'No break',true,720),/Shift span 12h.*Fixed unpaid breaks 4h.*Planned net work 8h/)
 rules.breaks[1].mode='quota';rules.breaks[1].quotaMinutes=120;rules.breaks[1].punchMode='required'
 const quota=shiftRuleSummary({rules},'无休息',false,720);assert.match(quota,/固定非计薪休息2小时/);assert.match(quota,/最终工时按实际打卡核准，额度不作为固定扣除/);assert.doesNotMatch(quota,/计划净工时/)
})
test('shift list and schedule snapshot show rule counts instead of dormant legacy mode',async()=>{
 const shift={id:'shift',version:1,name:'午晚班',startTime:'11:00',endTime:'23:00',nextDay:false,breakMode:'none',breakMinutes:0,breakAfterMinutes:0,breakStartOffset:0,breakEndOffset:0,breakPaid:true,enabled:true,rules:sampleAttendanceRules()}
 handler=async()=>[shift]
 const list=await mount(ShiftList);assert.match(text(list),/多段休息 3段/);assert.doesNotMatch(text(list),/无休息|带薪休息/)
 const schedule={id:'schedule',shiftId:'shift',employeeId:'employee',employeeName:'Alice',workDate:'2026-11-01',status:'scheduled',version:1,timezone:'Asia/Singapore',shift}
 handler=async path=>path==='/shifts'?[shift]:{items:[schedule],employees:[{id:'employee',name:'Alice'}],timezone:'Asia/Singapore'}
 const editor=await mount(ScheduleEditor,{props:{id:'schedule'}});assert.match(text(editor),/班次快照.*多段休息 3段/);assert.doesNotMatch(text(editor),/无休息/)
})
test('validation rejects overlaps, partial windows and unrecorded actual/quota breaks',()=>{
 const rules=sampleAttendanceRules();rules.breaks[1].startOffset=150;assert.equal(validAttendanceRules(rules,720),false)
 const clean=defaultAttendanceRules();clean.breaks=[{id:'one',name:'午餐',mode:'actual',startOffset:null,endOffset:null,quotaMinutes:null,punchMode:'none',paid:false}];assert.equal(validAttendanceRules(clean,720),false)
 clean.breaks[0].punchMode='required';assert.equal(validAttendanceRules(clean,720),true)
 clean.breaks[0].endOffset=180;assert.equal(validAttendanceRules(clean,720),false)
 clean.breaks[0].startOffset=120;clean.breaks[0].mode='quota';clean.breaks[0].quotaMinutes=61;assert.equal(validAttendanceRules(clean,720),false)
 clean.breaks[0].quotaMinutes=60;assert.equal(validAttendanceRules(clean,720),true)
 clean.anomalies[1].code='LATE';assert.equal(validAttendanceRules(clean,720),false)
})
test('legacy shift stays unchanged until explicitly enabling rules, then copies template snapshot',async()=>{
 const old={id:'old',version:3,name:'旧班',startTime:'11:00',endTime:'23:00',nextDay:false,breakMode:'duration',breakMinutes:30,breakAfterMinutes:360,breakStartOffset:0,breakEndOffset:0,breakPaid:false,enabled:true}
 const source={id:'template',name:'午晚班',enabled:true,version:2,rules:sampleAttendanceRules()}
 handler=async path=>path==='/shifts'?[old]:{items:[source],headOffice:false}
 const root=await mount(ShiftForm,{path:'/stores/A/workforce/shifts/old',manager:true})
 assert.equal(nodes(root,n=>n.tag==='select').length,2);assert.match(text(root),/达到出勤分钟数后扣除/)
 const checks=nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox');checkbox(root,'启用多段休息与考勤规则').props['onUpdate:modelValue'](true);await settle()
 const select=nodes(root,n=>n.tag==='select'&&nodes(n,o=>o.tag==='option'&&o.props.value==='template').length)[0];select.props['onUpdate:modelValue']('template');select.props.onChange();await settle()
 assert.match(text(root),/本地调整不修改原模板/);assert.equal(nodes(root,n=>n.tag==='input'&&n.props.value==='午餐').length,1)
 assert.match(text(root),/班次跨度12小时 · 固定非计薪休息4小时 · 计划净工时8小时/)
 const firstBreakName=nodes(root,n=>n.tag==='input'&&n.props.value==='午餐')[0];firstBreakName.props.onInput({target:{value:'自订午餐'}});await settle()
 assert.equal(source.rules.breaks[0].name,'午餐')
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
 const saved=calls.find(c=>c.method==='PUT').body;assert.equal(saved.rules.sourceTemplateId,'template');assert.equal(saved.rules.sourceTemplateVersion,2);assert.equal(saved.rules.breaks[0].name,'自订午餐');assert.equal(saved.breakMode,'duration');assert.equal(saved.expectedVersion,3)
})
test('manager can view templates but cannot create or write them',async()=>{
 handler=async()=>({items:[{id:'a',name:'共享模板',enabled:true,version:1,rules:defaultAttendanceRules()}],headOffice:false})
 const root=await mount(Settings,{manager:true});assert.equal(button(root,'新增模板'),undefined)
 button(root,'查看').props.onClick();await settle();assert.equal(button(root,'保存模板'),undefined)
 assert.equal(nodes(root,n=>n.tag==='fieldset'&&n.props.disabled).length>=1,true);assert.equal(calls.filter(c=>c.method==='PUT').length,0)
})
test('disabling new rules writes explicit null and restores legacy mode',async()=>{
 const old={id:'old',version:4,name:'夜班',startTime:'22:00',endTime:'06:00',nextDay:true,breakMode:'manual',breakMinutes:0,breakAfterMinutes:0,breakStartOffset:0,breakEndOffset:0,breakPaid:false,enabled:true,rules:defaultAttendanceRules()}
 handler=async path=>path==='/shifts'?[old]:{items:[],headOffice:true}
 const root=await mount(ShiftForm,{path:'/stores/A/workforce/shifts/old'})
 checkbox(root,'启用多段休息与考勤规则').props['onUpdate:modelValue'](false);await settle()
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
 const saved=calls.find(c=>c.method==='PUT').body;assert.equal(saved.rules,null);assert.equal(saved.breakMode,'manual');assert.equal(saved.nextDay,true)
})
test('clock editor retains an invalid pre-midnight value until next-day is selected',async()=>{
 const initial=defaultAttendanceRules();initial.breaks=[{id:'night',name:'夜宵',mode:'window',startOffset:90,endOffset:120,quotaMinutes:null,punchMode:'none',paid:false}]
 const value=ref(initial),validity=[]
 const Wrapper={render:()=>h(Editor,{modelValue:value.value,startTime:'22:00',duration:480,'onUpdate:modelValue':v=>value.value=v,onValid:v=>validity.push(v)})}
 const root=await mount(Wrapper),time=nodes(root,n=>n.tag==='input'&&n.props.type==='time')[0]
 time.props.onInput({target:{value:'00:30'}});await settle();assert.equal(validity.at(-1),false)
 nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox')[0].props.onChange({target:{checked:true}});await settle()
 assert.equal(value.value.breaks[0].startOffset,150)
 // End is still 00:00, so overlapping/backward input is never silently accepted.
 assert.equal(validity.at(-1),false)
 const end=nodes(root,n=>n.tag==='input'&&n.props.type==='time')[1];end.props.onInput({target:{value:'01:00'}});await settle();assert.equal(value.value.breaks[0].endOffset,180);assert.equal(validity.at(-1),true)
})
test('head office creates and disables templates with optimistic version, no deletion',async()=>{
 const root=await mount(Settings);button(root,'新增模板').props.onClick();await settle()
 nodes(root,n=>n.tag==='input'&&n.props.maxlength==='80')[0].props['onUpdate:modelValue']('午晚班')
 nodes(root,n=>n.tag==='input'&&n.props.type==='checkbox')[0].props['onUpdate:modelValue'](false);await settle()
 await nodes(root,n=>n.tag==='form')[0].props.onSubmit({preventDefault(){}});await settle()
 const saved=calls.find(c=>c.method==='PUT');assert.equal(saved.body.name,'午晚班');assert.equal(saved.body.expectedVersion,0);assert.equal(saved.body.enabled,false);assert.equal(saved.body.rules.breaks.length,0)
})
test('template errors are visible and editor disabled mode prevents mutations',async()=>{
 handler=async()=>{throw new Error('offline')};const root=await mount(Settings);assert.match(text(root),/请求未完成/)
 let emitted=0;const locked=await mount(Editor,{props:{modelValue:defaultAttendanceRules(),disabled:true,'onUpdate:modelValue':()=>emitted++}})
 button(locked,'新增休息段').props.onClick();await settle();assert.equal(emitted,0)
})

