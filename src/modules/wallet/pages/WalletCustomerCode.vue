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
const {t}=useI18n(),router=useRouter(),{store,account,home}=useCustomerWallet(),{busy,error,run}=useWalletTask()
const code=ref<PaymentCode|null>(null);let generation=0
watch(()=>account.value?.id,()=>{generation++;code.value=null},{immediate:true})
async function issue(){const id=account.value?.id,current=generation;if(!id)return;await run(async()=>{const result=await walletApi(store.value,true).post<PaymentCode>('/payment-codes',{accountId:id});if(current===generation)code.value=result})}
// A bound new bill appears through the shared read poll; bring its exact confirmation into focus.
const initialBills=new Set(home.value?.pendingBills.map(b=>b.id)??[])
watch(()=>home.value?.pendingBills,bills=>{const newest=bills?.find(b=>!initialBills.has(b.id)&&!b.walletApplied);if(newest)void router.replace(customerWalletPath(store.value,`bills/${newest.id}`))})
onUnmounted(()=>{generation++})
</script>
<template><section class="wallet-page-heading"><h2>{{ t('wallet.showMyCode') }}</h2><p>{{ t('wallet.cashierScanned') }}</p></section><p v-if="error" class="wallet-error" role="alert">{{ error }}</p>
  <section v-if="account && home?.context.transactionsEnabled" class="wallet-code-card"><p>{{ home.context.store.operatingEntityName }}</p><strong>{{ t('wallet.available') }} {{ money(account.balanceCents-account.reservedCents) }}</strong><WalletQrDisplay v-if="code" :value="code.code" :expires-at="code.expiresAt" /><div v-else class="wallet-qr-placeholder">{{ t('wallet.ui.codePrivate') }}</div><button class="wallet-primary wallet-block" :disabled="busy" @click="issue">{{ t(code?'wallet.regenerate':'wallet.showMyCode') }}</button><small>{{ t('wallet.ui.codePrivate') }}</small></section>
  <p v-else class="wallet-empty">{{ t('wallet.expired') }}</p>
</template>
