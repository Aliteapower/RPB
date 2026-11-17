import test from 'node:test'
import assert from 'node:assert/strict'
import {staffScanCode} from '../../modules/shared-commerce/scanning/staffCode.mjs'

test('staff scanner recognizes a voucher credential and rejects malformed values', () => {
  const code = 'VC.' + 'a'.repeat(64)
  assert.deepEqual(staffScanCode(code, 'http://localhost'), {kind:'voucher', code})
  assert.equal(staffScanCode(code.slice(0, -1), 'http://localhost'), null)
})
