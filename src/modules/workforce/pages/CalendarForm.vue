<script setup lang="ts">
import {onMounted,reactive,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {usePhasePage} from '../phasePage'
import {usePhaseThreeText} from '../phaseThreeText'
import {storeToday} from '../time'
import {validDay} from '../timekeepingUi'
import type {CalendarPage} from '../phaseThreeTypes'
import PhaseStatus from '../components/PhaseStatus.vue'
const route=useRoute(),router=useRouter(),editing=!!route.params.id,id=String(route.params.id||crypto.randomUUID()),{api,home,t,p,base,loading,busy,failure,notice,load,write,current}=usePhasePage(),{q}=usePhaseThreeText(),found=ref(!editing),draft=reactive({expectedVersion:0,date:typeof route.query.date==='string'?route.query.date:storeToday(),kind:'public_holiday',name:'',enabled:true})
onMounted(()=>{if(!home.value?.employeeManage){failure.value=p('noAccess');return}if(editing){if(!validDay(draft.date)){failure.value=p('invalid');return}void load(()=>api().get<CalendarPage>(`/calendar?from=${draft.date}&to=${draft.date}`),v=>{const item=v.items.find(e=>e.id===id);if(!item){failure.value=t('notFound');return}found.value=true;Object.assign(draft,{expectedVersion:item.version,date:item.date,kind:item.kind,name:item.name,enabled:item.enabled})})}})
function save(){if(!home.value?.employeeManage||!found.value)return;if(!validDay(draft.date)||!draft.name.trim()||draft.name.length>100){failure.value=p('invalid');return}void write(()=>api().put('/calendar/'+id,{...draft,name:draft.name.trim()}),async()=>{if(current())await router.push(base.value+'/calendar')})}
</script>
<template><section class="wf-panel wf-form-panel"><h2>{{q(editing?'editEvent':'newEvent')}}</h2><PhaseStatus :failure="failure" :notice="notice" :loading="loading"/><form v-if="home?.employeeManage&&found&&!loading" @submit.prevent="save"><fieldset :disabled="busy"><div class="wf-grid"><label>{{q('date')}}<input v-model="draft.date" required type="date"></label><label>{{q('kind')}}<select v-model="draft.kind"><option value="public_holiday">{{q('public_holiday')}}</option><option value="store_rest">{{q('store_rest')}}</option></select></label></div><label>{{q('name')}}<input v-model="draft.name" required maxlength="100"></label><label class="wf-check"><input v-model="draft.enabled" type="checkbox">{{q('enabled')}}</label></fieldset><p class="wf-hint">{{q('calendarHint')}}</p><footer class="wf-form-actions"><RouterLink class="wf-button" :to="base+'/calendar'">{{t('cancel')}}</RouterLink><button class="primary" :disabled="busy">{{busy?t('saving'):t('save')}}</button></footer></form></section></template>
