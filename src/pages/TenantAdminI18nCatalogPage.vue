<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

import {
  I18nCatalogApiError,
  getTenantAdminI18nCatalog,
  updateTenantAdminI18nCatalog
} from '../api/i18nCatalogApi'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'
import type {
  I18nCatalogEntry,
  I18nCatalogLocaleEntry,
  I18nCatalogMessage,
  I18nCatalogMessageMutation,
  I18nCatalogResponse,
  I18nCatalogScopeLevel
} from '../types/i18nCatalog'

interface DraftLocale {
  locale: string
  message: string
  existing: I18nCatalogMessage | null
  effectiveMessage: string
  effectiveSource: string
  platformMessage: string
}

interface DraftEntry {
  source: I18nCatalogEntry
  locales: DraftLocale[]
}

const { t } = useI18n()
const route = useRoute()
const auth = useAuthSessionStore()
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const savedText = ref('')
const scopeLevel = ref<I18nCatalogScopeLevel>('store')
const namespaceFilter = ref('')
const catalog = ref<I18nCatalogResponse | null>(null)
const rows = ref<DraftEntry[]>([])

const storeId = computed(() => String(route.params.storeId || ''))
const namespaceOptions = computed(() => {
  const options = new Set((catalog.value?.entries ?? []).map(entry => entry.key.namespace))
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

watch(scopeLevel, () => rebuildRows())

async function loadCatalog(): Promise<void> {
  loading.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    catalog.value = await getTenantAdminI18nCatalog(storeId.value)
    rebuildRows()
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
  const messages: I18nCatalogMessageMutation[] = []
  for (const row of rows.value) {
    for (const locale of row.locales) {
      const message = locale.message.trim()
      if (message) {
        if (locale.existing && locale.existing.message === message) {
          continue
        }
        messages.push({
          i18nKey: row.source.key.i18nKey,
          locale: locale.locale,
          message,
          status: 'active' as const,
          version: locale.existing?.version ?? null
        })
        continue
      }
      if (locale.existing) {
        messages.push({
          i18nKey: row.source.key.i18nKey,
          locale: locale.locale,
          version: locale.existing.version,
          clear: true
        })
      }
    }
  }
  if (messages.length === 0) {
    savedText.value = t('tenant.i18nCatalog.messages.noChanges')
    return
  }

  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    catalog.value = await updateTenantAdminI18nCatalog(storeId.value, {
      scopeLevel: scopeLevel.value,
      messages
    })
    rebuildRows()
    savedText.value = t('tenant.i18nCatalog.messages.saved')
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

function rebuildRows(): void {
  rows.value = (catalog.value?.entries ?? []).map(entry => ({
    source: entry,
    locales: entry.locales.map(toDraftLocale)
  }))
}

function toDraftLocale(locale: I18nCatalogLocaleEntry): DraftLocale {
  const existing = scopeLevel.value === 'store' ? locale.storeOverride : locale.tenantOverride
  return {
    locale: locale.locale,
    message: existing?.message ?? '',
    existing,
    effectiveMessage: locale.effectiveMessage,
    effectiveSource: locale.effectiveSource,
    platformMessage: locale.platformMessage?.message ?? ''
  }
}

function clearDraft(locale: DraftLocale): void {
  locale.message = ''
}

function sourceLabel(source: string): string {
  if (source === 'store') {
    return t('tenant.i18nCatalog.sources.store')
  }
  if (source === 'tenant') {
    return t('tenant.i18nCatalog.sources.tenant')
  }
  if (source === 'platform') {
    return t('tenant.i18nCatalog.sources.platform')
  }
  return t('tenant.i18nCatalog.sources.frontend')
}

function placeholderText(entry: DraftEntry): string {
  return entry.source.key.placeholderNames.length
    ? entry.source.key.placeholderNames.join(', ')
    : t('tenant.i18nCatalog.fields.noPlaceholders')
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof I18nCatalogApiError)) {
    return t('tenant.i18nCatalog.errors.operationFailed')
  }
  if (error.status === 401 || error.response.error.code === 'UNAUTHENTICATED') {
    auth.clear()
    return t('tenant.i18nCatalog.errors.sessionExpired')
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return t('tenant.i18nCatalog.errors.forbidden')
  }
  if (error.response.error.code === 'VERSION_CONFLICT') {
    return t('tenant.i18nCatalog.errors.versionConflict')
  }
  if (error.response.error.code === 'PLACEHOLDER_UNKNOWN') {
    return t('tenant.i18nCatalog.errors.placeholderUnknown')
  }
  if (error.response.error.code === 'KEY_NOT_ALLOWED') {
    return t('tenant.i18nCatalog.errors.keyNotAllowed')
  }
  return t('tenant.i18nCatalog.errors.invalid')
}
</script>

<template>
  <main class="tenant-shell">
    <TenantAdminNav />

    <section class="tenant-workspace">
      <header class="page-heading">
        <div>
          <span>{{ $t('tenant.i18nCatalog.page.kicker') }}</span>
          <h1>{{ $t('tenant.i18nCatalog.page.title') }}</h1>
        </div>
        <div class="heading-actions">
          <select v-model="namespaceFilter" :aria-label="$t('tenant.i18nCatalog.fields.namespaceFilter')">
            <option value="">{{ $t('tenant.i18nCatalog.fields.allNamespaces') }}</option>
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

      <section v-else class="catalog-panel" :aria-label="$t('tenant.i18nCatalog.page.title')">
        <div class="mode-row" :aria-label="$t('tenant.i18nCatalog.fields.scopeLevel')">
          <label>
            <input v-model="scopeLevel" value="store" type="radio" />
            <span>{{ $t('tenant.i18nCatalog.scope.store') }}</span>
          </label>
          <label>
            <input v-model="scopeLevel" value="tenant" type="radio" />
            <span>{{ $t('tenant.i18nCatalog.scope.tenant') }}</span>
          </label>
        </div>

        <p class="scope-note">{{ $t('tenant.i18nCatalog.page.note') }}</p>

        <div v-for="entry in filteredRows" :key="entry.source.key.i18nKey" class="catalog-row">
          <div class="key-column">
            <strong>{{ entry.source.key.displayName }}</strong>
            <code>{{ entry.source.key.i18nKey }}</code>
            <span>{{ entry.source.key.namespace }} / {{ entry.source.key.category }} / {{ entry.source.key.textKind }}</span>
            <small>{{ placeholderText(entry) }}</small>
          </div>

          <div class="locale-columns">
            <label v-for="locale in entry.locales" :key="`${entry.source.key.i18nKey}-${scopeLevel}-${locale.locale}`">
              <span>{{ locale.locale }} · {{ $t('tenant.i18nCatalog.fields.override') }}</span>
              <textarea
                v-model.trim="locale.message"
                :placeholder="locale.effectiveMessage"
                rows="3"
              />
              <small>
                {{ $t('tenant.i18nCatalog.fields.effective') }}:
                {{ sourceLabel(locale.effectiveSource) }} · {{ locale.effectiveMessage || locale.platformMessage }}
              </small>
              <button class="link-button" type="button" @click="clearDraft(locale)">
                {{ $t('tenant.i18nCatalog.fields.clearOverride') }}
              </button>
            </label>
          </div>
        </div>

        <p v-if="catalog && filteredRows.length === 0" class="loading-line">
          {{ $t('tenant.i18nCatalog.fields.empty') }}
        </p>
      </section>
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
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
}

.page-heading span,
.key-column span,
.key-column small,
label span,
label small {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.page-heading h1 {
  margin: 0;
  color: #0f172a;
  font-size: 24px;
}

.heading-actions,
.mode-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.mode-row label {
  min-height: 38px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  background: #ffffff;
}

.mode-row input {
  width: auto;
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

.link-button {
  justify-self: start;
  min-height: 30px;
  border: 0;
  padding: 0;
  color: #be123c;
  background: transparent;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
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
  .tenant-shell,
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
