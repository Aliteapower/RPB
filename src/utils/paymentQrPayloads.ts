export function extractPayNowQrPayload(qrPayloadsJson: string | null | undefined): string {
  if (!qrPayloadsJson) {
    return ''
  }

  try {
    const payload = JSON.parse(qrPayloadsJson) as Record<string, unknown>
    const payloads = payload.payloads as Record<string, unknown> | undefined
    const paynow = payloads?.paynow as Record<string, unknown> | undefined
    return typeof paynow?.payload === 'string' ? paynow.payload : ''
  } catch {
    return ''
  }
}
