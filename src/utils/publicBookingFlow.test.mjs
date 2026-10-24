import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'

import { createServer } from 'vite'

let resolvePublicBookingLoginFlow
let vite

before(async () => {
  vite = await createServer({
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true }
  })
  const flowModule = await vite
    .ssrLoadModule('/src/utils/publicBookingFlow.ts')
    .catch(() => ({}))
  ;({ resolvePublicBookingLoginFlow } = flowModule)
})

after(async () => {
  await vite.close()
})

test('public booking flow follows the persisted customer login requirement', () => {
  assert.equal(typeof resolvePublicBookingLoginFlow, 'function')

  assert.deepEqual(resolvePublicBookingLoginFlow(false, false), {
    canEnterContact: true,
    nextStepAfterSelection: 3,
    previousStepFromContact: 1,
    contactStepNumber: 2
  })
  assert.deepEqual(resolvePublicBookingLoginFlow(true, false), {
    canEnterContact: false,
    nextStepAfterSelection: 2,
    previousStepFromContact: 2,
    contactStepNumber: 3
  })
  assert.deepEqual(resolvePublicBookingLoginFlow(true, true), {
    canEnterContact: true,
    nextStepAfterSelection: 3,
    previousStepFromContact: 2,
    contactStepNumber: 3
  })
  assert.deepEqual(resolvePublicBookingLoginFlow(undefined, false), {
    canEnterContact: false,
    nextStepAfterSelection: 2,
    previousStepFromContact: 2,
    contactStepNumber: 3
  })
})
