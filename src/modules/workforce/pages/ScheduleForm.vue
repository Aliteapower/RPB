<script setup lang="ts">
import {useRoute,useRouter} from 'vue-router'
import {usePhasePage} from '../phasePage'
import ScheduleEditor from '../components/ScheduleEditor.vue'
const route=useRoute(),router=useRouter(),{base}=usePhasePage()
function back(){void router.push(base.value+'/schedule')}
</script>
<template><section class="wf-panel wf-form-panel"><ScheduleEditor :id="String(route.params.id||'')" :employee-id="route.query.employeeId" :work-date="route.query.workDate" @saved="back" @deleted="back" @close="back"/></section></template>
