<script setup lang="ts">
import ProductPageHeader from '../../components/common/ProductPageHeader.vue'
import {ref,watch} from 'vue'
import type {Item} from './types'
import {useCustomerStorage} from './customer/useCustomerStorage'
import {useCustomerStorageText} from './customer/text'
import CustomerStorageWithdrawal from './customer/CustomerStorageWithdrawal.vue'
import CustomerStorageRecords from './customer/CustomerStorageRecords.vue'
import StorageApplicationForm from './StorageApplicationForm.vue'
import WalletQrScanner from '../wallet/components/WalletQrScanner.vue'
import WalletCustomerLogin from '../wallet/components/WalletCustomerLogin.vue'
import '../wallet/wallet.css'
import './storage.css'
import './customer/customer.css'
defineOptions({inheritAttrs:false})
const {t}=useCustomerStorageText(),{store,api,home,loading,busy,login,pending,error,notice,view,activeIntent,scannedCode,open,load,run,scan,recover,created,quickCreated,applied,fail}=useCustomerStorage()
const childBusy=ref(false),showScan=ref(false),again=ref<{item:Item;intentId:string;quantity:number}|null>(null)
watch(store,()=>{childBusy.value=false;showScan.value=false;again.value=null})
function deposit(value?:{item:Item;intentId:string;quantity:number}){again.value=value??null;open('deposit')}
</script>
<template><main class="cards-page storage-page storage-customer-page" data-storage-role="customer">
 <ProductPageHeader :title="t('title')" :caption="home?.storeName" :back-to="`/wallet/${store}`" :back-label="t('back')" :refresh-label="t('refresh')" :refresh-disabled="loading||busy||childBusy" @refresh="load"/>
 <p v-if="loading&&!home" role="status">{{t('loading')}}</p><p v-if="error" role="alert" class="storage-error">{{error}}</p><p v-if="notice" role="status" class="storage-notice">{{notice}}</p>
 <WalletCustomerLogin v-if="login" :store="store" @authenticated="load"/>
 <section v-if="pending" class="storage-panel"><p>{{t('pendingHint')}}</p><button :disabled="busy||childBusy" @click="recover">{{t('recover')}}</button></section>
 <template v-if="home">
  <nav class="storage-customer-tasks" :aria-label="t('sections')"><button v-for="task in ['deposit','withdraw','records'] as const" :key="task" :aria-pressed="view===task" :class="{primary:view===task}" :disabled="busy||childBusy" @click="open(task)">{{t(task==='withdraw'?'withdrawTask':task)}}</button></nav>
  <p v-if="home.operationMode!=='staff_registration'||view==='records'" class="storage-customer-hint">{{t(view==='deposit'?'depositHint':view==='withdraw'?'withdrawHint':'recordsHint')}}</p>
  <p v-if="home.operationMode==='staff_registration'&&view!=='records'" class="storage-notice">{{t('staffModeHint')}}</p>
  <template v-if="view==='deposit'&&home.operationMode!=='staff_registration'"><p v-if="!home.newBusinessEnabled" class="storage-notice">{{t('closed')}}</p><StorageApplicationForm v-else :key="`${store}:${again?.intentId??'new'}`" :api="api" :disabled="busy||pending" :again="again" @saved="applied" @error="fail" @busy-change="childBusy=$event"/><details class="storage-panel"><summary>{{t('rulesTitle')}}</summary><p>{{t('expiryHint')}}</p><p v-for="rule in home.rules" :key="rule.id">{{rule.name}} · {{rule.months}} {{t('months')}}</p></details></template>
  <template v-if="view==='withdraw'&&home.operationMode!=='staff_registration'"><CustomerStorageWithdrawal :key="store" :api="api" :items="home.items" :active-intent="activeIntent" :disabled="busy||pending" @quick-created="quickCreated" @cancel="run(()=>api.command(`/intents/${activeIntent!.id}/cancel`))" @created="created" @error="fail" @deposit="deposit()" @scan="showScan=!showScan" @busy-change="childBusy=$event"/><WalletQrScanner v-if="showScan" purpose="storage" :title="t('scan')" :privacy-message="t('scanPrivacy')" :manual-label="t('pasteCode')" :invalid-message="t('invalidCode')" :disabled="busy||pending" @code="code=>run(()=>scan(code))"/></template>
  <CustomerStorageRecords v-if="view==='records'" :home="home" :api="api" :disabled="busy||pending" :active-intent="activeIntent" :scanned-code="scannedCode" @command="run" @deposit="deposit()" @again="deposit" @cleared="activeIntent=null"/>
 </template>
</main></template>
