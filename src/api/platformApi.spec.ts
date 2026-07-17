import { describe, expect, it, vi } from 'vitest'

import { deleteOperatingEntity, PlatformApiError } from './platformApi'

function response(status: number, payload: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(payload)
  } as Response
}

describe('deleteOperatingEntity', () => {
  it('encodes path segments and sends an authenticated DELETE request', async () => {
    const payload = {
      success: true,
      operatingEntity: {
        id: 'entity/one', tenantId: 'tenant one', entityCode: 'entity-one', displayName: 'Entity One',
        status: 'archived', defaultLocale: 'en-SG', contactPhone: null, address: null,
        principalName: null, deleted: true, createdAt: '2026-07-17T00:00:00Z',
        updatedAt: '2026-07-17T01:00:00Z', deletedAt: '2026-07-17T01:00:00Z'
      }
    }
    const fetcher = vi.fn(async () => response(200, payload)) as unknown as typeof fetch

    await expect(deleteOperatingEntity('tenant one', 'entity/one', fetcher)).resolves.toEqual(payload)
    expect(fetcher).toHaveBeenCalledWith(
      '/api/v1/platform/tenants/tenant%20one/operating-entities/entity%2Fone',
      expect.objectContaining({ method: 'DELETE', credentials: 'include', body: undefined })
    )
  })

  it('wraps a conflict response in PlatformApiError', async () => {
    const payload = {
      success: false as const,
      error: {
        code: 'OPERATING_ENTITY_HAS_STORES',
        messageKey: 'platform.tenants.operating_entity_has_stores',
        details: {}
      }
    }
    const fetcher = vi.fn(async () => response(409, payload)) as unknown as typeof fetch

    const error = await deleteOperatingEntity('tenant', 'entity', fetcher).catch(candidate => candidate)

    expect(error).toBeInstanceOf(PlatformApiError)
    expect(error).toMatchObject({ status: 409, response: payload })
  })
})
