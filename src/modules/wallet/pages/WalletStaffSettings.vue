<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useStaffWallet } from '../useStaffWallet'
import { staffWalletPath } from '../navigation'
import WalletSettingsPanel from '../components/WalletSettingsPanel.vue'
import WalletChannelSettings from '../components/WalletChannelSettings.vue'
import WalletPayNowSettings from '../components/WalletPayNowSettings.vue'
const props = defineProps<{ section: 'overview'|'channels'|'paynow' }>()
const { t } = useI18n(), { store, context, can, refresh, run } = useStaffWallet()
const saved = ref(false)
watch(() => props.section, () => { saved.value = false })
async function changed() { await run(async () => { await refresh(); saved.value = true }) }
</script>
<template><template v-if="context">
  <WalletSettingsPanel v-if="section==='overview'" :store="store" :context="context" :allowed="can('wallet.settings.manage')" />
  <template v-else-if="can('wallet.settings.manage')">
    <section class="wallet-page-heading"><h2>{{ t(`wallet.settingsUi.${section}`) }}</h2><p>{{ t(section==='channels'?'wallet.settingsUi.issuerScope':'wallet.settingsUi.storeScope') }}</p></section>
    <p v-if="saved" class="wallet-settings-saved" role="status">{{ t('wallet.settingsUi.saved') }}</p>
    <WalletChannelSettings v-if="section==='channels'" :store="store" :context="context" @changed="changed" />
    <WalletPayNowSettings v-else :store="store" :enabled="!context.denialReason" @changed="changed" />
    <RouterLink class="wallet-link-button" :to="staffWalletPath(store,'settings')">{{ t('wallet.settingsUi.back') }}</RouterLink>
  </template>
  <p v-else class="wallet-empty">{{ t('wallet.noRights') }}</p>
</template></template>
<style scoped>.wallet-settings-saved{padding:12px 16px;background:#e8f6ef;border:1px solid #bce3cb;border-radius:12px;color:#146a45}</style>
