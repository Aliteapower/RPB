<script setup lang="ts">
import {computed,useId} from 'vue'
import {validReportDate} from './reportDates'
const props=defineProps<{modelValue:string;label:string;placeholder:string;calendarLabel:string;min?:string;max?:string}>()
const emit=defineEmits<{'update:modelValue':[string]}>(),id=useId()
const value=computed({get:()=>props.modelValue,set:(value:string)=>emit('update:modelValue',value.trim())})
const calendarValue=computed({get:()=>validReportDate(props.modelValue)?props.modelValue:'',set:(value:string)=>emit('update:modelValue',value)})
</script>
<template><div class="storage-report-date-field"><label :for="id">{{label}}</label><div class="storage-report-date-control"><input :id="id" v-model="value" type="text" :placeholder="placeholder" pattern="[0-9]{4}-[0-9]{2}-[0-9]{2}" maxlength="10" autocomplete="off" :aria-invalid="validReportDate(modelValue)?undefined:true"><span class="storage-report-calendar"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 11h18"/></svg><input v-model="calendarValue" type="date" :min="min" :max="max" :aria-label="calendarLabel"></span></div></div></template>
