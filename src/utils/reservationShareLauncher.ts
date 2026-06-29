import { copyPlainText } from './plainTextClipboard'

export type ShareLinkResult = 'native-share' | 'copied' | 'manual' | 'cancelled'

export interface ShareLinkPayload {
  title: string
  text: string
  url: string
}

export async function shareLinkOrCopy(payload: ShareLinkPayload): Promise<ShareLinkResult> {
  const sharePayload: ShareData = {
    title: payload.title,
    text: payload.text,
    url: payload.url
  }

  if (canUseNativeShare(sharePayload)) {
    try {
      await navigator.share(sharePayload)
      return 'native-share'
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return 'cancelled'
      }
    }
  }

  if (await copyPlainText(payload.url)) {
    return 'copied'
  }

  return 'manual'
}

export function canUseNativeShare(payload: ShareData): boolean {
  if (typeof navigator === 'undefined' || typeof navigator.share !== 'function') {
    return false
  }

  if (!isLikelyMobileShareSurface()) {
    return false
  }

  if (typeof navigator.canShare === 'function' && !navigator.canShare(payload)) {
    return false
  }

  return true
}

function isLikelyMobileShareSurface(): boolean {
  if (typeof navigator === 'undefined' || typeof window === 'undefined') {
    return false
  }

  const userAgent = navigator.userAgent.toLowerCase()
  if (/android|iphone|ipad|ipod/.test(userAgent)) {
    return true
  }

  return navigator.maxTouchPoints > 1 && window.matchMedia('(pointer: coarse)').matches
}
