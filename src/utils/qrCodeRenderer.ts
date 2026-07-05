import QRCode from 'qrcode'

export type QrCodeErrorCorrectionLevel = 'L' | 'M' | 'Q' | 'H'

export interface QrCodeRenderOptions {
  value: string
  logoUrl?: string | null
  size?: number
  margin?: number
  foreground?: string
  background?: string
  errorCorrectionLevel?: QrCodeErrorCorrectionLevel
  logoSizeRatio?: number
}

export async function renderQrCodeToCanvas(
  canvas: HTMLCanvasElement,
  options: QrCodeRenderOptions
): Promise<void> {
  const value = options.value.trim()
  if (!value) {
    throw new Error('qr_code_value_required')
  }
  const size = options.size ?? 220
  await QRCode.toCanvas(canvas, value, {
    color: {
      dark: options.foreground ?? '#0f172a',
      light: options.background ?? '#ffffff'
    },
    errorCorrectionLevel: options.errorCorrectionLevel ?? 'H',
    margin: options.margin ?? 2,
    width: size
  })

  if (options.logoUrl?.trim()) {
    await drawLogoOnCanvas(canvas, options.logoUrl.trim(), options.logoSizeRatio ?? 0.22)
  }
}

export async function drawLogoOnCanvas(
  canvas: HTMLCanvasElement,
  logoUrl: string,
  logoSizeRatio: number
): Promise<boolean> {
  const context = canvas.getContext('2d')
  if (!context) {
    return false
  }
  try {
    const image = await loadImage(logoUrl)
    const logoSize = Math.round(canvas.width * logoSizeRatio)
    const padding = Math.max(8, Math.round(logoSize * 0.18))
    const backgroundSize = logoSize + padding * 2
    const x = Math.round((canvas.width - backgroundSize) / 2)
    const y = Math.round((canvas.height - backgroundSize) / 2)
    const logoX = x + padding
    const logoY = y + padding

    context.save()
    roundedRect(context, x, y, backgroundSize, backgroundSize, Math.round(backgroundSize * 0.18))
    context.fillStyle = '#ffffff'
    context.fill()
    context.clip()
    context.drawImage(image, logoX, logoY, logoSize, logoSize)
    context.restore()
    return true
  } catch {
    return false
  }
}

export function qrCanvasToPngDataUrl(canvas: HTMLCanvasElement): string {
  return canvas.toDataURL('image/png')
}

export function safeQrDownloadFileName(fileName: string | null | undefined): string {
  const cleaned = (fileName || 'qr-code.png')
    .trim()
    .replace(/[\\/:*?"<>|]+/g, '-')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
  const normalized = cleaned || 'qr-code.png'
  return normalized.toLowerCase().endsWith('.png') ? normalized : `${normalized}.png`
}

function loadImage(source: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.crossOrigin = 'anonymous'
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('qr_code_logo_load_failed'))
    image.src = source
  })
}

function roundedRect(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number
): void {
  const right = x + width
  const bottom = y + height
  context.beginPath()
  context.moveTo(x + radius, y)
  context.lineTo(right - radius, y)
  context.quadraticCurveTo(right, y, right, y + radius)
  context.lineTo(right, bottom - radius)
  context.quadraticCurveTo(right, bottom, right - radius, bottom)
  context.lineTo(x + radius, bottom)
  context.quadraticCurveTo(x, bottom, x, bottom - radius)
  context.lineTo(x, y + radius)
  context.quadraticCurveTo(x, y, x + radius, y)
  context.closePath()
}
