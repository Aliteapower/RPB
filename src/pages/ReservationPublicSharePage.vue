<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

import {
  getReservationPublicShare,
  reservationPublicShareErrorMessage,
  ReservationPublicShareApiError
} from '../api/reservationPublicShareApi'
import { shareLinkOrCopy } from '../utils/reservationShareLauncher'
import type { ReservationPublicShare } from '../types/reservationPublicShare'
import { useGeneratedText } from '../i18n/generatedText'

const { gt } = useGeneratedText()
const { locale } = useI18n({ useScope: 'global' })

const route = useRoute()
const share = ref<ReservationPublicShare | null>(null)
const isLoading = ref(false)
const errorText = ref('')
const shareStatusText = ref('')
const fallbackUrl = ref('')

const hiddenShareTextLabels = [
  '\u9884\u8ba2\u7f16\u53f7',
  '\u9884\u7ea6\u7f16\u53f7',
  '\u8ba2\u4f4d\u7f16\u53f7',
  gt('generated.reservation-public-share.019'),
  gt('generated.reservation-public-share.020'),
  gt('generated.reservation-public-share.021'),
  gt('generated.reservation-public-share.022')
]

const token = computed(() => String(route.params.token || '').trim())
const activeLocale = computed(() => String(locale.value || 'zh-CN'))
const tableLabel = computed(() => {
  if (!share.value || share.value.tablePending || !share.value.tableCode.trim()) {
    return gt('generated.reservation-public-share.023')
  }

  return share.value.tableCode.trim()
})
const tableStatus = computed(() => (share.value?.tablePending ? gt('generated.reservation-public-share.024') : gt('generated.reservation-public-share.025')))
const customerShareText = computed(() => {
  if (!share.value) {
    return { intro: '', details: '' }
  }

  return splitShareText(share.value.shareText)
})
const customerIntroText = computed(() => customerShareText.value.intro)
const customerVisibleShareText = computed(() => customerShareText.value.details)
const pageUrl = computed(() => {
  if (typeof window === 'undefined') {
    return ''
  }

  return window.location.href
})
const whatsappContactUrl = computed(() => {
  const digits = (share.value?.storeWhatsappPhone || '').replace(/\D/g, '')
  return digits ? `https://wa.me/${digits}` : ''
})

onMounted(() => {
  void loadShare()
})

watch([token, activeLocale], () => {
  void loadShare()
})

async function loadShare(): Promise<void> {
  if (!token.value) {
    share.value = null
    errorText.value = gt('generated.reservation-public-share.026')
    return
  }

  isLoading.value = true
  errorText.value = ''
  shareStatusText.value = ''
  fallbackUrl.value = ''

  try {
    const response = await getReservationPublicShare(token.value, activeLocale.value)
    share.value = response.share
  } catch (error) {
    share.value = null
    errorText.value =
      error instanceof ReservationPublicShareApiError
        ? publicSharePageErrorText(error)
        : gt('generated.reservation-public-share.027')
  } finally {
    isLoading.value = false
  }
}

function publicSharePageErrorText(error: ReservationPublicShareApiError): string {
  if (error.response.error.code === 'TOKEN_EXPIRED' || error.response.error.code === 'TOKEN_REVOKED') {
    return gt('generated.reservation-public-share.028')
  }

  return reservationPublicShareErrorMessage(error.response.error.code)
}

function splitShareText(text: string): { intro: string; details: string } {
  const lines = text.split(/\r?\n/)
  const firstDetailsIndex = lines.findIndex((line) => isHiddenShareTextLine(line.trim()))

  if (firstDetailsIndex < 0) {
    return {
      intro: '',
      details: compactShareTextLines(lines)
    }
  }

  return {
    intro: collapseBlankLines(lines.slice(0, firstDetailsIndex)).join('\n').trim(),
    details: compactShareTextLines(lines.slice(firstDetailsIndex))
  }
}

function compactShareTextLines(lines: string[]): string {
  const filteredLines = lines.filter((line) => {
    const trimmedLine = line.trim()
    if (!trimmedLine) {
      return true
    }

    return !isHiddenShareTextLine(trimmedLine)
  })

  return collapseBlankLines(filteredLines).join('\n').trim()
}

function isHiddenShareTextLine(line: string): boolean {
  return hiddenShareTextLabels.some((label) => startsWithLabel(line, label))
}

