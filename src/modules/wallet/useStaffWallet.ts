import { computed, inject, onUnmounted, provide, ref, watch, type InjectionKey } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthSessionStore } from '../../stores/authSession'
import { walletApi, WalletApiError } from './api'
import { useWalletTask } from './useWalletTask'
import type { Account, Context, Customer } from './types'

function createWorkspace() {
  const route = useRoute(), auth = useAuthSessionStore(), task = useWalletTask()
  const store = computed(() => String(route.params.storeId)), context = ref<Context | null>(null)
  const customer = ref<Customer | null>(null), account = ref<Account | null>(null), customerCode = ref(''), customers = ref<Customer[]>([])
  const can = (permission: string) => auth.user?.permissions.includes(permission) === true
  let generation = 0, selection = 0
  function clear() { selection++; customer.value = null; account.value = null; customerCode.value = ''; customers.value = [] }
  async function refresh() {
    const current = generation, id = store.value, selected = account.value?.id
    const result = await walletApi(id).get<Context>('/context').catch(e=>{
      if(current===generation && e instanceof WalletApiError && [401,403,404].includes(e.status)){context.value=null;clear()}
      throw e
    })
    if (current !== generation) return
    context.value = result
    if (selected) {
      const latest = await walletApi(id).get<Account>(`/accounts/${selected}`)
      if (current === generation && account.value?.id === selected) account.value = latest
    }
  }
  async function search(query: string) {
    await task.run(async () => { const current = generation; const result = await walletApi(store.value).get<Customer[]>(`/customers?query=${encodeURIComponent(query)}`); if (current === generation) customers.value = result })
  }
  async function choose(value: Customer) {
    await task.run(async () => {
      const current = generation, request = ++selection, id = store.value
      const existing = await walletApi(id).get<Account[]>(`/customers/${value.id}/accounts`)
      if (current !== generation || request !== selection) return
      let selected = existing[0] ?? null
      if (!selected && context.value?.transactionsEnabled && can('wallet.topup.create')) selected = await walletApi(id).command<Account>('/accounts', { customerId: value.id })
      if (current !== generation || request !== selection) return
      customer.value = value; account.value = selected; customerCode.value = ''; customers.value = []
    })
  }
  async function scan(code: string) {
    await task.run(async () => {
      const current = generation, request = ++selection
      const result = await walletApi(store.value).post<{ account: Account; customer: Customer }>('/payment-codes/resolve', { code })
      if (current !== generation || request !== selection) return
      account.value = result.account; customer.value = result.customer; customerCode.value = code; customers.value = []
    })
  }
  watch(store, () => {
    generation++; clear(); context.value = null;task.error.value=''
    const current=generation
    void refresh().catch(e=>{if(current===generation)task.error.value=e instanceof Error?e.message:String(e)})
  }, { immediate: true })
  const timer = window.setInterval(() => { if (!task.busy.value && document.visibilityState === 'visible') void refresh().catch(e => { task.error.value = String(e.message ?? e) }) }, 5000)
  onUnmounted(() => { generation++; window.clearInterval(timer) })
  return { store, context, customer, account, customerCode, customers, can, clear, refresh, search, choose, scan, ...task }
}
type Workspace = ReturnType<typeof createWorkspace>
const key: InjectionKey<Workspace> = Symbol('staff-wallet')
export function provideStaffWallet() { const workspace = createWorkspace(); provide(key, workspace); return workspace }
export function useStaffWallet() { const workspace = inject(key); if (!workspace) throw new Error('Wallet staff workspace missing'); return workspace }
