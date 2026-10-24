import { computed, inject, onUnmounted, provide, ref, watch, type InjectionKey } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { walletApi, WalletApiError } from './api'
import { merchantCode } from './scanning/walletQr'
import { customerWalletPath, legacyWalletTarget } from './navigation'
import type { CustomerHome } from './types'
import {useI18n} from 'vue-i18n'
import {logoutCustomer} from '../../api/customerAuthApi'

function createWorkspace() {
  const route=useRoute(),router=useRouter(),store=computed(()=>String(route.params.storeId))
  const home=ref<CustomerHome|null>(null),needsLogin=ref(false),error=ref(''),linkError=ref(''),loading=ref(false)
  const loggingOut=ref(false),{t}=useI18n()
  const account=computed(()=>home.value?.accounts[0]??null)
  let generation=0,sequence=0,legacyRunning=false,lastLegacyAttempt=''
  async function scan(value:string,replace=false) {
    const decoded=merchantCode(value,window.location.origin)
    if(decoded.store && decoded.store!==store.value){const path=`${customerWalletPath(decoded.store)}#${decoded.kind}=${encodeURIComponent(decoded.code)}`;if(replace)await router.replace(path);else await router.push(path);return}
    const current=generation,id=store.value
    const redemption=decoded.kind==='redeem'
    const bill=await walletApi(id,true).post<{id:string}>(decoded.kind==='order'?'/merchant-order-code':redemption?'/points-redemption-code':'/bill-code',{code:decoded.code})
    if(current===generation){const path=customerWalletPath(id,`${decoded.kind==='order'?'merchant-orders':redemption?'points-redemptions':'bills'}/${bill.id}`);if(replace)await router.replace(path);else await router.push(path)}
  }
  async function legacy() {
    if(!home.value || legacyRunning)return
    const target=legacyWalletTarget(store.value,route.hash)
    const attempt=store.value+route.hash
    if(!target || lastLegacyAttempt===attempt)return
    lastLegacyAttempt=attempt
    legacyRunning=true
    try { if(target.type==='receipt')await router.replace(target.path);else await scan(target.code,true) }
    catch(e){linkError.value=e instanceof Error?e.message:String(e)} finally{legacyRunning=false}
  }
  async function refresh() {
    if(loggingOut.value)return
    const current=generation,request=++sequence,id=store.value
    loading.value=!home.value
    try {
      const result=await walletApi(id,true).get<CustomerHome>('/me')
      if(current!==generation || request!==sequence)return
      home.value=result;needsLogin.value=false;error.value='';await legacy()
    }catch(e){
      if(current!==generation || request!==sequence)return
      if(e instanceof WalletApiError && e.status===401){home.value=null;needsLogin.value=true;error.value=''}
      else{if(e instanceof WalletApiError && [403,404].includes(e.status))home.value=null;error.value=e instanceof Error?e.message:String(e)}
    }finally{if(current===generation && request===sequence)loading.value=false}
  }
  async function authenticated(){needsLogin.value=false;error.value='';await refresh()}
  async function logout(){
    if(loggingOut.value)return
    loggingOut.value=true;generation++;sequence++;error.value='';linkError.value=''
    try{
      await logoutCustomer()
      generation++;sequence++;home.value=null;needsLogin.value=true;loading.value=false;lastLegacyAttempt=''
      await router.replace(customerWalletPath(store.value))
    }catch{error.value=t('wallet.operation.logoutFailed')}
    finally{loggingOut.value=false}
  }
  watch(store,()=>{generation++;home.value=null;needsLogin.value=false;error.value='';linkError.value='';lastLegacyAttempt='';void refresh()},{immediate:true})
  watch(()=>route.hash,()=>{lastLegacyAttempt='';linkError.value='';void legacy()})
  const timer=window.setInterval(()=>{if(!needsLogin.value && document.visibilityState==='visible')void refresh()},5000)
  onUnmounted(()=>{generation++;window.clearInterval(timer)})
  return{store,home,account,needsLogin,error:computed(()=>linkError.value||error.value),loading,refresh,scan,authenticated,logout,loggingOut}
}
type Workspace=ReturnType<typeof createWorkspace>
const key:InjectionKey<Workspace>=Symbol('customer-wallet')
export function provideCustomerWallet(){const workspace=createWorkspace();provide(key,workspace);return workspace}
export function useCustomerWallet(){const workspace=inject(key);if(!workspace)throw new Error('Customer wallet workspace missing');return workspace}
