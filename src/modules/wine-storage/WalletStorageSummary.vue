<script setup lang="ts">
import {computed,onUnmounted,ref,watch} from 'vue'
import {useVisiblePageRefresh} from '../../composables/useVisiblePageRefresh'
import {storageApi} from './api'
import type {Home} from './types'
import {useStorageText} from './text'
const props=defineProps<{store:string;customer?:boolean}>(),{t}=useStorageText(),summary=ref<Home|null>(null),loading=ref(false);let generation=0,request=0
async function refresh(){const g=generation,r=++request;loading.value=true;try{const data=await storageApi(props.store,!!props.customer).get<Home>('/home');if(g===generation&&r===request)summary.value=data}catch{if(g===generation&&r===request)summary.value=null}finally{if(g===generation&&r===request)loading.value=false}}
watch(()=>[props.store,props.customer],()=>{generation++;summary.value=null;void refresh()},{immediate:true})
useVisiblePageRefresh(refresh,()=>!loading.value)
onUnmounted(()=>{generation++;request++})
const visible=computed(()=>summary.value&&(summary.value.newBusinessEnabled||[summary.value.items,summary.value.applications,summary.value.intents,summary.value.events].some(rows=>rows.length>0)))
</script>
<template><RouterLink v-if="summary&&visible" class="storage-wallet-entry" :to="customer?`/wallet/${store}/wine-storage${summary.newBusinessEnabled?'':'?view=records'}`:`/stores/${store}/wine-storage${summary.newBusinessEnabled?'':'?task=records&history=1'}`"><strong>{{t(customer?'title':'manage')}}</strong><small>{{t('stored')}} {{summary.items.filter(i=>i.status==='stored').reduce((n,i)=>n+i.remaining,0)}} · {{t('pending')}} {{summary.applications.filter(a=>a.status==='pending').length}}</small></RouterLink></template>
<style scoped>.storage-wallet-entry{display:block;border:1px solid #d0dfd5;border-radius:14px;padding:18px;margin:16px 0;color:inherit;text-decoration:none;background:white}.storage-wallet-entry small{display:block;opacity:.7;margin-top:6px}</style>
