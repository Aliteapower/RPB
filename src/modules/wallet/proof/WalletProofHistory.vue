<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useWalletRecord } from '../useWalletRecord'
import type { WalletProof } from './types'

const props = defineProps<{ store: string; sourceType: string; sourceId: string }>()
const { t } = useI18n()
const path = computed(() => `/collection-proofs/${props.sourceType}/${encodeURIComponent(props.sourceId)}`)
const { record, error, busy, ready, refresh, run } = useWalletRecord<WalletProof[]>(computed(() => props.store), path)
</script>
<template>
  <section class="wallet-card">
    <div class="wallet-proof-heading"><h3>{{ t('wallet.proof.history') }}</h3><button type="button" :disabled="busy" @click="run(refresh)">{{ t('wallet.refresh') }}</button></div>
    <p v-if="busy && !ready" role="status">{{ t('wallet.loading') }}</p>
    <p v-if="error" class="wallet-error" role="alert">{{ error }}</p>
    <p v-if="ready && !error && !record?.length" class="wallet-empty">{{ t('wallet.empty') }}</p>
    <article v-for="item in record ?? []" :key="item.id" class="wallet-proof-item">
      <p role="status">{{ t(item.confirmation.verified === true ? 'wallet.proof.confirmed' : 'wallet.proof.unconfirmed') }}</p>
      <p v-if="item.confirmation.verified !== true" class="wallet-warning">{{ t('wallet.proof.unconfirmedHint') }}</p>
      <dl class="wallet-detail-fields">
        <dt>{{ t('wallet.proof.systemReference') }}</dt><dd>{{ item.context.systemReference }}</dd>
        <dt>{{ t('wallet.proof.bankReference') }}</dt><dd>{{ item.confirmation.bankReference || item.bankReference || '—' }}</dd>
        <template v-if="item.confirmation.verified === true">
          <dt>{{ t('wallet.proof.actualAccount') }}</dt><dd>{{ item.confirmation.actualAccount }}</dd>
          <dt>{{ t('wallet.note') }}</dt><dd>{{ item.confirmation.note || '—' }}</dd>
        </template>
        <template v-else><dt>{{ t('wallet.proof.expectedAccount') }}</dt><dd>{{ item.context.expectedAccount }}</dd></template>
        <template v-for="(result, field) in item.checks" :key="field"><dt>{{ t('wallet.proof.' + field) }}</dt><dd>{{ t('wallet.proof.' + result) }}</dd></template>
      </dl>
      <details><summary>{{ t('wallet.proof.scan') }}</summary><pre>{{ item.extracted.rawText }}</pre><p>{{ t('wallet.proof.bankReference') }}: {{ item.bankReference || '—' }}</p></details>
      <a class="wallet-secondary-link" :href="item.imageUrl" target="_blank" rel="noopener noreferrer">{{ t('wallet.proof.view') }}</a>
    </article>
  </section>
</template>
<style scoped>
.wallet-proof-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}
.wallet-proof-heading h3{margin:0}
.wallet-proof-item{margin-top:16px}
.wallet-proof-item+.wallet-proof-item{padding-top:16px;border-top:1px solid var(--wallet-line)}
pre{white-space:pre-wrap;overflow-wrap:anywhere}dd{overflow-wrap:anywhere}
</style>
