<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useCustomerWallet } from '../useCustomerWallet'
import { customerWalletPath } from '../navigation'
import { money } from '../format'
import CustomerPasswordChange from '../../../components/customer-auth/CustomerPasswordChange.vue'
import WalletIcon from '../components/WalletIcon.vue'
const {t}=useI18n(),{store,home,account}=useCustomerWallet()
</script>
<template><template v-if="home"><section class="wallet-balance-hero"><span>{{ t('wallet.available') }} (SGD)</span><h2>{{ money(account ? account.balanceCents-account.reservedCents : 0) }}</h2><div class="wallet-balance-foot"><span>{{ t('wallet.balance') }} {{ money(account?.balanceCents??0) }}</span><span>{{ t('wallet.reserved') }} {{ money(account?.reservedCents??0) }}</span></div>
  <div v-if="account && home.context.transactionsEnabled" class="wallet-hero-actions wallet-code-actions"><RouterLink :to="customerWalletPath(store,'code')+'?purpose=topup'"><WalletIcon name="plus" :size="32" /><strong>{{ t('wallet.topupQr.show') }}</strong></RouterLink><RouterLink :to="customerWalletPath(store,'code')"><WalletIcon name="qr" :size="32" /><strong>{{ t('wallet.showMyCode') }}</strong></RouterLink><RouterLink :to="customerWalletPath(store,'scan')"><WalletIcon name="scan" :size="32" /><strong>{{ t('wallet.ui.scanPay') }}</strong></RouterLink></div>
</section>
<RouterLink v-if="home.pendingBills.length" class="wallet-pending-banner" :to="customerWalletPath(store,'bills')+'?status=pending'"><WalletIcon name="bill" /><span>{{ t('wallet.ui.pendingNotice',{count:home.pendingBills.length}) }}</span><WalletIcon name="arrow" :size="18" /></RouterLink>
<div class="wallet-shortcuts"><RouterLink :to="customerWalletPath(store,'bills')"><span class="wallet-shortcut-icon"><WalletIcon name="bill" /></span><strong>{{ t('wallet.bills') }}</strong><small>{{ t('wallet.ui.customerBillsHint') }}</small></RouterLink><RouterLink :to="customerWalletPath(store,'history')"><span class="wallet-shortcut-icon"><WalletIcon name="history" /></span><strong>{{ t('wallet.ui.entries') }}</strong><small>{{ t('wallet.ui.entriesHint') }}</small></RouterLink></div>
<section class="wallet-brand-note"><span>{{ t('wallet.issuer') }}</span><strong>{{ home.context.store.operatingEntityName }}</strong><p>{{ account ? t('wallet.ui.offlineTopupHint') : t('wallet.ui.customerNoAccount') }}</p><details><summary>{{ t('wallet.publicProfile') }}</summary><p>{{ home.context.store.storeName }}</p><p>{{ home.context.store.contactPhone }}</p><p>{{ home.context.store.address }}</p></details></section>
<CustomerPasswordChange :key="store" /></template></template>
