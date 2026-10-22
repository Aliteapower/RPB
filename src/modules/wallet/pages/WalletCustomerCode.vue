<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useCustomerWallet } from '../useCustomerWallet'
import { walletApi } from '../api'
import { customerWalletPath } from '../navigation'
import { money } from '../format'
import { useWalletTask } from '../useWalletTask'
import type { PaymentCode } from '../types'
import WalletQrDisplay from '../components/WalletQrDisplay.vue'
import { useWalletCodeReceipt } from '../useWalletCodeReceipt'
const {t}=useI18n(),router=useRouter(),{store,account,home}=useCustomerWallet(),{busy,error,run}=useWalletTask()
const code=ref<PaymentCode|null>(null);let generation=0
watch(()=>account.value?.id,()=>{generation++;code.value=null},{immediate:true})
async function issue(){const id=account.value?.id,current=generation;if(!id)return;await run(async()=>{const result=await walletApi(store.value,true).post<PaymentCode>('/payment-codes',{accountId:id,purpose:'merchant_charge'});if(current===generation)code.value=result})}
const receipt=useWalletCodeReceipt(store,code,bill=>router.replace(customerWalletPath(bill.storeId,`bills/${bill.id}`)))
onUnmounted(()=>{generation++})
</script>
<template><section class="wallet-page-heading"><h2>{{ t('wallet.showMyCode') }}</h2><p>{{ t('wallet.scanCharge.customerConsent') }}</p></section><p v-if="error || receipt.error.value" class="wallet-error" role="alert">{{ error || receipt.error.value }}</p>
  <section v-if="account && home?.context.transactionsEnabled" class="wallet-code-card"><p>{{ home.context.store.operatingEntityName }}</p><strong>{{ t('wallet.available') }} {{ money(account.balanceCents-account.reservedCents) }}</strong><WalletQrDisplay v-if="code" :value="code.code" :expires-at="code.expiresAt" /><div v-else class="wallet-qr-placeholder">{{ t('wallet.ui.codePrivate') }}</div><button class="wallet-primary wallet-block" :disabled="busy" @click="issue">{{ t(code?'wallet.regenerate':'wallet.showMyCode') }}</button><small>{{ t('wallet.ui.codePrivate') }}</small></section>
  <p v-else class="wallet-empty">{{ t('wallet.expired') }}</p>
</template>
