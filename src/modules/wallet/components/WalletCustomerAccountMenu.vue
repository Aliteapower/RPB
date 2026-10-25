<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import CustomerPasswordChange from '../../../components/customer-auth/CustomerPasswordChange.vue'
import WalletIcon from './WalletIcon.vue'

const props = defineProps<{ store: string; storeName: string; loggingOut: boolean; error: string }>()
const emit = defineEmits<{ logout: [] }>()
const { t } = useI18n()
const open = ref(false)
const changingPassword = ref(false)
const dialog = ref<HTMLDialogElement>()
async function show() {
  open.value = true
  await nextTick()
  dialog.value?.showModal()
}
function close() {
  if (changingPassword.value || props.loggingOut) return
  dialog.value?.close()
  open.value = false
}
watch(() => props.store, () => {
  dialog.value?.close()
  open.value = false
  changingPassword.value = false
})
</script>
<template>
  <div class="wallet-header-title">
    <button type="button" class="wallet-account-trigger" aria-haspopup="dialog" :aria-expanded="open" :aria-label="t('wallet.accountUi.open')" @click="show">
      <span class="wallet-account-caption"><span role="heading" aria-level="1" class="wallet-account-title">{{ t('wallet.customerWallet') }}</span><small>{{ storeName }}</small></span>
      <WalletIcon name="arrow" :size="16" />
    </button>
    <dialog v-if="open" ref="dialog" class="wallet-account-dialog" aria-modal="true" :aria-label="t('wallet.accountUi.title')" @cancel.prevent="close">
      <header class="wallet-account-dialog-heading"><h2>{{ t('wallet.accountUi.title') }}</h2><button type="button" class="wallet-icon-button" :aria-label="t('wallet.close')" :disabled="changingPassword || loggingOut" autofocus @click="close">×</button></header>
      <p class="wallet-account-store">{{ storeName }}</p>
      <CustomerPasswordChange :key="store" :disabled="loggingOut" @busy-change="changingPassword = $event">
        <template #actions><button type="button" class="wallet-account-logout" :disabled="loggingOut" @click="emit('logout')">{{ t(loggingOut ? 'wallet.operation.loggingOut' : 'wallet.operation.logout') }}</button></template>
      </CustomerPasswordChange>
      <p v-if="error" class="wallet-error" role="alert">{{ error }}</p>
    </dialog>
  </div>
</template>
<style scoped>
.wallet-shell .wallet-account-trigger{display:flex;align-items:center;justify-content:flex-start;gap:12px;max-width:100%;padding:0;border:0;background:transparent;color:inherit;text-align:start;box-shadow:none}
.wallet-account-caption{min-width:0;overflow-wrap:anywhere}
.wallet-account-title{display:block;font-size:19px;font-weight:650;line-height:1.4}
.wallet-account-trigger svg{flex:none;color:var(--wallet-muted)}
.wallet-account-dialog{width:min(480px,calc(100% - 32px));max-height:calc(100dvh - 48px);margin:auto;padding:24px;border:1px solid var(--wallet-line);border-radius:20px;color:var(--wallet-ink);background:#f5f7fb;overflow-y:auto;box-shadow:0 20px 80px #16243b33;overscroll-behavior:contain}
.wallet-account-dialog::backdrop{background:#16243b66}
.wallet-account-dialog-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}
.wallet-account-dialog-heading h2{font-size:21px;margin:0}
.wallet-account-store{margin:4px 0 20px;color:var(--wallet-muted);overflow-wrap:anywhere}
.wallet-shell .wallet-account-logout{color:#b42318}
.wallet-account-dialog :deep(.customer-password-change){margin-top:0}
@media(max-width:480px){.wallet-account-dialog{padding:18px}.wallet-account-dialog :deep(.customer-account-actions){align-items:stretch;flex-direction:column}.wallet-account-dialog :deep(.customer-account-actions button){width:100%}}
</style>
