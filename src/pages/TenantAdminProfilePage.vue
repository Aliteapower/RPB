<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import {
  clearTenantProfileLogo,
  getTenantProfile,
  TenantAdminApiError,
  updateTenantProfile,
  uploadTenantProfileLogo,
  type TenantAdminProfile,
  type TenantAdminProfileMutation
} from '../api/tenantAdminApi'
import {
  getTenantAdminShareProfile,
  updateTenantAdminShareProfile
} from '../api/tenantAdminShareProfileApi'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'
import type {
  TenantAdminShareProfile,
  TenantAdminShareProfileMutation
} from '../types/tenantAdminShareProfile'

interface TenantProfileForm {
  tenantCode: string
  displayName: string
  status: string
  defaultLocale: string
  contactPhone: string
  address: string
  principalName: string
  logoMediaUrl: string
}

interface TenantShareProfileForm {
  storeDisplayName: string
  shareDisplayName: string
  googleMapUrl: string
  shareEmail: string
  whatsappBusinessPhoneE164: string
  reservationShareNote: string
  reservationShareTemplate: string
  usesDefaultReservationShareTemplate: boolean
}

const route = useRoute()
const auth = useAuthSessionStore()
const loading = ref(false)
const saving = ref(false)
const logoSaving = ref(false)
const errorText = ref('')
const savedText = ref('')
const logoFile = ref<File | null>(null)
const logoFileInput = ref<HTMLInputElement | null>(null)
const localLogoPreviewUrl = ref('')

const storeId = computed(() => String(route.params.storeId || ''))
const logoPreviewUrl = computed(() => localLogoPreviewUrl.value || form.logoMediaUrl)

const form = reactive<TenantProfileForm>({
  tenantCode: '',
  displayName: '',
  status: '',
  defaultLocale: 'zh-CN',
  contactPhone: '',
  address: '',
  principalName: '',
  logoMediaUrl: ''
})

const shareForm = reactive<TenantShareProfileForm>({
  storeDisplayName: '',
  shareDisplayName: '',
  googleMapUrl: '',
  shareEmail: '',
  whatsappBusinessPhoneE164: '',
  reservationShareNote: '',
  reservationShareTemplate: '',
  usesDefaultReservationShareTemplate: true
})

onMounted(() => {
  void loadProfile()
})

onUnmounted(() => {
  revokeLocalLogoPreview()
})

async function loadProfile(): Promise<void> {
  loading.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const [profileResponse, shareProfileResponse] = await Promise.all([
      getTenantProfile(storeId.value),
      getTenantAdminShareProfile(storeId.value)
    ])
    applyProfile(profileResponse.profile)
    applyShareProfile(shareProfileResponse.shareProfile)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function submitProfile(): Promise<void> {
  if (saving.value) {
    return
  }

  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const [profileResponse, shareProfileResponse] = await Promise.all([
      updateTenantProfile(storeId.value, toPayload()),
      updateTenantAdminShareProfile(storeId.value, toSharePayload())
    ])
    applyProfile(profileResponse.profile)
    applyShareProfile(shareProfileResponse.shareProfile)
    savedText.value = '已保存'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function submitLogo(): Promise<void> {
  if (logoSaving.value || !logoFile.value) {
    return
  }

  logoSaving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await uploadTenantProfileLogo(storeId.value, logoFile.value)
    applyProfile(response.profile)
    clearSelectedLogo()
    savedText.value = 'LOGO 已更新'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    logoSaving.value = false
  }
}

async function removeLogo(): Promise<void> {
  if (logoSaving.value) {
    return
  }

  logoSaving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await clearTenantProfileLogo(storeId.value)
    applyProfile(response.profile)
    clearSelectedLogo()
    savedText.value = 'LOGO 已清空'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    logoSaving.value = false
  }
}

function handleLogoFileChange(event: Event): void {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0] || null
  logoFile.value = file
  revokeLocalLogoPreview()
  if (file) {
    localLogoPreviewUrl.value = URL.createObjectURL(file)
  }
}

function clearSelectedLogo(): void {
  logoFile.value = null
  revokeLocalLogoPreview()
  if (logoFileInput.value) {
    logoFileInput.value.value = ''
  }
}

function revokeLocalLogoPreview(): void {
  if (localLogoPreviewUrl.value) {
    URL.revokeObjectURL(localLogoPreviewUrl.value)
    localLogoPreviewUrl.value = ''
  }
}

