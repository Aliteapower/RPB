<script setup lang="ts">
import {onMounted, ref} from 'vue'

withDefaults(defineProps<{title: string; closeLabel?: string; tone?: 'neutral' | 'success' | 'notice' | 'error'}>(), {tone:'neutral'})
const marks = {neutral:'', success:'✓', notice:'!', error:'!'}
const emit = defineEmits<{close: []}>()
const dialog = ref<HTMLDialogElement>()
onMounted(() => dialog.value?.showModal())
</script>

<template>
  <dialog ref="dialog" class="voucher-dialog" :data-tone="tone" :aria-label="title" @cancel.prevent="emit('close')">
    <header class="voucher-dialog-heading">
      <span v-if="tone !== 'neutral'" class="voucher-dialog-mark" aria-hidden="true">{{marks[tone]}}</span>
      <h2>{{ title }}</h2>
    </header>
    <div v-if="$slots.default" class="voucher-dialog-content"><slot /></div>
    <footer class="voucher-dialog-actions">
      <button type="button" class="primary wallet-primary" autofocus @click="emit('close')">{{closeLabel ?? '关闭'}}</button>
    </footer>
  </dialog>
</template>

<style scoped>
.voucher-dialog{box-sizing:border-box;width:min(380px,calc(100% - 32px));max-height:calc(100dvh - 40px);overflow:auto;padding:24px;border:1px solid #dce7ef;border-radius:18px;background:#fff;color:#172b36;box-shadow:0 20px 64px #142d4640}
.voucher-dialog::backdrop{background:#10263b99}
.voucher-dialog-heading{display:grid;gap:12px}
.voucher-dialog h2{margin:0;font-size:22px;line-height:1.4;font-weight:750;overflow-wrap:anywhere}
.voucher-dialog-mark{display:grid;place-items:center;width:38px;height:38px;border-radius:50%;background:#fff4de;color:#865107;font-size:21px;font-weight:750}
.voucher-dialog[data-tone=success] .voucher-dialog-mark{background:#e5f5ec;color:#126244}
.voucher-dialog[data-tone=error] .voucher-dialog-mark{background:#fcebec;color:#a9242e}
.voucher-dialog-content{margin-top:16px}
.voucher-dialog-actions{margin-top:20px}
.voucher-dialog-actions button{width:100%;min-height:44px}
</style>
