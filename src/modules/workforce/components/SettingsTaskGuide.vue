<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import {useWorkforce} from '../context'
import {usePhaseText} from '../phaseText'
import {settingsSubpageGuidance} from '../settingsSubpageGuidance'
const props=defineProps<{section:string;form?:boolean}>()
const {home,payrollHome,store}=useWorkforce(),{english}=usePhaseText(),route=useRoute()
const guide=computed(()=>settingsSubpageGuidance(props.section,{employeeManage:!!home.value?.employeeManage,headOffice:!!home.value?.headOffice,companyIds:payrollHome?.value?.companies.map(c=>c.id)||[],storeCompanyId:home.value?.stores?.find(s=>s.id===store.value)?.companyId},String(route.query.companyId||''),!!props.form,english()))
</script>
<template><aside v-if="guide" class="wf-panel wf-settings-guide" :aria-label="english()?'How to complete this setting':'本页办理指引'">
 <details :open="!form"><summary>{{english()?'How to complete this setting':'本页办理指引'}}</summary><ol><li v-for="(instruction,index) in guide.steps" :key="index"><strong>{{english()?['Before you start','On this page','What happens next'][index]:['先确认','本页办理','完成以后'][index]}}</strong><p>{{instruction}}</p></li></ol>
 <RouterLink v-if="guide.next" class="wf-button" :to="{path:`/stores/${store}/workforce/${guide.next.path}`,query:{...(guide.next.companyId?{companyId:guide.next.companyId}:{}),...(guide.next.path==='management'?{step:'3'}:{})}}">{{guide.nextLabel}}</RouterLink>
</details></aside></template>
