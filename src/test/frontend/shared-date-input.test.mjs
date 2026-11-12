// Run with --import ./scripts/tests/workforce-register.mjs
import {test,before,after,afterEach} from 'node:test'
import assert from 'node:assert/strict'
import {createRenderer,h,nextTick,reactive} from 'vue'
import {createI18n} from 'vue-i18n'
import DateInput from '../../components/common/DateInput.vue'
import {validDateInput,displayDateInput,parseDateInput,dateInputPlaceholder} from '../../components/common/dateInput.ts'
import {bareDateInputs} from '../../../scripts/check-date-inputs.mjs'
const mounted=[]
const oldDom={Document:globalThis.Document,ShadowRoot:globalThis.ShadowRoot,document:globalThis.document}
before(()=>{globalThis.Document=class {};globalThis.ShadowRoot=class {};globalThis.document={activeElement:null}})
after(()=>Object.assign(globalThis,oldDom))
const nodes=(n,p)=>[...(p(n)?[n]:[]),...(n.children||[]).flatMap(c=>nodes(c,p))]
const tick=async()=>{for(let i=0;i<10;i++){await Promise.resolve();await nextTick()}}
function element(tag){const n={tag,props:{},children:[],value:'',setCustomValidity(message){this.customMessage=message},getRootNode(){return {}},addEventListener(){},removeEventListener(){}};Object.defineProperty(n,'validity',{get(){return {valid:!n.nativeError}}});Object.defineProperty(n,'validationMessage',{get(){return n.nativeError||''}});return n}
const renderer=createRenderer({createElement:element,createText:text=>({text}),createComment:()=>({}),setText:(n,t)=>n.text=t,setElementText:(n,t)=>n.text=t,patchProp:(n,k,o,v)=>{n.props[k]=v;if(['value','type','disabled','readonly','required','name','id'].includes(k))n[k]=v},insert:(n,p)=>{n.parent=p;p.children.push(n)},remove:n=>{if(n.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},parentNode:n=>n.parent,nextSibling:()=>null})
function mount(input={},locale='zh-CN'){const props=reactive({modelValue:'',...input}),root={children:[]},events=[];const app=renderer.createApp({render:()=>h(DateInput,{...props,'onUpdate:modelValue':v=>{props.modelValue=v;events.push({model:v})},onInput:e=>events.push({input:e.target.value,target:e.target}),onChange:e=>events.push({change:e.target.value,target:e.target}),'onValidity-change':valid=>events.push({valid})})});const i18n=createI18n({legacy:false,locale,messages:{}});app.use(i18n);app.mount(root);mounted.push(app);return{props,root,events,i18n,text:()=>nodes(root,n=>n.tag==='input'&&n.type==='text')[0],native:()=>nodes(root,n=>n.tag==='input'&&n.type!=='text')[0]}}
afterEach(()=>{for(const app of mounted.splice(0))app.unmount()})
test('calendar validation covers leap years, month bounds and local timestamps without timezone conversion',()=>{
 for(const value of ['','0001-01-01','2024-02-29','2026-11-03','9999-12-31'])assert.equal(validDateInput(value),true,value)
 for(const value of ['0000-01-01','2026-02-30','2025-02-29','2026-13-01','03/11/2026','2026-11'])assert.equal(validDateInput(value),false,value)
 assert.equal(validDateInput('2026-11','month'),true);assert.equal(validDateInput('2026-13','month'),false)
 for(const value of ['2026-11-03T00:00','2026-11-03T23:59:59.123'])assert.equal(validDateInput(value,'datetime-local'),true)
 for(const value of ['2026-11-03T24:00','2026-11-03T12:60','2026-11-03T12:30Z'])assert.equal(validDateInput(value,'datetime-local'),false)
 assert.equal(displayDateInput('2026-11-03T12:30','datetime-local'),'2026-11-03 12:30');assert.equal(parseDateInput('2026-11-03 12:30','datetime-local'),'2026-11-03T12:30')
})
test('all three formats have explicit Chinese and English placeholders',()=>{
 for(const type of ['date','month','datetime-local'])for(const en of [false,true])assert.ok(dateInputPlaceholder(type,en));assert.equal(dateInputPlaceholder('date',false),'年-月-日');assert.equal(dateInputPlaceholder('month',true),'YYYY-MM')
})
test('typing and native selection synchronize one ISO model and one canonical input/change callback',async()=>{
 const s=mount({name:'businessDate',id:'business',min:'2026-01-01',max:'2026-12-31',required:true});await tick();assert.equal(s.text().name,'businessDate');assert.equal(s.native().name,undefined);assert.equal(s.native().props.min,'2026-01-01');assert.equal(s.native().props.max,'2026-12-31')
 s.text().value='2026-11-03';s.text().props.onInput({target:s.text()});s.text().props.onChange({target:s.text()});await tick();assert.equal(s.props.modelValue,'2026-11-03');assert.equal(s.native().value,'2026-11-03');assert.equal(s.events.filter(e=>'input'in e).length,1);assert.equal(s.events.find(e=>'change'in e).change,'2026-11-03');assert.equal(s.events.find(e=>'change'in e).target,s.native())
 s.native().props['onUpdate:modelValue']('2026-11-04');await tick();assert.equal(s.text().value,'2026-11-04');assert.equal(s.props.modelValue,'2026-11-04')
})
test('invalid partial drafts block form validity, publish an empty canonical value and survive parent echo',async()=>{
 const s=mount({modelValue:'2026-11-03'});await tick();s.text().props['onUpdate:modelValue']('2026-11-');await tick();assert.equal(s.props.modelValue,'');assert.equal(s.text().value,'2026-11-');assert.ok(s.text().customMessage);assert.equal(s.events.filter(e=>'valid'in e).at(-1).valid,false)
 s.text().props['onUpdate:modelValue']('2026-11-05');await tick();assert.equal(s.text().customMessage,'');assert.equal(s.props.modelValue,'2026-11-05');s.props.modelValue='2026-12-01';await tick();assert.equal(s.text().value,'2026-12-01')
})
test('readonly and disabled remain noneditable; native constraint errors reach the visible input',async()=>{
 const s=mount({modelValue:'2026-11-03',readonly:true,required:true});await tick();assert.equal(s.text().readonly,true);assert.equal(s.native().disabled,true);assert.equal(s.props.modelValue,'2026-11-03');s.props.readonly=false;s.props.disabled=true;await tick();assert.equal(s.text().disabled,true);s.props.disabled=false;s.native().nativeError='Outside range';s.text().props['onUpdate:modelValue']('2026-11-05');await tick();assert.equal(s.text().customMessage,'Outside range')
})
test('month and local datetime preserve values and step while language and external values update',async()=>{
 const s=mount({type:'month',modelValue:'2026-11'});await tick();assert.equal(s.text().value,'2026-11');assert.equal(s.text().props.placeholder,'年-月');s.props.type='datetime-local';s.props.modelValue='2026-11-03T12:30';s.props.step=900;await tick();assert.equal(s.text().value,'2026-11-03 12:30');assert.equal(s.native().props.step,900);s.i18n.global.locale.value='en-SG';await tick();assert.equal(s.text().props.placeholder,'YYYY-MM-DD HH:mm');s.text().props['onUpdate:modelValue']('2026-11-03 12:45');await tick();assert.equal(s.props.modelValue,'2026-11-03T12:45')
})
test('all product pages use the shared component instead of native date placeholders',()=>assert.deepEqual(bareDateInputs(),[]))

test('an explicit reset key clears an invalid local draft even when the canonical model is already empty',async()=>{
 const s=mount({resetKey:0});await tick();s.text().props['onUpdate:modelValue']('2026-11-');await tick();assert.equal(s.props.modelValue,'');assert.ok(s.text().customMessage);s.props.resetKey=1;await tick();assert.equal(s.text().value,'');assert.equal(s.text().customMessage,'')
})
