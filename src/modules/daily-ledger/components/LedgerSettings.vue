<script setup lang="ts">
import { ref } from 'vue'
import LedgerModal from './LedgerModal.vue'
import { ledgerApi } from '../api'
import { clone } from '../model'
import type { Settings } from '../types'
const props = defineProps<{ storeId: string; initial: Settings }>()
const emit = defineEmits<{ saved: [settings: Settings]; close: [] }>()
const crypto = window.crypto
const form = ref(clone(props.initial)), saving = ref(false), message = ref(''), error = ref('')
form.value.branding = { store_name: form.value.branding.store_name || form.value.config.store_name, store_name_en: form.value.branding.store_name_en || '', logo_data: form.value.branding.logo_data || '' }
async function logo(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]; if (!file) return
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type) || file.size > 10 * 1024 * 1024) { error.value = '请选择10MB以内 PNG、JPG 或 WebP'; return }
  const url = URL.createObjectURL(file)
  try { const img = new Image(); img.src = url; await img.decode(); const scale = Math.min(1, 256 / Math.max(img.width, img.height)); const canvas = document.createElement('canvas'); canvas.width = Math.round(img.width * scale); canvas.height = Math.round(img.height * scale); canvas.getContext('2d')!.drawImage(img, 0, 0, canvas.width, canvas.height); form.value.branding.logo_data = canvas.toDataURL('image/png') } catch { error.value = '图片读取失败' } finally { URL.revokeObjectURL(url) }
}
async function save() { saving.value = true; error.value = ''; message.value = ''; try { form.value = await ledgerApi(props.storeId).saveSettings(form.value); emit('saved', clone(form.value)); message.value = '设置已保存，已有日期仍使用当天冻结配置' } catch (e) { error.value = String(e) } finally { saving.value = false } }
</script>
<template>
  <LedgerModal title="管理设置" wide @close="emit('close')">
    <form @submit.prevent="save">
      <p v-if="error" role="alert" class="ledger-error">{{ error }}</p><p v-if="message" class="notice" role="status">{{ message }}</p>
      <fieldset class="record-inputs" :disabled="saving">
        <section class="brand-settings"><h3>门店展示</h3><p class="small muted">展示名称与 Logo 用于账本工作台，已有营业记录继续保留原配置。</p><div class="settings-columns two"><label class="field-label">门店展示名称<input v-model="form.branding.store_name" required maxlength="60" /></label><label class="field-label">英文名称<input v-model="form.branding.store_name_en" maxlength="100" /></label></div><div class="brand-preview"><div class="brand"><span class="brand-symbol custom-logo"><img v-if="form.branding.logo_data" :src="form.branding.logo_data" alt="门店Logo" /></span><span class="brand-copy">{{ form.branding.store_name }}<small>{{ form.branding.store_name_en }}</small></span></div></div><label class="field-label">门店 Logo<input type="file" accept="image/png,image/jpeg,image/webp" @change="logo" /></label><button v-if="form.branding.logo_data" type="button" class="text-button" @click="form.branding.logo_data = ''">移除Logo</button></section>
        <h3>记账设置</h3><p class="notice">设置用于尚未保存的新日期。已有账目保留当天配置，历史金额不会随设置改变。</p><div class="settings-columns"><label class="field-label">账本名称<input v-model="form.config.store_name" required maxlength="60" /></label><label class="field-label">默认底钱（$）<input v-model="form.config.base_amount" type="number" min="0" step="0.01" required /></label><label class="field-label">币种标记<input v-model="form.config.currency" required pattern="[A-Z]{3}" maxlength="3" /></label></div>
        <h3>记录时段</h3><div class="period-settings"><div v-for="item in form.config.periods" :key="item.id"><input v-model="item.name" :aria-label="`${item.id}名称`" required maxlength="40" /><label class="switch-label"><input v-model="item.enabled" :aria-label="`${item.name}启用`" type="checkbox" />记录此时段</label></div></div>
        <label class="field-label">当日最终统计班次<select v-model="form.config.final_period"><option v-for="p in form.config.periods.filter(p => p.enabled)" :key="p.id" :value="p.id">{{ p.name }}</option></select></label><p class="small muted">只取此班次最终提交的累计数；其他班次仅作中间盘点，不加进当日营业额。</p>
        <div class="settings-columns two"><section v-for="group in (['payments', 'expenses'] as const)" :key="group"><div class="subheading"><h3>{{ group === 'payments' ? '非现金项目' : '开支项目' }}</h3><button type="button" class="text-button" @click="form.config[group].push({ id: `item_${crypto.randomUUID().replaceAll('-', '')}`, name: '', enabled: true })">＋ 添加</button></div><div v-for="item in form.config[group]" :key="item.id" class="setting-item"><input v-model="item.name" :aria-label="`${item.id}名称`" required maxlength="40" /><label class="switch-label"><input v-model="item.enabled" :aria-label="`${item.name}启用`" type="checkbox" />启用</label></div></section></div>
        <div class="modal-actions"><button class="button secondary" type="button" @click="emit('close')">关闭</button><button class="button primary">保存设置</button></div>
      </fieldset>
    </form>
  </LedgerModal>
</template>
