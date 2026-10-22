<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { walletApi } from '../api'
import { staffWalletPath } from '../navigation'
import { useStaffWallet } from '../useStaffWallet'
import { useWalletTask } from '../useWalletTask'
import type { Channel, Topup } from '../types'
import WalletStaffCustomerPicker from '../components/WalletStaffCustomerPicker.vue'
const { t } = useI18n(), router = useRouter(), { store, account, context, can } = useStaffWallet()
const { busy, error, run } = useWalletTask(), amount = ref(''), channel = ref<Channel>('cash')
watch(() => context.value?.program?.channels, channels => { if (channels?.length && !channels.includes(channel.value)) channel.value = channels[0]! }, { immediate: true })
async function create() { if (account.value) await run(async () => { const result = await walletApi(store.value).command<Topup>('/topups',{accountId:account.value!.id,amount:amount.value,channel:channel.value}); amount.value=''; await router.push(staffWalletPath(store.value,`topups/${result.id}`)) }) }
</script>
<template><section class="wallet-page-heading"><h2>{{ t('wallet.ui.customerTopup') }}</h2><p>{{ t('wallet.offline') }}</p></section>
  <template v-if="can('wallet.topup.create') && context?.transactionsEnabled && context.program"><WalletStaffCustomerPicker :allow-scan="false" />
    <form v-if="account" class="wallet-amount-card" @submit.prevent="create"><label>{{ t('wallet.amount') }}<span class="wallet-amount-input"><span>S$</span><input v-model="amount" :aria-label="t('wallet.amount')" type="number" min="0.01" step="0.01" inputmode="decimal" placeholder="0.00" required></span></label>
      <fieldset class="wallet-channel-picker"><legend>{{ t('wallet.channel') }}</legend><label v-for="c in context.program.channels" :key="c"><input v-model="channel" type="radio" :value="c" :disabled="c==='paynow' && !context.paynowConfigured">{{ t(`wallet.channels.${c}`) }}</label></fieldset>
      <small>{{ t('wallet.ui.topupNotCredited') }}</small><p v-if="error" class="wallet-error" role="alert">{{ error }}</p><button class="wallet-primary wallet-block" :disabled="busy || (channel==='paynow' && !context.paynowConfigured)">{{ t('wallet.ui.nextConfirm') }}</button>
    </form></template><p v-else class="wallet-empty">{{ t('wallet.noRights') }}</p>
</template>
