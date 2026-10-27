<script setup lang="ts">
import CustomerAccessLinks from '../../customers/CustomerAccessLinks.vue'
import {channelType,channelLabel,topupChannelName} from '../collectionMethods'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { staffWalletPath } from '../navigation'
import type { Context } from '../types'
import WalletIcon from './WalletIcon.vue'
const props = defineProps<{ store: string; context: Context; allowed: boolean }>()
const { t } = useI18n()
const channels = computed(() => props.context.program
  ? props.context.program.channels.map(c => channelLabel(t,c,props.context.program?.channelLabels)).join(' / ')
  : t('wallet.settingsUi.unopened'))
</script>
<template>
  <section class="wallet-page-heading"><h2>{{ t('wallet.settings') }}</h2><p>{{ t('wallet.settingsUi.hint') }}</p></section>
  <nav class="wallet-settings-directory" :aria-label="t('wallet.settings')">
    <template v-if="allowed">
      <RouterLink :to="staffWalletPath(store,'settings/channels')" class="wallet-card wallet-setting-link">
        <WalletIcon name="wallet" /><div><h3>{{ t('wallet.settingsUi.channels') }}</h3><p>{{ t('wallet.settingsUi.channelsHint') }}</p><span>{{ channels }}</span></div><WalletIcon name="arrow" :size="18" />
      </RouterLink>
      <RouterLink :to="staffWalletPath(store,'settings/paynow')" class="wallet-card wallet-setting-link">
        <WalletIcon name="qr" /><div><h3>{{ t('wallet.settingsUi.paynow') }}</h3><p>{{ t('wallet.settingsUi.paynowHint') }}</p><span>{{ t(context.paynowConfigured?'wallet.settingsUi.configured':'wallet.settingsUi.unconfigured') }}</span></div><WalletIcon name="arrow" :size="18" />
      </RouterLink>
    </template>
    <RouterLink :to="`/stores/${store}/admin/profile`" class="wallet-card wallet-setting-link">
      <WalletIcon name="settings" /><div><h3>{{ t('wallet.settingsUi.profile') }}</h3><p>{{ t('wallet.settingsUi.profileHint') }}</p><span>{{ t('wallet.issuer') }}：{{ context.store.operatingEntityName || '-' }}</span></div><WalletIcon name="arrow" :size="18" />
    </RouterLink>
  </nav>
  <CustomerAccessLinks :store="store" source="wallet-settings"/>
  <p v-if="!allowed" class="wallet-empty">{{ t('wallet.noRights') }}</p>
</template>
<style scoped>
.wallet-settings-directory{display:grid;gap:12px}.wallet-setting-link{display:flex;align-items:center;gap:16px;margin:0}.wallet-setting-link>svg{flex:none;color:var(--wallet-blue)}.wallet-setting-link>div{flex:1;min-width:0}.wallet-setting-link h3{margin:0 0 6px}.wallet-setting-link p{margin:0 0 8px;color:var(--wallet-muted);font-size:14px}.wallet-setting-link span{font-size:13px;color:#34537c;overflow-wrap:anywhere}.wallet-setting-link:hover{border-color:#9ec8ff}
@media(max-width:480px){.wallet-setting-link{gap:12px;padding:18px}.wallet-setting-link h3{font-size:17px}}
</style>
