import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it } from 'vitest'

describe('Vue behaviour-test harness', () => {
  it('mounts and reacts to a DOM action in jsdom', async () => {
    const component = defineComponent({
      data: () => ({ count: 0 }),
      template: '<button type="button" @click="count += 1">{{ count }}</button>'
    })
    const wrapper = mount(component)

    await wrapper.get('button').trigger('click')

    expect(wrapper.get('button').text()).toBe('1')
    wrapper.unmount()
  })
})
