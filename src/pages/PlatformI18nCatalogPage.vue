<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  I18nCatalogApiError,
  getPlatformI18nCatalog,
  updatePlatformI18nCatalog
} from '../api/i18nCatalogApi'
import PlatformAdminNav from '../components/platform/PlatformAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'
import type {
  I18nCatalogEntry,
  I18nCatalogMessageStatus,
  I18nCatalogResponse
} from '../types/i18nCatalog'

interface DraftLocale {
  locale: string
  message: string
  status: I18nCatalogMessageStatus
  version: number | null
  originalMessage: string
  originalStatus: I18nCatalogMessageStatus
}

interface DraftEntry {
  source: I18nCatalogEntry
  locales: DraftLocale[]
}

const { t } = useI18n()
const auth = useAuthSessionStore()
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const savedText = ref('')
const namespaceFilter = ref('')
const catalog = ref<I18nCatalogResponse | null>(null)
const rows = ref<DraftEntry[]>([])

const namespaceOptions = computed(() => {
  const options = new Set(rows.value.map(row => row.source.key.namespace))
  return Array.from(options).sort((left, right) => left.localeCompare(right))
})
const filteredRows = computed(() => {
  if (!namespaceFilter.value) {
    return rows.value
  }
  return rows.value.filter(row => row.source.key.namespace === namespaceFilter.value)
})

onMounted(() => {
  void loadCatalog()
})

async function loadCatalog(): Promise<void> {
  loading.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    applyCatalog(await getPlatformI18nCatalog())
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function saveCatalog(): Promise<void> {
  if (saving.value) {
    return
  }
  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const messages = rows.value.flatMap(row =>
      row.locales
        .filter(locale => locale.message.trim() !== locale.originalMessage || locale.status !== locale.originalStatus)
        .map(locale => ({
          i18nKey: row.source.key.i18nKey,
          locale: locale.locale,
          message: locale.message.trim(),
          status: locale.status,
          version: locale.version
        }))
    )
    if (messages.length === 0) {
      savedText.value = t('platform.i18nCatalog.messages.noChanges')
      return
    }
    applyCatalog(await updatePlatformI18nCatalog({ messages }))
    savedText.value = t('platform.i18nCatalog.messages.saved')
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

function applyCatalog(response: I18nCatalogResponse): void {
  catalog.value = response
  rows.value = response.entries.map(entry => ({
    source: entry,
    locales: entry.locales.map(locale => ({
      locale: locale.locale,
      message: locale.platformMessage?.message ?? locale.effectiveMessage,
      status: locale.platformMessage?.status ?? 'active',
      version: locale.platformMessage?.version ?? null,
      originalMessage: locale.platformMessage?.message ?? locale.effectiveMessage,
      originalStatus: locale.platformMessage?.status ?? 'active'
    }))
  }))
}

function placeholderText(entry: DraftEntry): string {
  return entry.source.key.placeholderNames.length
    ? entry.source.key.placeholderNames.join(', ')
    : t('platform.i18nCatalog.fields.noPlaceholders')
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof I18nCatalogApiError)) {
    return t('platform.i18nCatalog.errors.operationFailed')
  }
  if (error.status === 401 || error.response.error.code === 'UNAUTHENTICATED') {
    auth.clear()
    return t('platform.i18nCatalog.errors.sessionExpired')
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return t('platform.i18nCatalog.errors.forbidden')
  }
  if (error.response.error.code === 'VERSION_CONFLICT') {
    return t('platform.i18nCatalog.errors.versionConflict')
  }
  if (error.response.error.code === 'PLACEHOLDER_UNKNOWN') {
    return t('platform.i18nCatalog.errors.placeholderUnknown')
  }
  if (error.response.error.code === 'KEY_NOT_ALLOWED') {
    return t('platform.i18nCatalog.errors.keyNotAllowed')
  }
  return t('platform.i18nCatalog.errors.invalid')
}
</script>

