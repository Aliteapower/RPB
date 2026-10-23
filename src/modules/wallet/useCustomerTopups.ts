import { computed, onUnmounted, ref, watch } from 'vue'
import { useCustomerWallet } from './useCustomerWallet'
import { useTopupOffers } from './useTopupOffers'
import { useWalletTask } from './useWalletTask'
import { walletApi, WalletApiError } from './api'
import { rows } from './marketingTypes'
import type { Topup } from './types'

export function useCustomerTopups() {
  const { store, home, refresh: refreshHome } = useCustomerWallet()
  const offer = useTopupOffers(store, true), task = useWalletTask()
  const { offers, selected, channel, quote, expired } = offer
  const records = ref<Topup[]>([]), active = ref<Topup | null>(null), qr = ref(''), note = ref('')
  const ready = ref(false), hasMore = ref(false)
  const timezone = computed(() => home.value?.context.store.timezone ?? 'Asia/Singapore')
  const canApply = computed(() => !!home.value?.context.transactionsEnabled && offers.value?.selfServiceEnabled === true
    && !!offers.value.offers.find(o => o.id === selected.value)
    && offers.value.channels.includes(channel.value as never)
    && (channel.value !== 'paynow' || home.value.context.paynowConfigured))
  let generation = 0, selection = 0
  const current = (version: number) => version === generation
  function remember(result: Topup) {
    active.value = result
    records.value = [result, ...records.value.filter(r => r.id !== result.id)]
    if (result.status !== 'pending_collection') qr.value = ''
  }
  async function history(append = false) {
    const version = generation, id = store.value
    await task.run(async () => {
      const result = rows(await walletApi(id, true).get<Topup[]>(`/topups?offset=${append ? records.value.length : 0}`))
      if (!current(version)) return
      records.value = append ? [...records.value, ...result.filter(r => !records.value.some(o => o.id === r.id))] : result
      ready.value = true; hasMore.value = result.length === 100
      if (active.value) active.value = records.value.find(r => r.id === active.value?.id) ?? active.value
      if (active.value?.status !== 'pending_collection') qr.value = ''
    })
  }
  async function apply() {
    if (!canApply.value || offer.busy.value) return
    const version = generation, id = store.value, choice = selected.value, method = channel.value
    await task.run(async () => {
      // Reuse an unexpired quote on an uncertain command retry so its idempotency key remains stable.
      if (!quote.value || expired.value) await offer.getQuote()
      if (!current(version) || choice !== selected.value || method !== channel.value || !canApply.value || !quote.value || expired.value) return
      const quoteId = quote.value.quoteId
      try {
        const result = await walletApi(id, true).command<Topup>('/topups', { quoteId })
        if (!current(version)) return
        quote.value = null; qr.value = ''; remember(result)
      } catch (e) {
        if (!current(version)) return
        if (e instanceof WalletApiError && e.code === 'WALLET_PENDING_TOPUP_EXISTS' && typeof e.details.pendingTopupId === 'string') {
          const result = await walletApi(id, true).get<Topup>(`/topups/${encodeURIComponent(e.details.pendingTopupId)}`)
          if (!current(version)) return
          remember(result); quote.value = null
        }
        if (e instanceof WalletApiError && ['WALLET_OFFER_CHANGED', 'WALLET_TOPUP_QUOTE_EXPIRED'].includes(e.code)) quote.value = null
        throw e
      }
    })
  }
  async function action(kind: 'declare-paid' | 'cancel') {
    if (!active.value) return
    const version = generation, id = store.value, topupId = active.value.id
    await task.run(async () => {
      const result = await walletApi(id, true).command<Topup>(`/topups/${topupId}/${kind}`, kind === 'cancel' ? { reason: note.value } : { note: note.value })
      if (!current(version)) return
      remember(result); if (kind === 'cancel') qr.value = ''
      await refreshHome()
    })
  }
  async function show(record: Topup) {
    if (task.busy.value) return
    active.value = record; qr.value = ''; note.value = ''
    const version = generation, request = ++selection, id = store.value
    await task.run(async () => {
      const result = await walletApi(id, true).get<Topup>(`/topups/${record.id}`)
      if (current(version) && request === selection) remember(result)
    })
  }
  async function showQr() {
    if (!active.value) return
    const version = generation, topupId = active.value.id, id = store.value
    await task.run(async () => {
      const result = await walletApi(id, true).get<{ payload: string }>(`/topups/${topupId}/paynow`)
      if (current(version) && active.value?.id === topupId) qr.value = result.payload
    })
  }
  watch(store, () => { generation++; selection++; records.value = []; active.value = null; qr.value = ''; ready.value = false; void history() }, { immediate: true })
  onUnmounted(() => { generation++; selection++ })
  return { store, home, offer, task, offers, selected, channel, quote, expired, canApply, records, active, qr, note, ready, hasMore, timezone, history, apply, action, show, showQr }
}
