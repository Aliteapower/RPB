<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import {
  getTenantAdminCustomerEmailSettings,
  getTenantAdminCustomerOAuthProviderSettings,
  getTenantAdminPublicBookingSettings,
  updateTenantAdminCustomerEmailSettings,
  updateTenantAdminCustomerOAuthProviderSettings,
  updateTenantAdminPublicBookingQuotaOverride,
  updateTenantAdminPublicBookingSettings,
  PublicBookingApiError
} from '../api/publicBookingApi'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import type {
  TenantAdminCustomerEmailSettingsMutation,
  TenantAdminCustomerOAuthProviderSettingsMutation,
  TenantAdminPublicBookingQuotaOverrideMutation,
  TenantAdminPublicBookingSettingsMutation
} from '../types/publicBooking'

const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const savingEmail = ref(false)
const savingGoogle = ref(false)
const savingFacebook = ref(false)
const savingOverride = ref(false)
const errorText = ref('')
const savedText = ref('')
const emailSecretConfigured = ref(false)
const googleSecretConfigured = ref(false)
const facebookSecretConfigured = ref(false)

const storeId = computed(() => String(route.params.storeId || ''))

const form = reactive<TenantAdminPublicBookingSettingsMutation>({
  enabled: false,
  requireCustomerLogin: true,
  defaultQuotaMode: 'percentage',
  defaultQuotaPercent: 20,
  defaultTableCount: null,
  defaultGuestCount: null,
  minLeadMinutes: 60,
  maxAdvanceDays: 30
})

const overrideForm = reactive<TenantAdminPublicBookingQuotaOverrideMutation>({
  businessDate: new Date().toISOString().slice(0, 10),
  periodKey: '',
  quotaMode: 'table_count',
  quotaPercent: null,
  tableCount: 4,
  guestCount: null
})

const emailForm = reactive<TenantAdminCustomerEmailSettingsMutation>({
  enabled: false,
  fromEmail: '',
  fromName: '',
  smtpHost: '',
  smtpPort: 587,
  smtpUsername: '',
  smtpPassword: '',
  smtpStartTls: true
})

const googleForm = reactive<TenantAdminCustomerOAuthProviderSettingsMutation>({
  enabled: false,
  clientId: '',
  clientSecret: ''
})

const facebookForm = reactive<TenantAdminCustomerOAuthProviderSettingsMutation>({
  enabled: false,
  clientId: '',
  clientSecret: ''
})

const publicBookingUrl = computed(() => `${window.location.origin}/book/${storeId.value}`)

onMounted(() => {
  void loadSettings()
})

async function loadSettings(): Promise<void> {
  loading.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const [settings, email, google, facebook] = await Promise.all([
      getTenantAdminPublicBookingSettings(storeId.value),
      getTenantAdminCustomerEmailSettings(storeId.value),
      getTenantAdminCustomerOAuthProviderSettings(storeId.value, 'google'),
      getTenantAdminCustomerOAuthProviderSettings(storeId.value, 'facebook')
    ])
    Object.assign(form, {
      enabled: settings.enabled,
      requireCustomerLogin: settings.requireCustomerLogin,
      defaultQuotaMode: settings.defaultQuotaMode,
      defaultQuotaPercent: settings.defaultQuotaPercent,
      defaultTableCount: settings.defaultTableCount,
      defaultGuestCount: settings.defaultGuestCount,
      minLeadMinutes: settings.minLeadMinutes,
      maxAdvanceDays: settings.maxAdvanceDays
    })
    Object.assign(emailForm, {
      enabled: email.enabled,
      fromEmail: email.fromEmail || '',
      fromName: email.fromName || '',
      smtpHost: email.smtpHost || '',
      smtpPort: email.smtpPort,
      smtpUsername: email.smtpUsername || '',
      smtpPassword: '',
      smtpStartTls: email.smtpStartTls
    })
    emailSecretConfigured.value = email.secretConfigured
    Object.assign(googleForm, {
      enabled: google.enabled,
      clientId: google.clientId || '',
      clientSecret: ''
    })
    googleSecretConfigured.value = google.secretConfigured
    Object.assign(facebookForm, {
      enabled: facebook.enabled,
      clientId: facebook.clientId || '',
      clientSecret: ''
    })
    facebookSecretConfigured.value = facebook.secretConfigured
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function saveEmailSettings(): Promise<void> {
  if (savingEmail.value) {
    return
  }
  savingEmail.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await updateTenantAdminCustomerEmailSettings(storeId.value, normalizedEmailSettings())
    emailSecretConfigured.value = response.secretConfigured
    emailForm.smtpPassword = ''
    savedText.value = '邮件服务已保存'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    savingEmail.value = false
  }
}

