<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { provideCustomerWallet } from './useCustomerWallet'
import { customerWalletPath } from './navigation'
import WalletCustomerLogin from './components/WalletCustomerLogin.vue'
import WalletBottomNav from './components/WalletBottomNav.vue'
import WalletIcon from './components/WalletIcon.vue'
import WalletLocaleControl from './components/WalletLocaleControl.vue'
import './wallet.css'
const route=useRoute(),{t}=useI18n(),{store,home,needsLogin,error,loading,refresh,authenticated}=provideCustomerWallet()
const viewRevision=ref(0)
function refreshView(){viewRevision.value++;void refresh()}
const atHome=computed(()=>route.name==='wallet-customer')
const backPath=computed(()=>route.name==='wallet-customer-bill'?customerWalletPath(store.value,'bills'):customerWalletPath(store.value))
</script>
<template><main class="wallet-shell wallet-customer">
  <header class="wallet-header"><RouterLink v-if="!atHome" class="wallet-icon-button" :to="backPath" :aria-label="t('wallet.ui.back')"><WalletIcon name="back" /></RouterLink><img v-else-if="home?.context.store.logoMediaAssetId" class="wallet-logo" :src="`/api/v1/public/stores/${store}/wallet/logo/${home.context.store.logoMediaAssetId}`" alt=""><div class="wallet-header-title"><h1>{{ t('wallet.customerWallet') }}</h1><small>{{ home?.context.store.storeName || t('wallet.ui.secureWallet') }}</small></div><button class="wallet-icon-button" :disabled="loading" :aria-label="t('wallet.refresh')" @click="refreshView"><WalletIcon name="refresh" /></button></header>
  <div class="wallet-utility-bar"><WalletLocaleControl /></div>
  <p v-if="error" class="wallet-error" role="alert">{{ error }}</p><p v-if="loading && !home && !needsLogin" class="wallet-empty" role="status">{{ t('wallet.loading') }}</p>
  <WalletCustomerLogin v-if="needsLogin" :key="store" :store="store" @authenticated="authenticated" />
  <template v-if="home"><p v-if="!home.context.transactionsEnabled" class="wallet-warning" role="status">{{ t('wallet.expired') }}</p><RouterView :key="`${store}:${viewRevision}`" /><WalletBottomNav :store="store" customer /></template>
</main></template>
