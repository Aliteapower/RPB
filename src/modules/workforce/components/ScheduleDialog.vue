<script setup lang="ts">
import {onBeforeUnmount,onMounted,ref} from 'vue'
import type {Schedule} from '../types'
import ScheduleEditor from './ScheduleEditor.vue'
defineProps<{id?:string;employeeId?:string;workDate?:string}>()
const emit=defineEmits<{saved:[schedule:Schedule];deleted:[id:string];close:[]}>()
const dialog=ref<HTMLDialogElement|null>(null),blocked=ref(false)
function close(){if(!blocked.value)emit('close')}
onMounted(()=>dialog.value?.showModal?.())
onBeforeUnmount(()=>dialog.value?.close?.())
</script>
<template><dialog ref="dialog" class="wf-schedule-dialog" aria-modal="true" aria-labelledby="wf-schedule-editor-title" @cancel.prevent="close"><ScheduleEditor :id="id" :employee-id="employeeId" :work-date="workDate" @blocked="blocked=$event" @close="close" @saved="emit('saved',$event)" @deleted="emit('deleted',$event)"/></dialog></template>
