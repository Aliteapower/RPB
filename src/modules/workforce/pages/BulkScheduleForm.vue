<script setup lang="ts">
import {useRouter} from 'vue-router'
import BulkScheduleEditor from '../components/BulkScheduleEditor.vue'
const router=useRouter()
function changeStore(id:string){void router.push(`/stores/${encodeURIComponent(id)}/workforce/schedule/bulk`)}
</script>
<template><BulkScheduleEditor @store-changed="changeStore"/></template>
