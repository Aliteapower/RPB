<script setup lang="ts">
import {onMounted,reactive,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {usePhasePage} from '../phasePage'
import {usePhaseThreeText} from '../phaseThreeText'
import type {LeaveType} from '../phaseThreeTypes'
import PhaseStatus from '../components/PhaseStatus.vue'
const route=useRoute(),router=useRouter(),editing=!!route.params.id,id=String(route.params.id||crypto.randomUUID()),{api,home,t,p,base,loading,busy,failure,notice,load,write,current}=usePhasePage(),{q}=usePhaseThreeText(),found=ref(!editing),draft=reactive({expectedVersion:0,name:'',paid:false,enabled:true})
onMounted(()=>{if(!home.value?.employeeManage){failure.value=p('noAccess');return}if(editing)void load(()=>api().get<LeaveType[]>('/leave-types'),v=>{const s=v.find(x=>x.id===id);if(!s){failure.value=t('notFound');return}found.value=true;Object.assign(draft,{expectedVersion:s.version,name:s.name,paid:s.paid,enabled:s.enabled})})})
function save(){if(!home.value?.employeeManage||!found.value)return;if(!draft.name.trim()||draft.name.length>100){failure.value=p('invalid');return}void write(()=>api().put('/leave-types/'+id,{...draft,name:draft.name.trim()}),async()=>{if(current())await router.push(base.value+'/leave-types')})}
</script>
<template><section class="wf-panel wf-form-panel"><h2>{{q(editing?'editLeaveType':'newLeaveType')}}</h2><PhaseStatus :failure="failure" :notice="notice" :loading="loading"/><form v-if="home?.employeeManage&&found&&!loading" @submit.prevent="save"><fieldset :disabled="busy"><label>{{q('typeName')}}<input v-model="draft.name" required maxlength="100"></label><label class="wf-check"><input v-model="draft.paid" type="checkbox">{{q('paid')}}</label><label class="wf-check"><input v-model="draft.enabled" type="checkbox">{{q('enabled')}}</label></fieldset><p class="wf-hint">{{q('leaveTypeHint')}}</p><footer class="wf-form-actions"><RouterLink class="wf-button" :to="base+'/leave-types'">{{t('cancel')}}</RouterLink><button class="primary" :disabled="busy">{{busy?t('saving'):t('save')}}</button></footer></form></section></template>
