<script setup lang="ts">
import { computed,ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useStaffWallet } from '../useStaffWallet'
import { staffWalletPath } from '../navigation'
import WalletIcon from '../components/WalletIcon.vue'
const { t } = useI18n(), { store, context, can } = useStaffWallet()
const active=ref('collect')
const actions=computed(()=>[...(context.value?.transactionsEnabled&&context.value.program&&can('wallet.charge.create')&&can('wallet.collection.confirm')?[{id:'collect',tab:'wallet.personal.collectEntry',label:'wallet.personal.collectEntry',hint:'wallet.personal.collectHint',to:staffWalletPath(store.value,'collect')}]:[]),...(context.value?.transactionsEnabled&&context.value.program&&can('wallet.view')&&can('wallet.topup.create')?[{id:'money',tab:'wallet.transactions.folder_topup',label:'wallet.marketing.moneyTopup',hint:'wallet.marketing.scanTopupHint',to:staffWalletPath(store.value,'topup')+'?scan=1&kind=money'}]:[]),...(can('wallet.points.view')?[{id:'points',tab:'wallet.transactions.folder_points',label:'wallet.marketing.pointsOperations',hint:'wallet.marketing.scanPointsHint',to:staffWalletPath(store.value,'topup')+'?scan=1&kind=points'}]:[])])
const action=computed(()=>actions.value.find(a=>a.id===active.value)??actions.value[0])
</script>
<template><section v-if="action" class="wallet-home-hero"><p class="wallet-eyebrow">{{t('wallet.ui.cashier')}}</p>
 <nav class="wallet-cashier-tabs" :aria-label="t('wallet.marketing.operationType')"><button v-for="item in actions" :key="item.id" type="button" :data-cashier-tab="item.id" :aria-pressed="action.id===item.id" @click="active=item.id">{{t(item.tab)}}</button></nav>
 <h2>{{t(action.label)}}</h2><p>{{t(action.hint)}}</p><div class="wallet-hero-actions wallet-single-action"><RouterLink :to="action.to"><WalletIcon name="scan" :size="32" /><strong>{{t(action.label)}}</strong><small>{{t(action.hint)}}</small></RouterLink></div>
</section>
<div class="wallet-shortcuts">
  <RouterLink v-if="(context?.transactionsEnabled && context.program && can('wallet.topup.create')) || can('wallet.points.view')" :to="staffWalletPath(store,'topup')+'?entry=phone'"><span class="wallet-shortcut-icon"><WalletIcon name="plus" /></span><strong>{{ t('wallet.marketing.phoneEntry') }}</strong><small>{{ t('wallet.marketing.phoneHint') }}</small></RouterLink>
  <RouterLink :to="staffWalletPath(store,'transactions')"><span class="wallet-shortcut-icon"><WalletIcon name="bill" /></span><strong>{{ t('wallet.transactions.title') }}</strong><small>{{ t('wallet.ui.recordsHint') }}</small></RouterLink>
  <RouterLink :to="staffWalletPath(store,'transactions')+'?type=topup&status=pending'"><span class="wallet-shortcut-icon"><WalletIcon name="history" /></span><strong>{{ t('wallet.ui.pendingTopups') }}</strong><small>{{ t('wallet.ui.pendingTopupsHint') }}</small></RouterLink>
  <RouterLink :to="staffWalletPath(store,'more')"><span class="wallet-shortcut-icon"><WalletIcon name="settings" /></span><strong>{{ t('wallet.ui.more') }}</strong><small>{{ t('wallet.ui.managementHint') }}</small></RouterLink>
</div></template>
