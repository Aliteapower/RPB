<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useStaffWallet } from '../useStaffWallet'
import WalletStaffTopup from './WalletStaffTopup.vue'
import WalletPoints from './WalletPoints.vue'
const { t } = useI18n(), route = useRoute(), router = useRouter()
const { can, busy } = useStaffWallet(), operationBusy = ref(false)
const moneyAllowed = computed(() => can('wallet.view') && can('wallet.topup.create'))
const pointsAllowed = computed(() => can('wallet.points.view'))
const pointsMode = computed(() => pointsAllowed.value && (!moneyAllowed.value || route.query.kind === 'points'))
function change(kind: 'money' | 'points') {
  if (busy.value || operationBusy.value) return
  void router.replace({ query: { ...route.query, kind, scan: undefined } })
}
</script>
<template>
  <section class="wallet-page-heading"><h2>{{ t('wallet.marketing.rechargeEntry') }}</h2></section>
  <div v-if="moneyAllowed || pointsAllowed" class="wallet-recharge-options" :aria-label="t('wallet.marketing.operationType')">
    <button v-if="moneyAllowed" type="button" :aria-pressed="!pointsMode" :disabled="busy || operationBusy" @click="change('money')">{{ t('wallet.marketing.moneyTopup') }}</button>
    <button v-if="pointsAllowed" type="button" :aria-pressed="pointsMode" :disabled="busy || operationBusy" @click="change('points')">{{ t('wallet.marketing.pointsOperations') }}</button>
  </div>
  <WalletPoints v-if="pointsMode" embedded @busy-change="operationBusy=$event" />
  <WalletStaffTopup v-else-if="moneyAllowed" embedded @busy-change="operationBusy=$event" />
  <p v-else class="wallet-empty">{{ t('wallet.noRights') }}</p>
</template>
