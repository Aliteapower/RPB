<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import {
  createPaymentProofTemplate,
  getPaymentProofTemplateContributions,
  getPaymentProofTemplates,
  PaymentApiError,
  submitPaymentProofTemplateContribution,
  suggestPaymentProofTemplateRule,
  updatePaymentProofTemplate
} from '../api/paymentApi'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useGeneratedText } from '../i18n/generatedText'
import { useAuthSessionStore } from '../stores/authSession'
import type {
  PaymentProofTemplate,
  PaymentProofTemplateContribution,
  PaymentProofTemplateMutation,
  PaymentProofTemplateTestScanResponse
} from '../types/payment'
import { formatAppGateErrorMessage } from '../utils/appGateErrorMessages'

const route = useRoute()
const auth = useAuthSessionStore()
const { gt } = useGeneratedText()

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const submitting = ref(false)
const errorText = ref('')
const savedText = ref('')
const templates = ref<PaymentProofTemplate[]>([])
const contributions = ref<PaymentProofTemplateContribution[]>([])
const selected = ref<PaymentProofTemplate | null>(null)
const testResult = ref<PaymentProofTemplateTestScanResponse | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const defaultLayout = {
  matchKeywords: ['OCBC', '您已支付'],
  successKeywords: ['您已支付', '支付成功', '转账成功'],
  referencePatterns: ['(?:讯息|信息|Message|Ref)\\s*[:：]?\\s*([A-Z0-9.-]{10,32})'],
  amountPatterns: ['您已支付\\s*([0-9OoIl,.]+)\\s*(?:SGD|S6D|SG)'],
  referenceRoi: [0, 0.3, 1, 0.66],
  amountRoi: [0, 0.15, 1, 0.34]
}

const form = reactive<PaymentProofTemplateMutation>({
  bankCode: 'ocbc',
  bankName: 'OCBC',
  locale: 'zh-CN',
  templateName: 'OCBC tenant PayNow',
  status: 'active',
  priority: 20,
  layoutJson: JSON.stringify(defaultLayout, null, 2),
  version: null
})

const storeId = computed(() => String(route.params.storeId || ''))
const editable = computed(() => !selected.value || selected.value.source !== 'platform_seed')
const sortedTemplates = computed(() => [...templates.value].sort((a, b) => {
  if (a.source !== b.source) {
    return a.source === 'tenant_custom' || a.source === 'tenant_override' ? -1 : 1
  }
  return a.priority - b.priority || a.bankCode.localeCompare(b.bankCode)
}))
const platformTemplates = computed(() => sortedTemplates.value.filter(template => template.source === 'platform_seed'))
const tenantTemplates = computed(() => sortedTemplates.value.filter(template => template.source !== 'platform_seed'))

onMounted(() => {
  void loadTemplates()
})

