<script setup lang="ts">
import MerchantStorageInventory from './merchant/MerchantStorageInventory.vue'
import CustomerQuickCreate from '../customers/CustomerQuickCreate.vue'
import MerchantStorageSettingsPage from './merchant/MerchantStorageSettingsPage.vue'
import {computed,ref,watch} from 'vue'
import {useRouter} from 'vue-router'
import {useAuthSessionStore} from '../../stores/authSession'
import {useMerchantStorage} from './merchant/useMerchantStorage'
import StorageScanResult from './merchant/StorageScanResult.vue'
import StorageCustomerPicker from './merchant/StorageCustomerPicker.vue'
import MerchantStorageInbox from './merchant/MerchantStorageInbox.vue'
import MerchantStorageDeposit from './merchant/MerchantStorageDeposit.vue'
import MerchantStorageWithdrawal from './merchant/MerchantStorageWithdrawal.vue'
import MerchantStorageRecords from './merchant/MerchantStorageRecords.vue'
import WalletQrScanner from '../wallet/components/WalletQrScanner.vue'
import '../wallet/wallet.css'
import './storage.css'
import './merchant/storage-management.css'
defineOptions({inheritAttrs:false})
const {store,api,home,customer,results,query,task,busy,loading,searching,searched,pending,error,notice,activeIntent,scanResult,scoped,can,choose,search,open,load,run,scan,recover,fail,t}=useMerchantStorage()
const inventoryRefresh=ref(0)
const depositTask=computed(()=>task.value==='deposit'||task.value==='inbox'),customerTask=computed(()=>task.value==='withdraw'||task.value==='records')
const pendingCount=computed(()=>home.value?.applications.filter(a=>a.status==='pending').length??0)
const addingCustomer=ref(false),previousTask=ref('deposit')
const auth=useAuthSessionStore(),router=useRouter(),operationBusy=ref(false),showScan=ref(false),storeInput=ref(store.value)
watch(store,()=>{addingCustomer.value=false;operationBusy.value=false;showScan.value=false;storeInput.value=store.value})
const canCreate=computed(()=>!!home.value?.newBusinessEnabled&&auth.isTenantAdmin&&auth.user?.storeIds.includes(store.value)===true)
watch(canCreate,allowed=>{if(!allowed)addingCustomer.value=false})
watch(task,()=>{addingCustomer.value=false})
function showSettings(){previousTask.value=task.value==='settings'?'deposit':task.value;showScan.value=false;open('settings')}
function backSettings(){open(previousTask.value==='deposit'&&!home.value?.newBusinessEnabled?'records':previousTask.value)}
function createdCustomer(value:{id:string;displayName:string|null;phoneE164:string|null}){addingCustomer.value=false;choose({id:value.id,displayName:value.displayName||value.phoneE164||value.id,phone:value.phoneE164})}
function lookupCustomer(phone:string){addingCustomer.value=false;query.value=phone;void search()}
function identify(code:string){showScan.value=false;void run(()=>scan(code))}
</script>
<template><main class="cards-page storage-page storage-merchant-page" data-storage-role="merchant">
 <header class="storage-page-header"><div class="storage-heading"><RouterLink :to="`/stores/${store}/wallet`">← {{t('back')}}</RouterLink><div><span class="cards-badge">{{t('merchantArea')}}</span><h1>{{t('manage')}}</h1></div></div><div class="storage-inline storage-header-actions"><button v-if="home&&task!=='settings'" :disabled="busy||operationBusy||pending" @click="showSettings">{{t('settingsEntry')}}</button><button :disabled="loading||busy||operationBusy" @click="load();inventoryRefresh++">{{t('refresh')}}</button></div></header>
 <div class="storage-inline"><p class="storage-customer-store">{{home?.storeName}}</p><form v-if="home&&home.stores.length>1" class="storage-inline" @submit.prevent="router.push(`/stores/${encodeURIComponent(storeInput)}/wine-storage`)"><select v-model="storeInput" :disabled="busy||operationBusy" :aria-label="t('storeId')"><option v-for="option in home.stores" :key="option.id" :value="option.id">{{option.name}}</option></select><button :disabled="busy||operationBusy">{{t('switchStore')}}</button></form></div><p v-if="loading&&!home" role="status">{{t('loading')}}</p><p v-if="error" class="storage-error" role="alert">{{error}}</p><p v-if="notice" class="storage-notice" role="status">{{notice}}</p>
 <section v-if="pending" class="storage-panel"><p>{{t('pendingResult')}}</p><button :disabled="busy||operationBusy" @click="recover">{{t('recover')}}</button></section>
 <template v-if="home">
  <MerchantStorageSettingsPage v-if="task==='settings'" :key="store" :store="store" :home="home" :api="api" :disabled="busy||pending||operationBusy" @back="backSettings" @command="run" @error="fail" @busy-change="operationBusy=$event"/>
  <template v-else>
  <nav class="storage-customer-tasks" :aria-label="t('sections')"><button v-for="view in (home?.newBusinessEnabled?['deposit','withdraw','inventory','records']:['withdraw','inventory','records'])" :key="view" :aria-label="t(view)" :aria-pressed="task===view||(view==='deposit'&&depositTask)" :class="{primary:task===view||(view==='deposit'&&depositTask)}" :disabled="busy||operationBusy" @click="open(view)">{{t(view+'Tab')}}</button></nav><p v-if="task!=='inbox'" class="storage-customer-hint">{{t(task==='inventory'?'inventoryHint':task==='withdraw'?(home.operationMode==='staff_registration'?'directWithdrawHint':'withdrawHint'):task==='records'?'recordsHint':'depositHint')}}</p>
  <nav v-if="depositTask&&home.newBusinessEnabled&&can('receive')" class="storage-deposit-views" :aria-label="t('depositSections')"><button :aria-label="t('directRegister')" :title="t('directRegister')" :class="{primary:task==='deposit'}" :aria-current="task==='deposit'?'page':undefined" :disabled="busy||operationBusy||pending" @click="open('deposit')">{{t('directRegisterTab')}}</button><button :aria-label="`${t('inbox')} (${pendingCount})`" :title="t('inbox')" :class="{primary:task==='inbox'}" :aria-current="task==='inbox'?'page':undefined" :disabled="busy||operationBusy||pending" @click="open('inbox')">{{t('inboxTab')}} <span class="cards-badge">{{pendingCount}}</span></button></nav>
  <MerchantStorageInventory v-if="task==='inventory'" :key="store" :api="api" :timezone="home.timezone" :refresh-key="inventoryRefresh"/>
  <template v-else>
  <button v-if="task==='withdraw'&&home.operationMode!=='staff_registration'&&can('deliver')" :disabled="busy||operationBusy||pending" @click="showScan=!showScan">{{t('scanCode')}}</button><WalletQrScanner v-if="task==='withdraw'&&home.operationMode!=='staff_registration'&&showScan" purpose="storage" :title="t('scanCode')" :privacy-message="t('scanPrivacy')" :manual-label="t('pasteCode')" :invalid-message="t('invalidCode')" :disabled="busy||pending" @code="identify"/>
