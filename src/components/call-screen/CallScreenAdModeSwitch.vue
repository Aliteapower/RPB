<script setup lang="ts">
type CallScreenAdMode = 'text' | 'media'

const props = withDefaults(defineProps<{
  modelValue: CallScreenAdMode
  textLabel?: string
  mediaLabel?: string
  ariaLabel?: string
  disabled?: boolean
}>(), {
  textLabel: '文案轮播',
  mediaLabel: '图片/视频轮播',
  ariaLabel: '轮播类型',
  disabled: false
})

const emit = defineEmits<{
  'update:modelValue': [value: CallScreenAdMode]
}>()

function selectMode(value: CallScreenAdMode): void {
  if (props.disabled || props.modelValue === value) {
    return
  }
  emit('update:modelValue', value)
}
</script>

<template>
  <div class="call-screen-ad-mode-switch" role="radiogroup" :aria-label="ariaLabel">
    <label class="call-screen-ad-mode-switch__option" :class="{ selected: modelValue === 'text' }">
      <input
        type="radio"
        value="text"
        :checked="modelValue === 'text'"
        :disabled="disabled"
        @change="selectMode('text')"
      />
      <span class="call-screen-ad-mode-switch__marker" aria-hidden="true" />
      <span>{{ textLabel }}</span>
    </label>
    <label class="call-screen-ad-mode-switch__option" :class="{ selected: modelValue === 'media' }">
      <input
        type="radio"
        value="media"
        :checked="modelValue === 'media'"
        :disabled="disabled"
        @change="selectMode('media')"
      />
      <span class="call-screen-ad-mode-switch__marker" aria-hidden="true" />
      <span>{{ mediaLabel }}</span>
    </label>
  </div>
</template>

<style scoped>
.call-screen-ad-mode-switch {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.call-screen-ad-mode-switch__option {
  align-items: center;
  background: #f8fafc;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  color: #475569;
  cursor: pointer;
  display: flex;
  font-weight: 800;
  gap: 9px;
  min-height: 42px;
  padding: 0 12px;
}

.call-screen-ad-mode-switch__option.selected {
  background: #f0fdfa;
  border-color: #5eead4;
  color: #115e59;
}

.call-screen-ad-mode-switch__option:has(input:focus-visible) {
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.16);
}

.call-screen-ad-mode-switch__option:has(input:disabled) {
  cursor: default;
  opacity: 0.62;
}

.call-screen-ad-mode-switch__option input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.call-screen-ad-mode-switch__marker {
  background: #cbd5e1;
  border-radius: 999px;
  height: 9px;
  width: 9px;
}

.call-screen-ad-mode-switch__option.selected .call-screen-ad-mode-switch__marker {
  background: #0f766e;
  box-shadow: 0 0 0 4px rgba(20, 184, 166, 0.14);
}

@media (max-width: 980px) {
  .call-screen-ad-mode-switch {
    grid-template-columns: 1fr;
  }
}
</style>
