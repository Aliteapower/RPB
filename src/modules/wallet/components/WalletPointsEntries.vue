<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PointsEntry } from '../marketingTypes'
import { dateTime } from '../format'
const props = defineProps<{ entries: PointsEntry[]; timezone: string; canReverse?: boolean; disabled?: boolean }>()
defineEmits<{ reverse: [entryId: string] }>()
const { t, locale } = useI18n()
const reversedIds = computed(() => new Set(props.entries.map(e => e.originalEntryId).filter(Boolean)))
function delta(e: PointsEntry) {
  const value = e.afterBalance - e.beforeBalance
  return (value > 0 ? '+' : '') + new Intl.NumberFormat(locale.value).format(value)
}
</script>
<template>
  <article v-for="entry in entries" :key="entry.id" class="wallet-record wallet-points-record">
    <div class="wallet-points-record-heading"><strong>{{ t(`wallet.marketing.${entry.kind}`) }}</strong><strong :class="entry.afterBalance >= entry.beforeBalance ? 'wallet-points-in' : 'wallet-points-out'">{{ delta(entry) }}</strong></div>
    <span>{{ t('wallet.marketing.balance') }} {{ entry.beforeBalance }} → {{ entry.afterBalance }}</span>
    <p v-if="entry.redemptionDescription">{{ t('wallet.marketing.description') }}：{{ entry.redemptionDescription }}</p>
    <p v-if="entry.reason || entry.note">{{ entry.reason }} {{ entry.note }}</p>
    <small>{{ dateTime(entry.createdAt, timezone) }}<template v-if="entry.externalReference"> · {{ entry.externalReference }}</template></small>
    <button v-if="canReverse && !entry.reversed && !entry.reversalOf && entry.kind!=='reversal' && !reversedIds.has(entry.id)" :disabled="disabled" @click="$emit('reverse',entry.id)">{{ t('wallet.marketing.reverse') }}</button>
  </article>
</template>
