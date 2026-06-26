<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

import {
  PlatformCallScreenSeedApiError,
  getPlatformCallScreenMediaSeed,
  getPlatformCallScreenTextSeed,
  updatePlatformCallScreenMediaSeed,
  updatePlatformCallScreenTextSeed,
  uploadPlatformCallScreenMedia
} from '../api/platformCallScreenSeedApi'
import PlatformAdminNav from '../components/platform/PlatformAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'
import type {
  PlatformCallScreenSeedSet,
  PlatformCallScreenMediaSeedSet,
  PlatformCallScreenMediaSeedSlide,
  PlatformCallScreenMediaSeedSlideMutation,
  PlatformCallScreenSeedSlide,
  PlatformCallScreenSeedStatus
} from '../types/platformCallScreenSeed'

const auth = useAuthSessionStore()

const seedSet = ref<PlatformCallScreenSeedSet | null>(null)
const mediaSeedSet = ref<PlatformCallScreenMediaSeedSet | null>(null)
const loading = ref(false)
const saving = ref(false)
const savingMedia = ref(false)
const uploadingMedia = ref(false)
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

onUnmounted(() => {
  stopPreviewCarousel()
})

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
    const mediaResponse = await getPlatformCallScreenMediaSeed()
    seedSet.value = cloneSeedSet(response.seedSet)
    mediaSeedSet.value = cloneMediaSeedSet(mediaResponse.seedSet)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function saveMediaSeedSet(): Promise<void> {
  if (!mediaSeedSet.value || savingMedia.value) {
    return
  }
  const validationError = validateMediaSeedSet(mediaSeedSet.value)
  if (validationError) {
    errorText.value = validationError
    return
  }

  savingMedia.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await updatePlatformCallScreenMediaSeed({
      displayName: mediaSeedSet.value.displayName.trim(),
      status: mediaSeedSet.value.status,
      mediaSlides: sortMediaSlides(mediaSeedSet.value.mediaSlides).map(toMediaSlideMutation),
      version: mediaSeedSet.value.version
    })
    mediaSeedSet.value = cloneMediaSeedSet(response.seedSet)
    savedText.value = '图片/视频种子模板已保存'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    savingMedia.value = false
  }
}

async function uploadMediaSlide(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !mediaSeedSet.value || uploadingMedia.value) {
    return
  }
  uploadingMedia.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await uploadPlatformCallScreenMedia(file)
    mediaSeedSet.value.mediaSlides.push({
      id: null,
      mediaAssetId: response.media.id,
      mediaKind: response.media.mediaKind,
      mediaUrl: response.media.mediaUrl,
      title: response.media.originalFilename,
      altText: response.media.originalFilename,
      sortOrder: nextMediaSortOrder(),
      status: 'active',
      version: 0
    })
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    uploadingMedia.value = false
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

function cloneMediaSeedSet(nextSeedSet: PlatformCallScreenMediaSeedSet): PlatformCallScreenMediaSeedSet {
  return {
    ...nextSeedSet,
    adType: 'media',
    mediaSlides: nextSeedSet.mediaSlides.map(slide => ({ ...slide }))
  }
}

function sortSlides(slides: PlatformCallScreenSeedSlide[]): PlatformCallScreenSeedSlide[] {
  return [...slides].sort((left, right) => left.sortOrder - right.sortOrder || left.title.localeCompare(right.title))
}

function sortMediaSlides(slides: PlatformCallScreenMediaSeedSlide[]): PlatformCallScreenMediaSeedSlide[] {
  return [...slides].sort((left, right) => left.sortOrder - right.sortOrder || (left.title ?? '').localeCompare(right.title ?? ''))
}

