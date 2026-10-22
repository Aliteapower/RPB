<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { customerWalletPath, staffWalletPath } from '../navigation'
import WalletIcon from './WalletIcon.vue'
const props = defineProps<{ store: string; customer?: boolean }>()
const { t } = useI18n()
const route=useRoute()
const href = (page: string) => props.customer ? customerWalletPath(props.store, page) : staffWalletPath(props.store, page)
const relative=computed(()=>route.path.slice(href('').length))
const section=computed(()=>/^\/(bills|topups)(\/|$)/.test(relative.value)?'bills':props.customer?(relative.value==='/history'?'other':'home'):(['','/collect','/topup'].includes(relative.value)?'home':'other'))
</script>
<template><nav class="wallet-bottom-nav" :aria-label="t('wallet.ui.navigation')">
  <RouterLink :to="href('')" :class="{'is-current':section==='home'}"><WalletIcon name="wallet" /><span>{{ t(customer ? 'wallet.ui.myWallet' : 'wallet.ui.cashier') }}</span></RouterLink>
  <RouterLink :to="href('bills')" :class="{'is-current':section==='bills'}"><WalletIcon name="bill" /><span>{{ t('wallet.bills') }}</span></RouterLink>
  <RouterLink :to="href(customer ? 'history' : 'more')" :class="{'is-current':section==='other'}"><WalletIcon :name="customer ? 'history' : 'more'" /><span>{{ t(customer ? 'wallet.ui.entries' : 'wallet.ui.more') }}</span></RouterLink>
</nav></template>
