<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {voucherPublic, voucherPresentation, type PublicView, type VoucherPresentation} from './api'
import {renderVoucherImage} from './images'
import VoucherHero from './VoucherHero.vue'
import VoucherStoreContacts from './VoucherStoreContacts.vue'
import VoucherImagePreview from './VoucherImagePreview.vue'
import QRCode from 'qrcode'
import VoucherDialog from './VoucherDialog.vue'
import VoucherShareDialog from './VoucherShareDialog.vue'
import {voucherReceipt} from './receipt'
import '../../wallet/wallet.css'

const route = useRoute()
const id = computed(() => String(route.params.id))
const result = ref<PublicView>()
const qr = ref('')
const presentation = ref<VoucherPresentation>()
const savedImage = ref('')
const imageBusy = ref(false)
const busy = ref(false)
const message = ref('')
const tone = ref<'success' | 'notice' | 'error'>('notice')
const sharing = ref(false)
const closedMessage = '你来晚了，活动已结束，敬请期待下期活动！'
const statusText = {
  open:'', claimed:'知足常乐，趟早使用!', used:'已使用', already_claimed:'你已领取1张，成功走起！',
  upcoming:'活动尚未开始', disabled:'活动已关闭', ended:closedMessage, sold_out:closedMessage
}

async function load(claim = false) {
  if (claim && busy.value) return
  const campaign = id.value
  busy.value = true
  message.value = ''
  try {
    const receipt = voucherReceipt(campaign)
    const [view, brand] = await Promise.all([voucherPublic(campaign, receipt, claim), voucherPresentation(campaign)])
    if (id.value !== campaign) return
    presentation.value = brand
    const image = view.code ? await QRCode.toDataURL(view.code, {width:320, margin:4, errorCorrectionLevel:'M'}) : ''
    if (id.value !== campaign) return
    result.value = view
    qr.value = image
    tone.value = view.status === 'claimed' || view.status === 'already_claimed' ? 'success' : 'notice'
    message.value = !claim && (view.status === 'claimed' || view.status === 'used')
      ? statusText.already_claimed
      : statusText[view.status]
  } catch (e) {
    if (id.value === campaign) {
      tone.value = 'error'
      message.value = e instanceof Error ? e.message : String(e)
    }
  } finally {
    if (id.value === campaign) busy.value = false
  }
}

async function download() {
  if (!result.value || !qr.value || imageBusy.value) return
  imageBusy.value = true
  const campaign = id.value
  try {
    const image = await renderVoucherImage(result.value.face, qr.value, presentation.value)
    if (campaign === id.value) savedImage.value = image
  } catch (e) {
    if (campaign === id.value) {
      tone.value = 'error'
      message.value = e instanceof Error ? e.message : String(e)
    }
  } finally {
    if (campaign === id.value) imageBusy.value = false
  }
}

watch(id, () => { result.value = undefined; qr.value = ''; presentation.value=undefined; savedImage.value=''; imageBusy.value=false; sharing.value = false; void load() }, {immediate:true})
</script>

<template>
  <main class="wallet-shell voucher-public">
    <VoucherDialog v-if="message" :title="message" :tone="tone" close-label="确定" @close="message=''" />
    <VoucherImagePreview v-else-if="savedImage && result" :source="savedImage" :name="result.face.name" @close="savedImage=''" />
    <VoucherShareDialog v-else-if="sharing && result" :id="id" :name="result.face.name" :batch-code="result.batchCode" @close="sharing=false" />
    <p v-if="busy && !result" role="status">正在加载活动…</p>
    <article v-if="result" class="wallet-card voucher-card">
      <VoucherHero :face="result.face" :batch-code="result.batchCode" :logo-url="presentation?.logoUrl" :tenant-name="presentation?.tenantName" />
      <div v-if="result.status === 'open'" class="voucher-claim">
        <button class="wallet-primary" :disabled="busy" @click="load(true)">立即抢券</button>
      </div>
      <section v-if="qr" class="voucher-code">
        <h2>核销二维码</h2>
        <p>到店后出示此码</p>
        <img class="voucher-qr" :src="qr" alt="出示给门店管理员的核销二维码">
        <button class="wallet-primary" :disabled="imageBusy" @click="download">{{imageBusy?'正在生成券图…':'保存券图到相册'}}</button>
      </section>
      <p v-else-if="result.status === 'used'" class="voucher-used">此券已核销</p>
      <section class="voucher-details">
        <h2>使用信息</h2>
        <dl>
          <div v-if="result.face.specification"><dt>商品规格</dt><dd>{{ result.face.specification }}</dd></div>
          <div><dt>使用日期</dt><dd>{{ result.face.validFrom }} 至 {{ result.face.validThrough }}</dd></div>
          <div><dt>适用门店</dt><dd>{{ result.face.stores.map(s => s.name).join('、') }}</dd></div>
        </dl>
        <VoucherStoreContacts v-if="presentation?.contacts.length" class="voucher-rules" :contacts="presentation.contacts" />
        <div v-if="result.face.rules" class="voucher-rules"><h2>活动规则</h2><p>{{ result.face.rules }}</p></div>
        <img v-if="result.face.image" class="voucher-image" :src="result.face.image" alt="活动券图">
      </section>
      <footer class="voucher-actions">
        <button type="button" :disabled="busy" @click="sharing=true">分享抢券活动</button>
      </footer>
    </article>
  </main>
</template>

<style scoped>
.voucher-public{max-width:540px}
.voucher-card{padding:0;overflow:hidden;overflow-wrap:anywhere}
.voucher-code,.voucher-details,.voucher-actions,.voucher-used,.voucher-claim{padding:20px 24px}
.voucher-claim{padding-bottom:0}
.voucher-claim button{width:100%;min-height:44px}
.voucher-code{text-align:center;border-bottom:1px dashed #bacbd8}
.voucher-code h2,.voucher-details h2{margin:0 0 6px;font-size:16px;font-weight:720;color:#172b36}
.voucher-code p,.voucher-used{margin:0;color:#4d6272;font-size:14px}
.voucher-qr{display:block;width:min(100%,208px);height:auto;margin:8px auto}
.voucher-code button{width:100%;min-height:44px}
.voucher-details dl{display:grid;gap:12px;margin:12px 0 0}
.voucher-details dl>div{display:grid;grid-template-columns:72px minmax(0,1fr);gap:8px}
.voucher-details dt{font-size:13px;color:#506575}
.voucher-details dd,.voucher-rules p{margin:0;font-size:14px;line-height:1.6;color:#263e4e;white-space:pre-wrap}
.voucher-rules{margin-top:16px;padding-top:16px;border-top:1px solid #e5ecf1}
.voucher-image{display:block;width:100%;aspect-ratio:4/3;object-fit:cover;border-radius:8px;margin-top:16px}
.voucher-actions{display:grid;gap:8px;padding-top:0}
.voucher-actions button{min-height:44px}
</style>
