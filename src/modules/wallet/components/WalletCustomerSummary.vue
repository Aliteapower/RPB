<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { walletApi } from '../api'
import type { Account, Customer } from '../types'
const props = defineProps<{store:string;accountId:string}>(), {t} = useI18n()
const customer = ref<Customer | null>(null), error = ref(''); let generation = 0
watch(()=>[props.store,props.accountId],async()=>{
  const current=++generation;customer.value=null;error.value=''
  try {const a=await walletApi(props.store).get<Account>(`/accounts/${props.accountId}`);const c=await walletApi(props.store).get<Customer>(`/customers/${a.customerId}`);if(current===generation)customer.value=c}
  catch(e){if(current===generation)error.value=e instanceof Error?e.message:String(e)}
},{immediate:true})
onUnmounted(()=>{generation++})
</script>
<template><section><p v-if="customer">{{ t('wallet.customer') }}：{{ customer.displayName || customer.customerCode }} · {{ customer.email || customer.phone }}</p><p v-else-if="error" class="wallet-error" role="alert">{{ error }}</p><p v-else>{{ t('wallet.loading') }}</p><slot v-if="customer" /></section></template>
