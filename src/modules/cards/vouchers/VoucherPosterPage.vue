<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import {publicEntryOrigin} from '../../../utils/hostContext'
import {voucherPresentation,voucherRedemptionTime,type VoucherPresentation} from './api'
import {renderVoucherImage} from './images'
import VoucherHero from './VoucherHero.vue'
import VoucherStoreContacts from './VoucherStoreContacts.vue'
import '../../wallet/wallet.css'

const route=useRoute()
const id=computed(()=>String(route.params.id))
const presentation=ref<VoucherPresentation>()
const qr=ref('')
const error=ref('')
const exported=ref('')
const saving=ref(false)
const readyToPrint=ref(false)
let generation=0
watch(id,async value=>{
  const current=++generation
  presentation.value=undefined
  qr.value=''
  error.value=''
  exported.value=''
  readyToPrint.value=false
  try {
    const origin=import.meta.env.VITE_VOUCHER_PUBLIC_ORIGIN||publicEntryOrigin()
    const url=new URL('/voucher-campaigns/'+encodeURIComponent(value),origin).href
    const [data,{default:QRCode}]=await Promise.all([voucherPresentation(value),import('qrcode')])
    const code=await QRCode.toDataURL(url,{width:400,margin:4,errorCorrectionLevel:'M'})
    if(current!==generation)return
    presentation.value=data
    qr.value=code
    await document.fonts.ready
    if(data.logoUrl) await new Promise(resolve=>{
      const image=new Image()
      image.onload=image.onerror=resolve
      image.src=data.logoUrl!
    })
    if(current===generation)readyToPrint.value=true
  } catch(e) {
    if(current===generation)error.value=e instanceof Error?e.message:String(e)
  }
},{immediate:true})

function print() { window.print() }
async function save() {
  if(!presentation.value||!qr.value||saving.value)return
  const current=generation
  saving.value=true
  try {
    const image=await renderVoucherImage(presentation.value.face,qr.value,presentation.value,true)
    if(current!==generation)return
    exported.value=image
    const link=document.createElement('a')
    link.download=presentation.value.face.name+'-活动海报.png'
    link.href=image
    link.click()
  } catch(e) {
    if(current===generation)error.value=e instanceof Error?e.message:String(e)
  } finally { if(current===generation)saving.value=false }
}
</script>
<template>
  <main class="voucher-poster-page">
    <nav class="poster-tools" aria-label="海报操作">
      <button :disabled="!readyToPrint" @click="print">打印海报（A4）</button>
      <button :disabled="!presentation||saving" @click="save">{{saving?'正在生成…':'保存海报图片'}}</button>
      <p>打印时选择 A4 纸张。内容较长时自动续页。</p>
    </nav>
    <p v-if="error" class="poster-tools" role="alert">{{error}}</p>
    <p v-if="!presentation&&!error" class="poster-tools" role="status">正在生成活动海报…</p>
    <article v-if="presentation" class="poster-paper">
      <VoucherHero :face="presentation.face" :batch-code="presentation.batchCode"
        :logo-url="presentation.logoUrl" :tenant-name="presentation.tenantName"/>
      <section class="poster-qr">
        <h2>扫码抢券</h2>
        <img :src="qr" alt="活动抢券二维码">
        <p>幸运号 {{presentation.batchCode}}</p>
      </section>
      <div class="poster-details">
        <section class="poster-dates">
          <h2>领取时间</h2>
          <p>{{voucherRedemptionTime(presentation.startsAt,presentation.face.timezone)}} 至 {{voucherRedemptionTime(presentation.endsAt,presentation.face.timezone)}}</p>
          <h2>使用日期</h2>
          <p>{{presentation.face.validFrom}} 至 {{presentation.face.validThrough}}</p>
        </section>
        <section><h2>适用门店</h2><VoucherStoreContacts :contacts="presentation.contacts"/></section>
        <section v-if="presentation.face.specification"><h2>商品规格</h2><p>{{presentation.face.specification}}</p></section>
        <section v-if="presentation.face.rules" class="poster-rules"><h2>活动规则</h2><p>{{presentation.face.rules}}</p></section>
        <img v-if="presentation.face.image" class="poster-art" :src="presentation.face.image" alt="活动券图">
      </div>
    </article>
    <section v-if="exported" class="poster-tools">
      <p>手机可长按海报图片保存</p>
      <img :src="exported" alt="可保存的活动海报" class="poster-export">
    </section>
  </main>
</template>
<style scoped>
.voucher-poster-page{background:#edf2f6;min-height:100vh;padding:20px;box-sizing:border-box;color:#172b36}
.poster-tools{display:flex;gap:12px;flex-wrap:wrap;align-items:center;max-width:760px;margin:0 auto 16px}
.poster-tools button{padding:10px 16px;border:1px solid #b8ccce;background:#fff;border-radius:8px;cursor:pointer}
.poster-tools button:first-child{background:#173d58;color:#fff}
.poster-tools button:disabled{opacity:.5;cursor:default}
.poster-tools p{width:100%;margin:0;font-size:13px;color:#506575}
.poster-paper{max-width:760px;margin:0 auto;background:#fff;overflow-wrap:anywhere;box-shadow:0 2px 20px #173d5812}
.poster-paper :deep(.voucher-hero){padding:36px}
.poster-paper :deep(.voucher-benefit){font-size:42px}
.poster-qr{text-align:center;padding:20px;border-bottom:1px dashed #bacbd8;break-inside:avoid}
.poster-qr h2{font-size:28px;margin:0}
.poster-qr img{width:200px;max-width:100%;height:auto;display:block;margin:0 auto}
.poster-qr p{margin:0;font-size:15px}
.poster-details{padding:24px 36px}
.poster-details section{margin-bottom:16px;break-inside:avoid}
.poster-details h2{font-size:16px;margin:0 0 5px;break-after:avoid}
.poster-details p{font-size:14px;line-height:1.6;margin:0;white-space:pre-wrap}
.poster-details .poster-rules{break-inside:auto}
.poster-art{display:block;width:100%;height:auto;break-inside:avoid}
.poster-export{width:100%;height:auto}
:global(#app:has(.voucher-poster-page)>.frontend-locale-switcher){display:none}
@media(max-width:500px){
 .voucher-poster-page{padding:12px}
 .poster-paper :deep(.voucher-hero),.poster-details{padding:20px}
 .poster-paper :deep(.voucher-benefit){font-size:32px}
}
@page{size:A4 portrait;margin:12mm}
@media print{
 :global(body:has(.voucher-poster-page)),:global(html:has(.voucher-poster-page)){background:#fff}
 :global(#app:has(.voucher-poster-page)>.frontend-update-notice){display:none}
 .poster-tools{display:none!important}
 .voucher-poster-page{background:#fff;padding:0;min-height:0}
 .poster-paper{max-width:none;box-shadow:none}
 .poster-paper :deep(.voucher-hero){print-color-adjust:exact;-webkit-print-color-adjust:exact;break-inside:avoid}
 .poster-paper :deep(.voucher-benefit){font-size:32pt}
 .poster-details{padding:5mm 7mm}
 .poster-qr{padding:4mm}
 .poster-qr img{width:45mm}
 .poster-details p{orphans:3;widows:3}
}
</style>
