<script setup lang="ts">
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
</script>
<template><section class="storage-settings-page"><div class="storage-settings-heading"><h2>{{t('storageSettings')}}</h2><button :disabled="disabled" @click="emit('back')">← {{t('backStorage')}}</button></div><p class="cards-muted">{{t('storageSettingsHint')}}</p>
<MerchantStorageModeSettings v-if="home.newBusinessEnabled&&can('rules.manage')&&auth.user?.roles.includes('tenant_admin')" :home="home" :api="api" :disabled="disabled" @command="emit('command',$event)"/>
<MerchantStorageLocations v-if="home.newBusinessEnabled&&can('rules.manage')&&auth.user?.roles.includes('tenant_admin')" :api="api" :disabled="disabled" @error="emit('error',$event)" @busy-change="emit('busy-change',$event)"/>
<MerchantStorageCustodians v-if="home.newBusinessEnabled&&can('custodians.manage')" :api="api" :disabled="disabled" @error="emit('error',$event)" @busy-change="emit('busy-change',$event)"/>
<MerchantStorageSettings :home="home" :api="api" :disabled="disabled" :can-manage="home.newBusinessEnabled&&can('rules.manage')" @command="emit('command',$event)"/>
</section></template>
