<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthSessionStore } from '../../../stores/authSession'
import { canReversePoints } from '../pointsOperations'
import { staffWalletPath } from '../navigation'
import type { TransactionDetail } from '../transactions/types'
const props=defineProps<{store:string;detail:TransactionDetail;returnTo:string}>()
const {t}=useI18n(),auth=useAuthSessionStore()
const allowed=computed(()=>auth.user?.permissions.includes('wallet.points.reverse')&&canReversePoints(props.detail))
</script>
<template><RouterLink v-if="allowed" class="wallet-secondary-link" :to="{path:staffWalletPath(store,`points/${detail.transaction.id}/reverse`),query:{returnTo}}">{{t('wallet.operation.reverse')}}</RouterLink></template>
