<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

import {
  acceptPlatformPaymentProofTemplateContribution,
  createPlatformPaymentProofTemplate,
  getPlatformPaymentProofTemplateContributions,
  getPlatformPaymentProofTemplates,
  PaymentApiError,
  rejectPlatformPaymentProofTemplateContribution,
  suggestPlatformPaymentProofTemplateRule,
  updatePlatformPaymentProofTemplate
} from '../api/paymentApi'
import PlatformAdminNav from '../components/platform/PlatformAdminNav.vue'
import { useGeneratedText } from '../i18n/generatedText'
import { useAuthSessionStore } from '../stores/authSession'
import type {
  PaymentProofTemplate,
  PaymentProofTemplateContribution,
  PaymentProofTemplateMutation
} from '../types/payment'

const auth = useAuthSessionStore()
const { gt } = useGeneratedText()

const loading = ref(false)
const saving = ref(false)
const suggesting = ref(false)
const reviewingContributionId = ref('')
const errorText = ref('')
const successText = ref('')
const statusFilter = ref<'all' | PaymentProofTemplate['status']>('all')
const templates = ref<PaymentProofTemplate[]>([])
const contributions = ref<PaymentProofTemplateContribution[]>([])
const selected = ref<PaymentProofTemplate | null>(null)
const sampleInput = ref<HTMLInputElement | null>(null)
const reviewNotes = reactive<Record<string, string>>({})
const reviewTargets = reactive<Record<string, string>>({})
const form = reactive<PaymentProofTemplateMutation>({
  bankCode: 'ocbc',
  bankName: 'OCBC',
  locale: 'zh-CN',
  templateName: 'OCBC PayNow',
  status: 'draft',
  priority: 20,
  layoutJson: '{}',
  version: null
})

const filteredTemplates = computed(() => templates.value.filter(template =>
  statusFilter.value === 'all' || template.status === statusFilter.value
))

onMounted(() => {
  void loadAll()
})

