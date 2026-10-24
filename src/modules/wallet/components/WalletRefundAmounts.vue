<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { money } from '../format'
defineProps<{amounts:{cashPrincipalRefundCents?:number;principalReturnCents?:number;bonusReturnCents?:number;bonusRevokeCents?:number}}>()
const {t}=useI18n()
const fields={cashPrincipalRefundCents:'cashRefund',principalReturnCents:'principalReturn',bonusReturnCents:'bonusReturn',bonusRevokeCents:'bonusRevoke'} as const
</script>
<template><dl class="wallet-detail-fields"><template v-for="(label,field) in fields" :key="field"><template v-if="amounts[field]!==undefined"><dt>{{t(`wallet.marketing.${label}`)}}</dt><dd>{{money(amounts[field]!)}}</dd></template></template></dl></template>
