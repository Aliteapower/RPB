<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useCustomerWallet } from '../useCustomerWallet'
import { customerWalletPath } from '../navigation'
import { money } from '../format'
import WalletCustomerPointsSummary from '../components/WalletCustomerPointsSummary.vue'
import WalletAmountSplit from '../components/WalletAmountSplit.vue'
import WalletIcon from '../components/WalletIcon.vue'
const {t}=useI18n(),{store,home,account}=useCustomerWallet()
</script>
<template><template v-if="home"><section class="wallet-balance-hero"><div class="wallet-balance-heading"><span>{{ t('wallet.available') }} (SGD)</span><RouterLink :to="customerWalletPath(store,'transactions')+'?category=money'">{{ t('wallet.transactions.title') }} ›</RouterLink></div><h2>{{ money(account ? account.balanceCents-account.reservedCents : 0) }}</h2><div class="wallet-balance-foot"><span>{{ t('wallet.balance') }} {{ money(account?.balanceCents??0) }}</span><span>{{ t('wallet.reserved') }} {{ money(account?.reservedCents??0) }}</span></div>
  <div v-if="home.context.transactionsEnabled" class="wallet-hero-actions wallet-code-actions"><RouterLink :to="customerWalletPath(store,'code')"><WalletIcon name="qr" :size="32" /><strong>{{ t('wallet.showMyCode') }}</strong></RouterLink><RouterLink :to="customerWalletPath(store,'scan')"><WalletIcon name="scan" :size="32" /><strong>{{ t('wallet.ui.scanPay') }}</strong></RouterLink></div>
</section>
<RouterLink v-if="home.pendingBills.length" class="wallet-pending-banner" :to="customerWalletPath(store,'transactions')+'?status=pending'"><WalletIcon name="bill" /><span>{{ t('wallet.ui.pendingNotice',{count:home.pendingBills.length}) }}</span><WalletIcon name="arrow" :size="18" /></RouterLink>
<WalletAmountSplit :principal="account?.principalBalanceCents??account?.balanceCents??0" :bonus="account?.bonusBalanceCents??0" /><WalletCustomerPointsSummary /><div class="wallet-shortcuts"><RouterLink :to="customerWalletPath(store,'topup')"><strong>{{t('wallet.marketing.applications')}}</strong></RouterLink><RouterLink :to="customerWalletPath(store,'transactions')+'?category=all'"><span class="wallet-shortcut-icon"><WalletIcon name="bill" /></span><strong>{{ t('wallet.transactions.title') }}</strong><small>{{ t('wallet.ui.customerBillsHint') }}</small></RouterLink></div>
<section class="wallet-brand-note"><span>{{ t('wallet.issuer') }}</span><strong>{{ home.context.store.operatingEntityName }}</strong><p>{{ account ? t('wallet.ui.offlineTopupHint') : t('wallet.ui.customerNoAccount') }}</p><details><summary>{{ t('wallet.publicProfile') }}</summary><p>{{ home.context.store.storeName }}</p><p>{{ home.context.store.contactPhone }}</p><p>{{ home.context.store.address }}</p></details></section>
</template></template>
<style scoped>
.wallet-balance-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}
.wallet-balance-heading>span{font-size:13px;opacity:.8}
.wallet-balance-heading>a{color:inherit;font-size:14px;white-space:nowrap;min-height:44px;display:inline-flex;align-items:center}
</style>