async function loadAll(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const [templateResponse, contributionResponse] = await Promise.all([
      getPlatformPaymentProofTemplates(),
      getPlatformPaymentProofTemplateContributions('submitted')
    ])
    templates.value = templateResponse.templates
    contributions.value = contributionResponse.contributions
    if (!selected.value && templates.value.length) {
      selectTemplate(templates.value[0])
    }
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

function selectTemplate(template: PaymentProofTemplate): void {
  selected.value = template
  Object.assign(form, {
    bankCode: template.bankCode,
    bankName: template.bankName,
    locale: template.locale,
    templateName: template.templateName,
    status: template.status,
    priority: template.priority,
    layoutJson: formatJson(template.layoutJson),
    version: template.version
  })
}

function startNewTemplate(): void {
  selected.value = null
  Object.assign(form, {
    bankCode: 'ocbc',
    bankName: 'OCBC',
    locale: 'zh-CN',
    templateName: 'OCBC PayNow',
    status: 'draft',
    priority: 20,
    layoutJson: '{}',
    version: null
  })
}

async function saveTemplate(): Promise<void> {
  if (saving.value) {
    return
  }
  if (!isValidJson(form.layoutJson)) {
    errorText.value = gt('generated.platform-payment-proof-templates.013')
    return
  }
  saving.value = true
  errorText.value = ''
  successText.value = ''
  try {
    const response = selected.value
      ? await updatePlatformPaymentProofTemplate(selected.value.id, normalizedMutation())
      : await createPlatformPaymentProofTemplate(normalizedMutation())
    selectTemplate(response.template)
    successText.value = gt('generated.platform-payment-proof-templates.014')
    await loadAll()
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function suggestRule(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || suggesting.value) {
    return
  }
  suggesting.value = true
  errorText.value = ''
  successText.value = ''
  try {
    const response = await suggestPlatformPaymentProofTemplateRule(file, {
      bankCode: form.bankCode,
      bankName: form.bankName,
      locale: form.locale
    })
    Object.assign(form, {
      bankCode: response.bankCode || form.bankCode,
      bankName: response.bankName || form.bankName,
      locale: response.locale || form.locale,
      templateName: response.templateName || form.templateName,
      layoutJson: formatJson(response.suggestedLayoutJson)
    })
    successText.value = gt('generated.platform-payment-proof-templates.015')
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    suggesting.value = false
  }
}

async function reviewContribution(contribution: PaymentProofTemplateContribution, decision: 'accept' | 'reject'): Promise<void> {
  if (reviewingContributionId.value) {
    return
  }
  reviewingContributionId.value = contribution.id
  errorText.value = ''
  successText.value = ''
  try {
    const request = {
      platformTemplateId: reviewTargets[contribution.id] || null,
      reviewNote: reviewNotes[contribution.id]?.trim() || null,
      version: contribution.version
    }
    if (decision === 'accept') {
      await acceptPlatformPaymentProofTemplateContribution(contribution.id, request)
    } else {
      await rejectPlatformPaymentProofTemplateContribution(contribution.id, request)
    }
    successText.value = gt(decision === 'accept'
      ? 'generated.platform-payment-proof-templates.016'
      : 'generated.platform-payment-proof-templates.017')
    await loadAll()
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    reviewingContributionId.value = ''
  }
}

function normalizedMutation(): PaymentProofTemplateMutation {
  return {
    bankCode: form.bankCode.trim(),
    bankName: form.bankName.trim(),
    locale: form.locale.trim() || 'zh-CN',
    templateName: form.templateName.trim(),
    status: form.status,
    priority: Number(form.priority || 100),
    layoutJson: JSON.stringify(JSON.parse(form.layoutJson)),
    version: form.version ?? null
  }
}

function formatJson(value: string): string {
  try {
    return JSON.stringify(JSON.parse(value || '{}'), null, 2)
  } catch {
    return value || '{}'
  }
}

function isValidJson(value: string): boolean {
  try {
    JSON.parse(value || '{}')
    return true
  } catch {
    return false
  }
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof PaymentApiError)) {
    return gt('generated.platform-payment-proof-templates.018')
  }
  if (error.status === 401) {
    auth.clear()
    return gt('generated.platform-payment-proof-templates.019')
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return gt('generated.platform-payment-proof-templates.020')
  }
  if (error.response.error.code === 'VERSION_CONFLICT') {
    return gt('generated.platform-payment-proof-templates.021')
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return gt('generated.platform-payment-proof-templates.022')
  }
  return gt('generated.platform-payment-proof-templates.018')
}
</script>