<MerchantStorageInbox v-if="task==='inbox'&&home.newBusinessEnabled&&can('receive')" :key="store" :api="api" :home="home" :disabled="busy||pending||operationBusy" @changed="load();notice=$event" @error="fail" @busy-change="operationBusy=$event"/>
  <section v-if="home?.newBusinessEnabled&&task==='deposit'" class="storage-direct-registration"><CustomerQuickCreate v-if="addingCustomer&&canCreate" :key="store" :store="store" :keyword="query" :disabled="busy||pending" @created="createdCustomer" @cancel="addingCustomer=false" @lookup="lookupCustomer" @busy-change="operationBusy=$event"/><StorageCustomerPicker v-else v-model:query="query" :customer="customer" :results="results" :busy="busy||operationBusy" :searching="searching" :searched="searched" :can-create="canCreate" @create="addingCustomer=true" @search="search" @choose="choose"/><MerchantStorageDeposit v-if="scoped&&customer" :key="`${store}:${customer.id}`" :home="scoped" :api="api" :store="store" :customer-id="customer.id" :disabled="busy||pending||operationBusy" :can-receive="can('receive')" @saved="load();notice=t('received')" @error="fail" @busy-change="operationBusy=$event"/></section>
  <StorageScanResult v-if="task==='withdraw'&&scanResult" :intent="scanResult" :home="home" :disabled="busy||pending" @next="scanResult=null;showScan=true"/>
  <CustomerQuickCreate v-if="customerTask&&addingCustomer&&canCreate" :key="store" :store="store" :keyword="query" :disabled="busy||pending" @created="createdCustomer" @cancel="addingCustomer=false" @lookup="lookupCustomer" @busy-change="operationBusy=$event"/>
  <StorageCustomerPicker v-if="customerTask&&!addingCustomer&&!scanResult" v-model:query="query" :customer="customer" :results="results" :busy="busy||operationBusy" :searching="searching" :searched="searched" :can-create="canCreate" @create="addingCustomer=true" @search="search" @choose="choose"/>
  <template v-if="customerTask&&scoped&&customer&&!scanResult">
   <MerchantStorageWithdrawal v-if="task==='withdraw'" :key="`${store}:${customer.id}`" :home="scoped" :customer-id="customer.id" :api="api" :disabled="busy||pending" :can-deliver="can('deliver')" :active-intent="activeIntent" @created="activeIntent=$event;load()" @command="run" @refresh="load" @error="fail" @cleared="activeIntent=null" @busy-change="operationBusy=$event"/>
   <MerchantStorageRecords v-if="task==='records'" :key="`${store}:${customer.id}`" :home="scoped" :api="api" :disabled="busy||pending" :can-correct="can('correct')" :can-photo="can('receive')" @command="run" @error="fail" @changed="load"/>
  </template><p v-else-if="customerTask&&!scanResult" class="storage-panel storage-empty">{{t('noCustomer')}}</p>
  </template>
  </template>
 </template>
</main></template>
