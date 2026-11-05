<script setup lang="ts">
import {computed,onUnmounted,ref,watch} from 'vue'
import {useI18n} from 'vue-i18n'
import type {StorageApi} from '../api'
import type {Product} from '../types'
import StaffBarcodeScanner from '../../shared-commerce/scanning/StaffBarcodeScanner.vue'
import {useScanDevice} from '../../shared-commerce/scanning/useScanDevice'
const props=defineProps<{api:StorageApi;disabled:boolean}>(),emit=defineEmits<{choose:[Product];busy:[boolean]}>()
const {locale}=useI18n(),en=computed(()=>locale.value.startsWith('en')),{mode,identity}=useScanDevice()
const open=ref(false),busy=ref(false),message=ref('')
let generation=0
watch([()=>props.api.base,identity],()=>{generation++;open.value=false;busy.value=false;message.value='';emit('busy',false)})
onUnmounted(()=>{generation++;emit('busy',false)})
async function scan(code:string){
  if(props.disabled||busy.value)return
  const g=generation;busy.value=true;message.value='';emit('busy',true)
  try{
    const found=await props.api.get<{items:Product[];hasMore?:boolean}>('/product-options?barcode='+encodeURIComponent(code))
    if(g!==generation)return
    if(found.items?.length===1&&!found.hasMore){emit('choose',found.items[0]);message.value=en.value?'Wine recognised. Check the draft below.':'已识别酒品，请核对下方待确认清单。';if(mode.value!=='hardware')open.value=false}
    else message.value=found.items?.length?(en.value?'Barcode matches multiple products. Check the product catalog.':'条码匹配多个酒品，请先检查商品资料。'):(en.value?'No available wine found at this store.':'未找到本店可用酒品，请核对条码或查询酒品。')
  }catch(e){if(g===generation)message.value=e instanceof Error?e.message:String(e)}
  finally{if(g===generation){busy.value=false;emit('busy',false)}}
}
</script>
<template><section class="storage-bottle-scanner">
  <button type="button" :disabled="disabled||busy" :aria-expanded="open" @click="open=!open">{{en?'Scan bottle barcode':'扫描瓶身商品条码'}}</button>
  <template v-if="open"><p class="cards-muted">{{en?'Each successful scan adds one whole bottle to the draft. Confirm the receipt after checking the list.':'每次成功扫码加入一瓶未开瓶酒；核对清单并确认收存后才登记库存。'}}</p><StaffBarcodeScanner auto-start :disabled="disabled||busy" @code="scan"/></template>
  <p v-if="message" role="status" class="storage-notice">{{message}}</p>
</section></template>
