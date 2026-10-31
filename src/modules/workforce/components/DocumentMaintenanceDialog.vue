<script setup lang="ts">
import EmployeeDocuments from './EmployeeDocuments.vue'
import {useDocumentExpiryText} from '../documentExpiry'
defineProps<{employeeId:string;documentId:string;employeeName:string}>()
const emit=defineEmits<{close:[];changed:[]}>(),{x}=useDocumentExpiryText()
</script>
<template><EmployeeDocuments :employee-id="employeeId" :document-id="documentId" :dialog-title="x('maintain')+' · '+employeeName" editor-only @close="emit('close')" @changed="emit('changed')"/></template>
