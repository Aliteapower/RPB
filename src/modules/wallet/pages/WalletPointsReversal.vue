<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useStaffWallet } from '../useStaffWallet'
import { useWalletRecord } from '../useWalletRecord'
import { usePoints } from '../usePoints'
import { canReversePoints } from '../pointsOperations'
import { staffWalletPath } from '../navigation'
import { safeTransactionReturn } from '../transactions/query'
import type { TransactionDetail } from '../transactions/types'
import WalletTransactionRow from '../transactions/WalletTransactionRow.vue'
const route=useRoute(),router=useRouter(),{t}=useI18n(),{store,can}=useStaffWallet()
const path=computed(()=>`/transactions/points/${encodeURIComponent(String(route.params.entryId))}`)
const {record,busy,error,refresh}=useWalletRecord<TransactionDetail>(store,path)
const customerId=computed(()=>record.value?.transaction.customerId??undefined)
const points=usePoints(store,false,customerId,()=>can('wallet.points.view')&&can('wallet.points.reverse'))
const reason=ref(''),verified=ref(false)
watch([store,path],()=>{reason.value='';verified.value=false},{flush:'sync'})
const back=computed(()=>safeTransactionReturn(route.query.returnTo??staffWalletPath(store.value,'transactions')+'?category=points',store.value))
const original=computed(()=>staffWalletPath(store.value,`transactions/points/${route.params.entryId}`))
const allowed=computed(()=>can('wallet.points.reverse')&&canReversePoints(record.value))
async function submit(){
 if(!allowed.value||busy.value||points.busy.value||points.stale.value||!points.account.value||!verified.value||!reason.value.trim())return
 const entry=record.value!.transaction.id,id=store.value
 await points.command('reverse',{reason:reason.value.trim()},entry)
 if(id===store.value&&entry===record.value?.transaction.id&&!points.error.value&&points.entries.value[0])await router.push({path:staffWalletPath(id,`transactions/points/${points.entries.value[0].id}`),query:{returnTo:back.value}})
}
async function reload(){verified.value=false;await refresh();await points.refresh()}
</script>
<template><section class="wallet-page-heading"><h2>{{t('wallet.operation.reverse')}}</h2><p>{{t('wallet.operation.reverseHint')}}</p></section>
<p v-if="error||points.error.value" class="wallet-error" role="alert">{{error||points.error.value}}</p>
<p v-if="!can('wallet.points.reverse')" class="wallet-empty">{{t('wallet.noRights')}}</p>
<template v-else-if="record">
<WalletTransactionRow :row="record.transaction" :customer="false" :return-to="back" />
<p v-if="!allowed" class="wallet-warning">{{t('wallet.operation.ineligible')}}</p>
<form v-else class="wallet-form" @submit.prevent="submit"><label>{{t('wallet.marketing.reason')}}<input v-model.trim="reason" maxlength="200" required :disabled="points.busy.value"></label><label class="wallet-check"><input v-model="verified" type="checkbox" :disabled="points.busy.value" required>{{t('wallet.operation.verified')}}</label><p v-if="points.stale.value" class="wallet-warning">{{t('wallet.marketing.stale')}}</p><button type="button" :disabled="busy||points.busy.value" @click="reload">{{t('wallet.refresh')}}</button><button class="wallet-primary" :disabled="busy||points.busy.value||!points.account.value||points.stale.value||!verified||!reason.trim()">{{t('wallet.operation.confirmReverse')}}</button></form>
</template><RouterLink class="wallet-secondary-link" :to="{path:original,query:{returnTo:back}}">{{t('wallet.ui.back')}}</RouterLink></template>
