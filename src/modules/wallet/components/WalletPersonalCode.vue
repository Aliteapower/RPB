<script setup lang="ts">
import { computed,ref,watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useCustomerWallet } from '../useCustomerWallet'
import { usePersonalCode } from '../usePersonalCode'
import { money,dateTime } from '../format'
import { customerWalletPath } from '../navigation'
import WalletQrDisplay from './WalletQrDisplay.vue'
import {chargePreview} from '../scanCharge'
const {t}=useI18n(),{store,home}=useCustomerWallet(),accepted=ref(false),limit=ref(''),editing=ref(false)
const enabled=computed(()=>home.value?.context.transactionsEnabled===true)
const {code,consent,receipt,needsConfirmation,busy,error,issue,accept}=usePersonalCode(store,enabled)
const selected=computed(()=>chargePreview(limit.value,0)?.totalCents)
watch(()=>consent.value?.policyVersion,()=>{accepted.value=false;editing.value=false;limit.value=consent.value?.consentedLimitCents==null?'':(consent.value.consentedLimitCents/100).toFixed(2)})
watch(limit,()=>accepted.value=false)
</script>
<template>
 <p v-if="error" class="wallet-error" role="alert">{{error}}</p>
 <section v-if="receipt" class="wallet-card" role="status"><strong>{{t(needsConfirmation?'wallet.waitConfirm':'wallet.paymentDone')}}</strong><p>{{money(receipt.amountCents)}}</p><p v-if="receipt.createdAt">{{dateTime(receipt.createdAt,home?.context.store.timezone)}}</p><RouterLink :to="customerWalletPath(receipt.storeId,`bills/${receipt.id}`)">{{t(needsConfirmation?'wallet.customerConfirm':'wallet.bills')}}</RouterLink></section>
 <section v-if="enabled" class="wallet-code-card">
  <p>{{home?.context.store.operatingEntityName}}</p>
  <WalletQrDisplay v-if="code" :value="code.code" :expires-at="code.expiresAt" /><p v-else role="status">{{t('wallet.loading')}}</p>
  <button class="wallet-primary wallet-block" :disabled="busy" @click="issue">{{t(code?'wallet.regenerate':'wallet.showMyCode')}}</button><small>{{t('wallet.personal.hint')}}</small>
 </section><p v-else class="wallet-empty">{{t('wallet.expired')}}</p>
 <section v-if="consent" class="wallet-card">
  <h3>{{t('wallet.personal.payment')}}</h3>
  <p v-if="!consent.configured">{{t('wallet.personal.unconfigured')}}</p>
  <p v-if="consent.paymentEnabled">{{t('wallet.personal.enabled',{amount:money(consent.consentedLimitCents??0)})}}</p>
  <p>{{t('wallet.personal.once')}}</p>
  <button v-if="consent.paymentEnabled&&!editing" :disabled="busy" @click="editing=true">{{t('wallet.personal.edit')}}</button>
  <form v-if="!consent.paymentEnabled||editing" class="wallet-form" @submit.prevent="accepted&&selected&&accept(true,selected)"><label>{{t('wallet.personal.customerLimit')}}<input v-model="limit" inputmode="decimal" maxlength="16" :disabled="busy" required></label><label class="wallet-check"><input v-model="accepted" type="checkbox" :disabled="busy||!selected">{{t('wallet.personal.agree',{amount:money(selected??0)})}}</label><button :disabled="busy||!accepted||!selected">{{t(consent.paymentEnabled?'wallet.personal.update':'wallet.personal.enable')}}</button></form>
  <button v-if="consent.paymentEnabled" :disabled="busy" @click="accept(false)">{{t('wallet.personal.disable')}}</button>
 </section>
</template>
