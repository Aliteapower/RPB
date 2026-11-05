<script setup lang="ts">
import {computed} from 'vue'
import ProductPageHeader from '../../../components/common/ProductPageHeader.vue'
import StoreSwitcher from '../../../components/store/StoreSwitcher.vue'
import {uniqueHeaderCaption} from '../../../components/common/headerCaption'
import {useAuthSessionStore} from '../../../stores/authSession'
import {useLedgerI18n} from '../i18n'
import type {Branding} from '../types'
const props=defineProps<{branding?:Branding;storeId:string;tab:string;currency?:string}>()
const auth=useAuthSessionStore(),{text}=useLedgerI18n()
const caption=computed(()=>uniqueHeaderCaption(props.branding?.store_name||auth.authorizedStores.find(store=>store.storeId===props.storeId)?.storeName,props.branding?.store_name_en))
</script>
<template>
 <ProductPageHeader class="ledger-page-header" :title="text(tab==='history'?'历史账目':tab==='settings'?'记账设置':'每日营业记录')" :caption="caption" :logo="branding?.logo_data" :back-to="`/stores/${storeId}/staff`" :back-label="text('员工首页')">
  <template #navigation><StoreSwitcher surface="staff" compact/></template>
  <template #actions><span class="ledger-header-user" :title="auth.user?.displayName">{{auth.user?.displayName}}</span><span class="ledger-header-currency">{{currency||'SGD'}}</span></template>
 </ProductPageHeader>
</template>
<style scoped>
.ledger-page-header{--page-header-line:var(--line);--page-header-ink:var(--ink);--page-header-muted:var(--muted);--page-header-accent:var(--green);margin:0 38px;padding-top:18px;padding-bottom:12px}
.ledger-header-user{max-width:120px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px;color:var(--muted)}
.ledger-header-currency{font-size:11px;color:var(--muted)}
@media(max-width:1200px){.ledger-page-header{margin:0 25px}}
@media(max-width:760px){.ledger-page-header{margin:0 17px;padding-top:14px}}
</style>
