<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  CallScreenAdminApiError,
  createCallScreenAdSet,
  getCallScreenSettings,
  listCallScreenAdSets,
  updateCallScreenAdSet,
  updateCallScreenSettings,
  uploadCallScreenMedia
} from '../api/callScreenAdminApi'
import { getTenantProfile } from '../api/tenantAdminApi'
import CallScreenAdModeSwitch from '../components/call-screen/CallScreenAdModeSwitch.vue'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'
import type {
  CallScreenAdMode,
  CallScreenAdSet,
  CallScreenMediaSlide,
  CallScreenMediaSlideMutation,
  CallScreenSettings,
  CallScreenStatus,
  CallScreenTextSlide,
  CallScreenTextSlideMutation
} from '../types/callScreenAdmin'

type PreviewSlide = CallScreenTextSlide | CallScreenMediaSlide

const route = useRoute()
const auth = useAuthSessionStore()

const settings = ref<CallScreenSettings | null>(null)
const adSets = ref<CallScreenAdSet[]>([])
const selectedAdSetId = ref('')
const editableAdSet = ref<CallScreenAdSet | null>(null)
const loading = ref(false)
const savingSettings = ref(false)
const savingAdSet = ref(false)
const uploadingMedia = ref(false)
const errorText = ref('')
const savedText = ref('')
const previewSlideIndex = ref(0)
const previewFullscreenOpen = ref(false)
const previewLogoUrl = ref('')
const previewLogoFailed = ref(false)
let previewCarouselTimer: number | undefined

const MAX_CALL_SCREEN_IMAGE_BYTES = 10 * 1024 * 1024
const MAX_CALL_SCREEN_VIDEO_BYTES = 80 * 1024 * 1024
const CALL_SCREEN_MEDIA_UPLOAD_HINT = '支持 JPG、PNG、WebP、MP4、WebM；图片不超过 10MB，视频不超过 80MB。'
const CALL_SCREEN_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])
const CALL_SCREEN_VIDEO_TYPES = new Set(['video/mp4', 'video/webm'])

const storeId = computed(() => String(route.params.storeId || ''))
const adMode = computed<CallScreenAdMode>(() => normalizeAdMode(settings.value?.adMode))
const textAdSets = computed(() => adSets.value.filter(adSet => adSet.adType === 'text'))
const mediaAdSets = computed(() => adSets.value.filter(adSet => normalizeAdMode(adSet.adType) === 'media'))
const selectableAdSets = computed(() => (adMode.value === 'media' ? mediaAdSets.value : textAdSets.value))
const sortedPreviewSlides = computed<PreviewSlide[]>(() => {
  if (!editableAdSet.value) {
    return []
  }
  return editableAdSet.value.adType === 'text'
    ? sortTextSlides(editableAdSet.value.slides)
    : sortMediaSlides(editableAdSet.value.mediaSlides)
})
const previewSlides = computed(() => {
  const activeSlides = sortedPreviewSlides.value.filter(slide => slide.status === 'active')
  return activeSlides.length > 0 ? activeSlides : sortedPreviewSlides.value
})
const previewSlide = computed(() => previewSlides.value[previewSlideIndex.value] ?? previewSlides.value[0])
const hasSelectableSets = computed(() => selectableAdSets.value.length > 0)
const showPreviewLogoImage = computed(() => !!previewLogoUrl.value && !previewLogoFailed.value)

onMounted(() => {
  void loadCallScreenConfig()
  startPreviewCarousel()
})

onUnmounted(() => {
  stopPreviewCarousel()
})

watch(selectedAdSetId, value => {
  if (settings.value) {
    settings.value.activeAdSetId = value || null
  }
  syncEditableAdSet(value)
})