<template>
  <main class="platform-shell">
    <PlatformAdminNav />

    <section class="platform-workspace">
      <header class="page-heading">
        <div>
          <span>{{ $t('platform.i18nCatalog.page.kicker') }}</span>
          <h1>{{ $t('platform.i18nCatalog.page.title') }}</h1>
        </div>
        <div class="heading-actions">
          <select v-model="namespaceFilter" :aria-label="$t('platform.i18nCatalog.fields.namespaceFilter')">
            <option value="">{{ $t('platform.i18nCatalog.fields.allNamespaces') }}</option>
            <option v-for="namespace in namespaceOptions" :key="namespace" :value="namespace">
              {{ namespace }}
            </option>
          </select>
          <button class="secondary-button" type="button" :disabled="loading" @click="loadCatalog">
            {{ $t('common.actions.refresh') }}
          </button>
          <button class="primary-button" type="button" :disabled="saving || loading" @click="saveCatalog">
            {{ saving ? $t('common.actions.saving') : $t('common.actions.save') }}
          </button>
        </div>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner" role="status">{{ savedText }}</p>
      <p v-if="loading" class="loading-line">{{ $t('common.actions.loading') }}</p>

      <section v-else class="catalog-panel" :aria-label="$t('platform.i18nCatalog.page.title')">
        <p class="scope-note">{{ $t('platform.i18nCatalog.page.note') }}</p>

        <div v-for="entry in filteredRows" :key="entry.source.key.i18nKey" class="catalog-row">
          <div class="key-column">
            <strong>{{ entry.source.key.displayName }}</strong>
            <code>{{ entry.source.key.i18nKey }}</code>
            <span>{{ entry.source.key.namespace }} / {{ entry.source.key.category }} / {{ entry.source.key.textKind }}</span>
            <small>{{ placeholderText(entry) }}</small>
          </div>

          <div class="locale-columns">
            <label v-for="locale in entry.locales" :key="`${entry.source.key.i18nKey}-${locale.locale}`">
              <span>{{ locale.locale }}</span>
              <textarea v-model.trim="locale.message" rows="3" required />
              <select v-model="locale.status" :aria-label="$t('platform.i18nCatalog.fields.status')">
                <option value="active">{{ $t('platform.i18nCatalog.status.active') }}</option>
                <option value="inactive">{{ $t('platform.i18nCatalog.status.inactive') }}</option>
              </select>
            </label>
          </div>
        </div>

        <p v-if="catalog && filteredRows.length === 0" class="loading-line">
          {{ $t('platform.i18nCatalog.fields.empty') }}
        </p>
      </section>
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
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
}

.page-heading span,
.key-column span,
.key-column small,
label span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.page-heading h1 {
  margin: 0;
  color: #0f172a;
  font-size: 24px;
}

.heading-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.catalog-panel {
  display: grid;
  gap: 12px;
}

.scope-note,
.catalog-row,
.error-banner,
.success-banner,
.loading-line {
  border-radius: 8px;
  background: #ffffff;
}

.scope-note {
  margin: 0;
  padding: 12px 14px;
  border: 1px dashed #cbd5e1;
  color: #475569;
  font-weight: 700;
}

.catalog-row {
  display: grid;
  grid-template-columns: minmax(220px, 0.38fr) minmax(0, 1fr);
  gap: 16px;
  padding: 16px;
  border: 1px solid #dbe3ea;
}

.key-column,
.locale-columns,
label {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.key-column strong {
  color: #0f172a;
  font-size: 16px;
}

.key-column code {
  overflow-wrap: anywhere;
  color: #0f766e;
  font-size: 12px;
}

.locale-columns {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

textarea,
select {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 9px 10px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
}

textarea {
  resize: vertical;
  min-height: 86px;
}

select {
  min-height: 38px;
}

.primary-button,
.secondary-button {
  min-height: 38px;
  border-radius: 6px;
  padding: 0 14px;
  font: inherit;
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
  color: #334155;
  background: #ffffff;
}

.primary-button:disabled,
.secondary-button:disabled {
  opacity: 0.55;
  cursor: default;
}

.error-banner,
.success-banner,
.loading-line {
  margin: 0 0 12px;
  padding: 10px 12px;
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
}

@media (max-width: 980px) {
  .platform-shell,
  .page-heading,
  .catalog-row,
  .locale-columns {
    grid-template-columns: 1fr;
  }

  .heading-actions {
    align-items: stretch;
  }

  .primary-button,
  .secondary-button,
  .heading-actions select {
    width: 100%;
  }
}
</style>
