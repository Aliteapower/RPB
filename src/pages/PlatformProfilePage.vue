<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import {
  PlatformProfileApiError,
  clearPlatformProfileLogo,
  clearPlatformSocialLinkLogo,
  createPlatformSocialLink,
  deletePlatformSocialLink,
  getPlatformProfile,
  updatePlatformProfile,
  updatePlatformSocialLink,
  uploadPlatformProfileLogo,
  uploadPlatformSocialLinkLogo,
  type PlatformProfile,
  type PlatformSocialLink
} from '../api/platformProfileApi'
import PlatformAdminNav from '../components/platform/PlatformAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'

interface EditableSocialLink {
  id: string
  displayName: string
  url: string
  logoMediaUrl: string
  sortOrder: number
  status: 'active' | 'disabled'
  logoFile: File | null
}

const auth = useAuthSessionStore()
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const socialLinks = ref<EditableSocialLink[]>([])
const profileLogoFile = ref<File | null>(null)

const profileForm = reactive({
  platformName: '',
  uen: '',
  address: '',
  phone: '',
  email: '',
  website: '',
  logoMediaUrl: ''
})

const newSocialLink = reactive({
  displayName: '',
  url: '',
  sortOrder: 1,
  status: 'active' as 'active' | 'disabled'
})

onMounted(() => {
  void loadProfile()
})

