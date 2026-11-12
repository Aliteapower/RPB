<script setup lang="ts">
import {ref,watch,onUnmounted} from 'vue'
import type {Home,PendingApplication} from '../types'
import type {StorageApi} from '../api'
import StorageApplicationEvidence from '../StorageApplicationEvidence.vue'
import StorageReceiveForm from '../StorageReceiveForm.vue'
import {displayName} from '../../shared-commerce/format'
import {dateTime} from '../format'
import {useMerchantStorageText} from './text'
const props=defineProps<{api:StorageApi;home:Home;disabled:boolean}>(),emit=defineEmits<{changed:[string];error:[unknown];'busy-change':[boolean];'reviewing-change':[boolean]}>(),{t,locale}=useMerchantStorageText()
const rows=ref<PendingApplication[]>([]),selected=ref<PendingApplication|null>(null),loading=ref(false),working=ref(false),rejecting=ref(false),reason=ref('')
let generation=0
async function load(){const g=++generation;loading.value=true;try{const values=await props.api.get<PendingApplication[]>('/pending-applications');if(g===generation)rows.value=values}catch(e){if(g===generation)emit('error',e)}finally{if(g===generation)loading.value=false}}
watch(()=>props.home,load,{immediate:true});watch(selected,value=>emit('reviewing-change',!!value),{flush:'sync'});watch(working,value=>emit('busy-change',value),{flush:'sync'})
onUnmounted(()=>{generation++;emit('busy-change',false);emit('reviewing-change',false)})
function close(){if(working.value)return;selected.value=null;rejecting.value=false;reason.value=''}
function saved(){selected.value=null;rejecting.value=false;reason.value='';emit('changed',t('received'));void load()}
async function reject(){if(props.disabled||working.value||!selected.value||!reason.value.trim())return;working.value=true;try{await props.api.command(`/applications/${selected.value.application.id}/reject`,{reason:reason.value});selected.value=null;rejecting.value=false;reason.value='';emit('changed',t('rejected'));await load()}catch(e){emit('error',e)}finally{working.value=false}}
</script>
<template><section class="storage-section storage-inbox">
 <template v-if="selected"><button :disabled="disabled||working" @click="close">← {{t('backInbox')}}</button><section class="storage-panel"><h2>{{t('reviewRequest')}}</h2><strong>{{selected.customer.displayName}}</strong><p>{{selected.customer.phone}}</p><small>{{dateTime(selected.application.createdAt,home.timezone)}}</small><StorageApplicationEvidence :application="selected.application" :api="api"/></section>
 <StorageReceiveForm :key="selected.application.id" :application="selected.application" :api="api" :home="home" :disabled="disabled||!home.newBusinessEnabled||rejecting" @saved="saved" @error="emit('error',$event)" @busy-change="working=$event"/>
 <section class="storage-panel"><button :disabled="disabled||working" @click="rejecting=!rejecting">{{t('reject')}}</button><form v-if="rejecting" @submit.prevent="reject"><label>{{t('rejectReason')}}<textarea v-model="reason" required maxlength="2000" :disabled="disabled||working"/></label><button :disabled="disabled||working||!reason.trim()">{{t('confirmReject')}}</button></form></section></template>
 <section v-else class="storage-panel"><div class="storage-inline"><h2>{{t('inbox')}} <span class="cards-badge">{{home.applications.filter(a=>a.status==='pending').length}}</span></h2><button :disabled="disabled||loading" @click="load">{{t('refresh')}}</button></div><p class="cards-muted">{{t('inboxHint')}}</p><p v-if="loading" role="status">{{t('loading')}}</p><p v-else-if="!rows.length" class="storage-empty">{{t('noInbox')}}</p>
 <article v-for="row in rows" :key="row.application.id" class="storage-panel"><strong>{{row.customer.displayName}}</strong><p>{{row.customer.phone}}</p><small>{{dateTime(row.application.createdAt,home.timezone)}}</small><p v-for="line in row.application.lines" :key="line.id"><strong>{{displayName(line,locale)}}</strong> × {{line.quantity}} {{t('unit')}} · {{t(line.opened?'opened':'whole')}}</p><StorageApplicationEvidence :application="row.application" :api="api"/><button class="primary" :disabled="disabled||loading" @click="selected=row">{{t('reviewRequest')}}</button></article></section>
</section></template>
