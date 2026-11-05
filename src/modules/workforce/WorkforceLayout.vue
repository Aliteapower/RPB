<script setup lang="ts">
import {computed,provide,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import ProductPageHeader from '../../components/common/ProductPageHeader.vue'
import {workforceApi} from './api'
import {workforceKey} from './context'
import {useWorkforceText} from './text'
import {useWorkforceNavigation} from './navigation'
import SettingsTaskGuide from './components/SettingsTaskGuide.vue'
import {managementNavigation} from './managementNavigation'
import type {PayrollHomeData} from './payrollTypes'
import {usePayrollText} from './payrollText'
import {useAuthSessionStore} from '../../stores/authSession'
import {useI18n} from 'vue-i18n'
import {workforceRoleCaption} from './identity'
import type {Home} from './types'
import './workforce.css'
const auth=useAuthSessionStore(),{locale}=useI18n(),roleCaption=computed(()=>auth.user?workforceRoleCaption(auth.user.roles,locale.value):undefined)
const route=useRoute(),router=useRouter(),store=computed(()=>String(route.params.storeId)),home=ref<Home|null>(null),loading=ref(false),failure=ref('')
const payrollHome=ref<PayrollHomeData|null>(null),payrollFailure=ref(''),{r}=usePayrollText()
const {t,error}=useWorkforceText();let revision=0
async function refresh(){const rev=++revision;home.value=null;payrollHome.value=null;payrollFailure.value='';failure.value='';loading.value=true;try{const api=workforceApi(store.value),[basic,payroll]=await Promise.allSettled([api.get<Home>('/home'),api.get<PayrollHomeData>('/payroll-home')]);if(rev!==revision)return;if(basic.status==='rejected')throw basic.reason;home.value=basic.value;if(payroll.status==='fulfilled')payrollHome.value=payroll.value;else payrollFailure.value=r('settingsFailure')}catch(e){if(rev===revision)failure.value=error(e)}finally{if(rev===revision)loading.value=false}}
provide(workforceKey,{store,home,payrollHome,payrollFailure,api:()=>workforceApi(store.value),refresh})
watch(store,refresh,{immediate:true})
const tabs=useWorkforceNavigation(home,payrollHome)
const managementNav=computed(()=>managementNavigation(route,home.value,payrollHome.value,locale.value))
const switchStores=computed(()=>{const value=home.value;if(!value)return [];return route.path.endsWith('/workforce/clock')||!value.employeeManage?(value.clockStores??value.stores):value.stores})
function switchStore(e:Event){void router.push(`/stores/${encodeURIComponent((e.target as HTMLSelectElement).value)}/workforce`)}
</script>
<template><main class="workforce-page" :class="{'wf-settings-context':!!managementNav}">
 <ProductPageHeader :title="t('title')" :caption="roleCaption" :back-to="`/stores/${store}/staff`" :back-label="t('back')" :refresh-label="t('refresh')" :refresh-disabled="loading" @refresh="refresh">
  <template #navigation><select v-if="switchStores.length>1" :value="store" :aria-label="t('stores')" @change="switchStore"><option v-for="s in switchStores" :key="s.id" :value="s.id">{{s.name}}</option></select></template>
 </ProductPageHeader>
 <p v-if="loading" role="status">{{t('loading')}}</p><p v-if="failure" role="alert" class="wf-error">{{failure}}</p>
 <p v-if="home&&payrollFailure&&!route.path.endsWith('/management')" role="alert" class="wf-error">{{payrollFailure}}</p>
 <template v-if="home"><nav class="wf-tabs" :aria-label="t('title')"><RouterLink v-for="tab in tabs" :key="tab.path" :to="`/stores/${store}/workforce${tab.path?'/'+tab.path:''}`" :class="{selected:tab.path?(tab.matches||[tab.path]).some(path=>route.path.includes('/workforce/'+path)):route.path.endsWith('/workforce')}">{{tab.label}}</RouterLink></nav>
  <div v-if="managementNav" class="wf-management-navigation">
   <RouterLink class="wf-button wf-management-return" :to="managementNav.backTo">{{managementNav.backLabel}}</RouterLink>
   <nav :aria-label="managementNav.ariaLabel"><ol><li v-for="crumb in managementNav.crumbs" :key="crumb.label"><RouterLink v-if="crumb.to" :to="crumb.to">{{crumb.label}}</RouterLink><span v-else aria-current="page">{{crumb.label}}</span></li></ol></nav>
  </div>
  <SettingsTaskGuide v-if="managementNav" :section="managementNav.section" :form="managementNav.isForm"/>
  <RouterView :key="`${store}:${route.name==='workforce-payroll'?route.path:route.fullPath}`"/>
 </template>
</main></template>
<style scoped>
.wf-management-navigation{display:flex;align-items:center;flex-wrap:wrap;gap:10px 16px;margin:0 0 12px}
.wf-management-return{font-weight:700;border:1px solid #2563eb;background:#eff6ff;color:#1d4ed8}
.wf-management-navigation nav{min-width:0}
.wf-management-navigation ol{display:flex;flex-wrap:wrap;align-items:center;gap:6px;margin:0;padding:0;list-style:none;font-size:13px;color:#475569}
.wf-management-navigation li{display:flex;align-items:center;gap:6px;overflow-wrap:anywhere}
.wf-management-navigation li+li::before{content:'/';color:#94a3b8}
.wf-management-navigation a:focus-visible{outline:2px solid #2563eb;outline-offset:3px}
</style>
