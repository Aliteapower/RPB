<script setup lang="ts">
import { computed,onUnmounted,ref,watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useCustomerWallet } from '../useCustomerWallet'
import { walletApi } from '../api'
import type { PaymentCode } from '../types'
import WalletQrDisplay from './WalletQrDisplay.vue'
const {t}=useI18n(),{store,home}=useCustomerWallet(),code=ref<PaymentCode|null>(null),busy=ref(false),error=ref('')
const enabled=computed(()=>home.value?.context.transactionsEnabled===true)
let generation=0
async function issue(){
 if(!enabled.value||busy.value)return
 const current=generation;busy.value=true;error.value=''
 try{const result=await walletApi(store.value,true).post<PaymentCode>('/customer-codes',{});if(current===generation)code.value=result}
 catch(e){if(current===generation)error.value=e instanceof Error?e.message:String(e)}
 finally{if(current===generation)busy.value=false}
}
watch([store,enabled],()=>{generation++;code.value=null;busy.value=false;error.value='';void issue()},{immediate:true})
onUnmounted(()=>{generation++})
</script>
<template>
  <p v-if="error" class="wallet-error" role="alert">{{error}}</p>
  <section v-if="enabled" class="wallet-code-card">
    <p>{{home?.context.store.operatingEntityName}}</p><WalletQrDisplay v-if="code" :value="code.code" :expires-at="code.expiresAt" /><p v-else role="status">{{t('wallet.loading')}}</p>
    <button class="wallet-primary wallet-block" :disabled="busy" @click="issue">{{t(code?'wallet.regenerate':'wallet.topupQr.show')}}</button><small>{{t('wallet.topupQr.private')}}</small>
  </section>
  <p v-else class="wallet-empty">{{t('wallet.expired')}}</p>
</template>
