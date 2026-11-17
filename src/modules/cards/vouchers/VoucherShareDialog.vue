<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {publicEntryOrigin} from '../../../utils/hostContext'
import VoucherDialog from './VoucherDialog.vue'

const props = defineProps<{id: string; name: string}>()
const emit = defineEmits<{close: []}>()
const origin = import.meta.env.VITE_VOUCHER_PUBLIC_ORIGIN || publicEntryOrigin()
const url = computed(() => new URL('/voucher-campaigns/' + encodeURIComponent(props.id), origin).href)
const image = ref('')
const feedback = ref('')
const linkField = ref<HTMLInputElement>()
const canShare = typeof navigator.share === 'function'

onMounted(async () => {
  try {
    const {default:QRCode} = await import('qrcode')
    image.value = await QRCode.toDataURL(url.value, {width:320, margin:4})
  } catch {
    feedback.value = '二维码生成失败，仍可复制活动链接'
  }
})

async function copy() {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(url.value)
      feedback.value = '已复制抢券链接'
      return
    } catch {
      // Some embedded browsers expose the API but deny clipboard writes.
    }
  }
  const input = linkField.value
  if (!input) return
  input.focus()
  input.select()
  input.setSelectionRange(0, input.value.length)
  let copied = false
  try {
    copied = document.execCommand('copy')
  } catch {
    // Keep the link selected for browsers without clipboard access.
  }
  feedback.value = copied ? '已复制抢券链接' : '请手动复制选中的链接'
}

async function share() {
  try {
    await navigator.share({title:props.name, url:url.value})
  } catch (error) {
    if (!(error instanceof DOMException && error.name === 'AbortError')) await copy()
  }
}
</script>

<template>
  <VoucherDialog title="分享抢券活动" @close="emit('close')">
    <p>{{name}}</p>
    <img v-if="image" :src="image" alt="抢券活动二维码" class="share-qr">
    <label class="share-link">抢券链接 <input ref="linkField" :value="url" readonly></label>
    <div class="share-actions">
      <button type="button" @click="copy">复制链接</button>
      <button v-if="canShare" type="button" @click="share">转发链接</button>
      <a v-if="image" :href="image" download="抢券活动二维码.png">下载二维码</a>
    </div>
    <p v-if="feedback" role="status">{{ feedback }}</p>
  </VoucherDialog>
</template>

<style scoped>
.share-qr{display:block;width:min(240px,100%);height:auto;margin:auto}
.share-link{display:grid;gap:4px}
.share-link input{box-sizing:border-box;width:100%;min-width:0}
.share-actions{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-top:12px}
</style>
