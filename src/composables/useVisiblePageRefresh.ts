import { onMounted, onUnmounted } from 'vue'

/** Refresh read models only while visible; callers retain request and business guards. */
export function useVisiblePageRefresh(refresh: () => void | Promise<unknown>, ready: () => boolean, intervalMs = 30_000) {
  let timer: ReturnType<typeof setInterval> | undefined
  let browserWindow: Window | undefined
  let browserDocument: Document | undefined
  let disposed = false
  function refreshVisible() {
    if (!disposed && browserDocument?.visibilityState !== 'hidden' && ready()) void refresh()
  }
  onMounted(() => {
    if (typeof window === 'undefined') return
    browserWindow = window
    browserDocument = typeof document === 'undefined' ? undefined : document
    browserWindow.addEventListener('focus', refreshVisible)
    browserWindow.addEventListener('pageshow', refreshVisible)
    browserDocument?.addEventListener('visibilitychange', refreshVisible)
    timer = setInterval(refreshVisible, intervalMs)
  })
  onUnmounted(() => {
    disposed = true
    if (timer !== undefined) clearInterval(timer)
    browserWindow?.removeEventListener('focus', refreshVisible)
    browserWindow?.removeEventListener('pageshow', refreshVisible)
    browserDocument?.removeEventListener('visibilitychange', refreshVisible)
  })
}
