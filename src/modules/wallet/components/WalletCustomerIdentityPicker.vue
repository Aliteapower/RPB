<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { useStaffWallet } from '../useStaffWallet'
import WalletQrScanner from './WalletQrScanner.vue'
import WalletIcon from './WalletIcon.vue'
defineProps<{ disabled?: boolean }>()
const emit=defineEmits<{clear:[];identified:[]}>()
const {t}=useI18n(),{customer,identifiedCustomerId,busy,error,clear,identifyCustomer}=useStaffWallet(),scanning=ref(useRoute().query.scan==='1')
function reset(){clear();error.value='';scanning.value=true;emit('clear')}
async function scanned(code:string){if(await identifyCustomer(code)){scanning.value=false;emit('identified')}}
</script>
<template>
  <section v-if="customer&&customer.id===identifiedCustomerId" class="wallet-customer-chip">
    <div class="wallet-avatar"><WalletIcon name="user" /></div><div><strong>{{customer.displayName||customer.customerCode}}</strong><small>{{customer.phone||customer.customerCode}}</small><small>{{t('wallet.marketing.identityReady')}}</small></div>
    <button :disabled="busy||disabled" @click="reset">{{t('wallet.personal.nextCustomer')}}</button>
  </section>
  <section v-else class="wallet-card">
    <p>{{t('wallet.marketing.scanFirst')}}</p>
    <button class="wallet-primary wallet-block" :disabled="busy||disabled" @click="scanning=!scanning"><WalletIcon name="scan" />{{t('wallet.marketing.scanPersonal')}}</button>
    <WalletQrScanner v-if="scanning" purpose="identity" auto-start :disabled="busy||disabled" @code="scanned" />
    <p v-if="error" class="wallet-error" role="alert">{{error}}</p>
  </section>
</template>
