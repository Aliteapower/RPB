import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          const normalized = id.replace(/\\/g, '/')
          if (normalized.includes('/node_modules/')) {
            return 'vendor'
          }
          if (normalized.includes('/src/i18n/')) {
            return 'i18n'
          }
          if (normalized.includes('/src/api/')) {
            return 'api'
          }
        }
      }
    }
  },
  server: {
    host: '127.0.0.1',
    port: 5176,
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true
      }
    }
  }
})
