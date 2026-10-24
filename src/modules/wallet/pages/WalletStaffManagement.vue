<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStaffWallet } from '../useStaffWallet'
import { useI18n } from 'vue-i18n'
import { staffWalletPath } from '../navigation'
import WalletReportPanel from '../components/WalletReportPanel.vue'
import WalletLedgerPanel from '../components/WalletLedgerPanel.vue'
import WalletExceptionsPanel from '../components/WalletExceptionsPanel.vue'
import WalletStaffCustomerPicker from '../components/WalletStaffCustomerPicker.vue'
const {t}=useI18n(),route=useRoute(),router=useRouter(),{store,context,account,can,refresh}=useStaffWallet()
const mode=computed(()=>route.name==='wallet-refund-create'?'refund-create':route.name==='wallet-refund-detail'?'refund-detail':'refund-detail')
</script>
<template><template v-if="context">
  <WalletReportPanel v-if="route.name==='wallet-report' && can('wallet.reconcile.view')" :store="store" />
  <WalletExceptionsPanel v-else-if="route.name==='wallet-exceptions' && can('wallet.refund.manage')" :store="store" :paynow-configured="context.paynowConfigured" />
  <template v-else-if="['wallet-refund-create','wallet-refund-detail'].includes(String(route.name)) && can('wallet.refund.manage')"><WalletStaffCustomerPicker v-if="mode==='refund-create'" :allow-scan="false" /><WalletLedgerPanel :key="String(route.name)+String(route.params.refundId||'')+String(route.query.sourceId||'')" :store="store" :account="account" :timezone="context.store.timezone" :can-refund="can('wallet.refund.manage')" :mode="mode" :refund-id="String(route.params.refundId||'')" @changed="refresh" @created="id=>router.push(staffWalletPath(store,`refunds/${id}`))" /></template>
  <p v-else class="wallet-empty">{{ t('wallet.noRights') }}</p>
</template></template>
