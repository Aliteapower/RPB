import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  test: {
    clearMocks: true,
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
    restoreMocks: true,
    setupFiles: ['./src/test/setup.ts']
  }
})
