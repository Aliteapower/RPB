<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

import {
  PlatformCallScreenSeedApiError,
  getPlatformCallScreenTextSeed,
  updatePlatformCallScreenTextSeed
} from '../api/platformCallScreenSeedApi'
import PlatformAdminNav from '../components/platform/PlatformAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'
import type {
  PlatformCallScreenSeedSet,
  PlatformCallScreenSeedSlide,
  PlatformCallScreenSeedStatus
} from '../types/platformCallScreenSeed'

const auth = useAuthSessionStore()

const seedSet = ref<PlatformCallScreenSeedSet | null>(null)
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const savedText = ref('')
const previewSlideIndex = ref(0)
const previewFullscreenOpen = ref(false)
let previewCarouselTimer: number | undefined

const sortedSlides = computed(() => sortSlides(seedSet.value?.slides ?? []))
const previewSlides = computed(() => {
  const activeSlides = sortedSlides.value.filter(slide => slide.status === 'active')
  return activeSlides.length > 0 ? activeSlides : sortedSlides.value
})
const previewSlide = computed(() => previewSlides.value[previewSlideIndex.value] ?? previewSlides.value[0])

onMounted(() => {
  void loadSeedSet()
  startPreviewCarousel()
})

onUnmounted(stopPreviewCarousel)

watch(previewSlides, slides => {
  if (previewSlideIndex.value >= slides.length) {
    previewSlideIndex.value = 0
  }
})

