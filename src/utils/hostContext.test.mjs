import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'

import { createServer } from 'vite'

let publicBookingUrlForTenant
let vite

before(async () => {
  globalThis.window = { __RPB_HOST_PREFIX_BASE_HOST__: 'booking.yumstone.sg' }
  vite = await createServer({
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true }
  })
  ;({ publicBookingUrlForTenant } = await vite.ssrLoadModule('/src/utils/hostContext.ts'))
})

after(async () => {
  await vite.close()
  delete globalThis.window
})

test('public booking admin links always identify the selected store', () => {
  const storeId = '855df0ef-ade8-4209-aac4-a3afd2de91e3'

  assert.equal(
    publicBookingUrlForTenant('lsc106', storeId, {
      protocol: 'https:',
      hostname: 'lsc106.booking.yumstone.sg',
      port: '',
      origin: 'https://lsc106.booking.yumstone.sg'
    }),
    `https://lsc106.booking.yumstone.sg/book/${storeId}`
  )

  assert.equal(
    publicBookingUrlForTenant('lsc106', storeId, {
      protocol: 'https:',
      hostname: 'booking.yumstone.sg',
      port: '',
      origin: 'https://booking.yumstone.sg'
    }),
    `https://lsc106.booking.yumstone.sg/book/${storeId}`
  )
})
