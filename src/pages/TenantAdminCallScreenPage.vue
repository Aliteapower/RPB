<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  CallScreenAdminApiError,
  getCallScreenSettings,
  listCallScreenAdSets,
  updateCallScreenAdSet,
  updateCallScreenSettings
} from '../api/callScreenAdminApi'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'
import type {
  CallScreenAdSet,
  CallScreenAdSetMutation,
  CallScreenSettings,
  CallScreenStatus,
  CallScreenTextSlide,
  CallScreenTextSlideMutation
} from '../types/callScreenAdmin'

const route = useRoute()
const auth = useAuthSessionStore()

const settings = ref<CallScreenSettings | null>(null)
const adSets = ref<CallScreenAdSet[]>([])
const selectedAdSetId = ref('')
const editableAdSet = ref<CallScreenAdSet | null>(null)
const loading = ref(false)
const savingSettings = ref(false)
const savingAdSet = ref(false)
const errorText = ref('')
const savedText = ref('')
const previewSlideIndex = ref(0)
const previewFullscreenOpen = ref(false)
let previewCarouselTimer: number | undefined

const storeId = computed(() => String(route.params.storeId || ''))
const textAdSets = computed(() => adSets.value.filter(adSet => adSet.adType === 'text'))
const sortedPreviewSlides = computed(() => sortTextSlides(editableAdSet.value?.slides ?? []))
const previewSlides = computed(() => {
  const activeSlides = sortedPreviewSlides.value.filter(slide => slide.status === 'active')
  return activeSlides.length > 0 ? activeSlides : sortedPreviewSlides.value
})
const previewSlide = computed(() => previewSlides.value[previewSlideIndex.value] ?? previewSlides.value[0])
const hasSelectableSets = computed(() => textAdSets.value.length > 0)

onMounted(() => {
  void loadCallScreenConfig()
  startPreviewCarousel()
})

onUnmounted(stopPreviewCarousel)

watch(selectedAdSetId, value => {
  if (settings.value) {
    settings.value.activeAdSetId = value || null
  }
  syncEditableAdSet(value)
})

watch(previewSlides, slides => {
  if (previewSlideIndex.value >= slides.length) {
    previewSlideIndex.value = 0
  }
})

async function loadCallScreenConfig(): Promise<void> {
  loading.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const [settingsResult, setsResult] = await Promise.all([
      getCallScreenSettings(storeId.value),
      listCallScreenAdSets(storeId.value)
    ])
    settings.value = { ...settingsResult.settings, adMode: 'text' }
    adSets.value = setsResult.adSets.map(cloneAdSet)
    const active = adSets.value.find(adSet => adSet.id === settingsResult.settings.activeAdSetId)
    selectedAdSetId.value = active?.id ?? textAdSets.value[0]?.id ?? ''
    syncEditableAdSet(selectedAdSetId.value)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function saveSettings(): Promise<void> {
  if (!settings.value || savingSettings.value) {
    return
  }
  const activeAdSetId = selectedAdSetId.value || null
  if (!activeAdSetId) {
    errorText.value = '请选择要启用的文案组'
    return
  }

  savingSettings.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await updateCallScreenSettings(storeId.value, {
      activeAdSetId,
      adMode: 'text',
      status: settings.value.status,
      slideDurationSeconds: Number(settings.value.slideDurationSeconds),
      statePollSeconds: Number(settings.value.statePollSeconds),
      showWaitingPreview: Boolean(settings.value.showWaitingPreview),
      version: settings.value.version
    })
    settings.value = { ...response.settings, adMode: 'text' }
    selectedAdSetId.value = response.settings.activeAdSetId ?? activeAdSetId
    savedText.value = '门店叫号屏设置已保存'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    savingSettings.value = false
  }
}

async function saveAdSet(): Promise<void> {
  if (!editableAdSet.value || savingAdSet.value) {
    return
  }
  const validationError = validateAdSet(editableAdSet.value)
  if (validationError) {
    errorText.value = validationError
    return
  }

  savingAdSet.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await updateCallScreenAdSet(storeId.value, editableAdSet.value.id, toAdSetMutation(editableAdSet.value))
    replaceAdSet(response.adSet)
    selectedAdSetId.value = response.adSet.id
    editableAdSet.value = cloneAdSet(response.adSet)
    savedText.value = '文案组已保存'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    savingAdSet.value = false
  }
}

