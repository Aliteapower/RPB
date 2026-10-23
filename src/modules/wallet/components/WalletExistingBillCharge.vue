<script setup lang="ts">
import { computed, ref, toRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { walletApi, WalletApiError } from '../api'
import { createScanChargeAttempt, isChargeCode } from '../scanCharge'
import { amountInput, remaining } from '../format'
import { useWalletTask } from '../useWalletTask'
import type { Account, Bill, Customer } from '../types'
import { useChargeComponents } from '../useChargeComponents'
import WalletAmountSplit from './WalletAmountSplit.vue'
import WalletQrScanner from './WalletQrScanner.vue'
import WalletChargePreview from './WalletChargePreview.vue'
const props=defineProps<{store:string;bill:Bill}>(),emit=defineEmits<{paid:[bill:Bill]}>(),{t}=useI18n()
const {busy,error,run}=useWalletTask(),selected=ref<{account:Account;customer:Customer;code:string}|null>(null)
const confirmCharge=createScanChargeAttempt()
const allocation=useChargeComponents(toRef(props,'store'),computed(()=>selected.value?.account??null),computed(()=>amountInput(remaining(props.bill))),computed(()=>selected.value?.code??''),computed(()=>!!selected.value),toRef(props,'bill'))
const {components,loading:previewLoading,error:previewError}=allocation
let generation=0
watch(()=>[props.store,props.bill.id,props.bill.version],()=>{generation++;selected.value=null})
async function scan(code:string) {
  if(busy.value)return
  selected.value=null;const current=++generation,id=props.store,billId=props.bill.id
  await run(async()=>{
    if(!isChargeCode(code))throw new Error(t('wallet.scanCharge.requireNew'))
    const result=await walletApi(id).post<{account:Account;customer:Customer}>('/payment-codes/resolve',{code})
    if(current!==generation||id!==props.store||billId!==props.bill.id)return
    if(result.account.id!==props.bill.accountId)throw new Error(t('wallet.scanCharge.wrongCustomer'))
    selected.value={...result,code}
  })
}
async function confirm(){const current=selected.value;if(!current||!components.value)return;const version=generation,reviewed={...current.account,version:components.value.accountVersion};await run(async()=>{try{const result=await confirmCharge(props.store,reviewed,amountInput(props.bill.amountCents),current.code,props.bill);if(version!==generation)return;selected.value=null;emit('paid',result.bill)}catch(e){if(e instanceof WalletApiError&&e.status>=400&&e.status<500)allocation.clear();throw e}})}
</script>
<template><section class="wallet-card"><h3>{{ t('wallet.scanCharge.existing') }}</h3><p v-if="error" class="wallet-error" role="alert">{{ error }}</p><template v-if="selected"><p>{{ selected.customer.displayName || selected.customer.customerCode }}</p><WalletChargePreview v-if="components" :debit-cents="components.principalCents+components.bonusCents" :due-cents="remaining(bill)-components.principalCents-components.bonusCents" /><WalletAmountSplit v-if="components" :principal="components.principalCents" :bonus="components.bonusCents" /><p v-if="previewLoading" role="status">{{t('wallet.loading')}}</p><p v-if="previewError" class="wallet-error" role="alert">{{previewError}}</p><button v-if="!components" :disabled="busy||previewLoading" @click="allocation.refresh">{{t('wallet.refresh')}}</button><button class="wallet-primary wallet-block" :disabled="busy||!components||previewLoading" @click="confirm">{{ t('wallet.scanCharge.confirm') }}</button><button :disabled="busy" @click="selected=null">{{ t('wallet.ui.rescan') }}</button></template><WalletQrScanner v-else purpose="customer" auto-start :disabled="busy" @code="scan" /></section></template>
