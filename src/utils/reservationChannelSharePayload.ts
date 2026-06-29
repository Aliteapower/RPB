import type { ReservationShareInfo } from '../types/reservationShareInfo'

export function reservationWechatShareText(info: ReservationShareInfo, shareUrl: string): string {
  const directText = info.wechatShareText?.trim()
  if (directText) {
    return directText
  }

  const text = info.shareText?.trim() ?? ''
  const url = shareUrl.trim()
  if (text && url) {
    return `${text}\n\n${url}`
  }

  return text || url
}
