<script setup lang="ts">
import {ref,watch} from 'vue'
import ProductBarcodeScanner from '../../shared-commerce/barcode/ProductBarcodeScanner.vue'
import {useCatalogText} from '../text'
const props=defineProps<{modelValue:string[];disabled:boolean;readOnly:boolean}>(),emit=defineEmits<{'update:modelValue':[string[]]}>(),{t}=useCatalogText(),scanning=ref(false)
function update(value:string){emit('update:modelValue',value?[value]:[])}
watch(()=>props.disabled,v=>{if(v)scanning.value=false})
</script>
<template>
 <section class="catalog-barcode-fields" :aria-label="t('barcodes')">
  <h3>{{t('barcodes')}}</h3><p class="cards-muted">{{t(readOnly?'onlyAdmin':'barcodeHint')}}</p>
  <template v-if="modelValue.length>1">
   <p role="alert">{{t('singleBarcode')}}</p>
   <div v-for="value in modelValue" :key="value" class="barcode-field-row"><span>{{value}}</span><button v-if="!readOnly" type="button" :disabled="disabled" @click="update(value)">{{t('keepBarcode')}}</button></div>
   <button v-if="!readOnly" type="button" :disabled="disabled" @click="update('')">{{t('clearBarcodes')}}</button>
  </template>
  <div v-else class="barcode-field-row">
   <label>{{t('barcodes')}}<input :value="modelValue[0]??''" inputmode="numeric" maxlength="14" :disabled="disabled||readOnly" @input="update(($event.target as HTMLInputElement).value)"></label>
   <div v-if="!readOnly" class="cards-actions"><button type="button" :disabled="disabled" @click="scanning=true">{{t('scanInto')}}</button><button v-if="modelValue[0]" type="button" :disabled="disabled" @click="scanning=false;update('')">{{t('remove')}}</button></div>
  </div>
  <ProductBarcodeScanner v-if="scanning" :auto-start="true" :show-manual="false" :disabled="disabled" @code="update($event);scanning=false"/>
 </section>
</template>
<style scoped>.catalog-barcode-fields{border-top:1px solid #d8e5e8;margin:20px 0;padding-top:16px}.barcode-field-row{padding:10px 0;border-bottom:1px solid #edf2f3}.barcode-field-row input{font-variant-numeric:tabular-nums}</style>
