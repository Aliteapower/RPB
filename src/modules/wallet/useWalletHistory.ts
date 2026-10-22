import { onUnmounted, ref, shallowRef, watch, type Ref } from 'vue'
import { walletApi } from './api'
import { useWalletTask } from './useWalletTask'
/** Filter/pagination are server-owned; view changes invalidate outstanding responses. */
export function useWalletHistory<T extends { id: string }>(store: Ref<string>, resource: Ref<string>, status: Ref<string>, customer = false) {
  const rows = shallowRef<T[]>([]), more = ref(false), loaded = ref(false), task = useWalletTask()
  let generation = 0, sequence = 0
  async function page(append = false) {
    const current = generation, request=++sequence, offset = append ? rows.value.length : 0
    const result = await walletApi(store.value, customer).get<T[]>(`${resource.value}?offset=${offset}&status=${encodeURIComponent(status.value)}`)
    if (current !== generation || request!==sequence) return
    rows.value = append ? [...rows.value, ...result.filter(r => !rows.value.some(old => old.id === r.id))] : result
    more.value = result.length === 100; loaded.value = true
  }
  watch([store, resource, status], () => {
    generation++; rows.value = []; loaded.value = false; more.value = false; task.error.value = ''
    const current=generation;task.busy.value=true
    void page().catch(e=>{if(current===generation)task.error.value=e instanceof Error?e.message:String(e)}).finally(()=>{if(current===generation)task.busy.value=false})
  }, { immediate: true })
  const timer = window.setInterval(() => { if (rows.value.length <= 100 && document.visibilityState === 'visible') void task.run(() => page()) }, 5000)
  onUnmounted(() => { generation++; window.clearInterval(timer) })
  return { rows, more, loaded, refresh: () => task.run(() => page()), loadMore: () => task.run(() => page(true)), ...task }
}
