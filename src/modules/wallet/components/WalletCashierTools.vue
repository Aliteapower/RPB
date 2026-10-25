<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import {useStaffWallet} from '../useStaffWallet'
import {staffWalletPath} from '../navigation'
import WalletIcon from './WalletIcon.vue'
import WalletCustomerManagementEntry from './WalletCustomerManagementEntry.vue'
defineProps<{disabled?:boolean}>()
const {t}=useI18n(),{store,context,can}=useStaffWallet()
</script>
<template><div class="wallet-shortcuts" :inert="disabled" :aria-disabled="disabled||undefined">
  <WalletCustomerManagementEntry shortcut />
  <RouterLink v-if="(can('wallet.charge.create')&&can('wallet.view'))||(can('wallet.points.view')&&can('wallet.points.redeem'))" :to="staffWalletPath(store,'merchant-code')"><span class="wallet-shortcut-icon"><WalletIcon name="qr" /></span><strong>{{t('wallet.merchantQr.title')}}</strong><small>{{t('wallet.merchantQr.hint')}}</small></RouterLink>
  <RouterLink v-if="(context?.transactionsEnabled && context.program && can('wallet.topup.create')) || can('wallet.points.view')" :to="staffWalletPath(store,'topup')+'?entry=phone'"><span class="wallet-shortcut-icon"><WalletIcon name="plus" /></span><strong>{{ t('wallet.marketing.phoneEntry') }}</strong><small>{{ t('wallet.marketing.phoneHint') }}</small></RouterLink>
  <RouterLink :to="staffWalletPath(store,'transactions')"><span class="wallet-shortcut-icon"><WalletIcon name="bill" /></span><strong>{{ t('wallet.transactions.title') }}</strong><small>{{ t('wallet.ui.recordsHint') }}</small></RouterLink>
  <RouterLink :to="staffWalletPath(store,'transactions')+'?type=topup&status=pending'"><span class="wallet-shortcut-icon"><WalletIcon name="history" /></span><strong>{{ t('wallet.ui.pendingTopups') }}</strong><small>{{ t('wallet.ui.pendingTopupsHint') }}</small></RouterLink>
  <RouterLink :to="staffWalletPath(store,'more')"><span class="wallet-shortcut-icon"><WalletIcon name="settings" /></span><strong>{{ t('wallet.ui.more') }}</strong><small>{{ t('wallet.ui.managementHint') }}</small></RouterLink>
</div></template>
<style scoped>[inert]{opacity:.55}</style>