function startsWithLabel(line: string, label: string): boolean {
  return line.startsWith(`${label}：`) || line.startsWith(`${label}:`)
}

function collapseBlankLines(lines: string[]): string[] {
  const result: string[] = []
  let previousLineWasBlank = false

  for (const line of lines) {
    const isBlank = !line.trim()
    if (isBlank && previousLineWasBlank) {
      continue
    }

    result.push(line)
    previousLineWasBlank = isBlank
  }

  return result
}

async function shareCurrentPage(): Promise<void> {
  if (!share.value || !pageUrl.value) {
    return
  }

  shareStatusText.value = ''
  fallbackUrl.value = ''

  const result = await shareLinkOrCopy({
    title: share.value.shareTitle,
    text: share.value.shareSummary,
    url: pageUrl.value
  })

  if (result === 'cancelled') {
    return
  }

  if (result === 'copied' || result === 'native-share') {
    shareStatusText.value = result === 'copied' ? gt('generated.reservation-public-share.029') : gt('generated.reservation-public-share.030')
    return
  }

  fallbackUrl.value = pageUrl.value
  shareStatusText.value = gt('generated.reservation-public-share.031')
}
</script>

<template>
  <main class="reservation-public-share">
    <section class="reservation-public-share__shell" :aria-label="gt('generated.reservation-public-share.001')">
      <section v-if="isLoading" class="reservation-public-share__state" aria-live="polite"> {{ gt('generated.reservation-public-share.002') }} </section>

      <section v-else-if="errorText" class="reservation-public-share__state" role="alert">
        <strong>{{ errorText }}</strong>
        <span>{{ gt('generated.reservation-public-share.003') }}</span>
      </section>

      <template v-else-if="share">
        <section
          v-if="customerIntroText"
          class="reservation-public-share__intro"
          :aria-label="gt('generated.reservation-public-share.004')"
        >
          {{ customerIntroText }}
        </section>

        <section class="reservation-public-share__focus" :aria-label="gt('generated.reservation-public-share.005')">
          <p class="reservation-public-share__focus-label">{{ gt('generated.reservation-public-share.006') }}</p>
          <h1>{{ share.storeName }}</h1>

          <div class="reservation-public-share__datetime">
            <div>
              <span>{{ gt('generated.reservation-public-share.007') }}</span>
              <strong>{{ share.reservationDate }}</strong>
            </div>
            <div>
              <span>{{ gt('generated.reservation-public-share.008') }}</span>
              <strong>{{ share.reservationTime }}</strong>
            </div>
          </div>

          <div class="reservation-public-share__table">
            <span>{{ gt('generated.reservation-public-share.009') }}</span>
            <strong>{{ tableLabel }}</strong>
            <small>{{ tableStatus }}</small>
          </div>

          <div class="reservation-public-share__party">
            <span>{{ gt('generated.reservation-public-share.010') }}</span>
            <strong>{{ share.partySize }}{{ gt('generated.reservation-public-share.011') }}</strong>
          </div>
        </section>

        <section
          v-if="customerVisibleShareText"
          class="reservation-public-share__template"
          :aria-label="gt('generated.reservation-public-share.012')"
        >
          {{ customerVisibleShareText }}
        </section>

        <footer class="reservation-public-share__actions">
          <nav
            v-if="share.googleMapUrl || share.storePhone || share.storeEmail || share.storeWhatsappPhone"
            class="reservation-public-share__contact-actions"
            :aria-label="gt('generated.reservation-public-share.013')"
          >
            <a
              v-if="share.googleMapUrl"
              :href="share.googleMapUrl"
              target="_blank"
              rel="noopener noreferrer"
            > {{ gt('generated.reservation-public-share.014') }} </a>
            <a v-if="share.storePhone" :href="`tel:${share.storePhone}`">{{ gt('generated.reservation-public-share.015') }}</a>
            <a v-if="share.storeEmail" :href="`mailto:${share.storeEmail}`">{{ gt('generated.reservation-public-share.016') }}</a>
            <a
              v-if="whatsappContactUrl"
              :href="whatsappContactUrl"
              target="_blank"
              rel="noopener noreferrer"
            >
              WhatsApp
            </a>
          </nav>
          <button type="button" @click="shareCurrentPage">{{ gt('generated.reservation-public-share.017') }}</button>
          <p v-if="shareStatusText" role="status">{{ shareStatusText }}</p>
          <textarea
            v-if="fallbackUrl"
            readonly
            :value="fallbackUrl"
            :aria-label="gt('generated.reservation-public-share.018')"
          ></textarea>
        </footer>
      </template>
    </section>
  </main>
