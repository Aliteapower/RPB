<script setup lang="ts">
import { useLedgerI18n } from '../i18n'
import { ref } from 'vue'
import { useAuthSessionStore } from '../../../stores/authSession'
import LedgerModal from './LedgerModal.vue'
import { ledgerApi } from '../api'
import { clone } from '../model'
import type { Settings } from '../types'
const { text, errorText, itemName } = useLedgerI18n()
const props = defineProps<{ storeId: string; initial: Settings }>()
const emit = defineEmits<{ saved: [settings: Settings]; close: [] }>()
const crypto = window.crypto
const form = ref(clone(props.initial)), saving = ref(false), message = ref(''), error = ref('')
const auth = useAuthSessionStore()
async function save() { saving.value = true; error.value = ''; message.value = ''; try { form.value = await ledgerApi(props.storeId).saveSettings(form.value); emit('saved', clone(form.value)); message.value = '设置已保存，已有日期仍使用当天冻结配置' } catch (e) { error.value = String(e) } finally { saving.value = false } }
</script>
<template>
  <LedgerModal :title="text('记账设置')" wide @close="emit('close')">
    <form @submit.prevent="save">
      <p v-if="error" role="alert" class="ledger-error">{{ errorText(error) }}</p><p v-if="message" class="notice" role="status">{{ text(message) }}</p>
      <fieldset class="record-inputs" :disabled="saving">
        <p class="notice">{{ text('门店名称与 Logo 在租户资料统一维护。') }}<RouterLink v-if="auth.isTenantAdmin" :to="`/stores/${storeId}/admin/profile#tenant-logo`">{{ text('前往租户资料 →') }}</RouterLink><span v-else>{{ text('如需修改，请联系租户管理员。') }}</span></p>
        <p class="notice">{{ text('设置用于尚未保存的新日期。已有账目保留当天配置，历史金额不会随设置改变。') }}</p><div class="settings-columns two"><label class="field-label">{{ text('默认底钱（$）') }}<input v-model="form.config.base_amount" type="number" min="0" step="0.01" required /></label><label class="field-label">{{ text('币种标记') }}<input v-model="form.config.currency" required pattern="[A-Z]{3}" maxlength="3" /></label></div>
        <h3>{{ text('记录时段') }}</h3><div class="period-settings"><div v-for="item in form.config.periods" :key="item.id"><input v-model="item.name" :aria-label="text('{name}名称', { name: item.id })" required maxlength="40" /><label class="switch-label"><input v-model="item.enabled" :aria-label="text('{name}启用', { name: item.name })" type="checkbox" />{{ text('记录此时段') }}</label></div></div>
        <label class="field-label">{{ text('当日最终统计班次') }}<select v-model="form.config.final_period"><option v-for="p in form.config.periods.filter(p => p.enabled)" :key="p.id" :value="p.id">{{ itemName(p) }}</option></select></label><p class="small muted">{{ text('只取此班次最终提交的累计数；其他班次仅作中间盘点，不加进当日营业额。') }}</p>
        <div class="settings-columns two"><section v-for="group in (['payments', 'expenses'] as const)" :key="group"><div class="subheading"><h3>{{ group === 'payments' ? text("非现金项目") : text("开支项目") }}</h3><button type="button" class="text-button" @click="form.config[group].push({ id: `item_${crypto.randomUUID().replaceAll('-', '')}`, name: '', enabled: true })">{{ text('＋ 添加') }}</button></div><div v-for="item in form.config[group]" :key="item.id" class="setting-item"><input v-model="item.name" :aria-label="text('{name}名称', { name: item.id })" required maxlength="40" /><label class="switch-label"><input v-model="item.enabled" :aria-label="text('{name}启用', { name: item.name })" type="checkbox" />{{ text('启用') }}</label></div></section></div>
        <div class="modal-actions"><button class="button secondary" type="button" @click="emit('close')">{{ text('关闭') }}</button><button class="button primary">{{ text('保存设置') }}</button></div>
      </fieldset>
    </form>
  </LedgerModal>
</template>