function applyProfile(profile: TenantAdminProfile): void {
  Object.assign(form, {
    tenantCode: profile.tenantCode,
    displayName: profile.displayName,
    status: profile.status,
    defaultLocale: profile.defaultLocale || 'zh-CN',
    contactPhone: profile.contactPhone || '',
    address: profile.address || '',
    principalName: profile.principalName || '',
    logoMediaUrl: profile.logoMediaUrl || ''
  } satisfies TenantProfileForm)
}

function applyShareProfile(profile: TenantAdminShareProfile): void {
  Object.assign(shareForm, {
    storeDisplayName: profile.storeDisplayName,
    shareDisplayName: profile.shareDisplayName,
    googleMapUrl: profile.googleMapUrl,
    shareEmail: profile.shareEmail,
    whatsappBusinessPhoneE164: profile.whatsappBusinessPhoneE164,
    reservationShareNote: profile.reservationShareNote,
    reservationShareTemplate: profile.reservationShareTemplate,
    usesDefaultReservationShareTemplate: profile.usesDefaultReservationShareTemplate
  } satisfies TenantShareProfileForm)
}

function toPayload(): TenantAdminProfileMutation {
  return {
    displayName: form.displayName.trim(),
    defaultLocale: optionalValue(String(form.defaultLocale || '')),
    contactPhone: optionalValue(String(form.contactPhone || '')),
    address: optionalValue(String(form.address || '')),
    principalName: optionalValue(String(form.principalName || ''))
  }
}

function toSharePayload(): TenantAdminShareProfileMutation {
  return {
    shareDisplayName: optionalValue(shareForm.shareDisplayName),
    googleMapUrl: optionalValue(shareForm.googleMapUrl),
    shareEmail: optionalValue(shareForm.shareEmail),
    whatsappBusinessPhoneE164: optionalValue(shareForm.whatsappBusinessPhoneE164),
    reservationShareNote: optionalValue(shareForm.reservationShareNote),
    reservationShareTemplate: shareForm.usesDefaultReservationShareTemplate
      ? null
      : optionalValue(shareForm.reservationShareTemplate)
  }
}

function optionalValue(value: string): string | null {
  const normalized = value.trim()
  return normalized ? normalized : null
}

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    created: '已创建',
    active: '启用',
    suspended: '停用',
    closed: '关闭'
  }
  return labels[status] || status || '-'
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof TenantAdminApiError)) {
    return '操作失败'
  }
  if (error.status === 401) {
    auth.clear()
    return '登录已失效'
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return '请检查租户名称和默认语言'
  }
  if (error.response.error.code === 'STORE_SCOPE_MISMATCH') {
    return '没有该店面的后台权限'
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return '没有租户后台权限'
  }
  if (error.response.error.code === 'TENANT_PROFILE_NOT_FOUND') {
    return '租户资料不存在'
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
          <h1>租户资料</h1>
        </div>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner" role="status">{{ savedText }}</p>
      <p v-if="loading" class="loading-line">加载中</p>

      <form v-else class="form-panel" @submit.prevent="submitProfile">
        <section class="section-panel">
          <div class="section-heading">
            <h2>基础信息</h2>
          </div>

          <div class="field-grid">
            <label>
              <span>租户代码</span>
              <input :value="form.tenantCode" readonly />
            </label>
            <label>
              <span>状态</span>
              <input :value="statusLabel(form.status)" readonly />
            </label>
            <label>
              <span>租户名称</span>
              <input v-model.trim="form.displayName" required />
            </label>
            <label>
              <span>默认语言</span>
              <input v-model.trim="form.defaultLocale" required />
            </label>
            <label>
              <span>负责人</span>
              <input v-model.trim="form.principalName" />
            </label>
            <label>
              <span>电话</span>
              <input v-model.trim="form.contactPhone" />
            </label>
            <label class="wide-field">
              <span>租户地址</span>
              <input v-model.trim="form.address" />
            </label>
          </div>
        </section>

        <section class="section-panel">
          <div class="section-heading">
            <h2>门店分享资料</h2>
          </div>

          <div class="field-grid">
            <label>
              <span>后台店名</span>
              <input v-model.trim="shareForm.storeDisplayName" readonly />
            </label>
            <label>
              <span>分享显示名称</span>
              <input v-model.trim="shareForm.shareDisplayName" />
            </label>
            <label>
              <span>Google Map 链接</span>
              <input v-model.trim="shareForm.googleMapUrl" />
            </label>
            <label>
              <span>对外邮箱</span>
              <input v-model.trim="shareForm.shareEmail" type="email" placeholder="booking@example.com" />
            </label>
            <label>
              <span>WhatsApp 固定号码</span>
              <input v-model.trim="shareForm.whatsappBusinessPhoneE164" placeholder="+6588880000" />
            </label>
            <label class="wide-field">
              <span>到店提示</span>
              <input v-model.trim="shareForm.reservationShareNote" />
            </label>
          </div>
        </section>

        <section class="section-panel logo-panel">
          <div class="logo-preview" aria-label="租户 LOGO 预览">
            <img v-if="logoPreviewUrl" :src="logoPreviewUrl" alt="租户 LOGO" />
            <span v-else>LOGO</span>
          </div>

          <div class="logo-control">
            <div class="section-heading">
              <h2>租户 LOGO</h2>
            </div>
            <input
              ref="logoFileInput"
              type="file"
              accept="image/png,image/jpeg,image/webp"
              @change="handleLogoFileChange"
            />
            <div class="button-row">
              <button type="button" class="secondary-button" :disabled="!logoFile || logoSaving" @click="submitLogo">
                {{ logoSaving && logoFile ? '上传中' : '上传 LOGO' }}
              </button>
              <button type="button" class="ghost-button" :disabled="logoSaving" @click="removeLogo">清空 LOGO</button>
            </div>
          </div>
        </section>

        <div class="form-actions">
          <button class="primary-button" type="submit" :disabled="saving">
            {{ saving ? '保存中' : '保存资料' }}
          </button>
        </div>
      </form>
    </section>
  </main>
</template>

<style scoped>
.tenant-shell {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  background: #f3f6f8;
  color: #102033;
}

.tenant-workspace {
  min-width: 0;
  padding: 22px;
}

.page-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
}