<template>
  <main class="platform-shell">
    <PlatformAdminNav />

    <section class="platform-workspace" aria-label="PayNow 回单样式库">
      <header class="page-heading">
        <div>
          <span>PayNow</span>
          <h1>{{ gt('generated.platform-payment-proof-templates.001') }}</h1>
        </div>
        <button class="secondary-button" type="button" :disabled="loading" @click="loadAll">
          {{ loading ? gt('generated.platform-payment-proof-templates.002') : gt('generated.platform-payment-proof-templates.003') }}
        </button>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="successText" class="success-banner" role="status">{{ successText }}</p>

      <div class="template-workspace">
        <section class="template-list">
          <header class="panel-header">
            <strong>{{ gt('generated.platform-payment-proof-templates.004') }}</strong>
            <select v-model="statusFilter" :aria-label="gt('generated.platform-payment-proof-templates.005')">
              <option value="all">{{ gt('generated.platform-payment-proof-templates.006') }}</option>
              <option value="draft">draft</option>
              <option value="active">active</option>
              <option value="inactive">inactive</option>
            </select>
          </header>

          <button class="new-template-button" type="button" @click="startNewTemplate">
            {{ gt('generated.platform-payment-proof-templates.007') }}
          </button>
          <button
            v-for="template in filteredTemplates"
            :key="template.id"
            class="template-row"
            :class="{ active: selected?.id === template.id }"
            type="button"
            @click="selectTemplate(template)"
          >
            <span>{{ template.bankName }} · {{ template.locale }}</span>
            <strong>{{ template.templateName }}</strong>
            <small>{{ template.status }} · {{ template.priority }}</small>
          </button>
          <p v-if="!loading && !filteredTemplates.length" class="empty-line">
            {{ gt('generated.platform-payment-proof-templates.008') }}
          </p>
        </section>

        <form class="template-editor" @submit.prevent="saveTemplate">
          <header class="panel-header">
            <strong>{{ selected?.templateName || gt('generated.platform-payment-proof-templates.007') }}</strong>
            <span>{{ selected ? selected.status : 'draft' }}</span>
          </header>

          <fieldset class="field-grid" :disabled="saving">
            <label><span>{{ gt('generated.platform-payment-proof-templates.009') }}</span><input v-model.trim="form.bankCode" maxlength="32" /></label>
            <label><span>{{ gt('generated.platform-payment-proof-templates.010') }}</span><input v-model.trim="form.bankName" maxlength="80" /></label>
            <label><span>{{ gt('generated.platform-payment-proof-templates.011') }}</span><input v-model.trim="form.locale" maxlength="16" /></label>
            <label><span>{{ gt('generated.platform-payment-proof-templates.012') }}</span><input v-model.trim="form.templateName" maxlength="120" /></label>
            <label><span>{{ gt('generated.platform-payment-proof-templates.005') }}</span><select v-model="form.status"><option value="draft">draft</option><option value="active">active</option><option value="inactive">inactive</option></select></label>
            <label><span>{{ gt('generated.platform-payment-proof-templates.023') }}</span><input v-model.number="form.priority" min="1" max="999" type="number" /></label>
          </fieldset>

          <label class="layout-field">
            <span>{{ gt('generated.platform-payment-proof-templates.024') }}</span>
            <textarea v-model="form.layoutJson" rows="16" spellcheck="false"></textarea>
          </label>
          <div class="editor-actions">
            <button class="primary-button" type="submit" :disabled="saving">{{ saving ? gt('generated.platform-payment-proof-templates.002') : gt('generated.platform-payment-proof-templates.025') }}</button>
            <button class="secondary-button" type="button" :disabled="suggesting" @click="sampleInput?.click()">{{ suggesting ? gt('generated.platform-payment-proof-templates.002') : gt('generated.platform-payment-proof-templates.026') }}</button>
            <input ref="sampleInput" accept="image/png,image/jpeg,image/webp" hidden type="file" @change="suggestRule" />
          </div>
        </form>
      </div>

      <section class="contribution-panel">
        <header class="panel-header">
          <div><span>{{ gt('generated.platform-payment-proof-templates.027') }}</span><strong>{{ gt('generated.platform-payment-proof-templates.028') }}</strong></div>
          <small>{{ contributions.length }}</small>
        </header>
        <div v-for="contribution in contributions" :key="contribution.id" class="contribution-row">
          <div class="contribution-summary">
            <strong>{{ contribution.bankName }} · {{ contribution.templateName }}</strong>
            <span>{{ contribution.tenantId }} · {{ contribution.locale }} · {{ contribution.sampleFileName || '-' }}</span>
            <pre>{{ formatJson(contribution.layoutJson) }}</pre>
          </div>
          <div class="review-controls">
            <select v-model="reviewTargets[contribution.id]" :aria-label="gt('generated.platform-payment-proof-templates.029')">
              <option value="">{{ gt('generated.platform-payment-proof-templates.030') }}</option>
              <option v-for="template in templates" :key="template.id" :value="template.id">{{ template.bankName }} · {{ template.templateName }}</option>
            </select>
            <input v-model="reviewNotes[contribution.id]" :placeholder="gt('generated.platform-payment-proof-templates.031')" maxlength="500" />
            <div class="review-actions">
              <button class="primary-button" type="button" :disabled="Boolean(reviewingContributionId)" @click="reviewContribution(contribution, 'accept')">{{ gt('generated.platform-payment-proof-templates.032') }}</button>
              <button class="danger-button" type="button" :disabled="Boolean(reviewingContributionId)" @click="reviewContribution(contribution, 'reject')">{{ gt('generated.platform-payment-proof-templates.033') }}</button>
            </div>
          </div>
        </div>
        <p v-if="!loading && !contributions.length" class="empty-line">{{ gt('generated.platform-payment-proof-templates.034') }}</p>
      </section>
    </section>
  </main>
