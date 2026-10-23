<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useStaffWallet } from '../useStaffWallet'
import { staffWalletPath } from '../navigation'
import WalletIcon from '../components/WalletIcon.vue'
const {t}=useI18n(),{store,can}=useStaffWallet()
const options=[['marketing','settings','wallet.marketing.marketing','wallet.marketing.view'],['points','plus','wallet.marketing.points','wallet.points.view'],['topups','history','wallet.marketing.applications','wallet.collection.confirm'],['history','history','wallet.ui.entries','wallet.view'],['refunds','history','wallet.refunds','wallet.refund.manage'],['refund-create','plus','wallet.refund','wallet.refund.manage'],['exceptions','bill','wallet.collectionExceptions','wallet.refund.manage'],['report','chart','wallet.report','wallet.reconcile.view'],['settings','settings','wallet.settings','wallet.settings.manage']]
</script>
<template><section class="wallet-page-heading"><h2>{{ t('wallet.ui.more') }}</h2><p>{{ t('wallet.shared') }}</p></section><section class="wallet-menu-list"><template v-for="[page,icon,label,permission] in options" :key="page"><RouterLink v-if="can(permission!)" :to="staffWalletPath(store,page)"><WalletIcon :name="icon!" /><strong>{{ t(label!) }}</strong><WalletIcon name="arrow" :size="17" /></RouterLink></template><a :href="`/wallet/${store}`" target="_blank" rel="noreferrer"><WalletIcon name="wallet" /><strong>{{ t('wallet.customerEntry') }}</strong><WalletIcon name="arrow" :size="17" /></a></section></template>
