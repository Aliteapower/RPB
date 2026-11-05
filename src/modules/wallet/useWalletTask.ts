import { ref } from 'vue'
export function useWalletTask() {
  const busy = ref(false), error = ref('')
  async function run<T>(work: () => Promise<T>): Promise<T | undefined> {
    if (busy.value) return
    busy.value = true; error.value = ''
    try { return await work() } catch (e) { error.value = e instanceof Error ? e.message : String(e) }
    finally { busy.value = false }
  }
  return { busy, error, run }
}
