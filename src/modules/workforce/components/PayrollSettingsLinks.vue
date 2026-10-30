<script setup lang="ts">
import {usePayrollText} from '../payrollText'
defineProps<{base:string;companyId?:string}>()
const {r}=usePayrollText()
</script>
<template><RouterLink :to="{path:base+'/salary-templates',query:companyId?{companyId}:undefined}"><strong>{{r('templates')}}</strong><span>{{r('templateEntryHint')}}</span></RouterLink><RouterLink :to="{path:base+'/pay-profiles',query:companyId?{companyId}:undefined}"><strong>{{r('profiles')}}</strong><span>{{r('profileEntryHint')}}</span></RouterLink></template>