async function loadSeedSet(): Promise<void> {
  loading.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await getPlatformCallScreenTextSeed()
    seedSet.value = cloneSeedSet(response.seedSet)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function saveSeedSet(): Promise<void> {
  if (!seedSet.value || saving.value) {
    return
  }

  const validationError = validateSeedSet(seedSet.value)
  if (validationError) {
    errorText.value = validationError
    return
  }

  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await updatePlatformCallScreenTextSeed({
      displayName: seedSet.value.displayName.trim(),
      status: seedSet.value.status,
      slides: sortSlides(seedSet.value.slides).map(slide => ({
        id: slide.id,
        title: slide.title.trim(),
        subtitle: slide.subtitle.trim(),
        tagline: slide.tagline.trim(),
        sortOrder: Number(slide.sortOrder),
        status: slide.status,
        version: slide.version
      })),
      version: seedSet.value.version
    })
    seedSet.value = cloneSeedSet(response.seedSet)
    savedText.value = '文案种子模板已保存'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

function addSlide(): void {
  if (!seedSet.value) {
    return
  }
  const nextSortOrder = Math.max(0, ...seedSet.value.slides.map(slide => Number(slide.sortOrder) || 0)) + 1
  seedSet.value.slides.push({
    id: null,
    title: '',
    subtitle: '',
    tagline: '',
    sortOrder: nextSortOrder,
    status: 'active',
    version: 0
  })
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

function cloneSeedSet(nextSeedSet: PlatformCallScreenSeedSet): PlatformCallScreenSeedSet {
  return {
    ...nextSeedSet,
    slides: nextSeedSet.slides.map(slide => ({ ...slide }))
  }
}

function sortSlides(slides: PlatformCallScreenSeedSlide[]): PlatformCallScreenSeedSlide[] {
  return [...slides].sort((left, right) => left.sortOrder - right.sortOrder || left.title.localeCompare(right.title))
}

function validateSeedSet(nextSeedSet: PlatformCallScreenSeedSet): string {
  if (!nextSeedSet.displayName.trim()) {
    return '请填写模板名称'
  }
  if (nextSeedSet.slides.length === 0) {
    return '文案种子模板至少需要一条文案'
  }

  const sortOrders = new Set<number>()
  for (const slide of nextSeedSet.slides) {
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

function statusLabel(status: PlatformCallScreenSeedStatus): string {
  return status === 'active' ? '启用' : '停用'
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof PlatformCallScreenSeedApiError)) {
    return '操作失败'
  }
  if (error.status === 401 || error.response.error.code === 'UNAUTHENTICATED') {
    auth.clear()
    return '登录已失效'
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return '没有平台模板维护权限'
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return '请检查模板名称、文案和排序'
  }
  if (error.response.error.code === 'SEED_NOT_FOUND') {
    return '默认种子模板不存在'
  }
  if (error.response.error.code === 'VERSION_CONFLICT') {
    return '模板已被其他操作更新，请重新加载后再保存'
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
          <h1>平台叫号模板</h1>
        </div>
        <button class="secondary-button" type="button" :disabled="loading" @click="loadSeedSet">
          刷新
        </button>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner" role="status">{{ savedText }}</p>
      <p v-if="loading" class="loading-line">加载中</p>

      <div v-else-if="seedSet" class="workspace-grid">
        <section class="config-panel" aria-labelledby="platform-seed-settings-title">
          <div class="panel-heading">
            <div>
              <span>模板设置</span>
              <h2 id="platform-seed-settings-title">文案种子模板</h2>
            </div>
            <small>{{ seedSet.seedKey }} · 版本 {{ seedSet.version }}</small>
          </div>

          <div class="settings-grid">
            <label>
              <span>模板名称</span>
              <input v-model.trim="seedSet.displayName" maxlength="40" required />
            </label>
            <label>
              <span>状态</span>
              <select v-model="seedSet.status" required>
                <option value="active">启用</option>
                <option value="disabled">停用</option>
              </select>
            </label>
            <label>
              <span>类型</span>
              <input :value="seedSet.adType" disabled />
            </label>
          </div>
        </section>

        <section class="config-panel" aria-labelledby="platform-seed-slides-title">
          <div class="panel-heading">
            <div>
              <span>平台文案库</span>
              <h2 id="platform-seed-slides-title">种子文案编辑</h2>
            </div>
            <button class="secondary-button compact" type="button" @click="addSlide">
              新增一组
            </button>
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
                <tr v-for="slide in seedSet.slides" :key="slide.id || `new-${slide.sortOrder}`">
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
            <button class="primary-button" type="button" :disabled="saving" @click="saveSeedSet">
              {{ saving ? '保存中' : '保存文案种子模板' }}
            </button>
          </div>
        </section>

        <aside class="preview-panel" aria-label="平台叫号文案预览">
          <div class="preview-header">
            <div class="preview-title">
              <span>终端预览</span>
              <strong>{{ seedSet.displayName }} · {{ statusLabel(seedSet.status) }}</strong>
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
            <p class="preview-subtitle">{{ previewSlide?.subtitle || '请维护文案模板' }}</p>
            <p class="preview-tagline">{{ previewSlide?.tagline || '新租户默认副本将使用平台种子文案' }}</p>
          </div>
          <div class="preview-dots" aria-label="预览文案轮播">
            <button
              v-for="(slide, index) in previewSlides"
              :key="slide.id || `dot-${index}-${slide.sortOrder}`"
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
          <section class="preview-fullscreen-stage" aria-label="平台叫号文案大屏预览">
            <span class="preview-mark preview-mark-large">食</span>
            <h2>{{ previewSlide?.title || '暂无文案' }}</h2>
            <p class="preview-fullscreen-subtitle">{{ previewSlide?.subtitle || '请维护文案模板' }}</p>
            <p class="preview-fullscreen-tagline">{{ previewSlide?.tagline || '新租户默认副本将使用平台种子文案' }}</p>
            <div class="preview-dots preview-dots-large" aria-label="大屏预览文案轮播">
              <button
                v-for="(slide, index) in previewSlides"
                :key="slide.id || `fullscreen-dot-${index}-${slide.sortOrder}`"
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

.page-heading,
.panel-heading,
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

.settings-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 150px 130px;
  gap: 12px;
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

input:disabled {
  color: #64748b;
  background: #f8fafc;
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
    radial-gradient(circle at 24% 18%, rgba(249, 115, 22, 0.22), transparent 30%),
    linear-gradient(135deg, #101827 0%, #172033 52%, #0b1120 100%);
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
  .platform-shell,
  .settings-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .platform-workspace {
    padding: 14px;
  }

  .page-heading,
  .panel-heading,
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
