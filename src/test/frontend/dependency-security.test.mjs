import { test } from 'node:test'
import assert from 'node:assert/strict'
import { ssrRenderAttrs } from '@vue/server-renderer'

test('Vue rejects carriage returns in attribute names instead of emitting extra HTML attributes', t => {
  const warnings = []
  t.mock.method(console, 'warn', message => warnings.push(message))
  t.mock.method(console, 'error', message => warnings.push(message))

  const attributes = ssrRenderAttrs({
    'data-customer': 'safe-label',
    ['data-label\ronfocus']: 'untrusted-handler',
  })

  assert.equal(attributes, ' data-customer="safe-label"')
  assert.ok(warnings.length > 0)
})

test('Vue preserves normal data attributes and escapes untrusted customer text', () => {
  const attributes = ssrRenderAttrs({ 'data-customer': '<顾客> & "test"' })
  assert.equal(attributes, ' data-customer="&lt;顾客&gt; &amp; &quot;test&quot;"')
})
