<script setup lang="ts">
import {usePhasePage} from '../phasePage'
import AttendanceSettingsNav from '../components/AttendanceSettingsNav.vue'
import AttendancePolicySettings from '../components/AttendancePolicySettings.vue'
import {attendanceSettingsText as text} from '../attendanceSettingsText'
const {base,home,english,t}=usePhasePage()
</script>
<template><section v-if="home?.employeeManage" class="wf-panel"><AttendanceSettingsNav :base="base" active="attendance-rules"/><h2>{{text('rules',english())}}</h2><AttendancePolicySettings/></section><p v-else role="alert">{{t('noAccess')}}</p></template>
