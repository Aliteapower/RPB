<script setup lang="ts">
import {usePayrollText} from '../payrollText'
import {usePhaseText} from '../phaseText'
defineProps<{base:string;companyId?:string}>()
const {r}=usePayrollText()
const {english}=usePhaseText()
</script>
<template><RouterLink :to="{path:base+'/salary-templates',query:companyId?{companyId}:undefined}"><strong>{{r('templates')}}</strong><span>{{r('templateEntryHint')}}</span></RouterLink><RouterLink :to="{path:base+'/pay-profiles',query:companyId?{companyId}:undefined}"><strong>{{r('profiles')}}</strong><span>{{r('profileEntryHint')}}</span></RouterLink><RouterLink :to="{path:base+'/pay-change-requests',query:companyId?{companyId}:undefined}"><strong>{{english()?'Pay change requests':'工资变更申请'}}</strong><span>{{english()?'Request store template, employee pay and monthly changes within your access.':'统一办理门店模板、员工方案与月度调整；可办范围按权限显示。'}}</span></RouterLink></template>
