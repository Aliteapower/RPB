<script setup lang="ts">
import {onBeforeUnmount,onMounted,ref} from 'vue'
import type {BulkResult} from '../bulkScheduleTypes'
import BulkScheduleEditor from './BulkScheduleEditor.vue'
defineProps<{periodFrom:string;periodTo:string;initialPosition:string;initialSearch:string}>()
const emit=defineEmits<{saved:[result:BulkResult,storeId:string];close:[]}>()
const dialog=ref<HTMLDialogElement|null>(null),blocked=ref(false)
function close(){if(!blocked.value)emit('close')}
onMounted(()=>dialog.value?.showModal?.())
onBeforeUnmount(()=>dialog.value?.close?.())
</script>
<template><dialog ref="dialog" class="wf-schedule-dialog wf-bulk-dialog" aria-modal="true" aria-labelledby="wf-bulk-schedule-title" @cancel.prevent="close"><BulkScheduleEditor inline :period-from="periodFrom" :period-to="periodTo" :initial-position="initialPosition" :initial-search="initialSearch" @blocked="blocked=$event" @close="close" @saved="(result,storeId)=>emit('saved',result,storeId)"/></dialog></template>
