<script setup lang="ts">
import {useAuthSessionStore} from '../../../stores/authSession'
import {useCardText} from '../../cards/text'
import { useI18n } from 'vue-i18n'
import { useStaffWallet } from '../useStaffWallet'
import { staffWalletPath } from '../navigation'
import WalletIcon from '../components/WalletIcon.vue'
import WalletCustomerManagementEntry from '../components/WalletCustomerManagementEntry.vue'
const {t}=useI18n(),{store,can}=useStaffWallet()
const {t:ct}=useCardText(),auth=useAuthSessionStore()
const options=[['business-report','chart','wallet.businessReport.title','wallet.reconcile.view'],['marketing','settings','wallet.marketing.marketing','wallet.marketing.view'],['refund-create','plus','wallet.refund','wallet.refund.manage'],['exceptions','bill','wallet.collectionExceptions','wallet.refund.manage'],['report','chart','wallet.report','wallet.reconcile.view'],['settings','settings','wallet.settings','wallet.settings.manage']]
</script>
<template><section class="wallet-page-heading"><h2>{{ t('wallet.ui.more') }}</h2><p>{{ t('wallet.shared') }}</p></section><section class="wallet-menu-list"><WalletCustomerManagementEntry /><RouterLink v-if="auth.isTenantAdmin && can('catalog.view')" :to="`/stores/${store}/catalog`"><WalletIcon name="settings" /><strong>{{ct('catalog')}}</strong></RouterLink><RouterLink v-if="can('wallet.topup.create')||can('wallet.points.view')" :to="staffWalletPath(store,'topup')"><WalletIcon name="plus" /><strong>{{t('wallet.marketing.rechargeEntry')}}</strong><WalletIcon name="arrow" :size="17" /></RouterLink><template v-for="[page,icon,label,permission] in options" :key="page"><RouterLink v-if="can(permission!)||(page==='settings'&&auth.isTenantAdmin)" :to="staffWalletPath(store,page)"><WalletIcon :name="icon!" /><strong>{{ t(label!) }}</strong><WalletIcon name="arrow" :size="17" /></RouterLink></template><a :href="`/wallet/${store}`" target="_blank" rel="noreferrer"><WalletIcon name="wallet" /><strong>{{ t('wallet.customerEntry') }}</strong><WalletIcon name="arrow" :size="17" /></a></section></template>
