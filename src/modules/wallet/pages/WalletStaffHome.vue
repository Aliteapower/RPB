<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useStaffWallet } from '../useStaffWallet'
import { staffWalletPath } from '../navigation'
import WalletIcon from '../components/WalletIcon.vue'
const { t } = useI18n(), { store, context, can } = useStaffWallet()
</script>
<template><section v-if="context?.transactionsEnabled && context.program && can('wallet.charge.create') && can('wallet.collection.confirm')" class="wallet-home-hero"><p class="wallet-eyebrow">{{ t('wallet.ui.cashier') }}</p><h2>{{ t('wallet.ui.cashierTitle') }}</h2><p>{{ t('wallet.ui.cashierHint') }}</p>
  <div class="wallet-hero-actions wallet-single-action">
    <RouterLink v-if="can('wallet.charge.create') && can('wallet.collection.confirm')" :to="staffWalletPath(store,'collect')"><WalletIcon name="scan" :size="32" /><strong>{{ t('wallet.personal.collectEntry') }}</strong><small>{{ t('wallet.personal.collectHint') }}</small></RouterLink>
  </div>
</section>
<div class="wallet-shortcuts">
  <RouterLink v-if="(context?.transactionsEnabled && context.program && can('wallet.topup.create')) || can('wallet.points.view')" :to="staffWalletPath(store,'topup')"><span class="wallet-shortcut-icon"><WalletIcon name="plus" /></span><strong>{{ t('wallet.marketing.rechargeEntry') }}</strong><small>{{ t('wallet.marketing.rechargeHint') }}</small></RouterLink>
  <RouterLink :to="staffWalletPath(store,'transactions')"><span class="wallet-shortcut-icon"><WalletIcon name="bill" /></span><strong>{{ t('wallet.transactions.title') }}</strong><small>{{ t('wallet.ui.recordsHint') }}</small></RouterLink>
  <RouterLink :to="staffWalletPath(store,'transactions')+'?type=topup&status=pending'"><span class="wallet-shortcut-icon"><WalletIcon name="history" /></span><strong>{{ t('wallet.ui.pendingTopups') }}</strong><small>{{ t('wallet.ui.pendingTopupsHint') }}</small></RouterLink>
  <RouterLink :to="staffWalletPath(store,'more')"><span class="wallet-shortcut-icon"><WalletIcon name="settings" /></span><strong>{{ t('wallet.ui.more') }}</strong><small>{{ t('wallet.ui.managementHint') }}</small></RouterLink>
</div></template>
