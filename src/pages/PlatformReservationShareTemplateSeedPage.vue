<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

import {
  PlatformReservationShareTemplateSeedApiError,
  getPlatformReservationShareTemplateSeed,
  updatePlatformReservationShareTemplateSeed
} from '../api/platformReservationShareTemplateSeedApi'
import { getPlatformProfile } from '../api/platformProfileApi'
import PlatformAdminNav from '../components/platform/PlatformAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'
import type { PlatformProfile } from '../api/platformProfileApi'
import type {
  PlatformReservationShareTemplateSeed,
  PlatformReservationShareTemplateSeedMutation,
  PlatformReservationShareTemplateSeedStatus
} from '../types/platformReservationShareTemplateSeed'
import {
  buildReservationShareTemplatePreviewVariables,
  renderReservationShareTemplatePreview
} from '../utils/reservationShareTemplatePreview'
import { useGeneratedText } from '../i18n/generatedText'

const { gt } = useGeneratedText()

const auth = useAuthSessionStore()
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const savedText = ref('')
const availableVariables = ref<string[]>(['guestSalutation', 'tableCode', 'holdMinutes'])
const previewVariables = ref(buildReservationShareTemplatePreviewVariables())

const form = reactive({
  seedKey: '',
  displayName: '',
  locale: 'zh-CN',
  templateText: '',
  status: 'active' as PlatformReservationShareTemplateSeedStatus,
  version: 0
})

const previewText = computed(() => renderReservationShareTemplatePreview(form.templateText, previewVariables.value))

onMounted(() => {
  void loadSeed()
})

async function loadSeed(): Promise<void> {
  loading.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const [seedResponse, profileResponse] = await Promise.all([
      getPlatformReservationShareTemplateSeed(),
      getPlatformProfile().catch(() => null)
    ])
    applySeed(seedResponse.seed)
    if (profileResponse) {
      applyPlatformProfilePreviewSource(profileResponse.profile)
    }
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function saveSeed(): Promise<void> {
  if (saving.value) {
    return
  }

  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await updatePlatformReservationShareTemplateSeed(toMutation())
    applySeed(response.seed)
    savedText.value = gt('generated.platform-reservation-share-template-seed.021')
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

function applySeed(seed: PlatformReservationShareTemplateSeed): void {
  form.seedKey = seed.seedKey
  form.displayName = seed.displayName
  form.locale = seed.locale
  form.templateText = seed.templateText
  form.status = seed.status
  form.version = seed.version
  availableVariables.value = seed.allowedVariables
}

function applyPlatformProfilePreviewSource(profile: PlatformProfile): void {
  previewVariables.value = buildReservationShareTemplatePreviewVariables({
    storeName: profile.platformName,
    storeAddress: profile.address,
    storePhone: profile.phone,
    googleMapUrl: platformMapUrl(profile)
  })
}

function toMutation(): PlatformReservationShareTemplateSeedMutation {
  return {
    displayName: form.displayName.trim(),
    locale: form.locale.trim(),
    templateText: form.templateText,
    status: form.status,
    version: form.version
  }
}

function insertVariable(variable: string): void {
  const token = `{{${variable}}}`
  form.templateText = form.templateText ? `${form.templateText}\n${token}` : token
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof PlatformReservationShareTemplateSeedApiError)) {
    return gt('generated.platform-reservation-share-template-seed.022')
  }
  if (error.status === 401) {
    auth.clear()
    return gt('generated.platform-reservation-share-template-seed.023')
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return gt('generated.platform-reservation-share-template-seed.024')
  }
  if (error.response.error.code === 'TEMPLATE_UNKNOWN_VARIABLE') {
    return gt('generated.platform-reservation-share-template-seed.025')
  }
  if (error.response.error.code === 'VERSION_CONFLICT') {
    return gt('generated.platform-reservation-share-template-seed.026')
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return gt('generated.platform-reservation-share-template-seed.027')
  }
  return gt('generated.platform-reservation-share-template-seed.028')
}

function platformMapUrl(profile: PlatformProfile): string | null {
  const mapLink = profile.socialLinks.find(link =>
    link.status === 'active' &&
    `${link.displayName} ${link.url}`.toLowerCase().includes('map')
  )
  return mapLink?.url || null
}
</script>

