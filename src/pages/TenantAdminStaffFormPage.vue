<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  clearTenantProfileLogo,
  createStaff,
  getCurrentTenantAdminStaff,
  getStaff,
  getTenantProfile,
  TenantAdminApiError,
  updateCurrentTenantAdminStaff,
  updateStaff,
  updateTenantProfile,
  uploadTenantProfileLogo,
  type TenantAdminProfile,
  type TenantAdminProfileMutation,
  type TenantAdminStaff,
  type TenantAdminStaffMutation
} from '../api/tenantAdminApi'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'

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

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()
const loading = ref(false)
const saving = ref(false)
const logoSaving = ref(false)
const errorText = ref('')
const savedText = ref('')
const logoFile = ref<File | null>(null)
const logoFileInput = ref<HTMLInputElement | null>(null)
const localLogoPreviewUrl = ref('')

const mode = computed<'create' | 'edit' | 'self'>(() => {
  if (route.name === 'tenant-admin-staff-create') {
    return 'create'
  }
  return route.name === 'tenant-admin-staff-self-edit' ? 'self' : 'edit'
})
const pageTitle = computed(() => {
  if (mode.value === 'create') {
    return '新增员工'
  }
  return mode.value === 'self' ? '租户资料与管理员账号' : '编辑员工'
})
const statusFieldVisible = computed(() => mode.value !== 'self')
const storeId = computed(() => String(route.params.storeId || ''))
const staffId = computed(() => String(route.params.staffId || ''))
const logoPreviewUrl = computed(() => localLogoPreviewUrl.value || tenantProfileForm.logoMediaUrl)

const tenantProfileForm = reactive<TenantProfileForm>({
  tenantCode: '',
  displayName: '',
  status: '',
  defaultLocale: 'zh-CN',
  contactPhone: '',
  address: '',
  principalName: '',
  logoMediaUrl: ''
})

const form = reactive({
  employeeNo: '',
  name: '',
  phone: '',
  email: '',
  status: 'active' as TenantAdminStaffMutation['status'],
  password: ''
})

onMounted(() => {
  if (mode.value !== 'create') {
    void loadStaff()
  }
})

onUnmounted(() => {
  revokeLocalLogoPreview()
})

