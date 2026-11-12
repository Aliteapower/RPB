<script setup lang="ts">
import {channelType,channelLabel,topupChannelName} from '../collectionMethods'
import { computed, reactive, watch, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { amountInput } from '../format'
import type { Channel, CollectionConfirm } from '../types'
import WalletPayNowProof from '../proof/WalletPayNowProof.vue'
import type {ProofContext} from '../proof/types'
const props = defineProps<{ store?:string;sourceType?:'topup'|'bill';sourceId?:string;amountCents: number; fixedChannel?: Channel; channels: Channel[]; busy: boolean; paynowConfigured: boolean; topup?: boolean; channelLabels?:Record<string,string> }>()
const emit = defineEmits<{ confirm: [value: CollectionConfirm] }>()
const { t } = useI18n()
const form = reactive<CollectionConfirm>({ channel: props.fixedChannel ?? props.channels[0] ?? 'cash', amount: amountInput(props.amountCents), collectionAccount: '', receiptReference: '', terminalCode: 'T1', note: '', verified: false })
const cashTopup = computed(() => props.topup === true && channelType(form.channel) === 'cash')
const proofEnabled=computed(()=>!!props.store&&!!props.sourceId&&!!props.sourceType&&channelType(form.channel)==='paynow')
const proofBlocked=ref(false)
let autoBankReference=''
function evidence(state:{proofId?:string;bankReference?:string;context:ProofContext|null;blocked:boolean}){
 if(autoBankReference&&(!state.proofId||state.bankReference!==autoBankReference)&&form.receiptReference===autoBankReference)form.receiptReference=''
 autoBankReference=state.bankReference??'';form.proofId=state.proofId;form.verified=false;proofBlocked.value=state.blocked
 if(state.bankReference)form.receiptReference=state.bankReference
 if(state.context&&!form.collectionAccount)form.collectionAccount=state.context.expectedAccount
}
watch(()=>[props.store,props.sourceId],()=>{form.collectionAccount='';form.receiptReference='';autoBankReference=''})
watch(()=>[form.channel,form.amount,props.sourceId],()=>{delete form.proofId;proofBlocked.value=proofEnabled.value;form.verified=false})
function submit(){if(!props.busy&&!proofBlocked.value&&form.verified&&(cashTopup.value||form.receiptReference)&&!(channelType(form.channel)==='paynow'&&!props.paynowConfigured))emit('confirm',{...form})}
watch(() => [props.amountCents, props.fixedChannel], () => { form.amount = amountInput(props.amountCents); form.channel = props.fixedChannel ?? props.channels[0] ?? 'cash'; form.verified = false; form.receiptReference = '' })
watch(()=>props.channels.join('|'),()=>{if(!props.channels.includes(form.channel)){form.channel=props.fixedChannel??props.channels[0]??'';form.verified=false;form.receiptReference='';form.collectionAccount=''}})
watch(()=>[form.receiptReference,form.collectionAccount],()=>{form.verified=false})
watch(()=>form.channel,()=>{form.verified=false;form.receiptReference='';form.collectionAccount=''})
</script>
<template>
  <form class="wallet-form wallet-verification" @submit.prevent="submit">
    <p>{{ t('wallet.offline') }}</p>
    <label>{{ t('wallet.channel') }}<select v-model="form.channel" :disabled="!!fixedChannel || busy"><option v-for="channel in channels" :key="channel" :value="channel" :disabled="channelType(channel) === 'paynow' && !paynowConfigured">{{ channelLabel(t,channel,channelLabels) }}</option></select></label>
    <label>{{ t('wallet.amount') }}<input v-model="form.amount" inputmode="decimal" type="number" min="0.01" step="0.01" :max="amountInput(amountCents)" :readonly="topup" required></label>
    <WalletPayNowProof v-if="proofEnabled" :store="store!" :source-type="sourceType!" :source-id="sourceId!" :channel="form.channel" :amount="form.amount" :disabled="busy" @change="evidence" />
    <label>{{ t(channelType(form.channel)==='paynow'?'wallet.proof.bankReference':cashTopup ? 'wallet.cashTopupReceipt' : 'wallet.receipt') }}<input v-model.trim="form.receiptReference" :required="!cashTopup" maxlength="160" autocomplete="off"></label>
    <details v-if="proofEnabled"><summary>{{t('wallet.proof.more')}}</summary><label>{{ t('wallet.terminal') }}<input v-model.trim="form.terminalCode" required maxlength="50"></label></details><template v-else><label>{{ t('wallet.terminal') }}<input v-model.trim="form.terminalCode" required maxlength="50"></label></template>
    <label v-if="['credit_card','other'].includes(channelType(form.channel))">{{ t(channelType(form.channel)==='other'?'wallet.operation.otherCollectionAccount':'wallet.collectionAccount') }}<input v-model.trim="form.collectionAccount" required maxlength="100" autocomplete="off"></label>
    <label v-if="channelType(form.channel) === 'paynow'">{{ t('wallet.proof.actualAccount') }}<input v-model.trim="form.collectionAccount" required maxlength="100" autocomplete="off"></label>
    <label>{{ t('wallet.note') }}<textarea v-model="form.note" maxlength="500" /></label>
    <label class="wallet-check"><input v-model="form.verified" type="checkbox" required>{{ t(cashTopup ? 'wallet.cashTopupAttested' : 'wallet.attested') }}</label>
    <button :disabled="busy || proofBlocked || !form.verified || (!cashTopup && !form.receiptReference) || (channelType(form.channel) === 'paynow' && !paynowConfigured)">{{ t(topup ? 'wallet.confirmCollection' : 'wallet.confirmSupplement') }}</button>
  </form>
</template>
