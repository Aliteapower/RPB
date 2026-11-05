import { onUnmounted, ref, shallowRef, watch, type Ref } from 'vue'
import { walletApi } from './api'
import { useWalletTask } from './useWalletTask'

/** A detail owns its read/poll lifecycle; obsolete store or route responses cannot repaint it. */
export function useWalletRecord<T>(store: Ref<string>, path: Ref<string>, customer = false) {
  const record = shallowRef<T | null>(null), ready = ref(false)
  const task = useWalletTask()
  let generation = 0, sequence = 0
  async function refresh() {
    const current = generation, request = ++sequence
    const result = await walletApi(store.value, customer).get<T>(path.value)
    if (current === generation && request === sequence) record.value = result
  }
  watch([store, path], () => {
    generation++; record.value = null; ready.value = false; task.error.value = ''
    const current=generation
    task.busy.value=true
    void refresh().catch(e=>{if(current===generation)task.error.value=e instanceof Error?e.message:String(e)}).finally(() => { if(current===generation){ready.value=true;task.busy.value=false} })
  }, { immediate: true })
  const timer = window.setInterval(() => { if (document.visibilityState === 'visible') void task.run(refresh) }, 5000)
  onUnmounted(() => { generation++; window.clearInterval(timer) })
  return { record, ready, refresh, ...task }
}