.page-heading span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.page-heading h1 {
  margin: 0;
  color: #0f172a;
  font-size: 24px;
}

.error-banner,
.success-banner,
.loading-line {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 6px;
}

.error-banner {
  border: 1px solid #fecaca;
  color: #991b1b;
  background: #fff1f2;
}

.success-banner {
  border: 1px solid #bbf7d0;
  color: #166534;
  background: #f0fdf4;
}

.loading-line,
.section-panel {
  border: 1px solid #dbe3ea;
  background: #ffffff;
}

.loading-line {
  color: #475569;
}

.form-panel {
  width: min(100%, 960px);
  display: grid;
  gap: 16px;
}

.section-panel {
  padding: 18px;
  border-radius: 8px;
}

.section-heading {
  display: grid;
  gap: 4px;
  margin-bottom: 16px;
}

.section-heading h2 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
}

.section-heading span {
  color: #64748b;
  font-size: 13px;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

label {
  display: grid;
  gap: 7px;
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

input {
  width: 100%;
  min-height: 40px;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 9px 10px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
}

input[readonly] {
  color: #64748b;
  background: #f8fafc;
}

input[type='file'] {
  padding: 7px 10px;
}

.wide-field {
  grid-column: 1 / -1;
}

.logo-panel {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.logo-preview {
  width: 84px;
  aspect-ratio: 1;
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: #64748b;
  background: #f8fafc;
  font-size: 13px;
  font-weight: 800;
}

.logo-preview img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #ffffff;
}

.logo-control {
  display: grid;
  gap: 10px;
}

.button-row,
.form-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.form-actions {
  justify-content: flex-end;
}

.primary-button,
.secondary-button,
.ghost-button {
  min-height: 38px;
  border-radius: 6px;
  padding: 0 14px;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.primary-button {
  border: 1px solid #0f766e;
  color: #ffffff;
  background: #0f766e;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  color: #0f172a;
  background: #ffffff;
}

.ghost-button {
  border: 1px solid #dbe3ea;
  color: #64748b;
  background: #f8fafc;
}

.primary-button:disabled,
.secondary-button:disabled,
.ghost-button:disabled {
  opacity: 0.55;
  cursor: default;
}

@media (max-width: 980px) {
  .tenant-shell {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .tenant-workspace {
    padding: 14px;
  }

  .field-grid,
  .logo-panel {
    grid-template-columns: 1fr;
  }

  .logo-preview {
    width: 76px;
  }

  .form-actions {
    justify-content: stretch;
  }

  .form-actions .primary-button {
    width: 100%;
  }
}
</style>