function syncEditableAdSet(adSetId: string): void {
  const source = adSets.value.find(adSet => adSet.id === adSetId) ?? null
  editableAdSet.value = source ? cloneAdSet(source) : null
  previewSlideIndex.value = 0
}

function startPreviewCarousel(): void {
  stopPreviewCarousel()
  previewCarouselTimer = window.setInterval(showNextPreviewSlide, 3500)
}

function stopPreviewCarousel(): void {
  if (previewCarouselTimer === undefined) {
    return
  }
  window.clearInterval(previewCarouselTimer)
  previewCarouselTimer = undefined
}

function showNextPreviewSlide(): void {
  const slideCount = previewSlides.value.length
  if (slideCount <= 1) {
    return
  }
  previewSlideIndex.value = (previewSlideIndex.value + 1) % slideCount
}

function selectPreviewSlide(index: number): void {
  previewSlideIndex.value = index
  startPreviewCarousel()
}

function openPreviewFullscreen(): void {
  if (previewSlides.value.length === 0) {
    return
  }
  previewFullscreenOpen.value = true
}

function closePreviewFullscreen(): void {
  previewFullscreenOpen.value = false
}

function addSlide(): void {
  if (!editableAdSet.value) {
    return
  }
  const nextSortOrder = Math.max(0, ...editableAdSet.value.slides.map(slide => Number(slide.sortOrder) || 0)) + 1
  editableAdSet.value.slides.push({
    id: `draft-${Date.now()}-${nextSortOrder}`,
    title: '',
    subtitle: '',
    tagline: '',
    sortOrder: nextSortOrder,
    status: 'active',
    version: 0
  })
  previewSlideIndex.value = Math.max(0, previewSlides.value.length - 1)
}

function replaceAdSet(nextAdSet: CallScreenAdSet): void {
  const index = adSets.value.findIndex(adSet => adSet.id === nextAdSet.id)
  if (index >= 0) {
    adSets.value.splice(index, 1, cloneAdSet(nextAdSet))
  } else {
    adSets.value.push(cloneAdSet(nextAdSet))
  }
}

function cloneAdSet(adSet: CallScreenAdSet): CallScreenAdSet {
  return {
    ...adSet,
    adType: 'text',
    slides: (adSet.slides ?? []).map(slide => ({ ...slide }))
  }
}

function toAdSetMutation(adSet: CallScreenAdSet): CallScreenAdSetMutation {
  return {
    name: adSet.name.trim(),
    adType: 'text',
    status: adSet.status,
    slides: toSlideMutations(adSet.slides),
    version: adSet.version
  }
}

function sortTextSlides(slides: CallScreenTextSlide[]): CallScreenTextSlide[] {
  return [...slides].sort((left, right) => left.sortOrder - right.sortOrder || left.title.localeCompare(right.title))
}

function toSlideMutations(slides: CallScreenTextSlide[]): CallScreenTextSlideMutation[] {
  return sortTextSlides(slides).map(slide => ({
    title: slide.title.trim(),
    subtitle: slide.subtitle.trim(),
    tagline: slide.tagline.trim(),
    sortOrder: Number(slide.sortOrder),
    status: slide.status
  }))
}

function validateAdSet(adSet: CallScreenAdSet): string {
  if (!adSet.name.trim()) {
    return '请填写广告组名称'
  }
  if (adSet.slides.length === 0) {
    return '文案组至少需要一条文案'
  }
  const sortOrders = new Set<number>()
  for (const slide of adSet.slides) {
    if (!slide.title.trim() || !slide.subtitle.trim() || !slide.tagline.trim()) {
      return '请填写完整的标题、副标题和标语'
    }
    const sortOrder = Number(slide.sortOrder)
    if (!Number.isInteger(sortOrder) || sortOrder <= 0) {
      return '排序必须是大于 0 的整数'
    }
    if (sortOrders.has(sortOrder)) {
      return '排序号不能重复'
    }
    sortOrders.add(sortOrder)
  }
  return ''
}