async function saveOAuthSettings(provider: 'google' | 'facebook'): Promise<void> {
  const busy = provider === 'google' ? savingGoogle : savingFacebook
  if (busy.value) {
    return
  }
  busy.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const target = provider === 'google' ? googleForm : facebookForm
    const response = await updateTenantAdminCustomerOAuthProviderSettings(
      storeId.value,
      provider,
      normalizedOAuthSettings(target)
    )
    if (provider === 'google') {
      googleSecretConfigured.value = response.secretConfigured
      googleForm.clientSecret = ''
      savedText.value = 'Google 登录已保存'
    } else {
      facebookSecretConfigured.value = response.secretConfigured
      facebookForm.clientSecret = ''
      savedText.value = 'Facebook 登录已保存'
    }
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    busy.value = false
  }
}

async function saveSettings(): Promise<void> {
  if (saving.value) {
    return
  }
  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await updateTenantAdminPublicBookingSettings(storeId.value, normalizedSettings())
    Object.assign(form, response)
    savedText.value = '公网预约设置已保存'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function saveOverride(): Promise<void> {
  if (savingOverride.value) {
    return
  }
  savingOverride.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    await updateTenantAdminPublicBookingQuotaOverride(storeId.value, normalizedOverride())
    savedText.value = '日期覆盖已保存'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    savingOverride.value = false
  }
}

async function copyPublicUrl(): Promise<void> {
  await navigator.clipboard.writeText(publicBookingUrl.value)
  savedText.value = '链接已复制'
}

function normalizedSettings(): TenantAdminPublicBookingSettingsMutation {
  return {
    ...form,
    defaultQuotaPercent: form.defaultQuotaMode === 'percentage' ? Number(form.defaultQuotaPercent ?? 20) : null,
    defaultTableCount: form.defaultQuotaMode === 'table_count' ? Number(form.defaultTableCount ?? 0) : null,
    defaultGuestCount: form.defaultQuotaMode === 'guest_count' ? Number(form.defaultGuestCount ?? 0) : null,
    minLeadMinutes: Number(form.minLeadMinutes),
    maxAdvanceDays: Number(form.maxAdvanceDays)
  }
}

function normalizedOverride(): TenantAdminPublicBookingQuotaOverrideMutation {
  return {
    businessDate: overrideForm.businessDate,
    periodKey: nullableText(overrideForm.periodKey || ''),
    quotaMode: overrideForm.quotaMode,
    quotaPercent: overrideForm.quotaMode === 'percentage' ? Number(overrideForm.quotaPercent ?? 20) : null,
    tableCount: overrideForm.quotaMode === 'table_count' ? Number(overrideForm.tableCount ?? 0) : null,
    guestCount: overrideForm.quotaMode === 'guest_count' ? Number(overrideForm.guestCount ?? 0) : null
  }
}

function normalizedEmailSettings(): TenantAdminCustomerEmailSettingsMutation {
  return {
    enabled: emailForm.enabled,
    fromEmail: nullableText(emailForm.fromEmail || ''),
    fromName: nullableText(emailForm.fromName || ''),
    smtpHost: nullableText(emailForm.smtpHost || ''),
    smtpPort: Number(emailForm.smtpPort || 587),
    smtpUsername: nullableText(emailForm.smtpUsername || ''),
    smtpPassword: nullableText(emailForm.smtpPassword || ''),
    smtpStartTls: emailForm.smtpStartTls
  }
}

function normalizedOAuthSettings(
  source: TenantAdminCustomerOAuthProviderSettingsMutation
): TenantAdminCustomerOAuthProviderSettingsMutation {
  return {
    enabled: source.enabled,
    clientId: nullableText(source.clientId || ''),
    clientSecret: nullableText(source.clientSecret || '')
  }
}

