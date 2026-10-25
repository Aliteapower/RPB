<script setup lang="ts">
import {ref,onUnmounted} from 'vue'
import type {Custodian} from '../types'
import type {StorageApi} from '../api'
import {useMerchantStorageText} from './text'
const props=defineProps<{api:StorageApi;disabled:boolean}>(),emit=defineEmits<{error:[unknown];'busy-change':[boolean]}>(),{t}=useMerchantStorageText(),people=ref<Custodian[]>([]),loading=ref(false),saving=ref(false),loaded=ref(false)
const draft=ref<{id:string|null;name:string;kind:string;enabled:boolean;version:number|null}>({id:null,name:'',kind:'hostess',enabled:true,version:null})
let generation=0
async function load(){const g=++generation;loading.value=true;try{const values=await props.api.get<Custodian[]>('/custodians?includeDisabled=true');if(g===generation){people.value=values;loaded.value=true}}catch(e){if(g===generation)emit('error',e)}finally{if(g===generation)loading.value=false}}
function reset(){draft.value={id:null,name:'',kind:'hostess',enabled:true,version:null}}
async function save(){if(props.disabled||saving.value||!draft.value.name.trim())return;saving.value=true;emit('busy-change',true);try{const{id,...body}=draft.value;await props.api.command(id?`/custodians/${id}`:'/custodians',body);reset();await load()}catch(e){emit('error',e)}finally{saving.value=false;emit('busy-change',false)}}
onUnmounted(()=>{generation++;emit('busy-change',false)})
</script>
<template><details class="storage-panel" @toggle="($event.target as HTMLDetailsElement).open&&!loaded&&load()"><summary>{{t('custodianManagement')}}</summary><p class="cards-muted">{{t('custodianManagementHint')}}</p><button :disabled="disabled||saving||loading" @click="load">{{t('refresh')}}</button><p v-if="loading" role="status">{{t('loading')}}</p><p v-if="loaded&&!people.length">{{t('noPeople')}}</p><article v-for="person in people" :key="person.id" class="storage-panel"><strong>{{person.name}}</strong> · {{t(person.kind)}} · {{t(person.enabled?'enabledPerson':'disabledPerson')}} <button :disabled="disabled||saving" @click="draft={...person}">{{t('editPerson')}}</button></article>
 <form @submit.prevent="save"><fieldset :disabled="disabled||saving||loading"><h3>{{t(draft.id?'editPerson':'addPerson')}}</h3><label>{{t('personName')}}<input v-model="draft.name" required maxlength="100"></label><label>{{t('personKind')}}<select v-model="draft.kind"><option value="hostess">{{t('hostess')}}</option><option value="custodian">{{t('custodian')}}</option></select></label><label><input v-model="draft.enabled" type="checkbox">{{t('enabledPerson')}}</label><div class="storage-inline"><button class="primary" :disabled="disabled||saving||!draft.name.trim()">{{t('savePerson')}}</button><button v-if="draft.id" type="button" @click="reset">{{t('cancelEdit')}}</button></div></fieldset></form></details></template>