function statusLabel(status: CallScreenStatus): string {
  return status === 'active' ? '启用' : '停用'
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof CallScreenAdminApiError)) {
    return '操作失败'
  }
  if (error.status === 401 || error.response.error.code === 'UNAUTHENTICATED') {
    auth.clear()
    return '登录已失效'
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return '没有租户后台权限'
  }
  if (error.response.error.code === 'STORE_SCOPE_MISMATCH') {
    return '没有该店面的后台权限'
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return '请检查文案组、排序和轮播时间'
  }
  if (error.response.error.code === 'AD_SET_NOT_FOUND') {
    return '文案组不存在或已被删除'
  }
  if (error.response.error.code === 'VERSION_CONFLICT') {
    return '配置已被其他操作更新，请重新加载后再保存'
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
          <h1>叫号屏配置</h1>
        </div>
        <button class="secondary-button" type="button" :disabled="loading" @click="loadCallScreenConfig">
          刷新
        </button>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner" role="status">{{ savedText }}</p>
      <p v-if="loading" class="loading-line">加载中</p>

      <div v-else-if="settings" class="workspace-grid">
        <section class="config-panel" aria-labelledby="call-screen-settings-title">
          <div class="panel-heading">
            <div>
              <span>门店设置</span>
              <h2 id="call-screen-settings-title">文本轮播参数</h2>
            </div>
            <small>版本 {{ settings.version }}</small>
          </div>

          <div class="settings-grid">
            <label>
              <span>启用文案组</span>
              <select v-model="selectedAdSetId" :disabled="!hasSelectableSets" required>
                <option v-for="adSet in textAdSets" :key="adSet.id" :value="adSet.id">
                  {{ adSet.name }} · {{ statusLabel(adSet.status) }}
                </option>
              </select>
            </label>
            <label>
              <span>单页秒数</span>
              <input v-model.number="settings.slideDurationSeconds" type="number" min="3" max="60" required />
            </label>
            <label>
              <span>状态轮询秒数</span>
              <input v-model.number="settings.statePollSeconds" type="number" min="2" max="30" required />
            </label>
            <label class="checkbox-field">
              <input v-model="settings.showWaitingPreview" type="checkbox" />
              <span>显示等待预览</span>
            </label>
          </div>

          <div class="panel-actions">
            <button class="primary-button" type="button" :disabled="savingSettings || !hasSelectableSets" @click="saveSettings">
              {{ savingSettings ? '保存中' : '保存播放参数' }}
            </button>
          </div>
        </section>

        <section class="config-panel" aria-labelledby="call-screen-copy-title">
          <div class="panel-heading">
            <div>
              <span>文案组</span>
              <h2 id="call-screen-copy-title">文案编辑</h2>
            </div>
            <div class="panel-heading-actions">
              <button class="secondary-button compact" type="button" :disabled="!editableAdSet" @click="addSlide">
                新增一组
              </button>
              <small v-if="editableAdSet">版本 {{ editableAdSet.version }}</small>
            </div>
          </div>

          <p v-if="!editableAdSet" class="empty-line">暂无可编辑文案组</p>

          <template v-else>
            <div class="ad-set-fields">
              <label>
                <span>名称</span>
                <input v-model.trim="editableAdSet.name" maxlength="40" required />
              </label>
              <label>
                <span>状态</span>
                <select v-model="editableAdSet.status" required>
                  <option value="active">启用</option>
                  <option value="disabled">停用</option>
                </select>
              </label>
            </div>

            <div class="slide-editor">
              <table>
                <thead>
                  <tr>
                    <th>排序</th>
                    <th>状态</th>
                    <th>标题</th>
                    <th>副标题</th>
                    <th>标语</th>
                    <th>版本</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="slide in editableAdSet.slides" :key="slide.id">
                    <td>
                      <input v-model.number="slide.sortOrder" class="sort-input" type="number" min="1" required />
                    </td>
                    <td>
                      <select v-model="slide.status" class="status-select" required>
                        <option value="active">启用</option>
                        <option value="disabled">停用</option>
                      </select>
                    </td>
                    <td>
                      <input v-model.trim="slide.title" maxlength="24" required />
                    </td>
                    <td>
                      <input v-model.trim="slide.subtitle" maxlength="32" required />
                    </td>
                    <td>
                      <input v-model.trim="slide.tagline" maxlength="48" required />
                    </td>
                    <td class="version-cell">{{ slide.version }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div class="panel-actions">
              <button class="primary-button" type="button" :disabled="savingAdSet" @click="saveAdSet">
                {{ savingAdSet ? '保存中' : '保存文案组' }}
              </button>
            </div>
          </template>
        </section>

        <aside class="preview-panel" aria-label="叫号屏文案预览">
          <div class="preview-header">
            <div class="preview-title">
              <span>叫号屏预览</span>
              <strong>{{ editableAdSet?.name || '未选择文案组' }}</strong>
            </div>
            <button
              class="preview-expand-button"
              type="button"
              :disabled="previewSlides.length === 0"
              @click="openPreviewFullscreen"
            >
              大屏预览
            </button>
          </div>
          <div class="preview-screen">
            <span class="preview-mark">食</span>
            <h3>{{ previewSlide?.title || '暂无文案' }}</h3>
            <p class="preview-subtitle">{{ previewSlide?.subtitle || '请选择文案组' }}</p>
            <p class="preview-tagline">{{ previewSlide?.tagline || '保存后将在终端屏生效' }}</p>
          </div>
          <div class="preview-dots" aria-label="预览文案轮播">
            <button
              v-for="(slide, index) in previewSlides"
              :key="slide.id"
              type="button"
              :aria-label="`切换预览文案 ${index + 1}`"
              :class="{ active: index === previewSlideIndex }"
              @click="selectPreviewSlide(index)"
            />
          </div>
        </aside>

        <div
          v-if="previewFullscreenOpen"
          class="preview-fullscreen"
          role="dialog"
          aria-modal="true"
          aria-label="大屏预览"
          @click.self="closePreviewFullscreen"
        >
          <button class="preview-close-button" type="button" @click="closePreviewFullscreen">
            关闭预览
          </button>
          <section class="preview-fullscreen-stage" aria-label="叫号屏文案大屏预览">
            <span class="preview-mark preview-mark-large">食</span>
            <h2>{{ previewSlide?.title || '暂无文案' }}</h2>
            <p class="preview-fullscreen-subtitle">{{ previewSlide?.subtitle || '请选择文案组' }}</p>
            <p class="preview-fullscreen-tagline">{{ previewSlide?.tagline || '保存后将在终端屏生效' }}</p>
            <div class="preview-dots preview-dots-large" aria-label="大屏预览文案轮播">
              <button
                v-for="(slide, index) in previewSlides"
                :key="`fullscreen-dot-${slide.id}`"
                type="button"
                :aria-label="`切换预览文案 ${index + 1}`"
                :class="{ active: index === previewSlideIndex }"
                @click="selectPreviewSlide(index)"
              />
            </div>
          </section>
        </div>
      </div>
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

.page-heading,
.panel-heading,
.panel-heading-actions,
.panel-actions,
.preview-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.page-heading {
  margin-bottom: 16px;
}

.page-heading span,
.panel-heading span,
.preview-header span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.page-heading h1,
.panel-heading h2 {
  margin: 0;
  color: #0f172a;
}

.page-heading h1 {
  font-size: 24px;
}

.panel-heading h2 {
  font-size: 18px;
}

.error-banner,
.success-banner,
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

.success-banner {
  border: 1px solid #bbf7d0;
  color: #166534;
  background: #f0fdf4;
}

.loading-line,
.empty-line {
  border: 1px solid #dbe3ea;
  color: #475569;
  background: #ffffff;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}

.config-panel,
.preview-panel {
  min-width: 0;
  display: grid;
  gap: 16px;
  padding: 18px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.config-panel:nth-of-type(2) {
  grid-column: 1;
}

.preview-panel {
  position: sticky;
  top: 22px;
  grid-column: 2;
  grid-row: 1 / span 2;
}

.settings-grid,
.ad-set-fields {
  display: grid;
  gap: 12px;
}

.settings-grid {
  grid-template-columns: 1.4fr repeat(2, minmax(120px, 0.7fr));
}

.ad-set-fields {
  grid-template-columns: minmax(0, 1fr) 160px;
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

input[type='checkbox'] {
  width: auto;
  min-height: auto;
}

.checkbox-field {
  grid-auto-flow: column;
  justify-content: start;
  align-content: end;
  align-items: center;
  gap: 9px;
  min-height: 67px;
}

.slide-editor {
  overflow-x: auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
}

th,
td {
  border-bottom: 1px solid #e2e8f0;
  padding: 10px;
  text-align: left;
  vertical-align: top;
}

th {
  color: #475569;
  background: #f8fafc;
  font-size: 12px;
  font-weight: 800;
}

tr:last-child td {
  border-bottom: 0;
}

.sort-input {
  width: 74px;
}

.status-select {
  width: 88px;
}

.version-cell {
  color: #64748b;
  font-weight: 800;
  text-align: center;
}

.primary-button,
.secondary-button,
.preview-expand-button {
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

.secondary-button,
.preview-expand-button {
  border: 1px solid #cbd5e1;
  color: #334155;
  background: #ffffff;
}

.secondary-button.compact {
  min-height: 34px;
  padding: 0 10px;
  font-size: 13px;
}

button:disabled {
  opacity: 0.6;
  cursor: default;
}

.preview-title {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.preview-header strong {
  color: #0f172a;
  font-size: 16px;
}

.preview-screen,
.preview-fullscreen-stage {
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 12px;
  color: #f8fafc;
  background:
    radial-gradient(circle at 25% 20%, rgba(249, 115, 22, 0.22), transparent 30%),
    linear-gradient(135deg, #101827 0%, #172033 50%, #0b1120 100%);
  text-align: center;
}

.preview-screen {
  min-height: 280px;
  padding: 24px;
  border-radius: 8px;
}

.preview-mark {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(251, 146, 60, 0.6);
  border-radius: 50%;
  color: #fed7aa;
  font-size: 20px;
  font-weight: 900;
}

.preview-screen h3 {
  margin: 0;
  color: #ffffff;
  font-size: 32px;
}

.preview-subtitle,
.preview-tagline,
.preview-fullscreen-subtitle,
.preview-fullscreen-tagline {
  margin: 0;
}

.preview-subtitle,
.preview-fullscreen-subtitle {
  color: #fdba74;
  font-weight: 900;
}

.preview-tagline,
.preview-fullscreen-tagline {
  color: #cbd5e1;
  line-height: 1.6;
}

.preview-dots {
  display: flex;
  justify-content: center;
  gap: 6px;
}

.preview-dots button {
  width: 7px;
  height: 7px;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: #cbd5e1;
  cursor: pointer;
}

.preview-dots button.active {
  width: 18px;
  background: #f97316;
}

.preview-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 48px;
  background: rgba(4, 9, 18, 0.9);
}

.preview-close-button {
  position: fixed;
  top: 22px;
  right: 22px;
  min-height: 38px;
  border: 1px solid rgba(226, 232, 240, 0.36);
  border-radius: 6px;
  padding: 0 14px;
  color: #f8fafc;
  background: rgba(15, 23, 42, 0.72);
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.preview-fullscreen-stage {
  width: min(1120px, calc(100vw - 96px));
  min-height: min(680px, calc(100dvh - 96px));
  padding: 56px;
  border: 1px solid rgba(251, 146, 60, 0.22);
  border-radius: 8px;
}

.preview-mark-large {
  width: 72px;
  height: 72px;
  font-size: 30px;
}

.preview-fullscreen-stage h2 {
  margin: 0;
  color: #ffffff;
  font-size: 84px;
  line-height: 1.05;
}

.preview-fullscreen-subtitle {
  font-size: 34px;
}

.preview-fullscreen-tagline {
  max-width: 760px;
  font-size: 22px;
}

@media (max-width: 1180px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .config-panel:nth-of-type(2),
  .preview-panel {
    grid-column: auto;
    grid-row: auto;
  }

  .preview-panel {
    position: static;
  }
}

@media (max-width: 980px) {
  .tenant-shell,
  .settings-grid,
  .ad-set-fields {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .tenant-workspace {
    padding: 14px;
  }

  .page-heading,
  .panel-heading,
  .panel-heading-actions,
  .panel-actions,
  .preview-header {
    display: grid;
    justify-content: stretch;
  }

  .primary-button,
  .secondary-button,
  .preview-expand-button {
    width: 100%;
  }

  .preview-fullscreen {
    padding: 18px;
  }

  .preview-fullscreen-stage {
    width: calc(100vw - 36px);
    min-height: calc(100dvh - 96px);
    padding: 28px;
  }

  .preview-fullscreen-stage h2 {
    font-size: 46px;
  }
}
</style>