</template>

<style scoped>
.platform-shell { background: #f3f6f8; color: #102033; display: grid; grid-template-columns: 220px minmax(0, 1fr); min-height: 100dvh; }
.platform-workspace { min-width: 0; padding: 22px; }
.page-heading, .panel-header, .editor-actions, .review-actions { align-items: center; display: flex; gap: 12px; justify-content: space-between; }
.page-heading { margin-bottom: 16px; }
.page-heading span, .panel-header span, .contribution-summary span { color: #64748b; font-size: 13px; font-weight: 800; }
.page-heading h1 { color: #0f172a; font-size: 24px; margin: 0; }
.secondary-button, .primary-button, .danger-button, .new-template-button { border-radius: 6px; cursor: pointer; font: inherit; font-weight: 850; min-height: 38px; padding: 0 14px; }
.secondary-button, .new-template-button { background: #ffffff; border: 1px solid #cbd5e1; color: #334155; }
.primary-button { background: #0f766e; border: 0; color: #ffffff; }
.danger-button { background: #b91c1c; border: 0; color: #ffffff; }
button:disabled { cursor: default; opacity: 0.58; }
.error-banner, .success-banner { border-radius: 6px; margin: 0 0 12px; padding: 10px 12px; }
.error-banner { background: #fff1f2; border: 1px solid #fecaca; color: #991b1b; }
.success-banner { background: #f0fdf4; border: 1px solid #bbf7d0; color: #166534; }
.template-workspace { display: grid; gap: 14px; grid-template-columns: minmax(240px, 320px) minmax(0, 1fr); margin-bottom: 14px; max-width: 1180px; }
.template-list, .template-editor, .contribution-panel { background: #ffffff; border: 1px solid #dbe3ea; border-radius: 8px; display: grid; gap: 12px; padding: 16px; }
.template-list { align-content: start; }
.panel-header > div { display: grid; gap: 3px; }
.panel-header strong { color: #0f172a; font-size: 16px; }
.panel-header select { max-width: 140px; }
.new-template-button { text-align: left; }
.template-row { background: #f8fafc; border: 1px solid #dbe3ea; border-radius: 6px; color: #0f172a; cursor: pointer; display: grid; gap: 4px; padding: 12px; text-align: left; }
.template-row.active { border-color: #0f766e; box-shadow: inset 3px 0 0 #0f766e; }
.template-row span, .template-row small { color: #64748b; font-size: 12px; font-weight: 800; }
.field-grid { border: 0; display: grid; gap: 12px; grid-template-columns: repeat(3, minmax(0, 1fr)); margin: 0; padding: 0; }
label { color: #334155; display: grid; font-size: 14px; font-weight: 800; gap: 7px; }
input, select, textarea { background: #ffffff; border: 1px solid #cbd5e1; border-radius: 6px; box-sizing: border-box; color: #0f172a; font: inherit; min-height: 40px; padding: 9px 10px; width: 100%; }
textarea, pre { font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace; }
textarea { line-height: 1.45; resize: vertical; }
.layout-field { min-width: 0; }
.editor-actions { justify-content: flex-start; }
.contribution-panel { max-width: 1180px; }
.contribution-row { border-top: 1px solid #dbe3ea; display: grid; gap: 16px; grid-template-columns: minmax(0, 1fr) minmax(260px, 360px); padding-top: 14px; }
.contribution-summary, .review-controls { display: grid; gap: 8px; min-width: 0; }
.contribution-summary pre { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 12px; margin: 0; max-height: 140px; overflow: auto; padding: 8px; white-space: pre-wrap; }
.review-actions { justify-content: flex-start; }
.empty-line { color: #64748b; margin: 0; }
@media (max-width: 980px) { .platform-shell, .template-workspace, .field-grid, .contribution-row { grid-template-columns: 1fr; } .page-heading { align-items: stretch; flex-direction: column; } }
@media (max-width: 700px) { .platform-workspace { padding: 14px; } .editor-actions, .review-actions { align-items: stretch; flex-direction: column; } }
</style>
