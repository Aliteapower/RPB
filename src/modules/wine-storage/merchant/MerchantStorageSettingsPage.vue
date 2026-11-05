<script setup lang="ts">
import ScanDeviceSettings from '../../shared-commerce/scanning/ScanDeviceSettings.vue'
import StorageGuide from '../guide/StorageGuide.vue'
import StorageGuideAccess from '../guide/StorageGuideAccess.vue'
import CustomerAccessLinks from '../../customers/CustomerAccessLinks.vue'
import {computed,ref,watch} from 'vue'
import {useRouter} from 'vue-router'
import CatalogPage from '../../catalog/CatalogPage.vue'
import type {Home} from '../types'
import type {StorageApi} from '../api'
import {useAuthSessionStore} from '../../../stores/authSession'
import MerchantStorageLocations from './MerchantStorageLocations.vue'
import MerchantStorageCustodians from './MerchantStorageCustodians.vue'
import MerchantStorageModeSettings from './MerchantStorageModeSettings.vue'
import MerchantStorageSettings from './MerchantStorageSettings.vue'
import {useMerchantStorageText} from './text'
const props=defineProps<{store:string;home:Home;api:StorageApi;disabled:boolean}>(),emit=defineEmits<{back:[];refresh:[];command:[()=>Promise<unknown>];error:[unknown];'busy-change':[boolean]}>()
const auth=useAuthSessionStore(),{t,locale}=useMerchantStorageText(),x=(zh:string,en:string)=>locale.value.startsWith('en')?en:zh,can=(key:string)=>props.home.permissions.includes('wine_storage.'+key)
const managingProducts=ref(false),readingGuide=ref(false),loggingOut=ref(false)
const router=useRouter()
async function signOut(){
 if(props.disabled||loggingOut.value)return
 loggingOut.value=true;emit('busy-change',true)
 try{await auth.logoutCurrentUser();await router.push({name:'login'})}
 catch(error){emit('error',error)}
 finally{loggingOut.value=false;emit('busy-change',false)}
}
const canCatalog=computed(()=>auth.user?.roles.includes('tenant_admin')===true&&auth.user?.permissions.includes('catalog.view')===true&&auth.user?.storeIds.includes(props.store)===true)
watch(canCatalog,value=>{if(!value)managingProducts.value=false})
</script>
<template><section class="storage-settings-page"><div class="storage-settings-heading"><h2 v-if="readingGuide">{{x('酒水寄存操作指南','Bottle storage quick guide')}}</h2><h2 v-else-if="!managingProducts">{{t('storageSettings')}}</h2><button v-else :disabled="disabled" @click="managingProducts=false">{{t('backStorageSettings')}}</button><button v-if="readingGuide" type="button" :disabled="disabled" @click="readingGuide=false">{{t('backStorageSettings')}}</button><button :disabled="disabled" @click="emit('back')">← {{t('backStorage')}}</button></div><p v-if="!managingProducts&&!readingGuide" class="cards-muted storage-settings-hint">{{t('storageSettingsHint')}}</p>
<StorageGuide v-if="readingGuide" :store="store" :enabled="home.newBusinessEnabled"/>
<template v-else-if="managingProducts&&canCatalog"><CatalogPage :key="store" :store-id="store" storage-only embedded :disabled="disabled" @busy-change="emit('busy-change',$event)" @categories-changed="emit('refresh')"/></template>
<template v-else><div class="storage-settings-grid"><section class="storage-panel"><ScanDeviceSettings :store="store" :disabled="disabled"/></section><section class="storage-panel storage-guide-entry"><div><h3>{{x('操作指南','Quick guide')}}</h3><p class="cards-muted">{{x('选任务、按步骤，快速了解存酒、取酒与报表。','Step-by-step help for deposits, pickups and reports.')}}</p></div><div class="storage-guide-entry-actions"><button type="button" :disabled="disabled" @click="readingGuide=true">{{x('查看操作指南','Read quick guide')}}</button><StorageGuideAccess :key="store" :store="store" :enabled="home.newBusinessEnabled" compact/></div></section><section v-if="canCatalog" class="storage-panel storage-settings-product-entry"><div><h3>{{t('wineProducts')}}</h3><p class="cards-muted">{{t('wineProductsHint')}}</p></div><button :disabled="disabled" @click="managingProducts=true">{{t('manageWineProducts')}}</button></section>
<MerchantStorageModeSettings v-if="home.newBusinessEnabled&&can('rules.manage')&&auth.user?.roles.includes('tenant_admin')" :home="home" :api="api" :disabled="disabled" @command="emit('command',$event)"/>
<MerchantStorageLocations v-if="home.newBusinessEnabled&&can('rules.manage')&&auth.user?.roles.includes('tenant_admin')" :api="api" :disabled="disabled" @error="emit('error',$event)" @busy-change="emit('busy-change',$event)"/>
<MerchantStorageCustodians v-if="home.newBusinessEnabled&&can('custodians.manage')" :api="api" :disabled="disabled" @error="emit('error',$event)" @busy-change="emit('busy-change',$event)"/>
<MerchantStorageSettings class="storage-rule-settings" :home="home" :api="api" :disabled="disabled" :can-manage="home.newBusinessEnabled&&can('rules.manage')" @command="emit('command',$event)"/>
<CustomerAccessLinks :store="store" source="storage" :disabled="disabled"/><section class="storage-panel storage-settings-product-entry"><div><h3>{{x('账号','Account')}}</h3><p class="cards-muted">{{x('退出当前账号，返回登录页。','Sign out of this account and return to sign in.')}}</p></div><button type="button" :disabled="disabled||loggingOut" @click="signOut">{{loggingOut?x('正在退出…','Signing out…'):x('退出登录','Sign out')}}</button></section></div></template></section></template>
