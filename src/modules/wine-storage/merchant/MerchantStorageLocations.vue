<script setup lang="ts">
import {ref,onUnmounted} from 'vue'
import type {StorageLocation} from '../types'
import type {StorageApi} from '../api'
import {useMerchantStorageText} from './text'
const props=defineProps<{api:StorageApi;disabled:boolean}>(),emit=defineEmits<{error:[unknown];'busy-change':[boolean]}>(),{t}=useMerchantStorageText()
const places=ref<StorageLocation[]>([]),loaded=ref(false),loading=ref(false),saving=ref(false),draft=ref<{id:string|null;name:string;version:number|null}>({id:null,name:'',version:null})
let generation=0,active=true
async function load(){const g=++generation;loading.value=true;try{const result=await props.api.get<StorageLocation[]>('/locations');if(active&&g===generation){places.value=result;loaded.value=true}}catch(e){if(active&&g===generation)emit('error',e)}finally{if(active&&g===generation)loading.value=false}}
function reset(){draft.value={id:null,name:'',version:null}}
async function save(){if(props.disabled||saving.value||!draft.value.name.trim())return;saving.value=true;emit('busy-change',true);try{const{id,...body}=draft.value;await props.api.command(id?`/locations/${id}`:'/locations',body);if(active){reset();await load()}}catch(e){if(active)emit('error',e)}finally{if(active){saving.value=false;emit('busy-change',false)}}}
onUnmounted(()=>{active=false;generation++;emit('busy-change',false)})
</script>
<template><details class="storage-panel" @toggle="($event.target as HTMLDetailsElement).open&&!loaded&&load()"><summary>{{t('locationManagement')}}</summary><p class="cards-muted">{{t('locationManagementHint')}}</p><button :disabled="disabled||saving||loading" @click="load">{{t('refresh')}}</button><p v-if="loading" role="status">{{t('loading')}}</p><article v-for="place in places" :key="place.id" class="storage-panel"><strong>{{place.name}}</strong><span v-if="place.defaultLocation" class="cards-badge">{{t('defaultLocation')}}</span><button :disabled="disabled||saving" @click="draft={id:place.id,name:place.name,version:place.version}">{{t('editLocation')}}</button></article><form class="storage-location-editor" @submit.prevent="save"><fieldset :disabled="disabled||saving||loading"><h3>{{t(draft.id?'editLocation':'addLocation')}}</h3><label>{{t('locationName')}}<input v-model="draft.name" required maxlength="100"></label><div class="storage-inline"><button class="primary" :disabled="disabled||saving||!draft.name.trim()">{{t('saveLocation')}}</button><button v-if="draft.id" type="button" @click="reset">{{t('cancelEdit')}}</button></div></fieldset></form></details></template>
