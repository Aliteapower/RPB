<script setup lang="ts">
import {ref,watch,computed,onUnmounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {moduleRequest} from './api'
import {normalizeGtin} from '../shared-commerce/barcode/gtin'
import ProductBarcodeScanner from '../shared-commerce/barcode/ProductBarcodeScanner.vue'
const props=defineProps<{base:string;productId:string;productName?:string;productCode?:string;disabled:boolean}>(),{locale}=useI18n(),en=computed(()=>locale.value.startsWith('en'))
const codes=ref<string[]>([]),busy=ref(false),error=ref(''),draft=ref(''),editingCode=ref(''),notice=ref('')
type Command={barcode:string;remove:boolean;idempotencyKey:string;replaceBarcode?:string}
const pending=ref<Command|null>(null),blocked=computed(()=>props.disabled||busy.value||!!pending.value)
const noticeText=computed(()=>notice.value==='scanned'?(en.value?'Barcode read. Check or edit it, then save.':'已识别条码，可修改，核对后点击保存条码。'):(en.value?'Product barcode saved.':'当前商品条码已保存。'))
let generation=0
watch([()=>props.base,()=>props.productId],async()=>{const own=++generation;pending.value=null;draft.value='';editingCode.value='';notice.value='';busy.value=true;error.value='';codes.value=[];try{const data=await moduleRequest<{barcodes:string[]}>(props.base,`/products/${props.productId}/barcodes`);if(own===generation)codes.value=data.barcodes}catch(e){if(own===generation)error.value=e instanceof Error?e.message:String(e)}finally{if(own===generation)busy.value=false}},{immediate:true})
onUnmounted(()=>generation++)
function scanned(value:string){if(blocked.value)return;draft.value=value;error.value='';notice.value='scanned'}
function edit(code:string){if(blocked.value)return;editingCode.value=code;draft.value=code;error.value='';notice.value=''}
function cancel(){if(blocked.value)return;editingCode.value='';draft.value='';notice.value=''}
async function execute(command:Command){
 if(props.disabled||busy.value)return
 const own=generation;busy.value=true;error.value='';notice.value='';pending.value=command
 try{const data=await moduleRequest<{barcodes:string[]}>(props.base,`/products/${props.productId}/barcodes`,'POST',command);if(own===generation){codes.value=data.barcodes;pending.value=null;if(!command.remove||editingCode.value===command.barcode){draft.value='';editingCode.value=''}notice.value='saved'}}
 catch(e){if(own===generation){error.value=e instanceof Error?e.message:String(e);const status=(e as {status?:number}).status;if(status!==undefined&&status<500)pending.value=null}}
 finally{if(own===generation)busy.value=false}
}
function save(){if(blocked.value)return;if(!normalizeGtin(draft.value)){error.value=en.value?'Enter a valid 8, 12, 13 or 14 digit product barcode.':'请核对商品条码，需为校验有效的 8、12、13 或 14 位数字。';return}void execute({barcode:draft.value.trim(),remove:false,idempotencyKey:crypto.randomUUID(),...(editingCode.value?{replaceBarcode:editingCode.value}:{})})}
function remove(code:string){if(blocked.value)return;void execute({barcode:code,remove:true,idempotencyKey:crypto.randomUUID()})}
</script>
<template><section class="catalog-barcodes" :aria-label="en?'Product barcodes':'商品条码号'"><h4>{{en?'Product barcodes':'商品条码号'}}</h4><p v-if="productName" class="barcode-product"><strong>{{productName}}</strong><small>{{productCode}}</small></p><p class="cards-muted">{{en?'Scan or type the barcode, check it, then save. No photo is required.':'扫描或输入条码，核对后保存。无需拍照或选择文件。'}}</p><p v-if="error" class="cards-error" role="alert">{{error}}</p><p v-if="notice" role="status">{{noticeText}}</p><p v-if="busy" role="status">{{en?'Loading…':'处理中…'}}</p><div v-for="code in codes" :key="code" class="cards-actions barcode-saved"><code>{{code}}</code><button type="button" :disabled="blocked" @click="edit(code)">{{en?'Edit':'修改'}}</button><button type="button" :disabled="blocked" @click="remove(code)">{{en?'Remove barcode':'移除条码'}}</button></div><label class="barcode-input">{{editingCode?(en?'Edit barcode':'修改商品条码'):(en?'Product barcode':'商品条码号')}}<input v-model="draft" inputmode="numeric" maxlength="14" :disabled="blocked" @keydown.enter.prevent="save"></label><div class="cards-actions"><button type="button" class="primary" :disabled="blocked||!draft.trim()" @click="save">{{editingCode?(en?'Save changes':'保存修改'):(en?'Save barcode':'保存条码')}}</button><button v-if="draft||editingCode" type="button" :disabled="blocked" @click="cancel">{{en?'Cancel':'取消'}}</button><button v-if="pending" type="button" :disabled="busy||disabled" @click="execute(pending)">{{en?'Retry previous operation':'重试上次条码操作'}}</button></div><ProductBarcodeScanner :disabled="blocked" :show-manual="false" @code="scanned"/></section></template>
<style scoped>.catalog-barcodes{border-top:1px solid #d8e5e8;margin-top:18px;padding-top:16px;min-width:0}.catalog-barcodes h4{font-size:17px;margin:0 0 8px}.barcode-product{display:flex;flex-wrap:wrap;align-items:baseline;gap:8px}.barcode-product small{color:#70818a}.barcode-input{display:grid;gap:6px;margin:14px 0 10px}.barcode-input input{width:100%;min-width:0;box-sizing:border-box}.barcode-saved{flex-wrap:wrap}.barcode-saved code{overflow-wrap:anywhere}.catalog-barcodes .cards-actions{flex-wrap:wrap}</style>
