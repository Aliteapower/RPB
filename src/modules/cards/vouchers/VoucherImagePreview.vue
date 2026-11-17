<script setup lang="ts">
import {computed,ref} from 'vue'
import VoucherDialog from './VoucherDialog.vue'
const props=defineProps<{source:string; name:string}>()
const emit=defineEmits<{close:[]}>()
const feedback=ref('')
const file=computed(()=>{
  const binary=atob(props.source.split(',')[1]!)
  const bytes=Uint8Array.from(binary,c=>c.charCodeAt(0))
  return new File([bytes],props.name+'.png',{type:'image/png'})
})
const canShare=computed(()=>typeof navigator.canShare==='function'&&navigator.canShare({files:[file.value]}))
async function share() {
  try{await navigator.share({files:[file.value],title:props.name})}
  catch(e){if(!(e instanceof DOMException&&e.name==='AbortError'))feedback.value='请长按下方券图保存，或点击下载图片'}
}
</script>
<template>
  <VoucherDialog title="保存券图" @close="emit('close')">
    <p class="save-hint">长按图片，选择「存储到照片」或「保存图片」</p>
    <img :src="source" :alt="name+' · 完整券图'" class="voucher-save-image">
    <div class="save-actions">
      <button v-if="canShare" type="button" @click="share">保存 / 分享图片</button>
      <a :href="source" :download="name+'.png'">下载图片</a>
    </div>
    <p v-if="feedback" role="status">{{feedback}}</p>
  </VoucherDialog>
</template>
<style scoped>
.save-hint{font-size:14px;line-height:1.6;color:#4b6173;margin:0 0 12px}
.voucher-save-image{display:block;width:100%;height:auto;border:1px solid #dce7ef;user-select:auto;-webkit-touch-callout:default}
.save-actions{display:flex;flex-wrap:wrap;gap:10px;align-items:center;margin-top:14px}
</style>
