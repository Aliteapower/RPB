<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {useWorkforce} from '../context'
import {useWorkforceText} from '../text'
import {useDocumentExpiryText} from '../documentExpiry'
import {useEmployeeExpiryCounts} from '../employeeExpiryCounts'
import EmployeeExpiryDialog from '../components/EmployeeExpiryDialog.vue'
import {useDocumentText} from '../documentText'
import {useWorkModeText} from '../workMode'
const {w}=useWorkModeText()
import type {Employee} from '../types'
const {d}=useDocumentText(),{x}=useDocumentExpiryText(),alerts=useEmployeeExpiryCounts(),counts=alerts.counts,alertLoading=alerts.loading,alertFailed=alerts.failed
const selected=ref<{id:string;name:string;status:'due'|'expired'}|null>(null);let revision=0
const {api,home,store,current}=useWorkforce(),{t,error}=useWorkforceText(),items=ref<Employee[]>([]),loading=ref(true),failure=ref(''),offset=ref(0)
async function load(){const rev=++revision;loading.value=true;failure.value='';items.value=[];void alerts.read([]);try{const r=await api().get<{items:Employee[]}>(`/employees?limit=50&offset=${offset.value}`);if(current()&&rev===revision){items.value=r.items;void refreshAlerts()}}catch(e){if(current()&&rev===revision)failure.value=error(e)}finally{if(current()&&rev===revision)loading.value=false}}
function refreshAlerts(){return alerts.read(items.value.filter(e=>e.editable).map(e=>e.id))}
function showAlerts(e:Employee,status:'due'|'expired'){if(e.editable&&!alertLoading.value&&!alertFailed.value)selected.value={id:e.id,name:e.name,status}}
function page(delta:number){offset.value=Math.max(0,offset.value+delta);void load()}
onMounted(()=>{if(home.value?.employeeManage)void load();else loading.value=false})
</script>
<template><section v-if="home?.employeeManage" class="wf-panel"><header class="wf-toolbar"><h2>{{t('employees')}}</h2><RouterLink class="wf-button primary" :to="`/stores/${store}/workforce/employees/new`">{{t('create')}}</RouterLink></header><p v-if="failure" role="alert" class="wf-error">{{failure}}</p><p v-if="alertFailed" role="alert" class="wf-error">{{x('badgeFailure')}} <button type="button" :disabled="alertLoading" @click="refreshAlerts">{{t('refresh')}}</button></p><p v-if="loading" role="status">{{t('loading')}}</p><div v-else class="wf-table-wrap"><table><thead><tr><th>{{t('employeeNo')}}</th><th>{{t('name')}}</th><th>{{t('jobTitle')}}</th><th>{{t('status')}}</th><th>{{x('badges')}}</th><th>{{t('manage')}}</th></tr></thead><tbody><tr v-for="e in items" :key="e.id"><td>{{e.employeeNo}}</td><td>{{e.name}}</td><td>{{e.jobTitle||'—'}}</td><td><span class="wf-badge">{{t(e.status)}}</span><span v-if="e.enabled===false" class="wf-badge">{{d('disabled')}}</span></td><td><template v-if="e.editable"><small v-if="alertLoading">{{t('loading')}}</small><small v-else-if="alertFailed">{{x('unavailable')}}</small><div v-else class="wf-inline"><button v-if="(counts[e.id]?.due||0)>0" type="button" class="wf-badge wf-badge-warning" :title="x('badgeHint')" :aria-label="x('due')+' · '+e.name+' · '+counts[e.id].due" @click="showAlerts(e,'due')">{{x('due')}} {{counts[e.id].due}}</button><button v-if="(counts[e.id]?.expired||0)>0" type="button" class="wf-badge wf-employee-expired" :aria-label="x('expired')+' · '+e.name+' · '+counts[e.id].expired" @click="showAlerts(e,'expired')">{{x('expired')}} {{counts[e.id].expired}}</button><span v-if="!(counts[e.id]?.due||counts[e.id]?.expired)">—</span></div></template><span v-else>—</span></td><td><div v-if="e.editable" class="wf-inline"><RouterLink :to="`/stores/${store}/workforce/employees/${e.id}/edit`">{{t('edit')}}</RouterLink><RouterLink :to="`/stores/${store}/workforce/employees/${e.id}/work-mode`">{{w('title')}}</RouterLink><RouterLink :to="`/stores/${store}/workforce/employees/${e.id}/stores`">{{t('assignments')}}</RouterLink></div><small v-else>{{t('partial')}}</small></td></tr><tr v-if="!items.length"><td colspan="6" class="wf-empty">{{t('empty')}}</td></tr></tbody></table></div><footer class="wf-pagination"><button :disabled="loading||!!selected||offset===0" @click="page(-50)">{{t('previous')}}</button><button :disabled="loading||!!selected||items.length<50" @click="page(50)">{{t('next')}}</button></footer></section><p v-else role="alert">{{t('noAccess')}}</p><EmployeeExpiryDialog v-if="selected" :employee-id="selected.id" :employee-name="selected.name" :status="selected.status" @close="selected=null" @changed="refreshAlerts"/></template>

<style scoped>
.wf-employee-expired{color:#9a3935;background:#fff1f0;border-color:#efd4d2}
</style>

