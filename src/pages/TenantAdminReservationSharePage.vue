<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import { TenantAdminApiError } from '../api/tenantAdminApi'
import {
  getTenantAdminShareProfile,
  previewTenantAdminShareProfile,
  resetTenantAdminShareProfileTemplate,
  updateTenantAdminShareProfileTemplate
} from '../api/tenantAdminShareProfileApi'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'
import type {
  TenantAdminShareProfile,
  TenantAdminShareTemplateMutation
} from '../types/tenantAdminShareProfile'

const route = useRoute()
const auth = useAuthSessionStore()
const loading = ref(false)
const saving = ref(false)
const previewing = ref(false)
const restoring = ref(false)
const errorText = ref('')
const savedText = ref('')
const previewText = ref('')
const availableVariables = ref<string[]>([])

const storeId = computed(() => String(route.params.storeId || ''))

const form = reactive({
  reservationShareTemplate: ''
})

onMounted(() => {
  void loadShareProfile()
})

async function loadShareProfile(): Promise<void> {
  loading.value = true
  errorText.value = ''
  savedText.value = ''
  previewText.value = ''

  try {
    const response = await getTenantAdminShareProfile(storeId.value)
    applyShareProfile(response.shareProfile)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function submitShareProfile(): Promise<void> {
  if (saving.value) {
    return
  }

  saving.value = true
  errorText.value = ''
  savedText.value = ''

  try {
    const response = await updateTenantAdminShareProfileTemplate(storeId.value, toTemplateMutation())
    applyShareProfile(response.shareProfile)
    savedText.value = '已保存'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function previewShareProfile(): Promise<void> {
  if (previewing.value) {
    return
  }

  previewing.value = true
  errorText.value = ''
  previewText.value = ''

  try {
    const response = await previewTenantAdminShareProfile(storeId.value, toTemplateMutation())
    previewText.value = response.preview.shareText
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    previewing.value = false
  }
}

async function restoreDefaultTemplate(): Promise<void> {
  if (restoring.value) {
    return
  }

  restoring.value = true
  errorText.value = ''
  savedText.value = ''

  try {
    const response = await resetTenantAdminShareProfileTemplate(storeId.value)
    applyShareProfile(response.shareProfile)
    savedText.value = '已恢复默认模板'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    restoring.value = false
  }
}

function applyShareProfile(profile: TenantAdminShareProfile): void {
  form.reservationShareTemplate = profile.reservationShareTemplate
  availableVariables.value = profile.availableVariables
}

function toTemplateMutation(): TenantAdminShareTemplateMutation {
  return {
    reservationShareTemplate: nullableText(form.reservationShareTemplate)
  }
}

function nullableText(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function insertVariable(variable: string): void {
  const token = `{{${variable}}}`
  form.reservationShareTemplate = form.reservationShareTemplate
    ? `${form.reservationShareTemplate}\n${token}`
    : token
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof TenantAdminApiError)) {
    return '操作失败'
  }
  if (error.status === 401) {
    auth.clear()
    return '登录已失效'
  }
  if (error.response.error.code === 'TEMPLATE_UNKNOWN_VARIABLE') {
    return '模板包含未支持的变量'
  }
  if (error.response.error.code === 'STORE_SCOPE_MISMATCH') {
    return '没有该店面的后台权限'
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return '没有租户后台权限'
  }
  return '操作失败'
}
</script>

<template>
  <main class="tenant-shell">
    <TenantAdminNav />

    <section class="tenant-workspace">
      <header class="page-heading">
        <div>
          <span>租户</span>
          <h1>订位分享模板</h1>
        </div>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner" role="status">{{ savedText }}</p>
      <p v-if="loading" class="loading-line">加载中</p>

      <form v-else class="form-panel" @submit.prevent="submitShareProfile">
        <section class="template-tools form-panel__wide" aria-label="模板变量">
          <button
            v-for="variable in availableVariables"
            :key="variable"
            type="button"
            @click="insertVariable(variable)"
          >
            {{ variable }}
          </button>
        </section>

        <label class="form-panel__wide">
          <span>分享模板</span>
          <textarea v-model="form.reservationShareTemplate" rows="16"></textarea>
        </label>

        <section v-if="previewText" class="preview-panel form-panel__wide" aria-label="分享预览">
          <pre>{{ previewText }}</pre>
        </section>

        <div class="form-actions">
          <button type="button" :disabled="previewing" @click="previewShareProfile">
            {{ previewing ? '预览中' : '预览' }}
          </button>
          <button type="button" :disabled="restoring" @click="restoreDefaultTemplate">
            {{ restoring ? '恢复中' : '恢复默认模板' }}
          </button>
          <button class="primary-button" type="submit" :disabled="saving">
            {{ saving ? '保存中' : '保存' }}
          </button>
        </div>
      </form>
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

.page-heading {
  align-items: center;
  display: flex;
  gap: 16px;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-heading span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.page-heading h1 {
  color: #0f172a;
  font-size: 24px;
  margin: 0;
}

.error-banner,
.success-banner,
.loading-line {
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

.loading-line {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  color: #475569;
}

.form-panel {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding: 18px;
  width: min(100%, 980px);
}

.form-panel__wide {
  grid-column: 1 / -1;
}

label {
  color: #334155;
  display: grid;
  font-size: 14px;
  font-weight: 700;
  gap: 7px;
}

input,
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

textarea {
  line-height: 1.5;
  resize: vertical;
}

input:disabled {
  background: #f8fafc;
  color: #64748b;
}

.template-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.template-tools button,
.form-actions button {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #334155;
  font: inherit;
  font-weight: 800;
  min-height: 36px;
  padding: 0 12px;
}

.preview-panel {
  background: #f8fafc;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  padding: 12px;
}

.preview-panel pre {
  color: #0f172a;
  font: inherit;
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.form-actions {
  display: flex;
  gap: 10px;
  grid-column: 1 / -1;
  justify-content: flex-end;
}

.form-actions .primary-button {
  background: #0f766e;
  border-color: #0f766e;
  color: #ffffff;
}

.form-actions button:disabled {
  opacity: 0.6;
}

@media (max-width: 980px) {
  .tenant-shell,
  .form-panel {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .tenant-workspace {
    padding: 14px;
  }

  .form-actions {
    display: grid;
  }
}
</style>
