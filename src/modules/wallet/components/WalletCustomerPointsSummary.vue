<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePoints } from '../usePoints'
import { useCustomerWallet } from '../useCustomerWallet'
import { customerWalletPath } from '../navigation'
import WalletPointsEntries from './WalletPointsEntries.vue'
const { t, locale } = useI18n(), { store, home } = useCustomerWallet()
const points = usePoints(store, true, ref(undefined)), { account, entries, busy, error, ready } = points
watch(home, () => { if (!busy.value) void points.refresh() })
</script>
<template>
  <section class="wallet-card wallet-points-summary">
    <div class="wallet-page-heading"><h2>{{ t('wallet.marketing.balance') }}<template v-if="account">: {{ account.balance.toLocaleString(locale) }}</template></h2><RouterLink :to="customerWalletPath(store,'points')">{{ t('wallet.marketing.pointsRecords') }} ›</RouterLink></div>
    <p v-if="busy&&!ready" role="status">{{ t('wallet.loading') }}</p>
    <p v-if="error" class="wallet-error" role="alert">{{ error }} <button :disabled="busy" @click="points.refresh()">{{ t('wallet.refresh') }}</button></p>
    <small>{{ t('wallet.marketing.pointsHint') }}</small>
    <p v-if="ready&&!entries.length" class="wallet-empty">{{ t('wallet.marketing.noPointsRecords') }}</p>
    <WalletPointsEntries :entries="entries.slice(0,3)" :timezone="home?.context.store.timezone??'Asia/Singapore'" />
  </section>
</template>
