<script setup lang="ts">
import {channelType,channelLabel,topupChannelName} from '../collectionMethods'
import { computed, ref, watch } from 'vue'
import { useRouter,useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { walletApi, WalletApiError } from '../api'
import { staffWalletPath } from '../navigation'
import { useStaffWallet } from '../useStaffWallet'
import { useWalletTask } from '../useWalletTask'
import type { Channel, Topup } from '../types'
import { useTopupOffers } from '../useTopupOffers'
import WalletTopupOffers from '../components/WalletTopupOffers.vue'
import WalletStaffCustomerPicker from '../components/WalletStaffCustomerPicker.vue'
defineProps<{embedded?:boolean}>()
const emit=defineEmits<{'busy-change':[busy:boolean]}>()
const { t } = useI18n(), router = useRouter(), route=useRoute(), { store, account, context, can } = useStaffWallet()
const offer=useTopupOffers(store,false,computed(()=>account.value?.id)),{offers,selected,quote,expired}=offer
const { busy, error, run } = useWalletTask(), amount = ref(''), channel = ref<Channel>('cash')
const effectiveChannel=computed(()=>offers.value?.campaignId?offer.channel.value:channel.value)
const canCreate=computed(()=>can('wallet.topup.create') && context.value?.transactionsEnabled && !!account.value && !!offers.value
  && (offers.value.campaignId?offers.value.offers.some(o=>o.id===selected.value):Number(amount.value)>0)
  && (offers.value.campaignId?offers.value.channels:context.value?.program?.channels??[]).includes(effectiveChannel.value as Channel)
  && (channelType(effectiveChannel.value)!=='paynow'||context.value?.paynowConfigured))
watch([busy,offer.busy],()=>emit('busy-change',busy.value||offer.busy.value),{immediate:true,flush:'sync'})
watch(() => context.value?.program?.channels, channels => { if (channels?.length && !channels.includes(channel.value)) channel.value = channels[0]! }, { immediate: true })
async function create() {
  if (!canCreate.value || busy.value || offer.busy.value || !account.value || !offers.value) return
  const id=store.value,accountId=account.value.id,campaignId=offers.value.campaignId,choice=selected.value,method=effectiveChannel.value
  await run(async () => {
    // An uncertain command retry reuses the quote and therefore the existing idempotency key.
    if(campaignId && (!quote.value || expired.value))await offer.getQuote()
    if(id!==store.value || accountId!==account.value?.id || !canCreate.value || campaignId!==offers.value?.campaignId
      || choice!==selected.value || method!==effectiveChannel.value || (campaignId && (!quote.value || expired.value)))return
    try {
      const result = await walletApi(id).command<Topup>('/topups',campaignId?{accountId,quoteId:quote.value!.quoteId}:{accountId,amount:String(amount.value),channel:method})
      if(id!==store.value || accountId!==account.value?.id)return
      quote.value=null
      amount.value='';await router.push({path:staffWalletPath(id,`topups/${result.id}`),query:route.query.entry==='phone'?{entry:'phone',kind:'money'}:{kind:'money'}})
    } catch(e) {
      if(e instanceof WalletApiError&&['WALLET_OFFER_CHANGED','WALLET_TOPUP_QUOTE_EXPIRED','WALLET_TOPUP_OFFER_REQUIRED'].includes(e.code))quote.value=null
      throw e
    }
  })
}
</script>
<template><section v-if="!embedded" class="wallet-page-heading"><h2>{{ t('wallet.ui.customerTopup') }}</h2><p>{{ t('wallet.offline') }}</p></section>
  <template v-if="can('wallet.topup.create') && context?.transactionsEnabled && context.program"><WalletStaffCustomerPicker v-if="!embedded" scan-purpose="topup" :disabled="busy||offer.busy.value" />
    <form v-if="account" class="wallet-amount-card" @submit.prevent="create"><template v-if="offers && !offers.campaignId"><label>{{ t('wallet.amount') }}<span class="wallet-amount-input"><span>S$</span><input v-model="amount" :aria-label="t('wallet.amount')" type="number" min="0.01" step="0.01" inputmode="decimal" placeholder="0.00" required></span></label>
      <fieldset class="wallet-channel-picker" :disabled="busy"><legend>{{ t('wallet.channel') }}</legend><label v-for="c in context.program.channels" :key="c"><input v-model="channel" type="radio" :value="c" :disabled="channelType(c)==='paynow' && !context.paynowConfigured">{{ channelLabel(t,c,context.program.channelLabels) }}</label></fieldset></template><WalletTopupOffers v-else v-model:selected="selected" v-model:channel="offer.channel.value" :offers="offers" :quote="quote" :expired="expired" :busy="busy||offer.busy.value" :timezone="context.store.timezone" :paynow-configured="context.paynowConfigured" confirm-only /><p v-if="offer.error.value" class="wallet-error" role="alert">{{offer.error.value}}</p><button type="button" :disabled="busy||offer.busy.value" @click="offer.refresh">{{t('wallet.refresh')}}</button>
      <small>{{ t('wallet.ui.topupNotCredited') }}</small><p v-if="error" class="wallet-error" role="alert">{{ error }}</p><button class="wallet-primary wallet-block" :disabled="busy || offer.busy.value || !canCreate">{{ t('wallet.operation.confirmTopup') }}</button>
    </form></template><p v-else class="wallet-empty">{{ t('wallet.noRights') }}</p>
</template>