async function loadProfile(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const response = await getPlatformProfile()
    applyProfile(response.profile)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function saveProfile(): Promise<void> {
  if (saving.value) {
    return
  }
  saving.value = true
  errorText.value = ''
  try {
    const response = await updatePlatformProfile({
      platformName: profileForm.platformName.trim(),
      uen: optionalValue(profileForm.uen),
      address: optionalValue(profileForm.address),
      phone: optionalValue(profileForm.phone),
      email: optionalValue(profileForm.email),
      website: optionalValue(profileForm.website)
    })
    applyProfile(response.profile)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function uploadProfileLogo(): Promise<void> {
  if (!profileLogoFile.value || saving.value) {
    return
  }
  saving.value = true
  errorText.value = ''
  try {
    const response = await uploadPlatformProfileLogo(profileLogoFile.value)
    profileLogoFile.value = null
    applyProfile(response.profile)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function removeProfileLogo(): Promise<void> {
  if (saving.value) {
    return
  }
  saving.value = true
  errorText.value = ''
  try {
    const response = await clearPlatformProfileLogo()
    profileLogoFile.value = null
    applyProfile(response.profile)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function addSocialLink(): Promise<void> {
  if (saving.value) {
    return
  }
  saving.value = true
  errorText.value = ''
  try {
    const response = await createPlatformSocialLink({
      displayName: newSocialLink.displayName.trim(),
      url: newSocialLink.url.trim(),
      sortOrder: newSocialLink.sortOrder,
      status: newSocialLink.status
    })
    Object.assign(newSocialLink, {
      displayName: '',
      url: '',
      sortOrder: response.profile.socialLinks.length + 1,
      status: 'active'
    })
    applyProfile(response.profile)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function saveSocialLink(row: EditableSocialLink): Promise<void> {
  if (saving.value) {
    return
  }
  saving.value = true
  errorText.value = ''
  try {
    const response = await updatePlatformSocialLink(row.id, {
      displayName: row.displayName.trim(),
      url: row.url.trim(),
      sortOrder: row.sortOrder,
      status: row.status
    })
    applyProfile(response.profile)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function removeSocialLink(row: EditableSocialLink): Promise<void> {
  if (saving.value || !window.confirm(`删除社交媒体 ${row.displayName}？`)) {
    return
  }
  saving.value = true
  errorText.value = ''
  try {
    const response = await deletePlatformSocialLink(row.id)
    applyProfile(response.profile)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function uploadSocialLogo(row: EditableSocialLink): Promise<void> {
  if (!row.logoFile || saving.value) {
    return
  }
  saving.value = true
  errorText.value = ''
  try {
    const response = await uploadPlatformSocialLinkLogo(row.id, row.logoFile)
    applyProfile(response.profile)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function removeSocialLogo(row: EditableSocialLink): Promise<void> {
  if (saving.value) {
    return
  }
  saving.value = true
  errorText.value = ''
  try {
    const response = await clearPlatformSocialLinkLogo(row.id)
    applyProfile(response.profile)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

function handleProfileLogoChange(event: Event): void {
  const input = event.target as HTMLInputElement | null
  profileLogoFile.value = input?.files?.[0] ?? null
}

function handleSocialLogoChange(row: EditableSocialLink, event: Event): void {
  const input = event.target as HTMLInputElement | null
  row.logoFile = input?.files?.[0] ?? null
}

function applyProfile(profile: PlatformProfile): void {
  Object.assign(profileForm, {
    platformName: profile.platformName,
    uen: profile.uen || '',
    address: profile.address || '',
    phone: profile.phone || '',
    email: profile.email || '',
    website: profile.website || '',
    logoMediaUrl: profile.logoMediaUrl || ''
  })
  socialLinks.value = profile.socialLinks.map(toEditableSocialLink)
  newSocialLink.sortOrder = profile.socialLinks.length + 1
}

function toEditableSocialLink(link: PlatformSocialLink): EditableSocialLink {
  return {
    id: link.id,
    displayName: link.displayName,
    url: link.url,
    logoMediaUrl: link.logoMediaUrl || '',
    sortOrder: link.sortOrder,
    status: link.status,
    logoFile: null
  }
}

function optionalValue(value: string): string | null {
  const normalized = value.trim()
  return normalized ? normalized : null
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof PlatformProfileApiError)) {
    return '操作失败'
  }
  if (error.status === 401) {
    auth.clear()
    return '登录已失效'
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return '没有平台后台权限'
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return '请检查必填项'
  }
  return '操作失败'
}
</script>

<template>
  <main class="platform-shell">
    <PlatformAdminNav />

    <section class="platform-workspace">
      <header class="page-heading">
        <div>
          <span>平台</span>
          <h1>平台资料</h1>
        </div>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="loading" class="loading-line">加载中</p>

      <template v-else>
        <section class="settings-panel" aria-label="平台资料">
          <div class="section-heading">
            <h2>平台资料</h2>
            <button class="primary-button" type="button" :disabled="saving" @click="saveProfile">保存</button>
          </div>

          <div class="profile-grid">
            <label>
              <span>平台名称</span>
              <input v-model.trim="profileForm.platformName" required maxlength="120" autocomplete="off" />
            </label>
            <label>
              <span>UEN</span>
              <input v-model.trim="profileForm.uen" maxlength="60" autocomplete="off" />
            </label>
            <label class="span-2">
              <span>地址</span>
              <input v-model.trim="profileForm.address" maxlength="240" autocomplete="off" />
            </label>
            <label>
              <span>电话</span>
              <input v-model.trim="profileForm.phone" maxlength="40" autocomplete="off" />
            </label>
            <label>
              <span>电邮</span>
              <input v-model.trim="profileForm.email" maxlength="120" autocomplete="off" />
            </label>
            <label class="span-2">
              <span>网址</span>
              <input v-model.trim="profileForm.website" maxlength="160" autocomplete="off" />
            </label>
          </div>

          <div class="logo-line">
            <div class="logo-preview" :class="{ empty: !profileForm.logoMediaUrl }">
              <img v-if="profileForm.logoMediaUrl" :src="profileForm.logoMediaUrl" alt="平台 LOGO" />
              <span v-else>LOGO</span>
            </div>
            <label>
              <span>平台 LOGO</span>
              <input type="file" accept="image/jpeg,image/png,image/webp" @change="handleProfileLogoChange" />
            </label>
            <button class="secondary-button" type="button" :disabled="!profileLogoFile || saving" @click="uploadProfileLogo">
              上传
            </button>
            <button class="secondary-button" type="button" :disabled="!profileForm.logoMediaUrl || saving" @click="removeProfileLogo">
              清空
            </button>
          </div>
        </section>

        <section class="settings-panel" aria-label="社交媒体">
          <div class="section-heading">
            <h2>社交媒体</h2>
          </div>

          <div class="social-create">
            <input v-model.trim="newSocialLink.displayName" maxlength="80" placeholder="名称" />
            <input v-model.trim="newSocialLink.url" maxlength="240" placeholder="URL" />
            <input v-model.number="newSocialLink.sortOrder" type="number" min="1" aria-label="排序" />
            <select v-model="newSocialLink.status">
              <option value="active">启用</option>
              <option value="disabled">停用</option>
            </select>
            <button class="primary-button" type="button" :disabled="saving" @click="addSocialLink">新增</button>
          </div>

          <div class="social-list">
            <article v-for="row in socialLinks" :key="row.id" class="social-row">
              <div class="social-logo" :class="{ empty: !row.logoMediaUrl }">
                <img v-if="row.logoMediaUrl" :src="row.logoMediaUrl" :alt="`${row.displayName} 社媒 LOGO`" />
                <span v-else>社媒 LOGO</span>
              </div>
              <input v-model.trim="row.displayName" maxlength="80" aria-label="社交媒体名称" />
              <input v-model.trim="row.url" maxlength="240" aria-label="社交媒体 URL" />
              <input v-model.number="row.sortOrder" type="number" min="1" aria-label="排序" />
              <select v-model="row.status" aria-label="状态">
                <option value="active">启用</option>
                <option value="disabled">停用</option>
              </select>
              <input type="file" accept="image/jpeg,image/png,image/webp" aria-label="社媒 LOGO" @change="handleSocialLogoChange(row, $event)" />
              <button class="secondary-button" type="button" :disabled="saving" @click="saveSocialLink(row)">保存</button>
              <button class="secondary-button" type="button" :disabled="!row.logoFile || saving" @click="uploadSocialLogo(row)">上传 LOGO</button>
              <button class="secondary-button" type="button" :disabled="!row.logoMediaUrl || saving" @click="removeSocialLogo(row)">清空</button>
              <button class="danger-button" type="button" :disabled="saving" @click="removeSocialLink(row)">删除</button>
            </article>
            <p v-if="!socialLinks.length" class="empty-line">暂无社交媒体</p>
          </div>
        </section>
      </template>
    </section>
  </main>
</template>

<style scoped>
.platform-shell {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  background: #f3f6f8;
  color: #102033;
}

.platform-workspace {
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

.settings-panel {
  display: grid;
  gap: 16px;
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.section-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.section-heading h2 {
  margin: 0;
  font-size: 18px;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

label {
  display: grid;
  gap: 7px;
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

.span-2 {
  grid-column: span 2;
}

input,
select {
  min-height: 38px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 8px 10px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
}

.logo-line {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  gap: 10px;
  align-items: end;
}

.logo-preview,
.social-logo {
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
}

.logo-preview {
  width: 74px;
  height: 74px;
}

.social-logo {
  width: 52px;
  height: 52px;
}

.logo-preview img,
.social-logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.logo-preview span,
.social-logo span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  text-align: center;
}

.social-create,
.social-row {
  display: grid;
  grid-template-columns: minmax(120px, 0.8fr) minmax(180px, 1.3fr) 84px 92px auto;
  gap: 8px;
  align-items: center;
}

.social-row {
  grid-template-columns: auto minmax(120px, 0.8fr) minmax(180px, 1.2fr) 76px 86px minmax(160px, 1fr) auto auto auto auto;
  padding: 10px 0;
  border-top: 1px solid #edf2f7;
}

.social-list {
  display: grid;
}

.primary-button,
.secondary-button,
.danger-button {
  min-height: 36px;
  border-radius: 6px;
  padding: 0 12px;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.primary-button {
  border: 0;
  color: #ffffff;
  background: #0f766e;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  color: #1e3a5f;
  background: #ffffff;
}

.danger-button {
  border: 1px solid #fecaca;
  color: #991b1b;
  background: #fff1f2;
}

.primary-button:disabled,
.secondary-button:disabled,
.danger-button:disabled {
  opacity: 0.55;
  cursor: default;
}

.error-banner,
.loading-line,
.empty-line {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 6px;
}

.error-banner {
  border: 1px solid #fecaca;
  color: #991b1b;
  background: #fff1f2;
}

.loading-line,
.empty-line {
  border: 1px solid #dbe3ea;
  color: #475569;
  background: #ffffff;
}

@media (max-width: 1200px) {
  .social-create,
  .social-row {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 980px) {
  .platform-shell {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .platform-workspace {
    padding: 14px;
  }

  .profile-grid,
  .logo-line,
  .social-create,
  .social-row {
    grid-template-columns: 1fr;
  }

  .span-2 {
    grid-column: auto;
  }
}
</style>