async function loadStaff(): Promise<void> {
  loading.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    if (mode.value === 'self') {
      const [profileResponse, staffResponse] = await Promise.all([
        getTenantProfile(storeId.value),
        getCurrentTenantAdminStaff(storeId.value)
      ])
      applyTenantProfile(profileResponse.profile)
      applyAdminAccount(staffResponse.staff)
      return
    }

    const response = await getStaff(storeId.value, staffId.value)
    applyAdminAccount(response.staff)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function submitStaff(): Promise<void> {
  if (saving.value) {
    return
  }
  if (mode.value === 'self' && !validateSelfForm()) {
    return
  }

  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    if (mode.value === 'create') {
      await createStaff(storeId.value, toPayload())
    } else if (mode.value === 'self') {
      const [profileResponse, staffResponse] = await Promise.all([
        updateTenantProfile(storeId.value, tenantProfilePayload()),
        updateCurrentTenantAdminStaff(storeId.value, adminAccountPayload())
      ])
      applyTenantProfile(profileResponse.profile)
      applyAdminAccount(staffResponse.staff)
      savedText.value = '已保存'
      if (logoFile.value) {
        await uploadSelectedLogo('资料和 LOGO 已保存')
      }
    } else {
      await updateStaff(storeId.value, staffId.value, toPayload())
    }
    await router.push({ name: 'tenant-admin-staff', params: { storeId: storeId.value } })
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function submitLogo(): Promise<void> {
  if (logoSaving.value || saving.value || !logoFile.value) {
    return
  }

  errorText.value = ''
  savedText.value = ''
  try {
    await uploadSelectedLogo('LOGO 已更新')
  } catch (error) {
    errorText.value = apiErrorText(error)
  }
}

async function uploadSelectedLogo(successMessage: string): Promise<void> {
  if (!logoFile.value) {
    return
  }

  logoSaving.value = true
  try {
    const response = await uploadTenantProfileLogo(storeId.value, logoFile.value)
    applyTenantProfile(response.profile)
    clearSelectedLogo()
    savedText.value = successMessage
  } finally {
    logoSaving.value = false
  }
}

async function removeLogo(): Promise<void> {
  if (logoSaving.value || saving.value) {
    return
  }

  logoSaving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await clearTenantProfileLogo(storeId.value)
    applyTenantProfile(response.profile)
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

function applyTenantProfile(profile: TenantAdminProfile): void {
  Object.assign(tenantProfileForm, {
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

function applyAdminAccount(staff: TenantAdminStaff): void {
  Object.assign(form, {
    employeeNo: staff.employeeNo,
    name: staff.name,
    phone: staff.phone || '',
    email: staff.email || '',
    status: staff.status,
    password: ''
  })
}

function tenantProfilePayload(): TenantAdminProfileMutation {
  return {
    displayName: tenantProfileForm.displayName.trim(),
    defaultLocale: optionalValue(String(tenantProfileForm.defaultLocale || '')),
    contactPhone: optionalValue(String(tenantProfileForm.contactPhone || '')),
    address: optionalValue(String(tenantProfileForm.address || '')),
    principalName: optionalValue(String(tenantProfileForm.principalName || ''))
  }
}

function adminAccountPayload(): TenantAdminStaffMutation {
  return {
    name: form.name.trim(),
    email: optionalValue(form.email),
    password: optionalValue(form.password)
  }
}

function validateSelfForm(): boolean {
  errorText.value = ''
  savedText.value = ''
  if (!tenantProfileForm.displayName.trim() || !tenantProfileForm.defaultLocale.trim()) {
    errorText.value = '请检查租户名称和默认语言'
    return false
  }
  if (form.password.trim() && !/^[A-Za-z0-9]{6}$/.test(form.password.trim())) {
    errorText.value = '密码为 6 位数字或英文字母'
    return false
  }
  return true
}

function toPayload(): TenantAdminStaffMutation {
  return {
    employeeNo: mode.value === 'create' ? form.employeeNo.trim() : undefined,
    name: form.name.trim(),
    phone: optionalValue(form.phone),
    email: optionalValue(form.email),
    status: form.status,
    password: mode.value === 'create' ? form.password.trim() : optionalValue(form.password)
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
  if (error.response.error.code === 'STAFF_CODE_CONFLICT') {
    return '员工号已存在'
  }
  if (error.response.error.code === 'STAFF_NOT_FOUND') {
    return '员工不存在'
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return mode.value === 'self' ? '请检查租户名称、默认语言和 6 位密码' : '请检查必填项和 6 位密码'
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
          <span>员工管理</span>
          <h1>{{ pageTitle }}</h1>
        </div>
        <button type="button" class="secondary-button" @click="router.push({ name: 'tenant-admin-staff', params: { storeId } })">
          返回列表
        </button>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner" role="status">{{ savedText }}</p>
      <p v-if="loading" class="loading-line">加载中</p>

      <form v-else class="form-panel" :class="{ 'self-form': mode === 'self' }" @submit.prevent="submitStaff">
        <template v-if="mode === 'self'">
          <section class="section-panel">
            <div class="section-heading">
              <h2>租户资料</h2>
            </div>

            <div class="field-grid">
              <label>
                <span>租户代码</span>
                <input :value="tenantProfileForm.tenantCode" readonly />
              </label>
              <label>
                <span>租户名称</span>
                <input v-model.trim="tenantProfileForm.displayName" required />
              </label>
              <label>
                <span>状态</span>
                <input :value="statusLabel(tenantProfileForm.status)" readonly />
              </label>
              <label>
                <span>默认语言</span>
                <input v-model.trim="tenantProfileForm.defaultLocale" required />
              </label>
              <label>
                <span>负责人</span>
                <input v-model.trim="tenantProfileForm.principalName" />
              </label>
              <label>
                <span>电话</span>
                <input v-model.trim="tenantProfileForm.contactPhone" inputmode="tel" />
              </label>
              <label class="wide-field">
                <span>租户地址</span>
                <input v-model.trim="tenantProfileForm.address" />
              </label>
            </div>
          </section>

          <section class="section-panel logo-panel">
            <div class="logo-preview" aria-label="租户 LOGO 预览">
              <img v-if="logoPreviewUrl" :src="logoPreviewUrl" alt="租户 LOGO" />
              <span v-else>LOGO</span>
            </div>

            <div class="logo-control">
              <div class="section-heading compact-heading">
                <h2>租户 LOGO</h2>
              </div>
              <input
                ref="logoFileInput"
                type="file"
                accept="image/png,image/jpeg,image/webp"
                @change="handleLogoFileChange"
              />
              <div class="button-row">
                <button type="button" class="secondary-button" :disabled="!logoFile || logoSaving || saving" @click="submitLogo">
                  {{ logoSaving && logoFile ? '上传中' : '上传 LOGO' }}
                </button>
                <button type="button" class="ghost-button" :disabled="logoSaving || saving" @click="removeLogo">清空 LOGO</button>
              </div>
            </div>
          </section>

          <section class="section-panel">
            <div class="section-heading">
              <h2>管理员账号</h2>
            </div>

            <div class="field-grid">
              <label>
                <span>员工号</span>
                <input v-model.trim="form.employeeNo" readonly required />
              </label>
              <label>
                <span>姓名</span>
                <input v-model.trim="form.name" required />
              </label>
              <label>
                <span>电邮</span>
                <input v-model.trim="form.email" type="email" />
              </label>
              <label>
                <span>修改密码</span>
                <input v-model="form.password" type="password" maxlength="6" pattern="[A-Za-z0-9]{6}" />
                <small>密码为 6 位数字或英文字母</small>
              </label>
            </div>
          </section>
        </template>

        <template v-else>
          <label>
            <span>员工号</span>
            <input v-model.trim="form.employeeNo" :readonly="mode !== 'create'" required />
          </label>
          <label>
            <span>姓名</span>
            <input v-model.trim="form.name" required />
          </label>
          <label>
            <span>电话</span>
            <input v-model.trim="form.phone" inputmode="tel" />
          </label>
          <label>
            <span>电邮</span>
            <input v-model.trim="form.email" type="email" />
          </label>
          <label v-if="statusFieldVisible">
            <span>状态</span>
            <select v-model="form.status">
              <option value="active">启用</option>
              <option value="disabled">停用</option>
              <option value="locked">锁定</option>
            </select>
          </label>
          <label>
            <span>{{ mode === 'create' ? '初始密码' : '修改密码' }}</span>
            <input v-model="form.password" type="password" maxlength="6" :required="mode === 'create'" />
            <small>密码为 6 位数字或英文字母</small>
          </label>
        </template>

        <div class="form-actions">
          <button class="primary-button" type="submit" :disabled="saving || logoSaving">
            {{ saving ? '保存中' : mode === 'self' ? '保存资料' : '保存' }}
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

.secondary-button,
.primary-button,
.ghost-button {
  min-height: 38px;
  border-radius: 6px;
  padding: 0 14px;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  color: #0f172a;
  background: #ffffff;
}

.primary-button {
  border: 0;
  color: #ffffff;
  background: #0f766e;
}

.ghost-button {
  border: 1px solid #dbe3ea;
  color: #64748b;
  background: #f8fafc;
}

.primary-button:disabled,
.secondary-button:disabled,
.ghost-button:disabled {
  opacity: 0.6;
  cursor: default;
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

.loading-line {
  border: 1px solid #dbe3ea;
  color: #475569;
  background: #ffffff;
}

.form-panel {
  width: min(100%, 760px);
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding: 18px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.form-panel.self-form {
  width: min(100%, 960px);
  grid-template-columns: 1fr;
  padding: 0;
  border: 0;
  background: transparent;
}

.section-panel {
  padding: 18px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
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

.compact-heading {
  margin-bottom: 4px;
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

input,
select {
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

small {
  color: #64748b;
  font-weight: 500;
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
  grid-column: 1 / -1;
  justify-content: flex-end;
}

.self-form .form-actions {
  padding-top: 0;
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

  .page-heading,
  .form-panel,
  .field-grid,
  .logo-panel {
    display: grid;
    grid-template-columns: 1fr;
  }

  .logo-preview {
    width: 76px;
  }

  .form-actions {
    justify-content: stretch;
  }

  .secondary-button,
  .primary-button,
  .ghost-button {
    width: 100%;
  }
}
</style>
