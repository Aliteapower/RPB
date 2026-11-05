<script setup lang="ts">
import {computed,nextTick,onMounted,onUpdated,ref,shallowRef,useId,watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {dateInputPlaceholder,displayDateInput,parseDateInput,validDateInput,type DateInputType} from './dateInput'
defineOptions({inheritAttrs:false})
const props=withDefaults(defineProps<{modelValue?:string|null;value?:string|null;type?:DateInputType;min?:string;max?:string;step?:string|number;required?:boolean;disabled?:boolean;readonly?:boolean;id?:string;name?:string;placeholder?:string;calendarLabel?:string;resetKey?:number|string}>(),{type:'date'})
const emit=defineEmits<{'update:modelValue':[string];input:[Event];change:[Event];'validity-change':[boolean]}>()
const {locale}=useI18n(),english=computed(()=>locale.value.startsWith('en')),ownId=useId(),textInput=shallowRef<HTMLInputElement|null>(null),picker=shallowRef<HTMLInputElement|null>(null)
const external=()=>props.modelValue??props.value??'',draft=ref(displayDateInput(external(),props.type))
let lastEmitted:string|undefined,lastValid:boolean|undefined
const hint=computed(()=>props.placeholder||dateInputPlaceholder(props.type,english.value))
const pickerLabel=computed(()=>props.calendarLabel||(props.type==='month'?(english.value?'Choose month':'选择月份'):props.type==='datetime-local'?(english.value?'Choose date and time':'选择日期和时间'):(english.value?'Choose date':'选择日期')))
const normalized=computed(()=>{const value=parseDateInput(draft.value,props.type);return validDateInput(value,props.type)?value:''})
function validate(){
 const raw=parseDateInput(draft.value,props.type),valid=validDateInput(raw,props.type)
 let message=valid?'':english.value?'Enter a valid date in the shown format':'请按提示格式输入有效日期'
 if(!message&&picker.value?.validity&&!picker.value.validity.valid)message=picker.value.validationMessage
 textInput.value?.setCustomValidity?.(message)
 const complete=!message&&(!props.required||!!raw)
 if(lastValid!==complete){lastValid=complete;emit('validity-change',complete)}
 return complete
}
function publish(raw:string){
 draft.value=displayDateInput(raw,props.type)
 let value=validDateInput(parseDateInput(raw,props.type),props.type)?parseDateInput(raw,props.type):''
 if(picker.value){picker.value.value=value;value=picker.value.value}
 lastEmitted=value;emit('update:modelValue',value);validate()
}
const textValue=computed({get:()=>draft.value,set:(value:string)=>publish(value)})
const calendarValue=computed({get:()=>normalized.value,set:(value:string)=>publish(value)})
function inputEvent(event:Event=new Event('input')){const value=(event.target as HTMLInputElement|undefined)?.value;if(typeof value==='string'&&parseDateInput(value,props.type)!==parseDateInput(draft.value,props.type))publish(value);forwarded('input',event)}
function emitEvent(type:'input'|'change',event:Event){if(type==='input')emit('input',event);else emit('change',event)}
function forwarded(type:'input'|'change',event:Event=new Event(type)){
 if(!picker.value){emitEvent(type,event);return}
 if(event.target===picker.value){emitEvent(type,event);return}
 const native=new Event(type,{bubbles:true});Object.defineProperty(native,'target',{value:picker.value});Object.defineProperty(native,'currentTarget',{value:picker.value});emitEvent(type,native)
}
watch(()=>[props.modelValue,props.value,props.type,props.resetKey] as const,(next,old)=>{
 const value=external()
 if(next[2]!==old[2]||next[3]!==old[3]||value!==lastEmitted)draft.value=displayDateInput(value,props.type)
 void nextTick(validate)
})
onMounted(()=>void nextTick(validate));onUpdated(validate)
</script>
<template><span class="rpb-date-input" :class="{'is-readonly':readonly}"><input v-bind="$attrs" :id="id||ownId" ref="textInput" v-model="textValue" class="rpb-date-input__text" type="text" :name="name" :required="required" :disabled="disabled" :readonly="readonly" :placeholder="hint" autocomplete="off" :aria-invalid="!validDateInput(parseDateInput(draft,type),type)||undefined" @input="inputEvent" @change="forwarded('change',$event)"><span class="rpb-date-input__calendar"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 11h18"/></svg><input :id="(id||ownId)+'-picker'" ref="picker" v-model="calendarValue" :type="type" :min="min" :max="max" :step="step" :disabled="disabled||readonly" :readonly="readonly" :required="required" :aria-label="pickerLabel" @input="inputEvent" @change="forwarded('change',$event)"></span></span></template>
<style scoped>
.rpb-date-input{position:relative;display:inline-flex;min-width:0;max-width:100%;vertical-align:middle}
.rpb-date-input>input.rpb-date-input__text{width:100%;min-width:0;box-sizing:border-box;padding:10px 44px 10px 12px;border:1px solid #b8cdd1;border-radius:8px;background:#fff;color:inherit;font:inherit;line-height:1.4;min-height:40px;font-variant-numeric:tabular-nums}
.rpb-date-input>input:disabled{background:#f3f7f7;color:#61777b}
.rpb-date-input>input[aria-invalid=true]{border-color:#bc3d36}
.rpb-date-input__calendar{position:absolute;right:1px;top:1px;bottom:1px;width:42px;display:flex;align-items:center;justify-content:center;color:#61777b;border-radius:6px}
.rpb-date-input__calendar:focus-within{outline:2px solid #155f52;outline-offset:1px}
.rpb-date-input__calendar input{position:absolute;inset:0;opacity:0;width:100%;min-width:0;height:100%;padding:0;cursor:pointer}
.rpb-date-input__calendar input::-webkit-calendar-picker-indicator{position:absolute;inset:0;width:auto;height:auto;margin:0;padding:0;cursor:pointer}
.is-readonly .rpb-date-input__calendar{pointer-events:none}
</style>
