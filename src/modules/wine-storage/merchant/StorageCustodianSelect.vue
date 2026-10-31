<script setup lang="ts">
import {ref,watch,onUnmounted} from 'vue'
import type {StorageApi} from '../api'
import type {Custodian} from '../types'
import {useMerchantStorageText} from './text'
const props=defineProps<{api:StorageApi;modelValue:string;disabled:boolean}>(),emit=defineEmits<{'update:modelValue':[string];error:[string]}>(),{t}=useMerchantStorageText(),people=ref<Custodian[]>([]),loading=ref(false)
let generation=0
watch(()=>props.api.base,async()=>{const g=++generation;people.value=[];loading.value=true;try{const result=await props.api.get<Custodian[]>('/custodians');if(g===generation)people.value=result}catch(e){if(g===generation)emit('error',String(e))}finally{if(g===generation)loading.value=false}},{immediate:true})
onUnmounted(()=>generation++)
</script>
<template><label>{{t('custodianOptional')}}<select :value="modelValue" :disabled="disabled||loading" @change="emit('update:modelValue',($event.target as HTMLSelectElement).value)"><option value="">{{t('noCustodian')}}</option><option v-for="person in people" :key="person.id" :value="person.id">{{person.name}} · {{t(person.kind)}}</option></select><small>{{t('custodianHint')}}</small></label></template>
