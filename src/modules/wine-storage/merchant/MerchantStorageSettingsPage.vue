<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import CatalogPage from '../../catalog/CatalogPage.vue'
import type {Home} from '../types'
import type {StorageApi} from '../api'
import {useAuthSessionStore} from '../../../stores/authSession'
import MerchantStorageLocations from './MerchantStorageLocations.vue'
import MerchantStorageCustodians from './MerchantStorageCustodians.vue'
import MerchantStorageModeSettings from './MerchantStorageModeSettings.vue'
import MerchantStorageSettings from './MerchantStorageSettings.vue'
import {useMerchantStorageText} from './text'
const props=defineProps<{store:string;home:Home;api:StorageApi;disabled:boolean}>(),emit=defineEmits<{back:[];command:[()=>Promise<unknown>];error:[unknown];'busy-change':[boolean]}>()
const auth=useAuthSessionStore(),{t}=useMerchantStorageText(),can=(key:string)=>props.home.permissions.includes('wine_storage.'+key)
const managingProducts=ref(false)
const canCatalog=computed(()=>auth.user?.roles.includes('tenant_admin')===true&&auth.user?.permissions.includes('catalog.view')===true&&auth.user?.storeIds.includes(props.store)===true)
watch(canCatalog,value=>{if(!value)managingProducts.value=false})
</script>
<template><section class="storage-settings-page"><div class="storage-settings-heading"><h2 v-if="!managingProducts">{{t('storageSettings')}}</h2><button v-else :disabled="disabled" @click="managingProducts=false">{{t('backStorageSettings')}}</button><button :disabled="disabled" @click="emit('back')">← {{t('backStorage')}}</button></div><p v-if="!managingProducts" class="cards-muted storage-settings-hint">{{t('storageSettingsHint')}}</p>
<template v-if="managingProducts&&canCatalog"><CatalogPage :key="store" :store-id="store" storage-only embedded :disabled="disabled" @busy-change="emit('busy-change',$event)"/></template>
<template v-else><section v-if="canCatalog" class="storage-panel"><h3>{{t('wineProducts')}}</h3><p class="cards-muted">{{t('wineProductsHint')}}</p><button :disabled="disabled" @click="managingProducts=true">{{t('manageWineProducts')}}</button></section>
<MerchantStorageModeSettings v-if="home.newBusinessEnabled&&can('rules.manage')&&auth.user?.roles.includes('tenant_admin')" :home="home" :api="api" :disabled="disabled" @command="emit('command',$event)"/>
<MerchantStorageLocations v-if="home.newBusinessEnabled&&can('rules.manage')&&auth.user?.roles.includes('tenant_admin')" :api="api" :disabled="disabled" @error="emit('error',$event)" @busy-change="emit('busy-change',$event)"/>
<MerchantStorageCustodians v-if="home.newBusinessEnabled&&can('custodians.manage')" :api="api" :disabled="disabled" @error="emit('error',$event)" @busy-change="emit('busy-change',$event)"/>
<MerchantStorageSettings :home="home" :api="api" :disabled="disabled" :can-manage="home.newBusinessEnabled&&can('rules.manage')" @command="emit('command',$event)"/>
</template></section></template>
