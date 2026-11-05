<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { money, dateTime } from '../format'
import WalletIcon from './WalletIcon.vue'
defineProps<{ to: string; title: string; cents: number; status: string; time: string; timezone: string; detail?: string }>()
const { t } = useI18n()
</script>
<template><RouterLink :to="to" class="wallet-list-row"><span class="wallet-list-icon"><WalletIcon name="bill" /></span><span class="wallet-list-copy"><strong>{{ title }}</strong><small>{{ dateTime(time, timezone) }}</small><small v-if="detail">{{ detail }}</small></span><span class="wallet-list-amount"><strong>{{ money(cents) }}</strong><span class="wallet-status" :data-state="status">{{ t(`wallet.ui.status.${status}`) }}</span></span><WalletIcon name="arrow" :size="17" /></RouterLink></template>
