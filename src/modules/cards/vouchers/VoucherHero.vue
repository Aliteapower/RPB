<script setup lang="ts">
import {onMounted,onUnmounted,ref,watch} from 'vue'
import {paintVoucherTexture} from './texture'
import {voucherBenefit,voucherValue,type Face} from './api'
import './voucher-brand.css'
const props=defineProps<{face:Face; batchCode?:string; logoUrl?:string|null; tenantName?:string}>()
const logoFailed=ref(false)
const header=ref<HTMLElement>()
const texture=ref<HTMLCanvasElement>()
let observer:ResizeObserver|undefined
function drawTexture() {
  const element=header.value, canvas=texture.value
  if(!element||!canvas||!element.clientWidth||!element.clientHeight)return
  const ratio=Math.min(window.devicePixelRatio||1,2)
  canvas.width=Math.round(element.clientWidth*ratio)
  canvas.height=Math.round(element.clientHeight*ratio)
  const context=canvas.getContext('2d')
  if(!context)return
  context.setTransform(ratio,0,0,ratio,0,0)
  paintVoucherTexture(context,element.clientWidth,element.clientHeight)
}
onMounted(()=>{
  drawTexture()
  if(typeof ResizeObserver!=='undefined') {
    observer=new ResizeObserver(drawTexture)
    if(header.value)observer.observe(header.value)
  }
})
onUnmounted(()=>observer?.disconnect())
watch(()=>props.logoUrl,()=>logoFailed.value=false)
</script>
<template>
  <header ref="header" class="voucher-hero">
    <canvas ref="texture" class="voucher-security-texture" aria-hidden="true"></canvas>
    <div class="voucher-brand-row">
      <span class="voucher-type">{{face.kind==='cash'?'代金券':'产品券'}}</span>
      <img v-if="logoUrl&&!logoFailed" class="voucher-logo" :src="logoUrl" :alt="(tenantName||'租户')+' Logo'" @error="logoFailed=true">
    </div>
    <strong class="voucher-benefit">{{face.kind==='cash'?voucherValue(face):voucherBenefit(face)}}</strong>
    <p v-if="face.kind==='product'" class="voucher-value">价值 {{voucherValue(face)}}</p>
    <h1>{{face.name}}</h1>
    <p v-if="batchCode" class="voucher-value">幸运号 {{batchCode}}</p>
  </header>
</template>
<style scoped>
.voucher-hero{display:grid;justify-items:start;gap:8px;padding:24px;color:#fff;overflow:hidden;overflow-wrap:anywhere}
.voucher-security-texture{position:absolute;inset:0;width:100%;height:100%;z-index:-1;pointer-events:none}
</style>