<template>
  <main class="platform-shell">
    <PlatformAdminNav />

    <section class="platform-workspace">
      <header class="page-heading">
        <div>
          <span>{{ gt('generated.platform-reservation-share-template-seed.001') }}</span>
          <h1>{{ gt('generated.platform-reservation-share-template-seed.002') }}</h1>
        </div>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner" role="status">{{ savedText }}</p>
      <p v-if="loading" class="loading-line">{{ gt('generated.platform-reservation-share-template-seed.003') }}</p>

      <form v-else class="settings-panel" :aria-label="gt('generated.platform-reservation-share-template-seed.004')" @submit.prevent="saveSeed">
        <div class="section-heading">
          <h2>{{ gt('generated.platform-reservation-share-template-seed.005') }}</h2>
          <button class="primary-button" type="submit" :disabled="saving">{{ saving ? gt('generated.platform-reservation-share-template-seed.006') : gt('generated.platform-reservation-share-template-seed.007') }}</button>
        </div>

        <div class="profile-grid">
          <label>
            <span>{{ gt('generated.platform-reservation-share-template-seed.008') }}</span>
            <input v-model="form.seedKey" disabled autocomplete="off" />
          </label>
          <label>
            <span>{{ gt('generated.platform-reservation-share-template-seed.009') }}</span>
            <input v-model.number="form.version" disabled autocomplete="off" />
          </label>
          <label>
            <span>{{ gt('generated.platform-reservation-share-template-seed.010') }}</span>
            <input v-model.trim="form.displayName" required maxlength="120" autocomplete="off" />
          </label>
          <label>
            <span>{{ gt('generated.platform-reservation-share-template-seed.011') }}</span>
            <input v-model.trim="form.locale" required maxlength="20" autocomplete="off" />
          </label>
          <label>
            <span>{{ gt('generated.platform-reservation-share-template-seed.012') }}</span>
            <select v-model="form.status">
              <option value="active">{{ gt('generated.platform-reservation-share-template-seed.013') }}</option>
              <option value="disabled">{{ gt('generated.platform-reservation-share-template-seed.014') }}</option>
            </select>
          </label>
        </div>

        <section class="template-tools" :aria-label="gt('generated.platform-reservation-share-template-seed.015')">
          <p class="template-tools__hint"> {{ gt('generated.platform-reservation-share-template-seed.016') }} </p>
          <div class="template-tools__buttons">
            <button
              v-for="variable in availableVariables"
              :key="variable"
              type="button"
              @click="insertVariable(variable)"
            >
              {{ variable }}
            </button>
          </div>
        </section>

        <section class="template-layout">
          <label class="template-editor">
            <span>{{ gt('generated.platform-reservation-share-template-seed.017') }}</span>
            <textarea v-model="form.templateText" rows="22" required></textarea>
          </label>

          <aside class="preview-panel" :aria-label="gt('generated.platform-reservation-share-template-seed.018')">
            <div class="preview-heading">
              <span>{{ gt('generated.platform-reservation-share-template-seed.019') }}</span>
              <strong>{{ gt('generated.platform-reservation-share-template-seed.020') }}</strong>
            </div>
            <pre>{{ previewText }}</pre>
          </aside>
        </section>
      </form>
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
  width: min(100%, 980px);
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

input,
select,
textarea {
  min-height: 38px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  box-sizing: border-box;
  padding: 8px 10px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
  width: 100%;
}

input:disabled {
  color: #64748b;
  background: #f8fafc;
}

textarea {
  line-height: 1.5;
  resize: vertical;
}

.template-tools {
  display: grid;
  gap: 10px;
}

.template-tools__hint {
  color: #475569;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.5;
  margin: 0;
}

.template-tools__buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.template-tools__buttons button {
  min-height: 36px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #334155;
  background: #ffffff;
  font: inherit;
  font-weight: 800;
  padding: 0 12px;
  cursor: pointer;
}

.template-editor {
  min-width: 0;
}

.template-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(300px, 0.75fr);
  gap: 14px;
  align-items: stretch;
}

.preview-panel {
  min-width: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 12px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  padding: 12px;
  background: #f8fafc;
}

.preview-heading {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: baseline;
}

.preview-heading span {
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
}

.preview-heading strong {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.preview-panel pre {
  min-height: 0;
  margin: 0;
  overflow: auto;
  color: #0f172a;
  font: inherit;
  line-height: 1.55;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.primary-button {
  min-height: 36px;
  border: 0;
  border-radius: 6px;
  color: #ffffff;
  background: #0f766e;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
  padding: 0 12px;
  cursor: pointer;
}

.primary-button:disabled {
  opacity: 0.55;
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

@media (max-width: 980px) {
  .platform-shell,
  .profile-grid,
  .template-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .platform-workspace {
    padding: 14px;
  }

  .section-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .preview-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .preview-heading strong {
    white-space: normal;
  }
}
</style>
