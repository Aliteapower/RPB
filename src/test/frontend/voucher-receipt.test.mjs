import test from 'node:test'
import assert from 'node:assert/strict'
import {voucherReceipt} from '../../modules/cards/vouchers/receipt.ts'

test('one browser keeps its existing receipt per campaign across page instances', () => {
  const values = new Map([['rpb.voucher.receipt:old', 'existing-receipt']])
  const storage = {getItem:key=>values.get(key)??null, setItem:(key,value)=>values.set(key,value)}
  assert.equal(voucherReceipt('old', storage), 'existing-receipt')
  const first = voucherReceipt('a', storage)
  assert.match(first, /^[0-9a-f-]{36}$/)
  assert.equal(voucherReceipt('a', storage), first)
  assert.notEqual(voucherReceipt('b', storage), first)
  const otherPhone = {getItem:()=>null, setItem:()=>{}}
  assert.notEqual(voucherReceipt('a', otherPhone), first)
})
