<script setup lang="ts">
import {ref,watch} from 'vue'
import {storageApi} from './api'
import type {Home} from './types'
import {useStorageText} from './text'
const props=defineProps<{store:string;customer?:boolean}>(),{t}=useStorageText(),summary=ref<Home|null>(null);let generation=0
watch(()=>props.store,async store=>{const g=++generation;summary.value=null;try{const data=await storageApi(store,!!props.customer).get<Home>('/home');if(g===generation)summary.value=data}catch{/* Wallet entry is independent of this product's authorization. */}},{immediate:true})
</script>
<template><RouterLink v-if="summary" class="storage-wallet-entry" :to="customer?`/wallet/${store}/wine-storage`:`/stores/${store}/wine-storage`"><strong>{{t(customer?'title':'manage')}}</strong><small>{{t('stored')}} {{summary.items.filter(i=>i.status==='stored').reduce((n,i)=>n+i.remaining,0)}} · {{t('pending')}} {{summary.applications.filter(a=>a.status==='pending').length}}</small></RouterLink></template>
<style scoped>.storage-wallet-entry{display:block;border:1px solid #d0dfd5;border-radius:14px;padding:18px;margin:16px 0;color:inherit;text-decoration:none;background:white}.storage-wallet-entry small{display:block;opacity:.7;margin-top:6px}</style>
