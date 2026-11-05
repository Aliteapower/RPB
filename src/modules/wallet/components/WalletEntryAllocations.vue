<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Entry } from '../types'
import WalletAmountSplit from './WalletAmountSplit.vue'
defineProps<{entry:Entry}>()
const {t}=useI18n()
</script>
<template><details v-if="entry.allocations?.length" class="wallet-entry-allocations"><summary>{{t('wallet.marketing.allocations')}}</summary><article v-for="allocation in entry.allocations" :key="allocation.lotId" class="wallet-record"><small>{{t('wallet.marketing.lot')}} {{allocation.lotId}}</small><WalletAmountSplit :principal="allocation.principalCents" :bonus="allocation.bonusCents" /></article></details><small v-else class="wallet-muted">{{t((entry.bonusCents??0)===0?'wallet.marketing.legacyPrincipal':'wallet.marketing.noAllocations')}}</small></template>
