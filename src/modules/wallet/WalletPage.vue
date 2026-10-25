<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import StoreSwitcher from '../../components/store/StoreSwitcher.vue'
import { provideStaffWallet } from './useStaffWallet'
import { staffWalletPath } from './navigation'
import WalletBottomNav from './components/WalletBottomNav.vue'
import WalletIcon from './components/WalletIcon.vue'
import WalletLocaleControl from './components/WalletLocaleControl.vue'
import WalletCashierTabs from './components/WalletCashierTabs.vue'
import WalletCashierTools from './components/WalletCashierTools.vue'
import WalletStaffAccountMenu from './components/WalletStaffAccountMenu.vue'
import {safeTransactionReturn} from './transactions/query'
import WalletTransactionDetail from './pages/WalletTransactionDetail.vue'
import './wallet.css'
import {useAuthSessionStore} from '../../stores/authSession'
const route=useRoute(),{t}=useI18n(),{store,context,pointsContext,busy,error,run,refresh,can}=provideStaffWallet()
const pointsOnly=computed(()=>can('wallet.points.view')&&!can('wallet.view'))
const auth=useAuthSessionStore()
const headerStore=computed(()=>context.value?.store??auth.authorizedStores.find(item=>item.storeId===store.value))
const storeCaption=computed(()=>[headerStore.value?.storeName,headerStore.value?.operatingEntityName].filter(Boolean).join(' · ')||t('staffHome.store.label',{shortId:store.value.slice(0,8)}))
watch(()=>route.name==='wallet-customers',active=>{if(active)void auth.ensureAuthorizedStores()},{immediate:true})
const viewRevision=ref(0),operationBusy=ref(false)
watch(()=>route.fullPath,()=>{operationBusy.value=false},{flush:'sync'})
function refreshView(){if(busy.value||operationBusy.value)return;viewRevision.value++;void run(refresh)}
const queryPage=computed(()=>String(route.name).includes('transaction')||route.name==='wallet-customers')
const nativeDetail=computed(()=>['wallet-staff-bill','wallet-staff-topup','wallet-refund-detail','wallet-customer-bill'].includes(String(route.name)))
const historical=computed(()=>queryPage.value||nativeDetail.value)
const atHome=computed(()=>route.name==='wallet-workbench'||route.name==='wallet-collect'||(route.name==='wallet-topup'&&route.query.entry!=='phone'))
const backPath=computed(()=>{
 if(route.name==='wallet-customers')return route.query.from==='wallet-recharge'?staffWalletPath(store.value,'topup')+'?entry=phone':staffWalletPath(store.value)
 if(route.name==='wallet-points-reverse')return {path:staffWalletPath(store.value,`transactions/points/${route.params.entryId}`),query:{returnTo:safeTransactionReturn(route.query.returnTo??(route.name==='wallet-points-reverse'||route.params.sourceType==='points'?staffWalletPath(store.value,'transactions')+'?category=points':route.params.sourceType==='topup'||route.name==='wallet-staff-topup'?staffWalletPath(store.value,'transactions')+'?category=topup':undefined),store.value)}}
 if(String(route.name).startsWith('wallet-settings-'))return staffWalletPath(store.value,'settings')
 if(['wallet-staff-topup','wallet-staff-bill','wallet-refund-detail','wallet-staff-transaction'].includes(String(route.name)))return safeTransactionReturn(route.query.returnTo??(route.name==='wallet-points-reverse'||route.params.sourceType==='points'?staffWalletPath(store.value,'transactions')+'?category=points':route.params.sourceType==='topup'||route.name==='wallet-staff-topup'?staffWalletPath(store.value,'transactions')+'?category=topup':undefined),store.value)
 if(['wallet-settings','wallet-report','wallet-exceptions','wallet-refund-create'].includes(String(route.name)))return staffWalletPath(store.value,'more')
 return staffWalletPath(store.value)
})
</script>
<template><main class="wallet-shell wallet-staff">
  <header class="wallet-header"><RouterLink v-if="!atHome&&!pointsOnly" class="wallet-icon-button" :to="backPath" :aria-label="t('wallet.ui.back')"><WalletIcon name="back" /></RouterLink><img v-else-if="context?.store.logoMediaAssetId" class="wallet-logo" :src="`/api/v1/stores/${store}/wallet/logo/${context.store.logoMediaAssetId}`" alt=""><WalletStaffAccountMenu v-if="atHome && auth.user" :key="store" :store="store" :store-name="storeCaption" /><div v-else class="wallet-header-title"><h1>{{ t('wallet.ui.staffWallet') }}</h1><small>{{storeCaption}}</small></div><button class="wallet-icon-button" :disabled="busy||operationBusy" :aria-label="t('wallet.refresh')" @click="refreshView"><WalletIcon name="refresh" /></button></header>
  <div class="wallet-utility-bar"><template v-if="atHome&&!pointsOnly"><RouterLink :to="`/stores/${store}/staff`">{{ t('nav.staff.home') }}</RouterLink><StoreSwitcher surface="staff" /></template><WalletLocaleControl /></div>
  <WalletCashierTabs v-if="atHome" :disabled="operationBusy" />
  <p v-if="error&&!historical" class="wallet-error" role="alert">{{ error }}</p><p v-if="busy && !context&&!historical" class="wallet-empty" role="status">{{ t('wallet.loading') }}</p>
  <RouterView v-if="queryPage" :key="`${store}:${viewRevision}`" /><WalletTransactionDetail v-else-if="nativeDetail&&!context" :key="`${String(route.name)}:${store}:${String(route.params.billId??route.params.topupId??route.params.refundId)}`" /><template v-else-if="context"><p v-if="!context.transactionsEnabled" class="wallet-warning" role="status">{{ pointsContext?t('wallet.marketing.unavailable'):context.program?t('wallet.expired'):t('wallet.noProgram') }}</p><RouterView :key="`${store}:${viewRevision}`" @busy-change="operationBusy=$event" /></template>
  <WalletCashierTools v-if="atHome" :disabled="busy||operationBusy" />
  <WalletBottomNav :store="store" />
</main></template>
