<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import StoreSwitcher from '../../components/store/StoreSwitcher.vue'
import { provideStaffWallet } from './useStaffWallet'
import { staffWalletPath } from './navigation'
import WalletBottomNav from './components/WalletBottomNav.vue'
import WalletIcon from './components/WalletIcon.vue'
import WalletLocaleControl from './components/WalletLocaleControl.vue'
import './wallet.css'
const route=useRoute(),{t}=useI18n(),{store,context,pointsContext,busy,error,run,refresh,can}=provideStaffWallet()
const pointsOnly=computed(()=>can('wallet.points.view')&&!can('wallet.view'))
const viewRevision=ref(0)
function refreshView(){viewRevision.value++;void run(refresh)}
const atHome=computed(()=>route.name==='wallet-workbench')
const backPath=computed(()=>{
  if(route.name==='wallet-staff-topup')return staffWalletPath(store.value,'bills')+'?kind=topup'
  if(route.name==='wallet-staff-bill')return staffWalletPath(store.value,'bills')
  if(route.name==='wallet-refund-detail')return staffWalletPath(store.value,'refunds')
  if(['wallet-settings','wallet-report','wallet-exceptions','wallet-staff-history','wallet-refunds','wallet-refund-create'].includes(String(route.name)))return staffWalletPath(store.value,'more')
  return staffWalletPath(store.value)
})
</script>
<template><main class="wallet-shell wallet-staff">
  <header class="wallet-header"><RouterLink v-if="!atHome&&!pointsOnly" class="wallet-icon-button" :to="backPath" :aria-label="t('wallet.ui.back')"><WalletIcon name="back" /></RouterLink><img v-else-if="context?.store.logoMediaAssetId" class="wallet-logo" :src="`/api/v1/stores/${store}/wallet/logo/${context.store.logoMediaAssetId}`" alt=""><div class="wallet-header-title"><h1>{{ t('wallet.ui.staffWallet') }}</h1><small>{{ context?.store.storeName }} · {{ context?.store.operatingEntityName }}</small></div><button class="wallet-icon-button" :disabled="busy" :aria-label="t('wallet.refresh')" @click="refreshView"><WalletIcon name="refresh" /></button></header>
  <div class="wallet-utility-bar"><template v-if="atHome&&!pointsOnly"><RouterLink :to="`/stores/${store}/staff`">{{ t('nav.staff.home') }}</RouterLink><StoreSwitcher surface="staff" /></template><WalletLocaleControl /></div>
  <p v-if="error" class="wallet-error" role="alert">{{ error }}</p><p v-if="busy && !context" class="wallet-empty" role="status">{{ t('wallet.loading') }}</p>
  <template v-if="context"><p v-if="!context.transactionsEnabled" class="wallet-warning" role="status">{{ pointsContext?t('wallet.marketing.unavailable'):context.program?t('wallet.expired'):t('wallet.noProgram') }}</p><RouterView :key="`${store}:${viewRevision}`" /></template>
  <WalletBottomNav v-if="!pointsOnly" :store="store" />
</main></template>
