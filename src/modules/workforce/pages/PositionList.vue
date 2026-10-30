<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {useWorkforce} from '../context'
import {useWorkforceText} from '../text'
import {usePositionText} from '../positionText'
import {WorkforceApiError} from '../api'
import type {Position} from '../types'
import {useWorkModeText,type WorkMode} from '../workMode'
import PositionSalaryDefaults from '../components/PositionSalaryDefaults.vue'
import WorkModeFields from '../components/WorkModeFields.vue'
const {w}=useWorkModeText(),attendanceMode=ref<WorkMode|undefined>(undefined)
const {api,home,current}=useWorkforce(),{t,error}=useWorkforceText(),{j}=usePositionText()
const items=ref<Position[]>([]),loading=ref(true),busy=ref(false),failure=ref(''),notice=ref(''),editing=ref<Position|null>(null),name=ref(''),enabled=ref(true),deleting=ref('')
let newId=crypto.randomUUID()
async function load(){items.value=await api().get<Position[]>('/positions')}
onMounted(async()=>{if(!home.value?.headOffice){loading.value=false;return}try{await load()}catch(e){if(current())failure.value=error(e)}finally{if(current())loading.value=false}})
function reset(){attendanceMode.value=undefined;editing.value=null;name.value='';enabled.value=true;newId=crypto.randomUUID();deleting.value=''}
function edit(p:Position){editing.value=p;attendanceMode.value=p.attendanceMode||undefined;name.value=p.name;enabled.value=p.enabled;deleting.value='';notice.value=''}
async function run(action:()=>Promise<unknown>,message:string){if(busy.value)return;busy.value=true;failure.value='';notice.value='';try{await action();if(!current())return;reset();await load();if(current())notice.value=message}catch(e){if(current())failure.value=e instanceof WorkforceApiError&&e.code==='WORKFORCE_POSITION_IN_USE'?j('inUse'):error(e)}finally{if(current())busy.value=false}}
function save(){return run(()=>api().put(`/positions/${editing.value?.id||newId}`,{expectedVersion:editing.value?.version||0,name:name.value,enabled:enabled.value,...(attendanceMode.value?{attendanceMode:attendanceMode.value}:{})}),j('saved'))}
function toggle(p:Position){return run(()=>api().put(`/positions/${p.id}`,{expectedVersion:p.version,name:p.name,enabled:!p.enabled}),j('saved'))}
function remove(p:Position){return run(()=>api().put(`/positions/${p.id}/deletion`,{expectedVersion:p.version}),j('removed'))}
</script>
<template><section v-if="home?.headOffice" class="wf-panel">

 <h2>{{j('title')}}</h2><p class="wf-hint">{{j('hint')}}</p><p v-if="failure" role="alert" class="wf-error">{{failure}}</p><p v-if="notice" role="status">{{notice}}</p><p v-if="loading" role="status">{{t('loading')}}</p>
 <template v-else><form @submit.prevent="save"><fieldset :disabled="busy"><legend>{{j(editing?'edit':'add')}}</legend><div class="wf-filters"><label>{{j('name')}}<input v-model="name" required maxlength="80"></label><WorkModeFields v-model="attendanceMode" optional/><label class="wf-check"><input v-model="enabled" type="checkbox">{{j('enabled')}}</label><button class="primary" :disabled="busy">{{j(busy?'saving':'save')}}</button><button v-if="editing" type="button" @click="reset">{{j('cancel')}}</button></div></fieldset></form>
 <div class="wf-table-wrap"><table><thead><tr><th>{{j('name')}}</th><th>{{w('mode')}}</th><th>{{t('status')}}</th><th>{{t('manage')}}</th></tr></thead><tbody><tr v-for="p in items" :key="p.id"><td>{{p.name}}</td><td>{{p.attendanceMode?w(p.attendanceMode):w('unchanged')}}</td><td><span class="wf-badge">{{j(p.enabled?'enabled':'disabled')}}</span></td><td><div class="wf-inline"><button :disabled="busy" @click="edit(p)">{{j('edit')}}</button><button :disabled="busy" @click="toggle(p)">{{j(p.enabled?'disable':'enable')}}</button><button :disabled="busy" @click="deleting=p.id">{{j('remove')}}</button></div><div v-if="deleting===p.id" class="wf-inline"><span>{{j('removeHint')}}</span><button :disabled="busy" @click="remove(p)">{{j('confirmRemove')}}</button><button :disabled="busy" @click="deleting=''">{{j('cancel')}}</button></div><PositionSalaryDefaults :position-id="p.id"/></td></tr><tr v-if="!items.length"><td colspan="4" class="wf-empty">{{j('empty')}}</td></tr></tbody></table></div></template>
</section><p v-else role="alert">{{t('noAccess')}}</p></template>
