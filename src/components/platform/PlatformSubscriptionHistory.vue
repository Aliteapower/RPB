<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { listSubscriptionHistory } from '../../api/platformSubscriptionHistoryApi'
import { PlatformBillingApiError } from '../../api/platformProductLineBillingApi'
import type { SubscriptionHistoryEvent } from '../../types/platformSubscriptionHistory'
import { historyAmount, historyValue, subscriptionHistoryText } from './subscriptionHistoryText'

const props = defineProps<{ tenantId: string; subscriptionId: string }>()
const { locale } = useI18n()
const english = computed(() => locale.value.startsWith('en'))
const text = computed(() => subscriptionHistoryText(english.value))
const events = ref<SubscriptionHistoryEvent[]>([])
const loading = ref(false)
const error = ref<'forbidden' | 'error' | ''>('')
let request = 0
let controller: AbortController | undefined
async function load() {
  const token = ++request
  controller?.abort()
  controller = new AbortController()
  events.value = []
  error.value = ''
  loading.value = false
  if (!props.tenantId || !props.subscriptionId) return
  loading.value = true
  try {
    const result = await listSubscriptionHistory(props.tenantId, props.subscriptionId, controller.signal)
    if (token === request) events.value = result.events
  } catch (failure) {
    if (token === request) error.value = failure instanceof PlatformBillingApiError && failure.status === 403 ? 'forbidden' : 'error'
  } finally { if (token === request) loading.value = false }
}
watch(() => [props.tenantId, props.subscriptionId], load, { immediate: true })
onBeforeUnmount(() => { ++request; controller?.abort() })
const period = (start: string | null, end: string | null) => `${start ?? '—'} → ${end ?? '—'}`
</script>

<template>
  <section class="subscription-history" :aria-label="text.title">
    <h4>{{ text.title }}</h4>
    <p class="history-notice">{{ text.notice }}</p>
    <p v-if="loading" role="status">{{ text.loading }}</p>
    <p v-else-if="error" role="alert">{{ text[error] }} <button v-if="error === 'error'" type="button" @click="load">{{ text.retry }}</button></p>
    <p v-else-if="!events.length">{{ text.empty }}</p>
    <article v-for="event in events" :key="event.id" class="history-event">
      <strong>{{ historyValue(event.eventType, english) }} · {{ historyValue(event.billingCycle, english) }}</strong>
      <small>{{ event.createdAt }} · {{ historyValue(event.status, english) }}</small>
      <p>{{ text.amount }}: {{ historyAmount(event.amount, event.currency) }}</p>
      <p>{{ text.period }}: {{ period(event.periodStart, event.periodEnd) }}</p>
      <p v-if="event.paymentNote">{{ text.note }}: {{ event.paymentNote }}</p>
      <p v-if="!event.snapshotAvailable" class="history-notice">{{ text.legacy }}</p>
      <p v-else-if="!event.stores.length">{{ text.noStores }}</p>
      <ul v-else class="history-stores">
        <li v-for="(store, index) in event.stores" :key="`${store.storeId}:${index}`">
          <strong>{{ text.store }}: {{ store.storeName }} <span v-if="store.storeCode">({{ store.storeCode }})</span></strong>
          <span>{{ text.entity }}: {{ store.operatingEntityName ?? '—' }}</span>
          <span>{{ text.amount }}: {{ historyAmount(store.amount, store.currency) }} · {{ text.quantity }}: {{ store.quantity ?? '—' }}</span>
          <span>{{ text.period }}: {{ period(store.periodStart, store.periodEnd) }}</span>
          <span>{{ text.status }}: {{ historyValue(store.itemStatus ?? '—', english) }} · {{ text.store }}: {{ historyValue(store.storeStatus ?? '—', english) }}</span>
        </li>
      </ul>
    </article>
  </section>
</template>

<style scoped>
.subscription-history { border-top: 1px solid #dbe3ed; margin-top: 16px; padding-top: 12px; overflow-wrap: anywhere; }
.subscription-history h4 { margin: 0 0 8px; }
.history-notice, .history-event small { color: #64748b; font-size: 12px; }
.history-event { border-top: 1px solid #e2e8f0; padding: 12px 0; }
.history-event small { display: block; margin-top: 4px; }
.history-event p { margin: 6px 0; }
.history-stores { padding-left: 20px; }
.history-stores li { margin: 10px 0; }
.history-stores span { display: block; font-size: 13px; margin-top: 4px; }
.history-stores strong span { display: inline; }
</style>
