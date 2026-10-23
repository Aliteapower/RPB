<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useStaffWallet } from '../useStaffWallet'
import { staffWalletPath } from '../navigation'
import WalletIcon from '../components/WalletIcon.vue'
const { t } = useI18n(), { store, context, can } = useStaffWallet()
</script>
<template><section v-if="context?.transactionsEnabled && context.program && can('wallet.charge.create')" class="wallet-home-hero"><p class="wallet-eyebrow">{{ t('wallet.ui.cashier') }}</p><h2>{{ t('wallet.ui.cashierTitle') }}</h2><p>{{ t('wallet.ui.cashierHint') }}</p>
  <div class="wallet-hero-actions">
    <RouterLink v-if="can('wallet.charge.create')" :to="staffWalletPath(store,'collect')+'?scan=1'"><WalletIcon name="scan" :size="32" /><strong>{{ t('wallet.scanCustomer') }}</strong><small>{{ t('wallet.ui.scanStart') }}</small></RouterLink>
    <RouterLink v-if="can('wallet.charge.create')" :to="staffWalletPath(store,'collect')"><WalletIcon name="user" :size="32" /><strong>{{ t('wallet.ui.customerCollect') }}</strong><small>{{ t('wallet.ui.selectStart') }}</small></RouterLink>
  </div>
</section>
<div class="wallet-shortcuts">
  <RouterLink v-if="context?.transactionsEnabled && context.program && can('wallet.topup.create')" :to="staffWalletPath(store,'topup')+'?scan=1'"><span class="wallet-shortcut-icon"><WalletIcon name="plus" /></span><strong>{{ t('wallet.topupQr.entry') }}</strong><small>{{ t('wallet.topupQr.entryHint') }}</small></RouterLink>
  <RouterLink :to="staffWalletPath(store,'bills')"><span class="wallet-shortcut-icon"><WalletIcon name="bill" /></span><strong>{{ t('wallet.ui.collectRecords') }}</strong><small>{{ t('wallet.ui.recordsHint') }}</small></RouterLink>
  <RouterLink :to="staffWalletPath(store,'topups')"><span class="wallet-shortcut-icon"><WalletIcon name="history" /></span><strong>{{ t('wallet.ui.pendingTopups') }}</strong><small>{{ t('wallet.ui.pendingTopupsHint') }}</small></RouterLink>
  <RouterLink :to="staffWalletPath(store,'more')"><span class="wallet-shortcut-icon"><WalletIcon name="settings" /></span><strong>{{ t('wallet.ui.more') }}</strong><small>{{ t('wallet.ui.managementHint') }}</small></RouterLink>
</div></template>
