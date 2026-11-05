<script setup lang="ts">
import {useCatalogText} from '../text'
import type {CatalogProductForm} from './useCatalogProductEditor'
const form=defineModel<CatalogProductForm>({required:true})
defineProps<{globalEdit:boolean;showEnglishName?:boolean}>()
const {t}=useCatalogText()
</script>
<template>
 <fieldset :disabled="!globalEdit" class="catalog-extra-global">
  <div class="cards-grid">
   <label v-if="showEnglishName">{{t('nameEn')}}<input v-model="form.nameEn"></label>
   <label>{{t('descriptionZh')}}<textarea v-model="form.descriptionZh" /></label>
   <label>{{t('descriptionEn')}}<textarea v-model="form.descriptionEn" /></label>
   <label>{{t('kind')}}<select v-model="form.kind"><option value="physical">{{t('physical')}}</option><option value="service">{{t('service')}}</option></select></label>
   <label>{{t('image')}}<input v-model="form.imageUrl"></label>
  </div>
 </fieldset>
 <div class="cards-grid">
  <label>{{t('price')}}<input v-model="form.price" inputmode="decimal" :required="!form.channels.every(c=>c==='storage')"></label>
  <label>{{t('inclusive')}} / {{t('exclusive')}}<select v-model="form.taxMode" :disabled="!globalEdit"><option value="inclusive">{{t('inclusive')}}</option><option value="exclusive">{{t('exclusive')}}</option></select></label>
  <label>{{t('taxCategory')}}<select v-model="form.taxCategory" :disabled="!globalEdit" @change="form.taxCategory!=='taxable'&&(form.taxRate='0')"><option v-for="v in ['taxable','zero','exempt','not_applicable']" :key="v" :value="v">{{t(v)}}</option></select></label>
  <label>{{t('taxRate')}}<input v-model="form.taxRate" :disabled="!globalEdit||form.taxCategory!=='taxable'" inputmode="decimal"></label>
 </div>
 <p class="cards-muted">{{t('taxHint')}}</p>
 <div class="cards-actions"><span>{{t('channels')}}</span><label v-for="v in ['cards','rewards','shopping','storage']" :key="v" class="check"><input v-model="form.channels" type="checkbox" :value="v">{{t(v)}}</label></div>
</template>
<style scoped>.catalog-extra-global{border:0;padding:0;margin:0 0 14px;min-width:0}</style>
