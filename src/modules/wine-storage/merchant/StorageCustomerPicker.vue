<script setup lang="ts">
import type {StorageCustomer} from './scope'
import {useMerchantStorageText} from './text'
defineProps<{customer:StorageCustomer|null;results:StorageCustomer[];query:string;busy:boolean;searching:boolean;searched:boolean}>()
const emit=defineEmits<{'update:query':[string];search:[];choose:[StorageCustomer|null]}>(),{t}=useMerchantStorageText()
</script>
<template><section class="storage-panel">
 <template v-if="customer"><div class="storage-inline"><div><small>{{t('selected')}}</small><h2>{{customer.displayName}}</h2><span>{{customer.phone}}</span></div><button :disabled="busy" @click="emit('choose',null)">{{t('change')}}</button></div></template>
 <template v-else><h2>{{t('identify')}}</h2><form class="storage-inline storage-merchant-customer-search" @submit.prevent="emit('search')"><label>{{t('searchCustomer')}}<input :value="query" :disabled="busy||searching" @input="emit('update:query',($event.target as HTMLInputElement).value)" maxlength="120" autocomplete="off"></label><button class="primary" :disabled="busy||searching||!query.trim()">{{t(searching?'loading':'search')}}</button></form><p v-if="searched&&!searching&&!results.length" role="status">{{t('noMatches')}}</p><div class="storage-product-options"><button v-for="entry in results" :key="entry.id" :disabled="busy" @click="emit('choose',entry)"><strong>{{entry.displayName}}</strong><small>{{entry.phone}}</small><span>{{t('choose')}}</span></button></div></template>
</section></template>
