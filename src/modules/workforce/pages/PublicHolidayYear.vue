<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {useRoute} from 'vue-router'
import {usePhasePage} from '../phasePage'
import {useHolidayText,copyHolidayYear,suggestObserved,type PublicHoliday,type HolidayYear} from '../holidayYears'
import {storeToday,addDays} from '../time'
import PhaseStatus from '../components/PhaseStatus.vue'
import AttendanceSettingsNav from '../components/AttendanceSettingsNav.vue'
import {attendanceSettingsText as settingsText} from '../attendanceSettingsText'
const {api,home,current,base,t,p,english,load,write,loading,busy,failure,notice}=usePhasePage(),{h,weekday}=useHolidayText(),route=useRoute(),year=ref(Number(route.query.year)||Number(storeToday().slice(0,4))),sourceYear=ref(year.value-1),version=ref(0),loadedYear=ref<number|null>(null),rows=ref<PublicHoliday[]>([]),source=ref('')
const editing=ref(false),savedRows=ref<PublicHoliday[]>([])
const clone=(items:PublicHoliday[])=>items.map(item=>({...item}))
function editSaved(){if(loadedYear.value!==year.value||editing.value)return;rows.value=clone(savedRows.value);editing.value=true}
function discard(){rows.value=clone(savedRows.value);source.value='';editing.value=false}
function refresh(){editing.value=false;savedRows.value=[];rows.value=[];version.value=0;loadedYear.value=null;source.value='';sourceYear.value=year.value-1;void load(()=>api().get<HolidayYear>('/public-holidays/'+year.value),result=>{version.value=result.version;loadedYear.value=result.year;rows.value=result.items;savedRows.value=clone(result.items);editing.value=false})}
function template(){void load(()=>api().get<{source:string;items:PublicHoliday[]}>('/public-holiday-templates/'+year.value),result=>{editing.value=true;rows.value=result.items;source.value=result.source;notice.value=h('draft')})}
function copy(){void load(async()=>{const existing=await api().get<HolidayYear>('/public-holidays/'+sourceYear.value);return existing.items.length?existing.items:(await api().get<{items:PublicHoliday[]}>('/public-holiday-templates/'+sourceYear.value)).items},items=>{editing.value=true;rows.value=copyHolidayYear(items,year.value);notice.value=h('draft')})}
function add(){editing.value=true;rows.value.push({id:crypto.randomUUID(),date:'',name:'',englishName:'',observedOn:null,observedKind:'none',enabled:true,tentative:false})}
function suggest(row:PublicHoliday){if(!row.date)return;row.observedOn=suggestObserved(row,rows.value)||null;row.observedKind=row.observedOn?'agreed':'none'}
function save(){if(!home.value?.headOffice||loadedYear.value!==year.value)return;void write(async()=>{const result=await api().put<HolidayYear>('/public-holidays/'+year.value,{expectedVersion:version.value,items:rows.value.map(row=>({...row,observedOn:row.observedOn||null,observedKind:row.observedOn?row.observedKind==='none'?'agreed':row.observedKind:'none'}))});if(current()){version.value=result.version;loadedYear.value=result.year;rows.value=result.items;savedRows.value=clone(result.items);editing.value=false}})}
onMounted(refresh)
</script>
<style scoped>
.wf-holiday-year fieldset{min-width:0}
.wf-holiday-year table{min-width:1000px;table-layout:fixed}
.wf-holiday-year th:nth-child(1){width:150px}
.wf-holiday-year th:nth-child(2){width:150px}
.wf-holiday-year th:nth-child(3){width:165px}
.wf-holiday-year th:nth-child(4){width:215px}
.wf-holiday-year th:nth-child(5){width:145px}
.wf-holiday-year th:nth-child(6){width:115px}
.wf-holiday-year td{vertical-align:top}
.wf-holiday-year td input:not([type=checkbox]),.wf-holiday-year td select{width:100%;min-width:0;box-sizing:border-box}
.wf-holiday-year td small{display:block;margin-top:4px}
.wf-holiday-year td:nth-child(4) button{display:block;margin-top:4px}
.wf-holiday-year .wf-check{display:flex;align-items:center;gap:5px;white-space:nowrap;margin:0 0 8px}
.wf-holiday-year table.wf-saved-holidays{min-width:600px;table-layout:auto}
</style>
<template><section class="wf-panel wf-holiday-year"><AttendanceSettingsNav v-if="home?.employeeManage" :base="base" active="public-holidays"/><header class="wf-toolbar"><h2>{{h('annual')}}</h2><RouterLink class="wf-button" :to="base+'/calendar?month='+year+'-01'">{{h('calendar')}}</RouterLink></header><p class="wf-hint">{{h('hint')}}</p><form class="wf-filters" @submit.prevent="refresh"><label>{{h('year')}}<input v-model.number="year" type="number" min="2000" max="2100" required :disabled="busy||loading"></label><button :disabled="busy||loading">{{h('load')}}</button></form><PhaseStatus :loading="loading" :failure="failure" :notice="notice"/><p v-if="!home?.headOffice" class="wf-hint">{{h('readonly')}}</p><div v-if="home?.headOffice" class="wf-filters"><button type="button" :disabled="busy||loading||loadedYear!==year||![2026,2027].includes(year)" @click="template">{{h('template')}}</button><label>{{h('sourceYear')}}<input v-model.number="sourceYear" type="number" min="2000" max="2100" :disabled="busy||loading"></label><button type="button" :disabled="busy||loading||loadedYear!==year||sourceYear===year" @click="copy">{{h('copy')}}</button><button type="button" :disabled="busy||loading||loadedYear!==year||rows.length>=100" @click="add">{{h('add')}}</button></div><p class="wf-hint">{{h('rule')}}</p><p v-if="source"><a :href="source" target="_blank" rel="noopener">{{h('source')}}</a></p>
 <section v-if="loadedYear!==null&&!loading" class="wf-section"><header class="wf-toolbar"><h3>{{loadedYear}} · {{settingsText('savedHolidays',english())}} · {{savedRows.length}}</h3><button v-if="home?.headOffice" type="button" :disabled="busy||editing||loadedYear!==year" @click="editSaved">{{settingsText('editYear',english())}}</button></header><div class="wf-table-wrap"><table class="wf-saved-holidays"><thead><tr><th>{{h('date')}}</th><th>{{h('name')}}</th><th>{{h('observed')}}</th><th>{{t('status')}}</th></tr></thead><tbody><tr v-for="row in savedRows" :key="row.id"><td>{{row.date}}</td><td>{{row.name}}<small>{{row.englishName}}</small></td><td>{{row.observedOn||'—'}}</td><td>{{row.enabled?h('enabled'):t('disabled')}}</td></tr><tr v-if="!savedRows.length"><td colspan="4">{{settingsText('emptySaved',english())}}</td></tr></tbody></table></div></section>
 <form v-if="editing" @submit.prevent="save"><header class="wf-toolbar"><h3>{{loadedYear}} · {{settingsText('draft',english())}}</h3><button type="button" :disabled="busy||loading" @click="discard">{{settingsText('cancelDraft',english())}}</button></header><fieldset :disabled="busy||loading||loadedYear!==year||!home?.headOffice"><div class="wf-table-wrap"><table><thead><tr><th>{{h('date')}}</th><th>{{h('name')}}</th><th>{{h('englishName')}}</th><th>{{h('observed')}}</th><th>{{t('status')}}</th><th/></tr></thead><tbody><tr v-for="(row,index) in rows" :key="row.id"><td><input v-model="row.date" required type="date" :min="year+'-01-01'" :max="year+'-12-31'" :aria-label="h('date')+' '+(index+1)"><small>{{weekday(row.date)}}</small></td><td><input v-model="row.name" required maxlength="100" :aria-label="h('name')+' '+(index+1)"></td><td><input v-model="row.englishName" maxlength="100" :aria-label="h('englishName')+' '+(index+1)"></td><td><input v-model="row.observedOn" type="date" :min="row.date?addDays(row.date,1):undefined" :aria-label="h('observed')+' '+(index+1)"><small>{{row.observedOn?weekday(row.observedOn):''}}</small><select v-if="row.observedOn" v-model="row.observedKind" :aria-label="h('mode')+' '+(index+1)"><option value="official">{{h('official')}}</option><option value="agreed">{{h('agreed')}}</option></select><button type="button" :disabled="!row.date" @click="suggest(row)">{{h('suggest')}}</button></td><td><label class="wf-check"><input v-model="row.enabled" type="checkbox">{{h('enabled')}}</label><label class="wf-check"><input v-model="row.tentative" type="checkbox">{{h('tentative')}}</label></td><td><button type="button" @click="rows.splice(index,1)">{{h('remove')}}</button></td></tr><tr v-if="!rows.length"><td colspan="6" class="wf-empty">{{h('empty')}}</td></tr></tbody></table></div></fieldset><footer v-if="home?.headOffice" class="wf-form-actions"><button class="primary" :disabled="busy||loading||loadedYear!==year">{{h('save')}}</button></footer></form></section></template>

