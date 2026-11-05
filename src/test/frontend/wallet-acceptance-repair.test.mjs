import { test } from 'node:test'
import assert from 'node:assert/strict'
import { storesForEntity, walletBillingWarning } from '../../components/platform/storeBillingEligibility.ts'
const linked = { id: 'A', status: 'active', operatingEntityId: 'E' }
const orphan = { id: 'B', status: 'active', operatingEntityId: null }
test('unassigned legacy stores remain accessible without mixing other entity stores', () => {
  const all = [linked, orphan, { ...linked, id: 'C', operatingEntityId: 'F' }]
  assert.deepEqual(storesForEntity(all, 'unassigned'), [orphan])
  assert.deepEqual(storesForEntity(all, 'E'), [linked])
  assert.deepEqual(storesForEntity(all, ''), all)
})
test('billing distinguishes absent, inactive, unassigned, unselected, subscribed stores and inactive pricing', () => {
  const reason = (stores, count = 0, eligible = 0, price = true) => walletBillingWarning(stores, count, eligible, price)
  assert.equal(reason([]), 'noStores')
  assert.equal(reason([{ ...orphan, status: 'inactive' }]), 'noActiveStores')
  assert.equal(reason([orphan]), 'unassignedStores')
  assert.equal(reason([linked], 0, 1), 'selectStores')
  assert.equal(reason([linked]), 'noAdditionalStores')
  assert.equal(reason([linked], 1, 1, false), 'inactivePrice')
  assert.equal(reason([linked], 1, 1), '')
})
