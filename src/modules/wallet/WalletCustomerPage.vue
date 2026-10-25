<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { provideCustomerWallet } from './useCustomerWallet'
import { customerWalletPath } from './navigation'
import WalletCustomerLogin from './components/WalletCustomerLogin.vue'
import WalletCustomerAccountMenu from './components/WalletCustomerAccountMenu.vue'
import WalletBottomNav from './components/WalletBottomNav.vue'
import WalletIcon from './components/WalletIcon.vue'
import WalletLocaleControl from './components/WalletLocaleControl.vue'
import {safeTransactionReturn} from './transactions/query'
import WalletTransactionDetail from './pages/WalletTransactionDetail.vue'
import './wallet.css'
const route=useRoute(),{t}=useI18n(),{store,home,needsLogin,error,loading,refresh,authenticated,logout,loggingOut}=provideCustomerWallet()
const viewRevision=ref(0)
function refreshView(){viewRevision.value++;void refresh()}
const queryPage=computed(()=>String(route.name).includes('transaction'))
const registrationPage=computed(()=>route.name==='wallet-customer-register')
const nativeDetail=computed(()=>['wallet-staff-bill','wallet-staff-topup','wallet-refund-detail','wallet-customer-bill'].includes(String(route.name)))
const historical=computed(()=>queryPage.value||nativeDetail.value)
const atHome=computed(()=>route.name==='wallet-customer')
const backPath=computed(()=>['wallet-customer-bill','wallet-customer-transaction'].includes(String(route.name))?safeTransactionReturn(route.query.returnTo,store.value,true):customerWalletPath(store.value))
</script>
<template><main class="wallet-shell wallet-customer">
  <header class="wallet-header"><RouterLink v-if="!atHome" class="wallet-icon-button" :to="backPath" :aria-label="t('wallet.ui.back')"><WalletIcon name="back" /></RouterLink><img v-else-if="home?.context.store.logoMediaAssetId" class="wallet-logo" :src="`/api/v1/public/stores/${store}/wallet/logo/${home.context.store.logoMediaAssetId}`" alt=""><WalletCustomerAccountMenu v-if="home && atHome" :key="store" :store="store" :store-name="home.context.store.storeName" :logging-out="loggingOut" :error="error" @logout="logout" /><div v-else class="wallet-header-title"><h1>{{ t('wallet.customerWallet') }}</h1><small>{{ home?.context.store.storeName || t('wallet.ui.secureWallet') }}</small></div><button class="wallet-icon-button" :disabled="loading" :aria-label="t('wallet.refresh')" @click="refreshView"><WalletIcon name="refresh" /></button></header>
  <div class="wallet-utility-bar"><WalletLocaleControl /></div>
  <p v-if="error&&!historical" class="wallet-error" role="alert">{{ error }}</p><p v-if="loading && !home && !needsLogin&&!historical" class="wallet-empty" role="status">{{ t('wallet.loading') }}</p>
  <WalletCustomerLogin v-if="needsLogin&&!registrationPage" :key="store" :store="store" @authenticated="authenticated" />
  <template v-if="registrationPage"><RouterView :key="store" /><WalletBottomNav :store="store" customer /></template><template v-else-if="historical&&!needsLogin"><RouterView v-if="queryPage||home" :key="`${store}:${viewRevision}`" /><WalletTransactionDetail v-else :key="`${store}:${String(route.params.billId)}`" /><WalletBottomNav :store="store" customer /></template><template v-else-if="home"><p v-if="!home.context.transactionsEnabled" class="wallet-warning" role="status">{{ t('wallet.expired') }}</p><RouterView :key="`${store}:${viewRevision}`" /><WalletBottomNav :store="store" customer /></template>
</main></template>
