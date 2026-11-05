import test from 'node:test'
import assert from 'node:assert/strict'
import {createRenderer,h,nextTick,ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {createRouter,createMemoryHistory} from 'vue-router'
import {workforceKey} from '../../src/modules/workforce/context.ts'
import PayPlanEstimate from '../../src/modules/workforce/components/PayPlanEstimate.vue'
import {estimateDays,initialEstimateMonth,addMonthlyAllowance} from '../../src/modules/workforce/fullAttendanceEstimate.ts'


const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const text=n=>(n.text||'')+(n.children||[]).map(text).join(' ')
const settle=async()=>{for(let i=0;i<30;i++){await Promise.resolve();await nextTick()}}
function element(tag){return {tag,props:{},children:[],addEventListener(){},removeEventListener(){},getRootNode(){return {}},style:{},get options(){return nodes(this,n=>n.tag==='option')}}}
const renderer=createRenderer({createElement:element,createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>{n.text=t;n.children=[]},patchProp:(n,k,o,v)=>{n.props[k]=v;n[k]=v},insert:(n,p,a)=>{if(n.parent)n.parent.children=n.parent.children.filter(c=>c!==n);n.parent=p;const i=a?p.children.indexOf(a):-1;if(i<0)p.children.push(n);else p.children.splice(i,0,n)},remove:n=>{if(n?.parent)n.parent.children=n.parent.children.filter(c=>c!==n)},parentNode:n=>n?.parent,nextSibling:n=>{const a=n?.parent?.children||[];return a[a.indexOf(n)+1]||null}})

test('full attendance days come from explicit working weekdays and common items keep their meaning',()=>{assert.equal(estimateDays('2026-10',[1,2,3,4,5]),22);assert.equal(estimateDays('2026-10',[]),'');const items=[];addMonthlyAllowance(items,'attendance',false);addMonthlyAllowance(items,'attendance',false);assert.equal(items.length,1);assert.equal(items[0].kind,'ow_attendance');assert.equal(items[0].proration,'full')})
test('estimate does not save payroll and changing assumptions clears stale money',async()=>{globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document={};const rule=ref({basis:'monthly',rate:'3000',dayHours:'8',weekHours:'44',overtimeEnabled:false,overtimeMultiplier:'1.5',items:[],generation:{mode:'attendance',workingWeekdays:[1,2,3,4,5]}});let finish;const posts=[];const root=element('root'),app=renderer.createApp({render:()=>h(PayPlanEstimate,{companyId:'C',employeeId:'E',effectiveFrom:'2026-10-01',initialMonth:'2026-10',rule:rule.value,cpf:{citizenship:'foreign',birthDate:null,prDate:null,prMode:'GG',electionReference:''},cdac:{status:'not_applicable',reference:''}})});app.use(createI18n({legacy:false,locale:'zh-CN',messages:{}}));const router=createRouter({history:createMemoryHistory(),routes:[{path:'/:pathMatch(.*)*',component:{render:()=>h('div')}}]});await router.push('/stores/S/workforce/pay-profiles/new');app.use(router);app.provide(workforceKey,{store:ref('S'),home:ref({}),current:()=>true,api:()=>({post:(p,b)=>{posts.push([p,b]);return new Promise(r=>finish=r)}})});app.mount(root);await settle();const button=nodes(root,n=>n.tag==='button'&&text(n).includes('测算满勤所得'))[0];assert.ok(button);button.props.onClick();await settle();assert.equal(posts[0][0],'/pay-plan-estimate');rule.value.rate='3200';await settle();finish({month:'2026-10',days:22,dayHours:'8',workSeconds:633600,policy:'P',assumptions:[],breakdown:{gross:'3000.00',net:'2999.00',items:[]}});await settle();assert.doesNotMatch(text(root),/2999/);assert.equal(posts.length,1);app.unmount()})

test('common monthly allowance shortcuts cannot duplicate when switching languages',()=>{for(const preset of ['duty','housing','attendance','other']){const items=[];addMonthlyAllowance(items,preset,false);addMonthlyAllowance(items,preset,true);assert.equal(items.length,1)}})

test('saved old plans estimate current month, future plans their effective month, explicit historical payroll context wins',()=>{assert.equal(initialEstimateMonth('2020-01-01','','2026-10-31'),'2026-10');assert.equal(initialEstimateMonth('2026-12-01','','2026-10-31'),'2026-12');assert.equal(initialEstimateMonth('2020-01-01','2025-09','2026-10-31'),'2025-09')})