async function loadTemplates(preserveSavedText = false): Promise<void> {
  loading.value = true
  errorText.value = ''
  if (!preserveSavedText) {
    savedText.value = ''
  }
  try {
    const [templateResponse, contributionResponse] = await Promise.all([
      getPaymentProofTemplates(storeId.value),
      getPaymentProofTemplateContributions(storeId.value)
    ])
    templates.value = templateResponse.templates
    contributions.value = contributionResponse.contributions
    const refreshedSelected = templateResponse.templates.find(template => template.id === selected.value?.id)
    if (refreshedSelected) {
      selectTemplate(refreshedSelected)
    } else if (selected.value) {
      selected.value = null
    } else if (templateResponse.templates.length) {
      selectTemplate(templateResponse.templates[0])
    }
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

function selectTemplate(template: PaymentProofTemplate): void {
  selected.value = template
  testResult.value = null
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
  testResult.value = null
  Object.assign(form, {
    bankCode: 'ocbc',
    bankName: 'OCBC',
    locale: 'zh-CN',
    templateName: 'OCBC tenant PayNow',
    status: 'active',
    priority: 20,
    layoutJson: JSON.stringify(defaultLayout, null, 2),
    version: null
  })
}

function copyPlatformSeed(): void {
  if (!selected.value) {
    startNewTemplate()
    return
  }
  Object.assign(form, {
    bankCode: selected.value.bankCode,
    bankName: selected.value.bankName,
    locale: selected.value.locale,
    templateName: `${selected.value.templateName} custom`,
    status: 'active',
    priority: Math.max(1, selected.value.priority - 1),
    layoutJson: formatJson(selected.value.layoutJson),
    version: null
  })
  selected.value = null
}

async function saveTemplate(): Promise<void> {
  if (saving.value) {
    return
  }
  if (!isValidJson(form.layoutJson)) {
    errorText.value = gt('generated.tenant-admin-payment-proof-templates.018')
    return
  }
  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const payload = normalizedMutation()
    const response = selected.value
      ? await updatePaymentProofTemplate(storeId.value, selected.value.id, payload)
      : await createPaymentProofTemplate(storeId.value, payload)
    savedText.value = gt('generated.tenant-admin-payment-proof-templates.019')
    await loadTemplates(true)
    selectTemplate(response.template)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function suggestFromSelectedFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || testing.value) {
    return
  }
  testing.value = true
  errorText.value = ''
  testResult.value = null
  try {
    const response = await suggestPaymentProofTemplateRule(storeId.value, file, {
      bankCode: form.bankCode,
      bankName: form.bankName,
      locale: form.locale
    })
    form.layoutJson = formatJson(response.suggestedLayoutJson)
    testResult.value = { success: true, template: selected.value, ocr: response.ocr }
    savedText.value = gt('generated.tenant-admin-payment-proof-templates.020')
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    testing.value = false
  }
}

function contributionFor(template: PaymentProofTemplate): PaymentProofTemplateContribution | null {
  return contributions.value.find(contribution => contribution.sourceTemplateId === template.id) || null
}

function submissionLabel(template: PaymentProofTemplate): string {
  return contributionFor(template)?.status === 'submitted'
    ? gt('generated.tenant-admin-payment-proof-templates.037')
    : ''
}

async function submitSelectedToPlatform(): Promise<void> {
  if (!selected.value || selected.value.source === 'platform_seed' || submitting.value || contributionFor(selected.value)?.status === 'submitted') {
    return
  }
  submitting.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    await submitPaymentProofTemplateContribution(storeId.value, {
      sourceTemplateId: selected.value.id,
      bankCode: selected.value.bankCode,
      bankName: selected.value.bankName,
      locale: selected.value.locale,
      templateName: selected.value.templateName,
      layoutJson: selected.value.layoutJson
    })
    savedText.value = gt('generated.tenant-admin-payment-proof-templates.032')
    await loadTemplates(true)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    submitting.value = false
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

function formatSource(template: PaymentProofTemplate | null): string {
  if (!template) {
    return gt('generated.tenant-admin-payment-proof-templates.034')
  }
  if (template.source === 'platform_seed') {
    return gt('generated.tenant-admin-payment-proof-templates.033')
  }
  return gt('generated.tenant-admin-payment-proof-templates.034')
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
    return gt('generated.tenant-admin-payment-proof-templates.021')
  }
  if (error.status === 401) {
    auth.clear()
    return gt('generated.tenant-admin-payment-proof-templates.022')
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return gt('generated.tenant-admin-payment-proof-templates.023')
  }
  if (error.response.error.code === 'VERSION_CONFLICT') {
    return gt('generated.tenant-admin-payment-proof-templates.024')
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return gt('generated.tenant-admin-payment-proof-templates.025')
  }
  if (error.response.error.messageKey.startsWith('appgate.') || error.response.error.code === 'PERMISSION_DENIED') {
    return formatAppGateErrorMessage(error.response.error, gt('generated.tenant-admin-payment-proof-templates.023'))
  }
  return gt('generated.tenant-admin-payment-proof-templates.021')
}
</script>

<template>
  <main class="tenant-shell">
    <TenantAdminNav />

    <section class="tenant-workspace">
      <header class="page-heading">
        <div>
          <span>PayNow</span>
          <h1>{{ gt('generated.tenant-admin-payment-proof-templates.001') }}</h1>
        </div>
        <div class="page-actions">
          <RouterLink class="secondary-link" :to="{ name: 'tenant-admin-payment-settings', params: { storeId } }">
            {{ gt('generated.tenant-admin-payment-proof-templates.002') }}
          </RouterLink>
          <button class="secondary-link" type="button" @click="startNewTemplate">
            {{ gt('generated.tenant-admin-payment-proof-templates.003') }}
          </button>
        </div>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner" role="status">{{ savedText }}</p>

      <div class="template-workspace">
        <section class="template-list" aria-label="回单样式库">
          <header>
            <strong>{{ gt('generated.tenant-admin-payment-proof-templates.004') }}</strong>
            <button type="button" :disabled="loading" @click="loadTemplates()">
              {{ loading ? gt('generated.tenant-admin-payment-proof-templates.026') : gt('generated.tenant-admin-payment-proof-templates.005') }}
            </button>
          </header>

          <section v-if="platformTemplates.length" class="template-group">
            <span class="template-group-label">{{ gt('generated.tenant-admin-payment-proof-templates.033') }}</span>
            <button
              v-for="template in platformTemplates"
              :key="template.id"
              class="template-row"
              :class="{ active: selected?.id === template.id }"
              type="button"
              @click="selectTemplate(template)"
            >
              <span>{{ template.bankName }}</span>
              <strong>{{ template.templateName }}</strong>
              <small>{{ formatSource(template) }} · {{ template.status }} · {{ template.locale }}</small>
            </button>
          </section>

          <section v-if="tenantTemplates.length" class="template-group">
            <span class="template-group-label">{{ gt('generated.tenant-admin-payment-proof-templates.034') }}</span>
            <button
              v-for="template in tenantTemplates"
              :key="template.id"
              class="template-row"
              :class="{ active: selected?.id === template.id }"
              type="button"
              @click="selectTemplate(template)"
            >
              <span>{{ template.bankName }}</span>
              <strong>{{ template.templateName }}</strong>
              <small>{{ formatSource(template) }} · {{ template.status }} · {{ template.locale }}<template v-if="submissionLabel(template)"> · {{ submissionLabel(template) }}</template></small>
            </button>
          </section>

          <p v-if="!loading && !templates.length" class="empty-line">
            {{ gt('generated.tenant-admin-payment-proof-templates.027') }}
          </p>
        </section>

        <form class="template-editor" @submit.prevent="saveTemplate">
          <header>
            <div>
              <strong>{{ selected ? selected.templateName : gt('generated.tenant-admin-payment-proof-templates.003') }}</strong>
              <span>{{ formatSource(selected) }}</span>
            </div>
            <button
              v-if="selected?.source === 'platform_seed'"
              class="secondary-link"
              type="button"
              @click="copyPlatformSeed"
            >
              {{ gt('generated.tenant-admin-payment-proof-templates.008') }}
            </button>
            <button
              v-else-if="selected"
              class="secondary-link"
              type="button"
              :disabled="submitting || contributionFor(selected)?.status === 'submitted'"
              @click="submitSelectedToPlatform"
            >
              {{ contributionFor(selected)?.status === 'submitted' ? gt('generated.tenant-admin-payment-proof-templates.037') : gt('generated.tenant-admin-payment-proof-templates.035') }}
            </button>
          </header>

          <fieldset :disabled="!editable || saving" class="field-grid">
            <label>
              <span>{{ gt('generated.tenant-admin-payment-proof-templates.009') }}</span>
              <input v-model.trim="form.bankCode" maxlength="32" />
            </label>
            <label>
              <span>{{ gt('generated.tenant-admin-payment-proof-templates.010') }}</span>
              <input v-model.trim="form.bankName" maxlength="80" />
            </label>
            <label>
              <span>{{ gt('generated.tenant-admin-payment-proof-templates.011') }}</span>
              <input v-model.trim="form.locale" maxlength="16" />
            </label>
            <label>
              <span>{{ gt('generated.tenant-admin-payment-proof-templates.012') }}</span>
              <input v-model.trim="form.templateName" maxlength="120" />
            </label>
            <label>
              <span>{{ gt('generated.tenant-admin-payment-proof-templates.013') }}</span>
              <select v-model="form.status">
                <option value="active">active</option>
                <option value="draft">draft</option>
                <option value="inactive">inactive</option>
              </select>
            </label>
            <label>
              <span>{{ gt('generated.tenant-admin-payment-proof-templates.014') }}</span>
              <input v-model.number="form.priority" min="1" max="999" type="number" />
            </label>
          </fieldset>

          <label class="layout-field">
            <span>{{ gt('generated.tenant-admin-payment-proof-templates.015') }}</span>
            <textarea v-model="form.layoutJson" :disabled="!editable || saving" rows="15" spellcheck="false"></textarea>
          </label>

          <div class="editor-actions">
            <button class="primary-button" type="submit" :disabled="!editable || saving">
              {{ saving ? gt('generated.tenant-admin-payment-proof-templates.026') : gt('generated.tenant-admin-payment-proof-templates.016') }}
            </button>
            <button class="secondary-link" type="button" :disabled="testing" @click="fileInput?.click()">
              {{ testing ? gt('generated.tenant-admin-payment-proof-templates.026') : gt('generated.tenant-admin-payment-proof-templates.036') }}
            </button>
            <input
              ref="fileInput"
              accept="image/png,image/jpeg,image/webp"
              hidden
              type="file"
              @change="suggestFromSelectedFile"
            />
          </div>

          <article v-if="testResult" class="test-panel">
            <strong>{{ gt('generated.tenant-admin-payment-proof-templates.028') }}</strong>
            <dl>
              <dt>{{ gt('generated.tenant-admin-payment-proof-templates.029') }}</dt>
              <dd>{{ testResult.template?.templateName || '-' }}</dd>
              <dt>Ref</dt>
              <dd>{{ testResult.ocr?.extractedReference || '-' }}</dd>
              <dt>{{ gt('generated.tenant-admin-payment-proof-templates.030') }}</dt>
              <dd>{{ testResult.ocr?.extractedAmount || '-' }}</dd>
              <dt>{{ gt('generated.tenant-admin-payment-proof-templates.031') }}</dt>
              <dd>{{ testResult.ocr?.confidence || '-' }}</dd>
            </dl>
            <pre>{{ testResult.ocr?.rawText || '-' }}</pre>
          </article>
        </form>
      </div>
    </section>
  </main>
</template>

<style scoped>
.tenant-shell {
  background: #f3f6f8;
  color: #102033;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  min-height: 100dvh;
}

.tenant-workspace {
  min-width: 0;
  padding: 22px;
}

.page-heading,
.page-actions,
.template-list header,
.template-editor header,
.editor-actions {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.page-heading {
  margin-bottom: 16px;
}

.page-heading span,
.template-editor header span {
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.page-heading h1 {
  color: #0f172a;
  font-size: 24px;
  margin: 0;
}

.secondary-link,
.primary-button,
.template-list header button {
  align-items: center;
  border-radius: 6px;
  display: inline-flex;
  font: inherit;
  font-weight: 850;
  min-height: 38px;
  padding: 0 14px;
}

.secondary-link,
.template-list header button {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
  cursor: pointer;
  text-decoration: none;
}

.primary-button {
  background: #0f766e;
  border: 0;
  color: #ffffff;
  cursor: pointer;
}

button:disabled {
  cursor: default;
  opacity: 0.58;
}

.error-banner,
.success-banner {
  border-radius: 6px;
  margin: 0 0 12px;
  padding: 10px 12px;
}

.error-banner {
  background: #fff1f2;
  border: 1px solid #fecaca;
  color: #991b1b;
}

.success-banner {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #166534;
}

.template-workspace {
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
  max-width: 1160px;
}

.template-list,
.template-editor {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 12px;
  padding: 16px;
}

.template-list {
  align-content: start;
}

.template-group {
  display: grid;
  gap: 8px;
}

.template-group-label {
  color: #64748b;
  font-size: 12px;
  font-weight: 850;
}

.template-row {
  background: #f8fafc;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  color: #0f172a;
  cursor: pointer;
  display: grid;
  gap: 4px;
  padding: 12px;
  text-align: left;
}

.template-row.active {
  border-color: #0f766e;
  box-shadow: inset 3px 0 0 #0f766e;
}

.template-row span,
.template-row small {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.template-row strong,
.template-editor header strong {
  color: #0f172a;
  font-size: 15px;
  font-weight: 900;
}

.field-grid {
  border: 0;
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0;
  padding: 0;
}

label {
  color: #334155;
  display: grid;
  font-size: 14px;
  font-weight: 800;
  gap: 7px;
}

input,
select,
textarea {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  box-sizing: border-box;
  color: #0f172a;
  font: inherit;
  min-height: 40px;
  padding: 9px 10px;
  width: 100%;
}

fieldset:disabled input,
fieldset:disabled select,
textarea:disabled {
  background: #f8fafc;
  color: #64748b;
}

textarea {
  font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
  line-height: 1.45;
  resize: vertical;
}

.layout-field {
  min-width: 0;
}

.editor-actions {
  justify-content: flex-start;
}

.test-panel {
  background: #f8fafc;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 10px;
  padding: 12px;
}

.test-panel dl {
  display: grid;
  gap: 6px 12px;
  grid-template-columns: max-content minmax(0, 1fr);
  margin: 0;
}

.test-panel dt {
  color: #64748b;
  font-weight: 800;
}

.test-panel dd {
  color: #0f172a;
  font-weight: 900;
  margin: 0;
  overflow-wrap: anywhere;
}

.test-panel pre {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  color: #334155;
  margin: 0;
  max-height: 220px;
  overflow: auto;
  padding: 10px;
  white-space: pre-wrap;
}

.empty-line {
  color: #64748b;
  margin: 0;
}

@media (max-width: 980px) {
  .tenant-shell,
  .template-workspace,
  .field-grid {
    grid-template-columns: 1fr;
  }

  .page-heading,
  .page-actions,
  .template-editor header,
  .editor-actions {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 700px) {
  .tenant-workspace {
    padding: 14px;
  }
}
</style>
