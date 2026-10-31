<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { customerWalletPath, staffWalletPath } from '../navigation'
import WalletIcon from './WalletIcon.vue'
const props = defineProps<{ store: string; customer?: boolean; historyOnly?: boolean }>()
const { t } = useI18n()
const route=useRoute()
const href = (page: string) => props.customer ? customerWalletPath(props.store, page) : staffWalletPath(props.store, page)
const relative=computed(()=>route.path.slice(href('').length))
const section=computed(()=>/^\/(transactions|bills|topups|refunds|points)(\/|$)/.test(relative.value)?'transactions':(['','/collect','/topup','/merchant-code'].includes(relative.value)||relative.value.startsWith('/merchant-orders/'))?'home':'other')
</script>
<template><nav class="wallet-bottom-nav" :aria-label="t('wallet.ui.navigation')">
  <RouterLink v-if="!historyOnly" :to="href('')" :class="{'is-current':section==='home'}"><WalletIcon name="wallet" /><span>{{ t(customer ? 'wallet.ui.myWallet' : 'wallet.ui.cashier') }}</span></RouterLink>
  <RouterLink :to="href('transactions')+(customer?'?category=all':historyOnly?'?history=1':'')" :class="{'is-current':section==='transactions'}"><WalletIcon name="bill" /><span>{{ t('wallet.transactions.title') }}</span></RouterLink>
  <RouterLink v-if="!customer&&!historyOnly" :to="href('more')" :class="{'is-current':section==='other'}"><WalletIcon :name="customer ? 'history' : 'more'" /><span>{{ t(customer ? 'wallet.ui.entries' : 'wallet.ui.more') }}</span></RouterLink>
</nav></template>
