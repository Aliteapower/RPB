<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { walletApi } from '../api'
import { createScanChargeAttempt, isChargeCode } from '../scanCharge'
import { amountInput, remaining } from '../format'
import { useWalletTask } from '../useWalletTask'
import type { Account, Bill, Customer } from '../types'
import WalletQrScanner from './WalletQrScanner.vue'
import WalletChargePreview from './WalletChargePreview.vue'
const props=defineProps<{store:string;bill:Bill}>(),emit=defineEmits<{paid:[bill:Bill]}>(),{t}=useI18n()
const {busy,error,run}=useWalletTask(),selected=ref<{account:Account;customer:Customer;code:string}|null>(null)
const confirmCharge=createScanChargeAttempt()
const debit=computed(()=>selected.value?Math.min(selected.value.account.balanceCents-selected.value.account.reservedCents,remaining(props.bill)):0)
async function scan(code:string) {
  selected.value=null
  await run(async()=>{
    if(!isChargeCode(code))throw new Error(t('wallet.scanCharge.requireNew'))
    const result=await walletApi(props.store).post<{account:Account;customer:Customer}>('/payment-codes/resolve',{code})
    if(result.account.id!==props.bill.accountId)throw new Error(t('wallet.scanCharge.wrongCustomer'))
    selected.value={...result,code}
  })
}
async function confirm(){const current=selected.value;if(!current)return;await run(async()=>{const result=await confirmCharge(props.store,current.account,amountInput(props.bill.amountCents),current.code,props.bill);selected.value=null;emit('paid',result.bill)})}
</script>
<template><section class="wallet-card"><h3>{{ t('wallet.scanCharge.existing') }}</h3><p v-if="error" class="wallet-error" role="alert">{{ error }}</p><template v-if="selected"><p>{{ selected.customer.displayName || selected.customer.customerCode }}</p><WalletChargePreview :debit-cents="debit" :due-cents="remaining(bill)-debit" /><button class="wallet-primary wallet-block" :disabled="busy" @click="confirm">{{ t('wallet.scanCharge.confirm') }}</button><button :disabled="busy" @click="selected=null">{{ t('wallet.ui.rescan') }}</button></template><WalletQrScanner v-else purpose="customer" auto-start :disabled="busy" @code="scan" /></section></template>
