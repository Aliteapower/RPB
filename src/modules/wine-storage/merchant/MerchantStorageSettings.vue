<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import type {Home} from '../types'
import type {StorageApi} from '../api'
import {useMerchantStorageText} from './text'
const props=defineProps<{home:Home;api:StorageApi;disabled:boolean;canManage:boolean}>()
const emit=defineEmits<{command:[()=>Promise<unknown>]}>(),{t}=useMerchantStorageText(),rule=ref({categoryId:'',name:'',months:3}),unlimited=ref(false)
const validMonths=computed(()=>unlimited.value||Number.isInteger(rule.value.months)&&rule.value.months>=1&&rule.value.months<=120)
watch(()=>rule.value.categoryId,categoryId=>{
 unlimited.value=props.home.categories.some(category=>category.id===categoryId&&category.uncategorized)
})
function save(){if(props.disabled||!props.canManage||!props.home.newBusinessEnabled||!rule.value.categoryId||!rule.value.name.trim()||!validMonths.value)return;const command={...rule.value,months:unlimited.value?0:rule.value.months};emit('command',()=>props.api.command('/rules',command))}
</script>
<template><details class="storage-panel"><summary>{{t('settings')}}</summary><p class="cards-muted">{{t('settingsHint')}}</p><p v-for="r in home.rules" :key="r.id">{{r.name}} · {{r.months===0?t('unlimited'):`${r.months} ${t('months')}`}} · v{{r.version}}</p><p v-if="home.categories.some(c=>c.uncategorized&&!home.rules.some(r=>r.categoryId===c.id))">{{t('defaultUncategorizedRule')}}</p><form v-if="canManage" @submit.prevent="save"><div class="storage-customer-fields"><label>{{t('category')}}<select v-model="rule.categoryId" required :disabled="disabled"><option value="">—</option><option v-for="c in home.categories" :key="c.id" :value="c.id">{{c.nameZh||c.nameEn}}</option></select></label><label>{{t('ruleName')}}<input v-model="rule.name" required maxlength="200" :disabled="disabled"></label><label>{{t('retention')}}<select v-model="unlimited" :disabled="disabled"><option :value="false">{{t('monthly')}}</option><option :value="true">{{t('unlimited')}}</option></select></label><label v-if="!unlimited">{{t('months')}}<input v-model.number="rule.months" type="number" min="1" max="120" required :disabled="disabled"></label></div><button :disabled="disabled||!home.newBusinessEnabled||!rule.categoryId||!rule.name.trim()||!validMonths">{{t('save')}}</button></form></details></template>