function nullableText(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof PublicBookingApiError)) {
    return '操作失败'
  }
  if (error.status === 401) {
    return '登录已失效'
  }
  if (error.status === 403) {
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
          <h1>公网预约</h1>
        </div>
        <button type="button" @click="copyPublicUrl">复制公网链接</button>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner" role="status">{{ savedText }}</p>
      <p v-if="loading" class="loading-line">加载中</p>

      <section v-else class="settings-layout">
        <form class="form-panel" @submit.prevent="saveSettings">
          <section class="form-panel__wide switch-row">
            <label>
              <input v-model="form.enabled" type="checkbox" />
              <span>开放公网预约</span>
            </label>
            <label>
              <input v-model="form.requireCustomerLogin" type="checkbox" />
              <span>要求邮箱登录</span>
            </label>
          </section>

          <label>
            <span>默认配额模式</span>
            <select v-model="form.defaultQuotaMode">
              <option value="percentage">桌台容量百分比</option>
              <option value="table_count">自定义桌数</option>
              <option value="guest_count">自定义人数</option>
            </select>
          </label>

          <label v-if="form.defaultQuotaMode === 'percentage'">
            <span>默认百分比</span>
            <input v-model.number="form.defaultQuotaPercent" min="0" max="100" type="number" />
          </label>

          <label v-if="form.defaultQuotaMode === 'table_count'">
            <span>默认桌数</span>
            <input v-model.number="form.defaultTableCount" min="0" type="number" />
          </label>

          <label v-if="form.defaultQuotaMode === 'guest_count'">
            <span>默认人数</span>
            <input v-model.number="form.defaultGuestCount" min="0" type="number" />
          </label>

          <label>
            <span>最少提前分钟</span>
            <input v-model.number="form.minLeadMinutes" min="0" type="number" />
          </label>

          <label>
            <span>最多提前天数</span>
            <input v-model.number="form.maxAdvanceDays" min="0" max="366" type="number" />
          </label>

          <div class="form-actions">
            <button class="primary-button" type="submit" :disabled="saving">
              {{ saving ? '保存中' : '保存设置' }}
            </button>
          </div>
        </form>

        <form class="form-panel" @submit.prevent="saveEmailSettings">
          <h2 class="form-panel__wide">邮件验证码</h2>

          <section class="form-panel__wide switch-row">
            <label>
              <input v-model="emailForm.enabled" type="checkbox" />
              <span>启用邮件服务</span>
            </label>
            <span class="secret-hint">{{ emailSecretConfigured ? 'SMTP 密钥已配置' : 'SMTP 密钥未配置' }}</span>
          </section>

          <label>
            <span>发件邮箱</span>
            <input v-model="emailForm.fromEmail" autocomplete="off" type="email" />
          </label>

          <label>
            <span>发件名称</span>
            <input v-model="emailForm.fromName" autocomplete="off" />
          </label>

          <label>
            <span>SMTP Host</span>
            <input v-model="emailForm.smtpHost" autocomplete="off" />
          </label>

          <label>
            <span>SMTP Port</span>
            <input v-model.number="emailForm.smtpPort" min="1" max="65535" type="number" />
          </label>

          <label>
            <span>SMTP 用户名</span>
            <input v-model="emailForm.smtpUsername" autocomplete="off" />
          </label>

          <label>
            <span>SMTP 密钥</span>
            <input v-model="emailForm.smtpPassword" autocomplete="new-password" type="password" />
          </label>

          <section class="form-panel__wide switch-row">
            <label>
              <input v-model="emailForm.smtpStartTls" type="checkbox" />
              <span>StartTLS</span>
            </label>
          </section>

          <div class="form-actions">
            <button class="primary-button" type="submit" :disabled="savingEmail">
              {{ savingEmail ? '保存中' : '保存邮件服务' }}
            </button>
          </div>
        </form>

        <form class="form-panel" @submit.prevent="saveOAuthSettings('google')">
          <h2 class="form-panel__wide">Google 登录</h2>

          <section class="form-panel__wide switch-row">
            <label>
              <input v-model="googleForm.enabled" type="checkbox" />
              <span>启用 Google</span>
            </label>
          </section>

          <label>
            <span>Client ID</span>
            <input v-model="googleForm.clientId" autocomplete="off" />
          </label>

          <div class="form-actions">
            <button class="primary-button" type="submit" :disabled="savingGoogle">
              {{ savingGoogle ? '保存中' : '保存 Google' }}
            </button>
          </div>
        </form>

        <form class="form-panel" @submit.prevent="saveOAuthSettings('facebook')">
          <h2 class="form-panel__wide">Facebook 登录</h2>

          <section class="form-panel__wide switch-row">
            <label>
              <input v-model="facebookForm.enabled" type="checkbox" />
              <span>启用 Facebook</span>
            </label>
            <span class="secret-hint">{{ facebookSecretConfigured ? 'App Secret 已配置' : 'App Secret 未配置' }}</span>
          </section>

          <label>
            <span>App ID</span>
            <input v-model="facebookForm.clientId" autocomplete="off" />
          </label>

          <label>
            <span>App Secret</span>
            <input v-model="facebookForm.clientSecret" autocomplete="new-password" type="password" />
          </label>

          <div class="form-actions">
            <button class="primary-button" type="submit" :disabled="savingFacebook">
              {{ savingFacebook ? '保存中' : '保存 Facebook' }}
            </button>
          </div>
        </form>

        <form class="form-panel" @submit.prevent="saveOverride">
          <h2 class="form-panel__wide">日期餐段覆盖</h2>

          <label>
            <span>日期</span>
            <input v-model="overrideForm.businessDate" type="date" />
          </label>

          <label>
            <span>餐段 Key</span>
            <input v-model="overrideForm.periodKey" placeholder="dinner" />
          </label>

          <label>
            <span>覆盖模式</span>
            <select v-model="overrideForm.quotaMode">
              <option value="percentage">百分比</option>
              <option value="table_count">桌数</option>
              <option value="guest_count">人数</option>
              <option value="closed">关闭</option>
            </select>
          </label>

          <label v-if="overrideForm.quotaMode === 'percentage'">
            <span>百分比</span>
            <input v-model.number="overrideForm.quotaPercent" min="0" max="100" type="number" />
          </label>

          <label v-if="overrideForm.quotaMode === 'table_count'">
            <span>桌数</span>
            <input v-model.number="overrideForm.tableCount" min="0" type="number" />
          </label>

          <label v-if="overrideForm.quotaMode === 'guest_count'">
            <span>人数</span>
            <input v-model.number="overrideForm.guestCount" min="0" type="number" />
          </label>

          <div class="form-actions">
            <button class="primary-button" type="submit" :disabled="savingOverride">
              {{ savingOverride ? '保存中' : '保存覆盖' }}
            </button>
          </div>
        </form>
      </section>
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

.page-heading button,
.form-actions button {
  border-radius: 6px;
  font: inherit;
  font-weight: 800;
  min-height: 38px;
  padding: 0 12px;
}

.page-heading button {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
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

.settings-layout {
  display: grid;
  gap: 16px;
  max-width: 980px;
}

.form-panel {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding: 18px;
}

.form-panel__wide {
  grid-column: 1 / -1;
}

.form-panel h2 {
  color: #0f172a;
  font-size: 18px;
  margin: 0;
}

.switch-row {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
}

label {
  color: #334155;
  display: grid;
  font-size: 14px;
  font-weight: 700;
  gap: 7px;
}

.switch-row label {
  align-items: center;
  display: inline-flex;
  gap: 8px;
}

.secret-hint {
  align-items: center;
  background: #eef6ff;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  color: #1d4ed8;
  display: inline-flex;
  font-size: 13px;
  font-weight: 800;
  min-height: 30px;
  padding: 0 10px;
}

input,
select {
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

input[type='checkbox'] {
  min-height: auto;
  width: auto;
}

.form-actions {
  display: flex;
  grid-column: 1 / -1;
  justify-content: flex-end;
}

.primary-button {
  background: #0f766e;
  border: 1px solid #0f766e;
  color: #ffffff;
}

button:disabled {
  cursor: default;
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

  .page-heading,
  .form-actions {
    align-items: stretch;
    display: grid;
  }
}
</style>
