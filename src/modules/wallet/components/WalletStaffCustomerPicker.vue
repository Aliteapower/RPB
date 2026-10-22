<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { useStaffWallet } from '../useStaffWallet'
import { money } from '../format'
import WalletQrScanner from './WalletQrScanner.vue'
import WalletIcon from './WalletIcon.vue'
const props = withDefaults(defineProps<{ allowScan?: boolean }>(), { allowScan: true })
const { t } = useI18n(), route = useRoute(), workspace = useStaffWallet()
const { customer, account, customerCode, customers, busy, clear, choose, search } = workspace
const query = ref(''), scanning = ref(false), searched = ref(false)
watch(() => route.query.scan, value => { scanning.value = props.allowScan && value === '1'; if(scanning.value)clear() }, { immediate: true })
async function lookup() { searched.value = true; await search(query.value) }
async function scan(code: string) { await workspace.scan(code); if (customerCode.value === code) scanning.value = false }
</script>
<template>
  <section v-if="customer" class="wallet-customer-chip">
    <div class="wallet-avatar"><WalletIcon name="user" /></div><div><strong>{{ customer.displayName || customer.customerCode }}</strong><small>{{ account ? `${t('wallet.available')} ${money(account.balanceCents-account.reservedCents)}` : t('wallet.ui.noAccount') }}</small></div>
    <button class="wallet-link-button" :disabled="busy" @click="clear(); searched=false">{{ t('wallet.ui.changeCustomer') }}</button>
    <button v-if="allowScan && customerCode" class="wallet-link-button" :disabled="busy" @click="clear();scanning=true;searched=false">{{ t('wallet.ui.rescan') }}</button>
    <small v-if="customerCode" class="wallet-scan-note">{{ t('wallet.ui.scannedIdentity') }}</small>
  </section>
  <section v-else class="wallet-card">
    <button v-if="allowScan" class="wallet-primary wallet-block" :disabled="busy" @click="scanning=!scanning"><WalletIcon name="scan" />{{ t('wallet.scanCustomer') }}</button>
    <WalletQrScanner v-if="scanning && allowScan" purpose="customer" auto-start :disabled="busy" @code="scan" />
    <form class="wallet-search" @submit.prevent="lookup"><input v-model.trim="query" :aria-label="t('wallet.customer')" :placeholder="t('wallet.search')" maxlength="160"><button :disabled="busy">{{ t('wallet.choose') }}</button></form>
    <div class="wallet-customer-results"><button v-for="c in customers" :key="c.id" :disabled="busy" @click="choose(c)"><strong>{{ c.displayName || c.customerCode }}</strong><small>{{ c.email || c.phone || c.customerCode }}</small></button></div>
    <p v-if="searched && !busy && !customers.length" class="wallet-muted">{{ t('wallet.empty') }}</p><small>{{ t('wallet.authenticatedOnly') }}</small>
  </section>
</template>