watch(adMode, mode => {
  const current = adSets.value.find(adSet => adSet.id === selectedAdSetId.value)
  if (current && normalizeAdMode(current.adType) === mode) {
    return
  }
  selectedAdSetId.value = selectableAdSets.value[0]?.id ?? ''
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
  void loadPreviewLogo()
  try {
    const [settingsResult, setsResult] = await Promise.all([
      getCallScreenSettings(storeId.value),
      listCallScreenAdSets(storeId.value)
    ])
    settings.value = { ...settingsResult.settings, adMode: normalizeAdMode(settingsResult.settings.adMode) }
    adSets.value = setsResult.adSets.map(cloneAdSet)
    const active = adSets.value.find(adSet => adSet.id === settingsResult.settings.activeAdSetId)
    selectedAdSetId.value =
      active && normalizeAdMode(active.adType) === adMode.value
        ? active.id
        : selectableAdSets.value[0]?.id ?? ''
    syncEditableAdSet(selectedAdSetId.value)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function loadPreviewLogo(): Promise<void> {
  try {
    const response = await getTenantProfile(storeId.value)
    previewLogoUrl.value = response.profile.logoMediaUrl || ''
    previewLogoFailed.value = false
  } catch {
    previewLogoUrl.value = ''
    previewLogoFailed.value = true
  }
}

async function saveSettings(): Promise<void> {
  if (!settings.value || savingSettings.value) {
    return
  }
  const activeAdSetId = selectedAdSetId.value || null
  if (!activeAdSetId) {
    errorText.value = adMode.value === 'media' ? '请先上传图片或视频并选择媒体组' : '请选择要启用的文案组'
    return
  }

  savingSettings.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await updateCallScreenSettings(storeId.value, {
      activeAdSetId,
      adMode: adMode.value,
      status: settings.value.status,
      slideDurationSeconds: Number(settings.value.slideDurationSeconds),
      statePollSeconds: Number(settings.value.statePollSeconds),
      showWaitingPreview: Boolean(settings.value.showWaitingPreview),
      version: settings.value.version
    })
    settings.value = { ...response.settings, adMode: normalizeAdMode(response.settings.adMode) }
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
    savedText.value = editableAdSet.value.adType === 'media' ? '媒体组已保存' : '文案组已保存'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    savingAdSet.value = false
  }
}

async function uploadMediaSlide(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || uploadingMedia.value) {
    return
  }
  const validationError = validateMediaUploadFile(file)
  if (validationError) {
    errorText.value = validationError
    savedText.value = ''
    return
  }
  uploadingMedia.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const upload = await uploadCallScreenMedia(storeId.value, file)
    const nextSlide: CallScreenMediaSlide = {
      id: `draft-${Date.now()}`,
      mediaAssetId: upload.media.id,
      mediaKind: upload.media.mediaKind,
      mediaUrl: upload.media.mediaUrl,
      title: upload.media.originalFilename,
      altText: upload.media.originalFilename,
      sortOrder: nextMediaSortOrder(),
      status: 'active',
      version: 0
    }
    if (editableAdSet.value && normalizeAdMode(editableAdSet.value.adType) === 'media') {
      editableAdSet.value.mediaSlides.push(nextSlide)
      previewSlideIndex.value = Math.max(0, previewSlides.value.length - 1)
      return
    }
    const response = await createCallScreenAdSet(storeId.value, {
      name: '默认图片/视频轮播',
      adType: 'media',
      status: 'active',
      slides: [],
      mediaSlides: [toMediaSlideMutation(nextSlide)]
    })
    replaceAdSet(response.adSet)
    settings.value = settings.value ? { ...settings.value, adMode: 'media' } : settings.value
    selectedAdSetId.value = response.adSet.id
    editableAdSet.value = cloneAdSet(response.adSet)
  } catch (error) {
    errorText.value = mediaUploadErrorText(error)
  } finally {
    uploadingMedia.value = false
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

function handlePreviewLogoError(): void {
  previewLogoFailed.value = true
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
  if (!editableAdSet.value || editableAdSet.value.adType !== 'text') {
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
    adType: normalizeAdMode(adSet.adType),
    slides: (adSet.slides ?? []).map(slide => ({ ...slide })),
    mediaSlides: (adSet.mediaSlides ?? []).map(slide => ({ ...slide }))
  }
}

function toAdSetMutation(adSet: CallScreenAdSet) {
  return {
    name: adSet.name.trim(),
    adType: normalizeAdMode(adSet.adType),
    status: adSet.status,
    slides: adSet.adType === 'text' ? toSlideMutations(adSet.slides) : [],
    mediaSlides: normalizeAdMode(adSet.adType) === 'media' ? toMediaSlideMutations(adSet.mediaSlides) : [],
    version: adSet.version
  }
}

function sortTextSlides(slides: CallScreenTextSlide[]): CallScreenTextSlide[] {
  return [...slides].sort((left, right) => left.sortOrder - right.sortOrder || left.title.localeCompare(right.title))
}

function sortMediaSlides(slides: CallScreenMediaSlide[]): CallScreenMediaSlide[] {
  return [...slides].sort((left, right) => left.sortOrder - right.sortOrder || (left.title ?? '').localeCompare(right.title ?? ''))
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

function toMediaSlideMutations(slides: CallScreenMediaSlide[]): CallScreenMediaSlideMutation[] {
  return sortMediaSlides(slides).map(toMediaSlideMutation)
}

function toMediaSlideMutation(slide: CallScreenMediaSlide): CallScreenMediaSlideMutation {
  return {
    mediaAssetId: slide.mediaAssetId,
    mediaKind: slide.mediaKind,
    title: slide.title?.trim() || null,
    altText: slide.altText?.trim() || null,
    sortOrder: Number(slide.sortOrder),
    status: slide.status
  }
}

function validateAdSet(adSet: CallScreenAdSet): string {
  if (!adSet.name.trim()) {
    return '请填写广告组名称'
  }
  return normalizeAdMode(adSet.adType) === 'media' ? validateMediaAdSet(adSet) : validateTextAdSet(adSet)
}

function validateTextAdSet(adSet: CallScreenAdSet): string {
  if (adSet.slides.length === 0) {
    return '文案组至少需要一条文案'
  }
  const sortOrders = new Set<number>()
  for (const slide of adSet.slides) {
    if (!slide.title.trim() || !slide.subtitle.trim() || !slide.tagline.trim()) {
      return '请填写完整的标题、副标题和标语'
    }
    const error = validateSortOrder(slide.sortOrder, sortOrders)
    if (error) {
      return error
    }
  }
  return ''
}

function validateMediaAdSet(adSet: CallScreenAdSet): string {
  if (adSet.mediaSlides.length === 0) {
    return '媒体组至少需要一张图片或一个视频'
  }
  const sortOrders = new Set<number>()
  for (const slide of adSet.mediaSlides) {
    if (!slide.mediaAssetId || !slide.mediaKind) {
      return '请先上传图片或视频'
    }
    const error = validateSortOrder(slide.sortOrder, sortOrders)
    if (error) {
      return error
    }
  }
  return ''
}

function validateSortOrder(value: number, sortOrders: Set<number>): string {
  const sortOrder = Number(value)
  if (!Number.isInteger(sortOrder) || sortOrder <= 0) {
    return '排序必须是大于 0 的整数'
  }
  if (sortOrders.has(sortOrder)) {
    return '排序号不能重复'
  }
  sortOrders.add(sortOrder)
  return ''
}

function nextMediaSortOrder(): number {
  return Math.max(0, ...(editableAdSet.value?.mediaSlides ?? []).map(slide => Number(slide.sortOrder) || 0)) + 1
}

function isMediaSlide(slide: PreviewSlide | undefined): slide is CallScreenMediaSlide {
  return !!slide && 'mediaKind' in slide
}

function validateMediaUploadFile(file: File): string {
  if (CALL_SCREEN_IMAGE_TYPES.has(file.type)) {
    return file.size > MAX_CALL_SCREEN_IMAGE_BYTES ? '图片不能超过 10MB' : ''
  }
  if (CALL_SCREEN_VIDEO_TYPES.has(file.type)) {
    return file.size > MAX_CALL_SCREEN_VIDEO_BYTES ? '视频不能超过 80MB' : ''
  }
  return '仅支持 JPG、PNG、WebP、MP4、WebM'
}

function normalizeAdMode(value: string | null | undefined): CallScreenAdMode {
  return value === 'media' || value === 'image' ? 'media' : 'text'
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
    return '请检查广告组、排序和轮播时间'
  }
  if (error.response.error.code === 'AD_SET_NOT_FOUND' || error.response.error.code === 'MEDIA_NOT_FOUND') {
    return '广告资源不存在或已被删除'
  }
  if (error.response.error.code === 'VERSION_CONFLICT') {
    return '配置已被其他操作更新，请重新加载后再保存'
  }
  return '操作失败'
}

function mediaUploadErrorText(error: unknown): string {
  if (error instanceof CallScreenAdminApiError && error.response.error.code === 'REQUEST_INVALID') {
    return '请检查媒体文件格式或大小，图片不超过 10MB，视频不超过 80MB'
  }
  return apiErrorText(error)
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
              <h2 id="call-screen-settings-title">播放参数</h2>
            </div>
            <small>版本 {{ settings.version }}</small>
          </div>

          <CallScreenAdModeSwitch v-model="settings.adMode" aria-label="轮播类型" />

          <p class="phase-note">媒体轮播支持 JPG、PNG、WebP、MP4、WebM，租户只可使用自己上传的资源。</p>

          <div class="settings-grid">
            <label>
              <span>{{ adMode === 'media' ? '启用媒体组' : '启用文案组' }}</span>
              <select v-model="selectedAdSetId" :disabled="!hasSelectableSets" required>
                <option v-for="adSet in selectableAdSets" :key="adSet.id" :value="adSet.id">
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
              <h2 id="call-screen-copy-title">{{ editableAdSet?.adType === 'media' ? '媒体编辑' : '文案编辑' }}</h2>
            </div>
            <div class="panel-heading-actions">
              <label v-if="editableAdSet?.adType === 'media' || adMode === 'media'" class="upload-button">
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp,video/mp4,video/webm"
                  :disabled="uploadingMedia"
                  @change="uploadMediaSlide"
                />
                {{ uploadingMedia ? '上传中' : '上传媒体' }}
              </label>
              <button
                v-if="editableAdSet?.adType !== 'media'"
                class="secondary-button compact"
                type="button"
                :disabled="!editableAdSet"
                @click="addSlide"
              >
                新增一组
              </button>
              <small v-if="editableAdSet">版本 {{ editableAdSet.version }}</small>
            </div>
          </div>

          <p v-if="!editableAdSet" class="empty-line">
            {{ adMode === 'media' ? '暂无媒体组，请先上传图片或视频' : '暂无可编辑文案组' }}
          </p>

          <template v-else>
            <p v-if="editableAdSet.adType === 'media'" class="media-upload-hint">
              {{ CALL_SCREEN_MEDIA_UPLOAD_HINT }}
            </p>
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

            <div v-if="editableAdSet.adType === 'text'" class="slide-editor">
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

            <div v-else class="slide-editor media-slide-editor">
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
                  <tr v-for="slide in editableAdSet.mediaSlides" :key="slide.id">
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

            <div class="panel-actions">
              <button class="primary-button" type="button" :disabled="savingAdSet" @click="saveAdSet">
                {{ savingAdSet ? '保存中' : editableAdSet.adType === 'media' ? '保存媒体组' : '保存文案组' }}
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
              <span class="preview-expand-icon" aria-hidden="true" />
              大屏预览
            </button>
          </div>
          <div class="preview-screen">
            <template v-if="isMediaSlide(previewSlide)">
              <img
                v-if="previewSlide.mediaKind === 'image'"
                class="preview-media"
                :src="previewSlide.mediaUrl"
                :alt="previewSlide.altText || previewSlide.title || '媒体预览'"
              />
              <video
                v-else
                class="preview-media"
                :src="previewSlide.mediaUrl"
                muted
                playsinline
                autoplay
              />
              <h3 class="preview-media-title">{{ previewSlide.title || '媒体广告' }}</h3>
            </template>
            <template v-else>
              <span class="preview-mark">
                <img
                  v-if="showPreviewLogoImage"
                  class="preview-logo-image"
                  :src="previewLogoUrl"
                  alt=""
                  @error="handlePreviewLogoError"
                />
                <span v-else>食</span>
              </span>
              <h3>{{ previewSlide?.title || '暂无文案' }}</h3>
              <p class="preview-subtitle">{{ previewSlide?.subtitle || '请选择文案组' }}</p>
              <p class="preview-tagline">{{ previewSlide?.tagline || '保存后将在终端屏生效' }}</p>
            </template>
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
            <template v-if="isMediaSlide(previewSlide)">
              <img
                v-if="previewSlide.mediaKind === 'image'"
                class="preview-fullscreen-media"
                :src="previewSlide.mediaUrl"
                :alt="previewSlide.altText || previewSlide.title || '媒体大屏预览'"
              />
              <video
                v-else
                class="preview-fullscreen-media"
                :src="previewSlide.mediaUrl"
                muted
                playsinline
                autoplay
                controls
              />
              <h2 v-if="previewSlide.title">{{ previewSlide.title }}</h2>
            </template>
            <template v-else>
              <span class="preview-mark preview-mark-large">
                <img
                  v-if="showPreviewLogoImage"
                  class="preview-logo-image"
                  :src="previewLogoUrl"
                  alt=""
                  @error="handlePreviewLogoError"
                />
                <span v-else>食</span>
              </span>
              <h2>{{ previewSlide?.title || '暂无文案' }}</h2>
              <p class="preview-fullscreen-subtitle">{{ previewSlide?.subtitle || '请选择文案组' }}</p>
              <p class="preview-fullscreen-tagline">{{ previewSlide?.tagline || '保存后将在终端屏生效' }}</p>
            </template>
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

.settings-grid,
.ad-set-fields {
  display: grid;
  gap: 12px;
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

.media-upload-hint {
  margin: -6px 0 0;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
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

input[type='radio'],
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
  position: relative;
  min-height: 280px;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 12px;
  padding: 24px;
  border-radius: 8px;
  color: #f8fafc;
  background:
    radial-gradient(circle at 25% 20%, rgba(249, 115, 22, 0.22), transparent 30%),
    linear-gradient(135deg, #101827 0%, #172033 50%, #0b1120 100%);
  text-align: center;
}

.preview-mark {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid rgba(251, 146, 60, 0.6);
  border-radius: 50%;
  background: rgba(249, 115, 22, 0.14);
  color: #fed7aa;
  font-size: 20px;
  font-weight: 900;
}

.preview-logo-image {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  border-radius: 999px;
  padding: 7px;
  background: rgba(255, 255, 255, 0.92);
  object-fit: contain;
}

.preview-mark-large .preview-logo-image {
  padding: 10px;
}

.preview-screen h3 {
  margin: 0;
  color: #ffffff;
  font-size: 32px;
  letter-spacing: 0;
}

.preview-media {
  width: 100%;
  height: 100%;
  min-height: 240px;
  border-radius: 8px;
  object-fit: cover;
}

.preview-media-title {
  position: absolute;
  left: 24px;
  right: 24px;
  bottom: 24px;
  text-shadow: 0 8px 24px rgba(0, 0, 0, 0.62);
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
    radial-gradient(circle at 25% 20%, rgba(249, 115, 22, 0.24), transparent 30%),
    linear-gradient(135deg, #101827 0%, #172033 50%, #0b1120 100%);
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

.preview-fullscreen-media {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border-radius: 8px;
  object-fit: cover;
}

.preview-fullscreen-stage:has(.preview-fullscreen-media) {
  position: relative;
  overflow: hidden;
}

.preview-fullscreen-stage:has(.preview-fullscreen-media) h2 {
  position: absolute;
  left: 48px;
  right: 48px;
  bottom: 48px;
  text-shadow: 0 12px 32px rgba(0, 0, 0, 0.7);
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

  .preview-fullscreen-subtitle {
    font-size: 24px;
  }

  .preview-fullscreen-tagline {
    font-size: 16px;
  }
}
</style>
