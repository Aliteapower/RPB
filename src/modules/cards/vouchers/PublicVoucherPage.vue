<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {voucherPublic, voucherBenefit, voucherValue, type PublicView} from './api'
import {downloadVoucher} from './images'
import QRCode from 'qrcode'
import VoucherDialog from './VoucherDialog.vue'
import VoucherShareDialog from './VoucherShareDialog.vue'
import '../../wallet/wallet.css'

const route = useRoute()
const id = computed(() => String(route.params.id))
const result = ref<PublicView>()
const qr = ref('')
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
  const key = 'rpb.voucher.receipt:' + campaign
  busy.value = true
  message.value = ''
  try {
    let receipt = localStorage.getItem(key) ?? ''
    if (claim && !receipt) {
      receipt = crypto.randomUUID()
      localStorage.setItem(key, receipt)
    }
    const view = await voucherPublic(campaign, receipt, claim)
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
  if (!result.value || !qr.value) return
  try {
    await downloadVoucher(result.value.face, qr.value)
  } catch (e) {
    tone.value = 'error'
    message.value = e instanceof Error ? e.message : String(e)
  }
}

watch(id, () => { result.value = undefined; qr.value = ''; sharing.value = false; void load() }, {immediate:true})
</script>

<template>
  <main class="wallet-shell voucher-public">
    <VoucherDialog v-if="message" :title="message" :tone="tone" close-label="确定" @close="message=''" />
    <VoucherShareDialog v-else-if="sharing && result" :id="id" :name="result.face.name" @close="sharing=false" />
    <p v-if="busy && !result" role="status">正在加载活动…</p>
    <article v-if="result" class="wallet-card voucher-card">
      <header class="voucher-hero">
        <span class="voucher-type">{{ result.face.kind === 'cash' ? '代金券' : '产品券' }}</span>
        <strong class="voucher-benefit">{{ result.face.kind === 'cash' ? voucherValue(result.face) : voucherBenefit(result.face) }}</strong>
        <p v-if="result.face.kind === 'product'" class="voucher-value">价值 {{ voucherValue(result.face) }}</p>
        <h1>{{ result.face.name }}</h1>
      </header>
      <div v-if="result.status === 'open'" class="voucher-claim">
        <button class="wallet-primary" :disabled="busy" @click="load(true)">立即抢券</button>
      </div>
      <section v-if="qr" class="voucher-code">
        <h2>核销二维码</h2>
        <p>到店后出示此码</p>
        <img class="voucher-qr" :src="qr" alt="出示给门店管理员的核销二维码">
        <button class="wallet-primary" @click="download">下载券图</button>
      </section>
      <p v-else-if="result.status === 'used'" class="voucher-used">此券已核销</p>
      <section class="voucher-details">
        <h2>使用信息</h2>
        <dl>
          <div v-if="result.face.specification"><dt>商品规格</dt><dd>{{ result.face.specification }}</dd></div>
          <div><dt>使用日期</dt><dd>{{ result.face.validFrom }} 至 {{ result.face.validThrough }}</dd></div>
          <div><dt>适用门店</dt><dd>{{ result.face.stores.map(s => s.name).join('、') }}</dd></div>
        </dl>
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
.voucher-hero{display:grid;justify-items:start;gap:6px;padding:24px;background:#173d58;color:#fff}
.voucher-type{padding:3px 9px;border-radius:5px;background:#ffffff24;font-size:13px;font-weight:650}
.voucher-benefit{font-size:clamp(30px,7vw,42px);font-weight:780;line-height:1.17;font-variant-numeric:tabular-nums}
.voucher-hero h1{margin:3px 0 0;font-size:16px;font-weight:600;line-height:1.5;color:#e3edf3}
.voucher-value{margin:0;color:#e3edf3;font-size:14px}
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
