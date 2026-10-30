<script setup lang="ts">
import {usePayrollText} from '../payrollText'
import {usePhaseText} from '../phaseText'
defineProps<{base:string;companyId?:string}>()
const {r}=usePayrollText()
const {english}=usePhaseText()
</script>
<template><RouterLink :to="{path:base+'/salary-templates',query:companyId?{companyId}:undefined}"><strong>{{r('templates')}}</strong><span>{{r('templateEntryHint')}}</span></RouterLink><RouterLink :to="{path:base+'/pay-profiles',query:companyId?{companyId}:undefined}"><strong>{{r('profiles')}}</strong><span>{{r('profileEntryHint')}}</span></RouterLink><RouterLink :to="{path:base+'/pay-change-requests',query:companyId?{companyId}:undefined}"><strong>{{english()?'Pay change requests':'工资变更申请'}}</strong><span>{{english()?'Create, submit and review personal and monthly pay changes.':'新建、提交及审批个人方案和月度工资变更。'}}</span></RouterLink></template>
