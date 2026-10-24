<script setup lang="ts">
import {channelType,channelLabel,topupChannelName} from '../collectionMethods'
import { computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { amountInput } from '../format'
import type { Channel, CollectionConfirm } from '../types'
const props = defineProps<{ amountCents: number; fixedChannel?: Channel; channels: Channel[]; busy: boolean; paynowConfigured: boolean; topup?: boolean; channelLabels?:Record<string,string> }>()
const emit = defineEmits<{ confirm: [value: CollectionConfirm] }>()
const { t } = useI18n()
const form = reactive<CollectionConfirm>({ channel: props.fixedChannel ?? props.channels[0] ?? 'cash', amount: amountInput(props.amountCents), collectionAccount: '', receiptReference: '', terminalCode: 'T1', note: '', verified: false })
const cashTopup = computed(() => props.topup === true && channelType(form.channel) === 'cash')
watch(() => [props.amountCents, props.fixedChannel], () => { form.amount = amountInput(props.amountCents); form.channel = props.fixedChannel ?? props.channels[0] ?? 'cash'; form.verified = false; form.receiptReference = '' })
watch(()=>props.channels.join('|'),()=>{if(!props.channels.includes(form.channel)){form.channel=props.fixedChannel??props.channels[0]??'';form.verified=false;form.receiptReference='';form.collectionAccount=''}})
watch(()=>form.channel,()=>{form.verified=false;form.receiptReference='';form.collectionAccount=''})
</script>
<template>
  <form class="wallet-form wallet-verification" @submit.prevent="emit('confirm', { ...form })">
    <p>{{ t('wallet.offline') }}</p>
    <label>{{ t('wallet.channel') }}<select v-model="form.channel" :disabled="!!fixedChannel || busy"><option v-for="channel in channels" :key="channel" :value="channel" :disabled="channelType(channel) === 'paynow' && !paynowConfigured">{{ channelLabel(t,channel,channelLabels) }}</option></select></label>
    <label>{{ t('wallet.amount') }}<input v-model="form.amount" inputmode="decimal" type="number" min="0.01" step="0.01" :max="amountInput(amountCents)" :readonly="topup" required></label>
    <label>{{ t(cashTopup ? 'wallet.cashTopupReceipt' : 'wallet.receipt') }}<input v-model.trim="form.receiptReference" :required="!cashTopup" maxlength="160" autocomplete="off"></label>
    <label>{{ t('wallet.terminal') }}<input v-model.trim="form.terminalCode" required maxlength="50"></label>
    <label v-if="['credit_card','other'].includes(channelType(form.channel))">{{ t(channelType(form.channel)==='other'?'wallet.operation.otherCollectionAccount':'wallet.collectionAccount') }}<input v-model.trim="form.collectionAccount" required maxlength="100" autocomplete="off"></label>
    <label v-if="channelType(form.channel) === 'paynow'">{{ t('wallet.paynowActualAccount') }}<input v-model.trim="form.collectionAccount" required maxlength="100" autocomplete="off"></label>
    <label>{{ t('wallet.note') }}<textarea v-model="form.note" maxlength="500" /></label>
    <label class="wallet-check"><input v-model="form.verified" type="checkbox" required>{{ t(cashTopup ? 'wallet.cashTopupAttested' : 'wallet.attested') }}</label>
    <button :disabled="busy || !form.verified || (!cashTopup && !form.receiptReference) || (channelType(form.channel) === 'paynow' && !paynowConfigured)">{{ t(topup ? 'wallet.confirmCollection' : 'wallet.confirmSupplement') }}</button>
  </form>
</template>
