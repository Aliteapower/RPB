import type {
  QueueDisplayAdSlide,
  QueueDisplayAds,
  QueueDisplayApiErrorResponse,
  QueueDisplayCurrentCall,
  QueueDisplayStateResponse,
  QueueDisplayStoreTime,
  QueueDisplayTextAdSlide,
  QueueDisplayWaiting,
  QueueDisplayWaitingPreviewItem
} from '../types/queueDisplay'

export class QueueDisplayApiError extends Error {
  readonly status: number
  readonly response: QueueDisplayApiErrorResponse

  constructor(status: number, response: QueueDisplayApiErrorResponse) {
    super(response.error.messageKey)
    this.name = 'QueueDisplayApiError'
    this.status = status
    this.response = response
  }
}

export async function fetchQueueDisplayState(
  storeId: string,
  fetcher: typeof fetch = fetch
): Promise<QueueDisplayStateResponse> {
  const endpoint = `/api/v1/stores/${encodeURIComponent(storeId)}/queue-display/state`
  let response: Response

  try {
    response = await fetcher(endpoint, {
      method: 'GET',
      headers: {
        Accept: 'application/json'
      }
    })
  } catch {
    throw new QueueDisplayApiError(0, unknownError())
  }

  const payload = await readJson(response)

  if (!response.ok || isQueueDisplayApiErrorResponse(payload)) {
    throw new QueueDisplayApiError(
      response.status,
      isQueueDisplayApiErrorResponse(payload) ? payload : unknownError(response.status)
    )
  }

  if (!isQueueDisplayStateResponse(payload)) {
    throw new QueueDisplayApiError(response.status, unknownError(response.status))
  }

  return payload
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text()

  if (!text) {
    return null
  }

  try {
    return JSON.parse(text) as unknown
  } catch {
    return null
  }
}

function unknownError(httpStatus?: number): QueueDisplayApiErrorResponse {
  return {
    success: false,
    error: {
      code: 'UNKNOWN_ERROR',
      messageKey: 'queue.display.unknown_error',
      details: httpStatus === undefined ? {} : { httpStatus }
    }
  }
}

function isQueueDisplayApiErrorResponse(
  payload: unknown
): payload is QueueDisplayApiErrorResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<QueueDisplayApiErrorResponse>
  return (
    candidate.success === false &&
    typeof candidate.error?.code === 'string' &&
    typeof candidate.error.messageKey === 'string'
  )
}

function isQueueDisplayStateResponse(payload: unknown): payload is QueueDisplayStateResponse {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<QueueDisplayStateResponse>
  return (
    candidate.success === true &&
    typeof candidate.serverNow === 'string' &&
    isOptionalString(candidate.storeDisplayName) &&
    isOptionalString(candidate.storeName) &&
    isStoreTime(candidate.storeTime) &&
    (candidate.currentCall === null || isCurrentCall(candidate.currentCall)) &&
    isWaiting(candidate.waiting) &&
    isAds(candidate.ads)
  )
}

function isStoreTime(value: unknown): value is QueueDisplayStoreTime {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Partial<QueueDisplayStoreTime>
  return (
    typeof candidate.timezone === 'string' &&
    typeof candidate.timeText === 'string' &&
    typeof candidate.businessDate === 'string'
  )
}

function isCurrentCall(value: unknown): value is QueueDisplayCurrentCall {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Partial<QueueDisplayCurrentCall>
  return (
    typeof candidate.queueTicketId === 'string' &&
    typeof candidate.displayNumber === 'string' &&
    typeof candidate.customerDisplayName === 'string' &&
    typeof candidate.partySize === 'number' &&
    typeof candidate.partySizeGroup === 'string' &&
    typeof candidate.calledAt === 'string' &&
    typeof candidate.holdUntilAt === 'string'
  )
}

function isWaiting(value: unknown): value is QueueDisplayWaiting {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Partial<QueueDisplayWaiting>
  return (
    typeof candidate.count === 'number' &&
    Array.isArray(candidate.preview) &&
    candidate.preview.every(isWaitingPreviewItem)
  )
}

function isWaitingPreviewItem(value: unknown): value is QueueDisplayWaitingPreviewItem {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Partial<QueueDisplayWaitingPreviewItem>
  return (
    typeof candidate.displayNumber === 'string' &&
    typeof candidate.customerDisplayName === 'string' &&
    typeof candidate.partySize === 'number' &&
    typeof candidate.partySizeGroup === 'string'
  )
}

function isAds(value: unknown): value is QueueDisplayAds {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Partial<QueueDisplayAds>
  const slides = candidate.slides
  const slideShapeIsValid =
    Array.isArray(slides) &&
    slides.every(isTextAdSlide)

  return (
    candidate.mode === 'text' &&
    typeof candidate.slideDurationSeconds === 'number' &&
    (candidate.statePollSeconds === undefined || typeof candidate.statePollSeconds === 'number') &&
    slideShapeIsValid
  )
}

function isTextAdSlide(value: unknown): value is QueueDisplayTextAdSlide {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.slideId === 'string' &&
    typeof candidate.title === 'string' &&
    typeof candidate.subtitle === 'string' &&
    typeof candidate.tagline === 'string'
  )
}

function isOptionalString(value: unknown): value is string | null | undefined {
  return value === undefined || value === null || typeof value === 'string'
}
