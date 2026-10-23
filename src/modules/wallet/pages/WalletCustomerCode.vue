<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useCustomerWallet } from '../useCustomerWallet'
import { customerWalletPath } from '../navigation'
import { money } from '../format'
import { useCustomerWalletCode } from '../useCustomerWalletCode'
import WalletQrDisplay from '../components/WalletQrDisplay.vue'
import { useWalletCodeReceipt } from '../useWalletCodeReceipt'
const {t}=useI18n(),router=useRouter(),route=useRoute(),{store,account,home}=useCustomerWallet()
const isTopup=computed(()=>route.query.purpose==='topup')
const purpose=computed(() => isTopup.value ? 'topup' as const : 'merchant_charge' as const)
const enabled=computed(()=>home.value?.context.transactionsEnabled===true)
const {code,busy,error,issue}=useCustomerWalletCode(store,account,purpose,enabled)
const receipt=useWalletCodeReceipt(store,code,bill=>router.replace(customerWalletPath(bill.storeId,`bills/${bill.id}`)))
</script>
<template><section class="wallet-page-heading"><h2>{{ t(isTopup?'wallet.topupQr.show':'wallet.showMyCode') }}</h2><p>{{ t(isTopup?'wallet.topupQr.hint':'wallet.scanCharge.customerConsent') }}</p></section>
  <nav class="wallet-code-tabs" :aria-label="t('wallet.topupQr.mode')"><RouterLink :to="customerWalletPath(store,'code')+'?purpose=topup'" :aria-current="isTopup?'page':undefined">{{ t('wallet.topupQr.title') }}</RouterLink><RouterLink :to="customerWalletPath(store,'code')" :aria-current="!isTopup?'page':undefined">{{ t('wallet.showMyCode') }}</RouterLink></nav>
  <p v-if="error || receipt.error.value" class="wallet-error" role="alert">{{ error || receipt.error.value }}</p>
  <section v-if="account && home?.context.transactionsEnabled" class="wallet-code-card"><p>{{ home.context.store.operatingEntityName }}</p><strong>{{ t('wallet.available') }} {{ money(account.balanceCents-account.reservedCents) }}</strong><WalletQrDisplay v-if="code" :value="code.code" :expires-at="code.expiresAt" /><div v-else class="wallet-qr-placeholder">{{ t(isTopup?'wallet.topupQr.hint':'wallet.ui.codePrivate') }}</div><button class="wallet-primary wallet-block" :disabled="busy" @click="issue">{{ t(code?'wallet.regenerate':isTopup?'wallet.topupQr.show':'wallet.showMyCode') }}</button><small>{{ t(isTopup?'wallet.topupQr.private':'wallet.ui.codePrivate') }}</small></section>
  <p v-else class="wallet-empty">{{ t('wallet.expired') }}</p>
</template>
