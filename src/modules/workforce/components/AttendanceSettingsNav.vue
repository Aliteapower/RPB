<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import {attendanceSettingsText as text} from '../attendanceSettingsText'
defineProps<{base:string;active:string}>()
const {locale}=useI18n(),links=[{path:'leave-types',label:'leave'},{path:'public-holidays',label:'annual'},{path:'calendar',label:'calendar'},{path:'attendance-rules',label:'rules'}] as const
</script>
<template><nav class="wf-subnav" :aria-label="locale.startsWith('en')?'Attendance and leave settings':'考勤与假期设置'"><RouterLink v-for="link in links" :key="link.path" :to="base+'/'+link.path" :aria-current="active===link.path?'page':undefined">{{text(link.label,locale.startsWith('en'))}}</RouterLink></nav></template>
