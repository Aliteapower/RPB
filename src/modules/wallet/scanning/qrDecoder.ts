import jsQR from 'jsqr'

/** Canvas stays local. A small centre crop saves CPU; periodic whole frames catch off-centre codes. */
export function createQrDecoder() {
  const canvas = document.createElement('canvas')
  const context = canvas.getContext('2d', { willReadFrequently: true })
  function decode(source: CanvasImageSource, width: number, height: number, fullFrame: boolean, maxPixels: number) {
    if (!context) throw new Error('QR canvas unavailable')
    if (!width || !height) return null
    const size = Math.min(width, height) * .82
    const sw = fullFrame ? width : size, sh = fullFrame ? height : size
    const scale = Math.min(1, maxPixels / Math.max(sw, sh))
    canvas.width = Math.max(1, Math.round(sw * scale)); canvas.height = Math.max(1, Math.round(sh * scale))
    context.drawImage(source, (width - sw) / 2, (height - sh) / 2, sw, sh, 0, 0, canvas.width, canvas.height)
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height)
    return jsQR(pixels.data, pixels.width, pixels.height, { inversionAttempts: 'attemptBoth' })?.data ?? null
  }
  return {
    video: (video: HTMLVideoElement, fullFrame: boolean) => decode(video, video.videoWidth, video.videoHeight, fullFrame, fullFrame ? 960 : 640),
    image: (image: HTMLImageElement) => decode(image, image.naturalWidth, image.naturalHeight, true, 1920) ?? decode(image, image.naturalWidth, image.naturalHeight, false, 1280)
  }
}