</template>

<style scoped>
.reservation-public-share {
  background:
    linear-gradient(180deg, rgba(255, 247, 237, 0.92), rgba(240, 253, 250, 0.78)),
    #f8fafc;
  color: #0f172a;
  min-height: 100dvh;
  padding: 18px;
}

.reservation-public-share__shell {
  display: grid;
  gap: 14px;
  margin: 0 auto;
  max-width: 520px;
}

.reservation-public-share__state span,
.reservation-public-share__actions p {
  color: #334155;
  font-size: 0.9rem;
  font-weight: 800;
  margin: 0;
  overflow-wrap: anywhere;
}

.reservation-public-share__state,
.reservation-public-share__intro,
.reservation-public-share__focus,
.reservation-public-share__template,
.reservation-public-share__actions {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px;
}

.reservation-public-share__state {
  display: grid;
  gap: 6px;
}

.reservation-public-share__intro {
  color: #0f172a;
  font-size: 0.98rem;
  font-weight: 850;
  line-height: 1.72;
  white-space: pre-line;
  word-break: break-word;
}

.reservation-public-share__focus {
  display: grid;
  gap: 14px;
}

.reservation-public-share__focus-label,
.reservation-public-share__datetime span,
.reservation-public-share__table span,
.reservation-public-share__party span {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 900;
  margin: 0;
}

.reservation-public-share__focus h1 {
  color: #0f172a;
  font-size: 1.28rem;
  font-weight: 950;
  letter-spacing: 0;
  line-height: 1.24;
  margin: 0;
  overflow-wrap: anywhere;
}

.reservation-public-share__datetime {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.reservation-public-share__datetime div,
.reservation-public-share__party {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 12px;
}

.reservation-public-share__datetime strong,
.reservation-public-share__party strong {
  color: #0f172a;
  font-size: 1.3rem;
  font-weight: 950;
  line-height: 1.18;
  overflow-wrap: anywhere;
}

.reservation-public-share__table {
  background: #ecfdf5;
  border: 1px solid #5eead4;
  border-radius: 8px;
  display: grid;
  gap: 5px;
  padding: 14px;
}

.reservation-public-share__table strong {
  color: #0f766e;
  font-size: 2.15rem;
  font-weight: 950;
  letter-spacing: 0;
  line-height: 1;
  overflow-wrap: anywhere;
}

.reservation-public-share__table small {
  color: #0f766e;
  font-size: 0.9rem;
  font-weight: 900;
}

.reservation-public-share__template {
  color: #0f172a;
  font-size: 0.95rem;
  font-weight: 800;
  line-height: 1.72;
  white-space: pre-line;
  word-break: break-word;
}

.reservation-public-share__state strong {
  color: #b42318;
  font-size: 1rem;
  font-weight: 950;
}

.reservation-public-share__actions {
  display: grid;
  gap: 10px;
}

.reservation-public-share__contact-actions {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(auto-fit, minmax(128px, 1fr));
}

.reservation-public-share__contact-actions a,
.reservation-public-share__actions button {
  align-items: center;
  border-radius: 8px;
  display: inline-flex;
  font-size: 0.92rem;
  font-weight: 950;
  justify-content: center;
  min-height: 42px;
  text-decoration: none;
}

.reservation-public-share__contact-actions a {
  background: #ffffff;
  border: 1px solid #99f6e4;
  color: #0f766e;
}

.reservation-public-share__actions button {
  background: #f97316;
  border: 1px solid #f97316;
  color: #ffffff;
}

.reservation-public-share__actions textarea {
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: #0f172a;
  min-height: 78px;
  padding: 10px;
  resize: vertical;
  width: 100%;
}

.reservation-public-share a:focus-visible,
.reservation-public-share button:focus-visible,
.reservation-public-share textarea:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.26);
  outline-offset: 2px;
}

@media (max-width: 420px) {
  .reservation-public-share {
    padding: 12px;
  }

  .reservation-public-share__datetime strong,
  .reservation-public-share__party strong {
    font-size: 1.18rem;
  }

  .reservation-public-share__table strong {
    font-size: 1.9rem;
  }

  .reservation-public-share__contact-actions {
    grid-template-columns: 1fr;
  }
}
</style>
