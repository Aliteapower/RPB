import { computed, inject, onUnmounted, provide, ref, watch, type InjectionKey } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthSessionStore } from '../../stores/authSession'
import { walletApi, WalletApiError } from './api'
import { useWalletTask } from './useWalletTask'
import type { Account, Context, Customer } from './types'

function createWorkspace() {
  const route = useRoute(), auth = useAuthSessionStore(), task = useWalletTask()
  const actorId = computed(() => auth.user?.accountId ?? '')
  const store = computed(() => String(route.params.storeId)), context = ref<Context | null>(null)
  const identifiedCustomerId=ref<string|null>(null),phoneConfirmedCustomerId=ref<string|null>(null)
  const customer = ref<Customer | null>(null), account = ref<Account | null>(null), customerCode = ref(''), customers = ref<Customer[]>([])
  const can = (permission: string) => auth.user?.permissions.includes(permission) === true
  const pointsContext=computed(()=>(can('wallet.points.view')&&!can('wallet.view'))||(['wallet-topup','wallet-merchant-code'].includes(String(route.name))&&(route.query.kind==='points'||!(route.name==='wallet-merchant-code'?can('wallet.charge.create'):can('wallet.topup.create')))&&can('wallet.points.view')))
  let generation = 0, selection = 0
  function clear() { selection++; identifiedCustomerId.value=null;phoneConfirmedCustomerId.value=null; customer.value = null; account.value = null; customerCode.value = ''; customers.value = [] }
  async function refresh() {
    if(route.name==='wallet-customers')return
    const current = generation, id = store.value, selected = account.value?.id
    const pointsOnly=pointsContext.value
    const result = await walletApi(id).get<Context>(pointsOnly?'/points/context':'/context').catch(e=>{
      if(current===generation && e instanceof WalletApiError && [401,403,404].includes(e.status)){context.value=null;clear()}
      throw e
    })
    if (current !== generation) return
    context.value = result
    if (selected && !pointsOnly) {
      const latest = await walletApi(id).get<Account>(`/accounts/${selected}`)
      if (current === generation && account.value?.id === selected) account.value = latest
    }
  }
  async function search(query: string) {
    await task.run(async () => { const current = generation; const result = await walletApi(store.value).get<Customer[]>(`/customers?query=${encodeURIComponent(query)}`); if (current === generation) customers.value = result })
  }
  async function choose(value: Customer, openAccount = true) {
    await task.run(async () => {
      const current = generation, request = ++selection, id = store.value
      const existing = openAccount ? await walletApi(id).get<Account[]>(`/customers/${value.id}/accounts`) : []
      if (current !== generation || request !== selection) return
      let selected = existing[0] ?? null
      if (openAccount && !selected && value.phone && context.value?.transactionsEnabled && can('wallet.topup.create')) selected = await walletApi(id).command<Account>('/accounts', { customerId: value.id })
      if (current !== generation || request !== selection) return
      if(identifiedCustomerId.value!==value.id)identifiedCustomerId.value=null
      if(phoneConfirmedCustomerId.value!==value.id)phoneConfirmedCustomerId.value=null
      customer.value = value; account.value = selected; customerCode.value = ''; customers.value = []
    })
  }
  async function identifyCustomer(code:string){
    return await task.run(async()=>{
      const current=generation,request=++selection
      const result=await walletApi(store.value).post<Customer>('/customer-codes/resolve',{code})
      if(current!==generation||request!==selection)return
      identifiedCustomerId.value=result.id;customer.value=result;account.value=null;customerCode.value='';customers.value=[]
      return true
    })
  }
  async function scan(code: string, purpose?: 'topup') {
    return await task.run(async () => {
      const current = generation, request = ++selection
      const result = await walletApi(store.value).post<{ account: Account; customer: Customer }>('/payment-codes/resolve', { code, ...(purpose ? { purpose } : {}) })
      if (current !== generation || request !== selection) return
      identifiedCustomerId.value=null;account.value = result.account; customer.value = result.customer; customerCode.value = purpose === 'topup' ? '' : code; customers.value = []
      return true
    })
  }
  watch([store,pointsContext,()=>route.query.entry==='phone',()=>route.name==='wallet-customers'], ([id,only,byPhone],old) => {
    const preserved=old?.[0]===id&&old?.[2]===byPhone?customer.value:null
    const identity=preserved?.id===identifiedCustomerId.value?identifiedCustomerId.value:null
    const phoneIdentity=preserved?.id===phoneConfirmedCustomerId.value?phoneConfirmedCustomerId.value:null
    generation++; clear();customer.value=preserved;identifiedCustomerId.value=identity;phoneConfirmedCustomerId.value=phoneIdentity;if(route.name!=='wallet-customers'||old?.[0]!==id)context.value = null;task.error.value=''
    const current=generation
    void refresh().then(async()=>{if(current===generation && preserved && !only && !String(route.name).includes('transaction') && route.name!=='wallet-business-report' && route.name!=='wallet-customers' && route.name!=='wallet-points-reverse' && (route.name!=='wallet-topup'||((identity||phoneIdentity)&&route.query.kind==='money')))await choose(preserved)}).catch(e=>{if(current===generation)task.error.value=e instanceof Error?e.message:String(e)})
  }, { immediate: true,flush:'sync' })
  const timer = window.setInterval(() => { if (!task.busy.value && document.visibilityState === 'visible') void refresh().catch(e => { task.error.value = String(e.message ?? e) }) }, 5000)
  onUnmounted(() => { generation++; window.clearInterval(timer) })
  return { store, actorId, context, pointsContext, identifiedCustomerId,phoneConfirmedCustomerId, customer, account, customerCode, customers, can, clear, refresh, search, choose, scan, identifyCustomer, ...task }
}
type Workspace = ReturnType<typeof createWorkspace>
const key: InjectionKey<Workspace> = Symbol('staff-wallet')
export function provideStaffWallet() { const workspace = createWorkspace(); provide(key, workspace); return workspace }
export function useStaffWallet() { const workspace = inject(key); if (!workspace) throw new Error('Wallet staff workspace missing'); return workspace }