function toMediaSlideMutation(slide: PlatformCallScreenMediaSeedSlide): PlatformCallScreenMediaSeedSlideMutation {
  return {
    id: slide.id,
    mediaAssetId: slide.mediaAssetId,
    mediaKind: slide.mediaKind,
    title: slide.title?.trim() || null,
    altText: slide.altText?.trim() || null,
    sortOrder: Number(slide.sortOrder),
    status: slide.status,
    version: slide.version
  }
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

function validateMediaSeedSet(nextSeedSet: PlatformCallScreenMediaSeedSet): string {
  if (!nextSeedSet.displayName.trim()) {
    return '请填写图片/视频模板名称'
  }
  if (nextSeedSet.status === 'active' && nextSeedSet.mediaSlides.length === 0) {
    return '启用图片/视频模板前至少需要一个媒体'
  }
  const sortOrders = new Set<number>()
  for (const slide of nextSeedSet.mediaSlides) {
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

function nextMediaSortOrder(): number {
  return Math.max(0, ...(mediaSeedSet.value?.mediaSlides ?? []).map(slide => Number(slide.sortOrder) || 0)) + 1
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
  if (error.response.error.code === 'MEDIA_NOT_FOUND') {
    return '媒体资源不存在或已被删除'
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

      <div v-else-if="seedSet && mediaSeedSet" class="workspace-grid">
        <section class="config-panel" aria-labelledby="platform-seed-settings-title">
          <div class="panel-heading">
            <div>
              <span>模板设置</span>
              <h2 id="platform-seed-settings-title">文案种子模板</h2>
            </div>
            <small>{{ seedSet.seedKey }} · 版本 {{ seedSet.version }}</small>
          </div>

          <div class="mode-control" aria-label="模板类型">
            <div class="mode-option">
              <span class="mode-marker" aria-hidden="true"></span>
              <span>文案模板</span>
            </div>
            <div class="mode-option">
              <span class="mode-marker" aria-hidden="true"></span>
              <span>图片/视频模板</span>
            </div>
          </div>

          <p class="phase-note">平台维护文案种子模板和图片/视频种子模板；租户启用时仍使用自己的隔离副本。</p>

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

        <section class="config-panel media-seed-panel" aria-labelledby="platform-media-seed-title">
          <div class="panel-heading">
            <div>
              <span>平台媒体库</span>
              <h2 id="platform-media-seed-title">图片/视频模板</h2>
            </div>
            <div class="panel-heading-actions">
              <label class="upload-button">
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp,video/mp4,video/webm"
                  :disabled="uploadingMedia"
                  @change="uploadMediaSlide"
                />
                {{ uploadingMedia ? '上传中' : '上传媒体' }}
              </label>
              <small>{{ mediaSeedSet.seedKey }} · 版本 {{ mediaSeedSet.version }}</small>
            </div>
          </div>

          <div class="settings-grid">
            <label>
              <span>模板名称</span>
              <input v-model.trim="mediaSeedSet.displayName" maxlength="40" required />
            </label>
            <label>
              <span>状态</span>
              <select v-model="mediaSeedSet.status" required>
                <option value="active">启用</option>
                <option value="disabled">停用</option>
              </select>
            </label>
            <label>
              <span>类型</span>
              <input value="media" disabled />
            </label>
          </div>

          <div class="slide-editor media-slide-editor">
            <table>
              <thead>
                <tr>
                  <th>排序</th>
                  <th>状态</th>
                  <th>类型</th>
                  <th>预览</th>
                  <th>标题</th>
                  <th>替代文本</th>
                  <th>版本</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="slide in mediaSeedSet.mediaSlides" :key="slide.id || slide.mediaAssetId">
                  <td>
                    <input v-model.number="slide.sortOrder" class="sort-input" type="number" min="1" required />
                  </td>
                  <td>
                    <select v-model="slide.status" class="status-select" required>
                      <option value="active">启用</option>
                      <option value="disabled">停用</option>
                    </select>
                  </td>
                  <td class="version-cell">{{ slide.mediaKind === 'video' ? '视频' : '图片' }}</td>
                  <td>
                    <img
                      v-if="slide.mediaKind === 'image'"
                      class="media-thumb"
                      :src="slide.mediaUrl"
                      :alt="slide.altText || slide.title || '媒体预览'"
                    />
                    <video
                      v-else
                      class="media-thumb"
                      :src="slide.mediaUrl"
                      muted
                      playsinline
                      controls
                    />
                  </td>
                  <td>
                    <input v-model.trim="slide.title" maxlength="32" />
                  </td>
                  <td>
                    <input v-model.trim="slide.altText" maxlength="48" />
                  </td>
                  <td class="version-cell">{{ slide.version }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="media-preview-strip" aria-label="图片/视频模板预览">
            <template v-if="mediaSeedSet.mediaSlides.length">
              <div v-for="slide in sortMediaSlides(mediaSeedSet.mediaSlides).slice(0, 2)" :key="`preview-${slide.mediaAssetId}`" class="media-preview-card">
                <img
                  v-if="slide.mediaKind === 'image'"
                  :src="slide.mediaUrl"
                  :alt="slide.altText || slide.title || '媒体预览'"
                />
                <video v-else :src="slide.mediaUrl" muted playsinline autoplay loop />
                <strong>{{ slide.title || (slide.mediaKind === 'video' ? '视频广告' : '图片广告') }}</strong>
              </div>
            </template>
            <p v-else class="empty-media-line">上传图片或视频后可轮播预览。</p>
          </div>

          <div class="panel-actions">
            <button class="primary-button" type="button" :disabled="savingMedia" @click="saveMediaSeedSet">
              {{ savingMedia ? '保存中' : '保存图片/视频种子模板' }}
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
              <span class="preview-expand-icon" aria-hidden="true" />
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

.page-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
}

.page-heading span,
.panel-heading span,
.preview-header span {
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

.config-panel {
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

.media-seed-panel {
  grid-column: 1 / -1;
}

.panel-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: start;
}

.panel-heading h2 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
}

.panel-heading small {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.panel-heading-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.mode-control,
.settings-grid {
  display: grid;
  gap: 12px;
}

.mode-control {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.mode-option {
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 12px;
  border: 1px solid #99f6e4;
  border-radius: 6px;
  color: #115e59;
  background: #f0fdfa;
  font-weight: 800;
}

.mode-option.disabled {
  border-color: #e2e8f0;
  color: #94a3b8;
  background: #f8fafc;
}

.mode-marker {
  width: 9px;
  height: 9px;
  border-radius: 999px;
  background: #0f766e;
  box-shadow: 0 0 0 4px rgba(20, 184, 166, 0.14);
}

.phase-note {
  margin: 0;
  padding: 10px 12px;
  border: 1px dashed #cbd5e1;
  border-radius: 6px;
  color: #475569;
  background: #f8fafc;
  font-size: 13px;
  font-weight: 700;
}

.settings-grid {
  grid-template-columns: minmax(0, 1fr) 150px 130px;
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

input[type='radio'] {
  width: auto;
  min-height: auto;
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

.media-slide-editor table {
  min-width: 1080px;
}

.media-thumb {
  width: 96px;
  height: 54px;
  display: block;
  border-radius: 6px;
  background: #0f172a;
  object-fit: cover;
}

.media-preview-strip {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.media-preview-card {
  position: relative;
  min-height: 180px;
  overflow: hidden;
  border-radius: 8px;
  background: #0f172a;
}

.media-preview-card img,
.media-preview-card video {
  width: 100%;
  height: 100%;
  min-height: 180px;
  display: block;
  object-fit: cover;
}

.media-preview-card strong {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 12px;
  color: #ffffff;
  font-size: 18px;
  text-shadow: 0 8px 24px rgba(0, 0, 0, 0.7);
}

.empty-media-line {
  grid-column: 1 / -1;
  margin: 0;
  padding: 12px;
  border: 1px dashed #cbd5e1;
  border-radius: 6px;
  color: #64748b;
  background: #f8fafc;
  font-weight: 700;
}

.panel-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
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

.secondary-button.compact {
  min-height: 34px;
  padding: 0 10px;
  font-size: 13px;
}

.upload-button {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #fed7aa;
  border-radius: 6px;
  padding: 0 10px;
  color: #c2410c;
  background: #fff7ed;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.upload-button input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.primary-button:disabled,
.secondary-button:disabled {
  opacity: 0.6;
  cursor: default;
}

.preview-panel {
  position: sticky;
  top: 22px;
  grid-column: 2;
  grid-row: 1 / span 2;
  min-width: 0;
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 4px;
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

.preview-expand-button {
  min-height: 32px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  border: 1px solid #fed7aa;
  border-radius: 6px;
  padding: 0 10px;
  color: #c2410c;
  background: #fff7ed;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.preview-expand-button:disabled {
  opacity: 0.55;
  cursor: default;
}

.preview-expand-icon {
  position: relative;
  width: 15px;
  height: 11px;
  box-sizing: border-box;
  border: 2px solid currentColor;
  border-radius: 2px;
}

.preview-expand-icon::after {
  content: '';
  position: absolute;
  right: -3px;
  bottom: -4px;
  width: 6px;
  height: 2px;
  border-radius: 999px;
  background: currentColor;
}

.preview-screen {
  min-height: 280px;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 12px;
  padding: 24px;
  border-radius: 8px;
  color: #f8fafc;
  background:
    radial-gradient(circle at 24% 18%, rgba(249, 115, 22, 0.22), transparent 30%),
    linear-gradient(135deg, #101827 0%, #172033 52%, #0b1120 100%);
  text-align: center;
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
  letter-spacing: 0;
}

.preview-subtitle,
.preview-tagline {
  margin: 0;
}

.preview-subtitle {
  color: #fdba74;
  font-size: 18px;
  font-weight: 800;
}

.preview-tagline {
  color: #cbd5e1;
  font-size: 14px;
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

.preview-dots button:focus-visible {
  outline: 2px solid #fb923c;
  outline-offset: 3px;
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
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 22px;
  padding: 56px;
  border: 1px solid rgba(251, 146, 60, 0.22);
  border-radius: 8px;
  color: #f8fafc;
  background:
    radial-gradient(circle at 24% 18%, rgba(249, 115, 22, 0.24), transparent 30%),
    linear-gradient(135deg, #101827 0%, #172033 52%, #0b1120 100%);
  text-align: center;
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
  letter-spacing: 0;
}

.preview-fullscreen-subtitle,
.preview-fullscreen-tagline {
  margin: 0;
}

.preview-fullscreen-subtitle {
  color: #fdba74;
  font-size: 34px;
  font-weight: 900;
}

.preview-fullscreen-tagline {
  max-width: 760px;
  color: #cbd5e1;
  font-size: 22px;
  line-height: 1.6;
}

.preview-dots-large button {
  width: 10px;
  height: 10px;
}

.preview-dots-large button.active {
  width: 30px;
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
  .mode-control,
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

  .preview-fullscreen-subtitle {
    font-size: 24px;
  }

  .preview-fullscreen-tagline {
    font-size: 16px;
  }
}
</style>
