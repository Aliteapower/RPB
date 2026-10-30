<script setup lang="ts">
import {computed} from 'vue'
import type {TemplateDraftForm} from '../payTemplatePublication'
import type {Option,StoreOption} from '../types'
import {usePhaseText} from '../phaseText'
import PayRuleFields from './PayRuleFields.vue'
const props=defineProps<{draft:TemplateDraftForm;companies:Option[];stores:StoreOption[];busy:boolean}>()
defineEmits<{save:[];cancel:[]}>()
const {english}=usePhaseText(),x=(zh:string,en:string)=>english()?en:zh
const shops=computed(()=>props.stores.filter(s=>s.companyId&&props.draft.companyIds.includes(s.companyId)))
const unavailableCompanies=computed(()=>props.draft.companyIds.filter(id=>!props.companies.some(c=>c.id===id)))
const unavailableStores=computed(()=>props.draft.storeIds.filter(id=>!shops.value.some(s=>s.id===id)))
</script>
<template><form class="wf-section" @submit.prevent="$emit('save')"><h3>{{x('母版草稿','Master draft')}} · {{draft.expectedVersion}}</h3><fieldset :disabled="busy"><label>{{x('名称','Name')}}<input v-model="draft.name" required maxlength="100" :aria-label="x('母版名称','Master name')"></label><fieldset><legend>{{x('适用公司','Companies in scope')}}</legend><label v-for="c in companies" :key="c.id" class="wf-check"><input v-model="draft.companyIds" type="checkbox" :value="c.id">{{c.name}}</label><label v-for="id in unavailableCompanies" :key="id" class="wf-check"><input v-model="draft.companyIds" type="checkbox" :value="id">{{x('已选公司当前失效，可取消','Selected company unavailable; deselect to repair')}}: {{id}}</label></fieldset><fieldset><legend>{{x('选择现有门店','Select existing stores')}}</legend><label v-for="s in shops" :key="s.id" class="wf-check"><input v-model="draft.storeIds" type="checkbox" :value="s.id">{{s.name}}</label><label v-for="id in unavailableStores" :key="id" class="wf-check"><input v-model="draft.storeIds" type="checkbox" :value="id">{{x('已选门店已停用或不在当前公司，可取消','Selected store inactive or outside company; deselect to repair')}}: {{stores.find(s=>s.id===id)?.name||id}}</label><label class="wf-check"><input v-model="draft.includeFutureStores" type="checkbox">{{x('未来新增且公司匹配的门店自动可见（仍须显式启用）','Show future matching stores (explicit enable still required)')}}</label></fieldset><PayRuleFields :rule="draft.rule" :disabled="busy"/></fieldset><p class="wf-hint">{{x('保存只修改草稿。已发布版本和门店采用保持原样。','Saving changes the draft. Published versions and adoptions stay fixed.')}}</p><footer class="wf-form-actions"><button type="button" :disabled="busy" @click="$emit('cancel')">{{x('取消','Cancel')}}</button><button class="primary" :disabled="busy">{{x('保存草稿','Save draft')}}</button></footer></form></template>
