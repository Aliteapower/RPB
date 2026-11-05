<script setup lang="ts">
import {computed,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {useAuthSessionStore} from '../../stores/authSession'
import {useStoreVisibleApps} from '../../composables/useStoreVisibleApps'
import ProductPageHeader from '../../components/common/ProductPageHeader.vue'
import {productForPath,routeAccessDecision} from './routePolicy'

const route=useRoute(),router=useRouter(),auth=useAuthSessionStore(),{locale}=useI18n()
const store=computed(()=>!route.meta.public&&productForPath(route.path)&&auth.isAuthenticated?String(route.params.storeId||'') : undefined)
const identity=computed(()=>JSON.stringify([auth.user?.accountId,auth.user?.tenantId,auth.user?.roles,auth.user?.permissions]))
const directory=useStoreVisibleApps(store,{includeAccess:true,identity})
const decision=computed(()=>routeAccessDecision(route,directory.productAccess.value,directory.loaded.value,directory.failed.value))
const canMount=computed(()=>['unmanaged','business','history','recovery'].includes(decision.value.mode))
const english=computed(()=>locale.value.startsWith('en'))
const title=computed(()=>english.value?'Product access':'产品入口')
const message=computed(()=>decision.value.mode==='loading'||decision.value.mode==='historyRedirect'?(english.value?'Checking access…':'正在检查入口…'):decision.value.mode==='failed'?(english.value?'Unable to check product access. Please retry.':'暂时无法确认产品状态，请重试。'):(english.value?'This product is unavailable or you do not have access. Available entries return automatically after activation.':'该产品尚未开通、已停用或您没有访问权限。开通后，可用入口会自动恢复。'))
watch(()=>[decision.value.mode,route.fullPath],()=>{
  if(decision.value.mode==='historyRedirect')void router.replace({path:route.path,query:{...route.query,history:'1'},hash:route.hash})
},{immediate:true})
</script>
<template>
  <slot v-if="canMount" />
  <main v-else class="product-access-page">
    <ProductPageHeader :title="title" :back-to="auth.defaultHomeRoute" :back-label="english?'Back':'返回'" :refresh-label="english?'Retry':'重试'" :refresh-disabled="directory.loading.value" @refresh="directory.refresh" />
    <section class="product-access-panel" role="status">
      <p>{{message}}</p>
      <RouterLink v-if="decision.historyRoute" :to="decision.historyRoute">{{english?'View history':'查看历史记录'}}</RouterLink>
      <p><RouterLink :to="auth.defaultHomeRoute">{{english?'Return to home':'返回首页'}}</RouterLink></p>
    </section>
  </main>
</template>
<style scoped>
.product-access-page{padding:24px;min-width:0}
.product-access-panel{padding:18px;background:#fff;border:1px solid #e1e7ef;border-radius:12px;overflow-wrap:anywhere}
.product-access-panel p{line-height:1.65}
@media(max-width:600px){.product-access-page{padding:14px}}
</style>
